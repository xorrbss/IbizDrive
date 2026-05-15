# Quick Action Dialog (Upload / New Folder) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dashboard `WelcomeHeader` 의 업로드/새 폴더 quick action 2 버튼을 정식 구현해, 사용자가 root `/` 진입 직후 1-click 으로 default workspace root 폴더에 파일 업로드 또는 폴더 생성을 시작할 수 있게 한다.

**Architecture:** 두 액션을 다른 패턴으로 분리. 업로드는 WelcomeHeader 안에서 `<input type="file">` 즉시 open + `useUpload.enqueue` + `router.push(workspaceRoot)` (user gesture 보존). 새 폴더는 `router.push(workspaceRoot?action=new-folder)` 로 navigation 한 뒤 explorer page 가 `useQuickActionParam` hook 으로 query 감지 + `CreateFolderDialog` mount + `router.replace` 로 1-shot consume. URL convention `?action=new-folder` 한 케이스만 도입(KISS).

**Tech Stack:** Next.js 15 App Router (`useRouter`/`useSearchParams`/`usePathname`), React 18, TypeScript, Vitest + React Testing Library, Zustand (`useUploadStore` via `useUpload` hook), TanStack Query.

**Spec:** `docs/superpowers/specs/2026-05-15-quick-action-dialog-design.md`

---

## File Structure

| 파일 | 책임 | 변경 종류 |
|---|---|---|
| `frontend/src/hooks/useQuickActionParam.ts` | `?action=new-folder` query 감지 + 1-shot consume + dialog open state 반환 | 신규 |
| `frontend/src/hooks/useQuickActionParam.test.ts` | hook 단위 테스트 (5 케이스) | 신규 |
| `frontend/src/components/home/WelcomeHeader.tsx` | "내 워크스페이스 →" link 제거, 업로드/새 폴더 버튼 2개 + hidden file input + 클릭 핸들러 | 수정 |
| `frontend/src/components/home/WelcomeHeader.test.tsx` | 기존 3 케이스 갱신(link 제거 후 버튼 verify) + 신규 케이스 4건 | 수정 |
| `frontend/src/app/(explorer)/d/[deptId]/[[...parts]]/ClientFilesPage.tsx` | `useQuickActionParam` 호출 + `CreateFolderDialog` mount | 수정 |
| `frontend/src/app/(explorer)/t/[teamId]/[[...parts]]/ClientFilesPage.tsx` | 동일(team workspace) | 수정 |

**backend**: 변경 0. **신규 endpoint**: 0. **신규 컴포넌트**: 0 (hook 1 + 기존 컴포넌트 수정만).

---

## Task 1: `useQuickActionParam` hook — failing test

**Files:**
- Create: `frontend/src/hooks/useQuickActionParam.test.ts`

- [ ] **Step 1: Write the failing test file**

Create `frontend/src/hooks/useQuickActionParam.test.ts`:

```typescript
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { renderHook, act } from '@testing-library/react'

const replaceMock = vi.fn()
let mockPath = '/d/dept-1/root-1'
let mockQuery = ''

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: replaceMock, push: vi.fn() }),
  usePathname: () => mockPath,
  useSearchParams: () => new URLSearchParams(mockQuery),
}))

import { useQuickActionParam } from './useQuickActionParam'

describe('useQuickActionParam', () => {
  beforeEach(() => {
    replaceMock.mockReset()
    mockPath = '/d/dept-1/root-1'
    mockQuery = ''
  })

  it('action=new-folder + folderId 있음 → newFolderOpen=true + ?action 제거 replace', () => {
    mockQuery = 'action=new-folder'
    const { result } = renderHook(() => useQuickActionParam('root-1'))
    expect(result.current.newFolderOpen).toBe(true)
    expect(replaceMock).toHaveBeenCalledWith('/d/dept-1/root-1')
  })

  it('action=new-folder + folderId 빈 문자열 → newFolderOpen=false + replace 미호출', () => {
    mockQuery = 'action=new-folder'
    const { result } = renderHook(() => useQuickActionParam(''))
    expect(result.current.newFolderOpen).toBe(false)
    expect(replaceMock).not.toHaveBeenCalled()
  })

  it('action 알 수 없는 값 → newFolderOpen=false + replace 미호출', () => {
    mockQuery = 'action=upload'
    const { result } = renderHook(() => useQuickActionParam('root-1'))
    expect(result.current.newFolderOpen).toBe(false)
    expect(replaceMock).not.toHaveBeenCalled()
  })

  it('action=new-folder + 다른 query (file=xxx) 보존', () => {
    mockQuery = 'action=new-folder&file=file_abc'
    const { result } = renderHook(() => useQuickActionParam('root-1'))
    expect(result.current.newFolderOpen).toBe(true)
    expect(replaceMock).toHaveBeenCalledWith('/d/dept-1/root-1?file=file_abc')
  })

  it('closeNewFolder 호출 → newFolderOpen=false', () => {
    mockQuery = 'action=new-folder'
    const { result } = renderHook(() => useQuickActionParam('root-1'))
    expect(result.current.newFolderOpen).toBe(true)
    act(() => {
      result.current.closeNewFolder()
    })
    expect(result.current.newFolderOpen).toBe(false)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && pnpm test useQuickActionParam --run`
Expected: FAIL with "Cannot find module './useQuickActionParam'"

---

## Task 2: `useQuickActionParam` hook — implementation

**Files:**
- Create: `frontend/src/hooks/useQuickActionParam.ts`

- [ ] **Step 1: Create the hook**

Create `frontend/src/hooks/useQuickActionParam.ts`:

```typescript
'use client'
import { useEffect, useState } from 'react'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'

/**
 * Dashboard quick action `?action=new-folder` query 감지 + 1-shot consume.
 *
 * <p>WelcomeHeader 의 "새 폴더" 버튼 → `router.push(workspaceRoot?action=new-folder)`.
 * Explorer page (`ClientFilesPage`) 가 본 hook 으로 query 감지 → `CreateFolderDialog` mount.
 * mount 시점에 `router.replace` 로 `?action` 만 제거(다른 query 보존) → URL re-trigger 차단.
 *
 * <p>folderId 가 비어있는 동안(workspace landing → root redirect 중) hook 은 무동작.
 * redirect 완료 후 folderId 채워지면 effect 재실행.
 *
 * <p>spec: `docs/superpowers/specs/2026-05-15-quick-action-dialog-design.md` §5.
 */
export function useQuickActionParam(folderId: string) {
  const router = useRouter()
  const pathname = usePathname()
  const params = useSearchParams()
  const action = params.get('action')

  const [newFolderOpen, setNewFolderOpen] = useState(false)

  useEffect(() => {
    if (action === 'new-folder' && folderId.length > 0) {
      setNewFolderOpen(true)
      const next = new URLSearchParams(params)
      next.delete('action')
      const qs = next.toString()
      router.replace(qs ? `${pathname}?${qs}` : pathname)
    }
  }, [action, folderId, pathname, params, router])

  return {
    newFolderOpen,
    closeNewFolder: () => setNewFolderOpen(false),
  }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `cd frontend && pnpm test useQuickActionParam --run`
Expected: PASS (5/5).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/hooks/useQuickActionParam.ts frontend/src/hooks/useQuickActionParam.test.ts
git commit -m "feat(quick-action-dialog): useQuickActionParam hook (?action=new-folder 1-shot consume)"
```

---

## Task 3: WelcomeHeader — failing tests (extend existing)

**Files:**
- Modify: `frontend/src/components/home/WelcomeHeader.test.tsx`

- [ ] **Step 1: Replace existing test file content**

Replace `frontend/src/components/home/WelcomeHeader.test.tsx` fully:

```typescript
/* eslint-disable @typescript-eslint/no-explicit-any -- vi.mocked return value cast (AuthSession/WorkspaceMeResponse 전체 shape 재현 회피) */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { WelcomeHeader } from './WelcomeHeader'

vi.mock('@/hooks/useMe')
vi.mock('@/hooks/useWorkspaces')
vi.mock('@/hooks/useUpload')

const pushMock = vi.fn()
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: pushMock, replace: vi.fn() }),
}))

import { useMe } from '@/hooks/useMe'
import { useWorkspaces } from '@/hooks/useWorkspaces'
import { useUpload } from '@/hooks/useUpload'

const enqueueMock = vi.fn()

describe('WelcomeHeader', () => {
  beforeEach(() => {
    pushMock.mockReset()
    enqueueMock.mockReset()
    vi.mocked(useUpload).mockReturnValue({ enqueue: enqueueMock } as any)
  })

  it('이름 + 부서 + 팀 수 표시 + quick action 버튼 2개 활성', () => {
    vi.mocked(useMe).mockReturnValue({
      data: { user: { id: 'u1', email: 'x@y', name: '이태석', kind: 'human', mustChangePassword: false }, departments: [], roles: [], effectivePermissionsCacheKey: '' },
    } as any)
    vi.mocked(useWorkspaces).mockReturnValue({
      data: {
        department: { id: 'd1', name: '개발', rootFolderId: 'r1' },
        teams: [{ id: 't1', name: '팀A', rootFolderId: 'tr1' }],
      },
    } as any)

    render(<WelcomeHeader />)
    expect(screen.getByText(/안녕하세요, 이태석님/)).toBeTruthy()
    expect(screen.getByText(/개발/)).toBeTruthy()
    expect(screen.getByText(/팀 1개/)).toBeTruthy()
    expect((screen.getByRole('button', { name: '업로드' }) as HTMLButtonElement).disabled).toBe(false)
    expect((screen.getByRole('button', { name: '새 폴더' }) as HTMLButtonElement).disabled).toBe(false)
  })

  it('department 없고 첫 팀 — 버튼 활성 (첫 팀 root 가 destination)', () => {
    vi.mocked(useMe).mockReturnValue({ data: null } as any)
    vi.mocked(useWorkspaces).mockReturnValue({
      data: {
        department: null,
        teams: [
          { id: 'team-1', name: '디자인', rootFolderId: 'team-root-1' },
          { id: 'team-2', name: '제품', rootFolderId: 'team-root-2' },
        ],
      },
    } as any)

    render(<WelcomeHeader />)
    fireEvent.click(screen.getByRole('button', { name: '새 폴더' }))
    expect(pushMock).toHaveBeenCalledWith('/t/team-1/team-root-1?action=new-folder')
  })

  it('workspace 0건 시 안내 + 버튼 disabled', () => {
    vi.mocked(useMe).mockReturnValue({ data: null } as any)
    vi.mocked(useWorkspaces).mockReturnValue({
      data: { department: null, teams: [] },
    } as any)

    render(<WelcomeHeader />)
    expect(screen.getByText(/안녕하세요, 사용자님/)).toBeTruthy()
    expect(screen.getByText(/아직 소속된 workspace 가 없습니다/)).toBeTruthy()
    expect((screen.getByRole('button', { name: '업로드' }) as HTMLButtonElement).disabled).toBe(true)
    expect((screen.getByRole('button', { name: '새 폴더' }) as HTMLButtonElement).disabled).toBe(true)
  })

  it('새 폴더 click → ?action=new-folder query 로 navigate (department 우선)', () => {
    vi.mocked(useMe).mockReturnValue({ data: null } as any)
    vi.mocked(useWorkspaces).mockReturnValue({
      data: {
        department: { id: 'd1', name: '개발', rootFolderId: 'r1' },
        teams: [],
      },
    } as any)

    render(<WelcomeHeader />)
    fireEvent.click(screen.getByRole('button', { name: '새 폴더' }))
    expect(pushMock).toHaveBeenCalledWith('/d/d1/r1?action=new-folder')
  })

  it('업로드 click → hidden file input change → enqueue + workspaceRoot 로 push', () => {
    vi.mocked(useMe).mockReturnValue({ data: null } as any)
    vi.mocked(useWorkspaces).mockReturnValue({
      data: {
        department: { id: 'd1', name: '개발', rootFolderId: 'r1' },
        teams: [],
      },
    } as any)

    const { container } = render(<WelcomeHeader />)
    const input = container.querySelector('input[type="file"]') as HTMLInputElement
    expect(input).toBeTruthy()

    const file = new File(['hello'], 'a.txt', { type: 'text/plain' })
    Object.defineProperty(input, 'files', { value: [file], configurable: true })
    fireEvent.change(input)

    expect(enqueueMock).toHaveBeenCalledTimes(1)
    expect(enqueueMock.mock.calls[0][0]).toEqual([file])
    expect(enqueueMock.mock.calls[0][1]).toBe('r1')
    expect(pushMock).toHaveBeenCalledWith('/d/d1/r1')
  })

  it('업로드 click + 파일 0개 선택 (취소) → enqueue 미호출 + push 미호출', () => {
    vi.mocked(useMe).mockReturnValue({ data: null } as any)
    vi.mocked(useWorkspaces).mockReturnValue({
      data: {
        department: { id: 'd1', name: '개발', rootFolderId: 'r1' },
        teams: [],
      },
    } as any)

    const { container } = render(<WelcomeHeader />)
    const input = container.querySelector('input[type="file"]') as HTMLInputElement
    Object.defineProperty(input, 'files', { value: [], configurable: true })
    fireEvent.change(input)

    expect(enqueueMock).not.toHaveBeenCalled()
    expect(pushMock).not.toHaveBeenCalled()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && pnpm test WelcomeHeader --run`
Expected: FAIL — 버튼이 아직 link 라 `getByRole('button', { name: '업로드' })` 미발견 + useUpload 사용 안함.

---

## Task 4: WelcomeHeader — implementation

**Files:**
- Modify: `frontend/src/components/home/WelcomeHeader.tsx`

- [ ] **Step 1: Replace WelcomeHeader content**

Replace `frontend/src/components/home/WelcomeHeader.tsx` fully:

```typescript
'use client'
import { useRef, ChangeEvent } from 'react'
import { useRouter } from 'next/navigation'
import { Upload, FolderPlus } from 'lucide-react'
import { useMe } from '@/hooks/useMe'
import { useWorkspaces } from '@/hooks/useWorkspaces'
import { useUpload } from '@/hooks/useUpload'
import { buildWorkspacePath } from '@/lib/workspacePath'

/**
 * User Home Dashboard ① — 환영 헤더 + quick action 2 (업로드 / 새 폴더).
 *
 * <p>업로드: hidden &lt;input type=file&gt; 즉시 click → useUpload.enqueue → workspaceRoot 로 navigate.
 * user gesture 보존 (브라우저 popup blocker 회피).
 *
 * <p>새 폴더: workspaceRoot?action=new-folder 로 push → explorer page 의 useQuickActionParam 이
 * CreateFolderDialog 를 mount + URL 1-shot consume.
 *
 * <p>default workspace: 부서 우선 → 없으면 첫 팀. 0 workspace 시 두 버튼 모두 disabled.
 *
 * <p>spec: `docs/superpowers/specs/2026-05-15-quick-action-dialog-design.md`.
 */
export function WelcomeHeader() {
  const { data: session } = useMe()
  const { data: workspaces } = useWorkspaces()
  const router = useRouter()
  const { enqueue } = useUpload()
  const fileInputRef = useRef<HTMLInputElement>(null)

  const name = session?.user?.name ?? '사용자'
  const department = workspaces?.department
  const firstTeam = workspaces?.teams?.[0]
  const teamCount = workspaces?.teams?.length ?? 0
  const hasWorkspace = !!department || teamCount > 0

  const defaultWorkspace = department
    ? { kind: 'department' as const, id: department.id, rootFolderId: department.rootFolderId }
    : firstTeam
    ? { kind: 'team' as const, id: firstTeam.id, rootFolderId: firstTeam.rootFolderId }
    : null

  const workspaceLink = defaultWorkspace
    ? buildWorkspacePath(
        { kind: defaultWorkspace.kind, workspaceId: defaultWorkspace.id },
        defaultWorkspace.rootFolderId,
        [],
      )
    : null

  const handleUploadClick = () => {
    if (!defaultWorkspace) return
    fileInputRef.current?.click()
  }

  const handleFileChange = (e: ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files
    e.target.value = ''
    if (!files || files.length === 0) return
    if (!defaultWorkspace || !workspaceLink) return
    enqueue(Array.from(files), defaultWorkspace.rootFolderId)
    router.push(workspaceLink)
  }

  const handleNewFolderClick = () => {
    if (!workspaceLink) return
    router.push(`${workspaceLink}?action=new-folder`)
  }

  return (
    <div className="flex items-end justify-between gap-4">
      <div>
        <h1 className="text-[20px] font-semibold text-fg mb-1">
          안녕하세요, {name}님
        </h1>
        {hasWorkspace ? (
          <p className="text-[13px] text-fg-2">
            {department?.name ?? '소속 부서 없음'}
            {teamCount > 0 && ` · 팀 ${teamCount}개`}
          </p>
        ) : (
          <p className="text-[13px] text-fg-2">
            아직 소속된 workspace 가 없습니다. 관리자에게 부서 배정을 요청하거나, 팀을 만드세요.
          </p>
        )}
      </div>
      <div className="flex items-center gap-2 shrink-0">
        <button
          type="button"
          aria-label="업로드"
          disabled={!defaultWorkspace}
          onClick={handleUploadClick}
          className="h-8 px-3 inline-flex items-center gap-1.5 rounded bg-accent text-accent-fg text-[12.5px] font-medium hover:opacity-90 disabled:opacity-40 disabled:cursor-not-allowed transition-opacity"
        >
          <Upload size={13} aria-hidden />
          <span>업로드</span>
        </button>
        <button
          type="button"
          aria-label="새 폴더"
          disabled={!defaultWorkspace}
          onClick={handleNewFolderClick}
          className="h-8 px-3 inline-flex items-center gap-1.5 rounded border border-border text-fg-2 text-[12.5px] hover:bg-surface-2 hover:text-fg disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
        >
          <FolderPlus size={13} aria-hidden />
          <span>새 폴더</span>
        </button>
      </div>
      <input
        ref={fileInputRef}
        type="file"
        multiple
        hidden
        aria-hidden
        onChange={handleFileChange}
      />
    </div>
  )
}
```

- [ ] **Step 2: Run WelcomeHeader test to verify it passes**

Run: `cd frontend && pnpm test WelcomeHeader --run`
Expected: PASS (6/6).

- [ ] **Step 3: Run typecheck**

Run: `cd frontend && pnpm typecheck`
Expected: exit 0.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/home/WelcomeHeader.tsx frontend/src/components/home/WelcomeHeader.test.tsx
git commit -m "feat(quick-action-dialog): WelcomeHeader 업로드/새 폴더 quick action 2 버튼"
```

---

## Task 5: ClientFilesPage (department) — wire hook + dialog

**Files:**
- Modify: `frontend/src/app/(explorer)/d/[deptId]/[[...parts]]/ClientFilesPage.tsx`

- [ ] **Step 1: Add `CreateFolderDialog` import + `useQuickActionParam` hook + dialog mount**

Modify `frontend/src/app/(explorer)/d/[deptId]/[[...parts]]/ClientFilesPage.tsx`:

Add import after line 17 (`import { ShareDialog } from '@/components/shares/ShareDialog'`):

```typescript
import { CreateFolderDialog } from '@/components/explorer/CreateFolderDialog'
import { useQuickActionParam } from '@/hooks/useQuickActionParam'
```

Add hook call after `useGlobalShortcuts()` (line 28):

```typescript
  const { newFolderOpen, closeNewFolder } = useQuickActionParam(folderId)
```

Add `CreateFolderDialog` mount in the JSX return block, after `<ShareDialog />` (line 89), before the closing `</div>` of the outer flex container:

```tsx
      <CreateFolderDialog
        parentId={folderId}
        open={newFolderOpen}
        onClose={closeNewFolder}
      />
```

Final structure of return block (line 76 onward):

```tsx
  return (
    <div className="flex flex-1 min-h-0 min-w-0">
      <div className="flex-1 min-w-0 flex flex-col bg-bg">
        <BreadcrumbWithStar />
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
      <CreateFolderDialog
        parentId={folderId}
        open={newFolderOpen}
        onClose={closeNewFolder}
      />
    </div>
  )
```

- [ ] **Step 2: Run typecheck**

Run: `cd frontend && pnpm typecheck`
Expected: exit 0.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/\(explorer\)/d/\[deptId\]/\[\[...parts\]\]/ClientFilesPage.tsx
git commit -m "feat(quick-action-dialog): department ClientFilesPage 에 useQuickActionParam wire"
```

---

## Task 6: ClientFilesPage (team) — wire hook + dialog

**Files:**
- Modify: `frontend/src/app/(explorer)/t/[teamId]/[[...parts]]/ClientFilesPage.tsx`

- [ ] **Step 1: Read the file**

Run: `cat "frontend/src/app/(explorer)/t/[teamId]/[[...parts]]/ClientFilesPage.tsx"` (확인용 — 구조가 department 와 동일한지 검증. 차이는 `useCurrentWorkspace` 기반 workspace kind 와 buildWorkspacePath `kind:'team'` 만일 것).

- [ ] **Step 2: Apply same edits as Task 5**

Mirror Task 5 changes in the team variant:
- Same 2 imports (`CreateFolderDialog`, `useQuickActionParam`).
- Same hook call (`const { newFolderOpen, closeNewFolder } = useQuickActionParam(folderId)`) immediately after `useGlobalShortcuts()`.
- Same `<CreateFolderDialog parentId={folderId} open={newFolderOpen} onClose={closeNewFolder} />` mount at the end of the JSX flex container, after `<ShareDialog />`.

(Variable names — `deptId` vs `teamId` — are unaffected; the hook depends only on `folderId` and pathname.)

- [ ] **Step 3: Run typecheck**

Run: `cd frontend && pnpm typecheck`
Expected: exit 0.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/\(explorer\)/t/\[teamId\]/\[\[...parts\]\]/ClientFilesPage.tsx
git commit -m "feat(quick-action-dialog): team ClientFilesPage 에 useQuickActionParam wire"
```

---

## Task 7: Full suite verification

**Files:** none modified

- [ ] **Step 1: Run full frontend test suite**

Run: `cd frontend && pnpm test --run`
Expected: 모든 케이스 PASS. 회귀 0건. 신규 케이스(`useQuickActionParam` 5건 + `WelcomeHeader` 6건) 포함.

- [ ] **Step 2: Run typecheck + lint + build**

Run: `cd frontend && pnpm typecheck && pnpm lint && pnpm build`
Expected: 모두 exit 0.

- [ ] **Step 3: 수동 검증 (optional, 단 권장)**

dev preview 가동(`pnpm dev`) 후 브라우저:

1. `/` 진입 → WelcomeHeader 우측 "업로드" + "새 폴더" 버튼 노출.
2. "업로드" 클릭 → file picker open → 파일 선택 → `/d/{deptId}/{rootFolderId}` 로 navigate + `UploadQueueDock` 에 task 표시.
3. "새 폴더" 클릭 → `/d/{deptId}/{rootFolderId}?action=new-folder` 로 navigate → `CreateFolderDialog` 자동 mount + input focus → URL 의 `?action=new-folder` 즉시 사라짐(query 만 제거, path 유지).
4. dialog X 닫기 후 history.back → dashboard 진입 → 페이지 새로고침해도 dialog 재오픈 없음.
5. workspace 0 사용자(테스트 계정) 로그인 → 두 버튼 disabled 표시.

수동 검증을 수행하지 않는 경우 본 step 을 skip 가능. PR description 에 수행/미수행 명시.

---

## Task 8: Docs 갱신

**Files:**
- Modify: `docs/v1x-backlog.md` (Tier 1 행 closure 전환)
- Modify: `docs/progress.md` (closure entry 추가, 최상단에)
- Modify: `docs/01-frontend-design.md` (URL convention 메모 — §9 업로드 또는 §17 URL/라우팅 후보 중 한 곳)

- [ ] **Step 1: Update `docs/v1x-backlog.md`**

기존 행:
```markdown
| 업로드/새 폴더 quick action dialog 자동 오픈 | explorer `?action=` query handler 도입 |
```

를 `~~취소선~~` + closure 마킹 으로 전환:

```markdown
| ~~업로드/새 폴더 quick action dialog 자동 오픈~~ | — | — | ✓ 2026-05-15 quick-action-dialog (PR #<TBD>) | **closure** — WelcomeHeader 업로드 (in-place file picker + `useUpload.enqueue` + `router.push`) + 새 폴더 (`router.push(workspaceRoot?action=new-folder)` → `useQuickActionParam` hook 이 ClientFilesPage 에서 query 감지 + `CreateFolderDialog` mount + 1-shot consume `router.replace`). URL convention `?action=new-folder` 한 케이스만 도입(KISS) |
```

PR 번호는 push 후 채워서 followup commit.

- [ ] **Step 2: Update `docs/progress.md`**

최상단에 다음 entry 추가 (기존 entry 보존):

```markdown
## 2026-05-15 — quick-action-dialog (WelcomeHeader 업로드/새 폴더, PR #<TBD>)

> User Home Dashboard PR #246 spec §3.1 의 보류 트랙 closure. PR #253 의 단일 navigation link 를 quick action 2 버튼 + URL convention `?action=new-folder` 로 정식 구현.

### 범위

WelcomeHeader 의 "내 워크스페이스 →" link 1 개를 업로드/새 폴더 quick action 2 버튼으로 교체. 두 액션을 다른 패턴으로 분리(업로드 = in-place file picker / 새 폴더 = `?action=` URL convention). backend 변경 0.

### 변경

- frontend `hooks/useQuickActionParam.ts` (신규) — `?action=new-folder` query 감지 + 1-shot consume (`router.replace` 로 `?action` 만 제거, 다른 query 보존).
- frontend `hooks/useQuickActionParam.test.ts` (신규) — 5 케이스.
- frontend `components/home/WelcomeHeader.tsx` — link 제거 + 업로드/새 폴더 버튼 + hidden file input + 클릭 핸들러. 0-workspace 시 두 버튼 disabled.
- frontend `components/home/WelcomeHeader.test.tsx` — 기존 3 → 6 케이스(navigate URL verify + 업로드 enqueue/push verify + 파일 0개 취소 가드).
- frontend `app/(explorer)/d/[deptId]/[[...parts]]/ClientFilesPage.tsx` + 동일 team 변형 — `useQuickActionParam` 호출 + `CreateFolderDialog` mount.

### 결정/편차

- **두 액션 분리** — 업로드는 user gesture 보존(`<input>.click()` 정책), 새 폴더는 dialog 가 explorer 컨텍스트 안에서 열려야 의미 있어 navigation 필요. URL convention 1 케이스만(KISS).
- **`closeNewFolder` 후 URL 재방문 무동작** — `?action` 이 mount 시점에 이미 cleansed 되어 dialog 재오픈 없음. 사용자가 X 로 close 후 history.back 해도 dialog 재오픈 없음.
- **수동 검증 (선택)** — Task 7 §3 수동 검증 항목 5건. CI 풀그린 + hook 단위 + WelcomeHeader 단위 가드로 회귀 충분 판단 시 skip 가능.

### 검증

- frontend `pnpm test --run` PASS (신규 11건 포함, 회귀 0).
- frontend `pnpm typecheck && pnpm lint && pnpm build` exit 0.
- (수동 검증 수행 여부 PR description 에 명시)

### 회고

- WelcomeHeader 변경 시 기존 link 가 신규 버튼 2 개로 교체되어 기존 test 케이스 3 건도 갱신 필요 — 회피 불가. test 파일 전면 rewrite 가 부분 수정보다 명료.
- `?action=` URL convention 의 1-shot consume 패턴은 `useOpenFile` 의 ?file= 패턴 답습. 향후 다른 deep-link 추가 시 동일 hook 확장.
```

- [ ] **Step 3: Update `docs/01-frontend-design.md`**

§17 URL/라우팅 섹션에 (정확한 라인은 spec 문서 통해 결정) 짧은 메모 추가:

```markdown
- `?action=new-folder` — dashboard quick action 의 navigation destination 으로만 발급되며, workspace folder 페이지(`/d/...`, `/t/...`) 에서 `useQuickActionParam` hook 이 감지 + `CreateFolderDialog` mount 후 즉시 `?action` 만 제거(`router.replace`). 다른 위치(/trash, /shared, /favorites, /admin) 에서는 무시. spec: `docs/superpowers/specs/2026-05-15-quick-action-dialog-design.md`.
```

추가 위치 결정 — `grep -n "?file=\|deep link\|URL convention\|query param" docs/01-frontend-design.md` 로 인접 행 찾아 후속 줄에 추가.

- [ ] **Step 4: Commit docs**

```bash
git add docs/v1x-backlog.md docs/progress.md docs/01-frontend-design.md
git commit -m "docs(quick-action-dialog): backlog closure + progress + §17 URL convention 메모"
```

---

## Self-Review (이 plan 자체)

1. **Spec coverage**:
   - spec §2 핵심 결정 (두 액션 분리) → Task 4 WelcomeHeader 핸들러로 구현.
   - spec §3.1 URL contract `?action=new-folder` → Task 2 hook + Task 5/6 wire.
   - spec §3.2 업로드 URL convention 없음 → Task 4 핸들러가 router.push 시 query 미발급.
   - spec §4 WelcomeHeader 변경 (버튼 2, 0-workspace disabled, 핸들러) → Task 4.
   - spec §5 Explorer query handler (hook 추출 + 두 ClientFilesPage 통합) → Task 1/2 + Task 5/6.
   - spec §6 Empty/Error/Loading → Task 3 테스트 케이스 (workspace 0건, 파일 0개 취소).
   - spec §7 테스트 전략 §7.1 WelcomeHeader 6 케이스 → Task 3. §7.2 hook 5 케이스 → Task 1. §7.3 ClientFilesPage 통합 (선택) → 본 plan 미포함(spec 에서 선택으로 명시).
   - spec §8 영향받는 문서 → Task 8.

2. **Placeholder scan**:
   - PR 번호 `<TBD>` 는 docs 갱신 시 push 후 채움 — 실제 placeholder 가 아니라 future-fill 표기. 명시.
   - 다른 TBD/TODO 없음.

3. **Type consistency**:
   - hook 시그니처: `useQuickActionParam(folderId: string): { newFolderOpen: boolean; closeNewFolder: () => void }` — Task 1 정의, Task 5/6 사용 일치.
   - WelcomeHeader 핸들러: `useUpload.enqueue(files: File[], folderId: string): void` — 기존 시그니처 확인 (`useUpload.ts` Step 1 test mock 시그니처와 동일).
   - `buildWorkspacePath(scope, folderId, slugPath)` — WelcomeHeader 의 호출이 기존 사용처(`SharedWithMeCard`, `BreadcrumbWithStar`) 와 동일 형태.

4. **누락 가드**:
   - workspace 0 시 enqueue 호출 가드 (Task 4 핸들러의 `if (!defaultWorkspace || !workspaceLink) return`).
   - file 0개 선택 시 enqueue 호출 가드 (`if (!files || files.length === 0) return`).
   - folderId 빈 문자열 시 hook 무동작 (Task 1 hook 의 `folderId.length > 0` 가드).
