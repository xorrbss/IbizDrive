package com.ibizdrive.auth.password;

/**
 * forgot 호출 빈도 한도 초과 — auth-forgot-rate-limit 트랙, ADR #44.
 *
 * <p>HTTP 429 + {@code Retry-After} 헤더 + body {@code { code: "RATE_LIMIT_EXCEEDED",
 * retryAfterSec: <ttl> }}로 변환 (docs/02 §8 기존 코드 재사용).
 */
public class RateLimitExceededException extends RuntimeException {
    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("Rate limit exceeded");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
