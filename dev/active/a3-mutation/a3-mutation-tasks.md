---
Last Updated: 2026-04-29
---

# A3 Mutation Tasks

## Phase Status

| Phase | Status |
|---|---|
| Dev Docs bootstrap | done |
| Domain schema RED | done |
| Domain schema GREEN | done |
| Repository locks | done |
| Folder create/rename service RED | done via focused compile contract |
| Folder create/rename service GREEN | done |
| Mutation services | in progress: next file rename/move/soft-delete |
| Controllers/errors | pending |
| Verification/docs sync | done for folder create/rename |

## Checklist

- [x] Create active Dev Docs.
- [x] Create process ownership file.
- [x] RED: repository tests for folder/file uniqueness and soft-delete behavior.
- [x] GREEN: V5 migration for folders/files/file_versions.
- [x] GREEN: `Folder`, `FileItem`, `FileVersion` entities.
- [x] GREEN: repositories with active lookup and pessimistic lock methods.
- [x] Run focused compile verification for foundation.
- [x] Run Gradle targeted tests for `FolderMutationServiceTest`.
- [x] RED: `FolderMutationServiceTest` for create/rename locks, normalization, conflict/not-found errors, and `@Transactional`/`@Audited` annotations.
- [x] GREEN: `FolderMutationService`, folder rename mutator, `FolderNotFoundException`, `FolderNameConflictException`.
- [x] Focused fallback verification for service/test.
- [x] Update docs/spec/progress for folder create/rename.

## References

- `docs/02-backend-data-model.md` sections 2.3-2.5
- `docs/02-backend-data-model.md` sections 6.3-6.5
- `docs/02-backend-data-model.md` sections 7.5-7.6
- `docs/02-backend-data-model.md` section 8

## Verification

- `cd backend && .\gradlew.bat test --tests "com.ibizdrive.folder.FolderMutationServiceTest"` PASS in the clean worktree.
