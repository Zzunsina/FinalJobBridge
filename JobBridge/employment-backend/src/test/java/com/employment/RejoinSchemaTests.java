package com.employment;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.employment.auth.RejoinSchema;
import static org.assertj.core.api.Assertions.*;
class RejoinSchemaTests {
 @Test void upgradesOldUniqueConstraintsWithoutChangingHistoricalValuesAndIsIdempotent(){
  var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:rejoin"+java.util.UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
  jdbc.execute("CREATE TABLE users(user_id BIGINT PRIMARY KEY,email VARCHAR(255) NOT NULL UNIQUE,phone VARCHAR(30) NOT NULL UNIQUE,status VARCHAR(20) NOT NULL)");
  jdbc.execute("CREATE TABLE companies(company_id BIGINT PRIMARY KEY,user_id BIGINT NOT NULL,business_number VARCHAR(20) NOT NULL UNIQUE)");
  jdbc.update("INSERT INTO users VALUES(1,'old@test.local','01012345678','WITHDRAWN')");jdbc.update("INSERT INTO companies VALUES(1,1,'1234567890')");
  var migration=new RejoinSchema(jdbc);migration.initialize();migration.initialize();
  jdbc.update("INSERT INTO users(user_id,email,phone,status) VALUES(2,'old@test.local','01012345678','ACTIVE')");jdbc.update("INSERT INTO companies(company_id,user_id,business_number) VALUES(2,2,'1234567890')");
  assertThat(jdbc.queryForObject("SELECT email FROM users WHERE user_id=1",String.class)).isEqualTo("old@test.local");
  assertThat(jdbc.queryForObject("SELECT business_number FROM companies WHERE company_id=1",String.class)).isEqualTo("1234567890");
  assertThatThrownBy(()->jdbc.update("INSERT INTO users(user_id,email,phone,status) VALUES(3,'old@test.local','01099999999','ACTIVE')")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->jdbc.update("INSERT INTO users(user_id,email,phone,status) VALUES(4,'new@test.local','01012345678','SUSPENDED')")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->jdbc.update("INSERT INTO companies(company_id,user_id,business_number) VALUES(3,3,'1234567890')")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
 }
}
