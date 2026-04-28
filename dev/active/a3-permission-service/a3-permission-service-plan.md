---
Last Updated: 2026-04-28
---

# A3 Permission Service — Plan

## Summary

Implement the backend permission evaluation seam promised by docs/03 §3: a `PermissionService` backed by the current system role model, plus Spring Security `hasPermission(...)` support for future controllers.

## Current State

- docs/03 §3 already defines the permission enum, preset matrix, role defaults, and `@PreAuthorize("hasPermission(...)")` pattern.
- Backend has `Role` and authenticated `IbizDriveUserDetails`, but no `PermissionService`, no permission enum class, and no method-security permission evaluator.
- Resource-level folder/file permission rows do not exist yet, so this phase must not invent a fake permission table.

## Target State

- Backend exposes a typed `Permission` enum matching docs/03 §3.1.
- `PermissionService` is the single role-level permission decision point.
- Spring method security supports `hasPermission(#id, 'folder', 'READ')`.
- `ADMIN` has all permissions, including `PURGE`.
- `AUDITOR` has `READ` only.
- `MEMBER` has no role-level permissions until resource-level grants are implemented.
- Unknown principals, unknown target types, and unknown permission strings fail closed.

## Phases

1. RED: add focused tests for role permissions and method-security `hasPermission`.
2. GREEN: add `Permission`, `PermissionService`, `IbizDrivePermissionEvaluator`, and method-security wiring.
3. REFACTOR: keep names and packages aligned with existing auth/security code.
4. VERIFY: run targeted backend tests, then broader backend regression if feasible.
5. SYNC: update task/context/progress docs with results and remaining deferred work.

## Acceptance Criteria

- Tests prove `ADMIN` can all, `AUDITOR` can only `READ`, and `MEMBER` has no role-level grant.
- Tests prove `hasPermission(id, type, permission)` delegates through Spring method security.
- Tests prove invalid permission values deny instead of allowing or throwing out to callers.
- No new DB table, migration, or resource inheritance fake is introduced.
- Existing auth tests still pass.

## Risks

- `docs/00` currently labels permission backend as A1.5 while A2 handoff labels the next milestone A3. This task uses the A2 handoff name but keeps scope to permission backend only.
- Resource-level grants remain deferred; controllers that need member access must wait for the real permissions data model.
