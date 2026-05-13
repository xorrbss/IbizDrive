package com.ibizdrive.admin.approval;

import com.ibizdrive.approval.PendingAdminApproval;
import com.ibizdrive.approval.PendingApprovalStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * `/api/admin/approvals` 응답 envelope — dual-approval framework Phase 2b (ADR #47, docs/02 §2.11).
 *
 * <p>{@link PendingAdminApproval} entity 1:1 mirror. {@code payloadJson}은 raw String 그대로 노출 —
 * 호출자(admin UI Phase 4)가 action_type에 따라 client-side parse.
 */
public record AdminApprovalDto(
    UUID id,
    String actionType,
    String payloadJson,
    UUID requestedBy,
    OffsetDateTime requestedAt,
    PendingApprovalStatus status,
    UUID secondaryApproverId,
    OffsetDateTime decidedAt,
    String decisionReason,
    OffsetDateTime expiresAt
) {
    public static AdminApprovalDto from(PendingAdminApproval row) {
        return new AdminApprovalDto(
            row.getId(),
            row.getActionType(),
            row.getPayloadJson(),
            row.getRequestedBy(),
            row.getRequestedAt(),
            row.getStatus(),
            row.getSecondaryApproverId(),
            row.getDecidedAt(),
            row.getDecisionReason(),
            row.getExpiresAt()
        );
    }
}
