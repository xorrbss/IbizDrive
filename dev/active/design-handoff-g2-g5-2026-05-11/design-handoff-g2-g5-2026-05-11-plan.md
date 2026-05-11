---
Last Updated: 2026-05-11
Status: 🟢 ACTIVE — Phase 1 in progress
---

# Design Handoff G2~G5 — Plan

## 요약

Claude Design 핸드오프 (`q_E8bGXpCKkbcXTFb4TiTg`)와 현재 frontend 사이의 시각/구조 gap 4건 정렬:
- **G3** SearchBar — `⌘K`/`Ctrl+K` kbd 칩 + max-w 560 중앙 정렬 + clear 버튼 (`/` 단축키 fallback 유지)
- **G5** Density 토글 — `compact|default|comfortable` (TweaksPanel에 노출, localStorage + variant 우선순위 정의)
- **G2** TopBar — 3-column grid (auto / 1fr / auto) 레이아웃 (햄버거 / 사이드바 collapse는 분리 트랙)
- **G4** FileTable — 6열 (체크박스 36 / 이름 1fr / 크기 140 / 수정일 130 / 수정자 90 / 액션 44)

원본 gap-report: `dev/active/design-handoff-gap-report-2026-05-10.md`.

## Out-of-scope (이 트랙에서 제외)

| 항목 | 사유 | 후속 |
|---|---|---|
| G1 TopBar 48px | ✅ PR #148 완료 | — |
| G6 GridView min-col 172 | ✅ PR #148 완료 | — |
| G7 `.mobile-view` | 사내 데스크톱 메인, gap-report `낮은 우선순위 backlog` | 별도 마일스톤 (M-mobile) |
| G2 햄버거 / 사이드바 collapse | 사이드바 collapse infra 미존재 | Plan B sidebar 트랙에서 동반 |
| G8 DropOverlay | ✅ PR #148 commit `166432b` | — |
| G9 StatusBar | ✅ 이미 일치 | — |

## 현재 상태 분석

| 영역 | 상태 | 근거 |
|---|---|---|
| `useViewParam` (view=list\|grid URL param) | ✅ | `frontend/src/hooks/useViewParam.ts` |
| `useVariant` (linear/notion/dropbox/terminal localStorage) | ✅ | `frontend/src/hooks/useVariant.ts` + `lib/variant.ts` |
| `--row-h` 토큰 + variant override (linear=34/notion=40/dropbox=38/terminal=28) | ✅ | `frontend/src/app/globals.css` L48 |
| TweaksPanel | ✅ | `frontend/src/components/topbar/TweaksPanel.tsx` (theme + variant) |
| Density 토글 / `density` state | ❌ | 신규 필요 |
| Selection store (`useSelectionStore`) | ✅ | `frontend/src/stores/selection.ts` (M4 완료) |
| BulkActionBar | ✅ | `frontend/src/components/files/BulkActionBar.tsx` |
| FileTable 체크박스 컬럼 | ❌ | 현재 5열 `28px 1fr 110px 130px 90px`, 첫 컬럼은 placeholder |
| FileTable 액션 컬럼 | ❌ | 미존재 |
| FileRow 체크박스 UI | ❌ | `isSelected` 상태만 있고 visual 없음 |
| SearchBar 중앙정렬 + max-w 560 | ❌ | 현재 `w-72` (288px), `flex justify-between` 우측 정렬 |
| SearchBar ⌘K 단축키 | ❌ | 현재 `/`만 (`FOCUS_SEARCH_EVENT`) |
| SearchBar kbd 칩 | ❌ | placeholder text hint만 |
| SearchBar clear 버튼 | ❌ | Esc만 |
| TopBar 3-col grid | ❌ | 현재 `flex justify-between` 2-col |

## 목표 상태

트랙 종료 시점:
- 4개 phase PR 모두 머지 완료 + CI 그린
- 디자인 핸드오프 admin 외 main explorer 영역의 시각 fidelity 95%+ 동기화 (G7 제외)
- `globals.css` density 토큰 + `useDensity` hook + TweaksPanel UI 통합
- FileTable 6열 + checkbox/action column 시각화 (selection store 이미 wired, 시각만)
- SearchBar 중앙정렬 + kbd 칩 + clear 버튼

## Phase 실행 지도

### Phase 1 — G3 SearchBar (현재 phase)

**Branch**: `feat/design-handoff-g3-searchbar`
**Scope**: SearchBar 한 컴포넌트 + useGlobalShortcuts 키 매핑 확장

**Spec 근거**:
- `prototype/styles.css` L274-310 `.search-form`, `.search-input`
- gap-report §G3
- 디자인: 중앙정렬, `max-width: 560px`, `height: 30px`, `⌘K` kbd 칩 우측, clear 버튼

**RED — 테스트 먼저**:
1. SearchBar.test.tsx: max-w 560 적용
2. SearchBar.test.tsx: kbd 칩 mac/win 분기 (navigator.platform 또는 ⌘/Ctrl)
3. SearchBar.test.tsx: clear 버튼 클릭 시 query 비우고 close
4. useGlobalShortcuts.test.ts: ⌘K (mac) / Ctrl+K (win) 또는 `/` 모두 FOCUS_SEARCH_EVENT dispatch

**GREEN — 구현**:
- `useGlobalShortcuts.ts`: `⌘K|Ctrl+K` 추가 (`/`도 보존)
- `SearchBar.tsx`:
  - 컨테이너 `w-full max-w-[560px] mx-auto` (혹은 `max-w-[35rem]`)
  - 내부 wrapper relative
  - 우측 kbd 칩: `<kbd>⌘K</kbd>` (mac) / `<kbd>Ctrl K</kbd>` (win) — platform 감지
  - query 있을 때 우측 X 버튼 (clear)
  - placeholder에서 `( / )` 제거

**검증**:
- `pnpm typecheck` + `pnpm lint` + `pnpm test --run`
- 수동: SearchBar 중앙정렬, kbd 칩 표시, clear 동작

### Phase 2 — G5 Density 토글

**Branch**: `feat/design-handoff-g5-density`
**Scope**: density state + `--row-h` 동적 override + TweaksPanel UI

**Spec 근거**:
- `prototype/styles.css` L629-635 `.row.compact/.row.comfortable`
- gap-report §G5
- 디자인 spec: compact=28 / default=34 / comfortable=40

**핵심 의사결정**:
- density는 variant와 같이 per-user persistent preference → variant pattern (localStorage + `[data-density]`) 따름
- variant가 이미 `--row-h`를 override (notion=40/dropbox=38/terminal=28) → density는 variant override를 다시 override
- 우선순위: **density (사용자 명시 선택) > variant default**
- variant 자체적 row-h가 있더라도 density="comfortable"이면 40px, density 미설정이면 variant default 따름
- 구현: `[data-density="compact"] { --row-h: 28px }` `[data-density="comfortable"] { --row-h: 40px }` — `[data-density]` 없으면 variant default 유지

**RED — 테스트 먼저**:
1. useDensity.test.ts: 초기값 = null (`undefined`), set 시 localStorage 저장 + `[data-density]` attr 적용
2. useDensity.test.ts: SSR-safe (window 없을 때 default 반환)
3. TweaksPanel.test.tsx: density 라디오 3개 표시 + 선택 시 useDensity.set 호출

**GREEN — 구현**:
- `frontend/src/lib/density.ts`: `Density = 'compact'|'default'|'comfortable'`, `getInitialDensity()`, `applyDensity()`
- `frontend/src/hooks/useDensity.ts`: useState + localStorage + body attr
- `frontend/src/app/globals.css`: `[data-density="compact"]/.comfortable` rules
- `frontend/src/components/topbar/TweaksPanel.tsx`: density 라디오 그룹 추가

**검증**:
- `pnpm typecheck` + `pnpm lint` + `pnpm test --run`
- 수동: density 토글 시 row 높이 변화 (linear → 28/34/40), variant + density 조합

### Phase 3 — G2 TopBar 3-col grid

**Branch**: `feat/design-handoff-g2-topbar-grid`
**Scope**: TopBar 레이아웃만 (햄버거 제외)

**Spec 근거**:
- `prototype/styles.css` L134-145 `.topbar { display: grid; grid-template-columns: auto 1fr auto }`
- gap-report §G2

**구조**:
```text
[ (좌측: 빈 영역 또는 logo placeholder) ][ SearchBar (중앙) ][ TweaksPanel + Avatar (우측) ]
```
좌측은 햄버거 자리지만 이번엔 placeholder (`<div />`) — Plan B sidebar 트랙에서 햄버거 wire.

**RED — 테스트 먼저**:
1. TopBar.test.tsx: 3-col grid 적용 (`grid-cols-[auto_1fr_auto]`)
2. TopBar.test.tsx: 좌측 placeholder가 빈 div이고 SearchBar는 중앙

**GREEN — 구현**:
- `TopBar.tsx`: `flex justify-between` → `grid grid-cols-[auto_1fr_auto]` 또는 `grid-cols-3` (변형 검토)

**검증**:
- `pnpm typecheck` + `pnpm lint` + `pnpm test --run`
- 수동: 좁은/넓은 뷰포트에서 SearchBar 중앙 유지

### Phase 4 — G4 FileTable 6열 (체크박스 + action col)

**Branch**: `feat/design-handoff-g4-filetable-6col`
**Scope**: FileTable 그리드 폭 재정의 + FileRow 체크박스 / action 버튼 시각화

**Spec 근거**:
- `prototype/styles.css` L612-619 `.file-table .row { grid-template-columns: 36px 1fr 140px 130px 90px 44px }`
- gap-report §G4
- M4 selection model 이미 완료 — visual만 빠짐

**구현 범위 결정**:
- 체크박스: row hover 또는 selected일 때 표시 (디자인 핸드오프 spec 확인 필요)
- 액션 컬럼: row hover 시 표시되는 ⋯ 메뉴 버튼 (KISS — 메뉴 내용은 후속, 버튼만 placeholder 가능?)
  - **결정 필요**: action 버튼이 BulkActionBar의 단일행 등가물인지 / 단순 placeholder인지
  - 기본 방향: action 버튼 = 단일 행 컨텍스트 메뉴 트리거 (rename/move/share/delete). 메뉴 내용은 후속이면 placeholder 버튼 + ⋯ 아이콘만으로도 layout 완성

**RED — 테스트 먼저**:
1. FileTable.test.tsx: gridCols 6열 적용 시 width 확인
2. FileRow.test.tsx: 체크박스 렌더링 + 토글 시 useSelectionStore.toggle 호출
3. FileRow.test.tsx: action 버튼 hover 노출 / aria-label

**GREEN — 구현**:
- `FileTable.tsx`: `GRID_COLS = 'grid grid-cols-[36px_1fr_140px_130px_90px_44px] gap-3 items-center px-4'`
- header 6열 매핑 추가
- `FileRow.tsx`: 좌측 체크박스 셀 (`<input type="checkbox" checked={isSelected} />`) + 우측 action 버튼 (`<button aria-label="..."><MoreHorizontal /></button>`)
- BulkActionBar는 변경 없음

**검증**:
- `pnpm typecheck` + `pnpm lint` + `pnpm test --run`
- 수동: 체크박스 클릭 / Shift+click / Ctrl+click, action 버튼 hover 노출

### Phase 5 — Track closure

**Branch**: `docs/design-handoff-g2-g5-closure`
**Scope**: dev-doc archive + progress.md 종료 블록

- dev/active/design-handoff-g2-g5-2026-05-11/ → dev/completed/
- gap-report에 G2~G5 ✅ 마킹 + commit reference
- progress.md 종료 블록 + ADR (필요 시)

## Acceptance Criteria

- [ ] Phase 1 PR 머지 + CI 그린
- [ ] Phase 2 PR 머지 + CI 그린
- [ ] Phase 3 PR 머지 + CI 그린
- [ ] Phase 4 PR 머지 + CI 그린
- [ ] gap-report에 G2/G3/G4/G5 ✅ 처리됨 / commit hash 기재
- [ ] G7은 backlog로 명시 (gap-report 또는 별도 issue/note)
- [ ] dev-docs archive (dev/completed/)
- [ ] progress.md 트랙 종료 블록

## 검증 게이트 (각 phase 공통)

| 게이트 | 명령 | 통과 기준 |
|---|---|---|
| Typecheck | `cd frontend && pnpm typecheck` | 0 오류 |
| Lint | `cd frontend && pnpm lint` | 0 오류 |
| Unit test | `cd frontend && pnpm test --run` | 100% PASS |
| Build | `cd frontend && pnpm build` | 성공 (각 phase 마지막에만) |
| Visual smoke | 수동 — 4 variants 토글 | regression 없음 |

## 리스크 / 완화

| 리스크 | 영향 | 완화 |
|---|---|---|
| ⌘K 단축키 변경이 muscle memory 영향 | medium | `/` fallback 유지 + placeholder/kbd 칩 안내 |
| Density × variant 우선순위 혼란 | medium | Phase 2 plan에 우선순위 명시, CSS 명확한 cascade |
| FileTable action 버튼 scope 미정 | medium | 메뉴 내용 미정시 placeholder 버튼만 (KISS), 메뉴는 후속 PR |
| Co-session 충돌 (다른 worktree에서 동일 파일) | low | Phase별 별도 branch + state check before each phase |
| TopBar 3-col에서 SearchBar가 좁은 viewport에서 overflow | low | max-w-[560px] mx-auto + responsive (1fr 컬럼이 자연 축소) |

## 후속 (이 트랙 종료 후)

- G7 Mobile view — 별도 마일스톤
- G2 햄버거 / 사이드바 collapse — Plan B sidebar 트랙
- FileTable action 버튼 메뉴 내용 (rename/move/share/delete 컨텍스트 메뉴) — v1.x
