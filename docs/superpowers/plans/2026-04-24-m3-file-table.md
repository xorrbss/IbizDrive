# M3 FileTable Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a virtualized file table (`FileTable`) that displays files in a folder with 4 states (loading/empty/error/forbidden), basic keyboard navigation (arrow keys, Enter, Esc), and full aria attributes for accessibility.

**Architecture:** `useFilesInFolder` hook fetches file list via TanStack Query keyed by `(folderId, sort, dir)`. `FileTable` renders a TanStack Virtual grid with `FileRow` components. Sort/dir come from URL searchParams — no sort UI yet. Selection always false (M4). Focus managed via roving tabIndex pattern.

**Tech Stack:** TanStack Virtual (`@tanstack/react-virtual`), TanStack Query v5, Next.js 15 App Router, Zustand, TypeScript, Tailwind CSS

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `src/types/file.ts` | `FileItem` and `SortKey` types |
| Modify | `src/lib/queryKeys.ts` | Add `files()`, `filesInFolder()` keys |
| Modify | `src/lib/api.ts` | Add mock file data + `getFilesInFolder()` |
| Create | `src/hooks/useFilesInFolder.ts` | Query hook for file list |
| Create | `src/hooks/useSortParams.ts` | Read sort/dir from URL searchParams |
| Create | `src/components/files/FileTable.tsx` | Main table with virtualization + state routing |
| Create | `src/components/files/FileRow.tsx` | Single row rendering |
| Create | `src/components/files/FileTableEmpty.tsx` | Empty folder state |
| Create | `src/components/files/FileTableError.tsx` | Error state with retry |
| Create | `src/components/files/FileTableForbidden.tsx` | 403 state |
| Create | `src/components/files/FileTableSkeleton.tsx` | Loading skeleton |
| Modify | `src/app/(explorer)/files/[...parts]/ClientFilesPage.tsx` | Integrate FileTable |

---

## Task 1: Install `@tanstack/react-virtual`

**Files:**
- Modify: `package.json`

- [ ] **Step 1: Install the package**

```bash
cd C:\project\IbizDrive\frontend && npm install @tanstack/react-virtual
```

- [ ] **Step 2: Verify installation**

```bash
cd C:\project\IbizDrive\frontend && node -e "require('@tanstack/react-virtual')" && echo OK
```

Expected: `OK`

- [ ] **Step 3: Commit**

```bash
cd C:\project\IbizDrive\frontend && git add package.json package-lock.json && git commit -m "chore: add @tanstack/react-virtual for FileTable virtualization"
```

---

## Task 2: Add `FileItem` type and `SortKey`

**Files:**
- Create: `src/types/file.ts`

- [ ] **Step 1: Create the type file**

```ts
// src/types/file.ts

export type FileItem = {
  id: string
  name: string
  type: 'file' | 'folder'
  mimeType: string | null   // null for folders
  size: number | null        // bytes, null for folders
  updatedAt: string          // ISO 8601
  updatedBy: string          // user display name
  parentId: string
}

export type SortKey = 'name' | 'updatedAt' | 'size'
```

- [ ] **Step 2: Commit**

```bash
git add src/types/file.ts && git commit -m "feat(types): add FileItem and SortKey types for M3"
```

---

## Task 3: Extend query keys

**Files:**
- Modify: `src/lib/queryKeys.ts`

The design doc (§6.1) specifies `files()` and `filesInFolder()` keys. We need to import `SortKey` and add these entries.

- [ ] **Step 1: Update queryKeys.ts**

Replace the entire file with:

```ts
// src/lib/queryKeys.ts
import type { SortKey } from '@/types/file'

export const qk = {
  all: ['explorer'] as const,
  folders: () => [...qk.all, 'folders'] as const,
  folderTree: () => [...qk.folders(), 'tree'] as const,
  folder: (id: string) => [...qk.folders(), 'detail', id] as const,
  effectivePermissions: () => [...qk.all, 'permissions', 'effective'] as const,

  files: () => [...qk.all, 'files'] as const,
  filesInFolder: (folderId: string, sort: SortKey, dir: 'asc' | 'desc') =>
    [...qk.files(), 'list', folderId, sort, dir] as const,
  fileDetail: (id: string) => [...qk.files(), 'detail', id] as const,
} as const
```

- [ ] **Step 2: Verify typecheck**

```bash
cd C:\project\IbizDrive\frontend && npx tsc --noEmit
```

Expected: no errors

- [ ] **Step 3: Commit**

```bash
git add src/lib/queryKeys.ts && git commit -m "feat(queryKeys): add files, filesInFolder, fileDetail keys (docs/01 §6.1)"
```

---

## Task 4: Add mock file data and `getFilesInFolder` API

**Files:**
- Modify: `src/lib/api.ts`

We add a `MOCK_FILES` array and a `getFilesInFolder` method that returns files filtered by `parentId`, sorted by the given key/direction. This mock will be replaced in M5 with real API calls.

- [ ] **Step 1: Update api.ts**

Add the following imports and data at the top, and extend the `api` object:

After the existing imports, add:

```ts
import type { FileItem, SortKey } from '@/types/file'
```

Add the mock data constant after `MOCK_TREE`:

```ts
const MOCK_FILES: FileItem[] = [
  {
    id: 'file_proposal',
    name: '제안서_2026.pdf',
    type: 'file',
    mimeType: 'application/pdf',
    size: 2_400_000,
    updatedAt: '2026-04-20T09:00:00Z',
    updatedBy: '김영수',
    parentId: 'root',
  },
  {
    id: 'file_budget',
    name: '예산안.xlsx',
    type: 'file',
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    size: 580_000,
    updatedAt: '2026-04-18T14:30:00Z',
    updatedBy: '이지은',
    parentId: 'root',
  },
  {
    id: 'file_minutes',
    name: '회의록_0415.docx',
    type: 'file',
    mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    size: 120_000,
    updatedAt: '2026-04-15T11:00:00Z',
    updatedBy: '박준형',
    parentId: 'root',
  },
  {
    id: 'file_contract_a',
    name: '계약서_A사.pdf',
    type: 'file',
    mimeType: 'application/pdf',
    size: 3_100_000,
    updatedAt: '2026-04-22T16:00:00Z',
    updatedBy: '김영수',
    parentId: 'folder_contracts',
  },
  {
    id: 'file_contract_b',
    name: '계약서_B사.pdf',
    type: 'file',
    mimeType: 'application/pdf',
    size: 2_800_000,
    updatedAt: '2026-04-21T10:00:00Z',
    updatedBy: '이지은',
    parentId: 'folder_contracts',
  },
  // Sub-folders appear as type:'folder' items in the listing
  {
    id: 'folder_sales',
    name: '영업팀',
    type: 'folder',
    mimeType: null,
    size: null,
    updatedAt: '2026-04-19T08:00:00Z',
    updatedBy: '관리자',
    parentId: 'root',
  },
  {
    id: 'folder_hr',
    name: '인사팀',
    type: 'folder',
    mimeType: null,
    size: null,
    updatedAt: '2026-04-10T08:00:00Z',
    updatedBy: '관리자',
    parentId: 'root',
  },
]
```

Add this method inside the `api` object:

```ts
  async getFilesInFolder(
    folderId: string,
    sort: SortKey = 'name',
    dir: 'asc' | 'desc' = 'asc'
  ): Promise<FileItem[]> {
    await new Promise((r) => setTimeout(r, 150))
    const items = MOCK_FILES.filter((f) => f.parentId === folderId)
    return items.sort((a, b) => {
      let cmp = 0
      if (sort === 'name') {
        cmp = a.name.localeCompare(b.name, 'ko')
      } else if (sort === 'updatedAt') {
        cmp = a.updatedAt.localeCompare(b.updatedAt)
      } else if (sort === 'size') {
        cmp = (a.size ?? 0) - (b.size ?? 0)
      }
      return dir === 'asc' ? cmp : -cmp
    })
  },
```

- [ ] **Step 2: Verify typecheck**

```bash
cd C:\project\IbizDrive\frontend && npx tsc --noEmit
```

Expected: no errors

- [ ] **Step 3: Commit**

```bash
git add src/lib/api.ts && git commit -m "feat(api): add mock getFilesInFolder with sorting (M3)"
```

---

## Task 5: Create `useSortParams` hook

**Files:**
- Create: `src/hooks/useSortParams.ts`

Reads `sort` and `dir` from URL searchParams. No setter — sort UI is deferred.

- [ ] **Step 1: Create the hook**

```ts
// src/hooks/useSortParams.ts
'use client'
import { useSearchParams } from 'next/navigation'
import type { SortKey } from '@/types/file'

const VALID_SORT_KEYS: SortKey[] = ['name', 'updatedAt', 'size']

export function useSortParams() {
  const searchParams = useSearchParams()
  const rawSort = searchParams.get('sort')
  const rawDir = searchParams.get('dir')

  const sort: SortKey = VALID_SORT_KEYS.includes(rawSort as SortKey)
    ? (rawSort as SortKey)
    : 'name'
  const dir: 'asc' | 'desc' = rawDir === 'desc' ? 'desc' : 'asc'

  return { sort, dir }
}
```

- [ ] **Step 2: Verify typecheck**

```bash
cd C:\project\IbizDrive\frontend && npx tsc --noEmit
```

- [ ] **Step 3: Commit**

```bash
git add src/hooks/useSortParams.ts && git commit -m "feat(hooks): add useSortParams to read sort/dir from URL"
```

---

## Task 6: Create `useFilesInFolder` hook

**Files:**
- Create: `src/hooks/useFilesInFolder.ts`

- [ ] **Step 1: Create the hook**

```ts
// src/hooks/useFilesInFolder.ts
'use client'
import { useQuery } from '@tanstack/react-query'
import { qk } from '@/lib/queryKeys'
import { api } from '@/lib/api'
import type { SortKey } from '@/types/file'

export function useFilesInFolder(
  folderId: string,
  sort: SortKey,
  dir: 'asc' | 'desc'
) {
  return useQuery({
    queryKey: qk.filesInFolder(folderId, sort, dir),
    queryFn: () => api.getFilesInFolder(folderId, sort, dir),
    staleTime: 30_000,
  })
}
```

- [ ] **Step 2: Verify typecheck**

```bash
cd C:\project\IbizDrive\frontend && npx tsc --noEmit
```

- [ ] **Step 3: Commit**

```bash
git add src/hooks/useFilesInFolder.ts && git commit -m "feat(hooks): add useFilesInFolder query hook (docs/01 §6)"
```

---

## Task 7: Create state components (Skeleton, Empty, Error, Forbidden)

**Files:**
- Create: `src/components/files/FileTableSkeleton.tsx`
- Create: `src/components/files/FileTableEmpty.tsx`
- Create: `src/components/files/FileTableError.tsx`
- Create: `src/components/files/FileTableForbidden.tsx`

- [ ] **Step 1: Create FileTableSkeleton**

```tsx
// src/components/files/FileTableSkeleton.tsx
export function FileTableSkeleton() {
  return (
    <div className="space-y-2 p-4" role="status" aria-label="파일 목록 로딩 중">
      {Array.from({ length: 8 }).map((_, i) => (
        <div key={i} className="flex items-center gap-4 h-10 animate-pulse">
          <div className="w-6 h-6 bg-gray-200 rounded" />
          <div className="flex-1 h-4 bg-gray-200 rounded" />
          <div className="w-24 h-4 bg-gray-200 rounded" />
          <div className="w-20 h-4 bg-gray-200 rounded" />
        </div>
      ))}
    </div>
  )
}
```

- [ ] **Step 2: Create FileTableEmpty**

```tsx
// src/components/files/FileTableEmpty.tsx
export function FileTableEmpty() {
  return (
    <div className="flex flex-col items-center justify-center py-20 text-gray-500">
      <svg
        className="w-16 h-16 mb-4 text-gray-300"
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
        aria-hidden="true"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={1.5}
          d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z"
        />
      </svg>
      <p className="text-lg font-medium">이 폴더는 비어 있습니다</p>
      <p className="mt-1 text-sm">파일을 드래그하거나 업로드 버튼을 눌러 추가하세요</p>
    </div>
  )
}
```

- [ ] **Step 3: Create FileTableError**

```tsx
// src/components/files/FileTableError.tsx
type Props = {
  onRetry: () => void
}

export function FileTableError({ onRetry }: Props) {
  return (
    <div className="flex flex-col items-center justify-center py-20 text-gray-500">
      <p className="text-lg font-medium text-red-600">파일 목록을 불러올 수 없습니다</p>
      <p className="mt-1 text-sm">네트워크를 확인하고 다시 시도해주세요</p>
      <button
        onClick={onRetry}
        className="mt-4 px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
      >
        다시 시도
      </button>
    </div>
  )
}
```

- [ ] **Step 4: Create FileTableForbidden**

```tsx
// src/components/files/FileTableForbidden.tsx
export function FileTableForbidden() {
  return (
    <div className="flex flex-col items-center justify-center py-20 text-gray-500">
      <p className="text-lg font-medium">접근 권한이 없습니다</p>
      <p className="mt-1 text-sm">이 폴더의 열람 권한이 필요합니다. 관리자에게 문의하세요.</p>
    </div>
  )
}
```

- [ ] **Step 5: Verify typecheck**

```bash
cd C:\project\IbizDrive\frontend && npx tsc --noEmit
```

- [ ] **Step 6: Commit**

```bash
git add src/components/files/FileTableSkeleton.tsx src/components/files/FileTableEmpty.tsx src/components/files/FileTableError.tsx src/components/files/FileTableForbidden.tsx && git commit -m "feat(FileTable): add 4 state components — skeleton, empty, error, forbidden (docs/01 §11)"
```

---

## Task 8: Create `FileRow` component

**Files:**
- Create: `src/components/files/FileRow.tsx`

Each row displays: icon (folder/file type), name, size, updatedAt, updatedBy. Has `aria-rowindex`, `aria-selected` (always false for now), roving `tabIndex`, and an `onClick` prop placeholder for M4.

- [ ] **Step 1: Create FileRow**

```tsx
// src/components/files/FileRow.tsx
'use client'
import type { FileItem } from '@/types/file'

type Props = {
  item: FileItem
  rowIndex: number         // 1-based for aria-rowindex
  isFocused: boolean
  onClick?: (id: string) => void
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
  onClick,
  onDoubleClick,
  onKeyDown,
}: Props) {
  return (
    <div
      role="row"
      aria-rowindex={rowIndex}
      aria-selected={false}
      tabIndex={isFocused ? 0 : -1}
      className={`flex items-center gap-4 h-10 px-4 cursor-pointer select-none border-b border-gray-100 ${
        isFocused ? 'bg-blue-50 outline outline-2 outline-blue-400' : 'hover:bg-gray-50'
      }`}
      onClick={() => onClick?.(item.id)}
      onDoubleClick={() => onDoubleClick?.(item)}
      onKeyDown={onKeyDown}
      data-file-id={item.id}
    >
      <span className="w-6 text-center" aria-hidden="true">{fileIcon(item)}</span>
      <span className="flex-1 truncate text-sm font-medium">{item.name}</span>
      <span className="w-24 text-right text-xs text-gray-500">{formatFileSize(item.size)}</span>
      <span className="w-28 text-right text-xs text-gray-500">{formatDate(item.updatedAt)}</span>
      <span className="w-20 text-right text-xs text-gray-500 truncate">{item.updatedBy}</span>
    </div>
  )
}
```

- [ ] **Step 2: Verify typecheck**

```bash
cd C:\project\IbizDrive\frontend && npx tsc --noEmit
```

- [ ] **Step 3: Commit**

```bash
git add src/components/files/FileRow.tsx && git commit -m "feat(FileRow): virtualized row with aria-rowindex, roving tabIndex, icon display"
```

---

## Task 9: Create `FileTable` — main component with virtualization + keyboard

**Files:**
- Create: `src/components/files/FileTable.tsx`

This is the core component. It:
1. Uses `useFilesInFolder` to fetch data
2. Routes to the 4 state sub-components based on query state
3. Uses `@tanstack/react-virtual` `useVirtualizer` for virtualization
4. Manages focus index state for keyboard navigation
5. Handles ↑↓ (focus move), Enter (open folder/file), Esc (blur)

- [ ] **Step 1: Create FileTable**

```tsx
// src/components/files/FileTable.tsx
'use client'
import { useRef, useState, useCallback } from 'react'
import { useVirtualizer } from '@tanstack/react-virtual'
import { useRouter } from 'next/navigation'
import { useFilesInFolder } from '@/hooks/useFilesInFolder'
import { useSortParams } from '@/hooks/useSortParams'
import { buildCanonicalPath } from '@/lib/folderPath'
import { FileRow } from './FileRow'
import { FileTableSkeleton } from './FileTableSkeleton'
import { FileTableEmpty } from './FileTableEmpty'
import { FileTableError } from './FileTableError'
import { FileTableForbidden } from './FileTableForbidden'
import type { FileItem } from '@/types/file'

const ROW_HEIGHT = 40
const HEADER_HEIGHT = 36

type Props = {
  folderId: string
}

export function FileTable({ folderId }: Props) {
  const { sort, dir } = useSortParams()
  const { data: items, isLoading, error, refetch } = useFilesInFolder(folderId, sort, dir)
  const [focusedIndex, setFocusedIndex] = useState(-1)
  const parentRef = useRef<HTMLDivElement>(null)
  const router = useRouter()

  const rowCount = items?.length ?? 0

  const virtualizer = useVirtualizer({
    count: rowCount,
    getScrollElement: () => parentRef.current,
    estimateSize: () => ROW_HEIGHT,
    overscan: 10,
  })

  const handleOpen = useCallback(
    (item: FileItem) => {
      if (item.type === 'folder') {
        // Navigate into the folder — M1 routing handles canonical redirect
        router.push(`/files/${item.id}`)
      } else {
        // Open file detail in RightPanel via query param (M6)
        const url = new URL(window.location.href)
        url.searchParams.set('file', item.id)
        router.replace(url.pathname + url.search, { scroll: false })
      }
    },
    [router]
  )

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (!items || items.length === 0) return

      switch (e.key) {
        case 'ArrowDown': {
          e.preventDefault()
          setFocusedIndex((prev) => {
            const next = Math.min(prev + 1, items.length - 1)
            virtualizer.scrollToIndex(next, { align: 'auto' })
            return next
          })
          break
        }
        case 'ArrowUp': {
          e.preventDefault()
          setFocusedIndex((prev) => {
            const next = Math.max(prev - 1, 0)
            virtualizer.scrollToIndex(next, { align: 'auto' })
            return next
          })
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
          parentRef.current?.focus()
          break
        }
      }
    },
    [items, focusedIndex, handleOpen, virtualizer]
  )

  // --- State routing ---
  if (isLoading) return <FileTableSkeleton />

  const status = (error as { status?: number })?.status
  if (status === 403) return <FileTableForbidden />
  if (error) return <FileTableError onRetry={refetch} />
  if (!items || items.length === 0) return <FileTableEmpty />

  return (
    <div className="flex flex-col border rounded-lg overflow-hidden mt-4">
      {/* Column headers — static labels, sort UI deferred */}
      <div
        className="flex items-center gap-4 h-9 px-4 bg-gray-50 border-b text-xs font-medium text-gray-600"
        role="row"
        aria-rowindex={1}
      >
        <span className="w-6" />
        <span className="flex-1">이름</span>
        <span className="w-24 text-right">크기</span>
        <span className="w-28 text-right">수정일</span>
        <span className="w-20 text-right">수정자</span>
      </div>

      {/* Virtualized rows */}
      <div
        ref={parentRef}
        role="grid"
        aria-rowcount={items.length + 1}
        aria-multiselectable={true}
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
                  onClick={() => setFocusedIndex(virtualRow.index)}
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

- [ ] **Step 2: Verify typecheck**

```bash
cd C:\project\IbizDrive\frontend && npx tsc --noEmit
```

- [ ] **Step 3: Commit**

```bash
git add src/components/files/FileTable.tsx && git commit -m "feat(FileTable): virtualized grid with 4 states, keyboard nav, aria (docs/01 §4,§6,§11,§12)"
```

---

## Task 10: Integrate FileTable into ClientFilesPage

**Files:**
- Modify: `src/app/(explorer)/files/[...parts]/ClientFilesPage.tsx`

Replace the placeholder content with `Breadcrumb` + `FileTable`.

- [ ] **Step 1: Update ClientFilesPage**

Replace the entire file content with:

```tsx
// src/app/(explorer)/files/[...parts]/ClientFilesPage.tsx
'use client'
import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { buildCanonicalPath } from '@/lib/folderPath'
import { Breadcrumb } from '@/components/folders/Breadcrumb'
import { FileTable } from '@/components/files/FileTable'

export function ClientFilesPage({ parts }: { parts: string[] }) {
  const router = useRouter()
  const { folderId, folder, isLoading, error } = useCurrentFolder()

  // Canonical redirect
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
      <FileTable folderId={folderId} />
    </div>
  )
}
```

- [ ] **Step 2: Verify typecheck**

```bash
cd C:\project\IbizDrive\frontend && npx tsc --noEmit
```

- [ ] **Step 3: Run dev server and verify visually**

```bash
cd C:\project\IbizDrive\frontend && npm run build
```

Expected: build succeeds with no errors.

Verify manually:
- Visit `/files/root` — should see Breadcrumb + column headers + 5 items (3 files + 2 folders)
- Visit `/files/folder_contracts` — should see 2 contract files
- Visit `/files/folder_hr` — should see empty state ("이 폴더는 비어 있습니다")
- Arrow keys should move focus highlight between rows
- Enter on a folder row should navigate into it
- Esc should clear focus highlight

- [ ] **Step 4: Commit**

```bash
git add src/app/\(explorer\)/files/\[...parts\]/ClientFilesPage.tsx && git commit -m "feat(ClientFilesPage): integrate FileTable into explorer view (M3 complete)"
```

---

## Summary

| Task | What | Files |
|------|------|-------|
| 1 | Install `@tanstack/react-virtual` | `package.json` |
| 2 | `FileItem` + `SortKey` types | `types/file.ts` |
| 3 | Extend query keys | `lib/queryKeys.ts` |
| 4 | Mock data + `getFilesInFolder` | `lib/api.ts` |
| 5 | `useSortParams` hook | `hooks/useSortParams.ts` |
| 6 | `useFilesInFolder` hook | `hooks/useFilesInFolder.ts` |
| 7 | 4 state components | `components/files/` (4 files) |
| 8 | `FileRow` component | `components/files/FileRow.tsx` |
| 9 | `FileTable` (virtualization + keyboard) | `components/files/FileTable.tsx` |
| 10 | Integration into `ClientFilesPage` | `ClientFilesPage.tsx` |

### Deferred to later milestones
- **M4**: Click-to-select, Shift/Ctrl select, BulkActionBar, `useSelectionStore`
- **M6**: RightPanel (`?file=` query param)
- **M10**: F2 rename, Delete, Ctrl+A, `/` search focus
- **M5**: Real API replacing mocks
