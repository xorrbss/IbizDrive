# 진행 상황 (Progress Log)

> 각 세션이 완료될 때마다 **최상단에 추가**합니다. 기존 내용은 보존.
> 양식: `CLAUDE.md §7` 또는 각 BRIEF의 회고 섹션 참조.

---

## 2026-04-25 — M15 완료 (Layout Extras: SortChip + ViewSwitch + StorageBar + RightPanel 탭)

### 완료
- [M15] **hooks/useSetSortParams.ts** — URL `?sort=&dir=` setter, 다른 query params 보존
- [M15] **components/files/SortChip.tsx + test** — 네이티브 `<select>`(이름/수정일/크기) + ArrowUp/Down 방향 버튼. 네이티브 select로 키보드/ARIA 무료 (3 tests)
- [M15] **hooks/useViewParam.ts + test** — `?view=list|grid` 양방향. setView('list')는 ?view 제거, setView('grid')는 ?view=grid (3 tests). M16 사전 인프라
- [M15] **components/files/ViewSwitch.tsx + test** — segmented 토글 (List/LayoutGrid icons, aria-pressed). active 시 surface-1 + shadow-sm (3 tests)
- [M15] **FolderToolbar** — 우측에 SortChip + ViewSwitch 마운트. flex-1 spacer로 양쪽 분리
- [M15] **lib/api.ts** — `getStorageQuota()` mock (65 GB / 100 GB, 50ms latency)
- [M15] **lib/queryKeys.ts** — `qk.storageQuota()`
- [M15] **hooks/useStorageQuota.ts** — staleTime 5분
- [M15] **components/layout/StorageBar.tsx + test** — `mt-auto`로 사이드바 하단 push. 사용량 % + progressbar(role/aria-valuenow) + GB/GB + 업그레이드 버튼. 90/100% 임계 시 warn/danger 색 자동 분기 (3 tests)
- [M15] **(explorer)/layout.tsx** — 사이드바 `<FolderTree/>` 다음에 `<StorageBar/>` 마운트
- [M15] **components/files/RightPanel.tsx + test** — 헤더-바디 사이 tablist 추가 (세부정보/버전/활동/권한). role=tab/tablist/tabpanel + aria-selected/controls/labelledby. ←→/Home/End 키보드 이동 + roving tabindex. fileId 변경 시 details로 리셋 (+3 tests)
- [M15] **검증** — typecheck PASS · lint PASS · **192 tests PASS** (M11 기준 177 → +15)
- [M15] **로드맵** — docs/01 §18 M15 행 완료 마커(2026-04-25)

### 핵심 설계 결정
- **SortChip은 네이티브 `<select>`** — Radix dropdown은 의존성/번들 비용. 네이티브가 키보드/ARIA/모바일 모두 무료. 디자인은 chip 컨테이너로 감쌈
- **ViewSwitch는 M15에서 URL만, Grid 본체는 M16** — `?view=grid` 진입은 가능하지만 FileTable/SearchResults는 list 그대로 렌더. 분기는 M16의 책임. URL 인프라 선행으로 M16 진입 시 변경 범위 축소
- **StorageBar는 mock 데이터** — 실제 quota API는 백엔드 의존(docs/02 §5.x 미정). 65/100 GB 고정값. 임계 색상(90/100%)은 토큰(`bg-warn`, `bg-danger`)으로 자동 적용
- **RightPanel 탭 = local state** — `?file_tab=` URL 동기화 안 함. 패널을 닫고 다시 열면 details로 리셋되는 게 자연스러운 UX (`useEffect [fileId] → setTab('details')`)
- **버전/활동/권한 탭은 placeholder** — 각각 M5(업로드 본체)/M11/M8(권한 매트릭스 미정) 의존. 탭 인프라만 선행하여 후속 마일스톤에서 placeholder만 교체
- **flex-1 spacer로 toolbar 양쪽 분리** — 좌측 업로드 버튼, 우측 SortChip+ViewSwitch. 디자인 ref와 동일

### 다음 세션 컨텍스트
**M16 (Grid View)**
- M15 ViewSwitch URL 인프라 활용 — `useViewParam()`으로 `view === 'grid'` 분기
- FileTable에 grid 모드 추가 — 가상화는 list 전용. grid는 CSS grid + IntersectionObserver 검토
- design-reference `GridView`/`GridThumb` 패턴
- 썸네일 mock — mimeType별 색상 placeholder 또는 큰 Lucide 아이콘 카드

**M8 (권한 UI)**
- docs/03 §3 권한 매트릭스 미정 — 백엔드 미들웨어 설계 의존
- 일단 프론트는 mock permissions(`useEffectivePermissions`?)로 진행 가능. 403 전역 처리는 M3에서 완료
- RightPanel 권한 탭 본체 + BulkActionBar의 파괴적 액션 숨김 분기

---

## 2026-04-25 — M11 완료 (검색: URL `?q=` canonical + debounce + abort + 결과 그리드)

### 완료
- [M11] **lib/queryKeys.ts** — `SearchFilters` type alias(`Record<string, never>` — 향후 type/owner/date 등 확장) + `qk.search(q, filters)` 추가
- [M11] **lib/api.ts** — `searchFiles({ q, filters }, { signal })` mock. `normalizeForSearch` 적용 후 `MOCK_FILES` 부분일치 검색 (`includes`) + 한글 locale sort. 200ms 시뮬 latency + `AbortSignal` 즉시/사후 cancel 양쪽 처리(`DOMException('Aborted', 'AbortError')`). <2자 즉시 빈 배열 (7 tests)
- [M11] **hooks/useDebounce.ts** — generic `useDebounce<T>(value, ms)`. setTimeout/clearTimeout (4 tests, fakeTimers)
- [M11] **hooks/useSearch.ts** — `useQuery<FileItem[], Error>`. `useDebounce(rawQuery.trim(), 300)` → `normalizeForSearch` → `enabled: normalized.length >= 2`. `placeholderData: (prev) => prev` (타이핑 중 이전 결과 유지). `staleTime: 30_000` (4 tests: <2자 disabled, 결과 반환, trim, lowercase)
- [M11] **components/files/SearchEmpty.tsx** — SearchX 아이콘 + "검색 결과가 없습니다" + q 표시
- [M11] **components/files/SearchRow.tsx** — FileRow 슬림 버전 (DnD 없음). columns: icon / name / parentName(위치) / updatedAt / updatedBy. 행 36px, role=row + aria-rowindex + data-file-id
- [M11] **components/files/SearchResults.tsx** — 가상화 그리드. SearchHeader(role=status, aria-live=polite, "검색 결과: 'q' · N개 항목"). useFolderTree + findNode로 부모 폴더명 lookup. 키보드 네비(↑↓/Space/Ctrl+A/Enter/Esc) + selection store 연동. Enter on folder → router.push(/files/<id>) (URL 자연 변경으로 ?q= drop). FileTable과 동일한 4가지 상태 처리(skeleton/empty/error/forbidden) (3 tests)
- [M11] **components/layout/TopBar.tsx** — `?q=` URL canonical 양방향 동기화. `useSearchParams.get('q')`로 mount 시드, `useDebounce(query, 300)`로 `router.replace` 호출(타이핑 중 history pollution 방지). clear 버튼은 즉시 URL 제거. 다른 query params(`?file=` 등) 보존 (7 tests, +2 신규)
- [M11] **ClientFilesPage** — `?q=` 정규화 후 ≥2자면 검색 모드 진입. `<SearchResults query={rawQ}/>` 렌더 + FileTable·StatusBar 숨김. Breadcrumb·FolderToolbar·BulkActionBar는 그대로 표시(현재 폴더 컨텍스트 유지)
- [M11] **검증** — typecheck PASS · lint PASS · 177 tests PASS (M14 기준 157 → +20 신규)
- [M11] **로드맵** — docs/01 §18 M11 행 완료 마커(2026-04-25)

### 핵심 설계 결정
- **URL `?q=` canonical (Option A)** — 별도 `/search` 라우트(B)나 dropdown(C) 대신 인라인 결과 페이지. selection store / RightPanel(`?file=`) / 키보드 인프라 그대로 재사용. 폴더 클릭 시 `router.push(/files/<id>)`로 pathname 자체가 바뀌어 `?q=` 자연 소실 (Next15 App Router 동작)
- **enable threshold = 2자** — normalize 후 길이로 판정. trim + NFC + lowercase + 공백 collapse 일관 적용. 백엔드 검색 API 동일 정규화 가정(docs/02 §3)
- **debounce 300ms** — 사용자 perception 임계와 cancel 비용 균형. AbortSignal로 inflight 요청 자동 취소(useQuery v5는 queryKey 변경 시 자동 abort)
- **placeholderData = (prev) => prev** — 타이핑 중 결과 깜빡임 제거. v5 패턴(v4 `keepPreviousData` 대체). `isFetching` 분리하여 SearchHeader에 "검색 중…" 표시
- **SearchRow ≠ FileRow** — FileRow는 동일 부모 폴더 기준 DnD draggable. 검색 결과는 부모가 섞여서 DnD 의미 없음. 별도 슬림 컴포넌트로 책임 분리(props 부담 감소)
- **부모 폴더명 컬럼** — useFolderTree 캐시(60s staleTime) + findNode lookup. 별도 API 라운드트립 0건. design-reference 검색 결과의 "위치" 컬럼 매칭
- **검색 모드 시 StatusBar 숨김** — StatusBar는 "현재 폴더 컨텍스트"(folderId 의존). 검색은 폴더-횡단이라 의미 충돌. SearchHeader가 항목 개수 역할 대체
- **SearchResults 테스트 — virtualizer 행은 e2e** — jsdom layout 0px라 `getVirtualItems()` 빈 배열. unit test는 SearchHeader 개수/role=grid만 검증, 실제 행 렌더는 Playwright(v1.x)에서

### 블로커 / TODO
- **백엔드 search API 미정** — 정규화 일치 정책(docs/02 §3) 외에 ranking/highlighting 스펙 미설계. v1.x에서 `filters: SearchFilters` 확장(type/parent/owner/date) 시 docs/01 §10 + docs/02 §7.x 동기 업데이트 필요
- **search API에 `signal: AbortSignal` 통과** — useQuery v5 자동 abort, mock api도 처리. 실제 fetch 구현 시 axios `CancelToken` 또는 fetch `signal` 전달 패턴 유지

### 다음 세션 컨텍스트
**M9 (휴지통 + Undo)**
- `/trash` 라우트 + 휴지통 전용 FileTable variant
- Delete 키 → 5초 Undo 토스트 (현재 M10에서 키바인딩만 등록)
- 영구 삭제 / 복원 — 백엔드 트랜잭션 의존 (docs/02 §6.5)

**M8 (권한 UI)**
- 403 전역 처리는 M3에서 완료. 권한별 비활성/숨김 분기 + RightPanel 권한 탭
- docs/03 §3 권한 매트릭스 미완성 → 대기

---

## 2026-04-25 — M14 완료 (Visual Identity: TopBar + Lucide + 밀도 + StatusBar)

### 완료
- [M14] **lucide-react 0.468 의존성 추가** — 트리쉐이킹 아이콘. React 19 호환. design-reference의 inline SVG를 표준 라이브러리로 환원
- [M14] **lib/fileIcons.tsx** — `getFileIcon(item)`/`getFileIconColor(item)`. mime/type → Lucide 컴포넌트 매핑(folder/image/video/pdf/spreadsheet/code/archive/word/text/default) + 색상 (folder=accent, pdf=danger, sheet=#3EA971, image=#B06BCC, video=#C95A7B, code=#5A7C9E, default=fg-muted) (13 tests)
- [M14] **FileRow** — emoji 함수 제거 → `<Icon size={16} className={iconColor} strokeWidth={1.6} />`. `h-10` → `h-9` (40 → 36px). aria-hidden 유지
- [M14] **FileTable** — `ROW_HEIGHT = 40` → `36` (가상화 픽셀 동기화)
- [M14] **components/layout/TopBar.tsx** — `grid grid-cols-[auto_1fr_auto] h-12` (48px). 좌: Menu icon-btn (사이드바 토글 스텁). 중: SearchBox (Search icon + input + ⌘K hint, max-w-560, focus-within으로 surface-1 + border-strong). 우: ThemeToggle (Sun/Moon, `data-theme` 직접 조작) + Help + Avatar("나", 26px accent 원). `useEffect`로 `app:focus-search` 이벤트 수신 → input.focus() (5 tests)
- [M14] **components/layout/StatusBar.tsx** — `h-7 border-t bg-surface-1 text-[11.5px]`. `useFilesInFolder` + `useSelectionStore` 구독. 좌: "N개 항목 · M개 선택됨", 우: 선택 size sum (folder=null 제외). role=contentinfo (3 tests)
- [M14] **(explorer)/layout.tsx** — `grid-rows-[48px_1fr]` 재구성. TopBar 마운트 후 sidebar+main 가로 분할. 사이드바 brand mark는 그대로 유지 (M13 적용분)
- [M14] **ClientFilesPage** — 메인 컬럼 끝(FileTable 아래)에 `<StatusBar folderId={folderId} />` 추가
- [M14] **검증** — typecheck PASS · lint PASS · 157 tests PASS (M10 기준 136 → +21 신규)
- [M14] **로드맵** — docs/01 §18 M14 행에 완료 마커(2026-04-25)

### 핵심 설계 결정
- **StatusBar = main 하단** — sidebar 하단은 M15 StorageBar(저장공간 진척률)로 분리. StatusBar는 "현재 폴더 컨텍스트"(항목/선택/size sum), StorageBar는 "사용자 계정 컨텍스트"
- **FileRow 36px (단일 밀도)** — 디자인 ref의 `density-comfortable` 40px는 sparse, `density-compact` 28px는 hit-area 부족. 36px 중간값 채택. 밀도 toggle은 v1.x
- **TopBar 위치 = 전체 폭** — design-reference 구조 그대로. grid-rows로 재구성하여 sidebar 브랜드(M13)와 TopBar 좌측 menu icon이 시각적으로 분리 (현재 menu는 no-op)
- **테마 토글 비영구** — `document.documentElement.dataset.theme` 직접 조작. localStorage 영속화는 v1.x. M13 토큰 체계상 className 변경 0건으로 즉시 전환
- **Lucide 아이콘 색상은 인라인 hex** — `text-[#3EA971]` 등. 정식 토큰 등록은 6열 확장(v1.x)과 함께 일괄 검토
- **검색 입력은 컴포넌트 내부 state** — M11 진입 시 URL `?q=` 파라미터로 승격 + `useDebouncedSearchQuery` 추가. 현재는 placeholder/포커스/⌘K hint만
- **`/` 키 → TopBar focus 결합** — M10이 디스패치하던 `app:focus-search` 이벤트의 첫 리스너 등록. 기존 listener 없으면 no-op이던 lazy 패턴 그대로 검증

### 다음 세션 컨텍스트
**M15 (Layout Extras)**
- SortChip — 현재 헤더 클릭으로만 정렬. URL `?sort=&dir=` 그대로 사용하는 dropdown UI
- ViewSwitch — List/Grid 토글. `viewMode` Zustand 슬라이스 신설 (URL? 검토 필요 — 현재 URL 진실 출처 원칙상 query param이 자연스러움)
- StorageBar — 사이드바 하단 (`sidebar-storage` 클래스 매핑). 저장공간 % + 진척바 + "용량 업그레이드" 버튼
- RightPanel 탭 — 세부정보/버전/활동/권한 4개. M8 권한 UI와 함께 검토

**M16 (Grid View)**
- M15 ViewSwitch 의존. design-reference `GridView`/`GridThumb` 패턴
- 가상화는 List만 적용 — Grid는 CSS grid + IntersectionObserver 검토 (별도 spec)

**TopBar 검색 (M11)**
- `query` state → URL `?q=` 파라미터 승격
- `lib/api.search` 신규 (debounce 250ms, abort)
- `qk.search(q, folderId?)` 키 팩토리

**테마 영속화 (v1.x)**
- localStorage `ibizdrive:theme` + 시스템 prefers-color-scheme 폴백
- M9 설정 페이지에 토글 추가

### 블로커
- 없음

### 마일스톤 상태 (docs/01 §18)
- ✅ M1~M7, M10, M13, M14 완료. M8(권한 UI)는 docs/03 §3 작성 후 진입 가능. M11(검색)은 M14 TopBar 인프라 + M10 `app:focus-search` 결합 완료 → 즉시 진입 가능. M15는 디자인 ref 매핑만 남은 상태.

---

## 2026-04-25 — M10 완료 (고급 키보드 + 접근성 마무리)

### 완료
- [M10] **api.renameFile mock** — VALIDATION_ERROR (빈 이름) / RENAME_CONFLICT (같은 부모 정규화 중복) / NOT_FOUND, 폴더 시 MOCK_TREE도 갱신 (6 tests)
- [M10] **에러 코드 정합성** — docs/02 §8의 기존 코드 `RENAME_CONFLICT`(409) / `VALIDATION_ERROR`(400) 그대로 사용 (원칙 #12). 신규 코드 추가 없이 계약 유지
- [M10] **stores/renameUi.ts** — `{ isOpen, targetId, targetName, error }` + open/close/setError (3 tests)
- [M10] **useRenameFile** — markPending → renameFile → invalidate(filesInFolder/fileDetail/+folderTree+folder if folder) → unmarkPending + close, 실패 시 setError (5 tests)
- [M10] **RenameDialog** — role=dialog aria-modal, input focus + select-all, 이전 focus 복귀, role=alert 에러, 동일/빈 이름 disabled (7 tests)
- [M10] **useGlobalShortcuts** — `/` 키 → window CustomEvent 'app:focus-search' (input/textarea/contenteditable/modifier 시 무시, JSDOM contentEditable 폴백 포함) (7 tests)
- [M10] **FileTable handleKeyDown 확장** — Shift+↑↓ (anchor 유지 selectRange), Ctrl/Meta+↑↓ (focus only), F2 (단일 선택 또는 focus → openRename), Delete (selection or focus → confirm → useDeleteBulk)
- [M10] **ClientFilesPage 마운트** — RenameDialog + useGlobalShortcuts() 호출
- [M10] **검증** — typecheck PASS · lint PASS · 136 tests PASS (M7 기준 108 → +28 신규)
- [M10] **로드맵** — docs/01 §18 M10 행에 완료 마커(2026-04-25)

### 핵심 설계 결정
- **F2 다이얼로그 (인라인 X)** — 가상화 컨테이너에서 인라인 편집은 row 리렌더링으로 입력 휘발 위험. 다이얼로그는 MoveFolderDialog 패턴 그대로 재사용
- **Shift+↑↓ anchor 안정성** — selectRange가 lastClickedId를 변경하지 않으므로 진동 없음. M4 anchor 폴백(null/pending/폴더 변경) 그대로 동작
- **Ctrl/Meta+↑↓ alias** — 현재 ↑↓가 이미 focus-only이므로 modifier는 §12.1 키맵 명세 만족용 별칭
- **Delete native confirm** — 브라우저 내장 포커스 트랩/스크린리더 지원, MVP zero-cost. 디자인 일관성 필요해지면 ConfirmDialog로 교체 (M14/M15)
- **`/` 트리거는 lazy 이벤트** — 검색 입력 컴포넌트(M11/M14)와 디커플. listener 없으면 no-op
- **원칙 #6 (서버가 진실)** — RenameDialog는 빈 입력 외 client validation X. 충돌은 서버 RENAME_CONFLICT → setError로 다이얼로그 유지
- **에러 코드 계약 준수** — spec 작성 시 `NAME_CONFLICT`/`INVALID_NAME` 가정했으나 docs/02 §8에 이미 `RENAME_CONFLICT`/`VALIDATION_ERROR`가 동일 의미로 존재 → 코드를 docs에 정합 (원칙 #12)

### 다음 세션 컨텍스트
- 검색 입력 컴포넌트(M11) 마운트 시 `useEffect`에서 `window.addEventListener(FOCUS_SEARCH_EVENT, onFocus)` 등록 잊지 말 것 (`useGlobalShortcuts`가 이미 디스패치 중)
- ConfirmDialog 디자인 시스템 컴포넌트화는 M14 Visual Identity와 함께 검토 (현재 native confirm)
- 다중 선택 + F2 일괄 이름 변경(prefix 등)은 v1.x 범위
- 백엔드 연결 시 `api.renameFile`는 `PATCH /files/:id` 또는 `POST /folders/:id/rename`으로 교체
- BulkActionBar에 "이름 변경" 버튼 추가는 M14 Visual Identity와 함께 (단일 선택 시만 활성화)

### 블로커
- 없음

### 마일스톤 상태 (docs/01 §18)
- ✅ M1~M7, M10, M13 완료. M8 (권한 UI)는 docs/03 §3 작성 후 진입 가능. M11 검색은 M10의 `app:focus-search` 트리거 활용 가능.

---

## 2026-04-25 — M7 완료 (DnD 이동: dnd-kit + 다이얼로그 듀얼 경로)

### 완료
- [M7] **@dnd-kit/core 6.x 설치** — 이동 전용 (업로드 native DnD와 분리, 원칙 #7)
- [M7] **lib/folderTreeUtils.ts** — `findNode`/`containsNode`/`isSelfOrDescendantOfAny` 순수 유틸 (12 tests)
- [M7] **api.moveFiles mock** — MOCK_TREE/MOCK_FILES 상태 갱신, self/descendant/target 검증, 3 에러 코드 throw (5 tests)
- [M7] **에러 코드 추가 (docs/02 §8)** — `MOVE_INTO_SELF` / `MOVE_INTO_DESCENDANT` / `TARGET_NOT_FOUND` (원칙 #12)
- [M7] **stores/moveUi.ts** — Zustand 다이얼로그 슬라이스 (`isMoveDialogOpen`/`moveIds`/`moveSourceFolderId`) (3 tests)
- [M7] **components/dnd/types.ts** — `MoveDragData` 타입 + droppable id prefix
- [M7] **useDragPayload** — selection vs single-row 결정, containsFolderIds 캐시 조회 (4 tests)
- [M7] **useMoveBulk** — markPending → moveFiles → invalidate(source/target/folderTree/fileDetail) → unmarkPending (3 tests)
- [M7] **MoveDragOverlay** — `role="status" aria-live="polite"` 카운트 배지 (행 복제 X)
- [M7] **useFolderDroppable** — useDndContext로 active 읽음, isInvalid/isSameFolder/isOver/isDragging 플래그
- [M7] **DndProvider** — PointerSensor distance:5px (클릭 vs 드래그 구분), DragEnd 시 self/descendant/같은-폴더 no-op
- [M7] **explorer/layout.tsx** — DndProvider 마운트 (sidebar+main 모두 droppable 영역)
- [M7] **FolderTree / Breadcrumb / FileRow(폴더)** — drop 타겟 통합 + 시각화
- [M7] **FileRow draggable** — useDraggable + dragData 합성, hook 호출 순서 안정화 위해 비폴더 행도 droppable hook 호출 (`__not_a_target__`)
- [M7] **BulkActionBar 이동 버튼** — 스텁 제거, openMoveDialog(ids, folderId) 호출
- [M7] **MoveFolderDialog** — radiogroup 폴더 트리 picker, source/self/descendant disabled, Esc/Enter, role="dialog" aria-modal (5 tests)
- [M7] **ClientFilesPage 마운트** — UploadConflictDialog 옆에 MoveFolderDialog 마운트
- [M7] **린트 정리** — useDragPayload/useMoveBulk 테스트 wrapper에 displayName 부여
- [M7] **검증** — typecheck PASS · lint PASS · 108 tests PASS (M5 기준 76 → +32)
- [M7] **로드맵** — docs/01 §18 M7 행에 완료 마커(2026-04-25) + 핵심 DoD 추가

### 핵심 설계 결정
- **듀얼 진입**: 마우스(DnD) + 키보드(BulkActionBar 다이얼로그). 두 경로 모두 `useMoveBulk` mutation으로 수렴 (단일 책임)
- **DragOverlay = 카운트 배지** (`📎 N개 항목 이동 중`). 행 복제 X — 가상화/접근성 충돌 회피
- **자기/후손 차단 3중 방어**: ① useFolderDroppable.disabled (드롭 자체 불가) ② DndProvider.handleDragEnd 재검증 ③ api.moveFiles에서 throw
- **낙관적 업데이트 X** (원칙 #3): selection.markPending → mutation 완료 후 invalidate. 실패 시 unmarkPending만.
- **무효화 매트릭스 (원칙 #6)**: source/target `filesInFolder` (모든 sort/dir 변종, prefix 매치) + `folderTree` + 각 id의 `fileDetail`
- **`__not_a_target__` 트릭**: FileRow에서 폴더가 아닌 행도 useFolderDroppable을 호출해 React Hook 순서 안정화. 트리에 없는 id이므로 자동으로 드롭 비대상.

### 다음 세션 컨텍스트
- 백엔드 연결 시 api.moveFiles는 `POST /folders/:id/move` 또는 `POST /files/move-bulk`로 교체 (mock fakeXHR 패턴 유지)
- DragOverlay 카운트 배지의 i18n 처리는 v1.x 검색 마일스톤(M11)과 함께 처리
- Task 17 DnDProvider 통합 테스트는 jsdom DnD 한계로 스킵 — Playwright e2e에서 검증 (관련 항목은 M10 접근성/키보드 마일스톤에 함께)
- M8 권한 UI는 `usePermission()` 훅 이미 BulkActionBar에 사용 중 → 권한 매트릭스 (docs/03 §3) 작성 필요

### 블로커
- 없음

### 마일스톤 상태 (docs/01 §18)
- ✅ M1~M7 완료. M8(권한 UI)는 docs/03 §3 작성 후 진입 가능. M13(디자인 토큰) 별도 완료.

---

## 2026-04-25 — M13 완료 (Claude 디자인 시스템 토큰 적용)

### 완료
- [M13] `design-reference/` 번들 추가 (Claude 핸드오프: `IbizDrive.html` + `styles.css` 1318줄 + `README.md` + 4 jsx 참고용)
- [M13] `docs/design-system.md` 갱신 — §5에 M5 업로드 컴포넌트 매핑 섹션 추가, §10 Open Questions(다크 모드 토글, variant 범위, 폰트 로딩, 6열 확장)
- [M13] M5 업로드 컴포넌트 4종 className만 styles.css 치수에 맞춰 조정 (JSX/props/handlers/aria 변경 없음):
  - `UploadButton`: h-7 px-2.5 + border-accent (`.btn-primary` 매핑)
  - `UploadOverlay`: inset-2 + backdrop-blur-[2px] + accent 8% + rounded-lg (`.drop-overlay`)
  - `UploadQueueDock`: w-[340px] max-h-[420px] + border-border-strong + header bg-surface-2 + progress h-[2px] bg-surface-3
  - `UploadConflictDialog`: max-w-[460px] + 백드롭 rgba(0,0,0,.32)
- [M13] `.gitignore` 초기 생성 (`.tmp/`, `.claude/`, `node_modules/`, `.next/`, etc.)

### DoD
- ✅ typecheck / lint / test 76/76 통과
- ✅ SSR HTML 검증: `bg-bg`, `bg-surface-1`, `bg-accent`, `text-fg` 등 토큰 클래스 렌더링 확인
- ✅ CSS 번들 검증: `.bg-accent { background-color: var(--accent); }` 등 13종 토큰 유틸 정상 생성
- ✅ 코드베이스 전수 검사: `bg-white` / `bg-gray-*` / `text-gray-*` 0건
- ✅ 사용자 시각 확인 (스크린샷): 따뜻한 회색 배경, 인디고 accent 버튼, surface-1 사이드바 모두 토큰 값 적용 확인

### 원칙 체크
- ✅ 디자인 진실 출처: `design-reference/styles.css` → `globals.css` (이미 prior 세션 0315a04에서 적용) → `@theme inline`으로 Tailwind 유틸 노출
- ✅ "토큰만, 구조 변경 없음" 원칙 준수: JSX 트리, props, handlers, aria 속성 모두 그대로

### 사용자 결정 (2026-04-25 세션)
시각적 임팩트가 큰 추가 작업(TopBar / Lucide 아이콘 / FileRow 밀도 / StatusBar / SortChip / ViewSwitch / 6열 테이블 / RightPanel 탭)은 M13 범위에서 명시적으로 제외하고 후속 마일스톤으로 분리:
- **M14 Visual Identity** — TopBar + Lucide 아이콘 + FileRow 밀도 + StatusBar
- **M15 Layout Extras** — SortChip + ViewSwitch + StorageBar + RightPanel 탭
- **M16 Grid View** — FileTable grid 모드 (M14 ViewSwitch 의존)

→ `docs/01-frontend-design.md §18` 로드맵에 추가됨.

### 다음 세션 컨텍스트
**M14 진입 시 필요**
- 의존성 1개 추가 검토: `lucide-react` (아이콘) — 사용자 confirm 필요
- TopBar는 새 컴포넌트 (`src/components/layout/TopBar.tsx`) — `app/(explorer)/layout.tsx` grid를 `grid-rows-[48px_1fr]`로 재구성
- FileRow는 emoji → SVG 아이콘 매핑 테이블 도입 (mime → 아이콘)
- StatusBar는 사이드바 하단 또는 main 하단 고정 — 디자인 결정 필요
- 테스트 영향: FileRow 테스트가 emoji assertion을 쓰면 수정 필요 (현재 없음 확인 → 영향 0)

**브랜드 다크 모드 토글 UI**
- M13에서 토큰만 정의됨. 토글 UI는 M14 TopBar에 포함 검토.

### 블로커
- 없음

---

## 2026-04-25 — M5 완료 (업로드: multipart + 충돌 + 실패 분류)

### 완료
- [M5] `lib/uploadErrors.ts` 5종 분류 (network/permission/quota/server/conflict) + 단위 테스트 8개
- [M5] `lib/fakeXhr.ts` + 매직 파일명 6종 (normal/conflict.pdf/huge.bin/deny.txt/srv_500.any/net_fail.any) + 단위 테스트 7개
- [M5] `lib/api.ts#uploadFile` — FakeXHR 반환 (교체 경계: 실제 XHR로 교체 시 소비자 변경 없음)
- [M5] `stores/upload.ts` — queue/applyToAll/enqueue/updateTask/resolveConflict/retry/cancel/clearDone + 단위 테스트 8개
- [M5] `hooks/useUpload.ts` — store subscribe 기반 XHR orchestration + done시 `filesInFolder` invalidate + 단위 테스트 8개 (DoD o 포함)
- [M5] `hooks/useNativeFileDrop.ts` — `types.includes('Files')` 가드로 dnd-kit 분리 (원칙 #7), depth counter + 단위 테스트 4개
- [M5] `hooks/useUploadBeforeUnload.ts` — pending(`queued|uploading|conflict`) > 0 시 경고
- [M5] UI 5개: `UploadButton` / `FolderToolbar` / `UploadOverlay` / `UploadQueueDock` / `UploadConflictDialog`
  - UploadOverlay (2 tests), UploadQueueDock (4 tests), UploadConflictDialog (5 tests) — a11y (aria-modal, aria-labelledby/describedby, Esc=skip, Tab 포커스트랩, applyToAll)
- [M5] `FileTable` — `containerRef` + `useNativeFileDrop` + `UploadOverlay` 통합, early return을 body 변수로 리팩토링하여 Empty/Error/Forbidden 상태에도 drop 동작
- [M5] `ClientFilesPage` — `FolderToolbar` + `UploadQueueDock` + `UploadConflictDialog` + `useUploadBeforeUnload` 통합
- [M5] `FileTableEmpty` — `UploadButton` CTA 삽입 (재사용)
- [M5] `docs/01 §5.3` 구현 노트 추가 (paused/tusUrl/overwrite 제외 사유, pendingCount/enqueue/applyToAll/cancel 의미론)

### DoD
- ✅ typecheck / lint / test 전체 통과 (기존 30 + M5 46 = 76 passing)
- ⏳ 수동 검증 a~l, n — 사용자 브라우저 확인 대기 (pnpm dev → /files/root → 시나리오 12종)

### 원칙 체크
- ✅ #1 URL folderId canonical — `task.targetFolderId`는 `enqueue` 시점의 folderId 스냅샷 (Zustand는 "무엇을" 올릴지만 가짐)
- ✅ #3 낙관적 업데이트 비파괴적만 — 업로드 결과 낙관 append 금지, done 시 `invalidateQueries({ queryKey: [...qk.files(), 'list', folderId] })`로 prefix match
- ✅ #7 DnD 분리 — native는 `FileTable` 컨테이너만, `types.includes('Files')` 가드로 dnd-kit 이벤트 무시
- ✅ #12 에러 코드 — `lib/uploadErrors.ts`가 docs/02 §8의 status 코드 매핑 유지

### 다음 세션 컨텍스트
**M5.1 (tus 재개 업로드)**
- `UploadTask`에 `tusUrl`, `paused` 상태 도입
- `useUpload`의 transport만 교체 (store/UI 변경 없음)

**실제 백엔드 연결**
- `api.uploadFile` 내부를 실제 `XMLHttpRequest`로 교체 (인터페이스 동일)
- `FakeXHR` 파일 및 매직 파일명 테스트는 제거 또는 e2e로 이관

**M7 DnD 이동 + 드롭 타겟 확장**
- `useNativeFileDrop`을 `FolderTree` 노드에도 연결 (dnd-kit 이동과 공존)
- 동일 가드(`types.includes('Files')`)로 분리 유지

### 블로커
- 없음

---

## 2026-04-25 — 디자인 시스템 적용 (M5 업로드 구현 진입 전)

### 완료
- [DS] `docs/design-system.md` 신규 — 토큰 2-layer 전략 (base CSS vars + `@theme inline` 매핑), 컴포넌트별 클래스 매핑
- [DS] `frontend/src/app/globals.css` 재작성 — color/typography/radius/shadow/spacing 토큰, `[data-theme="dark"]` 다크 모드, focus-visible 전역 링, 스크롤바 스타일
- [DS] `(explorer)/layout.tsx` — 3-col 레이아웃 + 사이드바 브랜드 마크 (22px accent 사각형 + "IbizDrive" 14px semibold)
- [DS] `FolderTree.tsx` — active: `bg-accent-soft text-accent font-medium`, inactive: `hover:bg-surface-2 hover:text-fg`, 깊이 들여쓰기 유지
- [DS] `Breadcrumb.tsx` — 마지막 노드 `text-[15px] font-semibold text-fg`, 구분자 `›` `text-fg-subtle`
- [DS] `BulkActionBar.tsx` — `bg-accent-soft border-y` 바, `h-7 px-2.5 rounded` 버튼 패턴, 위험: `hover:bg-[color-mix(in_oklch,var(--danger)_12%,transparent)] hover:text-danger`
- [DS] `FileTable.tsx` — `GRID_COLS` 상수 추출 후 FileRow에 prop 전달, 헤더 `h-[30px] bg-surface-1 text-[11px] uppercase tracking-[0.04em]`
- [DS] `FileRow.tsx` — `gridCols` prop 수신, 상태별 class 토큰화 (pending/selected/hover), focus-visible은 globals.css 전역 링이 담당
- [DS] `RightPanel.tsx` — `w-[360px] bg-surface-1 border-l`, 상세 그리드 `grid-cols-[80px_1fr] text-[12px]`, 에러 `text-danger`
- [DS] Empty/Error/Forbidden/Skeleton 4개 상태 컴포넌트 — `flex-1 flex flex-col items-center justify-center gap-3 py-[60px]` 공통, danger 변형은 `bg-[color-mix(in_oklch,var(--danger)_10%,transparent)]`
- [DS] `ClientFilesPage.tsx` — 2-pane 래퍼 `flex flex-1 min-h-0`, 메인에 `bg-bg`
- [DS] route-level states — `loading.tsx` / `error.tsx` / `not-found.tsx` 모두 center-layout 상태 패턴으로 통일

### 원칙 준수 체크
- ✅ 구조·로직 변경 없음 (className만 수정)
- ✅ aria 속성 전원 유지 (aria-rowcount/rowindex, role=grid/row/gridcell, role=toolbar, aria-live, role=alert)
- ✅ focus-visible 링 가시성 유지 (globals.css에서 전역 `:focus-visible` 스타일)
- ✅ §19 원칙 1~5 영향 없음 (URL 진실 출처, query-param RightPanel, pending 낙관, DnD 분리 원칙 모두 그대로)
- ✅ 새 의존성 추가 없음 (폰트/아이콘 패키지 생략, 시스템 폰트 + 이모지 유지)

### 토큰 설계 요약
- Base vars는 `:root`에 raw 값(`oklch`/`#hex`)으로 정의, `[data-theme="dark"]`가 오버라이드
- Tailwind 4의 `@theme inline`이 base vars를 util class로 노출: `--color-bg`, `--color-surface-1`, `--color-accent`, `--color-fg-muted` 등
- 결과: `bg-bg` / `bg-surface-1` / `text-fg-muted` / `text-accent` 같은 utility가 `var(--bg)`를 참조 → 다크 모드 전환 시 className 변경 0건

### DoD
- typecheck: 통과
- lint: 통과
- test: 30/30 통과 (기존 테스트 regressions 없음 — className 전용 변경이라 snapshot/DOM 쿼리 영향 없음)
- 브라우저 시각 검증: 대기 (사용자 확인 권장)

### 다음 세션 컨텍스트
**M5 (업로드) 구현 재개**
- 승인된 spec: `docs/superpowers/specs/2026-04-25-m5-upload-design.md`
- 다음 단계: writing-plans skill → implementation plan 작성 후 구현 진입
- 새 UI 요소(UploadButton, UploadToasts, ConflictDialog)는 이번 디자인 토큰 체계 위에 구축

**다크 모드 활성화**
- 현재 `[data-theme="dark"]` 셀렉터로 정의됨. 토글 UI는 M9(설정)로 보류
- 테스트: DevTools에서 `<html data-theme="dark">` 수동 설정 시 전환 확인 가능

### 블로커
- 없음

---

## 2026-04-25 — M6 완료 (RightPanel + useOpenFile + ?file= 자동 제거) ✅ 브라우저 검증 통과

### 완료
- [M6] `hooks/useOpenFile.ts` — §17.5 설계 그대로. `?file=` query param 진실 출처. open/close/fileId 반환, replace + scroll:false, 다른 param 보존
- [M6] `hooks/useFileDetail.ts` — `qk.fileDetail(id)` 캐시 키, enabled:Boolean(id), staleTime 30s
- [M6] `api.getFileDetail(id)` MOCK — 404 throw 포함
- [M6] `components/files/RightPanel.tsx` — role=complementary, 이름/유형/크기/수정일/수정자 dl, 로딩 스켈레톤, 에러 role=alert, 닫기 버튼, document keydown Esc 리스너 (fileId 있을 때만 등록)
- [M6] `ClientFilesPage` 2-pane 레이아웃: 좌측 Breadcrumb+BulkActionBar+FileTable, 우측 RightPanel (fileId 없으면 null 반환해 공간 미차지)
- [M6] `FileTable.handleOpen` 리팩터 — 인라인 URL 조작 제거, `useOpenFile().open()` 사용. 분기 확정: folder → router.push, file → openFile(id)
- [M6] `hooks/useCloseFileOnFolderChange.ts` — 폴더 변경 시 `?file=` 자동 제거. prevRef로 초기 마운트는 건너뜀 (딥링크 보존). M3 "folderId 변경 시 focus/selection reset"과 대칭
- [M6] test/setup.ts에 afterEach cleanup 추가 (testing-library v16 + globals:false 조합에서 자동 cleanup 미작동 → 문서 레벨 리스너/DOM 누적 버그 방지)

### 계약 파일 추가/수정
- frontend/src/hooks/useOpenFile.ts                          신규 (docs/01 §17.5)
- frontend/src/hooks/useFileDetail.ts                        신규 (docs/01 §6.1 키 사용)
- frontend/src/hooks/useCloseFileOnFolderChange.ts           신규 (M3 대칭 정책)
- frontend/src/hooks/useOpenFile.test.ts                     신규 (5 tests)
- frontend/src/hooks/useCloseFileOnFolderChange.test.ts      신규 (5 tests)
- frontend/src/components/files/RightPanel.tsx               신규 (docs/01 §11, §12.1)
- frontend/src/components/files/RightPanel.test.tsx          신규 (5 tests)
- frontend/src/lib/api.ts                                    수정 (getFileDetail 추가)
- frontend/src/components/files/FileTable.tsx                수정 (useOpenFile로 refactor)
- frontend/src/app/(explorer)/files/[...parts]/ClientFilesPage.tsx 수정 (RightPanel 통합, 2-pane, useCloseFileOnFolderChange 호출)
- frontend/src/test/setup.ts                                 수정 (cleanup 추가)

### 미결 결정 사항 (이번 세션 확정)
1. **폴더 이동 시 `?file=` 자동 제거: YES** — 파일은 특정 폴더 컨텍스트이므로 폴더가 바뀌면 패널은 의미 없음. M3 focus/selection reset과 대칭. 딥링크는 prevRef=null 조건으로 보존
2. **단일 클릭 = 패널 열기: 유지 (현재 Enter/더블클릭만)** — 단일 클릭은 M4에서 "선택"으로 합의됨. Windows/Mac 파일 탐색기 표준 UX와 일치. 빠른 미리보기는 향후 M10 별도 설계

### 원칙 준수 체크
- ✅ §19 원칙 2 (RightPanel은 query param, parallel route 아님) — `?file=` 유지
- ✅ §19 원칙 1 (URL이 "어디"를 소유) — 파일 선택 상태도 URL query에 존재
- ✅ Esc 정책 (§12.1) — RightPanel 전역 리스너가 닫기 담당. FileTable grid의 Esc(선택 해제)는 독립. 둘 다 누르면 각자 동작 (상호 방해 없음)

### DoD
- typecheck: 통과
- lint: 통과
- test: 30/30 통과 (기존 15 + useOpenFile 5 + RightPanel 5 + useCloseFileOnFolderChange 5 = M6 15개 신규)
- 브라우저 수동 검증: 통과 (A~F 섹션 15 시나리오)

### 회고 — testing-library v16 + vitest globals:false cleanup 이슈
- 증상: RightPanel 테스트에서 "Found multiple elements with role button and name 패널 닫기"
- 원인: vitest.config의 `globals: false` → `@testing-library/react`의 자동 afterEach cleanup이 비활성. 테스트 간 DOM이 `document.body`에 누적되어 이전 테스트의 aside가 남아 있었음. 동시에 RightPanel의 `document.addEventListener('keydown')` 리스너도 누적됨
- 해결: `test/setup.ts`에서 `afterEach(cleanup)` 수동 등록. 앞으로 컴포넌트 테스트 추가 시 자동 적용됨
- 교훈: `globals: false`를 선호한다면 cleanup/matchers는 setup에 명시적으로 넣어야 함

### 회고 — 폴더 변경 시 ?file= 자동 제거 설계
- 자연 네비게이션(FolderTree/Breadcrumb Link 클릭, handleOpen router.push)은 이미 ?file=을 자동 탈락시킴. 따라서 `useCloseFileOnFolderChange`는 엣지 케이스(back/forward, 프로그래밍 navigation, 딥링크 뒤 이동)를 위한 안전망 역할
- 딥링크(`/files/foo?file=x` 초기 마운트) 보존을 위해 `prevRef.current === null`일 때는 건너뜀. 이후 이동부터 동작
- 훅 분리 이유: 단일 callsite이지만 ref+초기 스킵 로직이 비자명 → 단위 테스트로 회귀 방지 (CLAUDE.md의 "premature abstraction 금지" 원칙에 근접하나 테스트 가치로 상쇄)

### 다음 세션 컨텍스트
**M5 (백엔드 API 연결)**
- MOCK(api.ts) 전체 → 실제 fetch로 교체. 계약은 docs/02 §7
- `getFileDetail`은 현재 `FileItem` 그대로 반환. 백엔드 계약에서 권한·버전·공유자 등 포함하면 `FileDetail` 타입 분리 필요
- 에러 코드 (docs/02 §8) 매핑 후 RightPanel 에러 상태 메시지 세분화

**M7 (권한)**
- RightPanel에 권한 기반 액션 버튼 (다운로드/공유/이름변경) 추가 자리 있음
- usePermission 실제 훅 교체 후 확장

**M10 (고급 키보드)**
- 단일 클릭 = 빠른 미리보기 UX 재검토 지점 (이번 세션에서 유지 결정)
- Space로 패널 토글 등 탐색기 스타일 옵션 검토

**후속 개선 (우선순위 낮음)**
- ClientFilesPage의 canonical redirect가 현재 pathname만 비교, `router.replace(canonical)`에서 query를 전부 탈락시킴. `?file=` 및 `?sort=` 등이 의도치 않게 날아갈 가능성. 재현되면 canonical redirect에 query 보존 로직 추가

### 블로커
- 없음

---

## 2026-04-25 — M4 완료 (선택 모델 + BulkActionBar) ✅ 브라우저 검증 통과

### 완료
- [M4] selection store (stores/selection.ts) + Vitest 단위 테스트 12개
  - §5.1 스펙 + markPending이 selection에서 자동 제거 (상호배제)
  - selectRange 앵커 폴백 3케이스 (null / pending / 폴더 외)
- [M4] usePermission 스텁 훅 (§14.2 시그니처, M7 교체 예정)
- [M4] useDeleteBulk 훅 + 3케이스 단위 테스트 (성공 / 실패+같은폴더 / 실패+다른폴더)
- [M4] BulkActionBar (role=toolbar, aria-live=polite, count===0 숨김)
- [M4] FileRow: aria-selected 실제 연결, pending 시 opacity+스피너+aria-disabled, onClick(item, MouseEvent) 시그니처
- [M4] FileTable: aria-multiselectable 복원, 키보드 Space/Ctrl+A/Esc clear, ArrowUp/Down pending 스킵, markPending focus 보정 useEffect, 폴더 변경 시 clear
- [M4] Vitest + jsdom + @testing-library/react 테스트 인프라 세팅
- [M4] api.deleteBulk mock 추가
- [M4] eslint.config.mjs: .next/build ignore 추가 (lint 오경보 수정)
- [M4] BulkActionBar selector 무한 렌더 버그 수정 (a589032)
- [M4] next.config.ts allowedDevOrigins 추가 (127.0.0.1 dev 접근 허용)

### 계약 파일 추가/수정
- frontend/src/stores/selection.ts               신규 (docs/01 §5.1)
- frontend/src/hooks/usePermission.ts            신규 (docs/01 §14.2 스텁)
- frontend/src/hooks/useDeleteBulk.ts            신규 (설계안 §2.5)
- frontend/src/components/files/BulkActionBar.tsx 신규 (docs/01 §8.2)
- frontend/src/components/files/FileRow.tsx      수정 (Props 시그니처 변경)
- frontend/src/components/files/FileTable.tsx    수정 (selection 연결)
- frontend/src/lib/api.ts                        수정 (deleteBulk 추가)
- frontend/src/app/(explorer)/files/[...parts]/ClientFilesPage.tsx  수정 (BulkActionBar 렌더)

### 설계 문서 업데이트
- docs/01 §5.1 하단에 구현 노트 (상호배제, 앵커 폴백) 추가
- docs/superpowers/specs/2026-04-25-m4-selection-bulkactionbar-design.md 신규 작성
- docs/superpowers/plans/2026-04-25-m4-selection-bulkactionbar.md 신규 작성

### DoD
- typecheck: 통과
- lint: 통과
- test: 15/15 통과 (selection 12 + useDeleteBulk 3)
- **브라우저 수동 검증: 12/12 시나리오 통과** (클릭/Ctrl+click/Shift+click/Space/Ctrl+A/Esc/폴더이동 clear/휴지통 pending→삭제→invalidate 포함)

### 버그 재발 방지 — Zustand v5 selector 무한 렌더
- **증상**: "Maximum update depth exceeded" (BulkActionBar 마운트 직후)
- **루트 원인**: `useSelectionStore((s) => Array.from(s.ids))` — selector가 매 snapshot 호출마다 **새 배열 참조** 반환
- **메커니즘**: Zustand v5는 `useSyncExternalStore` 기반. React가 매 render마다 이전/신규 snapshot 참조 비교 → 항상 "변경됨" 판정 → 무한 재렌더
- **올바른 패턴**: selector는 store에 **이미 존재하는 안정 참조**(Set/Map/객체 자체)만 반환. 파생 변환(Array.from, filter, map, spread)은 selector 밖 render 본문에서 수행
  ```tsx
  // ❌ 금지
  const ids = useSelectionStore((s) => Array.from(s.ids))
  // ✅ 권장
  const idsSet = useSelectionStore((s) => s.ids)
  const ids = Array.from(idsSet)
  ```
- **예외**: 변환이 꼭 필요하면 `useShallow` 또는 `zustand/shallow` equality 함수 명시. 하지만 대부분은 위 패턴이 충분하고 단순함

### 다음 세션 컨텍스트
**M5 (백엔드 API 연결)**
- MOCK(api.ts)을 실제 fetch로 교체. 계약은 docs/02 §7
- useDeleteBulk 실패 경로: 현재 console.warn → 토스트 라이브러리 통합 (sonner 후보)
- 에러 코드 (docs/02 §8) 매핑: 409 CONFLICT / 403 FORBIDDEN / 423 LOCKED 등 UI 메시지

**M6 (DnD 이동)**
- BulkActionBar 이동 버튼: 현재 스텁 → dnd-kit + 이동 다이얼로그
- 업로드 DnD(window native)와 컨텍스트 분리 원칙(§19) 유지
- pendingIds 재사용 (이동 중인 row는 pending UI)

**M7 (권한)**
- usePermission 스텁 → `useQuery + api.getEffectivePermissions(nodeId)` 로 교체 (docs/01 §14.2)
- BulkActionBar 버튼 표시 조건(can.download/move/delete) 실제 동작
- docs/03 §3 권한 매트릭스 확정 선행 필요 (현재 스켈레톤)

**M10 (고급 키보드)**
- Shift+↑↓ 범위 선택, Ctrl+↑↓ 포커스-only 이동, F2 rename, Delete 삭제, `/` 검색 포커스
- 설계 §12 참고, M4에서 anchor/pending 인프라 이미 확보됨

### 블로커
- 없음 (M5 진입 가능. docs/03 §3 권한 매트릭스는 M7 시작 전 확정 필요)

---

## 2026-04-25 — M3 브라우저 검증 완료

### 검증 결과
- /files/root — 5개 항목 정상 렌더링 (📁영업팀, 📁인사팀, 📄제안서, 📊예산안, 📝회의록)
- /files/folder_contracts — 계약서 2개 정상
- /files/folder_hr — empty state ("이 폴더는 비어 있습니다") 정상
- 키보드 ↑↓ Enter Esc 모두 정상 동작
- hydration 에러는 브라우저 확장(testim) 주입 문제, 코드 무관

### 다음 세션 컨텍스트 (M4: 선택 모델 + BulkActionBar)
- FileRow aria-selected 항상 false → M4에서 useSelectionStore 연결
- FileRow onClick → M4에서 selectOnly/toggle/range 로직 연결
- aria-multiselectable={true} → M4에서 grid에 다시 추가
- 컬럼 헤더 정렬 토글 UI 미구현 (sort/dir은 URL param으로 동작)
- F2/Delete/Ctrl+A/검색(/) 키보드는 M10으로 미룸
- 3000~4000 포트 대역은 다른 앱이 점유 중 → dev 서버는 4100+ 사용 권장

---

## 2026-04-24 — M3 구현

### 완료
- [M3] FileTable — TanStack Virtual 가상화 (overscan: 10, 10k+ 행 대응)
- [M3] 4가지 상태 컴포넌트: Skeleton, Empty, Error(onRetry), Forbidden(403)
- [M3] FileRow — 아이콘, 크기/날짜 포맷, roving tabIndex
- [M3] WAI-ARIA grid 패턴: role="grid/row/gridcell/columnheader", aria-rowcount/rowindex/selected
- [M3] 기본 키보드: ↑↓ 포커스 이동 + DOM focus 동기화, Enter 열기, Esc 해제
- [M3] useFilesInFolder 훅 (qk.filesInFolder 캐시 키)
- [M3] useSortParams 훅 (URL searchParams에서 sort/dir 읽기)
- [M3] ClientFilesPage에 FileTable 통합

### 계약 파일 추가/수정
- src/types/file.ts            (FileItem, SortKey 타입)
- src/lib/queryKeys.ts 수정     (files, filesInFolder, fileDetail 키 추가)
- src/lib/api.ts 수정           (MOCK_FILES + getFilesInFolder 추가)

### 코드 리뷰 후 수정
- role="grid"를 외부 컨테이너로 이동 (header row가 grid 안에 포함되도록)
- aria-multiselectable 제거 (M4 선택 기능 전까지 premature)
- folderId 변경 시 focusedIndex 리셋 useEffect 추가
- 포커스 시 DOM .focus() 호출 추가 (스크린 리더 대응)
- role="gridcell", role="columnheader" 추가

### 다음 세션 컨텍스트 (M4: 선택 모델 + BulkActionBar)
- FileRow의 aria-selected는 현재 항상 false. M4에서 useSelectionStore 연결 필요
- FileRow onClick prop은 현재 focusedIndex만 설정. M4에서 selectOnly/toggle/range 연결
- aria-multiselectable={true}는 M4에서 다시 추가
- 컬럼 헤더 정렬 토글 UI 미구현 (sort/dir은 URL param으로 동작)
- api.ts mock 데이터: folder_sales/folder_hr가 MOCK_TREE와 MOCK_FILES에 이중 존재 — 실제 API 계약 확정 시 정리 필요
- F2/Delete/Ctrl+A/검색(/) 키보드는 M10으로 미룸

### 블로커
- 없음

### 설계 문서 업데이트 필요
- 없음 (docs/01 §4, §6, §11, §12 스펙 그대로 반영)

---

## 2026-04-24 — M1 완료

### 완료
- [M1] folderId 중심 catch-all 라우팅 (`/files/[...parts]`)
- [M1] FolderTree / Breadcrumb URL 동기화
- [M1] canonical redirect (decodeURI 비교, 한글 URL 대응)
- [M1] 프로젝트 기본 셋업 (Providers, 훅, 스토어)
- [M1] `/files` → `/files/root` 리다이렉트
- [M1] Explorer 레이아웃 (사이드바 + 메인)
- [M1] loading / error / not-found 상태 페이지

### 계약 파일 추가
- src/lib/normalize.ts      (docs/02 §3)
- src/lib/queryKeys.ts       (docs/01 §6.1)
- src/lib/folderPath.ts      (docs/01 §17.3)
- src/lib/api.ts             (MOCK — M5에서 실제 API로 교체)

### 다음 세션 컨텍스트 (M2: FolderTree 심화 + TrashLink + QuickAccess 또는 M3: FileTable)
- api.ts는 현재 mock. 백엔드 나오면 실제 fetch로 교체. 계약은 docs/02 §7.3
- 서버 컴포넌트 전환은 M3에서 (notFound/redirect 조합)
- canonical redirect는 클라이언트에서 useEffect. 깜빡임 있으면 M3에서 서버 redirect로
- Next.js 16에서 Windows pnpm EPERM 이슈 발생 → 15.3.2로 다운그레이드, npm 사용
- next-env.d.ts의 `.next/types/routes.d.ts` import는 Next.js 16 전용 → 15에서는 무시됨

### 블로커
- 없음

### 설계 문서 업데이트 필요
- 없음 (코드 템플릿 그대로 반영)

---

## (세션 기록이 여기에 쌓입니다)
