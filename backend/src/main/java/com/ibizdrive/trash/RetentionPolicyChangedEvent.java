package com.ibizdrive.trash;

import java.util.UUID;

/**
 * 휴지통 보존 일수 변경 도메인 이벤트 — trash-retention-mutation Phase B.
 *
 * <p>{@link TrashPolicyService#updateRetentionDays}가 트랜잭션 commit 후 publish.
 * {@link com.ibizdrive.audit.TrashPolicyAuditListener}가 audit_log row로 변환
 * ({@code admin.retention.changed}, docs/03 §4.1).
 *
 * <p>변경 영향: 신규 soft-delete만 새 일수 적용. 기존 trash row의 {@code purge_after}는
 * 재계산하지 않음 (감소 시 hard purge 폭증 회피, docs/04 §15.4).
 */
public record RetentionPolicyChangedEvent(int beforeDays, int afterDays, UUID actorId) {}
