package com.ibizdrive.workspace.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ibizdrive.workspace.WorkspaceKind;

import java.util.List;
import java.util.UUID;

/**
 * {@code GET /api/workspaces/me} response — Plan A Task 15.
 *
 * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §5.
 * 사이드바 트리 첫 fetch + permission evaluation 진입 시 사용.
 *
 * <p>{@code department}는 user 소속 부서 1개 또는 null (미배정/비활성/root folder 미보유).
 * null인 경우 JSON에서 omit ({@link JsonInclude.Include#NON_NULL} — peer DTO {@code FolderDto}/{@code FileDto} 패턴과 일치).
 * {@code teams}는 멤버십 기반 0개 이상 — root folder 미보유 team은 제외 (Plan A2 backfill 대상).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkspaceMeResponse(WorkspaceRef department, List<WorkspaceRef> teams) {

    /** 단일 workspace 참조 — DepartmentWorkspace/TeamWorkspace에서 평탄화. */
    public record WorkspaceRef(WorkspaceKind kind, UUID id, String name, UUID rootFolderId) {}
}
