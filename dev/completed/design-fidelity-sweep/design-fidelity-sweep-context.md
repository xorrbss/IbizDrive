# Design Fidelity Sweep — Context

## 현재 상태 (2026-05-11)

- **master tip**: `c4e19d3` (PR #195 머지 직후, v1x-backlog + drift check + design gap 통합)
- **v1.0.0-beta**: 출시 직전, **회귀 테스트 진행 중 (다른 세션)** — 1~2주 슬립으로 sweep 완료 후 태그 push
- **열린 PR**: 0건

## 입력 자료

- 디자인 zip 추출 위치: `C:/project/IbizDrive/.claude/design-zip-extract/`
  - 루트 영역 (최신): `admin.jsx` (37KB) / `admin-teams.jsx` (30KB) / `components.jsx` / `panels.jsx` / `icons.jsx` / `app.jsx` / `data.js` / `styles.css` (2316줄) / `styles-teams.css` (204줄)
  - `design_handoff_ibizdrive/prototype/*` (이전): G1~G8 트랙으로 일부 반영됨
- zip README: `C:/project/IbizDrive/.claude/design-zip-extract/design_handoff_ibizdrive/README.md`

## 이미 반영된 부분 (기존 design-handoff 트랙)

- G1~G3: TopBar 3-col grid + SearchBar ⌘K + shortcut cheat sheet 모달
- G4: FileTable 6열 + action menu
- G5: density 토글 (compact/default/comfortable)
- G6: searchbar-kbd-platform 분기
- G7: 폐기 (mobile)
- G8: shortcuts help button

이들은 메인 탐색기 일부만 다룸. zip의 신규 admin 영역 + 메인 탐색기 잔여 fidelity는 미반영.

## 미반영 inventory (PR #195 backlog)

- Admin Sharing 페이지 전체 (placeholder)
- Admin Overview 위젯 (UploadChart/FlagRow/DeptRow)
- Admin Storage cleanup-list
- Admin Retention/Audit 스타일 (LegalRow/SeverityTab)
- 메인 탐색기 미세 fidelity gap (사용자 회귀에서 발견, 정확한 영역은 Phase 2 진입 시 inventory)

## 외부 의존 / 회귀 세션 가드

- **회귀 세션이 frontend dev server 실행 가능성 高** — sweep worktree는 별도 디렉토리 + 별도 dev 서버 포트(4101+) 사용
- master 머지는 회귀 세션 영향 — phase별 PR 머지 시 사용자에게 회귀 세션 재시작 안내 필요
- Backend 코드는 본 트랙에서 변경 zero (frontend-only)

## CLAUDE.md §3 원칙 (sweep 중 깨면 안 됨)

1. URL이 "어디"를 소유 — `/d/:deptId/:folderId/...`, `/t/:teamId/:folderId/...`, `/shared/:folderId/...`
2. RightPanel = query param (`?file=`)
3. 낙관적 업데이트 비파괴 한정 (이동/삭제/권한은 pending)
4. DnD 컨텍스트 두 개 분리 (업로드 native ≠ 이동 dnd-kit)
5. 가상화 시 aria-rowcount/rowindex 필수

## 빠른 재개 (다음 세션)

1. `git checkout master && git pull` — master 동기화
2. `ls dev/active/design-fidelity-sweep/` — 본 트랙 파일 확인
3. `design-fidelity-sweep-tasks.md` 열고 `in_progress` task 확인
4. 진행 중 phase의 worktree로 이동 (예: `cd .claude/worktrees/design-sweep-phase-X`)
5. zip 위치 + plan 재확인 후 task 재개

## 참조

- BETA-RELEASE.md §7 v1.x deferred (sharing 항목 포함)
- docs/v1x-backlog.md Tier 1 (design gap 4건)
- CLAUDE.md §3 11원칙
- 디자인 zip README (design_handoff_ibizdrive/README.md)
