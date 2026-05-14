package com.ibizdrive.admin;

import java.util.UUID;

/**
 * Admin이 사용자 수동 잠금을 해제 시 publish — admin-user-lock-unlock 트랙 (docs/02 §7.4 line 1309).
 *
 * <p>{@link AdminAuditListener}가 구독해 {@code user.unlocked} audit row INSERT (AFTER_COMMIT).
 * metadata.trigger는 {@code "admin.manual"} — 자동 lock에 대응하는 자동 unlock은 본 트랙 범위 외.
 *
 * @param userId  잠금 해제된 user의 id (audit target)
 * @param actorId 잠금 해제를 수행한 관리자의 user id (audit actor)
 */
public record AdminUserUnlockedEvent(UUID userId, UUID actorId) {
}
