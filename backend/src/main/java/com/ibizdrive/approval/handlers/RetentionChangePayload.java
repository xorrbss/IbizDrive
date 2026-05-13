package com.ibizdrive.approval.handlers;

/**
 * `retention_change` action payload (ADR #47, docs/02 §2.11 Tier 0).
 *
 * <p>`PendingAdminApproval.payloadJson`에 Jackson 직렬화된 형태로 저장. handler가
 * approve transition 시 deserialize 후 {@link com.ibizdrive.trash.TrashPolicyService}로 위임.
 *
 * @param fromDays primary 요청 시점의 현재 retention_days (audit 트레일용)
 * @param toDays   적용할 새 retention_days (7..90)
 * @param reason   primary 요청 사유 (audit 트레일용, optional)
 */
public record RetentionChangePayload(
    int fromDays,
    int toDays,
    String reason
) {}
