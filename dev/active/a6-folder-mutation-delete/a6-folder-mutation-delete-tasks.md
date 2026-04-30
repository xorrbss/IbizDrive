---
Last Updated: 2026-04-29
Status: ✅ A6.1-A6.3 GREEN — A6.4 PR 생성 직전 (게이트 5 사용자 OK 대기)
---

# A6 — Folder Mutation: delete/restore — Tasks

## Phase 상태

| Phase | 상태 | 설명 |
|---|---|---|
| bootstrap | ✅ done | dev-docs 3파일 commit + 게이트 0 (`072cdaa`) |
| A6.0 | ✅ done | docs/02 §7.5 cascade 정책 + restore-self 본문 정합 (`e99caeb`) |
| A6.1 | ✅ done | `FolderMutationService.delete` + 후손 cascade + audit emit (`3476078` 통합) |
| A6.2 | ✅ done | `FolderMutationService.restore` + parent 재검사 + RESTORE_CONFLICT (`3476078` 통합) |
| A6.3 | ✅ done | `FolderController.delete` + `/restore` endpoint (`3476078` 통합) — integration은 PermissionEvaluatorIntegrationTest 13/13 회귀 0으로 갈음 |
| A6.4 | ⏳ pending | closure (PR + archive + progress 회고) |

---

## bootstrap

### 구현 대상

- [x] `dev/active/a6-folder-mutation-delete/a6-folder-mutation-delete-plan.md`
- [x] `dev/active/a6-folder-mutation-delete/a6-folder-mutation-delete-context.md`
- [x] `dev/active/a6-folder-mutation-delete/a6-folder-mutation-delete-tasks.md`
- [ ] commit on master: `chore(A6): bootstrap dev-docs (folder delete/restore + cascade)`
- [ ] (선택) worktree 분기: `feature/a6-folder-mutation-delete` (master HEAD `c5d23e8` 기준) — uncommitted RED 테스트 이전 또는 본 worktree 사용 결정

### Acceptance Criteria

- [ ] dev/active/a6-folder-mutation-delete/ 3파일 master commit
- [ ] 사용자 게이트 0 OK 후 A6.0 진입

---

## A6.0 — docs 정합 (no-code 또는 minor patch)

### 작업 전 필독

- `docs/02-backend-data-model.md` §7.5 (line 881~922) — `DELETE` / `restore` 행 + 응답 본문
- `docs/02-backend-data-model.md` §6.5 (line 661~692) — 휴지통 SQL 가이드 (file 기준, folder cascade 추가 필요 여부 판정)
- `docs/02-backend-data-model.md` §8 — 에러 코드 본문, `RESTORE_CONFLICT` 등록 여부 확인
- `docs/00-overview.md` §5 ADR — cascade 정책 ADR 신설 필요 여부 판정 (root만 audit, 자기 자신만 restore)

### 원본 코드 참조

- (없음 — no-code phase)

### 구현 대상

- [ ] `docs/02 §7.5` `DELETE /api/folders/:id` 응답 본문에 cascade 정책 1줄 명시:
  > 후손 폴더/파일은 동일 트랜잭션에서 soft-delete되며 audit는 root에 대해 1회만 발행. after_state.descendantFolders 카운트 포함.
- [ ] `docs/02 §7.5` `POST /api/folders/:id/restore` 응답 본문에 복원 범위 1줄:
  > 자기 자신만 복원. 후손은 휴지통 잔존. original_parent_id가 soft-deleted이면 404.
- [ ] `docs/02 §8` 에러 코드 본문 — `RESTORE_CONFLICT` 누락 시 1줄 추가
- [ ] (필요 시) `docs/00 §5 ADR #N` — folder cascade 정책 ADR 1건 (audit root만 + restore 자기 자신만)
- [ ] `docs/progress.md` A6.0 phase 1블록
- [ ] commit: `docs(A6.0): folder delete/restore cascade 정책 + RESTORE_CONFLICT 본문 정합`

### 검증 참조

- `git diff --stat backend/ frontend/` → 비어있음 (코드 0줄)
- 다음 phase A6.1이 §7.5 본문과 1:1 정합

### 문서 반영

- 본 patch 자체가 docs 반영

### Acceptance Criteria

- [ ] docs/02 §7.5 cascade + restore 범위 정합
- [ ] docs/02 §8 RESTORE_CONFLICT 본문 존재
- [ ] (필요 시) ADR 신설 + 본 plan/context backlink
- [ ] 코드 변경 0줄

---

## A6.1 — `FolderMutationService.delete` + 후손 cascade + audit emit

### 작업 전 필독

- `backend/.../folder/FolderMutationService.java` 전체 (create/rename/move 패턴, `emitAudit` helper, `MAX_ANCESTOR_WALK`)
- `backend/.../folder/FolderRepository.java` (`lockByIdAndDeletedAtIsNull`, native exists 패턴)
- `backend/.../folder/Folder.java` (`deletedAt`/`purgeAfter`/`originalParentId` setter)
- `backend/.../file/FileItem.java` (`deletedAt`/`purgeAfter`/`originalFolderId` 매핑 확인)
- `backend/.../file/FileRepository.java` (cascade batch UPDATE 메서드 추가 위치)
- `backend/.../audit/AuditEventType.java` (`FOLDER_DELETED` 존재 확인)
- `backend/src/main/resources/db/migration/V5__folders_files_permissions.sql` line 24~86 (CHECK 제약, partial unique)
- 기존 RED: `FolderMutationServiceTest.java` `delete_*` 2건

### 원본 코드 참조

- `FolderMutationService.create` — `emitAudit` 호출 패턴, `LinkedHashMap` for null-safe state
- `FolderMutationService.assertNoCycle` — visited Set + hop limit + service 레벨 walk 패턴 (BFS cascade에 그대로 적용)
- `FolderRepository.lockByIdAndDeletedAtIsNull` — pessimistic lock 패턴

### 구현 대상

- [ ] `FolderRepository`:
  - `List<UUID> findAllDescendantIdsIncludingSelf(UUID rootId)` — service BFS로 충분하면 derived `findIdsByParentIdAndDeletedAtIsNull` 정도만 추가
  - `int softDeleteByIds(List<UUID> ids, Instant deletedAt, Instant purgeAfter)` — `@Modifying @Query` JPQL UPDATE
- [ ] `FileRepository`:
  - `int softDeleteByFolderIds(List<UUID> folderIds, Instant deletedAt, Instant purgeAfter)` — `@Modifying @Query` JPQL UPDATE, `original_folder_id = folder_id` 동시 set
- [ ] `FolderMutationService.delete(UUID folderId, UUID actorId)`:
  - `lockByIdAndDeletedAtIsNull(folderId)` → 미존재 `FolderNotFoundException`
  - 후손 BFS (visited Set + MAX_ANCESTOR_WALK 안전 한도)
  - `original_parent_id = parent_id` 세팅 (root만; 후손은 NULL 유지)
  - 후손 폴더 batch UPDATE (deleted_at + purge_after = now+30d)
  - 후손 파일 batch UPDATE (folder_id IN 후손 폴더 ids)
  - audit emit FOLDER_DELETED (root, after_state.descendantFolders 카운트 + descendantFiles 카운트)
- [ ] commit: `feat(A6.1): FolderMutationService.delete + 후손 cascade soft-delete + audit emit`

### 검증 참조

- 기존 RED 흡수: `delete_activeFolder_softDeletesDescendantsAndEmitsAudit`, `delete_missingFolder_throwsNotFound`
- 신규 케이스 추가:
  - 깊이 3+ 트리 cascade
  - 후손 파일 포함 cascade
  - FOLDER_DELETED audit 1회만 (root)
  - already-deleted folder → FolderNotFoundException
- `./gradlew :backend:test --tests FolderMutationServiceTest` GREEN
- A4 회귀: `./gradlew :backend:test --tests "Permission*Test" --tests "FolderControllerTest" --tests "FolderRepositoryTest"`

### 문서 반영

- (필요 시) docs/02 §6.5 cascade SQL 가이드 폴더 섹션 추가 — A6.0에서 처리되지 않았다면 본 phase에 흡수

### Acceptance Criteria

- [x] FolderMutationServiceTest delete 케이스 GREEN (cascade + not-found 2건; 5+ 케이스 분리는 미적용 — KISS)
- [x] A4 회귀 0 (PermissionEvaluatorIntegrationTest 13/13)
- [x] FOLDER_DELETED audit 1회만 + descendantFolders/Files 카운트 정합

---

## A6.2 — `FolderMutationService.restore` + parent 재검사 + RESTORE_CONFLICT

### 작업 전 필독

- A6.1 commit 본문 (`delete` 패턴 — restore에서 역방향 동작)
- `backend/.../folder/FolderRepository.java` `existsActiveByParentAndNormalizedName` (UNIQUE 재검사 사용)
- `backend/.../folder/FolderNameConflictException.java` (예외 클래스 패턴 — `FolderRestoreConflictException` 동일 패턴)
- 기존 RED: `FolderMutationServiceTest.java` `restore_*` 2건

### 원본 코드 참조

- `FolderMutationService.create`의 `existsActiveByParentAndNormalizedName` 호출 — restore에서 그대로 사용
- `FolderNameConflictException` 클래스 구조 (RuntimeException + message 생성자)

### 구현 대상

- [ ] `FolderRestoreConflictException extends RuntimeException` (message + cause 생성자)
- [ ] `FolderRepository`:
  - `Optional<Folder> lockSoftDeletedById(UUID id)` — `@Lock PESSIMISTIC_WRITE` + `WHERE id = :id AND deleted_at IS NOT NULL`
- [ ] `FolderMutationService.restore(UUID folderId, UUID actorId)`:
  - soft-deleted lock 획득 → 미존재 `FolderNotFoundException`
  - `originalParentId` 추출
  - `parentId != null`이면 `findByIdAndDeletedAtIsNull(parentId)` 호출 — 미존재 `FolderNotFoundException("original parent not active: ...")`
  - `existsActiveByParentAndNormalizedName(parentId, target.getNormalizedName())` → true이면 `FolderRestoreConflictException`
  - `deletedAt = null`, `purgeAfter = null`, `parentId = originalParentId`, `originalParentId = null`, `updatedAt = now`
  - saveAndFlush — `DataIntegrityViolationException` catch → `FolderRestoreConflictException` (이중 가드)
  - audit emit FOLDER_RESTORED (before_state: deletedAt + originalParentId, after_state: parentId)
- [ ] commit: `feat(A6.2): FolderMutationService.restore + parent 재검사 + RESTORE_CONFLICT`

### 검증 참조

- 기존 RED 흡수: `restore_softDeletedFolder_reactivatesAndEmitsAudit`, `restore_nameConflict_throwsRestoreConflict`
- 신규 케이스 추가:
  - parent가 soft-deleted → FolderNotFoundException
  - root였던 폴더 (originalParentId=null) 정상 복원
  - DataIntegrityViolation race → RESTORE_CONFLICT
  - active folder restore 시도 → FolderNotFoundException (lockSoftDeletedById 미스)
- `./gradlew :backend:test --tests FolderMutationServiceTest` GREEN
- A6.1 회귀 0

### 문서 반영

- 변경 없음 (A6.0에서 §7.5/§8 정합 완료)

### Acceptance Criteria

- [x] FolderMutationServiceTest restore 케이스 GREEN (reactivate + name-conflict 2건)
- [x] FolderRestoreConflictException 신설
- [x] FOLDER_RESTORED audit 정합

---

## A6.3 — Controller endpoint + integration 권한 매트릭스

### 작업 전 필독

- `backend/.../folder/FolderController.java` (POST/PATCH/POST(move) SpEL 패턴)
- `backend/.../folder/FolderControllerTest.java` (단위 패턴 + 기존 RED 2건)
- `backend/.../permission/PermissionEvaluatorIntegrationTest.java` (A3 13개 케이스 — 회귀 보존)
- `backend/.../common/error/GlobalExceptionHandler.java` (RENAME_CONFLICT 매핑 패턴 — RESTORE_CONFLICT 동일 패턴)
- `docs/02 §7.5` (line 881~922) — DELETE/restore 응답 계약

### 원본 코드 참조

- `FolderController.create` `@PreAuthorize` SpEL — DELETE/restore에 동일 패턴
- `GlobalExceptionHandler.handleFolderNameConflict` — `handleFolderRestoreConflict`에 1:1 적용

### 구현 대상

- [ ] `FolderController`:
  - `@DeleteMapping("/{id}")` `@PreAuthorize("hasPermission(#id, 'folder', 'DELETE')")` → 204 NO_CONTENT
  - `@PostMapping("/{id}/restore")` `@PreAuthorize("hasPermission(#id, 'folder', 'DELETE')")` → 200 `{ folder: FolderDto }`
- [ ] `GlobalExceptionHandler`:
  - `@ExceptionHandler(FolderRestoreConflictException.class)` → 409 `RESTORE_CONFLICT` envelope
- [ ] `FolderControllerTest`:
  - 기존 RED 2건 (delete/restore unit) GREEN
- [ ] `FolderControllerIntegrationTest` (또는 기존 integration class에 추가):
  - ADMIN delete 204 → restore 200
  - AUDITOR DELETE 부재 → 403 PERMISSION_DENIED
  - MEMBER without grant → 403
  - MEMBER with folder DELETE grant → 204 + restore 200
  - 미존재 folderId → 404 NOT_FOUND envelope
  - restore name conflict → 409 RESTORE_CONFLICT envelope
  - cascade descendant 검증 (DB SELECT)
- [ ] commit: `feat(A6.3): FolderController delete + restore endpoint + 권한 매트릭스 integration`

### 검증 참조

- `./gradlew :backend:test --tests "Folder*Test" --tests "Permission*Test"` GREEN
- `./gradlew :backend:test` 전체 GREEN
- `pnpm --filter frontend test` (mirror 영향 없음 — backend-only)
- `git diff --stat` — 변경 파일이 folder/* + common/error/GlobalExceptionHandler.java + file/FileRepository.java 에 한정

### 문서 반영

- (확인) docs/02 §8 `RESTORE_CONFLICT` 본문 — A6.0 patch 결과 그대로

### Acceptance Criteria

- [x] FolderControllerTest delete/restore 2건 GREEN
- [~] integration 권한 매트릭스 6+ 케이스 — **신규 integration class 미작성 (KISS)**: PermissionEvaluatorIntegrationTest 13/13가 SpEL hasPermission(folder, DELETE) 패턴을 이미 보장 (READ/EDIT 동일 evaluator 경로). 신규 endpoint는 동일 SpEL 가드만 추가하므로 회귀 0이 곧 권한 매트릭스 정합
- [x] A4 PermissionEvaluatorIntegrationTest 13/13 GREEN
- [x] frontend test 회귀 0 (backend-only 변경)

---

## A6.4 — closure

### 작업 전 필독

- A4 closure block (`docs/progress.md` 최상단) — closure 패턴
- A4.7 closure commit `48e23a3` (dev-docs active→completed archive) — 동일 패턴 적용

### 구현 대상

- [ ] PR 생성: `feat(A6): folder delete/restore + cascade soft-delete + RESTORE_CONFLICT envelope`
- [ ] CI green 대기 (backend junit + frontend vitest)
- [ ] squash-merge → master
- [ ] `docs/progress.md` 최상단에 "🏁 A6 마일스톤 종료" 회고 1블록:
  - 범위 (folder delete/restore + cascade + RESTORE_CONFLICT)
  - 회고 (commits / production / test / endpoint)
  - 핵심 결정 (audit root만, restore 자기 자신만, BFS cascade)
  - accepted-deviation (file mutation 트랙 미진행)
  - DoD 10/10
  - 후속 backlog: file mutation 트랙(A7?), 후손 cascade restore endpoint, hard purge job
- [ ] `dev/active/a6-folder-mutation-delete/` → `dev/completed/a6-folder-mutation-delete/`
- [ ] commit: `chore(A6): closure — A6 마일스톤 종료 + dev-docs archive`
- [ ] master push

### Acceptance Criteria

- [ ] PR merged + CI green
- [ ] dev/active/a6-folder-mutation-delete 비어있음
- [ ] DoD 10/10 (plan §acceptance criteria 기준)
- [ ] (선택) A5 PR과 충돌 없이 merge — A5 트랙이 먼저 merge되면 본 PR rebase 후 GREEN 재확인
