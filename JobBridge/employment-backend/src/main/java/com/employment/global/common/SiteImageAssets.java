package com.employment.global.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.*;

@Service
public class SiteImageAssets {
 private final JdbcTemplate jdbc;private final ObjectMapper json;
 public SiteImageAssets(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
 @jakarta.annotation.PostConstruct
 public void initialize() throws java.io.IOException{
  jdbc.execute("CREATE TABLE IF NOT EXISTS site_image_assets(asset_path VARCHAR(255) PRIMARY KEY,image_data LONGBLOB NOT NULL,image_type VARCHAR(80) NOT NULL,sha256 VARCHAR(64) NOT NULL,updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
  var existing=new HashSet<>(jdbc.query("SELECT asset_path FROM site_image_assets",(rs,n)->rs.getString(1)));
  List<Map<String,String>> manifest;try(var input=new ClassPathResource("site-images/manifest.json").getInputStream()){manifest=json.readValue(input,new TypeReference<List<Map<String,String>>>(){});}
  for(var item:manifest){String key=item.get("path");if(existing.contains(key))continue;if(!valid(key))throw new IllegalStateException("Invalid bundled image path");
   try(var input=new ClassPathResource("site-images"+key).getInputStream()){byte[] bytes=input.readAllBytes();try{jdbc.update("INSERT INTO site_image_assets(asset_path,image_data,image_type,sha256) VALUES(?,?,?,?)",key,bytes,item.get("type"),item.get("sha256"));}catch(org.springframework.dao.DuplicateKeyException ignored){/* Concurrent application startup already inserted this image. */}}
  }
 }
 public static boolean valid(String key){return key!=null&&key.matches("/images/[a-zA-Z0-9_./-]+")&&!key.contains("..")&&!key.contains("//");}
 public ResponseEntity<byte[]> image(String key){
  if(!valid(key))throw new Problem(404,"이미지를 찾을 수 없습니다.");
  var rows=jdbc.query("SELECT image_data,image_type,sha256 FROM site_image_assets WHERE asset_path=?",(rs,n)->Map.of("bytes",rs.getBytes(1),"type",rs.getString(2),"hash",rs.getString(3)),key);
  if(rows.isEmpty())throw new Problem(404,"이미지를 찾을 수 없습니다.");var row=rows.get(0);
  return ResponseEntity.ok().contentType(MediaType.parseMediaType(row.get("type").toString())).cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePublic()).eTag(row.get("hash").toString()).header("X-Content-Type-Options","nosniff").body((byte[])row.get("bytes"));
 }
 // Called after home defaults exist. Never replace an administrator-uploaded BLOB.
 @org.springframework.transaction.annotation.Transactional
 public void migrateReferences() throws java.io.IOException{
  for(var row:jdbc.query("SELECT promotion_key,content_json FROM home_promotions WHERE image_data IS NULL",(rs,n)->Map.of("key",rs.getString(1),"content",rs.getString(2)))){
   Map<String,Object> content=json.readValue(row.get("content"),new TypeReference<Map<String,Object>>(){});String key=Objects.toString(content.get("imageUrl"),"");if(key.startsWith("/api/site-images/"))key=key.substring("/api/site-images".length());if(!valid(key))continue;
   var assets=jdbc.query("SELECT image_data,image_type FROM site_image_assets WHERE asset_path=?",(rs,n)->Map.of("bytes",rs.getBytes(1),"type",rs.getString(2)),key);if(assets.isEmpty())throw new IllegalStateException("Bundled home image is missing from DB: "+key);
   content.put("imageUrl","");content.put("artworkLayout",key.contains("/home-banners-v1/")?"native-2-1":"custom");
   jdbc.update("UPDATE home_promotions SET image_data=?,image_type=?,content_json=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE promotion_key=? AND image_data IS NULL",assets.get(0).get("bytes"),assets.get(0).get("type"),json.writeValueAsString(content),row.get("key"));
  }
  jdbc.update("UPDATE job_postings SET banner_image_url=CONCAT('/api/site-images',banner_image_url) WHERE banner_image_url LIKE '/images/%'");
 }
}
