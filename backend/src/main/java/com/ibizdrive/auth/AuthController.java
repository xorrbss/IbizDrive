package com.ibizdrive.auth;

import com.ibizdrive.auth.dto.LoginRequest;
import com.ibizdrive.auth.dto.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 endpoint — docs/02 §7.4. A1.3 단계는 {@code POST /login}만 노출.
 * {@code GET /me}, {@code POST /logout}는 A1.4에서 본 controller에 추가.
 *
 * <p>{@code /api/auth/csrf}는 별도 {@link CsrfTokenController} (permitAll 영역 분리, docs/03 §2.4).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest httpReq) {
        return authService.login(req.email(), req.password(), httpReq);
    }
}
