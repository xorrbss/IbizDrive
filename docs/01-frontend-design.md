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
| **URL 구조 (Plan B)** | `/files/[...parts]` (단일 루트) | **`/d/:deptId/[[...parts]]`, `/t/:teamId/[[...parts]]`, `/shared/[[...parts]]`** — workspace prefix 3-route |
| **사이드바 (Plan B)** | 단일 `FolderTree` | **`SidebarSections` 3-section** (부서 / 팀 / 공유받음) + workspace-per-lazy-tree |
| **트리 상태 (Plan B)** | `useViewStore` (expandedFolderIds) | **`useSidebarTreeStore`** (persisted, 30일 TTL, collapsedSections) |
| **DnD 제약 (Plan B)** | workspace 경계 없음 | **same-workspace only** — `MoveDragData.sourceWorkspace` + `isCrossWorkspace` 시각 차단 |

> **MVP 범위**: 업로드는 multipart로 시작, 실시간은 폴링으로 시작. tus/SSE는 v1.x 로드맵. (도메인이 대용량/컴플라이언스면 MVP에 포함 가능 — 15절 참조)

---

## 1. 최상위 설계 원칙 (v3에서 새로 명시)

### 1.1 진실 출처 규칙

```text
현재 "어디"를 보는가 → URL  (workspace prefix + folderId가 canonical key — Plan B)
현재 "무엇"을 하는가 → Zustand (선택, 드래그, 업로드 큐, sidebar expand 상태)
"사실" 그 자체       → TanStack Query (서버 데이터)
```

절대로 서버 데이터를 Zustand에 복제하지 않음. 절대로 URL 정보(workspace/folderId)를 Zustand에 복제하지 않음.

> **Plan B**: workspace root folder ID는 `GET /api/workspaces/me` 응답에서 취득. `VIRTUAL_ROOT_ID('root')` 사용 금지.

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

## 2. URL 구조 (v3 → Plan B workspace prefix)

> **Plan B 변경**: `/files/[...parts]` 단일 루트를 폐기하고 workspace prefix 3-route 체계로 전환.
> `VIRTUAL_ROOT_ID('root')` 개념도 함께 폐기 — workspace root folder ID는 서버가 내려주는 실제 UUID.

> **Sidebar Chrome (G2 / 2026-05-11)**: 사이드바 자체의 collapse 토글은 TopBar 좌측 햄버거 버튼이 owns. `useSidebarChromeStore` (`sidebar-chrome:v1` localStorage persist) 의 `collapsed: boolean` 만으로 외곽 폭 (`w-[248px]` ↔ `w-0`)을 전환. 폴더 expand state(`sidebarTree`)와 책임 분리 — chrome=외곽 토글, tree=내부 expand. `aria-hidden`로 SR이 collapsed 상태에서 트리를 읽지 않게 함.

### 2.1 패턴: workspace prefix + folderId catch-all (Plan B)

```text
/d/[deptId]/[[...parts]]     → 부서 콘텐츠
/t/[teamId]/[[...parts]]     → 팀 콘텐츠
/shared/[[...parts]]         → 공유받은 콘텐츠

parts[0]      = folderId    (canonical key, 조회에 사용)  ← 기존 /files 패턴 유지
parts[1..]    = slug path   (표시용, 가독성/공유용)

예시:
    /d/dept-uuid-123                             (부서 landing — workspace root)
    /d/dept-uuid-123/folder-abc123               (부서 내 폴더)
    /d/dept-uuid-123/folder-abc123/영업팀/계약서  (부서 내 폴더 + slug)
    /t/team-uuid-456/folder-xyz789               (팀 내 폴더)
    /shared/folder-shared-1/보고서               (공유받은 폴더)

휴지통:
    /trash/d/:deptSlug                           (부서 휴지통)
    /trash/t/:teamSlug                           (팀 휴지통)

> ❌ 폐기: /files/[...parts]  — Plan B에서 제거됨.
```

Next.js App Router 파일 위치:
```text
app/(explorer)/
  d/[deptId]/[[...parts]]/page.tsx
  t/[teamId]/[[...parts]]/page.tsx
  shared/[[...parts]]/page.tsx
```

### 2.2 URL 검증 및 canonical redirect

```text
요청: /d/dept-uuid/folder_abc123/잘못된이름

1. folderId=folder_abc123 로 폴더 조회 → 실제 path = "영업팀/계약서"
2. URL slug ≠ 실제 path → 308 redirect → /d/dept-uuid/folder_abc123/영업팀/계약서
3. folderId 자체가 없거나 권한 없음 → 404 / 403

이로써:
  ✅ 폴더 이름 변경해도 URL 유효 (folderId가 안정 키)
  ✅ 링크 공유 시 가독성 있는 URL + workspace context 명시
  ✅ 동일 이름 폴더 충돌 없음
  ✅ 이동해도 URL 유효 (slug만 갱신)
```

### 2.3 URL 상태 맵

| 상태 | 위치 | 예시 |
|---|---|---|
| 현재 workspace | URL path prefix `/d/:id` or `/t/:id` | `/d/dept-uuid-123/...` |
| 현재 폴더 | URL path parts[0] (after workspace prefix) | `/d/dept-uuid/folder_abc123/...` |
| 표시용 slug | URL path parts[1..] | `/영업팀/계약서` |
| 열린 파일 상세 | URL query `?file=` | `?file=file_xyz789` |
| 검색어 | URL query `?q=` | `?q=2025계약` |
| 정렬 | URL query `?sort=&dir=` | `?sort=modifiedAt&dir=desc` |
| 필터 | URL query `?type=&owner=` | `?type=pdf&owner=me` |
| 선택된 파일 | Zustand (휘발성) | - |
| 뷰 모드 | localStorage + Zustand | - |
| 트리 확장 상태 | localStorage + `useSidebarTreeStore` (persisted) | - |
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

> **Plan B 변경**: `/files/*` 루트 제거 → workspace prefix 3-route 추가. `FolderTree` / `useFolderTree` / `folderPath.ts` 제거.

```text
app/
├─ layout.tsx                        # 루트 레이아웃 + Providers
├─ providers.tsx                     # QueryClient, DndContext, Zustand hydration
├─ (explorer)/
│  ├─ layout.tsx                     # <AppLayout> (TopBar + Sidebar + 컨텐츠)
│  ├─ d/[deptId]/[[...parts]]/
│  │  └─ page.tsx                    # 부서 폴더 뷰 + canonical redirect 처리
│  ├─ t/[teamId]/[[...parts]]/
│  │  └─ page.tsx                    # 팀 폴더 뷰 + canonical redirect 처리
│  ├─ shared/[[...parts]]/
│  │  └─ page.tsx                    # 공유받은 폴더 뷰
│  ├─ trash/
│  │  └─ page.tsx                    # <TrashView /> (workspace 탭 포함)
│  ├─ shares/
│  │  └─ page.tsx                    # 받은 공유 (F4, with-me)
│  └─ search/
│     └─ page.tsx                    # ?q=... 검색 결과
└─ api/
   └─ (proxy routes)

# ❌ @rightPanel parallel route 제거됨 (v3부터).
# ❌ /files/[...parts] 루트 제거됨 (Plan B — workspace prefix 체계로 전환).
# RightPanel은 ContentArea 내부 컴포넌트로, ?file= query param을 구독.

src/
├─ components/
│  ├─ layout/         (AppLayout, TopBar, Sidebar, ContentArea)
│  ├─ sidebar/        (SidebarSections, WorkspaceSection, WorkspaceFolderTree,
│  │                   SharedWithMeSection, FolderTreeNode, TeamCreateButton, TeamCreateDialog)
│  ├─ dnd/            (DndProvider, types.ts, useFolderDroppable — cross-workspace guard)
│  ├─ files/          (FileTable, FileRow, FileContextMenu, BulkActionBar)
│  ├─ upload/         (UploadOverlay, UploadQueue, UploadItem, DropZone)
│  ├─ detail/         (RightPanel, VersionList, ActivityTimeline)
│  ├─ permission/     (PermissionModal, PermissionTable)
│  ├─ search/         (SearchBar, SearchResults)
│  ├─ empty/          (EmptyFolder, EmptyTrash, EmptySearch)
│  └─ ui/             (primitives)
├─ stores/
│  ├─ selection.ts      (선택 슬라이스)
│  ├─ sidebarTree.ts    (트리 확장 + section collapsed — persisted, 30일 TTL)  ← Plan B
│  ├─ upload.ts         (업로드 큐)
│  └─ dnd.ts            (드래그 상태)
│  # ❌ view.ts 제거됨 (Plan B — 트리 확장은 sidebarTree.ts로 이전)
├─ hooks/
│  ├─ useCurrentFolder.ts       (URL → folderId + 검증)
│  ├─ useCurrentWorkspace.ts    (URL → workspace kind + id)  ← Plan B
│  ├─ useWorkspaces.ts          (GET /api/workspaces/me)      ← Plan B
│  ├─ useFolderChildren.ts      (lazy per-folder children)    ← Plan B
│  ├─ useExpandPathOnNavigate.ts (URL change → sidebar auto-expand)  ← Plan B
│  ├─ useOpenFile.ts            (?file= 동기화)
│  ├─ useFiles.ts
│  ├─ useFileDetail.ts
│  ├─ useVersions.ts
│  ├─ useUpload.ts
│  ├─ usePermission.ts
│  ├─ useKeyboardNav.ts
│  ├─ useRealtimeSync.ts
│  └─ useSearch.ts
│  # ❌ useFolderTree.ts 제거됨 (Plan B — lazy WorkspaceFolderTree로 대체)
├─ lib/
│  ├─ queryKeys.ts          (중앙 쿼리 키 팩토리)
│  ├─ api/                  (fetch wrappers, AbortController 지원)
│  ├─ normalize.ts          (NFC 정규화, search normalize)
│  ├─ workspacePath.ts      (buildWorkspacePath + parseWorkspaceUrl)  ← Plan B
│  └─ permissions.ts        (can() 헬퍼)
│  # ❌ folderPath.ts 제거됨 (Plan B — workspacePath.ts로 대체)
└─ types/
   ├─ file.ts
   ├─ folder.ts
   └─ permission.ts
```

---

## 4. 컴포넌트 트리 (v3 → Plan B workspace pivot)

> **Plan B 변경**: `<FolderTree />` 단일 컴포넌트 → `<SidebarSections>` 3-section 구조로 교체.

```text
<AppLayout>
 ├─ <TopBar>
 │   ├─ <GlobalSearch />
 │   └─ <UserMenu />
 ├─ <MainArea>
 │   ├─ <Sidebar>
 │   │   ├─ <SidebarSections>          ← Plan B: 3-section shell (replaces FolderTree)
 │   │   │   ├─ Section 1: 내 부서
 │   │   │   │   └─ <WorkspaceSection kind="department">
 │   │   │   │       └─ <WorkspaceFolderTree>   ← lazy per-workspace expand
 │   │   │   │           └─ <FolderTreeNode>    ← droppable (dnd-kit)
 │   │   │   ├─ Section 2: 내 팀 (N)
 │   │   │   │   ├─ <WorkspaceSection kind="team">  (×N, archived 시각)
 │   │   │   │   │   └─ <WorkspaceFolderTree>
 │   │   │   │   └─ <TeamCreateButton>         ← "[+ 새 팀 만들기]" CTA
 │   │   │   └─ Section 3: 공유받음
 │   │   │       └─ <SharedWithMeSection>      ← flat MVP (출처 workspace 그룹핑)
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

**빈 상태 처리**:
- 부서 미배정: Section 1에 "부서 미배정 — 관리자에게 문의" 텍스트만 표시
- 팀 0개: Section 2에 `<TeamCreateButton>` CTA만 표시
- 공유받음 0개: `<SharedWithMeSection>` 섹션 자체 hide

**archived 팀**: `WorkspaceSection` + `WorkspaceFolderTree`에서 `archived` prop → dim + 🔒 아이콘, read-only 진입.

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


### 5.2 SidebarTree slice (persisted) — Plan B

> **Plan B 변경**: `useViewStore` (stores/view.ts) 의 `expandedFolderIds` 역할을 `useSidebarTreeStore`가 대체.
> `useViewStore`는 뷰 모드·정렬처럼 콘텐츠 영역 UI 상태만 유지하도록 범위 축소 (또는 필요 없으면 제거).
> `VIRTUAL_ROOT_ID('root')` 초기값 제거 — workspace root는 서버에서 받은 UUID로 초기화.

```ts
// stores/sidebarTree.ts
import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { SidebarSectionKind } from '@/lib/workspacePath'

const THIRTY_DAYS = 30 * 24 * 3600 * 1000

interface SidebarTreeState {
  /** 펼쳐진 폴더 ID 집합 — workspace 전역 (folder UUID는 항상 unique). */
  expandedFolderIds: string[]
  /** 접힌 section 목록 — 'department' | 'team' | 'shared'. 기본 모두 펼침. */
  collapsedSections: SidebarSectionKind[]
  /** 마지막 상태 변경 타임스탬프 (ms). 30일 초과 시 persist migrate에서 reset. */
  lastWriteAt: number

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
      lastWriteAt: Date.now(),
      // ... actions
    }),
    {
      name: 'sidebar-tree-state:v1',
      version: 1,
      migrate: migrateSidebarTree, // 30일 초과 → reset
    },
  ),
)
```

**30일 TTL**: `migrateSidebarTree(persisted)` — `lastWriteAt` 기준 30일 초과 시 전체 상태 초기값으로 reset (stale expand 상태 누적 방지).

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

> **Plan B 변경**:
> - `folderTree()` 제거 → 단일 flat tree 폐기. 사이드바는 `folderChildren` lazy per-workspace 패턴으로 전환.
> - `folderPath()` 제거 → `workspacePath.ts`의 `buildWorkspacePath` 로 대체.
> - `workspaces.me()` 신규 — `GET /api/workspaces/me` (부서+팀+공유받은 root 한 번에).
> - `folderChildren(scopeType, scopeId, parentId)` 신규 — lazy sidebar tree 노드별.
> - `teams.all()` 신규 — 팀 목록 (팀 생성/변경 후 무효화).
> - `invalidations.afterTeamChanged()` 신규 — `workspaces.me()` 무효화.

```ts
// lib/queryKeys.ts
export const qk = {
  all: ['explorer'] as const,

  folders: () => [...qk.all, 'folders'] as const,
  // ❌ folderTree() 제거됨 (Plan B)
  // ❌ folderPath() 제거됨 (Plan B — workspacePath.ts 사용)
  folder: (id: string) => [...qk.folders(), 'detail', id] as const,

  files: () => [...qk.all, 'files'] as const,
  /** sort/dir 포함 정확한 단일 키 — direct cache read/write 시 사용. */
  filesInFolder: (folderId: string, sort: SortKey, dir: 'asc' | 'desc') =>
    [...qk.files(), 'list', folderId, sort, dir] as const,
  /** sort/dir 변종 일괄 무효화용 prefix 키 (서버가 진실 원칙). */
  filesListPrefix: (folderId: string) => [...qk.files(), 'list', folderId] as const,
  fileDetail: (id: string) => [...qk.files(), 'detail', id] as const,
  fileVersions: (fileId: string) => [...qk.files(), 'versions', fileId] as const,
  fileActivity: (fileId: string, page: number, pageSize: number) =>
    [...qk.files(), 'activity', fileId, page, pageSize] as const,

  permissions: (nodeId?: string) => /* effective or node */ [...qk.all, 'permissions', nodeId] as const,
  effectivePermissions: () => [...qk.all, 'permissions', 'effective'] as const,

  search: () => [...qk.all, 'search'] as const,
  /** prefix 키 — invalidate 매트릭스에서 사용 (afterDelete/afterRestore/afterPurge). */
  trash: () => [...qk.all, 'trash'] as const,
  /**
   * workspace scope 별 휴지통 listing 키 (Plan E T6, 2026-05-10).
   * backend `GET /api/trash?scopeType&scopeId` 필수 파라미터와 1:1 대응.
   * invalidate 시 `qk.trash()` prefix 매칭이면 전체 scope 일괄 갱신.
   */
  trashList: (scopeType: 'department' | 'team', scopeId: string) =>
    [...qk.trash(), 'list', scopeType, scopeId] as const,

  // F4: shares (by-me/with-me)
  shares: () => [...qk.all, 'shares'] as const,
  sharesByMe: () => [...qk.shares(), 'by-me'] as const,
  sharesWithMe: () => [...qk.shares(), 'with-me'] as const,

  // ── Workspaces (Plan B, spec §5.2) ──────────────────────────────────────
  workspaces: {
    all: () => [...qk.all, 'workspaces'] as const,
    /** GET /api/workspaces/me — 부서(1) + 팀(N) + 공유받은 root 리스트 한 번에. */
    me: () => [...qk.all, 'workspaces', 'me'] as const,
  },

  /**
   * 사이드바 트리 lazy children — spec §4.5 §3.
   * scopeType + scopeId 포함 — 동일 parentId가 부서/팀 간 우연 일치에도 캐시 분리.
   * prefix: ['explorer', 'folders', 'children']
   */
  folderChildren: (scopeType: 'department' | 'team', scopeId: string, parentId: string) =>
    [...qk.all, 'folders', 'children', scopeType, scopeId, parentId] as const,

  // ── Teams (Plan B, spec §5.2) ────────────────────────────────────────────
  teams: {
    /** 내가 속한 팀 목록. afterTeamChanged 무효화 대상. */
    all: () => [...qk.all, 'teams'] as const,
  },
} as const
```

### 6.2 무효화 매트릭스 (Plan B 업데이트)

> **Plan B 변경**: `folderTree()` → `[...qk.all, 'folders', 'children']` prefix 일괄 무효화로 교체.
> `afterTeamChanged` 헬퍼 신규 추가.

| 액션 | 낙관적 업데이트 | invalidate |
|---|---|---|
| **파일 업로드 성공** | `filesInFolder(target)` prepend | `folderChildren` prefix 전체 |
| **파일 이름 변경** | `fileDetail(id)`, 리스트 내 아이템 | - |
| **폴더 생성** | ❌ (단순화 — KISS) | 완료 후: `filesListPrefix(parentId)`, `folderChildren` prefix 전체, `folder(parentId)` (`invalidations.afterFolderCreated`) |
| **파일 이동** | ❌ (파괴적) | 완료 후: `filesListPrefix(from)`, `filesListPrefix(to)`, `folderChildren` prefix 전체, `fileDetail(id)` (`invalidations.afterFilesMoved`) |
| **파일 삭제 (휴지통)** | ❌ (파괴적) | 완료 후: `filesListPrefix(from)`, `trash()`, `folderChildren` prefix 전체 (`invalidations.afterDelete`) |
| **휴지통 복원** | ❌ | 완료 후: `trash()`, `filesListPrefix(parent)`, `folderChildren` prefix 전체 (`invalidations.afterRestore`) |
| **영구 삭제** | ❌ | 완료 후: `trash()` (`invalidations.afterPurge`) |
| **폴더 삭제** | ❌ | 완료 후: `folderChildren` prefix 전체, 하위 `filesListPrefix(*)` `removeQueries`, `trash()` |
| **권한 변경** | ❌ | **광역 무효화** (6.3절 참조) |
| **새 버전 업로드** | ❌ (current_version_id 바뀜) | 완료 후: `fileVersions(fileId)`, `fileDetail(id)`, `fileActivity(id, ...)` |
| **즐겨찾기 토글** | 리스트 아이템 플래그 | `quickAccess()` |
| **공유 생성 (F4)** | ❌ | 완료 후: `shares()` (by-me/with-me 동시) (`invalidations.afterShareCreate`) |
| **공유 해제 (F4)** | ❌ | 완료 후: `shares()` (by-me/with-me 동시) (`invalidations.afterShareRevoke`) |
| **팀 생성/멤버 변경** | ❌ | 완료 후: `workspaces.me()` (`invalidations.afterTeamChanged`) |
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

      // 3. 사이드바 folderChildren 전체 (안 보이던 폴더 보이거나, 보이던 폴더 사라짐)
      qc.invalidateQueries({ queryKey: [...qk.all, 'folders', 'children'] })

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

## 7. DnD 설계 (업로드 vs 이동 분리 + cross-workspace 제약)

두 개의 DnD 컨텍스트는 **이벤트 소스가 다름**:
- **OS → 브라우저** (업로드): 네이티브 `dragenter/dragover/drop`, `e.dataTransfer.types.includes('Files')`
- **브라우저 내부** (이동): dnd-kit 이벤트

> **Plan B 추가**: DnD 이동은 **same-workspace only**. cross-workspace hover 시 드롭 차단 + 시각 피드백.
> 공유받음(`shared`) 섹션은 drop 대상 불가 (re-share 금지).

### 7.1 MoveDragData — sourceWorkspace 필드 (Plan B)

```ts
// components/dnd/types.ts
export type MoveDragData = {
  type: 'move-files'
  ids: string[]
  sourceFolderId: string
  /** ids 중 폴더인 것만. self/descendant 판정에 사용. */
  containsFolderIds: string[]
  /**
   * 드래그 출발 workspace 정보. droppable이 cross-workspace 여부 판정에 사용 (Plan B).
   * shared에서 드래그하는 경우 kind='shared', id=null.
   */
  sourceWorkspace: { kind: 'department' | 'team' | 'shared'; id: string | null }
}
```

### 7.2 useFolderDroppable — isCrossWorkspace + isSharedTarget (Plan B)

`useFolderDroppable(droppableId, targetWorkspace?)` 반환값:
- `isCrossWorkspace`: `sourceWorkspace`와 `targetWorkspace` 불일치 시 true → 드롭 차단 + 시각 피드백
- `isSharedTarget`: target이 `shared` 섹션 폴더 → 항상 드롭 차단 (re-share 금지)
- `isInvalid`: self/descendant drop 차단 (기존)

**시각 피드백**:
- cross-workspace hover: 🚫 아이콘 overlay + 툴팁 "다른 workspace로 이동 불가. 컨텍스트 메뉴 '다른 workspace로 이동'을 사용하세요"
- shared target hover: 동일 🚫 + 툴팁 "공유받은 폴더로는 이동할 수 없습니다"

### 7.3 DnD 컨텍스트 + AppLayout

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

### 7.1 cross-workspace drop 차단 (Plan D §5.6)

dnd-kit `onDragEnd` 핸들러에서 source와 drop target의 `workspaceId`가 다르면 **drop 차단 + 사용자 안내**:

- **토스트**: "🚫 다른 workspace로 이동 불가"
- **툴팁 / 토스트 부가 설명**: "컨텍스트 메뉴 '다른 workspace로 이동'을 사용하세요"
- 드래그 시각적 피드백: drop target 위에 커서 있을 때 `data-cross-workspace` 속성 → CSS `cursor: not-allowed` + 반투명 오버레이

실제 cross-workspace 이동은 컨텍스트 메뉴 → `MoveToWorkspaceDialog` → `POST /api/folders/{id}/move` (`allowCrossScope: true`) 경로만 허용 (백엔드 서버 409 `ERR_CROSS_SCOPE_MOVE` 가드와 이중 방어).

> frontend 구현 활성화 시점: Plan B `WorkspaceFolderTree` 머지 후 별도 트랙 (Plan D Phase 7).

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

**100MB 사전 검증 (2026-07-02)**: `stores/upload.ts` `enqueue`가 `MAX_UPLOAD_SIZE_BYTES`
(`lib/uploadErrors.ts`, backend `spring.servlet.multipart.max-file-size: 100MB`와 동기화) 초과
파일을 서버 왕복 없이 즉시 `failed` + `too_large` 에러로 표면화한다 — 도크에 사유가 보이고
XHR 미기동. `retry()`는 `too_large` task를 재큐잉하지 않는다 (서버가 항상 거부).
모든 진입 경로(버튼/드롭/폴더 업로드)가 enqueue를 거치므로 단일 검증 지점.

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

### 9.5 다운로드 (M-Download)

`BulkActionBar` 다운로드 버튼은 단일 **파일** 선택 시 활성. `api.downloadFile(id)`이
programmatic anchor 클릭(`<a href="/api/files/{id}/download">` + body append + click +
remove)으로 backend `GET /api/files/{id}/download` (docs/02 §7.6.1)을 트리거.

- cookie 인증은 same-origin GET → 브라우저 자동 동봉 (별도 `withCredentials` 불요)
- RFC 5987 `Content-Disposition: attachment; filename*=UTF-8''...`을 backend가 처리하므로
  파일명 자동 적용
- 진행률은 브라우저 다운로드 매니저 책임 → fire-and-forget
- 가드: 단일 파일 선택만 활성. 폴더는 "파일만 다운로드 가능" tooltip, 다중은 "단일
  파일 선택 시 사용 가능", 캐시 미스(`useFilesInFolder` data undefined) disabled 폴백
- 다중 zip 다운로드는 별도 트랙(out of scope)

권한은 backend `hasPermission(#id, 'file', 'READ')` (ADR #36 — DOWNLOAD enum 미도입).
`usePermission().DOWNLOAD`(M8)는 UX 게이트, 진실의 출처는 backend READ 가드.

### 9.6 폴더 업로드 (디렉토리 구조 보존)

폴더(디렉토리)를 드롭하거나 선택하면 하위 구조를 대상 폴더 아래 그대로 재현하며 내부 파일을 업로드한다.
**프론트 오케스트레이션으로 구현하며 백엔드는 변경하지 않는다.**

#### 동작 흐름

```text
[drop: FileSystemEntry[]  |  input: File[].webkitRelativePath]
  → lib/folderUpload.extract*()   →  FolderUploadPlan { entries: {file, pathSegments[]}[], dirPaths: string[][] }
  → hooks/useFolderUpload.uploadFolder(plan, baseFolderId)
       1. dirPaths를 깊이별 그룹으로 (깊이 오름차순)
       2. 깊이별로 처리 — 같은 깊이(형제)는 Promise.all 병렬 생성, parent별 api.getFolderChildren는
          in-flight promise 캐시로 1회만 조회 → normalizeFileName 비교로 기존 폴더면 병합(id 재사용)
          / 없으면 api.createFolder → Map<pathKey, folderId>
       3. 파일을 resolved folderId별로 그룹핑 → useUpload.enqueue(files, folderId)  (기존 파이프라인)
       4. 생성 발생한 parentId마다 invalidations.afterFolderCreated
```

#### 진입점

- **드래그&드롭** — `useNativeFileDrop`이 drop 시점에 `dataTransfer.items[].webkitGetAsEntry()`를
  **동기** 캡처해 콜백에 `entries`로 전달(아래 ADR 참조). 디렉토리 entry가 있으면 폴더 경로,
  없으면 기존 flat `enqueue`.
- **버튼** — `SidebarNewButton` "폴더 업로드" 메뉴 → 별도 hidden `<input webkitdirectory>`.
  각 `File.webkitRelativePath`로 경로 복원.

#### 설계 결정 (ADR)

1. **백엔드 무변경 / 프론트 오케스트레이션** — 기존 `POST /api/folders`(`getFolderChildren`/`createFolder`)와
   `POST /api/files`(`enqueue`)만으로 충분. upload 엔드포인트에 `relativePath`를 추가하는 백엔드 확장은
   트랜잭션 범위·책임 분리 측면에서 거부. scope 상속·`UNIQUE(parent, normalized_name)`·409 RENAME_CONFLICT는
   백엔드가 그대로 보증한다 (docs/02 §3, §6).
2. **폴더 이름 충돌 = 병합(merge)** — 대상에 같은 이름 폴더가 이미 있으면 그 폴더 id를 재사용(중복 폴더 미생성).
   파일명 충돌만 기존 §9.2 `UploadConflictDialog`로 처리. 재업로드 시 자연스러운 동작.
3. **업로드 store 무변경** — 폴더를 먼저 전부 materialize한 뒤 해석된 folderId로 기존 `enqueue(files, folderId)`를
   호출. `UploadTask`에 path 필드를 추가하지 않는다 (파이프라인 무변경, §9.1 계약 유지).
4. **빈 폴더도 생성** — 디렉토리 경로는 내부 파일 유무와 무관하게 materialize.
5. **`DataTransferItemList` 수명** — drop 핸들러 반환 후 items가 무효화되므로 `webkitGetAsEntry()`는
   drop 시점에 동기 호출해 entry 참조를 캡처. 비동기 재귀(`file()`/`readEntries`)는 캡처한 entry로 수행.
   `readEntries()`는 한 번에 ≤100개만 반환하므로 empty까지 반복 호출한다.
6. **미지원 폴백** — `webkitGetAsEntry`/`webkitdirectory` 미지원 시 flat 파일 업로드로 폴백
   (사내 데스크탑 Chromium 가정, §실행 환경).

> 폴더 생성은 깊이별로 처리하되 같은 깊이(형제)는 병렬 — 벽시계 시간 ∝ 트리 깊이(폴더 수 아님). 깊이 방향은
> 부모 id 의존으로 직렬이 불가피. 동시성은 브라우저 호스트당 연결 수(~6)로 사실상 제한된다.

---

## 10. 검색 견고성 (v3 보강)

> **글로벌 단축키** (`useGlobalShortcuts`, 디자인 핸드오프 G3 — 2026-05-11):
> - `/`             → `app:focus-search` dispatch — input/textarea/contenteditable에서는 무시(editable 가드).
> - `⌘+K` / `Ctrl+K` → `app:focus-search` dispatch — **editable 가드 미적용** (다른 input에서도 검색 호출 가능, VS Code 패턴). modifier 외 조합(`Shift`/`Alt`)은 무시.
>
> TopBar `SearchBar` 의 우측 영역은 query 비어있고 unfocused일 때 `⌘K` kbd 칩, query 있을 때 clear 버튼(`X`) 노출. placeholder 텍스트는 "파일 검색"만 — 시각 hint는 kbd 칩이 담당.

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

### 11.1 RightPanel 파일 미리보기 (P4, ADR #51 — 2026-07-02)

`RightPanel`의 `PreviewCard`가 파일 타입별로 실제 미리보기를 렌더한다:

| 타입 | 동작 |
|---|---|
| 이미지 (png/jpeg/gif/webp) | 패널 내 `<img src="/api/files/{id}/download?disposition=inline">` 렌더. 클릭 시 새 탭. 로드 실패 시 플레이스홀더 폴백 (`key={file.id}`로 파일 전환 시 실패 상태 리셋) |
| PDF | "새 탭에서 미리보기" 버튼 → `window.open(inline URL, '_blank', 'noopener,noreferrer')`. 전역 `X-Frame-Options: DENY` 때문에 iframe 임베드 불가 — top-level 탐색은 무영향 |
| 그 외 / SVG | 기존 아이콘 플레이스홀더 유지. SVG는 script 실행 가능 → inline 화이트리스트 제외 (backend와 동기) |

- inline 허용 판정의 진실 출처는 **backend** `FileDownloadController.INLINE_SAFE_MIME` (docs/02 §7.6) —
  화이트리스트 밖 MIME은 서버가 attachment로 폴백하므로 frontend 목록(`INLINE_IMAGE_MIME`)은 UX 최적화용.
- wire 헬퍼: `api.previewFileUrl(id)` / `api.openFilePreview(id)` (`lib/api.ts`).
- Office 문서 인라인 미리보기(렌더러 필요)·그리드 썸네일은 별도 트랙 (§18 row 16).

---

## 12. 접근성 & 키보드 내비게이션

### 12.1 키맵

| 키 | List 모드 | Grid 모드 |
|---|---|---|
| ↑ ↓ | 행 ±1 이동 | columns 단위 ±1 이동 (column stride) |
| ← → | (no-op) | ±1 이동, row 경계에서 자연 wrap |
| Shift + ↑↓←→ | 범위 확장 | 범위 확장 |
| Ctrl/Meta + ↑↓ | 포커스만 이동 | 포커스만 이동 |
| Space | 선택 토글 | 선택 토글 |
| Enter | 열기 (폴더 진입 또는 `?file=` 설정) | 동일 |
| Delete | 휴지통으로 | 동일 |
| F2 | 이름 변경 | 이름 변경 |
| Ctrl/Meta + A | 전체 선택 | 전체 선택 |
| Esc | 선택 해제 / RightPanel 닫기 (`?file=` 제거) | 동일 |
| / | 검색창 포커스 | 동일 |
| ⌘K / Ctrl+K | 검색창 포커스 (editable 안에서도) | 동일 |
| ? | 단축키 cheat sheet 모달 open (editable 외) | 동일 |

> Grid 2D 내비게이션은 pure helper `frontend/src/lib/gridNav.ts:computeNextIndex`로 분리. ↓ overshoot 시 마지막 partial row에 항목이 있으면 `length-1`로 clamp, 없으면 stay. ↑은 첫 행에서 stay. pendingIds는 같은 stride 방향(↑/↓ = columns, ←/→ = 1)으로 skip하며 후보가 없으면 stay (M16VK).

> **Shortcut Cheat Sheet (2026-05-11)**: `?` 키 또는 TopBar 우측 Keyboard 아이콘 버튼 → `ShortcutsCheatSheet` 모달 open. 버튼은 `?` 단축키 미인지 사용자의 발견성(discoverability) 진입점 — `app:open-shortcuts` CustomEvent dispatch로 동일 모달 트리거. self-contained (props 없음, `(explorer)/layout.tsx`에 1회 마운트). 단축키 데이터는 `frontend/src/lib/keyboardShortcuts.ts` `KEYBOARD_SHORTCUTS` — **single source of truth** (본 §12.1 ↔ 코드 표현). 변경 시 양쪽 동기화 (CLAUDE.md §4 계약 파일 원칙).

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

- **URL** (Plan E, 2026-05-10 — workspace split, spec `2026-05-10-team-centric-pivot-plan-e-trash-workspace-split-design.md` §2.1):
  - `/trash` — redirect handler. 클라이언트에서 `useWorkspaces()` 응답을 보고 `router.replace`:
    - 부서 보유 → `/trash/d/:deptId` (MVP slug = workspace UUID, `workspacePath.ts` 주석 참조)
    - 부서 미보유 + 팀 N개 → 첫 활성 팀 우선, 없으면 첫 archived 팀 → `/trash/t/:teamId`
    - workspace 0 → `<EmptyWorkspacesState />` ("참여 중인 workspace가 없어 휴지통에 접근할 수 없습니다. 관리자에게 문의해 주세요.")
  - `/trash/d/:deptSlug` — 부서 휴지통 (탭 활성화: 부서)
  - `/trash/t/:teamSlug` — 팀 휴지통 (탭 활성화: 해당 팀; archived 팀도 진입 가능)
- **사이드바**: 단일 진입점 `<TrashLink />` href=`/trash` 그대로 (Plan B 구현). workspace별 휴지통은 사이드바에 노출하지 않고 탭 페이지에서 전환.
- **TrashWorkspaceTabs** (`components/trash/TrashWorkspaceTabs.tsx`, Plan E T12): 가로 탭 바.
  - 부서 1개 + 본인 팀 N개. archived 팀은 `opacity-60` + `🔒` prefix (clickable, listing 정상).
  - active 탭은 URL `useParams` 기반 (`aria-selected`).
  - source: `useWorkspaces()` (Plan B `GET /api/workspaces/me`).
- **archived 팀 페이지**: 페이지 상단 alert "이 팀은 archive되어 콘텐츠 복원이 불가능합니다." + `<TrashRowActions>` 의 복원 버튼 `disabled` (Plan E T13). 보안은 backend `TeamArchiveGuard`(`423 TEAM_ARCHIVED`) 가 책임 — UX 가드일 뿐.
- 행 액션: **원위치로 복원**, **영구 삭제**
- 삭제 직후 토스트의 **"되돌리기"** 버튼 (5초)
- **RESTORE_CONFLICT 다이얼로그** (v1.x M9 + Plan E T13 reason 분기): 행 복원 시 backend 가 409 `RESTORE_CONFLICT` envelope 을 반환하면 `<RestoreConflictDialog />` 가 열린다. body `details.reason` 으로 두 분기:
  - **`name_conflict`** (v1.x 기존): 원위치에 동일 이름 활성 항목 → 사용자에게 새 이름 입력. 기본 제안 `suggestRestoreName(name, type)` (file `report.pdf` → `report (1).pdf`, folder `Reports` → `Reports (1)`). 입력 후 `useRestoreItem.mutate({ ..., newName })` → backend body `{ name }` 로 재요청. 또 충돌 시 `RENAME_CONFLICT` → 다이얼로그 inline alert (유지). 다른 코드는 toast.error + 닫기.
  - **`scope_mismatch`** (Plan E T13 신규): 원위치 폴더가 다른 workspace 로 cross-workspace move (Plan D) 된 상태 → rename 으로 해결 불가. read-only 메시지 ("`'<name>'` 의 원위치가 다른 workspace로 이동되어 복원할 수 없습니다. 관리자에게 문의해 주세요.") + 닫기 버튼만 노출.
  - body `details` 추가 키: `expectedScopeType` / `expectedScopeId` / `actualScopeType` / `actualScopeId` (scope_mismatch 시). frontend 는 분기 메시지에만 사용 — 표시 X.
- BulkActionBar Undo 의 다건 복원 충돌은 다이얼로그 미적용 (v1.x 후속) — toast.error 메시지가 휴지통 페이지에서 행 단위 복원으로 안내.

> **Backend endpoints** (docs/02 §7.11):
> - `GET /api/trash?scopeType={department|team}&scopeId={uuid}&cursor=&type=` — list (Plan E T2). `scopeType` / `scopeId` **필수** — 누락 시 `422 VALIDATION_ERROR`. queryKey `qk.trashList(scopeType, scopeId)` (§6.1, Plan E T6 — 기존 무인자 `qk.trash()` 는 prefix 키로 invalidate 시 그대로 사용 가능).
> - `POST /api/files/:id/restore` / `POST /api/folders/:id/restore` — per-resource restore (A6). v1.x 부터 optional body `{ name?: string }` — `name` 미지정 시 원본 이름 그대로 복원, 충돌 envelope `RESTORE_CONFLICT` (`reason='name_conflict'`). `name` 지정 시 NFC 정규화 + UNIQUE 재검사, 충돌 envelope `RENAME_CONFLICT`. Plan E T4/T5 추가 검증: archived 팀 차단 (`423 TEAM_ARCHIVED`) + cross-workspace 원위치 mismatch (`409 RESTORE_CONFLICT` `reason='scope_mismatch'`).
> - `DELETE /api/trash/:type/:id` — manual purge, ADMIN only (A8, ADR #32, Plan E 변경 없음).
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

### 14.4 ShareDialog (F4 → F5 → A13 → F6 → A16)

`components/shares/ShareDialog.tsx` — 파일/폴더 공유 + by-me 목록 + revoke. F4(파일, 2026-05-01) → F5(폴더 양립, 2026-05-01) → A13(2026-05-01) `ShareDto` ↔ permissions join 복원으로 subject/preset 표시 부활 → F6(2026-05-01) A14 `GET /api/users/search`로 user picker 추가 → **A16(2026-05-01~02)** A16 `GET /api/departments/search` (docs/02 §7.15, ADR #37)을 활용해 subject picker에 **department 옵션** 추가 + ShareDto.subjectName surface.

- **트리거**:
  - 파일: `BulkActionBar` 단일 선택 시 `공유` 버튼 → `useShareUiStore.open({kind:'file', id, name})`.
  - 폴더: `Breadcrumb` 우측 `공유` 버튼(현재 폴더 = URL `folderId`, 비루트만) → `open({kind:'folder', id, name})`.
- **store 형상**: `target: ShareTarget = {kind:'file'|'folder', id, name}` discriminated. ShareDialog는 `target` 단일 선택자로 일반화.
- **mutation**: `useCreateShare` Vars `{target, req}` → target.kind === 'folder' ? POST `/api/folders/{id}/share` : POST `/api/files/{id}/share` (api.createFolderShares / createFileShares 분리).
- **subject picker (F6 → A16)** — `subjectType` 라디오 3종 (`everyone` | `user` | `department`), default `everyone`. **role은 v1.x backlog** — schema impedance(role enum vs role-grant lookup, ADR #37 결정 #5)로 UI 미노출. backend는 `subject_type='role'` persistable이므로 v1.x 활성화 시 frontend만 변경.
  - `user` 라디오 선택 시 `<UserSearchCombobox value={selectedUser} onChange={setSelectedUser}/>` 마운트.
  - `department` 라디오 선택 시 `<DepartmentSearchCombobox value={selectedDept} onChange={setSelectedDept}/>` 마운트 (A16).
  - 라디오 토글 시 inactive subject 리셋 (everyone 선택 → user/dept 둘 다 null, user 선택 → dept null, dept 선택 → user null).
  - submit 분기:
    - `everyone` → `subjects:[{type:'everyone'}]`
    - `user` + `selectedUser=null` → `toast.error('공유할 사용자를 선택해 주세요')` + 차단
    - `user` + `selectedUser` → `subjects:[{type:'user', id:selectedUser.id}]`
    - `department` + `selectedDept=null` → `toast.error('공유할 부서를 선택해 주세요')` + 차단 (A16)
    - `department` + `selectedDept` → `subjects:[{type:'department', id:selectedDept.id}]` (A16)
  - dialog 재오픈 시 `subjectType='everyone'` + `selectedUser=null` + `selectedDept=null` reset.
- **UserSearchCombobox** (`components/shares/UserSearchCombobox.tsx`, F6.3) / **DepartmentSearchCombobox** (`components/shares/DepartmentSearchCombobox.tsx`, A16.6):
  - WAI-ARIA 1.2 Combobox + Listbox 패턴 — `<input role="combobox" aria-expanded aria-controls aria-activedescendant>` + `<ul role="listbox">` + `<li role="option" aria-selected>`.
  - DepartmentSearchCombobox = UserSearchCombobox **1:1 답습**, 차이는 표시 필드만 (user: `displayName + email`, dept: `name` 단일).
  - **별도 컴포넌트 유지 (일반화 거부)** — KISS / ULTIMATE INVARIANT 5 (확장 전 검토). 추상화 정당화 3+ 규칙 (ADR #28 동형) 미충족 + 표시 필드 차이가 generic 도입 비용보다 작음.
  - 외부 a11y 라이브러리 거부 (KISS / ULTIMATE INVARIANT 5/3).
  - 키보드: ArrowDown/Up wrap-around, Enter 선택, Esc close (input 값 보존).
  - `useUserSearch`/`useDepartmentSearch` — debounce 300ms + minLen 2 + keepPreviousData + AbortSignal + staleTime 30s. normalize는 `q.trim().toLowerCase()` (NFC collapse 미적용).
  - 선택 후 input 변경 → `onChange(null)` (RenameDialog input-as-state 패턴).
  - **out-of-scope**: multi-chip / role 라벨 — backend wire에 없음 + MVP 단일 subject 공유로 충분.
- preset: `read | upload | edit | admin` 4값 (ADR #34, V5 CHECK는 SHARE 미지원).
- expiresAt: HTML5 datetime-local → `new Date(v).toISOString()`.
- 기존 by-me share 목록 매칭: `(s.fileId ?? s.folderId) === target.id` (wire `shares` 행은 file_id/folder_id XOR — V6 CHECK).
- 기존 share 행 표시 (A13 → A16): `subjectLabel(subjectType, subjectId, subjectName) · presetLabel(preset) · 만료/무기한 + 해제`. **subjectName 우선** (A16, ADR #37) — `subjectName != null`이면 그대로 표시. null fallback 시 subject UUID 머릿8자(가독성). everyone은 "모든 사용자".
- `해제` → `useRevokeShare`. 백엔드 `canRevoke`(sharedBy==me ‖ ADMIN)가 진실의 출처.
- 에러 envelope: 409 PERMISSION_CONFLICT / 403 PERMISSION_DENIED / 404 NOT_FOUND(파일|폴더 분기) / 그 외 → 한국어 toast.error.
- 무효화: §6.1 `qk.shares()` prefix 1회로 by-me/with-me 동시 갱신 (file/folder 무관 동일).
- **ShareDto wire** (14필드, backend `com.ibizdrive.share.ShareDto` record와 1:1):
  `{id, fileId|null, folderId|null, permissionId, sharedBy, message|null, expiresAt|null, createdAt, revokedAt|null, revokedBy|null, subjectType, subjectId|null, preset, subjectName|null}` — active 행에서 revoked* 항상 null. A13에서 backend가 `permissions` row를 join해 `subjectType`/`subjectId`/`preset` 3 필드를 surface. **A16(ADR #37)**에서 `subjectName` 추가 — user→display_name, department→name, everyone/lookup miss → null.
- **SharesTable** (`components/shares/SharesTable.tsx`): with-me 목록. A13에서 컬럼 4열로 복원: `항목 | 공유한 사람 | 권한 | 만료`. preset은 한국어 라벨(읽기/업로드/편집/관리). 항목 셀은 file/folder 아이콘 분기(`folderId !== null`).

### 14.5 GrantPermissionDialog (v1.x — Phase B/C/D 완료 2026-05-11)

> **Status: Phase A spec(2026-05-09) + Phase B 골격(2026-05-11) + Phase C subject 분기(2026-05-11) + Phase D `ResourcePermissionsList` 통합(2026-05-11) 완료.**
> Backend `POST /api/{folders|files}/{id}/permissions` (`PermissionController#grant`)는 완비 — Phase A1.4 grant endpoint(2026-04~).
> Phase D에서 `ResourcePermissionsList` 헤더에 "권한 부여" 버튼 + dialog trigger wire가 추가되어 다이얼로그가 사용자 가시 화면으로 노출됨(이전까지 dead code). 운영자 우회(SQL/ShareDialog)는 unblock.

#### 14.5.1 Scope

**단일 자원 단위 grant**만 — `ResourcePermissionsList` (RightPanel 권한 탭) 안에 "권한 부여" 버튼 → `GrantPermissionDialog`. **admin/permissions 페이지의 전역 grant는 미도입** (resource picker 부재라 v2.x).

#### 14.5.2 Architecture

```
ResourcePermissionsList (existing, components/files/)
 └─ "권한 부여" 버튼 (NEW, PERMISSION_ADMIN 보유 시 노출)
     └─ GrantPermissionDialog (NEW)
         ├─ SubjectPicker (USER/DEPARTMENT/ROLE/EVERYONE 라디오)
         │   ├─ UserSearchCombobox (재사용, components/shares/)
         │   ├─ DepartmentSearchCombobox (재사용, components/shares/)
         │   ├─ ROLE select — MEMBER/AUDITOR/ADMIN 3 enum
         │   └─ EVERYONE radio (subject_id NULL)
         ├─ PresetSelector (READ/UPLOAD/EDIT/SHARE/ADMIN 5종 select)
         ├─ ExpiresAtInput (datetime-local, optional, default 무기한)
         └─ Submit → api.grantPermission → onSuccess → invalidate qk.resourcePermissions
```

**ShareDialog (§14.4) 패턴 답습 — UserSearchCombobox / DepartmentSearchCombobox 재사용**, KISS / ULTIMATE INVARIANT 5 (확장 전 검토). 차이: preset 5값 (ShareDialog는 4값, V5 CHECK가 share table에 SHARE preset 미지원), ROLE 분기 추가.

#### 14.5.3 API wrapper (api.ts NEW)

```ts
async grantPermission(
  resource: 'folder' | 'file',
  resourceId: string,
  body: GrantPermissionRequest,
): Promise<PermissionDto> {
  const csrf = readCookie('XSRF-TOKEN') ?? ''
  const res = await fetch(
    `/api/${resource}s/${encodeURIComponent(resourceId)}/permissions`,
    {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        'X-CSRF-TOKEN': csrf,
      },
      body: JSON.stringify(body),
    },
  )
  if (!res.ok) throw await buildApiError(res, `grantPermission failed: ${res.status}`)
  const env = (await res.json()) as { permission: PermissionDto }
  return env.permission
}
```

**X-CSRF-TOKEN 헤더 필수** (csrf-mutation-sweep PR #121 sweep 결과 적용 — docs/03 §2.2 callout).

#### 14.5.4 wire body (`GrantPermissionRequest`)

```ts
interface GrantPermissionRequest {
  subject: { type: 'user' | 'department' | 'everyone'; id: string | null }
  preset: 'read' | 'upload' | 'edit' | 'share' | 'admin'
  expiresAt?: string  // ISO 8601 datetime, undefined = 무기한
}
```

backend `Preset.from(wire)` lower-case 매칭 (`backend/permission/Preset.java`). `subject.id`는 EVERYONE 시 null, USER/DEPARTMENT 시 UUID.

**ROLE/TEAM subject 제외 — v2.x backlog**: backend `permissions.subject_id` 컬럼이 UUID이고 `PermissionRepository.findEffective`가 `subject_type IN ('user', 'everyone', 'department')`만 매칭하므로(docs/03 §3.4.3) ROLE/TEAM grant row는 INSERT돼도 평가되지 않는다. spec/impl 정합 위해 wire 타입에서도 제외. ROLE/TEAM 평가 도입은 backend resolver 확장이 선결(별도 트랙).

#### 14.5.5 Subject 분기

| subjectType | input | submit body |
|---|---|---|
| `everyone` | radio (default) | `{ type:'everyone', id: null }` |
| `user` | UserSearchCombobox (A14, debounce 300ms + minLen 2) | `{ type:'user', id: selectedUser.id }` |
| `department` | DepartmentSearchCombobox (A16) | `{ type:'department', id: selectedDept.id }` |

**v2.x backlog (Phase C 미포함)**:
- `role` (MEMBER/AUDITOR/ADMIN) — backend가 INSERT는 허용(V5 CHECK)하나 평가 미도입(`PermissionResolver`/`findEffective` 미참조). 도입 시 backend resolver 확장 + UUID-encoded role enum 매핑 또는 `subject_id` 타입 분리(별도 트랙).
- `team` — Plan C에서 `shares` endpoint에 추가됐으나 `permissions` grant 평가는 여전히 user/dept/everyone만. team folder 권한은 `WorkspaceMembershipResolver`(membership-기반)로 자동 부여되므로 grant row 별도 부여 불필요.

#### 14.5.6 Preset 라벨 (한국어)

| wire | 라벨 | 포함 권한 |
|---|---|---|
| `read` | 읽기 | READ + DOWNLOAD |
| `upload` | 업로드 | + UPLOAD |
| `edit` | 편집 | + EDIT, MOVE, DELETE |
| `share` | 공유 | + SHARE |
| `admin` | 관리 | + 모든 권한 (PURGE 제외) |

#### 14.5.7 Error envelope mapping

| status / code | 원인 | UX |
|---|---|---|
| `409 PERMISSION_CONFLICT` | 동일 (resource, subject) 중복 grant | inline alert "이미 부여된 grant — 기존 row 만료 후 재부여 또는 row 수정" |
| `400 VALIDATION_ERROR` | preset/expiresAt 형식 오류, subject 누락 | field-level error |
| `403 PERMISSION_DENIED` | PERMISSION_ADMIN 미보유 (운영자가 권한 ADMIN 없는 자원 시도) | toast.error + 다이얼로그 닫기 |
| `404 NOT_FOUND` | 자원 자체 미존재 (race) | toast.error + 다이얼로그 닫기 + invalidate parent |

#### 14.5.8 캐시 무효화

```ts
onSuccess: () => {
  queryClient.invalidateQueries({ queryKey: qk.resourcePermissions(resource, resourceId) })
  queryClient.invalidateQueries({ queryKey: qk.adminPermissions() })  // /admin/permissions viewer
  queryClient.invalidateQueries({ queryKey: qk.permissions(resourceId) })  // useEffectivePermissions (자기 권한)
}
```

§6.1 prefix 무효화. 운영자가 자기에게 권한 부여 시 즉시 UI 권한 갱신.

#### 14.5.9 Phase 분할

본 spec(§14.5)은 **Phase A — 설계만(2026-05-09)**. 후속 phase 진척:

- **Phase B (2026-05-11 완료)**: `api.grantPermission` + `useGrantPermission` + `GrantPermissionDialog` 골격 (subject = `everyone` 만, preset/expiresAt 포함). 회귀 가드 vitest 18건.
- **Phase C (2026-05-11 완료)**: subject 분기 — everyone/user/department 3종 라디오 + `UserSearchCombobox`(A14) + `DepartmentSearchCombobox`(A16) 재사용. ROLE/TEAM은 평가 미도입이라 v2.x backlog로 분리(§14.5.4 callout). 추가 회귀 가드 9건(라디오 노출 2 / 미선택 inline 2 / 분기 body shape 2 / reset 1 / 400 1 / generic fallback 1) — 누적 vitest 25건.
- **Phase D (2026-05-11 완료)**: `ResourcePermissionsList` 통합 — 헤더 우측 "권한 부여" 버튼(`aria-haspopup=dialog`) + `useState` open / `GrantPermissionDialog` 마운트. 가드: `usePermission().PERMISSION_ADMIN` 보유 + `!isLoading && !isError`(loading/error 동안 trigger 차단). 추가 회귀 가드 4건(노출/클릭/admin 미보유/error). 다이얼로그가 사용자 가시 화면으로 노출됨.

#### 14.5.10 결정/편차

- **Subject 3종만 지원 (USER/DEPARTMENT/EVERYONE)** — Phase A spec은 4종(USER/DEPT/ROLE/EVERYONE)으로 작성됐으나 Phase B 완료 후 spec/impl 정합 검증에서 ROLE/TEAM grant가 backend에서 평가되지 않음을 확인(`PermissionRepository.findEffective`가 user/everyone/department만 매칭, docs/03 §3.4.3). **2026-05-11 spec 정정** — ROLE/TEAM은 v2.x backlog로 분리, wire 타입과 UI에서 모두 제외(§14.5.4 callout). ROLE 평가 도입 시 backend resolver 확장이 선결.
- **단일 다이얼로그 (별도 검색 모달 없음)** — KISS, ShareDialog와 동질 UX. 사용자 검색은 인라인 dropdown.
- **admin/permissions 페이지 전역 grant 미도입** — resource picker (folder tree + file search) 대형 컴포넌트 → v2.x. 단일 자원 grant는 RightPanel에서 충분.
- **Preset 5값 (SHARE 포함)** — ShareDialog와 분리. 자원 권한 grant는 share-grant 가능까지 포함하므로 5값 모두 노출.
- **403 → 다이얼로그 닫기** — 사용자 인지 후 회수 (§14.1 비파괴적 액션 패턴 정합).
- **invalidate 3종** — ResourcePermissionsList + admin/permissions + 자기 effective. 운영자가 자기에게 부여 시 즉시 권한 화면 갱신.

#### 14.5.11 회귀 가드 spec (Phase B 시 적용)

- `api.grantPermission.test.ts`: POST 메서드 + URL + X-CSRF-TOKEN 헤더 + body 형태 + 409/403/404/400 envelope.
- `GrantPermissionDialog.test.tsx`: subject 라디오 분기 + preset select + expiresAt parse + submit body shape + 409 inline alert.
- `useGrantPermission.test.tsx`: onSuccess invalidate 3종 + onError envelope pass-through.

### 14.6 2인 승인 framework — 202 APPROVAL_REQUIRED 응답 처리 (ADR #47, docs/02 §2.11)

dual-approval framework Tier 0 mutation(role_change / retention_change / trash_purge)이 gate=ON 상태일 때, backend는 **200 OK + entity 대신 202 ACCEPTED + APPROVAL_REQUIRED envelope**을 반환한다:

```json
{
  "error": {
    "code": "APPROVAL_REQUIRED",
    "message": "이 작업은 2인 승인이 필요합니다",
    "details": { "approvalId": "<uuid>", "expiresAt": "<ISO>" }
  }
}
```

#### 14.6.1 처리 패턴 (3 layer)

**Layer 1 — API wrapper** (`lib/api.ts`)
- 각 Tier 0 mutation wrapper(`adminUpdateUser` / `adminBulkTrash` / `updateAdminTrashPolicy`)는 `await throwIfApprovalRequired(res)`를 `res.ok` 분기 직전에 호출.
- 202 + envelope 매칭이면 `ApprovalRequiredError`(`lib/errors.ts`) throw. 일반 4xx는 기존 `buildApiError` 흐름.

**Layer 2 — Mutation hook** (`hooks/useAdminUpdateUser.ts` 등)
- `onError`에서 `err instanceof ApprovalRequiredError` 분기 → `showApprovalRequiredToast(err, actionLabel)`(`lib/approvalToast.ts`) 호출.
- 일반 에러는 무처리(호출자가 `mutation.error`로 수령). 캐시 무효화 미수행(`onSuccess` 미도달).

**Layer 3 — Component (form / page)**
- mutation `onError`에서 `ApprovalRequiredError` 분기 시 form 값/선택 상태/dialog 닫힘만 처리 — 사용자가 승인 페이지 확인 후 재시도 가능하도록 입력 유지.
- 일반 에러는 기존 inline alert / toast 분기 유지.

#### 14.6.2 토스트 UX

`showApprovalRequiredToast(err, actionLabel)`:
- `toast.info` 종류 (실패도 성공도 아닌 "보류" 상태).
- 메시지: `"승인 요청이 등록되었습니다 ({actionLabel}). 두 번째 관리자의 승인을 기다립니다."`
- action 버튼 label="승인 페이지", onClick → `window.location.href = '/admin/approvals/:approvalId'`.
- duration: 8000ms (일반 5초보다 길게).

#### 14.6.3 actionLabel 매핑

| mutation hook | actionLabel | backend actionType |
|---|---|---|
| `useAdminUpdateUser` | `'사용자 역할 변경'` | `role_change` |
| `useAdminBulkTrash` (action='purge') | `'휴지통 영구 삭제'` | `trash_purge` |
| `useUpdateAdminTrashPolicy` | `'휴지통 보존 정책 변경'` | `retention_change` |

새 Tier 0 mutation 추가 시 본 표 + hook의 `showApprovalRequiredToast` 호출 + 본 패턴 답습.

#### 14.6.4 회귀 가드

- `errors.test.ts`: `ApprovalRequiredError` 생성자/속성 + `parseApprovalRequired` 6 케이스(valid/다른 code/details 누락 2종/JSON 부재/비-JSON).
- `approvalToast.test.ts`: `toast.info` 호출 + actionLabel 포함 + action 버튼 onClick navigation + duration=8000.
- `api.adminUsers.test.ts` / `api.adminTrashBulk.test.ts` / `api.updateAdminTrashPolicy.test.ts`: 202 envelope → `ApprovalRequiredError` throw + approvalId/expiresAt 보존.
- mutation hook 테스트: 202 응답 시 invalidate 미호출 + toast.info 호출 + form 상태 유지.

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
    // 폴더 이벤트 — 사이드바 children 전체 + 해당 폴더 상세 + 부모 폴더의 자식 목록 무효화
    // Plan B: qk.folderTree() 대신 folderChildren prefix 전체 무효화
    qc.invalidateQueries({ queryKey: [...qk.all, 'folders', 'children'] })
    scope.folderIds.forEach((fid) => {
      qc.invalidateQueries({ queryKey: qk.folder(fid) })
      qc.invalidateQueries({ queryKey: qk.filesInFolder(fid), exact: false })
    })
    return
  }

  if (PERMISSION_EVENTS.includes(type)) {
    const p = payload as { resource: 'folder' | 'file'; resourceId: string }
    qc.invalidateQueries({ queryKey: qk.effectivePermissions(p.resourceId) })
    // 권한 변경은 가시성에도 영향 — 사이드바 children 전체 + 목록 무효화
    // Plan B: qk.folderTree() 대신 folderChildren prefix 전체 무효화
    qc.invalidateQueries({ queryKey: [...qk.all, 'folders', 'children'] })
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
| `FOLDER_*` | `[...qk.all, 'folders', 'children']` (prefix 전체), `qk.folder(scope.folderIds[i])`, `qk.filesInFolder(scope.folderIds[i])` |
| `PERMISSION_*` | `qk.effectivePermissions(payload.resourceId)`, `[...qk.all, 'folders', 'children']` (prefix 전체), `qk.filesInFolder(scope.folderIds[i])` |

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
/admin/audit-logs         전체 감사 로그 (검색/필터/export)     [구현됨 → AdminAudit]
/admin/download-logs      다운로드 이력                        [v1.x 미구현 — AdminAudit `file.downloaded` filter로 대체]
/admin/permission-logs    권한 변경 이력                        [v1.x 미구현 — AdminAudit `permission.*` filter로 대체]
/admin/storage-usage      사용량 대시보드                      [v1.x 미구현 — `/api/admin/dashboard/summary.storage.usedBytes` + AdminStorage(`/admin/storage`)로 대체]
```

> **deprecation note (2026-05-12 tier0-drift-sweep)** — `/admin/audit-logs` 외 3 페이지는 spec만 명시되었고 frontend route / backend endpoint 모두 0건. AdminAudit + AdminStorage가 동일 정보를 통합 제공 → 실 구현 deferred. 추후 운영 요구 발생 시 별도 트랙 + ADR 신설. `docs/02 §7.12` deprecation note와 동기.

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

## 17. 코드 템플릿 — workspace pivot 라우팅 (Plan B)

> **Plan B 변경**: §17.1~17.3은 기존 `/files/[...parts]` 기반 템플릿에서 workspace prefix 체계로 교체.
> `FolderTree` / `useFolderTree` / `folderPath.ts` / `useViewStore.expandedFolderIds` 모두 폐기.
> `VIRTUAL_ROOT_ID('root')` 사용 금지.

> **TopBar 레이아웃 (디자인 핸드오프 G2 / 2026-05-11)**: TopBar는 `grid auto / 1fr / auto` 3-column 구조 (`prototype/styles.css` L134). 좌측 햄버거 (`useSidebarChromeStore.toggle`, `aria-pressed={collapsed}`), 중앙 SearchBar (`max-w-[560px] mx-auto`), 우측 Keyboard 도움말 버튼 + TweaksPanel + Avatar. 도움말 버튼은 `app:open-shortcuts` CustomEvent dispatch → `ShortcutsCheatSheet` 모달 (§12.1 callout). (explorer)/layout.tsx의 `<aside>`는 `collapsed`에 따라 `w-[248px]` ↔ `w-0` 폭 transition (`transition-[width] duration-200 ease-out`) + `overflow-hidden` + `aria-hidden`.

### 17.1 workspace prefix catch-all 라우트 (Plan B)

```tsx
// app/(explorer)/d/[deptId]/[[...parts]]/page.tsx  (팀: t/[teamId]/[[...parts]])
'use client'
import { useParams } from 'next/navigation'
import { FileTable } from '@/components/files/FileTable'
import { Breadcrumb } from '@/components/folders/Breadcrumb'
import { BulkActionBar } from '@/components/files/BulkActionBar'
import { RightPanel } from '@/components/detail/RightPanel'

export default function DeptFilesPage() {
  const { deptId, parts } = useParams<{ deptId: string; parts?: string[] }>()
  const folderId = parts?.[0] ?? null  // workspace landing: null
  // canonical redirect 는 서버 컴포넌트에서 처리 (ClientFilesPage 참조)

  return (
    <>
      <Breadcrumb />
      <BulkActionBar />
      <FileTable folderId={folderId ?? deptId} />
      {/* RightPanel: ?file= query param 구독 */}
    </>
  )
}
```

실제 구현 파일: `frontend/src/app/(explorer)/d/[deptId]/[[...parts]]/page.tsx` 및 `ClientFilesPage.tsx`.

### 17.2 workspace path 헬퍼 (Plan B — folderPath.ts 대체)

```ts
// lib/workspacePath.ts
export type SidebarSectionKind = 'department' | 'team' | 'shared'

export type WorkspaceLocator =
  | { kind: 'department' | 'team'; workspaceId: string }
  | { kind: 'shared' }

export interface ParsedWorkspaceUrl {
  section: SidebarSectionKind
  workspaceId: string | null   // shared는 null
  folderId: string | null      // workspace landing은 null
  slugPath: string[]
}

/**
 * workspace + folderId + slugPath → URL 문자열 생성.
 *
 * 예시:
 *   buildWorkspacePath({ kind: 'department', workspaceId: 'dept-1' }, 'folder-abc', ['영업팀', '계약서'])
 *   // → "/d/dept-1/folder-abc/%EC%98%81%EC%97%85%ED%8C%80/%EA%B3%84%EC%95%BD%EC%84%9C"
 */
export function buildWorkspacePath(
  loc: WorkspaceLocator,
  folderId: string | null,
  slugPath: string[],
): string

/**
 * pathname → ParsedWorkspaceUrl. 매칭 실패 시 null.
 *
 * 예시:
 *   parseWorkspaceUrl('/d/dept-1/folder-abc/영업팀')
 *   // → { section: 'department', workspaceId: 'dept-1', folderId: 'folder-abc', slugPath: ['영업팀'] }
 */
export function parseWorkspaceUrl(pathname: string): ParsedWorkspaceUrl | null
```

실제 구현 파일: `frontend/src/lib/workspacePath.ts`.

### 17.3 useCurrentFolder (workspace params 적용)

`parts?.[0]`은 여전히 folderId. workspace landing(`/d/:deptId` 만)에서는 `parts`가 없어 `folderId=null` →
workspace root folder ID를 `GET /api/workspaces/me` 응답의 `department.rootFolderId`에서 취득.

### 17.4 useOpenFile (RightPanel query param 동기화)

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

### 17.4a `?action=` — dashboard quick action deep link

`/d/...` 와 `/t/...` workspace folder 페이지는 `?action=new-folder` query 를 받으면 `useQuickActionParam` hook 이 감지 → `CreateFolderDialog` 를 mount + `router.replace` 로 `?action` 만 즉시 제거(다른 query 보존). 발급자는 현재 dashboard `WelcomeHeader` 의 "새 폴더" 버튼 하나(`router.push(workspaceRoot?action=new-folder)`). `/trash`, `/shared`, `/favorites`, `/admin` 등 다른 위치에서는 무시. 알 수 없는 action 값도 silent skip. 새 action 케이스 추가 시 hook 의 switch 분기 확장 + 본 절 갱신.

spec: `docs/superpowers/specs/2026-05-15-quick-action-dialog-design.md`.

### 17.5 WorkspaceFolderTree (lazy expand — FolderTree 대체)

```tsx
// components/sidebar/WorkspaceFolderTree.tsx
// - useFolderChildren(scopeType, workspaceId, parentId) 로 lazy 자식 조회
// - useSidebarTreeStore.expandedFolderIds / toggleFolder 로 expand 상태 관리
// - 링크: buildWorkspacePath({ kind, workspaceId }, folderId, slugPath)
// - 드롭 대상: useFolderDroppable(droppableId, { kind, id: workspaceId })
//   → isCrossWorkspace / isSharedTarget 시각 피드백 표시
```

실제 구현 파일: `frontend/src/components/sidebar/WorkspaceFolderTree.tsx`.

### 17.6 SidebarSections (3-section shell)

실제 구현 파일: `frontend/src/components/sidebar/SidebarSections.tsx`.
섹션 구성: 부서(`WorkspaceSection`) + 팀 N개(`WorkspaceSection[]`) + 공유받음(`SharedWithMeSection`).

### 17.7 Breadcrumb (folder API data에서 derive)

```tsx
// components/folders/Breadcrumb.tsx
// buildWorkspacePath 를 사용해 링크 생성 (folderPath.ts → workspacePath.ts 교체)
```

### 17.8 Providers

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
| 13.1 | **Variant 시스템 + Tweaks** (M13.1, 완료 2026-05-08) | `[data-variant]` 4종(default/notion/dropbox/terminal) + `lib/variant.ts` 5함수 + `useVariant` 훅 + `layout.tsx` FOUC init script + `TweaksPanel` (TopBar settings → popover, ThemeToggle 임베드 + variant 라디오 4종). 누락 토큰 보충 (`--accent-text`, `--success-soft`). 옵션 B (KISS): terminal 17 selector 폰트 override 는 `:root --font-sans` 토글 + body letter-spacing 두 줄로 흡수. 자세한 내용은 `docs/design-system.md` §11. |
| 13.2 | **Density 토글** (G5, 완료 2026-05-11) | `[data-density]` 3종(compact=28 / default=base / comfortable=40) + `lib/density.ts` 5함수 + `useDensity` 훅 + `layout.tsx` FOUC init script + `TweaksPanel` density 라디오 3종 추가. 우선순위: `[data-density]` (사용자 명시) > `[data-variant]` default `--row-h`. CSS cascade로 variant rules 뒤에 배치하여 override. |
| 14 | **Visual Identity** | TopBar(검색/테마 토글/아바타) + Lucide 아이콘 도입 + FileRow 밀도 재조정 + StatusBar 하단. M13 토큰 위에서 JSX 추가 |
| 15 | **Layout Extras** | SortChip(정렬 드롭다운) + ViewSwitch(List/Grid 토글) + StorageBar(사이드바 하단) + RightPanel 탭(세부정보/버전/활동/권한) |
| 16 | **Grid View** | FileTable에 grid 모드 추가 (썸네일 카드형). M14의 ViewSwitch에서 토글. 본체 closed 2026-04-29 (PR #16). 가상화 closed 2026-05-01 (M16V follow-up: `useGridColumns` + row 단위 `useVirtualizer` + 키보드 scrollToIndex `Math.floor(idx/columns)` 매핑). v1.x 잔여: 2D 키보드 wrap / DnD / 썸네일 / 가변 높이. |
| M_team-pivot-frontend-foundation | **Team-Centric Pivot — Plan B Frontend Foundation** (진행 중, 2026-05-09~) | workspace prefix 3-route (`/d/*`, `/t/*`, `/shared/*`) + SidebarSections 3-section shell + WorkspaceFolderTree lazy expand + useSidebarTreeStore persisted + DnD same-workspace 제약 + buildWorkspacePath + qk.workspaces.me/folderChildren/teams.all + afterTeamChanged invalidation. 브랜치: `feat/team-centric-pivot-plan-b-frontend`. |
| v1.x | **tus 재개 업로드** | UploadStore 계약 유지, 훅만 교체 |
| v1.x | **SSE 실시간 동기화** | `file.created` 등 이벤트 반영, 폴백 폴링 |

---

## 19. 최상위 원칙 (리마인더)

1. **URL이 workspace + folder를 소유한다** (Plan B 갱신). `/d/:workspaceId/[folderId/slug...]` 형태로 workspace 컨텍스트와 folderId 모두 URL에서 도출. Zustand에 workspace/folderId 복제 금지. `VIRTUAL_ROOT_ID('root')` 폐기 — workspace root는 서버 UUID.
2. **RightPanel은 query param** (`?file=xxx`) 일관 사용. parallel route 쓰지 않음.
3. **프론트 권한은 UX용, 백엔드가 보안의 최종 방어**. 403은 일급 에러.
4. **낙관적 업데이트는 비파괴적 액션만**. 파괴적 액션(이동/삭제/권한)은 pending 상태 처리.
5. **같은 folderId + normalizedFileName = 동일 파일**. NFC + lowercase 정규화 일관 적용.
6. **문자열 정규화는 프론트/백엔드 동일 함수**. `files.normalized_name` 컬럼.
7. **DnD 컨텍스트 두 개는 절대 섞지 않음** (OS→브라우저 / 브라우저 내부). DnD 이동은 same-workspace only — cross-workspace는 컨텍스트 메뉴 전용.
8. **가상화에는 `aria-rowcount/rowindex` 필수**.
9. **삭제는 휴지통 + 5초 Undo + 30일 보관**. 즉시 영구 삭제는 관리자만.
10. **감사 로그는 사용자 activity와 분리**. 도메인에 따라 MVP 포함 여부 결정.
11. **MVP 범위는 파일 크기/팀 규모/컴플라이언스로 결정**. 대용량·실시간·감사는 선택적.

---

## 20. User Home Dashboard (root `/`)

> ADR #48 (2026-05-14) — root `/` redirect → personal dashboard. design-spec `2026-05-14-user-home-dashboard-design.md`.

### 20.1 진입점

- `frontend/src/app/(explorer)/page.tsx` — workspace redirect 로직 제거. `<HomeDashboard />` 마운트.
- (explorer) layout 의 AuthGuard + 사이드바 + TopBar reuse — dashboard 진입자는 사이드바에서 workspace 선택.

### 20.2 위젯 inventory

| # | 컴포넌트 | hook | 데이터 source |
|---|---|---|---|
| ① | `<WelcomeHeader>` | `useMe`, `useWorkspaces` | 사용자명 + 기본 workspace 메타 |
| ② | `<StarredCard>` | `useMyFavorites` (PR #243) | `FavoriteItem[]` slice(0, 8) |
| ③ | `<QuotaCard>` | `useStorageQuota` | `{ usedBytes, totalBytes }` |
| ④ | `<SharedWithMeCard>` | `useMySharedWithMe(5)` | `MySharedWithMeItem[]` |

- 시각: admin overview 의 `SectionCard` 패턴을 dashboard 전용 `<DashboardCard>` 로 재구현 (admin.css 결합 회피).
- 레이아웃: `flex max-w-[1400px] flex-col gap-4` + 1열/2열 grid (StarredCard 50% · QuotaCard 50%, SharedWithMeCard 풀폭).

### 20.3 QueryKey 정합

- `qk.myFavorites()` (PR #243 정의) reuse — 별 토글 mutation 후 `invalidations.afterStarToggle` 가 무효화.
- `qk.mySharedWithMe()` 신규 — permission grant/revoke mutation hook 에서 후속 invalidate (follow-up).

### 20.4 Empty/Error/Loading

각 카드는 `<DashboardCard>` body 안에 inline state 메시지. spec §6 매트릭스 참고. recent files 는 `FILE_VIEWED` audit (ADR #9) blocker 로 v1.x deferral.

---

## 다음에 만들 수 있는 것

- **FileTable virtualization + 키보드 내비 전체 구현** (TanStack Virtual + aria + 키맵)
- **업로드 시스템 전체 구현** (multipart + Zustand + ConflictDialog + Queue Dock)
- **권한 매트릭스 + 백엔드 엔드포인트 권한 검증 계약서**
- **DB 스키마** (files/folders/versions/permissions/audit_log) + normalized_name 정책
- **감사 로그 관리자 페이지** (/admin/audit-logs 필터/export)
- **SSE 서버 측 Node.js 구현** + 재연결 정책

필요한 것을 지정하면 바로 이어서 작성.
