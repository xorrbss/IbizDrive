# M16V Grid 2D 키보드 wrap — Plan

Last Updated: 2026-05-02

## 요약

Grid View에서 ↑/↓는 columns 단위로 step, ←/→는 1-step으로 이동하며 row 경계에서 자연 wrap한다. List View 동작은 변경 없이 보존. M16V closure 시 명시한 backlog 항목(`docs/progress.md:218`)을 닫는다.

## 현재 상태 분석

**파일**: `frontend/src/components/files/FileTable.tsx:161-307` (`handleKeyDown`).

- 현 동작: view에 무관하게 ↑/↓는 ±1 (1D index 직선 이동). Grid 모드에서는 `idx / gridSafeColumns`로 row index를 매핑해 `gridVirtualizer.scrollToIndex` 호출만 분기.
- 명시적 deferred 주석: line 168 — *"좌/우 wrap navigation (2D 키보드)은 v1.x — 본 트랙은 ↑/↓ 1D 유지."*
- ←/→ 키는 case 미존재 → 키 입력 시 default(no-op + propagation).
- pendingIds 스킵 로직은 1D 패턴 (`while (...) next++/next--`).
- `useGridColumns` 훅(`frontend/src/hooks/useGridColumns.ts`)이 ResizeObserver 기반 columns count를 이미 제공.

## 목표 상태

| view | ↑ | ↓ | ← | → |
|---|---|---|---|---|
| list (기존) | -1 | +1 | no-op | no-op |
| grid (신규) | -columns | +columns | -1 (wrap) | +1 (wrap) |

- Grid ↑: `prev - columns >= 0`이면 이동, 아니면 stay (top 행 wrap 없음).
- Grid ↓: `prev + columns < length`이면 이동, 아니면 last partial row가 존재할 경우 `length-1`로 clamp, 아니면 stay.
- Grid ←/→: 현재 ↑/↓ 1D 동작과 동일 (pending skip + bounds clamp + shift 범위 확장).
- pending skip: ↑/↓는 동일 column stride로 vertical skip, ←/→는 1-step linear skip.
- list 모드: 모든 키 동작 보존 (회귀 0).
- shift, ctrl/meta, F2, Delete, Enter, Esc, Ctrl+A, Space는 변경 없음.
- aria-rowcount/rowindex 변경 없음.

## phase별 실행 지도

### M16VK.0 — bootstrap (현재 phase)

- worktree `feature/m16v-grid-keyboard-wrap` 생성 ✓
- dev-docs plan/context/tasks 3파일 작성

### M16VK.1 — pure helper RED→GREEN

- 신설: `frontend/src/lib/gridNav.ts` — `computeNextIndex` pure function.
- 신설 테스트: `frontend/src/lib/gridNav.test.ts` — 14개 케이스 (list ↑/↓/←/→, grid ↑/↓/←/→, pending skip, 경계, 빈 배열, columns=1, last partial row clamp).
- 기존 코드 미수정 (RED 단계는 helper 신설만).

### M16VK.2 — FileTable 통합 GREEN

- 수정: `FileTable.tsx:161-307` — 4개 ArrowKey case가 `computeNextIndex`를 호출하도록 통합.
- 수정: line 168 deferred 주석 제거 + 2D wrap 도입 사실 명시.
- 신설/수정 테스트: `FileTable.test.tsx` — grid 모드 ↑/↓ columns step + ←/→ wrap + pending skip 케이스 추가 (4~6개).

### M16VK.3 — closure

- 수정: `docs/01-frontend-design.md §12.1` — Grid 모드 ↑/↓/←/→ row 분리 표 행 추가.
- 수정: `docs/progress.md` — 트랙 종료 entry top append.
- archive: `dev/active/m16v-grid-keyboard-wrap/` → `dev/completed/m16v-grid-keyboard-wrap/`
- PR + master squash-merge.

## acceptance criteria

1. Grid 모드 ↑/↓는 `gridSafeColumns` step으로 이동한다.
2. Grid 모드 ←/→는 ±1 step으로 이동하며 row 경계에서 wrap한다.
3. Grid 모드 ↓ overshoot 시 last partial row에 항목이 있으면 `length-1`로 clamp, 없으면 stay.
4. Grid 모드 ↑ overshoot(prev < columns)은 stay.
5. pending 스킵: ↑/↓는 column stride로, ←/→는 1-step으로 같은 방향 skip.
6. shift+arrow는 모든 방향에서 selectRange 호출.
7. List 모드 동작 회귀 0 — 기존 모든 FileTable 키보드 테스트 GREEN.
8. `pnpm test --run` ALL GREEN, `pnpm typecheck`, `pnpm lint`, `pnpm build` clean.

## 검증 게이트

- M16VK.1: `pnpm test --run gridNav` GREEN.
- M16VK.2: `pnpm test --run FileTable` GREEN, 회귀 0.
- M16VK.3: 위 + `pnpm typecheck` + `pnpm lint` + `pnpm build`.

## 리스크와 완화

- **R1**: `gridSafeColumns`가 1일 때(≈ List 모드) Grid ↑/↓도 1-step → ←/→와 동일. 영향 없음(아래 표대로 두 키 모두 동등 동작 = 의도된 결과).
- **R2**: 테스트에서 `useGridColumns`는 ResizeObserver 의존. JSDOM ResizeObserver mock 또는 `gridContainerRef.current.clientWidth` 직접 set + manual `act` trigger 필요.
  - 완화: 핵심 로직을 pure helper(`computeNextIndex(prev, key, view, columns, length, isPending)`)로 추출, helper unit test가 메인. FileTable 통합 테스트는 columns 값을 prop 또는 hook mock으로 주입.
- **R3**: shift+wrap 시 selectRange anchor가 row를 가로지르며 선택 범위가 선형으로 확장됨. 사용자가 직관적으로 기대하는 동작과 일치(현재 List ↑↓도 이미 동일 패턴).
- **R4**: focus DOM 동기화(`useEffect` line 79-87)는 1D index → DOM selector 매칭만 수행 → grid에서도 그대로 동작. 추가 변경 불필요.
