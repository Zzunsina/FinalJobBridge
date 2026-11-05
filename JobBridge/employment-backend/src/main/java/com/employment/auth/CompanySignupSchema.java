package com.employment.auth;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
@Component
public class CompanySignupSchema {
 private final JdbcTemplate jdbc;
 public CompanySignupSchema(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @EventListener(ApplicationReadyEvent.class)
 public void initialize(){jdbc.execute((ConnectionCallback<Void>)connection->{
  var metadata=connection.getMetaData();boolean h2=metadata.getDatabaseProductName().contains("H2");var required=new java.util.HashSet<String>();
  try(var columns=metadata.getColumns(connection.getCatalog(),null,null,null)){
   while(columns.next())if("users".equalsIgnoreCase(columns.getString("TABLE_NAME"))&&columns.getInt("NULLABLE")==java.sql.DatabaseMetaData.columnNoNulls){String name=columns.getString("COLUMN_NAME").toLowerCase(java.util.Locale.ROOT);if(name.equals("birth_date")||name.equals("gender"))required.add(name);}
  }
  try(var statement=connection.createStatement()){for(String name:required)statement.execute(h2?"ALTER TABLE users ALTER COLUMN "+name+" DROP NOT NULL":"ALTER TABLE users MODIFY COLUMN "+name+(name.equals("birth_date")?" DATE NULL":" VARCHAR(10) NULL"));}
  return null;
 });}
}
