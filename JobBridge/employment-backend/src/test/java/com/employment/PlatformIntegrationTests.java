package com.employment;

import static com.employment.global.common.Database.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.employment.auth.AuthService;
import com.employment.global.common.Database;
import com.employment.global.common.Problem;
import com.employment.job.service.EmploymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PlatformIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    Database db;

    @Autowired
    AuthService auth;

    @Autowired
    EmploymentService service;

    @Autowired
    ObjectMapper json;

    long seeker;
    long other;
    long companyUser;
    long company;
    long job;
    long resume;
    long admin;
    final String password = "TestPassword123!";

    @BeforeEach
    void setup() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        seeker = signup("seeker" + unique, false);
        other = signup("other" + unique, false);
        companyUser = signup("company" + unique, true);
        company = number(service.company(companyUser, false), "companyId");
        db.update("UPDATE companies SET approval_status='APPROVED' WHERE company_id=?", company);
        admin = db.insert(
            "users",
            fields(
                "email",
                "admin" + unique + "@test.local",
                "login_id",
                "admin" + unique,
                "password",
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(
                    password
                ),
                "name",
                "관리자",
                "phone",
                "01012345678",
                "role",
                "ADMIN"
            )
        );
        var body = fields(
            "title",
            "테스트 채용",
            "jobType",
            "정규직",
            "careerType",
            "신입",
            "workLocation",
            "서울",
            "description",
            "채용 상세",
            "headcount",
            1,
            "startDate",
            LocalDate.now().minusDays(1).toString(),
            "endDate",
            LocalDate.now().plusDays(30).toString()
        );
        job = number(service.saveJob(companyUser, null, body), "jobId");
        db.update("UPDATE job_postings SET status='PUBLISHED' WHERE job_id=?", job);
        resume = number(
            service.saveResume(
                seeker,
                null,
                fields("title", "원본 이력서", "skills", "Java", "isDefault", true)
            ),
            "resumeId"
        );
    }

    long signup(String name, boolean company) {
        var body = fields(
            "loginId",
            name,
            "birthDate",
            "20000123",
            "gender",
            "MALE",
            "email",
            name + "@test.local",
            "password",
            password,
            "name",
            name,
            "phone",
            "01012345678",
            "role",
            "ADMIN"
        );
        if (company) {
            body.put("companyName", "테스트 기업");
            body.put(
                "businessNumber",
                String.format(
                    "%010d",
                    Math.abs(UUID.randomUUID().getMostSignificantBits() % 10000000000L)
                )
            );
        }
        return number(auth.signup(body, company), "userId");
    }

    Cookie login(long user) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        var body = fields(
            "loginId",
            auth.publicUser(user).get("loginId"),
            "password",
            password,
            "memberType",
            auth.publicUser(user).get("role")
        );
        if ("ADMIN".equals(auth.publicUser(user).get("role"))) auth.adminLogin(body, response);
        else auth.login(body, response);
        return response.getCookie("accessToken");
    }

    @Test
    void adminDashboardReportsRealCountsQueuesAndSevenDaysWithoutPrivateFields() throws Exception {
        db.update("UPDATE companies SET approval_status='PENDING' WHERE company_id=?", company);
        db.update("UPDATE job_postings SET status='PENDING' WHERE job_id=?", job);
        long inquiry = db.insert(
            "inquiries",
            fields("user_id", seeker, "title", "대기 문의 검증", "content", "비공개 문의 본문")
        );
        service.requestLink(seeker);
        var response = mvc
            .perform(get("/api/admin/dashboard").cookie(login(admin)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        var data = json.readTree(response);
        assertThat(data.path("totalMembers").asLong()).isEqualTo(
            number(
                db.one("SELECT COUNT(*) AS n FROM users WHERE role IN ('JOB_SEEKER','COMPANY')"),
                "n"
            )
        );
        assertThat(data.path("companies").asLong()).isEqualTo(
            number(
                db.one("SELECT COUNT(*) AS n FROM companies WHERE approval_status='PENDING'"),
                "n"
            )
        );
        assertThat(data.path("pendingTotal").asLong()).isEqualTo(
            data.path("companies").asLong() +
                data.path("jobs").asLong() +
                data.path("inquiries").asLong() +
                data.path("newEmploymentLinks").asLong()
        );
        assertThat(data.path("trend").size()).isEqualTo(7);
        for (int i = 0; i < 7; i++) assertThat(
            data.path("trend").get(i).path("date").asText()
        ).isEqualTo(
            com.employment.job.service.EmploymentService.today().minusDays(6 - i).toString()
        );
        assertThat(data.path("pendingCompanies").toString()).contains(Long.toString(company));
        assertThat(data.path("pendingJobs").toString()).contains(Long.toString(job));
        assertThat(data.path("openInquiries").toString()).contains(Long.toString(inquiry));
        assertThat(response).doesNotContain(
            "비공개 문의 본문",
            "password",
            "birthDate",
            "gender",
            "phone",
            "memo"
        );
        mvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
        mvc
            .perform(get("/api/admin/dashboard").cookie(login(seeker)))
            .andExpect(status().isForbidden());
    }

    @Test
    void csrfRolesAndOwnershipAreEnforced() throws Exception {
        mvc
            .perform(post("/api/auth/login").contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/resumes")).andExpect(status().isUnauthorized());
        mvc
            .perform(get("/api/admin/users").cookie(login(seeker)))
            .andExpect(status().isForbidden());
        mvc
            .perform(get("/api/resumes/" + resume).cookie(login(other)))
            .andExpect(status().isNotFound());
        assertThat(auth.publicUser(seeker).get("role")).isEqualTo("JOB_SEEKER");
        mvc
            .perform(get("/api/jobs/" + job))
            .andExpect(status().isOk())
            .andExpect(jsonPath("reviewReason").doesNotExist());
    }

    @Test
    void loginIdIsValidatedUniqueAndEmailLoginIsRejected() throws Exception {
        String loginId = auth.publicUser(seeker).get("loginId").toString();
        var response = new MockHttpServletResponse();
        assertThat(
            number(
                auth.login(
                    fields(
                        "loginId",
                        loginId.toUpperCase(Locale.ROOT),
                        "password",
                        password,
                        "memberType",
                        "JOB_SEEKER"
                    ),
                    response
                ),
                "userId"
            )
        ).isEqualTo(seeker);
        assertThat(response.getCookie("accessToken")).isNotNull();
        var signup = fields(
            "loginId",
            loginId.toUpperCase(Locale.ROOT),
            "email",
            "new" + UUID.randomUUID() + "@test.local",
            "name",
            "중복 테스트",
            "phone",
            "01012345678",
            "password",
            password
        );
        assertThatThrownBy(() -> auth.signup(signup, false)).isInstanceOfSatisfying(
            Problem.class,
            ex -> assertThat(ex.status).isEqualTo(409)
        );
        signup.put("loginId", "bad@id");
        assertThatThrownBy(() -> auth.signup(signup, false)).isInstanceOfSatisfying(
            Problem.class,
            ex -> assertThat(ex.status).isEqualTo(400)
        );
        assertThatThrownBy(() ->
            auth.login(
                fields(
                    "loginId",
                    auth.publicUser(seeker).get("email"),
                    "password",
                    password,
                    "memberType",
                    "JOB_SEEKER"
                ),
                new MockHttpServletResponse()
            )
        ).isInstanceOf(Problem.class);
        assertThatThrownBy(() ->
            auth.login(
                fields(
                    "email",
                    auth.publicUser(seeker).get("email"),
                    "password",
                    password,
                    "memberType",
                    "JOB_SEEKER"
                ),
                new MockHttpServletResponse()
            )
        ).isInstanceOf(Problem.class);
    }

    @Test
    void memberTypeMismatchShowsGuidanceOnlyAfterValidCredentials() throws Exception {
        var cases = List.of(
            fields(
                "user",
                companyUser,
                "memberType",
                "JOB_SEEKER",
                "password",
                password,
                "message",
                "기업회원 계정입니다. 기업 탭을 선택해 로그인해 주세요."
            ),
            fields(
                "user",
                seeker,
                "memberType",
                "COMPANY",
                "password",
                password,
                "message",
                "개인회원 계정입니다. 개인 탭을 선택해 로그인해 주세요."
            ),
            fields(
                "user",
                companyUser,
                "memberType",
                "JOB_SEEKER",
                "password",
                "WrongPassword123!",
                "message",
                "아이디 또는 비밀번호를 확인해 주세요."
            ),
            fields(
                "user",
                admin,
                "memberType",
                "JOB_SEEKER",
                "password",
                password,
                "message",
                "아이디 또는 비밀번호를 확인해 주세요."
            ),
            fields(
                "user",
                admin,
                "memberType",
                "COMPANY",
                "password",
                password,
                "message",
                "아이디 또는 비밀번호를 확인해 주세요."
            )
        );
        for (var test : cases) {
            var response = mvc
                .perform(
                    post("/api/auth/login")
                        .with(csrf())
                        .contentType("application/json")
                        .content(
                            json.writeValueAsString(
                                fields(
                                    "loginId",
                                    auth.publicUser(number(test, "user")).get("loginId"),
                                    "password",
                                    test.get("password"),
                                    "memberType",
                                    test.get("memberType")
                                )
                            )
                        )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(test.get("message")))
                .andReturn()
                .getResponse();
            assertThat(response.getCookie("accessToken")).isNull();
            assertThat(response.getCookie("refreshToken")).isNull();
        }
    }

    @Test
    void administratorLoginRejectsOtherRolesBeforeIssuingCookies() throws Exception {
        var denied = mvc
            .perform(
                post("/api/auth/admin/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            fields(
                                "loginId",
                                auth.publicUser(seeker).get("loginId"),
                                "password",
                                password
                            )
                        )
                    )
            )
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse();
        assertThat(denied.getCookie("accessToken")).isNull();
        assertThat(denied.getCookie("refreshToken")).isNull();
        mvc
            .perform(
                post("/api/auth/admin/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            fields(
                                "loginId",
                                auth.publicUser(admin).get("loginId"),
                                "password",
                                password
                            )
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("role").value("ADMIN"));
        mvc
            .perform(post("/api/auth/admin/login").contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void signupSavesBirthAndGenderAndEnforcesNewInputRules() {
        assertThat(auth.publicUser(seeker).get("birthDate")).isEqualTo("2000-01-23");
        assertThat(auth.publicUser(seeker).get("gender")).isEqualTo("MALE");
        assertThat(
            db.one("SELECT birth_date FROM job_seekers WHERE user_id=?", seeker).get("birthDate")
        ).isEqualTo("2000-01-23");
        var body = fields(
            "loginId",
            "new" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
            "email",
            UUID.randomUUID() + "@test.local",
            "name",
            "검증",
            "phone",
            "01012345678",
            "password",
            password,
            "birthDate",
            "20000123",
            "gender",
            "FEMALE"
        );
        for (String invalid : List.of("test_id", "test-id", "한글아이디", "abc")) {
            var changed = new LinkedHashMap<>(body);
            changed.put("loginId", invalid);
            assertThatThrownBy(() -> auth.signup(changed, false)).isInstanceOf(Problem.class);
        }
        for (String invalid : List.of(
            "20000230",
            "20010229",
            "20001301",
            "2000012",
            "2000-01-23",
            "29990101"
        )) {
            var changed = new LinkedHashMap<>(body);
            changed.put("birthDate", invalid);
            assertThatThrownBy(() -> auth.signup(changed, false)).isInstanceOf(Problem.class);
        }
        for (String invalid : List.of("1234567", "1".repeat(21), "abcd efgh", "abcd\u00a0efgh")) {
            var changed = new LinkedHashMap<>(body);
            changed.put("password", invalid);
            assertThatThrownBy(() -> auth.signup(changed, false)).isInstanceOf(Problem.class);
        }
        var changed = new LinkedHashMap<>(body);
        changed.put("gender", "UNKNOWN");
        assertThatThrownBy(() -> auth.signup(changed, false)).isInstanceOf(Problem.class);
        body.put("password", "1".repeat(20));
        body.put("birthDate", "20000229");
        long user = number(auth.signup(body, false), "userId");
        assertThat(auth.publicUser(user).get("birthDate")).isEqualTo("2000-02-29");
        assertThat(auth.publicUser(user).get("gender")).isEqualTo("FEMALE");
        assertThat(
            com.employment.global.common.Input.password(Map.of("password", "12345678"), "password")
        ).isEqualTo("12345678");
    }

    @Test
    void normalLoginEnforcesSelectedRoleAndNeverIssuesAdminCookies() throws Exception {
        for (String memberType : List.of("JOB_SEEKER", "COMPANY")) {
            var response = mvc
                .perform(
                    post("/api/auth/login")
                        .with(csrf())
                        .contentType("application/json")
                        .content(
                            json.writeValueAsString(
                                fields(
                                    "loginId",
                                    auth.publicUser(admin).get("loginId"),
                                    "password",
                                    password,
                                    "memberType",
                                    memberType
                                )
                            )
                        )
                )
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse();
            assertThat(response.getCookie("accessToken")).isNull();
            assertThat(response.getCookie("refreshToken")).isNull();
        }
        mvc
            .perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            fields(
                                "loginId",
                                auth.publicUser(admin).get("loginId"),
                                "password",
                                password,
                                "memberType",
                                "ADMIN"
                            )
                        )
                    )
            )
            .andExpect(status().isBadRequest());
        var personal = fields(
            "loginId",
            auth.publicUser(seeker).get("loginId"),
            "password",
            password,
            "memberType",
            "COMPANY"
        );
        assertThatThrownBy(() -> auth.login(personal, new MockHttpServletResponse())).isInstanceOf(
            Problem.class
        );
        personal.put("memberType", "JOB_SEEKER");
        assertThat(auth.login(personal, new MockHttpServletResponse()).get("role")).isEqualTo(
            "JOB_SEEKER"
        );
        var business = fields(
            "loginId",
            auth.publicUser(companyUser).get("loginId"),
            "password",
            password,
            "memberType",
            "JOB_SEEKER"
        );
        assertThatThrownBy(() -> auth.login(business, new MockHttpServletResponse())).isInstanceOf(
            Problem.class
        );
        business.put("memberType", "COMPANY");
        assertThat(auth.login(business, new MockHttpServletResponse()).get("role")).isEqualTo(
            "COMPANY"
        );
        business.remove("memberType");
        assertThatThrownBy(() -> auth.login(business, new MockHttpServletResponse())).isInstanceOf(
            Problem.class
        );
    }

    @Test
    void applicationPreservesSnapshotAndRejectsDuplicateAndForeignResume() {
        assertThatThrownBy(() ->
            service.apply(other, job, fields("resumeId", resume))
        ).isInstanceOf(Problem.class);
        long app = number(
            service.apply(seeker, job, fields("resumeId", resume, "coverLetter", "지원합니다")),
            "applicationId"
        );
        service.saveResume(seeker, resume, fields("title", "수정한 이력서", "skills", "React"));
        assertThat(service.application(seeker, app).get("snapshot").toString()).contains(
            "원본 이력서"
        );
        assertThatThrownBy(() ->
            service.apply(seeker, job, fields("resumeId", resume))
        ).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> service.deleteResume(seeker, resume)).isInstanceOf(Problem.class);
        db.update("UPDATE job_postings SET status='CLOSED' WHERE job_id=?", job);
        assertThatThrownBy(() ->
            service.apply(seeker, job, fields("resumeId", resume))
        ).isInstanceOf(Problem.class);
    }

    @Test
    void cancellationAndInternalNotesAreProtected() {
        long app = number(service.apply(seeker, job, fields("resumeId", resume)), "applicationId");
        service.applicationStatus(companyUser, app, "UNDER_REVIEW");
        service.applicationStatus(companyUser, app, "DOCUMENT_PASSED");
        service.applicationStatus(companyUser, app, "INTERVIEW");
        var body = fields(
            "interviewAt",
            java.time.OffsetDateTime.now().plusDays(3).toString(),
            "interviewType",
            "OFFLINE",
            "location",
            "서울",
            "memo",
            "비공개 메모"
        );
        long interview = number(
            service.interview(companyUser, app, "same-key", body),
            "interviewId"
        );
        assertThat(
            number(service.interview(companyUser, app, "same-key", body), "interviewId")
        ).isEqualTo(interview);
        var changed = new LinkedHashMap<>(body);
        changed.put("location", "부산");
        assertThatThrownBy(() ->
            service.interview(companyUser, app, "same-key", changed)
        ).isInstanceOf(Problem.class);
        assertThat(service.application(seeker, app).get("interviews").toString()).doesNotContain(
            "비공개 메모"
        );
        service.cancel(seeker, app);
        assertThat(
            db.one("SELECT status FROM interviews WHERE interview_id=?", interview).get("status")
        ).isEqualTo("CANCELED");
        assertThatThrownBy(() ->
            service.applicationStatus(companyUser, app, "FINAL_PASSED")
        ).isInstanceOf(Problem.class);
    }

    @Test
    void unapprovedCompanyAndRevokedSessionAreRejected() throws Exception {
        Cookie cookie = login(companyUser);
        db.update("UPDATE companies SET approval_status='PENDING' WHERE company_id=?", company);
        mvc
            .perform(get("/api/company/jobs/" + job + "/applications").cookie(cookie))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/jobs/" + job)).andExpect(status().isNotFound());
        Cookie seekerCookie = login(seeker);
        mvc
            .perform(post("/api/auth/logout").cookie(seekerCookie).with(csrf()))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/resumes").cookie(seekerCookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void missingCompanyProfileCanBeRegisteredWithoutBypassingApproval() throws Exception {
        long orphan = signup("orphan" + UUID.randomUUID().toString().substring(0, 8), true);
        db.update("DELETE FROM companies WHERE user_id=?", orphan);
        Cookie cookie = login(orphan);
        mvc
            .perform(get("/api/company/dashboard").cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profile.profileMissing").value(true))
            .andExpect(jsonPath("$.jobs").value(0));
        mvc
            .perform(get("/api/company/profile").cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.approvalStatus").value("UNREGISTERED"));
        var body = fields(
            "companyName",
            "복구 기업",
            "businessNumber",
            "123",
            "approvalStatus",
            "APPROVED",
            "userId",
            companyUser
        );
        mvc
            .perform(
                put("/api/company/profile")
                    .cookie(cookie)
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(body))
            )
            .andExpect(status().isBadRequest());
        assertThat(db.list("SELECT * FROM companies WHERE user_id=?", orphan)).isEmpty();
        body.put(
            "businessNumber",
            String.format("%010d", ThreadLocalRandom.current().nextLong(1000000000L, 9999999999L))
        );
        for (int i = 0; i < 2; i++) {
            mvc
                .perform(
                    put("/api/company/profile")
                        .cookie(cookie)
                        .with(csrf())
                        .contentType("application/json")
                        .content(json.writeValueAsString(body))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("PENDING"))
                .andExpect(jsonPath("$.userId").value(orphan));
        }
        assertThat(db.list("SELECT * FROM companies WHERE user_id=?", orphan)).hasSize(1);
        assertThat(service.company(companyUser, false).get("approvalStatus")).isEqualTo("APPROVED");
        mvc
            .perform(get("/api/company/dashboard").cookie(login(companyUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jobs").value(1));
        mvc
            .perform(
                put("/api/company/profile")
                    .cookie(login(seeker))
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(body))
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void interviewIsVisibleAndNotifiedOnlyToItsApplicant() throws Exception {
        long app = number(service.apply(seeker, job, fields("resumeId", resume)), "applicationId");
        service.applicationStatus(companyUser, app, "UNDER_REVIEW");
        service.applicationStatus(companyUser, app, "DOCUMENT_PASSED");
        var body = fields(
            "interviewAt",
            java.time.OffsetDateTime.now().plusDays(2).toString(),
            "interviewType",
            "OFFLINE",
            "location",
            "서울특별시 중구 세종대로 110 면접실",
            "memo",
            "비공개 평가 메모"
        );
        service.interview(companyUser, app, "notification-check", body);
        service.interview(companyUser, app, "notification-check", body);
        assertThat(
            number(
                db.one(
                    "SELECT COUNT(*) AS n FROM notifications WHERE user_id=? AND notification_type='INTERVIEW' AND related_id=?",
                    seeker,
                    app
                ),
                "n"
            )
        ).isEqualTo(1);
        mvc
            .perform(get("/api/notifications").cookie(login(seeker)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].notificationType").value("INTERVIEW"))
            .andExpect(jsonPath("$[0].relatedId").value(app))
            .andExpect(
                jsonPath("$[0].content").value(org.hamcrest.Matchers.containsString("한국 시간"))
            )
            .andExpect(
                jsonPath("$[0].content").value(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("비공개 평가 메모")
                    )
                )
            );
        mvc
            .perform(get("/api/applications").cookie(login(seeker)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].nextInterviewAt").isNotEmpty());
        mvc
            .perform(get("/api/applications/" + app).cookie(login(seeker)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.interviews[0].location").value(body.get("location")))
            .andExpect(jsonPath("$.interviews[0].memo").doesNotExist());
        mvc
            .perform(get("/api/applications/" + app).cookie(login(other)))
            .andExpect(status().isNotFound());
        mvc
            .perform(get("/api/notifications").cookie(login(other)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deletionChecksOwnershipAndPreservesApplications() throws Exception {
        long foreignCompany = signup(
            "foreign" + UUID.randomUUID().toString().substring(0, 8),
            true
        );
        mvc
            .perform(delete("/api/company/jobs/" + job).cookie(login(foreignCompany)).with(csrf()))
            .andExpect(status().isNotFound());
        mvc
            .perform(delete("/api/admin/jobs/" + job).cookie(login(companyUser)).with(csrf()))
            .andExpect(status().isForbidden());
        service.bookmark(seeker, job, true);
        mvc
            .perform(delete("/api/company/jobs/" + job).cookie(login(companyUser)).with(csrf()))
            .andExpect(status().isNoContent());
        assertThat(db.list("SELECT * FROM job_postings WHERE job_id=?", job)).isEmpty();
        assertThat(db.list("SELECT * FROM job_bookmarks WHERE job_id=?", job)).isEmpty();
    }

    @Test
    void jobsWithApplicationsCannotBeDeletedByCompanyOrAdmin() throws Exception {
        long app = number(service.apply(seeker, job, fields("resumeId", resume)), "applicationId");
        mvc
            .perform(delete("/api/company/jobs/" + job).cookie(login(companyUser)).with(csrf()))
            .andExpect(status().isConflict());
        mvc
            .perform(delete("/api/admin/jobs/" + job).cookie(login(admin)).with(csrf()))
            .andExpect(status().isConflict());
        assertThat(service.application(seeker, app).get("applicationId")).isEqualTo(app);
    }

    @Test
    void noticesAndInquiriesCanBeDeletedOnlyWithPermissionAndListsAreNotTruncated()
        throws Exception {
        long notice = 0;
        for (int i = 0; i < 105; i++) {
            notice = db.insert(
                "notices",
                fields(
                    "title",
                    "페이지 확인 " + i,
                    "content",
                    "공지 본문",
                    "author_user_id",
                    admin,
                    "is_published",
                    true
                )
            );
        }
        var response = mvc
            .perform(get("/api/admin/notices").cookie(login(admin)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
        assertThat(json.readTree(response.getContentAsString()).size()).isGreaterThanOrEqualTo(105);
        mvc
            .perform(delete("/api/admin/notices/" + notice).cookie(login(companyUser)).with(csrf()))
            .andExpect(status().isForbidden());
        mvc
            .perform(delete("/api/admin/notices/" + notice).cookie(login(admin)).with(csrf()))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/notices/" + notice)).andExpect(status().isNotFound());
        long inquiry = db.insert(
            "inquiries",
            fields("user_id", seeker, "title", "잘못 작성한 문의", "content", "문의 본문")
        );
        mvc
            .perform(delete("/api/inquiries/" + inquiry).cookie(login(other)).with(csrf()))
            .andExpect(status().isNotFound());
        mvc
            .perform(delete("/api/inquiries/" + inquiry).cookie(login(seeker)).with(csrf()))
            .andExpect(status().isNoContent());
        mvc
            .perform(get("/api/inquiries/" + inquiry).cookie(login(seeker)))
            .andExpect(status().isNotFound());
    }

    @Test
    void workRegionsSupportDistrictsMultipleSelectionsAndFormalNames() throws Exception {
        String marker = "지역검증" + UUID.randomUUID().toString().substring(0, 8);
        db.update(
            "UPDATE job_postings SET title=?,work_location=? WHERE job_id=?",
            marker,
            "서울특별시 강남구 테헤란로 1",
            job
        );
        for (String location : List.of(
            "부산광역시 강서구",
            "충청남도 천안시 서북구",
            "경기도 광주시"
        )) {
            long added = number(
                service.saveJob(
                    companyUser,
                    null,
                    fields(
                        "title",
                        marker,
                        "workLocation",
                        location,
                        "description",
                        "지역 검색 검증용 공고",
                        "jobType",
                        "정규직",
                        "careerType",
                        "신입",
                        "headcount",
                        1,
                        "startDate",
                        LocalDate.now().minusDays(1).toString(),
                        "endDate",
                        LocalDate.now().plusDays(10).toString()
                    )
                ),
                "jobId"
            );
            db.update("UPDATE job_postings SET status='PUBLISHED' WHERE job_id=?", added);
        }
        for (String region : List.of(
            "서울",
            "서울 강남구",
            "서울특별시 강남구",
            "부산 강서구",
            "충남 천안시 서북구",
            "경기 광주시"
        )) {
            mvc
                .perform(get("/api/jobs").param("q", marker).param("region", region))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        }
        mvc
            .perform(get("/api/jobs").param("q", marker).param("region", "광주"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
        mvc
            .perform(get("/api/jobs").param("q", marker).param("region", "서울 중구"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
        mvc
            .perform(
                get("/api/jobs")
                    .param("q", marker)
                    .param("region", "서울 강남구|부산 강서구")
                    .param("size", "1")
                    .param("page", "1")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.content.length()").value(1));
        mvc
            .perform(
                get("/api/jobs")
                    .param("q", marker)
                    .param("region", "서울 강남구|부산 강서구")
                    .param("type", "계약직")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
        mvc
            .perform(get("/api/jobs").param("q", marker).param("region", ""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(4));
        mvc.perform(get("/api/jobs").param("region", "서울%")).andExpect(status().isBadRequest());
        mvc
            .perform(
                get("/api/jobs").param("region", String.join("|", Collections.nCopies(21, "서울")))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    void structuredResumeRoundTripsAndApplicationKeepsItsSavedSnapshot() throws Exception {
        var details = fields(
            "desiredRole",
            "프런트엔드 개발자",
            "summary",
            "사용자의 문제를 해결하는 개발자입니다.",
            "educations",
            List.of(
                fields(
                    "school",
                    "테스트대학교",
                    "major",
                    "컴퓨터공학",
                    "degree",
                    "학사",
                    "status",
                    "졸업",
                    "startDate",
                    "2019-03",
                    "endDate",
                    "2023-02"
                )
            ),
            "experiences",
            List.of(
                fields(
                    "company",
                    "테스트기업",
                    "position",
                    "개발자",
                    "current",
                    true,
                    "startDate",
                    "2023-03",
                    "description",
                    "기능 개발 및 성능 개선"
                )
            ),
            "projects",
            List.of(
                fields(
                    "name",
                    "잡브릿지",
                    "role",
                    "프런트엔드 개발",
                    "url",
                    "https://example.com/project"
                )
            ),
            "certificates",
            List.of(
                fields("name", "정보처리기사", "issuer", "한국산업인력공단", "date", "2023-06")
            ),
            "legacyCareerDescription",
            "이전에 작성한 프로젝트 경험"
        );
        var body = fields(
            "title",
            "상세 이력서",
            "education",
            "테스트대학교 컴퓨터공학",
            "careerDescription",
            "이전에 작성한 프로젝트 경험",
            "skills",
            "React, Java",
            "details",
            details,
            "isDefault",
            true
        );
        long saved = number(service.saveResume(seeker, null, body), "resumeId");
        mvc
            .perform(get("/api/resumes/" + saved).cookie(login(seeker)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.details.educations[0].school").value("테스트대학교"))
            .andExpect(jsonPath("$.details.experiences[0].current").value(true))
            .andExpect(jsonPath("$.details.certificates[0].name").value("정보처리기사"))
            .andExpect(
                jsonPath("$.details.legacyCareerDescription").value("이전에 작성한 프로젝트 경험")
            )
            .andExpect(jsonPath("$.detailsJson").doesNotExist());
        long app = number(service.apply(seeker, job, fields("resumeId", saved)), "applicationId");
        details.put("summary", "수정한 자기소개");
        body.put("isDefault", false);
        service.saveResume(seeker, saved, body);
        assertThat(service.resume(seeker, saved).get("isDefault")).isEqualTo(false);
        assertThat(service.application(seeker, app).get("snapshot").toString())
            .contains("사용자의 문제를 해결하는 개발자입니다.")
            .doesNotContain("수정한 자기소개");
        service.saveResume(seeker, saved, fields("title", "예전 형식의 수정"));
        assertThat(service.resume(seeker, saved).get("details").toString()).contains(
            "테스트대학교"
        );
        mvc
            .perform(get("/api/resumes/" + saved).cookie(login(other)))
            .andExpect(status().isNotFound());
        var invalid = fields(
            "title",
            "잘못된 이력서",
            "details",
            fields("educations", List.of(fields("startDate", "2025-13")))
        );
        mvc
            .perform(
                post("/api/resumes")
                    .cookie(login(seeker))
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(invalid))
            )
            .andExpect(status().isBadRequest());
        invalid.put("details", fields("projects", List.of(fields("url", "javascript:alert(1)"))));
        mvc
            .perform(
                post("/api/resumes")
                    .cookie(login(seeker))
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(invalid))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    void bookmarksPersistToggleAndRemainPrivateToTheirOwner() throws Exception {
        Cookie cookie = login(seeker);
        for (int i = 0; i < 2; i++) mvc
            .perform(put("/api/bookmarks/" + job).cookie(cookie).with(csrf()))
            .andExpect(status().isOk());
        mvc
            .perform(get("/api/bookmarks").cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].jobId").value(job));
        mvc
            .perform(get("/api/bookmarks").cookie(login(other)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
        mvc
            .perform(delete("/api/bookmarks/" + job).cookie(login(other)).with(csrf()))
            .andExpect(status().isOk());
        mvc
            .perform(get("/api/bookmarks").cookie(cookie))
            .andExpect(jsonPath("$.length()").value(1));
        mvc
            .perform(put("/api/bookmarks/" + job).cookie(login(companyUser)).with(csrf()))
            .andExpect(status().isForbidden());
        mvc
            .perform(delete("/api/bookmarks/" + job).cookie(cookie).with(csrf()))
            .andExpect(status().isOk());
        mvc
            .perform(get("/api/bookmarks").cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void withdrawalRejectsWrongPasswordAndRevokesAllSessions() throws Exception {
        Cookie first = login(seeker);
        Cookie second = login(seeker);
        long app = number(service.apply(seeker, job, fields("resumeId", resume)), "applicationId");
        mvc.perform(delete("/api/me").cookie(first).with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(fields("currentPassword", "wrong"))))
            .andExpect(status().isBadRequest());
        assertThat(auth.publicUser(seeker).get("status")).isEqualTo("ACTIVE");
        mvc.perform(delete("/api/me").cookie(first).contentType("application/json")
            .content(json.writeValueAsString(fields("currentPassword", password))))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/api/me").cookie(first).with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(fields("currentPassword", password))))
            .andExpect(status().isNoContent());
        assertThat(auth.publicUser(seeker).get("status")).isEqualTo("WITHDRAWN");
        assertThat(db.one("SELECT status FROM applications WHERE application_id=?", app).get("status")).isEqualTo("CANCELED");
        mvc.perform(get("/api/me").cookie(second)).andExpect(status().isUnauthorized());
        assertThat(db.list("SELECT session_id FROM auth_sessions WHERE user_id=? AND revoked=FALSE", seeker)).isEmpty();
        mvc.perform(patch("/api/admin/users/" + seeker + "/status").cookie(login(admin)).with(csrf())
            .contentType("application/json").content("{\"status\":\"ACTIVE\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void companyWithdrawalClosesJobsAndAdminCannotWithdraw() throws Exception {
        mvc.perform(delete("/api/me").cookie(login(admin)).with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(fields("currentPassword", password))))
            .andExpect(status().isForbidden());
        long app = number(service.apply(seeker, job, fields("resumeId", resume)), "applicationId");
        mvc.perform(delete("/api/me").cookie(login(companyUser)).with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(fields("currentPassword", password))))
            .andExpect(status().isNoContent());
        assertThat(auth.publicUser(companyUser).get("status")).isEqualTo("WITHDRAWN");
        assertThat(db.one("SELECT status FROM job_postings WHERE job_id=?", job).get("status")).isEqualTo("CLOSED");
        assertThat(db.one("SELECT status FROM applications WHERE application_id=?", app).get("status")).isEqualTo("CANCELED");
        mvc.perform(get("/api/jobs/" + job)).andExpect(status().isNotFound());
        assertThat(auth.publicUser(seeker).get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void employmentChoicesAreAdminOnlyAndReturnReadableNames() throws Exception {
        mvc.perform(get("/api/admin/employment-link-options").cookie(login(seeker)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/employment-link-options"))
            .andExpect(status().isUnauthorized());
        var response = mvc.perform(get("/api/admin/employment-link-options").cookie(login(admin)))
            .andExpect(status().isOk()).andReturn().getResponse();
        var data = json.readTree(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(data.get("managers").toString()).contains(auth.publicUser(admin).get("name").toString());
        assertThat(data.get("managers").get(0).has("password")).isFalse();
        assertThat(data.get("companies").toString()).contains("테스트 기업");
        assertThat(data.get("jobs").toString()).contains("companyId").contains("title");
        db.update("UPDATE users SET status='WITHDRAWN' WHERE user_id=?", companyUser);
        var filtered = mvc.perform(get("/api/admin/employment-link-options").cookie(login(admin)))
            .andExpect(status().isOk()).andReturn().getResponse();
        var after = json.readTree(filtered.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        for (var row : after.get("companies")) assertThat(row.get("companyId").asLong()).isNotEqualTo(company);
        for (var row : after.get("jobs")) assertThat(row.get("jobId").asLong()).isNotEqualTo(job);
    }

    @Test
    void profilePhotosPersistAndOnlyTheirOwnerCanReadOrChangeThem() throws Exception {
        var image = new java.awt.image.BufferedImage(
            40,
            60,
            java.awt.image.BufferedImage.TYPE_INT_RGB
        );
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", output);
        String encoded =
            "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        Cookie cookie = login(seeker);
        Cookie otherCookie = login(other);
        mvc.perform(get("/api/me/photo").cookie(cookie)).andExpect(status().isNoContent());
        mvc
            .perform(
                put("/api/me/photo")
                    .cookie(cookie)
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(fields("image", encoded)))
            )
            .andExpect(status().isNoContent());
        byte[] stored = mvc
            .perform(get("/api/me/photo").cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/jpeg"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
        var decoded = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(stored));
        assertThat(decoded.getWidth()).isEqualTo(512);
        assertThat(decoded.getHeight()).isEqualTo(512);
        mvc.perform(get("/api/me/photo").cookie(login(seeker))).andExpect(status().isOk());
        mvc.perform(get("/api/me/photo").cookie(otherCookie)).andExpect(status().isNoContent());
        mvc
            .perform(delete("/api/me/photo").cookie(otherCookie).with(csrf()))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/me/photo").cookie(cookie)).andExpect(status().isOk());
        mvc
            .perform(
                put("/api/me/photo")
                    .cookie(cookie)
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            fields("image", "data:image/png;base64,aW52YWxpZA==")
                        )
                    )
            )
            .andExpect(status().isBadRequest());
        mvc
            .perform(
                put("/api/me/photo")
                    .cookie(cookie)
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            fields("image", "data:image/svg+xml;base64,PHN2Zz4=")
                        )
                    )
            )
            .andExpect(status().isBadRequest());
        mvc
            .perform(
                put("/api/me/photo")
                    .cookie(cookie)
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            fields("image", "data:image/jpeg;base64," + "a".repeat(2800000))
                        )
                    )
            )
            .andExpect(status().isBadRequest());
        // Replacing a photo updates a single row rather than creating duplicates.
        mvc
            .perform(
                put("/api/me/photo")
                    .cookie(cookie)
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(fields("image", encoded)))
            )
            .andExpect(status().isNoContent());
        mvc
            .perform(delete("/api/me/photo").cookie(cookie).with(csrf()))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/me/photo").cookie(cookie)).andExpect(status().isNoContent());
        mvc.perform(get("/api/me/photo")).andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentApplicationsDefaultsAndLinksRemainUnique() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            var calls = IntStream.range(0, 4)
                .<Callable<Boolean>>mapToObj(
                    i ->
                        () -> {
                            try {
                                service.apply(seeker, job, fields("resumeId", resume));
                                return true;
                            } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                                return false;
                            }
                        }
                )
                .toList();
            long successes = 0;
            for (var result : pool.invokeAll(calls)) if (result.get()) successes++;
            assertThat(successes).isEqualTo(1);
            long second = number(
                service.saveResume(seeker, null, fields("title", "두번째")),
                "resumeId"
            );
            var defaults = IntStream.range(0, 4)
                .<Callable<Void>>mapToObj(
                    i ->
                        () -> {
                            service.defaultResume(seeker, i % 2 == 0 ? resume : second);
                            return null;
                        }
                )
                .toList();
            for (var result : pool.invokeAll(defaults)) result.get();
            assertThat(
                number(
                    db.one(
                        "SELECT COUNT(*) AS n FROM resumes WHERE job_seeker_id=? AND is_default=TRUE",
                        service.seeker(seeker)
                    ),
                    "n"
                )
            ).isEqualTo(1);
            var links = IntStream.range(0, 4)
                .<Callable<Boolean>>mapToObj(
                    i ->
                        () -> {
                            try {
                                service.requestLink(seeker);
                                return true;
                            } catch (Problem ex) {
                                return false;
                            }
                        }
                )
                .toList();
            successes = 0;
            for (var result : pool.invokeAll(links)) if (result.get()) successes++;
            assertThat(successes).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
