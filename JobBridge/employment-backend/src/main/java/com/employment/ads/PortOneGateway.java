package com.employment.ads;

import com.employment.global.common.Problem;
import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Component;

/** PortOne V1 adapter. The advertising service uses a provider-independent payment result. */
@Component
public class PortOneGateway {

  private final String impCode, channelKey, apiKey, secret;
  private final ObjectMapper json;
  private final HttpClient client;
  private final URI base;
  private volatile String token = "";
  private volatile long tokenExpiry;

  @Autowired
  public PortOneGateway(
    ObjectMapper json,
    @Value("${portone.imp-code:}") String impCode,
    @Value("${portone.channel-key:}") String channelKey,
    @Value("${portone.api-key:}") String apiKey,
    @Value("${portone.api-secret:}") String secret
  ) {
    this(
      json,
      impCode,
      channelKey,
      apiKey,
      secret,
      URI.create("https://api.iamport.kr"),
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    );
  }

  PortOneGateway(
    ObjectMapper json,
    String impCode,
    String channelKey,
    String apiKey,
    String secret,
    URI base,
    HttpClient client
  ) {
    this.json = json;
    this.impCode = impCode.trim();
    this.channelKey = channelKey.trim();
    this.apiKey = apiKey.trim();
    this.secret = secret.trim();
    this.base = base;
    this.client = client;
  }

  public boolean ready() {
    return (
      impCode.matches("imp_?[A-Za-z0-9]+") &&
      !channelKey.isBlank() &&
      !apiKey.isBlank() &&
      !secret.isBlank()
    );
  }

  public Map<String, Object> configuration() {
    return Map.of("ready", ready(), "impCode", impCode, "channelKey", channelKey, "testOnly", true);
  }

  public void requireReady() {
    if (!ready()) throw new Problem(
      503,
      "포트원 V1 테스트 결제 설정이 준비되지 않았습니다. 운영자에게 문의해 주세요."
    );
  }

  public JsonNode payment(String id) {
    return normalize(request("GET", "/payments/find/" + encode(id), null));
  }

  public JsonNode paymentByProviderId(String impUid) {
    if (!impUid.matches("imp_[A-Za-z0-9]{1,64}")) {
      throw new Problem(400, "포트원 거래번호를 확인해 주세요.");
    }
    return normalize(request("GET", "/payments/" + encode(impUid), null));
  }

  public void cancel(String id, String reason, int amount) {
    var payment = payment(id);
    validate(payment, id, amount);
    if ("CANCELLED".equals(payment.path("status").asText())) return;
    if (!"PAID".equals(payment.path("status").asText())) throw new Problem(
      409,
      "전액 취소할 수 있는 결제 상태가 아닙니다."
    );
    request(
      "POST",
      "/payments/cancel",
      Map.of(
        "imp_uid",
        payment.path("providerPaymentId").asText(),
        "merchant_uid",
        id,
        "reason",
        reason,
        "amount",
        amount,
        "checksum",
        amount
      )
    );
  }

  public void validate(JsonNode payment, String id, int amount) {
    if (
      !id.equals(payment.path("id").asText()) ||
      !payment.path("sandbox").isBoolean() ||
      !payment.path("sandbox").asBoolean() ||
      !"html5_inicis".equals(payment.path("provider").asText()) ||
      !payment.path("providerPaymentId").asText().matches("imp_[A-Za-z0-9]+") ||
      !"KRW".equals(payment.path("currency").asText()) ||
      !payment.path("amount").path("total").isNumber() ||
      payment
        .path("amount")
        .path("total")
        .decimalValue()
        .compareTo(java.math.BigDecimal.valueOf(amount)) !=
      0
    ) throw new Problem(
      409,
      "주문 금액 또는 KG이니시스 테스트 결제 정보가 일치하지 않습니다. 관리자에게 문의해 주세요."
    );
  }

  JsonNode normalize(JsonNode raw) {
    var result = json.createObjectNode();
    result.put("id", raw.path("merchant_uid").asText());
    result.put("providerPaymentId", raw.path("imp_uid").asText());
    result.put("provider", raw.path("pg_provider").asText());
    result.set("sandbox", raw.path("sandbox"));
    result.put("currency", raw.path("currency").asText());
    result.putObject("amount").set("total", raw.path("amount"));
    String status = raw.path("status").asText();
    String normalized = switch (status) {
      case "paid" -> "PAID";
      case "failed" -> "FAILED";
      case "ready" -> "READY";
      case "cancelled" -> raw
          .path("cancel_amount")
          .decimalValue()
          .compareTo(raw.path("amount").decimalValue()) >=
        0
        ? "CANCELLED"
        : "PARTIAL_CANCELLED";
      default -> "PENDING";
    };
    // Some PGs report a partially cancelled transaction as paid.
    if (status.equals("paid") && raw.path("cancel_amount").decimalValue().signum() > 0) normalized =
      raw.path("cancel_amount").decimalValue().compareTo(raw.path("amount").decimalValue()) >= 0
        ? "CANCELLED"
        : "PARTIAL_CANCELLED";
    result.put("status", normalized);
    return result;
  }

  private JsonNode request(String method, String path, Object body) {
    requireReady();
    try {
      for (int attempt = 0; attempt < 2; attempt++) {
        String access = accessToken();
        var response = send(method, path, body, access);
        if (response.statusCode() == 401 && attempt == 0) {
          synchronized (this) {
            if (token.equals(access)) {
              token = "";
              tokenExpiry = 0;
            }
          }
          continue;
        }
        return unwrap(response, false);
      }
      throw new Problem(502, "포트원 인증에 실패했습니다. V1 API Key와 Secret을 확인해 주세요.");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new Problem(502, "결제 확인이 중단되었습니다.");
    } catch (java.io.IOException e) {
      throw new Problem(502, "결제 서비스에 연결할 수 없습니다. 잠시 후 다시 확인해 주세요.");
    }
  }

  private synchronized String accessToken() throws java.io.IOException, InterruptedException {
    if (!token.isEmpty() && Instant.now().getEpochSecond() + 60 < tokenExpiry) return token;
    var result = unwrap(
      send("POST", "/users/getToken", Map.of("imp_key", apiKey, "imp_secret", secret), null),
      true
    );
    String next = result.path("access_token").asText();
    long expiry = result.path("expired_at").asLong();
    if (next.isBlank() || expiry <= Instant.now().getEpochSecond()) throw new Problem(
      502,
      "포트원 V1 인증 응답을 확인할 수 없습니다."
    );
    token = next;
    tokenExpiry = expiry;
    return token;
  }

  private HttpResponse<String> send(String method, String path, Object body, String auth)
    throws java.io.IOException, InterruptedException {
    var b = HttpRequest.newBuilder(base.resolve(path))
      .timeout(Duration.ofSeconds(15))
      .header("Content-Type", "application/json");
    if (auth != null) b.header("Authorization", auth);
    if (body == null) b.GET();
    else b.method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
    return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
  }

  private JsonNode unwrap(HttpResponse<String> response, boolean authentication)
    throws java.io.IOException {
    if (response.statusCode() == 404) throw new Problem(
      409,
      "포트원에서 이 결제를 찾지 못했습니다. 카드 승인이 있다면 다시 결제하지 말고 포트원 거래번호로 확인해 주세요."
    );
    if (response.statusCode() < 200 || response.statusCode() >= 300) throw new Problem(
      502,
      "포트원 결제 요청에 실패했습니다. V1 설정과 결제 상태를 확인해 주세요."
    );
    var data = json.readTree(response.body());
    if (
      !data.path("code").isIntegralNumber() ||
      data.path("code").asInt() != 0 ||
      !data.path("response").isObject()
    ) throw new Problem(
      authentication ? 502 : 409,
      authentication
        ? "포트원 V1 인증에 실패했습니다. API Key와 Secret을 확인해 주세요."
        : "결제 정보를 확인할 수 없습니다. 주문 내역에서 다시 확인해 주세요."
    );
    return data.path("response");
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
