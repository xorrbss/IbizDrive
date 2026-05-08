---
Last Updated: 2026-05-08
---

# design-variants-tweaks — Tasks

## Phase별 상태

- [ ] **Phase 1** — globals.css 토큰 보충
- [ ] **Phase 2** — Variant 시스템 CSS 블록
- [ ] **Phase 3** — variantStore + lib/variant.ts + useVariant 훅
- [ ] **Phase 4** — layout.tsx FOUC init script 확장
- [ ] **Phase 5** — TweaksPanel 컴포넌트 + TopBar 통합
- [ ] **Phase 6** — vitest 테스트 보강
- [ ] **Phase 7** — docs 동기화 (design-system, 01 §18, progress)
- [ ] **Phase 8** — PR

---

## Phase 1 — globals.css 토큰 보충

### 작업 전 필독
- `design-variants-tweaks-plan.md` Phase 1 acceptance.
- `design-reference/styles.css` L21 (`--accent-text: white`), L25 (`--success-soft`).

### 원본 코드 참조
- `design-reference/styles.css` L1~35 (`:root`).
- `design-reference/styles.css` L37~54 (`[data-theme="dark"]`).
- `frontend/src/app/globals.css` (현재 140줄).

### 구현 대상
- [ ] `frontend/src/app/globals.css` `:root` 에 `--accent-text: white;` 추가 (L22 직후 권장).
- [ ] `frontend/src/app/globals.css` `:root` 에 `--success-soft: oklch(94% 0.03 150);` 추가 (`--success` 다음 줄).
- [ ] `frontend/src/app/globals.css` `[data-theme="dark"]` 에 `--accent-text` (어두운 값, 추천 `#0F0F0E`) + `--success-soft: oklch(28% 0.06 150)` 추가.
- [ ] `@theme inline` 블록에 `--color-accent-text: var(--accent-text);` + `--color-success-soft: var(--success-soft);` 추가.

### 검증 참조
- `cd frontend && pnpm typecheck` exit 0.
- `cd frontend && pnpm lint` exit 0.

### 문서 반영
- 본 phase 단독 docs 변경 없음. Phase 7 에서 design-system.md 토큰 표 갱신 시 함께 기록.

---

## Phase 2 — Variant 시스템 CSS

### 작업 전 필독
- `design-variants-tweaks-plan.md` Phase 2 — 옵션 B (KISS) 채택 명시.
- `design-reference/styles.css` L57~113.

### 원본 코드 참조
- `design-reference/styles.css` L57~75 (`[data-variant="notion"]` + dark 결합 + `[data-variant="dropbox"]`).
- `design-reference/styles.css` L78~92 (`[data-variant="terminal"]` :root — `--font-sans` 토글 포함).
- `design-reference/styles.css` L94~113 (terminal 17 selector — **옵션 B 로 letter-spacing 한 줄로 흡수**).

### 구현 대상
- [ ] `frontend/src/app/globals.css` 에 다음 4개 블록 추가 (`@theme inline` 직후):
  - `[data-variant="notion"] { ... }` (L57~63 그대로).
  - `[data-variant="notion"][data-theme="dark"] { ... }` (L64~68 그대로).
  - `[data-variant="dropbox"] { ... }` (L71~75 그대로).
  - `[data-variant="terminal"] { ... }` (L78~92 그대로 — `--font-sans: var(--font-mono);` 포함).
  - `[data-variant="terminal"] body { letter-spacing: -0.01em; }` (옵션 B 한 줄).

### 검증 참조
- `cd frontend && pnpm typecheck` exit 0.
- `cd frontend && pnpm lint` exit 0.
- 수동: `pnpm dev` → DevTools `document.documentElement.setAttribute('data-variant','notion')` 입력 시 색/height/radius 변경 확인.
- 수동: `'terminal'` 입력 시 폰트가 mono 로 전환되는지 확인.

### 문서 반영
- Phase 7 에서 `docs/design-system.md` 에 variant 섹션 추가 시 본 phase 산출물 인용.

---

## Phase 3 — variantStore + lib/variant.ts + useVariant 훅

### 작업 전 필독
- `frontend/src/lib/theme.ts` 전체 (48줄) — 미러 대상.
- `frontend/src/hooks/useTheme.ts` (Read 필요).

### 원본 코드 참조
- `frontend/src/lib/theme.ts` — 5함수 시그니처.
- `frontend/src/hooks/useTheme.ts` — hook 패턴.

### 구현 대상
- [ ] `frontend/src/lib/variant.ts` 신규:
  - `export type Variant = 'default' | 'notion' | 'dropbox' | 'terminal'`.
  - `export const VARIANT_STORAGE_KEY = 'variant'`.
  - `getStoredVariant(): Variant | null` — localStorage 읽기 + 4종 검증.
  - `getInitialVariant(): Variant` — stored ?? 'default'.
  - `applyVariant(v: Variant): void` — `default` 면 `removeAttribute('data-variant')`, 아니면 `setAttribute`.
  - `persistVariant(v: Variant): void` — localStorage 쓰기. (try/catch).
- [ ] `frontend/src/hooks/useVariant.ts` 신규 — useTheme 패턴 미러:
  - `useState<Variant>` + `useEffect` 로 init 적용.
  - `setVariant(v)` — apply + persist 묶음.
  - 반환: `{ variant, setVariant }`.

### 검증 참조
- Phase 6 에서 vitest 추가 (`variant.test.ts`, `useVariant.test.tsx`).
- `cd frontend && pnpm typecheck` exit 0.

### 문서 반영
- Phase 7 에서 `docs/01-frontend-design.md` §5 (Zustand 슬라이스) 또는 별도 hooks 섹션에 useVariant 추가.

---

## Phase 4 — layout.tsx FOUC init script 확장

### 작업 전 필독
- `frontend/src/app/layout.tsx` L10~12 (themeInitScript IIFE).
- `frontend/src/lib/variant.ts` (Phase 3 산출물).

### 원본 코드 참조
- `frontend/src/app/layout.tsx` themeInitScript 패턴.

### 구현 대상
- [ ] `themeInitScript` 를 `appearanceInitScript` 로 rename (또는 보존하고 `variantInitScript` 별도 추가 후 둘 다 inject).
- [ ] variant IIFE 추가:
  ```js
  (function(){try{var v=localStorage.getItem('variant');if(v==='notion'||v==='dropbox'||v==='terminal')document.documentElement.setAttribute('data-variant',v);}catch(e){}})()
  ```
- [ ] `<script>` injection 위치 동일.

### 검증 참조
- `cd frontend && pnpm typecheck` exit 0.
- `cd frontend && pnpm build` 성공.
- 수동: build 후 `pnpm start` → localStorage `variant=terminal` 설정 후 새로고침 → 첫 paint 부터 terminal 색 (FOUC 0).

### 문서 반영
- 없음 (구현 디테일).

---

## Phase 5 — TweaksPanel 컴포넌트 + TopBar 통합

### 작업 전 필독
- `frontend/src/components/topbar/ThemeToggle.tsx` (UI 패턴).
- `frontend/src/components/topbar/TopBar.tsx` (Read 필요 — ThemeToggle 마운트 위치).
- `package.json` — Radix UI 또는 popover 라이브러리 설치 여부.

### 원본 코드 참조
- `design-reference/panels.jsx` (RightPanel 등 popover 가 있다면 패턴 참고).
- 기존 `frontend/src/components/files/RightPanel.tsx` 등 popover/dialog 패턴.

### 구현 대상
- [ ] `frontend/src/components/topbar/TweaksPanel.tsx` 신규:
  - Trigger: `<button aria-label="설정" aria-haspopup="dialog">` + Lucide `SlidersHorizontal` 또는 `Settings` 아이콘.
  - Content (popover/dialog):
    - 섹션 1 "테마": ThemeToggle 임베드.
    - 섹션 2 "변형": variant 4종 라디오 그룹 (`role="radiogroup"`).
  - 키보드: Esc 닫기, 외부 클릭 닫기, focus trap.
- [ ] `frontend/src/components/topbar/TopBar.tsx` 수정:
  - 기존 ThemeToggle 직접 마운트를 TweaksPanel trigger 로 교체.

### 검증 참조
- Phase 6 에서 vitest 추가.
- `cd frontend && pnpm typecheck && pnpm lint`.
- 수동: TopBar 의 settings 아이콘 → popover 열림 → variant 4종 토글 → 화면 색 변화 + ThemeToggle 동작.

### 문서 반영
- Phase 7 에서 `docs/01-frontend-design.md` §4 또는 §13 디자인 토큰 섹션에 TweaksPanel 마운트 위치 명시.

---

## Phase 6 — vitest 테스트 보강

### 작업 전 필독
- `frontend/src/lib/theme.test.ts` (있다면 패턴 미러).
- `frontend/src/components/topbar/ThemeToggle.test.tsx`.

### 원본 코드 참조
- `frontend/src/lib/theme.test.ts` — variant.test.ts 미러.
- 기존 컴포넌트 테스트 패턴.

### 구현 대상
- [ ] `frontend/src/lib/variant.test.ts`:
  - `getStoredVariant` — null / 4종 / invalid.
  - `getInitialVariant` — stored 우선 / 폴백 default.
  - `applyVariant` — default 면 removeAttribute, 아니면 setAttribute.
  - `persistVariant` — localStorage write / quota exceeded swallow.
- [ ] `frontend/src/hooks/useVariant.test.tsx`:
  - 초기 적용 / setVariant 호출 시 attribute + localStorage 둘 다.
- [ ] `frontend/src/components/topbar/TweaksPanel.test.tsx`:
  - Trigger 클릭 → popover 열림 (role=dialog).
  - variant 라디오 그룹 4종 노출 + 클릭 시 `data-variant` 변경.
  - ThemeToggle 임베드 동작.
  - Esc 키로 닫힘.

### 검증 참조
- `cd frontend && pnpm test --run` 전체 PASS, 신규 테스트 포함.

### 문서 반영
- 없음.

---

## Phase 7 — docs 동기화

### 작업 전 필독
- `docs/design-system.md` 현재 구조 (334줄).
- `docs/01-frontend-design.md` §18 (라인 1480~1500).
- `docs/progress.md` 최상단 entry 양식 (CLAUDE.md §7).

### 원본 코드 참조
- 본 트랙의 코드 변경 전체.

### 구현 대상
- [ ] `docs/design-system.md` 에 §"Variant 시스템" 섹션 추가:
  - 4종 토큰 표 (default / notion / dropbox / terminal — bg / accent / row-h / font 비교).
  - localStorage 키 + FOUC init script 설명.
  - TweaksPanel 사용 안내.
- [ ] `docs/01-frontend-design.md` §18 로드맵 라인 1496 다음에 M13.1 행 추가:
  ```
  | 13.1 | **Variant 시스템 + Tweaks** (M13.1, 완료 2026-05-08) | `[data-variant]` 4종(default/notion/dropbox/terminal) + lib/variant.ts + useVariant + layout init script(FOUC 0) + TweaksPanel(TopBar settings → popover, ThemeToggle 임베드 + variant 라디오 4종). 누락 토큰 보충(--accent-text, --success-soft). 옵션 B(:root --font-sans 토글). |
  ```
- [ ] `docs/01-frontend-design.md` 적절한 섹션에 useVariant 훅 추가 항목.
- [ ] `docs/progress.md` 최상단에 본 세션 entry (CLAUDE.md §7 양식: 완료 / 핵심 설계 결정 / 다음 세션 컨텍스트 / 블로커).

### 검증 참조
- `cd frontend && pnpm test --run` 통과 유지.
- 문서 링크 / 라인 참조 정합 (rg 로 backlink 확인).

### 문서 반영
- 본 phase 가 문서 동기화 자체.

---

## Phase 8 — PR

### 작업 전 필독
- 모든 phase 완료 + 검증 게이트 PASS.

### 원본 코드 참조
- 본 트랙의 모든 커밋.

### 구현 대상
- [ ] commit 별 정리 (Phase 단위, 작은 단위 권장).
- [ ] `git push -u origin feat/design-variants-tweaks`.
- [ ] `gh pr create`:
  - 제목: `feat(design-variants-tweaks): variant 시스템 + TweaksPanel (M13.1)`.
  - body: ## Summary (3 bullet) + ## Test plan (typecheck/lint/test 자동 + variant 4종 수동 토글).

### 검증 참조
- PR CI 통과.

### 문서 반영
- 머지 후 별도 archive PR 으로 `dev/active/` → `dev/completed/` 이관 (CLAUDE.md 와 progress.md 패턴).
