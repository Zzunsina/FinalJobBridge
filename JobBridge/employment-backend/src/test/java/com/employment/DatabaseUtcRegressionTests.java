package com.employment;

import static org.assertj.core.api.Assertions.*;
import com.employment.global.common.Database;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class DatabaseUtcRegressionTests {
    @Test
    void h2KoreaSessionWithSeoulJvmPreservesGeneratedAndBoundInstantsAndDate() throws Exception {
        verify("jdbc:h2:mem:utc-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "", false);
    }

    @Test
    @EnabledIfEnvironmentVariable(named="JOBBRIDGE_TEST_MARIADB_URL", matches="jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/jobbridge_regression_[a-f0-9]+")
    void isolatedMariaDbKoreaSessionWithSeoulJvmPreservesGeneratedAndBoundInstantsAndDate() throws Exception {
        verify(System.getenv("JOBBRIDGE_TEST_MARIADB_URL") + "_jdbc", "root", "", true);
    }

    private void verify(String url, String user, String password, boolean maria) throws Exception {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(maria ? "SET time_zone='+09:00'" : "SET TIME ZONE 'Asia/Seoul'");
                // Session-local fixture permits repeated test runs without altering previous data.
                statement.execute("CREATE TEMPORARY TABLE utc_regression (id INT AUTO_INCREMENT PRIMARY KEY, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, wall_at DATETIME, test_day DATE)");
            }
            Database db = new Database(new JdbcTemplate(new SingleConnectionDataSource(connection, true)));
            Instant before = Instant.now().minusSeconds(2);
            Instant fixed = Instant.parse("2030-02-03T04:05:06Z");
            db.insert("utc_regression", Database.fields("wall_at", Timestamp.from(fixed), "test_day", LocalDate.of(2030,2,3)));
            var row = db.one("SELECT * FROM utc_regression WHERE id=?", 1);
            assertThat(Instant.parse(row.get("createdAt").toString())).isBetween(before, Instant.now().plusSeconds(2));
            assertThat(row.get("wallAt")).isEqualTo(fixed.toString());
            assertThat(row.get("testDay")).isEqualTo("2030-02-03");
            // Validate the stored wall-clock value as well, to catch symmetric read/write shifts.
            try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery("SELECT wall_at FROM utc_regression")) {
                rs.next();
                assertThat(rs.getString(1)).startsWith("2030-02-03 13:05:06");
            }
            db.update("UPDATE utc_regression SET created_at=? WHERE id=?", Timestamp.from(fixed), 1);
            assertThat(db.one("SELECT created_at FROM utc_regression").get("createdAt")).isEqualTo(fixed.toString());
            assertThat(Database.number(db.one("SELECT COUNT(*) AS n FROM utc_regression WHERE created_at>=? AND created_at<?",
                Timestamp.from(fixed.minusSeconds(1)), Timestamp.from(fixed.plusSeconds(1))), "n")).isEqualTo(1);
            assertThat(db.one("SELECT 1 AS has_image, 42 AS total").get("hasImage")).isEqualTo(true);
            assertThat(db.one("SELECT 0 AS has_image").get("hasImage")).isEqualTo(false);
            assertThat(db.one("SELECT 42 AS total").get("total")).isInstanceOf(Number.class);
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
