package com.ibizdrive.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * `PUT /api/admin/users/{userId}/quota` 요청 본문 — quota mutation Phase 3 (`docs/02 §7.4`).
 *
 * <p>{@code storageQuota} 범위 {@code >= 0} (DB CHECK + Bean Validation 이중 가드).
 * null/누락은 400 VALIDATION_ERROR.
 *
 * <p>{@code storage_used}는 본 endpoint에서 변경하지 않는다 (Phase 5 enforcement에서만 증가).
 */
public record AdminUserQuotaUpdateRequest(
    @NotNull(message = "storageQuota is required")
    @Min(value = 0, message = "storageQuota must be >= 0")
    Long storageQuota
) {}
