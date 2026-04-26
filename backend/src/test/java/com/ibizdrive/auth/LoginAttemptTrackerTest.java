package com.ibizdrive.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LoginAttemptTracker} 단위 테스트 — A1.3, ADR #23.
 *
 * <p>검증:
 * <ul>
 *   <li>실패 카운터가 누적되어 5회째에 lockout 진입</li>
 *   <li>lockout TTL(15분) 경과 후 lazy 만료 + 카운터 reset</li>
 *   <li>로그인 성공 reset(`success`) 호출 시 카운터 0 복구</li>
 * </ul>
 *
 * <p>Clock은 {@link FixedClock}으로 주입하여 시계 진행을 결정적으로 제어한다.
 */
class LoginAttemptTrackerTest {

    private static final String EMAIL = "alice@example.com";

    @Test
    void increment_fromZero_returnsOneAndNotLocked() {
        FixedClock clock = new FixedClock(Instant.parse("2026-04-26T00:00:00Z"));
        LoginAttemptTracker tracker = new LoginAttemptTracker(clock);

        tracker.recordFailure(EMAIL);

        assertFalse(tracker.isLocked(EMAIL), "1회 실패는 잠금 아님");
        assertEquals(1, tracker.getFailureCount(EMAIL));
    }

    @Test
    void fiveFailures_triggerLockout_withRetryAfterPositive() {
        FixedClock clock = new FixedClock(Instant.parse("2026-04-26T00:00:00Z"));
        LoginAttemptTracker tracker = new LoginAttemptTracker(clock);

        for (int i = 0; i < 5; i++) {
            tracker.recordFailure(EMAIL);
        }

        assertTrue(tracker.isLocked(EMAIL), "5회 실패 후 잠금");
        long retryAfter = tracker.getRetryAfterSeconds(EMAIL);
        assertTrue(retryAfter > 0 && retryAfter <= 900,
            "retryAfter는 0보다 크고 15분(900초) 이하 — 실측: " + retryAfter);
    }

    @Test
    void lockoutExpires_afterFifteenMinutes_lazyEvaluated() {
        FixedClock clock = new FixedClock(Instant.parse("2026-04-26T00:00:00Z"));
        LoginAttemptTracker tracker = new LoginAttemptTracker(clock);

        for (int i = 0; i < 5; i++) {
            tracker.recordFailure(EMAIL);
        }
        assertTrue(tracker.isLocked(EMAIL));

        // 15분 1초 경과 — lazy 만료
        clock.advance(Duration.ofMinutes(15).plusSeconds(1));

        assertFalse(tracker.isLocked(EMAIL), "TTL 경과 시 lazy 해제");
        assertEquals(0, tracker.getFailureCount(EMAIL), "만료 시 카운터 reset");
    }

    @Test
    void recordSuccess_resetsCounter() {
        FixedClock clock = new FixedClock(Instant.parse("2026-04-26T00:00:00Z"));
        LoginAttemptTracker tracker = new LoginAttemptTracker(clock);

        tracker.recordFailure(EMAIL);
        tracker.recordFailure(EMAIL);
        assertEquals(2, tracker.getFailureCount(EMAIL));

        tracker.recordSuccess(EMAIL);

        assertEquals(0, tracker.getFailureCount(EMAIL));
        assertFalse(tracker.isLocked(EMAIL));
    }

    /**
     * 결정적 테스트용 mutable Clock — Instant를 직접 진행시킨다.
     * 표준 {@link Clock#fixed} 방식은 immutable이라 advance 불가.
     */
    private static final class FixedClock extends Clock {
        private Instant now;
        private final ZoneId zone = ZoneId.of("UTC");

        FixedClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() { return zone; }

        @Override
        public Clock withZone(ZoneId z) { return this; }

        @Override
        public Instant instant() { return now; }
    }
}
