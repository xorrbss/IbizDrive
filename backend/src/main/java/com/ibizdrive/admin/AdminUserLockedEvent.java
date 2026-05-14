package com.ibizdrive.admin;

import java.util.UUID;

/**
 * Admin이 사용자를 수동 잠금 시 publish — admin-user-lock-unlock 트랙 (docs/02 §7.4 line 1309).
 *
 * <p>{@link AdminAuditListener}가 구독해 {@code user.locked} audit row INSERT (AFTER_COMMIT).
 * 자동 lock(login 5회 실패 + 423 ACCOUNT_LOCKED, 별도 트랙)도 동일 wire를 공유하며
 * metadata.trigger로 발동 출처를 구분한다 — 본 이벤트의 metadata.trigger는 {@code "admin.manual"}.
 *
 * @param userId  잠금된 user의 id (audit target)
 * @param actorId 잠금을 수행한 관리자의 user id (audit actor)
 */
public record AdminUserLockedEvent(UUID userId, UUID actorId) {
}
