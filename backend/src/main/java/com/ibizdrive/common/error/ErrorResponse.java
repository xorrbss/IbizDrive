package com.ibizdrive.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 인증 도메인 에러 응답 — docs/02 §7.4 (인증 specific 응답 shape).
 *
 * <p>일반 API의 error envelope ({@code { error: { code, message, details } }}, §7.2)와
 * 별개로, 인증 endpoint는 docs/02 §7.4에 명시된 flat shape을 따른다:
 * <ul>
 *   <li>{@code 401 } → {@code { code: "UNAUTHORIZED", reason: "INVALID_CREDENTIALS" }}</li>
 *   <li>{@code 423 } → {@code { code: "ACCOUNT_LOCKED", retryAfterSec: <ttl> }}</li>
 *   <li>{@code 403 } → {@code { code: "CSRF_MISMATCH" }} (Spring Security가 직접 발급)</li>
 *   <li>{@code 400 } → {@code { code: "VALIDATION_ERROR", details: { field, rule } }}</li>
 * </ul>
 *
 * <p>{@link JsonInclude.Include#NON_NULL}로 미사용 필드는 응답에서 생략.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    String code,
    String reason,
    Long retryAfterSec,
    Object details
) {

    public static ErrorResponse invalidCredentials() {
        return new ErrorResponse("UNAUTHORIZED", "INVALID_CREDENTIALS", null, null);
    }

    public static ErrorResponse accountLocked(long retryAfterSec) {
        return new ErrorResponse("ACCOUNT_LOCKED", null, retryAfterSec, null);
    }

    public static ErrorResponse validationError(Object details) {
        return new ErrorResponse("VALIDATION_ERROR", null, null, details);
    }

    /**
     * ADR #41 회원가입 — 동일 이메일이 이미 활성 사용자로 존재.
     * envelope: {@code { code: "CONFLICT", reason: "DUPLICATE_EMAIL" }}.
     */
    public static ErrorResponse duplicateEmail() {
        return new ErrorResponse("CONFLICT", "DUPLICATE_EMAIL", null, null);
    }

    /**
     * a1.5 비밀번호 재설정 — 토큰이 만료/사용됨/존재하지 않음 (이유 비공개 — enumeration 방지).
     */
    public static ErrorResponse invalidToken() {
        return new ErrorResponse("INVALID_TOKEN", null, null, null);
    }
}
