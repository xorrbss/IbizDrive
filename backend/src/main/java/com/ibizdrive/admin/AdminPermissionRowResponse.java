package com.ibizdrive.admin;

import java.time.Instant;
import java.util.UUID;

/**
 * Wave 2 T5 — admin 권한 매트릭스 row 응답.
 *
 * <p>{@code GET /api/admin/permissions} 응답의 단일 row.
 *
 * <p><b>name 해석 정책</b>:
 * <ul>
 *   <li>{@code subjectName}: subject_type='user' → user.display_name, 'department' → dept.name,
 *       'role' → subject_id 의 UUID text (V5 schema artifact, MVP 평가 미사용 — docs/03 §3.4).
 *       'everyone' → {@code "전사"} 리터럴. 매칭 row 미존재(soft-delete 등) → {@code null} → controller 측 {@code null} 그대로 응답.</li>
 *   <li>{@code resourceName}: 'folder' → folders.name, 'file' → files.name. 매칭 row 미존재 → {@code null}.</li>
 *   <li>{@code grantedByName}: granted_by user 의 display_name. FK NOT NULL 이지만 user soft-delete 가능 — null 허용.</li>
 * </ul>
 *
 * <p>{@code isExpired}: service 가 derive — {@code expiresAt != null && expiresAt <= NOW()}.
 * V5 schema 의 {@code expires_at} 은 admin 매트릭스 표시 정책상 만료 row 도 포함 (cron 정리 전 가시화).
 */
public record AdminPermissionRowResponse(
    UUID id,
    String subjectType,
    UUID subjectId,
    String subjectName,
    String resourceType,
    UUID resourceId,
    String resourceName,
    String preset,
    UUID grantedByActorId,
    String grantedByName,
    Instant grantedAt,
    Instant expiresAt,
    boolean isExpired
) {
}
