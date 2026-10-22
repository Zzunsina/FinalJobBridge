package com.employment.auth;

import com.employment.global.common.Database;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CompanyImageController {
    private final AuthService auth;
    private final Database db;
    private final JdbcTemplate jdbc;
    public CompanyImageController(AuthService auth, Database db, JdbcTemplate jdbc) {
        this.auth = auth; this.db = db; this.jdbc = jdbc;
    }
    private long owner(HttpServletRequest request) {
        long user = auth.require(request, "COMPANY");
        return Database.number(db.one("SELECT company_id FROM companies WHERE user_id=?", user), "companyId");
    }
    private ResponseEntity<byte[]> image(long company) {
        var rows = jdbc.query("SELECT image_data FROM company_images WHERE company_id=?", (rs, index) -> rs.getBytes(1), company);
        if (rows.isEmpty()) return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_JPEG).body(rows.get(0));
    }
    @GetMapping("/company/image")
    ResponseEntity<byte[]> own(HttpServletRequest request) { return image(owner(request)); }
    @GetMapping("/companies/{id}/image")
    ResponseEntity<byte[]> publicImage(@PathVariable long id) {
        db.one("SELECT c.company_id FROM companies c JOIN users u ON u.user_id=c.user_id WHERE c.company_id=? AND c.approval_status='APPROVED' AND u.status='ACTIVE'", id);
        return image(id);
    }
    @GetMapping("/admin/companies/{id}/image")
    ResponseEntity<byte[]> reviewImage(@PathVariable long id, HttpServletRequest request) {
        auth.require(request, "ADMIN");
        db.one("SELECT company_id FROM companies WHERE company_id=?", id);
        return image(id);
    }
    @PutMapping("/company/image")
    @Transactional
    ResponseEntity<Void> save(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        long company = owner(request);
        byte[] bytes = ProfilePhotoController.normalize(body.get("image"));
        jdbc.queryForObject("SELECT company_id FROM companies WHERE company_id=? FOR UPDATE", Long.class, company);
        if (jdbc.update("UPDATE company_images SET image_data=? WHERE company_id=?", bytes, company) == 0)
            jdbc.update("INSERT INTO company_images(company_id,image_data) VALUES(?,?)", company, bytes);
        return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/company/image")
    ResponseEntity<Void> remove(HttpServletRequest request) {
        jdbc.update("DELETE FROM company_images WHERE company_id=?", owner(request));
        return ResponseEntity.noContent().build();
    }
}
