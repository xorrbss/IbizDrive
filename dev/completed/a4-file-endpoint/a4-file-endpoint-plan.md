# A4.8 — File mutation service + 4 REST endpoints

## Goal

Mirror A4.6 `FolderMutationService` + A4.7 `FolderController` for the **file** domain:
`rename` / `move` / `delete` / `restore` on `files` table, with audit emission, name-conflict
ddouble-guard (DB unique idx + service pre-check), pessimistic write lock, and SpEL permission
gate via `IbizDrivePermissionEvaluator` (already supports `targetType='file'`).

## Out of scope

- tus upload endpoint (A4.9 separate)
- ADMIN-only purge (separate)
- file_versions create/restore (A5)
- frontend changes
- folder/* code (other active session owns folder delete/restore)

## Endpoints

| Verb   | Path                               | Status | Permission                                                                |
|--------|------------------------------------|--------|---------------------------------------------------------------------------|
| PATCH  | `/api/files/{id}`                  | 200    | `hasPermission(#id, 'file', 'EDIT')`                                      |
| POST   | `/api/files/{id}/move`             | 200    | `hasPermission(#id, 'file', 'MOVE') and hasPermission(#req.targetFolderId, 'folder', 'EDIT')` |
| DELETE | `/api/files/{id}`                  | 204    | `hasPermission(#id, 'file', 'DELETE')`                                    |
| POST   | `/api/files/{id}/restore`          | 200    | `hasPermission(#id, 'file', 'DELETE')`                                    |

`targetFolderId` is required (NOT NULL) for files — files always live under a folder, no "root file".

## New files

- `file/FileNotFoundException.java` (extends `ResourceNotFoundException`)
- `file/FileNameConflictException.java` (RuntimeException)
- `file/dto/FileDto.java` (wire response)
- `file/dto/RenameFileRequest.java` (`{ name }`)
- `file/dto/MoveFileRequest.java` (`{ targetFolderId }`)
- `file/FileMutationService.java` — `rename`, `move`, `delete`, `restore`
- `file/FileController.java` — 4 endpoints
- `test/file/FileMutationServiceTest.java` (Testcontainers + V5 schema)
- `test/file/FileControllerTest.java` (direct invocation)

## Modified files

- `file/FileRepository.java` — add `lockByIdAndDeletedAtIsNull`,
  `existsActiveByFolderAndNormalizedName{,ExcludingId}`, `findByIdAndDeletedAtIsNotNull` (restore).
- `common/error/GlobalExceptionHandler.java` — add `FileNameConflictException → 409 RENAME_CONFLICT`.

## Mutation semantics

- **rename**: lock active file, normalize new name, no-op when normalized+display unchanged,
  conflict check excluding self in same `folder_id`, audit `FILE_RENAMED`.
- **move**: lock active file, validate target folder is active, conflict check in target folder
  (excluding self), update `folder_id`, audit `FILE_MOVED`. (No cycle check — files have no children.)
- **delete**: lock active file, set `deleted_at = now()`, `purge_after = now() + 30 days`,
  `original_folder_id = folder_id`. Audit `FILE_DELETED`.
- **restore**: find by id INCLUDING deleted, error if already active, validate `original_folder_id`
  is an active folder, conflict check in `original_folder_id`, set `folder_id = original_folder_id`,
  clear `deleted_at`/`purge_after`/`original_folder_id`. Audit `FILE_RESTORED`.

## Invariants honored (CLAUDE.md §3)

- §3.6 (DB constraint = truth): native query mirroring V5 `idx_files_unique_name` partial index;
  catch `DataIntegrityViolationException` as fallback guard at INSERT/UPDATE.
- §3.7 (transaction + SELECT FOR UPDATE on mutations): all mutations `@Transactional`, use
  `lockByIdAndDeletedAtIsNull` at entry.
- §3.8 (audit_log append-only): `AuditService.record` runs in `REQUIRES_NEW`, survives biz rollback.
- §3.10 (backend re-validation): `@PreAuthorize` SpEL via `IbizDrivePermissionEvaluator`.
