# M7 — DnD 이동 (dnd-kit + 다이얼로그 듀얼 진입) 설계

작성일: 2026-04-25
마일스톤: M7 (docs/01 §18의 #7)
범위 근거: docs/01 §1 원칙 #3·#6·#7·#10, §6.2, §7, §8.2, docs/02 §6.4

---

## 1. 범위

### 포함 (M7)

- `@dnd-kit/core` 단일 의존성 추가 (sortable 불필요)
- DndContext를 explorer layout에 마운트 (설계 §7의 "AppLayout"에 해당)
- 드래그 소스: `FileRow` (파일/폴더 공통)
- 드롭 타겟: `FolderNodeItem` (FolderTree), `Breadcrumb` 링크 항목, `FileRow`(`item.type === 'folder'`인 행)
- BulkActionBar "이동" 버튼 → **폴더 선택 다이얼로그** (키보드 기본 경로)
- DnD와 다이얼로그 모두 **동일 `useMoveBulk`** mutation으로 수렴
- 자기 자신 / 후손 폴더 이동 차단 (UI invalid 표시 + mock API 재검증)
- 같은 폴더 이동 = no-op (mutation 호출 안 함, 조용히 무시 + 토스트 후속)
- 비선택 행 드래그 시 **그 행만** 이동 (selection 전체 아님)
- pending 시각화 (opacity + 스피너 + aria-disabled, M4 deleteBulk와 동일 패턴)
- 캐시 무효화 (§6.2): 출발 폴더 + 도착 폴더 `filesInFolder` + `folderTree`
- DragOverlay = **카운트 배지**만 (행 복제 X). `role="status"`, `aria-live="polite"`
- 접근성: 드롭 타겟 `aria-dropeffect="move"` (deprecated이나 의도 표명 목적), 다이얼로그 포커스 트랩/Tab/Esc/Enter

### 제외 (M7 이후)

- Cut/Paste 전역 단축키 (Ctrl+X / Ctrl+V) — 전역 키맵 충돌 우려, 별도 마일스톤
- 오토스크롤 (긴 FolderTree를 드래그 중 스크롤) — MVP는 수동 스크롤
- 멀티 폴더 동시 이동 시 "일부 성공/일부 실패" 부분 성공 UX — MVP는 all-or-nothing (백엔드 트랜잭션 전제)
- 실제 백엔드 연결 (mock API가 트랜잭션/동시성 시뮬레이션)
- 토스트 시스템 (M_toast 별도). 현재는 `console.warn`로 자리만
- 터치 센서 (TouchSensor) — 데스크톱 MVP
- DragOverlay의 복잡한 애니메이션 / 스프링

---

## 2. 아키텍처

```
(explorer)/layout.tsx
└── DndContext                      신규. onDragStart/onDragEnd/onDragCancel
    ├── <aside> Sidebar
    │   └── FolderTree
    │       └── FolderNodeItem      수정: useDroppable (id: folder-<id>)
    ├── <main>
    │   └── ClientFilesPage
    │       ├── Breadcrumb          수정: 각 항목 useDroppable
    │       ├── FolderToolbar
    │       ├── BulkActionBar       수정: "이동" 버튼 → openMoveDialog
    │       ├── FileTable
    │       │   └── FileRow         수정: useDraggable + (type='folder'면 useDroppable)
    │       ├── RightPanel
    │       ├── UploadQueueDock
    │       ├── UploadConflictDialog
    │       └── MoveFolderDialog    신규. 전역 단일 (moveUi 스토어)
    └── <DragOverlay>               신규. 카운트 배지
```

### DndContext 위치 결정

- 후보 A — **explorer/layout.tsx** (채택): sidebar(FolderTree) + main(ClientFilesPage) 전체를 감싸므로 드래그/드롭이 두 영역 간 자유롭게 교차 가능
- 후보 B — ClientFilesPage 내부: 사이드바가 바깥이라 드롭 타겟이 될 수 없음 → **탈락**
- 후보 C — RootLayout (app/layout.tsx): explorer 외 페이지에도 DndContext가 달려 불필요 → **탈락**

근거: 설계 §7의 AppLayout은 실제 코드 구조상 explorer/layout.tsx와 정확히 대응.

---

## 3. 파일 구조

### 신규 파일

```
frontend/src/
  stores/moveUi.ts                              # 다이얼로그 open/close 상태
  hooks/useMoveBulk.ts                          # mutation (DnD/다이얼로그 공용)
  hooks/useDragPayload.ts                       # 드래그 시작 시 ids/sourceFolderId 결정
  lib/folderTreeUtils.ts                        # descendant 판정 유틸 (프론트)
  components/files/MoveFolderDialog.tsx
  components/dnd/DndProvider.tsx                # DndContext 래퍼 (센서/콜백)
  components/dnd/MoveDragOverlay.tsx            # 카운트 배지
  components/dnd/useFolderDroppable.ts          # FolderNode/Breadcrumb/FileRow 공용 훅
```

### 수정 파일

```
frontend/src/app/(explorer)/layout.tsx         # DndProvider로 감싸기
frontend/src/components/files/FileRow.tsx      # useDraggable + 폴더 행 useDroppable
frontend/src/components/files/BulkActionBar.tsx # "이동" 핸들러 = openMoveDialog
frontend/src/components/folders/FolderTree.tsx  # FolderNodeItem에 useDroppable
frontend/src/components/folders/Breadcrumb.tsx  # 각 항목 useDroppable
frontend/src/app/(explorer)/files/[...parts]/ClientFilesPage.tsx
                                               # <MoveFolderDialog /> 마운트
frontend/src/lib/api.ts                        # moveFiles mutation 추가 (+ MOCK_TREE 이동 반영)
frontend/src/lib/queryKeys.ts                  # (기존 키 재사용, 변경 없음 예상)
frontend/package.json                           # @dnd-kit/core 추가
```

### 테스트 파일

모두 Vitest + @testing-library/react.

```
stores/moveUi.test.ts
hooks/useMoveBulk.test.ts
hooks/useDragPayload.test.ts
lib/folderTreeUtils.test.ts
lib/api.moveFiles.test.ts
components/files/MoveFolderDialog.test.tsx
components/dnd/DndProvider.integration.test.tsx   # drag → drop → mutation 호출 경로
```

---

## 4. 데이터 모델

### 드래그 payload

```ts
// components/dnd/types.ts
export type MoveDragData = {
  type: 'move-files'
  ids: string[]            // 이동 대상. 비선택 행 드래그 시 [rowId], 선택에 포함되면 모두
  sourceFolderId: string   // 드래그 시작 시점 폴더 (invalidate에 사용)
  containsFolderIds: string[]  // ids 중 폴더인 것만. descendant 판정 소스
}
```

- dnd-kit `useDraggable({ id, data })`의 `data`로 전달
- drop 시 `event.active.data.current`로 꺼냄

### 드롭 타겟 id 규약

- 모든 드롭 타겟 id는 `folder-<folderId>` 문자열 패턴
- 드롭 핸들러가 prefix로 파싱하여 targetFolderId 추출
- 향후 `trash-<id>`, `share-<id>` 등 확장 여지

### moveUi 스토어

```ts
// stores/moveUi.ts
type MoveUiState = {
  isMoveDialogOpen: boolean
  moveIds: string[]
  moveSourceFolderId: string | null
  openMoveDialog: (ids: string[], sourceFolderId: string) => void
  closeMoveDialog: () => void
}
```

- UploadConflictDialog처럼 전역 단일 인스턴스 관리
- 다이얼로그 열기/닫기 외 상태는 mutation 훅이 소유

---

## 5. useMoveBulk 훅

### 시그니처

```ts
// hooks/useMoveBulk.ts
type Vars = {
  ids: string[]
  targetFolderId: string
  sourceFolderId: string
}

export function useMoveBulk() {
  const qc = useQueryClient()
  const markPending = useSelectionStore((s) => s.markPending)
  const unmarkPending = useSelectionStore((s) => s.unmarkPending)
  const clear = useSelectionStore((s) => s.clear)

  return useMutation({
    mutationFn: ({ ids, targetFolderId }: Vars) =>
      api.moveFiles(ids, targetFolderId),

    onMutate: ({ ids }: Vars) => {
      markPending(ids)
    },

    onSuccess: async (_data, { ids, sourceFolderId, targetFolderId }: Vars) => {
      // §6.2 무효화 매트릭스: 출발/도착 + folderTree + ids별 fileDetail
      await Promise.all([
        qc.invalidateQueries({ queryKey: [...qk.files(), 'list', sourceFolderId] }),
        qc.invalidateQueries({ queryKey: [...qk.files(), 'list', targetFolderId] }),
        qc.invalidateQueries({ queryKey: qk.folderTree() }),
        ...ids.map((id) => qc.invalidateQueries({ queryKey: qk.fileDetail(id) })),
      ])
      unmarkPending(ids)
      clear()
    },

    onError: (_err, { ids }: Vars) => {
      unmarkPending(ids)
      // 선택 복구는 하지 않음 — 이동은 복구 복잡 (원래 폴더로 돌아갔을 때만 의미)
      console.warn('moveBulk 실패', { ids })
    },
  })
}
```

### 원칙 준수

- 원칙 #3 — **낙관적 업데이트 없음**. `onMutate`는 pending 마킹만, 실제 리스트 변경은 서버 응답 후 invalidate
- 원칙 #6 — 충돌/제약 검증은 서버(mock api) 책임. 훅은 결과 invalidate만
- 원칙 #10 — UI 레벨 차단이 있어도 api에서 재검증

---

## 6. useDragPayload 훅

```ts
// hooks/useDragPayload.ts
export function useDragPayload(rowId: string, rowParentId: string): MoveDragData {
  const selectedIds = useSelectionStore((s) => s.ids)
  const currentFolder = useCurrentFolder()
  const filesQuery = useFilesInFolder(rowParentId)  // 타입 조회용

  const ids = selectedIds.has(rowId)
    ? Array.from(selectedIds)
    : [rowId]

  const containsFolderIds = (filesQuery.data ?? [])
    .filter((f) => ids.includes(f.id) && f.type === 'folder')
    .map((f) => f.id)

  return {
    type: 'move-files',
    ids,
    sourceFolderId: currentFolder.folderId,
    containsFolderIds,
  }
}
```

- 드래그 시작 시점에 선택 여부를 체크하여 "단일 행 vs 선택 전체" 결정
- `containsFolderIds`는 descendant 판정을 위한 소스 폴더 id 모음 (drop 타겟이 이들의 후손인지 검사)

---

## 7. DnD Provider

```tsx
// components/dnd/DndProvider.tsx
export function DndProvider({ children }: { children: React.ReactNode }) {
  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: { distance: 5 },  // 클릭 5px 이동 전엔 선택 제스처
    }),
  )
  const moveBulk = useMoveBulk()
  const { folderId } = useCurrentFolder()
  const [activeData, setActiveData] = useState<MoveDragData | null>(null)

  const handleDragStart = (e: DragStartEvent) => {
    setActiveData(e.active.data.current as MoveDragData)
  }

  const handleDragEnd = (e: DragEndEvent) => {
    setActiveData(null)
    const data = e.active.data.current as MoveDragData | undefined
    const overId = e.over?.id
    if (!data || typeof overId !== 'string') return
    if (!overId.startsWith('folder-')) return
    const targetFolderId = overId.slice('folder-'.length)

    // 같은 폴더 = no-op
    if (targetFolderId === data.sourceFolderId) return

    // invalid 재검증 (UI가 막았어도 방어적)
    if (data.containsFolderIds.includes(targetFolderId)) return
    // 후손 검사는 useFolderDroppable가 disabled로 막음. 이중 검증은 api에 맡김

    moveBulk.mutate({
      ids: data.ids,
      targetFolderId,
      sourceFolderId: data.sourceFolderId,
    })
  }

  const handleDragCancel = () => setActiveData(null)

  return (
    <DndContext
      sensors={sensors}
      onDragStart={handleDragStart}
      onDragEnd={handleDragEnd}
      onDragCancel={handleDragCancel}
    >
      {children}
      <DragOverlay dropAnimation={null}>
        {activeData && <MoveDragOverlay count={activeData.ids.length} />}
      </DragOverlay>
    </DndContext>
  )
}
```

### 센서 결정

- **PointerSensor + activationConstraint.distance=5px**만 사용
- 이유: FileRow는 이미 클릭(선택) 핸들러가 있음. 5px 이동 전엔 드래그 시작 안 함
- `KeyboardSensor`는 제외 — 다이얼로그가 키보드 기본 경로이므로 중복 모달리티 불필요. dnd-kit 키보드 센서는 focus 기반 타겟 순회 UX가 별도 학습 필요
- `TouchSensor`는 M7 이후

### DragOverlay

```tsx
// components/dnd/MoveDragOverlay.tsx
export function MoveDragOverlay({ count }: { count: number }) {
  return (
    <div
      role="status"
      aria-live="polite"
      className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded bg-accent text-accent-fg text-[12.5px] font-medium shadow-lg"
    >
      📎 {count}개 항목 이동 중
    </div>
  )
}
```

- `dropAnimation={null}` — 드롭 성공 시 스프링백 애니메이션 생략 (카운트 배지는 의미 없음)

---

## 8. useFolderDroppable 훅

세 위치(FolderTree/Breadcrumb/FileRow-folder)에서 재사용.

```ts
// components/dnd/useFolderDroppable.ts
import { useDndContext, useDroppable } from '@dnd-kit/core'
import { useFolderTree } from '@/hooks/useFolderTree'
import { isSelfOrDescendantOfAny } from '@/lib/folderTreeUtils'
import type { MoveDragData } from './types'

export function useFolderDroppable(folderId: string) {
  const { active } = useDndContext()
  const { data: tree } = useFolderTree()
  const dragData = active?.data.current as MoveDragData | undefined

  const isInvalid = dragData
    ? isSelfOrDescendantOfAny(tree, dragData.containsFolderIds, folderId)
    : false

  const { isOver, setNodeRef } = useDroppable({
    id: `folder-${folderId}`,
    disabled: isInvalid,
  })

  return { isOver, setNodeRef, isInvalid, isDragging: !!dragData }
}
```

- `useDndContext`로 현재 드래그의 `active` 접근 (dnd-kit 공식 API)
- `disabled: isInvalid`로 dnd-kit가 드롭 이벤트 자체를 차단
- 만약 dnd-kit가 `disabled` 동적 변경에 문제를 보이면(§19 리스크), `disabled`를 false로 두고 DndProvider.onDragEnd에서 재검증 + UI만 invalid 표시. 이 경우에도 api 재검증이 안전망 (원칙 #10)
- 반환 필드 사용처:
  - `setNodeRef`: 타겟 DOM 노드 바인딩
  - `isOver`: 드롭 호버 시각화
  - `isInvalid`: 회색/비활성 시각화
  - `isDragging`: 드래그 진행 중인지 (droppable 시각화를 드래그 중에만 켜기 위함)

### Invalid 표시 (UI 레벨)

- `isInvalid === true` → `opacity-50 cursor-not-allowed bg-surface-2` (hover 강조 없음)
- `isOver && !isInvalid` → `bg-accent-soft ring-2 ring-accent` (기존 hover 대체)
- `!isOver` → 평상 스타일 유지

---

## 9. lib/folderTreeUtils.ts

```ts
// lib/folderTreeUtils.ts
export function isDescendantOf(
  tree: FolderNode,
  ancestorId: string,
  candidateId: string,
): boolean {
  // tree에서 ancestorId 서브트리 루트 찾기 → candidateId가 서브트리 내부인지
  const sub = findNode(tree, ancestorId)
  if (!sub) return false
  return containsNode(sub, candidateId)
}

export function isSelfOrDescendantOfAny(
  tree: FolderNode | undefined,
  folderSourceIds: string[],
  targetFolderId: string,
): boolean {
  if (!tree) return false
  if (folderSourceIds.includes(targetFolderId)) return true  // self
  return folderSourceIds.some((src) => isDescendantOf(tree, src, targetFolderId))
}
```

- 파일 이동은 `containsFolderIds === []`이므로 self/descendant 검사 항상 false → 모든 타겟 valid (같은 폴더는 DndProvider의 no-op 체크가 처리)
- 폴더 이동 시에만 트리 순회. N이 작으므로 O(트리크기) 허용

---

## 10. FileRow 수정

### 드래그 소스

```tsx
const dragData = useDragPayload(item.id, item.parentId)
const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
  id: `row-${item.id}`,
  data: dragData,
  disabled: isPending,
})
```

- `isPending`이면 드래그 disabled (이동 중 재이동 방지)
- `isDragging` 시 원본 행은 `opacity-40` (실제 배지는 DragOverlay가 그림)

### 드롭 타겟 (type === 'folder')

```tsx
const droppable = useFolderDroppable(item.id)
// item.type === 'folder' 일 때만 ref 병합
const rowRef = item.type === 'folder' ? mergeRefs([setNodeRef, droppable.setNodeRef]) : setNodeRef
```

- 자기 자신에게 드롭 방지는 `useFolderDroppable`의 disabled/invalid로 자동 처리

---

## 11. FolderTree / Breadcrumb 수정

### FolderNodeItem

```tsx
const { setNodeRef, isOver, isInvalid } = useFolderDroppable(node.id)
// div 래퍼 ref에 바인딩, isOver/isInvalid 기반 클래스 토글
```

- 기존 Link 클릭 동작은 유지
- 드래그 중에만 droppable 시각화

### Breadcrumb

- 마지막 항목(현재 폴더) 제외, 나머지 Link들에 `useFolderDroppable` 적용
- 마지막 항목은 현재 폴더이므로 "같은 폴더로 이동" = no-op (DndProvider에서 차단)

---

## 12. BulkActionBar 수정

```tsx
const openMoveDialog = useMoveUiStore((s) => s.openMoveDialog)
const { folderId } = useCurrentFolder()

const handleMove = () => {
  openMoveDialog(ids, folderId)
}
```

- `can.move` 조건은 기존 유지
- 버튼 disabled 조건 없음 (열리는 다이얼로그에서 타겟 선택)

---

## 13. MoveFolderDialog

### 트리거

`moveUi.isMoveDialogOpen === true` 일 때 렌더 (ClientFilesPage에 마운트).

### 구조

```
┌─ "<N>개 항목 이동" (aria-labelledby)
│
│  대상 폴더를 선택하세요:
│  ┌──────────────────────────┐
│  │ ○ 📁 내 드라이브          │   ← FolderTree 복제 (라디오)
│  │   ○ 📁 영업팀            │
│  │     ○ 📁 계약서          │   (invalid 시 disabled + 회색)
│  │   ○ 📁 인사팀            │
│  └──────────────────────────┘
│
│  [취소]  [이동]  ← Enter 기본
└─
```

- 네이티브 `<dialog>` 또는 role="dialog" + aria-modal div
- 포커스 트랩: 열릴 때 첫 유효 라디오로 포커스
- Esc → closeMoveDialog
- Enter → "이동" 확정 (선택된 targetFolderId 있을 때만)
- invalid 옵션은 라디오 disabled + aria-disabled + 툴팁 "자기 자신/하위 폴더로는 이동할 수 없습니다"
- 같은 폴더(sourceFolderId) 선택 시 "이동" 비활성화 (no-op 방지)

### 확정 플로우

```ts
const onConfirm = () => {
  if (!selectedTargetId || selectedTargetId === moveSourceFolderId) return
  moveBulk.mutate({ ids: moveIds, sourceFolderId: moveSourceFolderId, targetFolderId: selectedTargetId })
  closeMoveDialog()
}
```

- mutation은 비동기. pending 표시는 FileRow 레벨에서 이미 동작
- 다이얼로그는 즉시 닫힘 → 진행 상황은 행 스피너로 시각화

---

## 14. Mock API — `moveFiles`

```ts
// lib/api.ts
async moveFiles(
  ids: string[],
  targetFolderId: string,
): Promise<{ movedIds: string[] }> {
  await new Promise((r) => setTimeout(r, 400))

  // 0) 타겟 폴더 존재 여부
  const targetExists =
    targetFolderId === 'root' || findNode(MOCK_TREE, targetFolderId)
  if (!targetExists) throw { status: 404, code: 'TARGET_NOT_FOUND' }

  // 1) self / descendant 검증 (원칙 #10 — UI와 독립)
  for (const id of ids) {
    if (id === targetFolderId) {
      throw { status: 400, code: 'MOVE_INTO_SELF' }
    }
    const node = findNode(MOCK_TREE, id)   // 폴더인 경우에만 트리에 존재
    if (node && containsNode(node, targetFolderId)) {
      throw { status: 400, code: 'MOVE_INTO_DESCENDANT' }
    }
  }

  // 2) 같은 폴더 이동은 204 성공 (no-op 보수적 처리)
  //    단, DndProvider/Dialog가 이미 막으므로 도달 드묾

  // 3) MOCK_FILES 의 parentId 갱신
  //    + 폴더는 MOCK_TREE 에서도 이동
  for (const id of ids) {
    const fileIdx = MOCK_FILES.findIndex((f) => f.id === id)
    if (fileIdx !== -1) {
      MOCK_FILES[fileIdx].parentId = targetFolderId
    }
    // 폴더인 경우 MOCK_TREE에서도 분리 → targetFolderId 밑으로 붙이기
    relocateInTree(MOCK_TREE, id, targetFolderId)
  }

  return { movedIds: ids }
},
```

### relocateInTree 유틸

```ts
function relocateInTree(tree: FolderNode, nodeId: string, newParentId: string): void {
  // 1) 기존 부모에서 children 배열에서 제거, 노드 보존
  // 2) newParentId 노드 찾기 → children에 push
  // 3) node.parentId = newParentId
  // 발견 못 하면 no-op (MOCK_FILES에만 존재하는 파일)
}
```

### 에러 코드

docs/02 §8 표준에 추가:

| code | status | 설명 |
|---|---|---|
| `TARGET_NOT_FOUND` | 404 | 이동 대상 폴더가 존재하지 않음 |
| `MOVE_INTO_SELF` | 400 | 자기 자신으로 이동 시도 |
| `MOVE_INTO_DESCENDANT` | 400 | 하위 폴더로 이동 시도 |

원칙 #12에 따라 docs/02 §8에 병행 업데이트.

---

## 15. Cache Invalidation

| 이벤트 | 무효화 |
|---|---|
| moveBulk 성공 | `qk.filesInFolder(sourceFolderId)`, `qk.filesInFolder(targetFolderId)`, `qk.folderTree()`, 각 id의 `qk.fileDetail(id)` |
| moveBulk 실패 | 없음 (pending만 해제) |

docs/01 §6.2 매트릭스 부합.

### filesInFolder 무효화 방식

`[...qk.files(), 'list', folderId]` prefix로 매치 (sort/dir 조합 모두 포함). useDeleteBulk 기존 패턴과 동일.

---

## 16. 원칙 체크리스트

| 원칙 | 준수 |
|---|---|
| #1 URL folderId canonical | ✅ `sourceFolderId`는 드래그 시점 URL folderId. store에 중복 저장 안 함 (moveUi는 다이얼로그 순간만) |
| #2 RightPanel query param | N/A |
| #3 낙관적 업데이트 비파괴적만 | ✅ 이동은 pending 마킹만, 서버 응답 후 invalidate |
| #6 DB 제약 = 진실 | ✅ UI 차단 + mock api 재검증. 실제 백엔드도 동일 계약 |
| #7 DnD 컨텍스트 분리 | ✅ dnd-kit은 이동 전용, window 네이티브(업로드)와 드롭 타겟 영역 비중첩 보장 |
| #8 aria-rowcount/rowindex | N/A (기존 FileTable 유지) |
| #10 파괴적 액션 서버 재검증 | ✅ `api.moveFiles`가 self/descendant 재검증 |
| #11 정규화 프론트/백엔드 동일 | N/A (이름 변경 없음) |
| #12 에러 코드 계약 | ✅ 3개 신규 코드 docs/02 §8 동기화 |

---

## 17. 접근성

### DragOverlay

- `role="status"` + `aria-live="polite"` → 스크린리더가 드래그 시작을 알림
- 문구: "N개 항목 이동 중"

### Droppable

- `aria-dropeffect="move"` 속성 부여 (deprecated이나 의도 표명; 서버 수신 없어도 가독성 제공)
- invalid 타겟: `aria-disabled="true"` + `title` 툴팁

### MoveFolderDialog

- `role="dialog"`, `aria-modal="true"`, `aria-labelledby`, `aria-describedby`
- Tab 순환이 다이얼로그 내부에 갇힘
- 첫 포커스 = 첫 유효 라디오 (현재 폴더 = sourceFolderId 제외)
- Esc = 취소, Enter = 적용
- 라디오 그룹은 `role="radiogroup"` + `aria-labelledby`

### 키보드 사용자

- DnD 키보드 센서 없음. **다이얼로그가 유일한 키보드 경로**
- 이 제약을 사용자 메모에 명시: "이동은 마우스 드래그 또는 BulkActionBar → 이동 버튼(키보드 접근 가능) 중 하나"

---

## 18. DoD (검증 시나리오)

### 자동 (Vitest)

**a. `stores/moveUi.test.ts`**
- `openMoveDialog(ids, src)` → state 반영
- `closeMoveDialog()` → reset

**b. `lib/folderTreeUtils.test.ts`**
- `isDescendantOf(tree, 'folder_sales', 'folder_contracts')` === true
- `isDescendantOf(tree, 'folder_sales', 'folder_hr')` === false
- `isSelfOrDescendantOfAny(tree, ['folder_sales'], 'folder_sales')` === true (self)
- 빈 배열 → false

**c. `lib/api.moveFiles.test.ts`**
- 정상 파일 이동 → MOCK_FILES parentId 갱신 + `movedIds` 반환
- 폴더 이동 → MOCK_TREE 구조 변경 + 하위 파일들은 parentId 유지 (부모 폴더만 이동)
- 자기 자신 이동 → `MOVE_INTO_SELF` throw
- 후손 폴더 이동 → `MOVE_INTO_DESCENDANT` throw
- 없는 targetFolderId → `TARGET_NOT_FOUND`
- 배열 일부가 폴더+일부는 파일 혼재 시 모두 이동

**d. `hooks/useMoveBulk.test.ts`**
- onMutate 시 markPending 호출
- onSuccess 시 4개 쿼리 키 invalidate 호출, unmarkPending + clear
- onError 시 unmarkPending만

**e. `hooks/useDragPayload.test.ts`**
- 선택에 rowId 포함 → ids = selection 전체
- 선택에 없음 → ids = [rowId]
- containsFolderIds는 filesInFolder 캐시에서 type='folder' 필터링

**f. `components/files/MoveFolderDialog.test.tsx`**
- isOpen true 시 렌더
- 트리 복제 + invalid 라디오 disabled
- 현재 폴더 라디오는 disabled (sourceFolderId)
- "이동" 클릭 시 useMoveBulk.mutate 호출 + closeMoveDialog
- Esc → close
- Tab 순환 확인 (first → last → first)

**g. `components/dnd/DndProvider.integration.test.tsx`**
- PointerSensor distance=5px 확인 (4px 이동 시 드래그 시작 안 함)
- drag FileRow → drop FolderNode → moveBulk.mutate 호출 (ids, source, target)
- 같은 폴더 drop → mutate 호출 안 함
- containsFolderIds와 target 일치 → mutate 호출 안 함

### 수동 (브라우저)

h. FileRow 단일 드래그 → FolderTree의 폴더에 드롭 → 진행 스피너 후 목록에서 제거, 타겟 폴더 이동 시 나타남
i. 3개 선택 후 한 행 드래그 → DragOverlay "3개 항목 이동 중" 표시 → 드롭 → 3개 모두 이동
j. 비선택 행 드래그 → 그 행만 이동 (선택 3개 무시)
k. BulkActionBar "이동" → 다이얼로그 열림 → 폴더 선택 → Enter → 이동 확정
l. 폴더를 자기 자신으로 드래그 시도 → invalid 회색, 드롭 안 됨
m. `folder_sales`를 `folder_contracts`(후손)로 드래그 → invalid
n. 다이얼로그에서 후손/자기 폴더 라디오 → disabled
o. 같은 폴더에 드롭 → 아무 일 안 일어남 (mutation 호출 없음)
p. 다이얼로그 Tab 순환 / Esc 취소 / Enter 확정 — 키보드만으로 이동 완주
q. 드래그 중 FolderTree 스크롤 수동 가능 (오토스크롤은 없음)
r. 드래그 중 폴더 Breadcrumb에 드롭 → 이동
s. 드래그 중 FileTable의 폴더 행에 드롭 → 이동
t. 드래그 중 파일 행(type='file')에 드롭 → 드롭 안 됨 (droppable 아님)
u. 이동 중(pending) 행은 opacity 0.5 + 스피너, 재드래그 시도 시작 안 함

### DoD 공통

- `pnpm typecheck && pnpm lint && pnpm test` 전부 통과 (기존 76 + M7 신규)
- 수동 h~u 전부 통과
- `docs/progress.md` M7 세션 기록 추가
- `docs/02 §8` 에 3개 신규 에러 코드 반영
- `docs/01 §18` 로드맵에서 M7 체크

---

## 19. 리스크 및 결정

### 결정: DndContext = explorer/layout.tsx

대안: ClientFilesPage 내부. 사이드바 droppable 불가 → 탈락.

### 결정: 드래그 payload에 `sourceFolderId` 저장

대안: drop 시점에 다시 조회. 드래그 중 사용자가 URL을 바꿀 수 있음 (드래그 중에도 브라우저 뒤로가기 가능) → 스냅샷이 안전.

### 결정: PointerSensor만 + activationConstraint 5px

대안: KeyboardSensor 추가. 키보드 경로는 다이얼로그가 담당 → 드래그 키보드 센서는 중복이며 학습 부담.

### 결정: MoveFolderDialog는 FolderTree 복제 (별도 컴포넌트)

대안: 기존 FolderTree 재사용. 기존 컴포넌트는 `Link` 네비게이션이 기본 동작이라 라디오 선택 UX와 충돌 → 별도.

### 결정: 낙관적 업데이트 금지 (원칙 #3)

사용자가 즉시 피드백을 원할 수 있으나, 이동 실패 시 롤백 UX가 복잡 (어느 폴더에서 나타나야 하나?). Pending 표시로 충분히 반응적.

### 결정: `applyToAll`처럼 같은 폴더 드롭 경고 토스트 없음

MVP는 조용히 무시. 토스트 시스템 도입 시점(M_toast)에 "같은 폴더입니다" 추가.

### 리스크: dnd-kit `useDroppable`의 `disabled` 동적 변경 동작

드래그 중 `active` 데이터에 따라 `disabled`가 바뀜. dnd-kit 내부 재평가가 올바른지 검증 필요.

완화: 통합 테스트에서 확인. 문제 시 `disabled` 대신 핸들러에서 검증하고 UI 클래스만 invalid로 표시.

### 리스크: relocateInTree의 동시성

mock api는 단일 JS 스레드 = 동시성 이슈 없음. 실제 백엔드 연결 시 docs/02 §6.4 트랜잭션(SELECT FOR UPDATE + parent_id 업데이트) 구현 필수.

### 리스크: 드래그 중 업로드 Overlay와 시각 충돌

업로드 Overlay는 `dataTransfer.types.includes('Files')`로 OS→브라우저만 감지. dnd-kit 드래그는 브라우저 내부 이벤트이므로 트리거되지 않음 (원칙 #7 검증 완료).

---

## 20. 미래 영향 (M7 이후)

- **실제 백엔드 연결** — `api.moveFiles` 내부만 HTTP 호출로 교체. `useMoveBulk`/UI 변경 없음
- **Toast 시스템 (M_toast)** — `useMoveBulk` onError에서 toast 호출, 같은 폴더 드롭 시 "같은 폴더입니다" 안내
- **TouchSensor** — mobile 지원. `useSensors`에 추가만
- **오토스크롤** — dnd-kit의 `autoScroll` 옵션 활성화. FolderTree 긴 경우 유용
- **Cut/Paste 단축키 (M_keyboard)** — 전역 키맵 정리와 함께
- **복사 (move → copy)** — `useCopyBulk` 훅 신규, payload type 'copy-files' 추가
- **대용량 선택 (가상화된 FileTable에서 100+ 선택)** — DragOverlay 카운트 배지가 이미 대응

---

## 부록 A. docs/01 §7 대비 차이

| 항목 | §7 설계 | M7 구현 |
|---|---|---|
| DndContext 위치 | AppLayout | explorer/layout.tsx (동일 개념, 실제 파일명) |
| DragOverlay | "드래그 미리보기" | 카운트 배지만 (행 복제 없음) |
| KeyboardSensor | 언급 없음 | 미채택. 다이얼로그로 대체 |
| 드롭 타겟 | FolderNode | FolderNode + Breadcrumb + FileRow(folder) 3종 |

§7은 spec 승인 후 "M7 구현 노트"로 보강 예정.
