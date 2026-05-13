package com.ibizdrive.approval;

/**
 * dual-approval framework — approval id 미존재 (또는 다른 사용자가 cancel 시도).
 * {@code GlobalExceptionHandler}가 HTTP 404 + envelope code {@code APPROVAL_NOT_FOUND}로 매핑
 * (docs/02 §8, ADR #47).
 */
public class PendingApprovalNotFoundException extends RuntimeException {
    public PendingApprovalNotFoundException(String message) {
        super(message);
    }
}
