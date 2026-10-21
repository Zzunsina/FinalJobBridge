package com.employment.job.service;

import com.employment.global.common.Input;
import com.employment.global.common.Problem;
import java.time.YearMonth;
import java.util.*;

final class ResumeDetails {

    private ResumeDetails() {}

    static Map<String, Object> validate(Object value) {
        if (!(value instanceof Map<?, ?> source)) throw new Problem(
            400,
            "이력서 상세 항목을 확인해 주세요."
        );
        Map<String, Object> body = new LinkedHashMap<>();
        source.forEach((key, item) -> body.put(String.valueOf(key), item));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("desiredRole", Input.text(body, "desiredRole", 100, false));
        result.put("summary", Input.text(body, "summary", 5000, false));
        result.put(
            "legacyEducation",
            Input.text(body, "legacyEducation", 100, false)
        );
        result.put(
            "legacyCareerDescription",
            Input.text(body, "legacyCareerDescription", 10000, false)
        );
        result.put(
            "educations",
            rows(
                body,
                "educations",
                List.of(
                    "school",
                    "major",
                    "degree",
                    "status",
                    "startDate",
                    "endDate"
                ),
                10
            )
        );
        result.put(
            "experiences",
            rows(
                body,
                "experiences",
                List.of(
                    "company",
                    "position",
                    "startDate",
                    "endDate",
                    "description",
                    "current"
                ),
                20
            )
        );
        result.put(
            "projects",
            rows(
                body,
                "projects",
                List.of(
                    "name",
                    "role",
                    "startDate",
                    "endDate",
                    "description",
                    "url"
                ),
                20
            )
        );
        result.put(
            "certificates",
            rows(
                body,
                "certificates",
                List.of("name", "issuer", "date", "score"),
                20
            )
        );
        return result;
    }

    private static List<Map<String, Object>> rows(
        Map<String, Object> body,
        String name,
        List<String> keys,
        int max
    ) {
        Object raw = body.getOrDefault(name, List.of());
        if (
            !(raw instanceof List<?> entries) || entries.size() > max
        ) throw new Problem(400, "이력서 항목 수를 확인해 주세요.");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> map)) throw new Problem(
                400,
                "이력서 항목을 확인해 주세요."
            );
            Map<String, Object> fields = new LinkedHashMap<>();
            map.forEach((key, item) -> fields.put(String.valueOf(key), item));
            Map<String, Object> row = new LinkedHashMap<>();
            for (String key : keys) {
                if (key.equals("current")) {
                    if (
                        fields.containsKey(key) &&
                        !(fields.get(key) instanceof Boolean)
                    ) throw new Problem(400, "재직 여부를 확인해 주세요.");
                    row.put(key, Boolean.TRUE.equals(fields.get(key)));
                } else if (key.equals("url")) row.put(
                    key,
                    Input.url(fields, key)
                );
                else {
                    String text = Input.text(
                        fields,
                        key,
                        key.equals("description") ? 5000 : 100,
                        false
                    );
                    if (
                        (key.endsWith("Date") || key.equals("date")) &&
                        !text.isEmpty()
                    ) {
                        try {
                            if (
                                !text.matches("[0-9]{4}-[0-9]{2}")
                            ) throw new IllegalArgumentException();
                            YearMonth.parse(text);
                        } catch (RuntimeException ex) {
                            throw new Problem(
                                400,
                                "기간은 올바른 연월로 입력해 주세요."
                            );
                        }
                    }
                    row.put(key, text);
                }
            }
            String start = Objects.toString(row.get("startDate"), "");
            String end = Objects.toString(row.get("endDate"), "");
            if (Boolean.TRUE.equals(row.get("current"))) row.put("endDate", "");
            else if (
                !start.isEmpty() && !end.isEmpty() && start.compareTo(end) > 0
            ) throw new Problem(400, "종료일은 시작일 이후로 입력해 주세요.");
            rows.add(row);
        }
        return rows;
    }
}
