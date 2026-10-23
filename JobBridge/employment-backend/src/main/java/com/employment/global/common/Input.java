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

    public static java.time.LocalDate birthDate(String value) {
        try {
            java.time.LocalDate date = value.length() == 8
                ? java.time.LocalDate.parse(value, java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                : java.time.LocalDate.parse(value);
            java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
            if (date.isBefore(today.minusYears(100)) || date.isAfter(today)) throw new IllegalArgumentException();
            return date;
        } catch (RuntimeException ex) {
            throw new Problem(400, "생년월일은 오늘 기준 100년 전부터 오늘까지의 실제 날짜로 입력해 주세요.");
        }
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

    public static String jobType(String value, boolean optional) {
        if (optional && "".equals(value)) return value;
        if (value == null || !java.util.Set.of("정규직", "계약직", "인턴").contains(value))
            throw new Problem(400, "고용형태를 확인해 주세요.", "jobType");
        return value;
    }

    public static java.time.LocalDate date(Map<String, Object> body, String key) {
        try {
            return java.time.LocalDate.parse(text(body, key, 10, true));
        } catch (java.time.DateTimeException ex) {
            throw new Problem(400, "실제 날짜를 YYYY-MM-DD 형식으로 입력해 주세요.", key);
        }
    }

    public static java.time.Instant instant(Map<String, Object> body, String key) {
        try {
            return java.time.OffsetDateTime.parse(text(body, key, 50, true)).toInstant();
        } catch (java.time.DateTimeException ex) {
            throw new Problem(400, "시간대를 포함한 올바른 일시를 입력해 주세요.", key);
        }
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
