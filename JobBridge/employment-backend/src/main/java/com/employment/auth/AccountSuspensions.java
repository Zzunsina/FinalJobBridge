package com.employment.auth;

import com.employment.global.common.Database;
import com.employment.global.common.Problem;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class AccountSuspensions {
    private final Database db;
    private final JdbcTemplate jdbc;
    public AccountSuspensions(Database db,JdbcTemplate jdbc){this.db=db;this.jdbc=jdbc;}
    @EventListener(ApplicationReadyEvent.class)
    public void initialize(){new ResourceDatabasePopulator(new ClassPathResource("db/account-suspensions-upgrade.sql")).execute(Objects.requireNonNull(jdbc.getDataSource()));}
    public String reason(long user){
        var rows=db.list("SELECT reason FROM account_suspensions WHERE user_id=? AND lifted_at IS NULL",user);
        return rows.isEmpty()?"등록된 정지 사유가 없습니다. 고객센터에 문의해 주세요.":rows.get(0).get("reason").toString();
    }
    public void record(long user,String status,String reason,long actor){
        if(status.equals("SUSPENDED")){
            if(reason==null||reason.isBlank()||reason.length()>1000)throw new Problem(400,"회원에게 안내할 정지 사유를 1~1000자로 입력해 주세요.","reason");
            if(db.update("UPDATE account_suspensions SET reason=?,suspended_by=?,suspended_at=CURRENT_TIMESTAMP,lifted_at=NULL WHERE user_id=?",reason.strip(),actor,user)==0)
                db.update("INSERT INTO account_suspensions(user_id,reason,suspended_by) VALUES(?,?,?)",user,reason.strip(),actor);
        }else db.update("UPDATE account_suspensions SET lifted_at=CURRENT_TIMESTAMP WHERE user_id=? AND lifted_at IS NULL",user);
    }
}
