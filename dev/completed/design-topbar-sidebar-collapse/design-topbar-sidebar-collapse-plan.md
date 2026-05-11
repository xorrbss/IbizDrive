---
task: design fidelity G2/G3 — TopBar 3-col grid + 사이드바 collapse + ⌘K
status: completed
created: 2026-05-11
completed: 2026-05-11
merged: PR #168
gap_report: dev/active/design-handoff-gap-report-2026-05-10.md (G2, G3)
spec: docs/01-frontend-design.md §2 (사이드바), §10 (검색), §17 (라우팅/layout)
branch: feat/design-topbar-3col-sidebar-collapse
worktree: C:/project/IbizDrive/.claude/worktrees/design-topbar-sidebar (제거됨)
result: vitest 174 file / 1264/1264 PASS, 신규/보강 14건, typecheck/lint exit 0
---

# Plan — Design Fidelity G2/G3 + Sidebar Collapse

## 0. 배경

design handoff gap report(2026-05-10)에서 G2/G3 라우팅을 "Plan B 사이드바 작업과 함께 검토" 권장. Plan B(PR #139)는 사이드바 3-section 구조까지 머지됐으나 **collapse 토글 자체가 미구현**이라 햄버거 진입점 없음 + TopBar는 `flex justify-between`로 검색바가 좌측에 협소(`w-72=288px`). 디자인은 `grid auto/1fr/auto` + 중앙 max-w 560.

본 트랙은 3 gap을 단일 PR로 묶음:
- **G2** TopBar 3-column grid 구조 + 햄버거 버튼 (사이드바 toggle 진입점)
- **사이드바 collapse** — Zustand store + (explorer)/layout.tsx에서 aside 폭 transition
- **G3** SearchBar h-30, ⌘K kbd 칩, clear 버튼, `useGlobalShortcuts`에 ⌘K/Ctrl+K 매핑 추가(`/` 호환 유지)

## 1. Scope

### in-scope
- `frontend/src/stores/sidebarChrome.ts` (NEW): `collapsed: boolean` + `setCollapsed/toggle` actions, localStorage persist (sidebarTree와 분리 — chrome은 단순 토글, tree는 expand state라 책임 분리).
- `frontend/src/app/(explorer)/layout.tsx`: aside에 `collapsed ? 'w-0' : 'w-[248px]'` + `transition-[width] duration-200` + `overflow-hidden` 시 패딩 제거. 사이드바 내부 컨텐츠는 aria-hidden 처리(focus trap 방지).
- `frontend/src/components/topbar/TopBar.tsx`: `grid grid-cols-[auto_1fr_auto]` + 좌측 햄버거(lucide `Menu` 아이콘) + 중앙 wrapper(`mx-auto max-w-[560px] w-full`) + 우측 기존 액션. 햄버거 `aria-pressed={collapsed}` + `aria-label="사이드바 접기/펴기"`.
- `frontend/src/components/topbar/SearchBar.tsx`: `h-[30px]`, 우측 영역에 query 비어있을 때 ⌘K kbd 칩 / 있을 때 clear 버튼(lucide `X` 아이콘). 폭은 부모 grid 컨테이너가 max-w 처리.
- `frontend/src/hooks/useGlobalShortcuts.ts`: `⌘+K` (Mac) / `Ctrl+K` (Win/Linux) 단축키 추가. 기존 `/` 호환 유지. 동일 `FOCUS_SEARCH_EVENT` dispatch.
- vitest 회귀 가드:
  - `sidebarChrome.test.ts` (NEW, 3 케이스: 초기값 false / toggle / setCollapsed).
  - `TopBar.test.tsx` (NEW, 3 케이스: 3-col 구조 / 햄버거 클릭 toggle / 햄버거 aria-pressed sync).
  - `SearchBar.test.tsx` (modify, +2 케이스: kbd 칩 노출/clear 버튼 토글).
  - `useGlobalShortcuts.test.ts` (modify, +2 케이스: ⌘K dispatch / Ctrl+K dispatch).
- spec 갱신: docs/01 §2 collapse UX 한 단락 / §10 단축키 표에 ⌘K·Ctrl+K 추가 / §17 layout grid + design fidelity 마커.

### out-of-scope
- **mobile-view (G7)** — 별도 마일스톤, 우선순위 낮음.
- **TweaksPanel density 토글 (G5)** — 별도 PR.
- **GridView min-col-width (G6 잔여, 172 확정)** — design fidelity PR #148에서 일부 처리, M16V 분기 잔여는 별도.
- **placeholder hint 변경** — 현재 "파일 검색 ( / )", 단축키 추가 후 `( ⌘K )` 또는 다중 표기 검토는 visual polish trade-off, 본 트랙은 kbd 칩으로 시각 노출하고 placeholder는 유지(언어 분기 회피).

## 2. 접근

### 2.1 sidebarChrome store 분리
- 기존 `sidebarTree.ts`는 expand state 30일 TTL + section collapse. 본 store는 chrome(외곽) toggle 단순 boolean. 별도 파일로 분리해 store 단일 책임 + persist 키 분리(`sidebar-chrome:v1`).

### 2.2 (explorer)/layout.tsx aside 전환
- aside의 `w-[248px] shrink-0` → `collapsed ? 'w-0' : 'w-[248px]'` + `transition-[width] duration-200 ease-out` + 내부 컨텐츠를 `overflow-hidden`로 감싸 텍스트 잘림 방지.
- `aria-hidden={collapsed}` 부착으로 SR이 collapsed 상태에서 트리/링크를 읽지 않게 함.
- 사이드바 내부 컴포넌트(`SidebarSections`, `SharesLink`, `TrashLink`, `StorageBar`, `UserMenu`)는 무수정 — 외곽만 변경.

### 2.3 TopBar 3-column grid
- 햄버거 lucide `Menu` 아이콘. h-8 px-2 hover:bg-surface-2.
- 중앙 SearchBar는 `mx-auto max-w-[560px] w-full` 컨테이너로 wrap. SearchBar 자체는 `w-full` 만 강제, 폭 결정은 부모.
- 우측은 기존 `flex items-center gap-2` 유지.

### 2.4 SearchBar polish
- `h-[30px]` (design styles.css L629 search h-30).
- 우측 12px 영역:
  - `query === ''` && `!focused` → `<kbd>⌘K</kbd>` chip (border + bg-surface-2 + text-fg-muted text-[11px] px-1 rounded).
  - `query !== ''` → `<button>` `<X size={14} />` (focusable, aria-label "검색어 지우기"). 클릭 시 query 초기화 + input 재focus.
- focused 상태에서 kbd 칩 숨김 — 사용자가 단축키를 이미 사용 중이므로 시각 잡음 감소.
- 단축키 변경에 따라 placeholder text는 유지(시각 hint는 kbd 칩이 대신). spec §10 단축키 표가 source of truth.

### 2.5 useGlobalShortcuts ⌘K/Ctrl+K
- 기존 `/` 분기 유지.
- 새 분기: `e.key === 'k' && (e.metaKey || e.ctrlKey) && !e.altKey && !e.shiftKey` → preventDefault + dispatch.
- `isEditableTarget` 가드는 ⌘K에는 적용하지 않음 — 사용자가 다른 input에서도 ⌘K로 검색 열 수 있어야 함(VS Code 패턴).
- 단축키 충돌: ⌘K는 브라우저 기본 단축키(파이어폭스: 검색바 focus)이지만 explorer 컨텍스트에서는 우리가 가로채는 것이 디자인 spec 의도.

## 3. 인터페이스

### 3.1 sidebarChrome store

```ts
interface SidebarChromeState {
  collapsed: boolean
  setCollapsed: (next: boolean) => void
  toggle: () => void
}

export const useSidebarChromeStore = create<SidebarChromeState>()(
  persist(/* ... */, { name: 'sidebar-chrome:v1' }),
)
```

### 3.2 TopBar 햄버거
- `<button onClick={toggle} aria-pressed={collapsed} aria-label="사이드바 접기/펴기">`
- 항상 노출 (collapsed 상태에서도 햄버거가 보여야 다시 열 수 있음).

## 4. 검증 계획

- `pnpm --filter frontend typecheck` exit 0.
- `pnpm --filter frontend lint` exit 0.
- `pnpm --filter frontend test --run` 전체 그린.
- 수동 spot-check 권장 (자율 모드 자체 검증은 typecheck+test 그린에서 PR):
  - 햄버거 클릭 시 사이드바 폭 transition + 콘텐츠 보존.
  - ⌘K (Mac) / Ctrl+K (Win) → 검색 focus.
  - 검색 결과 노출 + clear 버튼 + Esc 동작.

## 5. 회귀 위험

- aside의 `shrink-0` 제거 시 main이 사이드바를 압축할 가능성 → 명시적 `flex-shrink-0` 유지.
- transition 중 SR의 aria-hidden 토글이 트리 노드 focus를 빼앗을 수 있음 → 햄버거 자체는 TopBar 안이라 focus 유지.
- ⌘K의 isEditableTarget 가드 미적용으로 텍스트 input에서 ⌘K 누르면 검색으로 jump → 의도된 동작.
- localStorage `sidebar-chrome:v1` 신규 키 — 충돌 없음.
- 디자인 spec에서 검색 h-30이지만 TopBar 자체는 h-12(48px). 30/48 비율 그대로 9px 패딩 의도(styles.css L134). h-30 + flex items-center로 자동 정렬.

## 6. acceptance criteria

- [ ] 햄버거 클릭 → 사이드바 collapse 토글 (시각 + aria-pressed sync).
- [ ] localStorage 키 `sidebar-chrome:v1`에 collapsed boolean persist.
- [ ] TopBar grid 3-col 구조 + 중앙 SearchBar 정렬.
- [ ] SearchBar h-30 + 비어있을 때 ⌘K kbd 칩 + 있을 때 clear 버튼.
- [ ] `⌘K` (Mac) / `Ctrl+K` (Win/Linux) → 검색 focus dispatch.
- [ ] 기존 `/` 단축키 동작 유지.
- [ ] vitest 신규 + 회귀 가드 그린 (typecheck/lint/test).
- [ ] docs/01 §2/§10/§17 갱신.

## 7. PR 단위

단일 PR. 사이즈 ~10 file (5 source, 4 test, 1 spec). G2/G3/collapse는 시각 응집도 높아 분리 인센티브 약함.
