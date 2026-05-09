package com.ibizdrive.workspace;

import java.util.List;
import java.util.Optional;

/**
 * {@link WorkspaceService#findForUser} 결과 — Plan A Task 14.
 *
 * <p>{@code department}는 user의 소속 부서가 active이고 root folder 보유 시에만 present.
 * {@code teams}는 user가 속한 active team 중 root folder 보유한 것만 — Plan A2 backfill 전 비어있을 수 있음.
 */
public record WorkspaceListing(
    Optional<DepartmentWorkspace> department,
    List<TeamWorkspace> teams
) {}
