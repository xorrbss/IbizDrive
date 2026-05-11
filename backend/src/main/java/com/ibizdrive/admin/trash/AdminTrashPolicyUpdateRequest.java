package com.ibizdrive.admin.trash;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * `PUT /api/admin/trash/policy` 요청 본문 — trash-retention-mutation Phase B (docs/02 §7.11.1).
 *
 * <p>{@code days} 범위 7..90 (DB CHECK + Bean Validation 이중 가드). null/누락은 400 VALIDATION_ERROR
 * (`@NotNull`).
 */
public record AdminTrashPolicyUpdateRequest(
    @NotNull(message = "days is required")
    @Min(value = 7, message = "days must be between 7 and 90")
    @Max(value = 90, message = "days must be between 7 and 90")
    Integer days
) {}
