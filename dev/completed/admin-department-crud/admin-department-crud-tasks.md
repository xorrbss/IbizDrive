---
Last Updated: 2026-05-06
---

# Tasks — admin-department-crud (Wave 2 — T4)

## Phase 상태

- [x] P1 — DB migration + Domain (V9 — V8은 password_reset_tokens가 선점)
- [x] P2 — Repository + Service + Events
- [x] P3 — Audit infra + Controller + Listener
- [x] P4 — Frontend api + hooks + queryKeys + types
- [x] P5 — Frontend page + layout
- [ ] P6 — Docs sync + closure + PR  ← **active**

---

## P1 — DB migration + Domain

- [ ] V8 migration 작성 (`V8__admin_departments.sql`)
- [ ] `Department.rename(String)` + 검증 (trim, 1~100자, blank 거부)
- [ ] `Department.deactivate()` / `reactivate()` + `isActive()` boolean 도출
- [ ] `DepartmentTest` 도메인 단위 (rename trim/length/blank/idempotent / state transitions)
- [ ] gate: `cd backend && ./gradlew test --tests "*.DepartmentTest"` GREEN

### 작업 전 필독
- `dev/active/admin-department-crud/admin-department-crud-plan.md` §"P1"
- `backend/src/main/resources/db/migration/V3__audit_log.sql` line 25-26 (CHECK 정의)
- `backend/src/main/resources/db/migration/V7__departments_users_dept.sql` (departments 테이블)
- `backend/src/main/java/com/ibizdrive/user/User.java` line 220-235 (`deactivate()`/`reactivate()` 패턴)

### 원본 코드 참조
- `User.deactivate()` / `User.reactivate()` — 도메인 메서드 멱등 패턴.
- V3 `audit_log_target_type_check` — DROP / ADD CONSTRAINT 패턴.

### 구현 대상
- `backend/src/main/resources/db/migration/V8__admin_departments.sql` (NEW):
  ```sql
  -- 1. departments name partial unique
  CREATE UNIQUE INDEX idx_departments_name_active
      ON departments(name) WHERE deleted_at IS NULL;

  -- 2. audit_log target_type CHECK 갱신 — 'department' 추가
  ALTER TABLE audit_log DROP CONSTRAINT audit_log_target_type_check;
  ALTER TABLE audit_log ADD CONSTRAINT audit_log_target_type_check
      CHECK (target_type IN ('file','folder','user','permission','share','system','audit','department'));
  ```
- `Department.java`:
  - `rename(String newName)` — trim, length validation, no-op if same.
  - `deactivate()` — `deletedAt = now()`, idempotent.
  - `reactivate()` — `deletedAt = null`, idempotent.
  - `isActive()` — `deletedAt == null`.

### 검증 참조
- `backend/src/test/java/com/ibizdrive/user/UserTest.java` (도메인 테스트 패턴)

### 문서 반영
- 없음 (내부 도메인 변경 — docs 반영은 P6).

---

## P2 — Repository + Service + Events

- [ ] `DepartmentRepository.findAllForAdminPageable(String q, Pageable)` (q nullable, 비활성 포함)
- [ ] `DepartmentRepositoryTest.findAllForAdminPageable_*` (전체 / 검색 / 비활성 포함 / 정렬 / LIKE escape)
- [ ] `AdminDepartmentCreatedEvent` (record)
- [ ] `AdminDepartmentUpdatedEvent` (record — beforeJson, afterJson)
- [ ] `AdminDepartmentDeactivatedEvent` (record)
- [ ] `DepartmentConflictException` (RuntimeException + code "DEPARTMENT_CONFLICT")
- [ ] `AdminDepartmentService.list(Pageable, String q)`
- [ ] `AdminDepartmentService.create(String rawName, UUID actorId)` (충돌 → 409)
- [ ] `AdminDepartmentService.rename(UUID, String, UUID)` (멱등)
- [ ] `AdminDepartmentService.deactivate(UUID, UUID)` (멱등)
- [ ] `AdminDepartmentService.reactivate(UUID, UUID)` (멱등)
- [ ] `AdminDepartmentServiceTest` 매트릭스
- [ ] gate: `./gradlew test --tests "com.ibizdrive.department.*" --tests "*.AdminDepartmentServiceTest"`

### 작업 전 필독
- `backend/src/main/java/com/ibizdrive/admin/AdminUserService.java` (서비스 패턴 1:1 템플릿)
- `backend/src/main/java/com/ibizdrive/admin/AdminUserCreatedEvent.java` (event record 패턴)
- `backend/src/main/java/com/ibizdrive/user/UserRepository.java` (`findAllActivePageable` JPQL 패턴)

### 원본 코드 참조
- `AdminUserService.invite/changeRole/deactivate` — service 메서드 시그니처 + `@Transactional` + `eventPublisher`.
- `UserRepository.findAllActivePageable` — JPQL 패턴.

### 구현 대상
- `backend/src/main/java/com/ibizdrive/department/DepartmentRepository.java` 확장.
- `backend/src/main/java/com/ibizdrive/admin/AdminDepartmentService.java` (NEW).
- `backend/src/main/java/com/ibizdrive/admin/AdminDepartment{Created,Updated,Deactivated}Event.java` (NEW).
- `backend/src/main/java/com/ibizdrive/department/DepartmentConflictException.java` (NEW).
- `backend/src/test/java/com/ibizdrive/department/DepartmentRepositoryTest.java` 확장.
- `backend/src/test/java/com/ibizdrive/admin/AdminDepartmentServiceTest.java` (NEW).

### 검증 참조
- `AdminUserServiceTest` 매트릭스 (멱등/검증실패/예외).

### 문서 반영
- P6에서 일괄.

---

## P3 — Audit infra + Controller + Listener

- [ ] `AuditTargetType.DEPARTMENT("department")` 추가
- [ ] `AuditEventType.ADMIN_DEPARTMENT_CREATED/_UPDATED/_DEACTIVATED` 3개 추가
- [ ] `AdminDepartmentSummaryResponse` (DTO)
- [ ] `AdminDepartmentCreateRequest` + validation
- [ ] `AdminDepartmentPatchRequest` + `isEmpty()`
- [ ] `AdminDepartmentController` — GET/POST/PATCH `/api/admin/departments`
- [ ] `AdminDepartmentAuditListener` — 3 emit (`AFTER_COMMIT`)
- [ ] `errors.ts` (frontend) + `GlobalExceptionHandler`에 `DEPARTMENT_CONFLICT` → 409 매핑
- [ ] `AdminDepartmentControllerTest` (200/400/401/403/404/409)
- [ ] `AdminDepartmentAuditListenerTest`
- [ ] gate: `./gradlew test`

### 작업 전 필독
- `backend/src/main/java/com/ibizdrive/admin/AdminUserController.java` (controller 1:1 템플릿)
- `backend/src/main/java/com/ibizdrive/admin/AdminAuditListener.java` (listener 1:1 템플릿)
- `backend/src/main/java/com/ibizdrive/admin/AdminUserPatchRequest.java` (`isEmpty()` 패턴)
- `backend/src/main/java/com/ibizdrive/audit/AuditTargetType.java` (enum 추가 위치)
- `backend/src/main/java/com/ibizdrive/audit/AuditEventType.java` (enum 추가 위치 — 관리자 그룹)
- `backend/src/main/java/com/ibizdrive/common/error/GlobalExceptionHandler.java`

### 원본 코드 참조
- `AdminUserController.invite/list/patch` — REST 패턴.
- `AdminAuditListener.onAdminUserCreated/Deactivated/RoleChanged` — emit 패턴.

### 구현 대상
- `backend/src/main/java/com/ibizdrive/admin/AdminDepartmentController.java` (NEW).
- `backend/src/main/java/com/ibizdrive/admin/AdminDepartmentCreateRequest.java` (NEW).
- `backend/src/main/java/com/ibizdrive/admin/AdminDepartmentPatchRequest.java` (NEW).
- `backend/src/main/java/com/ibizdrive/admin/AdminDepartmentSummaryResponse.java` (NEW).
- `backend/src/main/java/com/ibizdrive/admin/AdminDepartmentAuditListener.java` (NEW).
- `backend/src/main/java/com/ibizdrive/audit/AuditTargetType.java` 수정.
- `backend/src/main/java/com/ibizdrive/audit/AuditEventType.java` 수정.
- `backend/src/main/java/com/ibizdrive/common/error/GlobalExceptionHandler.java` (DEPARTMENT_CONFLICT 매핑).
- `backend/src/test/java/com/ibizdrive/admin/AdminDepartmentController{,Audit}Test.java` (NEW).

### 검증 참조
- `AdminUserControllerTest`, `AdminAuditListenerTest`.

### 문서 반영
- P6에서 일괄.

---

## P4 — Frontend api + hooks + queryKeys + types

- [x] `frontend/src/types/audit.ts` — `'department'` resource + `'admin.department.created/updated/deactivated'` (P3에서 함께 처리)
- [x] `frontend/src/types/department.ts` — `AdminDepartmentSummary/Page/CreateBody/PatchBody` 추가
- [x] `qk.adminDepartmentsList(page, size, q)` + `invalidations.afterAdminDepartmentChanged`
- [x] `api.adminListDepartments` / `adminCreateDepartment` / `adminUpdateDepartment`
- [x] `useAdminDepartments(page, size, q='')`
- [x] `useAdminCreateDepartment`
- [x] `useAdminUpdateDepartment`
- [x] `useAdminDeactivateDepartment` (semantic wrapper)
- [x] hook 단위 테스트 (검색 키 / 충돌 처리) — `useAdminDepartments.test.tsx`
- [x] wire 테스트 — `lib/api.adminDepartments.test.ts`

> 결정: 4개 hook 파일 분리 대신 `useAdminDepartments.ts` 단일 파일에 list+create+update+deactivate를 모았다 — KISS + 4개 동작이 의미적으로 한 묶음(create+rename+(de)activate). admin-user-mgmt의 분리 패턴과 다르지만 본 트랙은 동작 수가 적어 분리 비용이 더 크다.

### 작업 전 필독
- `frontend/src/lib/api.ts` (`adminListUsers/adminInviteUser/adminUpdateUser` 패턴)
- `frontend/src/lib/queryKeys.ts` (`adminUsersList` 패턴)
- `frontend/src/hooks/useAdminUsers.ts` 등

### 구현 대상
- `frontend/src/lib/api.ts` 확장
- `frontend/src/lib/queryKeys.ts` 확장
- `frontend/src/types/audit.ts` 확장
- `frontend/src/hooks/useAdminDepartments.ts` (NEW) + `useAdminCreateDepartment.ts` (NEW) + `useAdminUpdateDepartment.ts` (NEW) + `useAdminDeactivateDepartment.ts` (NEW)
- `frontend/src/hooks/useAdminDepartments.test.tsx` (NEW)

### 검증 참조
- `useAdminUsers.test.tsx`

### 문서 반영
- P6에서 일괄 (CLAUDE.md §4 계약 파일 표 — `queryKeys.ts` 갱신은 docs/01 §6.1 동기화).

---

## P5 — Frontend page + layout

- [x] `/admin/departments/page.tsx` (NEW) — list + 검색(300ms debounce) + create form (modal 아님 — 인라인) + rename inline + (de)activate + pagination
- [x] `components/admin/AdminSideNav.tsx` — '부서' DEFERRED → ACTIVE_ITEMS (`/admin/departments`, prefix). layout.tsx는 nav 미보유 — nav는 AdminSideNav가 단독.
- [x] `page.test.tsx` (NEW) — 매트릭스 (생성 성공/409, 검색 debounce, rename 성공/409, deactivate, reactivate, 빈 목록/로딩/에러)
- [x] gate: `pnpm test --run` (789/789 GREEN, 99 files), `pnpm typecheck` (clean), `pnpm lint` (clean), `pnpm build` (clean — `/admin/departments` 5.09 kB)

### 작업 전 필독
- `frontend/src/app/admin/users/page.tsx` (UI 패턴 1:1 템플릿)
- `frontend/src/app/admin/users/page.test.tsx`
- `frontend/src/app/admin/layout.tsx`

### 구현 대상
- `frontend/src/app/admin/departments/page.tsx` (NEW)
- `frontend/src/app/admin/departments/page.test.tsx` (NEW)
- `frontend/src/app/admin/layout.tsx` 수정 (메뉴 추가)

### 검증 참조
- `users/page.test.tsx` 매트릭스 (vitest + RTL).

### 문서 반영
- P6에서 일괄 (docs/04 §5).

---

## P6 — Docs sync + closure + PR

- [ ] `docs/02-backend-data-model.md` §2.x departments unique index 명시 + §7.x admin departments endpoint
- [ ] `docs/03-security-compliance.md` §3 ADMIN 부서 CRUD + §4.1 audit 신규 4종 (target + 3 event)
- [ ] `docs/04-admin-operations.md` §5 부서 관리 페이지
- [ ] `BETA-RELEASE.md` §6 metric (44 → 47 enum, emit count) + §7 wording
- [ ] `docs/progress.md` entry
- [ ] `dev/active/admin-department-crud/` → `dev/completed/admin-department-crud/`
- [ ] `dev/process/wave2-t4-2026-05-06.md` 삭제
- [ ] PR 생성 (`feat(admin-department-crud): admin department CRUD + audit emit (Wave 2 T4)`)

### 작업 전 필독
- `docs/02-backend-data-model.md` §7 (admin endpoints 형식)
- `docs/03-security-compliance.md` §3 §4.1
- `docs/04-admin-operations.md` 현재 §5 상태
- `BETA-RELEASE.md` §6 emit metric 표

### 구현 대상
- 위 doc 파일들 직접 수정.
- `dev/completed/admin-department-crud/` 디렉터리로 이동.
- `docs/progress.md`에 closure entry append.

### 검증 참조
- 최종 acceptance criteria 전부 통과 (plan §"Acceptance Criteria").

### 문서 반영
- 본 phase 자체.
