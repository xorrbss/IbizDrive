/**
 * Workspace 관련 wire types — Plan B foundation.
 *
 * backend `WorkspaceMeResponse` (com.ibizdrive.workspace.dto) 와 1:1.
 * spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §5.2.
 *
 * `kind`: backend `WorkspaceKind` enum 와 매칭 — 'department' | 'team' (정확 lower-case
 * 직렬화 — Plan A V12~V15 + Jackson default). 신규 'shared' 가상 종류는 frontend 전용
 * (UI에서만 의미 있음 — 사이드바 Section 3 + /shared/* 라우팅).
 */
export type WorkspaceKind = 'department' | 'team'

/** UI용 sidebar section 식별자 — `WorkspaceKind` 확장 + 'shared' 가상 섹션. */
export type SidebarSectionKind = 'department' | 'team' | 'shared'

export interface WorkspaceRef {
  kind: WorkspaceKind
  id: string
  name: string
  rootFolderId: string
}

/** GET /api/workspaces/me 응답. department 미배정 사용자는 null, teams는 항상 배열(0개 이상). */
export interface WorkspaceMeResponse {
  department: WorkspaceRef | null
  teams: WorkspaceRef[]
}
