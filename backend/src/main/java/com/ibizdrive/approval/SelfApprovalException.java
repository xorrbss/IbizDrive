package com.ibizdrive.approval;

/**
 * dual-approval framework — secondary가 self (requested_by 또는 action target과 동일).
 * {@code GlobalExceptionHandler}가 HTTP 403 + envelope code {@code APPROVAL_SELF}로 매핑
 * (docs/02 §8, ADR #47).
 *
 * <p>Self 차단 invariant (ADR #47):
 * <ul>
 *   <li>{@code secondary_approver_id ≠ requested_by} — 모든 action_type 공통</li>
 *   <li>{@code action_type='role_change'} 추가: {@code secondary ≠ payload.userId} — target 사용자가
 *       자기 role 변경 승인 차단</li>
 *   <li>{@code trash_purge}/{@code retention_change}는 추가 체크 없음 (target이 시스템 자료/정책)</li>
 * </ul>
 */
public class SelfApprovalException extends RuntimeException {
    public SelfApprovalException(String message) {
        super(message);
    }
}
