package com.employment.auth;

import java.sql.*;
import java.util.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Preserve historical identifiers while enforcing uniqueness for non-withdrawn accounts. */
@Component
public class RejoinSchema {
 private final JdbcTemplate jdbc;
 public RejoinSchema(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @EventListener(ApplicationReadyEvent.class)
 public void initialize(){jdbc.execute((ConnectionCallback<Void>) connection->{
  boolean h2=connection.getMetaData().getDatabaseProductName().contains("H2");
  addColumn(connection,"users","current_email","VARCHAR(255) GENERATED ALWAYS AS (CASE WHEN status <> 'WITHDRAWN' THEN LOWER(email) ELSE NULL END)");
  addColumn(connection,"users","current_phone","VARCHAR(30) GENERATED ALWAYS AS (CASE WHEN status <> 'WITHDRAWN' THEN phone ELSE NULL END)");
  addColumn(connection,"companies","registration_closed","BOOLEAN NOT NULL DEFAULT FALSE");
  try(var statement=connection.createStatement()){statement.executeUpdate("UPDATE companies SET registration_closed=TRUE WHERE registration_closed=FALSE AND user_id IN (SELECT user_id FROM users WHERE status='WITHDRAWN')");}
  addColumn(connection,"companies","current_business_number","VARCHAR(20) GENERATED ALWAYS AS (CASE WHEN registration_closed=FALSE THEN business_number ELSE NULL END)");
  index(connection,"users","uq_users_current_email","current_email");
  index(connection,"users","uq_users_current_phone","current_phone");
  index(connection,"companies","uq_companies_current_business","current_business_number");
  dropHistoricalUnique(connection,h2,"users","email");
  dropHistoricalUnique(connection,h2,"users","phone");
  dropHistoricalUnique(connection,h2,"companies","business_number");
  return null;
 });}
 private void addColumn(Connection connection,String table,String name,String definition)throws SQLException{
  boolean exists=false;try(var columns=connection.getMetaData().getColumns(connection.getCatalog(),null,null,null)){while(columns.next())if(table.equalsIgnoreCase(columns.getString("TABLE_NAME"))&&name.equalsIgnoreCase(columns.getString("COLUMN_NAME"))){exists=true;break;}}
  if(!exists)try(var statement=connection.createStatement()){statement.execute("ALTER TABLE "+table+" ADD COLUMN "+name+" "+definition);}
 }
 private void index(Connection connection,String table,String name,String column)throws SQLException{
  boolean exists=false;String actual=actualTable(connection,table);
  try(var indexes=connection.getMetaData().getIndexInfo(connection.getCatalog(),null,actual,true,false)){while(indexes.next())if(name.equalsIgnoreCase(indexes.getString("INDEX_NAME"))){exists=true;break;}}
  if(!exists)try(var statement=connection.createStatement()){statement.execute("CREATE UNIQUE INDEX "+name+" ON "+table+"("+column+")");}
 }
 private String actualTable(Connection connection,String table)throws SQLException{
  try(var tables=connection.getMetaData().getTables(connection.getCatalog(),null,null,new String[]{"TABLE","BASE TABLE"})){while(tables.next())if(table.equalsIgnoreCase(tables.getString("TABLE_NAME")))return tables.getString("TABLE_NAME");}return table;
 }
 private void dropHistoricalUnique(Connection connection,boolean h2,String table,String column)throws SQLException{
  if(h2){
   var names=new ArrayList<String>();
   try(var query=connection.prepareStatement("SELECT tc.CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE k ON k.CONSTRAINT_CATALOG=tc.CONSTRAINT_CATALOG AND k.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA AND k.CONSTRAINT_NAME=tc.CONSTRAINT_NAME WHERE LOWER(tc.TABLE_NAME)=? AND tc.CONSTRAINT_TYPE='UNIQUE' GROUP BY tc.CONSTRAINT_NAME HAVING COUNT(*)=1 AND MIN(LOWER(k.COLUMN_NAME))=?")){query.setString(1,table);query.setString(2,column);try(var rows=query.executeQuery()){while(rows.next())names.add(rows.getString(1));}}
   for(String name:names)try(var statement=connection.createStatement()){statement.execute("ALTER TABLE "+table+" DROP CONSTRAINT \""+name.replace("\"","\"\"")+"\"");}
  }else{
   var columns=new LinkedHashMap<String,List<String>>();
   try(var indexes=connection.getMetaData().getIndexInfo(connection.getCatalog(),null,actualTable(connection,table),true,false)){while(indexes.next()){String name=indexes.getString("INDEX_NAME"),field=indexes.getString("COLUMN_NAME");if(name!=null&&field!=null&&!name.equalsIgnoreCase("PRIMARY"))columns.computeIfAbsent(name,key->new ArrayList<>()).add(field);}}
   for(var index:columns.entrySet())if(index.getValue().size()==1&&column.equalsIgnoreCase(index.getValue().get(0)))try(var statement=connection.createStatement()){statement.execute("ALTER TABLE "+table+" DROP INDEX `"+index.getKey().replace("`","``")+"`");}
  }
 }
}
