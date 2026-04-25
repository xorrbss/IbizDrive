# M16 Grid View — 설계 (2026-04-25)

## 1. 범위

M15 ViewSwitch URL 인프라 위에 Grid 모드 구현. List와 동일한 데이터/선택/키보드 머신을 공유.

## 2. 컴포넌트 구조

```
FileTable (기존 — useViewParam으로 분기)
├── view='list': 가상화 list (현재 그대로)
└── view='grid': <FileGrid items={items} ... />
    └── <FileCard item={...} ... /> × N
```

- **FileGrid**: CSS grid (`auto-fill, minmax(172px, 1fr)`) 컨테이너. 가상화 없음 (MVP 성능 한계 — 문서화)
- **FileCard**: 4:3 thumbnail + meta. 큰 Lucide 아이콘 + 폴더는 폴더 아이콘, 이미지는 image 아이콘 placeholder. (실제 썸네일은 v1.x)

## 3. 키보드 네비게이션

**M16 범위는 linear nav만** — list와 동일하게 ↑↓로 prev/next 이동. 2D 네비게이션(같은 행 내 ←→)은 v1.x로 이연 (열 개수가 viewport에 따라 동적이라 복잡)

`Enter`/`Space`/`Ctrl+A`/`Esc`/`F2`/`Delete` 모두 동일

## 4. 데이터/상태 공유

FileTable이 가진 모든 hook 호출(`useFilesInFolder`, `useSortParams`, `useSelectionStore`, ...)을 그대로 유지하고, 렌더 분기만 하단 영역에서 적용. 즉:

```tsx
const { view } = useViewParam()
// ... 모든 기존 hook + handlers 그대로 ...

const grid = (
  <FileGrid
    items={items}
    focusedIndex={focusedIndex}
    selectedIds={selectedIds}
    pendingIds={pendingIds}
    onClick={handleRowClick}
    onDoubleClick={handleOpen}
    onKeyDown={handleKeyDown}
    scrollRef={scrollRef}
  />
)

else body = view === 'grid' ? grid : list
```

aria 속성:
- grid 컨테이너: `role="grid"` + `aria-label="파일 목록"`
- 각 카드: `role="gridcell"` + `tabIndex={isFocused ? 0 : -1}` + `aria-selected`

(grid에서는 row가 동적이라 `aria-rowindex`/`aria-rowcount` 생략 — list 한정)

## 5. 비범위

- **가상화** — 폴더당 1k+ 카드 시 성능 문제. v1.x에서 IntersectionObserver 또는 `react-virtual`의 grid 모드 도입
- **2D arrow nav** — viewport 폭 의존이라 ResizeObserver 필요
- **드래그 이동** — DnD는 list만 (M7 그대로). grid 시 dragSource 미적용 (FileCard에 useDraggable 없음). 이동은 BulkActionBar/F2 우회로
- **검색 결과의 grid 모드** — SearchResults는 list 고정 (검색 결과는 부모 폴더명 메타가 유용해 list가 적합)

## 6. 검증

- typecheck/lint/test
- 192 + ~5 = ~197 tests
- 수동 확인: ?view=grid → 카드 표시, ?view=list → 기존 list

## 7. 새 파일

- `src/components/files/FileGrid.tsx` + test
- `src/components/files/FileCard.tsx`

## 8. 수정 파일

- `src/components/files/FileTable.tsx` — view 분기 + body에 FileGrid 마운트
