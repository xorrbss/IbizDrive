---
Last Updated: 2026-04-29
Status: 🟢 ACTIVE
Parent: dev/active/a4-folder-file-domain (A4.4 phase 분리 트랙)
Co-session: a4-evaluator (A4.3) — boundary 엄격
---

# A4-perm-endpoint — Plan

## 단일 책임

**A4.4 의 endpoint + audit emit 부분만**.

- `POST /api/{resource}/{id}/permissions` 신설
- `DELETE /api/permissions/{permissionId}` 신설
- `permission.granted` / `permission.revoked` audit emission 실호출처 도입 → **ADR #26 deferred close**

본 세션 **out of scope**:
- A4.3 (IbizDrivePermissionEvaluator 내부 교체) — 동시 진행 세션 a4-evaluator 책임
- A4.4 의 GET 두 endpoint (list / effective) — audit emit 무관, 후속 세션
- A4.5 folder/file CRUD
- A4.6 통합 E2E
- Folder.java / FileItem.@ManyToOne 승격

## 입력 (origin/master @ 22b4f00)

- `permissions` 테이블 (V5 — UNIQUE (resource_type, resource_id, subject_type, COALESCE(subject_id, ZERO_UUID)) WHERE 절 없음 — 활성/만료 무관 모두 unique)
- `PermissionRow` JPA entity + `PermissionRepository` (find 계층은 native CTE — 본 세션은 INSERT/DELETE만 사용)
- `Preset` enum (read|upload|edit|admin), `Permission` enum 9-value
- `PermissionService.changeRole` (REQUIRED tx + ApplicationEventPublisher → `RoleChangedEvent`)
- `PermissionAuditListener.onRoleChanged` (REQUIRES_NEW via `AuditService.record`)
- `IbizDrivePermissionEvaluator` (A3 user-level — ADMIN role bypass) — read-only

## 핵심 설계 결정

### D1 — REQUIRED tx + 이벤트 publish (A3 changeRole 동형)
service의 grant/revoke 메서드는 default `@Transactional` (REQUIRED). `AuditService.record`가 REQUIRES_NEW 이므로 비즈니스 rollback 시에도 audit row 보존 (ADR #24 재사용). REQUIRES_NEW 를 service 본체에 적용하지 않음 — A3 changeRole 패턴과 일관.

### D2 — POST guard = `hasPermission(#id, #resource, 'PERMISSION_ADMIN')`
A3 evaluator가 ADMIN role bypass → MVP 효과는 ADMIN-only. A4.3 머지 후 동일 SpEL 식이 자동으로 resource-level PERMISSION_ADMIN 체크로 확장 (호출처 보존, ADR #17/#26).

### D3 — DELETE guard = `@permissionService.canRevokePermission(#permissionId, principal)`
docs/02 §7.10 명세. MVP 본체는 ROLE 기반 (ADMIN). 서명/SpEL 호출처는 docs 그대로 → A4.3 evaluator 머지 후 service 내부만 resource-level 로 교체 가능.

### D4 — Folder 엔티티 부재 처리 (resource_type='folder' 케이스)
PathVariable `{resource}` ∈ {folder, file}. `:id` 검증을 위해:
- `file` → `FileItemRepository.existsByIdAndDeletedAtIsNull(id)` (entity 존재)
- `folder` → `JdbcTemplate.queryForObject("SELECT 1 FROM folders WHERE id = ? AND deleted_at IS NULL")` (V5 schema 존재, Folder entity 미의존)

A4.5 에서 Folder entity 도입 시 `JdbcTemplate` 호출만 `FolderRepository.existsByIdAndDeletedAtIsNull` 로 교체 — 서명 보존.

### D5 — request body shape 결정 (preset 단일)
`{ subject: { type: 'user'|'department'|'role'|'everyone', id?: UUID }, preset: 'read'|'upload'|'edit'|'admin', expiresAt?: ISO-8601 }`.

docs/02 §7.10 본문은 `permissions: Permission[]` 표기 — 이는 ADR #28 도입 이전 잔류. 현 entity 는 `preset` 단일 컬럼 → docs/02 §7.10 본문 1줄 정합 patch 필요 (`permissions: Permission[]` → `preset: Preset`). **본 세션에서 동시 patch**.

### D6 — 중복 grant 처리
DB unique violation (`DataIntegrityViolationException`)을 `409 PERMISSION_CONFLICT` 으로 매핑. 새 에러 코드 → `frontend/src/lib/errors.ts` mirror 동기화 + docs/02 §7.10 errors 컬럼에 `409` 추가. 본 세션 working_files 에 frontend errors mirror 1줄 patch 추가.

(대안 — 정확히 동일 (subject, resource, preset) 이면 idempotent 200 — 검토했으나 KISS 위배 + UNIQUE에 preset 미포함이라 서로 다른 preset 충돌 시 결국 별도 에러 필요 → 단순 409 일원화.)

### D7 — `everyone` subject_id null 허용
DB CHECK `(subject_type = 'everyone') = (subject_id IS NULL)`. service 가 검증 + `subject.id` 가 비어있을 수 있게 처리.

## 단위 분할

1. **events**: `PermissionGrantedEvent`, `PermissionRevokedEvent` (record)
2. **service 확장**: `PermissionService.grantPermission` / `revokePermission` / `canRevokePermission`
3. **DTO**: `GrantPermissionRequest`, `PermissionDto`
4. **controller**: `PermissionController` (POST/DELETE)
5. **listener 확장**: `PermissionAuditListener.onPermissionGranted` / `onPermissionRevoked`
6. **errors**: `PERMISSION_CONFLICT` 추가 + frontend mirror + docs/02 §7.10 정합 patch
7. **tests**: service grant/revoke unit, listener granted/revoked unit, controller slice (200/403/404/409)

## DoD

- [ ] POST/DELETE 두 endpoint GREEN (slice + e2e mock)
- [ ] `permission.granted`/`permission.revoked` audit row 생성 검증 (listener test)
- [ ] 중복 grant → 409 PERMISSION_CONFLICT envelope
- [ ] 권한 없음 → 403 PERMISSION_DENIED envelope (A3 GlobalExceptionHandler 재사용)
- [ ] resource_id 부재 → 404 (folder = jdbc, file = repo)
- [ ] A2/A3 회귀 0 (audit append-only / PURGE × hasPermission 차단 / changeRole 동작)
- [ ] docs/02 §7.10 본문 정합 patch (preset + 409)
- [ ] frontend `errors.ts` mirror PERMISSION_CONFLICT
- [ ] ADR #26 status: deferred → closed (A4.4 commit hash + 본 PR hash)
- [ ] PR 생성: `feat(A4): permissions endpoint + granted/revoked emission (ADR #26 close)`

## 리스크

- R1 — A4.3 미머지 상태에서 endpoint 가드가 ADMIN-only 효과 → 의도된 MVP 동작 (D2). 머지 후 자동 확장.
- R2 — docs §7.10 정합 patch 가 다른 세션과 충돌 가능성 — folder/file 패키지 ownership 과 무관 (docs 만), risk 낮음.
- R3 — `PermissionRow` setter 패턴은 mutable — 새 INSERT 시 setId(UUID.randomUUID())+save 필요. createdAt 은 DB DEFAULT NOW() — JPA save 시 PrePersist 미사용이라 createdAt 이 설정 안되면 DB 거부. 검증 필요 (단위 테스트로).
