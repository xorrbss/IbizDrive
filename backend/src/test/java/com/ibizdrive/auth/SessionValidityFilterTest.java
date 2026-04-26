package com.ibizdrive.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SessionValidityFilter} 단위 테스트 — A1.6, ADR #20, must-fix #1 close.
 *
 * <p>검증 포인트:
 * <ul>
 *   <li>세션 부재/issuedAt 부재 → pass-through (인증 전 요청 차단 금지)</li>
 *   <li>absolute TTL(8h) 미만 → pass-through (idle 정책은 Spring Session JDBC 담당)</li>
 *   <li>absolute TTL(8h) 도달 → session.invalidate() + 401 + chain 차단</li>
 *   <li>{@link Clock} 주입으로 시간 진행 결정적 검증</li>
 * </ul>
 *
 * <p>idle 30분 sliding은 Spring HttpSession {@code lastAccessedTime}이 컨테이너 레벨에서
 * 매 요청마다 갱신하므로 본 필터에서 검증하지 않는다. 본 필터는 {@code issuedAt} 기준
 * absolute 한도만 강제 (yml의 {@code spring.session.timeout=PT30M}이 idle 진실 출처).
 */
class SessionValidityFilterTest {

    private static final long EIGHT_HOURS_MS = Duration.ofHours(8).toMillis();

    /** 결정적 테스트용 mutable Clock — Instant 직접 진행. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void noSession_passesThrough_withoutTouchingResponse() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-26T00:00:00Z"));
        SessionValidityFilter filter = new SessionValidityFilter(clock);

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(req.getSession(false)).thenReturn(null);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        verify(res, never()).sendError(eq(401));
        verify(res, never()).sendError(eq(401), any());
    }

    @Test
    void sessionWithoutIssuedAt_passesThrough() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-26T00:00:00Z"));
        SessionValidityFilter filter = new SessionValidityFilter(clock);

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        HttpSession session = mock(HttpSession.class);
        when(req.getSession(false)).thenReturn(session);
        when(session.getAttribute("issuedAt")).thenReturn(null);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        verify(session, never()).invalidate();
        verify(res, never()).sendError(eq(401));
    }

    @Test
    void issuedWithinAbsoluteTtl_passesThrough_andDoesNotInvalidate() throws Exception {
        Instant start = Instant.parse("2026-04-26T00:00:00Z");
        MutableClock clock = new MutableClock(start);
        SessionValidityFilter filter = new SessionValidityFilter(clock);

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        HttpSession session = mock(HttpSession.class);
        when(req.getSession(false)).thenReturn(session);
        // issuedAt = 시작 시각, 7h 59m 59s 경과 (absolute 8h 미만)
        when(session.getAttribute("issuedAt")).thenReturn(start.toEpochMilli());
        clock.advance(Duration.ofHours(8).minusSeconds(1));

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        verify(session, never()).invalidate();
        verify(res, never()).sendError(eq(401));
    }

    @Test
    void issuedExceedsAbsoluteTtl_invalidatesSessionAndReturns401() throws Exception {
        Instant start = Instant.parse("2026-04-26T00:00:00Z");
        MutableClock clock = new MutableClock(start);
        SessionValidityFilter filter = new SessionValidityFilter(clock);

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        HttpSession session = mock(HttpSession.class);
        when(req.getSession(false)).thenReturn(session);
        when(session.getAttribute("issuedAt")).thenReturn(start.toEpochMilli());
        // 정확히 8h 경과 (>= 8h)
        clock.advance(Duration.ofHours(8));

        filter.doFilter(req, res, chain);

        verify(session, times(1)).invalidate();
        verify(res, times(1)).sendError(401);
        verify(chain, never()).doFilter(any(), any());
    }
}
