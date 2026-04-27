package com.ibizdrive.audit;

import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

/**
 * A1 인증 ApplicationEvent → audit_log 기록 (ADR #24, A2.4).
 *
 * <p>AuthService/AuthController의 비즈니스 로직과 분리된 cross-cutting layer.
 * 이벤트 publish는 호출 측에서 명시적으로 수행하고, 본 listener는 audit_log INSERT만 책임.
 *
 * <p>Failed 이벤트의 actorId는 email 기반 best-effort 조회 — 미존재 사용자는 null.
 *
 * <p>IP/User-Agent는 {@link WebRequestContextHolder}에서 ThreadLocal로 추출.
 * Spring Security 표준 이벤트 publish는 동일 요청 스레드에서 발생하므로 안전.
 */
@Component
public class AuthAuditListener {

    private final AuditService auditService;
    private final UserRepository userRepository;

    public AuthAuditListener(AuditService auditService, UserRepository userRepository) {
        this.auditService = auditService;
        this.userRepository = userRepository;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        UUID actorId = resolveActorId(auth);
        auditService.record(new AuditEvent(
            AuditEventType.USER_LOGIN_SUCCESS,
            actorId,
            WebRequestContextHolder.currentIp(),
            WebRequestContextHolder.currentUserAgent(),
            AuditTargetType.USER,
            actorId,
            null,
            null,
            null
        ));
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        String reason = reasonOf(event);
        String email = nameOf(event.getAuthentication());
        UUID actorId = lookupUserId(email);
        String metadata = "{\"reason\":\"" + reason + "\"}";
        auditService.record(new AuditEvent(
            AuditEventType.USER_LOGIN_FAILED,
            actorId,
            WebRequestContextHolder.currentIp(),
            WebRequestContextHolder.currentUserAgent(),
            AuditTargetType.USER,
            actorId,
            null,
            null,
            metadata
        ));
    }

    @EventListener
    public void onLogout(LogoutSuccessEvent event) {
        UUID actorId = resolveActorId(event.getAuthentication());
        auditService.record(new AuditEvent(
            AuditEventType.USER_LOGOUT,
            actorId,
            WebRequestContextHolder.currentIp(),
            WebRequestContextHolder.currentUserAgent(),
            AuditTargetType.USER,
            actorId,
            null,
            null,
            null
        ));
    }

    /**
     * Authentication.principal이 {@link com.ibizdrive.user.IbizDriveUserDetails}이면 User.id를,
     * 아니면 name(email)로 DB 조회. 둘 다 실패하면 null.
     */
    private UUID resolveActorId(Authentication auth) {
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof com.ibizdrive.user.IbizDriveUserDetails uds) {
            return uds.getUser().getId();
        }
        return lookupUserId(nameOf(auth));
    }

    private UUID lookupUserId(String email) {
        if (email == null || email.isBlank()) return null;
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return userRepository.findActiveByEmail(normalized).map(User::getId).orElse(null);
    }

    private static String nameOf(Authentication auth) {
        return auth == null ? null : auth.getName();
    }

    private static String reasonOf(AbstractAuthenticationFailureEvent event) {
        if (event instanceof AuthenticationFailureLockedEvent) return "locked";
        if (event instanceof AuthenticationFailureBadCredentialsEvent) {
            // BadCredentialsException 메시지에 user-not-found / inactive-or-locked / bad-password 구분 (AuthService)
            String msg = event.getException() == null ? null : event.getException().getMessage();
            return msg == null ? "bad-credentials" : msg;
        }
        return event.getClass().getSimpleName();
    }
}
