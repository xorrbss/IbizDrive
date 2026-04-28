---
Last Updated: 2026-04-28
---

# A3 Mutation — Tasks

## Phase Status

| Phase | Status |
|---|---|
| Dev Docs bootstrap | done |
| Domain schema RED | in progress |
| Domain schema GREEN | done |
| Repository locks | pending |
| Mutation services | pending |
| Controllers/errors | pending |
| Verification/docs sync | pending |

## Checklist

- [x] Create active Dev Docs.
- [ ] Create process ownership file.
- [ ] RED: repository tests for folder/file uniqueness and soft-delete behavior.
- [x] GREEN: V5 migration for folders/files/file_versions.
- [x] GREEN: `Folder`, `FileItem`, `FileVersion` entities.
- [x] GREEN: repositories with active lookup and pessimistic lock methods.
- [x] Run focused compile verification.
- [~] Run Gradle targeted tests — blocked by sandbox deletion policy on Gradle cache temp files.
- [ ] Update docs/spec/progress.

## References

- `docs/02-backend-data-model.md` §2.3~§2.5
- `docs/02-backend-data-model.md` §6.3~§6.5
- `docs/02-backend-data-model.md` §7.5~§7.6
- `docs/02-backend-data-model.md` §8

## Verification

- `cd backend && .\gradlew.bat test --tests "com.ibizdrive.folder.*" --tests "com.ibizdrive.file.*"`
