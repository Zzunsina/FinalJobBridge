package com.employment.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.employment.global.common.Problem;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;

class SmsServiceTests {
    static class TestClock extends Clock {
        Instant time = Instant.parse("2026-10-21T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return time; }
        void advance(long seconds) { time = time.plusSeconds(seconds); }
    }
    SmsGateway gateway;
    SmsService sms;
    TestClock clock;
    AtomicReference<String> code;
    @BeforeEach void setup() {
        gateway = mock(SmsGateway.class); clock = new TestClock(); code = new AtomicReference<>();
        doAnswer(call -> { code.set(call.getArgument(1)); return null; }).when(gateway).send(anyString(), anyString());
        sms = new SmsService(gateway, clock);
    }
    String send(String phone) { return sms.sendCode(phone, "test-address").get("challengeId").toString(); }
    @Test void proofIsBoundToPhoneExpiresAndIsConsumed() {
        String challenge = send("010-1234-5678");
        String token = sms.verifyCode("01012345678", challenge, code.get()).get("verificationToken").toString();
        sms.requireVerified("01012345678", token);
        assertThatThrownBy(() -> sms.requireVerified("01099998888", token)).isInstanceOf(Problem.class);
        assertThatThrownBy(() -> sms.verifyCode("01012345678", challenge, code.get())).isInstanceOf(Problem.class);
        sms.consumeAfterCommit(token);
        assertThatThrownBy(() -> sms.requireVerified("01012345678", token)).isInstanceOf(Problem.class);
        clock.advance(61); String secondChallenge = send("01012345678");
        String expiring = sms.verifyCode("01012345678", secondChallenge, code.get()).get("verificationToken").toString();
        clock.advance(600);
        assertThatThrownBy(() -> sms.requireVerified("01012345678", expiring)).isInstanceOf(Problem.class);
    }
    @Test void codeExpiresAndWrongAttemptsAreLimited() {
        String expired = send("01012345678"); clock.advance(180);
        assertThatThrownBy(() -> sms.verifyCode("01012345678", expired, code.get())).isInstanceOf(Problem.class);
        String challenge = send("01012345678"); String correct = code.get();
        String wrong = correct.equals("000000") ? "111111" : "000000";
        for(int i=0;i<5;i++) assertThatThrownBy(() -> sms.verifyCode("01012345678", challenge, wrong)).isInstanceOf(Problem.class);
        assertThatThrownBy(() -> sms.verifyCode("01012345678", challenge, correct)).isInstanceOf(Problem.class);
    }
    @Test void resendInvalidatesOldChallengeAndRequestsAreLimited() {
        String old = send("01012345678"); String oldCode = code.get();
        assertThatThrownBy(() -> send("01012345678")).isInstanceOfSatisfying(Problem.class, ex -> assertThat(ex.status).isEqualTo(429));
        clock.advance(60); send("01012345678");
        assertThatThrownBy(() -> sms.verifyCode("01012345678", old, oldCode)).isInstanceOf(Problem.class);
        send("01012345679"); send("01012345670"); send("01012345671");
        assertThatThrownBy(() -> send("01012345672")).isInstanceOfSatisfying(Problem.class, ex -> assertThat(ex.status).isEqualTo(429));
    }
    @Test void unavailableConfigurationFailsWithoutSending() {
        var disabled = new SolapiSmsGateway("", "", "");
        assertThatThrownBy(() -> disabled.send("01012345678", "123456")).isInstanceOfSatisfying(Problem.class, ex -> assertThat(ex.status).isEqualTo(503));
        assertThatThrownBy(() -> send("010abc12345678")).isInstanceOf(Problem.class);
    }
}
