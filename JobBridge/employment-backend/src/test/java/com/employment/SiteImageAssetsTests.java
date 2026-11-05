package com.employment;
import com.employment.global.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;

class SiteImageAssetsTests {
 @Test void bundledAssetsAreServedFromDatabaseAndMigrationPreservesUploadedImages() throws Exception {
  var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:assets-"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa",""));var json=new ObjectMapper();var assets=new SiteImageAssets(jdbc,json);assets.initialize();
  int count=jdbc.queryForObject("SELECT COUNT(*) FROM site_image_assets",Integer.class);assertThat(count).isGreaterThan(100);
  byte[] original=assets.image("/images/home-banners-v1/team.png").getBody();assertThat(original).isNotEmpty();
  jdbc.execute("CREATE TABLE home_promotions(promotion_key VARCHAR(50) PRIMARY KEY,content_json TEXT,image_data LONGBLOB,image_type VARCHAR(80),version INT DEFAULT 0,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");jdbc.execute("CREATE TABLE job_postings(job_id BIGINT PRIMARY KEY,banner_image_url TEXT)");
  jdbc.update("INSERT INTO home_promotions(promotion_key,content_json) VALUES('default',?)",json.writeValueAsString(Map.of("imageUrl","/images/home-banners-v1/team.png","label","관리자 이름 유지","to","/companies")));
  byte[] upload={4,5,6};jdbc.update("INSERT INTO home_promotions(promotion_key,content_json,image_data,image_type,version) VALUES('uploaded',?,?,'image/png',5)",json.writeValueAsString(Map.of("imageUrl","/images/home-banners-v1/career.png","label","교체한 이미지")),upload);
  jdbc.update("INSERT INTO job_postings VALUES(1,'/images/company-covers-v2/business.jpg')");assets.migrateReferences();
  assertThat(jdbc.queryForObject("SELECT image_data FROM home_promotions WHERE promotion_key='default'",byte[].class)).isEqualTo(original);
  assertThat(jdbc.queryForObject("SELECT content_json FROM home_promotions WHERE promotion_key='default'",String.class)).contains("관리자 이름 유지","/companies","native-2-1");
  assertThat(jdbc.queryForObject("SELECT image_data FROM home_promotions WHERE promotion_key='uploaded'",byte[].class)).isEqualTo(upload);assertThat(jdbc.queryForObject("SELECT version FROM home_promotions WHERE promotion_key='uploaded'",Integer.class)).isEqualTo(5);
  assertThat(jdbc.queryForObject("SELECT banner_image_url FROM job_postings WHERE job_id=1",String.class)).isEqualTo("/api/site-images/images/company-covers-v2/business.jpg");
  assets.migrateReferences();assertThat(jdbc.queryForObject("SELECT version FROM home_promotions WHERE promotion_key='default'",Integer.class)).isEqualTo(1);
  var mvc=MockMvcBuilders.standaloneSetup(new SiteImageController(assets)).build();mvc.perform(get("/api/site-images/images/home-banners-v1/team.png")).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(content().bytes(original)).andExpect(header().exists("ETag"));
  assertThatThrownBy(()->assets.image("/images/../secret.png")).isInstanceOf(Problem.class);assertThatThrownBy(()->assets.image("/images/not-present.png")).isInstanceOf(Problem.class);
  jdbc.update("UPDATE site_image_assets SET image_data=? WHERE asset_path='/images/jobbridge-logo.jpg'",upload);assets.initialize();assertThat(assets.image("/images/jobbridge-logo.jpg").getBody()).isEqualTo(upload);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site_image_assets",Integer.class)).isEqualTo(count);
 }
}

