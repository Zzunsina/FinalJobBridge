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
public class CareerExplorations {
    private final Database db;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final Map<String,Object> bank;
    private final List<Map<String,Object>> questions;
    public CareerExplorations(Database db,ObjectMapper json,JdbcTemplate jdbc) throws IOException {
        this.db=db;this.json=json;this.jdbc=jdbc;
        bank=json.readValue(new ClassPathResource("db/exploration-questions-v1.json").getInputStream(),new TypeReference<>(){});
        questions=json.convertValue(bank.get("questions"),new TypeReference<>(){});
    }
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new ClassPathResource("db/career-explorations-upgrade.sql")).execute(Objects.requireNonNull(jdbc.getDataSource()));
    }
    @Transactional
    public Map<String,Object> start(long user) {
        db.one("SELECT user_id FROM users WHERE user_id=? FOR UPDATE",user);
        var existing=db.one("SELECT * FROM career_exploration_attempts WHERE user_id=? AND status='IN_PROGRESS' ORDER BY attempt_id DESC LIMIT 1 FOR UPDATE",user);
        if(existing!=null) return view(expire(existing));
        long id=db.insert("career_exploration_attempts",Database.fields("user_id",user,"version",bank.get("version"),"answers_json",encode(new ArrayList<>(Collections.nCopies(questions.size(),null))),"started_at",Timestamp.from(Instant.now()),"deadline_at",Timestamp.from(Instant.now().plusSeconds(((Number)bank.get("durationSeconds")).longValue()))));
        return view(owned(user,id));
    }
    @Transactional
    public List<Map<String,Object>> history(long user) {
        var active=db.one("SELECT * FROM career_exploration_attempts WHERE user_id=? AND status='IN_PROGRESS' ORDER BY attempt_id DESC LIMIT 1 FOR UPDATE",user);
        if(active!=null)expire(active);
        return db.list("SELECT attempt_id,version,status,started_at,deadline_at,completed_at FROM career_exploration_attempts WHERE user_id=? ORDER BY attempt_id DESC LIMIT 30",user);
    }
    @Transactional
    public Map<String,Object> get(long user,long id) { return view(expire(owned(user,id))); }
    @Transactional
    public Map<String,Object> save(long user,long id,Map<String,Object> body,boolean submit) {
        var row=expire(owned(user,id));
        if("COMPLETED".equals(row.get("status")))return view(row);
        Object revision=body.get("revision");
        if(!(revision instanceof Number n)||n.doubleValue()!=((Number)row.get("revision")).intValue())throw new Problem(409,"다른 화면에서 답안이 변경되었습니다. 검사 화면을 새로고침해 주세요.");
        var answers=validate(body.get("answers"));
        if(submit&&answers.stream().anyMatch(Objects::isNull))throw new Problem(400,"모든 문항에 응답한 후 결과를 확인해 주세요.");
        db.update("UPDATE career_exploration_attempts SET answers_json=?,revision=revision+1 WHERE attempt_id=?",encode(answers),id);
        row=owned(user,id);
        if(submit) row=complete(row,false);
        return view(row);
    }
    private Map<String,Object> owned(long user,long id) {
        var row=db.one("SELECT * FROM career_exploration_attempts WHERE attempt_id=? AND user_id=? FOR UPDATE",id,user);
        if(row==null)throw new Problem(404,"검사 내역을 찾을 수 없습니다.");
        return row;
    }
    private Map<String,Object> expire(Map<String,Object> row) {
        if("IN_PROGRESS".equals(row.get("status"))&&!Instant.now().isBefore(Instant.parse(row.get("deadlineAt").toString())))return complete(row,true);
        return row;
    }
    private List<Integer> validate(Object raw) {
        if(!(raw instanceof List<?> list)||list.size()!=questions.size())throw new Problem(400,"검사 답안 형식을 확인해 주세요.");
        List<Integer> answers=new ArrayList<>();
        for(int i=0;i<list.size();i++) {
            var answer=list.get(i);if(answer==null){answers.add(null);continue;}
            int max=4;
            if(!(answer instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()!=n.intValue()||n.intValue()<0||n.intValue()>max)throw new Problem(400,"응답 범위를 확인해 주세요.");
            answers.add(((Number)answer).intValue());
        }
        return answers;
    }
    private List<Integer> answers(Map<String,Object> row) {try{return json.readValue(row.get("answersJson").toString(),new TypeReference<>(){});}catch(IOException ex){throw new IllegalStateException(ex);}}
    private Map<String,Object> complete(Map<String,Object> row,boolean timedOut) {
        var responses=answers(row);
        List<Map<String,Object>> interests=new ArrayList<>(),values=new ArrayList<>();
        List<Map<String,Object>> dimensions=json.convertValue(bank.get("dimensions"),new TypeReference<>(){});
        for(var dimension:dimensions) {
            int sum=0,count=0,total=0;
            for(int i=0;i<questions.size();i++)if(dimension.get("axis").equals(questions.get(i).get("axis"))){total++;if(responses.get(i)!=null){sum+=responses.get(i);count++;}}
            var result=new LinkedHashMap<>(dimension);result.put("answered",count);result.put("total",total);result.put("score",count==0?null:Math.round(sum*100.0/(count*4)));
            if("interest".equals(dimension.get("type")))interests.add(result);else values.add(result);
        }
        var report=Database.fields("interests",interests,"values",values,"answeredCount",responses.stream().filter(Objects::nonNull).count(),"timedOut",timedOut,"notice","잡브릿지 자체 진로 탐색용 예시 검사입니다. 직업 적합성이나 채용 가능성을 판정하지 않습니다. 결과는 개인 참고용이며 기업에 자동 제출되지 않습니다.");
        db.update("UPDATE career_exploration_attempts SET status='COMPLETED',report_json=?,completed_at=?,revision=revision+1 WHERE attempt_id=? AND status='IN_PROGRESS'",encode(report),Timestamp.from(Instant.now()),row.get("attemptId"));
        return owned(((Number)row.get("userId")).longValue(),((Number)row.get("attemptId")).longValue());
    }
    private Map<String,Object> view(Map<String,Object> row) {
        var response=new LinkedHashMap<>(row);response.remove("userId");response.remove("answersJson");response.remove("reportJson");response.put("kind","exploration");response.put("answers",answers(row));response.put("serverNow",Instant.now().toString());
        if("COMPLETED".equals(row.get("status"))){try{response.put("report",json.readValue(row.get("reportJson").toString(),new TypeReference<Map<String,Object>>(){}));}catch(IOException ex){throw new IllegalStateException(ex);}}
        else response.put("questions",questions.stream().map(q->{var publicQuestion=new LinkedHashMap<>(q);publicQuestion.remove("correct");publicQuestion.remove("explanation");publicQuestion.remove("reverse");return publicQuestion;}).toList());
        return response;
    }
    private String encode(Object data){try{return json.writeValueAsString(data);}catch(IOException ex){throw new IllegalStateException(ex);}}
}
