package com.employment.global.common;

import static org.assertj.core.api.Assertions.assertThat;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class EmailConflictTests {
    @Test void uniqueEmailRaceHasSpecificMessageForMariaDbAndH2() {
        var handler = new Problem.Handler();
        for (String detail : new String[] {
            "Duplicate entry 'redacted' for key 'email'",
            "Unique index or primary key violation: PUBLIC.USERS(EMAIL NULLS FIRST)"
        }) {
            var response = handler.conflict(new DataIntegrityViolationException("conflict", new SQLException(detail)));
            assertThat(response.getStatusCode().value()).isEqualTo(409);
            assertThat(response.getBody().toString()).contains("이미 사용 중인 이메일입니다.", "field=email");
        }
    }
}
