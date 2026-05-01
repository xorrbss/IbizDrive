---
Last Updated: 2026-05-01
Status: 🟡 ACTIVE — A11.0 bootstrap done, A11.1 미진입
---

# A11 — Backend `GET /api/me/effective-permissions?nodeId=` 신설

## 요약

`docs/02 §7.10` line 1173에 명시된 `GET /api/me/effective-permissions?nodeId=` endpoint가 backend
미구현이다. 프론트 `api.getEffectivePermissions` (frontend/src/lib/api.ts:500)는 admin preset 8권한
하드코딩 mock으로 머물러 있고, F2 frontend 실연결 트랙(mock→fetch swap)이 이 endpoint 부재 때문에
시작할 수 없다. 본 트랙은 thin controller + 기존 `PermissionService.effectivePermissions(Role)` +
9× evaluator loop(또는 resolver Set 확장)로 endpoint를 신설한다.

A9(`PR #19`)→F1(`PR #20`)과 동형 페어 — backend 트랙 머지 후 F2가 mock body만 fetch로 1:1 swap.

## 현재 상태 분석

### 이미 존재
- `PermissionService.effectivePermissions(Role) → Set<Permission>` — 역할 단독 평가. ADMIN→9권한, AUDITOR→{READ}, MEMBER→∅
- `IbizDrivePermissionEvaluator.hasPermission(auth, id, type, perm)` — ROLE 경로 + resource-level 경로(PermissionResolver 위임). 권한 1개 단위 평가
- `PermissionResolver.isGranted(userId, type, id, required)` — V5 `permissions` 테이블 + 재귀 CTE 상속/만료/everyone 처리
- `PermissionController` (`@RequestMapping("/api")`) — POST grant / DELETE revoke 보유. `me/effective-permissions` 라우트 부재
- `IbizDriveUserDetails` — `getUser().getId()`/`getRole()` 노출 (AuthenticationPrincipal로 주입 가능)

### 부재
- `GET /api/me/effective-permissions[?nodeId=...]` 라우트
- "user × node → Set<Permission>" 형태 평가 진입점 (현 evaluator는 단일 권한 boolean만)
- 응답 DTO (단순 `{permissions: Permission[]}` 충분)
- 노드 부재(404) 분기 — nodeId 지정 시 folder/file 둘 다 검사 필요 (어느 한쪽만 있어도 OK)

### docs/02 §7.10 표 (line 1173) 명세
- Path: `/api/me/effective-permissions?nodeId=`
- Guard: `isAuthenticated`
- TX: 없음 (read-only)
- SoftDel: `WHERE deleted_at IS NULL`
- Errors: 404 (nodeId 지정 시 노드 미존재)

## 목표 상태

### endpoint 시그니처
```
GET /api/me/effective-permissions
GET /api/me/effective-permissions?nodeId={uuid}

Response 200 application/json:
  { "permissions": ["READ", "UPLOAD", ...] }   // Permission enum wire (UPPER_SNAKE_CASE)

Errors:
  401 — 미인증 (Spring Security가 처리, envelope은 audit/search 트랙과 동일)
  400 — nodeId 형식 위반 (UUID 파싱 실패) — GlobalExceptionHandler IllegalArgumentException 매핑
  404 — nodeId 지정 + folder/file 둘 다 미존재 (또는 둘 다 deleted_at NOT NULL)
```

### 평가 로직
- `nodeId == null` → `permissionService.effectivePermissions(role)` 그대로 반환 (역할 단독)
- `nodeId != null` → 노드 존재 확인 (folder OR file, deleted_at IS NULL) + 9 Permission 각각 evaluator 호출하여 set 구성
  - 노드 부재 → 404 NOT_FOUND
  - 단축: ADMIN role은 evaluator가 항상 grant → 결과는 `EnumSet.allOf(Permission.class)` (early return)
- `MEMBER` role + nodeId — resource-level grant union이 의미. 9× evaluator는 V5 CTE를 권한별로 9회 호출 — staleTime 60초(`usePermission`)이므로 호출 빈도 낮아 MVP 허용

### 새 클래스/메소드
- `EffectivePermissionsController` (또는 `PermissionController`에 `@GetMapping("/me/effective-permissions")` 추가) — KISS 측면에서 후자 선호
- `PermissionService` 또는 `IbizDrivePermissionEvaluator`에 `Set<Permission> resolveAll(IbizDriveUserDetails, String resourceType, UUID resourceId)` 추가. resourceType=null이면 role-only.
- DTO: `EffectivePermissionsResponse(List<Permission> permissions)` record (또는 inline `Map.of("permissions", ...)`). KISS — `Map.of` 직접 사용.

### 노드 존재 검증
- nodeId가 folder UUID인지 file UUID인지 호출 측에 모름 → 둘 다 lookup. 어느 한쪽이라도 활성 row 발견 시 resourceType 결정.
- 둘 다 미존재 → 404. (folder/file 같은 UUID 동시 존재는 V5 스키마상 별도 테이블이라 충돌 없으나 운용상 거의 불가능)

## phase 실행 지도

| Phase | Title | 산출물 |
|---|---|---|
| A11.0 | dev-docs bootstrap | plan/context/tasks 3 파일 |
| A11.1 | service layer — `resolveAll(user, [type, id]) → Set<Permission>` + unit test | PermissionService 또는 evaluator 확장 + 단위 테스트 |
| A11.2 | controller endpoint — `GET /api/me/effective-permissions[?nodeId=]` + integration test (MockMvc + @SpringBootTest) | PermissionController 추가 매핑 + dto + 통합 테스트 |
| A11.3 | docs/02 §7.10 본문 보강 (status 표기 변경 + 응답 schema 명시) | docs/02 patch |
| A11.4 | full GREEN + PR + closure | `./gradlew test` + frontend `pnpm test` 회귀 0 + PR + master squash + archive + progress.md |

## acceptance criteria

- [ ] `GET /api/me/effective-permissions` (인증된 ADMIN) → 9권한 반환
- [ ] `GET /api/me/effective-permissions` (AUDITOR) → `["READ"]`
- [ ] `GET /api/me/effective-permissions` (MEMBER) → `[]`
- [ ] `GET /api/me/effective-permissions` (미인증) → 401
- [ ] `GET /api/me/effective-permissions?nodeId={folder UUID}` (MEMBER + V5 grant 보유) → grant preset에 매핑된 권한
- [ ] `GET /api/me/effective-permissions?nodeId={존재하는 file UUID}` (ADMIN) → 9권한 (early return 동일)
- [ ] `GET /api/me/effective-permissions?nodeId={미존재 UUID}` → 404 NOT_FOUND
- [ ] `GET /api/me/effective-permissions?nodeId=invalid-uuid` → 400 BAD_REQUEST
- [ ] `./gradlew test` GREEN (기존 테스트 회귀 0 — A4 controller 테스트 무영향)
- [ ] frontend `pnpm test` 회귀 0 (이번 트랙 frontend 무수정)
- [ ] docs/02 §7.10 라인 1173 표/본문 갱신

## 검증 게이트

- A11.1 service unit test: ADMIN/AUDITOR/MEMBER × (nodeId null/valid/invalid) 매트릭스 GREEN
- A11.2 MockMvc integration: 200/401/404/400 케이스 GREEN
- A11.3 docs sync: §7.10 표 + 응답 schema 본문 추가 라인 확인
- A11.4 full `./gradlew test` + `pnpm typecheck && pnpm lint && pnpm test` 모두 GREEN

## 리스크와 완화

1. **9× evaluator 호출 비용** — resource-level 평가 시 V5 재귀 CTE를 권한별 9회 호출. staleTime 60s(usePermission) + 사용자×노드 단위라 부하 낮음. 측정 후 hot path면 후속 트랙에서 `PermissionResolver.resolveSet` 1회 CTE로 최적화 — controller 시그니처 보존(ADR #26 패턴 동형).
2. **노드 존재 검증 race** — folder/file 둘 다 lookup 후 evaluator 호출 사이에 soft-delete 발생 가능. 이 경우 evaluator의 PermissionResolver가 `deleted_at IS NULL` 필터로 grant set이 빈 결과 → MEMBER는 빈 권한 반환. 401/403 envelope과 무관 — read-only이므로 허용.
3. **DTO 명세 표류** — frontend `Permission` 유니언이 backend `Permission.wire()` 1:1 미러. 응답 키 `permissions` 고정 (frontend `getEffectivePermissions`의 `Promise<Permission[]>` 반환과 정합). 매핑 inline 1줄.
4. **A4 evaluator MVP 분기 누락** — A4의 `IbizDrivePermissionEvaluator`는 ROLE 경로 + resource-level 경로 모두 보유. resource-level 경로는 `targetType ∈ {folder, file}` + UUID 가드. 본 트랙은 이 인터페이스를 그대로 사용 → A4 머지 상태 의존.
5. **PURGE 권한** — ADMIN role만 보유. resource-level grant로는 부여 불가(Preset 미포함). nodeId 지정 시 ADMIN이 아니면 응답 set에 PURGE 미포함이 정상 (docs/03 line 331~334).

## 비-목표

- frontend 변경 (F2 후속 트랙)
- WebSocket/SSE 권한 변경 알림 (15.x)
- bulk node id 처리 (`?nodeIds=a,b,c`) — 후속 최적화. 현 usePermission는 단일 nodeId만 사용
- caching layer — Spring 자체 cache 도입은 별도 트랙 (LoginResponse.effectivePermissionsCacheKey는 키만 정의, 캐시 미구현)
