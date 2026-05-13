package com.ibizdrive.approval;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * dual-approval framework — 게이트 활성 controller가 framework submit 후 1단계 응답을 위해 throw.
 *
 * <p>{@code GlobalExceptionHandler}가 HTTP **202 ACCEPTED** + envelope code {@code APPROVAL_REQUIRED}
 * (docs/02 §8) + details `{approvalId, expiresAt}`로 변환. frontend는 "승인 요청을 등록했습니다.
 * secondary admin 결정을 대기 중입니다" 토스트 + `/admin/approvals` redirect (Phase 4 UI).
 *
 * <p>controller가 직접 202 ResponseEntity를 반환하지 않고 본 예외로 통일하는 이유: 기존 controller
 * 메서드 signature(예: `ResponseEntity<AdminTrashPolicyDto>`)를 보존하면서 게이트 분기 추가.
 * 단일-approver와 dual-approver 응답이 다른 envelope이라 분기 처리도 자연.
 */
public class ApprovalRequiredException extends RuntimeException {

    private final UUID approvalId;
    private final OffsetDateTime expiresAt;

    public ApprovalRequiredException(UUID approvalId, OffsetDateTime expiresAt) {
        super("dual-approval required: approvalId=" + approvalId);
        this.approvalId = approvalId;
        this.expiresAt = expiresAt;
    }

    public UUID getApprovalId() {
        return approvalId;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }
}
