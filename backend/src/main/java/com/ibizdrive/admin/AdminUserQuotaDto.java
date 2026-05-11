package com.ibizdrive.admin;

/**
 * `GET/PUT /api/admin/users/{userId}/quota` 응답 envelope — quota mutation Phase 3.
 *
 * <p>`storageQuota`/`storageUsed` 둘 다 bytes. mutation 후 응답에는 적용된 값이 들어간다
 * (idempotent same-value 호출도 동일 응답).
 */
public record AdminUserQuotaDto(
    long storageQuota,
    long storageUsed
) {}
