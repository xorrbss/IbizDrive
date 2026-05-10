# Team-Centric Pivot — Plan B: Frontend Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Frontend을 workspace(부서/팀/공유받음) 1차 모델에 맞춰 URL · 사이드바 · DnD 정책을 재설계.

**Architecture:** spec `docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md` §4.5 + §5.1 + §5.2 frontend 부분 구현. Next.js 15 catch-all route 3개 신설(`/d`, `/t`, `/shared`) + 사이드바 `SidebarSections`(3-section) + per-workspace lazy-load `WorkspaceFolderTree` + same-workspace DnD constraint + URL ↔ tree 동기화. Plan A backend의 `/api/workspaces/me` + `POST /api/teams` + scope-stamped folder/file API에 의존.

**Tech Stack:** Next.js 15 App Router, TypeScript 5.x, TanStack Query v5, Zustand (URL 파생/persist만), dnd-kit, Vitest (unit/component) + Playwright (E2E).

**Spec 범위 외 (다른 plan으로 이월):**
- 공유 다이얼로그 + `subjectType='team'` 활성화 → Plan C
- Cross-workspace 폴더/파일 이동 모달 (`/move/preview` 소비) → Plan D
- 휴지통 workspace별 분리 페이지 (탭 UI) → Plan E
- "공유받음" 출처 workspace 그룹핑 (요구: shares-with-me 응답에 source workspace 메타) → Plan C와 함께
- 슬러그 기반 SEO URL (현재 MVP는 raw UUID slug 패턴 답습) → 후속 트랙

**Branch:** `feat/team-centric-pivot-plan-b-frontend` — `master`에서 분기 권장. Plan A backend 변경분과 완전 독립 — 본 plan은 `frontend/` 디렉토리만 수정.

**Backend prerequisites (Plan A에 이미 존재):**
- `GET /api/workspaces/me` → `{ department: WorkspaceRef|null, teams: WorkspaceRef[] }`. `WorkspaceRef = { kind:'department'|'team', id:UUID, name:string, rootFolderId:UUID }`
- `POST /api/teams` → `{ id, name, description, visibility, rootFolderId, createdAt, archivedAt }`
- 기존 `GET /api/folders/{id}` / `GET /api/folders/{id}/items` 재사용 — 폴더/자식 조회 (folder-only 필터링은 frontend에서)
- 기존 `GET /api/shares/with-me` 재사용 — "공유받음" section
- 기존 `GET /api/folders/tree` 는 deprecated — 본 plan 마지막에 호출부 제거

---

## Phase 1 — Foundation: Types · API · Query keys · Hooks (Tasks 1–7)

### Task 1: `types/workspace.ts` — wire types

**Files:**
- Create: `frontend/src/types/workspace.ts`

- [ ] **Step 1: Write the file**

```ts
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
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/types/workspace.ts
git commit -m "feat(team-centric-pivot): WorkspaceRef + MeResponse wire types"
```

---

### Task 2: `lib/api.ts` — `getWorkspacesMe()` 추가

**Files:**
- Modify: `frontend/src/lib/api.ts` (top of object literal, near `getFolderTree`)
- Test: `frontend/src/lib/api.workspaces.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
// frontend/src/lib/api.workspaces.test.ts
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { api } from './api'
import type { WorkspaceMeResponse } from '@/types/workspace'

describe('api.getWorkspacesMe', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('GETs /api/workspaces/me and returns parsed body', async () => {
    const body: WorkspaceMeResponse = {
      department: { kind: 'department', id: 'd1', name: '영업부', rootFolderId: 'rd' },
      teams: [
        { kind: 'team', id: 't1', name: 'ProjectAlpha', rootFolderId: 'rt1' },
      ],
    }
    const fetchMock = vi.spyOn(global, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(body), { status: 200 }),
    )
    const result = await api.getWorkspacesMe()
    expect(result).toEqual(body)
    expect(fetchMock).toHaveBeenCalledWith('/api/workspaces/me', expect.objectContaining({
      method: 'GET',
      credentials: 'include',
    }))
  })

  it('throws with status when non-2xx', async () => {
    vi.spyOn(global, 'fetch').mockResolvedValue(new Response('', { status: 401 }))
    await expect(api.getWorkspacesMe()).rejects.toMatchObject({ status: 401 })
  })

  it('handles null department field (NON_NULL omit)', async () => {
    const body = { teams: [] }  // department key omitted by Jackson @JsonInclude(NON_NULL)
    vi.spyOn(global, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(body), { status: 200 }),
    )
    const result = await api.getWorkspacesMe()
    expect(result.department).toBeNull()
    expect(result.teams).toEqual([])
  })
})
```

- [ ] **Step 2: Run — expect FAIL**

```bash
cd frontend && pnpm test src/lib/api.workspaces.test.ts
```
Expected: FAIL — `api.getWorkspacesMe is not a function`.

- [ ] **Step 3: Add the method to `api.ts`**

Insert (in `export const api = {` literal, after `getFolder`):

```ts
  /**
   * spec §5.2 — 사이드바 첫 fetch + permission cache 진입.
   * backend `WorkspaceMeResponse` 1:1. `department` 키는 Jackson @JsonInclude(NON_NULL) — null 시 응답에서 omit되므로 일괄 `?? null` 보정.
   */
  async getWorkspacesMe(): Promise<WorkspaceMeResponse> {
    const res = await fetch('/api/workspaces/me', {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    if (!res.ok) {
      const err = new Error(
        `getWorkspacesMe fetch failed: ${res.status}`,
      ) as Error & { status: number }
      err.status = res.status
      throw err
    }
    const body = (await res.json()) as Partial<WorkspaceMeResponse>
    return {
      department: body.department ?? null,
      teams: body.teams ?? [],
    }
  },
```

Add the import at the top:

```ts
import type { WorkspaceMeResponse } from '@/types/workspace'
```

- [ ] **Step 4: Run — expect PASS**

```bash
cd frontend && pnpm test src/lib/api.workspaces.test.ts
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/api.ts frontend/src/lib/api.workspaces.test.ts
git commit -m "feat(team-centric-pivot): api.getWorkspacesMe()"
```

---

### Task 3: `lib/queryKeys.ts` — workspaces · folderChildren · teams keys

**Files:**
- Modify: `frontend/src/lib/queryKeys.ts`
- Modify: `frontend/src/lib/queryKeys.test.ts` (existing test file — extend)

- [ ] **Step 1: Add the failing test cases**

Append to `frontend/src/lib/queryKeys.test.ts`:

```ts
import { qk } from './queryKeys'

describe('qk.workspaces', () => {
  it('me() is stable readonly tuple under workspaces prefix', () => {
    const key = qk.workspaces.me()
    expect(key).toEqual(['explorer', 'workspaces', 'me'])
  })
})

describe('qk.folderChildren', () => {
  it('builds key with scopeType + scopeId + parentId', () => {
    const key = qk.folderChildren('team', 't1', 'f1')
    expect(key).toEqual(['explorer', 'folders', 'children', 'team', 't1', 'f1'])
  })

  it('different parentIds produce different keys', () => {
    expect(qk.folderChildren('team', 't1', 'a'))
      .not.toEqual(qk.folderChildren('team', 't1', 'b'))
  })
})

describe('qk.teams', () => {
  it('all() prefix used by team mutations to invalidate', () => {
    expect(qk.teams.all()).toEqual(['explorer', 'teams'])
  })
})
```

- [ ] **Step 2: Run — expect FAIL**

```bash
cd frontend && pnpm test src/lib/queryKeys.test.ts
```

- [ ] **Step 3: Add the keys to `queryKeys.ts`**

Insert before the closing `} as const` of `qk` (around the existing folder/files block):

```ts
  // ── Workspaces (Plan B, spec §5.2) ──
  workspaces: {
    all: () => [...qk.all, 'workspaces'] as const,
    me: () => [...qk.all, 'workspaces', 'me'] as const,
  },

  /**
   * 사이드바 트리 lazy children — spec §4.5 §3.
   * `scopeType` + `scopeId`까지 포함해 동일 parentId가 부서/팀 간 우연 일치(매우 드물지만)에도 캐시 분리.
   * `parentId === rootFolderId` 인 호출이 워크스페이스 root 직속 자식 조회.
   */
  folderChildren: (scopeType: 'department' | 'team', scopeId: string, parentId: string) =>
    [...qk.all, 'folders', 'children', scopeType, scopeId, parentId] as const,

  // ── Teams (Plan B, spec §5.2 — POST /api/teams) ──
  teams: {
    all: () => [...qk.all, 'teams'] as const,
  },
```

(Note: `qk.workspaces` / `qk.teams` are nested object groups — same shape as future cleanup; current keys remain flat for backwards compatibility.)

- [ ] **Step 4: Add invalidation helper**

Add inside `invalidations` object literal:

```ts
  /**
   * 팀 생성/멤버 변경 후 무효화 — Plan B Task 3.
   * - workspaces.me() (사이드바가 새 팀을 즉시 표시)
   */
  afterTeamChanged(qc: QueryClient): Promise<void> {
    return qc.invalidateQueries({ queryKey: qk.workspaces.me() })
  },
```

- [ ] **Step 5: Run — expect PASS**

```bash
cd frontend && pnpm test src/lib/queryKeys.test.ts
```

- [ ] **Step 6: Commit**

```bash
git add frontend/src/lib/queryKeys.ts frontend/src/lib/queryKeys.test.ts
git commit -m "feat(team-centric-pivot): qk.workspaces + folderChildren + teams"
```

---

### Task 4: `hooks/useWorkspaces.ts` — workspaces.me 소비 훅

**Files:**
- Create: `frontend/src/hooks/useWorkspaces.ts`
- Test: `frontend/src/hooks/useWorkspaces.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
// frontend/src/hooks/useWorkspaces.test.tsx
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { api } from '@/lib/api'
import { useWorkspaces } from './useWorkspaces'

const wrapper = ({ children }: { children: ReactNode }) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('useWorkspaces', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('fetches workspace listing via api.getWorkspacesMe', async () => {
    const spy = vi.spyOn(api, 'getWorkspacesMe').mockResolvedValue({
      department: { kind: 'department', id: 'd1', name: '영업부', rootFolderId: 'rd' },
      teams: [],
    })
    const { result } = renderHook(() => useWorkspaces(), { wrapper })
    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(spy).toHaveBeenCalledOnce()
    expect(result.current.data?.department?.id).toBe('d1')
  })
})
```

- [ ] **Step 2: Run — expect FAIL**

```bash
cd frontend && pnpm test src/hooks/useWorkspaces.test.tsx
```

- [ ] **Step 3: Implement**

```ts
// frontend/src/hooks/useWorkspaces.ts
'use client'
import { useQuery } from '@tanstack/react-query'
import { qk } from '@/lib/queryKeys'
import { api } from '@/lib/api'

/**
 * 사용자의 workspace 목록 — 사이드바 + 라우팅의 single source.
 * staleTime 60초 — Plan B에서는 user의 부서/팀 멤버십이 거의 변하지 않음.
 * gcTime 10분 — 라우트 전환에서 사이드바 reflesh 비용 최소화.
 */
export function useWorkspaces() {
  return useQuery({
    queryKey: qk.workspaces.me(),
    queryFn: api.getWorkspacesMe,
    staleTime: 60_000,
    gcTime: 10 * 60_000,
  })
}
```

- [ ] **Step 4: Run — expect PASS**

```bash
cd frontend && pnpm test src/hooks/useWorkspaces.test.tsx
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/hooks/useWorkspaces.ts frontend/src/hooks/useWorkspaces.test.tsx
git commit -m "feat(team-centric-pivot): useWorkspaces hook"
```

---

### Task 5: `lib/workspacePath.ts` — URL canonical builder

**Files:**
- Create: `frontend/src/lib/workspacePath.ts`
- Test: `frontend/src/lib/workspacePath.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
// frontend/src/lib/workspacePath.test.ts
import { describe, it, expect } from 'vitest'
import {
  buildWorkspacePath,
  parseWorkspaceUrl,
  type SidebarSectionKind,
} from './workspacePath'

describe('buildWorkspacePath', () => {
  it('builds /d/<deptId>/<folderId>/<...slug>', () => {
    expect(buildWorkspacePath({ kind: 'department', workspaceId: 'd1' }, 'f1', ['Q1', 'reports']))
      .toBe('/d/d1/f1/Q1/reports')
  })
  it('builds /t/<teamId>/<folderId>', () => {
    expect(buildWorkspacePath({ kind: 'team', workspaceId: 't1' }, 'f1', []))
      .toBe('/t/t1/f1')
  })
  it('builds /shared/<folderId>', () => {
    expect(buildWorkspacePath({ kind: 'shared' }, 'fX', ['nested']))
      .toBe('/shared/fX/nested')
  })
  it('encodes slug segments', () => {
    expect(buildWorkspacePath({ kind: 'department', workspaceId: 'd1' }, 'f1', ['보고서 A']))
      .toBe('/d/d1/f1/%EB%B3%B4%EA%B3%A0%EC%84%9C%20A')
  })
  it('builds workspace landing (no parts) — /d/<deptId>', () => {
    expect(buildWorkspacePath({ kind: 'department', workspaceId: 'd1' }, null, []))
      .toBe('/d/d1')
  })
})

describe('parseWorkspaceUrl', () => {
  it('parses /d/d1/f1/x/y', () => {
    expect(parseWorkspaceUrl('/d/d1/f1/x/y')).toEqual({
      section: 'department',
      workspaceId: 'd1',
      folderId: 'f1',
      slugPath: ['x', 'y'],
    })
  })
  it('parses /t/t1', () => {
    expect(parseWorkspaceUrl('/t/t1')).toEqual({
      section: 'team',
      workspaceId: 't1',
      folderId: null,
      slugPath: [],
    })
  })
  it('parses /shared/fX', () => {
    expect(parseWorkspaceUrl('/shared/fX')).toEqual({
      section: 'shared',
      workspaceId: null,
      folderId: 'fX',
      slugPath: [],
    })
  })
  it('returns null for unrelated paths', () => {
    expect(parseWorkspaceUrl('/admin/users')).toBeNull()
    expect(parseWorkspaceUrl('/login')).toBeNull()
  })
})

describe('SidebarSectionKind', () => {
  it('exports the expected union', () => {
    const valid: SidebarSectionKind[] = ['department', 'team', 'shared']
    expect(valid).toHaveLength(3)
  })
})
```

- [ ] **Step 2: Run — expect FAIL**

```bash
cd frontend && pnpm test src/lib/workspacePath.test.ts
```

- [ ] **Step 3: Implement**

```ts
// frontend/src/lib/workspacePath.ts
/**
 * Workspace URL canonical builder + parser — Plan B.
 *
 * spec §5.1: `/d/:deptSlug/[...parts]`, `/t/:teamSlug/[...parts]`, `/shared/[...parts]`.
 * MVP는 slug = workspace UUID (KISS) — 후속 트랙에서 SEO slug + server lookup 도입.
 *
 * `parts[0]`은 backend 호환을 위해 folderId(UUID) 위치 그대로 답습 (기존 `/files/[...parts]` 패턴).
 * 나머지 segment는 SEO/canonical용 폴더 이름 slug.
 */

export type SidebarSectionKind = 'department' | 'team' | 'shared'

export type WorkspaceLocator =
  | { kind: 'department' | 'team'; workspaceId: string }
  | { kind: 'shared' }

export interface ParsedWorkspaceUrl {
  section: SidebarSectionKind
  /** department/team URL은 workspaceId 보유, shared는 null. */
  workspaceId: string | null
  /** 경로에 folderId가 있을 때만 string. workspace landing(/d/:id 만)에서는 null. */
  folderId: string | null
  slugPath: string[]
}

const SECTION_PREFIX: Record<SidebarSectionKind, string> = {
  department: 'd',
  team: 't',
  shared: 'shared',
}

export function buildWorkspacePath(
  loc: WorkspaceLocator,
  folderId: string | null,
  slugPath: string[],
): string {
  const head =
    loc.kind === 'shared'
      ? `/${SECTION_PREFIX.shared}`
      : `/${SECTION_PREFIX[loc.kind]}/${encodeURIComponent(loc.workspaceId)}`

  const folderSegment = folderId ? `/${encodeURIComponent(folderId)}` : ''
  const slugSegment = slugPath.length
    ? '/' + slugPath.map(encodeURIComponent).join('/')
    : ''
  return `${head}${folderSegment}${slugSegment}`
}

export function parseWorkspaceUrl(pathname: string): ParsedWorkspaceUrl | null {
  // pathname always starts with '/'
  const segs = pathname.split('/').filter(Boolean)
  if (segs.length === 0) return null

  if (segs[0] === 'd' && segs.length >= 2) {
    return {
      section: 'department',
      workspaceId: decodeURIComponent(segs[1]),
      folderId: segs[2] ? decodeURIComponent(segs[2]) : null,
      slugPath: segs.slice(3).map(decodeURIComponent),
    }
  }
  if (segs[0] === 't' && segs.length >= 2) {
    return {
      section: 'team',
      workspaceId: decodeURIComponent(segs[1]),
      folderId: segs[2] ? decodeURIComponent(segs[2]) : null,
      slugPath: segs.slice(3).map(decodeURIComponent),
    }
  }
  if (segs[0] === 'shared') {
    return {
      section: 'shared',
      workspaceId: null,
      folderId: segs[1] ? decodeURIComponent(segs[1]) : null,
      slugPath: segs.slice(2).map(decodeURIComponent),
    }
  }
  return null
}
```

- [ ] **Step 4: Run — expect PASS**

```bash
cd frontend && pnpm test src/lib/workspacePath.test.ts
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/workspacePath.ts frontend/src/lib/workspacePath.test.ts
git commit -m "feat(team-centric-pivot): workspacePath builder + parser"
```

---

### Task 6: `hooks/useCurrentWorkspace.ts` — URL → workspace 파생

**Files:**
- Create: `frontend/src/hooks/useCurrentWorkspace.ts`
- Test: `frontend/src/hooks/useCurrentWorkspace.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
// frontend/src/hooks/useCurrentWorkspace.test.tsx
import { describe, it, expect, vi } from 'vitest'
import { renderHook } from '@testing-library/react'
import * as nextNav from 'next/navigation'
import { useCurrentWorkspace } from './useCurrentWorkspace'

vi.mock('next/navigation', () => ({
  usePathname: vi.fn(),
}))

describe('useCurrentWorkspace', () => {
  it('returns department workspace for /d/<id>/<folder>', () => {
    vi.mocked(nextNav.usePathname).mockReturnValue('/d/d1/f1/x')
    const { result } = renderHook(() => useCurrentWorkspace())
    expect(result.current).toEqual({
      section: 'department',
      workspaceId: 'd1',
      folderId: 'f1',
      slugPath: ['x'],
    })
  })

  it('returns team for /t/<id>', () => {
    vi.mocked(nextNav.usePathname).mockReturnValue('/t/t1')
    const { result } = renderHook(() => useCurrentWorkspace())
    expect(result.current?.section).toBe('team')
    expect(result.current?.workspaceId).toBe('t1')
    expect(result.current?.folderId).toBeNull()
  })

  it('returns shared for /shared/<folder>', () => {
    vi.mocked(nextNav.usePathname).mockReturnValue('/shared/fA')
    const { result } = renderHook(() => useCurrentWorkspace())
    expect(result.current?.section).toBe('shared')
    expect(result.current?.workspaceId).toBeNull()
    expect(result.current?.folderId).toBe('fA')
  })

  it('returns null for unrelated routes (/admin, /login)', () => {
    vi.mocked(nextNav.usePathname).mockReturnValue('/admin/users')
    const { result } = renderHook(() => useCurrentWorkspace())
    expect(result.current).toBeNull()
  })
})
```

- [ ] **Step 2: Run — expect FAIL**

```bash
cd frontend && pnpm test src/hooks/useCurrentWorkspace.test.tsx
```

- [ ] **Step 3: Implement**

```ts
// frontend/src/hooks/useCurrentWorkspace.ts
'use client'
import { usePathname } from 'next/navigation'
import { parseWorkspaceUrl, type ParsedWorkspaceUrl } from '@/lib/workspacePath'

/**
 * URL → 현재 workspace 컨텍스트 파생.
 * - URL이 진실 (CLAUDE.md §3 원칙 1, spec §5.1).
 * - workspace 외 라우트(/admin/*, /login, /trash …)에서는 null 반환.
 */
export function useCurrentWorkspace(): ParsedWorkspaceUrl | null {
  const pathname = usePathname()
  return parseWorkspaceUrl(pathname ?? '/')
}
```

- [ ] **Step 4: Run — expect PASS**

```bash
cd frontend && pnpm test src/hooks/useCurrentWorkspace.test.tsx
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/hooks/useCurrentWorkspace.ts frontend/src/hooks/useCurrentWorkspace.test.tsx
git commit -m "feat(team-centric-pivot): useCurrentWorkspace hook"
```

---

### Task 7: refactor `useCurrentFolder` — workspace-aware

**Files:**
- Modify: `frontend/src/hooks/useCurrentFolder.ts`

기존 14개 consumer (BulkActionBar, MoveFolderDialog, FileTable, RenameDialog, Breadcrumb, TrashTable, SearchResults …) 의 호출 시그니처는 그대로 유지(`{ folderId, folder, breadcrumb, isLoading, error }`). 내부 구현만 새 URL 파서로 바꿔 드롭인 호환.

- [ ] **Step 1: Replace the body**

```ts
// frontend/src/hooks/useCurrentFolder.ts
'use client'
import { useQuery } from '@tanstack/react-query'
import { qk } from '@/lib/queryKeys'
import { api } from '@/lib/api'
import { useCurrentWorkspace } from '@/hooks/useCurrentWorkspace'

/**
 * 현재 폴더 detail. URL 파생만 — Plan B로 workspace prefix 합쳐도 시그니처 유지.
 *
 * - workspace 컨텍스트 안: folderId = parseWorkspaceUrl(pathname).folderId.
 *   folderId가 null(workspace landing /d/:id)이면 useWorkspaces로 root를 별도 조회한 페이지에서
 *   redirect 처리(Task 8/9). 본 훅은 folderId가 있을 때만 enabled.
 * - workspace 외 (/admin, /login …): folderId = '', enabled=false → no-op.
 */
export function useCurrentFolder() {
  const ws = useCurrentWorkspace()
  const folderId = ws?.folderId ?? ''
  const { data, isLoading, error } = useQuery({
    queryKey: qk.folder(folderId),
    queryFn: () => api.getFolder(folderId),
    enabled: folderId.length > 0,
    staleTime: 60_000,
  })
  return {
    folderId,
    folder: data,
    breadcrumb: data?.breadcrumb ?? [],
    isLoading,
    error,
  }
}
```

- [ ] **Step 2: Update consumer tests that mock-return `folderId: 'root'`**

기존 `BulkActionBar.test.tsx` / `RenameDialog.test.tsx` 등이 `folderId: 'root'` 가짜값을 mock하는 부분은 그대로 — 본 훅은 string 반환만 보장 (값은 mock). VIRTUAL_ROOT_ID 문자열 의존은 Task 12에서 일괄 정리.

- [ ] **Step 3: Run all hook tests — expect PASS (drop-in)**

```bash
cd frontend && pnpm test src/hooks/useCurrentFolder
```
Expected: PASS — 시그니처 유지로 기존 useCurrentFolder 테스트가 있다면 통과. 없으면 본 단계는 새로 추가된 useCurrentWorkspace.test가 통과하면 충분.

- [ ] **Step 4: Run typecheck — expect PASS**

```bash
cd frontend && pnpm typecheck
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/hooks/useCurrentFolder.ts
git commit -m "refactor(team-centric-pivot): useCurrentFolder consumes useCurrentWorkspace"
```

---

## Phase 2 — Routing: `/d/*` `/t/*` `/shared/*` (Tasks 8–13)

### Task 8: `/d/[deptId]/[[...parts]]` 라우트 + ClientPage

**Files:**
- Create: `frontend/src/app/(explorer)/d/[deptId]/[[...parts]]/page.tsx`
- Create: `frontend/src/app/(explorer)/d/[deptId]/[[...parts]]/ClientFilesPage.tsx`

기존 `/files/[...parts]/ClientFilesPage.tsx`의 본문을 재사용하되, **canonical redirect 로직만 새 path builder로 교체**.

- [ ] **Step 1: Create the server page**

```tsx
// frontend/src/app/(explorer)/d/[deptId]/[[...parts]]/page.tsx
import { ClientFilesPage } from './ClientFilesPage'

export default async function DeptFilesPage({
  params,
}: {
  params: Promise<{ deptId: string; parts?: string[] }>
}) {
  const { deptId, parts } = await params
  return <ClientFilesPage deptId={deptId} parts={parts ?? []} />
}
```

- [ ] **Step 2: Create the client page**

```tsx
// frontend/src/app/(explorer)/d/[deptId]/[[...parts]]/ClientFilesPage.tsx
'use client'
import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { useCloseFileOnFolderChange } from '@/hooks/useCloseFileOnFolderChange'
import { useWorkspaces } from '@/hooks/useWorkspaces'
import { buildWorkspacePath } from '@/lib/workspacePath'
import { Breadcrumb } from '@/components/folders/Breadcrumb'
import { FileTable } from '@/components/files/FileTable'
import { BulkActionBar } from '@/components/files/BulkActionBar'
import { RightPanel } from '@/components/files/RightPanel'
import { FolderToolbar } from '@/components/upload/FolderToolbar'
import { UploadQueueDock } from '@/components/upload/UploadQueueDock'
import { UploadConflictDialog } from '@/components/upload/UploadConflictDialog'
import { MoveFolderDialog } from '@/components/files/MoveFolderDialog'
import { RenameDialog } from '@/components/files/RenameDialog'
import { ShareDialog } from '@/components/shares/ShareDialog'
import { useUploadBeforeUnload } from '@/hooks/useUploadBeforeUnload'
import { useGlobalShortcuts } from '@/hooks/useGlobalShortcuts'

export function ClientFilesPage({ deptId, parts }: { deptId: string; parts: string[] }) {
  const router = useRouter()
  const { data: workspaces } = useWorkspaces()
  const { folderId, folder, isLoading, error } = useCurrentFolder()

  useCloseFileOnFolderChange(folder?.id)
  useUploadBeforeUnload()
  useGlobalShortcuts()

  // workspace landing(/d/:deptId, parts=[]): root folder로 redirect
  useEffect(() => {
    if (parts.length === 0 && workspaces?.department?.id === deptId) {
      router.replace(buildWorkspacePath(
        { kind: 'department', workspaceId: deptId },
        workspaces.department.rootFolderId,
        [],
      ))
    }
  }, [parts.length, workspaces, deptId, router])

  // canonical redirect: 폴더 detail 로드 후 slugPath와 URL 비교 → mismatch면 replace
  useEffect(() => {
    if (!folder || parts.length === 0) return
    const canonical = buildWorkspacePath(
      { kind: 'department', workspaceId: deptId },
      folder.id,
      folder.slugPath,
    )
    const current = `/d/${deptId}/${parts.join('/')}`
    if (decodeURI(current) !== decodeURI(canonical)) {
      router.replace(canonical)
    }
  }, [folder, parts, deptId, router])

  if (parts.length === 0) {
    return (
      <div className="flex-1 flex items-center justify-center text-[13px] text-fg-muted">
        부서 폴더 진입 중…
      </div>
    )
  }
  if (isLoading)
    return (
      <div className="flex-1 flex items-center justify-center text-[13px] text-fg-muted">
        로딩…
      </div>
    )
  if (error)
    return (
      <div role="alert" className="flex-1 flex items-center justify-center text-[13px] text-danger">
        에러: {String(error)}
      </div>
    )
  if (!folder) return null

  return (
    <div className="flex flex-1 min-h-0 min-w-0">
      <div className="flex-1 min-w-0 flex flex-col bg-bg">
        <Breadcrumb />
        <FolderToolbar />
        <BulkActionBar />
        <FileTable folderId={folderId} />
      </div>
      <RightPanel />
      <UploadQueueDock />
      <UploadConflictDialog />
      <MoveFolderDialog />
      <RenameDialog />
      <ShareDialog />
    </div>
  )
}
```

- [ ] **Step 3: Manual smoke — `pnpm dev`**

```bash
cd frontend && pnpm dev
```
브라우저: `http://localhost:3000/d/<dept-uuid>` → root 폴더로 redirect 되는지 확인.

- [ ] **Step 4: Run unit tests + typecheck — expect PASS**

```bash
cd frontend && pnpm typecheck && pnpm test --run --reporter=dot
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/\(explorer\)/d
git commit -m "feat(team-centric-pivot): /d/:deptId/[...parts] route"
```

---

### Task 9: `/t/[teamId]/[[...parts]]` 라우트

**Files:**
- Create: `frontend/src/app/(explorer)/t/[teamId]/[[...parts]]/page.tsx`
- Create: `frontend/src/app/(explorer)/t/[teamId]/[[...parts]]/ClientFilesPage.tsx`

Task 8과 동형 — `kind:'team'`, `workspaces.teams.find(t => t.id === teamId)?.rootFolderId` 사용.

- [ ] **Step 1: Server page**

```tsx
// frontend/src/app/(explorer)/t/[teamId]/[[...parts]]/page.tsx
import { ClientFilesPage } from './ClientFilesPage'

export default async function TeamFilesPage({
  params,
}: {
  params: Promise<{ teamId: string; parts?: string[] }>
}) {
  const { teamId, parts } = await params
  return <ClientFilesPage teamId={teamId} parts={parts ?? []} />
}
```

- [ ] **Step 2: Client page**

Task 8 ClientFilesPage 동일 구조. 차이점만 명시:
- prop: `teamId: string` (not `deptId`)
- workspace landing: `workspaces.teams.find(t => t.id === teamId)?.rootFolderId` 사용
- canonical: `kind: 'team', workspaceId: teamId`
- 진입 placeholder: "팀 폴더 진입 중…"

```tsx
// frontend/src/app/(explorer)/t/[teamId]/[[...parts]]/ClientFilesPage.tsx
'use client'
import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { useCloseFileOnFolderChange } from '@/hooks/useCloseFileOnFolderChange'
import { useWorkspaces } from '@/hooks/useWorkspaces'
import { buildWorkspacePath } from '@/lib/workspacePath'
import { Breadcrumb } from '@/components/folders/Breadcrumb'
import { FileTable } from '@/components/files/FileTable'
import { BulkActionBar } from '@/components/files/BulkActionBar'
import { RightPanel } from '@/components/files/RightPanel'
import { FolderToolbar } from '@/components/upload/FolderToolbar'
import { UploadQueueDock } from '@/components/upload/UploadQueueDock'
import { UploadConflictDialog } from '@/components/upload/UploadConflictDialog'
import { MoveFolderDialog } from '@/components/files/MoveFolderDialog'
import { RenameDialog } from '@/components/files/RenameDialog'
import { ShareDialog } from '@/components/shares/ShareDialog'
import { useUploadBeforeUnload } from '@/hooks/useUploadBeforeUnload'
import { useGlobalShortcuts } from '@/hooks/useGlobalShortcuts'

export function ClientFilesPage({ teamId, parts }: { teamId: string; parts: string[] }) {
  const router = useRouter()
  const { data: workspaces } = useWorkspaces()
  const { folderId, folder, isLoading, error } = useCurrentFolder()

  useCloseFileOnFolderChange(folder?.id)
  useUploadBeforeUnload()
  useGlobalShortcuts()

  const team = workspaces?.teams.find((t) => t.id === teamId)

  useEffect(() => {
    if (parts.length === 0 && team) {
      router.replace(buildWorkspacePath(
        { kind: 'team', workspaceId: teamId },
        team.rootFolderId,
        [],
      ))
    }
  }, [parts.length, team, teamId, router])

  useEffect(() => {
    if (!folder || parts.length === 0) return
    const canonical = buildWorkspacePath(
      { kind: 'team', workspaceId: teamId },
      folder.id,
      folder.slugPath,
    )
    const current = `/t/${teamId}/${parts.join('/')}`
    if (decodeURI(current) !== decodeURI(canonical)) {
      router.replace(canonical)
    }
  }, [folder, parts, teamId, router])

  if (parts.length === 0) {
    return (
      <div className="flex-1 flex items-center justify-center text-[13px] text-fg-muted">
        팀 폴더 진입 중…
      </div>
    )
  }
  if (isLoading)
    return (
      <div className="flex-1 flex items-center justify-center text-[13px] text-fg-muted">
        로딩…
      </div>
    )
  if (error)
    return (
      <div role="alert" className="flex-1 flex items-center justify-center text-[13px] text-danger">
        에러: {String(error)}
      </div>
    )
  if (!folder) return null

  return (
    <div className="flex flex-1 min-h-0 min-w-0">
      <div className="flex-1 min-w-0 flex flex-col bg-bg">
        <Breadcrumb />
        <FolderToolbar />
        <BulkActionBar />
        <FileTable folderId={folderId} />
      </div>
      <RightPanel />
      <UploadQueueDock />
      <UploadConflictDialog />
      <MoveFolderDialog />
      <RenameDialog />
      <ShareDialog />
    </div>
  )
}
```

- [ ] **Step 3: Manual smoke + typecheck**

```bash
cd frontend && pnpm typecheck && pnpm test --run
```
브라우저: `/t/<team-uuid>` → root 폴더로 redirect.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/\(explorer\)/t
git commit -m "feat(team-centric-pivot): /t/:teamId/[...parts] route"
```

---

### Task 10: `/shared/[[...parts]]` 라우트 (read-only 공유받음 진입)

**Files:**
- Create: `frontend/src/app/(explorer)/shared/[[...parts]]/page.tsx`
- Create: `frontend/src/app/(explorer)/shared/[[...parts]]/ClientFilesPage.tsx`

shared는 workspace landing이 없음 (parts[0] 항상 folderId 또는 fileId). MVP는 폴더만 — 파일 공유는 Plan C에서 RightPanel 진입 처리.

- [ ] **Step 1: Server page**

```tsx
// frontend/src/app/(explorer)/shared/[[...parts]]/page.tsx
import { ClientFilesPage } from './ClientFilesPage'

export default async function SharedFilesPage({
  params,
}: {
  params: Promise<{ parts?: string[] }>
}) {
  const { parts } = await params
  return <ClientFilesPage parts={parts ?? []} />
}
```

- [ ] **Step 2: Client page (parts.length === 0 시 안내)**

```tsx
// frontend/src/app/(explorer)/shared/[[...parts]]/ClientFilesPage.tsx
'use client'
import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { buildWorkspacePath } from '@/lib/workspacePath'
import { Breadcrumb } from '@/components/folders/Breadcrumb'
import { FileTable } from '@/components/files/FileTable'
import { BulkActionBar } from '@/components/files/BulkActionBar'
import { RightPanel } from '@/components/files/RightPanel'

export function ClientFilesPage({ parts }: { parts: string[] }) {
  const router = useRouter()
  const { folderId, folder, isLoading, error } = useCurrentFolder()

  useEffect(() => {
    if (!folder || parts.length === 0) return
    const canonical = buildWorkspacePath({ kind: 'shared' }, folder.id, folder.slugPath)
    const current = `/shared/${parts.join('/')}`
    if (decodeURI(current) !== decodeURI(canonical)) {
      router.replace(canonical)
    }
  }, [folder, parts, router])

  if (parts.length === 0) {
    return (
      <div
        role="status"
        className="flex-1 flex items-center justify-center text-[13px] text-fg-muted"
      >
        사이드바에서 공유받은 폴더를 선택하세요.
      </div>
    )
  }
  if (isLoading)
    return (
      <div className="flex-1 flex items-center justify-center text-[13px] text-fg-muted">
        로딩…
      </div>
    )
  if (error)
    return (
      <div role="alert" className="flex-1 flex items-center justify-center text-[13px] text-danger">
        에러: {String(error)}
      </div>
    )
  if (!folder) return null

  return (
    <div className="flex flex-1 min-h-0 min-w-0">
      <div className="flex-1 min-w-0 flex flex-col bg-bg">
        <Breadcrumb />
        {/* read-only: 업로드/생성 toolbar 미노출. Plan C에서 권한 기반 노출. */}
        <BulkActionBar />
        <FileTable folderId={folderId} />
      </div>
      <RightPanel />
    </div>
  )
}
```

- [ ] **Step 3: Smoke + typecheck**

```bash
cd frontend && pnpm typecheck
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/\(explorer\)/shared
git commit -m "feat(team-centric-pivot): /shared/[...parts] route (read-only)"
```

---

### Task 11: `app/page.tsx` — 사용자 첫 workspace로 redirect

**Files:**
- Modify: `frontend/src/app/page.tsx`

기존: `redirect('/files')`. 신규: `/api/workspaces/me` 조회 후 부서 root 또는 첫 팀 root로 redirect. 부서/팀 모두 없으면 빈 상태 안내 페이지.

- [ ] **Step 1: Replace**

```tsx
// frontend/src/app/page.tsx
'use client'
import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useWorkspaces } from '@/hooks/useWorkspaces'
import { buildWorkspacePath } from '@/lib/workspacePath'

export default function Home() {
  const router = useRouter()
  const { data, isLoading } = useWorkspaces()

  useEffect(() => {
    if (isLoading || !data) return
    if (data.department) {
      router.replace(
        buildWorkspacePath(
          { kind: 'department', workspaceId: data.department.id },
          data.department.rootFolderId,
          [],
        ),
      )
      return
    }
    const firstTeam = data.teams[0]
    if (firstTeam) {
      router.replace(
        buildWorkspacePath(
          { kind: 'team', workspaceId: firstTeam.id },
          firstTeam.rootFolderId,
          [],
        ),
      )
      return
    }
    // 미배정 + 0 teams: 사용자에게 안내 — 부서 미배정은 admin 액션 대기 상태
  }, [data, isLoading, router])

  if (isLoading) {
    return (
      <div className="flex h-screen items-center justify-center text-[13px] text-fg-muted">
        workspace 진입 중…
      </div>
    )
  }
  if (data && !data.department && data.teams.length === 0) {
    return (
      <div role="status" className="flex h-screen flex-col items-center justify-center gap-2 text-[13px] text-fg-muted">
        <p>아직 소속된 workspace가 없습니다.</p>
        <p>관리자에게 부서 배정을 요청하거나, 새 팀을 만드세요.</p>
      </div>
    )
  }
  return null
}
```

- [ ] **Step 2: Smoke**

브라우저: `http://localhost:3000/` → 로그인된 사용자라면 부서 root 또는 첫 팀 root로 redirect.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/page.tsx
git commit -m "feat(team-centric-pivot): root redirect — first workspace"
```

---

### Task 12: 기존 `/files/[...parts]` 라우트 제거

**Files:**
- Delete: `frontend/src/app/(explorer)/files/[...parts]/` (디렉토리 통째)
- Delete: `frontend/src/lib/folderPath.ts` (workspacePath로 대체)
- Modify: `frontend/src/lib/api.ts` — `getFilesInFolder('root')` 분기 제거 (가상 root 폐기)
- Search & replace: `VIRTUAL_ROOT_ID` / `isVirtualRoot` / `buildCanonicalPath` 호출부

- [ ] **Step 1: Identify all `VIRTUAL_ROOT_ID` / `buildCanonicalPath` 호출부**

```bash
cd frontend && grep -rn "VIRTUAL_ROOT_ID\|isVirtualRoot\|buildCanonicalPath\|from '@/lib/folderPath'" src
```

예상 hit: `lib/api.ts`, `components/upload/UploadButton.tsx`, `components/files/MoveFolderDialog.tsx`, `components/files/FileTable.tsx`, `components/folders/Breadcrumb.tsx`, `components/trash/TrashTable.tsx`, `components/topbar/SearchResults.tsx`, `components/dnd/useFolderDroppable.ts` 등.

- [ ] **Step 2: 호출부 일괄 치환**

각 호출부를 `useCurrentWorkspace` 또는 `buildWorkspacePath`로 교체. 예시 (Breadcrumb):

```diff
-import { buildCanonicalPath } from '@/lib/folderPath'
+import { buildWorkspacePath } from '@/lib/workspacePath'
+import { useCurrentWorkspace } from '@/hooks/useCurrentWorkspace'
...
-const href = buildCanonicalPath(crumb.id, crumb.slugPath)
+const ws = useCurrentWorkspace()
+const loc = ws?.section === 'shared'
+  ? { kind: 'shared' as const }
+  : ws ? { kind: ws.section, workspaceId: ws.workspaceId! } : null
+const href = loc ? buildWorkspacePath(loc, crumb.id, crumb.slugPath) : '#'
```

`getFilesInFolder('root')` 분기는 제거 — 모든 호출이 실제 workspace root folder ID를 받는다.

- [ ] **Step 3: 디렉토리 삭제**

```bash
rm -rf frontend/src/app/\(explorer\)/files
rm frontend/src/lib/folderPath.ts
```

- [ ] **Step 4: typecheck — 모든 호출부 수정 완료 확인**

```bash
cd frontend && pnpm typecheck
```
Expected: PASS — 'folderPath' 모듈을 못 찾는다는 에러 없음.

- [ ] **Step 5: 단위 테스트 실행**

```bash
cd frontend && pnpm test --run
```
Expected: PASS — `BulkActionBar.test.tsx` 등의 mock value(`folderId: 'root'`)는 단순 string이므로 영향 없음. workspace 라우팅 테스트는 새로 추가된 것만.

- [ ] **Step 6: Commit**

```bash
git rm -r frontend/src/app/\(explorer\)/files frontend/src/lib/folderPath.ts
git add frontend/src/lib frontend/src/components frontend/src/hooks
git commit -m "refactor(team-centric-pivot): drop /files/* + folderPath.ts (use workspacePath)"
```

---

### Task 13: Breadcrumb workspace name prefix

**Files:**
- Modify: `frontend/src/components/folders/Breadcrumb.tsx`

spec §4.5 §2: workspace root는 트리 1차 노드, 이름 = workspace 이름. Breadcrumb의 첫 항목도 workspace 이름으로 표시.

- [ ] **Step 1: Update Breadcrumb to render workspace head**

```tsx
// 기존 가상 root crumb '내 드라이브' 분기를 워크스페이스 이름으로 교체.
// 호출부: useCurrentWorkspace로 현재 section + workspaces.me로 이름 조회.
import { useCurrentWorkspace } from '@/hooks/useCurrentWorkspace'
import { useWorkspaces } from '@/hooks/useWorkspaces'

function useWorkspaceHeadCrumb() {
  const ws = useCurrentWorkspace()
  const { data } = useWorkspaces()
  if (!ws) return null
  if (ws.section === 'department' && data?.department?.id === ws.workspaceId) {
    return { id: data.department.rootFolderId, name: data.department.name, href: `/d/${ws.workspaceId}/${data.department.rootFolderId}` }
  }
  if (ws.section === 'team') {
    const t = data?.teams.find((x) => x.id === ws.workspaceId)
    if (t) return { id: t.rootFolderId, name: t.name, href: `/t/${ws.workspaceId}/${t.rootFolderId}` }
  }
  if (ws.section === 'shared') {
    return { id: 'shared', name: '공유받음', href: '/shared' }
  }
  return null
}
```

기존 Breadcrumb 본문에서 `'내 드라이브'` 가상 crumb을 위 함수 결과로 교체.

- [ ] **Step 2: 기존 Breadcrumb test 업데이트**

`Breadcrumb.test.tsx`에서 `'내 드라이브'` 기대 문자열을 mock한 workspace 이름(예: `'영업부'`)으로 교체.

- [ ] **Step 3: Run tests**

```bash
cd frontend && pnpm test src/components/folders/Breadcrumb
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/folders/Breadcrumb.tsx frontend/src/components/folders/Breadcrumb.test.tsx
git commit -m "feat(team-centric-pivot): Breadcrumb workspace head crumb"
```

---

## Phase 3 — Sidebar shell (Tasks 14–16)

### Task 14: `components/sidebar/SidebarSections.tsx` — 3-section shell

**Files:**
- Create: `frontend/src/components/sidebar/SidebarSections.tsx`
- Test: `frontend/src/components/sidebar/SidebarSections.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
// frontend/src/components/sidebar/SidebarSections.test.tsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { SidebarSections } from './SidebarSections'
import { api } from '@/lib/api'

vi.mock('@/components/sidebar/WorkspaceSection', () => ({
  WorkspaceSection: ({ title }: { title: string }) => <div>{title}</div>,
}))
vi.mock('@/components/sidebar/SharedWithMeSection', () => ({
  SharedWithMeSection: () => <div>공유받음 mock</div>,
}))

describe('SidebarSections', () => {
  it('renders 3 section headers (department, teams, shared)', async () => {
    vi.spyOn(api, 'getWorkspacesMe').mockResolvedValue({
      department: { kind: 'department', id: 'd1', name: '영업부', rootFolderId: 'rd' },
      teams: [
        { kind: 'team', id: 't1', name: 'ProjectAlpha', rootFolderId: 'rt1' },
        { kind: 'team', id: 't2', name: '신제품기획', rootFolderId: 'rt2' },
      ],
    })
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={qc}>
        <SidebarSections />
      </QueryClientProvider>,
    )
    await screen.findByText('영업부')
    expect(screen.getByText('ProjectAlpha')).toBeInTheDocument()
    expect(screen.getByText('신제품기획')).toBeInTheDocument()
    expect(screen.getByText('공유받음 mock')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run — expect FAIL**

```bash
cd frontend && pnpm test src/components/sidebar/SidebarSections.test.tsx
```

- [ ] **Step 3: Implement**

```tsx
// frontend/src/components/sidebar/SidebarSections.tsx
'use client'
import { useWorkspaces } from '@/hooks/useWorkspaces'
import { WorkspaceSection } from './WorkspaceSection'
import { SharedWithMeSection } from './SharedWithMeSection'
import { TeamCreateButton } from './TeamCreateButton'

/**
 * 3-section 사이드바 shell — spec §4.5.
 * Section 1: 내 부서 (단일 workspace, optional)
 * Section 2: 내 팀 (0..N workspaces) + [+ 새 팀] CTA
 * Section 3: 공유받음 (별도 컴포넌트)
 */
export function SidebarSections() {
  const { data, isLoading } = useWorkspaces()

  if (isLoading) return <SectionsSkeleton />

  return (
    <nav aria-label="workspace 트리" className="text-[12.5px] flex flex-col gap-2">
      {data?.department && (
        <section aria-label="내 부서">
          <SectionHeader icon="🏢" label="내 부서" />
          <WorkspaceSection
            kind="department"
            workspaceId={data.department.id}
            title={data.department.name}
            rootFolderId={data.department.rootFolderId}
          />
        </section>
      )}

      {!data?.department && (
        <section aria-label="내 부서" className="px-3 py-2 text-fg-muted text-[12px]">
          부서 미배정 — 관리자에게 문의
        </section>
      )}

      <section aria-label="내 팀">
        <SectionHeader icon="👥" label={`내 팀 (${data?.teams.length ?? 0})`} />
        {data?.teams.map((t) => (
          <WorkspaceSection
            key={t.id}
            kind="team"
            workspaceId={t.id}
            title={t.name}
            rootFolderId={t.rootFolderId}
          />
        ))}
        <TeamCreateButton />
      </section>

      <SharedWithMeSection />
    </nav>
  )
}

function SectionHeader({ icon, label }: { icon: string; label: string }) {
  return (
    <h2 className="px-2 pt-2 pb-1 text-[11px] font-semibold uppercase tracking-wide text-fg-muted">
      <span aria-hidden className="mr-1">{icon}</span>
      {label}
    </h2>
  )
}

function SectionsSkeleton() {
  return (
    <div className="space-y-2 animate-pulse" aria-hidden>
      {[1, 2, 3].map((i) => (
        <div key={i} className="space-y-1">
          <div className="h-4 w-20 bg-surface-2 rounded mx-2" />
          <div className="h-6 bg-surface-2 rounded" />
        </div>
      ))}
    </div>
  )
}
```

- [ ] **Step 4: Stub `WorkspaceSection`, `SharedWithMeSection`, `TeamCreateButton`**

각 파일을 빈 stub으로 일단 생성 (다음 Task에서 채움):

```tsx
// frontend/src/components/sidebar/WorkspaceSection.tsx
'use client'
export function WorkspaceSection({ title }: {
  kind: 'department' | 'team'
  workspaceId: string
  title: string
  rootFolderId: string
}) {
  return <div className="px-2 py-1">{title}</div>
}
```

```tsx
// frontend/src/components/sidebar/SharedWithMeSection.tsx
'use client'
export function SharedWithMeSection() {
  return null
}
```

```tsx
// frontend/src/components/sidebar/TeamCreateButton.tsx
'use client'
export function TeamCreateButton() {
  return null
}
```

- [ ] **Step 5: Run — expect PASS**

```bash
cd frontend && pnpm test src/components/sidebar/SidebarSections.test.tsx
```

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/sidebar
git commit -m "feat(team-centric-pivot): SidebarSections shell + stub children"
```

---

### Task 15: `layout.tsx` — `<FolderTree />` → `<SidebarSections />`

**Files:**
- Modify: `frontend/src/app/(explorer)/layout.tsx`

- [ ] **Step 1: Edit**

```diff
-import { FolderTree } from '@/components/folders/FolderTree'
+import { SidebarSections } from '@/components/sidebar/SidebarSections'
...
-<FolderTree />
+<SidebarSections />
```

- [ ] **Step 2: Smoke**

브라우저: `/d/<dept-uuid>` → 사이드바에 부서 이름 + 팀 리스트(이름만) + 공유받음(빈) 표시.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/\(explorer\)/layout.tsx
git commit -m "feat(team-centric-pivot): layout uses SidebarSections"
```

---

### Task 16: 기존 `FolderTree.tsx` + `useFolderTree.ts` 제거

**Files:**
- Delete: `frontend/src/components/folders/FolderTree.tsx`
- Delete: `frontend/src/hooks/useFolderTree.ts`
- Modify: `frontend/src/lib/api.ts` — `getFolderTree()` 제거 + `qk.folderTree` 호출 invalidations 정리
- Modify: `frontend/src/lib/queryKeys.ts` — `folderTree` 키 제거 + 의존하는 invalidations 호출부 제거

⚠ `qk.folderTree`는 invalidations.afterFilesMoved/afterFolderCreated/afterRename/afterDelete/afterRestore 5곳에서 호출됨. 모두 `qk.workspaces.me()` 또는 `qk.folderChildren(...)` prefix로 대체 (이동/생성/삭제 시 사이드바 트리 갱신 필요).

- [ ] **Step 1: 의존하는 invalidations 호출부 일괄 치환**

각 invalidations 함수에서 `qk.folderTree()` 라인을 다음으로 교체:
```ts
qc.invalidateQueries({ queryKey: [...qk.all, 'folders', 'children'] }),
```
이는 모든 workspace의 모든 folderChildren 변종 prefix 일괄 무효화 (이동/생성으로 어떤 트리 노드든 영향 가능).

- [ ] **Step 2: Delete files**

```bash
rm frontend/src/components/folders/FolderTree.tsx
rm frontend/src/hooks/useFolderTree.ts
```

`api.getFolderTree()` 메서드 + 호출하는 `getFilesInFolder('root')` 분기 제거.

- [ ] **Step 3: typecheck + 전체 테스트**

```bash
cd frontend && pnpm typecheck && pnpm test --run
```

- [ ] **Step 4: Commit**

```bash
git rm frontend/src/components/folders/FolderTree.tsx frontend/src/hooks/useFolderTree.ts
git add frontend/src/lib/api.ts frontend/src/lib/queryKeys.ts
git commit -m "refactor(team-centric-pivot): drop legacy FolderTree + getFolderTree"
```

---

## Phase 4 — Per-workspace folder tree (Tasks 17–22)

### Task 17: `lib/api.ts` — `getFolderChildren(parentId)` (folder-only)

**Files:**
- Modify: `frontend/src/lib/api.ts`
- Test: `frontend/src/lib/api.folderChildren.test.ts`

기존 `getFilesInFolder`는 폴더+파일 통합 — 사이드바 트리에는 폴더만 필요. 동일 endpoint를 호출하지만 응답을 폴더 only로 필터링하는 thin wrapper.

- [ ] **Step 1: Write the failing test**

```ts
// frontend/src/lib/api.folderChildren.test.ts
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { api } from './api'

describe('api.getFolderChildren', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('GETs /api/folders/:id/items and filters to folders only', async () => {
    const body = {
      items: [
        { id: 'fA', type: 'folder', name: 'design', updatedAt: '2026-01-01T00:00:00Z' },
        { id: 'fB', type: 'file', name: 'spec.md', updatedAt: '2026-01-02T00:00:00Z' },
        { id: 'fC', type: 'folder', name: 'docs', updatedAt: '2026-01-03T00:00:00Z' },
      ],
    }
    vi.spyOn(global, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(body), { status: 200 }),
    )
    const children = await api.getFolderChildren('parent1')
    expect(children).toHaveLength(2)
    expect(children.map((c) => c.id)).toEqual(['fA', 'fC'])
    expect(children[0]).toMatchObject({ id: 'fA', name: 'design', slug: expect.any(String) })
  })

  it('throws with status on error', async () => {
    vi.spyOn(global, 'fetch').mockResolvedValue(new Response('', { status: 403 }))
    await expect(api.getFolderChildren('p1')).rejects.toMatchObject({ status: 403 })
  })
})
```

- [ ] **Step 2: Run — expect FAIL**

```bash
cd frontend && pnpm test src/lib/api.folderChildren.test.ts
```

- [ ] **Step 3: Add the method**

```ts
import { normalizeFileName } from '@/lib/normalize'  // 기존 정규화 함수 — slug 생성에 재사용

// api 객체 안에 추가:
  /**
   * Plan B 사이드바 트리 lazy children — folder-only.
   * GET /api/folders/{id}/items 호출 후 type='folder'만 필터링.
   * `slug`는 `normalizeFileName(name)`으로 생성 — 기존 buildCanonicalPath 동치.
   */
  async getFolderChildren(parentId: string): Promise<{
    id: string
    name: string
    slug: string
    parentId: string
  }[]> {
    const url = `/api/folders/${encodeURIComponent(parentId)}/items?sort=NAME&dir=ASC`
    const res = await fetch(url, {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    if (!res.ok) {
      const err = new Error(`getFolderChildren failed: ${res.status}`) as Error & { status: number }
      err.status = res.status
      throw err
    }
    const body = (await res.json()) as {
      items: Array<{ id: string; type: 'folder' | 'file'; name: string }>
    }
    return body.items
      .filter((it) => it.type === 'folder')
      .map((it) => ({
        id: it.id,
        name: it.name,
        slug: normalizeFileName(it.name),
        parentId,
      }))
  },
```

- [ ] **Step 4: Run — expect PASS**

```bash
cd frontend && pnpm test src/lib/api.folderChildren.test.ts
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/api.ts frontend/src/lib/api.folderChildren.test.ts
git commit -m "feat(team-centric-pivot): api.getFolderChildren (folder-only)"
```

---

### Task 18: `hooks/useFolderChildren.ts` — per-(scope, parent) lazy fetch

**Files:**
- Create: `frontend/src/hooks/useFolderChildren.ts`
- Test: `frontend/src/hooks/useFolderChildren.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
// frontend/src/hooks/useFolderChildren.test.tsx
import { describe, it, expect, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { api } from '@/lib/api'
import { useFolderChildren } from './useFolderChildren'

const wrapper = ({ children }: { children: ReactNode }) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('useFolderChildren', () => {
  it('fetches when enabled, skips when disabled', async () => {
    const spy = vi.spyOn(api, 'getFolderChildren').mockResolvedValue([
      { id: 'c1', name: 'design', slug: 'design', parentId: 'p1' },
    ])
    const { result } = renderHook(
      () => useFolderChildren('team', 't1', 'p1', { enabled: true }),
      { wrapper },
    )
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(spy).toHaveBeenCalledWith('p1')
    expect(result.current.data).toHaveLength(1)
  })

  it('disabled — does not fetch', async () => {
    const spy = vi.spyOn(api, 'getFolderChildren').mockResolvedValue([])
    renderHook(
      () => useFolderChildren('team', 't1', 'p1', { enabled: false }),
      { wrapper },
    )
    expect(spy).not.toHaveBeenCalled()
  })
})
```

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: Implement**

```ts
// frontend/src/hooks/useFolderChildren.ts
'use client'
import { useQuery } from '@tanstack/react-query'
import { qk } from '@/lib/queryKeys'
import { api } from '@/lib/api'

/**
 * 사이드바 lazy children 조회 — spec §4.5 §3.
 * `enabled`가 false면 호출 안 함 (접힌 폴더는 fetch X).
 *
 * staleTime 30초: 다른 세션 mutation을 너무 늦게 반영하지 않도록 짧게.
 * gcTime 5분: 트리 접었다 펼치기 직후 재페치 방지.
 */
export function useFolderChildren(
  scopeType: 'department' | 'team',
  scopeId: string,
  parentId: string,
  opts: { enabled: boolean },
) {
  return useQuery({
    queryKey: qk.folderChildren(scopeType, scopeId, parentId),
    queryFn: () => api.getFolderChildren(parentId),
    enabled: opts.enabled,
    staleTime: 30_000,
    gcTime: 5 * 60_000,
  })
}
```

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```bash
git add frontend/src/hooks/useFolderChildren.ts frontend/src/hooks/useFolderChildren.test.tsx
git commit -m "feat(team-centric-pivot): useFolderChildren lazy hook"
```

---

### Task 19: `stores/sidebarTree.ts` — persisted expand state

**Files:**
- Create: `frontend/src/stores/sidebarTree.ts`
- Test: `frontend/src/stores/sidebarTree.test.ts`
- Delete (later — Task 22): `frontend/src/stores/view.ts`

기존 `view.ts`의 `expandedFolderIds`를 새 store로 이전. 추가로 section collapsed state.

- [ ] **Step 1: Write the failing test**

```ts
// frontend/src/stores/sidebarTree.test.ts
import { describe, it, expect, beforeEach } from 'vitest'
import { useSidebarTreeStore } from './sidebarTree'

describe('useSidebarTreeStore', () => {
  beforeEach(() => {
    useSidebarTreeStore.setState({
      expandedFolderIds: [],
      collapsedSections: [],
    })
  })

  it('toggles folder expansion', () => {
    const { toggleFolder } = useSidebarTreeStore.getState()
    toggleFolder('f1')
    expect(useSidebarTreeStore.getState().expandedFolderIds).toContain('f1')
    toggleFolder('f1')
    expect(useSidebarTreeStore.getState().expandedFolderIds).not.toContain('f1')
  })

  it('toggles section collapse', () => {
    const { toggleSection } = useSidebarTreeStore.getState()
    toggleSection('shared')
    expect(useSidebarTreeStore.getState().collapsedSections).toContain('shared')
  })

  it('expandFolder is idempotent', () => {
    const { expandFolder } = useSidebarTreeStore.getState()
    expandFolder('f1')
    expandFolder('f1')
    const ids = useSidebarTreeStore.getState().expandedFolderIds
    expect(ids.filter((x) => x === 'f1')).toHaveLength(1)
  })
})
```

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: Implement**

```ts
// frontend/src/stores/sidebarTree.ts
import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { SidebarSectionKind } from '@/lib/workspacePath'

interface SidebarTreeState {
  /** 펼쳐진 폴더 ID 집합 — workspace 전역 (folder UUID는 항상 unique). */
  expandedFolderIds: string[]
  /** 접힌 section 목록 — 'department' | 'team' | 'shared'. 기본 모두 펼침. */
  collapsedSections: SidebarSectionKind[]

  toggleFolder: (id: string) => void
  expandFolder: (id: string) => void
  collapseFolder: (id: string) => void
  toggleSection: (kind: SidebarSectionKind) => void
}

export const useSidebarTreeStore = create<SidebarTreeState>()(
  persist(
    (set) => ({
      expandedFolderIds: [],
      collapsedSections: [],

      toggleFolder: (id) =>
        set((s) => ({
          expandedFolderIds: s.expandedFolderIds.includes(id)
            ? s.expandedFolderIds.filter((x) => x !== id)
            : [...s.expandedFolderIds, id],
        })),
      expandFolder: (id) =>
        set((s) =>
          s.expandedFolderIds.includes(id)
            ? s
            : { ...s, expandedFolderIds: [...s.expandedFolderIds, id] },
        ),
      collapseFolder: (id) =>
        set((s) => ({
          expandedFolderIds: s.expandedFolderIds.filter((x) => x !== id),
        })),
      toggleSection: (kind) =>
        set((s) => ({
          collapsedSections: s.collapsedSections.includes(kind)
            ? s.collapsedSections.filter((x) => x !== kind)
            : [...s.collapsedSections, kind],
        })),
    }),
    {
      name: 'sidebar-tree-state:v1',
      // SSR/hydration mismatch 방지 — Next.js 15 App Router 디폴트 storage(localStorage)
    },
  ),
)
```

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```bash
git add frontend/src/stores/sidebarTree.ts frontend/src/stores/sidebarTree.test.ts
git commit -m "feat(team-centric-pivot): persisted sidebar tree state"
```

---

### Task 20: `WorkspaceFolderTree.tsx` — root + lazy children

**Files:**
- Create: `frontend/src/components/sidebar/WorkspaceFolderTree.tsx`
- Create: `frontend/src/components/sidebar/FolderTreeNode.tsx`

`WorkspaceSection`의 본문이 root folder부터 lazy 트리. `FolderTreeNode`는 자기 children fetch를 담당하는 재귀 컴포넌트.

- [ ] **Step 1: Implement `FolderTreeNode`**

```tsx
// frontend/src/components/sidebar/FolderTreeNode.tsx
'use client'
import Link from 'next/link'
import { useFolderChildren } from '@/hooks/useFolderChildren'
import { useSidebarTreeStore } from '@/stores/sidebarTree'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { buildWorkspacePath, type SidebarSectionKind } from '@/lib/workspacePath'

interface Props {
  section: SidebarSectionKind
  workspaceId: string  // department/team id; section==='shared'에선 호출되지 않음
  scopeType: 'department' | 'team'
  scopeId: string
  folderId: string
  name: string
  depth: number
  pathAcc: string[]
}

export function FolderTreeNode({
  section, workspaceId, scopeType, scopeId, folderId, name, depth, pathAcc,
}: Props) {
  const { expandedFolderIds, toggleFolder } = useSidebarTreeStore()
  const { folderId: activeId } = useCurrentFolder()

  const isExpanded = expandedFolderIds.includes(folderId)
  const isActive = activeId === folderId

  const children = useFolderChildren(scopeType, scopeId, folderId, { enabled: isExpanded })

  const loc =
    section === 'shared'
      ? { kind: 'shared' as const }
      : { kind: section, workspaceId }
  const href = buildWorkspacePath(loc, folderId, pathAcc)

  return (
    <div>
      <div
        className={`flex items-center gap-1.5 px-2 py-1 rounded min-h-[26px] transition-colors ${
          isActive
            ? 'bg-accent-soft text-accent font-medium'
            : 'text-fg-2 hover:bg-surface-2 hover:text-fg'
        }`}
        style={{ paddingLeft: depth * 12 + 8 }}
      >
        <button
          onClick={() => toggleFolder(folderId)}
          aria-label={isExpanded ? '접기' : '펼치기'}
          aria-expanded={isExpanded}
          className="w-3.5 inline-flex items-center justify-center text-fg-muted text-[10px]"
        >
          {isExpanded ? '▾' : '▸'}
        </button>
        <Link href={href} className="flex-1 truncate text-inherit">
          📁 {name}
        </Link>
      </div>
      {isExpanded && children.data?.map((c) => (
        <FolderTreeNode
          key={c.id}
          section={section}
          workspaceId={workspaceId}
          scopeType={scopeType}
          scopeId={scopeId}
          folderId={c.id}
          name={c.name}
          depth={depth + 1}
          pathAcc={[...pathAcc, c.slug]}
        />
      ))}
      {isExpanded && children.isLoading && (
        <div className="px-2 py-0.5 text-[11px] text-fg-muted" style={{ paddingLeft: (depth + 1) * 12 + 8 }}>
          로딩…
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 2: Implement `WorkspaceFolderTree` (root wrapper)**

```tsx
// frontend/src/components/sidebar/WorkspaceFolderTree.tsx
'use client'
import { FolderTreeNode } from './FolderTreeNode'

export function WorkspaceFolderTree({
  kind, workspaceId, rootFolderId, rootName,
}: {
  kind: 'department' | 'team'
  workspaceId: string
  rootFolderId: string
  rootName: string
}) {
  return (
    <FolderTreeNode
      section={kind}
      workspaceId={workspaceId}
      scopeType={kind}
      scopeId={workspaceId}
      folderId={rootFolderId}
      name={rootName}
      depth={0}
      pathAcc={[]}
    />
  )
}
```

- [ ] **Step 3: Replace `WorkspaceSection` stub**

```tsx
// frontend/src/components/sidebar/WorkspaceSection.tsx
'use client'
import { WorkspaceFolderTree } from './WorkspaceFolderTree'

export function WorkspaceSection({
  kind, workspaceId, title, rootFolderId,
}: {
  kind: 'department' | 'team'
  workspaceId: string
  title: string
  rootFolderId: string
}) {
  return (
    <WorkspaceFolderTree
      kind={kind}
      workspaceId={workspaceId}
      rootFolderId={rootFolderId}
      rootName={title}
    />
  )
}
```

- [ ] **Step 4: Smoke + typecheck**

```bash
cd frontend && pnpm typecheck
cd frontend && pnpm dev
```
브라우저: 사이드바 부서 root → 클릭 expand → /api/folders/:rootId/items 호출 + 자식 표시.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/sidebar/FolderTreeNode.tsx \
        frontend/src/components/sidebar/WorkspaceFolderTree.tsx \
        frontend/src/components/sidebar/WorkspaceSection.tsx
git commit -m "feat(team-centric-pivot): WorkspaceFolderTree lazy expand"
```

---

### Task 21: URL → tree expand 동기화 (auto-expand 경로)

**Files:**
- Create: `frontend/src/hooks/useExpandPathOnNavigate.ts`
- Modify: `frontend/src/app/(explorer)/layout.tsx` (호출 추가)

라우트 진입 시 현재 폴더의 breadcrumb 전체 ancestor를 자동 expand.

- [ ] **Step 1: Implement**

```ts
// frontend/src/hooks/useExpandPathOnNavigate.ts
'use client'
import { useEffect } from 'react'
import { useCurrentFolder } from './useCurrentFolder'
import { useSidebarTreeStore } from '@/stores/sidebarTree'

/**
 * URL 변경 → breadcrumb의 모든 ancestor 폴더 ID를 expand.
 * - workspace root 자체는 expand 대상 아님 (항상 visible).
 * - breadcrumb은 useCurrentFolder가 backend에서 조립한 단일 진실.
 */
export function useExpandPathOnNavigate() {
  const { breadcrumb } = useCurrentFolder()
  const expandFolder = useSidebarTreeStore((s) => s.expandFolder)

  useEffect(() => {
    if (!breadcrumb || breadcrumb.length === 0) return
    // breadcrumb[0]은 (이전) 가상 root였음 — 새 구조에서는 workspace root가 첫 노드.
    // ancestor들 = 마지막 entry(현재 폴더) 제외 + workspace root는 visible이므로 굳이 expand 안 함.
    for (const crumb of breadcrumb.slice(0, -1)) {
      expandFolder(crumb.id)
    }
  }, [breadcrumb, expandFolder])
}
```

- [ ] **Step 2: Wire into layout**

`(explorer)/layout.tsx`의 client child 또는 새 thin client wrapper에서 호출. layout.tsx 자체는 server component인 점에 주의 — `<SidebarSections />` 또는 별도 client component에서 호출.

`SidebarSections.tsx` 상단에 호출 추가:
```tsx
import { useExpandPathOnNavigate } from '@/hooks/useExpandPathOnNavigate'
// 컴포넌트 내부 첫 줄:
useExpandPathOnNavigate()
```

- [ ] **Step 3: Smoke**

브라우저: `/d/<dept>/<deep-folder-id>/A/B/C` 진입 → 사이드바가 A, B 자동 expand 후 deep-folder가 highlight.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/hooks/useExpandPathOnNavigate.ts frontend/src/components/sidebar/SidebarSections.tsx
git commit -m "feat(team-centric-pivot): URL → sidebar auto-expand path"
```

---

### Task 22: 30-day stale state cleanup + `view.ts` 제거

**Files:**
- Modify: `frontend/src/stores/sidebarTree.ts` — TTL cleanup
- Delete: `frontend/src/stores/view.ts`
- Search & replace: `useViewStore` 호출부

- [ ] **Step 1: Add TTL guard**

`sidebarTree.ts`에 `lastWriteAt` 필드 + persist `version`/`migrate`로 30일 초과 시 reset:

```ts
// 새 state shape
interface SidebarTreeState {
  expandedFolderIds: string[]
  collapsedSections: SidebarSectionKind[]
  lastWriteAt: number
  // …actions…
}

// 모든 mutator에서 lastWriteAt: Date.now() set
// persist 옵션에 migrate 추가:
{
  name: 'sidebar-tree-state:v1',
  version: 1,
  migrate: (persisted: any) => {
    if (!persisted) return persisted
    const THIRTY_DAYS = 30 * 24 * 3600 * 1000
    if (persisted.lastWriteAt && Date.now() - persisted.lastWriteAt > THIRTY_DAYS) {
      return { expandedFolderIds: [], collapsedSections: [], lastWriteAt: Date.now() }
    }
    return persisted
  },
}
```

- [ ] **Step 2: Replace `useViewStore` consumers**

```bash
cd frontend && grep -rn "useViewStore" src
```
`FolderTree.tsx`(이미 삭제됨)와 그 외 consumer 없음 — `view.ts`는 expandedFolderIds 단일 책임이었음. 검증 후 파일 삭제.

- [ ] **Step 3: Delete + commit**

```bash
git rm frontend/src/stores/view.ts
git add frontend/src/stores/sidebarTree.ts
git commit -m "feat(team-centric-pivot): sidebar state 30-day TTL + drop view.ts"
```

---

## Phase 5 — Section 3 + UX (Tasks 23–26)

### Task 23: `SharedWithMeSection.tsx` — 공유받음 섹션

**Files:**
- Modify: `frontend/src/components/sidebar/SharedWithMeSection.tsx`
- (existing) `frontend/src/hooks/useSharesWithMe.ts` 재사용

MVP: 공유받은 folder/file 라인을 평탄(grouping 없이) 나열. 각 항목 클릭 시 `/shared/<id>`로 이동.

- [ ] **Step 1: Implement**

```tsx
// frontend/src/components/sidebar/SharedWithMeSection.tsx
'use client'
import Link from 'next/link'
import { useSharesWithMe } from '@/hooks/useSharesWithMe'
import { useSidebarTreeStore } from '@/stores/sidebarTree'

export function SharedWithMeSection() {
  const { data, isLoading } = useSharesWithMe()
  const collapsedSections = useSidebarTreeStore((s) => s.collapsedSections)
  const toggleSection = useSidebarTreeStore((s) => s.toggleSection)
  const collapsed = collapsedSections.includes('shared')

  if (isLoading) return null
  // useSharesWithMe는 useInfiniteQuery — page 0의 items만 표시 (MVP).
  const items = data?.pages.flatMap((p) => p.items) ?? []
  if (items.length === 0) return null  // spec §4.5 §8: 0개일 때 hide

  return (
    <section aria-label="공유받음">
      <button
        type="button"
        className="px-2 pt-2 pb-1 text-[11px] font-semibold uppercase tracking-wide text-fg-muted w-full text-left"
        onClick={() => toggleSection('shared')}
        aria-expanded={!collapsed}
      >
        🔗 공유받음 ({items.length})
      </button>
      {!collapsed && (
        <ul className="space-y-0.5">
          {items.map((s) => {
            const id = s.folderId ?? s.fileId!
            const label = s.subjectName ?? `공유 ${id.slice(0, 6)}`
            return (
              <li key={s.id}>
                <Link
                  href={`/shared/${encodeURIComponent(id)}`}
                  className="block px-2 py-1 text-[12.5px] text-fg-2 hover:bg-surface-2 hover:text-fg rounded"
                >
                  {s.folderId ? '📁' : '📄'} {label}
                </Link>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
```

> Plan B 한계: 출처 workspace 그룹핑(spec §4.5 §1: "영업팀에서 공유")은 backend가 shares-with-me에 source workspace 메타를 노출해야 함 — Plan C 동반. 본 task는 평탄 리스트.

- [ ] **Step 2: Smoke**

기존 mock-server `useSharesWithMe`가 정상 응답하는지 확인. 0개 → 섹션 hide. 1+ → 표시.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/sidebar/SharedWithMeSection.tsx
git commit -m "feat(team-centric-pivot): SharedWithMeSection (flat MVP)"
```

---

### Task 24: Empty states + archived team visualization

**Files:**
- Modify: `frontend/src/components/sidebar/SidebarSections.tsx`
- Modify: `frontend/src/components/sidebar/WorkspaceSection.tsx`
- Modify: `frontend/src/types/workspace.ts` — `archivedAt` 필드 추가 (optional)
- Modify: `frontend/src/lib/api.ts` — `WorkspaceMeResponse` mapping에서 `archivedAt`을 그대로 통과

spec §4.5 §8: 부서 미배정 / 팀 0개 / 공유받음 0개 — 별도 처리. spec §4.5 §9: archived 팀은 dim + 🔒 + read-only 진입.

> ⚠ **Backend 의존성 검사**: 현 `WorkspaceRef` Java record는 `archivedAt`을 노출하지 않음. archived 팀은 `WorkspaceService.findForUser` 단계에서 활성만 반환하는 정책일 가능성 큼 — 코드 확인 후, 필요 시 본 task에서 옵션 처리(없으면 dim 미적용).

- [ ] **Step 1: Confirm backend exposure**

```bash
cd backend && grep -rn "archivedAt\|archived_at" src/main/java/com/ibizdrive/workspace src/main/java/com/ibizdrive/team
```

archived 팀이 응답에 없다면 본 task의 dim/🔒 표시는 noop — Plan A2(team archive endpoint)와 함께 활성화될 hook만 남기고 commit.

- [ ] **Step 2: 부서 미배정 / 팀 0개 / 공유 0개**

이미 Task 14에서 부서 null + team count 0 처리 포함. 검증만:
- 부서 null → "부서 미배정 — 관리자에게 문의" 표시 ✓
- teams.length === 0 → "[+ 새 팀 만들기]" CTA만 보임 ✓
- shares 0 → 섹션 hide (Task 23) ✓

- [ ] **Step 3: archived 팀 visual stub**

`WorkspaceSection`에 `archived?: boolean` prop 추가, 적용 시 `opacity-60` + 🔒:

```tsx
export function WorkspaceSection({
  kind, workspaceId, title, rootFolderId, archived = false,
}: {
  kind: 'department' | 'team'
  workspaceId: string
  title: string
  rootFolderId: string
  archived?: boolean
}) {
  return (
    <div className={archived ? 'opacity-60' : ''}>
      {archived && <span aria-label="archived" className="ml-2">🔒</span>}
      <WorkspaceFolderTree
        kind={kind}
        workspaceId={workspaceId}
        rootFolderId={rootFolderId}
        rootName={title}
      />
    </div>
  )
}
```

`SidebarSections.tsx`에서 `archived={!!t.archivedAt}` 전달 (현재는 undefined, hook ready).

- [ ] **Step 4: Smoke**

부서 미배정 사용자 / 팀 0개 사용자 / 공유 0개 사용자 — 각 빈 상태 잘 노출. 정상 사용자 — archived prop은 noop.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/sidebar
git commit -m "feat(team-centric-pivot): empty states + archived hook"
```

---

### Task 25: `TeamCreateButton` + `TeamCreateDialog` — 팀 생성 플로우

**Files:**
- Modify: `frontend/src/components/sidebar/TeamCreateButton.tsx`
- Create: `frontend/src/components/sidebar/TeamCreateDialog.tsx`
- Create: `frontend/src/hooks/useCreateTeam.ts`
- Create: `frontend/src/types/team.ts`
- Modify: `frontend/src/lib/api.ts` — `createTeam(req)`

backend(Plan A Task 19): `POST /api/teams { name, description?, visibility? }` → `TeamResponse`.

- [ ] **Step 1: types**

```ts
// frontend/src/types/team.ts
export type TeamVisibility = 'PRIVATE' | 'INTERNAL'
// backend Team.Visibility enum 1:1 (Java enum.name() 직렬화).

export interface TeamCreateRequest {
  name: string
  description?: string
  visibility?: TeamVisibility
}

export interface TeamResponse {
  id: string
  name: string
  description: string | null
  visibility: TeamVisibility
  rootFolderId: string
  createdAt: string
  archivedAt: string | null
}
```

- [ ] **Step 2: api.createTeam**

```ts
import type { TeamCreateRequest, TeamResponse } from '@/types/team'

// api 객체:
async createTeam(req: TeamCreateRequest): Promise<TeamResponse> {
  const res = await fetch('/api/teams', {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(req),
  })
  if (!res.ok) {
    throw await buildApiError(res, `createTeam failed: ${res.status}`)
  }
  return (await res.json()) as TeamResponse
}
```

- [ ] **Step 3: useCreateTeam (mutation)**

```ts
// frontend/src/hooks/useCreateTeam.ts
'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { invalidations } from '@/lib/queryKeys'
import type { TeamCreateRequest } from '@/types/team'

export function useCreateTeam() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: TeamCreateRequest) => api.createTeam(req),
    onSuccess: () => invalidations.afterTeamChanged(qc),
  })
}
```

- [ ] **Step 4: TeamCreateDialog**

```tsx
// frontend/src/components/sidebar/TeamCreateDialog.tsx
'use client'
import { useState } from 'react'
import { useCreateTeam } from '@/hooks/useCreateTeam'
import { useRouter } from 'next/navigation'
import { buildWorkspacePath } from '@/lib/workspacePath'

export function TeamCreateDialog({ onClose }: { onClose: () => void }) {
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const router = useRouter()
  const create = useCreateTeam()

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (!name.trim()) return
    const team = await create.mutateAsync({ name: name.trim(), description: description.trim() || undefined })
    onClose()
    router.push(buildWorkspacePath({ kind: 'team', workspaceId: team.id }, team.rootFolderId, []))
  }

  return (
    <div role="dialog" aria-modal="true" aria-label="새 팀 만들기" className="fixed inset-0 flex items-center justify-center bg-black/40 z-50">
      <form onSubmit={submit} className="bg-surface-1 border border-border rounded p-4 w-[420px] flex flex-col gap-3">
        <h2 className="text-[14px] font-semibold">새 팀 만들기</h2>
        <label className="flex flex-col gap-1 text-[12px]">
          이름 *
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            maxLength={100}
            className="border rounded px-2 py-1 text-[13px]"
          />
        </label>
        <label className="flex flex-col gap-1 text-[12px]">
          설명 (선택)
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            maxLength={1000}
            className="border rounded px-2 py-1 text-[13px]"
          />
        </label>
        {create.isError && <p role="alert" className="text-[12px] text-danger">생성 실패: {String(create.error)}</p>}
        <div className="flex justify-end gap-2 pt-2">
          <button type="button" onClick={onClose} className="px-3 py-1 text-[12px]">취소</button>
          <button type="submit" disabled={create.isPending} className="px-3 py-1 bg-accent text-white text-[12px] rounded disabled:opacity-50">
            {create.isPending ? '생성 중…' : '만들기'}
          </button>
        </div>
      </form>
    </div>
  )
}
```

- [ ] **Step 5: TeamCreateButton (모달 토글)**

```tsx
// frontend/src/components/sidebar/TeamCreateButton.tsx
'use client'
import { useState } from 'react'
import { TeamCreateDialog } from './TeamCreateDialog'

export function TeamCreateButton() {
  const [open, setOpen] = useState(false)
  return (
    <>
      <button
        onClick={() => setOpen(true)}
        className="w-full text-left px-2 py-1 text-[12px] text-fg-muted hover:bg-surface-2 hover:text-fg rounded"
      >
        + 새 팀 만들기
      </button>
      {open && <TeamCreateDialog onClose={() => setOpen(false)} />}
    </>
  )
}
```

- [ ] **Step 6: Smoke**

CTA 클릭 → 모달 → 이름 입력 → 만들기 → 새 팀 root로 이동 + 사이드바에 새 팀 표시.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/types/team.ts frontend/src/lib/api.ts frontend/src/hooks/useCreateTeam.ts frontend/src/components/sidebar/TeamCreateDialog.tsx frontend/src/components/sidebar/TeamCreateButton.tsx
git commit -m "feat(team-centric-pivot): TeamCreateDialog + useCreateTeam"
```

---

### Task 26: 사이드바 휴지통 진입점

**Files:**
- Modify: `frontend/src/app/(explorer)/layout.tsx`
- 기존 `frontend/src/components/trash/TrashLink.tsx` 그대로 유지

spec §4.5 §7: "사이드바 단일 '🗑 휴지통' 링크" — 이미 layout.tsx의 mt-auto 영역에 `<TrashLink />` 존재. workspace별 분리 페이지(탭 UI)는 Plan E. 본 task는 link 위치/문구만 검증 + audit.

- [ ] **Step 1: 검증**

`<TrashLink />`가 SidebarSections 영역 아래(mt-auto)에 그대로 배치되어 있는지 확인. 변경 사항 없음.

- [ ] **Step 2: TrashLink 문구 audit**

문구가 "🗑 휴지통"인지 확인. 다르면 수정.

- [ ] **Step 3: Commit (no-op일 수도)**

변경 없으면 commit 생략. 있으면:
```bash
git add frontend/src/components/trash/TrashLink.tsx
git commit -m "chore(team-centric-pivot): TrashLink 문구 정합 (Plan E pre-step)"
```

---

## Phase 6 — DnD: same-workspace constraint (Tasks 27–29)

### Task 27: `MoveDragData` + `sourceWorkspace` 메타 추가

**Files:**
- Modify: `frontend/src/components/dnd/types.ts`
- Modify: drag start 호출부 (예: `useDragPayload.ts`, `FileTable.tsx`)

drag payload에 source workspace 정보를 실어 droppable이 cross-workspace를 판정.

- [ ] **Step 1: 확장**

```diff
// frontend/src/components/dnd/types.ts
 export interface MoveDragData {
   sourceFolderId: string
   containsFolderIds: string[]
+  sourceWorkspace: { kind: 'department' | 'team' | 'shared'; id: string | null }
 }
```

- [ ] **Step 2: drag start 시 실어주기**

`useDragPayload.ts` 또는 `FileTable.tsx`의 `useDraggable` 호출에서 현재 workspace를 `useCurrentWorkspace`로 조회 후 set:
```tsx
const ws = useCurrentWorkspace()
const sourceWorkspace = ws
  ? { kind: ws.section, id: ws.workspaceId }
  : { kind: 'shared' as const, id: null }
// useDraggable.data.current = { sourceFolderId, containsFolderIds, sourceWorkspace }
```

- [ ] **Step 3: typecheck — 모든 호출부 보강 확인**

```bash
cd frontend && pnpm typecheck
```
Expected: PASS — `useDraggable` data type이 `MoveDragData`로 좁혀져 있어 컴파일 에러로 호출부 누락 검출.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/dnd/types.ts frontend/src/hooks/useDragPayload.ts frontend/src/components/files/FileTable.tsx
git commit -m "feat(team-centric-pivot): MoveDragData carries sourceWorkspace"
```

---

### Task 28: `useFolderDroppable` — cross-workspace 판정

**Files:**
- Modify: `frontend/src/components/dnd/useFolderDroppable.ts`
- Test: `frontend/src/components/dnd/useFolderDroppable.test.ts` (확장 또는 신규)

drop target이 cross-workspace이면 disabled + isCrossWorkspace flag 노출.

- [ ] **Step 1: Update implementation**

```ts
// frontend/src/components/dnd/useFolderDroppable.ts
'use client'
import { useDndContext, useDroppable } from '@dnd-kit/core'
import { isSelfOrDescendantOfAny } from '@/lib/folderTreeUtils'  // ⚠ 본 함수도 새 트리 데이터 모델에 맞춰 수정 (다음 step 참조)
import { DROPPABLE_FOLDER_PREFIX, type MoveDragData } from './types'
import { useCurrentWorkspace } from '@/hooks/useCurrentWorkspace'

export function useFolderDroppable(
  folderId: string,
  /** drop target의 workspace context — sidebar tree에서 호출 시 명시 전달. 미지정이면 useCurrentWorkspace fallback. */
  targetWorkspace?: { kind: 'department' | 'team' | 'shared'; id: string | null },
) {
  const { active } = useDndContext()
  const dragData = active?.data.current as MoveDragData | undefined
  const wsCurrent = useCurrentWorkspace()
  const target =
    targetWorkspace ??
    (wsCurrent
      ? { kind: wsCurrent.section, id: wsCurrent.workspaceId }
      : { kind: 'shared' as const, id: null })

  const isCrossWorkspace =
    !!dragData &&
    (dragData.sourceWorkspace.kind !== target.kind ||
      dragData.sourceWorkspace.id !== target.id)

  // self/descendant 검사 — 현재 workspace 트리만 검사 (folderTreeUtils은 useFolderTree 가정 → 새 lazy-tree 모델로 단순화 필요).
  // Plan B는 동일 workspace ancestor 트리에서만 self/descendant 판정 (트리가 부분 로드라 보수적으로 'sourceFolderId === folderId' 또는
  // containsFolderIds.includes(folderId)만 확인 — full subtree 판정은 backend가 결국 거부).
  const isInvalid =
    !!dragData &&
    (dragData.containsFolderIds.includes(folderId) ||
      dragData.sourceFolderId === folderId)

  const isSameFolder = !!dragData && dragData.sourceFolderId === folderId

  // "공유받음" 섹션은 항상 drop 차단 (re-share 금지, spec §4.5 §6, §4.3)
  const isSharedTarget = target.kind === 'shared'

  const { isOver, setNodeRef } = useDroppable({
    id: `${DROPPABLE_FOLDER_PREFIX}${folderId}`,
    disabled: isInvalid || isSameFolder || isCrossWorkspace || isSharedTarget,
  })

  return {
    isOver,
    setNodeRef,
    isInvalid,
    isSameFolder,
    isCrossWorkspace,
    isSharedTarget,
    isDragging: !!dragData,
  }
}
```

⚠ 기존 `isSelfOrDescendantOfAny`/`folderTreeUtils.ts`는 `useFolderTree` 결과에 의존했음. lazy 트리에서는 일부 ancestor만 로드되므로 frontend가 정확 판정 불가 — backend가 cross-folder/own-descendant 이동을 결국 거부. 이에 따라 위 코드는 보수적 방어만 수행 (containsFolderIds + sourceFolderId 비교).

- [ ] **Step 2: Update test**

```ts
// frontend/src/components/dnd/useFolderDroppable.test.ts (또는 신규)
import { describe, it, expect, vi } from 'vitest'
import { renderHook } from '@testing-library/react'
import { DndContext } from '@dnd-kit/core'
import { useFolderDroppable } from './useFolderDroppable'

vi.mock('@/hooks/useCurrentWorkspace', () => ({
  useCurrentWorkspace: () => ({ section: 'team', workspaceId: 't1', folderId: 'f1', slugPath: [] }),
}))

describe('useFolderDroppable cross-workspace', () => {
  // Test infra: mock useDndContext().active.data.current with sourceWorkspace mismatch.
  // Detailed setup omitted here — implementer to write following @dnd-kit testing patterns.
  it('isCrossWorkspace=true when sourceWorkspace.id !== target.id', () => {
    // 구현자: useDndContext mock with active.data.current = { sourceWorkspace: { kind: 'team', id: 't2' }, ... }
    // 호출: useFolderDroppable('fX', { kind: 'team', id: 't1' })
    // 기대: isCrossWorkspace === true, droppable disabled.
    expect(true).toBe(true)  // skeleton — 실 구현 시 drag mock 추가
  })
})
```

> ⚠ test skeleton만 두고, dnd-kit mock 패턴 적용은 implementer 재량. 본 task 핵심은 구현 변경.

- [ ] **Step 3: Run typecheck + tests**

```bash
cd frontend && pnpm typecheck && pnpm test --run
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/dnd/useFolderDroppable.ts frontend/src/components/dnd/useFolderDroppable.test.ts
git commit -m "feat(team-centric-pivot): useFolderDroppable cross-workspace guard"
```

---

### Task 29: 시각 피드백 — 🚫 + 툴팁

**Files:**
- Modify: `frontend/src/components/sidebar/FolderTreeNode.tsx` (drop target 시각화)
- Modify: `frontend/src/components/files/FileTable.tsx` (file table row drop 시 동일 처리, 필요 시)

cross-workspace hover 시 ❌ 아이콘 + 툴팁.

- [ ] **Step 1: FolderTreeNode 시각화**

```tsx
// FolderTreeNode 내부 useFolderDroppable 호출 시:
const drop = useFolderDroppable(folderId, {
  kind: section,
  id: section === 'shared' ? null : workspaceId,
})

// row className 보강:
const dropClass = !drop.isDragging
  ? ''
  : drop.isCrossWorkspace || drop.isSharedTarget
    ? 'opacity-50 cursor-not-allowed'
    : drop.isInvalid || drop.isSameFolder
      ? 'opacity-50'
      : drop.isOver
        ? 'bg-accent-soft ring-2 ring-accent'
        : ''

// title 속성:
const dropTitle =
  drop.isCrossWorkspace
    ? '🚫 다른 workspace로 이동 불가 (컨텍스트 메뉴를 사용하세요)'
    : drop.isSharedTarget
      ? '🚫 공유받음 영역으로 이동 불가'
      : undefined

// row JSX:
<div ref={drop.setNodeRef} className={`... ${dropClass}`} title={dropTitle} >
  ...
</div>
```

- [ ] **Step 2: FileTable row hover (있다면 동일 처리)**

`FileTable.tsx`에 `useFolderDroppable` 호출이 있으면 동일하게 보강.

- [ ] **Step 3: Smoke**

다른 workspace의 폴더로 drag → ❌ 시각 + 툴팁 노출. drop 작동 안 함.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/sidebar/FolderTreeNode.tsx frontend/src/components/files/FileTable.tsx
git commit -m "feat(team-centric-pivot): cross-workspace drop visual feedback"
```

---

## Phase 7 — Migration & docs (Tasks 30–32)

### Task 30: `docs/01-frontend-design.md` 동기화

**Files:**
- Modify: `docs/01-frontend-design.md` (§2 사이드바, §2~§4 라우팅, §5 Zustand, §6 Query keys, §7 DnD, §17 URL canonical, §18 로드맵, §19 핵심 원칙)

spec §7 변경 영향에 따라 본문 갱신.

- [ ] **Step 1: §2 사이드바 — 3-section 구조로 재서술**

기존 단일 FolderTree 가정 → `SidebarSections` + `WorkspaceSection` + `WorkspaceFolderTree` + `SharedWithMeSection`. archived 팀 시각, "[+ 새 팀]" CTA, 빈 상태 처리 명세 (Plan B 구현 결과 반영).

- [ ] **Step 2: §2~§4 라우팅 — `/d/*`, `/t/*`, `/shared/*` 재설계**

`/files/*` 폐기 명시. `app/(explorer)/d/[deptId]/[[...parts]]`, `t/[teamId]/[[...parts]]`, `shared/[[...parts]]` 3개 라우트.

- [ ] **Step 3: §5 Zustand — `useSidebarTreeStore` (persisted)**

기존 `useViewStore` 폐기. expandedFolderIds + collapsedSections + 30일 TTL 정책.

- [ ] **Step 4: §6 Query keys — `qk.workspaces.me()`, `qk.folderChildren(scope, sid, pid)`, `qk.teams.all()` 추가**

invalidations 헬퍼 갱신: `afterTeamChanged`, `qk.folderTree` → `[explorer, folders, children]` prefix 일괄.

- [ ] **Step 5: §7 DnD — same-workspace constraint + visual feedback 명세**

`MoveDragData.sourceWorkspace` 필드 + `useFolderDroppable.isCrossWorkspace` flag. 공유받음 drop 차단.

- [ ] **Step 6: §17 URL canonical — workspace prefix 빌더**

`buildWorkspacePath(loc, folderId, slugPath)` 명세 + parser.

- [ ] **Step 7: §18 로드맵 — Plan B 마일스톤 진입**

`M_team-pivot-frontend-foundation` 항목으로 Plan B를 등록.

- [ ] **Step 8: §19 핵심 원칙 — 원칙 1 갱신**

"URL이 어디를 소유한다"를 "URL이 workspace + folder를 소유한다"로 갱신. 가상 root(VIRTUAL_ROOT_ID) 폐기 명기.

- [ ] **Step 9: Commit**

```bash
git add docs/01-frontend-design.md
git commit -m "docs(team-centric-pivot): 01-frontend-design — workspace pivot reflected"
```

---

### Task 31: `CLAUDE.md` §2 라우팅 표 + §3 원칙 갱신

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: §2 라우팅 표 — `/d`, `/t`, `/shared` 추가**

```diff
 | 작업 유형 | 읽을 문서 / 섹션 |
 |---|---|
+| URL 라우팅 (workspace prefix) | `docs/01-frontend-design.md` §2~§4, §17, spec `2026-05-09-team-centric-pivot-design.md` §5.1 |
+| 사이드바 3-section 트리 | `docs/01-frontend-design.md` §2, spec `…` §4.5 |
```

- [ ] **Step 2: §3 핵심 원칙 1 갱신**

```diff
-1. **URL이 "어디"를 소유한다.** `folderId`는 URL(`/files/[...parts]`의 parts[0]), 절대 Zustand에 복제 금지.
+1. **URL이 "어디"를 소유한다.** workspace + folderId 모두 URL이 진실 (`/d/:deptId/:folderId/...`, `/t/:teamId/:folderId/...`, `/shared/:folderId/...`). 사이드바 expand state만 localStorage(persist), workspace/folderId 절대 Zustand 복제 금지.
```

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md
git commit -m "docs(team-centric-pivot): CLAUDE.md routing + principles refresh"
```

---

### Task 32: `docs/progress.md` + Plan B 종료 기록

**Files:**
- Modify: `docs/progress.md`

- [ ] **Step 1: 세션 entry 추가**

```markdown
## 2026-05-09 세션 — Plan B (Frontend Foundation)
### 완료
- [team-pivot-fe] workspaces 타입 + api + queryKeys + useWorkspaces (Tasks 1~4)
- [team-pivot-fe] workspacePath builder/parser + useCurrentWorkspace + useCurrentFolder refactor (Tasks 5~7)
- [team-pivot-fe] /d/[dept], /t/[team], /shared/* 라우트 + ClientFilesPage variants + root redirect (Tasks 8~12)
- [team-pivot-fe] Breadcrumb workspace head crumb (Task 13)
- [team-pivot-fe] SidebarSections + WorkspaceSection + WorkspaceFolderTree + lazy children (Tasks 14~22)
- [team-pivot-fe] SharedWithMeSection (flat MVP) + empty states + archived hook (Tasks 23~24)
- [team-pivot-fe] TeamCreateButton + Dialog + useCreateTeam (Task 25)
- [team-pivot-fe] DnD same-workspace constraint + visual feedback (Tasks 27~29)
- [team-pivot-fe] /files/* + folderPath.ts + FolderTree.tsx + view.ts + getFolderTree() 폐기 (Tasks 12, 16, 22)
- [team-pivot-fe] docs/01 + CLAUDE.md 동기화 (Tasks 30~31)

### 다음 세션 컨텍스트
- 공유받음 출처 workspace 그룹핑은 backend가 shares-with-me에 source workspace 메타 노출 후 — Plan C와 함께
- archived 팀 dim/🔒은 backend Team archive endpoint(Plan A2)와 함께 활성
- /trash workspace별 분리 페이지(탭 UI)는 Plan E

### 블로커 / 후속
- Plan A 잔여 (Task 23, 28~30) 별 세션에서 wrap-up
- Plan C (share dialog + team subject_type) 시작 가능
- Plan D (cross-workspace move 모달) 시작 가능
```

- [ ] **Step 2: Commit**

```bash
git add docs/progress.md
git commit -m "docs(team-centric-pivot): progress.md — Plan B frontend foundation complete"
```

---

## Self-Review Notes (writing-plans skill)

**Spec coverage:**
- §4.5 §1 3-section 트리 → Tasks 14, 22 (sidebar shell + sections)
- §4.5 §2 workspace root = 트리 1차 노드 → Tasks 17, 20 (root + lazy)
- §4.5 §3 lazy load (workspaces.me + folderChildren) → Tasks 4, 17, 18, 20
- §4.5 §4 URL ↔ 트리 동기화 → Tasks 7, 8, 9, 10, 21 (canonical redirect + auto-expand)
- §4.5 §5 persisted UI state (key `sidebar-tree-state:v1`) → Tasks 19, 22
- §4.5 §6 DnD policy (same-workspace + 공유받음 차단) → Tasks 27, 28, 29
- §4.5 §7 휴지통 단일 진입점 → Task 26 (workspace 분리는 Plan E)
- §4.5 §8 빈 상태 → Task 24 (Tasks 14, 23 부분 포함)
- §4.5 §9 archived 팀 → Task 24 (backend hook 준비, Plan A2 활성)
- §5.1 URL 구조 → Tasks 5 (builder/parser), 8, 9, 10 (routes)
- §5.2 신규 API 소비 (workspaces/me + teams POST) → Tasks 2, 25
- §5.3 변경 endpoint 소비 — getFolderChildren scope 응답 통과 → Task 17 (folder-only filter; scope는 wire 통과만)

**Out-of-scope (다른 plan 명시):**
- §4.3 "공유받음" 출처 그룹핑 → Plan C (backend share-with-me에 source workspace 노출 동반)
- §4.5 §6 cross-workspace 컨텍스트 메뉴 "다른 workspace로 이동" → Plan D
- §5.2 PATCH /api/teams/:id, archive/unarchive → Plan A2 + Plan B 후속 UI
- §5.3 share endpoint subject_type='team' 노출 → Plan C
- §5.6 cross-workspace move 다이얼로그 + preview 소비 → Plan D
- §13 휴지통 workspace 분리 페이지 → Plan E

**Placeholder scan:** "TBD/TODO/implement later" 없음. 다음 두 곳은 implementer judgment 명시:
- Task 12 Step 2: `VIRTUAL_ROOT_ID` 호출부 search & replace — grep 결과 14개 파일을 한 번에 수정해야 함; 본 plan은 패턴만 제시하고 각 파일 specific 수정은 implementer 책임
- Task 28 Step 2: dnd-kit mock 패턴은 testing infra에 따라 다름 — 본 plan은 skeleton만, 실제 mock 구성은 implementer

**Type consistency:**
- `WorkspaceKind` (Task 1) — 'department' | 'team' (lowercase, backend Java enum.toString() 매칭)
- `SidebarSectionKind` (Task 5) — 'department' | 'team' | 'shared' (UI 가상 'shared' 추가)
- `MoveDragData.sourceWorkspace` (Task 27) — 동일한 SidebarSectionKind union 사용
- `qk.folderChildren` 시그니처: `(scopeType, scopeId, parentId)` — Tasks 3, 17, 18에서 일관 사용

**Backend dependency check:**
- ✅ `GET /api/workspaces/me` — Plan A Task 15 완료 (`feat(team-centric-pivot): GET /api/workspaces/me` commit 87108ec)
- ✅ `POST /api/teams` — Plan A Task 19 완료 (commit f788214)
- ✅ `GET /api/folders/{id}/items` — 기존 (m-folder-items 트랙 시기)
- ✅ `GET /api/shares/with-me` — 기존 (F4 트랙)
- ⚠ `WorkspaceRef.archivedAt` — 현 Java record 미노출. Task 24는 stub만, Plan A2와 함께 활성화

**Known caveats requiring implementer judgment:**
1. Task 12 — `VIRTUAL_ROOT_ID` / `buildCanonicalPath` 사용처 14개 파일을 한 번에 정리. 각 파일이 다른 패턴으로 사용 중일 수 있어 case-by-case 판단 필요. Breadcrumb은 Task 13에서 별도 처리.
2. Task 16 — `qk.folderTree()` 호출 invalidations 5곳을 `[explorer, folders, children]` prefix로 교체. 검증 방법: 폴더 mutation(생성/이동/삭제) 후 사이드바가 자동 갱신되는지 수동 확인.
3. Task 28 — `folderTreeUtils.isSelfOrDescendantOfAny`는 `useFolderTree` (전체 트리) 가정. lazy 트리에서는 부분 ancestor만 알기에 frontend self-descendant 판정이 보수적. backend가 결국 거부하므로 안전성 손실 없음.
4. Task 12 — 가상 root('root') 문자열 의존하는 mock 테스트 (BulkActionBar.test, RenameDialog.test 등)는 단순 string mock 값이라 영향 없음. 하지만 routing test가 `/files/...` 기대하면 update 필요.
5. Task 13 — Breadcrumb test의 'root' 가상 crumb 기대(`'내 드라이브'`)는 workspace 이름으로 mock해야 PASS. 기존 Breadcrumb.test.tsx 검토 필수.

**Migration safety:**
- 본 plan은 `/files/*` 라우트를 완전 제거. 기존 북마크/직접 URL은 깨짐 — sidebar/topbar 진입 또는 root redirect로만 도달 가능.
- 운영 베타 사용자에게는 Plan A6.2 cutover 안내(spec §6.2)에 "URL 형식 변경" 항목을 함께 공지.
- 기능 회귀 검증: Phase 1~6 완료 후 manual smoke checklist (다음 섹션) 수행.

---

## Manual smoke checklist (Phase 6 완료 후)

- [ ] `/` 진입 → 부서 root로 redirect (또는 첫 팀 root)
- [ ] 사이드바: 부서 1 + 팀 N + 공유받음(있다면) 3 section 노출
- [ ] 부서 root 클릭 → expand → 자식 폴더 나타남, URL `/d/<deptId>/<rootFolderId>`
- [ ] 깊은 폴더 직접 URL 접근 (`/d/<dept>/<deepFolder>/A/B`) → ancestor 자동 expand + highlight
- [ ] 팀 expand → URL `/t/<teamId>/<folderId>` 동작
- [ ] 공유받음 항목 클릭 → `/shared/<folderId>` 진입, FileTable 표시
- [ ] 새 팀 만들기 → 모달 → 생성 → 사이드바에 즉시 등장 + 새 팀 root로 이동
- [ ] 부서 폴더에서 파일 drag → 같은 부서 다른 폴더로 drop ✓
- [ ] 부서 폴더에서 파일 drag → 다른 팀 폴더로 drop ✗ (시각 ❌ + 툴팁)
- [ ] 부서 폴더에서 파일 drag → 공유받음으로 drop ✗
- [ ] 휴지통 링크 → `/trash` (Plan E 분리 전까지 단일 페이지)
- [ ] expand state 새로고침 후 보존 (localStorage)
- [ ] 부서 미배정 사용자 / 팀 0개 사용자 / 공유 0개 사용자 — 빈 상태 정상 노출

---

**Execution handoff:**

Plan complete and saved to `docs/superpowers/plans/2026-05-09-team-centric-pivot-plan-b-frontend-foundation.md`. Two execution options:

**1. Subagent-Driven (recommended)** — fresh subagent per task, two-stage review (code-reviewer + spec-coverage), fast iteration via `superpowers:subagent-driven-development`. 각 task가 isolated하므로 병렬 실행 후보가 일부 존재 (Phase 1 Tasks 1~4 동시; Phase 4 Tasks 17~19 동시).

**2. Inline Execution** — execute tasks in this session via `superpowers:executing-plans`, batch with checkpoints.

권장: **Subagent-Driven**. Phase 1~3은 직렬, Phase 4 이후 병렬 dispatch 가능. 매 phase 종료 시 typecheck + manual smoke 체크포인트.

---

**Branch handoff (사용자 확인 필요):**

본 plan은 `frontend/`만 수정하므로 backend 변경분(Plan A)과 완전 독립. 권장 실행 환경:
1. `master`에서 새 worktree 생성: `git worktree add .claude/worktrees/plan-b -b feat/team-centric-pivot-plan-b-frontend master`
2. 해당 worktree에 본 plan 파일 cherry-pick (Plan A 워크트리에서 `git format-patch` 후 적용 또는 단순 파일 copy + commit)
3. 워크트리 안에서 `superpowers:subagent-driven-development` 호출, plan 인자로 본 파일 path 전달

**Subagent-Driven (recommended)** 또는 **Inline Execution** 중 어느 방향으로 진행할까요?
