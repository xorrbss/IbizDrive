---
Last Updated: 2026-04-29
Status: 🟡 IMPLEMENTATION DONE (commit/PR 대기)
---

# A4-perm-endpoint — Tasks

## 단위

### U1 — Events (record class 2개)
- [ ] `PermissionGrantedEvent` (record): actorId, resourceType, resourceId, subjectType, subjectId(nullable), preset, expiresAt(nullable), permissionId
- [ ] `PermissionRevokedEvent` (record): actorId, permissionId, resourceType, resourceId, subjectType, subjectId(nullable), preset, expiresAt(nullable) (snapshot of revoked row)
- AC: 컴파일 GREEN, immutable, null 가드 (record validator)

### U2 — Service 확장
- [ ] `PermissionService.grantPermission(resourceType, resourceId, subjectType, subjectId, preset, expiresAt, actorId): UUID` (returns permissionId)
- [ ] `PermissionService.revokePermission(permissionId, actorId)`
- [ ] `PermissionService.canRevokePermission(permissionId, currentUser)` — MVP: ADMIN role only. SpEL bean 호출용.
- [ ] 입력 검증: subject_id ↔ everyone, expiresAt past 검사, preset enum
- [ ] DB unique violation → `PermissionConflictException` (RuntimeException) — GlobalExceptionHandler 매핑
- AC: 단위 테스트 GREEN, A3 changeRole 회귀 0

### U3 — DTOs
- [ ] `dto/GrantPermissionRequest` (record) — `subject: SubjectRef`, `preset: String`, `expiresAt?: Instant`
- [ ] `dto/PermissionDto` — `id`, `resourceType`, `resourceId`, `subjectType`, `subjectId`, `preset`, `grantedBy`, `expiresAt`, `createdAt`
- AC: Jackson 직렬화 wire format (lower-case enum)

### U4 — Controller
- [ ] `PermissionController` `@RestController`
- [ ] `POST /api/{resource}/{id}/permissions` — `@PreAuthorize("hasPermission(#id, #resource, 'PERMISSION_ADMIN')")` + 201 + body
- [ ] `DELETE /api/permissions/{permissionId}` — `@PreAuthorize("@permissionService.canRevokePermission(#permissionId, principal)")` + 204
- [ ] resource_type validation (`folder`|`file`) + `:id` exists 검사 (404)
- [ ] folder existence: `JdbcTemplate` 직접 query
- [ ] file existence: `FileItemRepository`
- AC: slice test (`@WebMvcTest`) GREEN — 200/204/403/404/409

### U5 — Listener 확장
- [ ] `PermissionAuditListener.onPermissionGranted(@EventListener PermissionGrantedEvent)` → AuditService.record(PERMISSION_GRANTED) — before=null, after JSON
- [ ] `PermissionAuditListener.onPermissionRevoked(@EventListener PermissionRevokedEvent)` → AuditService.record(PERMISSION_REVOKED) — before JSON, after=null
- [ ] target_type = `permission`, target_id = `permissionId`
- [ ] 실패 swallow (ERROR 로그) — A3 패턴 동일
- AC: listener unit test GREEN

### U6 — Errors / docs / mirror
- [ ] `GlobalExceptionHandler` 에 `PermissionConflictException` 매핑 (409 PERMISSION_CONFLICT)
- [ ] `frontend/src/lib/errors.ts` 에 PERMISSION_CONFLICT 추가
- [ ] `docs/02-backend-data-model.md` §7.10 본문 patch — request body shape `permissions: Permission[]` → `preset: Preset`, errors `400, 403, 404` → `400, 403, 404, 409`
- [ ] `docs/00-overview.md` ADR #26 status: deferred → closed (commit hash 후속 갱신)
- AC: docs diff 정합

### U7 — Tests
- [ ] `PermissionServiceGrantRevokeTest` (unit, mocked deps) — grant/revoke happy path, 입력 검증, expires past, subject_id 검증
- [ ] `PermissionAuditListenerTest` 확장 — onPermissionGranted/Revoked 검증
- [ ] `PermissionControllerTest` (`@WebMvcTest`) — 201/204/403/404/409 시나리오
- [ ] (시간 허용 시) `PermissionEndpointE2ETest` 확장 — Testcontainers 풀 플로우 (선택)
- AC: 새 테스트 5+개 GREEN, 회귀 0

### U8 — Verify + commit + PR
- [ ] `./gradlew :backend:test` 그린
- [ ] frontend: PERMISSION_CONFLICT mirror만 — typecheck 영향 zero
- [ ] dev-docs-update (이 문서 + plan)
- [ ] commit 1~2건
- [ ] PR `feat(A4): permissions endpoint + granted/revoked emission (ADR #26 close)`

## 진행 상태

| 단위 | 상태 |
|---|---|
| U1 | ✅ |
| U2 | ✅ |
| U3 | ✅ |
| U4 | ✅ |
| U5 | ✅ |
| U6 | ✅ (frontend errors.ts mirror — 파일 미존재로 skip, A4-controllers 후속 세션에서 진입 시 추가) |
| U7 | ✅ (PermissionEvaluatorIntegrationTest @MockBean PermissionRepository 추가 — A3 슬라이스 회귀 가드) |
| U8 | ⏳ (commit/PR pending) |
