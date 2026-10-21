package com.employment.auth;

import com.employment.global.common.Database;
import com.employment.global.common.Problem;
import jakarta.servlet.http.HttpServletRequest;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me/photo")
public class ProfilePhotoController {

    private final AuthService auth;
    private final JdbcTemplate jdbc;

    public ProfilePhotoController(AuthService auth, JdbcTemplate jdbc) {
        this.auth = auth;
        this.jdbc = jdbc;
    }

    private long owner(HttpServletRequest request) {
        return Database.number(auth.user(request), "userId");
    }

    @GetMapping
    ResponseEntity<byte[]> photo(HttpServletRequest request) {
        var photos = jdbc.query(
            "SELECT image_data FROM user_profile_photos WHERE user_id=?",
            (rs, index) -> rs.getBytes(1),
            owner(request)
        );
        if (photos.isEmpty()) return ResponseEntity.noContent()
            .cacheControl(CacheControl.noStore())
            .build();
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.IMAGE_JPEG)
            .body(photos.get(0));
    }

    @PutMapping
    @Transactional
    ResponseEntity<Void> save(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        long id = owner(request);
        byte[] photo = normalize(body.get("image"));
        // Serialize changes for this user, including two first uploads arriving together.
        jdbc.queryForObject("SELECT user_id FROM users WHERE user_id=? FOR UPDATE", Long.class, id);
        if (
            jdbc.update("UPDATE user_profile_photos SET image_data=? WHERE user_id=?", photo, id) ==
            0
        ) {
            jdbc.update(
                "INSERT INTO user_profile_photos (user_id,image_data) VALUES (?,?)",
                id,
                photo
            );
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    ResponseEntity<Void> remove(HttpServletRequest request) {
        jdbc.update("DELETE FROM user_profile_photos WHERE user_id=?", owner(request));
        return ResponseEntity.noContent().build();
    }

    public static byte[] normalize(Object value) {
        if (
            !(value instanceof String image) ||
            image.length() > 2800000 ||
            !(image.startsWith("data:image/jpeg;base64,") ||
                image.startsWith("data:image/png;base64,"))
        ) {
            throw new Problem(400, "JPG 또는 PNG 사진을 선택해 주세요.");
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(image.substring(image.indexOf(',') + 1));
            if (bytes.length == 0 || bytes.length > 2 * 1024 * 1024) throw new Problem(
                400,
                "사진 용량은 2MB 이하여야 합니다."
            );
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new IOException();
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    int width = reader.getWidth(0),
                        height = reader.getHeight(0);
                    if (width < 1 || height < 1 || width > 4096 || height > 4096) {
                        throw new Problem(400, "사진 크기는 가로·세로 4096픽셀 이하여야 합니다.");
                    }
                    BufferedImage source = reader.read(0);
                    int side = Math.min(width, height);
                    BufferedImage result = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB);
                    var graphics = result.createGraphics();
                    try {
                        graphics.setColor(Color.WHITE);
                        graphics.fillRect(0, 0, 512, 512);
                        graphics.setRenderingHint(
                            RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BICUBIC
                        );
                        int left = (width - side) / 2,
                            top = (height - side) / 2;
                        graphics.drawImage(
                            source,
                            0,
                            0,
                            512,
                            512,
                            left,
                            top,
                            left + side,
                            top + side,
                            null
                        );
                    } finally {
                        graphics.dispose();
                    }
                    var output = new ByteArrayOutputStream();
                    ImageIO.write(result, "jpeg", output);
                    return output.toByteArray();
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException | IllegalArgumentException ex) {
            throw new Problem(
                400,
                "사진을 읽을 수 없습니다. 다른 JPG 또는 PNG 사진을 선택해 주세요."
            );
        }
    }
}
