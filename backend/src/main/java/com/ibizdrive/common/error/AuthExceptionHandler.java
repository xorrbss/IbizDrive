package com.ibizdrive.common.error;

import com.ibizdrive.auth.AccountLockedException;
import com.ibizdrive.auth.DuplicateEmailException;
import com.ibizdrive.auth.InvalidCredentialsException;
import com.ibizdrive.auth.password.InvalidPasswordResetTokenException;
import com.ibizdrive.auth.password.RateLimitExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 인증 도메인 예외 → HTTP 응답 매핑. docs/02 §7.4 / §8 에러 코드 표준에 맞춤.
 *
 * <p>{@link RestControllerAdvice}는 controller 계층에서 throw되는 예외를 가로채
 * {@link ErrorResponse}로 직렬화한다. CSRF/세션 인증 실패는 Spring Security 필터 단계에서
 * 별도 EntryPoint/AccessDeniedHandler가 처리하므로 본 advice 범위 밖.
 */
@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> invalid(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse.invalidCredentials());
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ErrorResponse> locked(AccountLockedException ex) {
        return ResponseEntity.status(HttpStatus.LOCKED)
            .body(ErrorResponse.accountLocked(ex.getRetryAfterSeconds()));
    }

    /**
     * ADR #41 회원가입 — 동일 email 활성 사용자 존재 시 409.
     */
    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> duplicateEmail(DuplicateEmailException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse.duplicateEmail());
    }

    /**
     * a1.5 비밀번호 재설정 — 토큰 무효 (만료/사용됨/미존재). 사유는 비공개.
     */
    @ExceptionHandler(InvalidPasswordResetTokenException.class)
    public ResponseEntity<ErrorResponse> invalidToken(InvalidPasswordResetTokenException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.invalidToken());
    }

    /**
     * auth-forgot-rate-limit (ADR #44) — forgot 호출 빈도 한도 초과.
     * 429 + {@code Retry-After} 헤더 + body {@code retryAfterSec}.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> rateLimited(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
            .body(ErrorResponse.rateLimitExceeded(ex.getRetryAfterSeconds()));
    }

    /**
     * Bean Validation 실패 (LoginRequest 형식 위반 등) → 400 VALIDATION_ERROR.
     * 첫 번째 위반 필드만 details로 노출 (docs/02 §8 details 스키마).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException ex) {
        var first = ex.getBindingResult().getFieldErrors().stream().findFirst();
        Map<String, String> details = first
            .map(fe -> Map.of("field", fe.getField(), "rule", String.valueOf(fe.getCode())))
            .orElse(Map.of());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.validationError(details));
    }
}
