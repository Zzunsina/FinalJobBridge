package com.employment.global.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Component;

@Component
@DependsOnDatabaseInitialization
public class FeatureSchemaCheck implements InitializingBean {
    private final DataSource source;
    public FeatureSchemaCheck(DataSource source) { this.source = source; }

    @Override
    public void afterPropertiesSet() throws SQLException {
        Set<String> missing = new TreeSet<>(Set.of(
            "resume_files", "resume_assessments", "job_assessment_settings", "application_assessments"
        ));
        try (Connection connection = source.getConnection();
             var tables = connection.getMetaData().getTables(connection.getCatalog(), connection.getSchema(), "%", null)) {
            while (tables.next()) missing.remove(tables.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                "Feature database tables are missing: " + String.join(", ", missing)
                + ". Back up and verify the target development database, then run once with Spring profile "
                + "'feature-setup' (append it to existing profiles), or manually apply db/feature-tables.sql. "
                + "Existing resumes/applications data must be retained. No schema change was performed by this check."
            );
        }
    }
}
