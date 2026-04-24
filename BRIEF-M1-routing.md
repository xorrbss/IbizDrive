# 작업 브리프 — M1: folderId 중심 라우팅 + canonical redirect

> **Claude Code에게 넘기는 세션 단위 작업 지시서.**
> 한 세션으로 이 파일만 열고 지시 따라 구현하면 M1 마일스톤이 완료됩니다.

---

## 0. 이 브리프의 사용법

1. 이 파일 전체를 Claude Code 세션 시작 시 열어둡니다.
2. 지시된 문서 섹션만 읽고 구현 (`view` with `view_range`).
3. §5의 완료 기준 전부 체크되어야 M1 완료.
4. 세션 종료 시 §6의 회고 항목을 `docs/progress.md`에 기록.

---

## 1. 목표

사용자가 `/files/folder_abc123` 같은 URL로 폴더에 접근할 때:

- **folderId가 canonical key**로 동작
- 폴더 이름 변경/이동해도 URL 유효
- 잘못된 slug는 정규 URL로 308 redirect
- FolderTree와 Breadcrumb이 URL과 항상 일치

**비범위** (이 마일스톤에서 하지 않음):
- FileTable 구현 (M3)
- 업로드 (M5)
- RightPanel (M6)
- 권한 체크 로직 (M8) — 일단 모든 접근 허용으로 가정

---

## 2. 읽어야 할 설계 섹션

**필수 (이 순서대로):**

1. `docs/00-overview.md` §3 문서 간 계약 지점 (전체)
2. `CLAUDE.md` §3 절대 깨지 않을 핵심 원칙 (전체)
3. `docs/01-frontend-design.md` §2 URL 구조 (라인 범위로 `view` 권장)
4. `docs/01-frontend-design.md` §17.1 ~ §17.9 코드 템플릿
5. `docs/02-backend-data-model.md` §2.3 folders 스키마
6. `docs/02-backend-data-model.md` §7.3 폴더 엔드포인트 부분만

**참고 (필요 시):**

- `docs/01-frontend-design.md` §1.1 진실 출처 규칙
- `docs/02-backend-data-model.md` §3 정규화 함수

**읽지 말 것** (이번 세션 범위 아님):

- 01 §8 이후 (업로드, 검색, 휴지통 등)
- 02 §6 이후 (트랜잭션)
- 03, 04 전체

---

## 3. 작업 목록

### 3.1 프로젝트 셋업 (이미 되어 있으면 스킵)

```bash
pnpm create next-app@latest . --typescript --app --tailwind --eslint
pnpm add zustand @tanstack/react-query @tanstack/react-query-devtools
pnpm add -D @types/node
```

폴더 구조 생성:

```text
src/
  components/
    folders/
    layout/
  hooks/
  lib/
  stores/
  types/
app/
  (explorer)/
    files/
      [...parts]/
        page.tsx
        loading.tsx
        error.tsx
        not-found.tsx
      page.tsx
    layout.tsx
  providers.tsx
  layout.tsx
```

### 3.2 타입 정의

`src/types/folder.ts` 생성:

```ts
export type FolderNode = {
  id: string
  parentId: string | null
  name: string
  slug: string            // NFC 정규화된 URL slug
  children?: FolderNode[]
}

export type BreadcrumbItem = {
  id: string
  name: string
  slugPath: string[]      // ["영업팀", "계약서"]
}

export type FolderDetail = {
  id: string
  name: string
  slugPath: string[]      // 루트에서 현재까지, URL slug용
  breadcrumb: BreadcrumbItem[]
  parentId: string | null
}
```

### 3.3 정규화 유틸

`src/lib/normalize.ts` 생성. **프론트-백엔드 동일 로직** (docs/02 §3.1):

```ts
export function normalizeFileName(s: string): string {
  return s.normalize('NFC').trim()
}

export function normalizedNameForDedup(s: string): string {
  return s.normalize('NFC').toLowerCase().trim()
}

export function normalizeForSearch(s: string): string {
  return s.normalize('NFC').toLowerCase().trim().replace(/\s+/g, ' ')
}
```

### 3.4 쿼리 키 팩토리

`src/lib/queryKeys.ts` 생성. docs/01 §6.1 그대로 (현 시점 필요한 것만):

```ts
export const qk = {
  all: ['explorer'] as const,
  folders: () => [...qk.all, 'folders'] as const,
  folderTree: () => [...qk.folders(), 'tree'] as const,
  folder: (id: string) => [...qk.folders(), 'detail', id] as const,
  effectivePermissions: () => [...qk.all, 'permissions', 'effective'] as const,
} as const
```

### 3.5 canonical path 헬퍼

`src/lib/folderPath.ts` — docs/01 §17.3 반영:

```ts
export function buildCanonicalPath(folderId: string, slugPath: string[]): string {
  const encoded = slugPath.map(encodeURIComponent).join('/')
  return encoded ? `/files/${folderId}/${encoded}` : `/files/${folderId}`
}

export function getFolderIdFromParts(parts: string[] | undefined): string {
  return parts?.[0] ?? 'root'
}
```

### 3.6 API 레이어 (mock 먼저)

백엔드가 아직 없으므로 **mock으로 시작**. 나중에 실제 API 붙일 때 이 파일만 교체.

`src/lib/api.ts`:

```ts
import type { FolderNode, FolderDetail } from '@/types/folder'

// MOCK DATA — 실제 API 붙이면 제거
const MOCK_TREE: FolderNode = {
  id: 'root',
  parentId: null,
  name: '내 드라이브',
  slug: '',
  children: [
    {
      id: 'folder_sales',
      parentId: 'root',
      name: '영업팀',
      slug: '영업팀',
      children: [
        {
          id: 'folder_contracts',
          parentId: 'folder_sales',
          name: '계약서',
          slug: '계약서',
        },
      ],
    },
    {
      id: 'folder_hr',
      parentId: 'root',
      name: '인사팀',
      slug: '인사팀',
    },
  ],
}

function findNodeAndPath(
  node: FolderNode,
  id: string,
  path: FolderNode[] = []
): FolderNode[] | null {
  const next = [...path, node]
  if (node.id === id) return next
  for (const c of node.children ?? []) {
    const r = findNodeAndPath(c, id, next)
    if (r) return r
  }
  return null
}

export const api = {
  async getFolderTree(): Promise<FolderNode> {
    await new Promise((r) => setTimeout(r, 100))
    return MOCK_TREE
  },

  async getFolder(id: string): Promise<FolderDetail> {
    await new Promise((r) => setTimeout(r, 100))
    const chain = findNodeAndPath(MOCK_TREE, id)
    if (!chain) throw { status: 404, code: 'NOT_FOUND' }
    const self = chain[chain.length - 1]
    const slugPath = chain.slice(1).map((n) => n.slug) // root 제외
    return {
      id: self.id,
      name: self.name,
      slugPath,
      breadcrumb: chain.map((n, i) => ({
        id: n.id,
        name: n.name,
        slugPath: chain.slice(1, i + 1).map((x) => x.slug),
      })),
      parentId: self.parentId,
    }
  },
}
```

### 3.7 Providers

`app/providers.tsx` — docs/01 §17.9 반영:

```tsx
'use client'
import { QueryClient, QueryClientProvider, QueryCache } from '@tanstack/react-query'
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'
import { useState } from 'react'
import { qk } from '@/lib/queryKeys'

export function Providers({ children }: { children: React.ReactNode }) {
  const [client] = useState(() => {
    const c = new QueryClient({
      defaultOptions: {
        queries: {
          staleTime: 30_000,
          refetchOnWindowFocus: true,
          retry: (count, err: any) =>
            err?.status !== 401 && err?.status !== 403 && count < 2,
        },
      },
      queryCache: new QueryCache({
        onError: (err: any) => {
          if (err?.status === 403) {
            c.invalidateQueries({ queryKey: qk.effectivePermissions() })
            console.warn('권한이 없거나 접근이 제한되었습니다')
          }
        },
      }),
    })
    return c
  })

  return (
    <QueryClientProvider client={client}>
      {children}
      <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
  )
}
```

### 3.8 훅

`src/hooks/useFolderTree.ts`:

```ts
import { useQuery } from '@tanstack/react-query'
import { qk } from '@/lib/queryKeys'
import { api } from '@/lib/api'

export function useFolderTree() {
  return useQuery({
    queryKey: qk.folderTree(),
    queryFn: api.getFolderTree,
    staleTime: 60_000,
    gcTime: 10 * 60_000,
  })
}
```

`src/hooks/useCurrentFolder.ts` — docs/01 §17.4:

```ts
'use client'
import { useParams } from 'next/navigation'
import { useQuery } from '@tanstack/react-query'
import { qk } from '@/lib/queryKeys'
import { api } from '@/lib/api'
import { getFolderIdFromParts } from '@/lib/folderPath'

export function useCurrentFolder() {
  const params = useParams<{ parts?: string[] }>()
  const folderId = getFolderIdFromParts(params.parts)
  const { data, isLoading, error } = useQuery({
    queryKey: qk.folder(folderId),
    queryFn: () => api.getFolder(folderId),
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

### 3.9 뷰 스토어 (트리 확장 상태만)

`src/stores/view.ts` — docs/01 §5.2 일부:

```ts
import { create } from 'zustand'
import { persist } from 'zustand/middleware'

type ViewState = {
  expandedFolderIds: string[]
  toggleExpanded: (id: string) => void
}

export const useViewStore = create<ViewState>()(
  persist(
    (set) => ({
      expandedFolderIds: ['root'],
      toggleExpanded: (id) =>
        set((s) => ({
          expandedFolderIds: s.expandedFolderIds.includes(id)
            ? s.expandedFolderIds.filter((x) => x !== id)
            : [...s.expandedFolderIds, id],
        })),
    }),
    { name: 'explorer-view' }
  )
)
```

### 3.10 FolderTree

`src/components/folders/FolderTree.tsx` — docs/01 §17.6:

전체 코드를 01 §17.6에서 복사. 주의점:
- `useCurrentFolder`의 `folderId`를 `activeId`로 사용
- `buildCanonicalPath`로 href 생성
- 트리 확장 상태는 `useViewStore`

### 3.11 Breadcrumb

`src/components/folders/Breadcrumb.tsx` — docs/01 §17.7 그대로.

### 3.12 Catch-all 라우트 + canonical redirect

`app/(explorer)/files/[...parts]/page.tsx` — docs/01 §17.2 **단, 아래 차이 주의**:

01 §17.2는 서버 컴포넌트에서 `await api.getFolder()` 호출하는 예시입니다. mock API는 클라이언트 전용이므로 이 단계에서는 **클라이언트 컴포넌트로 단순화**:

```tsx
// app/(explorer)/files/[...parts]/page.tsx
import { ClientFilesPage } from './ClientFilesPage'

export default function FilesPage({ params }: { params: { parts: string[] } }) {
  return <ClientFilesPage parts={params.parts} />
}
```

```tsx
// app/(explorer)/files/[...parts]/ClientFilesPage.tsx
'use client'
import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { buildCanonicalPath } from '@/lib/folderPath'
import { Breadcrumb } from '@/components/folders/Breadcrumb'

export function ClientFilesPage({ parts }: { parts: string[] }) {
  const router = useRouter()
  const { folder, isLoading, error } = useCurrentFolder()

  // Canonical redirect
  useEffect(() => {
    if (!folder) return
    const canonical = buildCanonicalPath(folder.id, folder.slugPath)
    const current = `/files/${parts.join('/')}`
    // decode 후 비교 (한글 URL)
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
      <h1 className="text-2xl font-bold mt-4">{folder.name}</h1>
      <p className="text-sm text-muted-foreground mt-2">
        folderId: {folder.id}
      </p>
    </div>
  )
}
```

> **추후 M3에서**: 서버 컴포넌트 + `notFound()` + `redirect()` 조합으로 전환. 지금은 mock 때문에 클라이언트로 단순화.

### 3.13 `/files` 루트 리다이렉트

`app/(explorer)/files/page.tsx`:

```tsx
import { redirect } from 'next/navigation'
export default function FilesRootPage() {
  redirect('/files/root')
}
```

### 3.14 Explorer 레이아웃

`app/(explorer)/layout.tsx`:

```tsx
import { FolderTree } from '@/components/folders/FolderTree'

export default function ExplorerLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex h-screen">
      <aside className="w-64 border-r p-4 overflow-y-auto">
        <FolderTree />
      </aside>
      <main className="flex-1 p-6 overflow-y-auto">{children}</main>
    </div>
  )
}
```

### 3.15 루트 레이아웃

`app/layout.tsx`에 `<Providers>` 감싸기:

```tsx
import { Providers } from './providers'
import './globals.css'

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  )
}
```

### 3.16 loading / error / not-found

`app/(explorer)/files/[...parts]/loading.tsx`:

```tsx
export default function Loading() {
  return <div className="p-6">폴더 불러오는 중...</div>
}
```

`app/(explorer)/files/[...parts]/error.tsx`:

```tsx
'use client'
export default function Error({ error, reset }: { error: Error; reset: () => void }) {
  return (
    <div className="p-6">
      <h2 className="text-lg font-semibold">문제가 발생했습니다</h2>
      <p className="text-sm text-muted-foreground mt-2">{error.message}</p>
      <button onClick={reset} className="mt-4 px-3 py-1 border rounded">
        다시 시도
      </button>
    </div>
  )
}
```

`app/(explorer)/files/[...parts]/not-found.tsx`:

```tsx
export default function NotFound() {
  return (
    <div className="p-6">
      <h2 className="text-lg font-semibold">폴더를 찾을 수 없습니다</h2>
    </div>
  )
}
```

---

## 4. 핵심 원칙 체크 (구현 중 지속 확인)

- [ ] `folderId`를 Zustand에 넣지 않았는가? URL/React Query에서만 조회하는가?
- [ ] Breadcrumb은 자체 상태 없이 `useCurrentFolder`에서 derive하는가?
- [ ] FolderTree의 active 판단이 URL `folderId`로만 되는가?
- [ ] canonical redirect가 decodeURI 비교를 쓰는가? (한글 URL 인코딩 차이 방지)
- [ ] 정규화 함수가 `docs/02 §3.1`과 **동일한 로직**인가?

---

## 5. 완료 기준

모두 체크되면 M1 완료:

- [ ] `/files` 접속 → `/files/root` 리다이렉트
- [ ] `/files/folder_sales` 접속 → `/files/folder_sales/영업팀` 리다이렉트 (canonical)
- [ ] `/files/folder_contracts` 접속 → `/files/folder_contracts/영업팀/계약서` 리다이렉트
- [ ] `/files/folder_sales/잘못된이름` 접속 → `/files/folder_sales/영업팀` 리다이렉트
- [ ] `/files/nonexistent_id` 접속 → 에러 표시 (404 처리는 M3에서 완성)
- [ ] FolderTree 클릭 시 URL 변경 + active highlight 이동
- [ ] Breadcrumb 중간 항목 클릭 시 해당 폴더로 이동
- [ ] 새로고침 시 현재 폴더 복원
- [ ] 트리 펼침/접힘 상태가 새로고침 후 유지 (localStorage)
- [ ] `pnpm typecheck` 통과
- [ ] `pnpm lint` 통과
- [ ] 한글 폴더명 URL 정상 동작 (인코딩/디코딩 이슈 없음)

---

## 6. 세션 종료 시 회고 (progress.md에 기록)

아래 양식으로 `docs/progress.md` 상단에 추가:

```markdown
## YYYY-MM-DD — M1 완료

### 완료
- [M1] folderId 중심 catch-all 라우팅
- [M1] FolderTree / Breadcrumb URL 동기화
- [M1] canonical redirect
- [M1] 프로젝트 기본 셋업 (Providers, 훅, 스토어)

### 계약 파일 추가
- src/lib/normalize.ts      (docs/02 §3)
- src/lib/queryKeys.ts       (docs/01 §6.1)
- src/lib/folderPath.ts      (docs/01 §17.3)
- src/lib/api.ts             (MOCK — M5에서 실제 API로 교체)

### 다음 세션 컨텍스트 (M2: FolderTree 심화 + TrashLink + QuickAccess 또는 M3: FileTable)
- api.ts는 현재 mock. 백엔드 나오면 실제 fetch로 교체. 계약은 docs/02 §7.3
- 서버 컴포넌트 전환은 M3에서 (notFound/redirect 조합)
- canonical redirect는 클라이언트에서 useEffect. 깜빡임 있으면 M3에서 서버 redirect로

### 블로커
- 없음 / (있으면 명시)

### 설계 문서 업데이트 필요
- 없음 / (있으면 명시)
```

---

## 7. 자주 발생하는 함정

1. **한글 URL 인코딩 비교**: `current !== canonical`로 바로 비교하면 인코딩 차이로 무한 redirect. 반드시 `decodeURI` 비교.
2. **Zustand에 folderId 유혹**: "여러 곳에서 쓰니까 그냥 store에 넣자" → 절대 금지 (원칙 #1).
3. **FolderTree가 router.push 사용**: `<Link>` 컴포넌트 사용해야 prefetch 동작. 프로그래매틱 네비게이션 금지.
4. **Breadcrumb을 URL parts에서 직접 생성**: `params.parts`는 slug 배열인데 이름이 아님. 반드시 API 응답의 `breadcrumb` 사용.
5. **expandedFolderIds 초기값**: persisted 상태이므로 hydration mismatch 주의. Next.js에서는 `useEffect`로 client-only 렌더링 또는 `skipHydration`.

---

## 8. 참고: 다음 마일스톤 미리보기

M1 완료 후 다음 후보 (docs/01 §18):

- **M2**: Sidebar 확장 (QuickAccess, TrashLink 자리 잡기)
- **M3**: FileTable (가상화 + 4가지 상태). 서버 컴포넌트 전환도 이때.
- **M4**: 선택 모델 + BulkActionBar
- 이후는 백엔드 API가 실제로 붙은 후 진행

사용자 지시에 따라 다음 브리프 작성.
