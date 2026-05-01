# M16V Grid 2D 키보드 wrap — Context

Last Updated: 2026-05-02

## SESSION PROGRESS

| Phase | 상태 | 요약 |
|---|---|---|
| M16VK.0 | ✅ done | worktree + dev-docs bootstrap (commit 58dac5e) |
| M16VK.1 | ✅ done | `lib/gridNav.ts` pure helper + 25 케이스 (commit 7087791) |
| M16VK.2 | ✅ done | `FileTable.tsx` 통합 + Grid 6 케이스 + 594/594 GREEN (commit 6f22e3d) |
| M16VK.3 | ✅ done | docs/01 §12.1 + progress entry + archive + PR + master squash-merge |

## Current Execution Contract

- **자율 실행 모드** (memory `feedback_autonomous_mode.md`): worktree 생성 직후부터 phase별 RED→GREEN→커밋 자율 진행. closure(merge/push/branch delete) + 트랙 시작/종료 게이트만 보고.
- **단일 PR 원칙**: phase별 commit은 worktree 안에서, 마지막에 squash-merge.
- **회귀 0**: 기존 List 모드 키보드 테스트 fail 시 즉시 ABORT + 보고.
- **TDD**: pure helper(`gridNav`)부터 RED 작성. 통합은 helper 안정화 이후.

## 현재 active phase

M16VK.0 (bootstrap) — 본 dev-docs 작성 완료 시점에 M16VK.1로 전환.

## 다음 세션 읽기 순서

1. `m16v-grid-keyboard-wrap-plan.md` — 목표 상태 표 + acceptance criteria.
2. `m16v-grid-keyboard-wrap-tasks.md` — 미완 phase의 참조 블록.
3. `frontend/src/components/files/FileTable.tsx:161-307` — `handleKeyDown` 현재 분기.
4. `frontend/src/hooks/useGridColumns.ts` — columns 산식.
5. `docs/01-frontend-design.md §12.1` — 키맵 표.
6. `docs/progress.md:218` — M16V backlog 항목 명시.

## 핵심 파일과 역할

| 파일 | 역할 |
|---|---|
| `frontend/src/components/files/FileTable.tsx` | view 분기 + handleKeyDown switch (수정 대상, M16VK.2) |
| `frontend/src/hooks/useGridColumns.ts` | columns 계산 hook (변경 없음, 입력) |
| `frontend/src/lib/gridNav.ts` | 신설 — `computeNextIndex` pure helper (M16VK.1) |
| `frontend/src/lib/gridNav.test.ts` | 신설 — helper unit test (M16VK.1) |
| `frontend/src/components/files/FileTable.test.tsx` | grid 모드 키 케이스 추가 (M16VK.2) |
| `docs/01-frontend-design.md` §12.1 | 키맵 표 갱신 (M16VK.3) |
| `docs/progress.md` | 트랙 종료 entry append (M16VK.3) |

## 중요한 의사결정

1. **Pure helper 추출**: FileTable.tsx handleKeyDown은 이미 130+라인. 핵심 로직(prev → next index 계산)을 `lib/gridNav.ts`로 분리해 ResizeObserver mock 없이 unit test. CLAUDE.md ULTIMATE INVARIANT 5(확장 전 검토) 충족 — 신규 모듈 추가가 정당화됨(테스트 가능성 확보).
2. **↑/↓ 오버슈트 정책**: ↑는 stay(첫 행 wrap 없음), ↓는 next row 항목이 있으면 `length-1`로 clamp. Windows Explorer / macOS Finder 패턴 답습.
3. **←/→ wrap 정책**: row 경계에서 prev/next row로 자연 wrap. List 모드 ↑/↓ 1D 패턴을 ←/→로 그대로 차용.
4. **List 모드 변경 0**: ←/→는 List에서 no-op 유지(설계 원칙 — 기존 행 동작과 충돌 없음).
5. **shift 확장**: 모든 방향에서 selectRange 호출 → 사용자 멘탈 모델 일관(현재 ↑↓도 동일).
6. **pending skip**: ↑/↓는 column stride 방향으로, ←/→는 1-step 선형 방향으로 같은 방향 진행하며 첫 non-pending 발견 시 정지. 진행 가능한 후보 없으면 stay.

## 빠른 재개 안내

```
cd C:/project/IbizDrive/.claude/worktrees/m16v-grid-keyboard-wrap
git status
cat dev/active/m16v-grid-keyboard-wrap/m16v-grid-keyboard-wrap-tasks.md
# 다음 미완 phase의 참조 블록을 따라 RED→GREEN 진행
cd frontend && pnpm test --run
```
