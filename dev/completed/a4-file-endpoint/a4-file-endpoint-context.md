# A4.8 — Context

## Anchors

- **A4.6** `FolderMutationService` — pattern source for service layer, audit emission, lock policy.
  See `backend/src/main/java/com/ibizdrive/folder/FolderMutationService.java`.
- **A4.7** `FolderController` — pattern source for REST + SpEL gating + DTO envelope.
  See `backend/src/main/java/com/ibizdrive/folder/FolderController.java`.
- **A4.5** completed — `FileItem` entity already has all required columns (`deleted_at`,
  `purge_after`, `original_folder_id`, `normalized_name`).

## Schema (V5) relevant indexes

- `idx_files_unique_name` (partial unique on `(folder_id, normalized_name) WHERE deleted_at IS NULL`).
  Native conflict-check query must mirror this exactly.

## Cross-cutting

- `IbizDrivePermissionEvaluator` already accepts `targetType='file'` and resolves via
  `PermissionResolver` (recursive CTE inheritance from folder).
- `AuditEventType.FILE_{RENAMED,MOVED,DELETED,RESTORED}` already defined.
- `AuditTargetType.FILE` already defined.
- `WebRequestContextHolder` provides IP/UA same as folder service.

## Concurrency notes

- delete/restore both grab `PESSIMISTIC_WRITE` on the row before mutation (same pattern as
  folder rename/move). Restore queries the row with `findByIdAndDeletedAtIsNotNull` (no lock, then
  re-lock by id once we know it exists — but simpler: lock by id including deleted → method
  `lockByIdIncludingDeleted`. We'll add this).

## Other active sessions (ownership)

- `dev/process/20260429-a4-folder-delete-restore.md` owns folder delete/restore — overlapping
  package only at imports, no file overlap.
- `dev/process/20260429-track-b-worktree-prune.md` is git infra cleanup.
