package com.employment.job.controller;

import com.employment.job.service.CompanyPaymentService;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/company/payments")
public class CompanyPaymentController {

    private final CompanyPaymentService paymentService;

    public CompanyPaymentController(CompanyPaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(
        @AuthenticationPrincipal Object principal,
        @RequestBody Map<String, Object> body) {

        long userId = parseUserId(principal);

        // body 객체를 통째로 서비스로 전달 (서비스 내부에서 productType과 amount를 꺼내 쓰도록 함)
        var response = paymentService.createOrder(userId, body);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirmPayment(
        @AuthenticationPrincipal Object principal,
        @RequestBody Map<String, Object> body) {

        long userId = parseUserId(principal);
        paymentService.confirmPayment(userId, body);
        return ResponseEntity.ok().build();
    }

    // 인증 정보(principal)에서 사용자 ID를 안전하게 추출하는 메서드
    private long parseUserId(Object principal) {
        // 💡 임시 테스트용: 로그인이 안 풀려있거나 anonymousUser인 경우 테스트 계정 ID(1번)를 강제로 반환
        if (principal == null || "anonymousUser".equals(principal.toString())) {
            return 1L; // 테스트할 기업회원의 PK 번호
        }

        try {
            return Long.parseLong(principal.toString());
        } catch (Exception e) {
            return 1L; // 예외 발생 시에도 테스트용 ID 반환
        }
    }
}
