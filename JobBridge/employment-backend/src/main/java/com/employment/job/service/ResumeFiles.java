package com.employment.job.service;

import com.employment.global.common.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ResumeFiles {
    private final Database db;
    private final JdbcTemplate jdbc;
    public ResumeFiles(Database db, JdbcTemplate jdbc) { this.db = db; this.jdbc = jdbc; }
    public Map<String,Object> current(long id) {
        var rows = db.list("SELECT file_id,file_name FROM resume_files WHERE resume_id=? AND is_current=TRUE ORDER BY file_id DESC", id);
        return rows.isEmpty() ? null : rows.get(0);
    }
    public void save(long id, Object raw) {
        // Old versions remain immutable because submitted applications refer to their file ID.
        byte[] bytes = null;
        String name = null;
        if (raw != null) {
            if (!(raw instanceof Map<?,?> file) || !(file.get("data") instanceof String data)
                || data.length() > 7000000) throw new Problem(400, "5MB 이하 PDF 파일을 선택해 주세요.", "attachment");
            name = Objects.toString(file.get("fileName"), "");
            if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf") || name.length() > 200
                || name.matches(".*[\\\\/\\r\\n\\p{Cntrl}].*"))
                throw new Problem(400, "올바른 PDF 파일 이름을 확인해 주세요.", "attachment");
            try { bytes = Base64.getDecoder().decode(data); }
            catch (IllegalArgumentException e) { throw new Problem(400, "첨부파일을 다시 선택해 주세요.", "attachment"); }
            if (bytes.length < 8 || bytes.length > 5 * 1024 * 1024
                || !new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))
                throw new Problem(400, "5MB 이하 PDF 파일만 첨부할 수 있습니다.", "attachment");
        }
        jdbc.queryForObject("SELECT resume_id FROM resumes WHERE resume_id=? FOR UPDATE", Long.class, id);
        db.update("UPDATE resume_files SET is_current=FALSE WHERE resume_id=?", id);
        if (bytes != null) jdbc.update("INSERT INTO resume_files(resume_id,file_name,file_data) VALUES(?,?,?)", id, name, bytes);
    }
    public record File(String name, byte[] bytes) {}
    public File read(long id) {
        var rows = jdbc.query("SELECT file_name,file_data FROM resume_files WHERE file_id=?", (rs,n) -> new File(rs.getString(1),rs.getBytes(2)), id);
        if (rows.isEmpty()) throw new Problem(404, "첨부파일을 찾을 수 없습니다.");
        return rows.get(0);
    }
}
