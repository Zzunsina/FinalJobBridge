package com.employment.admin;

import com.employment.global.common.Problem;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class HomePromotionTargets {
    private final JdbcTemplate jdbc;
    public HomePromotionTargets(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public List<Map<String,Object>> search(String kind,String query,long selectedId) {
        String q=Objects.toString(query,"").trim();
        if(q.length()>100 || selectedId<0)throw new Problem(400,"검색어를 확인해 주세요.");
        String pattern="%"+q+"%";
        return switch(kind) {
            case "company" -> jdbc.query("SELECT c.company_id AS id,c.company_name AS label FROM companies c JOIN users u ON u.user_id=c.user_id WHERE c.approval_status='APPROVED' AND u.status='ACTIVE' AND (c.company_name LIKE ? OR c.company_id=?) ORDER BY CASE WHEN c.company_id=? THEN 0 ELSE 1 END,c.company_name,c.company_id LIMIT 100",(r,n)->row(r.getLong("id"),r.getString("label"),"companies"),pattern,selectedId,selectedId);
            case "job" -> jdbc.query("SELECT j.job_id AS id,j.title,c.company_name FROM job_postings j JOIN companies c ON c.company_id=j.company_id JOIN users u ON u.user_id=c.user_id WHERE j.status='PUBLISHED' AND j.end_date>=? AND c.approval_status='APPROVED' AND u.status='ACTIVE' AND (j.title LIKE ? OR c.company_name LIKE ? OR j.job_id=?) ORDER BY CASE WHEN j.job_id=? THEN 0 ELSE 1 END,j.job_id DESC LIMIT 100",(r,n)->row(r.getLong("id"),r.getString("title")+" · "+r.getString("company_name"),"jobs"),LocalDate.now(ZoneId.of("Asia/Seoul")),pattern,pattern,selectedId,selectedId);
            case "notice" -> jdbc.query("SELECT notice_id AS id,title AS label FROM notices WHERE is_published=TRUE AND (title LIKE ? OR notice_id=?) ORDER BY CASE WHEN notice_id=? THEN 0 ELSE 1 END,notice_id DESC LIMIT 100",(r,n)->row(r.getLong("id"),r.getString("label"),"notices"),pattern,selectedId,selectedId);
            default -> throw new Problem(400,"이동 대상 종류를 선택해 주세요.");
        };
    }
    private static Map<String,Object> row(long id,String label,String prefix) { return Map.of("id",id,"label",label,"to","/"+prefix+"/"+id); }
    public void validate(String to) {
        String path=URI.create(to).getPath();
        for(var entry:Map.of("companies","company","jobs","job","notices","notice").entrySet()) {
            String prefix="/"+entry.getKey()+"/";
            if(!path.startsWith(prefix))continue;
            try {
                String suffix=path.substring(prefix.length());
                if(!suffix.matches("[1-9][0-9]*"))throw new IllegalArgumentException();
                long id=Long.parseLong(suffix);
                if(search(entry.getValue(),"",id).stream().noneMatch(row->Objects.equals(row.get("id"),id)))throw new IllegalArgumentException();
            } catch(Exception ex) { throw new Problem(400,"선택한 항목이 삭제되었거나 공개 상태가 아닙니다. 다른 항목을 선택해 주세요.","to"); }
        }
    }
}
