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

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.employment.auth.SmsService smsService;

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

    @Test
    void employmentLinkAdminReturnsSingleCharacterApplicantName() throws Exception {
        db.update("UPDATE users SET name=? WHERE user_id=?", "차", seeker);
        long linkId = number(service.requestLink(seeker), "employmentLinkId");
        Cookie session = login(admin);
        mvc.perform(get("/api/admin/employment-links").cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.employmentLinkId == " + linkId + ")].jobSeekerName").value(org.hamcrest.Matchers.contains("차")));
        mvc.perform(get("/api/admin/employment-links/" + linkId).cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jobSeekerName").value("차"));
        mvc.perform(get("/api/admin/dashboard").cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recentLinks[?(@.employmentLinkId == " + linkId + ")].jobSeekerName").value(org.hamcrest.Matchers.contains("차")));
    }

    @Test
    void adminInquiriesIncludeAuthorNameInListAndDetail() throws Exception {
        db.update("UPDATE users SET name=? WHERE user_id=?", "차", seeker);
        long inquiryId = db.insert("inquiries", fields("user_id", seeker, "title", "문의 작성자 확인", "content", "문의 내용"));
        Cookie session = login(admin);
        mvc.perform(get("/api/admin/inquiries").cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.inquiryId == " + inquiryId + ")].authorName").value(org.hamcrest.Matchers.contains("차")));
        mvc.perform(get("/api/admin/inquiries/" + inquiryId).cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authorName").value("차"));
    }

    @Test
    void onlyOwningCompanyCanDeleteInterview() throws Exception {
        long app = number(service.apply(seeker, job, fields("resumeId", resume)), "applicationId");
        service.applicationStatus(companyUser, app, "UNDER_REVIEW");
        service.applicationStatus(companyUser, app, "DOCUMENT_PASSED");
        long interview = number(service.interview(companyUser, app, "delete-test", fields(
            "interviewAt", java.time.OffsetDateTime.now().plusDays(3).toString(),
            "interviewType", "OFFLINE", "location", "서울"
        )), "interviewId");
        long otherCompany = signup("deletecompany" + UUID.randomUUID().toString().substring(0, 8), true);
        db.update("UPDATE companies SET approval_status='APPROVED' WHERE user_id=?", otherCompany);
        mvc.perform(delete("/api/company/interviews/" + interview).cookie(login(otherCompany)).with(csrf()))
            .andExpect(status().isNotFound());
        mvc.perform(delete("/api/company/interviews/" + interview).cookie(login(seeker)).with(csrf()))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/api/company/interviews/" + interview).cookie(login(companyUser)).with(csrf()))
            .andExpect(status().isNoContent());
        assertThat(db.list("SELECT interview_id FROM interviews WHERE interview_id=?", interview)).isEmpty();
        assertThat(db.list("SELECT notification_id FROM notifications WHERE user_id=? AND title=? AND related_id=?", seeker, "면접 일정이 삭제되었습니다.", app)).hasSize(1);
    }

    @Test
    void employmentDetailShowsPublicMatchAndProtectsPrivateInformation() throws Exception {
        long link = number(service.requestLink(seeker), "employmentLinkId");
        mvc.perform(get("/api/employment-links/" + link).cookie(login(seeker)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REQUESTED"))
            .andExpect(jsonPath("$.managerName").isEmpty());
        db.update("UPDATE employment_links SET status='MATCHING',manager_user_id=?,company_id=?,job_id=?,public_message=?,memo=? WHERE employment_link_id=?",
            admin, company, job, "기업 소개를 확인해 주세요.", "비공개 상담 메모", link);
        mvc.perform(get("/api/employment-links/" + link).cookie(login(seeker)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.managerName").value("관리자"))
            .andExpect(jsonPath("$.companyId").value(company))
            .andExpect(jsonPath("$.jobId").value(job))
            .andExpect(jsonPath("$.publicMessage").value("기업 소개를 확인해 주세요."))
            .andExpect(jsonPath("$.memo").doesNotExist())
            .andExpect(jsonPath("$.managerUserId").doesNotExist());
        mvc.perform(get("/api/employment-links/" + link).cookie(login(other)))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/employment-links/" + link).cookie(login(companyUser)))
            .andExpect(status().isForbidden());
        db.update("UPDATE job_postings SET status='CLOSED' WHERE job_id=?", job);
        mvc.perform(get("/api/employment-links/" + link).cookie(login(seeker)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.jobId").isEmpty())
            .andExpect(jsonPath("$.jobTitle").isNotEmpty());
    }

    @Test
    void companySeesAssignedEmploymentLinksOnlyWithoutPrivateNotes() throws Exception {
        long link = number(service.requestLink(seeker), "employmentLinkId");
        db.update("UPDATE employment_links SET status='COUNSELING',company_id=?,job_id=?,manager_user_id=?,memo=?,public_message=? WHERE employment_link_id=?",
            company, job, admin, "관리자 비공개 메모", "구직자 전용 안내", link);
        long unassigned = number(service.requestLink(other), "employmentLinkId");
        mvc.perform(get("/api/company/employment-links").cookie(login(companyUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.employmentLinkId == " + link + ")].managerName").value(org.hamcrest.Matchers.contains("관리자")))
            .andExpect(jsonPath("$[?(@.employmentLinkId == " + unassigned + ")]").isEmpty())
            .andExpect(jsonPath("$[0].memo").doesNotExist())
            .andExpect(jsonPath("$[0].publicMessage").doesNotExist());
        long otherCompany = signup("linkcompany" + UUID.randomUUID().toString().substring(0, 8), true);
        mvc.perform(get("/api/company/employment-links").cookie(login(otherCompany)))
            .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/company/employment-links").cookie(login(seeker)))
            .andExpect(status().isForbidden());
    }

    @Test
    void linkedCompanyReviewsOnlyAssignedApplicantAndPublicResume() throws Exception {
        long link = number(service.requestLink(seeker), "employmentLinkId");
        db.update("UPDATE employment_links SET company_id=?,job_id=?,manager_user_id=?,memo=?,public_message=? WHERE employment_link_id=?",
            company, job, admin, "내부 메모", "구직자 안내", link);
        db.update("UPDATE resumes SET is_public=FALSE WHERE resume_id=?", resume);
        Cookie session = login(companyUser);
        mvc.perform(get("/api/company/employment-links/" + link).cookie(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.resume").isEmpty())
            .andExpect(jsonPath("$.memo").doesNotExist()).andExpect(jsonPath("$.publicMessage").doesNotExist());
        db.update("UPDATE resumes SET is_public=TRUE WHERE resume_id=?", resume);
        mvc.perform(get("/api/company/employment-links/" + link).cookie(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.resume.resumeId").value(resume))
            .andExpect(jsonPath("$.resume.attachment").doesNotExist());
        var review = fields("reviewStatus", "INTERVIEW_REQUESTED", "opinion", "면접을 희망합니다.");
        for (int i = 0; i < 2; i++) mvc.perform(patch("/api/company/employment-links/" + link + "/review").cookie(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(review)))
            .andExpect(status().isNoContent());
        assertThat(db.one("SELECT status FROM employment_links WHERE employment_link_id=?", link).get("status")).isEqualTo("REQUESTED");
        assertThat(db.list("SELECT notification_id FROM notifications WHERE user_id=? AND related_id=? AND title=?", admin, link, "기업 취업연계 검토 의견이 등록되었습니다.")).hasSize(1);
        mvc.perform(get("/api/admin/employment-links/" + link + "/company-review").cookie(login(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].opinion").value("면접을 희망합니다."));
        mvc.perform(patch("/api/company/employment-links/" + link + "/review").cookie(session).with(csrf()).contentType("application/json").content("{\"reviewStatus\":\"DECLINED\",\"opinion\":\"\"}"))
            .andExpect(status().isBadRequest());
        long otherCompany = signup("reviewcompany" + UUID.randomUUID().toString().substring(0, 8), true);
        db.update("UPDATE companies SET approval_status='APPROVED' WHERE user_id=?", otherCompany);
        mvc.perform(get("/api/company/employment-links/" + link).cookie(login(otherCompany))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/company/employment-links/" + link + "/review").cookie(login(otherCompany)).with(csrf()).contentType("application/json").content(json.writeValueAsString(review))).andExpect(status().isNotFound());
        mvc.perform(get("/api/company/employment-links/" + link).cookie(login(seeker))).andExpect(status().isForbidden());
        db.update("UPDATE employment_links SET status='CLOSED' WHERE employment_link_id=?", link);
        mvc.perform(patch("/api/company/employment-links/" + link + "/review").cookie(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(review))).andExpect(status().isConflict());
    }

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
                "birth_date", LocalDate.of(2000, 1, 1),
                "gender", "MALE",
                "phone",
                nextPhone(),
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
            EmploymentService.today().toString(),
            "endDate",
            LocalDate.now().plusDays(30).toString()
        );
        job = number(service.saveJob(companyUser, null, body), "jobId");
        db.update("UPDATE job_postings SET status='PUBLISHED' WHERE job_id=?", job);
        resume = number(
            service.saveResume(
                seeker,
                null,
                fields("title", "원본 이력서", "skills", "Java", "isDefault", true, "assessmentAnswers", CORRECT_EXAMPLE_ANSWERS)
            ),
            "resumeId"
        );
    }

    static final java.util.concurrent.atomic.AtomicLong phoneSequence = new java.util.concurrent.atomic.AtomicLong(10000000);

    static String nextPhone() { return "010" + phoneSequence.getAndIncrement(); }

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
            nextPhone(),
            "role",
            "ADMIN"
        );
        body.put("termsAccepted", true);
        body.put("privacyAccepted", true);
        body.put("agreementVersion", "2026-10-21");
        body.put("smsVerificationToken", "platform-test-verification");
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
        body.put("smsVerificationToken", "platform-test-verification");
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
            nextPhone(),
            "password",
            password
        );
        signup.put("termsAccepted", true);
        signup.put("privacyAccepted", true);
        signup.put("agreementVersion", "2026-10-21");
        signup.put("smsVerificationToken", "platform-test-verification");
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
    void signupRequiresEveryMandatoryFieldAndDatabaseRejectsMissingValues() throws Exception {
        for (boolean isCompany : List.of(false, true)) {
            var body = fields("loginId", "required" + UUID.randomUUID().toString().replace("-", "").substring(0,10),
                "password", password, "name", "필수 정보 검증", "email", UUID.randomUUID() + "@test.local",
                "phone", nextPhone(), "birthDate", "20000123", "gender", "FEMALE",
                "companyName", "필수 기업", "businessNumber", "1234509876",
                "termsAccepted", true, "privacyAccepted", true, "agreementVersion", "2026-10-21", "smsVerificationToken", "platform-test-verification");
            var required = new ArrayList<>(List.of("loginId", "password", "name", "email", "phone", "birthDate", "gender"));
            if (isCompany) required.addAll(List.of("companyName", "businessNumber"));
            for (String field : required) {
                for (Object value : Arrays.asList(null, "", "   ")) {
                    var invalid = new LinkedHashMap<>(body); invalid.put(field, value);
                    assertThatThrownBy(() -> auth.signup(invalid, isCompany)).isInstanceOf(Problem.class);
                }
            }
        }
        for (String column : List.of("login_id", "password", "name", "email", "phone", "birth_date", "gender")) {
            assertThatThrownBy(() -> db.update("UPDATE users SET " + column + "=NULL WHERE user_id=?", seeker))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
        for (String column : List.of("login_id", "password", "name", "email", "phone")) {
            assertThatThrownBy(() -> db.update("UPDATE users SET " + column + "='   ' WHERE user_id=?", seeker))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
        for (String column : List.of("company_name", "business_number")) {
            assertThatThrownBy(() -> db.update("UPDATE companies SET " + column + "=NULL WHERE company_id=?", company))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(() -> db.update("UPDATE companies SET " + column + "='   ' WHERE company_id=?", company))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> db.update("UPDATE user_signup_consents SET terms_accepted=FALSE WHERE user_id=?", seeker))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("UPDATE user_signup_consents SET privacy_accepted=FALSE WHERE user_id=?", seeker))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void loginIdAvailabilityAndInlineConflict() throws Exception {
        String taken = auth.publicUser(seeker).get("loginId").toString();
        mvc.perform(get("/api/auth/login-id/availability").param("loginId", taken.toUpperCase(Locale.ROOT)))
            .andExpect(status().isOk()).andExpect(jsonPath("available").value(false));
        mvc.perform(get("/api/auth/login-id/availability").param("loginId", "free" + UUID.randomUUID().toString().replace("-", "").substring(0,12)))
            .andExpect(status().isOk()).andExpect(jsonPath("available").value(true));
        mvc.perform(get("/api/auth/login-id/availability").param("loginId", "ab_"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/signup/job-seeker").with(csrf()).contentType("application/json").content(json.writeValueAsString(fields("loginId", taken))))
            .andExpect(status().isConflict()).andExpect(jsonPath("fieldErrors[0].field").value("loginId"));
    }

    @Test
    void phoneUniquenessCoversSignupProfileAndDatabase() throws Exception {
        String taken = auth.publicUser(seeker).get("phone").toString();
        String formatted = taken.substring(0,3) + "-" + taken.substring(3,7) + "-" + taken.substring(7);
        var body = fields("loginId", "phone" + UUID.randomUUID().toString().replace("-", "").substring(0,12),
            "email", UUID.randomUUID() + "@test.local", "name", "번호 검증", "phone", formatted,
            "password", password, "birthDate", "20000123", "gender", "MALE",
            "termsAccepted", true, "privacyAccepted", true, "agreementVersion", "2026-10-21", "smsVerificationToken", "platform-test-verification");
        mvc.perform(post("/api/auth/signup/job-seeker").with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isConflict()).andExpect(jsonPath("message").value("이미 사용 중인 휴대폰번호입니다. 다른 번호를 입력해 주세요."));
        body.put("companyName", "번호 검증 기업"); body.put("businessNumber", "1234509876");
        mvc.perform(post("/api/auth/signup/company").with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isConflict());
        Cookie session = login(other);
        String previous = auth.publicUser(other).get("phone").toString();
        mvc.perform(patch("/api/me").cookie(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(fields("name", "번호 검증", "phone", formatted))))
            .andExpect(status().isConflict());
        assertThat(auth.publicUser(other).get("phone")).isEqualTo(previous);
        mvc.perform(patch("/api/me").cookie(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(fields("name", "번호 검증", "phone", previous))))
            .andExpect(status().isOk());
        String fresh = nextPhone();
        mvc.perform(patch("/api/me").cookie(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(fields("name", "번호 검증", "phone", fresh))))
            .andExpect(status().isOk());
        assertThatThrownBy(() -> db.update("UPDATE users SET phone=? WHERE user_id=?", taken, other))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
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
            nextPhone(),
            "password",
            password,
            "birthDate",
            "20000123",
            "gender",
            "FEMALE"
        );
        body.put("termsAccepted", true);
        body.put("privacyAccepted", true);
        body.put("agreementVersion", "2026-10-21");
        body.put("smsVerificationToken", "platform-test-verification");
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
        body.put("smsVerificationToken", "platform-test-verification");
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
        ).isInstanceOf(Problem.class).hasMessageContaining("중복 지원");
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
        assertThat(number(service.interview(companyUser, app, "another-click", body), "interviewId"))
            .isEqualTo(interview);
        assertThat(number(db.one("SELECT COUNT(*) AS n FROM interviews WHERE application_id=?", app), "n"))
            .isEqualTo(1);
        assertThat(number(db.one("SELECT COUNT(*) AS n FROM notifications WHERE notification_type='INTERVIEW' AND related_id=?", app), "n"))
            .isEqualTo(1);
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
                        EmploymentService.today().toString(),
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
        body.put("assessmentAnswers", CORRECT_EXAMPLE_ANSWERS);
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
    void signupRequiresExplicitConsentsAndRecordsOptionalChoice() throws Exception {
        for (String kind : List.of("job-seeker", "company")) {
            var body = fields("loginId", "agree" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                "email", UUID.randomUUID() + "@test.local", "name", "동의 검증", "phone", nextPhone(),
                "password", password, "birthDate", "20000123", "gender", "MALE",
                "companyName", "동의 기업", "businessNumber", String.format("%010d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 10000000000L)));
            for (Object invalid : new Object[]{false, "true"}) {
                body.put("termsAccepted", invalid); body.put("privacyAccepted", true);
                body.put("agreementVersion", "2026-10-21");
        body.put("smsVerificationToken", "platform-test-verification");
                mvc.perform(post("/api/auth/signup/" + kind).with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
            }
            body.put("termsAccepted", true); body.put("privacyAccepted", false);
            mvc.perform(post("/api/auth/signup/" + kind).with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
            body.put("privacyAccepted", true); body.put("agreementVersion", "old");
            mvc.perform(post("/api/auth/signup/" + kind).with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isConflict());
            assertThat(db.list("SELECT user_id FROM users WHERE login_id=?", body.get("loginId"))).isEmpty();
            body.put("agreementVersion", "2026-10-21");
        body.put("smsVerificationToken", "platform-test-verification");
            body.put("marketingAccepted", kind.equals("company"));
            var response = mvc.perform(post("/api/auth/signup/" + kind).with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn().getResponse();
            long user = json.readTree(response.getContentAsString()).get("userId").asLong();
            var consent = db.one("SELECT * FROM user_signup_consents WHERE user_id=?", user);
            assertThat(consent.get("termsAccepted")).isEqualTo(true);
            assertThat(consent.get("privacyAccepted")).isEqualTo(true);
            assertThat(consent.get("marketingAccepted")).isEqualTo(kind.equals("company"));
            assertThat(consent.get("termsVersion")).isEqualTo("2026-10-21");
            assertThat(consent.get("recordedAt")).isNotNull();
        }
    }

    @Test
    void companyImagesAreOwnedValidatedAndVisibleOnlyForActiveApprovedCompanies() throws Exception {
        var image = new java.awt.image.BufferedImage(40, 40, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", output);
        var body = fields("image", "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray()));
        mvc.perform(put("/api/company/image").cookie(login(seeker)).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isForbidden());
        mvc.perform(put("/api/company/image").cookie(login(companyUser)).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isNoContent());
        mvc.perform(get("/api/companies/" + company + "/image")).andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"));
        long another = signup("img" + UUID.randomUUID().toString().replace("-", "").substring(0, 8), true);
        mvc.perform(get("/api/company/image").cookie(login(another))).andExpect(status().isNoContent());
        mvc.perform(put("/api/company/image").cookie(login(companyUser)).with(csrf()).contentType("application/json").content("{\"image\":\"invalid\"}")).andExpect(status().isBadRequest());
        db.update("UPDATE companies SET approval_status='PENDING' WHERE company_id=?", company);
        mvc.perform(get("/api/companies/" + company + "/image")).andExpect(status().isNotFound());
        mvc.perform(get("/api/company/image").cookie(login(companyUser))).andExpect(status().isOk());
        mvc.perform(get("/api/admin/companies/" + company + "/image")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/companies/" + company + "/image").cookie(login(seeker))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/companies/" + company + "/image").cookie(login(companyUser))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/companies/" + company + "/image").cookie(login(admin))).andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"));
        mvc.perform(patch("/api/admin/companies/" + company + "/approval").cookie(login(admin)).with(csrf()).contentType("application/json").content("{\"status\":\"APPROVED\"}")).andExpect(status().isOk());
        assertThat(db.one("SELECT title,notification_type FROM notifications WHERE user_id=? AND notification_type='COMPANY' ORDER BY notification_id DESC LIMIT 1", companyUser).get("title")).isEqualTo("기업 승인이 완료되었습니다.");
        mvc.perform(delete("/api/company/image").cookie(login(companyUser)).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/companies/" + company + "/image")).andExpect(status().isNoContent());
    }

    @Test
    void noticeImagesPreserveShapeAndPublicationRules() throws Exception {
        var bitmap = new java.awt.image.BufferedImage(120, 240, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(bitmap, "png", output);
        String image = "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        var body = fields("title", "이미지 공지", "content", "공지 본문", "image", image, "isPublished", false, "isPinned", true);
        var response = mvc.perform(post("/api/admin/notices").cookie(login(admin)).with(csrf()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isCreated()).andReturn().getResponse();
        long id = json.readTree(response.getContentAsString()).get("noticeId").asLong();
        mvc.perform(get("/api/notices/" + id + "/image")).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/notices/" + id + "/image").cookie(login(seeker))).andExpect(status().isForbidden());
        var stored = mvc.perform(get("/api/admin/notices/" + id + "/image").cookie(login(admin)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        var decoded = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(stored));
        assertThat(decoded.getHeight()).isEqualTo(decoded.getWidth() * 2);
        body.remove("image"); body.put("isPublished", true);
        mvc.perform(put("/api/admin/notices/" + id).cookie(login(admin)).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isOk());
        mvc.perform(get("/api/notices/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.hasImage").value(true)).andExpect(jsonPath("$.isPinned").value(true));
        mvc.perform(get("/api/notices/" + id + "/image")).andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"));
        body.put("image", "invalid");
        long before = number(db.one("SELECT COUNT(*) AS n FROM notices"), "n");
        mvc.perform(post("/api/admin/notices").cookie(login(admin)).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
        assertThat(number(db.one("SELECT COUNT(*) AS n FROM notices"), "n")).isEqualTo(before);
        body.put("image", "");
        mvc.perform(put("/api/admin/notices/" + id).cookie(login(admin)).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isOk());
        mvc.perform(get("/api/notices/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.hasImage").value(false));
    }

    @Test
    void faqExamplesArePublicButInquiriesRemainPrivate() throws Exception {
        mvc.perform(get("/api/faqs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(15))
            .andExpect(jsonPath("$[0].question").value("회원가입은 어떻게 하나요?"))
            .andExpect(jsonPath("$[0].answer").isNotEmpty());
        mvc.perform(get("/api/inquiries")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/inquiries").with(csrf()).contentType("application/json").content("{\"title\":\"질문\",\"content\":\"내용\"}"))
            .andExpect(status().isUnauthorized());
        var response = mvc.perform(post("/api/inquiries").cookie(login(seeker)).with(csrf()).contentType("application/json").content("{\"title\":\"개인 문의\",\"content\":\"비공개 내용\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse();
        long id = json.readTree(response.getContentAsString()).get("inquiryId").asLong();
        mvc.perform(get("/api/inquiries/" + id).cookie(login(other))).andExpect(status().isNotFound());
        mvc.perform(get("/api/faqs")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("비공개 내용"))));
    }

    @Test
    void passwordChangeRejectsCurrentAndRecentPasswordsWithoutRevokingOnFailure() throws Exception {
        Cookie session = login(seeker);
        mvc.perform(patch("/api/me/password").cookie(session).with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(fields("currentPassword", password, "password", password))))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/me").cookie(session)).andExpect(status().isOk());
        assertThat(db.list("SELECT password_history_id FROM user_password_history WHERE user_id=?", seeker)).isEmpty();
        String next = "DifferentPass123!";
        mvc.perform(patch("/api/me/password").cookie(session).with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(fields("currentPassword", password, "password", next))))
            .andExpect(status().isOk());
        mvc.perform(get("/api/me").cookie(session)).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> auth.changePassword(seeker, fields("currentPassword", next, "password", password)))
            .isInstanceOfSatisfying(Problem.class, error -> assertThat(error.status).isEqualTo(400));
        var history = db.one("SELECT password_hash FROM user_password_history WHERE user_id=?", seeker);
        assertThat(history.get("passwordHash").toString()).isNotEqualTo(password);
        assertThat(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches(password, history.get("passwordHash").toString())).isTrue();
        // Another account's history must not restrict this user's choice.
        auth.changePassword(other, fields("currentPassword", password, "password", next));
        assertThat(db.list("SELECT password_history_id FROM user_password_history WHERE user_id=?", other)).hasSize(1);
    }

    @Test
    void passwordHistoryExpiresSixMonthsAfterLastUse() {
        String next = "DifferentPass123!";
        auth.changePassword(seeker, fields("currentPassword", password, "password", next));
        db.update("UPDATE user_password_history SET used_until=? WHERE user_id=?",
            java.sql.Timestamp.from(java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Seoul")).minusMonths(6).plusDays(1).toInstant()), seeker);
        assertThatThrownBy(() -> auth.changePassword(seeker, fields("currentPassword", next, "password", password))).isInstanceOf(Problem.class);
        db.update("UPDATE user_password_history SET used_until=? WHERE user_id=?",
            java.sql.Timestamp.from(java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Seoul")).minusMonths(6).minusDays(1).toInstant()), seeker);
        auth.changePassword(seeker, fields("currentPassword", next, "password", password));
        assertThat(db.list("SELECT password_history_id FROM user_password_history WHERE user_id=?", seeker)).hasSize(1);
        assertThatThrownBy(() -> auth.changePassword(seeker, fields("currentPassword", "wrong", "password", "AnotherPass123!"))).isInstanceOf(Problem.class);
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
                            } catch (Problem ex) {
                                assertThat(ex.status).isEqualTo(409);
                                return false;
                            }
                        }
                )
                .toList();
            long successes = 0;
            for (var result : pool.invokeAll(calls)) if (result.get()) successes++;
            assertThat(successes).isEqualTo(1);
            long second = number(
                service.saveResume(seeker, null, fields("title", "두번째", "assessmentAnswers", CORRECT_EXAMPLE_ANSWERS)),
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

    private Map<String, Object> regressionJobBody() {
        return fields("title", "Regression job", "jobType", "정규직", "careerType", "신입",
            "workLocation", "서울", "description", "Description", "headcount", 1,
            "startDate", EmploymentService.today().toString(),
            "endDate", EmploymentService.today().plusDays(10).toString());
    }

    @Test
    void frameworkClientErrorsKeep400404405AndAllowHeader() throws Exception {
        mvc.perform(get("/api/auth/login-id/availability"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("HTTP_400"));
        mvc.perform(get("/api/no-such-regression-route"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("HTTP_404"));
        mvc.perform(post("/api/jobs").with(csrf()))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("GET")))
            .andExpect(jsonPath("$.code").value("HTTP_405"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"2026-02-30", "not-a-date"})
    void malformedJobDatesAre400WithoutSaving(String invalid) throws Exception {
        Cookie session = login(companyUser);
        for (String key : List.of("startDate", "endDate")) {
            var body = regressionJobBody();
            body.put(key, invalid);
            mvc.perform(post("/api/company/jobs").cookie(session).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value(key));
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"2026-02-30T10:00:00+09:00", "not-a-date", "2030-01-01T10:00:00"})
    void malformedInterviewCreationAndUpdateAre400(String invalid) throws Exception {
        long app = number(service.apply(seeker, job, fields("resumeId", resume)), "applicationId");
        db.update("UPDATE applications SET status='DOCUMENT_PASSED' WHERE application_id=?", app);
        Cookie session = login(companyUser);
        var body = fields("interviewAt", invalid, "interviewType", "OFFLINE", "location", "Office");
        mvc.perform(post("/api/company/applications/" + app + "/interviews").cookie(session).with(csrf())
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("interviewAt"));
        long interview = db.insert("interviews", fields("application_id", app,
            "interview_at", java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(86400)),
            "interview_type", "OFFLINE", "location", "Office", "request_key", UUID.randomUUID().toString(),
            "request_hash", "regression"));
        db.update("UPDATE applications SET status='INTERVIEW' WHERE application_id=?", app);
        mvc.perform(put("/api/company/interviews/" + interview).cookie(session).with(csrf())
            .contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("interviewAt"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"INVALID", "정규", "%", "", "프리랜서"})
    void invalidEmploymentTypesCannotBeCreatedUpdatedOrFiltered(String invalid) throws Exception {
        var body = regressionJobBody();
        body.put("jobType", invalid);
        Cookie session = login(companyUser);
        mvc.perform(post("/api/company/jobs").cookie(session).with(csrf())
            .contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isBadRequest());
        db.update("UPDATE job_postings SET status='DRAFT' WHERE job_id=?", job);
        mvc.perform(put("/api/company/jobs/" + job).cookie(session).with(csrf())
            .contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isBadRequest());
        assertThat(db.one("SELECT job_type FROM job_postings WHERE job_id=?", job).get("jobType")).isEqualTo("정규직");
        if (!invalid.isEmpty()) mvc.perform(get("/api/jobs").param("type", invalid))
            .andExpect(status().isBadRequest());
    }

    @Test
    void legacyInvalidEmploymentTypeCannotEnterReviewOrPublish() throws Exception {
        db.update("UPDATE job_postings SET status='DRAFT',job_type='INVALID' WHERE job_id=?", job);
        mvc.perform(post("/api/company/jobs/" + job + "/review-request").cookie(login(companyUser)).with(csrf()))
            .andExpect(status().isBadRequest());
        assertThat(db.one("SELECT status FROM job_postings WHERE job_id=?", job).get("status")).isEqualTo("DRAFT");
        db.update("UPDATE job_postings SET status='PENDING' WHERE job_id=?", job);
        mvc.perform(patch("/api/admin/jobs/" + job + "/status").cookie(login(admin)).with(csrf())
            .contentType("application/json").content("{\"status\":\"PUBLISHED\"}"))
            .andExpect(status().isBadRequest());
        assertThat(db.one("SELECT status FROM job_postings WHERE job_id=?", job).get("status")).isEqualTo("PENDING");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"정규직", "계약직", "인턴"})
    void supportedEmploymentTypesCanPassCreateReviewPublishUpdateAndExactFilter(String type) throws Exception {
        Cookie companySession = login(companyUser);
        var body = regressionJobBody();
        body.put("jobType", type);
        var result = mvc.perform(post("/api/company/jobs").cookie(companySession).with(csrf())
            .contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isCreated()).andReturn();
        long id = json.readTree(result.getResponse().getContentAsString()).get("jobId").asLong();
        mvc.perform(post("/api/company/jobs/" + id + "/review-request").cookie(companySession).with(csrf()))
            .andExpect(status().isOk());
        mvc.perform(patch("/api/admin/jobs/" + id + "/status").cookie(login(admin)).with(csrf())
            .contentType("application/json").content("{\"status\":\"PUBLISHED\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/jobs").param("type", type)).andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].jobType", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(type))));
        mvc.perform(put("/api/company/jobs/" + id).cookie(companySession).with(csrf())
            .contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isOk());
        mvc.perform(get("/api/jobs")).andExpect(status().isOk()); // Omitted/empty filter retains "all".
    }

    @Test
    void experienceYearsOmissionRemainsZeroAndExplicitNullIs400WithoutMutation() throws Exception {
        Cookie session = login(seeker);
        long seekerId = service.seeker(seeker);
        db.update("UPDATE job_seekers SET experience_years=7 WHERE job_seeker_id=?", seekerId);
        var body = fields("birthDate", "2000-01-23", "experienceYears", null);
        mvc.perform(patch("/api/job-seekers/me").cookie(session).with(csrf())
            .contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("experienceYears"));
        assertThat(number(db.one("SELECT experience_years FROM job_seekers WHERE job_seeker_id=?", seekerId), "experienceYears")).isEqualTo(7);
        body.remove("experienceYears");
        mvc.perform(patch("/api/job-seekers/me").cookie(session).with(csrf())
            .contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.experienceYears").value(0));
    }

    @Test
    void unavailablePasswordResetDoesNotClaimSuccess() throws Exception {
        for (String action : List.of("request", "confirm"))
            mvc.perform(post("/api/auth/password-reset/" + action).with(csrf())
                .contentType("application/json").content("{\"email\":\"never-send@test.local\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("HTTP_503"));
    }

    @Test
    void noticeBooleanContractAndInquiryNoticeUtcCreationTimesMatchApis() throws Exception {
        java.time.Instant before = java.time.Instant.now().minusSeconds(3);
        long notice = db.insert("notices", fields("title", "UTC notice", "content", "Content",
            "author_user_id", admin, "is_published", true));
        var created = mvc.perform(post("/api/inquiries").cookie(login(seeker)).with(csrf())
            .contentType("application/json").content("{\"title\":\"UTC inquiry\",\"content\":\"Content\"}"))
            .andExpect(status().isCreated()).andReturn();
        long inquiry = json.readTree(created.getResponse().getContentAsString()).get("inquiryId").asLong();
        var detail = mvc.perform(get("/api/inquiries/" + inquiry).cookie(login(seeker)))
            .andExpect(status().isOk()).andReturn();
        assertThat(java.time.Instant.parse(json.readTree(detail.getResponse().getContentAsString()).get("createdAt").asText()))
            .isBetween(before, java.time.Instant.now().plusSeconds(3));
        Cookie adminSession = login(admin);
        for (boolean image : List.of(false, true)) {
            if (image) db.update("INSERT INTO notice_images(notice_id,image_data) VALUES(?,?)", notice, new byte[]{1,2,3});
            for (String path : List.of("/api/notices", "/api/notices/" + notice, "/api/admin/notices", "/api/admin/notices/" + notice)) {
                var response = mvc.perform(get(path).cookie(adminSession)).andExpect(status().isOk()).andReturn();
                var tree = json.readTree(response.getResponse().getContentAsString());
                var nodes = tree.isArray() ? tree : json.createArrayNode().add(tree);
                boolean found = false;
                for (var node : nodes) {
                    assertThat(node.get("hasImage").isBoolean()).isTrue();
                    if (node.get("noticeId").asLong() == notice) {
                        found = true;
                        assertThat(node.get("hasImage").asBoolean()).isEqualTo(image);
                        assertThat(java.time.Instant.parse(node.get("createdAt").asText()))
                            .isBetween(before, java.time.Instant.now().plusSeconds(3));
                    }
                }
                assertThat(found).isTrue();
            }
        }
    }


    @Autowired com.employment.job.service.Assessments assessments;

    private static final List<Integer> CORRECT_EXAMPLE_ANSWERS = List.of(2,1,1,2,2,1,2,0,2,2);

    @Test
    void resumeRequiresCompletedAssessmentAndKeepsExistingResult() {
        int before = service.resumes(seeker).size();
        assertThatThrownBy(() -> service.saveResume(seeker, null, fields("title", "검사 미완료")))
            .isInstanceOf(Problem.class).hasMessageContaining("인적성검사");
        assertThat(service.resumes(seeker)).hasSize(before);
        db.update("DELETE FROM resume_assessments WHERE resume_id=?", resume);
        assertThatThrownBy(() -> service.saveResume(seeker, resume, fields("title", "검사 없이 수정")))
            .isInstanceOf(Problem.class).hasMessageContaining("인적성검사");
        assertThat(service.resume(seeker, resume).get("title")).isEqualTo("원본 이력서");
        assertThatThrownBy(() -> service.saveResume(seeker, null, fields("title", "일부 응답", "assessmentAnswers", List.of(0))))
            .isInstanceOf(Problem.class);
        assertThat(service.resumes(seeker)).hasSize(before);
        var saved = service.saveResume(seeker, resume, fields("title", "검사 완료", "assessmentAnswers", CORRECT_EXAMPLE_ANSWERS));
        assertThat(((Map<?, ?>) saved.get("assessment")).get("score")).isEqualTo(100);
        var updated = service.saveResume(seeker, resume, fields("title", "완료 후 수정"));
        assertThat(((Map<?, ?>) updated.get("assessment")).get("score")).isEqualTo(100);
    }

    @Test
    void exampleQuestionsHideAnswersAndServerComputesScoreAndValidatesCompleteness() throws Exception {
        var body=mvc.perform(get("/api/assessments/questions")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(body).path("questions").size()).isEqualTo(10);
        assertThat(body).doesNotContain("answer","minScore");
        assertThatThrownBy(() -> service.saveResume(seeker,resume,fields("title","검사","assessmentAnswers",List.of(0))))
            .isInstanceOf(Problem.class);
        assertThatThrownBy(() -> service.saveResume(seeker,resume,fields("title","검사","assessmentAnswers",Collections.nCopies(10,0.5))))
            .isInstanceOf(Problem.class);
        var result=service.saveResume(seeker,resume,fields("title","검사","score",0,"assessmentAnswers",CORRECT_EXAMPLE_ANSWERS));
        assertThat(((Map<?,?>)result.get("assessment")).get("score")).isEqualTo(100);
        assertThatThrownBy(() -> service.saveResume(other,resume,fields("title","도용","assessmentAnswers",CORRECT_EXAMPLE_ANSWERS)))
            .isInstanceOf(Problem.class);
    }

    @Test
    void assessmentThresholdIsPrivateAndSubmissionScoreIsImmutable() throws Exception {
        assessments.configure(job,70);
        db.update("DELETE FROM resume_assessments WHERE resume_id=?", resume);
        assertThatThrownBy(() -> service.apply(seeker,job,fields("resumeId",resume))).isInstanceOf(Problem.class);
        assertThat(service.applications(seeker)).isEmpty();
        service.saveResume(seeker,resume,fields("title","검사","assessmentAnswers",CORRECT_EXAMPLE_ANSWERS));
        long app=number(service.apply(seeker,job,fields("resumeId",resume)),"applicationId");
        service.saveResume(seeker,resume,fields("title","다시 검사","assessmentAnswers",Collections.nCopies(10,3)));
        assertThat(service.ownJob(companyUser,job,false).get("assessmentMinScore")).isEqualTo(70);
        var publicBody=mvc.perform(get("/api/jobs/"+job)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(publicBody).doesNotContain("minScore","assessmentMinScore");
        String seekerBody=json.writeValueAsString(service.application(seeker,app));
        assertThat(seekerBody).doesNotContain("minScore","assessmentMinScore","belowThreshold");
        var internal=(Map<?,?>)service.companyApplication(companyUser,app).get("assessment");
        assertThat(internal.get("score")).isEqualTo(100);
        assertThat(internal.get("minScore")).isEqualTo(70);
        assertThatThrownBy(() -> assessments.configure(job,101)).isInstanceOf(Problem.class);
    }

    @Test
    void firstEmployerViewStartsFortyEightHourReviewWithoutAutomaticRejection() throws Exception {
        assessments.configure(job,70);
        service.saveResume(seeker,resume,fields("title","검토","assessmentAnswers",Collections.nCopies(10,3)));
        long app=number(service.apply(seeker,job,fields("resumeId",resume)),"applicationId");
        assertThat(db.one("SELECT first_viewed_at FROM application_assessments WHERE application_id=?",app).get("firstViewedAt")).isNull();
        var first=(Map<?,?>)service.companyApplication(companyUser,app).get("assessment");
        var start=java.time.Instant.parse(first.get("firstViewedAt").toString());
        var due=java.time.Instant.parse(first.get("reviewDueAt").toString());
        assertThat(java.time.Duration.between(start,due).toHours()).isEqualTo(48);
        assertThat(first.get("reviewRequired")).isEqualTo(false);
        var again=(Map<?,?>)service.companyApplication(companyUser,app).get("assessment");
        assertThat(again.get("reviewDueAt")).isEqualTo(first.get("reviewDueAt"));
        db.update("UPDATE application_assessments SET review_due_at=? WHERE application_id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)),app);
        assessments.flagDueReviews();assessments.flagDueReviews();
        assertThat(service.application(seeker,app).get("status")).isEqualTo("APPLIED");
        assertThat(((Map<?,?>)service.companyApplication(companyUser,app).get("assessment")).get("reviewRequired")).isEqualTo(true);
        assertThat(number(db.one("SELECT COUNT(*) AS n FROM notifications WHERE user_id=? AND related_id=? AND title='지원서 검토 시간이 도래했습니다.'",companyUser,app),"n")).isEqualTo(1);
        service.applicationStatus(companyUser,app,"REJECTED");
        assertThat(((Map<?,?>)service.companyApplication(companyUser,app).get("assessment")).get("reviewRequired")).isEqualTo(false);
    }

    private Map<String,Object> examplePdf(String name,String content) {
        return fields("fileName",name,"data",Base64.getEncoder().encodeToString(("%PDF-1.4\n"+content+"\n%%EOF").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void attachmentsRemainPrivateAndSubmissionKeepsOriginalFileAfterReplacementOrRemoval() throws Exception {
        service.saveResume(seeker,resume,fields("title","첨부","attachment",examplePdf("portfolio.pdf","original")));
        long app=number(service.apply(seeker,job,fields("resumeId",resume)),"applicationId");
        var current=mvc.perform(get("/api/resumes/"+resume+"/attachment").cookie(login(seeker))).andExpect(status().isOk())
            .andExpect(header().string("Content-Type","application/pdf")).andExpect(header().string("X-Content-Type-Options","nosniff")).andReturn().getResponse().getContentAsByteArray();
        mvc.perform(get("/api/resumes/"+resume+"/attachment").cookie(login(other))).andExpect(status().isNotFound());
        mvc.perform(get("/api/resumes/"+resume+"/attachment")).andExpect(status().isUnauthorized());
        service.saveResume(seeker,resume,fields("title","교체","attachment",examplePdf("new.pdf","replacement")));
        var submitted=mvc.perform(get("/api/applications/"+app+"/attachment").cookie(login(companyUser))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(submitted).isEqualTo(current);
        mvc.perform(get("/api/applications/"+app+"/attachment").cookie(login(other))).andExpect(status().isNotFound());
        service.saveResume(seeker,resume,fields("title","제거","attachment",null));
        mvc.perform(get("/api/resumes/"+resume+"/attachment").cookie(login(seeker))).andExpect(status().isNotFound());
        mvc.perform(get("/api/applications/"+app+"/attachment").cookie(login(seeker))).andExpect(status().isOk()).andExpect(content().bytes(current));
    }

    @Test
    void malformedAttachmentsAreRejectedAndFailedSavePreservesPriorResume() {
        for(var file:List.of(fields("fileName","bad.exe","data","JVBERi0x"), fields("fileName","bad.pdf","data","bm90LXBkZg=="),
            fields("fileName","../bad.pdf","data","JVBERi0x"), fields("fileName","large.pdf","data","A".repeat(7000001)))) {
            assertThatThrownBy(() -> service.saveResume(seeker,resume,fields("title","invalid change","attachment",file))).isInstanceOf(Problem.class);
            assertThat(service.resume(seeker,resume).get("title")).isEqualTo("원본 이력서");
        }
    }

    @Test
    void answeredInquiryCannotBeDeletedButUnansweredOwnedInquiryCanBeDeleted() throws Exception {
        long answered=db.insert("inquiries",fields("user_id",seeker,"title","완료","content","문의","status","ANSWERED","answer","답변"));
        mvc.perform(delete("/api/inquiries/"+answered).cookie(login(seeker)).with(csrf())).andExpect(status().isConflict());
        assertThat(db.one("SELECT answer FROM inquiries WHERE inquiry_id=?",answered).get("answer")).isEqualTo("답변");
        mvc.perform(delete("/api/inquiries/"+answered).cookie(login(other)).with(csrf())).andExpect(status().isNotFound());
        long pending=db.insert("inquiries",fields("user_id",seeker,"title","대기","content","문의"));
        mvc.perform(delete("/api/inquiries/"+pending).cookie(login(seeker)).with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    void pendingApplicationReturnsClearConflictAndFinalResultAllowsNewAttemptWithSeparateSnapshot() throws Exception {
        long first=number(service.apply(seeker,job,fields("resumeId",resume)),"applicationId");
        mvc.perform(post("/api/jobs/"+job+"/applications").cookie(login(seeker)).with(csrf()).contentType("application/json").content(json.writeValueAsString(fields("resumeId",resume))))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("전형 결과가 나오기 전까지 같은 공고에 중복 지원할 수 없습니다."));
        service.applicationStatus(companyUser,first,"REJECTED");
        long second=number(service.apply(seeker,job,fields("resumeId",resume)),"applicationId");
        assertThat(second).isNotEqualTo(first);
        assertThat(service.applications(seeker)).hasSize(2);
        assertThat(service.applications(other)).isEmpty();
        assertThatThrownBy(() -> service.application(other,second)).isInstanceOf(Problem.class);
    }

    @Test
    void defaultJobPaginationReturnsTwelveAndThirteenthOnNextPage() throws Exception {
        String title="pagination-"+UUID.randomUUID();
        for(int i=0;i<13;i++) {
            var row=service.saveJob(companyUser,null,fields("title",title,"jobType","계약직","careerType","신입","workLocation","서울","description","테스트","headcount",1,"startDate",EmploymentService.today().toString(),"endDate",LocalDate.now().plusDays(10).toString()));
            db.update("UPDATE job_postings SET status='PUBLISHED' WHERE job_id=?",row.get("jobId"));
        }
        mvc.perform(get("/api/jobs").param("q",title).param("type","계약직").param("region","서울"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(12)).andExpect(jsonPath("$.totalElements").value(13)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/jobs").param("q",title).param("page","1")).andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1));
    }


    @Test
    void uploadedBannerPersistsAndPublicCompanyUsesPublishedBanner() throws Exception {
        var image = new java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var random = new Random(42);
        for (int y=0; y<64; y++) for (int x=0; x<64; x++) image.setRGB(x,y,random.nextInt());
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image,"png",output);
        String banner="data:image/png;base64,"+Base64.getEncoder().encodeToString(output.toByteArray());
        assertThat(banner.length()).isGreaterThan(2000);
        var body = new LinkedHashMap<String,Object>(service.ownJob(companyUser,job,false));
        body.put("bannerImageUrl",banner);
        service.saveJob(companyUser,job,body);
        assertThat(service.ownJob(companyUser,job,false).get("bannerImageUrl")).isEqualTo(banner);
        assertThat(service.companyDetail(company).get("bannerImageUrl")).isNull();
        db.update("UPDATE job_postings SET status='PUBLISHED' WHERE job_id=?",job);
        assertThat(service.publicJob(job).get("bannerImageUrl")).isEqualTo(banner);
        assertThat(service.companyDetail(company).get("bannerImageUrl")).isEqualTo(banner);
        body.remove("bannerImageUrl");
        service.saveJob(companyUser,job,body);
        assertThat(service.ownJob(companyUser,job,false).get("bannerImageUrl")).isEqualTo(banner);
        body.put("bannerImageUrl","");
        service.saveJob(companyUser,job,body);
        assertThat(service.ownJob(companyUser,job,false).get("bannerImageUrl")).isEqualTo("");
    }

    @Test
    void jobDatesRejectBackdatingAndPreserveOriginalStartedDate() {
        long before=number(db.one("SELECT COUNT(*) AS total FROM job_postings WHERE company_id=?",company),"total");
        var body=regressionJobBody();
        body.put("startDate", EmploymentService.today().minusDays(1).toString());
        assertThatThrownBy(()->service.saveJob(companyUser,null,body)).isInstanceOf(Problem.class);
        assertThat(number(db.one("SELECT COUNT(*) AS total FROM job_postings WHERE company_id=?",company),"total")).isEqualTo(before);
        body.put("startDate",EmploymentService.today().toString());
        body.put("endDate",EmploymentService.today().minusDays(1).toString());
        assertThatThrownBy(()->service.saveJob(companyUser,null,body)).isInstanceOf(Problem.class);
        db.update("UPDATE job_postings SET start_date=? WHERE job_id=?",EmploymentService.today().minusDays(6),job);
        var existing=new LinkedHashMap<String,Object>(service.ownJob(companyUser,job,false));
        service.saveJob(companyUser,job,existing);
        assertThat(service.ownJob(companyUser,job,false).get("startDate")).isEqualTo(EmploymentService.today().minusDays(6).toString());
        existing.put("startDate",EmploymentService.today().minusDays(2).toString());
        assertThatThrownBy(()->service.saveJob(companyUser,job,existing)).isInstanceOf(Problem.class);
    }

    @Test
    void sidePanelActivityIsPrivateAndReportsOnlyUpcomingScheduledInterviews() throws Exception {
        long application=number(service.apply(seeker,job,fields("resumeId",resume)),"applicationId");
        var now=java.time.Instant.now();
        long next=0;
        for (int i=0; i<5; i++) {
            long interview=db.insert("interviews",fields("application_id",application,"interview_at",java.sql.Timestamp.from(now.plusSeconds((i-1)*3600L)),"interview_type","ONLINE","status",i==4?"CANCELED":"SCHEDULED","request_key","side-"+i,"request_hash","side-panel-test"));
            if (i==2) next=interview;
        }
        var data=service.seekerActivity(seeker);
        assertThat(number(data,"applicationCount")).isEqualTo(1);
        assertThat(number(data,"activeApplications")).isEqualTo(1);
        assertThat(number(data,"resumeCount")).isEqualTo(1);
        assertThat(number(data,"assessedResumeCount")).isEqualTo(1);
        var interviews=(List<Map<String,Object>>)data.get("upcomingInterviews");
        assertThat(interviews).hasSize(2);
        assertThat(number(interviews.get(0),"interviewId")).isEqualTo(next);
        var otherData=service.seekerActivity(other);
        assertThat(number(otherData,"applicationCount")).isZero();
        assertThat(number(otherData,"resumeCount")).isZero();
        assertThat((List<?>)otherData.get("upcomingInterviews")).isEmpty();
        mvc.perform(get("/api/job-seekers/me/activity")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/job-seekers/me/activity").cookie(login(companyUser))).andExpect(status().isForbidden());
        mvc.perform(get("/api/job-seekers/me/activity").cookie(login(seeker)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.applicationCount").value(1))
            .andExpect(jsonPath("$.upcomingInterviews.length()").value(2));
        db.update("UPDATE applications SET status='CANCELED' WHERE application_id=?",application);
        assertThat(number(service.seekerActivity(seeker),"activeApplications")).isZero();
        assertThat((List<?>)service.seekerActivity(seeker).get("upcomingInterviews")).isEmpty();
    }

    @Test
    void companyOverviewCountsAndManagementListsAreScopedToCompany() throws Exception {
        long ownApplication=number(service.apply(seeker,job,fields("resumeId",resume)),"applicationId");
        var body=regressionJobBody();body.put("endDate",EmploymentService.today().plusDays(3).toString());
        long closing=number(service.saveJob(companyUser,null,body),"jobId");
        db.update("UPDATE job_postings SET status='PUBLISHED' WHERE job_id=?",closing);
        long pending=number(service.saveJob(companyUser,null,regressionJobBody()),"jobId");
        db.update("UPDATE job_postings SET status='PENDING' WHERE job_id=?",pending);
        long foreign=signup("foreign"+UUID.randomUUID().toString().substring(0,8),true);
        long foreignJob=number(service.saveJob(foreign,null,regressionJobBody()),"jobId");
        db.update("UPDATE companies SET approval_status='APPROVED' WHERE user_id=?",foreign);
        db.update("UPDATE job_postings SET status='PUBLISHED' WHERE job_id=?",foreignJob);
        long foreignApplication=number(service.apply(seeker,foreignJob,fields("resumeId",resume)),"applicationId");
        long ownInterview=db.insert("interviews",fields("application_id",ownApplication,"interview_at",java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(7200)),"interview_type","ONLINE","request_key","company-own","request_hash","company-test"));
        db.insert("interviews",fields("application_id",foreignApplication,"interview_at",java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(3600)),"interview_type","ONLINE","request_key","company-foreign","request_hash","company-test"));
        var overview=service.companyOverview(companyUser);
        assertThat(number(overview,"jobs")).isEqualTo(3);
        assertThat(number(overview,"publishedJobs")).isEqualTo(2);
        assertThat(number(overview,"pendingJobs")).isEqualTo(1);
        assertThat(number(overview,"closingJobs")).isEqualTo(1);
        assertThat(number(overview,"applications")).isEqualTo(1);
        assertThat(number(overview,"newApplicants")).isEqualTo(1);
        assertThat(number(overview,"unreviewedApplicants")).isEqualTo(1);
        assertThat(number(overview,"interviewCount")).isEqualTo(1);
        assertThat(number(overview,"profileCompletion")).isBetween(0L,100L);
        assertThat(service.companyApplicants(companyUser,"")).hasSize(1);
        assertThat(number(service.companyApplicants(companyUser,"").get(0),"applicationId")).isEqualTo(ownApplication);
        assertThat(number(service.companyInterviews(companyUser).get(0),"interviewId")).isEqualTo(ownInterview);
        db.update("UPDATE applications SET status='UNDER_REVIEW' WHERE application_id=?",ownApplication);
        assertThat(service.companyApplicants(companyUser,"unreviewed")).isEmpty();
        assertThat(number(service.companyOverview(companyUser),"unreviewedApplicants")).isZero();
        assertThatThrownBy(()->service.companyApplicants(companyUser,"invalid")).isInstanceOf(Problem.class);
        mvc.perform(get("/api/company/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/company/applicants").cookie(login(seeker))).andExpect(status().isForbidden());
        mvc.perform(get("/api/company/interviews").cookie(login(seeker))).andExpect(status().isForbidden());
        mvc.perform(get("/api/company/dashboard").cookie(login(companyUser))).andExpect(status().isOk()).andExpect(jsonPath("$.publishedJobs").value(2)).andExpect(jsonPath("$.interviewCount").value(1));
        mvc.perform(get("/api/company/applicants").cookie(login(companyUser))).andExpect(status().isOk()).andExpect(jsonPath("$[0].applicationId").value(ownApplication));
        mvc.perform(get("/api/company/interviews").cookie(login(companyUser))).andExpect(status().isOk()).andExpect(jsonPath("$[0].interviewId").value(ownInterview));
    }

    @Test
    void companyOverviewWithoutProfileIsEmptyAndSafe() {
        long empty=signup("empty"+UUID.randomUUID().toString().substring(0,8),true);
        db.update("DELETE FROM companies WHERE user_id=?",empty);
        var overview=service.companyOverview(empty);
        assertThat(((Map<?,?>)overview.get("profile")).get("profileMissing")).isEqualTo(true);
        assertThat(number(overview,"jobs")).isZero();
        assertThat(number(overview,"interviewCount")).isZero();
        assertThat((List<?>)overview.get("activeJobs")).isEmpty();
        assertThat(service.companyApplicants(empty,"")).isEmpty();
        assertThat(service.companyInterviews(empty)).isEmpty();
    }
}
