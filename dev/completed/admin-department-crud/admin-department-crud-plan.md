---
Last Updated: 2026-05-06
---

# Plan — admin-department-crud (Wave 2 — T4)

## 요약

Wave 2 / T4 — 부서 admin CRUD. 현재 `Department`는 share-picker용 read-only entity (A16,
ADR #36)만 갖고, admin이 직접 부서를 만들거나 이름을 바꾸거나 비활성화할 경로가 없다.
본 트랙은 `admin-user-mgmt` PR #57 패턴을 답습해 backend CRUD + audit emit + frontend
`/admin/departments` 페이지를 도입한다. T5 (권한 매트릭스 UI)의 dept subject 분기를
unblock하는 선결 트랙.

## 현재 상태 (master 88e252a)

### Backend
- `Department.java` — `id, name, createdAt, deletedAt`만 노출. setter/도메인 메서드 없음 (A16 read-only 가정).
- `DepartmentRepository.java` — `searchActive(pattern, Pageable)` 1개 메서드. admin list/findById 부재.
- `DepartmentSearchController.java` — `GET /api/departments/search`만 (인증 사용자 모두 가능, share picker용).
- V7 schema (`departments`) — soft-delete 컬럼 보유, `app_user GRANT` 완료, **`UNIQUE(name)` 부재**.
- V3 audit `target_type CHECK`: 7값 (`file/folder/user/permission/share/system/audit`) — `department` 미포함.
- `AuditTargetType` enum — 7값. `AuditEventType` — 44값, `ADMIN_DEPARTMENT_*` 부재.

### Frontend
- `/admin/users/page.tsx` — admin user mgmt 페이지 (PR #57, T1 진행 중 보강).
- `/admin/departments` 페이지 부재.
- `frontend/src/types/audit.ts` — backend mirror, `department` target/`admin.department.*` event 부재.

### 의존
- `admin-user-mgmt` (PR #57): controller/service/listener/event record 패턴 확립 — 본 트랙 1:1 답습.
- `audit-emit-gap-mapping` (PR #58): BETA §6 metric 정정 — 본 트랙 closure 시 emit 추가됨에 따라 metric 갱신.
- Wave 1 T1 (`admin-user-search-update`): 별도 worktree에서 진행 — 코드 충돌 0 (User vs Department 트랙 분리).

## 목표 상태

### Backend
- **DB**: V8 migration —
  - `CREATE UNIQUE INDEX idx_departments_name_active ON departments(name) WHERE deleted_at IS NULL` (CLAUDE.md §3 원칙 6)
  - `ALTER audit_log_target_type_check` — `department` 추가 (8값)
- **Domain (`Department`)**: `rename(String)` / `deactivate()` / `reactivate()` 추가. trim + 1~100자 검증, blank 거부.
- **Repository (`DepartmentRepository`)**: `findAllForAdminPageable(String q, Pageable)` 추가 (q nullable, 비활성 포함, soft-delete 제외).
- **Service (`AdminDepartmentService`)** — 신규:
  - `list(Pageable, String q)` — 비활성 포함, soft-delete 제외. q는 LIKE-escape + lowercase.
  - `create(String rawName, UUID actorId)` — trim+검증, 충돌 시 `DepartmentConflictException` (409 매핑). save → `AdminDepartmentCreatedEvent` publish.
  - `rename(UUID, String, UUID)` — 멱등 (같은 이름이면 no-op + event 미발행). 다르면 `AdminDepartmentUpdatedEvent` publish (before/after JSON `{name}`).
  - `deactivate(UUID, UUID)` — 이미 비활성이면 멱등. 활성 → 비활성 시 `AdminDepartmentDeactivatedEvent` publish.
  - `reactivate(UUID, UUID)` — 이미 활성이면 멱등. 비활성 → 활성 시 `AdminDepartmentUpdatedEvent` publish (before/after `{isActive}`).
- **Controller (`AdminDepartmentController`)** — 신규:
  - `GET /api/admin/departments?q=&page=&size=` (`@PreAuthorize("hasRole('ADMIN')")`).
  - `POST /api/admin/departments` body `{name}` → 201.
  - `PATCH /api/admin/departments/{id}` body `{name?, isActive?}` → 200. 빈 body 400.
- **Audit**:
  - `AuditTargetType.DEPARTMENT("department")` 추가.
  - `AuditEventType.ADMIN_DEPARTMENT_CREATED("admin.department.created")` / `_UPDATED("admin.department.updated")` / `_DEACTIVATED("admin.department.deactivated")` 추가 — 3개.
  - `AdminDepartmentAuditListener` (`AFTER_COMMIT`) — 3 emit 메서드.
  - 의도: rename + reactivate → `_UPDATED`, deactivate → `_DEACTIVATED` (제재 분기), 신규 → `_CREATED`. T1의 user audit 매핑과 동형.

### Frontend
- `api.adminListDepartments(page, size, q?)`, `adminCreateDepartment({name})`, `adminUpdateDepartment(id, body)`.
- `qk.adminDepartmentsList(page, size, q)`.
- hooks: `useAdminDepartments`, `useAdminCreateDepartment`, `useAdminUpdateDepartment`, `useAdminDeactivateDepartment` (+ reactivate는 `useAdminUpdateDepartment` 재사용 또는 분리 — 일관성 위해 별도 훅).
- `/admin/departments/page.tsx` — list + 검색 input(300ms debounce) + Create modal + Rename inline edit + Deactivate/Reactivate 버튼.
- `/admin/layout.tsx` — Departments 메뉴 추가.
- `frontend/src/types/audit.ts` — 신규 wire 4종 (`department` target + `admin.department.*` 3종).

### Docs
- `docs/02-backend-data-model.md` §7.x — `/api/admin/departments` 추가. §2.x — `departments` UNIQUE index 명시.
- `docs/03-security-compliance.md` §3 — 부서 CRUD 권한 (ADMIN 전용). §4.1 — 신규 audit 4종.
- `docs/04-admin-operations.md` §5 — 부서 관리 페이지.
- `BETA-RELEASE.md` §6 — emit 카운트 갱신 (44 → 47 enum, emit 활성 수 갱신). §7 — wording.

## Phase

### P1 — DB migration + Domain
- V8 migration: `idx_departments_name_active` partial unique index + `audit_log_target_type_check` 갱신.
- `Department.rename(String)` / `deactivate()` / `reactivate()` + 검증.
- `DepartmentTest` 도메인 단위 (rename trim/length/blank, idempotent state transitions).
- gate: `cd backend && ./gradlew test --tests "*.DepartmentTest" --tests "*.NormalizeUtilTest"` GREEN.

### P2 — Repository + Service + Events
- `DepartmentRepository.findAllForAdminPageable(String q, Pageable)`.
- `DepartmentRepositoryTest.findAllForAdminPageable_*` (전체/검색/비활성 포함/soft-delete 제외).
- `AdminDepartmentCreatedEvent` / `AdminDepartmentUpdatedEvent` / `AdminDepartmentDeactivatedEvent` (record).
- `AdminDepartmentService` (list/create/rename/deactivate/reactivate).
- `DepartmentConflictException` — 409 매핑.
- `AdminDepartmentServiceTest` — 매트릭스 (충돌/멱등/검증 실패).
- gate: `./gradlew test --tests "com.ibizdrive.department.*" --tests "*.AdminDepartmentServiceTest"`.

### P3 — Audit infra + Controller + Listener
- `AuditTargetType.DEPARTMENT` 추가 + `AuditEventType.ADMIN_DEPARTMENT_*` 3개 추가.
- `AdminDepartmentSummaryResponse` (`{id, name, isActive, createdAt}`).
- `AdminDepartmentCreateRequest` / `AdminDepartmentPatchRequest`.
- `AdminDepartmentController` — GET/POST/PATCH.
- `AdminDepartmentAuditListener` — 3 emit.
- `AdminDepartmentControllerTest` — 200/400/401/403/404/409 매트릭스.
- `AdminDepartmentAuditListenerTest`.
- gate: `./gradlew test`.

### P4 — Frontend api + hooks + queryKeys + types
- `frontend/src/types/audit.ts` 신규 wire 4종.
- `qk.adminDepartmentsList`.
- `api.adminListDepartments` / `adminCreateDepartment` / `adminUpdateDepartment`.
- hooks 4종 + 단위 테스트.

### P5 — Frontend page + layout
- `/admin/departments/page.tsx` — list + search input + create modal + rename inline + (de)activate.
- `/admin/layout.tsx` — Departments 링크 추가.
- `page.test.tsx` — 검색/생성/rename/(de)activate/401·403/충돌 토스트 매트릭스.
- gate: `cd frontend && pnpm test --run && pnpm typecheck && pnpm lint && pnpm build`.

### P6 — Docs sync + closure + PR
- `docs/02 §2.x §7.x`, `docs/03 §3 §4.1`, `docs/04 §5`, `BETA-RELEASE.md §6 §7`, frontend types/audit.ts.
- `dev/active/admin-department-crud/` → `dev/completed/`.
- `docs/progress.md` entry.
- `dev/process/wave2-t4-2026-05-06.md` 삭제.
- PR.

## Acceptance Criteria

- `cd backend && ./gradlew test` GREEN — 신규 테스트 ≥15.
- `cd frontend && pnpm test --run && pnpm typecheck && pnpm lint && pnpm build` exit 0 — 신규 테스트 ≥6.
- `GET /api/admin/departments?q=` 200 — admin only — 비활성 포함.
- `POST /api/admin/departments` `{name:"Dev"}` 201 → `ADMIN_DEPARTMENT_CREATED` audit row.
- `POST /api/admin/departments` `{name:"Dev"}` 중복 → 409 `DEPARTMENT_CONFLICT`.
- `PATCH /api/admin/departments/{id}` `{name:"NewName"}` 200 → `ADMIN_DEPARTMENT_UPDATED` audit row, before/after JSON.
- `PATCH /api/admin/departments/{id}` `{isActive:false}` 200 → `ADMIN_DEPARTMENT_DEACTIVATED` audit row.
- `PATCH /api/admin/departments/{id}` `{isActive:true}` 200 → `ADMIN_DEPARTMENT_UPDATED` audit row.
- DB 직접 `INSERT INTO departments(name='X')` 두 번 — 두 번째 row가 unique violation으로 거부.
- audit_log INSERT with `target_type='department'` — V8 CHECK 갱신 후 통과.

## 검증 게이트

- backend: junit (`DepartmentTest`, `DepartmentRepositoryTest`, `AdminDepartmentServiceTest`, `AdminDepartmentControllerTest`, `AdminDepartmentAuditListenerTest`).
- frontend: vitest + typecheck + lint + build.
- 수동: PostgreSQL `\d departments` — partial unique index 확인.

## 리스크 / 완화

| 리스크 | 완화 |
|---|---|
| V3 audit CHECK 변경 마이그레이션 — 기존 row와 호환? | `target_type` 컬럼 값은 추가되는 8번째 enum이므로 기존 row 영향 없음. ALTER ... ADD CONSTRAINT 방식 (DROP/ADD 트랜잭션 단일 단위). |
| Department `name` 정규화 정책 — A16 search는 LOWER 매칭 | unique index는 case-sensitive(`name`). admin이 "Dev"와 "dev"를 별도 부서로 만들 수 있음. KISS — case-insensitive 강제는 v1.x 결정으로 미룸. UI에서 사용자에게 같은 이름 변형 노출되도록 list ORDER BY name. |
| audit listener 추가 시 `target_type='department'`로 INSERT — 기존 V3 CHECK 위반 | V8 migration이 P1에서 함께 박힘. test에서도 V8 적용된 schema로 실행 (Flyway auto). |
| Department reactivate UX — UI에서 비활성 row 보이는지 | list가 비활성 포함 + 비활성 row를 시각적으로 구분 (회색) + 재활성 버튼. T1과 동형. |
| Wave 1 T1 frontend 파일 (`api.ts`, `queryKeys.ts`, `admin/layout.tsx`) 동시 수정 | 두 트랙 모두 append-only export/key/link만 — merge 시 git auto-merge. T1 머지 후 본 트랙 rebase에서 conflict 발생 가능성 낮음. T1 머지 시점에 sync. |
| `AdminDepartmentUpdatedEvent` 의 before/after JSON 직렬화 — name/isActive 둘 다 노출 | `AdminAuditListener.onAdminRoleChanged` 패턴 답습 — Jackson 의존 없이 수동 직렬화. metadata 필드만 다름 (rename일 땐 `{name}`, reactivate일 땐 `{isActive}`). 분기는 service 단계에서 결정 → event payload에 명시 필드만 set. |
