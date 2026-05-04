package com.ibizdrive.admin;

/**
 * Admin endpoint에서 target user 미존재 — admin-user-mgmt.
 *
 * <p>HTTP 404 + body {@code { code: "NOT_FOUND", reason: "USER_NOT_FOUND" }}로 매핑된다
 * ({@link com.ibizdrive.common.error.AdminExceptionHandler}).
 */
public class AdminUserNotFoundException extends RuntimeException {
    public AdminUserNotFoundException(String userId) {
        super("user not found: " + userId);
    }
}
