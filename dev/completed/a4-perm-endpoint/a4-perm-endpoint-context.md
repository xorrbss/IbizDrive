---
Last Updated: 2026-04-29
Status: 🟢 ACTIVE
---

# A4-perm-endpoint — Context

## 코드 지도 (A3 패턴 재사용 포인트)

### 이벤트 publish 경로
`PermissionService.changeRole` (A3) → `eventPublisher.publishEvent(new RoleChangedEvent(...))` → `@EventListener PermissionAuditListener.onRoleChanged` → `auditService.record(new AuditEvent(PERMISSION_CHANGED, ...))` (REQUIRES_NEW)

본 세션은 동일 흐름을 PermissionGrantedEvent / PermissionRevokedEvent 로 복제.

### audit_log INSERT 경로
`AuditService.record(AuditEvent)` — REQUIRES_NEW + JdbcTemplate `INSERT ... ::jsonb` 캐스트. 본 세션은 Listener 에서 `before/after` JSON 텍스트만 만들어 호출.

### 403 envelope
`IbizDrivePermissionEvaluator.hasPermission()` 가 false 시 `PermissionDenyContext.record(required, role, have)` → `GlobalExceptionHandler` 가 `{ error: { code: 'PERMISSION_DENIED', details: { required, have } } }` 본문 생성. 본 세션 endpoint 도 동일 경로 자동 사용 (변경 없음).

### `@PreAuthorize` SpEL 패턴
`hasPermission(#id, #resource, 'PERMISSION_ADMIN')` — A3 evaluator 가 ADMIN role bypass.
`@permissionService.canRevokePermission(#permissionId, principal)` — bean 메서드 호출. principal = IbizDriveUserDetails.

## 함정

### F1 — `PermissionRow` setter / id / createdAt
JPA entity 지만 `@GeneratedValue` 미설정 + `@PrePersist` 없음. Service 에서 `setId(UUID.randomUUID())` + `setCreatedAt(Instant.now())` 명시 필요. (DB DEFAULT 도 있지만 JPA `save()` 후 flush 시 NULL 검사 통과 보장 필요 — Instant.now() 설정으로 안전.)

### F2 — ID 충돌
UUID v4 충돌 확률은 무시 가능 — 추가 가드 불필요.

### F3 — `expires_at` 검증
service 단에서 `expiresAt != null && expiresAt.isBefore(Instant.now())` → IllegalArgumentException → 400 BAD_REQUEST.

### F4 — `subject_id == null` ↔ `subject_type == 'everyone'` (DB CHECK)
service 가 입력 검증 (둘 중 하나만 매칭, 둘 다 OR 아님 매칭 시 400).

### F5 — `permission_changed` vs `granted/revoked` 의미
docs/03 §4.1 — `granted` = 새 grant 행 INSERT, `revoked` = 기존 grant 행 DELETE, `changed` = 기존 행의 preset/expiry 변경 (PATCH 미존재 → A5+ 이월). 본 세션은 granted/revoked 만.

### F6 — 중복 `everyone` grant
UNIQUE INDEX 가 `COALESCE(subject_id, ZERO_UUID)` 사용 → everyone (subject_id NULL) 중복도 차단. 동일 (resource, everyone, *) 두 번째 INSERT → 409.

## 외부 참조 (변경 없음)
- `frontend/src/types/permission.ts` — Preset/Permission enum
- `frontend/src/types/audit.ts` — AuditEventType (`permission.granted`/`permission.revoked` 이미 존재)
- `frontend/src/lib/errors.ts` — PERMISSION_CONFLICT 추가 (mirror)

## 검증 명령
```
./gradlew :backend:test --tests "Permission*Test" --tests "PermissionAuditListenerTest" --tests "PermissionControllerTest"
./gradlew :backend:test  # 전체 회귀 (A2/A3 포함)
```
