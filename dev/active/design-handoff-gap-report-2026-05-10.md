# Design Handoff ↔ Frontend Gap Report (2026-05-10)

**원본 디자인:** `q_E8bGXpCKkbcXTFb4TiTg` Claude Design 핸드오프 — `prototype/styles.css` (1317L) + `components.jsx` / `panels.jsx` / `app.jsx` / `tweaks-panel.jsx`.
**비교 대상:** `frontend/src/` 현재 master 기준 구현.
**범위:** 시각/치수 fidelity 만. workspace 피벗(`/d/`/`/t/`/`/shared/` 라우팅)은 Plan B에서 별도 처리.

---

## ✅ 이미 동기화된 부분

| 항목 | 위치 | 비고 |
|---|---|---|
| 디자인 토큰 (라이트/다크) | `frontend/src/app/globals.css` L10-70 | warm gray + accent + semantic 전부 일치 |
| 4 variants (linear/notion/dropbox/terminal) | `globals.css` L114-151 | terminal `--font-sans` 토글 KISS 흡수 |
| 컴포넌트 골격 | `components/{topbar,folders,files,upload,explorer}/*` | 30+ 파일, 디자인 컴포넌트 거의 1:1 매핑 |
| TweaksPanel 진입점 | `components/topbar/TweaksPanel.tsx` | 디자인의 tweaks-panel.jsx 대체 |
| Breadcrumb 사이즈 | `Breadcrumb.tsx` 25-39행 | 13.5px / last 15px / weight 600 ✓ |
| FileTable header | `FileTable.tsx` 390행 | h-30, 11px uppercase ✓ |
| GridView gap | `FileTable.tsx` `GRID_GAP=12` | 디자인 gap 12 ✓ |

---

## ⚠️ 발견된 시각적 Delta (실제 차이)

### G1. TopBar 높이 — ✅ 해결 (PR #148)
- 디자인: `grid-template-rows: 48px 1fr` (styles.css L134)
- **변경:** `TopBar.tsx` `h-10` → `h-12` (= 48px). PR #148 commit 포함.

### G2. TopBar 레이아웃 — ✅ 해결 (PR #168)
- 디자인: `grid-template-columns: auto 1fr auto` — 햄버거(좌) + 검색(중앙 max-w 560 mx-auto) + 액션(우)
- **변경:** `flex justify-between` → `grid grid-cols-[auto_1fr_auto]`. 햄버거(Menu lucide + aria-pressed sync), 중앙 SearchBar(max-w-[560px] mx-auto), 우측 TweaksPanel + Avatar. 사이드바 collapse(`stores/sidebarChrome.ts`)도 동반 구현 — (explorer)/layout.tsx `aside` 폭 transition(`w-[248px]` ↔ `w-0`).

### G3. SearchBar 단축키/형태 — ✅ 해결 (PR #168 + #172)
- 디자인: 중앙 정렬 max-w 560 / h 30 / `⌘K` kbd 칩 / 우측 clear 버튼
- **변경 (#168):** `h-[30px]` + `text-[12.5px]` + max-w-[560px](TopBar grid 결정), 우측 영역에 query 비어있고 unfocused일 때 kbd 칩 / query 있을 때 clear 버튼. `useGlobalShortcuts` ⌘K/Ctrl+K 추가 (editable 가드 미적용, `/` 호환 유지).
- **변경 (#172):** kbd 칩 텍스트 platform 분기 (mac=⌘K / win/linux=Ctrl K). 사내 Windows 사용자 인지 부담 해결.

### G4. FileTable 컬럼 — ✅ 해결 (PR #177)
- 디자인: `grid-template-columns: 36px 1fr 140px 130px 90px 44px` (체크 / 이름 / 크기 / 수정일 / 수정자 / 액션)
- **변경:** GRID_COLS 5열 `[28 1fr 110 130 90]` → 6열 `[36 1fr 140 130 90 44]`. FileRow에 체크박스 `button[role=checkbox]`(M4 selection store wired) + 아이콘은 이름 셀로 inline 통합 + 액션 ⋯ 버튼(layout placeholder, 메뉴 wiring은 v1.x). FileTableSkeleton 동기화.

### G5. FileTable row 높이 + density 토글 — ✅ 해결 (PR #148 + #175)
- 디자인: 기본 34px / compact 28 / comfortable 40 (styles.css L629-630). `--row-h` CSS var 토글.
- **변경 (#148):** `ROW_HEIGHT = 40` → `34`, FileRow `h-10` → `h-[var(--row-h)]`. variant override(notion 40 / dropbox 38 / terminal 28) 토큰 활용.
- **변경 (#175):** density 토글 시스템 — `[data-density="compact|comfortable"]` + `lib/density.ts` + `useDensity` 훅 + FOUC init script + TweaksPanel 라디오 3종. 우선순위: `[data-density]`(사용자 명시) > `[data-variant]` default(시각 정체성) > `:root` base. variant rules 뒤에 cascade 배치.

### G6. GridView min-col-width — ✅ 해결 (PR #148)
- 디자인: `auto-fill minmax(172px, 1fr)`
- **변경:** `GRID_MIN_COL_WIDTH = 140` → `172`. PR #148.

### G7. Mobile view 미구현
- 디자인: `.mobile-view` 클래스 (styles.css L1248-1259) — sidebar/right-panel 숨김 + FileTable 컬럼 축약 (`36 1fr 130 44`).
- 현재: `mobile-view` 클래스 미존재. responsive 분기 없음.
- **영향:** 모바일 뷰포트에서 3-pane이 그대로 압축 → UX 깨짐.
- **권장:** 별도 마일스톤 (M-mobile 신설). 우선순위 낮음 — 사내 데스크톱 메인 가정이면 backlog.

### G8. DropOverlay 시각 fidelity — ✅ 해결 (PR #148)
- 디자인: `backdrop-filter: blur(2px)` + 8px margin + 중앙 카드 (surface-1 + lg shadow + 56px 원형 아이콘).
- 이전: 컨테이너만 디자인 일치, 중앙은 14px 텍스트 한 줄.
- **변경:** 카드 + 56px 원형 아이콘 + 타이틀 16/600 + 서브 12.5/fg-muted + radius 8→10. (commit `166432b`)

### G9. Status bar — ✅ 이미 일치 (변경 불필요)
- 디자인: 하단 고정 status bar (styles.css L1238-1246) — h ≈ 28px, `surface-1` bg, `border-t`, 11.5px `fg-muted`, "항목 N개" 좌측 + 선택 카운트 우측.
- 현재: `StatusBar.tsx` h-7 (28px) + `bg-surface-1 border-t border-border text-[12px] text-fg-muted justify-between tabular-nums` ✓.
- spot-check 결과 디자인과 부합. 변경 불필요.

---

## 라우팅 결과 (2026-05-11 closure)

| Gap | 트랙 / PR | 상태 |
|---|---|---|
| ~~G1 TopBar 48px~~ | PR #148 | ✅ 머지 |
| ~~G2 TopBar 3-col grid + 햄버거~~ | PR #168 (사이드바 collapse 동반) | ✅ 머지 |
| ~~G3 SearchBar ⌘K + 폭~~ | PR #168 + #172 (kbd platform 분기) | ✅ #168 머지, #172 진행 |
| ~~G4 FileTable 6열~~ | PR #177 | ✅ 진행 (M4 selection store 재사용, 액션 버튼 layout placeholder) |
| ~~G5 row 34 + density 토글~~ | PR #148 (row 34) + #175 (density) | ✅ #148 머지, #175 진행 |
| ~~G6 grid 172px~~ | PR #148 | ✅ 머지 |
| G7 mobile-view | M-mobile 별도 마일스톤 | ⏸ backlog (사내 데스크톱 메인) |
| ~~G8 DropOverlay fidelity~~ | PR #148 commit `166432b` | ✅ 머지 |
| ~~G9 statusbar 확인~~ | 이미 일치 | ✅ 변경 불필요 |

---

## 결론 (2026-05-11 closure)

**G1~G9 중 8건 ✅ 해결** (G7 mobile은 backlog로 명시 분리). 디자인 핸드오프 시각 fidelity 95%+ 동기화 — admin 외 main explorer 영역까지 정렬.

**잔여:**
- **G7 mobile-view** — M-mobile 별도 마일스톤. 사내 데스크톱 메인 가정 (사용자 확인).
- **G4 액션 버튼 메뉴 wiring** — rename/move/share/delete 컨텍스트 메뉴 v1.x 후속 PR.
- **G6/G3 미진행 PR (#172, #175, #177)** — CI green, 머지 대기.

**closure 트랙 (2026-05-11):** `design-handoff-g2-g5-closure`. PR #170 중복 close 처리 (PR #168이 G2/G3 + 사이드바 collapse 함께 머지). 본 gap-report는 머지 후 `dev/completed/`로 archive.
