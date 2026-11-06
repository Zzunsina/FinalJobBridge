package com.employment.job.service;

import static com.employment.global.common.Database.*;

import com.employment.global.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EmploymentService {

    private final Database db;
    private final EmploymentReferrals referrals;
    private final ResumeFiles files;
    private final Assessments assessments;
    private final CareerAssessments careerAssessments;
    private final ObjectMapper json;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    // EmploymentService.java 상단부
    private static final String PUBLIC_JOB =
        " FROM job_postings j JOIN companies c ON c.company_id=j.company_id JOIN users u ON u.user_id=c.user_id WHERE c.approval_status='APPROVED' AND u.status='ACTIVE' ";

    // 👇 c.address, c.latitude, c.longitude 추가
    private static final String JOB_COLUMNS = "j.*, c.company_name, c.industry, c.address, c.latitude, c.longitude";
    public EmploymentService(Database db, ObjectMapper json, org.springframework.jdbc.core.JdbcTemplate jdbc, ResumeFiles files, Assessments assessments, EmploymentReferrals referrals, CareerAssessments careerAssessments) {
        this.db = db;
        this.referrals = referrals;
        this.files = files;
        this.assessments = assessments;
        this.careerAssessments = careerAssessments;
        this.json = json;
        this.jdbc = jdbc;
    }

    @jakarta.annotation.PostConstruct
    public void initializeJobDetailImages(){jdbc.execute("CREATE TABLE IF NOT EXISTS job_detail_images (job_id BIGINT PRIMARY KEY, image_data MEDIUMTEXT NOT NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(job_id) REFERENCES job_postings(job_id) ON DELETE CASCADE)");}
    private String detailImage(long job){var row=db.one("SELECT image_data FROM job_detail_images WHERE job_id=?",job);return row==null?"":Objects.toString(row.get("imageData"),"");}

    public Map<String, Object> jobs(
        String q,
        String region,
        String type,
        String career,
        String sort,
        int page,
        int size
    ) {
        if (
            page < 0 ||
            size < 1 ||
            size > 100 ||
            !Set.of("latest", "deadline").contains(sort)
        ) {
            throw new Problem(400, "페이지 또는 정렬 조건을 확인해 주세요.");
        }
        String where =
            PUBLIC_JOB +
            " AND j.status='PUBLISHED' AND j.end_date>=? AND (LOWER(j.title) LIKE ? OR LOWER(j.description) LIKE ?)";
        List<Object> args = new ArrayList<>(
            List.of(
                today(),
                "%" + q.toLowerCase(Locale.ROOT) + "%",
                "%" + q.toLowerCase(Locale.ROOT) + "%"
            )
        );
        where += WorkRegionFilter.condition(region, args);
        Input.jobType(type, true);
        where += " AND (?='' OR j.job_type=?) AND j.career_type LIKE ?";
        args.add(type);
        args.add(type);
        args.add("%" + career + "%");
        long total = number(
            db.one("SELECT COUNT(*) AS total" + where, args.toArray()),
            "total"
        );
        var params = new ArrayList<>(args);
        params.add(size);
        params.add((long) page * size);
        var rows = db.list(
            "SELECT " +
                JOB_COLUMNS +
                where +
                " ORDER BY " +
                (sort.equals("deadline")
                        ? "j.end_date ASC,j.job_id DESC"
                        : "j.job_id DESC") +
                " LIMIT ? OFFSET ?",
            params.toArray()
        );
        rows.forEach(this::sanitizeJob);
        return fields(
            "content",
            rows,
            "page",
            page,
            "size",
            size,
            "totalElements",
            total,
            "totalPages",
            (total + size - 1) / size,
            "first",
            page == 0,
            "last",
            (long) (page + 1) * size >= total
        );
    }

    public Map<String, Object> publicJob(long id) {
        var row = db.one(
            "SELECT " +
                JOB_COLUMNS +
                PUBLIC_JOB +
                " AND j.job_id=? AND j.status IN ('PUBLISHED','CLOSED')",
            id
        );
        if(row==null)throw new Problem(404,"공고를 찾을 수 없습니다.");
        sanitizeJob(row);
        row.put("detailImageUrl",detailImage(id));
        row.put(
            "accepting",
            row.get("status").equals("PUBLISHED") &&
                !today().isBefore(
                    LocalDate.parse(row.get("startDate").toString())
                ) &&
                !today().isAfter(LocalDate.parse(row.get("endDate").toString()))
        );
        return row;
    }

    private void sanitizeJob(Map<String, Object> row) {
        row.remove("reviewReason");
        row.remove("reviewedBy");
        row.remove("reviewedAt");
    }

    public List<Map<String, Object>> companies() {
        return db.list(
            "SELECT c.company_id, c.company_name, c.industry, c.company_size, c.address, c.latitude, c.longitude, c.website, c.description, " +
                "(SELECT j.banner_image_url FROM job_postings j WHERE j.company_id=c.company_id AND j.status='PUBLISHED' AND j.end_date>=CURRENT_DATE AND j.banner_image_url IS NOT NULL AND TRIM(j.banner_image_url)<>'' ORDER BY j.job_id DESC LIMIT 1) AS banner_image_url " +
                "FROM companies c JOIN users u ON u.user_id=c.user_id WHERE c.approval_status='APPROVED' AND u.status='ACTIVE' ORDER BY c.company_id DESC"
        );
    }

    public Map<String, Object> companyDetail(long id) {
        return companies()
            .stream()
            .filter(row -> number(row, "companyId") == id)
            .findFirst()
            .orElseThrow(() -> new Problem(404, "기업을 찾을 수 없습니다."));
    }

    public long seeker(long user) {
        return number(
            db.one(
                "SELECT job_seeker_id FROM job_seekers WHERE user_id=? FOR UPDATE",
                user
            ),
            "jobSeekerId"
        );
    }

    public Map<String, Object> companyProfile(long user) {
        var rows = db.list("SELECT * FROM companies WHERE user_id=?", user);
        return rows.isEmpty()
            ? fields(
                  "profileMissing",
                  true,
                  "approvalStatus",
                  "UNREGISTERED",
                  "companyName",
                  "",
                  "businessNumber",
                  ""
              )
            : rows.get(0);
    }

    public Map<String, Object> company(long user, boolean approved) {
        var company = db.one(
            "SELECT * FROM companies WHERE user_id=? FOR UPDATE",
            user
        );
        if (
            approved && !"APPROVED".equals(company.get("approvalStatus"))
        ) throw new Problem(403, "기업 승인 후 이용할 수 있습니다.");
        return company;
    }

    public List<Map<String, Object>> resumes(long user) {
        var rows = db.list(
            "SELECT * FROM resumes WHERE job_seeker_id=? ORDER BY is_default DESC,resume_id DESC",
            seeker(user)
        );
        rows.forEach(this::addResumeDetails);
        return rows;
    }

    public Map<String, Object> resume(long user, long id) {
        var row = db.one(
            "SELECT * FROM resumes WHERE resume_id=? AND job_seeker_id=?",
            id,
            seeker(user)
        );
        addResumeDetails(row);
        return row;
    }

    private void addResumeDetails(Map<String, Object> row) {
        if (row == null) throw new Problem(404, "이력서를 찾을 수 없습니다.");
        row.put("attachment", files.current(number(row, "resumeId")));
        row.put("assessment", assessments.result(number(row, "resumeId")));
        Object details = row.remove("detailsJson");
        try {
            row.put(
                "details",
                details == null ? null : json.readTree(details.toString())
            );
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public Map<String, Object> saveResume(
        long user,
        Long id,
        Map<String, Object> body
    ) {
        long seeker = seeker(user);
        if (id != null) resume(user, id);
        String title = Input.text(body, "title", 200, true);
        String education = Input.text(body, "education", 100, false);
        String career = Input.text(body, "careerDescription", 10000, false);
        String skills = Input.text(body, "skills", 10000, false);
        String url = Input.url(body, "portfolioUrl");
        boolean visible = Boolean.TRUE.equals(body.get("isPublic"));
        String details = null;
        if (body.containsKey("details")) {
            try {
                details = json.writeValueAsString(
                    ResumeDetails.validate(body.get("details"))
                );
                if (
                    details.getBytes(
                        java.nio.charset.StandardCharsets.UTF_8
                    ).length >
                    60000
                ) throw new Problem(
                    400,
                    "이력서 상세 내용이 너무 깁니다. 내용을 조금 줄여 주세요."
                );
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new IllegalArgumentException(ex);
            }
        }
        if (id == null) id = db.insert(
            "resumes",
            fields(
                "job_seeker_id",
                seeker,
                "title",
                title,
                "education",
                education,
                "career_description",
                career,
                "skills",
                skills,
                "portfolio_url",
                url,
                "is_public",
                visible
            )
        );
        else {
            resume(user, id);
            db.update(
                "UPDATE resumes SET title=?,education=?,career_description=?,skills=?,portfolio_url=?,is_public=?,updated_at=CURRENT_TIMESTAMP WHERE resume_id=?",
                title,
                education,
                career,
                skills,
                url,
                visible,
                id
            );
        }
        if (body.containsKey("details")) db.update(
            "UPDATE resumes SET details_json=? WHERE resume_id=?",
            details,
            id
        );
        if (Boolean.TRUE.equals(body.get("isDefault"))) defaultResume(user, id);
        else if (body.containsKey("isDefault")) db.update(
            "UPDATE resumes SET is_default=FALSE WHERE resume_id=?",
            id
        );
        if (body.containsKey("attachment")) files.save(id, body.get("attachment"));
        if (body.containsKey("assessmentAnswers")) assessments.grade(id, body.get("assessmentAnswers"));
        return resume(user, id);
    }

    public void defaultResume(long user, long id) {
        resume(user, id);
        db.update(
            "UPDATE resumes SET is_default=FALSE WHERE job_seeker_id=?",
            seeker(user)
        );
        db.update("UPDATE resumes SET is_default=TRUE WHERE resume_id=?", id);
    }

    public void deleteResume(long user, long id) {
        resume(user, id);
        if (
            !db
                .list(
                    "SELECT application_id FROM applications WHERE resume_id=?",
                    id
                )
                .isEmpty()
        ) throw new Problem(409, "지원에 사용한 이력서는 삭제할 수 없습니다.");
        db.update("DELETE FROM resumes WHERE resume_id=?", id);
    }

    public List<Map<String, Object>> bookmarks(long user) {
        var rows = db.list(
            "SELECT b.bookmark_id,b.job_id FROM job_bookmarks b WHERE b.job_seeker_id=? ORDER BY b.bookmark_id DESC",
            seeker(user)
        );
        for (var row : rows) {
            try {
                row.put("job", publicJob(number(row, "jobId")));
            } catch (Problem ex) {
                row.put("unavailable", true);
            }
        }
        return rows;
    }

    public void bookmark(long user, long id, boolean add) {
        long seeker = seeker(user);
        if (add) {
            publicJob(id);
            if (
                db
                    .list(
                        "SELECT bookmark_id FROM job_bookmarks WHERE job_seeker_id=? AND job_id=?",
                        seeker,
                        id
                    )
                    .isEmpty()
            ) {
                db.insert(
                    "job_bookmarks",
                    fields("job_seeker_id", seeker, "job_id", id)
                );
            }
        } else db.update(
            "DELETE FROM job_bookmarks WHERE job_seeker_id=? AND job_id=?",
            seeker,
            id
        );
    }

    public Map<String, Object> apply(
        long user,
        long jobId,
        Map<String, Object> body
    ) {
        long seeker = seeker(user);
        var job = db.one(
            "SELECT * FROM job_postings WHERE job_id=? FOR UPDATE",
            jobId
        );
        var company = db.one(
            "SELECT c.*,u.status AS user_status FROM companies c JOIN users u ON u.user_id=c.user_id WHERE company_id=?",
            job.get("companyId")
        );
        if (
            !"PUBLISHED".equals(job.get("status")) ||
            !"APPROVED".equals(company.get("approvalStatus")) ||
            !"ACTIVE".equals(company.get("userStatus")) ||
            today().isBefore(
                LocalDate.parse(job.get("startDate").toString())
            ) ||
            today().isAfter(LocalDate.parse(job.get("endDate").toString()))
        ) {
            throw new Problem(409, "현재 지원할 수 없는 공고입니다.");
        }
        if (!db.list("SELECT application_id FROM applications WHERE job_id=? AND job_seeker_id=? AND status IN ('APPLIED','UNDER_REVIEW','DOCUMENT_PASSED','INTERVIEW')", jobId, seeker).isEmpty())
            throw new Problem(409, "전형 결과가 나오기 전까지 같은 공고에 중복 지원할 수 없습니다.");
        long resumeId = Input.id(body, "resumeId");
        var resume = resume(user, resumeId);
        long id = db.insert(
            "applications",
            fields(
                "job_id",
                jobId,
                "job_seeker_id",
                seeker,
                "resume_id",
                resumeId,
                "cover_letter",
                Input.text(body, "coverLetter", 10000, false)
            )
        );
        assessments.snapshot(id, jobId, resumeId);
        var contact = db.one(
            "SELECT name,email,phone FROM users WHERE user_id=?",
            user
        );
        var photos = jdbc.query(
            "SELECT image_data FROM user_profile_photos WHERE user_id=?",
            (rs, index) -> rs.getBytes(1), user
        );
        if (!photos.isEmpty()) {
            contact.put("photo", "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(photos.get(0)));
        }
        String snapshot;
        try {
            snapshot = json.writeValueAsString(
                fields(
                    "resume",
                    resume,
                    "contact",
                    contact,
                    "jobTitle",
                    job.get("title"),
                    "careerAssessment",
                    careerAssessments.latestSubmission(user)
                )
            );
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        db.update(
            "INSERT INTO application_snapshots(application_id,snapshot_json) VALUES(?,?)",
            id,
            snapshot
        );
        notify(
            number(company, "userId"),
            "새로운 지원자가 있습니다.",
            "지원자 관리에서 확인해 주세요.",
            "APPLICATION",
            id
        );
        return application(user, id);
    }

    public List<Map<String, Object>> applications(long user) {
        return db.list(
            "SELECT a.*,j.title,c.company_name,(SELECT MIN(i.interview_at) FROM interviews i WHERE i.application_id=a.application_id AND i.status='SCHEDULED') AS next_interview_at FROM applications a JOIN job_postings j ON j.job_id=a.job_id JOIN companies c ON c.company_id=j.company_id WHERE a.job_seeker_id=? ORDER BY a.application_id DESC",
            seeker(user)
        );
    }

    public Map<String, Object> application(long user, long id) {
        var row = db.one(
            "SELECT a.*,j.title,c.company_name FROM applications a JOIN job_postings j ON j.job_id=a.job_id JOIN companies c ON c.company_id=j.company_id WHERE a.application_id=? AND a.job_seeker_id=? FOR UPDATE",
            id,
            seeker(user)
        );
        if(row==null)throw new Problem(404,"지원 내역을 찾을 수 없습니다.");
        addDetails(row, false);
        return row;
    }


    private void addDetails(Map<String, Object> row, boolean internal) {
        if (row == null) throw new Problem(404, "지원 내역을 찾을 수 없습니다.");
        long id = number(row, "applicationId");
        var snapshots = db.list(
            "SELECT snapshot_json FROM application_snapshots WHERE application_id=?",
            id
        );
        try {
            row.put(
                "snapshot",
                snapshots.isEmpty()
                    ? null
                    : json.readTree(
                          snapshots.get(0).get("snapshotJson").toString()
                      )
            );
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
        row.put(
            "interviews",
            db.list(
                "SELECT interview_id,interview_at,interview_type,location,meeting_url,status,applicant_guidance,cancellation_reason" +
                    (internal ? ",result,memo" : "") +
                    " FROM interviews WHERE application_id=? ORDER BY interview_at",
                id
            )
        );
    }

    public void cancel(
        long user,
        long id,
        Map<String, Object> body
    ) {
        String reason = Input.text(
            body,
            "reason",
            1000,
            true
        ).strip();

        if (reason.isBlank()) {
            throw new Problem(400, "지원 취소 사유를 입력해 주세요.");
        }

        // 구직자 본인의 지원인지 확인
        var app = application(user, id);

        if (
            !Set.of(
                "APPLIED",
                "UNDER_REVIEW",
                "DOCUMENT_PASSED",
                "INTERVIEW"
            ).contains(app.get("status"))
        ) {
            throw new Problem(
                409,
                "현재 상태에서는 취소할 수 없습니다."
            );
        }

        // 예정된 면접이 있는지 확인
        var scheduledInterviews = db.list(
            "SELECT interview_id FROM interviews " +
                "WHERE application_id=? AND status='SCHEDULED'",
            id
        );

        // 지원 취소 사유는 예정된 면접에도 함께 저장
        if (!scheduledInterviews.isEmpty()) {
            db.update(
                """
                UPDATE interviews
                SET status='CANCELED',
                    cancellation_reason=?
                WHERE application_id=?
                  AND status='SCHEDULED'
                """,
                reason,
                id
            );
        }

        // 입사지원 전체 취소
        db.update(
            """
            UPDATE applications
            SET status='CANCELED',
                updated_at=CURRENT_TIMESTAMP
            WHERE application_id=?
            """,
            id
        );

        // 해당 기업 사용자 조회
        long owner = number(
            db.one(
                """
                SELECT c.user_id
                FROM companies c
                JOIN job_postings j
                  ON j.company_id=c.company_id
                WHERE j.job_id=?
                """,
                app.get("jobId")
            ),
            "userId"
        );

        // 기업에 취소 사유까지 알림
        notify(
            owner,
            "지원이 취소되었습니다.",
            app.get("title") + " · 지원 취소 사유: " + reason,
            "APPLICATION",
            id
        );
    }

    public List<Map<String, Object>> companyJobs(long user) {
        return db.list(
            "SELECT * FROM job_postings WHERE company_id=? ORDER BY job_id DESC",
            number(company(user, false), "companyId")
        );
    }

    public Map<String, Object> ownJob(long user, long id, boolean approved) {
        var row = db.one(
            "SELECT * FROM job_postings WHERE job_id=? AND company_id=? FOR UPDATE",
            id,
            number(company(user, approved), "companyId")
        );
        row.put("detailImageUrl",detailImage(id));
        row.put("assessmentMinScore", assessments.minimum(id));
        return row;
    }

    public void deleteJob(long user, long id) {
        ownJob(user, id, false);
        removeJob(id);
    }

    public void removeJob(long id) {
        db.one("SELECT job_id FROM job_postings WHERE job_id=? FOR UPDATE", id);
        if (
            number(
                    db.one(
                        "SELECT COUNT(*) AS n FROM applications WHERE job_id=?",
                        id
                    ),
                    "n"
                ) >
                0 ||
            number(
                db.one(
                    "SELECT COUNT(*) AS n FROM employment_links WHERE job_id=?",
                    id
                ),
                "n"
            ) >
            0
        ) {
            throw new Problem(
                409,
                "지원·취업연계 이력이 있는 공고는 삭제할 수 없습니다."
            );
        }
        db.update("DELETE FROM job_bookmarks WHERE job_id=?", id);
        db.update("DELETE FROM job_postings WHERE job_id=?", id);
    }

    public Map<String, Object> saveJob(
        long user,
        Long id,
        Map<String, Object> body
    ) {
        long company = number(company(user, false), "companyId");
        String detail=null;
        if(body.containsKey("detailImageUrl")){try{detail=BannerImageValue.validate(fields("bannerImageUrl",body.get("detailImageUrl")));}catch(Problem error){throw new Problem(400,"4MB 이하의 올바른 JPG 또는 PNG 상세 소개 이미지를 첨부해 주세요.","detailImageUrl");}}
        LocalDate originalStart = null;
        if (id != null) {
            var old = ownJob(user, id, false);
            originalStart = LocalDate.parse(old.get("startDate").toString());
            if (
                Set.of("CLOSED", "PENDING").contains(old.get("status"))
            ) throw new Problem(
                409,
                "심사 중이거나 마감된 공고는 수정할 수 없습니다."
            );
        }
        Map<String, Object> values = new LinkedHashMap<>();
        String[][] texts = {
            { "title", "title", "200" },
            { "jobType", "job_type", "50" },
            { "careerType", "career_type", "50" },
            { "workLocation", "work_location", "255" },
            { "description", "description", "10000" },
            { "education", "education", "100" },
            { "salary", "salary", "100" },
            { "requirements", "requirements", "10000" },
            { "preferredQualifications", "preferred_qualifications", "10000" },
        };
        for (int i = 0; i < texts.length; i++) values.put(
            texts[i][1],
            Input.text(body, texts[i][0], Integer.parseInt(texts[i][2]), i < 5)
        );
        Input.jobType((String) values.get("job_type"), false);
        long headcount = Input.id(body, "headcount");
        if (headcount > 10000) throw new Problem(
            400,
            "모집 인원을 확인해 주세요."
        );
        LocalDate start = Input.date(body, "startDate");
        LocalDate end = Input.date(body, "endDate");
        JobDatePolicy.validate(start, end, originalStart, today());
        if(id!=null) com.employment.ads.AdvertisingLifecycle.validateDates(db,id,start,end);
        values.put("headcount", headcount);
        values.put("start_date", start);
        values.put("end_date", end);
        values.put("status", "DRAFT");
        values.put("review_reason", null);
        if (id == null || body.containsKey("bannerImageUrl")) values.put("banner_image_url", BannerImageValue.validate(body));
        if (id == null) {
            values.put("company_id", company);
            id = db.insert("job_postings", values);
        } else {
            var args = new ArrayList<>(values.values());
            args.add(id);
            db.update(
                "UPDATE job_postings SET " +
                    String.join(
                        ",",
                        values
                            .keySet()
                            .stream()
                            .map(k -> k + "=?")
                            .toList()
                    ) +
                    ",updated_at=CURRENT_TIMESTAMP WHERE job_id=?",
                args.toArray()
            );
        }
        if(detail!=null){db.update("DELETE FROM job_detail_images WHERE job_id=?",id);if(!detail.isBlank())db.update("INSERT INTO job_detail_images(job_id,image_data) VALUES(?,?)",id,detail);}
        if (body.containsKey("assessmentMinScore")) assessments.configure(id, body.get("assessmentMinScore"));
        return ownJob(user, id, false);
    }

    public void jobAction(long user, long id, String action) {
        var job = ownJob(user, id, !action.equals("close"));
        String status = job.get("status").toString();
        if (action.equals("review-request") && status.equals("DRAFT")) {
            Input.jobType((String) job.get("jobType"), false);
            db.update(
                "UPDATE job_postings SET status='PENDING',review_reason=NULL,updated_at=CURRENT_TIMESTAMP WHERE job_id=?",
                id
            );
            String companyName = company(user, true).get("companyName").toString();
            for (var admin : db.list("SELECT user_id FROM users WHERE role='ADMIN' AND status='ACTIVE'")) {
                notify(number(admin, "userId"), "새 공고 심사 요청", companyName + " 기업의 [" + job.get("title") + "] 공고 심사를 확인해 주세요.", "JOB_REVIEW", id);
            }
        } else if (action.equals("close") && status.equals("PUBLISHED")) {
            db.update(
                "UPDATE job_postings SET status='CLOSED',updated_at=CURRENT_TIMESTAMP WHERE job_id=?",
                id
            );
            com.employment.ads.AdvertisingLifecycle.refresh(db);
        } else throw new Problem(409, "허용되지 않은 상태 변경입니다.");
    }

    public List<Map<String, Object>> applicants(long user, long jobId) {
        ownJob(user, jobId, true);
        var rows=db.list("SELECT a.application_id,a.status,a.applied_at,s.user_id AS applicant_user_id,u.name,x.snapshot_json FROM applications a JOIN job_seekers s ON s.job_seeker_id=a.job_seeker_id JOIN users u ON u.user_id=s.user_id LEFT JOIN application_snapshots x ON x.application_id=a.application_id WHERE a.job_id=? ORDER BY a.application_id DESC",jobId);
        decorateSubmittedApplicantScores(rows);
        return rows;
    }

    public Map<String, Object> companyApplication(long user, long id) {
        long company = number(company(user, true), "companyId");
        var app = db.one(
            "SELECT a.*,s.user_id,j.title FROM applications a JOIN job_postings j ON j.job_id=a.job_id JOIN job_seekers s ON s.job_seeker_id=a.job_seeker_id WHERE a.application_id=? AND j.company_id=? FOR UPDATE",
            id,
            company
        );
        if(app==null)throw new Problem(404,"지원 내역을 찾을 수 없습니다.");
        app.put("assessment", assessments.companyResult(id, true));
        addDetails(app, true);
        return app;
    }

    public void applicationStatus(long user, long id, String next) {
        var app = companyApplication(user, id);
        Map<String, Set<String>> transitions = Map.of(
            "APPLIED",
            Set.of("UNDER_REVIEW", "REJECTED"),
            "UNDER_REVIEW",
            Set.of("DOCUMENT_PASSED", "REJECTED"),
            "DOCUMENT_PASSED",
            Set.of("INTERVIEW", "REJECTED"),
            "INTERVIEW",
            Set.of("FINAL_PASSED", "REJECTED")
        );
        if (
            !transitions
                .getOrDefault(app.get("status"), Set.of())
                .contains(next)
        ) throw new Problem(409, "허용되지 않은 지원 상태 변경입니다.");
        db.update(
            "UPDATE applications SET status=?,updated_at=CURRENT_TIMESTAMP WHERE application_id=?",
            next,
            id
        );
        if (Set.of("FINAL_PASSED", "REJECTED").contains(next)) db.update(
            "UPDATE interviews SET status='CANCELED' WHERE application_id=? AND status='SCHEDULED'",
            id
        );
        notify(
            number(app, "userId"),
            "지원 상태가 변경되었습니다.",
            "지원 내역에서 진행 상황을 확인해 주세요.",
            "APPLICATION",
            id
        );
    }

    public Map<String, Object> interview(
        long user,
        long appId,
        String key,
        Map<String, Object> body
    ) {
        var app = companyApplication(user, appId);
        if (
            key == null || key.isBlank() || key.length() > 100
        ) throw new Problem(400, "요청 키가 필요합니다.");
        String hash;
        try {
            hash = com.employment.auth.AuthService.hash(
                json.writeValueAsString(new TreeMap<>(body))
            );
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex);
        }
        var existing = db.list(
            "SELECT * FROM interviews WHERE application_id=? AND request_key=?",
            appId,
            key
        );
        if (!existing.isEmpty()) {
            if (
                !hash.equals(existing.get(0).get("requestHash"))
            ) throw new Problem(
                409,
                "같은 요청 키에 다른 내용이 사용되었습니다."
            );
            return fields("interviewId", existing.get(0).get("interviewId"));
        }
        if (
            !Set.of("DOCUMENT_PASSED", "INTERVIEW").contains(app.get("status"))
        ) throw new Problem(
            409,
            "서류 합격 또는 면접 단계에서만 등록할 수 있습니다."
        );
        Instant at = Input.instant(body, "interviewAt");
        if (!at.isAfter(Instant.now())) throw new Problem(
            400,
            "미래 면접 일시를 입력해 주세요."
        );
        String type = Input.text(body, "interviewType", 20, true);
        if (!Set.of("ONLINE", "OFFLINE").contains(type)) throw new Problem(
            400,
            "면접 방식을 확인해 주세요."
        );
        String location = Input.text(
            body,
            "location",
            255,
            type.equals("OFFLINE")
        );
        String url = Input.url(body, "meetingUrl");
        if (type.equals("ONLINE") && url.isBlank()) throw new Problem(
            400,
            "온라인 면접 주소가 필요합니다."
        );
        // companyApplication holds the application lock, serializing concurrent registrations.
        var duplicate = db.list(
            "SELECT interview_id FROM interviews WHERE application_id=? AND request_hash=? AND status='SCHEDULED'",
            appId,
            hash
        );
        if (!duplicate.isEmpty()) return fields("interviewId", duplicate.get(0).get("interviewId"));
        long id = db.insert(
            "interviews",
            fields(
                "application_id",
                appId,
                "interview_at",
                Timestamp.from(at),
                "interview_type",
                type,
                "location",
                location,
                "meeting_url",
                url,
                "applicant_guidance",
                Input.text(body, "applicantGuidance", 2000, false),
                "memo",
                Input.text(body, "memo", 10000, false),
                "request_key",
                key,
                "request_hash",
                hash
            )
        );
        db.update(
            "UPDATE applications SET status='INTERVIEW',updated_at=CURRENT_TIMESTAMP WHERE application_id=?",
            appId
        );
        notify(
            number(app, "userId"),
            "면접 일정이 등록되었습니다.",
            app.get("title") +
                " · " +
                at
                    .atZone(ZoneId.of("Asia/Seoul"))
                    .format(
                        java.time.format.DateTimeFormatter.ofPattern(
                            "yyyy.MM.dd HH:mm"
                        )
                    ) +
                " (한국 시간) · " +
                (type.equals("ONLINE") ? "온라인 면접" : location) +
                " — 지원 상세에서 안내를 확인해 주세요.",
            "INTERVIEW",
            appId
        );
        return fields("interviewId", id);
    }

    public void deleteInterview(long user, long id) {
        deleteInterview(user, id, null);
    }

    public void deleteInterview(long user, long id, String cancelReason) {
        var existing = db.one("SELECT application_id FROM interviews WHERE interview_id=?", id);
        var app = companyApplication(user, number(existing, "applicationId"));
        db.one("SELECT interview_id FROM interviews WHERE interview_id=? FOR UPDATE", id);
        if(!db.list("SELECT review_id FROM interview_reviews WHERE interview_id=?",id).isEmpty()||!db.list("SELECT request_id FROM interview_attendance_requests WHERE interview_id=?",id).isEmpty())throw new Problem(409,"후기 또는 참석 확인 기록이 있는 면접 일정은 삭제할 수 없습니다.");
        db.update("DELETE FROM interviews WHERE interview_id=?", id);

        // 💡 취소 사유가 존재하면 알림 본문에 추가
        String messageContent = app.get("title") + " · 기업에서 면접 일정을 삭제했습니다.";
        if (cancelReason != null && !cancelReason.trim().isEmpty()) {
            messageContent += "\n취소 사유: " + cancelReason.trim();
        } else {
            messageContent += " 지원 상세에서 확인해 주세요.";
        }

        notify(
            number(app, "userId"),
            "면접 일정이 삭제되었습니다.",
            messageContent,
            "INTERVIEW",
            number(app, "applicationId")
        );
    }

    public void cancelInterview(
        long user,
        long id,
        Map<String, Object> body
    ) {
        String reason = Input.text(
            body,
            "reason",
            1000,
            true
        ).strip();

        if (reason.isBlank()) {
            throw new Problem(400, "면접 취소 사유를 입력해 주세요.");
        }

        var existing = db.one(
            "SELECT application_id FROM interviews WHERE interview_id=?",
            id
        );

        var app = companyApplication(
            user,
            number(existing, "applicationId")
        );

        var interview = db.one(
            "SELECT * FROM interviews WHERE interview_id=? FOR UPDATE",
            id
        );

        if (!"SCHEDULED".equals(interview.get("status"))) {
            throw new Problem(409, "예정된 면접만 취소할 수 있습니다.");
        }

        db.update(
            """
            UPDATE interviews
            SET status='CANCELED',
                cancellation_reason=?
            WHERE interview_id=?
            """,
            reason,
            id
        );

        notify(
            number(app, "userId"),
            "면접 일정이 취소되었습니다.",
            app.get("title") + " · 면접 취소 사유: " + reason,
            "INTERVIEW",
            number(app, "applicationId")
        );
    }

    public void updateInterview(
        long user,
        long id,
        Map<String, Object> body,
        boolean statusOnly
    ) {
        var existing = db.one(
            "SELECT application_id FROM interviews WHERE interview_id=?",
            id
        );
        var app = companyApplication(user, number(existing, "applicationId"));
        var interview = db.one(
            "SELECT * FROM interviews WHERE interview_id=? FOR UPDATE",
            id
        );
        if (!"SCHEDULED".equals(interview.get("status"))) throw new Problem(
            409,
            "예정된 면접만 변경할 수 있습니다."
        );
        if (statusOnly) {
            String next = Input.text(body, "status", 30, true);
            if (
                !Set.of("COMPLETED", "CANCELED").contains(next)
            ) throw new Problem(400, "면접 상태를 확인해 주세요.");
            if(next.equals("COMPLETED")&&Instant.now().isBefore(Instant.parse(interview.get("interviewAt").toString())))throw new Problem(409,"면접일이 지난 후 완료 확인할 수 있습니다.");
            db.update(
                "UPDATE interviews SET status=?,result=?,memo=? WHERE interview_id=?",
                next,
                Input.text(body, "result", 10000, false),
                Input.text(body, "memo", 10000, false),
                id
            );
        } else {
            if (!"INTERVIEW".equals(app.get("status"))) throw new Problem(
                409,
                "종료된 지원의 면접은 변경할 수 없습니다."
            );
            Instant at = Input.instant(body, "interviewAt");
            if (!at.isAfter(Instant.now())) throw new Problem(
                400,
                "미래 면접 일시를 입력해 주세요."
            );
            String type = Input.text(body, "interviewType", 20, true);
            if (!Set.of("ONLINE", "OFFLINE").contains(type)) throw new Problem(
                400,
                "면접 방식을 확인해 주세요."
            );
            String location = Input.text(
                body,
                "location",
                255,
                type.equals("OFFLINE")
            );
            String url = Input.url(body, "meetingUrl");
            if (type.equals("ONLINE") && url.isBlank()) throw new Problem(
                400,
                "온라인 면접 주소가 필요합니다."
            );
            String guidance = body.containsKey("applicantGuidance")
                ? Input.text(body, "applicantGuidance", 2000, false)
                : Objects.toString(interview.get("applicantGuidance"), "");
            boolean scheduleChanged = !at.equals(Instant.parse(interview.get("interviewAt").toString()))
                || !type.equals(interview.get("interviewType"))
                || !location.equals(Objects.toString(interview.get("location"), ""))
                || !url.equals(Objects.toString(interview.get("meetingUrl"), ""))
                || !guidance.equals(Objects.toString(interview.get("applicantGuidance"), ""));
            db.update(
                "UPDATE interviews SET interview_at=?,interview_type=?,location=?,meeting_url=?,memo=?,applicant_guidance=? WHERE interview_id=?",
                Timestamp.from(at),
                type,
                location,
                url,
                Input.text(body, "memo", 10000, false),
                guidance,
                id
            );
            if (!scheduleChanged) return;
        }
        notify(
            number(app, "userId"),
            "COMPLETED".equals(body.get("status")) ? "면접 완료가 확인되었습니다." : "면접 일정이 변경되었습니다.",
            "COMPLETED".equals(body.get("status")) ? "나의 면접 후기에서 경험을 남겨 주세요." : "지원 상세에서 안내를 확인해 주세요.",
            "COMPLETED".equals(body.get("status")) ? "INTERVIEW_REVIEW" : "INTERVIEW",
            number(app, "applicationId")
        );
    }

    public List<Map<String, Object>> links(long user) {
        var rows = db.list(
            "SELECT employment_link_id,status,public_message,requested_at,completed_at,company_id,job_id FROM employment_links WHERE job_seeker_id=? ORDER BY employment_link_id DESC",
            seeker(user)
        );
        rows.forEach(referrals::decorateUser);
        rows.forEach(this::decorateEmploymentPreferences);
        return rows;
    }

    public List<Map<String, Object>> companyEmploymentLinks(long user) {
        return referrals.companyList(user);
    }
    public Map<String, Object> companyEmploymentDetail(long user, long id) {
        var rows = db.list("SELECT r.referral_id FROM employment_referrals r JOIN companies c ON c.company_id=r.company_id WHERE r.employment_link_id=? AND c.user_id=? ORDER BY r.referral_id DESC LIMIT 1", id,user);
        if (rows.isEmpty()) throw new Problem(404,"전달된 취업연계 추천이 없습니다.");
        return referrals.companyDetail(user,number(rows.get(0),"referralId"));
    }
    public void companyEmploymentReview(long user, long id, Map<String,Object> body) {
        // Old clients cannot safely respond without identifying the recommendation round.
        throw new Problem(409,"취업연계 화면을 새로고침한 뒤 해당 추천에서 응답해 주세요.");
    }

    public Map<String, Object> linkDetail(long user, long id) {
        var result = db.one(
            "SELECT e.employment_link_id,e.status,e.public_message,e.requested_at,e.completed_at,u.name AS applicant_name,m.name AS manager_name,c.company_name,j.title AS job_title," +
            "CASE WHEN c.approval_status='APPROVED' AND cu.status='ACTIVE' THEN c.company_id ELSE NULL END AS company_id," +
            "CASE WHEN c.approval_status='APPROVED' AND cu.status='ACTIVE' AND j.status='PUBLISHED' THEN j.job_id ELSE NULL END AS job_id " +
            "FROM employment_links e JOIN job_seekers s ON s.job_seeker_id=e.job_seeker_id JOIN users u ON u.user_id=s.user_id " +
            "LEFT JOIN users m ON m.user_id=e.manager_user_id LEFT JOIN companies c ON c.company_id=e.company_id " +
            "LEFT JOIN users cu ON cu.user_id=c.user_id LEFT JOIN job_postings j ON j.job_id=e.job_id " +
            "WHERE e.employment_link_id=? AND s.user_id=?",
            id, user
        );
        if (result == null) throw new Problem(404, "취업연계 내역을 찾을 수 없습니다.");
        referrals.decorateUser(result);
        decorateEmploymentPreferences(result);
        return result;
    }

    public Map<String, Object> employmentEligibility(long user) {
        long seeker = seeker(user);
        boolean hasPublicResume = !db.list("SELECT resume_id FROM resumes WHERE job_seeker_id=? AND is_public=TRUE LIMIT 1", seeker).isEmpty();
        return fields("hasPublicResume", hasPublicResume);
    }

    public void decorateEmploymentPreferences(Map<String,Object> row) {
        var outcome = referrals.latest(number(row,"employmentLinkId"));
        if (outcome != null) { row.put("referralStatus",outcome.get("reviewStatus")); row.put("joinedAt",outcome.get("joinedAt")); row.put("hiredAt",outcome.get("hiredAt")); }
        try {
            var preferences = db.list("SELECT preferred_industry,preferred_occupation FROM employment_preferences WHERE employment_link_id=?",row.get("employmentLinkId"));
            if (!preferences.isEmpty()) row.putAll(preferences.get(0));
        } catch (org.springframework.jdbc.BadSqlGrammarException ex) {
            if (!missingPreferencesTable(ex)) throw ex;
        }
    }
    private boolean missingPreferencesTable(org.springframework.jdbc.BadSqlGrammarException ex) {
        var cause = ex.getSQLException();
        return cause != null && ("42S02".equals(cause.getSQLState()) || "42P01".equals(cause.getSQLState()) || cause.getErrorCode()==42102)
            && Objects.toString(cause.getMessage(),"").toLowerCase(Locale.ROOT).contains("employment_preferences");
    }

    public Map<String, Object> requestLink(long user, Map<String,Object> body) {
        String industry = Input.text(body,"preferredIndustry",100,true).strip();
        String occupation = Input.text(body,"preferredOccupation",100,true).strip();
        long seeker = seeker(user);
        if (db.list("SELECT resume_id FROM resumes WHERE job_seeker_id=? AND is_public=TRUE LIMIT 1", seeker).isEmpty())
            throw new Problem(409, "공개된 이력서가 있어야 취업연계를 신청할 수 있습니다. 이력서 관리에서 이력서를 공개로 설정한 뒤 다시 신청해 주세요.");
        if (
            !db
                .list(
                    "SELECT employment_link_id FROM employment_links WHERE job_seeker_id=? AND status NOT IN ('EMPLOYED','CLOSED')",
                    seeker
                )
                .isEmpty()
        ) throw new Problem(409, "진행 중인 취업연계 신청이 있습니다.");
        long id = db.insert(
            "employment_links",
            fields("job_seeker_id", seeker)
        );
        try { db.update("INSERT INTO employment_preferences (employment_link_id,preferred_industry,preferred_occupation) VALUES (?,?,?)",id,industry,occupation); }
        catch (org.springframework.jdbc.BadSqlGrammarException ex) {
            if (missingPreferencesTable(ex)) throw new Problem(503,"취업연계 희망 조건 저장 기능의 DB 설치가 필요합니다. 관리자에게 문의해 주세요. (employment-preferences-upgrade.sql)");
            throw ex;
        }
        return fields("employmentLinkId", id, "status", "REQUESTED");
    }

    public void notify(
        long user,
        String title,
        String content,
        String type,
        long related
    ) {
        db.insert(
            "notifications",
            fields(
                "user_id",
                user,
                "title",
                title,
                "content",
                content,
                "notification_type",
                type,
                "related_id",
                related
            )
        );
    }

    public Map<String,Object> seekerActivity(long user) {
        long owner = seeker(user);
        var result = db.one("SELECT COUNT(*) AS application_count,COUNT(CASE WHEN status IN ('APPLIED','UNDER_REVIEW','DOCUMENT_PASSED','INTERVIEW') THEN 1 END) AS active_applications FROM applications WHERE job_seeker_id=?", owner);
        result.putAll(db.one("SELECT COUNT(*) AS resume_count,COUNT(a.resume_id) AS assessed_resume_count FROM resumes r LEFT JOIN resume_assessments a ON a.resume_id=r.resume_id WHERE r.job_seeker_id=?", owner));
        result.put("upcomingInterviews", db.list("SELECT i.interview_id,i.application_id,i.interview_at,i.interview_type,c.company_name FROM interviews i JOIN applications a ON a.application_id=i.application_id JOIN job_postings j ON j.job_id=a.job_id JOIN companies c ON c.company_id=j.company_id WHERE a.job_seeker_id=? AND i.status='SCHEDULED' AND i.interview_at>=? AND a.status IN ('APPLIED','UNDER_REVIEW','DOCUMENT_PASSED','INTERVIEW') ORDER BY i.interview_at,i.interview_id LIMIT 2", owner, Timestamp.from(Instant.now())));
        return result;
    }

    public Map<String,Object> companyOverview(long user) {
        var profile = companyProfile(user);
        var result = fields("profile",profile,"jobs",0,"applications",0,"publishedJobs",0,"pendingJobs",0,"closingJobs",0,"newApplicants",0,"unreviewedApplicants",0,"interviewCount",0,"profileCompletion",0,"activeJobs",List.of(),"upcomingInterviews",List.of());
        result.put("employmentRequests", List.of());
        if (Boolean.TRUE.equals(profile.get("profileMissing"))) return result;
        long owner = number(profile,"companyId");
        result.put("employmentRequests", referrals.companyRequestActivity(user, owner));
        result.putAll(db.one("SELECT COUNT(*) AS jobs,COUNT(CASE WHEN status='PUBLISHED' AND end_date>=? THEN 1 END) AS published_jobs,COUNT(CASE WHEN status='PENDING' THEN 1 END) AS pending_jobs,COUNT(CASE WHEN status='PUBLISHED' AND end_date>=? AND end_date<=? THEN 1 END) AS closing_jobs FROM job_postings WHERE company_id=?",today(),today(),today().plusDays(7),owner));
        result.putAll(db.one("SELECT COUNT(*) AS applications,COUNT(CASE WHEN a.applied_at>=? AND a.status<>'CANCELED' THEN 1 END) AS new_applicants,COUNT(CASE WHEN a.status='APPLIED' THEN 1 END) AS unreviewed_applicants FROM applications a JOIN job_postings j ON j.job_id=a.job_id WHERE j.company_id=?",Timestamp.from(Instant.now().minus(Duration.ofDays(7))),owner));
        var interviews = companyInterviews(user);
        result.put("interviewCount",interviews.size());
        result.put("upcomingInterviews",interviews.stream().limit(2).toList());
        result.put("activeJobs",db.list("SELECT j.job_id,j.title,j.status,j.job_type,j.work_location,j.end_date,j.banner_image_url,(SELECT COUNT(*) FROM applications a WHERE a.job_id=j.job_id) AS application_count FROM job_postings j WHERE j.company_id=? AND j.status='PUBLISHED' AND j.end_date>=? ORDER BY j.end_date,j.job_id DESC LIMIT 3",owner,today()));
        var profileFields = List.of("companyName","businessNumber","industry","companySize","address","description","website","ceoName");
        long completed=profileFields.stream().filter(key->profile.get(key)!=null&&!profile.get(key).toString().isBlank()).count();
        if (number(db.one("SELECT COUNT(*) AS n FROM company_images WHERE company_id=?",owner),"n")>0) completed++;
        result.put("profileCompletion",Math.round(completed*100.0/(profileFields.size()+1)));
        return result;
    }

    public List<Map<String,Object>> companyApplicants(long user,String view) {
        if (!Set.of("","new","unreviewed").contains(view)) throw new Problem(400,"지원자 조회 조건을 확인해 주세요.");
        var profile=companyProfile(user);
        if (Boolean.TRUE.equals(profile.get("profileMissing"))) return List.of();
        String sql="SELECT a.application_id,a.job_id,a.status,a.applied_at,s.user_id AS applicant_user_id,u.name,j.title,x.snapshot_json FROM applications a JOIN job_postings j ON j.job_id=a.job_id JOIN job_seekers s ON s.job_seeker_id=a.job_seeker_id JOIN users u ON u.user_id=s.user_id LEFT JOIN application_snapshots x ON x.application_id=a.application_id WHERE j.company_id=?";
        var args=new ArrayList<Object>();args.add(profile.get("companyId"));
        if (view.equals("new")) { sql+=" AND a.applied_at>=? AND a.status<>'CANCELED'";args.add(Timestamp.from(Instant.now().minus(Duration.ofDays(7)))); }
        if (view.equals("unreviewed")) sql+=" AND a.status='APPLIED'";
        var rows=db.list(sql+" ORDER BY a.applied_at DESC,a.application_id DESC",args.toArray());
        decorateSubmittedApplicantScores(rows);
        return rows;
    }

    private void decorateSubmittedApplicantScores(List<Map<String,Object>> rows) {
        for(var row:rows) {
            Object stored=row.remove("snapshotJson");
            try {
                var assessment=stored==null?json.nullNode():json.readTree(stored.toString()).path("careerAssessment");
                var score=assessment.path("report").path("aptitudeScore");
                row.put("score",score.isNumber()?score.numberValue():null);
                row.put("assessmentCompletedAt",assessment.path("completedAt").isTextual()?assessment.path("completedAt").asText():null);
                row.put("assessmentAttemptId",assessment.path("attemptId").isNumber()?assessment.path("attemptId").numberValue():null);
            } catch(java.io.IOException ex) { throw new IllegalStateException(ex); }
        }
    }

    public List<Map<String,Object>> companyInterviews(long user) {
        var profile=companyProfile(user);
        if (Boolean.TRUE.equals(profile.get("profileMissing"))) return List.of();
        return db.list("SELECT i.interview_id,i.application_id,i.interview_at,i.interview_type,u.name,j.title FROM interviews i JOIN applications a ON a.application_id=i.application_id JOIN job_postings j ON j.job_id=a.job_id JOIN job_seekers s ON s.job_seeker_id=a.job_seeker_id JOIN users u ON u.user_id=s.user_id WHERE j.company_id=? AND i.status='SCHEDULED' AND i.interview_at>=? AND a.status IN ('APPLIED','UNDER_REVIEW','DOCUMENT_PASSED','INTERVIEW') ORDER BY i.interview_at,i.interview_id",profile.get("companyId"),Timestamp.from(Instant.now()));
    }


    public static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Seoul"));
    }


}

