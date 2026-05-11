---
task: 단축키 cheat sheet `?` 모달
status: completed
created: 2026-05-11
completed: 2026-05-11
merged: PR #171 (데이터 single-source 추출은 후속 PR #174)
spec: docs/01-frontend-design.md §12.1 (키맵)
branch: feat/shortcut-cheatsheet-modal
worktree: C:/project/IbizDrive/.claude/worktrees/shortcut-cheatsheet (제거됨)
result: vitest 175 file / 1273/1273 PASS, 신규 9건, typecheck/lint exit 0
---

# Plan — Shortcut Cheat Sheet (`?` 모달)

## 0. 배경

design-fidelity G2/G3(PR #168) 종료 보고에서 v1.x backlog로 분리됐던 항목. 사내 운영자에게 단축키 cheat sheet은 ⌘K/검색/탐색 키맵 등 §12.1을 학습할 수 있는 빠른 발견 경로. Confluence나 별도 문서 없이 앱 안에서 도움말 모달 한 번에 노출.

## 1. Scope

### in-scope
- `frontend/src/components/topbar/ShortcutsCheatSheet.tsx` (NEW): role=dialog 모달 + 카테고리별(검색·내비게이션·선택·액션) 단축키 표 + ESC/X 닫기. self-contained — useState owns visibility, `app:open-shortcuts` CustomEvent 수신.
- `frontend/src/hooks/useGlobalShortcuts.ts`: `?` 단축키 분기 추가 (`Shift+/` 또는 `?` 직접). editable 가드 적용 (input/textarea/contenteditable에서는 무시). `app:open-shortcuts` CustomEvent dispatch.
- `frontend/src/app/(explorer)/layout.tsx`: layout root에 `<ShortcutsCheatSheet />` 1회 마운트 (sidebar/topbar/main과 동위).
- vitest 회귀 가드:
  - `ShortcutsCheatSheet.test.tsx` (NEW, 4-5 case: open 시 렌더, ESC 닫기, X 클릭 닫기, focus 복귀, 키맵 표 노출).
  - `useGlobalShortcuts.test.ts`: `?` dispatch + editable 가드 + modifier 무시 케이스 추가.
- spec 갱신: docs/01 §12.1 키맵 표 `?` row 추가 + callout 한 문장.

### out-of-scope
- 단축키 데이터 source 통합 (`useGlobalShortcuts` ↔ CheatSheet 단일 source) — 본 트랙은 컴포넌트 내부 정적 array. 추후 토큰화 별도 PR.
- 카테고리별 검색/필터, 단축키 편집 UI — YAGNI.

## 2. 접근

### 2.1 CheatSheet self-contained
- 컴포넌트가 useState owns. layout에 1회 마운트로 충분.
- `useEffect`로 `app:open-shortcuts` listener 등록 → `setOpen(true)`.
- ESC keydown → close. X 버튼 클릭 → close.
- 이전 focus 복귀 (ShareDialog/GrantPermissionDialog 패턴 답습).

### 2.2 `?` 단축키
- 일반적으로 `Shift+/` 로 `?` 입력. `e.key === '?'` 매칭이 명확 — modifier 별도 처리 불필요.
- editable 가드 적용 — 다른 input/textarea에서 `?` 입력 보호.
- modifier(Ctrl/Meta/Alt) 무시 — `Ctrl+?` 등은 다른 의미 우려.

### 2.3 단축키 데이터 array
- 컴포넌트 내부 정적 array. 카테고리:
  - 검색: `/`, `⌘K`/`Ctrl+K`
  - 내비게이션 (FileTable/Grid): `↑↓`, `←→ (Grid)`, `Enter`, `Esc`
  - 선택: `Space`, `Shift+Click`, `Ctrl+Click`, `Ctrl/⌘+A`
  - 액션: `Delete`, `F2`
  - 도움말: `?`

## 3. 인터페이스

- `<ShortcutsCheatSheet />` props 없음. 자기 관리.
- Custom event: `'app:open-shortcuts'` (typedef는 utility const로 export).

## 4. 검증

- `pnpm typecheck` exit 0.
- `pnpm lint` exit 0.
- `pnpm test --run` 전체 그린.

## 5. 회귀 위험

- `?` 입력은 다른 단축키와 충돌 가능성 적음 (현재 `/`, `⌘K`, `Esc` 만 글로벌).
- 모달 stacking: ShareDialog / GrantPermissionDialog 와 동시 표시 우려. CheatSheet의 z-index를 동일/상위로 두되, ESC가 두 다이얼로그 모두 닫기 시 충돌 가능. 본 PR은 단순화: ESC가 두 다이얼로그 양쪽 listen해도 자기 visibility만 처리 (각 다이얼로그 self-contained ESC).

## 6. acceptance criteria

- [ ] `?` 누르면 cheat sheet 모달 open.
- [ ] ESC / X 클릭 시 모달 close.
- [ ] 모달 닫힐 때 이전 focus 복귀.
- [ ] 키맵 표 5 카테고리 노출.
- [ ] input/textarea/contenteditable에서 `?` 입력은 무시.
- [ ] vitest 신규 + 회귀 zero (typecheck/lint/test 그린).
- [ ] docs/01 §12.1 `?` row 추가 + callout.

## 7. PR 단위

단일 PR. 사이즈: 컴포넌트 1 + 테스트 1 + 훅 1줄 + 테스트 추가 + layout 1줄 + spec 1줄 → ~6 파일.
