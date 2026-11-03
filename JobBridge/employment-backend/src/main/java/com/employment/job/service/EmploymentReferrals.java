package com.employment.job.service;

import static com.employment.global.common.Database.*;
import com.employment.global.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EmploymentReferrals {
    private final Database db;
    private final EmploymentOnboarding onboarding;
    private final EmploymentInterviewProposals proposals;
    private final ObjectMapper json;
    public EmploymentReferrals(Database db, ObjectMapper json, EmploymentInterviewProposals proposals, EmploymentOnboarding onboarding) { this.db = db; this.json = json; this.proposals=proposals; this.onboarding=onboarding; }

    public List<Map<String,Object>> history(long linkId) {
        ensureReady();
        var rows = db.list("SELECT r.referral_id,r.company_id,r.job_id,r.review_status,r.opinion,r.sent_at,r.responded_at,r.interview_at,r.interview_location,c.company_name,j.title AS job_title FROM employment_referrals r JOIN companies c ON c.company_id=r.company_id LEFT JOIN job_postings j ON j.job_id=r.job_id WHERE r.employment_link_id=? ORDER BY r.referral_id DESC", linkId);
        rows.addAll(db.list("SELECT r.company_id,r.review_status,r.opinion,r.updated_at AS responded_at,c.company_name FROM employment_company_reviews r JOIN companies c ON c.company_id=r.company_id WHERE r.employment_link_id=? ORDER BY r.updated_at DESC",linkId));
        rows.forEach(this::decorateCoordination);
        rows.forEach(proposals::decorate);
        rows.forEach(onboarding::decorate);
        return rows;
    }
    public Map<String,Object> latest(long linkId) {
        try {
            var rows = db.list("SELECT referral_id,company_id,job_id,review_status,responded_at AS hired_at,interview_at,interview_location FROM employment_referrals WHERE employment_link_id=? ORDER BY referral_id DESC LIMIT 1",linkId);
            if (rows.isEmpty()) return null;
            var result = rows.get(0);
            decorateCoordination(result);
            proposals.decorate(result);
            onboarding.decorate(result);
            return result;
        } catch (org.springframework.jdbc.BadSqlGrammarException ex) {
            if (missingReferralTable(ex)) return null;
            throw ex;
        }
    }
    private boolean missingReferralTable(org.springframework.jdbc.BadSqlGrammarException ex) {
        var cause = ex.getSQLException();
        return cause != null && ("42S02".equals(cause.getSQLState()) || "42P01".equals(cause.getSQLState()) || cause.getErrorCode() == 42102)
            && Objects.toString(cause.getMessage(),"").toLowerCase(Locale.ROOT).contains("employment_referrals");
    }
    private void ensureReady() {
        try { db.list("SELECT referral_id FROM employment_referrals WHERE 1=0"); }
        catch (org.springframework.jdbc.BadSqlGrammarException ex) {
            if (missingReferralTable(ex)) throw new Problem(503,"취업연계 추천 기능의 DB 설치가 필요합니다. 관리자에게 문의해 주세요. (employment-referral-upgrade.sql)");
            throw ex;
        }
    }
    private boolean live(Map<String,Object> r) { return r != null && !Set.of("DECLINED","CANCELED").contains(r.get("reviewStatus")); }
    private boolean same(Object a, Object b) { return Objects.equals(a == null ? null : a.toString(), b == null ? null : b.toString()); }

    public Map<String,Object> send(long linkId) {
        ensureReady();
        var link = one("SELECT * FROM employment_links WHERE employment_link_id=? FOR UPDATE", linkId);
        if (!"MATCHING".equals(link.get("status"))) throw new Problem(409,"기업 매칭 단계로 변경한 후 추천을 전달해 주세요.");
        if (link.get("companyId") == null || link.get("jobId") == null || link.get("managerUserId") == null) throw new Problem(400,"담당자, 기업과 채용공고를 먼저 저장해 주세요.");
        if (live(latest(linkId))) throw new Problem(409,"이미 검토 중인 추천이 있습니다. 재매칭하려면 현재 추천을 먼저 종료해 주세요.");
        var company = one("SELECT c.user_id FROM companies c JOIN users u ON u.user_id=c.user_id WHERE c.company_id=? AND c.approval_status='APPROVED' AND u.status='ACTIVE'", link.get("companyId"));
        one("SELECT user_id FROM users WHERE user_id=? AND role='ADMIN' AND status='ACTIVE'",link.get("managerUserId"));
        one("SELECT job_id FROM job_postings WHERE job_id=? AND company_id=? AND status='PUBLISHED' AND end_date>=?", link.get("jobId"), link.get("companyId"),EmploymentService.today());
        var resumes = db.list("SELECT resume_id,title,education,career_description,skills,portfolio_url,details_json,updated_at FROM resumes WHERE job_seeker_id=? AND is_public=TRUE ORDER BY is_default DESC,updated_at DESC,resume_id DESC LIMIT 1", link.get("jobSeekerId"));
        if (resumes.isEmpty()) throw new Problem(409,"신청자의 공개 이력서가 없습니다. 공개 설정 후 추천을 전달해 주세요.");
        var resume = resumes.get(0);
        Object details = resume.remove("detailsJson");
        var applicant = one("SELECT u.user_id,u.name FROM users u JOIN job_seekers s ON s.user_id=u.user_id WHERE s.job_seeker_id=?", link.get("jobSeekerId"));
        String snapshot;
        try { resume.put("details", details == null ? null : json.readTree(details.toString())); snapshot = json.writeValueAsString(fields("resume",resume,"name",applicant.get("name"))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
        long id = db.insert("employment_referrals", fields("employment_link_id",linkId,"company_id",link.get("companyId"),"job_id",link.get("jobId"),"resume_snapshot",snapshot));
        db.update("UPDATE employment_links SET status='MATCHING' WHERE employment_link_id=?",linkId);
        notify(number(company,"userId"),linkId,"취업연계 추천이 도착했습니다.","전달된 이력서를 확인하고 면접 희망 여부를 알려 주세요.");
        notify(number(applicant,"userId"),linkId,"기업에 취업연계 추천을 전달했습니다.","기업 검토가 시작되었습니다. 취업연계 상세를 확인해 주세요.");
        return fields("referralId",id);
    }
    public void cancel(long linkId) {
        ensureReady();
        var link = one("SELECT * FROM employment_links WHERE employment_link_id=? FOR UPDATE",linkId);
        if (Set.of("CLOSED","EMPLOYED").contains(link.get("status"))) throw new Problem(409,"종료된 취업연계입니다.");
        var referral = latest(linkId);
        if (live(referral)) {
            db.update("UPDATE employment_referrals SET review_status='CANCELED' WHERE referral_id=?",referral.get("referralId"));
            long companyUser = number(one("SELECT user_id FROM companies WHERE company_id=?",referral.get("companyId")),"userId");
            notify(companyUser,linkId,"취업연계 추천이 종료되었습니다.","관리자가 현재 추천을 종료하고 재매칭을 시작했습니다.");
        }
        db.update("UPDATE employment_links SET status='MATCHING',company_id=NULL,job_id=NULL WHERE employment_link_id=?",linkId);
        notify(number(one("SELECT user_id FROM job_seekers WHERE job_seeker_id=?",link.get("jobSeekerId")),"userId"),linkId,"다른 기업과의 매칭을 준비하고 있습니다.","담당 관리자가 새로운 기업을 검토합니다.");
    }
    public void cancelApplication(long administrator,long linkId,Map<String,Object> body) {
        String reason = Input.text(body,"reason",1000,true).strip();
        var link = one("SELECT * FROM employment_links WHERE employment_link_id=? FOR UPDATE",linkId);
        if ("CLOSED".equals(link.get("status"))) return;
        if ("EMPLOYED".equals(link.get("status"))) throw new Problem(409,"이미 취업 완료된 내역은 취소할 수 없습니다.");
        var referral = latest(linkId);
        if (live(referral)) {
            db.update("UPDATE employment_referrals SET review_status='CANCELED' WHERE referral_id=?",referral.get("referralId"));
            notify(number(one("SELECT user_id FROM companies WHERE company_id=?",referral.get("companyId")),"userId"),linkId,"취업연계가 관리자에 의해 취소되었습니다.","해당 추천은 종료되었습니다. 담당 관리자에게 확인해 주세요.");
        }
        String notice = "관리자가 취업연계를 취소했습니다.\n취소 사유: " + reason;
        String audit = Objects.toString(link.get("memo"),"") + "\n[관리자 취소 · 관리자 #" + administrator + " · " + Instant.now() + "] " + reason;
        db.update("UPDATE employment_links SET status='CLOSED',public_message=?,memo=?,completed_at=CURRENT_TIMESTAMP WHERE employment_link_id=?",notice,audit,linkId);
        notify(number(one("SELECT user_id FROM job_seekers WHERE job_seeker_id=?",link.get("jobSeekerId")),"userId"),linkId,"취업연계 신청이 취소되었습니다.","관리자가 신청을 취소했습니다. 취업연계 상세에서 취소 사유를 확인해 주세요.");
    }

    public void validateAdminUpdate(Map<String,Object> link,String next,Long company,Long job,Map<String,Object> body) {
        if (next.equals("EMPLOYED")) throw new Problem(409,"취업 완료는 사용자의 실제 입사 확인 후 자동으로 처리됩니다.");
        long id = number(link,"employmentLinkId");
        var r = latest(id);
        if (live(r) && (!same(company,r.get("companyId")) || !same(job,r.get("jobId")))) throw new Problem(409,"추천 전달 후 기업·공고 변경은 재매칭 시작으로 처리해 주세요.");
        if (next.equals("INTERVIEW") || next.equals("EMPLOYED")) {
            if (!live(r) || !same(company,r.get("companyId")) || !same(job,r.get("jobId"))) throw new Problem(409,"먼저 기업에 추천을 전달하고 응답을 받아 주세요.");
            if (next.equals("INTERVIEW")) {
                if (!"INTERVIEW".equals(link.get("status")) || !"ACCEPTED".equals(r.get("proposalResponse"))) throw new Problem(409,"기업 면접 제안을 사용자가 수락하면 자동으로 면접이 확정됩니다.");
                if (body.containsKey("interviewAt") || body.containsKey("interviewLocation")) throw new Problem(409,"면접 일시와 장소는 기업이 제안합니다. 관리자 화면에서는 변경할 수 없습니다.");
            } else if (!"HIRE_REQUESTED".equals(r.get("reviewStatus")) || !Boolean.TRUE.equals(body.get("employmentConfirmed"))) throw new Problem(409,"기업의 채용 희망 응답과 입사 확인이 필요합니다.");
        }
        if (next.equals("MATCHING") && "INTERVIEW".equals(link.get("status"))) throw new Problem(409,"면접 진행 중 재매칭은 '재매칭 시작' 버튼으로 처리해 주세요.");
        if (next.equals("CLOSED") && live(r)) db.update("UPDATE employment_referrals SET review_status='CANCELED' WHERE referral_id=?",r.get("referralId"));
    }
    public List<Map<String,Object>> companyList(long user) {
        ensureReady();
        return db.list("SELECT r.referral_id,r.employment_link_id,e.status,r.review_status,r.sent_at AS requested_at,u.name AS applicant_name,m.name AS manager_name,j.title AS job_title,r.job_id FROM employment_referrals r JOIN companies c ON c.company_id=r.company_id JOIN employment_links e ON e.employment_link_id=r.employment_link_id JOIN job_seekers s ON s.job_seeker_id=e.job_seeker_id JOIN users u ON u.user_id=s.user_id LEFT JOIN users m ON m.user_id=e.manager_user_id LEFT JOIN job_postings j ON j.job_id=r.job_id WHERE c.user_id=? ORDER BY r.referral_id DESC",user);
    }
    public Map<String,Object> companyDetail(long user,long id) {
        ensureReady();
        var r = one("SELECT r.*,e.status,m.name AS manager_name,j.title AS job_title FROM employment_referrals r JOIN companies c ON c.company_id=r.company_id JOIN employment_links e ON e.employment_link_id=r.employment_link_id LEFT JOIN users m ON m.user_id=e.manager_user_id LEFT JOIN job_postings j ON j.job_id=r.job_id WHERE r.referral_id=? AND c.user_id=?",id,user);
        try { var snapshot = json.readTree(r.remove("resumeSnapshot").toString()); r.put("resume",snapshot.get("resume")); r.put("applicantName",snapshot.get("name").asText()); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
        r.put("reviewUpdatedAt",r.get("respondedAt"));
        decorateCoordination(r);
        proposals.decorate(r);
        onboarding.decorate(r);
        return r;
    }
    public void respond(long user,long id,Map<String,Object> body) {
        ensureReady();
        var pointer = one("SELECT employment_link_id FROM employment_referrals WHERE referral_id=?",id);
        var link = one("SELECT * FROM employment_links WHERE employment_link_id=? FOR UPDATE",pointer.get("employmentLinkId"));
        var r = one("SELECT r.* FROM employment_referrals r JOIN companies c ON c.company_id=r.company_id WHERE r.referral_id=? AND c.user_id=? FOR UPDATE",id,user);
        if (!same(latest(number(link,"employmentLinkId")).get("referralId"),id)) throw new Problem(409,"현재 진행 중인 추천이 아닙니다.");
        String next = Input.text(body,"reviewStatus",30,true);
        String opinion = Input.text(body,"opinion",10000,next.equals("DECLINED"));
        if (next.equals(r.get("reviewStatus")) && opinion.equals(r.get("opinion"))) return;
        if (Set.of("CLOSED","EMPLOYED").contains(link.get("status"))) throw new Problem(409,"종료된 취업연계입니다.");
        boolean allowed = "PENDING".equals(r.get("reviewStatus")) && Set.of("INTERVIEW_REQUESTED","DECLINED").contains(next)
            || "INTERVIEW_REQUESTED".equals(r.get("reviewStatus")) && (next.equals("DECLINED") || "INTERVIEW".equals(link.get("status")) && next.equals("HIRE_REQUESTED"));
        if (!allowed) throw new Problem(409,"현재 단계에서 가능한 기업 응답을 선택해 주세요.");
        if (next.equals("HIRE_REQUESTED") && !"ACCEPTED".equals(latest(number(link,"employmentLinkId")).get("proposalResponse"))) throw new Problem(409,"사용자의 면접 참여 동의와 면접 일정 확정이 필요합니다.");
        if (next.equals("INTERVIEW_REQUESTED")) throw new Problem(409,"면접 일시·방식·장소를 작성하고 면접 제안 전달을 사용해 주세요.");
        db.update("UPDATE employment_referrals SET review_status=?,opinion=?,responded_at=CURRENT_TIMESTAMP WHERE referral_id=?",next,opinion,id);
        if (next.equals("DECLINED")) db.update("UPDATE employment_links SET status='MATCHING' WHERE employment_link_id=?",link.get("employmentLinkId"));
        if (next.equals("HIRE_REQUESTED")) db.update("UPDATE employment_links SET status='INTERVIEW',completed_at=NULL WHERE employment_link_id=?",link.get("employmentLinkId"));
        var managers = link.get("managerUserId") == null ? db.list("SELECT user_id FROM users WHERE role='ADMIN' AND status='ACTIVE'") : List.of(fields("userId",link.get("managerUserId")));
        String title = next.equals("HIRE_REQUESTED") ? "최종 합격 결과가 등록되었습니다. 입사 안내를 확인해 주세요." : "기업의 취업연계 응답이 도착했습니다.";
        for (var manager:managers) notify(number(manager,"userId"),number(link,"employmentLinkId"),title,"취업연계 관리에서 기업 응답과 최종 상태를 확인해 주세요.");
        notify(number(one("SELECT user_id FROM job_seekers WHERE job_seeker_id=?",link.get("jobSeekerId")),"userId"),number(link,"employmentLinkId"),next.equals("DECLINED") ? "다른 기업과의 매칭을 준비합니다." : title,"취업연계 상세에서 진행 상황과 결과를 확인해 주세요.");
    }
    public void decorateUser(Map<String,Object> row) {
        var r = latest(number(row,"employmentLinkId"));
        row.put("referralStatus",r == null ? null : r.get("reviewStatus"));
        row.put("referralId",r == null ? null : r.get("referralId"));
        if (r != null) for (String key:List.of("userResponse","userAvailableTimes","userRequests","userRespondedAt","companyAvailableTimes","coordinationReady","proposalId","proposedInterviewAt","proposedInterviewType","proposedLocation","proposedMeetingUrl","applicantGuidance","proposedAt","proposalResponse","proposalRequests","proposalRespondedAt","hiredAt","startAt","firstDayLocation","preparations","contact","instructions","onboardingUpdatedAt","acknowledgedAt","joinedAt","notJoinedAt","userMessage","onboardingReady")) row.put(key,r.get(key));
        if (r != null && r.get("notJoinedAt") != null) { row.put("outcomeCompanyName",row.get("companyName")); row.put("outcomeJobTitle",row.get("jobTitle")); }
        if (!live(r)) for (String key:List.of("companyId","companyName","jobId","jobTitle")) row.put(key,null);
        row.put("interviewAt",live(r) ? r.get("interviewAt") : null);
        row.put("interviewLocation",live(r) ? r.get("interviewLocation") : null);
    }
    private boolean missingCoordination(org.springframework.jdbc.BadSqlGrammarException ex) {
        var cause = ex.getSQLException();
        return cause != null && ("42S02".equals(cause.getSQLState()) || "42P01".equals(cause.getSQLState()) || cause.getErrorCode()==42102)
            && Objects.toString(cause.getMessage(),"").toLowerCase(Locale.ROOT).contains("employment_interview_coordination");
    }
    private void decorateCoordination(Map<String,Object> row) {
        if (row.get("referralId") == null) return;
        try {
            var data = db.list("SELECT company_available_times,user_response,user_available_times,user_requests,user_responded_at FROM employment_interview_coordination WHERE referral_id=?",row.get("referralId"));
            if (!data.isEmpty()) row.putAll(data.get(0));
            row.put("coordinationReady",true);
        } catch (org.springframework.jdbc.BadSqlGrammarException ex) {
            if (!missingCoordination(ex)) throw ex;
            row.put("coordinationReady",false);
        }
    }
    private void ensureCoordination() {
        try { db.list("SELECT referral_id FROM employment_interview_coordination WHERE 1=0"); }
        catch (org.springframework.jdbc.BadSqlGrammarException ex) {
            if (missingCoordination(ex)) throw new Problem(503,"면접 참여·일정 조율 기능의 DB 설치가 필요합니다. (employment-coordination-upgrade.sql)");
            throw ex;
        }
    }
    public void userParticipation(long user,long id,Map<String,Object> body) {
        throw new Problem(409,"면접 제안 화면을 새로고침한 뒤 기업이 제안한 일정을 확인하고 응답해 주세요.");
    }
    private Map<String,Object> one(String sql,Object... args) {
        var row = db.one(sql,args);
        if (row == null) throw new Problem(404,"취업연계 정보를 찾을 수 없습니다.");
        return row;
    }
    private void notify(long user,long link,String title,String content) { db.insert("notifications",fields("user_id",user,"title",title,"content",content,"notification_type","EMPLOYMENT","related_id",link)); }
}
