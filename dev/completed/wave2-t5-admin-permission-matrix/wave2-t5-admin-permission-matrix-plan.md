---
Last Updated: 2026-05-07
---

# Plan — wave2-t5-admin-permission-matrix (Wave 2 — T5)

## 요약

Wave 2 / T5 — **읽기 전용 권한 매트릭스 뷰어** (admin 전용).

현재 권한 grant 조회는 **per-resource** 수준에서만 가능 (`GET /api/folders/:id/permissions`,
M-RP.3 권한 탭). 관리자가 "어떤 subject가 어디에 어떤 preset으로 grant 되어 있는가"를
전사 단위로 audit/조회할 경로가 부재. 본 트랙은 admin-global filter+pagination 가능한
read-only matrix endpoint와 `/admin/permissions` 페이지를 도입한다.

`admin-department-crud` (Wave 2 T4) closure로 dept subject 분기가 unblock됨에 따라
filter dropdown에서 dept도 1급 시민으로 노출 가능.

## 현재 상태 (master 925130e)

### Backend
- `permissions` 테이블 (V5) — `resource_type|file/folder` × `subject_type|user/department/role/everyone` × `preset|read/upload/edit/admin` × optional `expires_at`. `granted_by` FK.
- `PermissionRepository.findEffective(userId, resourceType, resourceId)` — 권한 평가용 재귀 CTE. **admin-global 조회 메서드 없음.**
- `PermissionController` — `POST /api/{resource}/{id}/permissions` (grant) + `DELETE /api/permissions/{id}` (revoke) — per-resource only. `@PreAuthorize("hasPermission(#id, #resource, 'PERMISSION_ADMIN')")` 또는 `hasRole('ADMIN')`.
- `PermissionGrantedEvent` / `PermissionRevokedEvent` audit emit 활성 (`PERMISSION_GRANTED` / `PERMISSION_REVOKED`).
- `PermissionExpirationJob` — 만료된 row hard-delete cron (prod profile에서만 활성).

### Frontend
- `/admin/audit/logs` (M12), `/admin/users` (Wave 1 T1), `/admin/departments` (Wave 2 T4) 활성.
- `/admin/permissions` 페이지 부재.
- `AdminSideNav.DEFERRED_ITEMS`에 '권한' 포함 (deferred 상태).
- M-RP.3 (RightPanel 권한 탭) — per-file/folder 권한 chip view 활성.

### 의존
- `admin-department-crud` (PR #61): dept subject filter 활성화 의존성. ✓ 머지됨.
- `admin-user-mgmt` (PR #57) / `admin-user-search-update` (PR #59): user subject name resolution 시 `users` 조인 패턴 답습.
- `audit-export-endpoint` (PR #60): pagination + filter wire 패턴(특히 `PageResponse`) 답습.

## 목표 상태

### Backend
- **API**: `GET /api/admin/permissions` (`@PreAuthorize("hasRole('ADMIN')")`)
  - Query 파라미터 (모두 optional, 빈 값 무시):
    - `subjectType` ∈ `{user, department, role, everyone}`
    - `subjectId` — UUID(user/dept) 또는 role 문자열(`MEMBER|AUDITOR|ADMIN`). type-specific 검증.
    - `resourceType` ∈ `{file, folder}`
    - `preset` ∈ `{read, upload, edit, admin}`
    - `q` — subject 또는 resource 이름 부분매칭. `trim().toLowerCase()` + LIKE escape.
    - `page` (default 0), `size` (default 20, max 100)
  - 200 응답: `PageResponse<AdminPermissionRowResponse>` (기존 `audit-export-endpoint`/`admin-user-mgmt` 답습).
  - 400 응답: 검증 실패 (불일치 type+id 조합, `size>100`, 알 수 없는 enum 값).

- **DTO** (`AdminPermissionRowResponse` record):
  ```
  id, subjectType, subjectId(nullable), subjectName,
  resourceType, resourceId, resourceName,
  preset, grantedByActorId, grantedByName,
  grantedAt, expiresAt(nullable), isExpired
  ```
  - `subjectName`: user → `displayName`, dept → `name`, role → role 자체, everyone → `"전사"` (i18n 미적용 KISS).
  - `resourceName`: file → `files.name`, folder → `folders.name` (canonical path는 v1.x).
  - `grantedByName`: actor의 `displayName`. 본 user가 비활성/삭제되어도 노출 (audit 정합성).
  - `isExpired`: 서버 계산 (`expiresAt != null && expiresAt <= NOW()`). 만료 row도 표시 — `PermissionExpirationJob` 실행 전 가시화 목적.

- **Service** (`AdminPermissionService` 신규):
  - `list(filters, Pageable)` — DTO 변환 + name resolution.
  - 입력 validation은 controller layer에서 `@Validated` + Bean Validation 또는 service 진입부 수동 검증.

- **Repository 확장** (`PermissionRepository`):
  - `findAllForAdminPageable(filters, Pageable)` — single SQL JOIN으로 N+1 방지. native query 사용:
    - `LEFT JOIN users u ON p.subject_type='user' AND p.subject_id = u.id`
    - `LEFT JOIN departments d ON p.subject_type='department' AND p.subject_id = d.id`
    - `LEFT JOIN folders f ON p.resource_type='folder' AND p.resource_id = f.id`
    - `LEFT JOIN files fi ON p.resource_type='file' AND p.resource_id = fi.id`
    - `INNER JOIN users gu ON p.granted_by = gu.id` (granted_by는 nullable 아님)
    - filter는 동적 WHERE — JPA Specification 또는 wallet of optional clauses(Spring Data `@Query` + `nativeQuery=true` + `IS NULL OR ...` 패턴).
  - 정렬: `ORDER BY p.created_at DESC, p.id DESC` (안정 페이지네이션).
  - soft-delete 정책: `users.deleted_at IS NULL`은 LEFT JOIN이므로 row 자체는 항상 표시 (subject 비활성 → name 표시 시 "(비활성)" 접미). MVP는 plain join, 비활성 처리는 service에서 derive.

- **Audit emit**: 0 (read-only). `ADMIN_PERMISSION_VIEWED`도 미emit (대량 SELECT noise).
- **DB migration**: 0 (V9까지 그대로).

### Frontend
- **신규 파일**:
  - `app/admin/permissions/page.tsx` — Server Component shell + Client interactive child. FilterBar(5종 select + q input) + Table + Pagination.
  - `hooks/useAdminPermissions.ts` — single hook: list + filter state + 300ms debounce on q. invalidation 미지원 (read-only).
  - `lib/api.adminPermissions.test.ts` (test) — `adminListPermissions(filters)` 추가.
  - `app/admin/permissions/page.test.tsx` (test).
  - `hooks/useAdminPermissions.test.tsx` (test).

- **확장**:
  - `lib/api.ts` — `adminListPermissions` 함수 추가. 빈 값 query param skip (T4 `adminListDepartments`의 `q.trim().length>0` 패턴 답습).
  - `lib/queryKeys.ts` — `adminPermissionsList(filters)` 키 팩토리. invalidations entry 미추가 (read-only).
  - `types/permission.ts` — `AdminPermissionRow`, `AdminPermissionFilters` 타입 추가 (백엔드 mirror).
  - `components/admin/AdminSideNav.tsx` — `'권한' DEFERRED → ACTIVE_ITEMS`에 `{ label: '권한', href: '/admin/permissions', match: 'prefix' }` 추가, `DEFERRED_ITEMS`에서 제거.

- **UX**:
  - 컬럼: subject(type 배지 + name) / resource(type 배지 + name) / preset 배지 / grantedBy / grantedAt / expiresAt(없으면 "—", 만료된 row는 빨간 배지 "만료됨").
  - 빈 결과 → empty state ("조건에 일치하는 권한이 없습니다.")
  - 에러 → 409/500 시 inline 에러 노출 (M-RP.3 패턴 답습).
  - 모바일 반응형 — out of scope (admin 영역 전반 일관, T1/T4 동형).

### Audit 이벤트
- 신규 enum 0. `AUDITOR/ADMIN`은 audit_log에서 grant/revoke 이력을 이미 추적 가능.

### 에러 / 검증
- 400 코드는 기존 `VALIDATION_FAILED` 재사용 (docs/02 §8). 신규 코드 0.
- 401/403은 Spring Security 기본 경로.

## 비목표 (Non-goals)

다음은 본 트랙 스코프 밖. 후속 트랙에서 다룸.

| 항목 | 사유 |
|---|---|
| admin grant/revoke UI | resource picker(폴더 트리) UX 무거움. `/permissions/bulk` (v1.x) 영역. |
| share 매트릭스 | `shares`는 별도 테이블 (docs/02 §2.7). 본 트랙은 `permissions` 단일 테이블만. 통합 뷰는 v1.x. |
| 권한 일괄 변경 | docs/04 §2 `/permissions/bulk` v1.x deferred. |
| 권한 프리셋 템플릿 | docs/04 §2 `/permissions/templates` v1.x deferred. |
| canonical path 노출 | folder/file의 full path 계산. MVP는 folder.name / file.name만. |
| CSV export | T2(`audit-export-endpoint`)가 audit_log 대상. 권한 export는 v1.x. |
| dept 후손 자동 매칭 | A16 ADR #37에 명시된 v1.x deferred. |

## 핵심 위험 / 결정 포인트

| 위험 | 완화 |
|---|---|
| native query 동적 WHERE — SQL injection | `:param` placeholder + Spring Data binding으로 모든 입력 처리. q는 `LIKE escape`(`\` `%` `_`) + lowercase. UUID는 Spring binding이 자동 캐스팅. |
| LEFT JOIN으로 row 폭증 (subject_id가 user+dept 양쪽 ID 공간 겹칠 가능) | subject_type 별 분기 LEFT JOIN — `AND p.subject_type='user'` 조건 join에 포함하므로 dept_id로 user를 매치하지 않음. |
| 만료된 row 표시 정책 | service에서 isExpired 계산 + filter 미제공(만료/유효 모두 표시). 향후 `?excludeExpired=true` 추가 여지 남김. v1에서 미도입. |
| pagination 안정성 | `ORDER BY created_at DESC, id DESC`로 tie-break. cursor pagination은 v1.x. |
| name resolution 누락 (dangling FK) | `granted_by`는 NOT NULL FK이므로 INNER JOIN 안전. user/dept는 LEFT JOIN — name이 NULL이면 `"(삭제됨)"` 표시. |
| AdminSideNav prefix match — `/admin/permissions/123` 같은 자식 라우트 충돌 | 본 트랙은 자식 라우트 없음. prefix match 안전. |
| Wave 1 T1, T4와 frontend 파일 동시 수정 (`api.ts`, `queryKeys.ts`, `AdminSideNav.tsx`) | T1, T4 모두 머지됨 — rebase conflict 0. append-only edits. |

## 검증 (Acceptance Criteria)

- Backend `./gradlew test` GREEN — 신규 unit (service/controller) + E2E (V1~V9 + 시드 grant + 인가 + filter combination) 포함.
- Frontend `pnpm test --run` GREEN — 신규 hook/api/page 테스트 포함.
- `pnpm typecheck && pnpm lint && pnpm build` exit 0.
- 신규 `@PreAuthorize` 미보호 mutation 0 (mvp-qa-security P2.3 정책 유지).
- `/admin/permissions` 페이지 5종 filter (subjectType / subjectId / resourceType / preset / q) 각각 단독 + 결합 시 예상 행만 반환.
- AdminSideNav '권한' 항목이 ACTIVE 영역에 표시되고, `/admin/permissions` 진입 시 `aria-current="page"` 활성.
- BETA-RELEASE.md §7 deferred 항목에서 `/admin/permissions` 제거. emit coverage metric은 변동 없음 (read-only).

## Phase / Commit 분할

| Phase | 단위 | 산출물 |
|---|---|---|
| **P1** | Backend repo | `PermissionRepository.findAllForAdminPageable` + 단위 테스트 (filter 조합 + 정렬 + soft-delete LEFT JOIN). |
| **P2** | Backend service+controller+DTO | `AdminPermissionService` + `AdminPermissionController` + `AdminPermissionRowResponse` + service unit + controller MockMvc 테스트 (인가 + 400 + 200). |
| **P3** | Backend E2E | `AdminPermissionE2ETest` (실제 V1~V9 + 시드 user/dept/grant + filter 8 case + 인가 401/403). |
| **P4** | Frontend types/api/queryKeys/hook | `types/permission.ts`, `lib/api.ts`, `lib/queryKeys.ts`, `hooks/useAdminPermissions.ts` + 테스트 3종. |
| **P5** | Frontend page | `app/admin/permissions/page.tsx` + `AdminSideNav.tsx` swap + 테스트. |
| **P6** | Docs sync + closure + PR | gradle test 게이트 → docs/02 §7 admin endpoint table + docs/03 §3.5 (note "admin matrix endpoint") + docs/04 §2 (트리 갱신) + BETA §7 deferred 갱신 + progress.md → dev/active → dev/completed → PR. |

각 phase = 1 commit. PR은 P6 closure에서 1회.

## 다음 세션 읽기 순서

1. `wave2-t5-admin-permission-matrix-plan.md` (본 문서) — phase 지도 + acceptance criteria.
2. `wave2-t5-admin-permission-matrix-tasks.md` — 미완료 phase의 첫 task + 참조.
3. `wave2-t5-admin-permission-matrix-context.md` — 세션 진행 로그.
4. master `permissions/PermissionController.java` + `PermissionRepository.java` — 본 트랙 1차 의존 파일.
5. master `admin/AdminDepartmentController.java` + `AdminDepartmentService.java` — 패턴 1:1 템플릿.
6. master `audit/AuditQueryController.java` (PR #60) — pagination + filter wire 답습.
