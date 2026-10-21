package com.employment.global.common;

import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

public class Problem extends RuntimeException {

    public final int status;
    public final String field;

    public Problem(int status, String message) {
        super(message);
        this.status = status;
        this.field = null;
    }

    public Problem(int status, String message, String field) {
        super(message);
        this.status = status;
        this.field = field;
    }

    @RestControllerAdvice
    public static class Handler {

        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Handler.class);

        @ExceptionHandler(Problem.class)
        ResponseEntity<?> handle(Problem ex) {
            return response(ex.status, ex.getMessage(), ex.field);
        }

        @ExceptionHandler(DataIntegrityViolationException.class)
        ResponseEntity<?> conflict(DataIntegrityViolationException ex) {
            String detail = ex.getMostSpecificCause().getMessage();
            if (detail != null && detail.toLowerCase(java.util.Locale.ROOT).contains("login_id")) {
                return response(409, "이미 사용 중인 아이디입니다.", "loginId");
            }
            if (detail != null && detail.toLowerCase(java.util.Locale.ROOT).contains("uq_users_phone")) {
                return response(409, "이미 사용 중인 휴대폰번호입니다. 다른 번호를 입력해 주세요.");
            }
            return response(409, "중복 정보 또는 참조 중인 데이터입니다.");
        }

        @ExceptionHandler(
            {
                IllegalArgumentException.class,
                org.springframework.http.converter.HttpMessageNotReadableException.class,
                org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            }
        )
        ResponseEntity<?> invalid() {
            return response(400, "입력값을 확인해 주세요.");
        }

        @ExceptionHandler(Exception.class)
        ResponseEntity<?> unexpected(Exception ex) {
            String traceId = UUID.randomUUID().toString();
            // Do not log exception messages or request bodies: provider exceptions can contain secrets.
            Throwable cause = ex;
            for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause()) {
                log.error("Server failure traceId={} cause={} frames={}", traceId,
                    cause.getClass().getName(), java.util.Arrays.toString(cause.getStackTrace()));
                if (cause instanceof java.sql.SQLException sql) {
                    log.error("Database failure traceId={} sqlState={} vendorCode={}", traceId, sql.getSQLState(), sql.getErrorCode());
                }
            }
            return response(500, "서버 처리 중 오류가 발생했습니다.", null, traceId);
        }

        private ResponseEntity<?> response(int status, String message) {
            return response(status, message, null);
        }

        private ResponseEntity<?> response(int status, String message, String field) {
            return response(status, message, field, UUID.randomUUID().toString());
        }

        private ResponseEntity<?> response(int status, String message, String field, String traceId) {
            return ResponseEntity.status(status).body(
                Map.of(
                    "code",
                    "HTTP_" + status,
                    "message",
                    message,
                    "fieldErrors",
                    field == null ? java.util.List.of() : java.util.List.of(Map.of("field", field, "message", message)),
                    "traceId",
                    traceId
                )
            );
        }
    }
}
