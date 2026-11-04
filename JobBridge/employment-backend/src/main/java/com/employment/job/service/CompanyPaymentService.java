package com.employment.job.service;

import com.employment.global.common.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
@Transactional
public class CompanyPaymentService {

    private final Database db;
    private final JdbcTemplate jdbcTemplate; // 💡 스프링 표준 JdbcTemplate 직접 주입
    private final EmploymentService employmentService;

    @Value("${toss.secret-key}")
    private String tossSecretKey;

    public CompanyPaymentService(Database db, JdbcTemplate jdbcTemplate, EmploymentService employmentService) {
        this.db = db;
        this.jdbcTemplate = jdbcTemplate;
        this.employmentService = employmentService;
    }

    public Map<String, Object> createOrder(long user, Map<String, Object> body) {
        System.out.println(">>> [DEBUG] request body: " + body);

        var companyResult = employmentService.company(user, true);
        if (companyResult == null) {
            throw new Problem(400, "연결된 기업 회원 정보가 존재하지 않습니다.");
        }

        long companyId = 0;
        if (companyResult instanceof Map) {
            companyId = ((Number) ((Map<?, ?>) companyResult).get("companyId")).longValue();
        } else {
            companyId = Long.parseLong(companyResult.toString());
        }

        String productType = body.get("productType") != null ? body.get("productType").toString() : "RESUME_READ_50";

        int amount = 0;
        if (body.get("amount") != null) {
            amount = Integer.parseInt(body.get("amount").toString());
        } else if (body.get("price") != null) {
            amount = Integer.parseInt(body.get("price").toString());
        }

        String orderId = "ORD" + System.currentTimeMillis() + "_" + (int)(Math.random() * 9000 + 1000);
        String orderName = getProductName(productType);

        // 💡 NullPointerException 원흉인 Database.insert 대신 안전한 JdbcTemplate 사용
        String sql = "INSERT INTO company_orders (order_id, company_id, product_type, amount, status) VALUES (?, ?, ?, ?, 'PENDING')";
        jdbcTemplate.update(sql, orderId, companyId, productType, amount);

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("orderId", orderId);
        responseMap.put("amount", amount);
        responseMap.put("orderName", orderName);
        return responseMap;
    }

    public void confirmPayment(long user, Map<String, Object> body) {
        // 💡 1. 시크릿 키가 정상 주입되었는지 확인용 로그 (앞 6글자만 출력)
        System.out.println(">>> [DEBUG] tossSecretKey prefix: " + (tossSecretKey != null ? tossSecretKey.substring(0, Math.min(tossSecretKey.length(), 6)) + "..." : "null"));

        long companyId = 0;
        var companyResult = employmentService.company(user, true);
        if (companyResult instanceof Map) {
            companyId = ((Number) ((Map<?, ?>) companyResult).get("companyId")).longValue();
        }

        String paymentKey = body.get("paymentKey") != null ? body.get("paymentKey").toString() : "";
        String orderId = body.get("orderId") != null ? body.get("orderId").toString() : "";
        int amount = body.get("amount") != null ? Integer.parseInt(body.get("amount").toString()) : 0;

        var order = db.one("SELECT * FROM company_orders WHERE order_id=? AND company_id=?", orderId, companyId);
        if (order == null) {
            throw new Problem(404, "존재하지 않는 주문입니다.");
        }

        RestTemplate restTemplate = new RestTemplate();
        String url = "https://api.tosspayments.com/v1/payments/confirm";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 시크릿 키 뒤에 콜론(:)을 붙여 Base64 인코딩
        String authHeader = "Basic " + Base64.getEncoder().encodeToString((tossSecretKey.trim() + ":").getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", authHeader);

        Map<String, Object> tossBody = new HashMap<>();
        tossBody.put("paymentKey", paymentKey);
        tossBody.put("orderId", orderId);
        tossBody.put("amount", amount);

        HttpEntity<?> entity = new HttpEntity<>(tossBody, headers);

        try {
            ResponseEntity response = restTemplate.postForEntity(url, entity, Map.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                jdbcTemplate.update("UPDATE company_orders SET status='SUCCESS', payment_key=?, approved_at=CURRENT_TIMESTAMP WHERE order_id=?", paymentKey, orderId);
                grantCompanyEntitlement(companyId, order.get("product_type") != null ? order.get("product_type").toString() : order.get("productType").toString());
            }
        } catch (HttpClientErrorException e) {
            jdbcTemplate.update("UPDATE company_orders SET status='FAILED' WHERE order_id=?", orderId);
            // 💡 2. 토스가 거부한 실제 사유를 백엔드 콘솔에 상세히 출력
            System.err.println(">>> [TOSS ERROR RESPONSE]: " + e.getResponseBodyAsString());
            throw new Problem(400, "토스 결제 승인 실패: " + e.getResponseBodyAsString());
        }
    }

    private String getProductName(String productType) {
        if ("RESUME_READ_50".equals(productType)) return "인재 이력서 열람권 50건";
        if ("JOB_POSTING_TOP".equals(productType)) return "채용공고 프리미엄 상단 노출 (30일)";
        return "기업회원 서비스 이용권";
    }

    private void grantCompanyEntitlement(long companyId, String productType) {
        var existing = db.one("SELECT * FROM company_entitlements WHERE company_id=?", companyId);

        if ("RESUME_READ_50".equals(productType)) {
            if (existing == null) {
                jdbcTemplate.update("INSERT INTO company_entitlements (company_id, ticket_count) VALUES (?, 50)", companyId);
            } else {
                jdbcTemplate.update("UPDATE company_entitlements SET ticket_count = ticket_count + 50, updated_at=CURRENT_TIMESTAMP WHERE company_id=?", companyId);
            }
        } else if ("JOB_POSTING_TOP".equals(productType)) {
            if (existing == null) {
                jdbcTemplate.update("INSERT INTO company_entitlements (company_id, premium_until, updated_at) VALUES (?, DATE_ADD(NOW(), INTERVAL 30 DAY), CURRENT_TIMESTAMP)", companyId);
            } else {
                jdbcTemplate.update("UPDATE company_entitlements SET premium_until = DATE_ADD(COALESCE(premium_until, NOW()), INTERVAL 30 DAY), updated_at=CURRENT_TIMESTAMP WHERE company_id=?", companyId);
            }
        }
    }
}
