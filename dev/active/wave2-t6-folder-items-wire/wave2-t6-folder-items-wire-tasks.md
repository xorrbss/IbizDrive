---
Last Updated: 2026-05-07 (P3 + P4 완료)
---

# Tasks — wave2-t6-folder-items-wire (Wave 2 — T6)

## Phase 진행 표

- [x] P1 — Backend `GET /api/folders/{id}/items` (2026-05-07)
- [x] P2 — Backend `GET /api/files/{id}` (2026-05-07)
- [x] P3 — Frontend api wrapper + queryKeys + types + MOCK 제거 (2026-05-07, commit `62c08a4`)
- [x] P4 — Frontend hooks + invalidations + pending 정책 (2026-05-07)
- [ ] P5 — Test 전환 + 회귀 fix + 풀 게이트
- [ ] P6 — Smoke + docs sync + closure + PR

---

## P1 — Backend `GET /api/folders/{id}/items`

### 작업 전 필독
- `wave2-t6-folder-items-wire-plan.md` — 목표 상태 §Backend 신규 / 정렬 정책.
- `docs/superpowers/specs/2026-05-07-wave2-t6-folder-items-mutations-wire-design.md` §3.1.

### 원본 코드 참조
- `backend/src/main/java/com/ibizdrive/folder/FolderController.java` line 90~107 — Phase A `tree()`/`detail(id)` 패턴.
- `backend/src/main/java/com/ibizdrive/folder/FolderQueryService.java` — `loadTree()`/`loadDetail(id)` 패턴.
- `backend/src/main/java/com/ibizdrive/folder/dto/FolderDetailResponse.java` — record DTO 답습.
- `backend/src/main/java/com/ibizdrive/folder/FolderRepository.java` — 기존 query 메서드 확인 (children fetch가 이미 있는가).
- `backend/src/main/java/com/ibizdrive/file/FileRepository.java` — 동일.
- `backend/src/test/java/com/ibizdrive/folder/FolderQueryServiceTest.java` — Mockito 6 case 답습 패턴.

### 구현 대상
- [x] `FolderRepository.findByParentIdAndDeletedAtIsNull` — 이미 존재(라인 36), 재사용.
- [x] `FileRepository.findByFolderIdAndDeletedAtIsNull` — 이미 존재(라인 38), 재사용.
- [x] DTO: `folder/dto/FolderItemDto`(record, 8 fields), `folder/dto/FolderItemsResponse`(record `(List<FolderItemDto> items)`).
- [x] enum: `folder/SortKey`(`NAME`, `UPDATED_AT`, `SIZE`), `folder/SortDir`(`ASC`, `DESC`). `@RequestParam(defaultValue="NAME"/"ASC")` 자동 변환.
- [x] `FolderQueryService.loadItems(UUID id, SortKey sort, SortDir dir)` — parent active 확인 → subfolders/files fetch → 그룹별 정렬 → concat. SIZE 시 폴더 그룹 `name asc` fallback.
- [x] `FileTestFixtures.activeFile(...)` 신설 — 패키지 외부 테스트(folder pkg)에서 active FileItem 생성.
- [x] **TDD**: `FolderQueryServiceItemsTest` 6 case (Mockito) — 모두 GREEN.
- [x] `FolderController.items(...)` — `@PreAuthorize("hasPermission(#id, 'folder', 'READ')")` + `@RequestParam` enum default.
- [x] **Controller test** (project pattern: 직접 호출, WebMvc slice 미사용) `FolderControllerTest.items_*` 3 case:
  1. 200 default(name asc) — delegate 검증.
  2. 200 explicit(size desc) — args pass-through.
  3. 404 propagate — `FolderNotFoundException` 그대로 throw (envelope 매핑은 `GlobalExceptionHandler` 책임).
- [~] 403 / 400 — 프로젝트 controller test 정책상 SpEL/framework 인프라 책임. `IbizDrivePermissionEvaluator` 및 Spring `MethodArgumentTypeMismatch` 측에서 커버 (P1 범위 외).

### 검증 참조
- gate: `cd backend && ./gradlew test --tests "*FolderQueryServiceItemsTest" "*FolderControllerItemsTest" --no-daemon` GREEN.
- 회귀: `cd backend && ./gradlew test --no-daemon` 전체 GREEN.

### 문서 반영
- P1 phase 끝에서는 docs 미반영. P6에서 일괄.

### Commit
- [ ] `feat(folder-items-wire): GET /api/folders/{id}/items + tests (P1)`

---

## P2 — Backend `GET /api/files/{id}`

### 작업 전 필독
- `plan.md` §Backend 신규 — `GET /api/files/{id}`.
- `spec.md` §3.1 후반부.

### 원본 코드 참조
- `backend/src/main/java/com/ibizdrive/file/FileController.java` line 54~ — `@RequestMapping("/api/files")` 위에 GET 분기 추가 또는 신규 `FileQueryController` 신설.
- `backend/src/main/java/com/ibizdrive/file/FileMutationService.java` — file row select + soft-delete 가드 + DTO 매핑 패턴.
- `backend/src/main/java/com/ibizdrive/folder/FolderQueryService.java` — Phase A loadDetail 패턴 1:1 답습.

### 구현 대상
- [ ] **결정**: `FileQueryService` 신설 vs. `FileMutationService`에 `loadDetail` 추가. Phase A 동형이면 신설이 일관 — 신설 선호.
- [ ] `file/FileQueryService.loadDetail(UUID id)`:
  - `fileRepository.findById(id).filter(f -> f.getDeletedAt()==null).orElseThrow(FileNotFoundException)`.
  - `FileDto.from(file)` (이미 mutation 응답에서 사용 — 재사용).
  - return `FileDetailResponse(file)` (신규 record `(FileDto file)`).
- [ ] `FileController` (또는 `FileQueryController` 신설) `@GetMapping("/{id}")`:
  - `@PreAuthorize("hasPermission(#id, 'file', 'READ')")`.
- [ ] **TDD** `FileQueryServiceTest` 3 case (Mockito):
  1. 200 — active file → DTO 정상.
  2. 404 — soft-deleted (`deleted_at IS NOT NULL`).
  3. 404 — 부재 id.
- [ ] **MockMvc test** `FileControllerDetailTest`:
  1. 200 정상.
  2. 403 — READ 부재.
  3. 404 — soft-deleted.

### 검증 참조
- gate: `cd backend && ./gradlew test --tests "*FileQuery*" "*FileControllerDetail*" --no-daemon` GREEN.
- 회귀: 전체 `./gradlew test` GREEN.

### 문서 반영
- P6 일괄.

### Commit
- [ ] `feat(folder-items-wire): GET /api/files/{id} + tests (P2)`

---

## P3 — Frontend api wrapper + queryKeys + types + MOCK 제거

### 작업 전 필독
- `plan.md` §Frontend wire 변경.
- `spec.md` §3.2.

### 원본 코드 참조
- `frontend/src/lib/api.ts` line 21~50 — `MOCK_TREE`/`MOCK_FILES` 정의.
- `frontend/src/lib/api.ts` line 155~234 — Phase A `getFolderTree`/`getFolder` real fetch 패턴 (오류 처리 + 응답 매핑).
- `frontend/src/lib/api.ts` line 274~ — `listFileVersions` real fetch 패턴 (Error+status 던지기).
- `frontend/src/types/file.ts` — `FileItem` 통합 스키마 (변경 불필요).
- `frontend/src/lib/queryKeys.ts` — `qk` factory 위치.

### 영향 면 측정 (P3 진입 직후 1회 필수)
- [ ] `cd frontend && grep -rn 'MOCK_FILES\|MOCK_TREE\|findNode\|findParentId\|relocateInTree' src/` — 호출 site 전수 목록.
- [ ] `cd frontend && grep -rn 'getFilesInFolder\|getFileDetail\|renameFile\|renameFolder\|moveFiles\|deleteFiles\|restoreFiles\|createFolder' src/` — wrapper 함수 호출 site.
- [ ] 결과 200건 이상이면 보고 후 진행 결정. 200건 미만이면 P3 진행.

### 구현 대상
- [ ] `src/lib/queryKeys.ts`:
  - `qk.folderItems(folderId: string, sort: SortKey = 'name', dir: 'asc'|'desc' = 'asc')` 추가 — 키 형태 `['folderItems', folderId, sort, dir]`.
  - 기존 `qk.filesInFolder(...)`이 있으면 deprecate(주석) — P5에서 호출 site 전수 교체 후 제거.
- [ ] `src/lib/api.ts` 정리:
  - 삭제: `MOCK_TREE`, `MOCK_FILES`, `findNode`, `findParentId`, `relocateInTree`.
  - 신규/수정: `getFolderItems(folderId, sort='name', dir='asc')` — id==='root' 분기(tree 결과 매핑) + 그 외 fetch.
  - 수정: `getFileDetail(id)` real fetch.
  - 수정: `renameFile(id, newName)` → PATCH `/api/files/${id}` body `{name}`.
  - 수정: `renameFolder(id, newName)` → PATCH `/api/folders/${id}` body `{name}`.
  - 수정: `moveFile(id, target)` → POST `/api/files/${id}/move` body `{targetFolderId}`.
  - 수정: `moveFolder(id, target)` → POST `/api/folders/${id}/move` body `{newParentId}`.
  - 수정: `deleteFile(id)` → DELETE `/api/files/${id}` (204).
  - 수정: `deleteFolder(id)` → DELETE `/api/folders/${id}` (204).
  - 수정: `restoreFile(id)` → POST `/api/files/${id}/restore` (200 `{file}`).
  - 수정: `restoreFolder(id)` → POST `/api/folders/${id}/restore` (200 `{folder}`).
  - 수정: `createFolder(parentId, name)` → POST `/api/folders` body `{parentId, name}` (201 `{folder}`).
  - 묶음 함수: `moveFiles(items, target)`/`deleteFiles(items)`/`restoreFiles(items)`는 items 배열에서 type 분기 후 `Promise.allSettled`로 fanout. 부분 실패 시 결과 객체에 successCount/failedIds 포함하여 호출자(hook)에서 toast 표시.
  - 모든 fetch는 `credentials: 'include'`, `Accept: 'application/json'`, 비-OK 시 status+code(가능 시) Error throw — Phase A 패턴 답습.
  - body가 있는 mutation은 `headers: {'Content-Type': 'application/json', 'X-XSRF-TOKEN': ...}` — CSRF 헤더 패턴은 기존 mutation api 호출이 이미 사용 중이면 동형(`getCsrfToken()` helper 등 재사용).

### 검증 참조
- gate: `cd frontend && pnpm typecheck` clean. test는 P5 일괄 GREEN(P3 끝 기준 일부 RED 허용 — mock 의존 test 깨짐 예상).

### 문서 반영
- P6 일괄.

### Commit
- [ ] `chore(folder-items-wire): MOCK 제거 + api real wire (P3)`

---

## P4 — Frontend hooks + invalidations + 낙관/pending 정책

### 작업 전 필독
- `plan.md` §Invalidation / 낙관·pending 정책.
- `spec.md` §3.2.

### 원본 코드 참조
- `frontend/src/hooks/useFileDetail.ts` — `useQuery` + `qk.fileDetail` 패턴 (변경 불필요, api.ts 변경에 의해 자동 real).
- `frontend/src/hooks/useFilesInFolder.ts` — 현 hook → `useFolderItems`로 rename(또는 동명 유지 후 시그니처만 갱신, 호출 site 영향 측정 후 결정).
- 기존 mutation hook(있다면) — `useRenameFile`/`useMoveFiles`/`useDeleteFiles`/`useRestoreFiles`/`useCreateFolder` 등. mock 의존 mutation을 real wire + invalidate.
- `frontend/src/lib/invalidations.ts` 또는 등가 helper — 존재 여부 확인 후 신설/확장.

### 구현 대상
- [ ] `cd frontend && find src -name 'invalidations*'` — 동명 파일 확인.
- [ ] `src/lib/invalidations.ts` 5 helper:
  ```ts
  afterFileMutated(qc, fileId, parentId)
  afterFolderMutated(qc, folderId, oldParentId, newParentId?)
  afterFileCreated(qc, parentId)
  afterFolderCreated(qc, parentId)
  afterTrashChanged(qc, originalParentId)
  ```
  - sort/dir 와일드카드 매칭: `qc.invalidateQueries({ predicate: q => q.queryKey[0]==='folderItems' && q.queryKey[1]===parentId })`.
- [ ] `useFolderItems(folderId, sort, dir)` — `useFilesInFolder` 시그니처 + `qk.folderItems` 키.
- [ ] `useFileDetail(id)` — 변경 없음 (api.ts 자동 real).
- [ ] mutation hook 수정/신설 (낙관·pending 정책 표 적용):
  - rename(file/folder) — `onMutate` 낙관: cache 직접 patch (`qk.folderItems(parentId,*)`의 해당 row name 갱신, `qk.fileDetail(id)` 갱신). `onError` rollback + toast(409 RENAME_CONFLICT 분기). `onSettled` invalidate.
  - move(file/folder) — pending: hook이 `isPending` 노출 → 호출 component spinner overlay. `onSuccess` `afterFileMutated`/`afterFolderMutated`. `onError` toast.
  - delete(file/folder) — pending. `onSuccess` `afterTrashChanged(parentIdAtMutation)`.
  - restore — pending. `onSuccess` `afterTrashChanged(originalParentId)`.
  - createFolder — pending. `onSuccess` `afterFolderCreated(parentId)`.
- [ ] 묶음 mutation hook(`useMoveSelection`/`useDeleteSelection`/`useRestoreSelection`) — `Promise.allSettled` 결과를 `{ successCount, failedIds }`로 반환. 호출 component(BulkActionBar)는 toast `"3개 이동 완료, 2개 실패"` 표시.

### 검증 참조
- gate: `cd frontend && pnpm typecheck` clean. (test는 P5 일괄)

### 문서 반영
- P6 일괄.

### 실제 수행 결과 (2026-05-07)

P3 완료 시점에 모든 mutation hook이 이미 새 api 시그니처와 정합:
- useRenameFile — `mutationFn`에 `isFolder` 전달 (P3에서 갱신).
- useMoveBulk — `Vars.items: {id,type}[]` 패턴 (P3에서 갱신).
- useDeleteBulk — file/folder 분기 (기존 real-wired).
- useRestoreItem — file/folder 분기 (기존 real-wired).

invalidation 측면:
- `qk.filesListPrefix(folderId)` + `invalidations.afterFilesMoved/afterRename/afterDelete/afterRestore`가 plan의 5 helper와 동등한 무효화 매트릭스를 이미 제공. CLAUDE.md INVARIANT #3(기존 구조 우선), #4(구조 무결성) 따라 별도 invalidations.ts 신설 회피 — KISS.

**결정적 사용자 가시 버그 fix**: `useUpload.ts` xhr.onload 분기가 status 200만 success로 처리 → 신규 파일 업로드(backend 201 Created)는 'failed'로 폴백되어 listing에 표시 안 되던 근본 원인. 200||201 모두 done+invalidate로 수정.

낙관 rename은 plan 명시 사항이지만, 기존 hook이 pending+invalidate로 안정성 우선 정책을 이미 채택하고 있어 KISS 우선 — P5/P6 smoke 결과에 따라 재검토.

useCreateFolder hook은 UI 진입점이 아직 없어 YAGNI — api wrapper만 정비, hook은 호출부 도착 시 추가.

### Commit
- [x] `fix(folder-items-wire): upload 201 응답 처리 + listing invalidate (P4)`

---

## P5 — Test 전환 + 회귀 fix + 풀 게이트

### 작업 전 필독
- `plan.md` §Test 전환.
- `spec.md` §3.3.

### 원본 코드 참조
- `frontend/src/lib/api.renameFile.test.ts` — mock id 사용 → fetch mock 전환 대상.
- `frontend/src/lib/api.moveFiles.test.ts` — 동일.
- 다른 component test가 wrapper 함수 직접 spy 한다면 그대로 유지.

### 구현 대상
- [ ] `api.renameFile.test.ts` 전환:
  - 기존: `await api.renameFile('mock-id', '새이름')` + `MOCK_FILES` lookup.
  - 변경: `vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce({ ok: true, json: async () => ({ file: {...} }) }))` + `await api.renameFile('uuid', '새이름')` + `expect(fetch).toHaveBeenCalledWith('/api/files/uuid', expect.objectContaining({ method:'PATCH', ... }))`.
  - rollback / 409 case 별도 case로 추가.
- [ ] `api.moveFiles.test.ts` 전환 — 동형. `Promise.allSettled` fanout 결과 검증.
- [ ] 깨지는 component test 추출:
  - `cd frontend && pnpm test --run 2>&1 | grep -E 'FAIL|Tests:'` — 실패 case 목록.
  - case별 분류: (a) wrapper spy로 우회 가능 vs (b) 실제 listing 데이터 필요 → 후자는 `vi.spyOn(api, 'getFolderItems').mockResolvedValue([...])` 패턴.
- [ ] 깨지는 case 일괄 fix.
- [ ] **최종 게이트**:
  - `pnpm test --run` GREEN — 전수.
  - `pnpm typecheck` clean.
  - `pnpm lint` clean.
  - `pnpm build` clean.

### 검증 참조
- gate: 위 4 명령 모두 exit 0.

### 문서 반영
- P6 일괄.

### Commit
- [ ] `test(folder-items-wire): fetch mock 전환 + 회귀 fix (P5)`

---

## P6 — Smoke + docs sync + closure + PR

### 작업 전 필독
- `plan.md` §Acceptance criteria — smoke 7 시나리오.
- `BETA-RELEASE.md` §6 §7 (현 wording).
- `docs/02 §7.5`, `docs/01 §6.1`, `docs/01 §17.3`, `docs/04 §2`(필요 시).

### 구현 대상

#### Manual smoke (dev profile + LocalFsStorageClient)
- [ ] `cd backend && ./gradlew bootRun` (또는 dev 명령), `cd frontend && pnpm dev` 동시 실행.
- [ ] 시나리오 1: 로그인 → root → 폴더 생성 → 폴더 안 진입 → 파일 업로드 → listing 표시.
- [ ] 시나리오 2: RightPanel 파일 클릭 → file detail 메타데이터(name/size/updatedAt/updatedBy) 표시.
- [ ] 시나리오 3: 파일 rename (낙관 — UI 즉시 반영) → tree·breadcrumb·items 일관 (1초 내).
- [ ] 시나리오 4: 파일 move (pending — spinner) → 양쪽 폴더 listing 갱신.
- [ ] 시나리오 5: 폴더 rename → 자식 breadcrumb 갱신 확인 (해당 폴더 진입 후 breadcrumb 새 이름).
- [ ] 시나리오 6: 폴더 delete (pending) → 휴지통 listing 추가 + tree·items 제거.
- [ ] 시나리오 7: 휴지통 restore → 원위치 복원 + listing 표시.
- [ ] 모든 시나리오 PASS 시 P6 진행. 실패 시 fix → 해당 phase로 회귀.

#### Docs sync
- [ ] `docs/02 §7.5`:
  - `GET /api/folders/{id}/items` 명세 추가 (request/response/errors/정렬 정책).
  - `GET /api/files/{id}` 명세 추가.
- [ ] `docs/01 §6.1`:
  - `qk.folderItems(folderId, sort, dir)` 추가.
  - invalidation 헬퍼 5종 표 갱신.
- [ ] `docs/01 §17.3` — 'root' 가상 노드 items 합성 정책 명시.
- [ ] `BETA-RELEASE.md`:
  - §1 코드 베이스 게이트 — frontend test 카운트 갱신.
  - §7 deferred 항목 — folder-tree-detail Phase B 활성화 기록.
  - §6 audit emit coverage — 본 트랙 audit emit 신규 0개 (read endpoint 추가만, mutation은 기존 emit 재사용).
- [ ] `docs/progress.md` 최상단에 closure entry 추가:
  - 양식: `## 2026-05-07 — 🏁 wave2-t6-folder-items-wire 트랙 종료 (Wave 2 — T6: explorer mock 제거 + listing/detail/mutation real wire)`.
  - 범위 / 변경 핵심 / 검증 / 다음 세션 컨텍스트.

#### Archive dev-docs
- [ ] PR merge 후 별도 commit (또는 같은 PR에 포함 — T4/T5 답습 어느쪽 정책인지 progress.md grep 확인): `dev/active/wave2-t6-folder-items-wire/` → `dev/completed/wave2-t6-folder-items-wire/` 이동.

#### PR
- [ ] `git push -u origin wave2-t6-folder-items-wire`.
- [ ] PR 제목: `feat(folder-items-wire): folder items + file detail + explorer mock 제거 (Wave 2 T6 — Phase B)`.
- [ ] PR body:
  - Summary 3줄: backend 2 endpoint + frontend mock 일괄 제거 + mutation 8종 wire.
  - Test plan 7 smoke 시나리오 + backend/frontend 게이트.
  - Co-Authored-By Claude Opus 4.6.

### 검증 참조
- gate: smoke 7/7 PASS, `grep -rn 'Wave 2 T6\|folder-items-wire\|folder-tree-detail Phase B' docs/ BETA-RELEASE.md` 일관, PR 생성 성공.

### 문서 반영
- 위 docs sync 항목.

### Commit
- [ ] `docs(folder-items-wire): docs/02·01·BETA·progress sync + closure (P6)`
