---
Last Updated: 2026-04-28
---

# A3 Permission Service — Context

## SESSION PROGRESS

- 2026-04-28: User approved proceeding after confirming A3 permission code was not present on `master`.
- 2026-04-28: Implemented backend permission seam:
  - `Permission` enum (9 docs/03 values)
  - `PermissionService` role-level checks
  - `IbizDrivePermissionEvaluator`
  - `MethodSecurityConfig` with `hasPermission(...)`
  - `-parameters` Java compile flag so docs/03 `#id` SpEL works
  - focused tests for service matrix and method security delegation
  - frontend `types/permission.ts` mirror for docs/03/backend enum
  - minimal `docs/specs` bootstrap for the new authorization public surface

## Current Execution Contract

- Status: implemented, manual verification passed.
- Scope completed: role-level permission checks only; no fake resource permission storage.
- A4 implementation status: blocked until docs/00 §4.4 A3 Mutation folder/file domain is present on the working branch.
- Gradle blocker: local Gradle execution cannot complete in this sandbox because Gradle attempts to delete cache temp files and the policy denies deletion. Verification used `javac` + a manual Spring method-security runner instead.

## Read Order

1. `a3-permission-service-plan.md`
2. `a3-permission-service-tasks.md`
3. `docs/03-security-compliance.md` §3
4. Existing auth/security classes: `SecurityConfig`, `Role`, `IbizDriveUserDetails`

## Key Files

| File | Role |
|---|---|
| `backend/src/main/java/com/ibizdrive/config/SecurityConfig.java` | Add method security config if needed |
| `backend/src/main/java/com/ibizdrive/user/Role.java` | Existing system roles |
| `backend/src/main/java/com/ibizdrive/user/IbizDriveUserDetails.java` | Authenticated principal |
| `backend/src/main/java/com/ibizdrive/security/*` | New permission layer |

## Decisions

- Use `com.ibizdrive.security` for permission classes to avoid mixing authentication and authorization responsibilities.
- Deny by default for unsupported principal, target type, or permission value.
- Keep `MEMBER` role-level permissions empty until the real resource permission model exists.
- Add `-parameters` to backend Java compilation. Without it, `@PreAuthorize("hasPermission(#id, ...)")` cannot resolve `#id`, and method security denies even ADMIN.

## Fast Resume

Run targeted tests from `backend/`:

```bash
.\gradlew.bat test --tests "com.ibizdrive.security.*"
```

If Gradle cache deletion remains blocked in the local sandbox, the fallback verification used in this session was:

```bash
# compile focused main/test classes with cached Gradle jars and -parameters
# run a focused manual Spring method-security runner if Gradle remains blocked
```
