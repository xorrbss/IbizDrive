---
Last Updated: 2026-05-08
---

# v1x-restore-conflict-dialog — Tasks

## Phase별 상태

- [ ] **Phase 1** — Backend: `RestoreRequest` DTO + Service 시그니처 확장 + Controller body 바인딩
- [ ] **Phase 2** — Backend: 단위 테스트 (Service + Controller, file/folder 양쪽)
- [ ] **Phase 3** — Backend: GlobalExceptionHandler `FileRestoreConflictException` 매핑
- [ ] **Phase 4** — Frontend: api/hook/store 시그니처 확장
- [ ] **Phase 5** — Frontend: `RestoreConflictDialog` 컴포넌트 + 마운트
- [ ] **Phase 6** — Frontend: TrashRowActions/BulkActionBar onError 분기
- [ ] **Phase 7** — Frontend: 테스트 보강 (api/hook/dialog/actions/bulk)
- [ ] **Phase 8** — docs 동기화 (02 §7.5/§7.6/§8 + 01 §13 + progress)
- [ ] **Phase 9** — PR

---

## Phase 1 — Backend DTO + Service + Controller

### 작업 전 필독
- plan §"Phase 1 — Backend: DTO + Service + Controller" + §"리스크 & 완화".
- 사용자 컨펌 게이트 §1 (envelope 통일) 결과.

### 원본 코드 참조
- `backend/src/main/java/com/ibizdrive/file/FileController.java` L140~165 (restore endpoint).
- `backend/src/main/java/com/ibizdrive/file/FileMutationService.java` L242~281 (restore service).
- `backend/src/main/java/com/ibizdrive/folder/FolderController.java` L237~ (restore endpoint).
- `backend/src/main/java/com/ibizdrive/folder/FolderMutationService.java` L357~ (restore service).
- `backend/src/main/java/com/ibizdrive/file/FileNameConflictException.java`.
- `backend/src/main/java/com/ibizdrive/folder/FolderRestoreConflictException.java` (패턴 미러).

### 구현 대상
- [ ] `backend/src/main/java/com/ibizdrive/common/dto/RestoreRequest.java` 신규 — `record RestoreRequest(@Nullable String name) {}`.
- [ ] `backend/src/main/java/com/ibizdrive/file/FileRestoreConflictException.java` 신규 — RuntimeException(message). FolderRestoreConflictException 패턴 미러.
- [ ] `FileMutationService.restore(UUID fileId, UUID actorId)` → `restore(UUID fileId, UUID actorId, @Nullable String newName)`. newName != null 시:
  - NFC 정규화 + 검증 (`FileNameValidator` 또는 기존 rename util 재사용).
  - target.normalized_name = normalized(newName). UNIQUE 위반 → `FileNameConflictException`.
  - newName == null + 충돌 → `FileRestoreConflictException` (신규).
- [ ] `FolderMutationService.restore` 동일 패턴 — newName 인자 추가, FolderRestoreConflictException(원본) vs FolderNameConflictException(신규).
- [ ] `FileController.restore` body 바인딩 `@RequestBody(required = false) RestoreRequest req`. service 호출에 `req != null ? req.name() : null` 전달.
- [ ] `FolderController.restore` 동일.

### 검증 참조
- `cd backend && ./gradlew compileJava` 성공.
- Phase 2 테스트로 회귀 검증.

### 문서 반영
- Phase 8 에서 docs/02 §7.5/§7.6 갱신 — body 시그니처 명시.

---

## Phase 2 — Backend 단위 테스트

### 작업 전 필독
- 기존 `FileMutationServiceTest`, `FolderMutationServiceTest`, `FileControllerTest`, `FolderControllerTest` 패턴.

### 원본 코드 참조
- `backend/src/test/java/com/ibizdrive/file/FileMutationServiceTest.java` (기존 restore 케이스).
- `backend/src/test/java/com/ibizdrive/folder/FolderMutationServiceTest.java`.

### 구현 대상
- [ ] `FileMutationServiceTest`:
  - [ ] `restore_originalName_conflict_throws_FileRestoreConflictException`.
  - [ ] `restore_newName_success`.
  - [ ] `restore_newName_conflict_throws_FileNameConflictException`.
  - [ ] `restore_newName_invalid_throws_VALIDATION_ERROR` (빈 / 길이 초과 / 금지문자).
  - [ ] `restore_newName_normalized_NFC` (NFD 입력 → DB normalized_name 검증).
- [ ] `FolderMutationServiceTest` 동일 5건 (folder 양식).
- [ ] `FileControllerTest`:
  - [ ] body 누락 → 기존 200 (회귀).
  - [ ] body `{ "name": "foo.pdf" }` → service 호출 인자 검증 (Mockito captor).
  - [ ] body `{ "name": "" }` → 400 VALIDATION_ERROR.
- [ ] `FolderControllerTest` 동일.

### 검증 참조
- `cd backend && ./gradlew test --tests "com.ibizdrive.file.*" --tests "com.ibizdrive.folder.*"` BUILD SUCCESSFUL.

### 문서 반영
- 없음.

---

## Phase 3 — Backend GlobalExceptionHandler

### 작업 전 필독
- `backend/src/main/java/com/ibizdrive/common/error/GlobalExceptionHandler.java` L60~89 (기존 매핑).

### 원본 코드 참조
- 같은 파일 L83~89 (FolderRestoreConflictException 매핑).

### 구현 대상
- [ ] `FileRestoreConflictException` 매핑 추가 → 409 `RESTORE_CONFLICT` (file/folder 통일).
- [ ] `FileNameConflictException` 매핑은 그대로 `RENAME_CONFLICT` (rename/move/restore-with-name 공통).
- [ ] javadoc 업데이트 (envelope 의미 재정의).

### 검증 참조
- Phase 2 controller 테스트에서 envelope code 검증 (`expect("$.error.code").value("RESTORE_CONFLICT")` 등).

### 문서 반영
- Phase 8 에서 docs/02 §8 동기.

---

## Phase 4 — Frontend api + hook + store

### 작업 전 필독
- `frontend/src/lib/api.ts` L964~983.
- `frontend/src/hooks/useRestoreItem.ts`.
- `frontend/src/stores/renameUi.ts` (미러 대상).

### 원본 코드 참조
- `frontend/src/stores/renameUi.ts`.

### 구현 대상
- [ ] `frontend/src/lib/api.ts`:
  - `restoreFile(id: string, opts?: { newName?: string }): Promise<void>`.
  - `restoreFolder(id: string, opts?: { newName?: string }): Promise<void>`.
  - opts.newName 있을 때 body `{ name: opts.newName }` POST. 없으면 기존 빈 body POST.
- [ ] `frontend/src/hooks/useRestoreItem.ts` Vars 에 `newName?: string` 추가, mutationFn pass-through.
- [ ] `frontend/src/stores/restoreConflictUi.ts` 신규 — zustand:
  - state: `isOpen: boolean`, `targetType: 'file'|'folder'|null`, `targetId: string|null`, `originalName: string`, `sourceFolderId: string|null`, `error: string|null`.
  - actions: `open({type,id,originalName,sourceFolderId})`, `close()`, `setError(msg)`.

### 검증 참조
- `cd frontend && pnpm typecheck` exit 0.

### 문서 반영
- 없음.

---

## Phase 5 — Frontend RestoreConflictDialog

### 작업 전 필독
- `frontend/src/components/files/RenameDialog.tsx` (123줄, 미러 대상).
- `frontend/src/lib/normalize.ts` (NFC 정규화).

### 원본 코드 참조
- `RenameDialog.tsx` 전체.

### 구현 대상
- [ ] `frontend/src/components/trash/RestoreConflictDialog.tsx` 신규.
  - store 에서 isOpen/targetType/targetId/originalName/sourceFolderId/error 읽음.
  - 기본 입력값 = `suggestRestoreName(originalName, type)` — `report.pdf` → `report (1).pdf`, 폴더 `Reports` → `Reports (1)`.
  - submit → `useRestoreItem.mutate({ type, id, sourceFolderId, newName })`:
    - onSuccess → toast.success + close.
    - onError(NAME_CONFLICT) → setError('같은 이름이 이미 존재합니다 — 다른 이름으로 시도하세요').
    - onError(VALIDATION_ERROR) → setError(message).
    - onError(other) → toast.error + close.
  - Esc 닫기, previousFocus 복귀, role=dialog/aria-modal.
- [ ] `frontend/src/lib/restoreNameSuggest.ts` (또는 inline) — `suggestRestoreName(name, type)`. 파일은 확장자 보존 (`a.pdf` → `a (1).pdf`), 폴더는 단순 ` (1)` 접미사.
- [ ] `frontend/src/app/trash/page.tsx` 에 `<RestoreConflictDialog />` 마운트 (또는 (explorer)/layout 의 portal).

### 검증 참조
- Phase 7 테스트.
- `pnpm typecheck && pnpm lint`.

### 문서 반영
- Phase 8 에서 docs/01 §13 갱신.

---

## Phase 6 — Frontend TrashRowActions / BulkActionBar 통합

### 작업 전 필독
- `frontend/src/components/trash/TrashRowActions.tsx` L18~37.
- `frontend/src/components/files/BulkActionBar.tsx` L184~205 (`undoDelete`).

### 원본 코드 참조
- 위 두 파일.

### 구현 대상
- [ ] `TrashRowActions.tsx`:
  - onError 핸들러 RESTORE_CONFLICT 분기 → `restoreConflictUiStore.open({type:item.type, id:item.id, originalName:item.name, sourceFolderId:item.originalParentId})`.
  - toast.error 폐기.
- [ ] `BulkActionBar.tsx` `undoDelete`:
  - `items.length === 1` + RESTORE_CONFLICT → 동일 store.open.
  - 다건 + RESTORE_CONFLICT → 기존 `toast.error('같은 이름의 항목이 이미 존재합니다')` 유지.

### 검증 참조
- Phase 7 테스트.

### 문서 반영
- Phase 8 docs/01 §13.

---

## Phase 7 — Frontend 테스트

### 작업 전 필독
- 기존 테스트 패턴 (RenameDialog.test.tsx, useRestoreItem.test.tsx, BulkActionBar.test.tsx).

### 원본 코드 참조
- `frontend/src/components/files/RenameDialog.test.tsx` (있다면).
- `frontend/src/hooks/useRestoreItem.test.tsx`.
- `frontend/src/lib/api.trash.test.ts`.

### 구현 대상
- [ ] `frontend/src/lib/api.trash.test.ts` — `restoreFile/restoreFolder` body 분기 (newName 있음/없음).
- [ ] `frontend/src/hooks/useRestoreItem.test.tsx` 보강 — newName 전달 + RENAME_CONFLICT 분기.
- [ ] `frontend/src/components/trash/RestoreConflictDialog.test.tsx` 신규 — open/자동 제안/제출/NAME_CONFLICT inline alert/other error toast/Esc.
- [ ] `frontend/src/components/trash/TrashRowActions.test.tsx` (있다면 보강, 없으면 신규) — RESTORE_CONFLICT 시 store.open 호출.
- [ ] `frontend/src/components/files/BulkActionBar.test.tsx` — 단건 Undo 분기 + 다건 toast 유지.
- [ ] `frontend/src/lib/restoreNameSuggest.test.ts` 신규 (파일 확장자 보존 / 폴더 접미사).

### 검증 참조
- `cd frontend && pnpm test --run` 전체 PASS, 신규 추가 + 기존 통과.

### 문서 반영
- 없음.

---

## Phase 8 — docs 동기화

### 작업 전 필독
- `docs/02-backend-data-model.md` §7.5 (라인 1034~), §7.6 (라인 1094~), §8 (라인 1221~).
- `docs/01-frontend-design.md` §13 (라인 808~).
- `docs/progress.md` 최상단 양식.

### 원본 코드 참조
- 본 트랙의 backend/frontend 변경 전체.

### 구현 대상
- [ ] `docs/02 §7.5` folder restore 행 — body `{ name? }` + 에러 코드 (RESTORE_CONFLICT/RENAME_CONFLICT 분리).
- [ ] `docs/02 §7.6` file restore 행 — 동일.
- [ ] `docs/02 §8` 에러 코드 표 — RESTORE_CONFLICT 의미 (원본 이름 충돌), RENAME_CONFLICT (새 이름/rename/move/restore-with-name 공통).
- [ ] `docs/01 §13` 휴지통 — RestoreConflictDialog UX flow + suggestRestoreName 로직.
- [ ] `docs/progress.md` 최상단 entry (CLAUDE.md §7 양식).

### 검증 참조
- `cd frontend && pnpm test --run` 통과 유지.

### 문서 반영
- 본 phase 가 문서 동기화 자체.

---

## Phase 9 — PR

### 작업 전 필독
- 모든 phase 완료 + 검증 게이트 PASS.

### 원본 코드 참조
- 본 트랙의 모든 커밋.

### 구현 대상
- [ ] commit phase 별 정리.
- [ ] `git push -u origin feat/v1x-restore-conflict-dialog`.
- [ ] `gh pr create`:
  - 제목: `feat(v1x-restore-conflict-dialog): 휴지통 복원 시 다른 이름으로 복원 다이얼로그 (M9 v1.x)`.
  - body: ## Summary (3 bullet) + ## Test plan (backend gradle / frontend test/typecheck/lint / 수동 시나리오 3건).

### 검증 참조
- PR CI 통과.

### 문서 반영
- 머지 후 별도 archive PR 으로 dev/active → dev/completed.
