# 2026-04-28 A3 FolderMutationService Slice

task: A3 folder create/rename mutation service
last_updated: 2026-04-28

## Working Files

- backend/src/main/java/com/ibizdrive/folder/Folder.java
- backend/src/main/java/com/ibizdrive/folder/FolderMutationService.java
- backend/src/main/java/com/ibizdrive/folder/FolderNotFoundException.java
- backend/src/main/java/com/ibizdrive/folder/FolderNameConflictException.java
- backend/src/test/java/com/ibizdrive/folder/FolderMutationServiceTest.java
- dev/active/a3-mutation/a3-mutation-context.md
- dev/active/a3-mutation/a3-mutation-tasks.md
- docs/progress.md
- docs/specs/core/modules/folders-files.md
- docs/specs/index/symbols.md
- docs/specs/index/symbols.json
- docs/specs/coverage.yaml

## Notes

- Scope is folder create/rename only.
- Controller/error-envelope mapping stays in the later A3 controllers/errors phase.
- Legacy repo Gradle had been blocked locally before test execution; after porting to the clean worktree, targeted `FolderMutationServiceTest` passed with Gradle.
