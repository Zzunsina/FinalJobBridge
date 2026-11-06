package com.employment.admin;

import java.util.List;
import java.util.Locale;
import java.util.Map;

final class EmploymentMatch {
    private static final Map<String, List<String>> INDUSTRIES = Map.ofEntries(
        Map.entry("it정보통신", List.of("정보통신", "소프트웨어", "통신", "개발", "데이터", "머신러닝", "정보보안")),
        Map.entry("제조업", List.of("제조", "생산", "품질관리", "기술설비")),
        Map.entry("건설업", List.of("건설", "건축", "토목")),
        Map.entry("유통물류", List.of("유통", "물류", "운송", "구매자재")),
        Map.entry("금융보험", List.of("금융", "은행", "보험", "증권")),
        Map.entry("교육", List.of("교육", "강사", "학원")),
        Map.entry("보건복지", List.of("의료", "보건", "복지", "병원")),
        Map.entry("문화콘텐츠", List.of("문화", "콘텐츠", "영상", "디자인", "출판")),
        Map.entry("숙박음식", List.of("숙박", "호텔", "음식", "외식", "요식")),
        Map.entry("공공행정", List.of("공공", "행정", "공기업")),
        Map.entry("기타서비스업", List.of("서비스업", "서비스판매", "교육서비스", "고객지원", "고객상담"))
    );

    private EmploymentMatch() {}

    static boolean industryMatches(Object preferred, Object actual) {
        String wanted = normalize(preferred);
        String industry = normalize(actual);
        if (wanted.isEmpty() || wanted.equals("기타업종")) return true;
        if (wanted.equals("it") || wanted.equals("정보통신업")) wanted = "it정보통신";
        if (wanted.equals("it정보통신") && industry.equals("it")) return true;
        return industry.contains(wanted) || INDUSTRIES.getOrDefault(wanted, List.of()).stream()
            .anyMatch(industry::contains);
    }

    static boolean occupationMatches(Object preferred, Object actual) {
        String wanted = normalize(preferred);
        if (wanted.isEmpty() || wanted.equals("기타직무")) return true;
        String description = normalize(actual);
        if (description.contains(wanted)) return true;
        String role = wanted.replaceFirst("(개발자|디자이너)$", "");
        return !role.isEmpty() && description.contains(role);
    }

    private static String normalize(Object value) {
        return value == null ? "" : value.toString().toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
