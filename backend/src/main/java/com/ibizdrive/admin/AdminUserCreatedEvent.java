package com.ibizdrive.admin;

import java.util.UUID;

/**
 * Admin invite로 신규 사용자 생성 시 publish — ADR #21 (admin 트랙 closure).
 *
 * <p>{@link AdminAuditListener}가 구독해 {@code admin.user.created} audit row INSERT
 * (REQUIRES_NEW). {@link com.ibizdrive.auth.UserRegisteredEvent}와 분리된 이유:
 * self-signup과 admin invite는 actor/의도/감사 노이즈가 다르며 (signup은 본인,
 * invite는 ADMIN actor) ADR #24 cross-cutting 정책에서 별도 이벤트로 다룬다.
 *
 * <p>{@code actorId}는 invite를 호출한 ADMIN의 사용자 ID. 신규 생성된 사용자({@code userId})와는 다르다.
 */
public record AdminUserCreatedEvent(UUID userId, UUID actorId, String email) {
}
