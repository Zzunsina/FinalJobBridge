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
            var today = EmploymentService.today();
            String[] statuses = { "PUBLISHED", "PUBLISHED", "PUBLISHED", "DRAFT", "PENDING", "REJECTED", "CLOSED" };
            for (int i = 0; i < statuses.length; i++) {
                var end = i == 1 ? today : i == 2 ? today.plusDays(1) : today.minusDays(1);
                jdbc.update("INSERT INTO job_postings VALUES (?, ?, ?, NULL)", i, statuses[i], end);
            }
            var service = new JobExpirationService(new Database(jdbc));
            service.closeExpiredJobs();
            assertThat(jdbc.queryForList("SELECT status FROM job_postings ORDER BY job_id", String.class))
                .containsExactly("CLOSED", "PUBLISHED", "PUBLISHED", "DRAFT", "PENDING", "REJECTED", "CLOSED");
            assertThat(jdbc.queryForObject("SELECT updated_at IS NOT NULL FROM job_postings WHERE job_id=0", Boolean.class)).isTrue();
            service.closeExpiredJobs();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_postings WHERE status='CLOSED'", Integer.class)).isEqualTo(2);
            jdbc.update("UPDATE job_postings SET end_date=? WHERE job_id=1", today.minusDays(1));
            service.closeExpiredJobs();
            assertThat(jdbc.queryForObject("SELECT status FROM job_postings WHERE job_id=1", String.class)).isEqualTo("CLOSED");
        } finally {
            database.shutdown();
        }
    }
}
