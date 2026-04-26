package com.ibizdrive.auth;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 로그인 실패 카운터 + 계정 잠금 (lockout) — ADR #20, ADR #23.
 *
 * <p>저장소: in-memory {@link ConcurrentHashMap}. 키는 lowercased email.
 * MVP 단일 인스턴스 가정 (ADR #23). 다중 인스턴스/Redis 도입 시점에 본 클래스의 인터페이스를
 * 추출하여 교체. 데이터는 프로세스 재시작 시 휘발 (의도된 동작 — 재시작 후 재시도 허용).
 *
 * <p>정책 (docs/03 §2.6):
 * <ul>
 *   <li>5회 연속 실패 시 잠금. 잠금 지속 시간 15분.</li>
 *   <li>잠금 상태에서의 시도도 카운트. 단, 외부에서 {@link #isLocked}로 차단되므로
 *       AuthService는 잠금 상태를 먼저 검사하고 통과 못 하면 {@link #recordFailure}을 호출하지
 *       않는다 (TTL 갱신 회피 — ADR #23 본문대로 lazy 만료 채택).</li>
 *   <li>로그인 성공 시 카운터 reset.</li>
 * </ul>
 *
 * <p>Lazy 만료: 잠금 시각 + 15분 < 현재 시각이면 lazy 카운터 reset (조회 시점에 판정).
 * 별도 cleanup 스레드 미운영 (KISS). 메모리 누수는 사용자 수 × 메모리 항목 ≪ JVM heap.
 *
 * <p>Clock 주입: 테스트는 {@link FixedClock} 등 시계 진행 가능한 구현을 주입하여 검증.
 * 운영은 {@link Clock#systemUTC()} (no-arg ctor).
 */
@Component
public class LoginAttemptTracker {

    /** 잠금 임계 실패 횟수. */
    private static final int LOCKOUT_THRESHOLD = 5;

    /** 잠금 유지 시간. */
    private static final Duration LOCKOUT_TTL = Duration.ofMinutes(15);

    private final Clock clock;
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    /** 운영 기본 — Spring 컴포넌트 스캔 진입점. */
    public LoginAttemptTracker() {
        this(Clock.systemUTC());
    }

    /** 테스트용 — 임의 Clock 주입. */
    LoginAttemptTracker(Clock clock) {
        this.clock = clock;
    }

    /** 실패 1회 기록. 임계치 도달 시 lockedUntil 세팅. */
    public void recordFailure(String emailKey) {
        Instant now = clock.instant();
        attempts.compute(emailKey, (k, prev) -> {
            int count = (prev == null) ? 0 : prev.count;
            // lazy 만료 — 이전 잠금이 끝났으면 카운터 초기화 후 다시 카운트
            if (prev != null && prev.lockedUntil != null && now.isAfter(prev.lockedUntil)) {
                count = 0;
            }
            count += 1;
            Instant lockedUntil = (count >= LOCKOUT_THRESHOLD) ? now.plus(LOCKOUT_TTL) : null;
            return new Attempt(count, lockedUntil);
        });
    }

    /** 성공 시 카운터 reset. */
    public void recordSuccess(String emailKey) {
        attempts.remove(emailKey);
    }

    /** 현재 잠금 여부. lazy 만료 평가 후 만료 상태면 항목 제거. */
    public boolean isLocked(String emailKey) {
        Attempt a = attempts.get(emailKey);
        if (a == null || a.lockedUntil == null) {
            return false;
        }
        if (clock.instant().isAfter(a.lockedUntil)) {
            // 만료 — 카운터까지 reset (다음 실패는 1부터 재시작)
            attempts.remove(emailKey, a);
            return false;
        }
        return true;
    }

    /** 잠금 해제까지 남은 초. 잠겨있지 않으면 0. */
    public long getRetryAfterSeconds(String emailKey) {
        Attempt a = attempts.get(emailKey);
        if (a == null || a.lockedUntil == null) {
            return 0;
        }
        long sec = Duration.between(clock.instant(), a.lockedUntil).toSeconds();
        return Math.max(sec, 0);
    }

    /** 현재 누적 실패 횟수 (테스트·운영 디버그 보조). */
    public int getFailureCount(String emailKey) {
        Attempt a = attempts.get(emailKey);
        if (a == null) return 0;
        // lazy 만료 — 만료 후에는 0으로 본다
        if (a.lockedUntil != null && clock.instant().isAfter(a.lockedUntil)) {
            attempts.remove(emailKey, a);
            return 0;
        }
        return a.count;
    }

    /**
     * 시도 기록. immutable record로 잠금 진입 후 update race를 단순화한다
     * ({@link ConcurrentHashMap#compute} 내부에서만 새 인스턴스 발급).
     */
    private record Attempt(int count, Instant lockedUntil) {}
}
