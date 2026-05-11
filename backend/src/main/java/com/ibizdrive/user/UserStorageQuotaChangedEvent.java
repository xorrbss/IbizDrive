package com.ibizdrive.user;

import java.util.UUID;

/**
 * 사용자 storage_quota 변경 도메인 이벤트 — quota mutation Phase 3 (`docs/04 §6.1`).
 *
 * <p>{@link com.ibizdrive.admin.AdminUserQuotaService#updateQuota}가 트랜잭션 commit 후 publish.
 * {@link com.ibizdrive.audit.UserQuotaAuditListener}가 audit_log row로 변환
 * ({@code admin.quota.changed}, `docs/03 §4.1` placeholder 활성화).
 *
 * <p>변경 영향: {@code storage_used}는 미변경 — Phase 5 enforcement에서 upload commit 시점만 증가.
 * over-quota(`storage_used > storage_quota`)는 운영 grace로 허용, 신규 업로드만 차단.
 *
 * @param targetUserId quota가 변경된 사용자 id
 * @param beforeQuota  변경 전 한도(bytes)
 * @param afterQuota   변경 후 한도(bytes)
 * @param actorId      변경 actor(ADMIN principal)
 */
public record UserStorageQuotaChangedEvent(
    UUID targetUserId,
    long beforeQuota,
    long afterQuota,
    UUID actorId
) {}
