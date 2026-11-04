package com.employment.global.config;

import java.util.Objects;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Uploaded profile photos are embedded in immutable application snapshots. */
@Component
public class ApplicationSnapshotSchema {
    private final JdbcTemplate jdbc;
    public ApplicationSnapshotSchema(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() throws java.sql.SQLException {
        try(var connection=Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
            String product=connection.getMetaData().getDatabaseProductName();
            if(!product.equalsIgnoreCase("MariaDB")&&!product.equalsIgnoreCase("MySQL"))return;
        }
        var sizes=jdbc.query("SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='application_snapshots' AND COLUMN_NAME='snapshot_json'",(r,n)->r.getLong(1));
        if(!sizes.isEmpty() && sizes.get(0)<16777215L)jdbc.execute("ALTER TABLE application_snapshots MODIFY COLUMN snapshot_json MEDIUMTEXT NOT NULL");
    }
}
