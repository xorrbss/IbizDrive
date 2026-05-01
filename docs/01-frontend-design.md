# 파일 탐색기 UI - 통합 설계 문서 v3

> v2에서 지적된 **URL 안정성(folderId 중심), 라우팅 일관성(RightPanel query param), 백엔드 권한 계약, 버전 규칙, 낙관적 업데이트 정책, 한글 정규화, 검색 견고성** 반영.
> 스택: **Next.js 15 (App Router) + TypeScript + Zustand + TanStack Query v5 + dnd-kit + TanStack Virtual**

---

## 0. v2 → v3 변경 요약

| 영역 | v2 | v3 |
|---|---|---|
| **URL 키** | slug path (`/files/[[...path]]`) | **folderId 중심 hybrid** (`/files/[...parts]`, parts[0]=folderId) |
| **RightPanel** | parallel route `@rightPanel/[fileId]` | **query param `?file=xxx`** 로 통일 |
| **권한 원칙** | 프론트 조건부 렌더링만 | **백엔드 검증이 최종, 프론트는 UX용**임을 원칙 명시 |
| **버전 생성 규칙** | 충돌 다이얼로그만 | **folder + normalized name = 동일 파일** 계약 명시 |
| **낙관적 업데이트** | 모든 mutation에 적용 암묵 | **파괴적 액션(이동/삭제/권한) 금지, 비파괴적(rename/upload)만** |
| **캐시 무효화 파급** | 권한 변경 → permissions만 | 권한 변경 → **FolderTree + 하위 리스트 + 버튼 가시성** 전부 |
| **문자열 정규화** | - | **NFC 정규화 + encodeURIComponent + 검색 normalize 일치** |
| **검색 견고성** | SearchBar 존재만 | **debounce 300ms + AbortController + placeholderData + 최소 2자** |

> **MVP 범위**: 업로드는 multipart로 시작, 실시간은 폴링으로 시작. tus/SSE는 v1.x 로드맵. (도메인이 대용량/컴플라이언스면 MVP에 포함 가능 — 15절 참조)

---

## 1. 최상위 설계 원칙 (v3에서 새로 명시)

### 1.1 진실 출처 규칙

```text
현재 "어디"를 보는가 → URL  (folderId가 canonical key)
현재 "무엇"을 하는가 → Zustand (선택, 드래그, 업로드 큐)
"사실" 그 자체       → TanStack Query (서버 데이터)
```

절대로 서버 데이터를 Zustand에 복제하지 않음. 절대로 URL 정보를 Zustand에 복제하지 않음.

### 1.2 권한 검증 계약

```text
프론트의 usePermission은 UX용이다.
    → 버튼 숨김/비활성, 컨텍스트 메뉴 구성, 프리체크에만 사용.

백엔드는 모든 민감 엔드포인트에서 최종 권한을 재검증한다.
    → 파일 조회/다운로드/업로드/이동/삭제/권한 변경/버전 조회 전부.
    → 프론트가 "버튼을 숨겼으니 괜찮다"는 건 보안 논리가 아니다.

403 응답은 프론트의 일급 에러 상태이다.
    → 낙관적 업데이트 롤백, 토스트, 상태 재동기화.
```

### 1.3 낙관적 업데이트 정책

```text
✅ 허용: 비파괴적 액션
    - 이름 변경
    - 새 파일 업로드 성공 후 리스트 prepend
    - 폴더 생성
    - 즐겨찾기 토글

❌ 금지: 파괴적/권한성 액션
    - 파일/폴더 이동  (다른 폴더로 사라짐)
    - 삭제 (휴지통 이동도 포함)
    - 권한 변경 (다른 사용자 영향)
    - 새 버전 업로드 (이전 버전 참조가 바뀜)

금지된 액션은 "pending" 로딩 상태로 UX 처리.
    → 체크박스 opacity 낮춤, 행에 spinner, 다른 액션 잠금.
```

### 1.4 파일 동일성 규칙

```text
같은 folderId + normalizedFileName = 동일 파일

normalizedFileName = NFC.normalize(name).trim().toLowerCase()

새 업로드 시:
  - 동일 파일 존재 X → files 테이블에 새 row 생성
  - 동일 파일 존재 O → ConflictDialog로 사용자 선택
      - 새 버전    → file_versions에 row 추가, files.current_version_id 갱신
      - 이름 변경  → 새 files row (name에 " (2)" 등)
      - 건너뛰기   → 업로드 스킵

이전 버전은 절대 삭제하지 않음 (휴지통도 이전 버전은 유지).
파일 "이름 변경"은 files.name 변경 (버전과 무관).
특정 버전을 식별하려면 version_id 명시.
```

---

## 2. URL 구조 (v3 핵심 변경)

### 2.1 패턴: `[...parts]` catch-all (folderId + slug hybrid)

```text
/files/[...parts]
    parts[0]      = folderId    (canonical key, 조회에 사용)
    parts[1..]    = slug path   (표시용, 가독성/공유용)

예시:
    /files/root
    /files/folder_abc123
    /files/folder_abc123/영업팀/계약서
```

### 2.2 URL 검증 및 canonical redirect

```text
요청: /files/folder_abc123/잘못된이름

1. folderId=folder_abc123 로 폴더 조회 → 실제 path = "영업팀/계약서"
2. URL slug ≠ 실제 path → 308 redirect → /files/folder_abc123/영업팀/계약서
3. folderId 자체가 없거나 권한 없음 → 404 / 403

이로써:
  ✅ 폴더 이름 변경해도 URL 유효 (folderId가 안정 키)
  ✅ 링크 공유 시 가독성 있는 URL
  ✅ 동일 이름 폴더 충돌 없음
  ✅ 이동해도 URL 유효 (slug만 갱신)
```

### 2.3 URL 상태 맵

| 상태 | 위치 | 예시 |
|---|---|---|
| 현재 폴더 | URL path parts[0] | `/files/folder_abc123/...` |
| 표시용 slug | URL path parts[1..] | `/영업팀/계약서` |
| 열린 파일 상세 | URL query `?file=` | `?file=file_xyz789` |
| 검색어 | URL query `?q=` | `?q=2025계약` |
| 정렬 | URL query `?sort=&dir=` | `?sort=modifiedAt&dir=desc` |
| 필터 | URL query `?type=&owner=` | `?type=pdf&owner=me` |
| 선택된 파일 | Zustand (휘발성) | - |
| 뷰 모드 | localStorage + Zustand | - |
| 트리 확장 상태 | localStorage + Zustand | - |
| 업로드 큐 | Zustand (세션 휘발성) | - |

### 2.4 한글/유니코드 정규화 정책

```text
표준: NFC (Normalization Form C)

URL encoding:
  - encodeURIComponent 사용 (기본)
  - 단, slug는 / 로 구분되므로 segment 단위로 인코딩

DB 저장:
  - files.name: NFC로 정규화하여 저장
  - files.normalized_name: 추가 컬럼, 중복 검사용 (NFC + lowercase + trim)

검색 매칭:
  - 쿼리와 대상 모두 같은 normalize 함수 통과
  - 백엔드/프론트 normalize 함수 동기화 필수

주의:
  - macOS(NFD) vs Windows/Linux(NFC) 차이로 같아 보여도 다른 문자열일 수 있음
  - 업로드 시 서버에서 NFC 강제 변환
```

---

## 3. Next.js App Router 폴더 구조

```text
app/
├─ layout.tsx                        # 루트 레이아웃 + Providers
├─ providers.tsx                     # QueryClient, DndContext, Zustand hydration
├─ (explorer)/
│  ├─ layout.tsx                     # <AppLayout> (TopBar + Sidebar + 컨텐츠)
│  ├─ files/
│  │  ├─ page.tsx                    # /files → redirect("/files/root")
│  │  └─ [...parts]/
│  │     ├─ page.tsx                 # 폴더 뷰 + canonical redirect 처리
│  │     ├─ loading.tsx              # <FileTableSkeleton />
│  │     ├─ error.tsx                # <FileTableError />
│  │     └─ not-found.tsx
│  ├─ trash/
│  │  └─ page.tsx                    # <TrashView />
│  ├─ shares/
│  │  └─ page.tsx                    # 받은 공유 (F4, with-me)
│  └─ search/
│     └─ page.tsx                    # ?q=... 검색 결과
└─ api/
   └─ (proxy routes)

# ❌ @rightPanel parallel route 제거됨.
# RightPanel은 ContentArea 내부 컴포넌트로, ?file= query param을 구독.

src/
├─ components/
│  ├─ layout/         (AppLayout, TopBar, Sidebar, ContentArea)
│  ├─ files/          (FileTable, FileRow, FileContextMenu, BulkActionBar)
│  ├─ folders/        (FolderTree, FolderNode, Breadcrumb)
│  ├─ upload/         (UploadOverlay, UploadQueue, UploadItem, DropZone)
│  ├─ detail/         (RightPanel, VersionList, ActivityTimeline)
│  ├─ permission/     (PermissionModal, PermissionTable)
│  ├─ search/         (SearchBar, SearchResults)
│  ├─ empty/          (EmptyFolder, EmptyTrash, EmptySearch)
│  └─ ui/             (primitives)
├─ stores/
│  ├─ selection.ts    (선택 슬라이스)
│  ├─ view.ts         (뷰 모드, 정렬, 트리 확장)
│  ├─ upload.ts       (업로드 큐)
│  └─ dnd.ts          (드래그 상태)
├─ hooks/
│  ├─ useCurrentFolder.ts   (URL → folderId + 검증)
│  ├─ useOpenFile.ts        (?file= 동기화)
│  ├─ useFiles.ts
│  ├─ useFolderTree.ts
│  ├─ useFileDetail.ts
│  ├─ useVersions.ts
│  ├─ useUpload.ts
│  ├─ usePermission.ts
│  ├─ useKeyboardNav.ts
│  ├─ useRealtimeSync.ts
│  └─ useSearch.ts
├─ lib/
│  ├─ queryKeys.ts          (중앙 쿼리 키 팩토리)
│  ├─ api/                  (fetch wrappers, AbortController 지원)
│  ├─ normalize.ts          (NFC 정규화, search normalize)
│  ├─ folderPath.ts         (canonical URL 생성)
│  └─ permissions.ts        (can() 헬퍼)
└─ types/
   ├─ file.ts
   ├─ folder.ts
   └─ permission.ts
```

---

## 4. 컴포넌트 트리 (v3 정비)

```text
<AppLayout>
 ├─ <TopBar>
 │   ├─ <GlobalSearch />
 │   └─ <UserMenu />
 ├─ <MainArea>
 │   ├─ <Sidebar>
 │   │   ├─ <QuickAccess />
 │   │   ├─ <FolderTree />      ← URL folderId에서 active 판단
 │   │   └─ <TrashLink />
 │   └─ <ContentArea>
 │       ├─ <Breadcrumb />      ← folder API data에서 derive
 │       ├─ <Toolbar />
 │       ├─ <BulkActionBar />   ← 선택 > 0일 때만
 │       ├─ <FileTable>
 │       │   ├─ <FileTableSkeleton />
 │       │   ├─ <FileTableEmpty />
 │       │   ├─ <FileTableError />
 │       │   ├─ <FileTableForbidden />
 │       │   └─ <VirtualizedFileRows>
 │       │       └─ <FileRow />  ← draggable + drop target
 │       ├─ <RightPanel />      ← ?file= 있을 때만
 │       └─ <StatusBar />
 ├─ <UploadOverlay />           ← window 네이티브 drop
 ├─ <UploadQueueDock />         ← 페이지 이동해도 유지
 └─ <GlobalDialogs>
     ├─ <UploadConflictDialog />
     ├─ <RenameDialog />
     ├─ <MoveDialog />
     ├─ <PermissionModal />
     └─ <DeleteConfirmDialog />
```

---

## 5. Zustand 슬라이스 설계

### 5.1 Selection slice

```ts
// stores/selection.ts
import { create } from 'zustand'

type SelectionState = {
  ids: Set<string>
  lastClickedId: string | null
  pendingIds: Set<string>       // 파괴적 액션 진행 중 (이동/삭제)
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
  toggle: (id) => set((s) => {
    const next = new Set(s.ids)
    next.has(id) ? next.delete(id) : next.add(id)
    return { ids: next, lastClickedId: id }
  }),
  selectRange: (to, orderedIds) => {
    const { lastClickedId, ids } = get()
    if (!lastClickedId) return set({ ids: new Set([to]), lastClickedId: to })
    const a = orderedIds.indexOf(lastClickedId)
    const b = orderedIds.indexOf(to)
    const [start, end] = a < b ? [a, b] : [b, a]
    const next = new Set(ids)
    orderedIds.slice(start, end + 1).forEach((id) => next.add(id))
    set({ ids: next })
  },
  selectOnly: (id) => set({ ids: new Set([id]), lastClickedId: id }),
  clear: () => set({ ids: new Set(), lastClickedId: null }),
  selectAll: (ids) => set({ ids: new Set(ids) }),
  markPending: (ids) => set((s) => ({
    pendingIds: new Set([...s.pendingIds, ...ids])
  })),
  unmarkPending: (ids) => set((s) => {
    const next = new Set(s.pendingIds)
    ids.forEach((id) => next.delete(id))
    return { pendingIds: next }
  }),
}))
```

> **구현 노트 (M4, 2026-04-25)**
>
> 위 코드 shape는 그대로 유지하되 다음 규칙이 store 내부에서 강제된다:
> - `markPending(ids)`는 해당 id들을 `ids`(selected)에서도 제거한다 (pending↔selected 상호 배제)
> - `selectRange`는 앵커가 없거나 / pending이거나 / 현재 폴더에 없을 때 단일 선택으로 폴백
> - `selectRange` 범위 내 pending은 선택에서 제외
>
> 상세: `docs/superpowers/specs/2026-04-25-m4-selection-bulkactionbar-design.md` §2.1, §2.2


### 5.2 View slice (persisted)

```ts
// stores/view.ts
import { create } from 'zustand'
import { persist } from 'zustand/middleware'

type ViewState = {
  mode: 'list' | 'grid'
  density: 'comfortable' | 'compact'
  expandedFolderIds: string[]
  setMode: (m: 'list' | 'grid') => void
  toggleExpanded: (id: string) => void
}

export const useViewStore = create<ViewState>()(
  persist(
    (set) => ({
      mode: 'list',
      density: 'comfortable',
      expandedFolderIds: ['root'],
      setMode: (mode) => set({ mode }),
      toggleExpanded: (id) => set((s) => ({
        expandedFolderIds: s.expandedFolderIds.includes(id)
          ? s.expandedFolderIds.filter((x) => x !== id)
          : [...s.expandedFolderIds, id]
      })),
    }),
    { name: 'explorer-view' }
  )
)
```

### 5.3 Upload slice

```ts
// stores/upload.ts
type UploadTask = {
  id: string
  file: File
  targetFolderId: string
  status: 'queued' | 'uploading' | 'paused' | 'done' | 'failed' | 'conflict'
  progress: number       // 0..1
  uploadedBytes: number
  tusUrl?: string        // v1.x 재개용
  error?: {
    kind: 'network' | 'permission' | 'quota' | 'server' | 'conflict'
    message: string
  }
  conflictWith?: { fileId: string; fileName: string }
  conflictResolution?: 'overwrite' | 'rename' | 'skip' | 'new_version'
}

type UploadState = {
  queue: UploadTask[]
  applyToAll: UploadTask['conflictResolution'] | null
  enqueue: (files: File[], targetFolderId: string) => void
  updateTask: (id: string, patch: Partial<UploadTask>) => void
  resolveConflict: (id: string, resolution: UploadTask['conflictResolution'], applyToAll?: boolean) => void
  retry: (id: string) => void
  cancel: (id: string) => void
  clearDone: () => void
}
```

#### M5 구현 노트 (2026-04-25)

§5.3 스펙 대비 차이 (M5 MVP):
- `status: 'paused'` 제외 (tus는 M5.1로 미룸)
- `tusUrl?: string` 제외 (M5.1)
- `conflictResolution: 'overwrite'` 제외 — 파괴적이므로 정책상 `new_version`으로 대체
- `pendingCount()` selector 추가: `queued | uploading | conflict` 을 포함 (beforeunload 경고 대상)
- `enqueue` 반환값: 생성된 task ids (훅이 task별 XHR 기동에 사용)
- `applyToAll`은 store 전역, `clearDone` 호출 시점에 null 리셋 (배치별 스코프가 아님)
- `cancel(id)`은 `failed` + `error.kind='network'` + `message='취소됨'`으로 전환 (별도 `canceled` 상태 도입 안 함)
- `rename` 충돌 해결은 단일 시도 — 서버 정규화 결과 추측 금지(원칙 #6). 재차 409 시 dialog 재표시

### 5.4 DnD slice

```ts
// stores/dnd.ts
type DnDState = {
  draggingIds: string[] | null
  dropTargetId: string | null
  setDragging: (ids: string[] | null) => void
  setDropTarget: (id: string | null) => void
}
```

---

## 6. TanStack Query 캐시 키 + 무효화 매트릭스

### 6.1 쿼리 키 팩토리

```ts
// lib/queryKeys.ts
export const qk = {
  all: ['explorer'] as const,

  folders: () => [...qk.all, 'folders'] as const,
  folderTree: () => [...qk.folders(), 'tree'] as const,
  folder: (id: string) => [...qk.folders(), 'detail', id] as const,
  folderPath: (id: string) => [...qk.folders(), 'path', id] as const,  // breadcrumb용

  files: () => [...qk.all, 'files'] as const,
  filesInFolder: (folderId: string, sort: SortKey, dir: 'asc' | 'desc') =>
    [...qk.files(), 'list', folderId, sort, dir] as const,
  fileDetail: (id: string) => [...qk.files(), 'detail', id] as const,
  versions: (fileId: string) => [...qk.files(), 'versions', fileId] as const,
  activity: (fileId: string) => [...qk.files(), 'activity', fileId] as const,

  permissions: (nodeId: string) => [...qk.all, 'permissions', nodeId] as const,
  effectivePermissions: () => [...qk.all, 'permissions', 'effective'] as const,

  search: (q: string, filters: Filters) => [...qk.all, 'search', q, filters] as const,
  trash: () => [...qk.all, 'trash'] as const,

  // F4: shares (by-me/with-me)
  shares: () => [...qk.all, 'shares'] as const,
  sharesByMe: () => [...qk.shares(), 'by-me'] as const,
  sharesWithMe: () => [...qk.shares(), 'with-me'] as const,
} as const
```

### 6.2 무효화 매트릭스 (v3 파급 범위 반영)

| 액션 | 낙관적 업데이트 | invalidate |
|---|---|---|
| **파일 업로드 성공** | `filesInFolder(target)` prepend | `folderTree()` |
| **파일 이름 변경** | `fileDetail(id)`, 리스트 내 아이템 | - |
| **폴더 생성** | `folderTree()` 노드 추가 | - |
| **파일 이동** | ❌ (파괴적) | 완료 후: `filesInFolder(from)`, `filesInFolder(to)`, `folderTree()`, `fileDetail(id)` |
| **파일 삭제 (휴지통)** | ❌ (파괴적) | 완료 후: `filesInFolder(from)`, `trash()`, `folderTree()` |
| **휴지통 복원** | ❌ | 완료 후: `trash()`, `filesInFolder(restored.parent)`, `folderTree()` |
| **영구 삭제** | ❌ | 완료 후: `trash()` |
| **폴더 삭제** | ❌ | 완료 후: `folderTree()`, 하위 `filesInFolder(*)` `removeQueries`, `trash()` |
| **권한 변경** | ❌ | **광역 무효화** (6.3절 참조) |
| **새 버전 업로드** | ❌ (current_version_id 바뀜) | 완료 후: `versions(fileId)`, `fileDetail(id)`, `activity(id)` |
| **즐겨찾기 토글** | 리스트 아이템 플래그 | `quickAccess()` |
| **공유 생성 (F4)** | ❌ | 완료 후: `shares()` (by-me/with-me 동시) |
| **공유 해제 (F4)** | ❌ | 완료 후: `shares()` (by-me/with-me 동시) |
| **SSE 이벤트 수신** | 이벤트 타입별 처리 | 폴백: 관련 리스트 invalidate |

### 6.3 권한 변경 시 광역 무효화

권한 변경은 UI 전반에 파급되므로 **무효화 범위가 넓다.**

```ts
// hooks/usePermissionMutation.ts
export function useChangePermission() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: api.changePermission,
    onSuccess: (_, { nodeId }) => {
      // 1. 해당 노드 권한 (Permission 모달용)
      qc.invalidateQueries({ queryKey: qk.permissions(nodeId) })

      // 2. 유효 권한 캐시 (usePermission 훅)
      qc.invalidateQueries({ queryKey: qk.effectivePermissions() })

      // 3. FolderTree (안 보이던 폴더 보이거나, 보이던 폴더 사라짐)
      qc.invalidateQueries({ queryKey: qk.folderTree() })

      // 4. 현재 폴더 리스트 (읽기 권한 변경 시 일부 파일이 사라지거나 나타남)
      qc.invalidateQueries({ queryKey: qk.files() })

      // 5. 파일 상세 (공유 대상 목록 갱신)
      qc.invalidateQueries({ queryKey: qk.fileDetail(nodeId) })
    }
  })
}
```

### 6.4 403 응답의 전역 처리

```ts
// app/providers.tsx 내부
queryCache: new QueryCache({
  onError: (err, query) => {
    if (isForbiddenError(err)) {
      // 권한이 바뀐 것일 수 있으므로 유효 권한 재조회
      client.invalidateQueries({ queryKey: qk.effectivePermissions() })
      toast.error('권한이 없거나 접근이 제한되었습니다')
    }
  }
})
```

---

## 7. DnD 설계 (업로드 vs 이동 분리)

두 개의 DnD 컨텍스트는 **이벤트 소스가 다름**:
- **OS → 브라우저** (업로드): 네이티브 `dragenter/dragover/drop`, `e.dataTransfer.types.includes('Files')`
- **브라우저 내부** (이동): dnd-kit 이벤트

```tsx
// components/layout/AppLayout.tsx
export function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <DndContext
      onDragStart={handleMoveStart}
      onDragEnd={handleMoveEnd}
      onDragCancel={handleMoveCancel}
    >
      <UploadOverlay />   {/* window 네이티브 drop */}
      <TopBar />
      <div className="flex">
        <Sidebar />        {/* FolderNode = dnd-kit droppable */}
        <ContentArea>{children}</ContentArea>
      </div>
      <UploadQueueDock />
      <GlobalDialogs />
      <DragOverlay>{/* 드래그 미리보기 */}</DragOverlay>
    </DndContext>
  )
}
```

---

## 8. 벌크 선택 & 액션 바

### 8.1 키맵

| 키 | 동작 |
|---|---|
| 클릭 | `selectOnly(id)` |
| Ctrl/Meta + 클릭 | `toggle(id)` |
| Shift + 클릭 | `selectRange(id, orderedIds)` |
| Ctrl/Meta + A | 전체 선택 |
| Esc | 선택 해제 |

### 8.2 BulkActionBar

```tsx
// components/files/BulkActionBar.tsx
export function BulkActionBar() {
  const count = useSelectionStore((s) => s.ids.size)
  const ids = useSelectionStore((s) => [...s.ids])
  const can = usePermission()
  const { clear } = useSelectionStore()

  if (count === 0) return null

  return (
    <div className="sticky top-[56px] z-20 flex items-center gap-2 bg-background border-b px-4 py-2">
      <span className="text-sm">{count}개 선택</span>
      {can.download && <Button onClick={() => downloadBulk(ids)}>다운로드</Button>}
      {can.move     && <Button onClick={() => openMoveDialog(ids)}>이동</Button>}
      {can.delete   && <Button variant="destructive" onClick={() => deleteBulk(ids)}>휴지통으로</Button>}
      <Button variant="ghost" onClick={clear}>선택 해제</Button>
    </div>
  )
}
```

---

## 9. 업로드 (MVP: multipart, v1.x: tus)

### 9.1 MVP 구현 (multipart + 진행률)

```ts
// hooks/useUpload.ts
export function useUpload() {
  const updateTask = useUploadStore((s) => s.updateTask)

  const start = (task: UploadTask) => {
    const form = new FormData()
    form.append('file', task.file)
    form.append('folderId', task.targetFolderId)

    const xhr = new XMLHttpRequest()
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) {
        updateTask(task.id, {
          progress: e.loaded / e.total,
          uploadedBytes: e.loaded,
          status: 'uploading',
        })
      }
    }
    xhr.onload = () => {
      if (xhr.status === 200) {
        updateTask(task.id, { status: 'done', progress: 1 })
      } else if (xhr.status === 409) {
        // 서버가 감지한 이름 충돌
        const info = JSON.parse(xhr.responseText)
        updateTask(task.id, {
          status: 'conflict',
          conflictWith: info.existing,
        })
      } else {
        updateTask(task.id, { status: 'failed', error: classifyError(xhr) })
      }
    }
    xhr.onerror = () => updateTask(task.id, {
      status: 'failed',
      error: { kind: 'network', message: '네트워크 연결을 확인하세요' }
    })

    xhr.open('POST', '/api/files/upload')
    xhr.send(form)
  }

  return { start }
}

function classifyError(xhr: XMLHttpRequest): UploadTask['error'] {
  if (xhr.status === 403) return { kind: 'permission', message: '업로드 권한이 없습니다' }
  if (xhr.status === 413) return { kind: 'quota', message: '용량 한도를 초과했습니다' }
  if (xhr.status >= 500)  return { kind: 'server', message: '서버 오류가 발생했습니다' }
  return { kind: 'server', message: `오류 (${xhr.status})` }
}
```

### 9.2 충돌 다이얼로그

```tsx
<UploadConflictDialog>
  "document.pdf"가 이미 존재합니다.

  ○ 새 버전으로 추가 (기존 파일 유지, 버전 히스토리에 추가)
  ○ 이름 변경하여 업로드 (document (2).pdf)
  ○ 건너뛰기

  [ ] 이후 충돌에 동일하게 적용
  [ 취소 ]  [ 적용 ]
</UploadConflictDialog>
```

### 9.3 페이지 이탈 경고

```ts
useEffect(() => {
  const pending = queue.some((t) => ['uploading', 'queued'].includes(t.status))
  if (!pending) return
  const h = (e: BeforeUnloadEvent) => { e.preventDefault(); e.returnValue = '' }
  window.addEventListener('beforeunload', h)
  return () => window.removeEventListener('beforeunload', h)
}, [queue])
```

### 9.4 v1.x tus 업그레이드 경로

```ts
// lib/tus.ts (v1.x)
import * as tus from 'tus-js-client'

export function createResumableUpload(file, targetFolderId, callbacks) {
  return new tus.Upload(file, {
    endpoint: '/api/uploads',
    retryDelays: [0, 3000, 5000, 10000, 20000],
    chunkSize: 5 * 1024 * 1024,
    metadata: { filename: file.name, filetype: file.type, folderId: targetFolderId },
    onProgress: (b, total) => callbacks.onProgress(b / total, b),
    onSuccess: callbacks.onSuccess,
    onError: (err) => callbacks.onError(classifyTusError(err)),
  })
}
```

→ 같은 `UploadStore` 계약을 유지하므로 훅 구현만 교체 가능.

---

## 10. 검색 견고성 (v3 보강)

```ts
// hooks/useSearch.ts
import { useQuery } from '@tanstack/react-query'
import { useDebounce } from '@/hooks/useDebounce'
import { normalizeForSearch } from '@/lib/normalize'

export function useSearch(rawQuery: string, filters: Filters) {
  const debounced = useDebounce(rawQuery.trim(), 300)
  const normalized = normalizeForSearch(debounced)

  return useQuery({
    queryKey: qk.search(normalized, filters),
    queryFn: ({ signal }) => api.searchFiles({ q: normalized, filters }, { signal }),
    enabled: normalized.length >= 2,         // 최소 2자
    placeholderData: (prev) => prev,         // 타이핑 중 이전 결과 유지
    staleTime: 30_000,
  })
}
```

```ts
// lib/normalize.ts
export function normalizeForSearch(s: string): string {
  return s.normalize('NFC').toLowerCase().trim().replace(/\s+/g, ' ')
}

export function normalizeFileName(s: string): string {
  return s.normalize('NFC').trim()
}
```

백엔드도 **동일한 normalize 함수**로 `files.normalized_name` 컬럼을 만들어야 함 (Postgres `NORMALIZE()` 또는 애플리케이션 레벨 동기화).

> **백엔드 계약**: `api.searchFiles` → `GET /api/search?q=&type=&cursor=&limit=` (docs/02 §7.8). 알고리즘/필터 범위는 ADR #33 (docs/00 §5).

---

## 11. 빈 / 로딩 / 에러 상태

| 상태 | 컴포넌트 | 배치 |
|---|---|---|
| 로딩 | `<FileTableSkeleton />` | `loading.tsx` |
| 빈 폴더 | `<FileTableEmpty />` + 업로드 CTA | FileTable 내부 |
| 빈 검색 결과 | `<EmptySearch />` + "다른 키워드 시도" | SearchResults 내부 |
| 일반 에러 | `<FileTableError onRetry />` | `error.tsx` |
| 403 | `<FileTableForbidden />` | 에러 분기 |
| 404 | `<NotFound />` | `not-found.tsx` |

---

## 12. 접근성 & 키보드 내비게이션

### 12.1 키맵

| 키 | 동작 |
|---|---|
| ↑ ↓ | 행 포커스 이동 + 단일 선택 |
| Shift + ↑↓ | 범위 확장 |
| Ctrl/Meta + ↑↓ | 포커스만 이동 |
| Space | 선택 토글 |
| Enter | 열기 (폴더 진입 또는 `?file=` 설정) |
| Delete | 휴지통으로 |
| F2 | 이름 변경 |
| Ctrl/Meta + A | 전체 선택 |
| Esc | 선택 해제 / RightPanel 닫기 (`?file=` 제거) |
| / | 검색창 포커스 |

### 12.2 Virtualization + aria

```tsx
<div role="grid" aria-rowcount={totalCount} aria-multiselectable="true">
  {virtualItems.map((v) => (
    <div
      role="row"
      aria-rowindex={v.index + 1}
      aria-selected={selected.has(items[v.index].id)}
      tabIndex={focusedIndex === v.index ? 0 : -1}
    />
  ))}
</div>
```

---

## 13. 휴지통 & 되돌리기

### 13.1 데이터 모델

```text
files.deleted_at       : 휴지통 이동 시각 (NULL = active)
files.original_parent  : 복원 시 원위치
files.purge_after      : 영구 삭제 예정 (deleted_at + 30일)

상태:
  active   - deleted_at IS NULL
  trashed  - deleted_at IS NOT NULL AND purge_after > NOW()
  (purge는 백엔드 크론이 담당)
```

### 13.2 UX

- `/trash` 전용 페이지, Sidebar 하단 `<TrashLink />`
- 행 액션: **원위치로 복원**, **영구 삭제**
- 삭제 직후 토스트의 **"되돌리기"** 버튼 (5초)

> **Backend endpoints** (docs/02 §7.11):
> - `GET /api/trash?cursor=&type=` — list. queryKey `qk.trash()` (§6.1).
> - `POST /api/files/:id/restore` / `POST /api/folders/:id/restore` — per-resource restore (A6).
> - `DELETE /api/trash/:type/:id` — manual purge, ADMIN only (A8, ADR #32).
> - bulk `DELETE /api/trash`는 미구현 — `purge.expired` 배치(A7) 자동 처리.

```tsx
const handleDelete = (ids: string[]) => {
  markPending(ids)
  deleteMutation.mutate(ids, {
    onSuccess: () => {
      toast.show({
        message: `${ids.length}개 항목을 휴지통으로 이동`,
        action: { label: '되돌리기', onClick: () => restore.mutate(ids) },
        duration: 5000,
      })
    },
    onSettled: () => unmarkPending(ids)
  })
}
```

---

## 14. 권한 기반 조건부 렌더링

### 14.1 원칙 재확인

```
프론트 usePermission = UX 개선용
백엔드 권한 검증 = 보안의 마지막 방어선
403 응답 = 일급 에러 상태 (롤백 + 재동기화 + 유효 권한 캐시 무효화)
```

### 14.2 권한 훅

```ts
// hooks/usePermission.ts
export function usePermission(nodeId?: string) {
  const key = nodeId ? qk.permissions(nodeId) : qk.effectivePermissions()
  const { data } = useQuery({
    queryKey: key,
    queryFn: () => api.getEffectivePermissions(nodeId),
    staleTime: 60_000,
  })
  return {
    read:     data?.includes('read')     ?? false,
    upload:   data?.includes('upload')   ?? false,
    edit:     data?.includes('edit')     ?? false,
    delete:   data?.includes('delete')   ?? false,
    download: data?.includes('download') ?? false,
    move:     data?.includes('move')     ?? false,
    share:    data?.includes('share')    ?? false,
    admin:    data?.includes('admin')    ?? false,
  }
}
```

### 14.3 렌더링 규칙

| 액션 유형 | 권한 없을 때 | 이유 |
|---|---|---|
| **생산적** (업로드, 새 폴더, 공유) | 비활성 + 툴팁 설명 | 왜 안 되는지 알려줌 |
| **파괴적** (삭제, 영구 삭제) | 숨김 | 비활성이 오히려 유인 |
| **조회 불가** (읽기 권한 없음) | 애초에 리스트/트리에 안 뜸 | 백엔드 필터링 |

### 14.4 ShareDialog (F4 → F5)

`components/shares/ShareDialog.tsx` — 파일/폴더 공유 + by-me 목록 + revoke. F4(파일, 2026-05-01) → F5(폴더 양립, 2026-05-01).

- **트리거**:
  - 파일: `BulkActionBar` 단일 선택 시 `공유` 버튼 → `useShareUiStore.open({kind:'file', id, name})`.
  - 폴더: `Breadcrumb` 우측 `공유` 버튼(현재 폴더 = URL `folderId`, 비루트만) → `open({kind:'folder', id, name})`.
- **store 형상**: `target: ShareTarget = {kind:'file'|'folder', id, name}` discriminated. ShareDialog는 `target` 단일 선택자로 일반화.
- **mutation**: `useCreateShare` Vars `{target, req}` → target.kind === 'folder' ? POST `/api/folders/{id}/share` : POST `/api/files/{id}/share` (api.createFolderShares / createFileShares 분리).
- subject: **'everyone' 고정 (MVP)** — user/department/role 목록 endpoint 부재. 백로그.
- preset: `read | upload | edit | admin` 4값 (ADR #34, V5 CHECK는 SHARE 미지원).
- expiresAt: HTML5 datetime-local → `new Date(v).toISOString()`.
- 기존 by-me share 목록 매칭: `(s.fileId ?? s.folderId) === target.id` (wire `shares` 행은 file_id/folder_id XOR — V6 CHECK).
- 기존 share 행 표시: 만료 시각 + `해제` 버튼만. **subject/preset 필드는 backend `ShareDto` record에 부재 (wire drift, A13 backlog 등록 후 복원 예정).**
- `해제` → `useRevokeShare`. 백엔드 `canRevoke`(sharedBy==me ‖ ADMIN)가 진실의 출처.
- 에러 envelope: 409 PERMISSION_CONFLICT / 403 PERMISSION_DENIED / 404 NOT_FOUND(파일|폴더 분기) / 그 외 → 한국어 toast.error.
- 무효화: §6.1 `qk.shares()` prefix 1회로 by-me/with-me 동시 갱신 (file/folder 무관 동일).
- **ShareDto wire** (10필드, backend `com.ibizdrive.share.ShareDto` record와 1:1):
  `{id, fileId|null, folderId|null, permissionId, sharedBy, message|null, expiresAt|null, createdAt, revokedAt|null, revokedBy|null}` — active 행에서 revoked* 항상 null.
- **SharesTable** (`components/shares/SharesTable.tsx`): with-me 목록. F5에서 컬럼은 `항목 | 공유한 사람 | 만료` 3열로 단순화 (preset 컬럼은 wire 부재로 제거). 항목 셀은 file/folder 아이콘 분기(`folderId !== null`).

---

## 15. 실시간 동기화 (SSE)

> ADR #14에 의해 SSE를 MVP부터 채택 (ADR #8 superseded). Spring `SseEmitter` (MVC) + `EventSource` 클라이언트.
> 백엔드 endpoint·이벤트 정의: `docs/02-backend-data-model.md §7.13`. 본 절은 **프론트 통합** 관점.

### 15.1 개요

- 단일 `EventSource` 연결로 폴더 단위 구독 (`?folderIds=fld_a,fld_b`).
- 서버 push → 클라가 `queryKeys`(§6.1)에 매핑하여 `invalidateQueries` 호출.
- **낙관적 setQueriesData는 사용 안 함** — race 위험 회피, 단일 무효화 경로 유지.
- 연결 라이프사이클: 폴더 진입 시 open, 폴더 변경 시 close+reopen, 페이지 leave 시 close.

### 15.2 이벤트 타입 (`SseEventType` enum)

`src/types/sse.ts` 신규 파일 — 백엔드 `docs/02 §7.13.1`과 1:1 미러. 변경 시 양쪽 동시 갱신 (CLAUDE.md §4 계약 파일).

```ts
export type SseEventType =
  // 파일 — payload: { fileId, name?, folderId, ... }
  | 'FILE_CREATED'           // 업로드 완료, finalize 후
  | 'FILE_RENAMED'           // PATCH /api/files/:id { name }
  | 'FILE_MOVED'             // POST /api/files/:id/move — sourceFolderId + targetFolderId 양쪽 fan-out
  | 'FILE_VERSION_CREATED'   // POST /api/files/:id/versions
  | 'FILE_DELETED'           // soft delete (휴지통 이동)
  | 'FILE_RESTORED'          // POST /api/files/:id/restore
  | 'FILE_PURGED'            // 영구 삭제 (관리자 trash purge)
  // 폴더 — payload: { folderId, name?, parentFolderId, ... }
  | 'FOLDER_CREATED'
  | 'FOLDER_RENAMED'
  | 'FOLDER_MOVED'           // sourceParent + targetParent 양쪽 fan-out
  | 'FOLDER_DELETED'         // soft delete
  | 'FOLDER_RESTORED'
  | 'FOLDER_PURGED'
  // 권한 — payload: { resource, resourceId, subject?, permissionId? }
  | 'PERMISSION_GRANTED'     // POST /api/:resource/:id/permissions
  | 'PERMISSION_REVOKED'     // DELETE /api/permissions/:permissionId
  | 'PERMISSION_CHANGED'     // 기존 권한의 preset/expiry 변경

export interface SseEnvelope<T = unknown> {
  id: string                    // evt_<uuid>
  type: SseEventType
  occurredAt: string            // ISO8601
  actor: { userId: string; displayName: string }
  scope: { folderIds: string[] }
  payload: T
}
```

### 15.3 useRealtimeSync 훅

```ts
// src/hooks/useRealtimeSync.ts
import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { qk } from '@/lib/queryKeys'
import type { SseEnvelope, SseEventType } from '@/types/sse'

const FILE_EVENTS: SseEventType[] = [
  'FILE_CREATED', 'FILE_RENAMED', 'FILE_MOVED', 'FILE_VERSION_CREATED',
  'FILE_DELETED', 'FILE_RESTORED', 'FILE_PURGED',
]
const FOLDER_EVENTS: SseEventType[] = [
  'FOLDER_CREATED', 'FOLDER_RENAMED', 'FOLDER_MOVED',
  'FOLDER_DELETED', 'FOLDER_RESTORED', 'FOLDER_PURGED',
]
const PERMISSION_EVENTS: SseEventType[] = [
  'PERMISSION_GRANTED', 'PERMISSION_REVOKED', 'PERMISSION_CHANGED',
]

export function useRealtimeSync(folderIds: string[]) {
  const qc = useQueryClient()

  useEffect(() => {
    if (folderIds.length === 0) return
    const url = `${process.env.NEXT_PUBLIC_API_BASE_URL}/api/sse/files?folderIds=${folderIds.join(',')}`
    const es = new EventSource(url, { withCredentials: true })

    const onMessage = (e: MessageEvent) => {
      const env = JSON.parse(e.data) as SseEnvelope
      handleEvent(qc, env)
    }
    // 모든 이벤트 타입에 동일 핸들러 등록 (서버는 event: <TYPE> 송신)
    ;[...FILE_EVENTS, ...FOLDER_EVENTS, ...PERMISSION_EVENTS].forEach((t) =>
      es.addEventListener(t, onMessage),
    )

    es.onerror = () => {
      // EventSource는 자동 재연결 (브라우저 기본 동작) — 우리는 로깅만
      // 장기 disconnect 감지 시 staleTime 0으로 모든 쿼리 무효화 (TODO: A5)
    }
    return () => es.close()
  }, [folderIds.join(','), qc])
}

function handleEvent(qc: QueryClient, env: SseEnvelope) {
  const { type, scope, payload } = env

  if (FILE_EVENTS.includes(type)) {
    // 파일 이벤트 — scope.folderIds 각각의 filesInFolder 쿼리 무효화
    scope.folderIds.forEach((fid) =>
      qc.invalidateQueries({ queryKey: qk.filesInFolder(fid), exact: false }),
    )
    // 단일 파일 상세 캐시도 무효화
    if ((payload as { fileId?: string }).fileId) {
      qc.invalidateQueries({ queryKey: qk.file((payload as { fileId: string }).fileId) })
    }
    return
  }

  if (FOLDER_EVENTS.includes(type)) {
    // 폴더 이벤트 — 트리 + 해당 폴더 상세 + 부모 폴더의 자식 목록 무효화
    qc.invalidateQueries({ queryKey: qk.folderTree() })
    scope.folderIds.forEach((fid) => {
      qc.invalidateQueries({ queryKey: qk.folder(fid) })
      qc.invalidateQueries({ queryKey: qk.filesInFolder(fid), exact: false })
    })
    return
  }

  if (PERMISSION_EVENTS.includes(type)) {
    const p = payload as { resource: 'folder' | 'file'; resourceId: string }
    qc.invalidateQueries({ queryKey: qk.effectivePermissions(p.resourceId) })
    // 권한 변경은 가시성에도 영향 — 트리 + 목록 무효화
    qc.invalidateQueries({ queryKey: qk.folderTree() })
    scope.folderIds.forEach((fid) =>
      qc.invalidateQueries({ queryKey: qk.filesInFolder(fid), exact: false }),
    )
  }
}
```

### 15.4 무효화 매트릭스

| 이벤트 그룹 | 무효화되는 쿼리 키 |
|---|---|
| `FILE_*` | `qk.filesInFolder(scope.folderIds[i])` (각각), `qk.file(payload.fileId)` |
| `FOLDER_*` | `qk.folderTree()`, `qk.folder(scope.folderIds[i])`, `qk.filesInFolder(scope.folderIds[i])` |
| `PERMISSION_*` | `qk.effectivePermissions(payload.resourceId)`, `qk.folderTree()`, `qk.filesInFolder(scope.folderIds[i])` |

> `FILE_MOVED` / `FOLDER_MOVED`는 `scope.folderIds`에 source + target이 함께 들어옴 — 양쪽 무효화 자동.

### 15.5 연결 정책

- **재연결**: `EventSource`의 브라우저 기본 자동 재연결 사용 (마지막 이벤트 ID 기반). 서버는 `Last-Event-ID` 헤더 수신 시 갭 메시지 replay (A5에서 구현).
- **하트비트**: 서버 25초 keepalive (docs/02 §7.13). 클라는 별도 로직 불필요.
- **백오프**: 브라우저 기본 (1~3초 점증). 사용자 정의 백오프 필요 시 `EventSource`를 wrap한 polyfill 도입 (TODO: A5에서 결정).
- **다중 폴더**: 단일 연결로 다중 `folderIds` 구독. 폴더 변경 시 close + 새 URL로 reopen.

### 15.6 ADR #8 (폴링) 폐기 사유

ADR #8(MVP 폴링)은 SSE 인프라 복잡도 회피가 목적이었으나:
1. `SseEmitter`는 Spring MVC에 내장, Webflux 도입 불필요 (ADR #14)
2. 폴링 staleTime 추정의 트레이드오프(즉시성 vs 서버 부하) 회피
3. 권한 변경 등 즉시 반영이 필요한 이벤트(예: 회수 후 노출 차단)에서 폴링 지연이 보안 갭이 됨

따라서 MVP부터 SSE 채택.

---

## 16. 감사 로그 (v3 신규)

### 16.1 데이터 계층 분리

```text
activity (파일 단위) — ActivityTimeline에서 일반 사용자에게 노출
audit_log (전사)    — 관리자에게만 노출, 컴플라이언스 요구에 따라 영구 보관
```

### 16.2 관리자 페이지

```text
/admin/audit-logs         전체 감사 로그 (검색/필터/export)
/admin/download-logs      다운로드 이력
/admin/permission-logs    권한 변경 이력
/admin/storage-usage      사용량 대시보드
```

### 16.3 감사 대상 이벤트

```ts
type AuditEventType =
  | 'file.viewed'       // 민감 파일 조회 시
  | 'file.downloaded'
  | 'file.uploaded'
  | 'file.moved'
  | 'file.deleted'
  | 'file.restored'
  | 'file.purged'
  | 'version.created'
  | 'version.restored'
  | 'permission.granted'
  | 'permission.revoked'
  | 'permission.changed'
  | 'share.link_created'
  | 'share.link_revoked'
  | 'folder.created'
  | 'folder.deleted'
  | 'user.auth.login'
  | 'user.auth.failed'

type AuditEntry = {
  id: string
  timestamp: string
  actorId: string
  actorIp: string
  userAgent: string
  eventType: AuditEventType
  targetId: string       // file/folder/user id
  targetType: 'file' | 'folder' | 'user' | 'permission'
  before?: unknown       // 변경 전 상태
  after?: unknown        // 변경 후 상태
  reason?: string
}
```

---

## 17. 코드 템플릿 — 우선순위 1 (folderId 중심 라우팅)

### 17.1 루트 리다이렉트

```tsx
// app/files/page.tsx
import { redirect } from 'next/navigation'
export default function FilesRootPage() {
  redirect('/files/root')
}
```

### 17.2 Catch-all 라우트 + canonical redirect

```tsx
// app/(explorer)/files/[...parts]/page.tsx
import { redirect, notFound } from 'next/navigation'
import { api } from '@/lib/api'
import { buildCanonicalPath } from '@/lib/folderPath'
import { FileTable } from '@/components/files/FileTable'
import { Toolbar } from '@/components/layout/Toolbar'
import { BulkActionBar } from '@/components/files/BulkActionBar'
import { Breadcrumb } from '@/components/folders/Breadcrumb'
import { RightPanel } from '@/components/detail/RightPanel'

type SearchParams = {
  sort?: string
  dir?: 'asc' | 'desc'
  file?: string
  q?: string
}

export default async function FilesPage({
  params,
  searchParams,
}: {
  params: { parts: string[] }
  searchParams: SearchParams
}) {
  const [folderId, ...providedSlug] = params.parts

  // 서버에서 folder 조회 (권한 체크 포함, 403이면 notFound 또는 forbidden)
  const folder = await api.getFolder(folderId).catch(() => null)
  if (!folder) notFound()

  // Canonical URL 검증
  const canonicalSlug = folder.path   // 서버가 돌려주는 정규 path
  const providedPath = providedSlug.join('/')
  const canonicalPath = buildCanonicalPath(folderId, canonicalSlug)
  const currentPath = `/files/${params.parts.join('/')}`

  if (currentPath !== canonicalPath) {
    // slug 불일치 → canonical로 308 redirect
    const qs = new URLSearchParams(searchParams as any).toString()
    redirect(`${canonicalPath}${qs ? `?${qs}` : ''}`)
  }

  const sort = (searchParams.sort ?? 'name') as SortKey
  const dir = searchParams.dir ?? 'asc'

  return (
    <>
      <Breadcrumb folderId={folderId} />
      <Toolbar />
      <BulkActionBar />
      <FileTable folderId={folderId} sort={sort} dir={dir} />
      {searchParams.file && <RightPanel fileId={searchParams.file} />}
    </>
  )
}
```

### 17.3 canonical path 헬퍼

```ts
// lib/folderPath.ts
export function buildCanonicalPath(
  folderId: string,
  slugPath: string[]   // 서버가 돌려주는 정규 segment 배열, NFC 정규화됨
): string {
  const encoded = slugPath.map(encodeURIComponent).join('/')
  return encoded
    ? `/files/${folderId}/${encoded}`
    : `/files/${folderId}`
}

export function getFolderIdFromParts(parts: string[]): string | null {
  return parts[0] ?? null
}
```

### 17.4 useCurrentFolder

```ts
// hooks/useCurrentFolder.ts
'use client'
import { useParams } from 'next/navigation'
import { useQuery } from '@tanstack/react-query'
import { qk } from '@/lib/queryKeys'
import { api } from '@/lib/api'

export function useCurrentFolder() {
  const params = useParams<{ parts?: string[] }>()
  const folderId = params.parts?.[0] ?? 'root'

  const { data, isLoading, error } = useQuery({
    queryKey: qk.folder(folderId),
    queryFn: () => api.getFolder(folderId),
    staleTime: 60_000,
  })

  return {
    folderId,
    folder: data,
    breadcrumb: data?.breadcrumb ?? [],  // 서버가 derive해서 돌려줌
    isLoading,
    error,
  }
}
```

### 17.5 useOpenFile (RightPanel query param 동기화)

```ts
// hooks/useOpenFile.ts
'use client'
import { useSearchParams, useRouter, usePathname } from 'next/navigation'
import { useCallback } from 'react'

export function useOpenFile() {
  const params = useSearchParams()
  const router = useRouter()
  const pathname = usePathname()
  const fileId = params.get('file')

  const open = useCallback((id: string) => {
    const next = new URLSearchParams(params)
    next.set('file', id)
    router.replace(`${pathname}?${next.toString()}`, { scroll: false })
  }, [params, pathname, router])

  const close = useCallback(() => {
    const next = new URLSearchParams(params)
    next.delete('file')
    router.replace(
      next.size ? `${pathname}?${next.toString()}` : pathname,
      { scroll: false }
    )
  }, [params, pathname, router])

  return { fileId, open, close }
}
```

### 17.6 FolderTree (folderId로 active 판단)

```tsx
// components/folders/FolderTree.tsx
'use client'
import Link from 'next/link'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { useFolderTree } from '@/hooks/useFolderTree'
import { useViewStore } from '@/stores/view'
import { buildCanonicalPath } from '@/lib/folderPath'

export function FolderTree() {
  const { data: tree, isLoading } = useFolderTree()
  const { folderId: activeId } = useCurrentFolder()

  if (isLoading) return <FolderTreeSkeleton />
  if (!tree) return null

  return (
    <nav aria-label="폴더 트리">
      <FolderNode node={tree} activeId={activeId} depth={0} pathAcc={[]} />
    </nav>
  )
}

function FolderNode({
  node, activeId, depth, pathAcc,
}: {
  node: FolderNodeType
  activeId: string
  depth: number
  pathAcc: string[]
}) {
  const { expandedFolderIds, toggleExpanded } = useViewStore()
  const isExpanded = expandedFolderIds.includes(node.id)
  const isActive = activeId === node.id
  const nextPath = node.id === 'root' ? [] : [...pathAcc, node.slug]
  const href = buildCanonicalPath(node.id, nextPath)

  return (
    <div>
      <div
        className={`flex items-center gap-1 px-2 py-1 rounded hover:bg-muted ${
          isActive ? 'bg-accent text-accent-foreground' : ''
        }`}
        style={{ paddingLeft: depth * 12 + 8 }}
      >
        {node.children?.length ? (
          <button
            onClick={() => toggleExpanded(node.id)}
            aria-label={isExpanded ? '접기' : '펼치기'}
            aria-expanded={isExpanded}
          >
            {isExpanded ? '▾' : '▸'}
          </button>
        ) : (
          <span className="w-4" />
        )}
        <Link href={href} className="flex-1 truncate">📁 {node.name}</Link>
      </div>
      {isExpanded && node.children?.map((child) => (
        <FolderNode
          key={child.id}
          node={child}
          activeId={activeId}
          depth={depth + 1}
          pathAcc={nextPath}
        />
      ))}
    </div>
  )
}
```

### 17.7 Breadcrumb (folder API data에서 derive)

```tsx
// components/folders/Breadcrumb.tsx
'use client'
import Link from 'next/link'
import { useCurrentFolder } from '@/hooks/useCurrentFolder'
import { buildCanonicalPath } from '@/lib/folderPath'

export function Breadcrumb() {
  const { breadcrumb, isLoading } = useCurrentFolder()
  if (isLoading) return <BreadcrumbSkeleton />

  return (
    <nav aria-label="Breadcrumb" className="flex items-center gap-1 text-sm">
      {breadcrumb.map((c, i) => {
        const href = buildCanonicalPath(c.id, c.slugPath)
        const last = i === breadcrumb.length - 1
        return (
          <span key={c.id} className="flex items-center gap-1">
            {i > 0 && <span className="text-muted-foreground">/</span>}
            {last ? (
              <span className="font-medium">{c.name}</span>
            ) : (
              <Link href={href} className="hover:underline">{c.name}</Link>
            )}
          </span>
        )
      })}
    </nav>
  )
}
```

### 17.8 useFolderTree

```ts
// hooks/useFolderTree.ts
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

### 17.9 Providers

```tsx
// app/providers.tsx
'use client'
import { QueryClient, QueryClientProvider, QueryCache } from '@tanstack/react-query'
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'
import { useState } from 'react'
import { qk } from '@/lib/queryKeys'
import { toast } from '@/components/ui/toast'

export function Providers({ children }: { children: React.ReactNode }) {
  const [client] = useState(() => {
    const c = new QueryClient({
      defaultOptions: {
        queries: {
          staleTime: 30_000,
          refetchInterval: 30_000,
          refetchOnWindowFocus: true,
          retry: (count, err: any) =>
            err?.status !== 401 && err?.status !== 403 && count < 2,
        },
      },
      queryCache: new QueryCache({
        onError: (err: any) => {
          if (err?.status === 403) {
            c.invalidateQueries({ queryKey: qk.effectivePermissions() })
            toast.error('권한이 없거나 접근이 제한되었습니다')
          }
        }
      })
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

---

## 18. 조정된 우선순위 로드맵

| # | 마일스톤 | 완료 기준 |
|---|---|---|
| 1 | **folderId 중심 라우팅 + canonical redirect** | 폴더 이름 변경해도 URL 유효, slug 불일치 시 redirect |
| 2 | **FolderTree + Breadcrumb 동기화** | 트리/Breadcrumb/URL 항상 일치 |
| 3 | **FileTable** (virtualized, 4가지 상태) | 10k 행 60fps, Empty/Loading/Error/Forbidden |
| 4 | **선택 모델 + BulkActionBar** | Shift/Ctrl/A/Esc 전부 동작 |
| 5 | **업로드 (multipart + 충돌 + 실패 분류)** | 동일 이름 충돌 시 ConflictDialog, beforeunload |
| 6 | **RightPanel (query param)** | `?file=xxx` 딥링크, Esc로 닫기 |
| 7 | **DnD 이동** (dnd-kit, 완료 2026-04-25) | Row → FolderNode/Breadcrumb/FileRow(폴더). BulkActionBar 다이얼로그 키보드 경로. 자기/후손/같은-폴더 차단. pending 시각화 + role="status" 카운트 배지. |
| 8 | **권한 UI + 조건부 렌더링** | 생산적=비활성, 파괴적=숨김, 403 전역 처리 |
| 9 | **휴지통 + Undo** | 5초 토스트, `/trash` 페이지 |
| 10 | **접근성 + 키보드** (완료 2026-04-25) | Shift/Ctrl+화살표, F2 rename(다이얼로그), Delete 휴지통, `/` 전역 트리거. RenameDialog focus trap + role=alert 에러. |
| 11 | **검색** (debounce, abort, normalize 일치) | 2자 이상, 타이핑 중 placeholderData |
| 12 | **감사 로그 UI** (도메인에 따라) | `/admin/audit-logs` 필터/export |
| 13 | **디자인 토큰 적용** (M13, 완료 2026-04-25) | `:root` 토큰 + `@theme inline` + 모든 className 토큰화. 기준: `design-reference/IbizDrive.html` |
| 14 | **Visual Identity** | TopBar(검색/테마 토글/아바타) + Lucide 아이콘 도입 + FileRow 밀도 재조정 + StatusBar 하단. M13 토큰 위에서 JSX 추가 |
| 15 | **Layout Extras** | SortChip(정렬 드롭다운) + ViewSwitch(List/Grid 토글) + StorageBar(사이드바 하단) + RightPanel 탭(세부정보/버전/활동/권한) |
| 16 | **Grid View** | FileTable에 grid 모드 추가 (썸네일 카드형). M14의 ViewSwitch에서 토글 |
| v1.x | **tus 재개 업로드** | UploadStore 계약 유지, 훅만 교체 |
| v1.x | **SSE 실시간 동기화** | `file.created` 등 이벤트 반영, 폴백 폴링 |

---

## 19. 최상위 원칙 (리마인더)

1. **URL folderId가 canonical**, slug는 표시용 → canonical redirect로 안정성.
2. **RightPanel은 query param** (`?file=xxx`) 일관 사용. parallel route 쓰지 않음.
3. **프론트 권한은 UX용, 백엔드가 보안의 최종 방어**. 403은 일급 에러.
4. **낙관적 업데이트는 비파괴적 액션만**. 파괴적 액션(이동/삭제/권한)은 pending 상태 처리.
5. **같은 folderId + normalizedFileName = 동일 파일**. NFC + lowercase 정규화 일관 적용.
6. **문자열 정규화는 프론트/백엔드 동일 함수**. `files.normalized_name` 컬럼.
7. **DnD 컨텍스트 두 개는 절대 섞지 않음** (OS→브라우저 / 브라우저 내부).
8. **가상화에는 `aria-rowcount/rowindex` 필수**.
9. **삭제는 휴지통 + 5초 Undo + 30일 보관**. 즉시 영구 삭제는 관리자만.
10. **감사 로그는 사용자 activity와 분리**. 도메인에 따라 MVP 포함 여부 결정.
11. **MVP 범위는 파일 크기/팀 규모/컴플라이언스로 결정**. 대용량·실시간·감사는 선택적.

---

## 다음에 만들 수 있는 것

- **FileTable virtualization + 키보드 내비 전체 구현** (TanStack Virtual + aria + 키맵)
- **업로드 시스템 전체 구현** (multipart + Zustand + ConflictDialog + Queue Dock)
- **권한 매트릭스 + 백엔드 엔드포인트 권한 검증 계약서**
- **DB 스키마** (files/folders/versions/permissions/audit_log) + normalized_name 정책
- **감사 로그 관리자 페이지** (/admin/audit-logs 필터/export)
- **SSE 서버 측 Node.js 구현** + 재연결 정책

필요한 것을 지정하면 바로 이어서 작성.
