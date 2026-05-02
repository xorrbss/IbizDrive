package com.ibizdrive.auth.password;

import com.ibizdrive.auth.password.dto.ForgotPasswordRequest;
import com.ibizdrive.auth.password.dto.MessageResponse;
import com.ibizdrive.auth.password.dto.ResetPasswordRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 비밀번호 분실/재설정/변경 endpoint (a1.5).
 *
 * <ul>
 *   <li>{@code POST /api/auth/password/forgot} — 이메일 입력. 가입/미가입 모두 200 동일 응답.</li>
 *   <li>{@code POST /api/auth/password/reset} — 토큰 + 새 비밀번호. (P4)</li>
 *   <li>{@code POST /api/auth/password/change} — 현재 비밀번호 + 새 비밀번호 (인증 사용자). (P5)</li>
 * </ul>
 *
 * <p>SecurityConfig에서 {@code /forgot}, {@code /reset}은 permitAll + CSRF ignore (signup 패턴 동일).
 * {@code /change}는 인증 + CSRF 필수.
 */
@RestController
@RequestMapping("/api/auth/password")
public class PasswordController {

    private static final MessageResponse FORGOT_RESPONSE = MessageResponse.of(
        "요청을 처리했습니다. 가입된 이메일이라면 비밀번호 재설정 링크가 발송됩니다."
    );

    private static final MessageResponse RESET_RESPONSE = MessageResponse.of(
        "비밀번호가 재설정되었습니다. 새 비밀번호로 다시 로그인하세요."
    );

    private final PasswordResetService passwordResetService;

    public PasswordController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    /**
     * 가입/미가입 동일 200 (anti-enumeration). 호출 측은 응답 차이로 enumeration 불가.
     * 메일 발송 실패도 200 유지 — 서비스가 swallow + ERROR 로그.
     */
    @PostMapping("/forgot")
    public MessageResponse forgot(@Valid @RequestBody ForgotPasswordRequest req) {
        passwordResetService.requestReset(req.email());
        return FORGOT_RESPONSE;
    }

    /**
     * 토큰 + 새 비밀번호 → 갱신 + 모든 세션 invalidate. 토큰 무효 시 400 INVALID_TOKEN.
     */
    @PostMapping("/reset")
    public MessageResponse reset(@Valid @RequestBody ResetPasswordRequest req) {
        passwordResetService.reset(req.token(), req.newPassword());
        return RESET_RESPONSE;
    }
}
