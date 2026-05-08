---
Last Updated: 2026-05-08
---

# design-variants-tweaks — Variant 시스템 + TweaksPanel (M13.1)

## 요약

`design-reference/styles.css` 의 디자인 토큰 중 `--accent-text` / `--success-soft` 누락분과 **Variant 시스템 4종**(default + notion + dropbox + terminal)이 master `frontend/src/app/globals.css` 에 미적용. 사용자가 시각적 토대를 통째로 얻지 못한 상태. 이 트랙에서 갭을 채우고 사용자가 런타임에 4종을 토글할 수 있도록 **TweaksPanel** 을 추가한다.

## 현재 상태 분석

- `frontend/src/app/globals.css` (140줄): `:root` + `[data-theme="dark"]` 토큰만 정의. variant 0건. `--accent-text` / `--success-soft` 누락.
- `frontend/src/lib/theme.ts` (48줄): `Theme = 'light' | 'dark'` + `getStored/getSystem/getInitial/apply/persist` 5함수. localStorage 키 `'theme'`.
- `frontend/src/app/layout.tsx`: 인라인 IIFE `themeInitScript` 로 FOUC 방지. variant 미포함.
- `frontend/src/components/topbar/ThemeToggle.tsx`: Sun/Moon lucide 토글, `aria-pressed` + `useTheme` 훅 사용.
- `frontend/src/stores/`: view.ts(viewMode) 있으나 variant 슬라이스 없음.
- `docs/01-frontend-design.md` §18 (라인 1496): M13 (디자인 토큰 적용, 2026-04-25 완료) 마커. M13.1 행 미존재.
- `docs/design-system.md` (334줄): 디자인 시스템 문서. variant 섹션 없음.
- `design-reference/styles.css` L1~115:
  - L21 `--accent-text: white` (light)
  - L25 `--success-soft: oklch(94% 0.03 150)` (light)
  - L57~75 `[data-variant="notion|dropbox"]` 블록
  - L78~92 `[data-variant="terminal"]` :root 블록 — `--font-sans: var(--font-mono)` 폰트 토글 포함
  - L94~113 terminal 17 selector 폰트/letter-spacing override — **옵션 B (KISS) 채택**: `--font-sans` :root 토글 한 줄 + `[data-variant="terminal"] body { letter-spacing: -0.01em; }` 한 줄로 흡수

## 목표 상태

- `data-variant` 속성으로 4종 토글 가능 (`default` | `notion` | `dropbox` | `terminal`).
- localStorage `'variant'` 영속, FOUC 없음 (theme과 동일 init script 동기 실행).
- `TweaksPanel` (TopBar settings 아이콘 → popover) 에서 variant select + density slider + ThemeToggle 통합.
- 누락 토큰 2종 보충 (`--accent-text`, `--success-soft`).
- `docs/design-system.md` + `docs/01-frontend-design.md` §18 + `docs/progress.md` 동기.
- 백엔드/API/DB 변경 0건. 기존 컴포넌트 props 변경 0건.

## Phase별 실행 지도

### Phase 1 — 토큰 보충
- `globals.css` `:root` 에 `--accent-text: white` + `--success-soft: oklch(94% 0.03 150)` 추가.
- `[data-theme="dark"]` 에 `--accent-text` (현재 accent 명도 대비 검정 또는 흰색 — design-reference 미정의이므로 `#0F0F0E` 추정), `--success-soft: oklch(28% 0.06 150)` (어두운 변형).
- `@theme inline` 에 `--color-accent-text` / `--color-success-soft` 매핑.
- 검증: typecheck / lint.

### Phase 2 — Variant 시스템 CSS
- `globals.css` 에 `[data-variant="notion"]` / `"dropbox"` / `"terminal"` 블록 이식.
- 옵션 B: terminal 의 17 selector 폰트 override 대신 `:root` 의 `--font-sans` 자체를 토글하고 `[data-variant="terminal"] body { letter-spacing: -0.01em; }` 한 줄만 추가.
- `[data-variant="notion"][data-theme="dark"]` 결합 블록도 이식.
- 검증: 수동 확인 (DevTools 에서 `<html data-variant="...">` 토글 시 색/폰트/높이 반영).

### Phase 3 — variantStore + util + hook
- `frontend/src/lib/variant.ts` 신규 — `theme.ts` 시그니처 미러:
  - `Variant = 'default' | 'notion' | 'dropbox' | 'terminal'`
  - `VARIANT_STORAGE_KEY = 'variant'`
  - `getStoredVariant() / getInitialVariant() / applyVariant() / persistVariant()` (system default 함수는 불필요 — `default` 가 폴백).
- `frontend/src/stores/variant.ts` 신규 — Zustand 슬라이스 (theme 패턴 부재 시 useTheme 훅 패턴을 따라 hook 으로만 구현 가능).
- `frontend/src/hooks/useVariant.ts` 신규 — `{ variant, setVariant }` (useTheme 패턴 미러).
- 검증: vitest unit 테스트 (variant.ts 5함수, theme.test.ts 패턴 미러).

### Phase 4 — FOUC init script 확장
- `layout.tsx` 의 `themeInitScript` 를 `appearanceInitScript` (또는 `themeAndVariantInitScript`) 로 확장: theme 분기 직후에 variant 동일 IIFE 추가. localStorage 'variant' 읽고 default 가 아니면 `setAttribute('data-variant', v)`.
- 검증: 빌드 후 SSR HTML 확인 (next build → next start), DevTools Network throttling 으로 깜빡임 없는지 시각 검증.

### Phase 5 — TweaksPanel 컴포넌트
- `frontend/src/components/topbar/TweaksPanel.tsx` 신규.
  - Trigger: TopBar 의 settings 아이콘 (Lucide `Settings` 또는 `SlidersHorizontal`).
  - Content (popover): variant select 4종(라디오 그룹) + density slider (`--row-h` 28~40px) + ThemeToggle 임베드.
  - density slider 는 `document.documentElement.style.setProperty('--row-h', ...)` 직접 적용 + localStorage `'density'` 영속 (선택 — MVP 에선 variant 가 row-h 를 결정하므로 density slider 는 옵션. plan 시점에 사용자에게 컨펌받기).
  - 키보드 접근: Esc 닫기, Tab focus trap.
- `TopBar.tsx` 에 TweaksPanel trigger 마운트 (기존 ThemeToggle 위치 교체 — ThemeToggle 은 TweaksPanel 내부로 이동).
- 검증: vitest (variant 변경 시 `document.documentElement[data-variant]` 검증, popover open/close, ThemeToggle 동작).

### Phase 6 — 테스트 보강
- `frontend/src/lib/variant.test.ts` (theme.test.ts 미러).
- `frontend/src/hooks/useVariant.test.tsx` (useTheme 패턴).
- `frontend/src/components/topbar/TweaksPanel.test.tsx` (variant 토글 / density / ThemeToggle 임베드 검증).
- `ThemeToggle.test.tsx` 가 TopBar 마운트를 가정한 케이스가 있다면 TweaksPanel 내부 마운트로 정정.
- 검증: `cd frontend && pnpm test --run` 전체 PASS.

### Phase 7 — 문서 동기화
- `docs/design-system.md` 에 §"Variant 시스템" 섹션 추가 (4종 토큰 표 + 사용법).
- `docs/01-frontend-design.md` §18 로드맵에 M13.1 행 추가 (라인 1496 다음).
- `docs/01-frontend-design.md` §5 (Zustand 슬라이스) 에 variant 슬라이스 추가 항목.
- `docs/progress.md` 최상단에 본 세션 entry (CLAUDE.md §7 양식).

### Phase 8 — PR
- 제목: `feat(design-variants-tweaks): variant 시스템 + TweaksPanel (M13.1)`.
- Test plan: typecheck / lint / test PASS 자동 박스 + variant 4종 수동 토글 체크 항목.

## Acceptance Criteria

- [ ] `globals.css` 에 `--accent-text` + `--success-soft` light/dark 모두 정의 + `@theme inline` 매핑.
- [ ] `[data-variant="notion|dropbox|terminal"]` CSS 블록 4종 정의 (default = 미설정).
- [ ] `lib/variant.ts` 5함수 + `hooks/useVariant.ts` + `localStorage('variant')` 영속.
- [ ] `layout.tsx` init script 가 variant 도 hydration 전 동기 적용 (FOUC 0).
- [ ] TopBar 에 TweaksPanel trigger 마운트, variant select + ThemeToggle 임베드.
- [ ] vitest 신규 테스트 추가 (`variant.test.ts`, `useVariant.test.tsx`, `TweaksPanel.test.tsx`).
- [ ] `pnpm typecheck && pnpm lint && pnpm test --run` 전부 PASS.
- [ ] `docs/design-system.md` + `docs/01 §18 M13.1` + `docs/progress.md` 갱신.
- [ ] 백엔드/API/DB/마이그레이션 변경 0건.

## 검증 게이트

- `cd frontend && pnpm typecheck`
- `cd frontend && pnpm lint`
- `cd frontend && pnpm test --run`
- 수동: `pnpm dev` → DevTools 에서 `<html>` 의 `data-variant` 4종 토글 + `data-theme` dark 조합. variant 별 색/높이/폰트 변화 확인.

## 리스크 & 완화

| 리스크 | 완화 |
|---|---|
| Init script 누락 시 variant FOUC | theme 과 동일 IIFE 패턴, suppressHydrationWarning 이미 적용됨. 빌드 후 SSR HTML 시각 검증 추가. |
| variant 키 충돌 (theme 과 다른 키) | localStorage 키 `'variant'` 명시, theme 의 `'theme'` 와 격리. |
| terminal 폰트 토글이 디자인 ref 와 미세 차이 | 옵션 B 채택 명시 (KISS), letter-spacing 한 줄로 보완. 미세 차이 발생 시 v1.x 에서 옵션 A 로 재이식 가능. |
| TweaksPanel popover 가 Radix UI 미설치면 자체 구현 | `package.json` 확인. 미설치 시 ARIA APG 패턴으로 직접 구현 (간단한 `<dialog>` 또는 absolute div + Esc 핸들러). |
| ThemeToggle 위치 변경으로 기존 테스트 깨짐 | TweaksPanel 내부 마운트로 정정 + 기존 `ThemeToggle.test.tsx` 단위 테스트는 유지 (컴포넌트 자체는 그대로). |
| density slider 가 variant row-h 와 충돌 | MVP 에서는 density slider 미포함 (variant 가 row-h 결정). 사용자 컨펌받기. |

## Plan 단계 사용자 컨펌 포인트

1. **density slider 포함 여부** — variant 가 이미 `--row-h` 를 결정하므로 중복일 수 있음. MVP 제외 추천 (KISS).
2. **dark theme 의 `--accent-text` 색** — design-reference 미정의. 추천: `#0F0F0E` (terminal `--bg` 와 동일, accent 위 가독성 검증 필요).
3. **TopBar 의 ThemeToggle 위치** — TweaksPanel 내부로 이동(추천) vs TopBar 에 ThemeToggle 그대로 + TweaksPanel trigger 별도(중복).

## 다음 작업자 빠른 재개

1. `dev/active/design-variants-tweaks/design-variants-tweaks-context.md` 의 SESSION PROGRESS 확인.
2. `dev/active/design-variants-tweaks/design-variants-tweaks-tasks.md` 의 미완료 phase 확인.
3. `design-reference/styles.css` L1~115 + `frontend/src/app/globals.css` 비교.
4. Phase 순서대로 진행 (각 Phase 끝에 typecheck/lint/test).
