package com.employment.global.common;

import java.sql.Timestamp;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class Database {

    private final JdbcTemplate jdbc;

    public Database(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> list(String sql, Object... args) {
        System.out.println("list 실행");
        return jdbc.query(
            sql,
            ps -> bind(ps, args),
            (rs, row) -> {
                Map<String, Object> result = new LinkedHashMap<>();
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                    String raw = rs.getMetaData().getColumnLabel(i).toLowerCase(Locale.ROOT);
                    StringBuilder key = new StringBuilder();
                    boolean upper = false;
                    for (char c : raw.toCharArray()) {
                        if (c == '_') upper = true;
                        else {
                            key.append(upper ? Character.toUpperCase(c) : c);
                            upper = false;
                        }
                    }
                    Object value = rs.getObject(i);
                    // SQL TIMESTAMP/DATETIME values are exchanged in the configured Korea session.
                    // JDBC's default Calendar uses the JVM zone, which can differ from that session.
                    if (value instanceof Timestamp) value = rs.getTimestamp(i, korea()).toInstant().toString();
                    if ("has_image".equals(raw) && value != null) value = rs.getBoolean(i);
                    if (value instanceof java.sql.Date) value = rs.getObject(i, java.time.LocalDate.class).toString();
                    result.put(key.toString(), value);
                }
                return result;
            }
        );
    }

    //db.one("SELECT * FROM users WHERE login_id=? AND status='ACTIVE'", "k_5159672488");
    public Map<String, Object> one(String sql, Object... args) {
        System.out.println("one 실행");
        List<Map<String, Object>>  rows = list(sql, args);
        if (rows.isEmpty()) return null;
        else return rows.get(0);
    }

    public int update(String sql, Object... args) {
        return jdbc.update(sql, ps -> bind(ps, args));
    }

    public long insert(String table, Map<String, Object> fields) {
        var keys = new ArrayList<>(fields.keySet());
        String sql =
            "INSERT INTO " +
            table +
            " (" +
            String.join(",", keys) +
            ") VALUES (" +
            String.join(",", Collections.nCopies(keys.size(), "?")) +
            ")";
        var holder = new GeneratedKeyHolder();
        jdbc.update(
            connection -> {
                var statement = connection.prepareStatement(
                    sql,
                    java.sql.Statement.RETURN_GENERATED_KEYS
                );
                bind(statement, fields.values().toArray());
                return statement;
            },
            holder
        );
        var generated = Objects.requireNonNull(holder.getKeys());
        return generated
            .values()
            .stream()
            .filter(Number.class::isInstance)
            .map(Number.class::cast)
            .findFirst()
            .orElseThrow()
            .longValue();
    }

    private static Calendar korea() {
        return Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"));
    }

    private static void bind(PreparedStatement statement, Object[] args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Timestamp time) statement.setTimestamp(i + 1, time, korea());
            else statement.setObject(i + 1, args[i]);
        }
    }

    public static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    public static Map<String, Object> fields(Object... values) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) fields.put((String) values[i], values[i + 1]);
        return fields;
    }
}
