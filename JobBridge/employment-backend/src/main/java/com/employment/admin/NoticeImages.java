package com.employment.admin;

import com.employment.auth.AuthService;
import com.employment.global.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@Service
public class NoticeImages {
    private final JdbcTemplate jdbc;
    public NoticeImages(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public void save(long id, Object image) {
        if ("".equals(image)) { jdbc.update("DELETE FROM notice_images WHERE notice_id=?", id); return; }
        byte[] bytes = normalize(image);
        jdbc.queryForObject("SELECT notice_id FROM notices WHERE notice_id=? FOR UPDATE", Long.class, id);
        if (jdbc.update("UPDATE notice_images SET image_data=? WHERE notice_id=?", bytes, id) == 0)
            jdbc.update("INSERT INTO notice_images(notice_id,image_data) VALUES(?,?)", id, bytes);
    }
    ResponseEntity<byte[]> get(long id) {
        var rows = jdbc.query("SELECT image_data FROM notice_images WHERE notice_id=?", (rs, n) -> rs.getBytes(1), id);
        if (rows.isEmpty()) return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_JPEG).body(rows.get(0));
    }
    private byte[] normalize(Object value) {
        if (!(value instanceof String image) || image.length() > 5600000 ||
            !(image.startsWith("data:image/jpeg;base64,") || image.startsWith("data:image/png;base64,")))
            throw new Problem(400, "JPG 또는 PNG 안내 이미지를 선택해 주세요.");
        try {
            byte[] raw = Base64.getDecoder().decode(image.substring(image.indexOf(',') + 1));
            if (raw.length > 4 * 1024 * 1024) throw new IOException();
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(raw))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new IOException();
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    int w = reader.getWidth(0), h = reader.getHeight(0);
                    if (w < 1 || h < 1 || w > 5000 || h > 12000 || (long) w * h > 16000000) throw new IOException();
                    var source = reader.read(0);
                    double scale = Math.min(1.0, Math.min(1600.0 / w, 8000.0 / h));
                    int width = Math.max(1, (int) (w * scale)), height = Math.max(1, (int) (h * scale));
                    var result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                    var graphics = result.createGraphics();
                    try {
                        graphics.setColor(java.awt.Color.WHITE); graphics.fillRect(0, 0, width, height);
                        graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        graphics.drawImage(source, 0, 0, width, height, null);
                    } finally { graphics.dispose(); }
                    var output = new ByteArrayOutputStream(); ImageIO.write(result, "jpeg", output);
                    return output.toByteArray();
                } finally { reader.dispose(); }
            }
        } catch (IOException | IllegalArgumentException ex) {
            throw new Problem(400, "이미지를 읽을 수 없습니다. 4MB 이하의 JPG·PNG 이미지를 사용해 주세요.");
        }
    }
}

@RestController
@RequestMapping("/api")
class NoticeImageController {
    private final NoticeImages images;
    private final Database db;
    private final AuthService auth;
    NoticeImageController(NoticeImages images, Database db, AuthService auth) { this.images = images; this.db = db; this.auth = auth; }
    @GetMapping("/notices/{id}/image")
    ResponseEntity<byte[]> published(@PathVariable long id) {
        db.one("SELECT notice_id FROM notices WHERE notice_id=? AND is_published=TRUE", id);
        return images.get(id);
    }
    @GetMapping("/admin/notices/{id}/image")
    ResponseEntity<byte[]> review(@PathVariable long id, HttpServletRequest request) {
        auth.require(request, "ADMIN"); db.one("SELECT notice_id FROM notices WHERE notice_id=?", id);
        return images.get(id);
    }
}
