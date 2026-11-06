package com.employment.job.service;

import static com.employment.global.common.Database.*;
import com.employment.global.common.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EmploymentInterviewProposals {
    static final String CHANGE_REQUEST_NOTICE = "사용자가 면접 일정 변경을 요청했습니다.";
    static final String DECLINED_NOTICE = "사용자가 면접 참여 어려움을 전달했습니다.";
    private final Database db;
    public EmploymentInterviewProposals(Database db) { this.db=db; }
    private Map<String,Object> one(String sql,Object...args) {
        var row=db.one(sql,args);
        if(row==null) throw new Problem(404,"면접 제안 내역을 찾을 수 없습니다.");
        return row;
    }
    private boolean missing(org.springframework.jdbc.BadSqlGrammarException ex) {
        var c=ex.getSQLException();
        return c!=null && ("42S02".equals(c.getSQLState()) || "42P01".equals(c.getSQLState()) || c.getErrorCode()==42102) && Objects.toString(c.getMessage(),"").toLowerCase(Locale.ROOT).contains("employment_interview_proposals");
    }
    public Map<String,Object> latest(long referral) {
        try {
            var rows=db.list("SELECT proposal_id,interview_at AS proposed_interview_at,interview_type AS proposed_interview_type,location AS proposed_location,meeting_url AS proposed_meeting_url,applicant_guidance,proposed_at,user_response AS proposal_response,user_requests AS proposal_requests,responded_at AS proposal_responded_at FROM employment_interview_proposals WHERE referral_id=? ORDER BY proposal_id DESC LIMIT 1",referral);
            return rows.isEmpty() ? null : rows.get(0);
        } catch(org.springframework.jdbc.BadSqlGrammarException ex) { if(missing(ex)) return null; throw ex; }
    }
    public void decorate(Map<String,Object> referral) {
        if(referral.get("referralId")==null) return;
        var proposal=latest(number(referral,"referralId"));
        if(proposal!=null) referral.putAll(proposal);
    }
    private void ready() {
        try { db.list("SELECT proposal_id FROM employment_interview_proposals WHERE 1=0"); }
        catch(org.springframework.jdbc.BadSqlGrammarException ex) { if(missing(ex)) throw new Problem(503,"기업 면접 제안 기능의 DB 설치가 필요합니다. (employment-proposal-upgrade.sql)"); throw ex; }
    }
    public Map<String,Object> propose(long companyUser,long referral,Map<String,Object> body) {
        ready();
        var pointer=one("SELECT employment_link_id FROM employment_referrals WHERE referral_id=?",referral);
        var link=one("SELECT * FROM employment_links WHERE employment_link_id=? FOR UPDATE",pointer.get("employmentLinkId"));
        var r=one("SELECT r.* FROM employment_referrals r JOIN companies c ON c.company_id=r.company_id WHERE r.referral_id=? AND c.user_id=? FOR UPDATE",referral,companyUser);
        var latestReferral=one("SELECT referral_id FROM employment_referrals WHERE employment_link_id=? ORDER BY referral_id DESC LIMIT 1",link.get("employmentLinkId"));
        if(number(latestReferral,"referralId")!=referral || Set.of("CLOSED","EMPLOYED").contains(link.get("status")) || !Set.of("PENDING","INTERVIEW_REQUESTED").contains(r.get("reviewStatus"))) throw new Problem(409,"현재 면접을 제안할 수 없는 추천입니다.");
        var previous=latest(referral);
        Instant at=Input.instant(body,"interviewAt").truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        if(!at.isAfter(Instant.now())) throw new Problem(400,"미래 면접 일시를 선택해 주세요.");
        String type=Input.text(body,"interviewType",20,true);
        if(!Set.of("ONLINE","OFFLINE").contains(type)) throw new Problem(400,"면접 방식을 선택해 주세요.");
        String location=type.equals("OFFLINE") ? Input.text(body,"location",500,true).strip() : "";
        String url=type.equals("ONLINE") ? Input.url(body,"meetingUrl") : "";
        if(type.equals("ONLINE") && url.isBlank()) throw new Problem(400,"온라인 면접 접속 주소를 입력해 주세요.");
        String guidance=Input.text(body,"applicantGuidance",10000,false);
        if(previous!=null && previous.get("proposalResponse")==null) {
            boolean same=at.equals(Instant.parse(previous.get("proposedInterviewAt").toString())) && type.equals(previous.get("proposedInterviewType")) && location.equals(previous.get("proposedLocation")) && url.equals(previous.get("proposedMeetingUrl")) && guidance.equals(previous.get("applicantGuidance"));
            if(same) return fields("proposalId",previous.get("proposalId"));
            throw new Problem(409,"사용자가 제안을 검토 중입니다. 응답 후 다시 제안해 주세요.");
        }
        if(previous!=null && !"CHANGE_REQUESTED".equals(previous.get("proposalResponse"))) throw new Problem(409,"이미 응답이 완료된 제안입니다.");
        long id=db.insert("employment_interview_proposals",fields("referral_id",referral,"interview_at",Timestamp.from(at),"interview_type",type,"location",location,"meeting_url",url,"applicant_guidance",guidance));
        db.update("UPDATE employment_referrals SET review_status='INTERVIEW_REQUESTED',responded_at=CURRENT_TIMESTAMP,interview_at=NULL,interview_location=NULL WHERE referral_id=?",referral);
        db.update("UPDATE employment_links SET status='MATCHING' WHERE employment_link_id=?",link.get("employmentLinkId"));
        notify(number(one("SELECT user_id FROM job_seekers WHERE job_seeker_id=?",link.get("jobSeekerId")),"userId"),number(link,"employmentLinkId"),"기업이 면접 일정을 제안했습니다.","일시·장소와 기업 안내를 확인한 뒤 수락 또는 일정 변경 요청을 선택해 주세요.");
        notifyManagers(link,"기업의 면접 제안이 등록되었습니다.");
        return fields("proposalId",id);
    }
    public void respond(long user,long proposalId,Map<String,Object> body) {
        ready();
        var pointer=one("SELECT r.employment_link_id,p.referral_id FROM employment_interview_proposals p JOIN employment_referrals r ON r.referral_id=p.referral_id WHERE p.proposal_id=?",proposalId);
        var link=one("SELECT e.* FROM employment_links e JOIN job_seekers s ON s.job_seeker_id=e.job_seeker_id WHERE e.employment_link_id=? AND s.user_id=? FOR UPDATE",pointer.get("employmentLinkId"),user);
        var r=one("SELECT * FROM employment_referrals WHERE referral_id=? FOR UPDATE",pointer.get("referralId"));
        var p=one("SELECT * FROM employment_interview_proposals WHERE proposal_id=? FOR UPDATE",proposalId);
        var current=latest(number(r,"referralId"));
        var latestReferral=one("SELECT referral_id FROM employment_referrals WHERE employment_link_id=? ORDER BY referral_id DESC LIMIT 1",link.get("employmentLinkId"));
        if(number(latestReferral,"referralId")!=number(r,"referralId") || number(current,"proposalId")!=proposalId || Set.of("CLOSED","EMPLOYED").contains(link.get("status"))) throw new Problem(409,"현재 응답할 수 없는 면접 제안입니다.");
        String response=Input.text(body,"response",30,true);
        if(!Set.of("ACCEPTED","CHANGE_REQUESTED","DECLINED").contains(response)) throw new Problem(400,"면접 제안 응답을 선택해 주세요.");
        String requests=Input.text(body,"requests",2000,!response.equals("ACCEPTED")).strip();
        if(p.get("userResponse")!=null) {
            if(response.equals(p.get("userResponse")) && requests.equals(p.get("userRequests"))) return;
            throw new Problem(409,"이미 응답한 면접 제안입니다.");
        }
        if(!"INTERVIEW_REQUESTED".equals(r.get("reviewStatus"))) throw new Problem(409,"종료된 면접 제안입니다.");
        if(response.equals("ACCEPTED") && !Instant.parse(p.get("interviewAt").toString()).isAfter(Instant.now())) throw new Problem(409,"면접 일시가 지났습니다. 일정 변경을 요청해 주세요.");
        db.update("UPDATE employment_interview_proposals SET user_response=?,user_requests=?,responded_at=CURRENT_TIMESTAMP WHERE proposal_id=?",response,requests,proposalId);
        if(response.equals("ACCEPTED")) {
            db.update("UPDATE employment_referrals SET interview_at=?,interview_location=? WHERE referral_id=?",Timestamp.from(Instant.parse(p.get("interviewAt").toString())),"ONLINE".equals(p.get("interviewType")) ? p.get("meetingUrl") : p.get("location"),r.get("referralId"));
            db.update("UPDATE employment_links SET status='INTERVIEW' WHERE employment_link_id=?",link.get("employmentLinkId"));
        } else {
            if(response.equals("DECLINED")) db.update("UPDATE employment_referrals SET review_status='CANCELED' WHERE referral_id=?",r.get("referralId"));
            db.update("UPDATE employment_links SET status='MATCHING' WHERE employment_link_id=?",link.get("employmentLinkId"));
        }
        String title=response.equals("ACCEPTED") ? "사용자가 면접 제안을 수락해 일정이 확정되었습니다." : response.equals("CHANGE_REQUESTED") ? CHANGE_REQUEST_NOTICE : DECLINED_NOTICE;
        notify(number(one("SELECT user_id FROM companies WHERE company_id=?",r.get("companyId")),"userId"),number(link,"employmentLinkId"),title,"취업연계 상세에서 응답을 확인해 주세요.");
        notifyManagers(link,title);
        notify(user,number(link,"employmentLinkId"),title,"취업연계 상세에서 현재 진행 상황을 확인해 주세요.");
    }
    private void notifyManagers(Map<String,Object> link,String title) {
        var managers=link.get("managerUserId")==null ? db.list("SELECT user_id FROM users WHERE role='ADMIN' AND status='ACTIVE'") : List.of(fields("userId",link.get("managerUserId")));
        for(var manager:managers) notify(number(manager,"userId"),number(link,"employmentLinkId"),title,"취업연계 관리에서 제안과 사용자 응답을 확인해 주세요.");
    }
    private void notify(long user,long link,String title,String content) { db.insert("notifications",fields("user_id",user,"title",title,"content",content,"notification_type","EMPLOYMENT","related_id",link)); }
}
