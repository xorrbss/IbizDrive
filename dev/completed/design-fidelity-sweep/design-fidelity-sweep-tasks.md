# Design Fidelity Sweep — Tasks

## Phase 1 — Tokens + globals.css/admin.css [CLOSED — 작업 불필요]

진단 (2026-05-12): zip styles.css의 토큰 영역과 현재 `globals.css`가 이미 1:1 정합. G1~G8 트랙들이 누적 반영하면서 토큰 sweep을 이미 마침.

- [x] T1.1 zip styles.css `:root` 토큰 추출 — 27개
- [x] T1.2 zip styles.css `[data-theme="dark"]` 토큰 추출 — 16개
- [x] T1.3 styles-teams.css 추가 토큰 — 없음 (클래스만, admin.css가 흡수)
- [x] T1.4 globals.css 토큰 영역 식별 — line 10~166
- [x] T1.5 토큰 비교 결과: 27/27 일치, dark 16/16 + 정합 확장 (`--accent-text`/`--success-soft`)
- [x] T1.6 admin.css `--fg-3` alias — 이미 처리됨 (line 17-19)
- [x] T1.7 Tailwind config — `@theme inline`으로 globals.css 안에서 토큰 매핑 처리 중. 별도 config 없음
- [x] T1.8~T1.10 작업 불필요 → PR 생성 안 함

**Phase 1 closure**: worktree `design-sweep-phase-1` + 브랜치 `feat/design-sweep-phase-1-tokens` 제거. Phase 2 직행.

## Phase 2 — 메인 탐색기 + 공통 컴포넌트

인벤토리 결과 (2026-05-12 Agent): 35 컴포넌트 중 ✓ 9 / ⚠ 21 / ✗ 5. **base linear variant만 채택**. variants 3종(notion/dropbox/terminal)은 v1.x backlog로 분리됨 (이미 globals.css에 정의되어 있음).

### Sub-phase 1 — Breadcrumb 신규 구현 (✗, 가장 큰 gap)

- [ ] **T2a.1** zip components.jsx Breadcrumb 코드 추출 (className: `.breadcrumb`, `.breadcrumb-btn`, `.breadcrumb-star`)
- [ ] **T2a.2** `useCurrentFolder().breadcrumb` 또는 동등 API 확인 — 백엔드가 breadcrumb 반환 여부
- [ ] **T2a.3** `frontend/src/components/folders/Breadcrumb.tsx` 신규 작성 (docs/01 §4 spec 준수)
- [ ] **T2a.4** explorer 레이아웃에 마운트
- [ ] **T2a.5** Breadcrumb.test.tsx 추가
- [ ] **T2a.6** typecheck + lint PASS

### Sub-phase 2 — FileRow 배지 + Sidebar NewButton/storage bar (⚠ → ✓)

- [ ] **T2b.1** FileRow에 star/lock/share/items 배지 추가 (zip FileRow line, 데이터 source 확인 — file.starred / file.permissions / file.itemsCount)
- [ ] **T2b.2** Sidebar "새로 만들기" primary 버튼 추가 (zip `.sidebar-new-btn`, FolderTree/Upload 진입점)
- [ ] **T2b.3** Sidebar 하단 storage bar 추가 (4px bar, "X GB / Y GB", `useStorageOverview` 또는 동등 hook)
- [ ] **T2b.4** Sidebar collapse 토글 (48px collapsed mode) — 이미 `useSidebarChromeStore` 있다면 wiring만
- [ ] **T2b.5** typecheck + lint PASS

### Sub-phase 3 — VersionTab 타임라인 시각화 (⚠ → ✓)

- [ ] **T2c.1** zip panels.jsx VersionTab line 추출 (`.version-row`, 좌측 8px dot + 1px 라인)
- [ ] **T2c.2** `VersionsTab.tsx` 타임라인 구조 재구성
- [ ] **T2c.3** typecheck + lint PASS

### Sub-phase 4 — FileTable/GridView hover/select 정밀화 (⚠ → ✓)

- [ ] **T2d.1** FileTable hover/selected/opened 상태 className 정렬 (zip `.tr.selected`, `.tr.opened` inset border)
- [ ] **T2d.2** GridView hover/체크 오버레이 스타일 정렬
- [ ] **T2d.3** typecheck + lint PASS

### Sub-phase 5 — density 변수 + 미세 정렬 (⚠ → ✓)

- [ ] **T2e.1** 각 컴포넌트 `--row-h` 변수 의존 확인 (density 토글 시 일관성)
- [ ] **T2e.2** RightPanel/UploadDock/ConflictDialog hover transition 정렬
- [ ] **T2e.3** variants(notion/dropbox/terminal) 정합은 v1.x backlog로 분리 (본 phase 도입 X)
- [ ] **T2e.4** typecheck + lint + test PASS
- [ ] **T2e.5** `pnpm build` PASS

### Phase 2 closure

- [ ] **T2.X** 누적 commit을 한 PR로 정리 (`feat/design-sweep-phase-2-explorer`)
- [ ] **T2.Y** Phase 2 머지 후 Phase 3 진입

## Phase 3 — Admin Console 8탭

이전 디자인 zip inventory 결과:
- ✓ members / teams / permissions (반영 완료, 미세 fidelity는 자체 sweep 가능)
- ⚠ overview / storage / audit / retention (위젯 부분 미구현)
- ✗ sharing (전체 placeholder, frontend 신규 + backend는 v1.x backlog 유지)

### Sub-phase 1 — AdminSharing 전체 신규 (✗ → ✓ visual)

- [ ] **T3a.1** zip admin.jsx `AdminSharing` (line 582~706) 코드 + className 추출
- [ ] **T3a.2** zip styles.css `.policies-*`, `.domains-*`, `.flags-*` 등 클래스 정의 추출
- [ ] **T3a.3** `/admin/sharing/page.tsx` placeholder를 zip 디자인 1:1 재현 (정책행 + 도메인 allow/block + 플래그된 공유 검토 큐)
- [ ] **T3a.4** mock 데이터 컴포넌트(static array — backend endpoint v1.x deferred 명시) — 페이지 자체는 인터랙티브 (radio/toggle UI만 frontend, mutation 호출 X)
- [ ] **T3a.5** AdminGuard 유지, "v1.x 후속 트랙 — backend endpoint 미연결" callout 페이지 상단에 명시
- [ ] **T3a.6** typecheck + lint PASS
- [ ] **T3a.7** commit + push

### Sub-phase 2 — AdminOverview 위젯 (⚠ → ✓)

- [ ] **T3b.1** zip admin.jsx `AdminOverview` (line 98~182) — UploadChart / FlagRow / DeptRow 추출
- [ ] **T3b.2** 현재 `/admin/page.tsx` + DashboardSummary 컴포넌트 grep — 위젯 추가 위치 식별
- [ ] **T3b.3** UploadChart (단순 sparkline 또는 막대 — backend storage/upload metric API 확인, 없으면 mock)
- [ ] **T3b.4** FlagRow (플래그된 공유 row 표시)
- [ ] **T3b.5** DeptRow (부서별 사용량 row)
- [ ] **T3b.6** typecheck + lint + commit + push

### Sub-phase 3 — AdminStorage cleanup-list (⚠ → ✓)

- [ ] **T3c.1** zip admin.jsx `AdminStorage` (line 448~517) cleanup-list 영역 추출
- [ ] **T3c.2** 현재 `/admin/storage/page.tsx` + StorageOverviewCards 보강
- [ ] **T3c.3** typecheck + lint + commit + push

### Sub-phase 4 — AdminRetention/Audit 스타일 보강 (⚠ → ✓)

- [ ] **T3d.1** AdminRetention `LegalRow / legal-list` (zip admin.jsx line 862~922)
- [ ] **T3d.2** AdminAudit `SeverityTab` 필터 + `audit-stream` 가시화 (zip line 711~782)
- [ ] **T3d.3** typecheck + lint + commit + push

### Sub-phase 5 — AdminTabBar/Layout fidelity + 잔여 페이지 정렬 (✓ 마무리)

- [ ] **T3e.1** AdminTopHeader + AdminTabBar 8탭 네비 — zip admin.jsx line 30~93 vs 현재 `/admin/layout.tsx` 비교, fidelity 정합
- [ ] **T3e.2** departments / system / trash 페이지 디자인 토큰만 정렬 (zip에 별도 정의 없음, 기존 admin.css 사용)
- [ ] **T3e.3** members / teams / permissions 미세 fidelity 점검 (이미 반영 영역, 추가 정합 필요 시만 정정)
- [ ] **T3e.4** typecheck + lint + commit + push

### Phase 3 closure

- [ ] **T3.X** Phase 3 PR `feat/design-sweep-phase-3-admin` 생성 + CI green + 머지
- [ ] **T3.Y** BETA-RELEASE.md §1 readiness 갱신 + design fidelity gap 4건 closure
- [ ] **T3.Z** docs/v1x-backlog.md Tier 1 design gap 4건 제거 또는 완료 표시
- [ ] **T3.W** progress.md closure entry
- [ ] **T3.V** dev/active/design-fidelity-sweep/ → dev/completed/ 아카이브
- [ ] **T3.U** 사용자에게 v1.0.0-beta 태그 push 안내

## 트랙 종료

- [ ] Phase 3 머지 + CI GREEN
- [ ] `pnpm build` PASS
- [ ] BETA-RELEASE.md `Last Updated` 갱신
- [ ] docs/v1x-backlog.md design gap 4건 → 완료 표시 또는 제거
- [ ] progress.md closure entry
- [ ] dev/active/design-fidelity-sweep/ → dev/completed/로 아카이브
- [ ] 사용자에게 v1.0.0-beta 태그 push 안내
