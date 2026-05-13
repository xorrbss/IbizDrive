package com.ibizdrive.approval;

/**
 * dual-approval framework — approve 시점에 {@code action_type}에 매칭되는
 * {@link AdminApprovalActionHandler}가 발견되지 않음.
 *
 * <p>운영 상황: action_type을 Phase 1/2에서 reserve(예: 'role_change')했지만 해당 action handler
 * (Phase 3+)가 아직 배포되지 않은 빌드에서 approve가 호출된 케이스. 트랜잭션 rollback →
 * status=REQUESTED 유지 → 운영자가 handler 배포 후 재승인.
 *
 * <p>{@code GlobalExceptionHandler}가 HTTP 409 + envelope code {@code APPROVAL_ALREADY_DECIDED}로
 * 매핑하지 않고 별도 500 INTERNAL로 매핑 — 운영 코드 부재가 사용자 입력 오류로 위장되지 않도록.
 * frontend는 일반 에러 토스트.
 */
public class UnknownApprovalActionException extends RuntimeException {
    public UnknownApprovalActionException(String actionType) {
        super("no handler registered for action_type: " + actionType);
    }
}
