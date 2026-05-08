---
Last Updated: 2026-05-08
---

# design-variants-tweaks — Context

## SESSION PROGRESS

- 2026-05-08 16:50 — Dev Docs bootstrap. worktree `feat/design-variants-tweaks` (master `f0ab324` 컷). Phase 1 시작 전. 사용자 컨펌 포인트 3건 plan 에 명시.

## Current Execution Contract

- 옵션 B (KISS) 채택: terminal variant 폰트 override 는 `:root --font-sans` 토글 + `[data-variant="terminal"] body { letter-spacing: -0.01em }` 두 줄로 흡수.
- 백엔드/API/DB/마이그레이션 변경 0건.
- 기존 컴포넌트 props 변경 0건. ThemeToggle 컴포넌트 자체는 보존하고 위치만 TweaksPanel 내부로 이동.
- 모든 phase 끝에서 `cd frontend && pnpm typecheck && pnpm lint && pnpm test --run` 통과 확인.
- CLAUDE.md §3 핵심 원칙 11개와 충돌 시 즉시 중단.

## 현재 active task

- Phase 1 — globals.css 토큰 보충 (`--accent-text`, `--success-soft` 추가 + `@theme inline` 매핑).

## 다음 세션 읽기 순서

1. 본 `design-variants-tweaks-context.md` (SESSION PROGRESS / Current Execution Contract).
2. `design-variants-tweaks-tasks.md` (미완료 phase + 참조 블록).
3. `design-variants-tweaks-plan.md` Phase 별 acceptance + 검증 게이트.
4. `design-reference/styles.css` L1~115 (토큰 + variant 블록 원본).
5. `frontend/src/app/globals.css` (현재 적용 상태).
6. `frontend/src/lib/theme.ts` (영속 패턴 — variant.ts 미러 대상).
7. `frontend/src/app/layout.tsx` (FOUC init script — variant 동기 적용 추가 위치).
8. `frontend/src/components/topbar/ThemeToggle.tsx` (UI 패턴 — TweaksPanel 임베드 대상).

## 핵심 파일과 역할

| 파일 | 역할 | 본 트랙 변경 |
|---|---|---|
| `design-reference/styles.css` | 디자인 핸드오프 원본 (1317줄) | 읽기 전용 |
| `frontend/src/app/globals.css` | 토큰 + variant CSS | Phase 1 (토큰) + Phase 2 (variant) |
| `frontend/src/lib/theme.ts` | theme 영속 5함수 | 미변경 (variant.ts 미러 패턴) |
| `frontend/src/lib/variant.ts` | **신규** — variant 영속 5함수 | Phase 3 |
| `frontend/src/hooks/useTheme.ts` | theme 훅 | 미변경 |
| `frontend/src/hooks/useVariant.ts` | **신규** — variant 훅 | Phase 3 |
| `frontend/src/stores/variant.ts` | **신규(옵션)** — Zustand 슬라이스 | Phase 3 — 단순하면 hook 으로만 구현하고 store 생략 |
| `frontend/src/app/layout.tsx` | FOUC init script | Phase 4 |
| `frontend/src/components/topbar/ThemeToggle.tsx` | 다크 모드 토글 | 미변경 (위치만 TweaksPanel 내부) |
| `frontend/src/components/topbar/TweaksPanel.tsx` | **신규** — variant select + ThemeToggle 임베드 | Phase 5 |
| `frontend/src/components/topbar/TopBar.tsx` | TopBar 셸 | Phase 5 (TweaksPanel trigger 마운트) |
| `docs/design-system.md` | 디자인 시스템 본문 (334줄) | Phase 7 (variant 섹션 추가) |
| `docs/01-frontend-design.md` §18 (라인 1496) | 로드맵 | Phase 7 (M13.1 행 추가) |
| `docs/progress.md` | 진행 로그 | Phase 7 (최상단 entry) |

## 중요한 의사결정

- **옵션 B (KISS)**: terminal variant 의 17 selector 폰트 override 는 디자인 ref 1:1 이식 대신 `:root --font-sans` 토글 + body letter-spacing 두 줄로 흡수. 미세 차이 발생 시 v1.x 옵션 A 로 재이식.
- **localStorage 키 분리**: `'theme'` (existing) + `'variant'` (new). 향후 density slider 도입 시 `'density'` 추가.
- **default = data-variant 미설정**: 4번째 옵션을 `[data-variant="default"]` 로 두지 않고 속성 자체 제거. CSS 우선순위 단순.
- **density slider 미포함 (MVP)**: variant 가 `--row-h` 를 결정하므로 중복. 사용자 컨펌 후 결정.
- **TweaksPanel 내부에 ThemeToggle 임베드**: TopBar 에 토글 2개 (설정 + 다크모드) 노출 회피. ThemeToggle 컴포넌트는 보존, 부모만 변경.
- **variant store 보류**: theme 도 store 없이 hook + localStorage 로 충분. variant 도 동일 패턴, Zustand 슬라이스 추가는 YAGNI.

## 빠른 재개

```text
cd C:\project\IbizDrive\.claude\worktrees\design-variants-tweaks
git status                # feat/design-variants-tweaks, clean
cd frontend
pnpm install              # 첫 진입 시
pnpm typecheck            # baseline
pnpm test --run           # baseline (현재 master 기준 PASS)
# Phase 1 부터 plan 따라 진행
```

## 사용자 컨펌 대기 항목

`design-variants-tweaks-plan.md` "Plan 단계 사용자 컨펌 포인트" §3:
1. density slider 포함 여부 (추천: 미포함).
2. dark theme `--accent-text` 색 (추천: `#0F0F0E`).
3. ThemeToggle 위치 (추천: TweaksPanel 내부).

세 항목 모두 추천안으로 진행하면 plan 그대로 실행 가능. 사용자 다른 결정 시 plan 갱신 후 진행.
