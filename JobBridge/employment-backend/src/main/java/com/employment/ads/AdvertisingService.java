package com.employment.ads;

import static com.employment.global.common.Database.*;

import com.employment.global.common.*;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.net.URI;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdvertisingService {

  private final Database db;
  private final JdbcTemplate jdbc;
  private final PortOneGateway gateway;
  private final ObjectMapper json;

  public AdvertisingService(
    Database db,
    JdbcTemplate jdbc,
    PortOneGateway gateway,
    ObjectMapper json
  ) {
    this.db = db;
    this.jdbc = jdbc;
    this.gateway = gateway;
    this.json = json;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void initialize() {
    new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
      new ClassPathResource("db/ads-schema.sql")
    ).execute(Objects.requireNonNull(jdbc.getDataSource()));
    Object[][] defaults = {
      { "SLIDE", "프리미엄", "홈 채용 광고 상단의 대형 이미지 카드에 노출합니다.", 220000, 7, 2 },
      { "COMPANY", "스탠다드", "홈 채용 광고의 이미지형 카드에 노출합니다.", 110000, 7, 3 },
      { "JOB", "베이직", "홈 채용 광고의 간결한 카드에 노출합니다.", 55000, 7, 3 },
    };
    for (var p : defaults)
      if (
        db.one("SELECT product_code FROM ad_products WHERE product_code=?", p[0]) == null
      ) db.update(
        "INSERT INTO ad_products(product_code,name,description,price,duration_days,max_slots) VALUES(?,?,?,?,?,?)",
        p
      );
    upgradeTiers();
    upgradeHomeCapacity();
    if(db.one("SELECT upgrade_key FROM ad_feature_upgrades WHERE upgrade_key='home-standard-20-v1'")==null){
      db.update("UPDATE ad_products SET max_slots=20,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE product_code='COMPANY'");
      db.update("INSERT INTO ad_feature_upgrades(upgrade_key) VALUES('home-standard-20-v1')");
    }
    db.update(
      "UPDATE ad_orders SET review_status='ABANDONED',updated_at=CURRENT_TIMESTAMP WHERE review_status='AWAITING_PAYMENT' AND payment_status IN ('READY','FAILED')"
    );
  }

  private void upgradeTiers() {
    if (
      db.one(
        "SELECT upgrade_key FROM ad_feature_upgrades WHERE upgrade_key='tiered-home-cards-v1'"
      ) !=
      null
    ) return;
    String[][] tiers = {
      { "SLIDE", "프리미엄", "홈 채용 광고 상단의 대형 이미지 카드에 노출합니다.", "220000", "2" },
      { "COMPANY", "스탠다드", "홈 채용 광고의 이미지형 카드에 노출합니다.", "110000", "3" },
      { "JOB", "베이직", "홈 채용 광고의 간결한 카드에 노출합니다.", "55000", "3" },
    };
    for (var t : tiers)
      db.update(
        "UPDATE ad_products SET name=?,description=?,price=?,max_slots=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE product_code=?",
        t[1],
        t[2],
        Integer.parseInt(t[3]),
        Integer.parseInt(t[4]),
        t[0]
      );
    db.update("INSERT INTO ad_feature_upgrades(upgrade_key) VALUES('tiered-home-cards-v1')");
  }

  private void upgradeHomeCapacity(){
    if(db.one("SELECT upgrade_key FROM ad_feature_upgrades WHERE upgrade_key='home-capacity-9-21-unlimited-v1'")!=null)return;
    db.update("UPDATE ad_products SET max_slots=9,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE product_code='SLIDE'");
    db.update("UPDATE ad_products SET max_slots=21,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE product_code='COMPANY'");
    db.update("UPDATE ad_products SET max_slots=0,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE product_code='JOB'");
    db.update("INSERT INTO ad_feature_upgrades(upgrade_key) VALUES('home-capacity-9-21-unlimited-v1')");
  }

  public Map<String, Object> company(long user) {
    var c = db.one(
      "SELECT c.*,u.email,u.phone FROM companies c JOIN users u ON u.user_id=c.user_id WHERE c.user_id=? AND u.status='ACTIVE'",
      user
    );
    if (c == null || !"APPROVED".equals(c.get("approvalStatus"))) throw new Problem(
      403,
      "승인된 기업만 광고를 신청할 수 있습니다."
    );
    return c;
  }

  public List<Map<String, Object>> products(boolean admin) {
    return db.list(
      "SELECT * FROM ad_products" +
        (admin ? "" : " WHERE is_active=TRUE") +
        " ORDER BY CASE product_code WHEN 'SLIDE' THEN 1 WHEN 'COMPANY' THEN 2 ELSE 3 END"
    );
  }

  public Map<String, Object> workspace(long user) {
    var c = company(user);
    return fields(
      "company",
      fields("companyId", c.get("companyId"), "companyName", c.get("companyName")),
      "products",
      products(false),
      "gateway",
      gateway.configuration(),
      "jobs",
      db.list(
        "SELECT job_id,title,end_date FROM job_postings WHERE company_id=? AND status='PUBLISHED' AND end_date>=? ORDER BY job_id DESC",
        c.get("companyId"),
        today()
      ),
      "orders",
      orders(number(c, "companyId"))
    );
  }

  @org.springframework.scheduling.annotation.Scheduled(initialDelay=60000,fixedDelay=60000)
  @Transactional
  public void refreshInvalidOrders() { AdvertisingLifecycle.refresh(db); }

  public List<Map<String, Object>> orders(Long company) {
    refreshInvalidOrders();
    var rows = db.list(
      "SELECT o.order_id,CASE WHEN EXISTS(SELECT 1 FROM ad_order_simulations sim WHERE sim.order_id=o.order_id) THEN 'SIMULATION' ELSE 'PORTONE' END AS payment_source,o.company_id,c.company_name,o.product_code,o.product_name,o.price,o.vat,o.total_amount,o.duration_days,o.start_date,o.end_date,o.job_id,o.link_type,o.link_url,o.payment_status,o.review_status,o.refund_status,o.refund_reason,o.review_memo,o.created_at,o.updated_at,o.paid_at,o.reviewed_at,o.image_data IS NOT NULL AS has_image FROM ad_orders o JOIN companies c ON c.company_id=o.company_id" +
        (company == null
            ? " WHERE o.review_status<>'ABANDONED'"
            : " WHERE o.company_id=? AND o.review_status<>'ABANDONED'") +
        " ORDER BY CASE WHEN o.refund_status='REQUESTED' THEN 0 WHEN o.review_status='PENDING_REVIEW' THEN 1 ELSE 2 END,o.created_at DESC,o.order_id DESC",
      company == null ? new Object[] {} : new Object[] { company }
    );
    for (var r : rows) {
      r.put("imageUrl", "/api/ads/orders/" + r.get("orderId") + "/image?v=" + r.get("updatedAt"));
      r.put("displayStatus", display(r));
    }
    return rows;
  }

  // Approved unpaid applications reserve space, while pending review requests do not.
  private java.util.List<Map<String,Object>> reservations(String code,String exclude,LocalDate from,LocalDate to){
    return db.list("SELECT start_date,end_date FROM ad_orders WHERE product_code=? AND payment_status IN ('READY','FAILED','PAID') AND review_status='APPROVED' AND refund_status='NONE' AND order_id<>? AND start_date<=? AND end_date>=?",code,exclude,to,from);
  }
  private long remaining(java.util.List<Map<String,Object>> rows,LocalDate start,LocalDate end,long limit){
    if(limit==0)return -1;
    long peak=0;
    for(LocalDate day=start;!day.isAfter(end);day=day.plusDays(1)){long used=0;for(var r:rows)if(!date(r,"startDate").isAfter(day)&&!date(r,"endDate").isBefore(day))used++;peak=Math.max(peak,used);}
    return Math.max(0,limit-peak);
  }
  private void requireSpace(Map<String,Object> product,LocalDate start,LocalDate end,String exclude){
    long limit=number(product,"maxSlots");
    if(limit>0&&remaining(reservations(product.get("productCode").toString(),exclude,start,end),start,end,limit)==0)throw new Problem(409,"선택한 기간은 노출 자리가 모두 찼습니다. 노출 가능한 시작일을 다시 선택해 주세요.");
  }
  public Map<String,Object> availability(long user,String code,Long job,String exclude){
    var company=company(user);var product=db.one("SELECT * FROM ad_products WHERE product_code=? AND is_active=TRUE",code);
    if(product==null)throw new Problem(400,"판매 중인 상품을 선택해 주세요.");
    int duration=(int)number(product,"durationDays");
    if(!exclude.isBlank()){var own=owned(number(company,"companyId"),exclude,false);if(!code.equals(own.get("productCode")))throw new Problem(400,"신청 상품이 일치하지 않습니다.");duration=(int)number(own,"durationDays");job=own.get("jobId")==null?null:number(own,"jobId");}
    Map<String,Object> posting=null;
    if(job!=null){posting=db.one("SELECT start_date,end_date FROM job_postings WHERE job_id=? AND company_id=? AND status='PUBLISHED'",job,company.get("companyId"));if(posting==null)throw new Problem(400,"우리 기업의 공개 중인 공고를 선택해 주세요.");}
    var first=today();var last=first.plusDays(180);long limit=number(product,"maxSlots");var rows=limit==0?java.util.List.<Map<String,Object>>of():reservations(code,exclude,first,last.plusDays(duration-1));var days=new java.util.ArrayList<Map<String,Object>>();String next=null;
    for(LocalDate start=first;!start.isAfter(last);start=start.plusDays(1)){
      var end=start.plusDays(duration-1);long free=remaining(rows,start,end,limit);boolean jobValid=posting==null||(!date(posting,"startDate").isAfter(start)&&!date(posting,"endDate").isBefore(end));boolean available=(limit==0||free>0)&&jobValid;
      if(available&&next==null)next=start.toString();days.add(fields("startDate",start.toString(),"endDate",end.toString(),"remaining",free,"available",available,"reason",!jobValid?"공고 접수 기간 초과":free==0?"만석":""));
    }
    return fields("requestKey",code+":"+(job==null?"":job)+":"+exclude,"productCode",code,"durationDays",duration,"maxSlots",limit,"nextAvailableDate",next,"days",days);
  }

  @Transactional
  public Map<String, Object> create(long user, Map<String, Object> body) {
    var c = company(user);
    String code = Input.text(body, "productCode", 20, true);
    var p = db.one(
      "SELECT * FROM ad_products WHERE product_code=? AND is_active=TRUE FOR UPDATE",
      code
    );
    if (p == null) throw new Problem(400, "판매 중인 광고 상품을 선택해 주세요.");
    if (!Boolean.TRUE.equals(body.get("consent"))) throw new Problem(
      400,
      "광고 이용 및 환불 규정에 동의해 주세요."
    );
    LocalDate start = Input.date(body, "startDate");
    if (start.isBefore(today()) || start.isAfter(today().plusDays(180))) throw new Problem(
      400,
      "노출 희망일은 오늘부터 180일 이내로 선택해 주세요."
    );
    int days = ((Number) p.get("durationDays")).intValue(),
      price = ((Number) p.get("price")).intValue();
    var end = start.plusDays(days - 1);
    requireSpace(p,start,end,"");
    Long job = null;
    String type = Input.text(body, "linkType", 20, true),
      url;
    if (
      code.equals("JOB") || (body.get("jobId") != null && !body.get("jobId").toString().isBlank())
    ) {
      try {
        job = Long.parseLong(Objects.toString(body.get("jobId"), ""));
      } catch (Exception e) {
        throw new Problem(400, "홍보할 공고를 선택해 주세요.");
      }
      var j = db.one(
        "SELECT end_date FROM job_postings WHERE job_id=? AND company_id=? AND status='PUBLISHED' AND end_date>=?",
        job,
        c.get("companyId"),
        end
      );
      if (j == null) throw new Problem(
        400,
        "광고 종료일까지 접수 중인 우리 기업의 공고를 선택해 주세요."
      );
      type = "internal";
      url = "/jobs/" + job;
    } else if (type.equals("internal")) url = "/companies/" + c.get("companyId");
    else if (type.equals("external")) url = external(Input.text(body, "linkUrl", 500, true));
    else throw new Problem(400, "광고 이동 대상을 선택해 주세요.");
    var image = decode(Input.text(body, "image", 5600000, true));
    String id = "jb-ad-" + UUID.randomUUID().toString().replace("-", "");
    insertOrder(
      fields(
        "order_id",
        id,
        "review_status",
        "PENDING_REVIEW",
        "company_id",
        c.get("companyId"),
        "product_code",
        code,
        "product_name",
        p.get("name"),
        "price",
        price,
        "vat",
        price / 10,
        "total_amount",
        price + price / 10,
        "duration_days",
        days,
        "start_date",
        start,
        "end_date",
        end,
        "job_id",
        job,
        "link_type",
        type,
        "link_url",
        url,
        "image_data",
        image,
        "image_type",
        Input.text(body, "image", 5600000, true).startsWith("data:image/png;")
          ? "image/png"
          : "image/jpeg"
      )
    );
    notifyAdmins("광고 심사 요청", "새 광고 신청의 이미지와 노출 기간을 검토해 주세요.", id);
    var created = orderView(number(c, "companyId"), id);
    created.put("paymentId", id);
    return created;
  }

  public Map<String, Object> checkout(long user, String id) {
    refreshInvalidOrders();
    var c = company(user);
    var o = owned(number(c, "companyId"), id, false);
    if ("JOB_CLOSED".equals(o.get("reviewStatus"))) throw new Problem(409,"연결 공고가 마감되거나 변경되어 광고 신청이 취소되었습니다. 새 공고로 다시 신청해 주세요.");
    gateway.requireReady();
    if (
      !"APPROVED".equals(o.get("reviewStatus")) ||
      !"NONE".equals(o.get("refundStatus")) ||
      date(o, "startDate").isBefore(today()) ||
      !Set.of("READY", "FAILED").contains(o.get("paymentStatus"))
    ) throw new Problem(409, "관리자가 승인한 유효한 광고 신청만 결제할 수 있습니다.");
    if (
      o.get("jobId") != null &&
      db.one(
        "SELECT job_id FROM job_postings WHERE job_id=? AND company_id=? AND status='PUBLISHED' AND end_date>=?",
        o.get("jobId"),
        c.get("companyId"),
        date(o, "endDate")
      ) ==
      null
    ) throw new Problem(409, "광고 공고의 접수 기간을 확인해 주세요.");
    var config = new LinkedHashMap<String, Object>(gateway.configuration());
    config.remove("ready");
    config.remove("testOnly");
    config.putAll(
      fields(
        "paymentId",
        id,
        "orderName",
        "잡브릿지 " + o.get("productName"),
        "totalAmount",
        o.get("totalAmount"),
        "currency",
        "KRW",
        "payMethod",
        "card",
        "customer",
        fields(
          "customerId",
          "company-" + c.get("companyId"),
          "fullName",
          c.get("companyName"),
          "email",
          c.get("email"),
          "phoneNumber",
          c.get("phone")
        )
      )
    );
    return config;
  }

  @Transactional
  public Map<String, Object> confirm(long user, String id) {
    return confirm(user, id, "");
  }

  @Transactional
  public Map<String, Object> confirm(long user, String id, String impUid) {
    id = resolvePaymentOrderId(id);
    var c = company(user);
    owned(number(c, "companyId"), id, false);
    sync(id, impUid);
    return orderView(number(c, "companyId"), id);
  }

  @Transactional
  public void webhook(String id) {
    webhook(id, "");
  }

  @Transactional
  public void webhook(String id, String impUid) {
    id = resolvePaymentOrderId(id);
    if (db.one("SELECT order_id FROM ad_orders WHERE order_id=?", id) == null) return;
    sync(id, impUid);
  }

  @Transactional
  public void sync(String id) {
    sync(id, "");
  }

  private String resolvePaymentOrderId(String id) {
    if (id.matches("jb-ad-[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{10}")) {
      var matches = db.list("SELECT order_id FROM ad_orders WHERE LEFT(order_id,40)=?", id);
      if (matches.size() != 1) throw new Problem(409, "결제 주문번호를 고유하게 확인할 수 없습니다. 관리자에게 문의해 주세요.");
      return matches.get(0).get("orderId").toString();
    }
    return id;
  }

  private void sync(String id, String impUid) {
    if (!id.equals(PortOneGateway.merchantId(id)) && !id.equals(resolvePaymentOrderId(PortOneGateway.merchantId(id)))) throw new Problem(409, "결제 주문번호가 일치하지 않습니다.");
    var o = db.one("SELECT * FROM ad_orders WHERE order_id=? FOR UPDATE", id);
    if (o == null) throw new Problem(404, "광고 주문을 찾을 수 없습니다.");
    if (db.one("SELECT order_id FROM ad_order_simulations WHERE order_id=?", id) != null) return;
    var payment = impUid.isEmpty() ? gateway.payment(id) : gateway.paymentByProviderId(impUid);
    gateway.validate(payment, id, ((Number) o.get("totalAmount")).intValue());
    if (!impUid.isEmpty() && !impUid.equals(payment.path("providerPaymentId").asText())) {
      throw new Problem(409, "포트원 거래번호가 조회 결과와 일치하지 않습니다.");
    }
    String status = payment.path("status").asText();
    if (status.equals("PAID")) {
      if (Set.of("CANCELLED", "PARTIAL_CANCELLED").contains(o.get("paymentStatus"))) return;
      if (!"PAID".equals(o.get("paymentStatus"))) {
        db.update(
          "UPDATE ad_orders SET payment_status='PAID',paid_at=?,updated_at=CURRENT_TIMESTAMP WHERE order_id=?",
          Timestamp.from(Instant.now()),
          id
        );
        notifyAdmins(
          "광고 결제 완료",
          "광고 결제가 확인되었습니다. 승인된 광고는 지정한 기간에 노출됩니다.",
          id
        );
        notifyCompany(
          number(o, "companyId"),
          "광고 결제 완료",
          "광고·결제 메뉴에서 노출 상태를 확인해 주세요.",
          id
        );
      }
      refreshInvalidOrders();
    } else if (Set.of("CANCELLED", "PARTIAL_CANCELLED").contains(status)) {
      db.update(
        "UPDATE ad_orders SET payment_status=?,refund_status=?,review_status='STOPPED',updated_at=CURRENT_TIMESTAMP WHERE order_id=?",
        status,
        status.equals("CANCELLED") ? "COMPLETED" : "PARTIAL",
        id
      );
    } else if ("FAILED".equals(status) && !"PAID".equals(o.get("paymentStatus"))) db.update(
      "UPDATE ad_orders SET payment_status='FAILED',updated_at=CURRENT_TIMESTAMP WHERE order_id=?",
      id
    );
  }

  @Transactional
  public void creative(long user, String id, Map<String, Object> body) {
    var c = company(user);
    var o = owned(number(c, "companyId"), id, true);
    if (
      !Set.of("READY", "FAILED", "PAID").contains(o.get("paymentStatus")) ||
      !"CHANGES_REQUESTED".equals(o.get("reviewStatus")) ||
      !"NONE".equals(o.get("refundStatus"))
    ) throw new Problem(409, "보완 요청된 광고만 다시 제출할 수 있습니다.");
    LocalDate start = body.containsKey("startDate")
      ? Input.date(body, "startDate")
      : date(o, "startDate");
    if (start.isBefore(today()) || start.isAfter(today().plusDays(180))) throw new Problem(
      400,
      "노출 시작일을 다시 선택해 주세요."
    );
    var end = start.plusDays(number(o, "durationDays") - 1);
    var product=db.one("SELECT * FROM ad_products WHERE product_code=? FOR UPDATE",o.get("productCode"));
    requireSpace(product,start,end,id);
    if (
      o.get("jobId") != null &&
      db.one(
        "SELECT job_id FROM job_postings WHERE job_id=? AND company_id=? AND status='PUBLISHED' AND end_date>=?",
        o.get("jobId"),
        c.get("companyId"),
        end
      ) ==
      null
    ) throw new Problem(400, "광고 종료일까지 접수 중인 공고가 필요합니다.");
    db.update("UPDATE ad_orders SET start_date=?,end_date=? WHERE order_id=?", start, end, id);
    byte[] image = decode(Input.text(body, "image", 5600000, true));
    db.update(
      "UPDATE ad_orders SET image_data=?,image_type=?,review_status='PENDING_REVIEW',updated_at=CURRENT_TIMESTAMP WHERE order_id=?",
      image,
      body.get("image").toString().startsWith("data:image/png;") ? "image/png" : "image/jpeg",
      id
    );
    notifyAdmins("광고 보완 제출", "기업이 광고 이미지를 보완하여 제출했습니다.", id);
  }

  @Transactional
  public void withdraw(long user, String id) {
    var c = company(user);
    var o = owned(number(c, "companyId"), id, true);
    if (
      !Set.of("READY", "FAILED").contains(o.get("paymentStatus")) ||
      !Set.of("PENDING_REVIEW", "CHANGES_REQUESTED", "APPROVED").contains(o.get("reviewStatus"))
    ) throw new Problem(409, "미결제 광고 신청만 철회할 수 있습니다.");
    db.update(
      "UPDATE ad_orders SET review_status='WITHDRAWN',updated_at=CURRENT_TIMESTAMP WHERE order_id=?",
      id
    );
  }

  @Transactional
  public void refundRequest(long user, String id, String reason) {
    var c = company(user);
    var o = owned(number(c, "companyId"), id, true);
    if (
      !"PAID".equals(o.get("paymentStatus")) ||
      !"NONE".equals(o.get("refundStatus")) ||
      (!date(o, "startDate").isAfter(today()) && "APPROVED".equals(o.get("reviewStatus")))
    ) throw new Problem(
      409,
      "환불 요청 가능한 주문이 아닙니다. 노출을 시작한 광고는 고객센터로 문의해 주세요."
    );
    db.update(
      "UPDATE ad_orders SET refund_status='REQUESTED',refund_reason=?,updated_at=CURRENT_TIMESTAMP WHERE order_id=?",
      reason,
      id
    );
    notifyAdmins("광고 환불 요청", "광고 결제의 환불 요청이 접수되었습니다.", id);
  }

  @Transactional
  public void review(long admin, String id, Map<String, Object> body) {
    String action = Input.text(body, "action", 20, true),
      memo = Input.text(body, "memo", 1000, false).trim();
    // Lock the product first so concurrent approvals cannot exceed available slots.
    var before = db.one("SELECT product_code FROM ad_orders WHERE order_id=?", id);
    if (before == null) throw new Problem(404, "광고 주문을 찾을 수 없습니다.");
    var p = db.one(
      "SELECT * FROM ad_products WHERE product_code=? FOR UPDATE",
      before.get("productCode")
    );
    var o = db.one("SELECT * FROM ad_orders WHERE order_id=? FOR UPDATE", id);
    if (
      !Set.of("READY", "FAILED", "PAID").contains(o.get("paymentStatus")) ||
      !"NONE".equals(o.get("refundStatus"))
    ) throw new Problem(409, "환불 요청 또는 취소되지 않은 광고 신청만 심사할 수 있습니다.");
    String next;
    if (action.equals("approve")) {
      if (!Set.of("PENDING_REVIEW", "PAUSED").contains(o.get("reviewStatus"))) throw new Problem(
        409,
        "승인 가능한 상태가 아닙니다."
      );
      if (
        date(o, "endDate").isBefore(today()) ||
        (!"PAUSED".equals(o.get("reviewStatus")) && date(o, "startDate").isBefore(today()))
      ) throw new Problem(
        409,
        "노출 희망일이 지났습니다. 보완 요청 후 환불·재신청으로 처리해 주세요."
      );
      if (
        o.get("jobId") != null &&
        db.one(
          "SELECT job_id FROM job_postings WHERE job_id=? AND status='PUBLISHED' AND end_date>=?",
          o.get("jobId"),
          date(o, "endDate")
        ) ==
        null
      ) throw new Problem(409, "홍보 공고의 접수 기간을 확인해 주세요.");
      requireSpace(p,date(o,"startDate"),date(o,"endDate"),id);
      next = "APPROVED";
    } else if (action.equals("changes")) {
      if (
        !Set.of("PENDING_REVIEW", "CHANGES_REQUESTED").contains(o.get("reviewStatus")) ||
        memo.isEmpty()
      ) throw new Problem(400, "심사 대기 광고에 보완 사유를 입력해 주세요.");
      next = "CHANGES_REQUESTED";
    } else if (action.equals("pause")) {
      if (
        !"PAID".equals(o.get("paymentStatus")) || !"APPROVED".equals(o.get("reviewStatus"))
      ) throw new Problem(409, "승인된 광고만 중지할 수 있습니다.");
      next = "PAUSED";
    } else throw new Problem(400, "지원하지 않는 광고 처리입니다.");
    db.update(
      "UPDATE ad_orders SET review_status=?,review_memo=?,reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE order_id=?",
      next,
      memo,
      admin,
      id
    );
    notifyCompany(
      number(o, "companyId"),
      "광고 신청 상태 변경",
      "광고·결제 메뉴에서 심사 결과와 검토 의견을 확인해 주세요.",
      id
    );
  }

  @Transactional
  public Map<String, Object> refund(long admin, String id) {
    var o = db.one("SELECT * FROM ad_orders WHERE order_id=? FOR UPDATE", id);
    if (o == null) throw new Problem(404, "주문을 찾을 수 없습니다.");
    if ("COMPLETED".equals(o.get("refundStatus"))) return orderView(null, id);
    if (
      !"PAID".equals(o.get("paymentStatus")) || !"REQUESTED".equals(o.get("refundStatus"))
    ) throw new Problem(409, "환불 요청된 결제만 취소할 수 있습니다.");
    if (db.one("SELECT order_id FROM ad_order_simulations WHERE order_id=?", id) != null) {
      db.update(
        "UPDATE ad_orders SET payment_status='CANCELLED',refund_status='COMPLETED',review_status='STOPPED',updated_at=CURRENT_TIMESTAMP WHERE order_id=?",
        id
      );
      return orderView(null, id);
    }
    var payment = gateway.payment(id);
    gateway.validate(payment, id, ((Number) o.get("totalAmount")).intValue());
    if ("PAID".equals(payment.path("status").asText())) gateway.cancel(
      id,
      Objects.toString(o.get("refundReason"), "광고 신청 취소"),
      ((Number) o.get("totalAmount")).intValue()
    );
    sync(id);
    var result = orderView(null, id);
    if (!"CANCELLED".equals(result.get("paymentStatus"))) throw new Problem(
      409,
      "환불이 아직 완료되지 않았습니다. 결제 상태를 다시 확인해 주세요."
    );
    notifyCompany(
      number(o, "companyId"),
      "광고 결제 취소 완료",
      "광고·결제 메뉴에서 취소 내역을 확인해 주세요.",
      id
    );
    return result;
  }

  @Transactional
  public void product(String code, Map<String, Object> body) {
    var p = db.one("SELECT * FROM ad_products WHERE product_code=? FOR UPDATE", code);
    if (p == null) throw new Problem(404, "상품을 찾을 수 없습니다.");
    int version = integer(body, "version", 0, Integer.MAX_VALUE);
    if (number(p, "version") != version) throw new Problem(
      409,
      "다른 관리자가 수정했습니다. 새로고침해 주세요."
    );
    int price = integer(body, "price", 1000, 10000000);
    if (price % 10 != 0) throw new Problem(400, "가격은 10원 단위로 입력해 주세요.");
    if (!(body.get("isActive") instanceof Boolean)) throw new Problem(
      400,
      "판매 상태를 선택해 주세요."
    );
    db.update(
      "UPDATE ad_products SET name=?,description=?,price=?,duration_days=?,is_active=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE product_code=?",
      Input.text(body, "name", 80, true),
      Input.text(body, "description", 300, true),
      price,
      integer(body, "durationDays", 1, 90),
      body.get("isActive"),
      code
    );
  }

  public List<Map<String, Object>> active(String placement) {
    if (!Set.of("SLIDE", "COMPANY", "JOB").contains(placement)) throw new Problem(
      400,
      "광고 위치를 확인해 주세요."
    );
    var product=db.one("SELECT max_slots FROM ad_products WHERE product_code=?",placement);
    int limit=product==null?0:((Number)product.get("maxSlots")).intValue();
    var rows = db.list(
      "SELECT o.order_id,o.company_id,c.company_name,c.industry,c.address,o.job_id,j.title,j.end_date,j.career_type,j.work_location,o.link_type,o.link_url,o.updated_at FROM ad_orders o JOIN companies c ON c.company_id=o.company_id JOIN users u ON u.user_id=c.user_id LEFT JOIN job_postings j ON j.job_id=o.job_id WHERE o.product_code=? AND o.payment_status='PAID' AND o.review_status='APPROVED' AND o.refund_status='NONE' AND o.start_date<=? AND o.end_date>=? AND c.approval_status='APPROVED' AND u.status='ACTIVE' AND (o.job_id IS NULL OR (j.status='PUBLISHED' AND j.end_date>=?)) ORDER BY o.reviewed_at,o.order_id" +
        (limit>0?" LIMIT "+limit:""),
      placement,
      today(),
      today(),
      today()
    );
    for (var r : rows) {
      r.putAll(
        fields(
          "key",
          r.get("orderId"),
          "label",
          r.get("companyName"),
          "imageAlt",
          r.get("companyName") + " 광고",
          "advertiserName",
          r.get("companyName"),
          "isAd",
          true,
          "presentation",
          "banner",
          "to",
          r.get("linkUrl"),
          "version",
          r.get("updatedAt"),
          "imageUrl",
          "/api/ads/orders/" + r.get("orderId") + "/image?v=" + r.get("updatedAt")
        )
      );
    }
    return rows;
  }

  public org.springframework.http.ResponseEntity<byte[]> image(
    String id,
    Long user,
    boolean admin
  ) {
    var o = db.one("SELECT company_id,product_code FROM ad_orders WHERE order_id=?", id);
    if (o == null) throw new Problem(404, "광고 이미지를 찾을 수 없습니다.");
    boolean owner =
      user != null &&
      db.one(
        "SELECT company_id FROM companies WHERE company_id=? AND user_id=?",
        o.get("companyId"),
        user
      ) !=
      null;
    if (
      !admin &&
      !owner &&
      active(o.get("productCode").toString())
        .stream()
        .noneMatch(r -> id.equals(r.get("orderId")))
    ) throw new Problem(404, "광고 이미지를 찾을 수 없습니다.");
    var bytes = jdbc
      .query(
        "SELECT image_data,image_type FROM ad_orders WHERE order_id=?",
        (r, n) -> Map.of("bytes", r.getBytes(1), "type", r.getString(2)),
        id
      )
      .get(0);
    return org.springframework.http.ResponseEntity.ok()
      .cacheControl(org.springframework.http.CacheControl.noStore())
      .contentType(org.springframework.http.MediaType.parseMediaType(bytes.get("type").toString()))
      .body((byte[]) bytes.get("bytes"));
  }

  private void insertOrder(Map<String, Object> values) {
    db.update(
      "INSERT INTO ad_orders(" +
        String.join(",", values.keySet()) +
        ") VALUES(" +
        String.join(",", Collections.nCopies(values.size(), "?")) +
        ")",
      values.values().toArray()
    );
  }

  private Map<String, Object> owned(long company, String id, boolean lock) {
    var o = db.one(
      "SELECT * FROM ad_orders WHERE order_id=? AND company_id=?" + (lock ? " FOR UPDATE" : ""),
      id,
      company
    );
    if (o == null) throw new Problem(404, "광고 주문을 찾을 수 없습니다.");
    return o;
  }

  private Map<String, Object> orderView(Long c, String id) {
    return orders(c)
      .stream()
      .filter(r -> id.equals(r.get("orderId")))
      .findFirst()
      .orElseThrow(() -> new Problem(404, "광고 주문을 찾을 수 없습니다."));
  }

  private static String display(Map<String, Object> o) {
    String payment = Objects.toString(o.get("paymentStatus")),
      review = Objects.toString(o.get("reviewStatus"));
    if (Set.of("CANCELLED", "PARTIAL_CANCELLED").contains(payment)) return payment;
    if (!"NONE".equals(o.get("refundStatus"))) return "REFUND_" + o.get("refundStatus");
    if (!"APPROVED".equals(review)) return review;
    if (!"PAID".equals(payment)) return date(o, "startDate").isBefore(today())
      ? "PAYMENT_EXPIRED"
      : "PAYMENT_PENDING";
    if (today().isBefore(date(o, "startDate"))) return "SCHEDULED";
    return today().isAfter(date(o, "endDate")) ? "EXPIRED" : "LIVE";
  }

  private void notifyAdmins(String title, String content, String id) {
    for (var u : db.list("SELECT user_id FROM users WHERE role='ADMIN' AND status='ACTIVE'"))
      notification(number(u, "userId"), title, content);
  }

  private void notifyCompany(long c, String title, String content, String id) {
    notification(
      number(db.one("SELECT user_id FROM companies WHERE company_id=?", c), "userId"),
      title,
      content
    );
  }

  private void notification(long user, String title, String content) {
    db.insert(
      "notifications",
      fields(
        "user_id",
        user,
        "title",
        title,
        "content",
        content,
        "notification_type",
        "ADVERTISEMENT"
      )
    );
  }

  private static LocalDate today() {
    return LocalDate.now(ZoneId.of("Asia/Seoul"));
  }

  private static LocalDate date(Map<String, Object> r, String f) {
    return LocalDate.parse(r.get(f).toString());
  }

  private static int integer(Map<String, Object> b, String f, int min, int max) {
    try {
      int n = Integer.parseInt(Objects.toString(b.get(f), ""));
      if (n < min || n > max) throw new IllegalArgumentException();
      return n;
    } catch (Exception e) {
      throw new Problem(400, "입력 범위를 확인해 주세요: " + f);
    }
  }

  private static String external(String url) {
    try {
      var u = URI.create(url);
      if (
        !Set.of("https", "http").contains(u.getScheme()) ||
        u.getHost() == null ||
        u.getRawUserInfo() != null ||
        url.contains("\\") ||
        url.chars().anyMatch(ch -> ch <= 32)
      ) throw new IllegalArgumentException();
      return url;
    } catch (Exception e) {
      throw new Problem(400, "올바른 HTTP 또는 HTTPS 주소를 입력해 주세요.");
    }
  }

  private static byte[] decode(String value) {
    try {
      if (
        !value.startsWith("data:image/jpeg;base64,") && !value.startsWith("data:image/png;base64,")
      ) throw new IOException();
      byte[] bytes = Base64.getDecoder().decode(value.substring(value.indexOf(',') + 1));
      if (bytes.length > 4 * 1024 * 1024) throw new IOException();
      try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
        var readers = ImageIO.getImageReaders(stream);
        if (!readers.hasNext()) throw new IOException();
        var r = readers.next();
        try {
          r.setInput(stream);
          int w = r.getWidth(0),
            h = r.getHeight(0);
          if (
            w < 1 || h < 1 || w > 5000 || h > 5000 || (long) w * h > 16000000 || r.read(0) == null
          ) throw new IOException();
        } finally {
          r.dispose();
        }
      }
      return bytes;
    } catch (Exception e) {
      throw new Problem(400, "4MB 이하 JPG 또는 PNG 광고 이미지를 등록해 주세요.");
    }
  }
}
