---
session_id: 2026-05-10-team-archive-write-enforcement
task: ERR_TEAM_ARCHIVED enforcement (Plan A 라인 follow-on)
last_updated: 2026-05-10
worktree: .claude/worktrees/team-archive-write-enforcement
branch: feat/team-centric-pivot-team-archive-write-enforcement
---

# Working files (write owner — this session only)

backend:
  - backend/src/main/java/com/ibizdrive/team/TeamArchivedException.java         # NEW
  - backend/src/main/java/com/ibizdrive/team/TeamArchiveGuard.java               # NEW (helper)
  - backend/src/main/java/com/ibizdrive/common/error/GlobalExceptionHandler.java # mapping add
  - backend/src/main/java/com/ibizdrive/folder/FolderMutationService.java        # guards
  - backend/src/main/java/com/ibizdrive/file/FileMutationService.java            # guards
  - backend/src/main/java/com/ibizdrive/file/FileUploadService.java              # guards
  - backend/src/main/java/com/ibizdrive/file/FileVersionMutationService.java     # guards

backend tests:
  - backend/src/test/java/com/ibizdrive/folder/FolderArchivedTeamGuardTest.java  # NEW
  - backend/src/test/java/com/ibizdrive/file/FileArchivedTeamGuardTest.java      # NEW
  - backend/src/test/java/com/ibizdrive/file/FileUploadArchivedTeamGuardTest.java # NEW

docs:
  - docs/02-backend-data-model.md  # §8 drop "예약" marker
  - docs/progress.md               # session entry
  - dev/active/team-archive-write-enforcement/*.md

# NOT touched (other sessions own / out of scope this task)
  - backend/.../permission/PermissionService.java      (Plan C — already merged)
  - backend/.../move/CrossWorkspaceMoveService.java    (Plan D PR #138 — follow-on)
  - frontend/**                                        (Plan B PR #139 — toast wiring follow-on)
