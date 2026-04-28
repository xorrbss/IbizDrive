package com.ibizdrive.security;

/**
 * 권한 enum — docs/03 §3.1의 백엔드 단일 진실 출처.
 *
 * <p>프론트 {@code src/types/permission.ts}는 UX용 mirror이며, 보안 판단은 서버의
 * {@link PermissionService}와 {@code @PreAuthorize("hasPermission(...)")}가 담당한다.
 */
public enum Permission {
    READ,
    UPLOAD,
    EDIT,
    MOVE,
    DOWNLOAD,
    DELETE,
    SHARE,
    PERMISSION_ADMIN,
    PURGE
}
