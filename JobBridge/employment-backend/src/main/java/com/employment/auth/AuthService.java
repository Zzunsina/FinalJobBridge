package com.employment.auth;

import static com.employment.global.common.Database.*;

import com.employment.global.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final Database db;
    private final ObjectMapper json;
    private final byte[] secret;
    private final boolean secure;
    private final SmsService smsService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(
        Database db,
        ObjectMapper json,
        @Value("${app.jwt-secret}") String secret,
        @Value("${app.secure-cookie}") boolean secure,
        SmsService smsService
    ) {
        if (secret.length() < 32) throw new IllegalArgumentException(
            "JWT_SECRET must be at least 32 characters"
        );
        this.db = db;
        this.json = json;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.secure = secure;
        this.smsService = smsService;
    }

    public Map<String, Object> publicUser(long id) {
        return db.one(
            "SELECT user_id,login_id,email,name,phone,birth_date,gender,role,status FROM users WHERE user_id=?",
            id
        );
    }

    public Map<String, Object> user(HttpServletRequest request) {
        Object user = request.getAttribute("user");
        if (user == null) throw new Problem(401, "로그인이 필요합니다.");
        @SuppressWarnings("unchecked")
        var result = (Map<String, Object>) user;
        return result;
    }

    public long require(HttpServletRequest request, String role) {
        var user = user(request);
        if (!role.equals(user.get("role"))) throw new Problem(
            403,
            "이 기능을 사용할 권한이 없습니다."
        );
        return number(user, "userId");
    }

    public Map<String, Object> loginIdAvailability(String value) {
        String loginId = value.strip().toLowerCase(Locale.ROOT);
        if (!loginId.matches("[a-z0-9]{4,30}")) throw new Problem(400, "아이디는 영문과 숫자로 4~30자를 입력해 주세요.", "loginId");
        boolean available = db.list("SELECT user_id FROM users WHERE login_id=?", loginId).isEmpty();
        return Map.of("available", available, "message", available ? "사용할 수 있는 아이디입니다." : "이미 사용 중인 아이디입니다.");
    }

    public void requireAvailablePhone(String phone, Long currentUserId) {
        var rows = currentUserId == null
            ? db.list("SELECT user_id FROM users WHERE phone=?", phone)
            : db.list("SELECT user_id FROM users WHERE phone=? AND user_id<>?", phone, currentUserId);
        if (!rows.isEmpty()) throw new Problem(409, "이미 사용 중인 휴대폰번호입니다. 다른 번호를 입력해 주세요.");
    }

    @Transactional
    public Map<String, Object> signup(Map<String, Object> body, boolean company) {
        String loginId = Input.text(body, "loginId", 30, true).strip().toLowerCase(Locale.ROOT);
        if (!loginId.matches("[a-z0-9]{4,30}")) {
            throw new Problem(400, "아이디는 영문과 숫자만 사용하여 4~30자로 입력해 주세요.");
        }
        if (!db.list("SELECT user_id FROM users WHERE login_id=?", loginId).isEmpty()) {
            throw new Problem(409, "이미 사용 중인 아이디입니다.", "loginId");
        }
        String email = Input.email(body, "email");
        String birthText = Input.text(body, "birthDate", 8, true);
        LocalDate birth = Input.birthDate(birthText);
        String gender = Input.text(body, "gender", 10, true);
        if (!Set.of("MALE", "FEMALE").contains(gender)) throw new Problem(
            400,
            "성별을 선택해 주세요."
        );
        String phone = Input.text(body, "phone", 30, true).replaceAll("[^0-9]", "");
        if (!phone.matches("[0-9]{10,11}")) throw new Problem(
            400,
            "휴대폰번호는 10~11자리여야 합니다."
        );
        requireAvailablePhone(phone, null);
        if (!Boolean.TRUE.equals(body.get("termsAccepted")) || !Boolean.TRUE.equals(body.get("privacyAccepted"))) {
            throw new Problem(400, "필수 이용약관 및 개인정보 수집·이용에 동의해 주세요.");
        }
        String agreementVersion = "2026-10-21";
        if (!agreementVersion.equals(body.get("agreementVersion"))) {
            throw new Problem(409, "약관이 변경되었습니다. 페이지를 새로고침하고 다시 확인해 주세요.");
        }
        if (body.containsKey("marketingAccepted") && !(body.get("marketingAccepted") instanceof Boolean)) {
            throw new Problem(400, "광고성 정보 수신 동의 여부를 확인해 주세요.");
        }
        String smsToken = Input.text(body, "smsVerificationToken", 36, true);
        smsService.requireVerified(phone, smsToken);
        long id = db.insert(
            "users",
            fields(
                "login_id",
                loginId,
                "birth_date",
                birth,
                "gender",
                gender,
                "email",
                email,
                "password",
                encoder.encode(Input.password(body, "password")),
                "name",
                Input.text(body, "name", 100, true),
                "phone",
                phone,
                "role",
                company ? "COMPANY" : "JOB_SEEKER"
            )
        );
        if (company) {
            String business = Input.text(body, "businessNumber", 20, true).replaceAll("[^0-9]", "");
            if (!business.matches("[0-9]{10}")) throw new Problem(
                400,
                "사업자번호는 10자리여야 합니다."
            );
            db.insert(
                "companies",
                fields(
                    "user_id",
                    id,
                    "company_name",
                    Input.text(body, "companyName", 200, true),
                    "business_number",
                    business
                )
            );
        } else db.insert("job_seekers", fields("user_id", id, "birth_date", birth));
        db.update(
            "INSERT INTO user_signup_consents(user_id,terms_version,privacy_version,marketing_version,terms_accepted,privacy_accepted,marketing_accepted) VALUES(?,?,?,?,TRUE,TRUE,?)",
            id, agreementVersion, agreementVersion, agreementVersion, Boolean.TRUE.equals(body.get("marketingAccepted"))
        );
        smsService.consumeAfterCommit(smsToken);
        return publicUser(id);
    }

    @Transactional
    public Map<String, Object> login(Map<String, Object> body, HttpServletResponse response) {
        return loginInternal(body, response, false);
    }

    @Transactional
    public Map<String, Object> adminLogin(Map<String, Object> body, HttpServletResponse response) {
        return loginInternal(body, response, true);
    }

    private Map<String, Object> loginInternal(
        Map<String, Object> body,
        HttpServletResponse response,
        boolean adminOnly
    ) {
        String memberType = adminOnly ? "ADMIN" : Input.text(body, "memberType", 20, true);
        if (!adminOnly && !Set.of("JOB_SEEKER", "COMPANY").contains(memberType)) {
            throw new Problem(400, "개인 또는 기업 회원을 선택해 주세요.");
        }
        String identifier = Input.text(body, "loginId", 30, true).strip().toLowerCase(Locale.ROOT);
        var rows = db.list("SELECT * FROM users WHERE login_id=?", identifier);
        String password = Input.text(body, "password", 64, true);
        if (
            rows.isEmpty() ||
            !encoder.matches(password, rows.get(0).get("password").toString()) ||
            !"ACTIVE".equals(rows.get(0).get("status"))
        ) {
            throw new Problem(401, "아이디 또는 비밀번호를 확인해 주세요.");
        }
        String role = rows.get(0).get("role").toString();
        if (!memberType.equals(role)) {
            if (!adminOnly && "COMPANY".equals(role)) {
                throw new Problem(401, "기업회원 계정입니다. 기업 탭을 선택해 로그인해 주세요.");
            }
            if (!adminOnly && "JOB_SEEKER".equals(role)) {
                throw new Problem(401, "개인회원 계정입니다. 개인 탭을 선택해 로그인해 주세요.");
            }
            throw new Problem(401, "아이디 또는 비밀번호를 확인해 주세요.");
        }
        long userId = number(rows.get(0), "userId");
        issue(
            userId,
            UUID.randomUUID().toString(),
            Instant.now().plusSeconds(604800),
            response,
            true
        );
        return publicUser(userId);
    }

    @Transactional
    public Map<String, Object> refresh(HttpServletRequest request, HttpServletResponse response) {
        var session = validate(cookie(request, "refreshToken"), "refresh", true);
        long id = number(session, "userId");
        issue(
            id,
            session.get("sessionId").toString(),
            Instant.parse(session.get("refreshExpiresAt").toString()),
            response,
            false
        );
        return publicUser(id);
    }

    public void authenticate(HttpServletRequest request) {
        String token = cookie(request, "accessToken");
        if (token == null) return;
        try {
            var session = validate(token, "access", false);
            request.setAttribute("user", publicUser(number(session, "userId")));
        } catch (Problem ignored) {
            /* The endpoint returns 401 if authentication is required. */
        }
    }

    private Map<String, Object> validate(String token, String type, boolean lock) {
        try {
            if (token == null) throw new IllegalArgumentException();
            String[] pieces = token.split("\\.");
            if (
                pieces.length != 3 ||
                !MessageDigest.isEqual(sign(pieces[0] + "." + pieces[1]), decode(pieces[2]))
            ) {
                throw new IllegalArgumentException();
            }
            var claims = json.readTree(decode(pieces[1]));
            if (
                !type.equals(claims.path("type").asText()) ||
                claims.path("exp").asLong() <= Instant.now().getEpochSecond()
            ) {
                throw new IllegalArgumentException();
            }
            var session = db.one(
                "SELECT * FROM auth_sessions WHERE session_id=?" + (lock ? " FOR UPDATE" : ""),
                claims.path("sid").asText()
            );
            if (
                Boolean.TRUE.equals(session.get("revoked")) ||
                !hash(token).equals(session.get(type + "Hash")) ||
                !"ACTIVE".equals(publicUser(number(session, "userId")).get("status")) ||
                !Long.toString(number(session, "userId")).equals(claims.path("sub").asText())
            ) {
                throw new IllegalArgumentException();
            }
            return session;
        } catch (Exception ex) {
            throw new Problem(401, "로그인이 만료되었습니다.");
        }
    }

    private void issue(
        long userId,
        String sid,
        Instant end,
        HttpServletResponse response,
        boolean create
    ) {
        Instant accessEnd = Instant.now().plusSeconds(1800);
        String access = token(userId, sid, "access", accessEnd);
        String refresh = token(userId, sid, "refresh", end);
        if (create) {
            db.update(
                "INSERT INTO auth_sessions(session_id,user_id,access_hash,refresh_hash,access_expires_at,refresh_expires_at) VALUES(?,?,?,?,?,?)",
                sid,
                userId,
                hash(access),
                hash(refresh),
                Timestamp.from(accessEnd),
                Timestamp.from(end)
            );
        } else db.update(
            "UPDATE auth_sessions SET access_hash=?,refresh_hash=?,access_expires_at=? WHERE session_id=?",
            hash(access),
            hash(refresh),
            Timestamp.from(accessEnd),
            sid
        );
        setCookie(response, "accessToken", access, "/api", 1800);
        setCookie(
            response,
            "refreshToken",
            refresh,
            "/api/auth",
            Math.max(0, Duration.between(Instant.now(), end).getSeconds())
        );
    }

    private String token(long id, String sid, String type, Instant end) {
        try {
            String header = encode(
                "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8)
            );
            String payload = encode(
                json.writeValueAsBytes(
                    Map.of(
                        "sub",
                        Long.toString(id),
                        "sid",
                        sid,
                        "jti",
                        UUID.randomUUID().toString(),
                        "type",
                        type,
                        "iat",
                        Instant.now().getEpochSecond(),
                        "exp",
                        end.getEpochSecond()
                    )
                )
            );
            return header + "." + payload + "." + encode(sign(header + "." + payload));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private byte[] sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    public static String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() != null) for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private void setCookie(
        HttpServletResponse response,
        String name,
        String value,
        String path,
        long age
    ) {
        response.addHeader(
            "Set-Cookie",
            ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(path)
                .maxAge(age)
                .build()
                .toString()
        );
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        for (String type : List.of("access", "refresh")) {
            String token = cookie(request, type + "Token");
            if (token != null) db.update(
                "UPDATE auth_sessions SET revoked=TRUE WHERE " + type + "_hash=?",
                hash(token)
            );
        }
        setCookie(response, "accessToken", "", "/api", 0);
        setCookie(response, "refreshToken", "", "/api/auth", 0);
    }

    @Transactional
    public void withdraw(long userId, Map<String, Object> body) {
        var user = db.one("SELECT role,status,password FROM users WHERE user_id=? FOR UPDATE", userId);
        if (!Set.of("JOB_SEEKER", "COMPANY").contains(user.get("role"))) {
            throw new Problem(403, "개인·기업 회원만 탈퇴할 수 있습니다.");
        }
        if (!"ACTIVE".equals(user.get("status"))) throw new Problem(409, "탈퇴할 수 없는 계정입니다.");
        if (!encoder.matches(Input.text(body, "currentPassword", 64, true), user.get("password").toString())) {
            throw new Problem(400, "현재 비밀번호가 일치하지 않습니다.");
        }
        String applications;
        if ("COMPANY".equals(user.get("role"))) {
            applications = "SELECT application_id FROM applications WHERE job_id IN (SELECT job_id FROM job_postings WHERE company_id IN (SELECT company_id FROM companies WHERE user_id=?))";
            db.update("UPDATE job_postings SET status='CLOSED',updated_at=CURRENT_TIMESTAMP WHERE company_id IN (SELECT company_id FROM companies WHERE user_id=?) AND status IN ('DRAFT','PENDING','PUBLISHED')", userId);
        } else {
            applications = "SELECT application_id FROM applications WHERE job_seeker_id IN (SELECT job_seeker_id FROM job_seekers WHERE user_id=?)";
            db.update("UPDATE employment_links SET status='CLOSED' WHERE job_seeker_id IN (SELECT job_seeker_id FROM job_seekers WHERE user_id=?) AND status NOT IN ('EMPLOYED','CLOSED')", userId);
        }
        db.update("UPDATE interviews SET status='CANCELED' WHERE status='SCHEDULED' AND application_id IN (" + applications + ")", userId);
        // Use the ownership predicate directly to remain compatible with MySQL's update rules.
        String ownership = "COMPANY".equals(user.get("role"))
            ? "job_id IN (SELECT job_id FROM job_postings WHERE company_id IN (SELECT company_id FROM companies WHERE user_id=?))"
            : "job_seeker_id IN (SELECT job_seeker_id FROM job_seekers WHERE user_id=?)";
        db.update("UPDATE applications SET status='CANCELED',updated_at=CURRENT_TIMESTAMP WHERE " + ownership + " AND status IN ('APPLIED','UNDER_REVIEW','DOCUMENT_PASSED','INTERVIEW')", userId);
        db.update("UPDATE users SET status='WITHDRAWN',updated_at=CURRENT_TIMESTAMP WHERE user_id=?", userId);
        db.update("UPDATE auth_sessions SET revoked=TRUE WHERE user_id=?", userId);
        db.update("DELETE FROM user_profile_photos WHERE user_id=?", userId);
        db.update("DELETE FROM company_images WHERE company_id IN (SELECT company_id FROM companies WHERE user_id=?)", userId);
    }

    @Transactional
    public void changePassword(long userId, Map<String, Object> body) {
        var user = db.one("SELECT password FROM users WHERE user_id=? FOR UPDATE", userId);
        if (
            !encoder.matches(
                Input.text(body, "currentPassword", 64, true),
                user.get("password").toString()
            )
        ) {
            throw new Problem(400, "현재 비밀번호가 일치하지 않습니다.");
        }
        String nextPassword = Input.password(body, "password");
        String currentHash = user.get("password").toString();
        Timestamp cutoff = Timestamp.from(ZonedDateTime.now(ZoneId.of("Asia/Seoul")).minusMonths(6).toInstant());
        if (encoder.matches(nextPassword, currentHash)) {
            throw new Problem(400, "현재 비밀번호 또는 최근 6개월 이내 사용한 비밀번호는 다시 사용할 수 없습니다.");
        }
        var history = db.list("SELECT password_hash FROM user_password_history WHERE user_id=? AND used_until>=?", userId, cutoff);
        for (var previous : history) {
            if (encoder.matches(nextPassword, previous.get("passwordHash").toString())) {
                throw new Problem(400, "현재 비밀번호 또는 최근 6개월 이내 사용한 비밀번호는 다시 사용할 수 없습니다.");
            }
        }
        // Record when the old password stopped being used, including long-lived passwords.
        db.update("INSERT INTO user_password_history(user_id,password_hash,used_until) VALUES(?,?,?)", userId, currentHash, Timestamp.from(Instant.now()));
        db.update(
            "UPDATE users SET password=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=?",
            encoder.encode(nextPassword),
            userId
        );
        db.update("DELETE FROM user_password_history WHERE user_id=? AND used_until<?", userId, cutoff);
        db.update("UPDATE auth_sessions SET revoked=TRUE WHERE user_id=?", userId);
    }
}
