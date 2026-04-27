package com.ibizdrive.auth;

import com.ibizdrive.auth.dto.LoginRequest;
import com.ibizdrive.auth.dto.LoginResponse;
import com.ibizdrive.user.IbizDriveUserDetails;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 endpoint — docs/02 §7.4. {@code /login}, {@code /me}, {@code /logout}을 노출.
 *
 * <p>{@code /api/auth/csrf}는 별도 {@link CsrfTokenController} (permitAll 영역 분리, docs/03 §2.4).
 *
 * <p>응답 shape:
 * <ul>
 *   <li>{@code POST /login} → {@link LoginResponse} 200</li>
 *   <li>{@code GET /me} → {@link LoginResponse} 200 (login과 동일 shape — docs/02 §7.4)</li>
 *   <li>{@code POST /logout} → 204 + {@code Set-Cookie SESSION=; Max-Age=0}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final ApplicationEventPublisher eventPublisher;

    public AuthController(AuthService authService, ApplicationEventPublisher eventPublisher) {
        this.authService = authService;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req,
                               HttpServletRequest httpReq,
                               HttpServletResponse httpRes) {
        return authService.login(req.email(), req.password(), httpReq, httpRes);
    }

    /**
     * 현재 인증된 사용자 정보 조회. {@link IbizDriveUserDetails}는 {@link AuthService#login}이
     * SecurityContext에 저장한 principal — Spring Security가 세션에서 복원하여 주입한다.
     *
     * <p>응답 shape는 {@link LoginResponse}와 동일 (docs/02 §7.4) — 권한 매트릭스 백엔드 도입 전까지
     * 별도 DTO 분리 불필요 (KISS, ULTIMATE INVARIANTS §3 원칙 3).
     *
     * <p>미인증 요청은 {@code anyRequest().authenticated()} 가드가 401로 차단하므로 본 핸들러에
     * 도달하지 않는다 (HttpStatusEntryPoint).
     */
    @GetMapping("/me")
    public LoginResponse me(@AuthenticationPrincipal IbizDriveUserDetails principal) {
        return LoginResponse.from(principal.getUser());
    }

    /**
     * 세션 종료. 현재 세션 invalidate + SecurityContext 클리어 + SESSION 쿠키 만료.
     *
     * <p>docs/02 §7.4 line 836-845: 응답 204, {@code Set-Cookie SESSION=; Max-Age=0; Path=/},
     * audit user.logout 발생. 미인증은 {@code anyRequest().authenticated()}가 401로 차단.
     * CSRF 미제공은 CsrfFilter가 403으로 차단 (POST mutation).
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest req, HttpServletResponse res) {
        // ADR #24 — invalidate 전에 Authentication 캡처 후 표준 LogoutSuccessEvent publish.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        if (auth != null && auth.isAuthenticated()) {
            eventPublisher.publishEvent(new LogoutSuccessEvent(auth));
        }

        // 클라이언트 쿠키 만료 — application.yml의 server.servlet.session.cookie.name=SESSION 일치.
        // Max-Age=0 + Path=/로 즉시 만료. HttpOnly/SameSite는 만료 쿠키엔 의미 없으나 일관성 유지.
        Cookie expired = new Cookie("SESSION", "");
        expired.setMaxAge(0);
        expired.setPath("/");
        expired.setHttpOnly(true);
        res.addCookie(expired);
    }
}
