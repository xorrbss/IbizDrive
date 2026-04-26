package com.ibizdrive.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

/**
 * 세션 absolute 만료 강제 — A1.6, ADR #20, must-fix #1 close.
 *
 * <p>설계 분담 (ADR #20):
 * <ul>
 *   <li>idle 30분 sliding — Spring Session JDBC ({@code spring.session.timeout=PT30M}).
 *       매 요청마다 컨테이너가 {@code lastAccessedTime}을 갱신하고, 30분 무활동 시 자동 invalidate.</li>
 *   <li>absolute 8시간 — <b>본 필터</b>가 진실 출처. 로그인 시점에 {@code AuthService.login}이
 *       세션 attribute {@code issuedAt}(epoch millis)을 set. 본 필터가 매 요청마다
 *       {@code now - issuedAt >= 8h}인지 검사, 도달 시 {@code invalidate()} + 401.</li>
 * </ul>
 *
 * <p>인증 전 요청 (세션 미존재 또는 {@code issuedAt} 미기록) 은 pass-through —
 * 본 필터는 이미 발급된 세션의 absolute 만료만 차단.
 *
 * <p>Wire: {@code SecurityConfig.addFilterAfter(this, SecurityContextHolderFilter.class)} —
 * SecurityContext 로드 직후 검사하여 만료된 세션의 인증 컨텍스트가 다운스트림에 노출되는 것 회피.
 */
@Component
public class SessionValidityFilter extends OncePerRequestFilter {

    /** absolute 만료 한도 (ADR #20). */
    private static final long ABSOLUTE_TTL_MS = Duration.ofHours(8).toMillis();

    private final Clock clock;

    public SessionValidityFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        if (session == null) {
            chain.doFilter(req, res);
            return;
        }

        Object issuedAtObj = session.getAttribute("issuedAt");
        if (!(issuedAtObj instanceof Long issuedAt)) {
            chain.doFilter(req, res);
            return;
        }

        long age = clock.millis() - issuedAt;
        if (age >= ABSOLUTE_TTL_MS) {
            session.invalidate();
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        chain.doFilter(req, res);
    }
}
