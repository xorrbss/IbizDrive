package com.ibizdrive.auth;

/**
 * 5회 실패 누적 후 lockout 진입 — docs/03 §2.6, ADR #20, ADR #23.
 *
 * <p>HTTP 423 + body {@code { code: "ACCOUNT_LOCKED", retryAfterSec: <ttl> }}로 변환.
 */
public class AccountLockedException extends RuntimeException {
    private final long retryAfterSeconds;

    public AccountLockedException(long retryAfterSeconds) {
        super("Account locked");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
