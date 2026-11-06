package com.employment.ads;

import com.employment.auth.AuthService;
import com.employment.global.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AdvertisingController {

  private final AdvertisingService service;
  private final AuthService auth;
  private final ObjectMapper json;

  public AdvertisingController(AdvertisingService service, AuthService auth, ObjectMapper json) {
    this.service = service;
    this.auth = auth;
    this.json = json;
  }

  @GetMapping("/company/advertising")
  Object company(HttpServletRequest r) {
    return service.workspace(auth.require(r, "COMPANY"));
  }

  @GetMapping("/company/ad-availability")
  Object availability(@RequestParam String productCode,@RequestParam(required=false) Long jobId,@RequestParam(defaultValue="") String excludeOrderId,HttpServletRequest r){
    return service.availability(auth.require(r,"COMPANY"),productCode,jobId,excludeOrderId);
  }

  @PostMapping("/company/ad-orders")
  Object create(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.create(auth.require(r, "COMPANY"), b);
  }

  @PostMapping("/company/ad-orders/{id}/checkout")
  Object checkout(@PathVariable String id, HttpServletRequest r) {
    return service.checkout(auth.require(r, "COMPANY"), id);
  }

  @PostMapping("/company/ad-orders/{id}/confirm")
  Object confirm(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body, HttpServletRequest r) {
    String impUid = body == null ? "" : Input.text(body, "impUid", 68, false).strip();
    return service.confirm(auth.require(r, "COMPANY"), id, impUid);
  }

  @PutMapping("/company/ad-orders/{id}/creative")
  void creative(@PathVariable String id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    service.creative(auth.require(r, "COMPANY"), id, b);
  }

  @PostMapping("/company/ad-orders/{id}/withdraw")
  void withdraw(@PathVariable String id, HttpServletRequest r) {
    service.withdraw(auth.require(r, "COMPANY"), id);
  }

  @PostMapping("/company/ad-orders/{id}/refund-request")
  void refundRequest(
    @PathVariable String id,
    @RequestBody Map<String, Object> b,
    HttpServletRequest r
  ) {
    service.refundRequest(auth.require(r, "COMPANY"), id, Input.text(b, "reason", 500, true));
  }

  @GetMapping("/admin/advertising")
  Object admin(HttpServletRequest r) {
    auth.require(r, "ADMIN");
    return Database.fields("products", service.products(true), "orders", service.orders(null));
  }

  @PutMapping("/admin/ad-products/{code}")
  void product(
    @PathVariable String code,
    @RequestBody Map<String, Object> b,
    HttpServletRequest r
  ) {
    auth.require(r, "ADMIN");
    service.product(code, b);
  }

  @PostMapping("/admin/ad-orders/{id}/review")
  void review(@PathVariable String id, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    service.review(auth.require(r, "ADMIN"), id, b);
  }

  @PostMapping("/admin/ad-orders/{id}/sync")
  void sync(@PathVariable String id, HttpServletRequest r) {
    auth.require(r, "ADMIN");
    service.sync(id);
  }

  @PostMapping("/admin/ad-orders/{id}/refund")
  Object refund(@PathVariable String id, HttpServletRequest r) {
    return service.refund(auth.require(r, "ADMIN"), id);
  }

  @GetMapping("/ads/products")
  Object publicProducts(){return service.products(false);}

  @GetMapping("/ads/active")
  Object active(@RequestParam String placement) {
    return service.active(placement);
  }

  @GetMapping("/ads/orders/{id}/image")
  Object image(@PathVariable String id, HttpServletRequest r) {
    // Anonymous visitors can see only approved, currently running advertisements.
    Long user = null;
    boolean admin = false;
    try {
      user = auth.require(r, "ADMIN");
      admin = true;
    } catch (Problem ignored) {
      try {
        user = auth.require(r, "COMPANY");
      } catch (Problem ignoredAgain) {}
    }
    return service.image(id, user, admin);
  }

  private final java.util.concurrent.ConcurrentHashMap<String, long[]> webhookWindows =
    new java.util.concurrent.ConcurrentHashMap<>();

  @PostMapping("/payments/portone/webhook")
  void webhook(@RequestBody String body, HttpServletRequest r) throws java.io.IOException {
    if (body.length() > 10000) throw new Problem(400, "올바르지 않은 결제 알림입니다.");
    long now = System.currentTimeMillis();
    webhookWindows.entrySet().removeIf(e -> now - e.getValue()[0] > 60000);
    var window = webhookWindows.computeIfAbsent(r.getRemoteAddr(), key -> new long[] { now, 0 });
    synchronized (window) {
      if (++window[1] > 60) throw new Problem(429, "결제 알림 요청이 너무 많습니다.");
    }
    var event = json.readTree(body);
    String id = event.path("merchant_uid").asText(),
      imp = event.path("imp_uid").asText();
    if (!id.matches("jb-ad-(?:[a-f0-9]{32}|[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-(?:[a-f0-9]{10}|[a-f0-9]{12}))") || !imp.matches("imp_[A-Za-z0-9]+")) throw new Problem(
      400,
      "올바르지 않은 결제 알림입니다."
    );
    // V1 webhooks have no V2 signature. Ignore the supplied status and retrieve the
    // payment through our server's V1 REST credentials before changing any state.
    service.webhook(id, imp);
  }
}
