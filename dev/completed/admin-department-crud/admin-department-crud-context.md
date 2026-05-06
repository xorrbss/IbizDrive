---
Last Updated: 2026-05-06
---

# Context — admin-department-crud (Wave 2 — T4)

## SESSION PROGRESS

- 2026-05-06 — bootstrap. plan/context/tasks 생성. 다음: P1 (V8 migration + Department 도메인 메서드).
- 2026-05-06 — P1~P3 (backend) 완료 (uncommitted, 별도 커밋 단위 예정):
  - V9 migration (V8은 password_reset_tokens가 선점) — `idx_departments_name_active` partial unique + audit_log target_type CHECK 갱신.
  - `Department.rename/deactivate/reactivate/isActive` + `DepartmentTest`.
  - `AdminDepartmentService` (list/create/rename/deactivate/reactivate) + `DepartmentRepository.findAllForAdminPageable` + 4 events + `DepartmentConflictException`.
  - `AdminDepartmentController` GET/POST/PATCH `/api/admin/departments`.
  - `AdminDepartmentAuditListener` 3 emit (`AFTER_COMMIT`).
  - `AuditTargetType.DEPARTMENT` + `AuditEventType.ADMIN_DEPARTMENT_CREATED/_UPDATED/_DEACTIVATED`.
  - `GlobalExceptionHandler` DEPARTMENT_CONFLICT → 409 매핑.
- 2026-05-06 — P4~P5 (frontend) 완료:
  - `types/department.ts` — Admin* 타입 4종 추가.
  - `lib/queryKeys.ts` — `adminDepartmentsList(page,size,q)` + `invalidations.afterAdminDepartmentChanged`.
  - `lib/api.ts` — `adminListDepartments/Create/Update` (list q `trim().length>0` 조건부 인코딩, Create/Update CSRF 헤더, 409 코드 envelope 파싱).
  - `hooks/useAdminDepartments.ts` 단일 파일 — list + create + update + deactivate(semantic wrapper). q는 `trim().toLowerCase()` 정규화. 4개 hook 분리 대신 KISS 통합.
  - `app/admin/departments/page.tsx` — CreateSection + ListSection + DepartmentRow + Pagination. 검색 300ms debounce, rename inline form, (de)activate toggle, 409 인라인 에러.
  - `components/admin/AdminSideNav.tsx` — '부서' DEFERRED → ACTIVE_ITEMS (`/admin/departments`, prefix).
  - 테스트: `useAdminDepartments.test.tsx`, `lib/api.adminDepartments.test.ts`, `app/admin/departments/page.test.tsx`.
  - **gate (frontend)**: 789/789 GREEN (99 files), typecheck/lint clean, build clean — `/admin/departments` 5.09 kB First Load JS 119 kB.
- 다음: P6 — backend gradle test 게이트 + docs sync (docs/02 §2.x §7.x, docs/03 §3 §4.1, docs/04 §5, BETA-RELEASE.md §6 §7) + commit phase 분할 + dev/active → dev/completed + PR.

## Current Execution Contract

- worktree: `C:/project/IbizDrive/.claude/worktrees/wave2-t4-admin-department-crud`
- branch: `wave2-t4-admin-department-crud` (master 88e252a 기준)
- session file: `dev/process/wave2-t4-2026-05-06.md`
- 작업 단위: 1 phase = 1 commit 단위 (P1~P6). PR은 P6 closure에서 1회.
- 검증: 각 phase 끝에서 해당 gradle/vitest 명령 GREEN 확인 후 다음 phase로.

## Active phase / task

- **active phase**: P6 — Docs sync + closure + PR.
- **active task**: backend gradle test 게이트 → docs/02 §2.x §7.x + docs/03 §3 §4.1 + docs/04 §5 + BETA-RELEASE.md §6 §7 동기화 → phase 단위 commit 분할 → `dev/active` → `dev/completed` 이동 + 세션 파일 삭제 → PR.

## 다음 세션 읽기 순서

1. `admin-department-crud-plan.md` — phase 지도 + acceptance criteria 확인.
2. `admin-department-crud-tasks.md` — 미완료 phase의 첫 task + 참조 블록 확인.
3. `dev/process/wave2-t4-2026-05-06.md` — 작업 파일 충돌 검사 결과 확인.
4. master의 `admin-user-mgmt` PR #57 산출물 (`AdminUserService.java`, `AdminUserController.java`,
   `AdminAuditListener.java`, `AdminUserCreatedEvent.java`) — 본 트랙의 1:1 템플릿.
5. `backend/src/main/resources/db/migration/V3__audit_log.sql` line 25-26 — `target_type CHECK` 정의 확인.
6. `backend/src/main/resources/db/migration/V7__departments_users_dept.sql` — `departments` 테이블 정의.
7. `dev/process/wave1-t1-2026-05-06.md` — Wave 1 T1 작업 파일 충돌 영역 확인 (frontend api.ts/queryKeys.ts/layout.tsx — append-only).

## 핵심 파일과 역할

### Backend — 신규 트랙 (`com.ibizdrive.admin` 패키지에 추가)
| 파일 | 역할 | 템플릿 |
|---|---|---|
| `AdminDepartmentService` | list/create/rename/deactivate/reactivate | `AdminUserService` |
| `AdminDepartmentController` | GET/POST/PATCH `/api/admin/departments` | `AdminUserController` |
| `AdminDepartmentCreateRequest` | `{name}` body | `AdminInviteUserRequest` |
| `AdminDepartmentPatchRequest` | `{name?, isActive?}` + `isEmpty()` | `AdminUserPatchRequest` |
| `AdminDepartmentSummaryResponse` | `{id, name, isActive, createdAt}` | `AdminUserSummaryResponse` |
| `AdminDepartmentCreatedEvent` | record `(id, actorId, name)` | `AdminUserCreatedEvent` |
| `AdminDepartmentUpdatedEvent` | record `(id, actorId, beforeJson, afterJson)` | `AdminRoleChangedEvent` 변형 |
| `AdminDepartmentDeactivatedEvent` | record `(id, actorId)` | `AdminUserDeactivatedEvent` |
| `AdminDepartmentAuditListener` | 3 emit — `AFTER_COMMIT` | `AdminAuditListener` |
| `DepartmentConflictException` | 409 — service에서 throw, GlobalExceptionHandler에 매핑 | `DuplicateEmailException` |

### Backend — 기존 파일 확장
| 파일 | 변경 |
|---|---|
| `Department.java` | `rename(String)` / `deactivate()` / `reactivate()` 추가 + `isActive()` boolean 도출 (deletedAt 기반 또는 별도 필드 — V7 schema에 `is_active` 없음 → `deletedAt == null`로 도출). |
| `DepartmentRepository.java` | `findAllForAdminPageable(String q, Pageable)` 추가. |
| `AuditTargetType.java` | `DEPARTMENT("department")` 추가. |
| `AuditEventType.java` | `ADMIN_DEPARTMENT_CREATED/_UPDATED/_DEACTIVATED` 3개 추가. |

### Backend — 신규 migration
| 파일 | 내용 |
|---|---|
| `V8__admin_departments.sql` | (1) `idx_departments_name_active` partial unique. (2) `audit_log_target_type_check` 갱신 — `department` 추가. |

### Frontend
| 파일 | 변경 |
|---|---|
| `lib/api.ts` | `adminListDepartments` / `adminCreateDepartment` / `adminUpdateDepartment` 추가. |
| `lib/queryKeys.ts` | `adminDepartmentsList(page, size, q)` 추가. |
| `types/audit.ts` | `'department'` AuditResourceType + `'admin.department.*'` 3종 추가. |
| `hooks/useAdminDepartments.ts` (신규) | TanStack Query list hook. |
| `hooks/useAdminCreateDepartment.ts` (신규) | mutation. |
| `hooks/useAdminUpdateDepartment.ts` (신규) | mutation — rename + isActive 통합. |
| `hooks/useAdminDeactivateDepartment.ts` (신규) | `useAdminUpdateDepartment` wrapping (UX 의도 명시). |
| `app/admin/departments/page.tsx` (신규) | 페이지. |
| `app/admin/layout.tsx` | Departments 링크. |

## 중요한 의사결정

### isActive 도출 — `deletedAt`이냐 별도 컬럼이냐
- V7 `departments` schema에는 `is_active` 컬럼이 없고 `deleted_at` (TIMESTAMPTZ nullable)만 있음.
- 결정: **별도 컬럼 추가 안 함** — `isActive() := deletedAt == null` 메서드로 도출.
- 이유: `User`와 다르게 dept는 admin이 의도적으로 "비활성화" = "soft-delete"가 동등. KISS.
- 결과: `Department.deactivate()` = `this.deletedAt = OffsetDateTime.now()`. `reactivate()` = `this.deletedAt = null`.
- 영향: V7 `idx_users_department` partial index가 `is_active=TRUE AND department_id IS NOT NULL` 기준 — 본 의사결정과 무관 (users 컬럼).

### 충돌 매핑 — `DepartmentConflictException` vs 기존 `DuplicateEmailException`
- 결정: 신규 `DepartmentConflictException` (409 + body `{code:"DEPARTMENT_CONFLICT"}`).
- 이유: email vs name 충돌은 의미 다름. 에러 코드 분리 (CLAUDE.md §3 원칙 12 — 에러 코드는 계약).
- `errors.ts`에 `DEPARTMENT_CONFLICT` 추가 + GlobalExceptionHandler 매핑.

### audit `_UPDATED` 통합 정책 (T1과 동형)
- rename → `_UPDATED` (before/after `{name}`).
- reactivate → `_UPDATED` (before/after `{isActive: false→true}`).
- deactivate → `_DEACTIVATED` (제재 분기, 별도 audit type).
- 이유: T1의 plan §"Audit emit 매핑"과 동일 철학 — `_UPDATED`는 비제재 일반 속성 변경.

### case sensitivity
- `name` unique는 case-sensitive (PostgreSQL default).
- A16 search는 `LOWER(name) LIKE` (case-insensitive).
- 결정: KISS — 같은 이름 변형 ("Dev" vs "dev")은 혼란이지만 v1.x 결정으로 미룸.
- 차후 `LOWER(name)` 또는 collation 변경 시 별도 ADR.

### Wave 1 T1과의 frontend 파일 동시 수정
- `api.ts`/`queryKeys.ts`/`admin/layout.tsx`를 양 트랙 모두 수정 (append-only).
- 결정: T1 머지 시점에 본 트랙 rebase. conflict 가능성은 낮음 (다른 export/key/link 추가).
- 게이트: 본 트랙 PR 전 master에 T1 머지되어 있으면 rebase 1회.

## 빠른 재개 안내

다음 세션에서 작업을 재개할 때:

```
# 1. context 확인
cat dev/active/admin-department-crud/admin-department-crud-tasks.md

# 2. 현재 phase 확인 (위 SESSION PROGRESS)

# 3. P1 시작 시
cd .claude/worktrees/wave2-t4-admin-department-crud/backend
./gradlew test --tests "*.DepartmentTest" --tests "*.NormalizeUtilTest"

# 4. 직전 commit 확인
git log --oneline -5
```

## 백링크

- `docs/02-backend-data-model.md` §7.x (admin departments endpoint — P6에서 추가)
- `docs/03-security-compliance.md` §3 (권한 — ADMIN role 부서 CRUD), §4.1 (audit 신규 4종)
- `docs/04-admin-operations.md` §5 (관리자 부서 페이지)
- `BETA-RELEASE.md` §6 (emit metric), §7 (wording)
- ADR #36 (A16 부서 도메인 도입 — 본 트랙은 ADR #36의 admin 측면 확장, 신규 ADR 발번 0)
- ADR #21 (admin shell — 본 트랙은 admin user mgmt closure의 sibling)
