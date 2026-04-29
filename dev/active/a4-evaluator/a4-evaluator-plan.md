# A4.3 — IbizDrivePermissionEvaluator: resource-level + folder inheritance

## 목표
A3에서 user-level (Role 기반) 평가만 하던 `IbizDrivePermissionEvaluator` 내부를
resource-level grant + 폴더 재귀 상속 평가로 교체한다. SpEL 호출 시그니처
`hasPermission(#id, 'folder'|'file', '<PERMISSION>')`는 보존 (ADR #26).

## 비목표 (boundary)
- PermissionService.java 본문 변경 — A3 계약 유지 (read-only)
- PermissionController.java 신규 — 세션 3 (a4-perm-endpoint) 영역
- PermissionAuditListener.java 변경 — 세션 3 영역
- folder/Folder.java 신규 — A4.5 책임 (deferred)
- DB 스키마 변경 — V5 그대로 사용

## 핵심 설계
1. **PermissionResolver (신규)** — `permission` 패키지 내부 헬퍼.
   - 시그니처: `boolean isGranted(UUID userId, String resourceType, UUID resourceId, Permission required)`
   - 구현: `PermissionRepository.findEffective(...)` 호출 → `List<PermissionRow>` →
     각 row의 `preset` (lower-case wire) → `Preset.from()` → `preset.permissions()` →
     union → `required ∈ union` 검사.
   - 재귀 CTE/상속/만료/soft-delete/ everyone subject 처리는 이미 `findEffective` 안에 구현됨
     (A4-data 산출물). 본 헬퍼는 preset → permission 평면화만 담당.

2. **IbizDrivePermissionEvaluator 분기 (변경)**
   - 기존 인증/principal 추출/Permission 파싱 보존.
   - 평가 순서:
     1. ROLE 경로 (A3 보존): `permissionService.effectivePermissions(role).contains(required)` → grant.
        - ADMIN: all 9 (PURGE 포함). AUDITOR: READ. MEMBER: 없음.
     2. ROLE deny + `resourceType ∈ {folder, file}` + `targetId`가 UUID 파싱 가능 → Resolver 호출.
     3. 그 외 (비-folder/file, 또는 UUID 아님) → deny.
   - Deny 시 `PermissionDenyContext.record(required, role, effectivePermissions(role))` — 기존 로직 유지.

## 핵심 원칙 정합 (CLAUDE.md §3)
- §3 원칙 6 (DB 제약이 진실): `findEffective`가 V5 UNIQUE/CHECK 위에서 동작.
- §3 원칙 10 (백엔드 재검증): evaluator는 SpEL을 통해 controller 진입 전 차단.
- §3 원칙 11 (프론트/백엔드 정규화): 무관.
- ADR #26 (SpEL 시그니처 보존): 호출자 변경 0건.

## 회귀 가드
- A3 9개 통합 테스트 그린 유지 (admin canRead, member 403 envelope, PURGE × hasRole, 등).
- PURGE × hasPermission 차단: 어떤 preset에도 PURGE 없음 (Preset.java line 51).
  → 비-ADMIN이 admin preset grant 받아도 PURGE deny 보장.

## 단위 분할
- 단위 1: PermissionResolver 신설 + 단위 테스트 (Mockito로 PermissionRepository mock).
- 단위 2: IbizDrivePermissionEvaluator 분기 변경 + 기존 통합 테스트 그린 확인.
- 단위 3: Resolver 슬라이스 테스트 — 직접 grant, 부모 상속 깊이 3+, 권한 없음, 만료, everyone.
  Testcontainers 기반 (`@DataJpaTest` 또는 PermissionRepositoryTest 패턴 재사용).

## 검증
- `./gradlew test` (모든 모듈)
- A3 회귀 가드: `PermissionEvaluatorIntegrationTest` 9건 그린.
- 신규 가드: `PermissionResolverTest` (단위 + 슬라이스), `IbizDrivePermissionEvaluatorTest` (분기 단위).

## acceptance criteria
- [ ] `hasPermission(uuid, 'folder', 'READ')` — 직접 grant → grant
- [ ] `hasPermission(uuid, 'file', 'EDIT')` — 부모 폴더 깊이 3+ 상속 → grant
- [ ] `hasPermission(uuid, 'folder', 'READ')` — grant 부재 + MEMBER → deny
- [ ] `hasPermission(uuid, 'folder', 'PURGE')` — admin preset 보유 + non-ADMIN → deny (PURGE 차단)
- [ ] `hasRole('ADMIN')` 경로 무영향 — A3 9개 통합 테스트 그린
- [ ] SpEL 호출처 변경 0건 (grep로 검증)
