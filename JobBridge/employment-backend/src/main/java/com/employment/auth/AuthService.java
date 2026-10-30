package com.employment.auth;

import static com.employment.global.common.Database.*;

import com.employment.global.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
// 👇 카카오 로그인을 위해 추가한 import 명시
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;

@Service
public class AuthService {

    private final Database db;
    private final ObjectMapper json;
    private final byte[] secret;
    private final boolean secure;
    private final SmsService smsService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final JavaMailSender mailSender;

    public AuthService(
        Database db,
        ObjectMapper json,
        @Value("${app.jwt-secret}") String secret,
        @Value("${app.secure-cookie}") boolean secure,
        SmsService smsService,
        JavaMailSender mailSender
    ) {
        if (secret.length() < 32) throw new IllegalArgumentException(
            "JWT_SECRET must be at least 32 characters"
        );
        this.db = db;
        this.json = json;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.secure = secure;
        this.smsService = smsService;
        this.mailSender = mailSender;
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

        setCookie(response, "accessToken", access, "/api", -1);
        setCookie(response, "refreshToken", refresh, "/api/auth", -1);
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
        db.update("INSERT INTO user_password_history(user_id,password_hash,used_until) VALUES(?,?,?)", userId, currentHash, Timestamp.from(Instant.now()));

        db.update(
            "UPDATE users SET password=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=?",
            encoder.encode(nextPassword),
            userId
        );
        db.update("DELETE FROM user_password_history WHERE user_id=? AND used_until<?", userId, cutoff);
    }

    public Map<String, Object> findId(Map<String, Object> body) {
        String name = Input.text(body, "name", 100, true);
        String email = Input.email(body, "email");

        var user = db.one(
            "SELECT login_id FROM users WHERE name=? AND email=? AND status='ACTIVE'",
            name, email
        );

        if (user == null) {
            throw new Problem(404, "일치하는 회원 정보를 찾을 수 없습니다.");
        }

        Object loginId = user.get("loginId") != null ? user.get("loginId") : user.get("login_id");
        return Map.of("loginId", loginId);
    }

    @Transactional
    public void forgotPassword(Map<String, Object> body) {
        String loginId = Input.text(body, "loginId", 30, true);
        String email = Input.email(body, "email");

        var user = db.one(
            "SELECT user_id FROM users WHERE login_id=? AND email=? AND status='ACTIVE'",
            loginId, email
        );

        if (user == null) {
            throw new Problem(404, "일치하는 회원 정보를 찾을 수 없습니다.");
        }

        long userId = number(user, "userId");
        String resetToken = UUID.randomUUID().toString();
        String tokenHash = hash(resetToken);
        Timestamp expiresAt = Timestamp.from(Instant.now().plusSeconds(1800));

        db.update("DELETE FROM password_reset_tokens WHERE user_id=?", userId);
        db.update(
            "INSERT INTO password_reset_tokens(user_id, token_hash, expires_at) VALUES(?,?,?)",
            userId, tokenHash, expiresAt
        );

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");

            helper.setTo(email);
            helper.setSubject("[잡브릿지] 비밀번호 재설정 안내");

            String resetUrl = "http://localhost/reset-password?token=" + resetToken;
            String htmlContent =
                "<div style=\"font-family: 'Apple SD Gothic Neo', 'Malgun Gothic', sans-serif; padding: 20px; max-width: 600px; margin: 0 auto; border: 1px solid #e0e0e0; border-radius: 8px;\">" +
                    "  <h2 style=\"color: #333;\">비밀번호 재설정 안내</h2>" +
                    "  <p style=\"font-size: 16px; color: #555;\">안녕하세요, <strong>잡브릿지</strong>입니다.</p>" +
                    "  <p style=\"font-size: 16px; color: #555;\">아래 버튼을 클릭하여 비밀번호 재설정 페이지로 이동해 주세요.</p>" +
                    "  <div style=\"text-align: center; margin: 30px 0;\">" +
                    "    <a href=\"" + resetUrl + "\" target=\"_blank\" style=\"background-color: #007bff; color: #ffffff; padding: 12px 24px; text-decoration: none; border-radius: 4px; font-weight: bold; display: inline-block;\">비밀번호 재설정하기</a>" +
                    "  </div>" +
                    "  <p style=\"font-size: 14px; color: #888;\">본인이 요청하지 않은 경우 이 메일을 무시해 주세요.</p>" +
                    "</div>";

            helper.setText(htmlContent, true);
            mailSender.send(mimeMessage);

        } catch (Exception e) {
            throw new Problem(500, "이메일 전송 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    @Transactional
    public void resetPassword(Map<String, Object> body) {
        String token = Input.text(body, "token", 255, true);
        String newPassword = Input.password(body, "newPassword");

        String tokenHash = hash(token);

        var tokenRow = db.one(
            "SELECT * FROM password_reset_tokens WHERE token_hash=? AND expires_at > CURRENT_TIMESTAMP AND used_at IS NULL",
            tokenHash
        );

        if (tokenRow == null) {
            throw new Problem(400, "유효하지 않거나 만료된 비밀번호 재설정 링크입니다.");
        }

        long userId = number(tokenRow, "userId");

        db.update(
            "UPDATE users SET password=?, updated_at=CURRENT_TIMESTAMP WHERE user_id=?",
            encoder.encode(newPassword),
            userId
        );

        db.update("UPDATE password_reset_tokens SET used_at=CURRENT_TIMESTAMP WHERE token_hash=?", tokenHash);
        db.update("UPDATE auth_sessions SET revoked=TRUE WHERE user_id=?", userId);
    }

    // 261029_전진영 카카오 키(차후에 환경변수로 처리 예정)
    @Value("${kakao.client-id}")
    private String KAKAO_CLIENT_ID;
    @Value("${kakao.client-secret}")
    private String KAKAO_SECRET_KEY;
    @Value("${kakao.redirect-uri}")
    private String KAKAO_REDIRECT_URI;

    @Transactional
    public void kakaoLogin(String code, HttpServletResponse response) {
        System.out.println("kakaoLogin 시작");
        try {
            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", "authorization_code");
            params.add("client_id", KAKAO_CLIENT_ID); // 본인의 REST API 키 입력
            params.add("client_secret", KAKAO_SECRET_KEY);
            params.add("redirect_uri", KAKAO_REDIRECT_URI);
            params.add("code", code);

            System.out.println("요청데이터 담아서 요청객체에 담기");
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            System.out.println("카카오톡 토큰데이터 요청하기");
            ResponseEntity<Map> tokenResponse = restTemplate.postForEntity(
                "https://kauth.kakao.com/oauth/token", request, Map.class
            );
            System.out.println("tokenResponse값: " + tokenResponse);
            String accessToken = (String) tokenResponse.getBody().get("access_token");
            System.out.println("accessToken값: " + accessToken);

            HttpHeaders profileHeaders = new HttpHeaders();
            profileHeaders.set("Authorization", "Bearer " + accessToken);
            HttpEntity<Void> profileRequest = new HttpEntity<>(profileHeaders);


            ResponseEntity<Map> profileResponse = restTemplate.exchange(
                "https://kapi.kakao.com/v2/user/me",
                HttpMethod.GET,
                profileRequest,
                Map.class
            );

            Map<String, Object> kakaoAccount = (Map<String, Object>) profileResponse.getBody().get("kakao_account");
            System.out.println("kakaoAccount: "+ kakaoAccount);
            if(kakaoAccount == null){ throw new Exception("회원 정보를 불러오지 못했습니다.");}


            String phone_number = (String) kakaoAccount.get("phone_number");
            String email = kakaoAccount != null ? (String) kakaoAccount.get("email") : null;
            String nickname = kakaoAccount != null ? (String) kakaoAccount.get("name") : "카카오사용자";
            String phone = kakaoAccount == null ?  "01000000000" : phone_number == null || phone_number.equals("") ? "01000000000" : "0"+phone_number.replace("+82","").replace("-","").replaceAll("[\\s]","");
            String kakaoIdStr = String.valueOf(profileResponse.getBody().get("id"));
            String birthyear = (String)kakaoAccount.get("birthyear");
            String birthday = (String)kakaoAccount.get("birthday");
            String birth_date = birthyear + birthday;

            if (email == null) {
                email = kakaoIdStr + "@kakao.social";
            }

            System.out.println("DB select전");
            Map<String, Object> user = db.one("SELECT * FROM users WHERE login_id=? AND status='ACTIVE'", "k_"+kakaoIdStr);
            System.out.println("DB select후 user: " +user);

            long userId;
            if (user == null) {
                String loginId = "k_" + kakaoIdStr;
                userId = db.insert(
                    "users",
                    fields(
                        "login_id", loginId,
                        "email", email,
                        "password", encoder.encode("kakaopass"),
                        "name", nickname,
                        "phone", phone,
                        "role", "JOB_SEEKER",
                        "birth_date" , birth_date,
                        "gender" , ((String) kakaoAccount.get("gender")).toUpperCase(),
                        "status", "ACTIVE"
                    )
                );
                db.insert("job_seekers", fields("user_id", userId));
            } else {
                userId = number(user, "userId");
            }

            issue(
                userId,
                UUID.randomUUID().toString(),
                Instant.now().plusSeconds(604800),
                response,
                true
            );
        } catch (Exception e) {
            e.printStackTrace();
            throw new Problem(500, "카카오 로그인 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
}
