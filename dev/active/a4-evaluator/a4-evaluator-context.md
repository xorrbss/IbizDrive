# context — a4-evaluator (구현 완료 2026-04-29)

## 입력 산출물 (master @ 22b4f00)
- V5 schema: `backend/src/main/resources/db/migration/V5__folders_files_permissions.sql`
  - folders / files / permissions 3-테이블. folders는 `parent_id` 자기참조 (LTREE 아님 — 재귀 CTE는 parent_id 추적).
  - permissions: (resource_type, resource_id, subject_type, subject_id) UNIQUE.
- Permission 도메인:
  - `Permission` enum (9-value): READ, UPLOAD, EDIT, MOVE, DOWNLOAD, DELETE, SHARE, PERMISSION_ADMIN, PURGE.
  - `Preset` enum (5-value): READ/UPLOAD/EDIT/SHARE/ADMIN. `ADMIN`은 PURGE를 포함하지 않음 (Preset.java:51).
  - `PermissionRow` (JPA entity for `permissions` table).
  - `PermissionRepository.findEffective(userId, resourceType, resourceId)` — 재귀 CTE 이미 구현됨
    (PermissionRepository.java:45-86): folder ancestors, file→folder_id 시작, soft-delete 제외, everyone subject,
    expires_at 검사 모두 포함.
- A3 산출물:
  - `PermissionService` — role-based `check`, `effectivePermissions`, `changeRole`. 본 세션에서 read-only.
  - `IbizDrivePermissionEvaluator` — 본 세션에서 내부 교체 완료.
  - `PermissionDenyContext` — ThreadLocal로 deny 사유 기록.

## 구현 결과 (2026-04-29)

### 신규 파일
1. `backend/src/main/java/com/ibizdrive/permission/PermissionResolver.java`
   - 역할: row → preset → permission union → contains 검사.
   - 의존: `PermissionRepository.findEffective(...)` (재귀 CTE 캡슐화 활용).
   - 시그니처: `boolean isGranted(UUID userId, String resourceType, UUID resourceId, Permission required)`.
2. `backend/src/test/java/com/ibizdrive/permission/PermissionResolverTest.java` (Mockito 단위, 6 case).
3. `backend/src/test/java/com/ibizdrive/permission/IbizDrivePermissionEvaluatorTest.java` (Mockito 단위, 11 case).

### 변경 파일
1. `backend/src/main/java/com/ibizdrive/permission/IbizDrivePermissionEvaluator.java`
   - 의존 추가: `PermissionResolver`.
   - `permissionService.check(...)` 호출 제거 — 평가 로직이 evaluator 내부로 이동.
   - 평가 순서: ROLE → resource (folder/file + UUID) → deny + DenyContext.record.
2. `backend/src/test/java/com/ibizdrive/permission/PermissionEvaluatorIntegrationTest.java`
   - `@MockBean PermissionResolver` 추가 — `@WebMvcTest` 슬라이스에 JPA bean 미존재 보완.
   - 모든 A3 시나리오의 targetId가 비-UUID("abc")라 resolver는 호출되지 않음 (회귀 무영향).

## ADR 정합
- ADR #26 (SpEL 시그니처 보존): 호출처(`TestPermissionController` 등) 변경 0건. grep 검증.
  → A4 deferred 항목 close 가능.
- ADR #28 (deny semantics 이월): grant-only union 평가. 명시 deny 미지원.
- ADR #29 (file_versions deferred): 무관.

## 검증 결과
- `./gradlew test`: 294 tests / 0 failures / 0 errors.
- A3 회귀 가드 `PermissionEvaluatorIntegrationTest`: 10/10 그린.
- A4.3 신규 `IbizDrivePermissionEvaluatorTest`: 11/11.
- A4.3 신규 `PermissionResolverTest`: 6/6.

## 남은 리스크
- A4.4 `permissions` CRUD endpoint (세션 3 소관) 도입 전까지 resource-level grant는 DB 직접 INSERT만 가능.
  evaluator는 이미 동작 — endpoint 머지 후 E2E grant→hasPermission 흐름 검증.
- A4.5에서 Folder JPA 엔티티 도입 시 `findEffective` native query는 수정 불필요 (테이블 직접 참조).
