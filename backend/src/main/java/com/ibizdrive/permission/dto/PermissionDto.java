package com.ibizdrive.permission.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ibizdrive.permission.PermissionRow;

import java.time.Instant;
import java.util.UUID;

/**
 * {@link PermissionRow} 의 wire 표현. 다음 endpoint 응답에서 사용:
 * <ul>
 *   <li>{@code POST /api/{resource}/{id}/permissions} → {@code 201 { permission: PermissionDto }}.</li>
 *   <li>{@code GET /api/{resource}/{id}/permissions} → {@code 200 { items: PermissionDto[] }} (M8.0).</li>
 * </ul>
 *
 * <p>{@code subjectType} / {@code preset} 등 string 컬럼은 그대로 노출 (V5 CHECK 와 일치).
 * {@code subjectId} 는 everyone grant 시 NULL.
 *
 * <p><b>M8.0</b> — {@code subjectName} 필드 추가 (A16/ADR #36 ShareDto 패턴 동형). subject 표시명을
 * backend 에서 join 으로 채워 frontend 가 별도 lookup 없이 한 row 로 표시 가능. {@code subject_type='user'} →
 * {@code users.display_name}, {@code subject_type='department'} → {@code departments.name}, 그 외/lookup miss
 * (soft-delete 등) → null. POST 응답은 {@link #from(PermissionRow)} 가 subjectName=null 로 비워 둔다
 * (호출자가 필요 시 list 재조회). GET list 응답은 {@link com.ibizdrive.permission.PermissionService#listPermissions}
 * 가 batch resolve 후 {@link #from(PermissionRow, String)} 로 채운다.
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
    Instant createdAt,
    String subjectName
) {
    /** POST 응답 경로 — subjectName 미해결(null). */
    public static PermissionDto from(PermissionRow row) {
        return from(row, null);
    }

    /** GET list 응답 경로 — 호출자가 user/department batch lookup 으로 resolve 한 표시명을 주입. */
    public static PermissionDto from(PermissionRow row, String subjectName) {
        return new PermissionDto(
            row.getId(),
            row.getResourceType(),
            row.getResourceId(),
            row.getSubjectType(),
            row.getSubjectId(),
            row.getPreset(),
            row.getGrantedBy(),
            row.getExpiresAt(),
            row.getCreatedAt(),
            subjectName
        );
    }
}
