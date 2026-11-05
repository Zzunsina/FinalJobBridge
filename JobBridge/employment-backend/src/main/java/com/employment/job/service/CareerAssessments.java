package com.employment.job.service;

import com.employment.global.common.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CareerAssessments {
    private final Database db;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final Map<String,Object> bank;
    private final List<Map<String,Object>> questions;
    private final Map<String,Map<String,Object>> banks=new LinkedHashMap<>();
    public CareerAssessments(Database db,ObjectMapper json,JdbcTemplate jdbc) throws IOException {
        this.db=db;this.json=json;this.jdbc=jdbc;
        bank=json.readValue(new ClassPathResource("db/career-questions-v1.json").getInputStream(),new TypeReference<>(){});
        questions=json.convertValue(bank.get("questions"),new TypeReference<>(){});
        banks.put(bank.get("version").toString(),bank);
        List<Map<String,Object>> modes=json.readValue(new ClassPathResource("db/career-modes-v2.json").getInputStream(),new TypeReference<>(){});
        for(var mode:modes)banks.put(mode.get("version").toString(),mode);
    }
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new ClassPathResource("db/career-assessments-upgrade.sql")).execute(Objects.requireNonNull(jdbc.getDataSource()));
    }
    @Transactional
    public Map<String,Object> start(long user) { return startBank(user,bank); }
    @Transactional
    public Map<String,Object> start(long user,String mode) {
        var selected=banks.get(mode+"-v2");
        if(selected==null)throw new Problem(400,"검사 종류를 확인해 주세요.");
        return startBank(user,selected);
    }
    private Map<String,Object> startBank(long user,Map<String,Object> bank) {
        var questions=bankQuestions(bank);
        db.one("SELECT user_id FROM users WHERE user_id=? FOR UPDATE",user);
        var existing=db.one("SELECT * FROM career_assessment_attempts WHERE user_id=? AND version=? AND status='IN_PROGRESS' ORDER BY attempt_id DESC LIMIT 1 FOR UPDATE",user,bank.get("version"));
        if(existing!=null) return view(expire(existing));
        long id=db.insert("career_assessment_attempts",Database.fields("user_id",user,"version",bank.get("version"),"answers_json",encode(new ArrayList<>(Collections.nCopies(questions.size(),null))),"started_at",Timestamp.from(Instant.now()),"deadline_at",Timestamp.from(Instant.now().plusSeconds(((Number)bank.get("durationSeconds")).longValue()))));
        return view(owned(user,id));
    }
    @Transactional
    public List<Map<String,Object>> history(long user) {
        finalizeExpired(user);
        var rows=db.list("SELECT attempt_id,version,status,started_at,deadline_at,completed_at FROM career_assessment_attempts WHERE user_id=? ORDER BY attempt_id DESC LIMIT 300",user);
        rows.forEach(row->row.putAll(metadata(row)));
        return rows;
    }
    private Map<String,Object> selected(Map<String,Object> row){return banks.getOrDefault(row.get("version").toString(),bank);}
    private List<Map<String,Object>> bankQuestions(Map<String,Object> selected){return json.convertValue(selected.get("questions"),new TypeReference<>(){});}
    private Map<String,Object> metadata(Map<String,Object> row){var b=selected(row);return Database.fields("mode",b.getOrDefault("mode","legacy"),"title",b.getOrDefault("title","기존 인적성검사"),"questionCount",bankQuestions(b).size(),"durationSeconds",b.get("durationSeconds"));}
    private void finalizeExpired(long user){for(var row:db.list("SELECT * FROM career_assessment_attempts WHERE user_id=? AND status='IN_PROGRESS' FOR UPDATE",user))expire(row);}

    @Transactional
    public Map<String,Object> get(long user,long id) { return view(expire(owned(user,id))); }
    @Transactional
    public Map<String,Object> save(long user,long id,Map<String,Object> body,boolean submit) {
        var row=expire(owned(user,id));
        if("COMPLETED".equals(row.get("status")))return view(row);
        Object revision=body.get("revision");
        if(!(revision instanceof Number n)||n.doubleValue()!=((Number)row.get("revision")).intValue())throw new Problem(409,"다른 화면에서 답안이 변경되었습니다. 검사 화면을 새로고침해 주세요.");
        var answers=validate(body.get("answers"),bankQuestions(selected(row)));
        if(submit&&answers.stream().anyMatch(Objects::isNull))throw new Problem(400,"모든 문항에 응답한 후 결과를 확인해 주세요.");
        db.update("UPDATE career_assessment_attempts SET answers_json=?,revision=revision+1 WHERE attempt_id=?",encode(answers),id);
        row=owned(user,id);
        if(submit) row=complete(row,false);
        return view(row);
    }
    private Map<String,Object> owned(long user,long id) {
        var row=db.one("SELECT * FROM career_assessment_attempts WHERE attempt_id=? AND user_id=? FOR UPDATE",id,user);
        if(row==null)throw new Problem(404,"검사 내역을 찾을 수 없습니다.");
        return row;
    }
    private Map<String,Object> expire(Map<String,Object> row) {
        if("IN_PROGRESS".equals(row.get("status"))&&!Instant.now().isBefore(Instant.parse(row.get("deadlineAt").toString())))return complete(row,true);
        return row;
    }
    private List<Integer> validate(Object raw,List<Map<String,Object>> questions) {
        if(!(raw instanceof List<?> list)||list.size()!=questions.size())throw new Problem(400,"검사 답안 형식을 확인해 주세요.");
        List<Integer> answers=new ArrayList<>();
        for(int i=0;i<list.size();i++) {
            var answer=list.get(i);if(answer==null){answers.add(null);continue;}
            int max="personality".equals(questions.get(i).get("type"))?4:3;
            if(!(answer instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()!=n.intValue()||n.intValue()<0||n.intValue()>max)throw new Problem(400,"응답 범위를 확인해 주세요.");
            answers.add(((Number)answer).intValue());
        }
        return answers;
    }
    private List<Integer> answers(Map<String,Object> row) {try{return json.readValue(row.get("answersJson").toString(),new TypeReference<>(){});}catch(IOException ex){throw new IllegalStateException(ex);}}
    private Map<String,Object> complete(Map<String,Object> row,boolean timedOut) {
        var answers=answers(row);
        var questions=bankQuestions(selected(row));
        int aptitudeTotal=(int)questions.stream().filter(q->!"personality".equals(q.get("type"))).count();
        List<Map<String,Object>> traits=new ArrayList<>();
        for(String axis:List.of("cooperation","responsibility","planning","adaptability","composure")) {
            int sum=0,count=0;String label="";
            for(int i=0;i<questions.size();i++)if(axis.equals(questions.get(i).get("axis"))){label=questions.get(i).get("axisLabel").toString();Integer answer=answers.get(i);if(answer!=null){sum+=Boolean.TRUE.equals(questions.get(i).get("reverse"))?4-answer:answer;count++;}}
            Map<String,Object> trait=new LinkedHashMap<>();trait.put("axis",axis);trait.put("label",label);trait.put("answered",count);trait.put("score",count==0?null:Math.round(sum*100.0/(count*4)));if(!label.isEmpty())traits.add(trait);
        }
        List<Map<String,Object>> abilities=new ArrayList<>();
        List<Map<String,Object>> review=new ArrayList<>();int correctTotal=0;
        for(String label:List.of("언어 이해","수리 이해","논리 추론")) {
            int correct=0,answered=0,total=0;
            for(int i=0;i<questions.size();i++)if(label.equals(questions.get(i).get("axisLabel"))){total++;if(answers.get(i)!=null)answered++;if(Objects.equals(answers.get(i),questions.get(i).get("correct")))correct++;}
            correctTotal+=correct;if(total>0)abilities.add(Database.fields("label",label,"correct",correct,"total",total,"answered",answered,"score",Math.round(correct*100.0/total)));
        }
        for(int i=0;i<questions.size();i++) {
            if("personality".equals(questions.get(i).get("type")))continue;
            var q=questions.get(i);Map<String,Object> detail=new LinkedHashMap<>();detail.put("number",i+1);detail.put("prompt",q.get("prompt"));detail.put("choices",q.get("choices"));detail.put("selected",answers.get(i));detail.put("correct",q.get("correct"));detail.put("explanation",q.get("explanation"));review.add(detail);
        }
        var report=Database.fields("traits",traits,"abilities",abilities,"aptitudeScore",aptitudeTotal==0?null:Math.round(correctTotal*100.0/aptitudeTotal),"correctCount",correctTotal,"answeredCount",answers.stream().filter(Objects::nonNull).count(),"timedOut",timedOut,"review",review,"notice","잡브릿지 자체 취업 준비용 진단입니다. 표준화된 채용검사, 백분위 또는 합격 가능성을 의미하지 않습니다.");
        report.putAll(metadata(row));report.put("aptitudeCount",aptitudeTotal);
        db.update("UPDATE career_assessment_attempts SET status='COMPLETED',report_json=?,completed_at=?,revision=revision+1 WHERE attempt_id=? AND status='IN_PROGRESS'",encode(report),Timestamp.from(Instant.now()),row.get("attemptId"));
        return owned(((Number)row.get("userId")).longValue(),((Number)row.get("attemptId")).longValue());
    }
    private Map<String,Object> view(Map<String,Object> row) {
        var questions=bankQuestions(selected(row));
        var response=new LinkedHashMap<>(row);response.putAll(metadata(row));response.remove("userId");response.remove("answersJson");response.remove("reportJson");response.put("answers",answers(row));response.put("serverNow",Instant.now().toString());
        if("COMPLETED".equals(row.get("status"))){try{response.put("report",json.readValue(row.get("reportJson").toString(),new TypeReference<Map<String,Object>>(){}));}catch(IOException ex){throw new IllegalStateException(ex);}}
        else response.put("questions",questions.stream().map(q->{var publicQuestion=new LinkedHashMap<>(q);publicQuestion.remove("correct");publicQuestion.remove("explanation");publicQuestion.remove("reverse");return publicQuestion;}).toList());
        return response;
    }
    @Transactional
    public Map<String,Object> latestSubmission(long user) {
        finalizeExpired(user);
        var rows=db.list("SELECT attempt_id,version,completed_at,report_json FROM career_assessment_attempts WHERE user_id=? AND status='COMPLETED' ORDER BY completed_at DESC,attempt_id DESC",user);
        Map<String,Object> personality=null,aptitude=null;
        for(var row:rows){var mode=selected(row).getOrDefault("mode","legacy").toString();
            if("practice".equals(mode))continue;
            if(personality==null&&(mode.equals("personality")||mode.equals("legacy")))personality=summary(row);
            if(aptitude==null&&(mode.equals("mock")||mode.equals("legacy")))aptitude=summary(row);
            if(personality!=null&&aptitude!=null)break;
        }
        if(personality==null&&aptitude==null)return null;
        if(personality!=null&&aptitude!=null&&Objects.equals(personality.get("attemptId"),aptitude.get("attemptId")))return personality;
        var primary=aptitude!=null?aptitude:personality;
        var result=new LinkedHashMap<>(primary);
        var combined=new LinkedHashMap<String,Object>();
        Map<String,Object> pr=personality==null?Map.of():json.convertValue(personality.get("report"),new TypeReference<>(){});
        Map<String,Object> ar=aptitude==null?Map.of():json.convertValue(aptitude.get("report"),new TypeReference<>(){});
        combined.putAll(ar);combined.put("traits",pr.getOrDefault("traits",List.of()));combined.putIfAbsent("abilities",List.of());combined.putIfAbsent("aptitudeScore",null);
        combined.put("notice","잡브릿지 자체 취업 준비용 진단이며, 표준화된 채용검사나 합격 예측이 아닙니다.");
        result.put("report",combined);result.put("personality",personality);result.put("aptitude",aptitude);result.put("version","submission-v2");
        return result;
    }
    private Map<String,Object> summary(Map<String,Object> row){
        try{Map<String,Object> report=json.readValue(row.get("reportJson").toString(),new TypeReference<>(){});
            var summary=new LinkedHashMap<String,Object>();
            for(String key:List.of("traits","abilities","aptitudeScore","correctCount","answeredCount","timedOut","notice","mode","questionCount","aptitudeCount","title","durationSeconds"))if(report.containsKey(key))summary.put(key,report.get(key));
            var result=Database.fields("attemptId",row.get("attemptId"),"version",row.get("version"),"completedAt",row.get("completedAt"),"report",summary);result.putAll(metadata(row));return result;
        }catch(IOException ex){throw new IllegalStateException(ex);}
    }
    private String encode(Object data){try{return json.writeValueAsString(data);}catch(IOException ex){throw new IllegalStateException(ex);}}
}

