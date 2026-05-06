---
Last Updated: 2026-05-07
---

# Tasks — wave2-t5-admin-permission-matrix (Wave 2 — T5)

## Phase 진행 표

- [ ] P1 — Backend repo
- [ ] P2 — Backend service+controller+DTO
- [ ] P3 — Backend E2E
- [ ] P4 — Frontend types/api/queryKeys/hook
- [ ] P5 — Frontend page + sidebar swap
- [ ] P6 — Docs sync + closure + PR

---

## P1 — Backend repo

**참조**: `permission/PermissionRepository.java` line 50~103 (`findEffective` 재귀 CTE), `V5__permissions.sql` (CHECK 제약).

- [ ] V5 schema sql 확인 — role 타입 subject의 `subject_id` 저장 형태 (UUID? 아니면 enum 매핑?). plan 위험 §item 해소.
- [ ] `PermissionRepository.findAllForAdminPageable(filters, Pageable)` 추가:
  - native query, `LEFT JOIN users u ON p.subject_type='user' AND p.subject_id = u.id`,
    `LEFT JOIN departments d ON p.subject_type='department' AND p.subject_id = d.id`,
    `LEFT JOIN folders f ON p.resource_type='folder' AND p.resource_id = f.id`,
    `LEFT JOIN files fi ON p.resource_type='file' AND p.resource_id = fi.id`,
    `INNER JOIN users gu ON p.granted_by = gu.id`.
  - 동적 WHERE: 각 filter `(:param IS NULL OR p.column = :param)` 패턴.
  - q 처리: `(:q IS NULL OR LOWER(u.display_name) LIKE :q ESCAPE '\' OR LOWER(d.name) LIKE :q ESCAPE '\' OR LOWER(f.name) LIKE :q ESCAPE '\' OR LOWER(fi.name) LIKE :q ESCAPE '\')`. 호출자가 `%` wrap + escape 처리.
  - 정렬: `ORDER BY p.created_at DESC, p.id DESC`.
  - 반환 타입: `Page<AdminPermissionRowProjection>` (interface projection) — DTO mapper는 service에서.
- [ ] `AdminPermissionRowProjection` interface 추가 (또는 single-row DTO 직접 반환). 어떤 방식이 simpler한지 평가 후 결정.
- [ ] 단위 테스트 (`PermissionRepositoryAdminTest` 또는 기존 `PermissionRepositoryTest` 확장):
  - filter 단독 4종 (subjectType / resourceType / preset / q) 각각 grant 시드와 expectation.
  - filter 결합 2종.
  - 정렬 안정성 1 case (동일 created_at에서 id 역순).
  - LEFT JOIN — soft-deleted user의 grant row가 결과에 포함되는지 (name NULL 허용).
- [ ] gate: `cd backend && ./gradlew test --tests "*PermissionRepository*" --no-daemon` GREEN.
- [ ] commit (P1): `feat(admin-permission-matrix): repository.findAllForAdminPageable + tests`.

## P2 — Backend service+controller+DTO

**참조**: `admin/AdminDepartmentService.java`, `admin/AdminDepartmentController.java`, `audit/AuditQueryController.java` (filter wire).

- [ ] `AdminPermissionRowResponse` record:
  ```
  id, subjectType, subjectId(nullable),
  subjectName(nullable, "(삭제됨)" 시 호출자 책임),
  resourceType, resourceId, resourceName(nullable),
  preset, grantedByActorId, grantedByName,
  grantedAt, expiresAt(nullable), isExpired(boolean)
  ```
- [ ] `AdminPermissionFilters` record (controller가 raw query string 검증 후 service에 전달).
- [ ] `AdminPermissionService.list(filters, Pageable)`:
  - filter 정규화: q `trim().toLowerCase() + LIKE-escape + '%' wrap`. 빈 문자열 → null.
  - subjectId 검증: subjectType=`user`/`department` → UUID 파싱, `role` → enum 검증, `everyone` → null 강제.
  - subjectId-only(without subjectType) 입력은 400 — type-id mismatch 차단.
  - repo 호출 → DTO mapping → isExpired 계산.
- [ ] `AdminPermissionController.list` (`@GetMapping("/api/admin/permissions")`):
  - `@PreAuthorize("hasRole('ADMIN')")`.
  - Pageable: `page` default 0, `size` default 20 max 100 (controller에서 cap).
  - 응답 `PageResponse<AdminPermissionRowResponse>` (T4와 동형 reuse 또는 신규 record).
- [ ] 단위 테스트:
  - `AdminPermissionServiceTest` — filter 검증 (불일치 type+id 400), q 정규화, isExpired 계산, name resolution.
  - `AdminPermissionControllerTest` (MockMvc) — 인가 403 (MEMBER), 200, 400 (size>100, type-id 불일치).
- [ ] gate: `cd backend && ./gradlew test --tests "*AdminPermission*" --no-daemon` GREEN.
- [ ] commit (P2): `feat(admin-permission-matrix): service + controller + DTO + tests`.

## P3 — Backend E2E

**참조**: `admin/AdminDepartmentControllerE2ETest`(있다면) / `audit/AuditExportE2ETest.java` (시드 + 인가 패턴).

- [ ] `AdminPermissionE2ETest`:
  - 시드: ADMIN 1, MEMBER 1, dept 1, folder 1, file 1, grant 5종 (user/dept/role/everyone × preset 다양).
  - 인가 case: ANONYMOUS → 401 또는 302 (Spring Security 정책 따름), MEMBER → 403, ADMIN → 200.
  - filter case 8: subjectType only / subjectId(user/dept) / resourceType / preset / q (subject 이름 / resource 이름 부분일치) / 결합 2종.
  - 만료 case: 과거 expiresAt → isExpired=true, expiresAt=null → false.
  - pagination: size=2 page=0,1 → tie-break 정렬 일관.
- [ ] gate: `cd backend && ./gradlew test --tests "*AdminPermissionE2ETest" --no-daemon` GREEN.
- [ ] commit (P3): `feat(admin-permission-matrix): E2E test (filter + auth + expiry)`.

## P4 — Frontend types/api/queryKeys/hook

**참조**: `lib/api.ts` 에서 `adminListDepartments`, `lib/queryKeys.ts` 에서 `adminDepartmentsList`, `hooks/useAdminDepartments.ts` (T4 templates).

- [ ] `types/permission.ts` 확장:
  - `AdminPermissionRow` (백엔드 DTO 1:1 mirror).
  - `AdminPermissionFilters` — 8 filter 필드, 모두 optional.
- [ ] `lib/api.ts` — `adminListPermissions(filters: AdminPermissionFilters): Promise<PageResponse<AdminPermissionRow>>`:
  - 빈 값 query param skip (`q.trim().length>0`, subjectId/subjectType undefined 시 skip).
  - GET, no CSRF header (read-only).
  - 401 → throw `UnauthorizedError`, 400 → envelope code 파싱.
- [ ] `lib/queryKeys.ts` — `adminPermissionsList(filters)` 키 팩토리. invalidations entry 미추가.
- [ ] `hooks/useAdminPermissions.ts` — single hook:
  - filters state (5 select + q text), 300ms debounce on q.
  - `useQuery` w/ `enabled: true`, `keepPreviousData: true` (페이지 전환 깜빡임 완화).
- [ ] 테스트:
  - `lib/api.adminPermissions.test.ts` — 빈 값 skip, 401 throw, 400 envelope.
  - `hooks/useAdminPermissions.test.tsx` — filter 변경 시 query key 변화 + debounce.
- [ ] gate (frontend partial): `cd frontend && pnpm test --run hooks/useAdminPermissions lib/api.adminPermissions` GREEN.
- [ ] commit (P4): `feat(admin-permission-matrix): frontend types + api + hook + tests`.

## P5 — Frontend page + sidebar swap

**참조**: `app/admin/departments/page.tsx` (T4 page template).

- [ ] `app/admin/permissions/page.tsx`:
  - FilterBar 섹션: subjectType select, subjectId input(UUID 또는 role 자동 인식 — type=role 시 select 노출), resourceType select, preset select, q input.
  - Table 섹션: 컬럼 6종. 빈 결과 empty state. 만료 row "만료됨" 빨간 배지.
  - Pagination 섹션: prev/next + page X / Y (T4 동형).
  - aria — 서버 페이지 랜덤 접근 시 키보드 a11y는 T1/T4 수준 (가상화 없음 — virtualized 미적용).
- [ ] `components/admin/AdminSideNav.tsx`:
  - `ACTIVE_ITEMS`에 `{ label: '권한', href: '/admin/permissions', match: 'prefix' as const }` 추가.
  - `DEFERRED_ITEMS`에서 `'권한'` 제거.
- [ ] 테스트:
  - `app/admin/permissions/page.test.tsx` — 렌더, filter 입력 → API 호출 mock, empty state, 만료 배지, 401 redirect 동작 (T4 동형).
- [ ] gate (frontend full): `cd frontend && pnpm test --run && pnpm typecheck && pnpm lint && pnpm build` 모두 exit 0.
- [ ] commit (P5): `feat(admin-permission-matrix): page + sidebar swap + tests`.

## P6 — Docs sync + closure + PR

- [ ] gate (backend full): `cd backend && ./gradlew test --no-daemon` BUILD SUCCESSFUL.
- [ ] docs sync (4개 파일):
  - `docs/02-backend-data-model.md` §7 admin endpoint table — `GET /api/admin/permissions` row 추가.
  - `docs/03-security-compliance.md` §3.5 — admin matrix endpoint note 추가 (optional, plan 참고).
  - `docs/04-admin-operations.md` §2 트리 — `/permissions` 라인을 active로 swap (서브트리는 v1.x 유지). 활성 라우트 목록 갱신.
  - `BETA-RELEASE.md`:
    - 헤더 트랙 closure 추가 (admin-permission-matrix Wave 2 T5).
    - §6 audit emit metric — read-only이므로 변동 없음 (그대로 유지). 단, "admin frontend" 줄에 `/admin/permissions` 활성 표기 추가.
    - §7 deferred 항목 — admin frontend 줄에서 `권한` 제외 (active 이동).
- [ ] `docs/progress.md` — 본 트랙 closure entry 추가 (T4와 동형 형식: 범위 / 산출 / 검증 / 다음 세션 컨텍스트).
- [ ] `dev/active/wave2-t5-admin-permission-matrix/` → `dev/completed/wave2-t5-admin-permission-matrix/`로 이동.
- [ ] 모든 phase commit 확인 (총 6 커밋).
- [ ] 전체 gate 1회 더: backend `./gradlew test`, frontend `pnpm test --run && pnpm typecheck && pnpm lint && pnpm build`. GREEN.
- [ ] PR 생성 (`feat(admin-permission-matrix): admin permission matrix viewer (Wave 2 T5)`).
- [ ] commit (P6): `chore(admin-permission-matrix): docs sync + dev-docs archive (Wave 2 T5)`.
