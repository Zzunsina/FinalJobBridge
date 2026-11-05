package com.employment.admin;

import com.employment.global.common.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.URI;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HomePromotionService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final HomePromotionTargets targets;
    @org.springframework.beans.factory.annotation.Autowired private SiteImageAssets imageAssets;
    public HomePromotionService(JdbcTemplate jdbc, ObjectMapper json,HomePromotionTargets targets) { this.jdbc=jdbc; this.json=json;this.targets=targets; }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() throws IOException {
        // The existing installation disables general schema initialization. Only this
        // feature's new table is created here; existing tables and edited rows stay intact.
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new ClassPathResource("db/home-promotions-upgrade.sql")).execute(Objects.requireNonNull(jdbc.getDataSource()));
        var defaults=json.readValue(new ClassPathResource("db/home-promotion-defaults.json").getInputStream(), new TypeReference<List<Map<String,Object>>>() {});
        var legacyDefaults=json.readValue(new ClassPathResource("db/home-promotion-legacy-defaults.json").getInputStream(),new TypeReference<List<Map<String,Object>>>() {});
        for (var legacy:legacyDefaults) {
            var existing=jdbc.query("SELECT content_json FROM home_promotions WHERE promotion_key=? AND image_data IS NULL",(rs,n)->rs.getString(1),legacy.get("key"));
            if(existing.isEmpty())continue;
            var content=json.readValue(existing.get(0),new TypeReference<Map<String,Object>>() {});
            var designFields=List.of("title","highlight","titleSuffix","description","eyebrow","cta","tags","imageUrl","cardTitle","cardSubtitle","cardEyebrow","cardMark","photoTag","theme","search");
            if(designFields.stream().allMatch(field->Objects.equals(content.get(field),legacy.get(field)))) {
                var converted=new LinkedHashMap<>(defaults.stream().filter(row->row.get("key").equals(legacy.get("key"))).findFirst().orElseThrow());
                converted.put("label",content.get("label"));converted.put("to",content.get("to"));
                for(String field:List.of("linkType","advertiserName","isAd","startDate","endDate"))if(content.containsKey(field))converted.put(field,content.get(field));
                jdbc.update("UPDATE home_promotions SET content_json=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE promotion_key=?",json.writeValueAsString(converted),legacy.get("key"));
            }
        }
        for (var row:defaults) {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM home_promotions WHERE promotion_key=?",Long.class,row.get("key"))==0 && jdbc.queryForObject("SELECT COUNT(*) FROM home_promotion_deletions WHERE promotion_key=?",Long.class,row.get("key"))==0)
                jdbc.update("INSERT INTO home_promotions(promotion_key,content_json,sort_order,is_published) VALUES(?,?,?,?)",row.get("key"),json.writeValueAsString(row),row.get("sortOrder"),row.get("isPublished"));
        }
        imageAssets.migrateReferences();
    }

    @Transactional
    public Map<String,Object> create(Map<String,Object> body) {
        String key="promo-"+UUID.randomUUID().toString().replace("-", "").substring(0,24);
        jdbc.update("INSERT INTO home_promotions(promotion_key,content_json,sort_order,is_published) VALUES(?,?,?,?)",key,"{}",99,false);
        var data=new LinkedHashMap<>(body);data.put("version",0);
        return save(key,data);
    }

    @Transactional
    public void delete(String key,Map<String,Object> body) {
        var rows=jdbc.query("SELECT version FROM home_promotions WHERE promotion_key=? FOR UPDATE",(rs,n)->rs.getInt(1),key);
        if(rows.isEmpty())throw new Problem(404,"홍보 슬라이드를 찾을 수 없습니다.");
        if(integer(body,"version",0,Integer.MAX_VALUE)!=rows.get(0))throw new Problem(409,"슬라이드가 변경되었습니다. 새로고침 후 다시 확인해 주세요.");
        jdbc.update("INSERT INTO home_promotion_deletions(promotion_key) VALUES(?)",key);
        jdbc.update("DELETE FROM home_promotions WHERE promotion_key=?",key);
    }

    public List<Map<String,Object>> list(boolean admin) {
        return jdbc.query("SELECT promotion_key,content_json,sort_order,is_published,version,updated_at,image_data IS NOT NULL AS has_image FROM home_promotions"+(admin?"":" WHERE is_published=TRUE")+" ORDER BY sort_order,promotion_key",(rs,n)-> {
            try {
                Map<String,Object> row=json.readValue(rs.getString("content_json"),new TypeReference<Map<String,Object>>() {});
                String key=rs.getString("promotion_key");
                row.put("key",key); row.put("sortOrder",rs.getInt("sort_order"));row.put("isPublished",rs.getBoolean("is_published"));
                int version=rs.getInt("version");row.put("version",version);
                if(rs.getBoolean("has_image"))row.put("imageUrl","/api/"+(admin?"admin/":"")+"home-promotions/"+key+"/image?v="+version);
                row.putIfAbsent("linkType","internal");row.putIfAbsent("isAd",false);row.putIfAbsent("advertiserName","");
                row.put("displayStatus",!rs.getBoolean("is_published")?"HIDDEN":displayStatus(row));
                return row;
            } catch(IOException ex) { throw new IllegalStateException("홍보 데이터를 읽을 수 없습니다.",ex); }
        }).stream().filter(row->admin || "LIVE".equals(row.get("displayStatus"))).toList();
    }

    @Transactional
    public Map<String,Object> save(String key, Map<String,Object> body) {
        var versions=jdbc.query("SELECT version FROM home_promotions WHERE promotion_key=? FOR UPDATE",(rs,n)->rs.getInt(1),key);
        if(versions.isEmpty())throw new Problem(404,"홍보 슬라이드를 찾을 수 없습니다.");
        if(integer(body,"version",0,Integer.MAX_VALUE)!=versions.get(0))throw new Problem(409,"다른 관리자가 변경했습니다. 목록을 새로고침한 뒤 다시 수정해 주세요.");
        Map<String,Object> data=new LinkedHashMap<>();data.put("key",key);
        for(String field:List.of("label","imageAlt"))data.put(field,Input.text(body,field,field.equals("label")?50:300,true).trim());
        String linkType=Objects.toString(body.get("linkType"),"internal");
        if(!Set.of("internal","external").contains(linkType))throw new Problem(400,"이동 방식을 선택해 주세요.","linkType");
        String to=Input.text(body,"to",300,true).trim();
        if(linkType.equals("external"))validateExternalUrl(to);else {validatePath(to);targets.validate(to);}
        data.put("to",to);data.put("linkType",linkType);
        boolean isAd=body.containsKey("isAd")?bool(body,"isAd"):false;
        String advertiser=Input.text(body,"advertiserName",100,false).trim();
        if(isAd && advertiser.isEmpty())throw new Problem(400,"광고 기업을 입력해 주세요.","advertiserName");
        data.put("isAd",isAd);data.put("advertiserName",advertiser);
        var start=date(body,"startDate");var end=date(body,"endDate");
        if(start!=null && end!=null && end.isBefore(start))throw new Problem(400,"노출 종료일은 시작일 이후로 선택해 주세요.","endDate");
        data.put("startDate",start==null?null:start.toString());data.put("endDate",end==null?null:end.toString());
        int order=integer(body,"sortOrder",1,99);boolean published=bool(body,"isPublished");
        data.put("presentation","banner");
        String image=Input.text(body,"imageUrl",5600000,true);byte[] bytes=null;String mime=null;boolean replaceImage=false;
        if(image.startsWith("data:")) {
            bytes=decodeImage(image);mime=image.startsWith("data:image/png;")?"image/png":"image/jpeg";replaceImage=true;data.put("imageUrl","");
        } else if(image.equals("/api/admin/home-promotions/"+key+"/image?v="+versions.get(0)) || image.equals("/api/home-promotions/"+key+"/image?v="+versions.get(0))) {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM home_promotions WHERE promotion_key=? AND image_data IS NOT NULL",Long.class,key)==0) throw new Problem(400,"홍보 사진을 선택해 주세요.","imageUrl");
            data.put("imageUrl","");
        } else {
            if(!image.matches("/(?:api/site-images/)?images/[a-zA-Z0-9_./-]+")||image.contains("..")||image.contains("//"))throw new Problem(400,"기본 이미지 또는 업로드한 사진을 선택해 주세요.","imageUrl");
            data.put("imageUrl",image);replaceImage=true;
        }
        var priorRows=jdbc.query("SELECT content_json FROM home_promotions WHERE promotion_key=?",(rs,n)->rs.getString(1),key);
        try {
            var prior=json.readValue(priorRows.get(0),new TypeReference<Map<String,Object>>() {});
            boolean keepLegacy=!"banner".equals(prior.get("presentation")) && (image.equals(prior.get("imageUrl")) || (!replaceImage && image.startsWith("/api/")));
            if(keepLegacy && prior.containsKey("title")){prior.putAll(data);prior.remove("presentation");data=prior;}
        }catch(IOException ex){throw new IllegalStateException(ex);}
        try {
            jdbc.update("UPDATE home_promotions SET content_json=?,sort_order=?,is_published=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE promotion_key=?",json.writeValueAsString(data),order,published,key);
            if(replaceImage)jdbc.update("UPDATE home_promotions SET image_data=?,image_type=? WHERE promotion_key=?",bytes,mime,key);
        } catch(com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException(ex); }
        return list(true).stream().filter(row->key.equals(row.get("key"))).findFirst().orElseThrow();
    }

    public org.springframework.http.ResponseEntity<byte[]> image(String key,boolean admin) {
        var rows=jdbc.query("SELECT image_data,image_type FROM home_promotions WHERE promotion_key=? AND image_data IS NOT NULL"+(admin?"":" AND is_published=TRUE"),(rs,n)->Map.of("bytes",rs.getBytes(1),"type",rs.getString(2)),key);
        if(rows.isEmpty() || (!admin && list(false).stream().noneMatch(row->key.equals(row.get("key")))))throw new Problem(404,"홍보 이미지를 찾을 수 없습니다.");
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).contentType(org.springframework.http.MediaType.parseMediaType(rows.get(0).get("type").toString())).body((byte[])rows.get(0).get("bytes"));
    }
    private static java.time.LocalDate date(Map<String,Object> body,String field) {
        String value=Objects.toString(body.get(field),"").trim();
        if(value.isEmpty())return null;
        try {if(!value.matches("\\d{4}-\\d{2}-\\d{2}"))throw new IllegalArgumentException();return java.time.LocalDate.parse(value);}
        catch(Exception ex){throw new Problem(400,"올바른 날짜를 선택해 주세요.",field);}
    }
    private static String displayStatus(Map<String,Object> row) {
        var today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        var start=date(row,"startDate");var end=date(row,"endDate");
        if(start!=null && today.isBefore(start))return "SCHEDULED";
        if(end!=null && today.isAfter(end))return "EXPIRED";
        return "LIVE";
    }
    private static void validateExternalUrl(String value) {
        try {var uri=URI.create(value);
            if(!Set.of("https","http").contains(Objects.toString(uri.getScheme(),"").toLowerCase(Locale.ROOT)) || uri.getHost()==null || uri.getHost().isBlank() || uri.getRawUserInfo()!=null || value.contains("\\") || value.chars().anyMatch(c->c<=32) || value.toLowerCase(Locale.ROOT).matches(".*%(0a|0d|5c).*"))throw new IllegalArgumentException();
        }catch(Exception ex){throw new Problem(400,"http:// 또는 https://로 시작하는 홈페이지 주소를 입력해 주세요.","to");}
    }
    private static void validatePath(String value) {
        try { URI uri=URI.create(value);if(!value.startsWith("/")||value.startsWith("//")||value.contains("\\")||uri.isAbsolute()||uri.getRawAuthority()!=null||value.chars().anyMatch(c->c<32)||!Set.of("/jobs","/companies","/resumes","/employment","/notices","/inquiries","/login","/signup","/").stream().anyMatch(path->uri.getPath().equals(path)||(!path.equals("/")&&uri.getPath().startsWith(path+"/"))))throw new IllegalArgumentException(); }
        catch(Exception ex){throw new Problem(400,"이동 링크는 사이트 내부 경로로 입력해 주세요. 예: /jobs","to");}
    }
    private static int integer(Map<String,Object> body,String key,int min,int max) {try{int value=Integer.parseInt(Objects.toString(body.get(key),""));if(value<min||value>max)throw new IllegalArgumentException();return value;}catch(Exception ex){throw new Problem(400,key+" 값을 확인해 주세요.",key);}}
    private static boolean bool(Map<String,Object> body,String key){if(!(body.get(key) instanceof Boolean value))throw new Problem(400,key+" 값을 확인해 주세요.",key);return value;}
    private static byte[] decodeImage(String image) {
        try {
            if(!(image.startsWith("data:image/jpeg;base64,")||image.startsWith("data:image/png;base64,")))throw new IOException();
            byte[] bytes=Base64.getDecoder().decode(image.substring(image.indexOf(',')+1));if(bytes.length>4*1024*1024)throw new IOException();
            try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext())throw new IOException();var reader=readers.next();
                try {reader.setInput(stream);int w=reader.getWidth(0),h=reader.getHeight(0);String format=reader.getFormatName();if(w<1||h<1||w>5000||h>5000||(long)w*h>16000000||!(format.equalsIgnoreCase("JPEG")||format.equalsIgnoreCase("PNG"))||reader.read(0)==null)throw new IOException();}finally{reader.dispose();}
            }return bytes;
        }catch(Exception ex){throw new Problem(400,"4MB 이하의 올바른 JPG 또는 PNG 이미지를 선택해 주세요.","imageUrl");}
    }
}
