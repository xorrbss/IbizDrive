# Core Decisions

## Authorization

- 2026-04-28: `PermissionService` is the backend authorization decision point. Method security delegates to it through `IbizDrivePermissionEvaluator`.
- 2026-04-28: Resource-level permission persistence is deferred until folder/file permission storage exists. The current implementation handles system role grants only.
