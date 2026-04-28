---
Last Updated: 2026-04-29 (🏁 closure)
Status: ✅ closed
---

# A3 — Tasks

각 task는 RED → GREEN → REFACTOR 순. 완료 시 `[x]`, 부분 완료 `[-]` + 잔여 1줄.

## Phase 상태 요약

| Phase | 상태 |
|---|---|
| A3.0 docs/03 §3 정합화 + ADR #26 | ✅ (`ff5156c` + `aec7b74`) |
| A3.1 Permission/Preset enum + frontend mirror | ✅ (`4458feb`) |
| A3.2 PermissionService + Evaluator + 403 핸들러 | ✅ (`e1083e4`) |
| A3.3 effectivePermissionsCacheKey hash 교체 | ✅ (`e1083e4`) |
| A3.4 permission.changed (role 변경) emission | ✅ (`e1083e4`) |
| A3.5 통합 E2E (Testcontainers) | ✅ (`ccd766d`) — 11 + 2 케이스 |
| A3.6 closure (PR + archive) | 🏁 진행 중 — progress.md ✅ / PR ⏳ / archive ⏳ |

---

## A3.0 — docs 정합화 + ADR #26 (no-code phase)

### 작업 전 필독

- `dev/active/a3-permission-matrix/a3-permission-matrix-plan.md` §"A3.0"
- `docs/03-security-compliance.md` line 1~6 (header), line 303~385 (§3.1~§3.6)
- `docs/02-backend-data-model.md` line 1047~1062 (§7.10)
- `CLAUDE.md` §2 라우팅 표 + §4 계약 파일 표
- `docs/00-overview.md` §5 ADR 마지막 entry

### 원본 코드 참조

- (없음 — 문서 phase)

### 구현 대상

- [ ] docs/03 line 4 "현재 상태: 스켈레톤" → "본문 활성 (A3.0)"
- [ ] docs/03 §3.5 endpoint 매핑이 docs/02 §7.4~§7.13 Guard 컬럼과 1:1 일치하는지 확인 + 차이 patch
- [ ] CLAUDE.md §2 라우팅 표: "권한 매트릭스 (작성 예정)" → "권한 매트릭스 (본문)"
- [ ] CLAUDE.md §4 계약 파일 표: `src/types/permission.ts` "(예정)" 제거
- [ ] docs/00 §5 ADR #26 추가 — user-level MVP / resource-level A4 deferral 결정 본문
- [ ] `frontend/src/types/permission.ts` placeholder 신설 (`export {} // A3.1에서 채움`)

### 검증 참조

- [ ] `git diff CLAUDE.md docs/00-overview.md docs/03-security-compliance.md` — 표기/ADR 변경만, 본문 의미 보존
- [ ] frontend: `pnpm typecheck` PASS (placeholder 빈 export로 깨짐 없음)

### 문서 반영

- [ ] docs/progress.md 진행 기록 1줄 (A3.0 완료)

### commit

- [ ] `chore(A3.0): docs/03 §3 표기 정합 + ADR #26 등록 + permission.ts placeholder`

---

## A3.1 — Permission enum + Preset + frontend mirror (TDD)

### 작업 전 필독

- plan §"A3.1"
- `docs/03-security-compliance.md` §3.1 line 305~321, §3.2 line 323~334
- `backend/src/main/java/com/ibizdrive/audit/AuditEventType.java` (enum 패턴 참조)
- `frontend/src/types/audit.ts` (mirror 패턴 참조)

### 원본 코드 참조

- `Role.java` (enum 단일 파일 패턴)
- `AuditEventType.java` (`@JsonValue`/`@JsonCreator` wire 변환 — Permission/Preset도 동일하게 lowercase 직렬화 채택)

### 구현 대상

- [ ] RED: `PermissionEnumTest` — 9 values 정확히 (`READ/UPLOAD/EDIT/MOVE/DOWNLOAD/DELETE/SHARE/PERMISSION_ADMIN/PURGE`)
- [ ] RED: `PresetMappingTest` — 5 케이스 (read/upload/edit/share/admin) preset → permission set §3.2 표 동치
- [ ] RED: `PermissionEnumTest` — `@JsonValue` lowercase wire 검증 (`READ` ↔ `'read'`)
- [ ] GREEN: `com.ibizdrive.permission.Permission` enum
- [ ] GREEN: `com.ibizdrive.permission.Preset` enum + `Set<Permission> permissions()` static map
- [ ] GREEN: `frontend/src/types/permission.ts` (`Permission` union + `Preset` union + `PRESET_PERMISSIONS` const map)
- [ ] frontend: `permission.test.ts` — 백엔드 fixture (json) 9개 값과 1:1 검증

### 검증 참조

- [ ] `gradle test --tests PermissionEnumTest --tests PresetMappingTest` GREEN
- [ ] `pnpm test permission` GREEN
- [ ] `pnpm typecheck` PASS

### 문서 반영

- [ ] docs/03 §3.1/§3.2 backlink 주석 (Permission.java JSDoc/Javadoc → docs path)

### commit

- [ ] `feat(A3.1): Permission/Preset enum + frontend 1:1 mirror`

---

## A3.2 — PermissionService + PermissionEvaluator + 403 핸들러

### 작업 전 필독

- plan §"A3.2"
- `docs/03 §3.4` line 351~356, §3.5 line 358~379, §3.6 line 381~385
- `docs/02 §7.10` Guard 표 line 1047~1054
- `backend/src/main/java/com/ibizdrive/config/SecurityConfig.java` (MethodSecurity 등록 위치)
- `backend/src/main/java/com/ibizdrive/common/error/AuthExceptionHandler.java` (현 401 핸들러 패턴)

### 원본 코드 참조

- `Role.java` (Role 분기)
- `IbizDriveUserDetails.java` (`getRole()` 노출 여부 확인)
- A2 `AuditService` (REQUIRES_NEW 패턴 — A3.4에서 활용)

### 구현 대상

- [ ] RED: `PermissionServiceTest` — `check(_, ADMIN, _, _, READ)` true / `check(_, ADMIN, _, _, PURGE)` true / `check(_, AUDITOR, _, _, EDIT)` false / `check(_, AUDITOR, _, _, READ)` true / `check(_, MEMBER, _, _, READ)` false (resource-level 부재 deny by default)
- [ ] RED: `PermissionEvaluatorIntegrationTest` (`@WebMvcTest` + 가짜 `@PreAuthorize("hasPermission(#id, 'folder', 'READ')")` 컨트롤러) — ADMIN/AUDITOR 200, MEMBER 403, 익명 401
- [ ] RED: 403 응답 body — `{ error: { code: 'PERMISSION_DENIED', details: { required: ['READ'], have: [] } } }` 형식 검증
- [ ] GREEN: `com.ibizdrive.permission.PermissionService` (`check()` 단일 진입점)
- [ ] GREEN: `com.ibizdrive.permission.IbizDrivePermissionEvaluator implements PermissionEvaluator`
- [ ] GREEN: `SecurityConfig`에 `MethodSecurityExpressionHandler` 빈 등록 + `@EnableMethodSecurity(prePostEnabled=true)` 확인
- [ ] GREEN: `AuthExceptionHandler.handleAccessDenied(AccessDeniedException)` 추가 → 403 + `PERMISSION_DENIED` body
- [ ] REFACTOR: `IbizDriveUserDetails`에서 `Role` 직접 노출 (현재 ROLE_xxx authority만 노출 시 헬퍼 추가)

### 검증 참조

- [ ] `gradle test --tests PermissionServiceTest --tests PermissionEvaluatorIntegrationTest` GREEN
- [ ] 회귀: 기존 `AuthControllerTest`, `AuditQueryControllerTest` 모두 GREEN (SecurityConfig 변경 영향 없음)

### 문서 반영

- [ ] docs/02 §8 에러 코드 표에 `PERMISSION_DENIED` 등록 확인 (없으면 추가)
- [ ] docs/03 §3.6 본문이 실제 응답 body와 일치하는지 final check

### commit

- [ ] `feat(A3.2): PermissionService + Evaluator + 403 PERMISSION_DENIED 핸들러`

---

## A3.3 — `effectivePermissionsCacheKey` hash 교체

### 작업 전 필독

- plan §"A3.3"
- `backend/src/main/java/com/ibizdrive/auth/dto/LoginResponse.java` (특히 line 11, 42)
- `dev/completed/a1-auth-impl/a1-auth-impl-audit.md` line 38, 47 (deviation 출처)
- `docs/03 §3.6` (cache invalidate trigger)

### 원본 코드 참조

- `LoginResponse.from(User u)` line 34~44

### 구현 대상

- [ ] RED: `LoginResponseCacheKeyTest`
  - 같은 (userId, role, role permissions) 입력 → 동일 key
  - 다른 role (MEMBER vs ADMIN) → 다른 key (collision 회피)
  - 같은 role, 다른 userId → 다른 key
  - hex 16자 길이 검증
- [ ] GREEN: `com.ibizdrive.permission.PermissionCacheKeyService.computeKey(UUID userId, Role role) : String`
  - 입력 정규화: `userId.toString() + "|" + role.name() + "|" + sortedJoin(rolePermissions)`
  - SHA-256 hex prefix 16자 (`MessageDigest.getInstance("SHA-256")`)
- [ ] GREEN: `LoginResponse.from(User u, PermissionCacheKeyService keys)` 시그니처 변경 + 호출처 (`AuthController`/`AuthService`) 주입 추가
- [ ] GREEN: line 11 주석 갱신 — "MVP `userId:role:v0`" 제거, A3.3 backlink 추가
- [ ] REFACTOR: `Preset.ADMIN.permissions()` 등을 hash 입력에 활용 (role의 system-level permissions)

### 검증 참조

- [ ] `gradle test --tests LoginResponseCacheKeyTest` GREEN
- [ ] `gradle test --tests AuthControllerTest` GREEN (회귀 — 기존 응답 shape 보존)
- [ ] frontend `api.auth.test.ts` (있으면) 회귀

### 문서 반영

- [ ] dev/completed/a1-auth-impl/a1-auth-impl-audit.md (변경 안 함, 본 closure에서 deviation 해소 backlink만 본 tasks에 기록)
- [ ] A3 closure context에 "A1 deviation #2 (cache key 정적값) 해소" 명시

### commit

- [ ] `refactor(A3.3): effectivePermissionsCacheKey 정적값 → SHA-256 hash`

---

## A3.4 — `permission.changed` (role 변경) emission

### 작업 전 필독

- plan §"A3.4"
- `dev/completed/a2-audit-log/a2-audit-log-plan.md` §"A2.4" (REQUIRES_NEW + listener 패턴)
- `backend/src/main/java/com/ibizdrive/audit/AuditService.java`
- `docs/03 §4.1` `permission.changed` 이벤트 정의

### 원본 코드 참조

- `AuditService.record(AuditEvent)` (REQUIRES_NEW)
- `AuditedAspect` (`@Audited` 어노테이션 — 본 phase에서 service 메서드에 적용 가능)

### 구현 대상

- [ ] RED: `RoleChangeAuditTest`
  - `permissionService.changeRole(adminId, targetUserId, ADMIN)` 호출 → audit_log에 `permission.changed` row 1건
  - metadata에 `{ before: 'MEMBER', after: 'ADMIN', targetUserId: ... }`
  - actor_id = adminId
- [ ] RED: 호출자 트랜잭션 rollback 시 audit row 보존 (REQUIRES_NEW 회귀 검증)
- [ ] GREEN: `PermissionService.changeRole(UUID actorId, UUID targetUserId, Role newRole)`
  - `users.role` UPDATE
  - `AuditService.record(AuditEvent)` 호출 (event=`permission.changed`, target=user, before/after metadata)
- [ ] (deferred) `permission.granted` / `permission.revoked` emit 호출처는 A4 endpoint 도입 시 — 본 phase는 TODO 주석만

### 검증 참조

- [ ] `gradle test --tests RoleChangeAuditTest` GREEN
- [ ] 기존 A2 `AuthAuditE2ETest` 회귀 GREEN

### 문서 반영

- [ ] A3 closure context에 accepted-deviation 명시: `permission.granted/revoked` emit은 A4 이월

### commit

- [ ] `feat(A3.4): PermissionService.changeRole + permission.changed emission`

---

## A3.5 — 통합 E2E (Testcontainers, A2 패턴)

### 작업 전 필독

- plan §"A3.5"
- `backend/src/test/java/com/ibizdrive/audit/AuthAuditE2ETest.java` (패턴 참조)
- A2 closure context — `@SpringBootTest` + Testcontainers + HttpClient5 패턴

### 원본 코드 참조

- `AuthAuditE2ETest` (HttpClient5 + 세션 쿠키 흐름 그대로 재사용)

### 구현 대상

- [ ] `PermissionEndpointE2ETest` (`@SpringBootTest`)
  - test profile only `/api/test/permission-required` 컨트롤러 (`@PreAuthorize("hasPermission(#id, 'folder', 'READ')")`)
  - ADMIN 로그인 → 200
  - AUDITOR 로그인 → 200 (READ 권한)
  - MEMBER 로그인 → 403 + body docs/03 §3.6 형식
  - 익명 → 401
- [ ] `RoleChangeE2ETest`
  - ADMIN이 자기 자신의 role을 MEMBER로 변경 (test endpoint 또는 service 직접 호출)
  - 다음 요청부터 admin-only 액션 403
  - audit_log `permission.changed` 1건 검증

### 검증 참조

- [ ] `gradle test --tests PermissionEndpointE2ETest --tests RoleChangeE2ETest` GREEN
- [ ] CI 그린 확인 후 다음 phase

### 문서 반영

- [ ] (없음 — 검증만)

### commit

- [ ] `test(A3.5): full E2E permission evaluator + role change`

---

## A3.6 — Closure

### 작업 전 필독

- plan §"A3.6"
- `dev/completed/a2-audit-log/a2-audit-log-plan.md` 종료 블록 (closure 패턴 참조)

### 원본 코드 참조

- (없음)

### 구현 대상

- [ ] `docs/progress.md` 2026-MM-DD A3 종료 블록 (DoD 11/11, accepted-deviation 1건)
- [ ] `superpowers:requesting-code-review` 1회 — review 결과 반영
- [ ] `gh pr create` → master 대상 (PR body: DoD 체크리스트 + 의존 ADR + 후속 A4 연결)
- [ ] CI 그린 확인 후 squash merge
- [ ] `dev/active/a3-permission-matrix/` → `dev/completed/a3-permission-matrix/` archive (closure 마커 + audit 보존, A1/A2 패턴 동일)
- [ ] master rebase + 작업 트리 정리

### 검증 참조

- [ ] master CI 그린
- [ ] `dev/active/`에 A3 잔여 없음

### 문서 반영

- [ ] dev/completed/a3-permission-matrix/a3-permission-matrix-context.md (closure 마커 + A4 진입점 핸드오프)

### commit

- [ ] `chore(A3): closure — A3 dev-docs active→completed archive`
