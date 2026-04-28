---
Last Updated: 2026-04-29
---

# A3 Mutation Context

## SESSION PROGRESS

- 2026-04-28: Started after user said "go". Scope set to A3 Mutation because A4 depends on it.
- 2026-04-28: Completed first slice:
  - V5 schema for `folders`, `files`, `file_versions`.
  - `Folder`, `FileItem`, `FileVersion` entities.
  - `FolderRepository`, `FileItemRepository`, `FileVersionRepository`.
  - Repository tests for active duplicate rejection and soft-deleted duplicate allowance.
  - docs/02 folder unique index updated to use `COALESCE(parent_id, zero_uuid)` so root duplicates are blocked.
- 2026-04-29: Ported the folder create/rename service slice into a clean worktree based on `origin/codex/a3-mutation-domain`:
  - Added `FolderMutationService#create/rename`, `Folder.rename`, `FolderNotFoundException`, and `FolderNameConflictException`.
  - `create` locks a non-null parent, normalizes display/dedup names through `NormalizeUtil`, rejects active sibling conflicts, saves with generated UUID, and emits `FOLDER_CREATED` via `@Audited`.
  - `rename` locks the source folder, rejects conflict with another active sibling while allowing self, updates name/normalizedName/slug/updatedAt, saves, and emits `FOLDER_RENAMED` via `@Audited`.
  - Added `FolderMutationServiceTest` for lock order, normalization, conflict/not-found errors, save behavior, and `@Transactional`/`@Audited` contracts.

## Current Execution Contract

- Active task: implement A3 Mutation foundation first.
- First slice status: implemented and focused-compiled.
- Current slice: transactional folder create/rename service is implemented and targeted Gradle verification passed in the clean worktree.
- TDD required: tests first, then minimal implementation.
- Do not implement A4 tus yet.
- Next implementation slice should be file rename/move/soft-delete.

## Read Order

1. `a3-mutation-plan.md`
2. `a3-mutation-tasks.md`
3. `docs/02-backend-data-model.md` sections 2.3-2.5, 6.3-6.5, 7.5-7.6, 8
4. Existing patterns: `User`, `UserRepositoryTest`, audit service tests

## Key Files

| File | Role |
|---|---|
| `backend/src/main/resources/db/migration/V5__folders_files.sql` | A3 domain schema |
| `backend/src/main/java/com/ibizdrive/folder/*` | Folder entity/repository/mutation service |
| `backend/src/main/java/com/ibizdrive/folder/FolderMutationService.java` | Transactional folder create/rename application service |
| `backend/src/test/java/com/ibizdrive/folder/FolderMutationServiceTest.java` | Folder create/rename service contract tests |
| `backend/src/main/java/com/ibizdrive/file/*` | File and version entities/repositories |
| `backend/src/test/java/com/ibizdrive/folder/*` | Folder repository/service tests |
| `backend/src/test/java/com/ibizdrive/file/*` | File repository tests |

## Decisions

- Use separate `folder` and `file` packages to match existing domain package style (`user`, `audit`, `auth`).
- Keep storage/upload behavior out of this first slice.
- Use database constraints as the source of truth for sibling uniqueness.
- Folder create/rename emits audit via the existing `@Audited` AOP annotation, not direct `AuditService` calls, to keep the A2 ADR #24 pattern.
- Service-level errors use domain exceptions first; controller/error-envelope mapping belongs to the later controllers/errors phase.
- `Folder.updatedAt` is writable by JPA for mutation updates; `createdAt` remains DB-default/read-only.

## Fast Resume

Start with:

```powershell
cd backend
.\gradlew.bat test --tests "com.ibizdrive.folder.FolderMutationServiceTest"
```

Expected next steps:
1. Continue with file rename/move/soft-delete mutation service.
2. Controller/error-envelope mapping is still pending for `FolderNotFoundException` and `FolderNameConflictException`.
