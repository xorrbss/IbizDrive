package com.ibizdrive.auth.password;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code POST /api/auth/password/forgot} 호출 빈도 제한 — auth-forgot-rate-limit 트랙, ADR #44.
 *
 * <p>저장소: in-memory {@link ConcurrentHashMap} 2개 (email 키, IP 키 독립). MVP 단일 인스턴스
 * 가정 (ADR #23 답습 — {@link com.ibizdrive.auth.LoginAttemptTracker} 패턴 mirror).
 * 다중 인스턴스/Redis 도입 시점에 인터페이스 추출하여 교체.
 *
 * <p>정책:
 * <ul>
 *   <li>고정 윈도우 {@link #WINDOW} (60초), 한도 1회 / 키.</li>
 *   <li>두 키 OR 차단 — 어느 한쪽이라도 윈도우 내 갱신 기록이 있으면 차단.</li>
 *   <li>차단된 호출은 {@link Instant} 갱신 없음 (윈도우 연장 회피).</li>
 *   <li>lazy 만료 — 60초 경과 시 자연 통과 + 갱신.</li>
 * </ul>
 *
 * <p>한계 (ADR #44):
 * <ul>
 *   <li>다중 인스턴스 시 카운터 N배. v1.x Redis 교체.</li>
 *   <li>NAT 공유 IP 환경에서 다른 사용자 영향. 사내 베타 가정 + 60s/1회는 정상 사용 마찰 없음.</li>
 *   <li>{@code X-Forwarded-For} spoof 가능. trusted proxy 정책은 별도 트랙.</li>
 * </ul>
 *
 * <p>Clock 주입은 테스트 결정성 확보 — {@link LoginAttemptTracker} 패턴.
 */
@Component
public class ForgotPasswordRateLimiter {

    /** 윈도우 길이 — 60초 / 1회. */
    private static final Duration WINDOW = Duration.ofSeconds(60);

    private final Clock clock;
    private final Map<String, Instant> emailHits = new ConcurrentHashMap<>();
    private final Map<String, Instant> ipHits = new ConcurrentHashMap<>();

    /** 운영 기본 — Spring 컴포넌트 스캔 진입점. */
    public ForgotPasswordRateLimiter() {
        this(Clock.systemUTC());
    }

    /** 테스트용 — 임의 Clock 주입. */
    ForgotPasswordRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * email 또는 IP 키 중 하나라도 윈도우 내 hit이 있으면 false. 통과 시 두 키 모두 갱신.
     * 차단 시 갱신 없음 (윈도우 연장 회피).
     */
    public boolean tryAcquire(String emailKey, String ipKey) {
        Instant now = clock.instant();
        if (isBlocked(emailHits, emailKey, now) || isBlocked(ipHits, ipKey, now)) {
            return false;
        }
        emailHits.put(emailKey, now);
        ipHits.put(ipKey, now);
        return true;
    }

    /**
     * 두 키 잔여 시간 중 큰 값 (초). 둘 다 만료/미등록이면 0.
     * 응답 {@code Retry-After} 헤더 + body {@code retryAfterSec}에 동일 값 사용.
     */
    public long getRetryAfterSeconds(String emailKey, String ipKey) {
        Instant now = clock.instant();
        long emailRemaining = remainingSeconds(emailHits, emailKey, now);
        long ipRemaining = remainingSeconds(ipHits, ipKey, now);
        return Math.max(emailRemaining, ipRemaining);
    }

    private boolean isBlocked(Map<String, Instant> bucket, String key, Instant now) {
        Instant last = bucket.get(key);
        if (last == null) return false;
        if (Duration.between(last, now).compareTo(WINDOW) >= 0) {
            // lazy 만료 — 다음 통과 호출이 갱신할 것이므로 여기선 제거하지 않음.
            return false;
        }
        return true;
    }

    private long remainingSeconds(Map<String, Instant> bucket, String key, Instant now) {
        Instant last = bucket.get(key);
        if (last == null) return 0;
        long elapsed = Duration.between(last, now).toSeconds();
        long remain = WINDOW.toSeconds() - elapsed;
        return Math.max(remain, 0);
    }
}
