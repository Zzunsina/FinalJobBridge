package com.employment;

import static org.assertj.core.api.Assertions.*;

import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;

class PhoneMigrationRecoveryTests {
    private String database() {
        return "jdbc:h2:mem:phone-" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    }

    @Test
    void existingIndexStillRequiresAdministratorReconciliation() throws Exception {
        String url = database();
        Flyway.configure().dataSource(url, "sa", "").target("13").load().migrate();
        try (Connection c = DriverManager.getConnection(url, "sa", "");
             Statement s = c.createStatement()) {
            s.execute("CREATE UNIQUE INDEX uq_users_phone ON users(phone)");
            assertThatThrownBy(() -> Flyway.configure().dataSource(url, "sa", "")
                .load().migrate()).isInstanceOf(FlywayException.class);
            // Failure must not remove or weaken the existing uniqueness guarantee.
            try (ResultSet indexes = c.getMetaData().getIndexInfo(null, null, "users", true, false)) {
                boolean found = false;
                while (indexes.next()) {
                    if ("uq_users_phone".equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                        assertThat(indexes.getString("COLUMN_NAME")).isEqualTo("phone");
                        found = true;
                    }
                }
                assertThat(found).isTrue();
            }
        }
    }

    @Test
    void reviewedHistoryOnlyCorrectionPreservesLaterMigrationsAndUserData() throws Exception {
        String url = database();
        Flyway flyway = Flyway.configure().dataSource(url, "sa", "").load();
        flyway.migrate();
        try (Connection c = DriverManager.getConnection(url, "sa", "");
             Statement s = c.createStatement()) {
            s.execute("INSERT INTO users(email,password,name,phone,role,login_id,birth_date,gender) "
                + "VALUES('preserve@test.local','hash','Existing','01012345678','JOB_SEEKER',"
                + "'existing','2000-01-01','MALE')");
            // Reproduce the reported V14 failed / V15,V16 successful history.
            assertThat(s.executeUpdate("UPDATE flyway_schema_history SET success=FALSE WHERE version='14'"))
                .isEqualTo(1);
            assertThat(flyway.validateWithResult().validationSuccessful).isFalse();
            // Test-only simulation of an administrator's independently reviewed correction.
            assertThat(s.executeUpdate("UPDATE flyway_schema_history SET success=TRUE "
                + "WHERE version='14' AND success=FALSE")).isEqualTo(1);
            flyway.validate();
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            try (ResultSet rows = s.executeQuery("SELECT COUNT(*) FROM users WHERE login_id='existing'")) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(1);
            }
            try (ResultSet rows = s.executeQuery("SELECT COUNT(*) FROM flyway_schema_history "
                + "WHERE version IN ('15','16') AND success=TRUE")) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(2);
            }
            assertThatThrownBy(() -> s.execute("INSERT INTO users(email,password,name,phone,role,"
                + "login_id,birth_date,gender) VALUES('duplicate@test.local','hash','Duplicate',"
                + "'01012345678','JOB_SEEKER','duplicate','2000-01-01','MALE')"))
                .isInstanceOf(SQLException.class);
        }
    }
}
