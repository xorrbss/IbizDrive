package com.ibizdrive.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * CSRF 토큰 발급 엔드포인트 (docs/02 §7.4 GET /api/auth/csrf).
 *
 * <p>permitAll — 인증 전 SPA가 mutation 호출 직전 1회 호출하여
 * {@code XSRF-TOKEN} 쿠키와 응답 본문 양쪽으로 토큰을 받는다 (double-submit, ADR #12).
 *
 * <p>Spring Security 6의 deferred CSRF 모델은 token이 access되기 전에는
 * cookie를 발급하지 않는다. 따라서 controller가 직접 {@link CsrfTokenRepository#saveToken}을
 * 호출하여 응답 commit 전에 cookie 발급을 강제한다.
 */
@RestController
@RequestMapping("/api/auth")
public class CsrfTokenController {

    private final CsrfTokenRepository csrfRepo;

    public CsrfTokenController(CsrfTokenRepository csrfRepo) {
        this.csrfRepo = csrfRepo;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(HttpServletRequest req, HttpServletResponse res) {
        // CsrfFilter가 deferred token을 _csrf attribute에 주입.
        CsrfToken token = (CsrfToken) req.getAttribute(CsrfToken.class.getName());
        // saveToken을 명시 호출하여 XSRF-TOKEN cookie를 강제 발급.
        // (deferred 모드에서 GET 응답에는 자동 발급이 보장되지 않음)
        csrfRepo.saveToken(token, req, res);
        return Map.of("csrfToken", token.getToken());
    }
}
