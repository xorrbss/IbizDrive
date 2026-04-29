---
Last Updated: 2026-04-29
Status: 📋 BOOTSTRAP — A6.0 진입 대기 (게이트 0)
---

# A6 — Folder Mutation: delete/restore (휴지통 트랙 1차) — Plan

## 요약

A4 closure block에서 별도 트랙으로 분리 권장된 folder mutation 잔여분 중 **delete / restore**를 단일 PR로 닫는다. rename/move는 A4.6/A4.7에서 이미 처리. 본 트랙은 docs/02 §7.5의 `DELETE /api/folders/:id` + `POST /api/folders/:id/restore` 두 endpoint를 활성화하고, 후손(folders + files) 재귀 cascade soft-delete를 service 레벨에서 구현한다.

워크트리 `a4-folder-file-domain`에 **uncommitted RED 테스트 87줄**이 이미 존재(2026-04-29 발견) — 본 트랙의 GREEN 구현이 직접 흡수.

## 단위 분할 — 단일 PR (A2/A3/A5 패턴)

추정 6~9 commits 규모. A4 분할(13~19) 미달 → KISS, 단일 PR.

- A6.0 docs 정합 + 에러 코드 (`RESTORE_CONFLICT`, `MOVE_INTO_*`는 이번 트랙 제외) — no-code minor patch
- A6.1 `FolderMutationService.delete` + 재귀 cascade + audit emit (RED→GREEN, 기존 RED 흡수)
- A6.2 `FolderMutationService.restore` + parent active 재검사 + UNIQUE 재검사 + audit emit (RED→GREEN)
- A6.3 `FolderController.delete` + `restore` endpoint + integration 권한 매트릭스 (RED 컨트롤러 테스트 흡수)
- A6.4 closure (PR + archive)

**out-of-scope (별도 트랙)**:
- File mutation rename/move/delete/restore (`/api/files/:id*`) — 본 PR은 folder만. file 트랙은 후속 분리.
- Hard purge job (`purge_after` 경과 row 영구 삭제) — 배치/스케줄러 트랙(docs/04 §13).
- Frontend 휴지통 UI (docs/01 §13) — backend 계약 안정화 후 별도 마일스톤.

## 현재 상태 분석

### V5 schema (master HEAD `c5d23e8`)

- `folders.deleted_at` / `purge_after` / `original_parent_id` 존재. CHECK: `(deleted_at IS NULL) = (purge_after IS NULL)` — 둘은 함께 set/clear.
- `folders.parent_id REFERENCES folders(id) ON DELETE RESTRICT` — hard delete 차단. Soft delete는 application 레벨.
- `idx_folders_unique_name` partial unique `WHERE deleted_at IS NULL` — soft-delete된 row는 UNIQUE 제약 외 → restore 시 재검사 필수.
- `files.folder_id REFERENCES folders(id) ON DELETE RESTRICT` + `original_folder_id` 컬럼. **폴더 hard delete 불가능 → 후손 파일도 soft delete 필요**.
- `idx_files_unique_name` 동일 패턴 (folder_id 기준 partial unique).

### 코드 — 기존 자산

- `Folder` entity: `deletedAt`, `purgeAfter`, `originalParentId` 매핑 완료.
- `FolderRepository`: `lockByIdAndDeletedAtIsNull`, `findByIdAndDeletedAtIsNull`, `findByParentIdAndDeletedAtIsNull`, `existsActiveByParentAndNormalizedName(ExcludingId)` — A4.5/A4.6에서 도입.
- `FolderMutationService`: `create/rename/move` + `emitAudit` helper + 클래스 레벨 `@Transactional`. **`delete`/`restore` 부재**.
- `FolderController`: POST/PATCH/POST(move) + `@PreAuthorize` SpEL. **DELETE/restore endpoint 부재**.
- `AuditEventType.FOLDER_DELETED` / `FOLDER_RESTORED` enum 정의됨 (A2 commit).
- `Permission` enum에 `DELETE` 존재(A3) — `hasPermission(#id, 'folder', 'DELETE')`로 SpEL 평가됨.
- `GlobalExceptionHandler`: `RENAME_CONFLICT`(409), `NOT_FOUND`(404), `BAD_REQUEST`(400) 매핑. **`RESTORE_CONFLICT` 미매핑**.

### 코드 — 부재 항목 (A6 신설)

- `FolderRestoreConflictException` 클래스
- `FolderMutationService.delete(UUID folderId, UUID actorId)`: 재귀 cascade soft-delete + 단일 FOLDER_DELETED audit (root만)
- `FolderMutationService.restore(UUID folderId, UUID actorId)`: parent active 재검사 + UNIQUE 재검사 + 자기 자신만 복원 (후손 일괄 복원은 별도 결정)
- `FolderRepository`:
  - `findByParentIdAndDeletedAtIsNotNull(UUID parentId)` 또는 동등 native query — soft-deleted 후손 walk용
  - 재귀 cascade를 `WITH RECURSIVE` native query 1발로 처리할지, service 레벨 BFS로 처리할지 A6.1에서 결정
- `FileItem`/`FileRepository` — 후손 파일 soft-delete를 위한 cascade 쿼리(또는 service 호출)
- `FolderController.delete`: `@DeleteMapping("/{id}")` + 204
- `FolderController.restore`: `@PostMapping("/{id}/restore")` + 200
- `GlobalExceptionHandler.handleFolderRestoreConflict` → 409 `RESTORE_CONFLICT` envelope

### 기존 RED 테스트 (uncommitted, 흡수 대상)

`backend/src/test/java/com/ibizdrive/folder/FolderMutationServiceTest.java`:
- `delete_activeFolder_softDeletesDescendantsAndEmitsAudit` — root + child 둘 다 soft-deleted, FOLDER_DELETED 발행 (root에 대해)
- `delete_missingFolder_throwsNotFound` — FolderNotFoundException + audit 0회
- `restore_softDeletedFolder_reactivatesAndEmitsAudit` — restore 후 active, FOLDER_RESTORED 발행
- `restore_nameConflict_throwsRestoreConflict` — 동일 부모에 같은 이름 active row 존재 시 FolderRestoreConflictException

`backend/src/test/java/com/ibizdrive/folder/FolderControllerTest.java`:
- `delete_returnsNoContent_andDelegates` — 204 + service.delete 호출 검증
- `restore_returnsOk_andDelegates` — 200 + service.restore 호출 검증

### docs 정합 상태

- docs/02 §7.5 `DELETE` / `restore` 행 존재 (line 890~891). 응답 스키마 본문도 921 line 부근에 명시.
- docs/02 §6.5 휴지통 SQL 가이드(file 기준) 존재. 폴더 cascade는 본 plan §A6.1에서 application 레벨로 명시.
- docs/02 §8 에러 코드 — `RESTORE_CONFLICT`가 §7.5 표에 등장하지만 §8 본문 등록 여부 A6.0에서 확인 후 보강.

## 목표 상태 (A6 종료 시점)

1. `FolderMutationService.delete(folderId, actorId)` — 후손(folders + files) 재귀 soft-delete + 단일 FOLDER_DELETED audit emit (root 한정).
2. `FolderMutationService.restore(folderId, actorId)` — 자기 자신만 복원, original_parent_id가 active일 때만 허용, 동일 부모 UNIQUE 재검사, FOLDER_RESTORED audit emit.
3. `FolderController.delete` + `restore` endpoint — `@PreAuthorize("hasPermission(#id, 'folder', 'DELETE')")` 가드 + 권한 매트릭스 GREEN.
4. `FolderRestoreConflictException` + `GlobalExceptionHandler` 매핑 → 409 `RESTORE_CONFLICT` envelope.
5. uncommitted RED 테스트 6개 → 모두 GREEN 흡수.
6. A4 회귀 0 (PermissionEvaluatorIntegrationTest 13/13 + FolderMutationServiceTest 기존 케이스 + FolderControllerTest 기존 케이스).

## phase별 실행 지도

### A6.0 — docs 정합 (no-code 또는 minor patch)

- docs/02 §8 에러 코드 본문에 `RESTORE_CONFLICT` 항목 존재 여부 확인 → 누락 시 1줄 보강.
- docs/02 §7.5 응답 본문에 cascade 정책 1줄 명시: "후손 폴더/파일은 동일 트랜잭션에서 soft-delete되며 audit는 root만 발행".
- docs/00 §5 ADR — 본 트랙용 ADR 추가 여부 검토. cascade 정책(audit root만 vs 모두)이 명시 결정 사항이면 ADR 1건 신설.
- docs/progress.md A6.0 phase 1블록.

### A6.1 — `delete()` 구현 + 후손 cascade

핵심 결정 (A6.1 진입 시점):

**(a) cascade 전략** — 둘 중 택1:
1. **service 레벨 BFS** — `findByParentIdAndDeletedAtIsNull` 재귀 호출 + 각 row UPDATE. 트랜잭션 길어질 수 있으나 코드 명확.
2. **`WITH RECURSIVE` 단일 native query** — `UPDATE folders SET deleted_at = ?, purge_after = ? WHERE id IN (recursive_descendants)`. 성능 우수하나 native SQL 복잡도 ↑.

→ **A6.1 시작 시점 KISS 평가**. 기존 cycle walk(`assertNoCycle`) 패턴이 service 레벨 BFS이므로 일관성 유지 + 트리 깊이 ≤ MAX_ANCESTOR_WALK 가정 → **(1) 우선 채택** 권장. 성능 이슈 발견 시 (2)로 전환 + ADR.

**(b) audit emission 범위** — root만 vs 후손 전체:
- root만 emit (단일 FOLDER_DELETED). before_state에 cascade descendant_count 포함.
- 후손 전체 emit는 audit_log 폭증 위험 — root만 채택 권장(KISS).

**(c) 후손 파일 처리** — `FileRepository`에 cascade soft-delete 메서드 도입 또는 native batch UPDATE.

구현 골격:
```java
@Transactional
public void delete(UUID folderId, UUID actorId) {
  Folder root = folderRepository.lockByIdAndDeletedAtIsNull(folderId)
    .orElseThrow(() -> new FolderNotFoundException(...));
  Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
  Instant purgeAfter = now.plus(30, ChronoUnit.DAYS);

  // BFS: root + 후손 폴더 수집
  List<UUID> folderIds = collectDescendantFoldersIncludingSelf(root.getId());
  // 후손 폴더 일괄 UPDATE
  folderRepository.softDeleteByIds(folderIds, now, purgeAfter, /* originalParent 보존 */);
  // 후손 파일 일괄 UPDATE (folder_id IN folderIds AND deleted_at IS NULL)
  fileRepository.softDeleteByFolderIds(folderIds, now, purgeAfter);

  emitAudit(FOLDER_DELETED, root.getId(), actorId,
    /* before */ Map.of("name", root.getName(), "parentId", root.getParentId()),
    /* after  */ Map.of("deletedAt", now.toString(), "descendantFolders", folderIds.size()));
}
```

`originalParentId` 처리: root는 `original_parent_id = parent_id` 세팅, 후손은 NULL 유지(restore 정책: 자기 자신만 복원, 후손 일괄 복원은 별도 결정).

테스트:
- 기존 RED 4개 GREEN
- 추가: cascade 깊이 3+ 트리 검증, file이 후손에 있을 때도 함께 soft-delete, FOLDER_DELETED audit 1회만 발행

### A6.2 — `restore()` 구현

```java
@Transactional
public Folder restore(UUID folderId, UUID actorId) {
  // soft-deleted 행 조회를 위한 별도 query 필요
  Folder target = folderRepository.lockSoftDeletedById(folderId)
    .orElseThrow(() -> new FolderNotFoundException(...));

  UUID parentId = target.getOriginalParentId();
  if (parentId != null) {
    folderRepository.findByIdAndDeletedAtIsNull(parentId)
      .orElseThrow(() -> new FolderNotFoundException("original parent not active: " + parentId));
  }

  // UNIQUE 재검사 — partial unique는 active row만 대상이므로 충돌 가능
  if (folderRepository.existsActiveByParentAndNormalizedName(parentId, target.getNormalizedName())) {
    throw new FolderRestoreConflictException(...);
  }

  target.setDeletedAt(null);
  target.setPurgeAfter(null);
  target.setParentId(parentId);   // original_parent_id 복원
  target.setOriginalParentId(null);
  target.setUpdatedAt(now);

  Folder saved = folderRepository.saveAndFlush(target);
  emitAudit(FOLDER_RESTORED, ...);
  return saved;
}
```

**자기 자신만 복원 (후손 일괄 복원 안 함)**: 사용자가 root만 복원하면 후손은 휴지통 잔존. 후손 일괄 복원은 별도 endpoint(`/restore?cascade=true`)로 분리 — 본 PR 범위 외.

테스트:
- 기존 RED 2개 GREEN
- 추가: parent가 soft-deleted일 때 404, original_parent_id가 NULL(root였던 경우) 정상 복원, audit before/after JSON 직렬화 검증

### A6.3 — Controller endpoint + integration 권한 매트릭스

```java
@DeleteMapping("/{id}")
@PreAuthorize("hasPermission(#id, 'folder', 'DELETE')")
public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal IbizDriveUserDetails principal) {
  folderMutationService.delete(id, principal.getUser().getId());
  return ResponseEntity.noContent().build();
}

@PostMapping("/{id}/restore")
@PreAuthorize("hasPermission(#id, 'folder', 'DELETE')")
public ResponseEntity<Map<String, FolderDto>> restore(@PathVariable UUID id, @AuthenticationPrincipal IbizDriveUserDetails principal) {
  Folder restored = folderMutationService.restore(id, principal.getUser().getId());
  return ResponseEntity.ok(Map.of("folder", FolderDto.from(restored)));
}
```

`GlobalExceptionHandler`:
```java
@ExceptionHandler(FolderRestoreConflictException.class)
public ResponseEntity<ApiError> handleFolderRestoreConflict(FolderRestoreConflictException ex) {
  return ResponseEntity.status(HttpStatus.CONFLICT)
    .body(ApiError.of("RESTORE_CONFLICT", "동일 위치에 같은 이름의 폴더가 존재해 복원할 수 없습니다", null));
}
```

테스트 (integration, A4.7 패턴):
- ADMIN: delete 204 + restore 200
- AUDITOR: 403 (READ만 가지고 있음, DELETE 부재)
- MEMBER without grant: 403 PERMISSION_DENIED envelope
- MEMBER with folder DELETE grant: 204 + restore 200
- 미존재 folderId: 404 NOT_FOUND envelope
- restore name conflict: 409 RESTORE_CONFLICT envelope
- 기존 unit 컨트롤러 RED 2개 GREEN

### A6.4 — closure

- PR `feat(A6): folder delete/restore + cascade soft-delete + RESTORE_CONFLICT envelope`
- CI green (backend junit + frontend vitest)
- squash-merge → master
- docs/progress.md 최상단 "🏁 A6 마일스톤 종료" (A4 패턴 회고 1블록 — folder mutation 트랙 닫힘 + 잔여 file mutation 트랙 backlog 명시)
- `dev/active/a6-folder-mutation-delete/` → `dev/completed/`

## acceptance criteria

1. ✅ `FolderMutationService.delete` + `restore` 구현, FolderMutationServiceTest 6+ 케이스 GREEN
2. ✅ `FolderController.delete` + `restore` endpoint + ControllerTest 2 + integration 6+ GREEN
3. ✅ `FolderRestoreConflictException` + `GlobalExceptionHandler` 매핑 → 409 `RESTORE_CONFLICT` envelope
4. ✅ 후손 폴더 + 파일 cascade soft-delete (folder_id IN root 후손)
5. ✅ FOLDER_DELETED audit 1회만 (root) — descendantFolders count after_state에 포함
6. ✅ A4 회귀 0 — PermissionEvaluatorIntegrationTest 13/13, 기존 FolderMutationServiceTest 케이스, 기존 FolderControllerTest 케이스
7. ✅ A2 audit append-only `42501` 회귀 0 (V5 GRANT 무수정)
8. ✅ docs/02 §7.5 cascade 정책 1줄 명시 + §8 RESTORE_CONFLICT 본문 정합
9. ✅ PR CI green + squash-merge
10. ✅ dev/active 비어있음 (a5는 별도 세션 진행 중이므로 a6 항목만 archive)

## 검증 게이트

- **게이트 0** (bootstrap 종료): plan/context/tasks 3파일 commit
- **게이트 1** (A6.0 종료): docs/02 §7.5 + §8 + ADR(필요 시) 정합 commit
- **게이트 2** (A6.1 종료): delete + cascade GREEN, A4 회귀 0
- **게이트 3** (A6.2 종료): restore + RESTORE_CONFLICT GREEN, FOLDER_RESTORED audit 정합
- **게이트 4** (A6.3 종료): controller + integration 권한 매트릭스 GREEN
- **게이트 5** (A6.4 PR 생성 직전): 사용자 OK 대기

## 리스크와 완화 전략

| 리스크 | 영향 | 완화 |
|---|---|---|
| cascade BFS 깊이 폭증 (트리 corruption) | 트랜잭션 timeout / OOM | `MAX_ANCESTOR_WALK` 동일 패턴(visited Set + hop limit), 1000 hop 초과 시 IllegalStateException |
| descendant 파일 cascade 누락 | 후손 폴더 deleted인데 자식 파일 active → orphan | 동일 트랜잭션에서 `fileRepository.softDeleteByFolderIds` 일괄 UPDATE + 단위 테스트 (file이 후손에 있는 케이스) |
| restore 시 original_parent_id가 soft-deleted | UNIQUE 검사 통과해도 부모가 휴지통 → 일관성 깨짐 | parent active 사전 검사 (404 envelope) — 후손 일괄 복원 회피 정책의 보완 |
| audit before/after JSON 직렬화 (Map.of with null) | NPE | `LinkedHashMap` 패턴(create의 afterState와 동일) 사용 |
| `Folder.parentId == null` (root) restore | original_parent_id도 NULL — UNIQUE 검사 시 ZERO_UUID COALESCE | `existsActiveByParentAndNormalizedName(null, name)` 기존 native query가 이미 처리(ADR #27) |
| File mutation 트랙과의 의존 | A6에서 file repository에 새 메서드 추가 → 후속 file delete/restore 트랙과 충돌 | 본 PR은 cascade용 batch UPDATE 1개만 추가, mutation 시그니처는 file 트랙에서 별도 도입 |
| Testcontainers Docker 미가용 | 통합 테스트 SKIP | A4 패턴 그대로 — `@Testcontainers` + `assumeDockerAvailable` 가드 |
| frontend errors mirror 미반영 | 프론트가 RESTORE_CONFLICT 처리 못함 | A6 범위 외, frontend 트랙에서 후속 — backend 계약은 docs/02 §8에서 고정 |

## 참조

- docs/02 §2.3 (folders schema), §2.4 (files schema), §6.5 (휴지통 SQL — file 기준), §7.5 (folder API 표)
- docs/02 §8 (에러 코드 — RESTORE_CONFLICT 본문 정합 대상)
- A4 closure block (`docs/progress.md` 최상단) — file mutation 트랙 backlog 항목
- `backend/src/main/resources/db/migration/V5__folders_files_permissions.sql` line 24~53 (folders), 55~86 (files)
- `backend/.../folder/FolderMutationService.java` (create/rename/move 패턴)
- `backend/.../folder/FolderRepository.java` (lock + exists + native query 패턴)
- `backend/.../common/error/GlobalExceptionHandler.java` (RENAME_CONFLICT 매핑 패턴)
- 기존 RED 테스트 (uncommitted on worktree `a4-folder-file-domain`)
