# M14 Visual Identity — 구현 계획

> Spec: `docs/superpowers/specs/2026-04-25-m14-visual-identity-design.md`
> 자율 모드 진행. 단계별로 typecheck/lint/test 통과 확인.

---

## Phase 1 — 의존성 추가

### Task 1.1: `lucide-react` 설치
- `frontend/package.json` 수정: `dependencies`에 `"lucide-react": "^0.468.0"` 추가
- `npm install` (Windows pnpm EPERM 이슈로 npm 사용 — M1 결정)
- 검증: `pnpm typecheck` 또는 `npm run typecheck`

---

## Phase 2 — File Icon 매핑

### Task 2.1: `frontend/src/lib/fileIcons.tsx`
- export `getFileIcon(item: FileItem): LucideIcon`
- export `getFileIconColor(item: FileItem): string` (Tailwind class)
- 매핑: folder/pdf/image/video/spreadsheet/code/archive/word/text/default

### Task 2.2: `frontend/src/lib/fileIcons.test.ts`
- 8 케이스 단위 테스트
  - folder → Folder
  - mime image/png → Image
  - mime video/mp4 → Video
  - mime application/pdf → FileText (color text-danger)
  - mime application/vnd.ms-excel → FileSpreadsheet
  - mime text/javascript → FileCode
  - mime application/zip → FileArchive
  - mime ?? → File (default)

---

## Phase 3 — FileRow 시각 갱신

### Task 3.1: `frontend/src/components/files/FileRow.tsx`
- `fileIcon` string 함수 삭제
- import `getFileIcon`, `getFileIconColor`
- 아이콘 렌더: `<Icon size={16} className={...} aria-hidden />` (span 래퍼 그대로 — gridcell 유지)
- `h-10` → `h-9`
- 폰트/패딩 그대로

### Task 3.2: `frontend/src/components/files/FileTable.tsx`
- `ROW_HEIGHT = 40` → `36`
- 그 외 변경 없음

---

## Phase 4 — TopBar

### Task 4.1: `frontend/src/components/layout/TopBar.tsx`
- 컴포넌트 구조:
  - 좌: Menu icon-btn (no-op, future 사이드바 토글)
  - 중: SearchBox (input + ⌘K hint + clear button)
  - 우: ThemeToggle + Help icon-btn + Avatar (이니셜)
- 검색 입력 상태는 컴포넌트 내부 `useState` (M11에서 URL param으로 승격 예정)
- `useEffect`로 `app:focus-search` 이벤트 → input.focus()
- 테마 토글: `document.documentElement.dataset.theme` 직접 조작

### Task 4.2: `frontend/src/components/layout/TopBar.test.tsx`
- 5 테스트:
  1. 렌더링 — search input placeholder 검증
  2. `app:focus-search` 이벤트 디스패치 시 input focus
  3. 검색어 입력 → clear 버튼 표시 → 클릭 시 비움
  4. 테마 토글 클릭 → `data-theme` light ↔ dark 전환
  5. 검색어 비어있을 때 clear 버튼 미표시

### Task 4.3: `frontend/src/app/(explorer)/layout.tsx`
- grid-rows-[48px_1fr] 재구성
- TopBar 마운트 (sidebar+main 위)
- 사이드바 brand 영역(M13)은 그대로 유지

---

## Phase 5 — StatusBar

### Task 5.1: `frontend/src/components/layout/StatusBar.tsx`
- props: `folderId: string`
- `useFilesInFolder(folderId, sort, dir)` 호출 (cache hit)
- `useSelectionStore` `ids` 구독
- 표시: `{itemCount}개 항목 · {selectedCount}개 선택됨` + 선택 size sum (양수일 때만)

### Task 5.2: `frontend/src/components/layout/StatusBar.test.tsx`
- 3 테스트:
  1. 항목 개수 표시 (선택 0)
  2. 선택 1+ 시 "N개 선택됨" 표시
  3. 선택 size sum 표시 (folder size=0 제외)

### Task 5.3: `ClientFilesPage.tsx` 마운트
- 메인 컬럼 끝(FileTable 아래)에 `<StatusBar folderId={folderId} />` 추가

---

## Phase 6 — 검증 + 마감

### Task 6.1: 검증
- `npm run typecheck` PASS
- `npm run lint` PASS
- `npm run test` PASS (기존 136 + 신규 ~14 = 150)

### Task 6.2: docs/01 §18 M14 완료 마커
- `| 14 | **Visual Identity** ... |` 행 끝에 (완료 2026-04-25) 추가

### Task 6.3: docs/progress.md 세션 기록
- 표준 양식 (완료/핵심 결정/다음 세션 컨텍스트/블로커/마일스톤 상태)

---

## 변경 파일 요약

### 신규
- `frontend/src/lib/fileIcons.tsx`
- `frontend/src/lib/fileIcons.test.ts`
- `frontend/src/components/layout/TopBar.tsx`
- `frontend/src/components/layout/TopBar.test.tsx`
- `frontend/src/components/layout/StatusBar.tsx`
- `frontend/src/components/layout/StatusBar.test.tsx`

### 수정
- `frontend/package.json` (lucide-react)
- `frontend/src/components/files/FileRow.tsx` (icon + h-9)
- `frontend/src/components/files/FileTable.tsx` (ROW_HEIGHT 36)
- `frontend/src/app/(explorer)/layout.tsx` (TopBar 마운트, grid)
- `frontend/src/app/(explorer)/files/[...parts]/ClientFilesPage.tsx` (StatusBar 마운트)
- `docs/01-frontend-design.md` (§18 마커)
- `docs/progress.md` (세션 기록)

---

## 리스크 & 회피

- **Windows pnpm EPERM**: M1 결정대로 npm 사용
- **lucide-react React 19 호환**: 0.468+ peer deps `react: ^16.5.1 || ^17 || ^18 || ^19` — 안전
- **테스트 ROW_HEIGHT 영향**: 기존 테스트는 ROW_HEIGHT를 직접 검증하지 않음 (grep 결과 0건)
- **CSS classname 변경**: 토큰 그대로, h-10 → h-9만 변경. 시각만 영향, snapshot 테스트 없음

---

## 비범위 (M15/M16/v1.x)

- SortChip 정렬 드롭다운 (M15)
- ViewSwitch List/Grid 토글 (M15)
- StorageBar 사이드바 하단 (M15)
- RightPanel 탭 (M15)
- Grid View (M16)
- 테마 영속화 (v1.x)
- 6열 확장 (체크박스 + 액션) (v1.x)
- TopBar 검색 실제 동작 (M11)
