package com.ibizdrive.permission;

/**
 * 동일 (resource, subject) 조합에 이미 grant 가 존재할 때 발생 — A4.4.
 *
 * <p>{@link PermissionService#grantPermission} 가 V5 의 {@code idx_permissions_unique} 위반을 받아 변환.
 * {@code GlobalExceptionHandler} 가 {@code 409 PERMISSION_CONFLICT} envelope 으로 매핑한다.
 */
public class PermissionConflictException extends RuntimeException {
    public PermissionConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
