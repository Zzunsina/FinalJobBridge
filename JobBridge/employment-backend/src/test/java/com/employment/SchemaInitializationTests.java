package com.employment;

import static org.assertj.core.api.Assertions.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class SchemaInitializationTests {
    private Connection database() throws SQLException {
        return DriverManager.getConnection("jdbc:h2:mem:schema-" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    }
    private void initialize(Connection c) {
        var scripts = new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql"),
            new ClassPathResource("db/reference-data.sql"));
        scripts.setSqlScriptEncoding("UTF-8");
        scripts.execute(new SingleConnectionDataSource(c, true));
    }
    @Test
    void freshSchemaContainsOnlyTheExpectedBusinessTables() throws Exception {
        try (Connection c = database()) {
            initialize(c);
            Set<String> tables = new HashSet<>();
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(
                "SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'")) {
                while (rs.next()) tables.add(rs.getString(1));
            }
            assertThat(tables).containsExactlyInAnyOrder("users", "job_seekers", "companies",
                "job_postings", "resumes", "job_bookmarks", "applications", "interviews",
                "employment_links", "notices", "inquiries", "notifications", "auth_sessions",
                "password_reset_tokens", "application_snapshots", "user_profile_photos",
                "company_images", "notice_images", "customer_faqs", "user_signup_consents",
                "user_password_history", "resume_files", "resume_assessments", "job_assessment_settings", "application_assessments");
        }
    }
    @Test
    void repeatedInitializationPreservesUserDataReferenceRowsAndPhoneUniqueness() throws Exception {
        try (Connection c = database(); Statement s = c.createStatement()) {
            initialize(c);
            s.execute("INSERT INTO users(login_id,email,password,name,phone,role,birth_date,gender)"
                + " VALUES('existing','existing@test.local','hash','Existing','01012345678','JOB_SEEKER','2000-01-01','MALE')");
            int faqs;
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM customer_faqs")) { rs.next(); faqs = rs.getInt(1); }
            initialize(c);
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM users WHERE login_id='existing'")) {
                rs.next(); assertThat(rs.getInt(1)).isEqualTo(1);
            }
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM customer_faqs")) {
                rs.next(); assertThat(rs.getInt(1)).isEqualTo(faqs).isPositive();
            }
            assertThatThrownBy(() -> s.execute("INSERT INTO users(login_id,email,password,name,phone,role,birth_date,gender)"
                + " VALUES('duplicate','duplicate@test.local','hash','Duplicate','01012345678','JOB_SEEKER','2000-01-01','MALE')"))
                .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void missingFeatureTablesAreReportedBeforeScheduledQueriesAndSetupPreservesExistingRows() throws Exception {
        try (Connection c = database(); Statement statement = c.createStatement()) {
            initialize(c);
            statement.execute("INSERT INTO users(user_id,login_id,email,password,name,phone,role,birth_date,gender)"
                + " VALUES(100,'kept','kept@test.local','hash','Existing','01011112222','JOB_SEEKER','2000-01-01','MALE')");
            statement.execute("INSERT INTO job_seekers(job_seeker_id,user_id) VALUES(100,100)");
            statement.execute("INSERT INTO resumes(resume_id,job_seeker_id,title) VALUES(100,100,'Original resume')");
            for (String table : List.of("resume_files","resume_assessments","job_assessment_settings","application_assessments"))
                statement.execute("DROP TABLE " + table);
            var source = new SingleConnectionDataSource(c,true);
            var check = new com.employment.global.config.FeatureSchemaCheck(source);
            assertThatThrownBy(check::afterPropertiesSet).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("application_assessments").hasMessageContaining("feature-setup");

            var properties = org.springframework.core.io.support.PropertiesLoaderUtils.loadProperties(
                new ClassPathResource("application-feature-setup.properties"));
            assertThat(properties.getProperty("spring.sql.init.mode")).isEqualTo("always");
            assertThat(properties.getProperty("spring.sql.init.data-locations")).isEmpty();
            assertThat(properties.getProperty("app.seed-password")).isEmpty();
            String location=properties.getProperty("spring.sql.init.schema-locations").replace("classpath:","");
            var setup = new ResourceDatabasePopulator(new ClassPathResource(location));
            setup.setSqlScriptEncoding(properties.getProperty("spring.sql.init.encoding"));
            setup.execute(source);
            check.afterPropertiesSet();
            statement.execute("INSERT INTO resume_assessments(resume_id,score,version) VALUES(100,80,'demo-v1')");
            setup.execute(source);
            check.afterPropertiesSet();
            try (ResultSet rs=statement.executeQuery("SELECT title FROM resumes WHERE resume_id=100")) {
                rs.next();assertThat(rs.getString(1)).isEqualTo("Original resume");
            }
            try (ResultSet rs=statement.executeQuery("SELECT score FROM resume_assessments WHERE resume_id=100")) {
                rs.next();assertThat(rs.getInt(1)).isEqualTo(80);
            }
        }
    }
}
