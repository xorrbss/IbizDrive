---
Last Updated: 2026-05-29
---

# folder-upload — Tasks

## Phase 상태

- [x] **P0** bootstrap (plan/context/tasks)
- [x] **P1** 설계 문서 §9.6
- [x] **P2** `lib/folderUpload.ts` 추출 유틸
- [ ] **P3** `useNativeFileDrop.ts` entry 캡처
- [ ] **P4** `useFolderUpload.ts` materialize 오케스트레이터
- [ ] **P5** 진입점 와이어링 (FileTable / SidebarNewButton)
- [ ] **P6** 통합 검증 + 문서 동기화

---

## P1 — 설계 문서 §9.6  ✅

- [x] `docs/01-frontend-design.md`에 `### 9.6 폴더 업로드` 추가 (§9.5 다음, ADR 6항목 포함)

**작업 전 필독**
- `dev/active/folder-upload/folder-upload-plan.md` §"데이터 흐름", §"중요한 의사결정"
- CLAUDE.md §5.3 (문서에 없는 영역 → 설계 먼저)

**원본 코드 참조**
- `docs/01-frontend-design.md:755` (§9 업로드 시작), `:861` (§9.5 다운로드 — 그 앞에 §9.6 삽입)

**구현 대상**
- §9.6: 추출(드롭 webkitGetAsEntry 재귀 / 입력 webkitRelativePath) → materialize(getFolderChildren 병합 / createFolder) → 그룹 enqueue 흐름.
- **ADR 노트**: ① 백엔드 무변경 / 프론트 오케스트레이션 근거, ② 폴더 이름 충돌 = 병합 정책, ③ 빈 폴더 생성, ④ 미지원 폴백.

**검증 참조**
- 문서 게이트(코드 없음). 핵심 원칙 표(CLAUDE.md §3)와 충돌 없는지 자가 점검.

**문서 반영**
- 본 섹션 자체가 문서. `docs/progress.md`는 P6에서 일괄.

---

## P2 — `lib/folderUpload.ts` 추출 유틸 (RED→GREEN)

- [ ] `frontend/src/lib/folderUpload.test.ts` RED
- [ ] `frontend/src/lib/folderUpload.ts` GREEN

**작업 전 필독**
- plan §"데이터 흐름", context §"중요한 의사결정" 4·6·7
- `frontend/src/lib/normalize.ts` (`normalizeFileName`)

**원본 코드 참조**
- `frontend/src/lib/api.test.ts` 류 — 테스트 스타일 답습 (vitest)
- 웹 표준: `FileSystemDirectoryEntry.createReader().readEntries()` (≤100 배치), `FileSystemFileEntry.file()`

**구현 대상**
- `export type UploadEntry = { file: File; pathSegments: string[] }` (pathSegments = 파일 제외 디렉토리 체인)
- `export type FolderUploadPlan = { entries: UploadEntry[]; dirPaths: string[][] }` (dirPaths = 빈 폴더 포함 전체 디렉토리 경로)
- `extractInputFiles(files: File[]): FolderUploadPlan` — `webkitRelativePath` split, 마지막=파일명.
- `extractDropEntries(entries: FileSystemEntry[]): Promise<FolderUploadPlan>` — 재귀. `isDirectory`면 readEntries while-loop, `isFile`면 file(). 디렉토리 경로 누적.
- 빈 `pathSegments` (= 루트 직속 파일) 정상 처리.

**검증 참조**
- 단위: webkitRelativePath 파싱, 중첩 재귀(mock entry), readEntries 100+ 배치, 빈 폴더 경로 수집, 루트 직속 파일.
- `pnpm typecheck` + `pnpm test folderUpload`

**문서 반영**
- 없음 (P1 §9.6이 계약).

---

## P3 — `useNativeFileDrop.ts` entry 동기 캡처

- [ ] `frontend/src/hooks/useNativeFileDrop.test.ts` 갱신 RED
- [ ] `frontend/src/hooks/useNativeFileDrop.ts` GREEN

**작업 전 필독**
- context §"중요한 의사결정" 6 (DataTransferItemList 수명)

**원본 코드 참조**
- `frontend/src/hooks/useNativeFileDrop.ts:41` (`onDropEv`)
- `frontend/src/hooks/useNativeFileDrop.test.ts` (기존 케이스)

**구현 대상**
- 콜백 시그니처 변경: `onDrop(payload: { files: File[]; entries: FileSystemEntry[] | null })`.
- `onDropEv`에서 `e.dataTransfer.items`가 있으면 `Array.from(items).map(it => it.webkitGetAsEntry()).filter(Boolean)`를 **동기** 캡처. 없으면 `entries: null`.
- `isFileDrag` 등 나머지 로직 유지.

**검증 참조**
- 단위: files-only(entries null 폴백), items 있을 때 entries 캡처, dnd-kit 이동(types에 Files 없음) 무시 유지.
- `pnpm typecheck` + `pnpm test useNativeFileDrop`

**문서 반영**
- 없음.

---

## P4 — `useFolderUpload.ts` materialize 오케스트레이터 (RED→GREEN)

- [ ] `frontend/src/hooks/useFolderUpload.test.ts` RED
- [ ] `frontend/src/hooks/useFolderUpload.ts` GREEN

**작업 전 필독**
- plan §"데이터 흐름", context §"중요한 의사결정" 1·2·3·5
- `frontend/src/lib/queryKeys.ts:304` (`afterFolderCreated`)

**원본 코드 참조**
- `frontend/src/lib/api.ts:278` (`getFolderChildren`), `:589` (`createFolder`)
- `frontend/src/hooks/useUpload.ts:31` (`enqueue`), `frontend/src/lib/normalize.ts` (`normalizeFileName`)
- `frontend/src/hooks/useCreateFolder.ts` (있으면 mutation/에러 패턴 답습)

**구현 대상**
- `useFolderUpload()` → `{ uploadFolder(plan: FolderUploadPlan, baseFolderId: string): Promise<void> }`.
- materialize: `dirPaths`를 깊이순 정렬 + dedupe → parent별 `getFolderChildren` 1회 캐시 → `normalizeFileName` 비교로 병합/생성 → `Map<pathKey, folderId>`.
- 409(race) → `getFolderChildren` 재조회로 id 해석. 403/400 → 에러 surface + 해당 서브트리 스킵(나머지 진행).
- 파일을 resolved folderId별 그룹핑 → `enqueue(files, folderId)`.
- 생성 발생 시 `invalidations.afterFolderCreated(qc, {parentId})`.

**검증 참조**
- 단위(api mock): 신규 트리 생성, 기존 폴더 병합(중복 생성 X), 빈 폴더 생성, 409 재조회, 생성 실패 서브트리 스킵, 그룹 enqueue 호출 인자.
- `pnpm typecheck` + `pnpm test useFolderUpload`

**문서 반영**
- 없음.

---

## P5 — 진입점 와이어링

- [ ] `frontend/src/components/files/FileTable.tsx` 드롭 라우팅
- [ ] `frontend/src/components/sidebar/SidebarNewButton.tsx` "폴더 업로드" 항목
- [ ] 관련 컴포넌트 테스트 갱신/추가

**작업 전 필독**
- P3 콜백 페이로드, P4 `uploadFolder` 시그니처

**원본 코드 참조**
- `frontend/src/components/files/FileTable.tsx:72-80` (handleNativeDrop / useNativeFileDrop)
- `frontend/src/components/sidebar/SidebarNewButton.tsx:54-116` (메뉴 + hidden input)

**구현 대상**
- `FileTable`: drop payload에 디렉토리 entry가 있으면 `extractDropEntries` → `uploadFolder`, 아니면 기존 flat `enqueue`. (entries null이면 flat 폴백)
- `SidebarNewButton`: 메뉴에 "폴더 업로드" 항목 + 별도 hidden `<input webkitdirectory>` → `extractInputFiles` → `uploadFolder`. `disabled`(folderId 없음) 규칙 동일 적용.

**검증 참조**
- `pnpm typecheck && pnpm lint`
- 컴포넌트 테스트: 파일 드롭 회귀 없음, 폴더 메뉴 항목 렌더/disabled.

**문서 반영**
- 없음 (§9.6에 진입점 기술됨).

---

## P6 — 통합 검증 + 문서 동기화

- [ ] `pnpm typecheck && pnpm lint && pnpm test` 전체 통과
- [ ] `verify` 스킬로 실제 폴더 드롭/버튼 동작 관찰 (중첩 생성·병합·파일 충돌 다이얼로그)
- [ ] `docs/progress.md` 세션 기록
- [ ] plan Phase 지도 / context SESSION PROGRESS 최종 갱신
- [ ] PR open

**작업 전 필독**
- plan §"Acceptance Criteria" 전 항목 점검

**검증 참조**
- 전체 게이트 + acceptance 7항목 수동 확인.

**문서 반영**
- `docs/progress.md`, plan/context 최종 상태, 본 트랙 `dev/active` → 완료 시 `dev/completed` 이동(P6 종료 후).
