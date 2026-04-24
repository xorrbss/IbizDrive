# M4 — 선택 모델 & BulkActionBar 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 파일 탐색기에 다중선택 모델(`selection slice` + 클릭/Ctrl+click/Shift+click/Space/Ctrl+A/Esc)과 BulkActionBar(휴지통 실제 mutation, 다운로드·이동 스텁)를 추가. pendingIds 라이프사이클을 원칙 #3(낙관적 업데이트 정책)에 맞게 구현.

**Architecture:** Zustand selection store가 진실의 출처. `markPending`은 store 내부에서 selected에서 자동 제거(상호 배제). `useDeleteBulk` 훅이 mutation + invalidate + unmark + clear 순서를 고정. FileTable이 focus·키보드·pending skip을 관리하고, FileRow는 얇은 프리젠터. BulkActionBar는 ClientFilesPage 레벨에서 sticky 렌더.

**Tech Stack:** Next.js 15, React 19, Zustand 5, TanStack Query 5, TanStack Virtual 3, TypeScript strict. 테스트: Vitest + jsdom (npm, pnpm 아님).

**설계 근거:** `docs/superpowers/specs/2026-04-25-m4-selection-bulkactionbar-design.md`

**작업 디렉토리:** 모든 명령은 `frontend/`에서 실행 (예: `cd frontend && npm run test`).

---

## 파일 구조

### 신규 파일
| 경로 | 책임 |
|---|---|
| `frontend/vitest.config.ts` | Vitest 설정 (jsdom 환경, `@/*` path alias) |
| `frontend/src/test/setup.ts` | (현재 비어도 됨 — 향후 jest-dom 등 확장 지점) |
| `frontend/src/stores/selection.ts` | Zustand selection slice (§5.1 + 추가 규칙) |
| `frontend/src/stores/selection.test.ts` | selection store 단위 테스트 |
| `frontend/src/hooks/usePermission.ts` | 스텁 훅 (모든 권한 true, §14.2 시그니처 준수) |
| `frontend/src/hooks/useDeleteBulk.ts` | delete mutation + pending 라이프사이클 |
| `frontend/src/hooks/useDeleteBulk.test.ts` | mutation 단위 테스트 (3 케이스) |
| `frontend/src/components/files/BulkActionBar.tsx` | 선택 액션 툴바 |

### 수정 파일
| 경로 | 변경 내용 |
|---|---|
| `frontend/package.json` | devDependencies: vitest, jsdom, @vitejs/plugin-react; scripts: test, typecheck |
| `frontend/src/lib/api.ts` | `deleteBulk(ids)` mock 추가 (MOCK_FILES에서 splice) |
| `frontend/src/components/files/FileRow.tsx` | onClick 시그니처(MouseEvent), aria-selected 실제, pending 시각 |
| `frontend/src/components/files/FileTable.tsx` | aria-multiselectable, 키보드 확장, pending skip, focus 보정 useEffect, clear on folder change |
| `frontend/src/app/(explorer)/files/[...parts]/ClientFilesPage.tsx` | BulkActionBar 렌더 추가 |
| `docs/01-frontend-design.md` | §5.1 하단에 구현 노트 추가 |
| `docs/progress.md` | 세션 기록 |

---

## Task 0: 테스트 인프라 세팅

**Files:**
- Modify: `frontend/package.json`
- Create: `frontend/vitest.config.ts`
- Create: `frontend/src/test/setup.ts`

- [ ] **Step 1: Vitest + jsdom 설치**

```bash
cd frontend && npm install --save-dev vitest@^2 jsdom@^25 @vitejs/plugin-react@^4
```

Expected: `package.json`의 devDependencies에 세 패키지 추가, `package-lock.json` 갱신.

- [ ] **Step 2: `vitest.config.ts` 작성**

```ts
// frontend/vitest.config.ts
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import path from 'node:path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    globals: false,
  },
})
```

- [ ] **Step 3: `src/test/setup.ts` 빈 파일 생성**

```ts
// frontend/src/test/setup.ts
// 향후 확장 지점 (예: jest-dom matchers, MSW 등). 현재는 비워둠.
export {}
```

- [ ] **Step 4: `package.json` scripts 추가**

기존 scripts 블록을 아래로 교체 (dev/build/start/lint 유지):

```json
"scripts": {
  "dev": "next dev",
  "build": "next build",
  "start": "next start",
  "lint": "eslint",
  "typecheck": "tsc --noEmit",
  "test": "vitest run",
  "test:watch": "vitest"
}
```

- [ ] **Step 5: sanity check — 빈 테스트 실행**

임시 sanity 파일을 만들어 runner가 동작함을 확인:

```ts
// frontend/src/test/sanity.test.ts
import { describe, it, expect } from 'vitest'
describe('sanity', () => {
  it('runs', () => { expect(1 + 1).toBe(2) })
})
```

Run: `cd frontend && npm run test`
Expected: `1 passed` 출력. 완료 후 **sanity 파일 삭제**.

- [ ] **Step 6: 커밋**

```bash
git add frontend/package.json frontend/package-lock.json frontend/vitest.config.ts frontend/src/test/setup.ts
git commit -m "chore(M4): Vitest + jsdom 테스트 인프라 추가"
```

---

## Task 1: Selection store + 단위 테스트

**Files:**
- Create: `frontend/src/stores/selection.ts`
- Create: `frontend/src/stores/selection.test.ts`

- [ ] **Step 1: 실패 테스트 작성**

```ts
// frontend/src/stores/selection.test.ts
import { describe, it, expect, beforeEach } from 'vitest'
import { useSelectionStore } from './selection'

const reset = () => {
  useSelectionStore.setState({
    ids: new Set(),
    lastClickedId: null,
    pendingIds: new Set(),
  })
}

describe('selectionStore', () => {
  beforeEach(() => reset())

  describe('selectOnly', () => {
    it('replaces selection and sets anchor', () => {
      useSelectionStore.getState().selectOnly('a')
      expect(Array.from(useSelectionStore.getState().ids)).toEqual(['a'])
      expect(useSelectionStore.getState().lastClickedId).toBe('a')
    })
  })

  describe('toggle', () => {
    it('adds id if absent, sets anchor', () => {
      useSelectionStore.getState().toggle('a')
      expect(useSelectionStore.getState().ids.has('a')).toBe(true)
      expect(useSelectionStore.getState().lastClickedId).toBe('a')
    })

    it('removes id if present, still sets anchor', () => {
      useSelectionStore.getState().toggle('a')
      useSelectionStore.getState().toggle('a')
      expect(useSelectionStore.getState().ids.has('a')).toBe(false)
      expect(useSelectionStore.getState().lastClickedId).toBe('a')
    })
  })

  describe('selectRange', () => {
    const ordered = ['a', 'b', 'c', 'd', 'e']

    it('selects range between anchor and target, keeps anchor', () => {
      useSelectionStore.getState().selectOnly('b')
      useSelectionStore.getState().selectRange('d', ordered)
      expect(Array.from(useSelectionStore.getState().ids).sort()).toEqual(['b', 'c', 'd'])
      expect(useSelectionStore.getState().lastClickedId).toBe('b')
    })

    it('falls back to single select when anchor is null', () => {
      useSelectionStore.getState().selectRange('c', ordered)
      expect(Array.from(useSelectionStore.getState().ids)).toEqual(['c'])
      expect(useSelectionStore.getState().lastClickedId).toBe('c')
    })

    it('falls back when anchor is pending', () => {
      useSelectionStore.getState().selectOnly('b')
      useSelectionStore.getState().markPending(['b'])
      useSelectionStore.getState().selectRange('d', ordered)
      expect(Array.from(useSelectionStore.getState().ids)).toEqual(['d'])
      expect(useSelectionStore.getState().lastClickedId).toBe('d')
    })

    it('falls back when anchor is not in current folder', () => {
      useSelectionStore.setState({ lastClickedId: 'gone' })
      useSelectionStore.getState().selectRange('c', ordered)
      expect(Array.from(useSelectionStore.getState().ids)).toEqual(['c'])
      expect(useSelectionStore.getState().lastClickedId).toBe('c')
    })

    it('excludes pending ids from the selected range', () => {
      useSelectionStore.getState().selectOnly('a')
      useSelectionStore.getState().markPending(['c'])
      useSelectionStore.getState().selectRange('e', ordered)
      expect(useSelectionStore.getState().ids.has('c')).toBe(false)
      expect(Array.from(useSelectionStore.getState().ids).sort()).toEqual(['a', 'b', 'd', 'e'])
    })
  })

  describe('markPending', () => {
    it('removes marked ids from selection (mutual exclusion)', () => {
      useSelectionStore.getState().selectAll(['a', 'b', 'c'])
      useSelectionStore.getState().markPending(['b'])
      expect(useSelectionStore.getState().pendingIds.has('b')).toBe(true)
      expect(useSelectionStore.getState().ids.has('b')).toBe(false)
      expect(Array.from(useSelectionStore.getState().ids).sort()).toEqual(['a', 'c'])
    })
  })

  describe('unmarkPending', () => {
    it('removes ids from pendingIds only', () => {
      useSelectionStore.getState().markPending(['a', 'b'])
      useSelectionStore.getState().unmarkPending(['a'])
      expect(useSelectionStore.getState().pendingIds.has('a')).toBe(false)
      expect(useSelectionStore.getState().pendingIds.has('b')).toBe(true)
    })
  })

  describe('clear', () => {
    it('empties selection and anchor, keeps pendingIds', () => {
      useSelectionStore.getState().selectOnly('a')
      useSelectionStore.getState().markPending(['x'])
      useSelectionStore.getState().clear()
      expect(useSelectionStore.getState().ids.size).toBe(0)
      expect(useSelectionStore.getState().lastClickedId).toBe(null)
      expect(useSelectionStore.getState().pendingIds.has('x')).toBe(true)
    })
  })

  describe('selectAll', () => {
    it('replaces selection with given ids, does not change anchor', () => {
      useSelectionStore.getState().selectOnly('seed')
      useSelectionStore.getState().selectAll(['a', 'b'])
      expect(Array.from(useSelectionStore.getState().ids).sort()).toEqual(['a', 'b'])
      expect(useSelectionStore.getState().lastClickedId).toBe('seed')
    })
  })
})
```

- [ ] **Step 2: 테스트 실행으로 실패 확인**

Run: `cd frontend && npm run test -- selection`
Expected: 모든 테스트 FAIL (`Cannot find module './selection'` 또는 export 없음).

- [ ] **Step 3: selection store 구현**

```ts
// frontend/src/stores/selection.ts
import { create } from 'zustand'

type SelectionState = {
  ids: Set<string>
  lastClickedId: string | null
  pendingIds: Set<string>
  toggle: (id: string) => void
  selectRange: (to: string, orderedIds: string[]) => void
  selectOnly: (id: string) => void
  clear: () => void
  selectAll: (ids: string[]) => void
  markPending: (ids: string[]) => void
  unmarkPending: (ids: string[]) => void
}

export const useSelectionStore = create<SelectionState>((set, get) => ({
  ids: new Set(),
  lastClickedId: null,
  pendingIds: new Set(),

  toggle: (id) =>
    set((s) => {
      const next = new Set(s.ids)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return { ids: next, lastClickedId: id }
    }),

  selectRange: (to, orderedIds) => {
    const { lastClickedId, ids, pendingIds } = get()

    const anchorMissing = !lastClickedId
    const anchorPending = lastClickedId ? pendingIds.has(lastClickedId) : false
    const anchorNotInFolder = lastClickedId
      ? !orderedIds.includes(lastClickedId)
      : false

    if (anchorMissing || anchorPending || anchorNotInFolder) {
      set({ ids: new Set([to]), lastClickedId: to })
      return
    }

    const a = orderedIds.indexOf(lastClickedId!)
    const b = orderedIds.indexOf(to)
    const [start, end] = a < b ? [a, b] : [b, a]
    const next = new Set(ids)
    orderedIds
      .slice(start, end + 1)
      .filter((id) => !pendingIds.has(id))
      .forEach((id) => next.add(id))
    set({ ids: next })
  },

  selectOnly: (id) => set({ ids: new Set([id]), lastClickedId: id }),

  clear: () => set({ ids: new Set(), lastClickedId: null }),

  selectAll: (ids) => set({ ids: new Set(ids) }),

  markPending: (idsToMark) =>
    set((s) => {
      const nextPending = new Set(s.pendingIds)
      const nextSelected = new Set(s.ids)
      idsToMark.forEach((id) => {
        nextPending.add(id)
        nextSelected.delete(id) // 상호 배제
      })
      return { pendingIds: nextPending, ids: nextSelected }
    }),

  unmarkPending: (idsToUnmark) =>
    set((s) => {
      const next = new Set(s.pendingIds)
      idsToUnmark.forEach((id) => next.delete(id))
      return { pendingIds: next }
    }),
}))
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd frontend && npm run test -- selection`
Expected: 모든 테스트 PASS.

- [ ] **Step 5: typecheck**

Run: `cd frontend && npm run typecheck`
Expected: 에러 없음.

- [ ] **Step 6: 커밋**

```bash
git add frontend/src/stores/selection.ts frontend/src/stores/selection.test.ts
git commit -m "feat(M4): selection store + 단위 테스트

- §5.1 + 추가 규칙: markPending이 selection에서 자동 제거 (상호배제)
- selectRange 앵커 폴백 3케이스 (null / pending / 폴더 외)
- 범위 내 pending은 선택에서 제외"
```

---

## Task 2: usePermission 스텁 훅

**Files:**
- Create: `frontend/src/hooks/usePermission.ts`

- [ ] **Step 1: 스텁 훅 작성**

```ts
// frontend/src/hooks/usePermission.ts
// TODO(M7 권한): docs/01 §14.2 스펙대로 useQuery + api.getEffectivePermissions()로 교체.
// docs/03 §3 권한 매트릭스 확정 후 실제 구현 예정.

export type Permission =
  | 'read'
  | 'upload'
  | 'edit'
  | 'delete'
  | 'download'
  | 'move'
  | 'share'
  | 'admin'

export type PermissionFlags = Record<Permission, boolean>

export function usePermission(_nodeId?: string): PermissionFlags {
  return {
    read: true,
    upload: true,
    edit: true,
    delete: true,
    download: true,
    move: true,
    share: true,
    admin: true,
  }
}
```

- [ ] **Step 2: typecheck**

Run: `cd frontend && npm run typecheck`
Expected: 에러 없음.

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/hooks/usePermission.ts
git commit -m "feat(M4): usePermission 스텁 훅 (모든 권한 true)

§14.2 시그니처 준수. M7에서 실제 useQuery 연결로 교체 예정.
TODO 주석으로 교체 지점 명시."
```

---

## Task 3: deleteBulk mock API 추가

**Files:**
- Modify: `frontend/src/lib/api.ts`

- [ ] **Step 1: `api` 객체에 `deleteBulk` 추가**

기존 `api` 객체 닫는 `}` 직전에 다음 메서드를 추가:

```ts
  async deleteBulk(ids: string[]): Promise<{ deletedIds: string[] }> {
    await new Promise((r) => setTimeout(r, 500))
    for (const id of ids) {
      const idx = MOCK_FILES.findIndex((f) => f.id === id)
      if (idx !== -1) MOCK_FILES.splice(idx, 1)
    }
    return { deletedIds: ids }
  },
```

최종 `api` 객체는 `getFolderTree`, `getFolder`, `getFilesInFolder`, `deleteBulk` 4개 메서드를 가진다.

- [ ] **Step 2: typecheck**

Run: `cd frontend && npm run typecheck`
Expected: 에러 없음.

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/lib/api.ts
git commit -m "feat(M4): deleteBulk mock API 추가

MOCK_FILES에서 id 제거 + 500ms delay로 실제 서버 유사 UX 재현.
M5에서 실제 API로 교체 예정."
```

---

## Task 4: useDeleteBulk 훅 + 단위 테스트

**Files:**
- Create: `frontend/src/hooks/useDeleteBulk.ts`
- Create: `frontend/src/hooks/useDeleteBulk.test.ts`

- [ ] **Step 1: 실패 테스트 작성**

```ts
// frontend/src/hooks/useDeleteBulk.test.ts
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import { useDeleteBulk } from './useDeleteBulk'
import { useSelectionStore } from '@/stores/selection'
import { qk } from '@/lib/queryKeys'

// Mock useCurrentFolder to control currentFolderId
let mockFolderId = 'root'
vi.mock('@/hooks/useCurrentFolder', () => ({
  useCurrentFolder: () => ({ folderId: mockFolderId, folder: null, breadcrumb: [], isLoading: false, error: null }),
}))

// Mock api.deleteBulk
const deleteBulkMock = vi.fn()
vi.mock('@/lib/api', () => ({
  api: { deleteBulk: (...args: unknown[]) => deleteBulkMock(...args) },
}))

const makeWrapper = (qc: QueryClient) => {
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children)
}

describe('useDeleteBulk', () => {
  beforeEach(() => {
    deleteBulkMock.mockReset()
    useSelectionStore.setState({
      ids: new Set(),
      lastClickedId: null,
      pendingIds: new Set(),
    })
    mockFolderId = 'root'
  })

  it('성공: markPending → invalidate → unmarkPending → clear 순서', async () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')
    deleteBulkMock.mockResolvedValue({ deletedIds: ['a', 'b'] })

    useSelectionStore.getState().selectAll(['a', 'b', 'c'])

    const { result } = renderHook(() => useDeleteBulk(), { wrapper: makeWrapper(qc) })

    await act(async () => {
      result.current.mutate({ ids: ['a', 'b'], folderIdAtStart: 'root' })
    })

    // markPending 직후 selection에서 제거됨 (상호배제)
    // (타이밍상 onMutate는 mutate 호출 직후 동기 실행)
    await waitFor(() => {
      expect(useSelectionStore.getState().pendingIds.size).toBe(0)  // unmark 후
      expect(useSelectionStore.getState().ids.size).toBe(0)           // clear 후
    })

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: qk.filesInFolder('root', expect.anything(), expect.anything()).slice(0, -2) })
  })

  it('실패 + 같은 폴더: selection 복원', async () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
    deleteBulkMock.mockRejectedValue(new Error('network'))

    useSelectionStore.getState().selectAll(['a', 'b'])
    mockFolderId = 'root'

    const { result } = renderHook(() => useDeleteBulk(), { wrapper: makeWrapper(qc) })

    await act(async () => {
      result.current.mutate({ ids: ['a', 'b'], folderIdAtStart: 'root' })
    })

    await waitFor(() => {
      expect(useSelectionStore.getState().pendingIds.size).toBe(0) // unmark
      expect(Array.from(useSelectionStore.getState().ids).sort()).toEqual(['a', 'b']) // 복원
    })
  })

  it('실패 + 다른 폴더: 복원 스킵', async () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
    deleteBulkMock.mockRejectedValue(new Error('network'))

    useSelectionStore.getState().selectAll(['a', 'b'])
    mockFolderId = 'folder_other'  // 시작 시 current != start (시뮬레이션)

    const { result } = renderHook(() => useDeleteBulk(), { wrapper: makeWrapper(qc) })

    await act(async () => {
      result.current.mutate({ ids: ['a', 'b'], folderIdAtStart: 'root' })
    })

    await waitFor(() => {
      expect(useSelectionStore.getState().pendingIds.size).toBe(0) // unmark
      expect(useSelectionStore.getState().ids.size).toBe(0)          // 복원 안 됨
    })
  })
})
```

**테스트 의존성 추가 필요**: `@testing-library/react`. 이 훅만 훅 테스트용으로 필요. 설치:

Run: `cd frontend && npm install --save-dev @testing-library/react@^16`
Expected: package.json에 추가.

- [ ] **Step 2: 테스트 실행으로 실패 확인**

Run: `cd frontend && npm run test -- useDeleteBulk`
Expected: 모든 테스트 FAIL (`Cannot find module './useDeleteBulk'`).

- [ ] **Step 3: 훅 구현**

```ts
// frontend/src/hooks/useDeleteBulk.ts
'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import { useSelectionStore } from '@/stores/selection'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'

type Vars = { ids: string[]; folderIdAtStart: string }

export function useDeleteBulk() {
  const qc = useQueryClient()
  const markPending = useSelectionStore((s) => s.markPending)
  const unmarkPending = useSelectionStore((s) => s.unmarkPending)
  const clear = useSelectionStore((s) => s.clear)
  const selectAll = useSelectionStore((s) => s.selectAll)
  const { folderId: currentFolderId } = useCurrentFolder()

  return useMutation({
    mutationFn: ({ ids }: Vars) => api.deleteBulk(ids),

    onMutate: ({ ids }) => {
      markPending(ids)
    },

    onSuccess: async (_data, { ids, folderIdAtStart }) => {
      // 현재 폴더 무효화 — sort/dir 불문 모두 무효화하려면 prefix로
      await qc.invalidateQueries({
        queryKey: [...qk.files(), 'list', folderIdAtStart],
      })
      unmarkPending(ids)
      clear()
    },

    onError: (_err, { ids, folderIdAtStart }) => {
      unmarkPending(ids)
      if (folderIdAtStart === currentFolderId) {
        selectAll(ids)
      }
      // TODO(M5): 토스트 에러 알림
      console.warn('deleteBulk 실패', { ids, folderIdAtStart })
    },
  })
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd frontend && npm run test -- useDeleteBulk`
Expected: 3 tests PASS.

주의: 첫 테스트의 `invalidateSpy` 매치가 불안정하면 `expect(invalidateSpy).toHaveBeenCalled()`로 완화 가능. 다만 key prefix는 그대로 검증 유지.

- [ ] **Step 5: typecheck**

Run: `cd frontend && npm run typecheck`
Expected: 에러 없음.

- [ ] **Step 6: 커밋**

```bash
git add frontend/package.json frontend/package-lock.json frontend/src/hooks/useDeleteBulk.ts frontend/src/hooks/useDeleteBulk.test.ts
git commit -m "feat(M4): useDeleteBulk 훅 + 3케이스 테스트

- 성공 경로: markPending → invalidate → unmarkPending → clear (순서 보장)
- 실패 + 같은 폴더: selection 복원
- 실패 + 다른 폴더: 복원 스킵 (ghost 방지)
- folderIdAtStart을 variables로 캡처하여 mutation 도중 폴더 이동 대응"
```

---

## Task 5: BulkActionBar 컴포넌트

**Files:**
- Create: `frontend/src/components/files/BulkActionBar.tsx`

- [ ] **Step 1: BulkActionBar 구현**

```tsx
// frontend/src/components/files/BulkActionBar.tsx
'use client'
import { useSelectionStore } from '@/stores/selection'
import { usePermission } from '@/hooks/usePermission'
import { useDeleteBulk } from '@/hooks/useDeleteBulk'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'

export function BulkActionBar() {
  const count = useSelectionStore((s) => s.ids.size)
  const ids = useSelectionStore((s) => Array.from(s.ids))
  const clear = useSelectionStore((s) => s.clear)
  const can = usePermission()
  const { folderId } = useCurrentFolder()
  const deleteMut = useDeleteBulk()

  if (count === 0) return null

  const handleDownload = () => {
    // TODO(M_download): 실제 다운로드 구현
    console.warn('[스텁] 다운로드 대상:', ids)
  }

  const handleMove = () => {
    // TODO(M6 DnD): 이동 다이얼로그/DnD로 전환
    console.warn('[스텁] 이동 대상:', ids)
  }

  const handleDelete = () => {
    deleteMut.mutate({ ids, folderIdAtStart: folderId })
  }

  return (
    <div
      role="toolbar"
      aria-label="선택 항목 액션"
      aria-live="polite"
      className="sticky top-0 z-20 flex items-center gap-2 bg-white border-b px-4 py-2 shadow-sm"
    >
      <span className="text-sm font-medium">{count}개 선택</span>
      {can.download && (
        <button
          type="button"
          onClick={handleDownload}
          className="px-3 py-1 text-sm border rounded hover:bg-gray-50"
        >
          다운로드
        </button>
      )}
      {can.move && (
        <button
          type="button"
          onClick={handleMove}
          className="px-3 py-1 text-sm border rounded hover:bg-gray-50"
        >
          이동
        </button>
      )}
      {can.delete && (
        <button
          type="button"
          onClick={handleDelete}
          disabled={deleteMut.isPending}
          className="px-3 py-1 text-sm border rounded text-red-600 border-red-300 hover:bg-red-50 disabled:opacity-50"
        >
          휴지통으로
        </button>
      )}
      <button
        type="button"
        onClick={clear}
        className="px-3 py-1 text-sm text-gray-600 hover:bg-gray-50 rounded ml-auto"
      >
        선택 해제
      </button>
    </div>
  )
}
```

- [ ] **Step 2: typecheck**

Run: `cd frontend && npm run typecheck`
Expected: 에러 없음.

- [ ] **Step 3: lint**

Run: `cd frontend && npm run lint`
Expected: 에러 없음.

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/components/files/BulkActionBar.tsx
git commit -m "feat(M4): BulkActionBar — 휴지통은 실제 mutation, 나머지는 스텁

- count===0이면 숨김 (§8.2 스펙)
- role=toolbar + aria-label + aria-live='polite' (개수 변경 낭독)
- 다운로드/이동: console.warn 스텁 + TODO
- 휴지통: useDeleteBulk 호출, isPending일 때 버튼 disabled"
```

---

## Task 6: FileRow 수정 (선택 상태 + pending 시각)

**Files:**
- Modify: `frontend/src/components/files/FileRow.tsx`

- [ ] **Step 1: FileRow 전체 교체**

```tsx
// frontend/src/components/files/FileRow.tsx
'use client'
import type { FileItem } from '@/types/file'

type Props = {
  item: FileItem
  rowIndex: number
  isFocused: boolean
  isSelected: boolean
  isPending: boolean
  onClick?: (item: FileItem, e: React.MouseEvent) => void
  onDoubleClick?: (item: FileItem) => void
  onKeyDown?: (e: React.KeyboardEvent) => void
}

function formatFileSize(bytes: number | null): string {
  if (bytes === null) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function formatDate(iso: string): string {
  const d = new Date(iso)
  return d.toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  })
}

function fileIcon(item: FileItem): string {
  if (item.type === 'folder') return '📁'
  if (item.mimeType?.startsWith('image/')) return '🖼️'
  if (item.mimeType?.includes('pdf')) return '📄'
  if (item.mimeType?.includes('spreadsheet') || item.mimeType?.includes('excel')) return '📊'
  if (item.mimeType?.includes('word') || item.mimeType?.includes('document')) return '📝'
  return '📎'
}

export function FileRow({
  item,
  rowIndex,
  isFocused,
  isSelected,
  isPending,
  onClick,
  onDoubleClick,
  onKeyDown,
}: Props) {
  // 우선순위: pending > selected > focused > hover
  const bgClass = isPending
    ? 'opacity-50'
    : isSelected && isFocused
      ? 'bg-blue-100 outline outline-2 outline-blue-400'
      : isSelected
        ? 'bg-blue-100'
        : isFocused
          ? 'bg-blue-50 outline outline-2 outline-blue-400'
          : 'hover:bg-gray-50'

  return (
    <div
      role="row"
      aria-rowindex={rowIndex}
      aria-selected={isPending ? false : isSelected}
      aria-disabled={isPending || undefined}
      tabIndex={isFocused ? 0 : -1}
      className={`flex items-center gap-4 h-10 px-4 select-none border-b border-gray-100 ${
        isPending ? 'cursor-not-allowed' : 'cursor-pointer'
      } ${bgClass}`}
      onClick={(e) => {
        if (isPending) return
        onClick?.(item, e)
      }}
      onDoubleClick={() => {
        if (isPending) return
        onDoubleClick?.(item)
      }}
      onKeyDown={onKeyDown}
      data-file-id={item.id}
    >
      <span className="w-6 text-center" role="gridcell" aria-hidden="true">{fileIcon(item)}</span>
      <span className="flex-1 truncate text-sm font-medium" role="gridcell">{item.name}</span>
      <span className="w-24 text-right text-xs text-gray-500" role="gridcell">{formatFileSize(item.size)}</span>
      <span className="w-28 text-right text-xs text-gray-500" role="gridcell">{formatDate(item.updatedAt)}</span>
      <span className="w-20 text-right text-xs text-gray-500 truncate flex items-center justify-end gap-1" role="gridcell">
        {isPending && <span aria-hidden="true" className="inline-block w-3 h-3 border-2 border-gray-300 border-t-gray-600 rounded-full animate-spin" />}
        <span className="truncate">{item.updatedBy}</span>
      </span>
    </div>
  )
}
```

주의: Props 시그니처 변경. `onClick`이 `(id) => void`에서 `(item, e) => void`로 바뀜. FileTable에서 맞춰 수정(Task 7).

- [ ] **Step 2: typecheck (실패 예상 — FileTable이 아직 이전 시그니처 사용)**

Run: `cd frontend && npm run typecheck`
Expected: FileTable.tsx에서 Props 불일치 에러. **OK** — Task 7에서 맞춤.

커밋은 Task 7과 묶어서 수행 (중간 빌드 깨짐 방지). Task 6과 7은 연속 실행.

---

## Task 7: FileTable 수정 (키보드 확장 + pending skip + 통합)

**Files:**
- Modify: `frontend/src/components/files/FileTable.tsx`

- [ ] **Step 1: FileTable 전체 교체**

```tsx
// frontend/src/components/files/FileTable.tsx
'use client'
import { useRef, useState, useCallback, useEffect } from 'react'
import { useVirtualizer } from '@tanstack/react-virtual'
import { useRouter } from 'next/navigation'
import { useFilesInFolder } from '@/hooks/useFilesInFolder'
import { useSortParams } from '@/hooks/useSortParams'
import { useSelectionStore } from '@/stores/selection'
import { FileRow } from './FileRow'
import { FileTableSkeleton } from './FileTableSkeleton'
import { FileTableEmpty } from './FileTableEmpty'
import { FileTableError } from './FileTableError'
import { FileTableForbidden } from './FileTableForbidden'
import type { FileItem } from '@/types/file'

const ROW_HEIGHT = 40

type Props = {
  folderId: string
}

export function FileTable({ folderId }: Props) {
  const { sort, dir } = useSortParams()
  const { data: items, isLoading, error, refetch } = useFilesInFolder(folderId, sort, dir)
  const [focusedIndex, setFocusedIndex] = useState(-1)
  const scrollRef = useRef<HTMLDivElement>(null)
  const router = useRouter()

  const selectedIds = useSelectionStore((s) => s.ids)
  const pendingIds = useSelectionStore((s) => s.pendingIds)
  const selectOnly = useSelectionStore((s) => s.selectOnly)
  const toggle = useSelectionStore((s) => s.toggle)
  const selectRange = useSelectionStore((s) => s.selectRange)
  const selectAll = useSelectionStore((s) => s.selectAll)
  const clear = useSelectionStore((s) => s.clear)

  // 폴더 변경 시 focus와 selection 모두 리셋 (pendingIds는 유지)
  useEffect(() => {
    setFocusedIndex(-1)
    clear()
  }, [folderId, clear])

  // 포커스된 DOM 요소 동기화 (스크린 리더)
  useEffect(() => {
    if (focusedIndex < 0 || !items) return
    const row = scrollRef.current?.querySelector(
      `[data-file-id="${items[focusedIndex]?.id}"]`
    ) as HTMLElement | null
    row?.focus()
  }, [focusedIndex, items])

  // markPending 시 focus가 pending이 되면 최근접 non-pending으로 보정
  useEffect(() => {
    if (focusedIndex < 0 || !items) return
    const focusedItem = items[focusedIndex]
    if (!focusedItem || !pendingIds.has(focusedItem.id)) return

    const findNonPending = (start: number, step: 1 | -1) => {
      for (let i = start; i >= 0 && i < items.length; i += step) {
        if (!pendingIds.has(items[i].id)) return i
      }
      return -1
    }

    const downIdx = findNonPending(focusedIndex + 1, 1)
    const next = downIdx !== -1 ? downIdx : findNonPending(focusedIndex - 1, -1)
    setFocusedIndex(next)
  }, [pendingIds, focusedIndex, items])

  const rowCount = items?.length ?? 0

  const virtualizer = useVirtualizer({
    count: rowCount,
    getScrollElement: () => scrollRef.current,
    estimateSize: () => ROW_HEIGHT,
    overscan: 10,
  })

  const handleOpen = useCallback(
    (item: FileItem) => {
      if (item.type === 'folder') {
        router.push(`/files/${item.id}`)
      } else {
        const url = new URL(window.location.href)
        url.searchParams.set('file', item.id)
        router.replace(url.pathname + url.search, { scroll: false })
      }
    },
    [router]
  )

  const handleRowClick = useCallback(
    (item: FileItem, e: React.MouseEvent) => {
      if (!items) return
      const idx = items.findIndex((it) => it.id === item.id)
      if (idx === -1) return
      setFocusedIndex(idx)

      if (e.shiftKey) {
        const orderedIds = items.map((it) => it.id)
        selectRange(item.id, orderedIds)
      } else if (e.ctrlKey || e.metaKey) {
        toggle(item.id)
      } else {
        selectOnly(item.id)
      }
    },
    [items, selectOnly, toggle, selectRange]
  )

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (!items || items.length === 0) return

      switch (e.key) {
        case 'ArrowDown': {
          e.preventDefault()
          setFocusedIndex((prev) => {
            let next = prev + 1
            while (next < items.length && pendingIds.has(items[next].id)) next++
            if (next >= items.length) return prev
            virtualizer.scrollToIndex(next, { align: 'auto' })
            return next
          })
          break
        }
        case 'ArrowUp': {
          e.preventDefault()
          setFocusedIndex((prev) => {
            let next = prev - 1
            while (next >= 0 && pendingIds.has(items[next].id)) next--
            if (next < 0) return prev
            virtualizer.scrollToIndex(next, { align: 'auto' })
            return next
          })
          break
        }
        case ' ': {
          if (focusedIndex < 0) return
          const focusedId = items[focusedIndex]?.id
          if (!focusedId || pendingIds.has(focusedId)) return
          e.preventDefault()
          toggle(focusedId)
          break
        }
        case 'a':
        case 'A': {
          if (e.ctrlKey || e.metaKey) {
            e.preventDefault()
            const selectable = items
              .filter((it) => !pendingIds.has(it.id))
              .map((it) => it.id)
            if (selectable.length === 0) return
            selectAll(selectable)
          }
          break
        }
        case 'Enter': {
          e.preventDefault()
          if (focusedIndex >= 0 && focusedIndex < items.length) {
            handleOpen(items[focusedIndex])
          }
          break
        }
        case 'Escape': {
          e.preventDefault()
          setFocusedIndex(-1)
          clear()
          scrollRef.current?.focus()
          break
        }
      }
    },
    [items, focusedIndex, pendingIds, toggle, selectAll, clear, handleOpen, virtualizer]
  )

  if (isLoading) return <FileTableSkeleton />

  const status = (error as { status?: number })?.status
  if (status === 403) return <FileTableForbidden />
  if (error) return <FileTableError onRetry={refetch} />
  if (!items || items.length === 0) return <FileTableEmpty />

  return (
    <div
      role="grid"
      aria-rowcount={items.length + 1}
      aria-multiselectable={true}
      aria-label="파일 목록"
      className="flex flex-col border rounded-lg overflow-hidden mt-4"
    >
      <div
        className="flex items-center gap-4 h-9 px-4 bg-gray-50 border-b text-xs font-medium text-gray-600"
        role="row"
        aria-rowindex={1}
      >
        <span className="w-6" role="columnheader" />
        <span className="flex-1" role="columnheader">이름</span>
        <span className="w-24 text-right" role="columnheader">크기</span>
        <span className="w-28 text-right" role="columnheader">수정일</span>
        <span className="w-20 text-right" role="columnheader">수정자</span>
      </div>

      <div
        ref={scrollRef}
        tabIndex={0}
        onKeyDown={handleKeyDown}
        className="flex-1 overflow-auto outline-none"
        style={{ maxHeight: 'calc(100vh - 200px)' }}
      >
        <div
          className="relative w-full"
          style={{ height: `${virtualizer.getTotalSize()}px` }}
        >
          {virtualizer.getVirtualItems().map((virtualRow) => {
            const item = items[virtualRow.index]
            return (
              <div
                key={item.id}
                className="absolute top-0 left-0 w-full"
                style={{
                  height: `${virtualRow.size}px`,
                  transform: `translateY(${virtualRow.start}px)`,
                }}
              >
                <FileRow
                  item={item}
                  rowIndex={virtualRow.index + 2}
                  isFocused={focusedIndex === virtualRow.index}
                  isSelected={selectedIds.has(item.id)}
                  isPending={pendingIds.has(item.id)}
                  onClick={handleRowClick}
                  onDoubleClick={handleOpen}
                  onKeyDown={handleKeyDown}
                />
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: typecheck 통과 확인**

Run: `cd frontend && npm run typecheck`
Expected: 에러 없음.

- [ ] **Step 3: lint 통과 확인**

Run: `cd frontend && npm run lint`
Expected: 에러 없음.

- [ ] **Step 4: 테스트 전량 실행 (회귀 확인)**

Run: `cd frontend && npm run test`
Expected: 모든 테스트(selection + useDeleteBulk) PASS.

- [ ] **Step 5: 커밋 (Task 6+7 묶음)**

```bash
git add frontend/src/components/files/FileRow.tsx frontend/src/components/files/FileTable.tsx
git commit -m "feat(M4): FileRow/FileTable에 selection 모델 연결

FileRow:
- onClick 시그니처 (item, MouseEvent)로 변경 (shift/ctrl/meta 전달)
- aria-selected 실제 연결, aria-disabled when pending
- 시각 우선순위: pending > selected > focused > hover
- pending 시 opacity 0.5 + 우측 스피너

FileTable:
- aria-multiselectable='true' 복원
- 마우스: 클릭=selectOnly / Ctrl·Meta+click=toggle / Shift+click=selectRange
- 키보드 추가: Space(toggle), Ctrl+A(selectAll non-pending), Esc(+clear())
- ArrowUp/Down은 pending 스킵
- markPending 시 focus 보정 useEffect (non-pending 최근접으로)
- 폴더 변경 시 clear() (pendingIds는 유지)"
```

---

## Task 8: ClientFilesPage에 BulkActionBar 통합

**Files:**
- Modify: `frontend/src/app/(explorer)/files/[...parts]/ClientFilesPage.tsx`

- [ ] **Step 1: BulkActionBar import + 렌더**

기존 파일 내용을 아래로 교체:

```tsx
// frontend/src/app/(explorer)/files/[...parts]/ClientFilesPage.tsx
'use client'
import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { buildCanonicalPath } from '@/lib/folderPath'
import { Breadcrumb } from '@/components/folders/Breadcrumb'
import { FileTable } from '@/components/files/FileTable'
import { BulkActionBar } from '@/components/files/BulkActionBar'

export function ClientFilesPage({ parts }: { parts: string[] }) {
  const router = useRouter()
  const { folderId, folder, isLoading, error } = useCurrentFolder()

  useEffect(() => {
    if (!folder) return
    const canonical = buildCanonicalPath(folder.id, folder.slugPath)
    const current = `/files/${parts.join('/')}`
    if (decodeURI(current) !== decodeURI(canonical)) {
      router.replace(canonical)
    }
  }, [folder, parts, router])

  if (isLoading) return <div>로딩...</div>
  if (error) return <div>에러: {String(error)}</div>
  if (!folder) return null

  return (
    <div>
      <Breadcrumb />
      <BulkActionBar />
      <FileTable folderId={folderId} />
    </div>
  )
}
```

- [ ] **Step 2: typecheck + lint**

Run: `cd frontend && npm run typecheck && npm run lint`
Expected: 에러 없음.

- [ ] **Step 3: dev 서버로 수동 브라우저 검증 (4100번 포트)**

Run: `cd frontend && npm run dev -- -p 4100`

브라우저에서 http://localhost:4100/files/root 접속 후 아래 시나리오를 **모두** 확인:

| # | 동작 | 기대 |
|---|---|---|
| 1 | 한 행 클릭 | 해당 행 bg-blue-100 / BulkActionBar "1개 선택" 노출 |
| 2 | 다른 행 클릭 | 이전 선택 해제, 새 행만 선택 |
| 3 | Ctrl+click | 추가 선택 (2개) |
| 4 | Shift+click | 앵커~타겟 범위 선택 |
| 5 | Space (포커스 상태에서) | 해당 행 토글 |
| 6 | Ctrl+A | 보이는 모든 행 선택 |
| 7 | Esc | 선택 전체 해제 + BulkActionBar 사라짐 |
| 8 | 휴지통 버튼 | 선택 행 opacity 0.5 + 스피너 (~500ms) → 리스트에서 사라짐 |
| 9 | 폴더 이동 (FolderTree 클릭) | 선택 전부 clear |
| 10 | Shift+click 앵커가 없을 때 | 단일 선택으로 fallback |
| 11 | ↑↓ 이동 중 pending 행 | 건너뜀 |
| 12 | 다운로드/이동 버튼 | console.warn에 "[스텁] ..." 출력 |

**문제 발견 시**: Ctrl+C로 서버 중지 후 해당 Task로 돌아가 수정.

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/app/(explorer)/files/[...parts]/ClientFilesPage.tsx
git commit -m "feat(M4): ClientFilesPage에 BulkActionBar 통합

Breadcrumb과 FileTable 사이에 sticky 배치. count===0이면 숨김."
```

---

## Task 9: 문서 업데이트

**Files:**
- Modify: `docs/01-frontend-design.md`
- Modify: `docs/progress.md`

- [ ] **Step 1: docs/01 §5.1 하단에 구현 노트 추가**

`docs/01-frontend-design.md`에서 `### 5.1 Selection slice` 블록의 닫는 ``` (320줄 부근) 바로 아래에 다음을 추가:

```markdown

> **구현 노트 (M4, 2026-04-25)**
>
> 위 코드 shape는 그대로 유지하되 다음 규칙이 store 내부에서 강제된다:
> - `markPending(ids)`는 해당 id들을 `ids`(selected)에서도 제거한다 (pending↔selected 상호 배제)
> - `selectRange`는 앵커가 없거나 / pending이거나 / 현재 폴더에 없을 때 단일 선택으로 폴백
> - `selectRange` 범위 내 pending은 선택에서 제외
>
> 상세: `docs/superpowers/specs/2026-04-25-m4-selection-bulkactionbar-design.md` §2.1, §2.2
```

- [ ] **Step 2: docs/progress.md 최상단에 세션 기록 추가**

`docs/progress.md`의 `---` 구분선 바로 위(가장 위에 있는 세션 직전)에 다음 블록을 추가:

```markdown
## 2026-04-25 — M4 완료 (선택 모델 + BulkActionBar)

### 완료
- [M4] selection store (stores/selection.ts) + Vitest 단위 테스트
  - §5.1 스펙 + markPending이 selection에서 자동 제거 (상호배제)
  - selectRange 앵커 폴백 3케이스 (null / pending / 폴더 외)
- [M4] usePermission 스텁 훅 (§14.2 시그니처, M7 교체 예정)
- [M4] useDeleteBulk 훅 + 3케이스 단위 테스트 (성공 / 실패+같은폴더 / 실패+다른폴더)
- [M4] BulkActionBar (role=toolbar, aria-live=polite, count===0 숨김)
- [M4] FileRow: aria-selected 실제 연결, pending 시 opacity+스피너+aria-disabled, onClick(item, MouseEvent) 시그니처
- [M4] FileTable: aria-multiselectable 복원, 키보드 Space/Ctrl+A/Esc clear, ArrowUp/Down pending 스킵, markPending focus 보정 useEffect, 폴더 변경 시 clear
- [M4] Vitest + jsdom 테스트 인프라 세팅 (vitest.config.ts, test/setup.ts)
- [M4] api.deleteBulk mock 추가

### 계약 파일 추가/수정
- frontend/src/stores/selection.ts               (docs/01 §5.1)
- frontend/src/hooks/usePermission.ts            (docs/01 §14.2 스텁)
- frontend/src/hooks/useDeleteBulk.ts            (설계안 §2.5)
- frontend/src/components/files/BulkActionBar.tsx (docs/01 §8.2)
- frontend/src/components/files/FileRow.tsx 수정
- frontend/src/components/files/FileTable.tsx 수정
- frontend/src/lib/api.ts 수정 (deleteBulk 추가)

### 설계 문서 업데이트
- docs/01 §5.1 하단에 구현 노트 (상호배제, 앵커 폴백) 추가
- docs/superpowers/specs/2026-04-25-m4-selection-bulkactionbar-design.md 신규 생성

### 다음 세션 컨텍스트 (M5 백엔드 연결 또는 M6 DnD)
- useDeleteBulk의 실패 경로는 mock이라 현재 console.warn까지만. M5에서 토스트 라이브러리 통합 필요
- BulkActionBar 다운로드/이동은 여전히 스텁 (각각 별도 마일스톤)
- usePermission은 스텁 — M7 권한에서 §14.2대로 useQuery 연결
- Shift+↑↓ / Ctrl+↑↓ / F2 / Delete / `/` 검색 포커스는 M10으로 연기 유지

### 블로커
- 없음

---

```

- [ ] **Step 3: 전체 품질 게이트 최종 확인**

Run: `cd frontend && npm run typecheck && npm run lint && npm run test`
Expected: 모두 PASS.

- [ ] **Step 4: 커밋**

```bash
git add docs/01-frontend-design.md docs/progress.md
git commit -m "docs(M4): §5.1 구현 노트 + progress.md 세션 기록"
```

---

## 완료 후 확인

- [ ] `git log --oneline` 으로 M4 커밋 체인 확인 (Task 0~9 각 1커밋, Task 6+7 묶음)
- [ ] `cd frontend && npm run typecheck && npm run lint && npm run test` 최종 통과
- [ ] docs/progress.md 최상단에 M4 세션 기록
- [ ] docs/superpowers/specs/2026-04-25-m4-*.md 존재 확인

## 제약 & 주의

- **npm 사용 (pnpm 아님)** — 기존 프로젝트 설정 유지
- **작업 디렉토리는 `frontend/`** — npm 명령은 모두 frontend에서 실행
- **Task 6과 7은 묶어 커밋** — FileRow 시그니처 변경이 FileTable 수정 없이는 빌드 깨짐
- **토스트 라이브러리 추가 금지** — M4에서는 console.warn로 TODO 자리만 확보
- **설계 문서와 충돌 발견 시 중단**하고 사용자에게 확인 요청 (docs/01 §19 핵심 원칙과 충돌 여부)
