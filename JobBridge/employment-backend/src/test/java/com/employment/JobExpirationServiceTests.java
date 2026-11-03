package com.employment.job.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.employment.global.common.Database;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class JobExpirationServiceTests {
    @Test
    void closesOnlyPublishedJobsBeforeTodayAndCanRunRepeatedly() {
        var database = new EmbeddedDatabaseBuilder().generateUniqueName(true)
            .setType(EmbeddedDatabaseType.H2).build();
        try {
            var jdbc = new JdbcTemplate(database);
            jdbc.execute("CREATE TABLE job_postings (job_id INT PRIMARY KEY, status VARCHAR(20), end_date DATE, updated_at TIMESTAMP)");
            var today = LocalDate.of(2026, 11, 3);
            String[] statuses = { "PUBLISHED", "PUBLISHED", "PUBLISHED", "DRAFT", "PENDING", "REJECTED", "CLOSED" };
            for (int i = 0; i < statuses.length; i++) {
                var end = i == 1 ? today : i == 2 ? today.plusDays(1) : today.minusDays(1);
                jdbc.update("INSERT INTO job_postings VALUES (?, ?, ?, NULL)", i, statuses[i], end);
            }
            var service = new JobExpirationService(new Database(jdbc));
            assertThat(service.closeExpiredJobs(today)).isEqualTo(1);
            assertThat(jdbc.queryForList("SELECT status FROM job_postings ORDER BY job_id", String.class))
                .containsExactly("CLOSED", "PUBLISHED", "PUBLISHED", "DRAFT", "PENDING", "REJECTED", "CLOSED");
            assertThat(jdbc.queryForObject("SELECT updated_at IS NOT NULL FROM job_postings WHERE job_id=0", Boolean.class)).isTrue();
            assertThat(service.closeExpiredJobs(today)).isZero();
            assertThat(service.closeExpiredJobs(today.plusDays(1))).isEqualTo(1);
        } finally {
            database.shutdown();
        }
    }
}
