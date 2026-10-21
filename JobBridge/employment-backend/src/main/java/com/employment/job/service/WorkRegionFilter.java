package com.employment.job.service;

import com.employment.global.common.Problem;
import java.util.*;

final class WorkRegionFilter {

    private WorkRegionFilter() {}

    private static final Map<String, List<String>> ALIASES = Map.ofEntries(
        Map.entry("서울", List.of("서울", "서울특별시")),
        Map.entry("경기", List.of("경기", "경기도")),
        Map.entry("인천", List.of("인천", "인천광역시")),
        Map.entry("대전", List.of("대전", "대전광역시")),
        Map.entry("세종", List.of("세종", "세종특별자치시")),
        Map.entry("충남", List.of("충남", "충청남도")),
        Map.entry("충북", List.of("충북", "충청북도")),
        Map.entry("광주", List.of("광주", "광주광역시")),
        Map.entry("전남", List.of("전남", "전라남도")),
        Map.entry("전북", List.of("전북", "전라북도", "전북특별자치도")),
        Map.entry("대구", List.of("대구", "대구광역시")),
        Map.entry("경북", List.of("경북", "경상북도")),
        Map.entry("부산", List.of("부산", "부산광역시")),
        Map.entry("울산", List.of("울산", "울산광역시")),
        Map.entry("경남", List.of("경남", "경상남도")),
        Map.entry("강원", List.of("강원", "강원도", "강원특별자치도")),
        Map.entry("제주", List.of("제주", "제주도", "제주특별자치도"))
    );

    static String condition(String value, List<Object> parameters) {
        if (value == null || value.isBlank()) return "";
        if (value.length() > 2000) throw new Problem(
            400,
            "근무지역 선택을 확인해 주세요."
        );
        String[] selections = value.split("\\|", -1);
        if (selections.length > 20) throw new Problem(
            400,
            "지역은 최대 20개까지 선택할 수 있습니다."
        );
        List<String> conditions = new ArrayList<>();
        for (String selection : new LinkedHashSet<>(
            Arrays.asList(selections)
        )) {
            String normalized = selection.strip().replaceAll("\\s+", " ");
            if (normalized.isBlank() || !normalized.matches("[가-힣0-9 .-]+")) {
                throw new Problem(400, "근무지역 선택을 확인해 주세요.");
            }
            String[] words = normalized.split(" ");
            List<String> aliases = ALIASES.get(words[0]);
            if (aliases == null) {
                aliases = ALIASES.values()
                    .stream()
                    .filter(names -> names.contains(words[0]))
                    .findFirst()
                    .orElse(List.of(words[0]));
            }
            for (String alias : aliases) {
                // Compact both address and selection so formal province names and spaces work alike.
                StringBuilder pattern = new StringBuilder(alias).append('%');
                for (int i = 1; i < words.length; i++) pattern
                    .append(words[i])
                    .append('%');
                conditions.add("REPLACE(j.work_location,' ','') LIKE ?");
                parameters.add(pattern.toString());
            }
        }
        return " AND (" + String.join(" OR ", conditions) + ")";
    }
}
