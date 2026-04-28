---
Last Updated: 2026-04-29 (BOOTSTRAP — 사용자 plan 리뷰 게이트 대기)
Status: 🟡 DRAFT (단계 3 일시정지 진입 직전)
---

# A3 — Permission Matrix + PermissionService

## 요약

`Permission` enum 9종 + Preset 5종을 백엔드 단일 진실 출처로 도입하고, `PermissionService.check()`를 `@PreAuthorize` 단일 진입점으로 세운다. user-level (ROLE 기반) 평가만 우선 GREEN으로 만들고, resource-level (`file_permission`/`folder_permission`)은 폴더·파일 도메인 부재로 **A4 이월**한다. 동시에 A1 deviation `effectivePermissionsCacheKey="userId:role:v0"` 정적값을 권한 상태 hash로 교체하고, A2에서 enum만 도입된 `permission.granted/revoked/changed` 3종을 본 트랙에서 실제 emit한다.

## 현재 상태 분석

| 영역 | 상태 |
|---|---|
| `docs/03 §3.1~§3.6` 본문 | ⚠️ 본문 존재 (Permission/Preset/Subject/inheritance/매트릭스/403). 문서 라우팅 표·overview는 "(작성 예정)" 표기 → 표기·내부 일관성 정리 필요 |
| `docs/02 §7.10` 권한 endpoint Guard 표 | ✅ 존재 (`hasPermission(#id, #resource, 'READ'/'ADMIN')`, `canRevokePermission`, `effective-permissions`) — A3.2 SpEL evaluator의 계약 |
| `Permission` enum (Java) | ❌ 미존재 — A3.1에서 신설 |
| `Preset` enum + preset→permission mapping | ❌ 미존재 — A3.1 |
| `frontend/src/types/permission.ts` (1:1 mirror) | ❌ 미존재 — CLAUDE.md §4 계약 파일 표에 "(예정)"으로만 등록 |
| `PermissionService` + `PermissionEvaluator` | ❌ 미존재 — A3.2 신설 (`hasPermission()` SpEL hook) |
| `Role` enum (`MEMBER`/`AUDITOR`/`ADMIN`) | ✅ V2 도입 + `Role.java` 존재 — `PURGE`는 `hasRole('ADMIN')`로 분기 |
| `LoginResponse.effectivePermissionsCacheKey` | ⚠️ `userId:role:v0` 정적 문자열 (`LoginResponse.java:42`) — A3.3에서 hash 교체 |
| `AuditEventType.permission.granted/revoked/changed` | ✅ A2에서 enum 등록 (38종 중 3종) — emission 호출처는 A3.4 |
| 권한 endpoint POST/DELETE `/api/:resource/:id/permissions` | ❌ 폴더/파일 도메인 부재로 구현 자체가 A4 의존 → A3.4 emission 시점은 **accepted-deviation 후보** |
| 통합 테스트 패턴 | ✅ A2 확립 (`@SpringBootTest` + Testcontainers + HttpClient5) |

## 목표 상태 (DoD)

A3 PR 머지 시점에 다음이 모두 true:

1. `docs/03 §3.1~§3.6` "스켈레톤"/"작성 예정" 표기 제거 + 본문이 docs/02 §7.4~§7.13 Guard 컬럼과 1:1 일치 (drift 없음)
2. `com.ibizdrive.permission.Permission` enum 9 values (`READ/UPLOAD/EDIT/MOVE/DOWNLOAD/DELETE/SHARE/PERMISSION_ADMIN/PURGE`)
3. `com.ibizdrive.permission.Preset` enum 5 values (`READ/UPLOAD/EDIT/SHARE/ADMIN`) + preset→permission set 매핑 (§3.2 표 동치)
4. `frontend/src/types/permission.ts`가 위 enum 1:1 미러 (CLAUDE.md §4 계약 파일 표 등록 완료, "(예정)" 제거)
5. `PermissionService.check(userId, role, resource, resourceId, permission)` 단일 진입점 + `IbizDrivePermissionEvaluator implements PermissionEvaluator` 등록 → `hasPermission(#id, 'folder', 'READ')` SpEL 동작
6. **MVP user-level 평가 정책**: ROLE 기반 분기만 — `ADMIN`은 모든 permission true (단 `PURGE`는 `ADMIN`만 true), `AUDITOR`는 `READ`만 true, `MEMBER`는 (resource 도메인 부재로) **deny by default + 명시적 SpEL 우회 endpoint만 통과**. resource-level 평가는 명시적 TODO + A4 연결
7. `LoginResponse.effectivePermissionsCacheKey`가 권한 상태 hash (예: `SHA-256(role + sorted(permissions))[:16]`) — 같은 role/permission 입력에 같은 키 (deterministic), 다른 role/permission에 다른 키 (collision 회피 단위 테스트로 증명)
8. `permission.granted/revoked/changed` 3 이벤트의 emission 정책 명시:
   - **그랜트/리보크 endpoint 자체는 file/folder 의존 → A4**
   - 본 phase에서는 `PermissionService` 도메인 메서드 stub (`grantSystemRole`, `revokeSystemRole`)로 ROLE 변경에만 emit, resource-level granted/revoked는 A4에서
   - accepted-deviation 명시 (overview ADR + a3 closure context)
9. `@SpringBootTest` E2E (A2 패턴): admin endpoint를 가짜로 두고 `@PreAuthorize` 통과/차단을 hasPermission/hasRole 양쪽으로 검증
10. `gradle test` + `pnpm test` 모두 PASS, CI 그린, master rebase clean
11. ADR 신규 #26 (`PermissionEvaluator` user-level MVP + resource-level A4 deferral) docs/00 §5에 등록

## Phase 실행 지도

> **표기**: phase별 RED→GREEN→REFACTOR. acceptance는 phase 단위 게이트.

### A3.0 — 스코프 락 + docs/03 §3 정합화 + ADR #26 (no-code phase)

**Spec 근거**: docs/03 §3.1~§3.6, docs/02 §7.10, CLAUDE.md §4.

**작업**:
- docs/03 §3 본문 stale 표기(line 4 "스켈레톤", `CLAUDE.md` 라우팅 "(작성 예정)") 제거
- §3.5 endpoint 매핑이 docs/02 §7.4~§7.13 Guard 컬럼과 정확히 일치하는지 cross-check + 차이 patch
- ADR #26 추가 — user-level MVP / resource-level A4 deferral 결정
- `frontend/src/types/permission.ts` placeholder 신설 (값은 A3.1과 동시 채움 — bootstrap 시 빈 export로 등록)
- A3 scope 분할 사용자 리뷰 게이트 통과 후 진행

**acceptance**:
- docs/00 §5에 ADR #26 row 추가
- docs/03 §3 line 4 "스켈레톤" 문구 제거
- CLAUDE.md §2 라우팅 "권한 매트릭스 (작성 예정)" → "권한 매트릭스 (본문)"
- CLAUDE.md §4 계약 파일 표에서 `src/types/permission.ts` "(예정)" 제거
- 정적 분석: docs/03 §3.5 endpoint 명세가 docs/02 §7 Guard 컬럼과 1:1 일치

### A3.1 — Permission enum + Preset + frontend mirror (TDD)

**Spec 근거**: docs/03 §3.1, §3.2.

**RED**:
- `PermissionEnumTest`: 9 values 정확히, `valueOf` 라운드트립
- `PresetMappingTest`: §3.2 표 그대로 — `Preset.READ.permissions() == {READ, DOWNLOAD}` 등 5 케이스
- frontend `permission.test.ts`: `Permission` 타입이 백엔드 9종 정확히 대응 (fixture-based)

**GREEN**:
- `com.ibizdrive.permission.Permission` enum
- `com.ibizdrive.permission.Preset` enum + `Set<Permission> permissions()`
- `frontend/src/types/permission.ts` (1:1 mirror, JSDoc에 docs/03 §3.1 backlink)

### A3.2 — PermissionService + PermissionEvaluator + @PreAuthorize 통합

**Spec 근거**: docs/03 §3.4, ADR #26 (이번 phase 정의).

**RED**:
- `PermissionServiceTest`: `check(userId, role=ADMIN, "folder", id, READ)` → true. `check(_, MEMBER, _, _, PURGE)` → false. `check(_, AUDITOR, _, _, EDIT)` → false. `check(_, AUDITOR, _, _, READ)` → true
- `PermissionEvaluatorIntegrationTest` (`@WebMvcTest`): `@PreAuthorize("hasPermission(#id, 'folder', 'READ')")` 메서드를 ADMIN/AUDITOR가 호출 → 200, MEMBER 호출 → 403 PERMISSION_DENIED
- `PermissionEvaluatorIntegrationTest`: `hasRole('ADMIN')` SpEL은 그대로 동작 (Spring 기본)

**GREEN**:
- `PermissionService.check(userId UUID, role Role, resource String, resourceId Object, permission Permission) : boolean`
- `IbizDrivePermissionEvaluator implements PermissionEvaluator` — `SecurityConfig`에 `MethodSecurityExpressionHandler` 빈 등록
- 403 응답 body는 docs/03 §3.6 형식 (`{ error: { code: 'PERMISSION_DENIED', details: { required, have } } }`)
- `AuthExceptionHandler`에 `AccessDeniedException` 핸들러 추가 (현재 401만 처리)

**REFACTOR**:
- `IbizDriveUserDetails`에서 `Role` 직접 노출 (Authentication.getAuthorities → ROLE_xxx 변환과 별개)

### A3.3 — `effectivePermissionsCacheKey` hash 교체

**Spec 근거**: A1 deviation #2 (사실상 `LoginResponse.java:11` MVP 주석), docs/03 §3.6 (cache invalidate trigger).

**RED**:
- `LoginResponseCacheKeyTest`:
  - 같은 role 두 번 호출 → 동일 key (deterministic)
  - 다른 role (MEMBER vs ADMIN) → 다른 key
  - 미래 동일 role + permission set 입력 시 동일 key (resource-level 도입 전제용 안정성 보장)

**GREEN**:
- `PermissionCacheKeyService.computeKey(userId, role) : String`
  - 입력: `userId.toString() + "|" + role.name() + "|" + sortedJoin(rolePermissions)`
  - SHA-256 hex prefix 16자
- `LoginResponse.from(User u)` → `cacheKeyService.computeKey(u.getId(), u.getRole())` 사용. 정적 문자열 `:v0` 제거
- 주석 갱신: docs/03 §3.6 backlink

### A3.4 — `permission.granted/revoked/changed` emission (ROLE-level only)

**Spec 근거**: docs/03 §4.1 enum, A2 closure context "A3 진입점" 5단위.

**accepted-deviation 명시 (본 phase)**:
- resource-level grant/revoke endpoint (POST/DELETE `/api/:resource/:id/permissions`)는 **file/folder 도메인 부재로 A4 이월**
- 본 phase는 ROLE 변경 (`PATCH /api/admin/users/:id` — A4 endpoint이지만 service 레이어만 선도입)에 한해 `permission.changed` emit

**RED**:
- `RoleChangeAuditTest`: `PermissionService.changeRole(targetUserId, newRole)` 호출 → audit_log에 `permission.changed` row + before/after metadata
- `PermissionService` 호출자가 `@Transactional` rollback 시 audit row는 보존 (A2 REQUIRES_NEW 패턴)

**GREEN**:
- `PermissionService.changeRole(adminCallerId, targetUserId, newRole)` — `users.role` UPDATE + `AuditService.record()`
- `permission.granted/revoked` emit 호출처는 A4 endpoint 도입 시 추가 — 본 phase는 호출처 미존재 (TODO 주석 + A4 연결만)

### A3.5 — 통합 E2E (Testcontainers, A2 패턴)

**Spec 근거**: docs/03 §3.6 403 형식.

- `PermissionEndpointE2ETest` (`@SpringBootTest` + Testcontainers + HttpClient5)
  - 가짜 `/api/test/permission-required` 컨트롤러 (test profile only) — `@PreAuthorize("hasPermission(#id, 'folder', 'READ')")`
  - ADMIN/AUDITOR 로그인 → 200
  - MEMBER 로그인 → 403 + body docs/03 §3.6 형식
  - 익명 → 401 (기존 SecurityConfig 동작 유지)
- `RoleChangeE2ETest`: ADMIN이 자신의 role을 MEMBER로 변경 → 다음 요청부터 403 + audit `permission.changed` 1건

### A3.6 — Closure

- `docs/progress.md` A3 종료 블록
- `dev/active/a3-permission-matrix/` → `dev/completed/a3-permission-matrix/` archive (A1/A2 패턴)
- `superpowers:requesting-code-review` 1회
- `gh pr create` → master 대상

## acceptance criteria (phase별 게이트)

| Phase | 게이트 |
|---|---|
| A3.0 | 사용자 plan 리뷰 OK + ADR #26 등록 |
| A3.1 | enum/preset RED→GREEN, frontend mirror 동기 검증 |
| A3.2 | hasPermission SpEL 통과/차단 통합 테스트 GREEN, 403 body 포맷 검증 |
| A3.3 | cache key deterministic + role/permission delta 시 변경 단위 테스트 |
| A3.4 | role 변경 audit 1건 emit, REQUIRES_NEW 회귀 없음 |
| A3.5 | E2E ADMIN/AUDITOR/MEMBER 3 케이스 GREEN |
| A3.6 | CI 그린, PR 머지, archive 완료 |

## 검증 게이트 (CLAUDE.md §3 핵심 원칙)

- **#10 파괴적 액션 백엔드 재검증** — `PermissionEvaluator`가 controller 진입 차단 (프론트 권한은 UX용)
- **#6 DB 제약 진실 출처** — A3는 user-level만 (DB 레벨 permission 테이블 미사용), A4에서 `permissions` 테이블 + UNIQUE 제약 도입 시 §6과 정합 재검증
- **#7 트랜잭션** — role 변경 단일 row UPDATE, FOR UPDATE 불필요 (단일 admin 가정 + @Transactional)
- **#8 audit append-only** — A2 V4 REVOKE + REQUIRES_NEW 그대로 사용
- **#11 정규화** — Permission/Preset enum 문자열은 frontend/backend 동일 소문자 정책 (`Preset.read` ↔ `'read'`)
- **#12 에러 코드 계약** — `PERMISSION_DENIED` 신규 사용 (docs/02 §8 기존 등록 확인 필요 — A3.0에서 점검)

## 리스크와 완화 전략

| # | 리스크 | 완화 |
|---|---|---|
| R1 | resource-level 부재 상태에서 `hasPermission(#id, 'folder', 'READ')` SpEL이 의미 없는 통과/차단을 만들 수 있음 | MVP는 ROLE 기반 분기로만 평가. SpEL 인자 (`resource`, `resourceId`)는 PermissionEvaluator가 받지만 사용 안 함 + TODO 주석 + A4 연결. 통합 테스트에서 ADMIN/AUDITOR/MEMBER 3 케이스만 검증 |
| R2 | `effectivePermissionsCacheKey` hash 변경이 기존 `/me`/`/login` 클라이언트 응답을 깨뜨릴 가능성 | 응답 shape는 동일 (`String`), 값만 hex로 바뀜. frontend는 opaque 토큰처럼 invalidate trigger로만 사용 (역으로 파싱하지 않음) — 변경 안전 |
| R3 | `permission.granted/revoked` emission이 본 phase에서 비어 있음 | accepted-deviation을 ADR #26 + closure context에 명시. enum 자체는 A2에서 정의 완료, 호출처만 A4 이월 |
| R4 | 폴더/파일 도메인 도입 시 SpEL 평가가 user-level → resource-level로 확장될 때 기존 controller `@PreAuthorize` 시그니처가 깨질 위험 | A3.2 단계에서 SpEL 인자 시그니처 (`#id, 'folder', 'READ'`)를 docs/02 §7 그대로 채택 — A4에서는 `PermissionEvaluator` 내부만 교체, 호출 시그니처 보존 |
| R5 | docs/03 §3 본문이 이미 존재하나 progress·CLAUDE.md 라우팅 표가 "작성 예정"으로 stale → A3.1 블로커가 사실은 표기 정합성 작업 | A3.0을 "no-code phase"로 명시 분리. 실제 구현은 A3.1부터 |
| R6 | 자율 실행 모드에서 단계 3 게이트 누락 시 사용자 의도와 다른 분할 진행 | bootstrap 직후 즉시 사용자 보고 + 일시정지 (feedback_autonomous_mode 패턴) |

## 비명시 (deferred / out-of-scope)

- resource-level permission 테이블 (`permissions(subject_type, subject_id, resource_type, resource_id, ...)`) → **A4**
- POST/DELETE `/api/:resource/:id/permissions` endpoint → **A4**
- LTREE 부서 계층 + `includeDescendants` 평가 → **A4** (부서 모델 자체가 A1.5 후속)
- 권한 상속 재귀 CTE (docs/03 §3.4) → **A4** (folder tree 부재)
- `effectivePermissions` resource-level 캐시 — A3는 user-level hash key만, 실제 캐시 store는 v1.x

## ADR 신규

- **#26**: PermissionEvaluator MVP는 user-level (Role 기반) 평가만. resource-level은 A4 (folder/file 도메인 도입 시 evaluator 내부만 교체, SpEL 호출 시그니처는 docs/02 §7 그대로 유지)
