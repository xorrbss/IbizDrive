package com.ibizdrive.auth.password;

import com.ibizdrive.auth.password.dto.ChangePasswordRequest;
import com.ibizdrive.auth.password.dto.ForgotPasswordRequest;
import com.ibizdrive.auth.password.dto.MessageResponse;
import com.ibizdrive.auth.password.dto.ResetPasswordRequest;
import com.ibizdrive.user.IbizDriveUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

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

    private static final MessageResponse CHANGE_RESPONSE = MessageResponse.of(
        "비밀번호가 변경되었습니다. 다른 기기의 세션은 모두 종료되었습니다."
    );

    private static final Logger log = LoggerFactory.getLogger(PasswordController.class);

    private final PasswordResetService passwordResetService;
    private final ForgotPasswordRateLimiter rateLimiter;

    public PasswordController(PasswordResetService passwordResetService,
                              ForgotPasswordRateLimiter rateLimiter) {
        this.passwordResetService = passwordResetService;
        this.rateLimiter = rateLimiter;
    }

    /**
     * 가입/미가입 동일 200 (anti-enumeration). 호출 측은 응답 차이로 enumeration 불가.
     * 메일 발송 실패도 200 유지 — 서비스가 swallow + ERROR 로그.
     *
     * <p>호출 빈도 제한 (ADR #44, auth-forgot-rate-limit): email + IP 키 분당 1회.
     * 한도 초과 시 {@link RateLimitExceededException} → 429 + Retry-After.
     * 차단 응답은 가입자/미가입자 무관 — 가입 여부 노출 없음.
     */
    @PostMapping("/forgot")
    public MessageResponse forgot(@Valid @RequestBody ForgotPasswordRequest req,
                                  HttpServletRequest httpReq) {
        String emailKey = req.email().trim().toLowerCase(Locale.ROOT);
        String ipKey = resolveClientIp(httpReq);
        if (!rateLimiter.tryAcquire(emailKey, ipKey)) {
            long retryAfter = rateLimiter.getRetryAfterSeconds(emailKey, ipKey);
            log.warn("forgot rate-limited email={} ip={} retryAfter={}s",
                maskEmail(emailKey), ipKey, retryAfter);
            throw new RateLimitExceededException(retryAfter);
        }
        passwordResetService.requestReset(req.email());
        return FORGOT_RESPONSE;
    }

    /**
     * {@code X-Forwarded-For} 헤더 첫 값 우선 (사내 베타: reverse proxy 경유 가정).
     * 없으면 {@link HttpServletRequest#getRemoteAddr()}. spoof 가능 — trusted proxy 정책은 별도 트랙(ADR #44 한계).
     */
    private static String resolveClientIp(HttpServletRequest httpReq) {
        String fwd = httpReq.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            int comma = fwd.indexOf(',');
            return (comma < 0 ? fwd : fwd.substring(0, comma)).trim();
        }
        return httpReq.getRemoteAddr();
    }

    /** 로그 노출용 부분 마스킹 — {@code alice@example.com} → {@code a***@example.com}. */
    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***" + (at < 0 ? "" : email.substring(at));
        return email.charAt(0) + "***" + email.substring(at);
    }

    /**
     * 토큰 + 새 비밀번호 → 갱신 + 모든 세션 invalidate. 토큰 무효 시 400 INVALID_TOKEN.
     */
    @PostMapping("/reset")
    public MessageResponse reset(@Valid @RequestBody ResetPasswordRequest req) {
        passwordResetService.reset(req.token(), req.newPassword());
        return RESET_RESPONSE;
    }

    /**
     * 인증된 사용자의 비밀번호 변경 (P5). 현재 세션은 보존하고 다른 모든 세션을 invalidate.
     * currentPassword 미일치 시 401 INVALID_CREDENTIALS (login 실패와 동일).
     *
     * <p>SecurityConfig의 {@code anyRequest().authenticated()}가 미인증을 401로 차단하므로
     * 본 핸들러는 인증된 principal에서만 호출된다. CSRF는 default 정책 (signup/forgot/reset과 달리
     * ignore 미적용 — 인증 사용자이므로 토큰 발급 가능).
     */
    @PostMapping("/change")
    public MessageResponse change(@Valid @RequestBody ChangePasswordRequest req,
                                  @AuthenticationPrincipal IbizDriveUserDetails principal,
                                  HttpServletRequest httpReq) {
        HttpSession session = httpReq.getSession(false);
        String currentSessionId = (session != null) ? session.getId() : null;
        passwordResetService.change(
            principal.getUser(),
            req.currentPassword(),
            req.newPassword(),
            currentSessionId
        );
        return CHANGE_RESPONSE;
    }
}
