package com.ibizdrive.file.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * RightPanel sharedWith 아바타 스택용 grant 요약 (P_panel). {@link com.ibizdrive.permission.dto.PermissionDto}
 * 와 유사하나 다음 필드만 노출:
 * <ul>
 *   <li>{@code subjectType}/{@code subjectId}/{@code subjectName} — 표시용</li>
 *   <li>{@code preset} — 권한 수준 시각 신호 (read/upload/edit/admin)</li>
 * </ul>
 *
 * <p>제외되는 PermissionDto 필드:
 * <ul>
 *   <li>{@code grantedBy} — privacy: PERMISSION_ADMIN 게이트가 적용된
 *       {@code GET /api/{resource}/{id}/permissions} 전용. 일반 READ 권한자에게 노출 금지</li>
 *   <li>{@code expiresAt}/{@code createdAt} — RightPanel UX 비대상 (별도 권한 탭에서만)</li>
 *   <li>{@code id} — sharedWith 행 식별자 불필요 (mutation 진입점 아님)</li>
 * </ul>
 *
 * <p>{@code subjectType='everyone'} 시 {@code subjectId=null}, {@code subjectName="전체"}.
 * user/department subject가 soft-deleted/lookup 실패 시 {@code subjectName=null} → FE placeholder.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubjectGrantBriefDto(
    String subjectType,
    UUID subjectId,
    String subjectName,
    String preset
) {}
