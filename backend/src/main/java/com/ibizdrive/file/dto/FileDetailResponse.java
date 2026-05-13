package com.ibizdrive.file.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ibizdrive.folder.dto.BreadcrumbCrumbDto;
import com.ibizdrive.user.dto.UserBriefDto;

import java.util.List;

/**
 * {@code GET /api/files/{id}} 응답 envelope (P_panel-A — RightPanel backend 확장).
 *
 * <p>이전: {@code { file: FileDto }}. 본 트랙: 동일한 최상위 {@code file} 키를 보존하면서 RightPanel 이
 * 추가로 필요로 하는 정보를 동봉 — FE 기존 호출부({@code body.file.*})는 회귀 없음.
 *
 * <ul>
 *   <li>{@link #file} — 기존 {@link FileDto} (id/folderId/name/ownerId/size/mimeType/...)</li>
 *   <li>{@link #owner} — file.ownerId 의 사용자 brief. soft-delete/missing 시 {@code null}</li>
 *   <li>{@link #sharedWith} — 본 파일 자체에 부여된 active grant 의 brief 리스트. created_at ASC.
 *       PERMISSION_ADMIN 게이트가 있는 {@code /permissions} list 와 달리 일반 READ 권한자에게 노출되므로
 *       {@code grantedBy} 등 privacy 필드는 제외 ({@link SubjectGrantBriefDto} 참고).</li>
 *   <li>{@link #folderPath} — file의 폴더 부모 체인 (root → 직접 부모). soft-deleted 폴더는 체인 break 지점.
 *       파일이 root 폴더 직접 자식이면 단일 원소.</li>
 * </ul>
 *
 * <p><b>viewCount 미포함 (ADR #9 blocker)</b> — {@code FILE_VIEWED} audit emit 결정 보류. 본 트랙 scope 밖.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FileDetailResponse(
    FileDto file,
    UserBriefDto owner,
    List<SubjectGrantBriefDto> sharedWith,
    List<BreadcrumbCrumbDto> folderPath
) {}
