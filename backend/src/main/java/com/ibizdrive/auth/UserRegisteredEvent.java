package com.ibizdrive.auth;

import java.util.UUID;

/**
 * Self-signup 성공 시 publish — ADR #41. {@link com.ibizdrive.audit.AuthAuditListener}가 구독해
 * {@code USER_REGISTERED} audit row INSERT. PermissionAuditListener / AuthAuditListener와 동일 패턴.
 *
 * <p>비즈니스 로직(SignupService)과 감사 emission 분리 (ADR #24 cross-cutting).
 *
 * <p>Spring Security 표준 이벤트(AuthenticationSuccessEvent)와 별개 — signup은 신규 user 생성이므로
 * 표준 이벤트 시맨틱과 다르다. 자동 로그인으로 발생하는 세션 발급 자체에 대해서는 publish하지 않는다
 * (signup audit 1건만 기록 — login.success와 중복 방지).
 */
public record UserRegisteredEvent(UUID userId, String email) {
}
