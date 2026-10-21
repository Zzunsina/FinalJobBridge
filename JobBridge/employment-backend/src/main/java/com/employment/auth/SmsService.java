package com.employment.auth;

import com.employment.global.common.Problem;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SmsService {
    private record Challenge(String phone, String codeHash, Instant expiresAt, int attempts) {}
    private record Proof(String phone, Instant expiresAt) {}
    private record Limit(Instant start, int count) {}
    private final SmsGateway gateway;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Challenge> challenges = new HashMap<>();
    private final Map<String, Proof> proofs = new HashMap<>();
    private final Map<String, Instant> lastSend = new HashMap<>();
    private final Map<String, Limit> limits = new HashMap<>();

    @Autowired
    public SmsService(SmsGateway gateway) { this(gateway, Clock.systemUTC()); }
    SmsService(SmsGateway gateway, Clock clock) { this.gateway = gateway; this.clock = clock; }

    public static String normalize(String raw) {
        String phone = raw == null ? "" : raw.replaceAll("[\\s-]", "");
        if (!phone.matches("01[016789][0-9]{7,8}")) throw new Problem(400, "올바른 휴대폰번호를 입력해 주세요.");
        return phone;
    }

    public synchronized Map<String, Object> sendCode(String raw, String remoteAddress) {
        String phone = normalize(raw);
        Instant now = clock.instant();
        challenges.values().removeIf(item -> !item.expiresAt.isAfter(now));
        proofs.values().removeIf(item -> !item.expiresAt.isAfter(now));
        lastSend.values().removeIf(time -> !time.plusSeconds(60).isAfter(now));
        limits.values().removeIf(limit -> !limit.start.plusSeconds(600).isAfter(now));
        if (lastSend.containsKey(phone)) throw new Problem(429, "인증번호는 1분 후 다시 요청해 주세요.");
        var limit = limits.get(remoteAddress);
        if (limit != null && limit.count >= 5) throw new Problem(429, "문자 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
        if (challenges.size() + proofs.size() + limits.size() > 10000) throw new Problem(503, "잠시 후 다시 시도해 주세요.");
        // Count failed provider attempts too; repeated failures must not bypass rate limits.
        lastSend.put(phone, now);
        limits.put(remoteAddress, new Limit(limit == null ? now : limit.start, limit == null ? 1 : limit.count + 1));
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1000000));
        gateway.send(phone, code);
        challenges.values().removeIf(item -> item.phone.equals(phone));
        proofs.values().removeIf(item -> item.phone.equals(phone));
        String challengeId = UUID.randomUUID().toString();
        challenges.put(challengeId, new Challenge(phone, AuthService.hash(challengeId + code), now.plusSeconds(180), 0));
        return Map.of("challengeId", challengeId, "expiresIn", 180, "resendAfter", 60);
    }

    public synchronized Map<String, Object> verifyCode(String raw, String challengeId, String code) {
        String phone = normalize(raw);
        var challenge = challenges.get(challengeId);
        Instant now = clock.instant();
        if (challenge == null || !challenge.phone.equals(phone) || !challenge.expiresAt.isAfter(now)) {
            throw new Problem(400, "인증번호가 일치하지 않거나 만료되었습니다.");
        }
        if (code == null || !code.matches("[0-9]{6}") || !challenge.codeHash.equals(AuthService.hash(challengeId + code))) {
            int attempts = challenge.attempts + 1;
            if (attempts >= 5) challenges.remove(challengeId);
            else challenges.put(challengeId, new Challenge(phone, challenge.codeHash, challenge.expiresAt, attempts));
            throw new Problem(400, attempts >= 5 ? "인증 시도 횟수를 초과했습니다. 인증번호를 다시 요청해 주세요." : "인증번호가 일치하지 않거나 만료되었습니다.");
        }
        challenges.remove(challengeId);
        String token = UUID.randomUUID().toString();
        proofs.put(token, new Proof(phone, now.plusSeconds(600)));
        return Map.of("verificationToken", token, "expiresIn", 600);
    }

    public synchronized void requireVerified(String phone, String token) {
        var proof = proofs.get(token);
        if (proof == null || !proof.phone.equals(phone) || !proof.expiresAt.isAfter(clock.instant())) {
            throw new Problem(400, "휴대폰 문자 인증을 완료해 주세요.", "phone");
        }
    }

    public void consumeAfterCommit(String token) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { consume(token); }
            });
        } else consume(token);
    }
    private synchronized void consume(String token) { proofs.remove(token); }
}
