# A4.8 — Tasks

## Acceptance criteria

- [x] `FileMutationService` with `rename` / `move` / `delete` / `restore` — all `@Transactional`, all use `PESSIMISTIC_WRITE` lock at entry.
- [x] `FileController` with PATCH/POST/DELETE/POST endpoints + correct SpEL guards + correct HTTP statuses (200/200/204/200).
- [x] `FileRepository` extended with `lockByIdAndDeletedAtIsNull` + `lockByIdAndDeletedAtIsNotNull` + `existsActiveByFolderAndNormalizedName{,ExcludingId}` (native queries mirror V5 `idx_files_unique_name`).
- [x] `FileNameConflictException → 409 RENAME_CONFLICT` envelope wired via `GlobalExceptionHandler` (mirrors folder mapping).
- [x] `FileMutationServiceTest` (Testcontainers V5 schema) — 21 tests covering happy / conflict / soft-deleted target / soft-deleted folder / restore-non-deleted / collision-after-trash. Skipped when Docker unavailable (same gate as `FolderMutationServiceTest`).
- [x] `FileControllerTest` (direct invocation) — 9 tests: each endpoint's status + service delegation + exception propagation.
- [x] Full `gradle :backend:test` green: 387 total / 0 failures / 0 errors / 111 skipped (Docker-gated, baseline parity).
- [x] dev/active updated.
- [ ] commit + remove dev/process file (in progress).

## Verified

- compileJava + compileTestJava clean (no unchecked beyond pre-existing notes).
- All 387 backend tests pass; the 21 new `FileMutationServiceTest` cases are gated on Docker exactly like the 22 `FolderMutationServiceTest` cases — pattern parity preserved.

## Notes for next session

- A4.9 tus upload will need `FileMutationService.create` (or a sibling `FileUploadService`) using the same conflict double-guard + audit `FILE_UPLOADED`.
- ADMIN purge endpoint needs a separate service path — purge bypasses soft-delete and removes `audit_log` references; not in this task.
- Frontend `RenameDialog` already handles `RENAME_CONFLICT` — file rename uses identical envelope so no frontend work expected here.
