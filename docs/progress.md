# 진행 상황 (Progress Log)

> 각 세션이 완료될 때마다 **최상단에 추가**합니다. 기존 내용은 보존.
> 양식: `CLAUDE.md §7` 또는 각 BRIEF의 회고 섹션 참조.

---

## 2026-04-25 — M4 완료 (선택 모델 + BulkActionBar)

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
- **브라우저 수동 검증: 대기중** (다음 세션에서 M3 방식대로 진행 권장)

### 다음 세션 컨텍스트 (M5 백엔드 연결 또는 M6 DnD)
- **우선 M4 브라우저 수동 검증 필요** — 플랜 Task 8 Step 3의 12개 시나리오 (클릭/Ctrl+click/Shift+click/Space/Ctrl+A/Esc/휴지통 등)
- useDeleteBulk의 실패 경로는 mock이라 현재 console.warn까지만. M5에서 토스트 라이브러리 통합 필요
- BulkActionBar 다운로드/이동은 여전히 스텁 (다운로드 별도, 이동은 M6 DnD)
- usePermission은 스텁 — M7 권한에서 §14.2대로 useQuery 연결
- Shift+↑↓ / Ctrl+↑↓ / F2 / Delete / `/` 검색 포커스는 M10으로 연기 유지

### 블로커
- 없음

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
