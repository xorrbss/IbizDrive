---
Last Updated: 2026-05-05
---

# Plan — admin-user-mgmt

## 요약

`m-admin-entry-rewrite` 트랙(POST /api/admin/users invite) 후속. 사용자 **목록 조회 / role 변경 / 비활성화** endpoint + `/admin/users` 페이지 확장 + audit emit 3종(`ADMIN_USER_DEACTIVATED`, `ADMIN_ROLE_CHANGED`) 활성. ADR 신규 발번 0 — 기존 ADR #21(admin shell) + ADR #41(auth-pages) 자연 확장.

## 현재 상태 분석 (master b0fdffd)

### Backend
- `AdminUserController.java`: `POST /api/admin/users` only (invite + email). GET/PATCH 미존재.
- `AdminUserService.java`: `invite()` only.
- `AdminAuditListener.java`: `onAdminUserCreated()` only — `ADMIN_USER_CREATED` emit.
- `User.java`: `isActive` 필드 존재하나 setter 부재. `changeRoleTo()` 메서드 존재.
- `UserRepository.java`: `searchActive(pattern, pageable)` 존재 — 페이징 패턴은 있으나 admin 목록용은 없음.
- `AuditEventType.java`: `ADMIN_USER_UPDATED`, `ADMIN_USER_DEACTIVATED`, `ADMIN_ROLE_CHANGED` enum 정의됨, emit 0.

### Frontend
- `/admin/users/page.tsx`: invite 폼만. 목록/상세/role 변경 UI 부재.
- `lib/api.ts`: `adminInviteUser()` only. admin GET/PATCH client 부재.
- `lib/queryKeys.ts`: `qk.users()` (검색용)만, `qk.admin*` namespace 부재.
- `hooks/useAdminInviteUser.ts` 패턴 존재 — 동일 형태로 hook 3개 추가 가능.

### Audit emit coverage
- BETA-RELEASE.md §6: 32/42 (76%) 표기. 본 트랙 closure 시 +2 (`ADMIN_USER_DEACTIVATED`, `ADMIN_ROLE_CHANGED`) → **34/42 (81%)**.
- `ADMIN_USER_UPDATED`는 displayName 변경 메서드 미구현 → 본 트랙 scope 밖 (deferred).
- `ADMIN_QUOTA_CHANGED`는 quota 시스템 미구현 → 본 트랙 scope 밖.

## 목표 상태

### Backend
- `GET /api/admin/users?page=0&size=50`: `Page<AdminUserSummary>` 반환. ADMIN guard. soft-delete 제외.
- `PATCH /api/admin/users/{id}`: `{role?, isActive?}` 부분 수정. ADMIN guard. self-protection.
- `AdminUserService`:
  - `list(Pageable)` — 페이징 + soft-delete 제외
  - `changeRole(targetId, newRole, actorId)` — self-demote 차단(`actorId == targetId && newRole != ADMIN` → 403) + `AdminRoleChangedEvent` publish
  - `deactivate(targetId, actorId)` — self-deactivate 차단(`actorId == targetId` → 403) + `AdminUserDeactivatedEvent` publish
- `AdminUserDeactivatedEvent` / `AdminRoleChangedEvent` (record DTO).
- `AdminAuditListener.onAdminUserDeactivated()` / `.onAdminRoleChanged()` — `AFTER_COMMIT` + `try/catch` swallow (기존 `onAdminUserCreated` 패턴).
- `User.deactivate()` / `User.reactivate()` 메서드.
- `UserRepository.findAllActivePageable(Pageable)` `@Query` (deletedAt IS NULL ORDER BY createdAt DESC).

### Frontend
- `useAdminUsers(page, size)` query hook.
- `useAdminChangeRole()` mutation hook — `qk.adminUsers()` invalidate.
- `useAdminDeactivateUser()` mutation hook — `qk.adminUsers()` invalidate.
- `qk.admin()` / `qk.adminUsers()` / `qk.adminUsersList(page, size)` 추가.
- `api.getAdminUsers(page, size)` / `api.updateAdminUser(id, body)`.
- `/admin/users/page.tsx`: 기존 invite 폼 + 사용자 목록 테이블(email/displayName/role select/active toggle/createdAt). 자기 자신 row는 role select/deactivate 비활성화(UX).

### Docs
- `docs/02 §7.4`: GET/PATCH 명세 완성 (현 미완성).
- `docs/04 §4.1~4.3`: status를 v1.x deferred → 본 트랙 closure 활성화로 정정.
- `BETA-RELEASE.md` §6: 32/42 → 34/42 (81%) + emit cross-link 2종 추가.
- `BETA-RELEASE.md` §7: admin frontend 표현에 `사용자 목록/role 변경/비활성화` 활성 추가.

## Phase별 실행 지도

### P1 — Backend domain (User + Repository)
- `User.deactivate()` / `User.reactivate()` 메서드 + `UserTest` 케이스.
- `UserRepository.findAllActivePageable(Pageable)` + `UserRepositoryTest` 케이스.
- 게이트: `./gradlew test --tests "*.UserTest" --tests "*.UserRepositoryTest"` GREEN.

### P2 — Backend service + events
- `AdminUserDeactivatedEvent.java` (record: userId, actorId).
- `AdminRoleChangedEvent.java` (record: userId, actorId, oldRole, newRole).
- `AdminUserService.list(Pageable)` / `changeRole()` / `deactivate()`.
  - self-protection: 자기 자신 role 변경(ADMIN→non-ADMIN) / 자기 자신 deactivate → `IllegalStateException` (403 매핑).
- `AdminUserServiceTest`: list/changeRole(non-self/self/non-admin)/deactivate(non-self/self) 매트릭스.

### P3 — Backend controller + audit listener
- `AdminUserController.@GetMapping` (`?page=&size=`) + `@PatchMapping("/{id}")` (`{role?, isActive?}` body).
  - 응답 DTO `AdminUserSummary { id, email, displayName, role, isActive, createdAt, lastLoginAt }`.
- `AdminAuditListener.onAdminUserDeactivated()` / `.onAdminRoleChanged()`.
- `AdminUserControllerTest`: 200/400(invalid role)/401/403(member or self-protect)/404 매트릭스.
- `AdminAuditListenerTest` 신규 — emit 검증.

### P4 — Frontend hooks + api + queryKeys
- `qk.admin()` / `qk.adminUsers()` / `qk.adminUsersList(page, size)`.
- `api.getAdminUsers(page, size)` / `api.updateAdminUser(id, patch)`.
- `useAdminUsers` query hook + `useAdminChangeRole` / `useAdminDeactivateUser` mutation hooks (invalidate `qk.adminUsers()`).

### P5 — Frontend page
- `/admin/users/page.tsx`: 기존 invite 폼 유지 + 사용자 목록 테이블 추가.
- 자기 row는 role select / deactivate 버튼 비활성화 (`useMe` 조회 → `currentUserId === row.id`).
- vitest: 렌더 / role 변경 / deactivate / self-row 비활성화 / 401·403 표시.

### P6 — Docs sync + closure + PR
- `docs/02 §7.4` GET/PATCH spec 완성.
- `docs/04 §4` 활성 표시.
- `BETA-RELEASE.md §6` 34/42 + §7 admin frontend wording.
- `docs/progress.md` 본 트랙 closure entry.
- `dev/active/admin-user-mgmt/` → `dev/completed/`.
- 2-3 commits (P1+P2 backend / P3 controller / P4+P5 frontend / P6 closure 분리 또는 단일).

## Acceptance Criteria

- `cd backend && ./gradlew test` BUILD SUCCESSFUL — 신규 테스트 15+ 추가.
- `cd frontend && pnpm test --run` GREEN — 신규 테스트 4+ 추가, 기존 738 회귀 0.
- `pnpm typecheck && pnpm lint && pnpm build` exit 0.
- `GET /api/admin/users` 200 with `Page<AdminUserSummary>` (멤버 토큰: 403, 미인증: 401).
- `PATCH /api/admin/users/{id}` 200 (admin), 403 (self-demote / self-deactivate / non-admin), 404 (없는 id).
- audit_log에 `ADMIN_ROLE_CHANGED` / `ADMIN_USER_DEACTIVATED` 행 INSERT 검증.
- `/admin/users` 페이지 — 목록 + role select 변경 + deactivate 버튼 동작 + 자기 row 비활성화.
- BETA-RELEASE.md `34/42 (81%)` 정합.

## 검증 게이트

- backend: junit (sliced WebMvcTest + service unit + repository test).
- frontend: vitest + typecheck + lint.
- CI 1회 PR push 후 GREEN 확인.

## 리스크 / 완화

| 리스크 | 완화 |
|---|---|
| 마지막 ADMIN 본인 강등 → ADMIN 0 사태 | service 단 self-protection + controller 검증 + service 테스트 |
| `ADMIN_ROLE_CHANGED` vs `PERMISSION_CHANGED` 혼재 | admin user role 변경 = `ADMIN_ROLE_CHANGED`. 파일/폴더 permission 변경 = `PERMISSION_CHANGED` (현 PermissionAuditListener 유지). 의미 분리 명확화 |
| pagination 인덱스 부재 | `users (created_at DESC) WHERE deleted_at IS NULL`은 V1 인덱스로 커버. 신규 인덱스 0 |
| Frontend useMe 호출 race | 기존 `useMe` 훅 재사용. 페이지 진입 시 `<AdminGuard>`가 이미 보장 |
