---
Last Updated: 2026-04-28
---

# A3 Mutation — Context

## SESSION PROGRESS

- 2026-04-28: Started after user said "go". Scope set to A3 Mutation because A4 depends on it.
- 2026-04-28: Completed first slice:
  - V5 schema for `folders`, `files`, `file_versions`
  - `Folder`, `FileItem`, `FileVersion` entities
  - `FolderRepository`, `FileItemRepository`, `FileVersionRepository`
  - repository tests for active duplicate rejection and soft-deleted duplicate allowance
  - docs/02 folder unique index updated to use `COALESCE(parent_id, zero_uuid)` so root duplicates are blocked

## Current Execution Contract

- Active task: implement A3 Mutation foundation first.
- First slice status: implemented and focused-compiled.
- Next slice: transactional folder create/rename service.
- TDD required: tests first, then minimal implementation.
- Do not implement A4 tus yet.

## Read Order

1. `a3-mutation-plan.md`
2. `a3-mutation-tasks.md`
3. `docs/02-backend-data-model.md` §2.3~§2.5, §6.3~§6.5, §7.5~§7.6, §8
4. Existing patterns: `User`, `UserRepositoryTest`, audit service tests

## Key Files

| File | Role |
|---|---|
| `backend/src/main/resources/db/migration/V5__folders_files.sql` | A3 domain schema |
| `backend/src/main/java/com/ibizdrive/folder/*` | Folder entity/repository |
| `backend/src/main/java/com/ibizdrive/file/*` | File and version entities/repositories |
| `backend/src/test/java/com/ibizdrive/folder/*` | Folder repository tests |
| `backend/src/test/java/com/ibizdrive/file/*` | File repository tests |

## Decisions

- Use separate `folder` and `file` packages to match existing domain package style (`user`, `audit`, `auth`).
- Keep storage/upload behavior out of this first slice.
- Use database constraints as the source of truth for sibling uniqueness.

## Fast Resume

Start with:

```bash
cd backend
.\gradlew.bat test --tests "com.ibizdrive.folder.*" --tests "com.ibizdrive.file.*"
```
