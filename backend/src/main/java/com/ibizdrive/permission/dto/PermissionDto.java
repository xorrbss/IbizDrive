package com.ibizdrive.permission.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ibizdrive.permission.PermissionRow;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code POST /api/{resource}/{id}/permissions} 응답 본문 — {@code 201 { permission: PermissionDto }}.
 *
 * <p>{@link PermissionRow} 의 wire 표현 — {@code subjectType} / {@code preset} 등 string 컬럼은 그대로 노출
 * (V5 CHECK 와 일치). subjectId 는 everyone grant 시 NULL.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PermissionDto(
    UUID id,
    String resourceType,
    UUID resourceId,
    String subjectType,
    UUID subjectId,
    String preset,
    UUID grantedBy,
    Instant expiresAt,
    Instant createdAt
) {
    public static PermissionDto from(PermissionRow row) {
        return new PermissionDto(
            row.getId(),
            row.getResourceType(),
            row.getResourceId(),
            row.getSubjectType(),
            row.getSubjectId(),
            row.getPreset(),
            row.getGrantedBy(),
            row.getExpiresAt(),
            row.getCreatedAt()
        );
    }
}
