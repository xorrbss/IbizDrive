package com.ibizdrive.auth.password;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ForgotPasswordRateLimiter} 단위 테스트.
 *
 * <p>정책 (auth-forgot-rate-limit plan):
 * <ul>
 *   <li>email + IP 두 키 독립 버킷, 어느 한쪽이라도 한도 초과면 차단(OR).</li>
 *   <li>고정 윈도우 60초, 한도 1회.</li>
 *   <li>차단 시 {@link Instant} 갱신 없음 (윈도우 연장 회피).</li>
 *   <li>lazy 만료 — 60초 경과 시 자연 통과 + 갱신.</li>
 * </ul>
 *
 * <p>{@link FixedClock}는 {@code LoginAttemptTrackerTest} 패턴 답습 — 본 트랙 KISS로 inline 복제.
 */
class ForgotPasswordRateLimiterTest {

    private static final String EMAIL_A = "alice@example.com";
    private static final String EMAIL_B = "bob@example.com";
    private static final String IP_X = "10.0.0.1";
    private static final String IP_Y = "10.0.0.2";

    @Test
    void tryAcquire_firstCall_returnsTrue() {
        FixedClock clock = new FixedClock(Instant.parse("2026-05-03T00:00:00Z"));
        ForgotPasswordRateLimiter limiter = new ForgotPasswordRateLimiter(clock);

        assertTrue(limiter.tryAcquire(EMAIL_A, IP_X), "신규 키 1회차 통과");
    }

    @Test
    void tryAcquire_sameKeyWithinWindow_returnsFalseAndRetryAfterPositive() {
        FixedClock clock = new FixedClock(Instant.parse("2026-05-03T00:00:00Z"));
        ForgotPasswordRateLimiter limiter = new ForgotPasswordRateLimiter(clock);

        assertTrue(limiter.tryAcquire(EMAIL_A, IP_X));
        // 30초 경과 (윈도우 내)
        clock.advance(Duration.ofSeconds(30));

        assertFalse(limiter.tryAcquire(EMAIL_A, IP_X), "60s 내 동일 키 차단");
        long retryAfter = limiter.getRetryAfterSeconds(EMAIL_A, IP_X);
        assertTrue(retryAfter > 0 && retryAfter <= 60,
            "retryAfter는 0 < x <= 60 — 실측: " + retryAfter);
    }

    @Test
    void tryAcquire_afterWindowExpires_returnsTrue() {
        FixedClock clock = new FixedClock(Instant.parse("2026-05-03T00:00:00Z"));
        ForgotPasswordRateLimiter limiter = new ForgotPasswordRateLimiter(clock);

        assertTrue(limiter.tryAcquire(EMAIL_A, IP_X));
        // 60초 1ms 경과
        clock.advance(Duration.ofSeconds(60).plusMillis(1));

        assertTrue(limiter.tryAcquire(EMAIL_A, IP_X), "윈도우 경과 후 lazy 만료 + 통과");
    }

    @Test
    void tryAcquire_sameEmailDifferentIp_blocksByEmailKey() {
        FixedClock clock = new FixedClock(Instant.parse("2026-05-03T00:00:00Z"));
        ForgotPasswordRateLimiter limiter = new ForgotPasswordRateLimiter(clock);

        assertTrue(limiter.tryAcquire(EMAIL_A, IP_X));

        assertFalse(limiter.tryAcquire(EMAIL_A, IP_Y),
            "같은 email + 다른 IP는 email 한도로 차단");
    }

    @Test
    void tryAcquire_sameIpDifferentEmail_blocksByIpKey() {
        FixedClock clock = new FixedClock(Instant.parse("2026-05-03T00:00:00Z"));
        ForgotPasswordRateLimiter limiter = new ForgotPasswordRateLimiter(clock);

        assertTrue(limiter.tryAcquire(EMAIL_A, IP_X));

        assertFalse(limiter.tryAcquire(EMAIL_B, IP_X),
            "다른 email + 같은 IP는 IP 한도로 차단");
    }

    @Test
    void tryAcquire_bothKeysFresh_returnsTrue() {
        FixedClock clock = new FixedClock(Instant.parse("2026-05-03T00:00:00Z"));
        ForgotPasswordRateLimiter limiter = new ForgotPasswordRateLimiter(clock);

        assertTrue(limiter.tryAcquire(EMAIL_A, IP_X));

        assertTrue(limiter.tryAcquire(EMAIL_B, IP_Y),
            "둘 다 새 키 — 통과");
    }

    @Test
    void getRetryAfterSeconds_decreasesAsClockAdvances() {
        FixedClock clock = new FixedClock(Instant.parse("2026-05-03T00:00:00Z"));
        ForgotPasswordRateLimiter limiter = new ForgotPasswordRateLimiter(clock);

        assertTrue(limiter.tryAcquire(EMAIL_A, IP_X));
        long initial = limiter.getRetryAfterSeconds(EMAIL_A, IP_X);

        clock.advance(Duration.ofSeconds(20));
        long later = limiter.getRetryAfterSeconds(EMAIL_A, IP_X);

        assertTrue(later < initial, "시간 경과 시 잔여 retryAfter 감소");
        assertTrue(later >= 0, "음수 금지 (lazy 만료 후 0)");
    }

    @Test
    void getRetryAfterSeconds_returnsMaxOfBothKeys() {
        FixedClock clock = new FixedClock(Instant.parse("2026-05-03T00:00:00Z"));
        ForgotPasswordRateLimiter limiter = new ForgotPasswordRateLimiter(clock);

        // EMAIL_A + IP_X 등록 (T=0)
        assertTrue(limiter.tryAcquire(EMAIL_A, IP_X));
        // 30초 후 EMAIL_A + IP_Y 시도 (email 차단). IP_Y는 새 키지만 차단되므로 갱신 안 됨.
        clock.advance(Duration.ofSeconds(30));
        assertFalse(limiter.tryAcquire(EMAIL_A, IP_Y));

        // EMAIL_A 잔여 ≈ 30s, IP_Y는 미등록(0). max는 EMAIL_A 측.
        long retry = limiter.getRetryAfterSeconds(EMAIL_A, IP_Y);
        assertTrue(retry > 0 && retry <= 30,
            "두 키 잔여 중 큰 값 (email 측) — 실측: " + retry);
    }

    /** 결정적 테스트용 mutable Clock — LoginAttemptTrackerTest 패턴. */
    private static final class FixedClock extends Clock {
        private Instant now;
        private final ZoneId zone = ZoneId.of("UTC");

        FixedClock(Instant start) { this.now = start; }

        void advance(Duration d) { now = now.plus(d); }

        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId z) { return this; }
        @Override public Instant instant() { return now; }
    }
}
