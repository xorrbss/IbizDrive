# Folders / Files Module

## Purpose

Backend persistence foundation for A3 folder/file mutation and A4 upload finalize.

## Entry Points

- `Folder`
- `Folder.rename`
- `FolderRepository.findActiveById`
- `FolderRepository.lockActiveById`
- `FolderRepository.findActiveSibling`
- `FolderMutationService.create`
- `FolderMutationService.rename`
- `FileItem`
- `FileVersion`
- `FileItemRepository.findActiveById`
- `FileItemRepository.lockActiveById`
- `FileItemRepository.findActiveSibling`

## Invariants

- Active sibling folders must be unique by normalized name, including root folders with `parent_id IS NULL`.
- Active sibling files must be unique by `(folder_id, normalized_name)`.
- Soft-deleted rows keep their names but no longer participate in active uniqueness.
- Delete state requires `deleted_at` and `purge_after` to be both null or both non-null.
- File storage keys live in `file_versions.storage_key`, never in original file names.
- Folder create/rename mutations must take pessimistic lock paths before writes and emit audit via `@Audited`.

## Depends On

- `users.id` for ownership.
- `docs/02-backend-data-model.md` §2.3~§2.5 and §6.
- `NormalizeUtil` for caller-side normalized name generation.
- A2 audit AOP (`@Audited`) for mutation event emission.

## Change Impact

- Changing uniqueness rules requires migration updates, repository tests, and docs/02 synchronization.
- Changing folder create/rename behavior requires service tests plus controller/error mapping review.
- Upload finalize in A4 depends on these repositories for folder/file row locking and conflict checks.
