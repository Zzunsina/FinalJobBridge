package com.employment.job.service;
import com.employment.global.common.Problem;
import java.time.LocalDate;

final class JobDatePolicy {
    private JobDatePolicy() {}
    static void validate(LocalDate start, LocalDate end, LocalDate originalStart, LocalDate today) {
        if (start.isBefore(today) && !start.equals(originalStart))
            throw new Problem(400,"접수 시작일은 오늘 이후로 선택해 주세요. 기존 시작일은 그대로 유지할 수 있습니다.","startDate");
        if (end.isBefore(today))
            throw new Problem(400,"접수 마감일은 오늘 이후로 선택해 주세요.","endDate");
        if (start.isAfter(end))
            throw new Problem(400,"접수 마감일은 시작일보다 빠를 수 없습니다.","endDate");
    }
}
