package com.employment.auth;

import com.employment.global.common.*;
import jakarta.servlet.http.*;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService auth;

    private final SmsService smsService;

    public AuthController(AuthService auth, SmsService smsService) {
        this.auth = auth;
        this.smsService = smsService;
    }

    @GetMapping("/auth/csrf")
    Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken());
    }

    @GetMapping("/auth/login-id/availability")
    Map<String, Object> loginIdAvailability(@RequestParam String loginId) {
        return auth.loginIdAvailability(loginId);
    }

    @PostMapping("/auth/signup/{kind}")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> signup(@PathVariable String kind, @RequestBody Map<String, Object> body) {
        if (!kind.equals("job-seeker") && !kind.equals("company")) throw new Problem(
            404,
            "가입 유형을 확인해 주세요."
        );
        return auth.signup(body, kind.equals("company"));
    }

    @PostMapping("/auth/sms/send")
    Map<String, Object> sendSmsCode(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String phone = SmsService.normalize(Input.text(body, "phone", 30, true));
        auth.requireAvailablePhone(phone, null);
        return smsService.sendCode(phone, request.getRemoteAddr());
    }

    @PostMapping("/auth/sms/verify")
    Map<String, Object> verifySmsCode(@RequestBody Map<String, Object> body) {
        return smsService.verifyCode(Input.text(body, "phone", 30, true),
            Input.text(body, "challengeId", 36, true), Input.text(body, "code", 6, true));
    }

    @PostMapping("/auth/login")
    Map<String, Object> login(@RequestBody Map<String, Object> body, HttpServletResponse response) {
        return auth.login(body, response);
    }

    @PostMapping("/auth/admin/login")
    Map<String, Object> adminLogin(
        @RequestBody Map<String, Object> body,
        HttpServletResponse response
    ) {
        return auth.adminLogin(body, response);
    }

    @PostMapping("/auth/refresh")
    Map<String, Object> refresh(HttpServletRequest request, HttpServletResponse response) {
        return auth.refresh(request, response);
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request, HttpServletResponse response) {
        auth.logout(request, response);
    }

    @GetMapping("/me")
    Map<String, Object> me(HttpServletRequest request) {
        return auth.user(request);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void withdraw(@RequestBody Map<String, Object> body, HttpServletRequest request, HttpServletResponse response) {
        auth.withdraw(Database.number(auth.user(request), "userId"), body);
        auth.logout(request, response);
    }

    @PatchMapping("/me/password")
    void password(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        auth.changePassword(Database.number(auth.user(request), "userId"), body);
    }

    @PostMapping("/auth/password-reset/{action}")
    void reset() {
        throw new Problem(
            503,
            "비밀번호 재설정 메일 서비스가 아직 설정되지 않았습니다. 운영자에게 문의해 주세요."
        );
    }
}
