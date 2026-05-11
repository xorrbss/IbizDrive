---
Last Updated: 2026-05-11
Status: 🟢 ACTIVE — Phase 1 RED 대기
---

# Design Handoff G2~G5 — Tasks

## Phase 상태

| Phase | Branch | 상태 |
|---|---|---|
| 0 부트스트랩 (dev-doc) | `feat/design-handoff-g3-searchbar` 내 포함 | 🟡 in progress |
| 1 G3 SearchBar | `feat/design-handoff-g3-searchbar` | ⏸ 대기 |
| 2 G5 Density | `feat/design-handoff-g5-density` | ⏸ 대기 |
| 3 G2 TopBar 3-col | `feat/design-handoff-g2-topbar-grid` | ⏸ 대기 |
| 4 G4 FileTable 6열 | `feat/design-handoff-g4-filetable-6col` | ⏸ 대기 |
| 5 Closure | `docs/design-handoff-g2-g5-closure` | ⏸ 대기 |

---

## Phase 1 — G3 SearchBar  🟡 in progress

- [ ] **RED**: `SearchBar.test.tsx` 신규 또는 확장
  - [ ] max-w 560 + 중앙 정렬 적용 (className 검증)
  - [ ] kbd 칩 렌더 (mac: `⌘K`, win: `Ctrl K`) — `navigator.platform` mock
  - [ ] clear 버튼: query 있을 때만 렌더, 클릭 시 query 비우고 close
- [ ] **RED**: `useGlobalShortcuts.test.ts` 확장
  - [ ] `Cmd+K` (mac) → `FOCUS_SEARCH_EVENT` dispatch
  - [ ] `Ctrl+K` (win/linux) → `FOCUS_SEARCH_EVENT` dispatch
  - [ ] `/` (기존) 보존 검증
- [ ] **GREEN**: `useGlobalShortcuts.ts` 키 매핑 확장 (⌘K + Ctrl+K)
- [ ] **GREEN**: `SearchBar.tsx`
  - [ ] 컨테이너 `w-full max-w-[560px] mx-auto`
  - [ ] platform 감지 → kbd 칩 mac/win 분기
  - [ ] clear 버튼 (X 아이콘) 우측 + kbd 칩 (둘 다 우측, 적절 spacing)
  - [ ] placeholder에서 `( / )` 제거 → `파일 검색`
- [ ] **검증**: `cd frontend && pnpm typecheck && pnpm lint && pnpm test --run`
- [ ] **자체 리뷰**: KISS / YAGNI / 파일 500 라인 / 핵심 원칙 11개 충돌 없음
- [ ] **dev-doc 갱신**: tasks.md commit pointer + status
- [ ] **commit**: `feat(design-handoff-g3): SearchBar 중앙정렬 + ⌘K kbd 칩 + clear 버튼`
- [ ] **게이트**: 사용자 승인 → push + PR

### Phase 1 작업 전 필독

- `frontend/prototype/styles.css` L274-310 (`.search-form`, `.search-input`, kbd chip)
- `frontend/prototype/components.jsx` SearchBar (있다면)
- `dev/active/design-handoff-gap-report-2026-05-10.md` §G3
- `docs/01-frontend-design.md` §10 (검색) + §12 (단축키)

### Phase 1 원본 코드 참조

- `frontend/src/components/topbar/SearchBar.tsx` — 현재 구현 (w-72, `/` 단축키, `Search` lucide)
- `frontend/src/hooks/useGlobalShortcuts.ts` — `FOCUS_SEARCH_EVENT` dispatch
- `frontend/src/components/topbar/SearchResults.tsx` — 드롭다운 (변경 없음)

### Phase 1 구현 대상

- 수정 `frontend/src/components/topbar/SearchBar.tsx`
- 수정 `frontend/src/hooks/useGlobalShortcuts.ts`
- 신규/수정 `frontend/src/components/topbar/SearchBar.test.tsx`
- 수정 `frontend/src/hooks/useGlobalShortcuts.test.ts`

### Phase 1 검증 참조

- `cd frontend && pnpm test --run SearchBar` (단위)
- `cd frontend && pnpm test --run useGlobalShortcuts` (단위)
- 전체: `cd frontend && pnpm test --run`
- 수동: SearchBar visual, kbd 칩 platform 분기, clear 버튼, ⌘K + / 둘 다 작동

### Phase 1 문서 반영

- `docs/01-frontend-design.md` §10/§12 — ⌘K 추가 명시 (가능하면 작은 patch)
- `docs/progress.md` Phase 1 세션 블록 (PR 머지 후)

---

## Phase 2 — G5 Density 토글  ⏸ 대기

- [ ] **RED**: `useDensity.test.ts` — 초기값/persist/SSR-safe
- [ ] **RED**: `density.test.ts` — `applyDensity()` body attr 토글
- [ ] **RED**: `TweaksPanel.test.tsx` — density 3-radio 그룹 + 선택 시 콜백
- [ ] **GREEN**: `frontend/src/lib/density.ts`
- [ ] **GREEN**: `frontend/src/hooks/useDensity.ts`
- [ ] **GREEN**: `frontend/src/app/globals.css` — `[data-density="compact|comfortable"]` rule
- [ ] **GREEN**: `TweaksPanel.tsx` — density 라디오 그룹 추가
- [ ] **검증**: typecheck + lint + test
- [ ] **자체 리뷰**
- [ ] **dev-doc 갱신**
- [ ] **commit**: `feat(design-handoff-g5): density 토글 (compact|default|comfortable)`
- [ ] **게이트**: 사용자 승인 → push + PR

### Phase 2 작업 전 필독

- `frontend/prototype/styles.css` L629-635 + L48 (`--row-h` 토큰)
- gap-report §G5
- `frontend/src/lib/variant.ts` + `useVariant.ts` (pattern reference)
- 결정 D1 (density > variant 우선순위) — plan.md §Phase 2

### Phase 2 원본 코드 참조

- `frontend/src/lib/variant.ts` — localStorage + body[data-variant] 패턴
- `frontend/src/hooks/useVariant.ts` — hook 패턴
- `frontend/src/components/topbar/TweaksPanel.tsx` — variant 라디오 그룹 (참고 구조)
- `frontend/src/app/globals.css` L48 — `--row-h: 34px` + variant overrides

### Phase 2 구현 대상

- 신규 `frontend/src/lib/density.ts`
- 신규 `frontend/src/hooks/useDensity.ts`
- 신규 `frontend/src/lib/density.test.ts`
- 신규 `frontend/src/hooks/useDensity.test.ts`
- 수정 `frontend/src/app/globals.css`
- 수정 `frontend/src/components/topbar/TweaksPanel.tsx`
- 수정/신규 `frontend/src/components/topbar/TweaksPanel.test.tsx`

### Phase 2 검증 참조

- `cd frontend && pnpm test --run density useDensity TweaksPanel`
- 전체: `pnpm test --run`
- 수동: variant=linear/notion/dropbox/terminal × density=compact/default/comfortable 조합 (row 높이 표 확인)

### Phase 2 문서 반영

- `docs/01-frontend-design.md` (필요 시 § Tweaks 절 추가)
- `docs/progress.md` 세션 블록

---

## Phase 3 — G2 TopBar 3-col  ⏸ 대기

- [ ] **RED**: `TopBar.test.tsx` 신규 — grid 3-col 적용 + SearchBar 중앙
- [ ] **GREEN**: `TopBar.tsx` — flex → grid grid-cols-[auto_1fr_auto]
- [ ] **검증**: typecheck + lint + test
- [ ] **자체 리뷰**
- [ ] **dev-doc 갱신**
- [ ] **commit**: `feat(design-handoff-g2): TopBar 3-col grid 레이아웃`
- [ ] **게이트**: 사용자 승인 → push + PR

### Phase 3 작업 전 필독

- `frontend/prototype/styles.css` L134-145 (`.topbar`)
- gap-report §G2
- 결정 D3 (햄버거 분리)

### Phase 3 원본 코드 참조

- `frontend/src/components/topbar/TopBar.tsx` — 현재 flex 2-col
- Phase 1 결과물 SearchBar (max-w-560 이미 적용)

### Phase 3 구현 대상

- 수정 `frontend/src/components/topbar/TopBar.tsx`
- 신규/수정 `frontend/src/components/topbar/TopBar.test.tsx`

### Phase 3 검증 참조

- `cd frontend && pnpm test --run TopBar`
- 수동: 좁은 viewport(800px) / 넓은 viewport(1440px) SearchBar 중앙 유지

### Phase 3 문서 반영

- `docs/01-frontend-design.md` (필요 시 §2 컴포넌트 트리 갱신)
- `docs/progress.md` 세션 블록

---

## Phase 4 — G4 FileTable 6열  ⏸ 대기

- [ ] **RED**: `FileTable.test.tsx` — gridCols 6열 폭 검증
- [ ] **RED**: `FileRow.test.tsx` — 체크박스 렌더 + toggle, action 버튼 hover/aria
- [ ] **GREEN**: `FileTable.tsx` — GRID_COLS = `36px 1fr 140px 130px 90px 44px`, header 매핑 추가
- [ ] **GREEN**: `FileRow.tsx` — 좌측 체크박스 셀 + 우측 action 버튼 (⋯)
- [ ] **GREEN**: `FileTableSkeleton.tsx` — 6열 매핑 동기화
- [ ] **검증**: typecheck + lint + test
- [ ] **자체 리뷰**
- [ ] **dev-doc 갱신**
- [ ] **commit**: `feat(design-handoff-g4): FileTable 6열 + 체크박스/action 컬럼 시각화`
- [ ] **게이트**: 사용자 승인 → push + PR

### Phase 4 작업 전 필독

- `frontend/prototype/styles.css` L612-619 (`.file-table .row` grid)
- gap-report §G4
- `frontend/src/stores/selection.ts` (M4 완료된 store)
- 결정 D5 (action 버튼 placeholder scope)

### Phase 4 원본 코드 참조

- `frontend/src/components/files/FileTable.tsx` (현재 5열, 주석에 M7 deferral 언급)
- `frontend/src/components/files/FileRow.tsx` (isSelected 받지만 체크박스 visual 없음)
- `frontend/src/components/files/BulkActionBar.tsx` (selection 토글 reference)

### Phase 4 구현 대상

- 수정 `frontend/src/components/files/FileTable.tsx`
- 수정 `frontend/src/components/files/FileRow.tsx`
- 수정 `frontend/src/components/files/FileTableSkeleton.tsx`
- 수정/신규 테스트 파일

### Phase 4 검증 참조

- `cd frontend && pnpm test --run FileTable FileRow`
- 전체: `pnpm test --run`
- 수동: 체크박스 click / Shift+click / Ctrl+click, action 버튼 hover

### Phase 4 문서 반영

- `docs/01-frontend-design.md` (§4 컴포넌트 테이블 / §8 selection)
- `docs/progress.md` 세션 블록

---

## Phase 5 — Track closure  ⏸ 대기

- [ ] gap-report §G2/G3/G4/G5에 ✅ + commit hash
- [ ] dev/active/design-handoff-g2-g5-2026-05-11/ → dev/completed/
- [ ] dev/active/design-handoff-gap-report-2026-05-10.md — 잔여 G7만 active, 또는 backlog 표기 후 archive
- [ ] docs/progress.md 트랙 종료 블록
- [ ] **commit**: `docs(design-handoff-g2-g5): track closure + dev-doc archive`
- [ ] **게이트**: 사용자 승인 → push + PR

---

## 공통 원칙

- 모든 phase: TDD (RED → GREEN → 자체 리뷰)
- KISS / YAGNI / 핵심 원칙 11개 (CLAUDE.md) 준수
- 파일 500라인 한도 — 초과 시 분리 기준 명시
- 편법 / 임시 / 하드코딩 / TODO 우회 금지 — 충돌 시 중단하고 사용자 보고
- 디자인 토큰 / variant override 깨지 않도록 cascade 확인
- 작업 시작 전 state check (memory `feedback_state_check_first.md`)
