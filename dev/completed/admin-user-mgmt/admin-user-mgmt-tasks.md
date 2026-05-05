---
Last Updated: 2026-05-05
---

# Tasks — admin-user-mgmt

## Phase별 상태

- [x] P1 — Backend domain (User.deactivate + Repository pagination)
- [x] P2 — Backend service + events (AdminUserService.list/changeRole/deactivate)
- [x] P3 — Backend controller + audit listener (GET/PATCH + onDeactivated/onRoleChanged)
- [x] P4 — Frontend hooks + api + queryKeys
- [x] P5 — Frontend /admin/users page (list + actions)
- [ ] P6 — Docs sync + closure + PR

## P1 — Backend domain

- [ ] `User.deactivate()` — `this.isActive = false`
- [ ] `User.reactivate()` — `this.isActive = true`
- [ ] `UserRepository.findAllActivePageable(Pageable)` — `@Query("SELECT u FROM User u WHERE u.deletedAt IS NULL ORDER BY u.createdAt DESC")`
- [ ] `UserTest.deactivate_setsIsActiveToFalse()`
- [ ] `UserTest.reactivate_setsIsActiveToTrue()`
- [ ] `UserRepositoryTest.findAllActivePageable_excludesSoftDeleted_orderedByCreatedAtDesc()`

### 작업 전 필독
- `backend/src/main/java/com/ibizdrive/user/User.java` (line 161-209) — 기존 setter 패턴
- `backend/src/main/java/com/ibizdrive/user/UserRepository.java` — searchActive @Query 패턴

### 원본 코드 참조
- `User.java` — `changeRoleTo()` (line 186), `clearMustChangePassword()` (line 209)
- `UserRepository.java` — `searchActive(pattern, pageable)` (line 49)

### 구현 대상
- `User.java`: 메서드 2개 추가
- `UserRepository.java`: @Query 메서드 1개 추가
- `UserTest.java`: 케이스 2개 추가
- `UserRepositoryTest.java`: 케이스 1개 추가 (또는 신규 파일)

### 검증 참조
- `./gradlew test --tests "com.ibizdrive.user.UserTest"` GREEN
- `./gradlew test --tests "com.ibizdrive.user.UserRepositoryTest"` GREEN

### 문서 반영
- 본 phase 단독 docs 갱신 없음. P6 closure에서 일괄.

## P2 — Backend service + events

- [ ] `AdminUserDeactivatedEvent.java` — record(UUID userId, UUID actorId)
- [ ] `AdminRoleChangedEvent.java` — record(UUID userId, UUID actorId, Role oldRole, Role newRole)
- [ ] `AdminUserService.list(Pageable)` — return `Page<AdminUserSummary>` (또는 `Page<User>` + DTO mapping in controller)
- [ ] `AdminUserService.changeRole(UUID targetId, Role newRole, UUID actorId)`:
  - target 없으면 `EntityNotFoundException` (404)
  - `actorId == targetId && newRole != ADMIN` → `IllegalStateException("self-demote forbidden")` (403)
  - `user.changeRoleTo(newRole)` + `userRepository.save` + `events.publishEvent(new AdminRoleChangedEvent(...))`
- [ ] `AdminUserService.deactivate(UUID targetId, UUID actorId)`:
  - target 없으면 404
  - `actorId == targetId` → `IllegalStateException("self-deactivate forbidden")` (403)
  - 이미 inactive면 idempotent (no-op + no event)
  - `user.deactivate()` + save + publishEvent
- [ ] `AdminUserServiceTest`:
  - list_returnsPageOrderedByCreatedAtDesc_excludesSoftDeleted
  - changeRole_otherAdmin_publishesEvent
  - changeRole_self_demote_throwsIllegalState
  - changeRole_self_to_admin_idempotent_allowed (or noop — confirm)
  - changeRole_targetNotFound_throwsEntityNotFound
  - deactivate_otherUser_publishesEvent
  - deactivate_self_throwsIllegalState
  - deactivate_alreadyInactive_idempotent_noEvent
  - deactivate_targetNotFound_throwsEntityNotFound

### 작업 전 필독
- `backend/src/main/java/com/ibizdrive/admin/AdminUserService.java` — invite() 패턴
- `backend/src/main/java/com/ibizdrive/admin/AdminUserCreatedEvent.java` — event record 패턴
- `backend/src/test/java/com/ibizdrive/admin/AdminUserServiceTest.java` — invite 테스트 패턴

### 원본 코드 참조
- `AdminUserService.invite()` — `events.publishEvent(...)` 패턴
- `User.changeRoleTo(Role)` (line 186)

### 구현 대상
- 신규 파일 2개 (events).
- `AdminUserService.java`: 메서드 3개 추가.
- `AdminUserServiceTest.java`: 케이스 9개 추가.

### 검증 참조
- `./gradlew test --tests "com.ibizdrive.admin.AdminUserServiceTest"` GREEN

### 문서 반영
- 본 phase 단독 docs 갱신 없음.

## P3 — Backend controller + audit listener

- [ ] `AdminUserController.@GetMapping`:
  - `?page=0&size=50` (default)
  - `Pageable` 주입 또는 page/size 수동 매핑
  - 응답: `Page<AdminUserSummaryResponse>` (record { id, email, displayName, role, isActive, createdAt, lastLoginAt })
- [ ] `AdminUserController.@PatchMapping("/{id}")`:
  - body: `{role?: Role, isActive?: Boolean}` (둘 중 하나 이상 필수, 둘 다면 role 먼저 적용)
  - actorId 추출 (`@AuthenticationPrincipal` 또는 SecurityContext)
  - `role != null && role != current` → service.changeRole
  - `isActive == false && current.isActive` → service.deactivate
  - 응답: `AdminUserSummaryResponse`
- [ ] `AdminUserController` exception handler — `IllegalStateException` → 403, `EntityNotFoundException` → 404 (또는 controller advice 활용)
- [ ] `AdminAuditListener.onAdminUserDeactivated(AdminUserDeactivatedEvent)` — `@TransactionalEventListener(AFTER_COMMIT)` + emit `ADMIN_USER_DEACTIVATED`
- [ ] `AdminAuditListener.onAdminRoleChanged(AdminRoleChangedEvent)` — `@TransactionalEventListener(AFTER_COMMIT)` + emit `ADMIN_ROLE_CHANGED` + metadata(`{oldRole, newRole}`)
- [ ] `AdminUserControllerTest` 추가:
  - list_admin_returns200WithPagedBody
  - list_member_returns403
  - list_unauthenticated_returns401
  - patchRole_admin_returns200
  - patchRole_self_demote_returns403
  - patchRole_targetNotFound_returns404
  - patchActive_deactivate_returns200
  - patchActive_self_returns403
  - patch_emptyBody_returns400
  - patch_invalidRole_returns400
- [ ] `AdminAuditListenerTest`:
  - onAdminUserDeactivated_emitsAuditLog
  - onAdminRoleChanged_emitsAuditLogWithMetadata
  - listener_swallowsRuntimeException (audit 실패가 mutation 실패로 이어지지 않음)

### 작업 전 필독
- `AdminUserController.java` — invite 패턴
- `AdminAuditListener.java` — onAdminUserCreated 패턴
- `AdminUserControllerTest.java` — WebMvcTest + MockBean 패턴

### 원본 코드 참조
- `AdminAuditListener.onAdminUserCreated` (line 38-55)
- `AdminUserController.invite` (line 42-50)
- `AuditEvent` constructor signature

### 구현 대상
- `AdminUserController.java`: 2개 endpoint 메서드 + exception handler
- `AdminAuditListener.java`: 2개 listener 메서드
- `AdminUserControllerTest.java`: 10 케이스 추가
- `AdminAuditListenerTest.java`: 신규 파일, 3 케이스

### 검증 참조
- `./gradlew test --tests "com.ibizdrive.admin.*"` GREEN
- `./gradlew test` 전체 GREEN

### 문서 반영
- 본 phase 단독 docs 갱신 없음.

## P4 — Frontend hooks + api + queryKeys

- [ ] `frontend/src/lib/queryKeys.ts`:
  - `qk.admin: () => [...qk.all, 'admin'] as const`
  - `qk.adminUsers: () => [...qk.admin(), 'users'] as const`
  - `qk.adminUsersList: (page, size) => [...qk.adminUsers(), 'list', page, size] as const`
- [ ] `frontend/src/lib/api.ts`:
  - `getAdminUsers(page, size): Promise<PageResponse<AdminUserSummary>>`
  - `updateAdminUser(id, patch: { role?, isActive? }): Promise<AdminUserSummary>`
- [ ] `frontend/src/types/admin.ts` (or extend existing): `AdminUserSummary`, `AdminUserPatch`, `Role` mirror
- [ ] `frontend/src/hooks/useAdminUsers.ts`:
  - `useQuery({ queryKey: qk.adminUsersList(page, size), queryFn })`
- [ ] `frontend/src/hooks/useAdminChangeRole.ts`:
  - `useMutation({ mutationFn: api.updateAdminUser({role}), onSuccess: invalidate qk.adminUsers })`
- [ ] `frontend/src/hooks/useAdminDeactivateUser.ts`:
  - `useMutation({ mutationFn: api.updateAdminUser({isActive: false}), onSuccess: invalidate qk.adminUsers })`

### 작업 전 필독
- `frontend/src/lib/api.ts` — `adminInviteUser` 패턴 (line 1100-1115 영역)
- `frontend/src/lib/queryKeys.ts` (line 87-95) — namespace 추가 위치
- `frontend/src/hooks/useAdminInviteUser.ts` — mutation 패턴

### 원본 코드 참조
- 기존 query hook 예: `frontend/src/hooks/useFolderTree.ts` 등

### 구현 대상
- queryKeys.ts: 3 라인
- api.ts: 메서드 2개 + 타입 import
- types/admin.ts: 타입 2-3개
- hooks/* 신규 파일 3개

### 검증 참조
- `pnpm typecheck` exit 0
- `pnpm lint` exit 0

### 문서 반영
- 본 phase 단독 docs 갱신 없음.

## P5 — Frontend /admin/users page

- [ ] `frontend/src/app/admin/users/page.tsx`:
  - 기존 invite 폼 위/아래 사용자 목록 섹션 추가 (`<section>` 분리, semantic structure)
  - 테이블 columns: email / displayName / role(select) / isActive(toggle button) / createdAt
  - useMe로 현재 actor id 확보 → self row는 role select disabled + deactivate button hidden 또는 disabled
  - role 변경 select onChange → useAdminChangeRole.mutate
  - deactivate button onClick → confirm() → useAdminDeactivateUser.mutate
  - 401/403 응답 시 inline error
  - pagination next/prev 버튼 (단순 카운트 기반, 50/page)
- [ ] `frontend/src/app/admin/users/page.test.tsx` 추가 케이스:
  - 사용자 목록 렌더 (3개 row)
  - role 변경 select 호출 → useAdminChangeRole 호출 + invalidate
  - deactivate 버튼 클릭 → confirm → useAdminDeactivateUser 호출
  - self row — role select disabled + deactivate hidden
  - 빈 결과 — empty state 메시지

### 작업 전 필독
- `frontend/src/app/admin/users/page.tsx` (현재 invite form)
- `frontend/src/app/admin/users/page.test.tsx` — 기존 테스트 패턴
- `frontend/src/hooks/useMe.ts`

### 원본 코드 참조
- 기존 page.tsx — invite form (`useAdminInviteUser` mutateAsync)

### 구현 대상
- page.tsx: 사용자 목록 섹션 추가 (≈80-120 라인)
- page.test.tsx: 케이스 5개 추가

### 검증 참조
- `pnpm test --run --reporter=basic frontend/src/app/admin/users/page.test.tsx` GREEN
- `pnpm typecheck && pnpm lint` exit 0
- `pnpm build` exit 0

### 문서 반영
- 본 phase 단독 docs 갱신 없음.

## P6 — Docs sync + closure + PR

- [ ] `docs/02-backend-data-model.md §7.4`:
  - `GET /api/admin/users` spec 완성 (request, response, errors, audit)
  - `PATCH /api/admin/users/{id}` spec 완성
- [ ] `docs/04-admin-operations.md §4.1~4.3`:
  - 4.1 사용자 목록: v1.x deferred → 활성 (`/admin/users` 페이지)
  - 4.2 사용자 상세: 본 트랙 미포함 명시 (목록 + role/active만)
  - 4.3 사용자 비활성화: 활성 (`PATCH /api/admin/users/{id}` + `ADMIN_USER_DEACTIVATED`)
- [ ] `BETA-RELEASE.md`:
  - header Last Updated 2026-05-05 (이미 5일자 — 동일 일자 재closure 가능)
  - Source 라인에 `admin-user-mgmt` 추가
  - §6 audit emit `32 emit (76%)` → `34 emit (81%)` + `ADMIN_USER_DEACTIVATED` / `ADMIN_ROLE_CHANGED` cross-link
  - §7 admin frontend 표현에 `사용자 목록 + role 변경 + 비활성화 활성` 추가, v1.x scope 갱신 (admin user CRUD 잔여 = displayName edit + quota)
- [ ] `docs/progress.md`: 본 트랙 closure entry 최상단
- [ ] `dev/active/admin-user-mgmt/` → `dev/completed/admin-user-mgmt/`
- [ ] `dev/process/admin-user-mgmt.md` 삭제
- [ ] commit + push + PR

### 작업 전 필독
- 직전 트랙 progress entry (beta-release-sync 2026-05-05)
- `BETA-RELEASE.md` §6 / §7 현행

### 구현 대상
- docs 4개 수정
- progress.md 추가
- dev-docs archive 이동

### 검증 참조
- `gh pr checks` GREEN
- `gh pr view` mergeable=MERGEABLE
- BETA-RELEASE.md `34/42 (81%)` 정합 grep

### 문서 반영
- progress.md 본 entry 자체.
