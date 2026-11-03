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

/* 261103_이전 버전의 내용
@SpringBootTest(properties = {
    "kakao.client-id=test-only-client",
    "kakao.client-secret=test-only-secret",
    "kakao.redirect-uri=http://localhost/test-only-callback"
})
*/
@SpringBootTest(properties = {"kakao.client-id=test-client", "kakao.client-secret=test-secret", "kakao.redirect-uri=http://localhost/test-kakao"})
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
    com.employment.job.service.EmploymentReferrals referrals;

    @Autowired
    com.employment.job.service.EmploymentInterviewProposals proposals;

    @Autowired
    com.employment.job.service.EmploymentOnboarding onboarding;

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
        long linkId = number(service.requestLink(seeker, fields("preferredIndustry","IT·정보통신","preferredOccupation","개발자")), "employmentLinkId");
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
        long link = number(service.requestLink(seeker, fields("preferredIndustry","IT·정보통신","preferredOccupation","개발자")), "employmentLinkId");
        mvc.perform(get("/api/employment-links/" + link).cookie(login(seeker)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REQUESTED"))
            .andExpect(jsonPath("$.managerName").isEmpty());
        db.update("UPDATE employment_links SET status='MATCHING',manager_user_id=?,company_id=?,job_id=?,public_message=?,memo=? WHERE employment_link_id=?",
            admin, company, job, "기업 소개를 확인해 주세요.", "비공개 상담 메모", link);
        referrals.send(link);
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
        long link = number(service.requestLink(seeker, fields("preferredIndustry","IT·정보통신","preferredOccupation","개발자")), "employmentLinkId");
        db.update("UPDATE employment_links SET status='MATCHING',company_id=?,job_id=?,manager_user_id=?,memo=?,public_message=? WHERE employment_link_id=?",
            company, job, admin, "관리자 비공개 메모", "구직자 전용 안내", link);
        long unassigned = db.insert("employment_links", fields("job_seeker_id", service.seeker(other)));
        referrals.send(link);
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
        long link = number(service.requestLink(seeker, fields("preferredIndustry","IT·정보통신","preferredOccupation","개발자")), "employmentLinkId");
        db.update("UPDATE employment_links SET status='MATCHING',company_id=?,job_id=?,manager_user_id=? WHERE employment_link_id=?",company,job,admin,link);
        db.update("UPDATE resumes SET is_public=FALSE WHERE resume_id=?",resume);
        assertThatThrownBy(() -> referrals.send(link)).isInstanceOf(Problem.class);
        db.update("UPDATE resumes SET is_public=TRUE WHERE resume_id=?",resume);
        long referral = number(referrals.send(link),"referralId");
        mvc.perform(get("/api/company/employment-referrals/"+referral).cookie(login(companyUser)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.resume.resumeId").value(resume))
            .andExpect(jsonPath("$.memo").doesNotExist()).andExpect(jsonPath("$.resume.attachment").doesNotExist());
        mvc.perform(get("/api/company/employment-referrals/"+referral).cookie(login(seeker))).andExpect(status().isForbidden());
        var review = fields("availableTimes","평일 오후","reviewStatus","INTERVIEW_REQUESTED","opinion","면접 희망");
        mvc.perform(patch("/api/company/employment-referrals/"+referral+"/response").cookie(login(companyUser)).with(csrf()).contentType("application/json").content(json.writeValueAsString(review)))
            .andExpect(status().isOk());
        mvc.perform(get("/api/admin/employment-links/"+link+"/referrals").cookie(login(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].reviewStatus").value("INTERVIEW_REQUESTED"));
    }

    @Test
    void employmentApplicationRequiresCurrentlyPublicResume() throws Exception {
        Cookie session = login(seeker);
        db.update("UPDATE resumes SET is_public=FALSE WHERE resume_id=?", resume);
        mvc.perform(get("/api/employment-links/eligibility").cookie(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.hasPublicResume").value(false));
        mvc.perform(post("/api/employment-links").cookie(session).with(csrf()).contentType("application/json").content("{\"preferredIndustry\":\"IT\",\"preferredOccupation\":\"개발자\"}"))
            .andExpect(status().isConflict());
        assertThat(service.links(seeker)).isEmpty();
        mvc.perform(post("/api/employment-links").cookie(login(other)).with(csrf()).contentType("application/json").content("{\"preferredIndustry\":\"IT\",\"preferredOccupation\":\"개발자\"}"))
            .andExpect(status().isConflict());
        db.update("UPDATE resumes SET is_public=TRUE WHERE resume_id=?", resume);
        mvc.perform(get("/api/employment-links/eligibility").cookie(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.hasPublicResume").value(true));
        db.update("UPDATE resumes SET is_public=FALSE WHERE resume_id=?", resume);
        mvc.perform(post("/api/employment-links").cookie(session).with(csrf()).contentType("application/json").content("{\"preferredIndustry\":\"IT\",\"preferredOccupation\":\"개발자\"}"))
            .andExpect(status().isConflict());
        db.update("UPDATE resumes SET is_public=TRUE WHERE resume_id=?", resume);
        mvc.perform(post("/api/employment-links").cookie(session).with(csrf()).contentType("application/json").content("{\"preferredIndustry\":\"IT\",\"preferredOccupation\":\"개발자\"}"))
            .andExpect(status().isCreated());
        assertThat(service.links(seeker)).hasSize(1);
    }

    @Test
    void companyReviewIsFinalAfterFirstSubmission() {
        long link = number(service.requestLink(seeker, fields("preferredIndustry","IT·정보통신","preferredOccupation","개발자")),"employmentLinkId");
        db.update("UPDATE employment_links SET status='MATCHING',company_id=?,job_id=?,manager_user_id=? WHERE employment_link_id=?",company,job,admin,link);
        long referral = number(referrals.send(link),"referralId");
        var response = fields("availableTimes","평일 오후","reviewStatus","INTERVIEW_REQUESTED","opinion","면접 희망");
        referrals.respond(companyUser,referral,response);
        referrals.respond(companyUser,referral,response);
        assertThatThrownBy(() -> referrals.respond(companyUser,referral,fields("availableTimes","평일 오후","reviewStatus","INTERVIEW_REQUESTED","opinion","의견 변경"))).isInstanceOf(Problem.class);
        assertThat(referrals.companyDetail(companyUser,referral).get("opinion")).isEqualTo("면접 희망");
    }

    @Test
    void referralFlowRequiresSendResponseAndInterviewAndPreservesSnapshot() throws Exception {
        long linkId = number(service.requestLink(seeker, fields("preferredIndustry","IT·정보통신","preferredOccupation","개발자")), "employmentLinkId");
        db.update("UPDATE employment_links SET status='MATCHING',company_id=?,job_id=?,manager_user_id=? WHERE employment_link_id=?",company,job,admin,linkId);
        assertThat(referrals.companyList(companyUser)).isEmpty();
        assertThat(service.linkDetail(seeker,linkId).get("companyName")).isNull();
        long referral = number(referrals.send(linkId),"referralId");
        assertThat(referrals.companyList(companyUser)).anySatisfy(row -> assertThat(number(row,"referralId")).isEqualTo(referral));
        assertThatThrownBy(() -> referrals.send(linkId)).isInstanceOf(Problem.class);
        db.update("UPDATE resumes SET title=?,is_public=FALSE WHERE resume_id=?","수정 후 이력서",resume);
        var snapshot = (com.fasterxml.jackson.databind.JsonNode)referrals.companyDetail(companyUser,referral).get("resume");
        assertThat(snapshot.get("title").asText()).isEqualTo("원본 이력서");
        var link = db.one("SELECT * FROM employment_links WHERE employment_link_id=?",linkId);
        var schedule = fields("interviewAt",java.time.OffsetDateTime.now().plusDays(3).toString(),"interviewLocation","서울 면접실");
        assertThatThrownBy(() -> referrals.validateAdminUpdate(link,"INTERVIEW",company,job,schedule)).isInstanceOf(Problem.class);
        var response = fields("availableTimes","평일 오후","reviewStatus","INTERVIEW_REQUESTED","opinion","면접 희망");
        referrals.respond(companyUser,referral,response);
        referrals.respond(companyUser,referral,response);
        assertThatThrownBy(() -> referrals.respond(companyUser,referral,fields("reviewStatus","HIRE_REQUESTED","opinion","채용 희망"))).isInstanceOf(Problem.class);
        referrals.userParticipation(seeker,referral,fields("response","ACCEPTED","availableTimes","평일 오후","requests",""));
        referrals.validateAdminUpdate(link,"INTERVIEW",company,job,schedule);
        db.update("UPDATE employment_links SET status='INTERVIEW' WHERE employment_link_id=?",linkId);
        assertThat(service.linkDetail(seeker,linkId).get("interviewAt")).isNotNull();
        referrals.respond(companyUser,referral,fields("reviewStatus","HIRE_REQUESTED","opinion","채용 희망"));
        var current = db.one("SELECT * FROM employment_links WHERE employment_link_id=?",linkId);
        assertThatThrownBy(() -> referrals.validateAdminUpdate(current,"EMPLOYED",company,job,fields())).isInstanceOf(Problem.class);
        referrals.validateAdminUpdate(current,"EMPLOYED",company,job,fields("employmentConfirmed",true));
    }

    @Test
    void declinedReferralAllowsRematchKeepsHistoryAndRejectsOldResponses() {
        long linkId = number(service.requestLink(seeker, fields("preferredIndustry","IT·정보통신","preferredOccupation","개발자")),"employmentLinkId");
        db.update("UPDATE employment_links SET status='MATCHING',company_id=?,job_id=?,manager_user_id=? WHERE employment_link_id=?",company,job,admin,linkId);
        long first = number(referrals.send(linkId),"referralId");
        assertThatThrownBy(() -> referrals.respond(companyUser,first,fields("reviewStatus","DECLINED","opinion",""))).isInstanceOf(Problem.class);
        referrals.respond(companyUser,first,fields("reviewStatus","DECLINED","opinion","모집 조건 불일치"));
        assertThat(service.linkDetail(seeker,linkId).get("companyName")).isNull();
        assertThat(db.one("SELECT status FROM employment_links WHERE employment_link_id=?",linkId).get("status")).isEqualTo("MATCHING");
        long second = number(referrals.send(linkId),"referralId");
        assertThat(referrals.history(linkId)).hasSize(2);
        assertThatThrownBy(() -> referrals.respond(companyUser,first,fields("availableTimes","평일 오후","reviewStatus","INTERVIEW_REQUESTED"))).isInstanceOf(Problem.class);
        referrals.cancel(linkId);
        assertThat(referrals.companyDetail(companyUser,second).get("reviewStatus")).isEqualTo("CANCELED");
        assertThatThrownBy(() -> referrals.respond(companyUser,second,fields("availableTimes","평일 오후","reviewStatus","INTERVIEW_REQUESTED"))).isInstanceOf(Problem.class);
        assertThatThrownBy(() -> referrals.companyDetail(other,second)).isInstanceOf(Problem.class);
    }

    @Test
    void administratorCannotSkipCompanyResponseOrInterviewConfirmation() throws Exception {
        long link = number(service.requestLink(seeker, fields("preferredIndustry","IT·정보통신","preferredOccupation","개발자")),"employmentLinkId");
        db.update("UPDATE employment_links SET status='MATCHING',company_id=?,job_id=?,manager_user_id=? WHERE employment_link_id=?",company,job,admin,link);
        Cookie session = login(admin);
        mvc.perform(post("/api/admin/employment-links/"+link+"/referrals").cookie(session).with(csrf())).andExpect(status().isOk());
        long referral = number(referrals.latest(link),"referralId");
        var update = fields("status","INTERVIEW","companyId",company,"jobId",job,"managerUserId",admin);
        mvc.perform(patch("/api/admin/employment-links/"+link).cookie(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(update))).andExpect(status().isConflict());
        referrals.respond(companyUser,referral,fields("availableTimes","평일 오후","reviewStatus","INTERVIEW_REQUESTED","opinion","면접 희망"));
        mvc.perform(patch("/api/admin/employment-links/"+link).cookie(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(update))).andExpect(status().isConflict());
        referrals.userParticipation(seeker,referral,fields("response","ACCEPTED","availableTimes","평일 오후","requests",""));
        mvc.perform(patch("/api/admin/employment-links/"+link).cookie(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(update))).andExpect(status().isBadRequest());
        update.put("interviewAt",java.time.OffsetDateTime.now().plusDays(2).toString());
        update.put("interviewLocation","온라인 https://example.com/interview");
        mvc.perform(patch("/api/admin/employment-links/"+link).cookie(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(update))).andExpect(status().isOk());
        update.put("status","EMPLOYED"); update.put("employmentConfirmed",true);
        mvc.perform(patch("/api/admin/employment-links/"+link).cookie(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(update))).andExpect(status().isConflict());
        referrals.respond(companyUser,referral,fields("reviewStatus","HIRE_REQUESTED","opinion","채용 희망"));
        mvc.perform(patch("/api/admin/employment-links/"+link).cookie(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(update))).andExpect(status().isOk());
        assertThat(db.one("SELECT status FROM employment_links WHERE employment_link_id=?",link).get("status")).isEqualTo("EMPLOYED");
    }

    @Test
    void administratorCanCancelApplicationAndRecommendationWithPublicReason() throws Exception {
        long link = number(service.requestLink(seeker, fields("preferredIndustry","IT·정보통신","preferredOccupation","개발자")),"employmentLinkId");
        db.update("UPDATE employment_links SET status='MATCHING',company_id=?,job_id=?,manager_user_id=? WHERE employment_link_id=?",company,job,admin,link);
        long referral = number(referrals.send(link),"referralId");
        String path = "/api/admin/employment-links/"+link+"/cancel";
        mvc.perform(post(path).cookie(login(seeker)).with(csrf()).contentType("application/json").content("{\"reason\":\"취소 요청\"}")).andExpect(status().isForbidden());
        mvc.perform(post(path).cookie(login(admin)).with(csrf()).contentType("application/json").content("{\"reason\":\" \"}")).andExpect(status().isBadRequest());
        for (int i=0;i<2;i++) mvc.perform(post(path).cookie(login(admin)).with(csrf()).contentType("application/json").content("{\"reason\":\"사용자와 상담 후 취소\"}")).andExpect(status().isOk());
        var saved = db.one("SELECT * FROM employment_links WHERE employment_link_id=?",link);
        assertThat(saved).containsEntry("status","CLOSED");
        assertThat(saved.get("publicMessage").toString()).contains("사용자와 상담 후 취소");
        assertThat(saved.get("completedAt")).isNotNull();
        assertThat(referrals.companyDetail(companyUser,referral).get("reviewStatus")).isEqualTo("CANCELED");
        assertThat(db.list("SELECT notification_id FROM notifications WHERE user_id=? AND related_id=? AND title=?",seeker,link,"취업연계 신청이 취소되었습니다.")).hasSize(1);
        assertThat(db.list("SELECT notification_id FROM notifications WHERE user_id=? AND related_id=? AND title=?",companyUser,link,"취업연계가 관리자에 의해 취소되었습니다.")).hasSize(1);
    }

    @Test
    void employmentPreferencesAreRequiredAndAdministratorCanMatchWithoutCounseling() throws Exception {
        Cookie seekerSession = login(seeker);
        mvc.perform(post("/api/employment-links").cookie(seekerSession).with(csrf()).contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
        assertThat(service.links(seeker)).isEmpty();
        var preferences = fields("preferredIndustry","IT·정보통신","preferredOccupation","백엔드 개발자");
        var response = mvc.perform(post("/api/employment-links").cookie(seekerSession).with(csrf()).contentType("application/json").content(json.writeValueAsString(preferences)))
            .andExpect(status().isCreated()).andReturn();
        long link = json.readTree(response.getResponse().getContentAsString()).get("employmentLinkId").asLong();
        assertThat(service.linkDetail(seeker,link)).containsEntry("preferredIndustry","IT·정보통신").containsEntry("preferredOccupation","백엔드 개발자");
        mvc.perform(get("/api/admin/employment-links/"+link).cookie(login(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.preferredOccupation").value("백엔드 개발자"));
        var update = fields("status","COUNSELING","companyId",company,"jobId",job,"managerUserId",admin);
        mvc.perform(patch("/api/admin/employment-links/"+link).cookie(login(admin)).with(csrf()).contentType("application/json").content(json.writeValueAsString(update))).andExpect(status().isConflict());
        update.put("status","MATCHING");
        mvc.perform(patch("/api/admin/employment-links/"+link).cookie(login(admin)).with(csrf()).contentType("application/json").content(json.writeValueAsString(update))).andExpect(status().isOk());
        assertThat(number(referrals.send(link),"referralId")).isPositive();
    }

    @Test
    void userConsentWithoutAvailabilityAllowsInterviewConfirmation() throws Exception {
        long link = number(service.requestLink(seeker,fields("preferredIndustry","IT","preferredOccupation","개발자")),"employmentLinkId");
        db.update("UPDATE employment_links SET status='MATCHING',company_id=?,job_id=?,manager_user_id=? WHERE employment_link_id=?",company,job,admin,link);
        long referral = number(referrals.send(link),"referralId");
        assertThatThrownBy(() -> referrals.userParticipation(seeker,referral,fields("response","ACCEPTED","availableTimes","11월 5일 오후"))).isInstanceOf(Problem.class);
        referrals.respond(companyUser,referral,fields("reviewStatus","INTERVIEW_REQUESTED","availableTimes","11월 5일 오후 2~5시","opinion","면접 희망"));
        var linkRecord = db.one("SELECT * FROM employment_links WHERE employment_link_id=?",link);
        var schedule = fields("interviewAt",java.time.OffsetDateTime.now().plusDays(3).toString(),"interviewLocation","서울");
        assertThatThrownBy(() -> referrals.validateAdminUpdate(linkRecord,"INTERVIEW",company,job,schedule)).isInstanceOf(Problem.class).hasMessageContaining("사용자");
        String path = "/api/employment-referrals/"+referral+"/participation";
        var response = fields("response","ACCEPTED","requests","온라인 면접 희망");
        mvc.perform(patch(path).cookie(login(other)).with(csrf()).contentType("application/json").content(json.writeValueAsString(response))).andExpect(status().isNotFound());
        mvc.perform(patch(path).cookie(login(companyUser)).with(csrf()).contentType("application/json").content(json.writeValueAsString(response))).andExpect(status().isForbidden());
        mvc.perform(patch(path).cookie(login(seeker)).with(csrf()).contentType("application/json").content("{\"response\":\"INVALID\"}")).andExpect(status().isBadRequest());
        for(int i=0;i<2;i++) mvc.perform(patch(path).cookie(login(seeker)).with(csrf()).contentType("application/json").content(json.writeValueAsString(response))).andExpect(status().isOk());
        assertThat(service.linkDetail(seeker,link)).containsEntry("userResponse","ACCEPTED").containsEntry("userAvailableTimes","");
        assertThat(referrals.history(link).get(0)).containsEntry("companyAvailableTimes","11월 5일 오후 2~5시").containsEntry("userRequests","온라인 면접 희망");
        referrals.validateAdminUpdate(linkRecord,"INTERVIEW",company,job,schedule);
        assertThat(db.list("SELECT notification_id FROM notifications WHERE user_id=? AND related_id=? AND title=?",admin,link,"사용자의 면접 참여 응답이 도착했습니다.")).hasSize(1);
    }

    @Test
    void userDecliningInterviewOnlyEndsRecommendationAndAllowsRematching() {
        long link = number(service.requestLink(seeker,fields("preferredIndustry","IT","preferredOccupation","개발자")),"employmentLinkId");
        db.update("UPDATE employment_links SET status='MATCHING',company_id=?,job_id=?,manager_user_id=? WHERE employment_link_id=?",company,job,admin,link);
        long referral = number(referrals.send(link),"referralId");
        referrals.respond(companyUser,referral,fields("reviewStatus","INTERVIEW_REQUESTED","availableTimes","평일 오후"));
        assertThatThrownBy(() -> referrals.userParticipation(seeker,referral,fields("response","DECLINED"))).isInstanceOf(Problem.class);
        var response = fields("response","DECLINED","requests","근무지가 맞지 않습니다.","availableTimes","");
        referrals.userParticipation(seeker,referral,response);
        referrals.userParticipation(seeker,referral,response);
        assertThat(service.linkDetail(seeker,link)).containsEntry("status","MATCHING").containsEntry("companyName",null).containsEntry("userResponse","DECLINED");
        assertThat(referrals.companyDetail(companyUser,referral).get("reviewStatus")).isEqualTo("CANCELED");
        long second = number(referrals.send(link),"referralId");
        assertThat(second).isNotEqualTo(referral);
        assertThatThrownBy(() -> referrals.userParticipation(seeker,referral,response)).isInstanceOf(Problem.class);
        assertThat(referrals.history(link)).hasSize(2);
    }

    @Test
    void companyProposalIsConfirmedByUserAndAdministratorCannotChangeSchedule() throws Exception {
        long link=number(service.requestLink(seeker,fields("preferredIndustry","IT","preferredOccupation","개발자")),"employmentLinkId");
        db.update("UPDATE employment_links SET status='MATCHING',company_id=?,job_id=?,manager_user_id=? WHERE employment_link_id=?",company,job,admin,link);
        long referral=number(referrals.send(link),"referralId");
        var body=fields("interviewAt",java.time.OffsetDateTime.now().plusDays(3).toString(),"interviewType","ONLINE","meetingUrl","https://example.com/meet","applicantGuidance","포트폴리오를 준비해 주세요.");
        var result=mvc.perform(post("/api/company/employment-referrals/"+referral+"/proposals").cookie(login(companyUser)).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isOk()).andReturn();
        long proposal=json.readTree(result.getResponse().getContentAsString()).get("proposalId").asLong();
        assertThat(number(proposals.propose(companyUser,referral,body),"proposalId")).isEqualTo(proposal);
        assertThat(service.linkDetail(seeker,link)).containsEntry("applicantGuidance","포트폴리오를 준비해 주세요.").containsEntry("status","MATCHING");
        var update=fields("status","INTERVIEW","companyId",company,"jobId",job,"managerUserId",admin);
        mvc.perform(patch("/api/admin/employment-links/"+link).cookie(login(admin)).with(csrf()).contentType("application/json").content(json.writeValueAsString(update))).andExpect(status().isConflict());
        mvc.perform(patch("/api/employment-interview-proposals/"+proposal+"/response").cookie(login(other)).with(csrf()).contentType("application/json").content("{\"response\":\"ACCEPTED\"}")).andExpect(status().isNotFound());
        for(int i=0;i<2;i++) mvc.perform(patch("/api/employment-interview-proposals/"+proposal+"/response").cookie(login(seeker)).with(csrf()).contentType("application/json").content("{\"response\":\"ACCEPTED\"}")).andExpect(status().isOk());
        assertThat(service.linkDetail(seeker,link)).containsEntry("status","INTERVIEW").containsEntry("proposalResponse","ACCEPTED").containsEntry("interviewLocation","https://example.com/meet");
        update.put("interviewLocation","관리자가 바꾼 장소");
        mvc.perform(patch("/api/admin/employment-links/"+link).cookie(login(admin)).with(csrf()).contentType("application/json").content(json.writeValueAsString(update))).andExpect(status().isConflict());
        referrals.respond(companyUser,referral,fields("reviewStatus","HIRE_REQUESTED","opinion","채용 희망"));
        referrals.respond(companyUser,referral,fields("reviewStatus","HIRE_REQUESTED","opinion","채용 희망"));
        assertThat(service.linkDetail(seeker,link)).containsEntry("status","INTERVIEW");
        assertThat(db.one("SELECT completed_at FROM employment_links WHERE employment_link_id=?",link).get("completedAt")).isNull();
        assertThat(db.list("SELECT notification_id FROM notifications WHERE user_id=? AND related_id=? AND title=?",companyUser,link,"사용자가 면접 제안을 수락해 일정이 확정되었습니다.")).hasSize(1);
        assertThat(db.list("SELECT notification_id FROM notifications WHERE user_id=? AND related_id=? AND title=?",admin,link,"최종 합격 결과가 등록되었습니다. 입사 안내를 확인해 주세요.")).hasSize(1);
    }

    @Test
    void scheduleChangeRequestRequiresNewProposalAndOldProposalCannotBeAccepted() {
        long link=number(service.requestLink(seeker,fields("preferredIndustry","IT","preferredOccupation","개발자")),"employmentLinkId");
        db.update("UPDATE employment_links SET status='MATCHING',company_id=?,job_id=?,manager_user_id=? WHERE employment_link_id=?",company,job,admin,link);
        long referral=number(referrals.send(link),"referralId");
        var body=fields("interviewAt",java.time.OffsetDateTime.now().plusDays(2).toString(),"interviewType","OFFLINE","location","서울 면접실","applicantGuidance","신분증 지참");
        long first=number(proposals.propose(companyUser,referral,body),"proposalId");
        assertThatThrownBy(() -> proposals.respond(seeker,first,fields("response","CHANGE_REQUESTED"))).isInstanceOf(Problem.class);
        proposals.respond(seeker,first,fields("response","CHANGE_REQUESTED","requests","다음 주 오후로 변경 부탁드립니다."));
        body.put("interviewAt",java.time.OffsetDateTime.now().plusDays(5).toString());
        long second=number(proposals.propose(companyUser,referral,body),"proposalId");
        assertThat(second).isNotEqualTo(first);
        assertThatThrownBy(() -> proposals.respond(seeker,first,fields("response","ACCEPTED"))).isInstanceOf(Problem.class);
        proposals.respond(seeker,second,fields("response","DECLINED","requests","참여가 어렵습니다."));
        assertThat(service.linkDetail(seeker,link)).containsEntry("status","MATCHING").containsEntry("companyName",null);
        assertThat(proposals.latest(referral)).containsEntry("proposalResponse","DECLINED");
        assertThat(db.list("SELECT proposal_id FROM employment_interview_proposals WHERE referral_id=?",referral)).hasSize(2);
    }

    @Test
    void companyCanEndRecommendationAfterUserRequestsScheduleChange() {
        long link=number(service.requestLink(seeker,fields("preferredIndustry","IT","preferredOccupation","개발자")),"employmentLinkId");
        db.update("UPDATE employment_links SET status='MATCHING',company_id=?,job_id=?,manager_user_id=? WHERE employment_link_id=?",company,job,admin,link);
        long referral=number(referrals.send(link),"referralId");
        long proposal=number(proposals.propose(companyUser,referral,fields("interviewAt",java.time.OffsetDateTime.now().plusDays(2).toString(),"interviewType","OFFLINE","location","서울")),"proposalId");
        proposals.respond(seeker,proposal,fields("response","CHANGE_REQUESTED","requests","다른 일정을 희망합니다."));
        referrals.respond(companyUser,referral,fields("reviewStatus","DECLINED","opinion","요청한 일정으로 진행이 어렵습니다."));
        assertThat(service.linkDetail(seeker,link)).containsEntry("status","MATCHING").containsEntry("companyName",null);
        assertThatThrownBy(() -> proposals.respond(seeker,proposal,fields("response","ACCEPTED"))).isInstanceOf(Problem.class);
    }

    private long hiredReferralForOnboarding() {
        long link=number(service.requestLink(seeker,fields("preferredIndustry","IT","preferredOccupation","개발자")),"employmentLinkId");
        db.update("UPDATE employment_links SET status='MATCHING',company_id=?,job_id=?,manager_user_id=? WHERE employment_link_id=?",company,job,admin,link);
        long referral=number(referrals.send(link),"referralId");
        long proposal=number(proposals.propose(companyUser,referral,fields("interviewAt",java.time.OffsetDateTime.now().plusDays(1).toString(),"interviewType","OFFLINE","location","서울")),"proposalId");
        proposals.respond(seeker,proposal,fields("response","ACCEPTED"));
        referrals.respond(companyUser,referral,fields("reviewStatus","HIRE_REQUESTED","opinion","최종 합격"));
        return referral;
    }

    @Test
    void hiringIntentRemainsOfferUntilUserConfirmsActualEntry() throws Exception {
        long referral=hiredReferralForOnboarding();
        long link=number(referrals.companyDetail(companyUser,referral),"employmentLinkId");
        assertThat(service.linkDetail(seeker,link)).containsEntry("status","INTERVIEW").containsEntry("referralStatus","HIRE_REQUESTED").containsEntry("completedAt",null);
        var guidance=fields("startAt",java.time.OffsetDateTime.now().plusDays(7).toString(),"firstDayLocation","서울 본사 3층","preparations","신분증","contact","hr@example.com","instructions","담당자에게 연락해 주세요.");
        mvc.perform(put("/api/company/employment-referrals/"+referral+"/onboarding").cookie(login(seeker)).with(csrf()).contentType("application/json").content(json.writeValueAsString(guidance))).andExpect(status().isForbidden());
        onboarding.save(companyUser,referral,guidance);
        assertThat(service.linkDetail(seeker,link)).containsEntry("contact","hr@example.com").containsEntry("firstDayLocation","서울 본사 3층");
        assertThatThrownBy(() -> onboarding.userAction(other,referral,fields("action","ACKNOWLEDGE"))).isInstanceOf(Problem.class);
        onboarding.userAction(seeker,referral,fields("action","ACKNOWLEDGE"));
        assertThatThrownBy(() -> onboarding.userAction(seeker,referral,fields("action","JOINED"))).isInstanceOf(Problem.class);
        db.update("UPDATE employment_onboarding SET start_at=? WHERE referral_id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(3600)),referral);
        onboarding.userAction(seeker,referral,fields("action","JOINED"));
        onboarding.userAction(seeker,referral,fields("action","JOINED"));
        assertThat(service.linkDetail(seeker,link)).containsEntry("status","EMPLOYED");
        assertThat(service.linkDetail(seeker,link).get("joinedAt")).isNotNull();
        assertThat(service.linkDetail(seeker,link).get("completedAt")).isNotNull();
        assertThat(db.list("SELECT notification_id FROM notifications WHERE user_id=? AND related_id=? AND title=?",admin,link,"사용자가 실제 입사를 확인했습니다.")).hasSize(1);
    }

    @Test
    void onboardingHelpCreatesInquiryAndNotJoiningStartsRematch() {
        long referral=hiredReferralForOnboarding();
        long link=number(referrals.companyDetail(companyUser,referral),"employmentLinkId");
        onboarding.userAction(seeker,referral,fields("action","HELP","message","입사 일정 확인이 필요합니다."));
        onboarding.userAction(seeker,referral,fields("action","HELP","message","입사 일정 확인이 필요합니다."));
        assertThat(db.list("SELECT inquiry_id FROM inquiries WHERE user_id=? AND content=?",seeker,"입사 일정 확인이 필요합니다.")).hasSize(1);
        assertThatThrownBy(() -> onboarding.userAction(seeker,referral,fields("action","NOT_JOINED","message",""))).isInstanceOf(Problem.class);
        onboarding.userAction(seeker,referral,fields("action","NOT_JOINED","message","개인 사정으로 입사하지 않습니다."));
        assertThat(service.linkDetail(seeker,link)).containsEntry("status","MATCHING").containsEntry("referralStatus","CANCELED");
        assertThat(service.linkDetail(seeker,link).get("notJoinedAt")).isNotNull();
        assertThat(db.list("SELECT referral_id FROM employment_referrals WHERE employment_link_id=?",link)).hasSize(1);
    }

    @Test
    void editedPublishedJobPersistsAsDraftThroughCompanyApi() throws Exception {
        var body = new LinkedHashMap<>(service.ownJob(companyUser, job, false));
        body.put("title", "수정한 공고 제목");
        body.put("description", "수정된 상세 설명");
        body.put("requirements", "변경된 자격요건");
        body.put("assessmentMinScore", 75);
        body.put("startDate", EmploymentService.today().toString());
        body.put("endDate", EmploymentService.today().plusDays(20).toString());
        var session = login(companyUser);
        mvc.perform(put("/api/company/jobs/" + job).cookie(session).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.title").value("수정한 공고 제목"));
        mvc.perform(get("/api/company/jobs/" + job).cookie(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("수정된 상세 설명"))
            .andExpect(jsonPath("$.requirements").value("변경된 자격요건"))
            .andExpect(jsonPath("$.assessmentMinScore").value(75));
        // A validation failure must leave the saved contents intact.
        body.put("title", "저장되면 안 되는 변경");
        body.put("endDate", EmploymentService.today().minusDays(1).toString());
        mvc.perform(put("/api/company/jobs/" + job).cookie(session).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isBadRequest());
        assertThat(service.ownJob(companyUser, job, false).get("title")).isEqualTo("수정한 공고 제목");
    }

    @Test
    void reviewRequestNotifiesActiveAdminsAndUpdatesDashboard() throws Exception {
        long inactive = signup("inactive" + UUID.randomUUID().toString().substring(0, 8), false);
        db.update("UPDATE users SET role='ADMIN',status='SUSPENDED' WHERE user_id=?", inactive);
        db.update("UPDATE job_postings SET status='DRAFT' WHERE job_id=?", job);
        long before = number(db.one("SELECT COUNT(*) AS n FROM job_postings WHERE status='PENDING'"), "n");
        var session = login(companyUser);
        mvc.perform(post("/api/company/jobs/" + job + "/review-request").cookie(session).with(csrf()))
            .andExpect(status().isOk());
        var notices = db.list("SELECT * FROM notifications WHERE notification_type='JOB_REVIEW' AND related_id=?", job);
        assertThat(notices).isNotEmpty();
        assertThat(notices.stream().map(row -> number(row, "userId")).toList()).contains(admin).doesNotContain(inactive, companyUser, seeker);
        assertThat(db.list("SELECT * FROM notifications WHERE user_id=? AND notification_type='JOB_REVIEW' AND related_id=?", admin, job)).hasSize(1);
        mvc.perform(get("/api/notifications").cookie(login(admin))).andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.relatedId == " + job + " && @.notificationType == 'JOB_REVIEW')]").isNotEmpty());
        mvc.perform(get("/api/admin/dashboard").cookie(login(admin))).andExpect(status().isOk())
            .andExpect(jsonPath("$.jobs").value(before + 1));
        mvc.perform(post("/api/company/jobs/" + job + "/review-request").cookie(session).with(csrf()))
            .andExpect(status().isConflict());
        assertThat(db.list("SELECT * FROM notifications WHERE user_id=? AND notification_type='JOB_REVIEW' AND related_id=?", admin, job)).hasSize(1);
    }

    @Test
    void expiredPublishedJobsCloseAutomaticallyAndPreserveOtherStates() {
        var expiration = new com.employment.job.service.JobExpirationService(db);
        var today = EmploymentService.today();
        db.update("UPDATE job_postings SET start_date=?,end_date=?,updated_at=? WHERE job_id=?", today.minusDays(10), today.minusDays(1), java.sql.Timestamp.valueOf("2020-01-01 00:00:00"), job);
        expiration.closeExpiredJobs();
        var closed = db.one("SELECT status,updated_at FROM job_postings WHERE job_id=?", job);
        assertThat(closed.get("status")).isEqualTo("CLOSED");
        assertThat(java.time.Instant.parse(closed.get("updatedAt").toString())).isAfter(java.time.Instant.parse("2020-01-02T00:00:00Z"));
        expiration.closeExpiredJobs();
        assertThat(db.one("SELECT updated_at FROM job_postings WHERE job_id=?", job).get("updatedAt")).isEqualTo(closed.get("updatedAt"));
        for (var day : List.of(today, today.plusDays(1))) {
            db.update("UPDATE job_postings SET status='PUBLISHED',end_date=? WHERE job_id=?", day, job);
            expiration.closeExpiredJobs();
            assertThat(db.one("SELECT status FROM job_postings WHERE job_id=?", job).get("status")).isEqualTo("PUBLISHED");
        }
        for (var status : List.of("DRAFT", "PENDING", "REJECTED", "CLOSED")) {
            db.update("UPDATE job_postings SET status=?,end_date=? WHERE job_id=?", status, today.minusDays(1), job);
            expiration.closeExpiredJobs();
            assertThat(db.one("SELECT status FROM job_postings WHERE job_id=?", job).get("status")).isEqualTo(status);
        }
    }

    @Autowired
    com.employment.admin.HomePromotionService homePromotions;

    private Map<String,Object> promotion(String key) {
        return new LinkedHashMap<>(homePromotions.list(true).stream().filter(row -> key.equals(row.get("key"))).findFirst().orElseThrow());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void homePromotionsPersistTextOrderAndVisibilityWithAdminOnlyAccess() throws Exception {
        mvc.perform(get("/api/home-promotions")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(6));
        mvc.perform(get("/api/admin/home-promotions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/home-promotions").cookie(login(seeker))).andExpect(status().isForbidden());
        var body=promotion("team");
        body.put("label","관리자가 바꾼 배너 이름");body.put("imageAlt","새 배너 안내 문구");body.put("sortOrder",1);
        var session=login(admin);
        mvc.perform(put("/api/admin/home-promotions/team").cookie(login(companyUser)).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/home-promotions/team").cookie(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        var first=promotion("career");first.put("sortOrder",99);homePromotions.save("career",first);
        mvc.perform(get("/api/home-promotions")).andExpect(status().isOk()).andExpect(jsonPath("$[0].key").value("team")).andExpect(jsonPath("$[0].label").value("관리자가 바꾼 배너 이름")).andExpect(jsonPath("$[0].imageAlt").value("새 배너 안내 문구"));
        // A second editor cannot overwrite the saved version with an old form.
        mvc.perform(put("/api/admin/home-promotions/team").cookie(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isConflict());
        var saved=promotion("team");saved.put("isPublished",false);homePromotions.save("team",saved);
        assertThat(homePromotions.list(false).stream().map(row->row.get("key"))).doesNotContain("team");
        assertThat(promotion("team").get("label")).isEqualTo("관리자가 바꾼 배너 이름");
        for(var row:homePromotions.list(true)){row.put("isPublished",false);homePromotions.save(row.get("key").toString(),row);}
        mvc.perform(get("/api/home-promotions")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void homePromotionImagesStayPrivateWhenHiddenAndSurviveTextEdits() throws Exception {
        var image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",out);
        var body=promotion("resume");body.put("imageUrl","data:image/png;base64,"+Base64.getEncoder().encodeToString(out.toByteArray()));
        var saved=homePromotions.save("resume",body);
        assertThat(saved.get("imageUrl").toString()).startsWith("/api/admin/home-promotions/resume/image?v=");
        assertThat(json.writeValueAsString(homePromotions.list(false))).doesNotContain("base64");
        mvc.perform(get("/api/home-promotions/resume/image")).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(content().bytes(out.toByteArray()));
        saved.put("imageAlt","이미지는 유지하고 설명만 변경");saved=homePromotions.save("resume",saved);
        mvc.perform(get("/api/home-promotions/resume/image")).andExpect(status().isOk()).andExpect(content().bytes(out.toByteArray()));
        saved.put("isPublished",false);homePromotions.save("resume",saved);
        mvc.perform(get("/api/home-promotions/resume/image")).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/home-promotions/resume/image").cookie(login(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/admin/home-promotions/resume/image").cookie(login(companyUser))).andExpect(status().isForbidden());
        saved=promotion("resume");saved.put("imageUrl","/images/home-banners-v1/resume.png");homePromotions.save("resume",saved);
        mvc.perform(get("/api/admin/home-promotions/resume/image").cookie(login(admin))).andExpect(status().isNotFound());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void homePromotionsRejectUnsafeLinksInvalidImagesAndInvalidOrdering() throws Exception {
        var session=login(admin);
        for(var link:List.of("javascript:alert(1)","https://example.com","//example.com","/\\example.com","/admin/jobs")){
            var body=promotion("career");body.put("to",link);
            mvc.perform(put("/api/admin/home-promotions/career").cookie(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
        }
        var invalid=promotion("career");invalid.put("imageUrl","data:image/png;base64,bm90LWFuLWltYWdl");
        mvc.perform(put("/api/admin/home-promotions/career").cookie(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(invalid))).andExpect(status().isBadRequest());
        invalid=promotion("career");invalid.put("sortOrder",0);
        mvc.perform(put("/api/admin/home-promotions/career").cookie(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(invalid))).andExpect(status().isBadRequest());
        invalid=promotion("career");invalid.put("imageAlt","");
        mvc.perform(put("/api/admin/home-promotions/career").cookie(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(invalid))).andExpect(status().isBadRequest());
        assertThat(promotion("career").get("version")).isEqualTo(0);
    }

    @Test
    void adminCanAddAndDeletePromotionsWithVersionAndRoleChecks() throws Exception {
        var body=promotion("team");body.put("label","추가한 홍보");body.put("isPublished",false);body.put("sortOrder",7);
        long before=homePromotions.list(true).size();
        mvc.perform(post("/api/admin/home-promotions").cookie(login(companyUser)).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isForbidden());
        var session=login(admin);
        String response=mvc.perform(post("/api/admin/home-promotions").cookie(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String key=json.readTree(response).path("key").asText();
        assertThat(key).startsWith("promo-");assertThat(homePromotions.list(true)).hasSize((int)before+1);
        assertThat(homePromotions.list(false).stream().map(row->row.get("key"))).doesNotContain(key);
        var saved=promotion(key);int oldVersion=((Number)saved.get("version")).intValue();saved.put("isPublished",true);saved=homePromotions.save(key,saved);
        assertThat(homePromotions.list(false).stream().map(row->row.get("key"))).contains(key);
        mvc.perform(delete("/api/admin/home-promotions/"+key).cookie(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("version",oldVersion)))).andExpect(status().isConflict());
        mvc.perform(delete("/api/admin/home-promotions/"+key).cookie(login(seeker)).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("version",saved.get("version"))))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/home-promotions/"+key).cookie(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("version",saved.get("version"))))).andExpect(status().isNoContent());
        assertThat(homePromotions.list(true)).hasSize((int)before);
        var invalid=promotion("team");invalid.put("to","javascript:alert(1)");
        mvc.perform(post("/api/admin/home-promotions").cookie(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(invalid))).andExpect(status().isBadRequest());
        assertThat(homePromotions.list(true)).hasSize((int)before);
    }

    @Test
    void deletedDefaultPromotionDoesNotReturnWhenInitializationRunsAgain() throws Exception {
        try {
            homePromotions.delete("career",promotion("career"));
            homePromotions.initialize();
            assertThat(homePromotions.list(true).stream().map(row->row.get("key"))).doesNotContain("career");
            assertThat(homePromotions.list(false).stream().map(row->row.get("key"))).doesNotContain("career");
        } finally {
            db.update("DELETE FROM home_promotion_deletions WHERE promotion_key='career'");
            homePromotions.initialize();
        }
    }

    @Test
    void completedBannerMigrationPreservesCustomizedDesignsAndVisibility() throws Exception {
        var original=promotion("team");
        var defaults=json.readValue(new org.springframework.core.io.ClassPathResource("db/home-promotion-legacy-defaults.json").getInputStream(),new com.fasterxml.jackson.core.type.TypeReference<List<Map<String,Object>>>() {});
        var legacy=new LinkedHashMap<>(defaults.stream().filter(row->"team".equals(row.get("key"))).findFirst().orElseThrow());
        try {
            db.update("UPDATE home_promotions SET content_json=?,sort_order=9,is_published=FALSE WHERE promotion_key='team'",json.writeValueAsString(legacy));
            homePromotions.initialize();
            var converted=promotion("team");
            assertThat(converted.get("presentation")).isEqualTo("banner");
            assertThat(converted.get("imageUrl")).isEqualTo("/images/home-banners-v1/team.png");
            assertThat(converted.get("isPublished")).isEqualTo(false);
            assertThat(converted.get("sortOrder")).isEqualTo(9);
            legacy.put("title","운영 중에 바꿔 둔 제목");
            db.update("UPDATE home_promotions SET content_json=? WHERE promotion_key='team'",json.writeValueAsString(legacy));
            homePromotions.initialize();
            assertThat(promotion("team").get("title")).isEqualTo("운영 중에 바꿔 둔 제목");
            assertThat(promotion("team").get("imageUrl")).isEqualTo(legacy.get("imageUrl"));
        } finally {
            db.update("UPDATE home_promotions SET content_json=?,sort_order=?,is_published=?,version=? WHERE promotion_key='team'",json.writeValueAsString(original),original.get("sortOrder"),original.get("isPublished"),original.get("version"));
        }
    }

    @Test
    void suspensionReasonIsShownOnlyAfterCorrectPasswordAndUnblockingRestoresLogin() throws Exception {
        var adminCookie=login(admin);
        for(long user:List.of(seeker,companyUser)) {
            var previousSession=login(user);
            String reason="반복적인 운영 정책 위반으로 이용이 제한되었습니다.";
            mvc.perform(patch("/api/admin/users/"+user+"/status").cookie(adminCookie).with(csrf()).contentType("application/json").content(json.writeValueAsString(fields("status","SUSPENDED","reason",reason)))).andExpect(status().isOk());
            var payload=fields("loginId",auth.publicUser(user).get("loginId"),"password",password,"memberType",auth.publicUser(user).get("role"));
            mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(json.writeValueAsString(payload))).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("정지된 아이디입니다.\n정지 사유: "+reason)).andExpect(cookie().doesNotExist("accessToken"));
            payload.put("password","WrongPassword123!");
            mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(json.writeValueAsString(payload))).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("아이디 또는 비밀번호를 확인해 주세요."));
            mvc.perform(get("/api/me").cookie(previousSession)).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/admin/users/"+user).cookie(adminCookie)).andExpect(status().isOk()).andExpect(jsonPath("$.suspensionReason").value(reason));
            mvc.perform(patch("/api/admin/users/"+user+"/status").cookie(adminCookie).with(csrf()).contentType("application/json").content("{\"status\":\"ACTIVE\"}")).andExpect(status().isOk());
            payload.put("password",password);
            mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(json.writeValueAsString(payload))).andExpect(status().isOk());
            mvc.perform(get("/api/me").cookie(previousSession)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void suspensionRequiresReasonAndOnlyAdministratorCanApplyIt() throws Exception {
        var session=login(seeker);var actor=login(admin);String path="/api/admin/users/"+seeker+"/status";
        mvc.perform(patch(path).cookie(session).with(csrf()).contentType("application/json").content("{\"status\":\"SUSPENDED\",\"reason\":\"사유\"}")).andExpect(status().isForbidden());
        mvc.perform(patch(path).cookie(actor).with(csrf()).contentType("application/json").content("{\"status\":\"SUSPENDED\"}")).andExpect(status().isBadRequest());
        mvc.perform(patch(path).cookie(actor).with(csrf()).contentType("application/json").content("{\"status\":\"SUSPENDED\",\"reason\":\"   \"}")).andExpect(status().isBadRequest());
        assertThat(auth.publicUser(seeker).get("status")).isEqualTo("ACTIVE");
        mvc.perform(get("/api/me").cookie(session)).andExpect(status().isOk());
        mvc.perform(patch("/api/admin/users/"+admin+"/status").cookie(actor).with(csrf()).contentType("application/json").content("{\"status\":\"SUSPENDED\",\"reason\":\"사유\"}")).andExpect(status().isConflict());
    }

    @Test
    void legacySuspendedAccountWithoutReasonHasSupportGuidance() throws Exception {
        db.update("UPDATE users SET status='SUSPENDED' WHERE user_id=?",other);
        var payload=fields("loginId",auth.publicUser(other).get("loginId"),"password",password,"memberType","JOB_SEEKER");
        var response=mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(json.writeValueAsString(payload))).andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(json.readTree(response).path("message").asText()).contains("정지된 아이디입니다.","등록된 정지 사유가 없습니다.","고객센터");
    }

    @Test
    void publicJobsIncludeCompanyIndustryAndUseTheCompanyProfilePhoto() throws Exception {
        db.update("UPDATE companies SET industry='콘텐츠 디자인' WHERE company_id=?",company);
        var image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"jpg",bytes);
        db.update("INSERT INTO company_images(company_id,image_data) VALUES(?,?)",company,bytes.toByteArray());
        mvc.perform(get("/api/jobs/"+job)).andExpect(status().isOk()).andExpect(jsonPath("$.industry").value("콘텐츠 디자인")).andExpect(jsonPath("$.companyId").value(company));
        mvc.perform(get("/api/companies/"+company+"/image")).andExpect(status().isOk()).andExpect(content().bytes(bytes.toByteArray()));
        assertThat(service.jobs("테스트 채용","","","","latest",0,100).get("content").toString()).contains("industry=콘텐츠 디자인");
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
                fields("title", "원본 이력서", "skills", "Java", "isDefault", true, "isPublic", true, "assessmentAnswers", CORRECT_EXAMPLE_ANSWERS)
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
        service.requestLink(seeker, fields("preferredIndustry","IT·정보통신","preferredOccupation","개발자"));
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
            "applicantGuidance",
            "10분 전 도착해 주세요.\n신분증을 지참해 주세요.",
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
            .andExpect(jsonPath("$.interviews[0].applicantGuidance").value(body.get("applicantGuidance")))
            .andExpect(jsonPath("$.interviews[0].memo").doesNotExist());
        long interviewId = number(db.one("SELECT interview_id FROM interviews WHERE application_id=?", app), "interviewId");
        var updated = new LinkedHashMap<>(body);
        updated.put("applicantGuidance", "신분증과 포트폴리오를 준비해 주세요.");
        service.updateInterview(companyUser, interviewId, updated, false);
        mvc.perform(get("/api/applications/" + app).cookie(login(seeker)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.interviews[0].applicantGuidance").value(updated.get("applicantGuidance")))
            .andExpect(jsonPath("$.interviews[0].memo").doesNotExist());
        updated.remove("applicantGuidance");
        service.updateInterview(companyUser, interviewId, updated, false);
        assertThat(db.one("SELECT applicant_guidance FROM interviews WHERE interview_id=?", interviewId))
            .containsEntry("applicantGuidance", "신분증과 포트폴리오를 준비해 주세요.");
        updated.put("applicantGuidance", "가".repeat(2001));
        assertThatThrownBy(() -> service.updateInterview(companyUser, interviewId, updated, false))
            .isInstanceOf(Problem.class);
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
                                service.requestLink(seeker, fields("preferredIndustry","IT·정보통신","preferredOccupation","개발자"));
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
    void resumeCanBeCreatedAndUpdatedWithoutAnyAssessment() {
        var saved=service.saveResume(seeker,null,fields("title","검사 없이 신규 작성"));
        long id=number(saved,"resumeId");
        assertThat(saved.get("assessment")).isNull();
        assertThat(service.saveResume(seeker,id,fields("title","검사 없이 수정")).get("title")).isEqualTo("검사 없이 수정");
        assessments.configure(job,70);
        var app=service.apply(seeker,job,fields("resumeId",id));
        assertThat(app.get("applicationId")).isNotNull();
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
    @Autowired com.employment.job.service.CareerAssessments careerAssessments;

    @Test
    void careerAssessmentScoresOnServerAndKeepsResultsPrivate() throws Exception {
        Cookie session=login(seeker);
        var first=mvc.perform(post("/api/career-assessments").cookie(session).with(csrf())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var started=json.readTree(first);long id=started.path("attemptId").asLong();
        assertThat(started.path("questions").size()).isEqualTo(55);
        assertThat(first).doesNotContain("\"correct\"","explanation","reverse");
        assertThat(number(careerAssessments.start(seeker),"attemptId")).isEqualTo(id);
        mvc.perform(get("/api/career-assessments/"+id).cookie(login(other))).andExpect(status().isNotFound());
        mvc.perform(get("/api/career-assessments").cookie(login(companyUser))).andExpect(status().isForbidden());
        var bank=json.readTree(new org.springframework.core.io.ClassPathResource("db/career-questions-v1.json").getInputStream());
        var answers=new ArrayList<Integer>();for(int i=0;i<55;i++)answers.add(i<40?2:bank.path("questions").get(i).path("correct").asInt());
        var partial=new ArrayList<Integer>(Collections.nCopies(55,null));partial.set(0,4);
        var saved=careerAssessments.save(seeker,id,fields("revision",0,"answers",partial),false);
        assertThat(((List<?>)careerAssessments.get(seeker,id).get("answers")).get(0)).isEqualTo(4);
        assertThatThrownBy(()->careerAssessments.save(seeker,id,fields("revision",0,"answers",answers),false)).isInstanceOf(Problem.class).hasMessageContaining("변경");
        var invalid=new ArrayList<Object>(answers);invalid.set(0,0.5);
        assertThatThrownBy(()->careerAssessments.save(seeker,id,fields("revision",saved.get("revision"),"answers",invalid),false)).isInstanceOf(Problem.class);
        var finished=careerAssessments.save(seeker,id,fields("revision",saved.get("revision"),"answers",answers,"score",0),true);
        var report=json.valueToTree(finished.get("report"));
        assertThat(report.path("aptitudeScore").asInt()).isEqualTo(100);
        assertThat(report.path("traits").size()).isEqualTo(5);
        assertThat(report.path("traits").get(0).path("score").asInt()).isEqualTo(50);
        assertThat(finished.get("status")).isEqualTo("COMPLETED");
        assertThat(careerAssessments.save(seeker,id,fields("revision",0,"answers",Collections.nCopies(55,null)),true).get("report")).isEqualTo(finished.get("report"));
        assertThat(number(careerAssessments.start(seeker),"attemptId")).isNotEqualTo(id);
        assertThat(careerAssessments.history(seeker)).hasSize(2);
    }

    @Test
    void expiredCareerAssessmentUsesOnlySavedResponsesAndIncompleteSubmissionFails() {
        var start=careerAssessments.start(seeker);long id=number(start,"attemptId");
        var answers=new ArrayList<Integer>(Collections.nCopies(55,null));answers.set(0,4);
        assertThatThrownBy(()->careerAssessments.save(seeker,id,fields("revision",0,"answers",answers),true)).isInstanceOf(Problem.class).hasMessageContaining("모든 문항");
        careerAssessments.save(seeker,id,fields("revision",0,"answers",answers),false);
        db.update("UPDATE career_assessment_attempts SET deadline_at=? WHERE attempt_id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(2)),id);
        var late=careerAssessments.save(seeker,id,fields("revision",1,"answers",Collections.nCopies(55,0),"score",100),true);
        var report=json.valueToTree(late.get("report"));
        assertThat(report.path("timedOut").asBoolean()).isTrue();
        assertThat(report.path("answeredCount").asInt()).isEqualTo(1);
        assertThat(report.path("aptitudeScore").asInt()).isZero();
        assertThat(careerAssessments.history(other)).isEmpty();
    }

    @Autowired com.employment.job.service.CareerExplorations careerExplorations;

    private Map<String,Object> finishCareerAssessment(long user,int personalityAnswer,boolean correct) throws Exception {
        var started=careerAssessments.start(user);long id=number(started,"attemptId");
        var bank=json.readTree(new org.springframework.core.io.ClassPathResource("db/career-questions-v1.json").getInputStream());
        var answers=new ArrayList<Integer>();
        for(int i=0;i<55;i++)answers.add(i<40?personalityAnswer:(bank.path("questions").get(i).path("correct").asInt()+(correct?0:1))%4);
        return careerAssessments.save(user,id,fields("revision",started.get("revision"),"answers",answers),true);
    }

    @Test
    void applicationsAutomaticallyAttachLatestFullAssessmentAndFreezeIt() throws Exception {
        assertThat(careerAssessments.latestSubmission(seeker)).isNull();
        var first=finishCareerAssessment(seeker,2,true);
        // Even when a new attempt times out incomplete, the last complete result is selected.
        var incomplete=careerAssessments.start(seeker);long partial=number(incomplete,"attemptId");
        db.update("UPDATE career_assessment_attempts SET deadline_at=? WHERE attempt_id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)),partial);
        careerAssessments.get(seeker,partial);
        var exploration=careerExplorations.start(seeker);
        careerExplorations.save(seeker,number(exploration,"attemptId"),fields("revision",0,"answers",Collections.nCopies(36,4)),true);
        var preview=mvc.perform(get("/api/career-assessments/submission").cookie(login(seeker))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(preview).path("assessment").path("attemptId").asLong()).isEqualTo(number(first,"attemptId"));
        var application=service.apply(seeker,job,fields("resumeId",resume,"careerAssessment",fields("score",999),"assessmentId",partial));
        long app=number(application,"applicationId");
        var snapshot=json.valueToTree(application.get("snapshot"));
        assertThat(snapshot.path("careerAssessment").path("report").path("aptitudeScore").asInt()).isEqualTo(100);
        assertThat(snapshot.path("careerAssessment").path("attemptId").asLong()).isEqualTo(number(first,"attemptId"));
        assertThat(snapshot.path("careerAssessment").toString()).doesNotContain("answers","review","explanation");
        assertThat(snapshot.has("exploration")).isFalse();
        var before=json.valueToTree(service.companyApplication(companyUser,app).get("snapshot")).path("careerAssessment");
        finishCareerAssessment(seeker,4,false);
        var after=json.valueToTree(service.companyApplication(companyUser,app).get("snapshot")).path("careerAssessment");
        assertThat(after).isEqualTo(before);
        assertThat(careerAssessments.latestSubmission(seeker).get("attemptId")).isNotEqualTo(first.get("attemptId"));
        long foreignCompany=signup("foreign"+UUID.randomUUID().toString().substring(0,8),true);
        db.update("UPDATE companies SET approval_status='APPROVED' WHERE user_id=?",foreignCompany);
        mvc.perform(get("/api/company/applications/"+app).cookie(login(foreignCompany))).andExpect(status().isNotFound());
        mvc.perform(get("/api/applications/"+app).cookie(login(other))).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/career-assessments/submission").cookie(login(companyUser))).andExpect(status().isForbidden());
        var owned=mvc.perform(get("/api/company/applications/"+app).cookie(login(companyUser))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(owned).path("snapshot").path("careerAssessment")).isEqualTo(before);
    }

    @Test
    void careerExplorationHasThirtySixQuestionsAndPrivateScoredResults() throws Exception {
        var started=careerExplorations.start(seeker);long id=number(started,"attemptId");
        assertThat(((List<?>)started.get("questions"))).hasSize(36);
        assertThat(number(careerExplorations.start(seeker),"attemptId")).isEqualTo(id);
        assertThat(started.get("kind")).isEqualTo("exploration");
        var partial=new ArrayList<Integer>(Collections.nCopies(36,null));partial.set(0,4);
        var saved=careerExplorations.save(seeker,id,fields("revision",0,"answers",partial),false);
        assertThat(((List<?>)careerExplorations.get(seeker,id).get("answers")).get(0)).isEqualTo(4);
        assertThatThrownBy(()->careerExplorations.save(seeker,id,fields("revision",0,"answers",partial),false)).isInstanceOf(Problem.class);
        assertThatThrownBy(()->careerExplorations.save(seeker,id,fields("revision",saved.get("revision"),"answers",partial),true)).isInstanceOf(Problem.class);
        var invalid=new ArrayList<Object>(Collections.nCopies(36,2));invalid.set(0,0.5);
        assertThatThrownBy(()->careerExplorations.save(seeker,id,fields("revision",saved.get("revision"),"answers",invalid),false)).isInstanceOf(Problem.class);
        var answers=new ArrayList<Integer>(Collections.nCopies(36,2));
        // First interest and first value are deliberately higher; no client score is accepted.
        for(int i=0;i<24;i+=6)answers.set(i,4);answers.set(24,4);answers.set(30,4);
        var done=careerExplorations.save(seeker,id,fields("revision",saved.get("revision"),"answers",answers,"score",999),true);
        var report=json.valueToTree(done.get("report"));
        assertThat(report.path("interests").size()).isEqualTo(6);
        assertThat(report.path("values").size()).isEqualTo(6);
        assertThat(report.path("interests").get(0).path("score").asInt()).isEqualTo(100);
        assertThat(report.path("interests").get(1).path("score").asInt()).isEqualTo(50);
        assertThat(report.path("values").get(0).path("score").asInt()).isEqualTo(100);
        assertThat(report.path("answeredCount").asInt()).isEqualTo(36);
        assertThat(careerExplorations.get(seeker,id).get("report")).isEqualTo(done.get("report"));
        mvc.perform(get("/api/career-explorations/"+id).cookie(login(other))).andExpect(status().isNotFound());
        mvc.perform(get("/api/career-explorations").cookie(login(companyUser))).andExpect(status().isForbidden());
        assertThat(careerAssessments.latestSubmission(seeker)).isNull();
        var application=service.apply(seeker,job,fields("resumeId",resume));
        assertThat(json.valueToTree(application.get("snapshot")).path("careerAssessment").isNull()).isTrue();
    }

    @Test
    void expiredExplorationCannotAcceptLateAnswers() {
        var started=careerExplorations.start(seeker);long id=number(started,"attemptId");
        db.update("UPDATE career_exploration_attempts SET deadline_at=? WHERE attempt_id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(2)),id);
        var result=careerExplorations.save(seeker,id,fields("revision",0,"answers",Collections.nCopies(36,4)),true);
        var report=json.valueToTree(result.get("report"));
        assertThat(report.path("timedOut").asBoolean()).isTrue();
        assertThat(report.path("answeredCount").asInt()).isZero();
        assertThat(report.path("interests").get(0).path("score").isNull()).isTrue();
    }

}
