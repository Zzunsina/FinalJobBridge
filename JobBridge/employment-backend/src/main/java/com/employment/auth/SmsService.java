package com.employment.auth;

import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.response.SingleMessageSentResponse;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SmsService {

    private DefaultMessageService messageService;

    @Value("${coolsms.api-key}")
    private String apiKey;

    @Value("${coolsms.api-secret}")
    private String apiSecret;

    @Value("${coolsms.sender-number}")
    private String senderNumber;

    // 솔라피 서비스 초기화
    @PostConstruct
    public void init() {
        this.messageService = NurigoApp.INSTANCE.initialize(apiKey, apiSecret, "https://api.solapi.com");
    }

    private final Map<String, String> verificationStorage = new ConcurrentHashMap<>();
    private final Map<String, Boolean> verifiedPhones = new ConcurrentHashMap<>();

    // 1. 인증번호 전송 및 솔라피 API 호출
    public void sendCode(String phone) {
        // 6자리 랜덤 인증번호 생성
        String code = String.valueOf((int)(Math.random() * 899999) + 100000);

        // 메모리에 임시 저장 (유효시간 관리 필요시 추가 구현 가능)
        verificationStorage.put(phone, code);

        Message message = new Message();
        message.setFrom(senderNumber);
        message.setTo(phone);
        message.setText("[잡브릿지] 본인확인 인증번호는 [" + code + "] 입니다.");

        try {
            // 실제 솔라피 문자 발송 요청
            SingleMessageSentResponse response = this.messageService.sendOne(new SingleMessageSendingRequest(message));
            System.out.println("문자 전송 결과: " + response);
        } catch (Exception e) {
            System.err.println("문자 전송 실패: " + e.getMessage());
            throw new RuntimeException("문자 발송에 실패했습니다. 관리자에 문의해주세요.");
        }
    }

    // 2. 인증번호 검증
    public boolean verifyCode(String phone, String code) {
        String savedCode = verificationStorage.get(phone);
        if (savedCode != null && savedCode.equals(code)) {
            verificationStorage.remove(phone); // 사용된 인증번호 삭제
            verifiedPhones.put(phone, true);   // 인증 완료 상태 저장
            return true;
        }
        return false;
    }

    // 3. 인증 완료 여부 확인 (회원가입 시 체크용)
    public boolean isPhoneVerified(String phone) {
        return Boolean.TRUE.equals(verifiedPhones.get(phone));
    }
}
