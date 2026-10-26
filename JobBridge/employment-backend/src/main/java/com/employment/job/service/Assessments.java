package com.employment.job.service;

import com.employment.global.common.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.*;

@Service
@Transactional
@EnableScheduling
public class Assessments {
    private final Database db;
    public Assessments(Database db) { this.db = db; }
    private record Question(String prompt, List<String> choices, int answer) {}
    private static final List<Question> QUESTIONS = List.of(
        new Question("문서 12개를 세 명이 같은 양으로 검토하면 한 명당 몇 개인가요?", List.of("2","3","4","6"), 2),
        new Question("회의는 14시 20분에 시작해 40분간 진행됩니다. 종료 시각은?", List.of("14:40","15:00","15:20","16:00"), 1),
        new Question("일정표의 마감일이 변경됐습니다. 가장 먼저 할 일은?", List.of("이전 일정 그대로 진행","담당자와 변경 사항 확인","모든 자료 삭제","근거 없이 완료 처리"), 1),
        new Question("80개 자료 중 25%를 확인했습니다. 확인한 자료 수는?", List.of("10","15","20","25"), 2),
        new Question("표에 5, 10, 15, 20이 있습니다. 같은 규칙의 다음 수는?", List.of("21","24","25","30"), 2),
        new Question("업무 이메일의 첨부파일이 빠졌다면?", List.of("무시한다","첨부 확인 후 다시 전달한다","수신자 탓으로 돌린다","관련 없는 파일을 보낸다"), 1),
        new Question("한 상자에 8개씩 담으면 6상자는 모두 몇 개인가요?", List.of("14","24","48","64"), 2),
        new Question("공유 문서에서 잘못된 수치를 발견했다면?", List.of("근거를 확인하고 수정 이력을 남긴다","근거 없이 숫자를 바꾼다","문서 전체 삭제","외부에 그대로 배포"), 0),
        new Question("작업 A는 30분, B는 45분입니다. 순서대로 하면 총 몇 분인가요?", List.of("15","60","75","90"), 2),
        new Question("업무용 비밀번호 공유 요청을 받았다면?", List.of("게시판에 공개","동료에게 문자 전달","승인된 권한 부여 절차 안내","개인 이메일로 전달"), 2)
    );
    public Map<String,Object> questions() {
        var rows = new ArrayList<Map<String,Object>>();
        for (int i=0;i<QUESTIONS.size();i++) rows.add(Map.of("id",i,"prompt",QUESTIONS.get(i).prompt(),"choices",QUESTIONS.get(i).choices()));
        return Map.of("version","demo-v1","label","예시 직무기초 검사 · 실제 인적성 진단이 아닙니다.","questions",rows);
    }
    public void grade(long resume, Object raw) {
        if (!(raw instanceof List<?> answers) || answers.size()!=QUESTIONS.size())
            throw new Problem(400,"예시 검사 문항에 모두 답해 주세요.","assessmentAnswers");
        int correct=0;
        for (int i=0;i<answers.size();i++) {
            Object answer=answers.get(i);
            if (!(answer instanceof Number n) || n.doubleValue()!=n.intValue() || n.intValue()<0 || n.intValue()>3)
                throw new Problem(400,"검사 답안을 확인해 주세요.","assessmentAnswers");
            if (n.intValue()==QUESTIONS.get(i).answer()) correct++;
        }
        db.update("DELETE FROM resume_assessments WHERE resume_id=?",resume);
        db.update("INSERT INTO resume_assessments(resume_id,score,version) VALUES(?,?,'demo-v1')",resume,correct*10);
    }
    public Map<String,Object> result(long resume) {
        var rows=db.list("SELECT score,version FROM resume_assessments WHERE resume_id=?",resume);
        return rows.isEmpty()?null:rows.get(0);
    }
    public Integer minimum(long job) {
        var rows=db.list("SELECT min_score FROM job_assessment_settings WHERE job_id=?",job);
        return rows.isEmpty()?null:((Number)rows.get(0).get("minScore")).intValue();
    }
    public void configure(long job, Object raw) {
        Integer score=null;
        if (raw!=null && !raw.toString().isBlank()) {
            try { score=Integer.valueOf(raw.toString()); } catch(NumberFormatException e) { throw new Problem(400,"검토 기준 점수는 0~100 정수로 입력해 주세요.","assessmentMinScore"); }
            if(score<0||score>100) throw new Problem(400,"검토 기준 점수는 0~100으로 입력해 주세요.","assessmentMinScore");
        }
        db.update("DELETE FROM job_assessment_settings WHERE job_id=?",job);
        if(score!=null) db.update("INSERT INTO job_assessment_settings(job_id,min_score) VALUES(?,?)",job,score);
    }
    public void snapshot(long application,long job,long resume) {
        var result=result(resume);
        Integer min=minimum(job);
        if(min!=null && result==null) throw new Problem(400,"이 공고에 지원하려면 이력서에서 예시 검사를 완료해 주세요.");
        if(result!=null) db.update("INSERT INTO application_assessments(application_id,score,min_score) VALUES(?,?,?)",application,result.get("score"),min);
    }
    public Map<String,Object> companyResult(long application,boolean viewed) {
        var rows=db.list("SELECT * FROM application_assessments WHERE application_id=? FOR UPDATE",application);
        if(rows.isEmpty()) return null;
        var row=rows.get(0);
        boolean active=Set.of("APPLIED","UNDER_REVIEW","DOCUMENT_PASSED","INTERVIEW")
            .contains(db.one("SELECT status FROM applications WHERE application_id=?",application).get("status"));
        if(viewed && active && row.get("firstViewedAt")==null) {
            Instant now=Instant.now();
            db.update("UPDATE application_assessments SET first_viewed_at=?,review_due_at=? WHERE application_id=? AND first_viewed_at IS NULL",
                Timestamp.from(now),Timestamp.from(now.plus(Duration.ofHours(48))),application);
            row=db.one("SELECT * FROM application_assessments WHERE application_id=?",application);
        }
        boolean below=row.get("minScore") instanceof Number n && ((Number)row.get("score")).intValue()<n.intValue();
        row.put("belowThreshold",below && active);
        row.put("reviewRequired",below && active && row.get("reviewDueAt")!=null && !Instant.parse(row.get("reviewDueAt").toString()).isAfter(Instant.now()));
        row.remove("reviewNotified");
        return row;
    }
    @Scheduled(initialDelay=60000,fixedDelay=60000)
    public void flagDueReviews() {
        var rows=db.list("SELECT e.application_id,c.user_id FROM application_assessments e JOIN applications a ON a.application_id=e.application_id JOIN job_postings j ON j.job_id=a.job_id JOIN companies c ON c.company_id=j.company_id WHERE e.score<e.min_score AND e.review_due_at<=? AND e.review_notified=FALSE AND a.status IN ('APPLIED','UNDER_REVIEW','DOCUMENT_PASSED','INTERVIEW')",Timestamp.from(Instant.now()));
        for(var row:rows) {
            if(db.update("UPDATE application_assessments SET review_notified=TRUE WHERE application_id=? AND review_notified=FALSE",row.get("applicationId"))==1)
                db.insert("notifications",Database.fields("user_id",row.get("userId"),"title","지원서 검토 시간이 도래했습니다.","content","예시 검사 기준 미달 지원서를 담당자가 검토해 주세요. 자동 불합격 처리하지 않습니다.","notification_type","APPLICATION","related_id",row.get("applicationId")));
        }
    }
}
