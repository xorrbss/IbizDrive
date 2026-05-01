---
Last Updated: 2026-05-01
Status: ⏳ in progress
Owner: m16-grid-virtual worktree
---

# M16 follow-up — Grid View 가상화 (Plan)

## 출처

- `docs/01 §18 row 16` Grid View — M16 closure(2026-04-29) 시 가상화/2D 키보드/DnD/썸네일은 v1.x로 분리(`dev/completed/m16-grid-view/m16-grid-view-plan.md` 비범위 절). 본 트랙은 그중 **가상화**만 단독 처리.
- 비-가상화 grid는 100+ items 시 모든 카드를 동시에 mount → 큰 폴더에서 스크롤/렌더 stall 가능.

## 범위 (M16V.x)

| Phase | 산출물 | 비고 |
|---|---|---|
| M16V.0 | dev-docs bootstrap + 워크트리 | - |
| M16V.1 | `useGridColumns(ref, minColWidth, gap)` 훅 — ResizeObserver로 columns 계산 | 신설 |
| M16V.2 | FileTable grid 분기 → row 단위 가상화 (TanStack Virtual `useVirtualizer`) | view 분기 안에서만 동작; list 분기 무수정 |
| M16V.3 | 키보드 `scrollToIndex` 매핑 (1D index → row index) | 기존 `virtualizer.scrollToIndex(next)` 호출 재사용; rowMap 필요 |
| M16V.4 | tests — useGridColumns 단위 + FileTable grid 가상화 회귀 | jsdom 환경 한계: ResizeObserver mock 필요 |
| M16V.5 | closure (PR + dev-docs archive + progress.md + docs/01 footnote 갱신) | - |

## 비범위 (계속 v1.x)

- Grid 2D 키보드 wrap (좌/우 + columns 기반 ↑/↓) — **여전히 1D index ±1 유지**. 가상화로 인한 변동 없음.
- Grid DnD (이동) — list useDraggable 재사용은 가능하나 drop target 시각화 별도.
- 썸네일 이미지 (실제 미리보기) — backend thumbnail API 미정.
- 가변 카드 높이 (`measureElement`) — 본 트랙은 고정 높이 estimate.

## 현재 상태 분석

`FileTable.tsx`의 grid 분기 (라인 273~298 기준, M16):

```tsx
<div role="grid" ... className="flex-1 min-h-0 overflow-auto outline-none p-4" ref={scrollRef} ...>
  <div className="grid grid-cols-[repeat(auto-fill,minmax(140px,1fr))] gap-3">
    {items.map((item, idx) => (<FileCard ... />))}
  </div>
</div>
```

- `items.map` 직접 — 가상화 없음.
- `useVirtualizer`는 list 분기 전용으로만 인스턴스화(같은 함수 내 라인 96~101). 그러나 grid에서도 같은 `scrollRef`/`virtualizer` 객체에 접근(라인 146, 161 — `virtualizer.scrollToIndex(next)`). 즉 grid에서 ArrowUp/Down 시 list 기준 row index로 scroll → grid 컨테이너에서는 의미 없음(현재는 동작은 하지만 의도치 않은 좌표).
- 키보드 1D index는 view에 무관하게 동작 (`focusedIndex`).

## 목표 상태

```text
view='grid':
  - 컨테이너 width / minColWidth로 columns(>=1) 계산 (ResizeObserver)
  - rowCount = ceil(items.length / columns)
  - useVirtualizer({count: rowCount, estimateSize: ()=>CARD_ROW_HEIGHT, overscan: 4})
  - 각 virtualRow 안에서 [start, end) 슬라이스를 grid-template-columns로 columns만큼 배치
  - ArrowUp/Down 시: focusedIndex ±1 → virtualizer.scrollToIndex(Math.floor(idx/columns))
view='list':
  - 무수정 (회귀 0)
```

## phase별 실행 지도

### M16V.1 — useGridColumns 훅

```ts
// frontend/src/hooks/useGridColumns.ts
export function useGridColumns(
  ref: React.RefObject<HTMLElement>,
  opts: { minColWidth: number; gap: number }
): number
```

- `ResizeObserver` 구독, ref가 가리키는 요소의 `clientWidth`로 columns = `Math.max(1, Math.floor((width + gap) / (minColWidth + gap)))` 계산.
- SSR 안전: 초기값 1, 마운트 후 측정.
- jsdom: `globalThis.ResizeObserver` polyfill을 테스트 setup에서 mock.

### M16V.2 — FileTable grid 가상화

- Grid 분기에서:
  - `gridContainerRef` 별도 (overflow scroll element).
  - `columns = useGridColumns(gridContainerRef, { minColWidth: 140, gap: 12 })`.
  - `gridRowCount = Math.ceil(items.length / columns)`.
  - **단일 `virtualizer` 객체를 view에 따라 `count` / `estimateSize` 분기**: list 무수정 보장 위해 두 개의 별도 인스턴스 사용 (`listVirtualizer`, `gridVirtualizer`). 한쪽만 활성.
  - 가상화 row 내부: `<div className={'grid gap-3'} style={{gridTemplateColumns: \`repeat(${columns}, minmax(0, 1fr))\`}}>` — Tailwind dynamic class 회피 위해 inline style.

### M16V.3 — 키보드 ScrollToIndex 매핑

- 현재 `virtualizer.scrollToIndex(next)` 라인 146/161에서 호출. grid 모드일 때는 `Math.floor(next/columns)`을 gridVirtualizer에 전달.
- `viewMode === 'grid'`이면 `gridVirtualizer.scrollToIndex(Math.floor(next/columns))`, list면 기존대로.

### M16V.4 — tests

- `useGridColumns.test.ts`: ResizeObserver 모킹 → width=420, min=140, gap=12 → columns=2 (`(420+12)/(140+12)≈2.84` → floor=2). width=900 → 5. width<min → 1.
- `FileTable.test.tsx`: grid view에서 `useGridColumns` mock(=3) → 5개 items 렌더 시 gridcell 5, 그러나 가상화로 첫 row만 visible 가능 → jsdom layout 한계로 visible 검증 어려움. **대신** `getAllByRole('gridcell').length === items.length`가 보장될 만큼만 render(예: rowCount * CARD_ROW_HEIGHT < scroll viewport + overscan). 보다 안전한 회귀 테스트: 기존 M16.2 시나리오(2 items) 무회귀 + 신규 "grid 컨테이너에 `data-grid-virtual` 속성 존재" 정도로 한정.

### M16V.5 — closure

- PR squash → master.
- `docs/01 §18 row 16`에 closure marker 누적 (footnote 형태).
- `docs/progress.md` 최상단 entry 추가.
- `dev/active/m16-grid-virtual/` → `dev/completed/m16-grid-virtual/`.

## acceptance criteria

- [x] DoD 모음
- list 모드 회귀 0 (`FileTable.test.tsx`의 list 분기 테스트 GREEN, 기존 select/keyboard 등 전수 GREEN).
- grid 모드 200+ items에서 DOM에 mount된 `gridcell` 수 < items.length(가상화 작동 — 별도 단위 테스트 OR 수동 확인 항목으로 명시).
- 키보드 ArrowDown 시 grid에서도 focused row가 viewport에 들어옴 (scrollToIndex 매핑 검증).
- typecheck + lint clean, vitest 전 GREEN.
- v1.x backlog(2D 키보드/DnD/썸네일/가변 높이)는 본 트랙에서 건드리지 않음.

## 검증 게이트

| Gate | 명령 | 통과 조건 |
|---|---|---|
| typecheck | `pnpm --filter frontend typecheck` | 0 error |
| lint | `pnpm --filter frontend lint` | 0 error |
| unit | `pnpm --filter frontend test` (vitest run) | 전 케이스 GREEN, 회귀 0 |

## 리스크와 완화

| 리스크 | 완화 |
|---|---|
| jsdom에서 ResizeObserver 미지원 | `vitest.setup.ts`(이미 있을 가능성) 또는 테스트 파일 내 mock — `class ResizeObserver { observe(){} unobserve(){} disconnect(){} }`. |
| 가상화로 selection range select가 깨질 가능성 (Shift+Click 시 anchor가 가상화 unmount 영향) | selection은 ID 기반(`useSelectionStore`) → DOM mount 여부와 무관. 회귀 없음. |
| 카드 높이 측정 오차 | `estimateSize: 168` 고정(p-3+icon36+name+meta+gap 합산). M16V scope에서는 가변 높이 미지원 명시. |
| view 토글 시 두 virtualizer 인스턴스가 동시 활성화 → DOM/effect 누수 | 분기 안에서만 사용; 비활성 분기는 unmount되므로 react cleanup 자동. |
| keyboard scrollToIndex 시 columns가 0이면 NaN | columns = `Math.max(1, ...)` 보장. |
