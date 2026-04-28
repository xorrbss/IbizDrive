# Authorization Module

## Purpose

Backend authorization entry point for docs/03 §3. It exposes the permission enum, role-level permission checks, and Spring Security `hasPermission(...)` integration.

## Entry Points

- `PermissionService.check(IbizDriveUserDetails, String, String, Permission)`
- `IbizDrivePermissionEvaluator.hasPermission(Authentication, Serializable, String, Object)`
- `@PreAuthorize("hasPermission(#id, 'folder', 'READ')")`
- `frontend/src/types/permission.PermissionCode`

## Invariants

- Authorization fails closed for unsupported principals, target types, missing IDs, and invalid permission strings.
- `ADMIN` has all permissions, including `PURGE`.
- `AUDITOR` has only `READ`.
- `MEMBER` has no role-level grant until resource-level permissions exist.
- Resource-level grants, inheritance, and deny-first CTEs must be added inside `PermissionService`, not bypassing it.
- Java classes must be compiled with `-parameters` so SpEL names such as `#id` resolve.

## Depends On

- `com.ibizdrive.user.Role`
- `com.ibizdrive.user.IbizDriveUserDetails`
- Spring Security method security and `PermissionEvaluator`

## Change Impact

- Changing `Permission` values requires syncing docs/03 §3 and frontend permission mirrors.
- Changing role defaults affects controller/service `@PreAuthorize` behavior and must update `PermissionServiceTest`.
- Adding resource-level permission storage should preserve the existing `hasPermission(...)` expression surface.
