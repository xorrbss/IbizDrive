# 진행 상황 (Progress Log)

> 각 세션이 완료될 때마다 **최상단에 추가**합니다. 기존 내용은 보존.
> 양식: `CLAUDE.md §7` 또는 각 BRIEF의 회고 섹션 참조.

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
