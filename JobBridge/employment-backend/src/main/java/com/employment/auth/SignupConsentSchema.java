package com.employment.auth;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
@Component
public class SignupConsentSchema {
 private final JdbcTemplate jdbc;
 public SignupConsentSchema(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @EventListener(ApplicationReadyEvent.class)
 public void initialize(){jdbc.execute((ConnectionCallback<Void>) connection->{
  var existing=new java.util.HashSet<String>();try(var columns=connection.getMetaData().getColumns(connection.getCatalog(),null,null,null)){while(columns.next())if("user_signup_consents".equalsIgnoreCase(columns.getString("TABLE_NAME")))existing.add(columns.getString("COLUMN_NAME").toLowerCase(java.util.Locale.ROOT));}
  for(String column:java.util.List.of("recommendations_accepted","newsletter_accepted"))if(!existing.contains(column))try(var statement=connection.createStatement()){statement.execute("ALTER TABLE user_signup_consents ADD COLUMN "+column+" BOOLEAN NOT NULL DEFAULT FALSE");}
  return null;
 });}
}
