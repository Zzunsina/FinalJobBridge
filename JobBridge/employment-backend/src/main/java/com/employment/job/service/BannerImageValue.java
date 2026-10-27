package com.employment.job.service;

import com.employment.global.common.Input;
import com.employment.global.common.Problem;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Map;
import javax.imageio.ImageIO;

final class BannerImageValue {
    private BannerImageValue() {}
    static String validate(Map<String,Object> body) {
        String value = Input.text(body, "bannerImageUrl", 5600000, false);
        if (value.isEmpty()) return value;
        if (!value.startsWith("data:")) return Input.url(body, "bannerImageUrl");
        if (!(value.startsWith("data:image/jpeg;base64,") || value.startsWith("data:image/png;base64,")))
            throw invalid();
        try {
            byte[] bytes = Base64.getDecoder().decode(value.substring(value.indexOf(',') + 1));
            if (bytes.length > 4 * 1024 * 1024) throw invalid();
            try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw invalid();
                var reader = readers.next();
                try {
                    reader.setInput(stream);
                    int width=reader.getWidth(0), height=reader.getHeight(0);
                    if (width<1 || height<1 || width>5000 || height>12000 || (long)width*height>20000000) throw invalid();
                    String format=reader.getFormatName();
                    if (!(format.equalsIgnoreCase("JPEG") || format.equalsIgnoreCase("PNG"))) throw invalid();
                    if (reader.read(0)==null) throw invalid();
                } finally { reader.dispose(); }
            }
            return value;
        } catch (Exception ex) { throw invalid(); }
    }
    private static Problem invalid() { return new Problem(400,"4MB 이하의 올바른 JPG 또는 PNG 배너 이미지를 선택해 주세요.","bannerImageUrl"); }
}
