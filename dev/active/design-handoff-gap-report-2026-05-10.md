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

### G1. TopBar 높이 — **48px → 40px 축소 적용됨**
- 디자인: `grid-template-rows: 48px 1fr` (styles.css L134)
- 현재: `TopBar.tsx` L17 `h-10` (= 40px)
- **영향:** 8px 축소, 디자인 의도(검색 30px h를 9px 패딩으로 감싸는 형태) 깨짐
- **권장 라우팅:** 신규 작은 PR (frontend single-file). 또는 의도된 축소면 ADR + handoff README 패치.

### G2. TopBar 레이아웃 — 3-column grid 미반영
- 디자인: `grid-template-columns: auto 1fr auto` — 햄버거(좌) + 검색(중앙 max-w 560 mx-auto) + 액션(우)
- 현재: `flex justify-between` — 검색(좌, w-72=288px) + TweaksPanel/Avatar(우). 햄버거 없음.
- **영향:** 검색바 폭/위치 다름. 사이드바 collapse 토글(햄버거) 진입점 부재.
- **선결조건:** 사이드바 collapse 자체가 미구현이면 햄버거는 후속. Plan B 사이드바 3-section 작업과 함께 검토.

### G3. SearchBar 단축키/형태
- 디자인: 중앙 정렬 max-w 560 / h 30 / `⌘K` kbd 칩 / 우측 clear 버튼
- 현재: `SearchBar.tsx` L44 `w-72` (≈288px) / `/` 단축키 (`useGlobalShortcuts.ts` `FOCUS_SEARCH_EVENT`) / lucide `Search` 아이콘
- **영향:**
  - 단축키는 디자인 spec(⌘K)과 다름 — 의도된 차이일 수 있으나 `placeholder="파일 검색 ( / )"`가 시각적 hint 전부
  - polishlater: kbd 칩 표시
- **권장:** 의도된 변경이면 핸드오프 README에 반영. ⌘K로 가야 한다면 작은 PR로 `useGlobalShortcuts` 키 매핑만 변경.

### G4. FileTable 컬럼 — **6열 vs 5열, 폭 ≠**
- 디자인: `grid-template-columns: 36px 1fr 140px 130px 90px 44px` (체크 / 이름 / 크기 / 수정일 / 수정자 / 액션)
- 현재: `FileTable.tsx` L38 `28px 1fr 110px 130px 90px` (자리 / 이름 / 크기 / 수정일 / 수정자 — 액션 컬럼 없음, 체크박스 컬럼 28px)
- **영향:** 크기 110→140 (-30), 액션 44px 미존재. 코드 주석에 "현 M5 단계 — M7에서 체크박스/액션 컬럼 추가 시 재매핑" 명시 — 의도된 deferral.
- **권장 라우팅:** 기존 M7 마일스톤 plan에 종속. 별도 PR 불필요.

### G5. FileTable row 높이 — **default 34 → 40 (= comfortable)**
- 디자인: 기본 34px / compact 28 / comfortable 40 (styles.css L629-630). `--row-h` CSS var 토글.
- 현재: `FileTable.tsx` L28 `ROW_HEIGHT = 40` 하드코딩, density 토글 없음.
- **영향:** 한 화면 항목 수 ~15% 감소. 밀도 변경 UX 부재.
- **선결:** `--row-h` 토큰은 `globals.css` L48에 이미 존재(34px). FileTable이 이걸 안 씀.
- **권장:** 작은 PR — `ROW_HEIGHT`을 `var(--row-h)`(34) 기본으로 + viewStore에 `density: 'compact'|'default'|'comfortable'` 추가. TweaksPanel에 토글 노출.

### G6. GridView min-col-width — **172 → 140**
- 디자인: `auto-fill minmax(172px, 1fr)`
- 현재: `FileTable.tsx` L34 `GRID_MIN_COL_WIDTH = 140`
- **영향:** 카드가 디자인보다 23% 좁음 → 4:3 thumb 영역 작아 보임.
- **권장:** 1줄 변경. M16V 후속 PR에 묶거나 단독.

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

## 라우팅 제안 (gap → 트랙 매핑)

| Gap | 트랙 | 사이즈 |
|---|---|---|
| G1 TopBar 48px | 신규 single-file PR `fix-topbar-height` | XS (5분) |
| G2 TopBar 3-col grid + 햄버거 | Plan B (사이드바 collapse 같이) | M |
| ~~G1 TopBar 48px~~ | ✅ PR #148 (h-10 → h-12) | — |
| ~~G2 TopBar 3-col grid + 햄버거~~ | ✅ PR #168 (sidebar collapse 동반) | — |
| ~~G3 SearchBar ⌘K + 폭~~ | ✅ PR #168 (kbd 칩 + clear + ⌘K/Ctrl+K). 추가 plat 분기는 별도 트랙 (PR #172 in progress). | — |
| G4 FileTable 6열 | 기존 M7 plan (활성 트랙 — `feat/design-handoff-g4-filetable`) | M |
| G5 row 34 + density 토글 | 활성 트랙 — `feat/design-handoff-g5-density` | S |
| ~~G6 grid 172px~~ | ✅ PR #148 commit `166432b` | — |
| ~~G7 mobile-view~~ | ❌ **폐기 (2026-05-11 사용자 결정)** — 사내 데스크탑 메인. CLAUDE.md §3 원칙 13 동기화. | — |
| ~~G8 DropOverlay fidelity~~ | ✅ PR #148 commit `166432b` | — |
| ~~G9 statusbar 확인~~ | ✅ 이미 일치 — 변경 불필요 | — |

> **트랙 외 후속**: PR #171 (`?` 도움말 모달) + PR #174 (단축키 데이터 single-source) — 디자인 핸드오프 직접 항목은 아니지만 §12.1 키맵 spec 정합 강화. PR #168 종료 보고 backlog였음.

---

## 결론 (2026-05-11 갱신)

디자인 핸드오프 갭 **G1·G2·G3·G6·G8·G9 머지 완료**. **G4·G5는 활성 co-session 트랙** 진행 중. **G7은 폐기**(사내 데스크탑 메인 가정 — CLAUDE.md §3 원칙 13 동기화).

남은 활성 작업은 G4(FileTable 6열 + 액션 컬럼) + G5(density 토글) 두 트랙만. 두 트랙 머지 후 본 gap report는 archive(`dev/completed/`) 이동 후보.

**원본 권장 액션 결과:**
1. ~~G1 + G6 + G5(without density 토글) → `design-fidelity-quick-wins`~~ — PR #148로 G1+G6+G8 처리, G5 toggle은 별도 활성 트랙.
2. ~~G2/G3 → Plan B foundation에 흡수~~ — PR #168로 G2/G3 별도 trace 처리 (sidebar collapse 동반).
3. ~~G7/G8/G9 → 별도 spot-check~~ — G8/G9 spot-check 완료(PR #148), G7 폐기.
