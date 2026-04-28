---
Last Updated: 2026-04-28
---

# A3 Mutation — Plan

## Summary

Implement the folder/file mutation foundation required before A4 upload: schema, JPA domain entities, repositories with row locks, then transactional create/rename/move/soft-delete services and controllers.

## Current State

- A1/A2 auth and audit backbone exist.
- A3 permission seam exists in `com.ibizdrive.security`.
- `folders`, `files`, and `file_versions` tables are not present on the branch.
- No folder/file JPA entities, repositories, or mutation services exist.

## Target State

- DB schema enforces sibling-name uniqueness for active folders/files and soft-delete invariants.
- JPA entities map `folders`, `files`, and `file_versions` with Hibernate validate.
- Repositories expose active lookup and pessimistic lock entry points.
- Mutation services use `@Transactional` and `SELECT FOR UPDATE` paths before changing rows.
- Mutation endpoints use `@PreAuthorize("hasPermission(...)")` and emit audit events.

## Phases

1. Domain schema + entities + repositories.
2. Folder create/rename.
3. File rename/move/soft delete.
4. Folder move/soft delete recursion.
5. Controllers + error mapping.
6. Verification + docs/spec sync.

## Acceptance Criteria

- Partial unique indexes permit tombstoned duplicates but reject active sibling duplicates.
- Root folders cannot duplicate active names even though `parent_id` is nullable.
- Repository lock methods are annotated for pessimistic write.
- Services do not fake permissions or bypass `PermissionService`.
- Mutation writes include audit events.
- Relevant backend tests pass in a normal Gradle environment.

## Verification Gate

- Targeted repository/service tests first.
- Then `cd backend && .\gradlew.bat test --tests "com.ibizdrive.folder.*" --tests "com.ibizdrive.file.*"`.
- If local sandbox blocks Gradle cache deletion, use focused `javac`/manual checks and record the blocker.

## Risks

- Local sandbox currently prevents Gradle from deleting cache temp files, so Gradle may fail before executing tests.
- Folder root uniqueness requires a `COALESCE(parent_id, zero_uuid)` index instead of the older docs snippet's plain `(parent_id, normalized_name)`.
