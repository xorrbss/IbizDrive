package com.ibizdrive.approval;

/**
 * dual-approval framework — terminal status (APPROVED/REJECTED/CANCELLED/EXPIRED) row에 재결정 시도.
 * {@code GlobalExceptionHandler}가 HTTP 409 + envelope code {@code APPROVAL_ALREADY_DECIDED}로 매핑
 * (docs/02 §8, ADR #47).
 */
public class AlreadyDecidedException extends RuntimeException {

    private final PendingApprovalStatus currentStatus;

    public AlreadyDecidedException(String message, PendingApprovalStatus currentStatus) {
        super(message);
        this.currentStatus = currentStatus;
    }

    public PendingApprovalStatus getCurrentStatus() {
        return currentStatus;
    }
}
