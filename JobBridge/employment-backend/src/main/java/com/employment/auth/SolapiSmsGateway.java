package com.employment.auth;

import com.employment.global.common.Problem;
import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SolapiSmsGateway implements SmsGateway {
    private final String apiKey;
    private final String apiSecret;
    private final String senderNumber;

    public SolapiSmsGateway(@Value("${coolsms.api-key:}") String apiKey,
        @Value("${coolsms.api-secret:}") String apiSecret,
        @Value("${coolsms.sender-number:}") String senderNumber) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.senderNumber = senderNumber.replaceAll("[^0-9]", "");
    }

    @Override
    public void send(String phone, String code) {
        if (apiKey.isBlank() || apiSecret.isBlank() || !senderNumber.matches("[0-9]{8,11}")) {
            throw new Problem(503, "문자 인증 서비스가 설정되지 않았습니다. 관리자에게 문의해 주세요.");
        }
        var message = new Message();
        message.setFrom(senderNumber);
        message.setTo(phone);
        message.setText("[잡브릿지] 인증번호 [" + code + "]를 입력해 주세요. 3분 이내에 입력해 주세요.");
        try {
            var service = NurigoApp.INSTANCE.initialize(apiKey, apiSecret, "https://api.solapi.com");
            var result = service.sendOne(new SingleMessageSendingRequest(message));
            if (result == null || !"2000".equals(result.getStatusCode())) throw new IllegalStateException("SMS not accepted");
        } catch (Exception ex) {
            // Provider responses may contain phone numbers or credentials; never expose them.
            throw new Problem(502, "문자 발송에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }
}
