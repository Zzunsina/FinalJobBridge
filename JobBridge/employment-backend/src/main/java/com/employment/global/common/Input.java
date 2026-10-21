package com.employment.global.common;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class Input {

    private Input() {}

    public static String text(Map<String, Object> body, String key, int max, boolean required) {
        Object value = body.get(key);
        String result = value == null ? "" : value.toString();
        if (result.length() > max || (required && result.isBlank())) {
            throw new Problem(400, key + " 입력값을 확인해 주세요.");
        }
        return result;
    }

    public static String email(Map<String, Object> body, String key) {
        String value = text(body, key, 254, true).toLowerCase(java.util.Locale.ROOT);
        String[] parts = value.split("@", -1);
        if (parts.length != 2 || parts[0].length() > 64 ||
            !parts[0].matches("[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*") ||
            !parts[1].matches("(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}")) {
            throw new Problem(400, "이메일 형식을 확인해 주세요. 예: jobbridge@example.com");
        }
        return value;
    }

    public static String password(Map<String, Object> body, String key) {
        String value = text(body, key, 10000, true);
        if (
            value.length() < 8 ||
            value.length() > 20 ||
            value.getBytes(StandardCharsets.UTF_8).length > 72 ||
            value.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))
        ) {
            throw new Problem(400, "비밀번호는 공백 없이 8~20자로 입력해 주세요.");
        }
        return value;
    }

    public static String url(Map<String, Object> body, String key) {
        String value = text(body, key, 500, false);
        if (!value.isEmpty()) {
            URI uri = URI.create(value);
            if (
                !("http".equalsIgnoreCase(uri.getScheme()) ||
                    "https".equalsIgnoreCase(uri.getScheme())) ||
                uri.getHost() == null
            ) {
                throw new Problem(400, "HTTP 또는 HTTPS 주소를 입력해 주세요.");
            }
        }
        return value;
    }

    public static long id(Map<String, Object> body, String key) {
        try {
            long value = Long.parseLong(String.valueOf(body.get(key)));
            if (value < 1) throw new IllegalArgumentException();
            return value;
        } catch (RuntimeException ex) {
            throw new Problem(400, key + " 값을 확인해 주세요.");
        }
    }
}
