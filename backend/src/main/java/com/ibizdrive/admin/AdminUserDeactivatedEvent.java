package com.ibizdrive.admin;

import java.util.UUID;

/**
 * Admin이 사용자를 비활성화 시 publish — admin-user-mgmt, ADR #21 후속.
 *
 * <p>{@link AdminAuditListener}가 구독해 {@code admin.user.deactivated} audit row INSERT.
 * 비활성화는 soft-delete가 아니라 {@code is_active=false} 토글 — 로그인 차단(`SessionValidityFilter`)
 * 효과만 있고 데이터는 보존된다 (docs/04 §4.3).
 *
 * @param userId  비활성화된 user의 id (target)
 * @param actorId 비활성화를 수행한 관리자의 user id (audit actor)
 */
public record AdminUserDeactivatedEvent(UUID userId, UUID actorId) {
}
