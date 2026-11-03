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
public class EmploymentOnboarding {
    private final Database db;
    public EmploymentOnboarding(Database db) { this.db=db; }
    private Map<String,Object> one(String sql,Object...args) {
        var row=db.one(sql,args); if(row==null) throw new Problem(404,"입사 안내 내역을 찾을 수 없습니다."); return row;
    }
    private boolean missing(org.springframework.jdbc.BadSqlGrammarException ex) {
        var c=ex.getSQLException(); return c!=null && ("42S02".equals(c.getSQLState()) || "42P01".equals(c.getSQLState()) || c.getErrorCode()==42102) && Objects.toString(c.getMessage(),"").toLowerCase(Locale.ROOT).contains("employment_onboarding");
    }
    public void decorate(Map<String,Object> row) {
        if(row.get("referralId")==null) return;
        try {
            var rows=db.list("SELECT start_at,first_day_location,preparations,contact,instructions,updated_at AS onboarding_updated_at,acknowledged_at,joined_at,not_joined_at,user_message FROM employment_onboarding WHERE referral_id=?",row.get("referralId"));
            if(!rows.isEmpty()) row.putAll(rows.get(0)); row.put("onboardingReady",true);
        } catch(org.springframework.jdbc.BadSqlGrammarException ex) { if(!missing(ex)) throw ex; row.put("onboardingReady",false); }
    }
    private void ready() {
        try { db.list("SELECT referral_id FROM employment_onboarding WHERE 1=0"); }
        catch(org.springframework.jdbc.BadSqlGrammarException ex) { if(missing(ex)) throw new Problem(503,"입사 안내 기능의 DB 설치가 필요합니다. (employment-onboarding-upgrade.sql)"); throw ex; }
    }
    private Map<String,Object> lock(long referral) {
        var p=one("SELECT employment_link_id FROM employment_referrals WHERE referral_id=?",referral);
        var link=one("SELECT * FROM employment_links WHERE employment_link_id=? FOR UPDATE",p.get("employmentLinkId"));
        var latest=one("SELECT referral_id FROM employment_referrals WHERE employment_link_id=? ORDER BY referral_id DESC LIMIT 1",p.get("employmentLinkId"));
        if(number(latest,"referralId")!=referral) throw new Problem(409,"현재 진행 중인 추천이 아닙니다."); return link;
    }
    public void save(long companyUser,long referral,Map<String,Object> body) {
        ready(); var link=lock(referral);
        var r=one("SELECT r.* FROM employment_referrals r JOIN companies c ON c.company_id=r.company_id WHERE r.referral_id=? AND c.user_id=? FOR UPDATE",referral,companyUser);
        if(!"HIRE_REQUESTED".equals(r.get("reviewStatus")) || "CLOSED".equals(link.get("status"))) throw new Problem(409,"최종 합격한 추천에 입사 안내를 등록해 주세요.");
        var existing=db.list("SELECT * FROM employment_onboarding WHERE referral_id=?",referral);
        if(!existing.isEmpty() && existing.get(0).get("joinedAt")!=null) throw new Problem(409,"입사 확인이 완료된 안내는 수정할 수 없습니다.");
        Instant start=Input.instant(body,"startAt").truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        String location=Input.text(body,"firstDayLocation",500,true).strip(),preparations=Input.text(body,"preparations",10000,false),contact=Input.text(body,"contact",300,true).strip(),instructions=Input.text(body,"instructions",10000,false);
        if(!existing.isEmpty()) {
            var old=existing.get(0);
            if(old.get("startAt")!=null && start.equals(Instant.parse(old.get("startAt").toString())) && location.equals(old.get("firstDayLocation")) && preparations.equals(old.get("preparations")) && contact.equals(old.get("contact")) && instructions.equals(old.get("instructions"))) return;
        }
        if(existing.isEmpty()) db.update("INSERT INTO employment_onboarding (referral_id,start_at,first_day_location,preparations,contact,instructions,updated_at) VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP)",referral,Timestamp.from(start),location,preparations,contact,instructions);
        else db.update("UPDATE employment_onboarding SET start_at=?,first_day_location=?,preparations=?,contact=?,instructions=?,updated_at=CURRENT_TIMESTAMP,acknowledged_at=NULL WHERE referral_id=?",Timestamp.from(start),location,preparations,contact,instructions,referral);
        notify(number(one("SELECT user_id FROM job_seekers WHERE job_seeker_id=?",link.get("jobSeekerId")),"userId"),number(link,"employmentLinkId"),"기업의 입사 안내가 등록·변경되었습니다.","입사 예정일, 첫 출근 장소와 준비물을 확인해 주세요.");
    }
    public void userAction(long user,long referral,Map<String,Object> body) {
        ready(); var link=lock(referral);
        one("SELECT job_seeker_id FROM job_seekers WHERE job_seeker_id=? AND user_id=?",link.get("jobSeekerId"),user);
        var r=one("SELECT * FROM employment_referrals WHERE referral_id=? FOR UPDATE",referral);
        if(!"HIRE_REQUESTED".equals(r.get("reviewStatus")) || "CLOSED".equals(link.get("status"))) throw new Problem(409,"입사 결과를 처리할 수 없는 추천입니다.");
        String action=Input.text(body,"action",30,true);
        if(!Set.of("ACKNOWLEDGE","JOINED","NOT_JOINED","HELP").contains(action)) throw new Problem(400,"처리할 항목을 선택해 주세요.");
        var rows=db.list("SELECT * FROM employment_onboarding WHERE referral_id=?",referral);
        if(rows.isEmpty()) { db.update("INSERT INTO employment_onboarding (referral_id) VALUES (?)",referral); rows=db.list("SELECT * FROM employment_onboarding WHERE referral_id=?",referral); }
        var o=rows.get(0);
        if(action.equals("ACKNOWLEDGE")) {
            if(o.get("startAt")==null) throw new Problem(409,"기업의 입사 안내 등록을 기다려 주세요.");
            if(o.get("acknowledgedAt")==null) db.update("UPDATE employment_onboarding SET acknowledged_at=CURRENT_TIMESTAMP WHERE referral_id=?",referral);
            return;
        }
        if(action.equals("JOINED")) {
            if(o.get("joinedAt")!=null) return;
            if(o.get("acknowledgedAt")==null) throw new Problem(409,"입사 안내를 먼저 확인해 주세요.");
            if(o.get("startAt")==null || Instant.parse(o.get("startAt").toString()).isAfter(Instant.now())) throw new Problem(409,"입사 예정일 이후 실제 출근한 경우에 확인해 주세요.");
            db.update("UPDATE employment_onboarding SET joined_at=CURRENT_TIMESTAMP WHERE referral_id=?",referral);
            db.update("UPDATE employment_links SET status='EMPLOYED',completed_at=CURRENT_TIMESTAMP WHERE employment_link_id=?",link.get("employmentLinkId"));
        } else {
            String message=Input.text(body,"message",2000,true).strip();
            if(o.get("joinedAt")!=null && action.equals("NOT_JOINED")) throw new Problem(409,"입사 확인 후에는 도움 요청으로 문의해 주세요.");
            if(message.equals(o.get("userMessage")) && (action.equals("HELP") || o.get("notJoinedAt")!=null)) return;
            db.update("UPDATE employment_onboarding SET user_message=? WHERE referral_id=?",message,referral);
            if(action.equals("NOT_JOINED")) {
                db.update("UPDATE employment_onboarding SET not_joined_at=CURRENT_TIMESTAMP WHERE referral_id=?",referral);
                db.update("UPDATE employment_referrals SET review_status='CANCELED' WHERE referral_id=?",referral);
                db.update("UPDATE employment_links SET status='MATCHING',completed_at=NULL WHERE employment_link_id=?",link.get("employmentLinkId"));
            }
            db.insert("inquiries",fields("user_id",user,"title","취업연계 #"+link.get("employmentLinkId")+" 입사 관련 도움 요청","content",message));
        }
        String title=action.equals("JOINED") ? "사용자가 실제 입사를 확인했습니다." : action.equals("NOT_JOINED") ? "사용자가 입사하지 않음을 전달했습니다." : "사용자가 입사 관련 도움을 요청했습니다.";
        var managers=link.get("managerUserId")==null ? db.list("SELECT user_id FROM users WHERE role='ADMIN' AND status='ACTIVE'") : List.of(fields("userId",link.get("managerUserId")));
        for(var m:managers) notify(number(m,"userId"),number(link,"employmentLinkId"),title,"취업연계 관리 또는 문의 관리에서 확인해 주세요.");
        notify(number(one("SELECT user_id FROM companies WHERE company_id=?",r.get("companyId")),"userId"),number(link,"employmentLinkId"),title,"취업연계 상세에서 사용자 응답을 확인해 주세요.");
    }
    private void notify(long user,long link,String title,String content) { db.insert("notifications",fields("user_id",user,"title",title,"content",content,"notification_type","EMPLOYMENT","related_id",link)); }
}
