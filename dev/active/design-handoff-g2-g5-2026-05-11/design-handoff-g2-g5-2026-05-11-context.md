---
Last Updated: 2026-05-11
Status: 🟢 ACTIVE — Phase 1 in progress
---

# Design Handoff G2~G5 — Context

## SESSION PROGRESS

| 세션 | 진행 |
|---|---|
| 2026-05-11 (현재) | 트랙 부트스트랩, dev-doc 3파일 작성, Phase 1 worktree 생성 |

## Current Execution Contract

- **모드**: 자율 실행 (memory `feedback_autonomous_mode.md`)
- **게이트**: 큰 결정 / push / PR 생성 / 머지는 사용자 승인
- **컨텍스트 임계값**: 60/70/75/80% 핸드오프 (memory `feedback_context_limit.md`)
- **PR 패턴**: phase별 1 PR (memory `feedback_ralph_pattern.md` 차용)
- **Subagent**: frontend-heavy 트랙에 효과적 (memory `feedback_subagent_workflow.md`). 각 phase에서 implementer + spec reviewer + code quality reviewer dispatch 가능
- **Review fix loop**: spec/quality reviewer feedback → fix → re-review

## 현재 Active Task

**Phase 1 — G3 SearchBar**

Branch: `feat/design-handoff-g3-searchbar`
Worktree: `C:/project/IbizDrive/.claude/worktrees/design-handoff-g2-g5/`

다음 단계:
1. SearchBar.test.tsx RED 작성 (max-w 560, kbd 칩 platform 분기, clear 버튼)
2. useGlobalShortcuts.test.ts 확장 (⌘K|Ctrl+K dispatch 추가)
3. GREEN 구현
4. typecheck/lint/test
5. 자체 리뷰 + 사용자 게이트 → push + PR

## 다음 세션 읽기 순서

1. `design-handoff-g2-g5-2026-05-11-plan.md` (전체 phase 지도)
2. `design-handoff-g2-g5-2026-05-11-tasks.md` (현재 phase 상세 task)
3. `dev/active/design-handoff-gap-report-2026-05-10.md` (원본 spec)
4. `frontend/prototype/styles.css` (디자인 핸드오프 원본)
5. Phase별 영향 파일 (plan.md §Phase 실행 지도 참조)

## 핵심 파일과 역할

### 변경 영향 받는 frontend 파일

| 파일 | Phase | 역할 |
|---|---|---|
| `src/components/topbar/SearchBar.tsx` | 1 (G3) | 검색 입력 컴포넌트 |
| `src/hooks/useGlobalShortcuts.ts` | 1 (G3) | 전역 단축키 dispatch |
| `src/lib/density.ts` | 2 (G5) | density 타입 + 헬퍼 (신규) |
| `src/hooks/useDensity.ts` | 2 (G5) | density state hook (신규) |
| `src/app/globals.css` | 2 (G5) | `[data-density]` cascade rule 추가 |
| `src/components/topbar/TweaksPanel.tsx` | 2 (G5) | density 라디오 그룹 추가 |
| `src/components/topbar/TopBar.tsx` | 3 (G2) | flex → grid 3-col |
| `src/components/files/FileTable.tsx` | 4 (G4) | GRID_COLS 6열 + header 매핑 |
| `src/components/files/FileRow.tsx` | 4 (G4) | 체크박스 + action 버튼 시각 |

### Spec 진실 출처

| 파일 | 역할 |
|---|---|
| `frontend/prototype/styles.css` | 디자인 핸드오프 원본 CSS |
| `frontend/prototype/components.jsx` | 디자인 핸드오프 컴포넌트 reference |
| `dev/active/design-handoff-gap-report-2026-05-10.md` | gap 분석 + 라우팅 제안 |
| `docs/01-frontend-design.md` | 프로젝트 design contract (필요 시 §10 검색, §12 단축키 갱신) |

## 중요한 의사결정

### D1. density 우선순위
**결정**: `[data-density]`가 variant default `--row-h`를 override.
**이유**: density는 사용자 명시적 선택이고, variant 시각 정체성보다 사용자 의도 우선.
**효과**: variant=notion(40px default) + density=compact(28px) → 28px.

### D2. ⌘K + `/` 공존
**결정**: 디자인 spec ⌘K/Ctrl+K 추가하되 `/` fallback 유지.
**이유**: muscle memory 보호 + 디자인 핸드오프 따름 (placeholder/kbd 칩은 ⌘K 표시).
**효과**: 두 단축키 모두 작동, UI hint는 ⌘K 우선.

### D3. G2 햄버거 분리
**결정**: 이번 트랙은 TopBar 3-col grid 레이아웃만, 햄버거 / 사이드바 collapse는 Plan B sidebar 트랙으로.
**이유**: 사이드바 collapse infra 미존재. 햄버거만 추가하면 dead button.
**효과**: 좌측 컬럼은 placeholder div (Plan B에서 햄버거 wire).

### D4. G7 Mobile view 제외
**결정**: 이번 트랙 backlog.
**이유**: 사내 데스크톱 메인 가정 (사용자 확인), gap-report `낮은 우선순위 backlog`.
**효과**: 별도 마일스톤 M-mobile에서 다룸.

### D5. FileTable action 버튼 scope
**결정**: 버튼 layout만 (⋯ 아이콘), 클릭 시 메뉴 내용은 후속.
**이유**: G4의 시각 fidelity가 목표 (6열 grid). 메뉴 내용은 별도 feature.
**효과**: aria-label 설정 + onClick은 toast/console placeholder. 후속 PR에서 ContextMenu wire.

## 빠른 재개

1. `cd C:/project/IbizDrive/.claude/worktrees/design-handoff-g2-g5/frontend`
2. `pnpm install` (필요 시)
3. `git status` 확인 — 현재 Phase에서 어디까지 진행됐는지
4. `tasks.md` 체크박스 확인
5. RED test부터 (TDD)

## 마지막 commit pointer (각 phase 종료 시 갱신)

- Phase 0 부트스트랩: (commit 후 채움)
- Phase 1 G3: —
- Phase 2 G5: —
- Phase 3 G2: —
- Phase 4 G4: —
- Phase 5 closure: —
