package com.employment.admin;

import static com.employment.global.common.Database.*;

import com.employment.auth.AuthService;
import com.employment.global.common.*;
import com.employment.job.service.EmploymentService;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Transactional
public class OperationsController {

    private final Database db;
    private final AuthService auth;
    private final EmploymentService service;
    private final AdminDashboardService dashboardService;
    private final NoticeImages noticeImages;

    public OperationsController(
        Database db,
        AuthService auth,
        EmploymentService service,
        AdminDashboardService dashboardService,
        NoticeImages noticeImages
    ) {
        this.db = db;
        this.auth = auth;
        this.service = service;
        this.dashboardService = dashboardService;
        this.noticeImages = noticeImages;
    }

    private long member(HttpServletRequest req) {
        return number(auth.user(req), "userId");
    }

    private long admin(HttpServletRequest req) {
        return auth.require(req, "ADMIN");
    }

    @GetMapping("/notices")
    Object notices() {
        return db.list(
            "SELECT notice_id,title,content,is_pinned,created_at,EXISTS(SELECT 1 FROM notice_images ni WHERE ni.notice_id=notices.notice_id) AS has_image FROM notices WHERE is_published=TRUE ORDER BY is_pinned DESC,notice_id DESC"
        );
    }

    @GetMapping("/notices/{id}")
    Object notice(@PathVariable long id) {
        return db.one(
            "SELECT notice_id,title,content,is_pinned,created_at,EXISTS(SELECT 1 FROM notice_images ni WHERE ni.notice_id=notices.notice_id) AS has_image FROM notices WHERE notice_id=? AND is_published=TRUE",
            id
        );
    }

    @GetMapping("/notifications")
    Object notifications(HttpServletRequest req) {
        return db.list(
            "SELECT notification_id,title,content,notification_type,related_id,is_read,created_at FROM notifications WHERE user_id=? ORDER BY notification_id DESC",
            member(req)
        );
    }

    @PatchMapping("/notifications/{id}/read")
    void read(@PathVariable long id, HttpServletRequest req) {
        if (
            db.update(
                "UPDATE notifications SET is_read=TRUE WHERE notification_id=? AND user_id=?",
                id,
                member(req)
            ) ==
            0
        ) throw new Problem(404, "알림을 찾을 수 없습니다.");
    }

    @GetMapping("/faqs")
    Object faqs() {
        return db.list("SELECT faq_id,category,audience,question,answer FROM customer_faqs WHERE is_published=TRUE ORDER BY sort_order,faq_id");
    }

    @GetMapping("/inquiries")
    Object inquiries(HttpServletRequest req) {
        return db.list(
            "SELECT * FROM inquiries WHERE user_id=? ORDER BY inquiry_id DESC",
            member(req)
        );
    }

    @GetMapping("/inquiries/{id}")
    Object inquiry(@PathVariable long id, HttpServletRequest req) {
        return db.one(
            "SELECT * FROM inquiries WHERE inquiry_id=? AND user_id=?",
            id,
            member(req)
        );
    }

    @PostMapping("/inquiries")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    Object ask(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        long id = db.insert(
            "inquiries",
            fields(
                "user_id",
                member(req),
                "title",
                Input.text(body, "title", 200, true),
                "content",
                Input.text(body, "content", 10000, true)
            )
        );
        return fields("inquiryId", id);
    }

    @DeleteMapping("/inquiries/{id}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void deleteInquiry(@PathVariable long id, HttpServletRequest req) {
        long user = member(req);
        var inquiry = db.one("SELECT status,answer FROM inquiries WHERE inquiry_id=? AND user_id=? FOR UPDATE", id, user);
        if ("ANSWERED".equals(inquiry.get("status")) || inquiry.get("answer") != null)
            throw new Problem(409, "답변을 받은 문의는 삭제할 수 없습니다.");
        db.update("DELETE FROM inquiries WHERE inquiry_id=? AND user_id=?", id, user);
    }

    @PatchMapping("/me")
    Object updateMe(
        @RequestBody Map<String, Object> body,
        HttpServletRequest req
    ) {
        long id = member(req);
        String phone = Input.text(body, "phone", 30, true).replaceAll(
            "[^0-9]",
            ""
        );
        if (!phone.matches("[0-9]{10,11}")) throw new Problem(
            400,
            "휴대폰번호를 확인해 주세요."
        );
        auth.requireAvailablePhone(phone, id);
        db.update(
            "UPDATE users SET name=?,phone=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=?",
            Input.text(body, "name", 100, true),
            phone,
            id
        );
        return auth.publicUser(id);
    }

    @GetMapping("/job-seekers/me")
    Object seeker(HttpServletRequest req) {
        return db.one(
            "SELECT * FROM job_seekers WHERE user_id=?",
            auth.require(req, "JOB_SEEKER")
        );
    }

    @PatchMapping("/job-seekers/me")
    Object seekerUpdate(
        @RequestBody Map<String, Object> body,
        HttpServletRequest req
    ) {
        long id = auth.require(req, "JOB_SEEKER");
        String birth = Input.text(body, "birthDate", 10, false);
        java.time.LocalDate date = birth.isBlank() ? null : Input.birthDate(birth);
        // Preserve the existing omission default (0); explicit null is not a valid year count.
        if (body.containsKey("experienceYears") && body.get("experienceYears") == null)
            throw new Problem(400, "경력 연수를 확인해 주세요.", "experienceYears");
        int years = Integer.parseInt(body.getOrDefault("experienceYears", 0).toString());
        if (years < 0 || years > 80) throw new Problem(
            400,
            "경력 연수를 확인해 주세요."
        );
        db.update(
            "UPDATE job_seekers SET birth_date=?,address=?,desired_job=?,desired_region=?,experience_years=?,introduction=? WHERE user_id=?",
            date,
            Input.text(body, "address", 255, false),
            Input.text(body, "desiredJob", 100, false),
            Input.text(body, "desiredRegion", 100, false),
            years,
            Input.text(body, "introduction", 10000, false),
            id
        );
        db.update("UPDATE users SET birth_date=? WHERE user_id=?", date, id);
        return seeker(req);
    }

    @GetMapping("/company/profile")
    Object profile(HttpServletRequest req) {
        return service.companyProfile(auth.require(req, "COMPANY"));
    }

    @PutMapping("/company/profile")
    Object saveProfile(
        @RequestBody Map<String, Object> body,
        HttpServletRequest req
    ) {
        long user = auth.require(req, "COMPANY");
        db.one("SELECT user_id FROM users WHERE user_id=? FOR UPDATE", user);
        var company = service.companyProfile(user);
        String business = Input.text(
            body,
            "businessNumber",
            20,
            true
        ).replaceAll("[^0-9]", "");
        if (!business.matches("[0-9]{10}")) throw new Problem(
            400,
            "사업자번호를 확인해 주세요."
        );
        if (Boolean.TRUE.equals(company.get("profileMissing"))) {
            db.insert(
                "companies",
                fields(
                    "user_id",
                    user,
                    "company_name",
                    Input.text(body, "companyName", 200, true),
                    "business_number",
                    business,
                    "ceo_name",
                    Input.text(body, "ceoName", 100, false),
                    "industry",
                    Input.text(body, "industry", 100, false),
                    "company_size",
                    Input.text(body, "companySize", 100, false),
                    "address",
                    Input.text(body, "address", 255, false),
                    "website",
                    Input.url(body, "website"),
                    "description",
                    Input.text(body, "description", 10000, false),
                    "approval_status",
                    "PENDING"
                )
            );
            return profile(req);
        }
        String status = business.equals(company.get("businessNumber"))
            ? company.get("approvalStatus").toString()
            : "PENDING";
        db.update(
            "UPDATE companies SET company_name=?,business_number=?,ceo_name=?,industry=?,company_size=?,address=?,website=?,description=?,approval_status=? WHERE user_id=?",
            Input.text(body, "companyName", 200, true),
            business,
            Input.text(body, "ceoName", 100, false),
            Input.text(body, "industry", 100, false),
            Input.text(body, "companySize", 100, false),
            Input.text(body, "address", 255, false),
            Input.url(body, "website"),
            Input.text(body, "description", 10000, false),
            status,
            user
        );
        return profile(req);
    }

    @PostMapping("/company/profile/review-request")
    void requestReview(HttpServletRequest req) {
        long user = auth.require(req, "COMPANY");
        if (
            !"REJECTED".equals(
                service.company(user, false).get("approvalStatus")
            )
        ) throw new Problem(
            409,
            "반려 상태에서만 재심사를 요청할 수 있습니다."
        );
        db.update(
            "UPDATE companies SET approval_status='PENDING',review_reason=NULL WHERE user_id=?",
            user
        );
    }

    @GetMapping("/company/dashboard")
    Object companyDashboard(HttpServletRequest req) { return service.companyOverview(auth.require(req,"COMPANY")); }

    @GetMapping("/company/applicants")
    Object companyApplicants(HttpServletRequest req,@RequestParam(defaultValue="") String view) { return service.companyApplicants(auth.require(req,"COMPANY"),view); }

    @GetMapping("/company/interviews")
    Object companyInterviews(HttpServletRequest req) { return service.companyInterviews(auth.require(req,"COMPANY")); }

    @GetMapping("/admin/dashboard")
    Object dashboard(HttpServletRequest req) {
        admin(req);
        return dashboardService.overview();
    }

    @GetMapping("/admin/employment-link-options")
    Object employmentLinkOptions(HttpServletRequest req) {
        admin(req);
        return fields(
            "managers", db.list("SELECT user_id,name,login_id FROM users WHERE role='ADMIN' AND status='ACTIVE' ORDER BY name,user_id"),
            "companies", db.list("SELECT c.company_id,c.company_name,c.business_number FROM companies c JOIN users u ON u.user_id=c.user_id WHERE c.approval_status='APPROVED' AND u.status='ACTIVE' ORDER BY c.company_name,c.company_id"),
            "jobs", db.list("SELECT j.job_id,j.company_id,j.title,j.status FROM job_postings j JOIN companies c ON c.company_id=j.company_id JOIN users u ON u.user_id=c.user_id WHERE c.approval_status='APPROVED' AND u.status='ACTIVE' ORDER BY j.title,j.job_id")
        );
    }

    private static final Map<String, String> ADMIN_TABLES = Map.of(
        "users",
        "users",
        "companies",
        "companies",
        "jobs",
        "job_postings",
        "employment-links",
        "employment_links",
        "notices",
        "notices",
        "inquiries",
        "inquiries"
    );
    private static final Map<String, String> IDS = Map.of(
        "users",
        "user_id",
        "companies",
        "company_id",
        "jobs",
        "job_id",
        "employment-links",
        "employment_link_id",
        "notices",
        "notice_id",
        "inquiries",
        "inquiry_id"
    );

    @GetMapping("/admin/{resource}")
    Object adminList(@PathVariable String resource, HttpServletRequest req) {
        admin(req);
        String table = ADMIN_TABLES.get(resource);
        if (table == null) throw new Problem(404, "정보를 찾을 수 없습니다.");
        String columns = resource.equals("users")
            ? "user_id,email,name,phone,role,status,created_at"
            : resource.equals("inquiries") ? "*,(SELECT u.name FROM users u WHERE u.user_id=inquiries.user_id) AS author_name"
            : resource.equals("employment-links") ? "*,(SELECT u.name FROM job_seekers s JOIN users u ON u.user_id=s.user_id WHERE s.job_seeker_id=employment_links.job_seeker_id) AS job_seeker_name"
            : resource.equals("notices") ? "*,EXISTS(SELECT 1 FROM notice_images ni WHERE ni.notice_id=notices.notice_id) AS has_image" : "*";
        return db.list(
            "SELECT " +
                columns +
                " FROM " +
                table +
                " ORDER BY " +
                IDS.get(resource) +
                " DESC"
        );
    }

    @GetMapping("/admin/{resource}/{id}")
    Object adminDetail(
        @PathVariable String resource,
        @PathVariable long id,
        HttpServletRequest req
    ) {
        admin(req);
        String table = ADMIN_TABLES.get(resource);
        if (table == null) throw new Problem(404, "정보를 찾을 수 없습니다.");
        String columns = resource.equals("users")
            ? "user_id,email,name,phone,role,status,created_at"
            : resource.equals("inquiries") ? "*,(SELECT u.name FROM users u WHERE u.user_id=inquiries.user_id) AS author_name"
            : resource.equals("employment-links") ? "*,(SELECT u.name FROM job_seekers s JOIN users u ON u.user_id=s.user_id WHERE s.job_seeker_id=employment_links.job_seeker_id) AS job_seeker_name"
            : resource.equals("notices") ? "*,EXISTS(SELECT 1 FROM notice_images ni WHERE ni.notice_id=notices.notice_id) AS has_image" : "*";
        return db.one(
            "SELECT " +
                columns +
                " FROM " +
                table +
                " WHERE " +
                IDS.get(resource) +
                "=?",
            id
        );
    }

    @PatchMapping("/admin/companies/{id}/approval")
    void approval(
        @PathVariable long id,
        @RequestBody Map<String, Object> body,
        HttpServletRequest req
    ) {
        long admin = admin(req);
        var company = db.one(
            "SELECT * FROM companies WHERE company_id=? FOR UPDATE",
            id
        );
        String status = Input.text(body, "status", 20, true);
        if (!Set.of("APPROVED", "REJECTED").contains(status)) throw new Problem(
            400,
            "승인 상태를 확인해 주세요."
        );
        String reason = Input.text(
            body,
            "reason",
            10000,
            status.equals("REJECTED")
        );
        db.update(
            "UPDATE companies SET approval_status=?,review_reason=?,reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP WHERE company_id=?",
            status,
            reason,
            admin,
            id
        );
        service.notify(
            number(company, "userId"),
            status.equals("APPROVED") ? "기업 승인이 완료되었습니다." : "기업 심사가 반려되었습니다.",
            status.equals("APPROVED") ? "채용공고를 작성하고 심사를 요청할 수 있습니다. 기업 정보에서 승인 결과를 확인해 주세요." : "기업 정보에서 반려 사유를 확인한 뒤 수정하여 재심사를 요청해 주세요.",
            "COMPANY",
            id
        );
    }

    @PatchMapping("/admin/jobs/{id}/status")
    void reviewJob(
        @PathVariable long id,
        @RequestBody Map<String, Object> body,
        HttpServletRequest req
    ) {
        long admin = admin(req);
        var job = db.one(
            "SELECT * FROM job_postings WHERE job_id=? FOR UPDATE",
            id
        );
        String next = Input.text(body, "status", 20, true);
        String current = job.get("status").toString();
        if (next.equals("PUBLISHED")) Input.jobType((String) job.get("jobType"), false);
        if (
            !(current.equals("PENDING") &&
                Set.of("PUBLISHED", "REJECTED").contains(next)) &&
            !(current.equals("PUBLISHED") && next.equals("CLOSED"))
        ) throw new Problem(409, "허용되지 않은 상태 변경입니다.");
        var company = db.one(
            "SELECT c.*,u.status AS user_status FROM companies c JOIN users u ON u.user_id=c.user_id WHERE company_id=?",
            job.get("companyId")
        );
        if (
            next.equals("PUBLISHED") &&
            (!"APPROVED".equals(company.get("approvalStatus")) ||
                !"ACTIVE".equals(company.get("userStatus")) ||
                java.time.LocalDate.parse(
                    job.get("endDate").toString()
                ).isBefore(EmploymentService.today()))
        ) throw new Problem(409, "기업 승인과 접수 기간을 확인해 주세요.");
        String reason = Input.text(
            body,
            "reason",
            10000,
            next.equals("REJECTED")
        );
        db.update(
            "UPDATE job_postings SET status=?,review_reason=?,reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP WHERE job_id=?",
            next,
            reason,
            admin,
            id
        );
        service.notify(
            number(company, "userId"),
            "공고 심사 결과가 등록되었습니다.",
            "공고 관리에서 확인해 주세요.",
            "JOB",
            id
        );
    }

    @PatchMapping("/admin/users/{id}/status")
    void userStatus(
        @PathVariable long id,
        @RequestBody Map<String, Object> body,
        HttpServletRequest req
    ) {
        long actor = admin(req);
        if (actor == id) throw new Problem(
            409,
            "본인 계정은 정지할 수 없습니다."
        );
        var target = db.one(
            "SELECT role,status FROM users WHERE user_id=? FOR UPDATE",
            id
        );
        if ("ADMIN".equals(target.get("role"))) throw new Problem(
            403,
            "관리자 계정 상태는 변경할 수 없습니다."
        );
        if ("WITHDRAWN".equals(target.get("status"))) throw new Problem(409, "탈퇴한 회원의 상태는 변경할 수 없습니다.");
        String next = Input.text(body, "status", 20, true);
        if (!Set.of("ACTIVE", "SUSPENDED").contains(next)) throw new Problem(
            400,
            "회원 상태를 확인해 주세요."
        );
        db.update("UPDATE users SET status=? WHERE user_id=?", next, id);
        db.update("UPDATE auth_sessions SET revoked=TRUE WHERE user_id=?", id);
    }

    @DeleteMapping("/admin/notices/{id}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void deleteNotice(@PathVariable long id, HttpServletRequest req) {
        admin(req);
        if (db.update("DELETE FROM notices WHERE notice_id=?", id) == 0) {
            throw new Problem(404, "공지를 찾을 수 없습니다.");
        }
    }

    @DeleteMapping("/admin/jobs/{id}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void deleteJob(@PathVariable long id, HttpServletRequest req) {
        admin(req);
        service.removeJob(id);
    }

    @PostMapping("/admin/notices")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    Object createNotice(
        @RequestBody Map<String, Object> body,
        HttpServletRequest req
    ) {
        long id = db.insert(
            "notices",
            fields(
                "title",
                Input.text(body, "title", 200, true),
                "content",
                Input.text(body, "content", 10000, true),
                "author_user_id",
                admin(req),
                "is_pinned",
                Boolean.TRUE.equals(body.get("isPinned")),
                "is_published",
                Boolean.TRUE.equals(body.get("isPublished"))
            )
        );
        if (body.containsKey("image")) noticeImages.save(id, body.get("image"));
        return fields("noticeId", id);
    }

    @PutMapping("/admin/notices/{id}")
    void updateNotice(
        @PathVariable long id,
        @RequestBody Map<String, Object> body,
        HttpServletRequest req
    ) {
        adminDetail("notices", id, req);
        db.update(
            "UPDATE notices SET title=?,content=?,is_pinned=?,is_published=? WHERE notice_id=?",
            Input.text(body, "title", 200, true),
            Input.text(body, "content", 10000, true),
            Boolean.TRUE.equals(body.get("isPinned")),
            Boolean.TRUE.equals(body.get("isPublished")),
            id
        );
        if (body.containsKey("image")) noticeImages.save(id, body.get("image"));
    }

    @PutMapping("/admin/inquiries/{id}/answer")
    void answer(
        @PathVariable long id,
        @RequestBody Map<String, Object> body,
        HttpServletRequest req
    ) {
        long admin = admin(req);
        var inquiry = db.one(
            "SELECT * FROM inquiries WHERE inquiry_id=? FOR UPDATE",
            id
        );
        db.update(
            "UPDATE inquiries SET answer=?,status='ANSWERED',answered_by=?,answered_at=CURRENT_TIMESTAMP WHERE inquiry_id=?",
            Input.text(body, "answer", 10000, true),
            admin,
            id
        );
        service.notify(
            number(inquiry, "userId"),
            "문의에 답변이 등록되었습니다.",
            "고객센터에서 답변을 확인해 주세요.",
            "INQUIRY",
            id
        );
    }

    @GetMapping("/admin/employment-links/{id}/company-review")
    Object companyReview(@PathVariable long id, HttpServletRequest req) {
        admin(req);
        return db.list("SELECT r.review_status,r.opinion,r.updated_at,c.company_name FROM employment_company_reviews r JOIN companies c ON c.company_id=r.company_id JOIN employment_links e ON e.employment_link_id=r.employment_link_id AND e.company_id=r.company_id WHERE e.employment_link_id=?", id);
    }

    @PatchMapping("/admin/employment-links/{id}")
    void link(
        @PathVariable long id,
        @RequestBody Map<String, Object> body,
        HttpServletRequest req
    ) {
        admin(req);
        var link = db.one(
            "SELECT * FROM employment_links WHERE employment_link_id=? FOR UPDATE",
            id
        );
        String next = Input.text(body, "status", 30, true);
        String current = link.get("status").toString();
        Map<String, Set<String>> moves = Map.of(
            "REQUESTED",
            Set.of("COUNSELING", "CLOSED"),
            "COUNSELING",
            Set.of("MATCHING", "CLOSED"),
            "MATCHING",
            Set.of("INTERVIEW", "CLOSED"),
            "INTERVIEW",
            Set.of("MATCHING", "EMPLOYED", "CLOSED")
        );
        if (
            !moves.containsKey(current) ||
            !(current.equals(next) || moves.get(current).contains(next))
        ) throw new Problem(409, "허용되지 않은 연계 상태 변경입니다.");
        Long manager = nullableId(body, "managerUserId");
        Long company = nullableId(body, "companyId");
        Long job = nullableId(body, "jobId");
        if (manager != null) db.one(
            "SELECT user_id FROM users WHERE user_id=? AND role='ADMIN' AND status='ACTIVE'",
            manager
        );
        if (company != null) db.one(
            "SELECT company_id FROM companies WHERE company_id=? AND approval_status='APPROVED'",
            company
        );
        if (job != null) {
            if (company == null) throw new Problem(
                400,
                "연계 기업을 먼저 지정해 주세요."
            );
            db.one(
                "SELECT job_id FROM job_postings WHERE job_id=? AND company_id=?",
                job,
                company
            );
        }
        if (
            Set.of("MATCHING", "INTERVIEW", "EMPLOYED").contains(next) &&
            (manager == null || company == null)
        ) throw new Problem(400, "담당자와 연계 기업을 지정해 주세요.");
        db.update(
            "UPDATE employment_links SET status=?,manager_user_id=?,company_id=?,job_id=?,public_message=?,memo=?,completed_at=? WHERE employment_link_id=?",
            next,
            manager,
            company,
            job,
            Input.text(body, "publicMessage", 10000, false),
            Input.text(body, "memo", 10000, false),
            Set.of("EMPLOYED", "CLOSED").contains(next)
                ? Timestamp.from(Instant.now())
                : null,
            id
        );
        long user = number(
            db.one(
                "SELECT user_id FROM job_seekers WHERE job_seeker_id=?",
                link.get("jobSeekerId")
            ),
            "userId"
        );
        service.notify(
            user,
            "취업연계 진행 상태가 변경되었습니다.",
            "취업연계 메뉴에서 안내를 확인해 주세요.",
            "EMPLOYMENT",
            id
        );
        if (company != null && (!Objects.equals(company, link.get("companyId")) || !current.equals(next) || !Objects.equals(job, link.get("jobId")))) {
            long companyUser = number(db.one("SELECT user_id FROM companies WHERE company_id=?", company), "userId");
            service.notify(companyUser, "취업연계 정보가 등록·변경되었습니다.",
                "취업연계 메뉴에서 연결된 신청자와 채용공고를 확인해 주세요.", "EMPLOYMENT", id);
        }
    }

    private Long nullableId(Map<String, Object> body, String key) {
        return body.get(key) == null || body.get(key).toString().isBlank()
            ? null
            : Input.id(body, key);
    }
}
