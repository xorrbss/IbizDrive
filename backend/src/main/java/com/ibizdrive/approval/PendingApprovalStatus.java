package com.ibizdrive.approval;

/**
 * dual-approval framework state machine — ADR #47, docs/02 §2.11.
 *
 * <p>Terminal 4종 ({@link #APPROVED}, {@link #REJECTED}, {@link #CANCELLED}, {@link #EXPIRED}) +
 * 시작점 {@link #REQUESTED}.
 *
 * <p>전이 규칙 (트랜잭션 + SELECT FOR UPDATE):
 * <ul>
 *   <li>REQUESTED → APPROVED: secondary 승인 → action 실행 + audit emit</li>
 *   <li>REQUESTED → REJECTED: secondary 거부 + decision_reason</li>
 *   <li>REQUESTED → CANCELLED: requested_by 본인 취소</li>
 *   <li>REQUESTED → EXPIRED: expiration cron, expires_at &lt;= NOW, actor_id=NULL</li>
 * </ul>
 *
 * <p>그 외 transition은 모두 {@code APPROVAL_ALREADY_DECIDED} (409 CONFLICT) — 서비스가 강제.
 *
 * <p>DB CHECK 제약 (V20):
 * <ul>
 *   <li>{@code status IN (...)} — 5개 enum 강제</li>
 *   <li>{@code (decided_at IS NULL) = (status = 'REQUESTED')} — terminal이면 decided_at 필수</li>
 *   <li>{@code (secondary_approver_id IS NOT NULL) = (status IN ('APPROVED','REJECTED'))} —
 *       CANCELLED/EXPIRED는 secondary 부재</li>
 * </ul>
 */
public enum PendingApprovalStatus {
    REQUESTED,
    APPROVED,
    REJECTED,
    CANCELLED,
    EXPIRED;

    public boolean isTerminal() {
        return this != REQUESTED;
    }
}
