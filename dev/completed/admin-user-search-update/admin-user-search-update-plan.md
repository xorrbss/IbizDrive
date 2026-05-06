---
Last Updated: 2026-05-06
---

# Plan — admin-user-search-update (Wave 1 — T1)

## 요약

`admin-user-mgmt` 트랙(PR #57) 후속 — Wave 1 Quick Win T1. 사용자 목록 **검색(`?q=`)**,
**재활성(`PATCH isActive:true` 노출)**, **displayName 편집(`PATCH displayName`)** + `ADMIN_USER_UPDATED`
audit emit 첫 활성화. ADR 신규 발번 0 — 기존 ADR #21 자연 확장.

## 현재 상태 (master 88e252a)

- `AdminUserPatchRequest`: `{role, isActive}`만, `isActive=true`는 controller에서 400 거부 (line 112-114).
- `AdminUserService`: `list(Pageable)` (검색 미지원), `changeRole`, `deactivate`. `reactivate` / `changeDisplayName` 부재.
- `User`: `reactivate()` 메서드는 이미 존재(line 227-229) — 호출 경로만 추가하면 됨.
- `UserRepository.findAllActivePageable`: 검색 미지원. `searchActive`는 share picker용으로 `isActive=TRUE` 필터링.
- `AdminAuditListener`: `onAdminUserCreated/Deactivated/RoleChanged` 3종. `onAdminUserUpdated` 부재.
- `AuditEventType.ADMIN_USER_UPDATED`: enum 정의됨, emit 0 (audit-emit-gap-mapping deferred 9개 중 1개).
- frontend `/admin/users/page.tsx`: 목록 + role select + deactivate. 검색 input/displayName 편집/재활성 부재.

## 목표 상태

### Backend
- `GET /api/admin/users?q=alice&page=0&size=50`: q optional, 빈/null이면 전체. q 있으면 email/displayName 부분 매칭(case-insensitive). soft-delete 제외, 비활성 포함.
- `PATCH /api/admin/users/{id}`: body `{role?, isActive?, displayName?}`. 빈 객체 400. `isActive=true` 허용(reactivate). `displayName` trim + 1~100자 검증.
- `AdminUserService`:
  - `list(Pageable, String q)` — q null/blank이면 `findAllActivePageable`, 아니면 admin search query
  - `changeDisplayName(UUID targetId, String newName, UUID actorId)` — trim + length 검증, 같은 값이면 멱등(no event), 다르면 `AdminUserUpdatedEvent` publish
  - `reactivate(UUID targetId, UUID actorId)` — 이미 active면 멱등, 다르면 `AdminUserDeactivatedEvent` 재사용 X — `AdminUserReactivatedEvent` 신설 X. 같은 audit type 분기 vs 새 enum: **새 enum 미사용, `ADMIN_USER_UPDATED` 통합 metadata `{isActive: false→true}`** 로 처리
- `User.changeDisplayName(String)` — 도메인 메서드 + 길이/blank 검증
- `UserRepository`: 검색 query 추가 (admin용 — 비활성 포함)
- `AdminUserUpdatedEvent` (record: userId, actorId, before json, after json) — generic update event. metadata로 displayName/isActive 변경 추적
- `AdminAuditListener.onAdminUserUpdated` — `ADMIN_USER_UPDATED` audit emit

### Frontend
- `useAdminUsers(page, size, q)` — q 파라미터 추가, 키에 q 포함
- `api.adminListUsers(page, size, q?)` — URL에 `&q=` 추가 (q 비어있으면 생략)
- `AdminUserPatchBody.displayName?` 추가
- `qk.adminUsersList(page, size, q)` — q를 키 일부로
- `/admin/users/page.tsx`:
  - ListSection 상단 검색 input (debounced 300ms) — input 변경 시 `setQ` + `setPage(0)`
  - UserRow displayName 셀 — 클릭하면 input 모드, blur 또는 Enter로 PATCH
  - 비활성 row의 `비활성화` 버튼 → `재활성화` 버튼으로 변환

### Audit emit 매핑
- `displayName 변경` → `ADMIN_USER_UPDATED` + before/after `{displayName}` JSON
- `isActive: false → true` (reactivate) → `ADMIN_USER_UPDATED` + before/after `{isActive}` JSON
- `isActive: true → false` (deactivate) → 기존 `ADMIN_USER_DEACTIVATED` 유지 (의미 분리: deactivate는 명시적 제재 의미)
- `role 변경` → 기존 `ADMIN_ROLE_CHANGED` 유지

> 의도: `ADMIN_USER_UPDATED`는 "비제재 일반 속성 변경"의 우산. deactivate는 별도 카테고리(제재).

### Docs
- `docs/02 §7.4`: PATCH body에 `displayName?` + GET에 `?q=` 추가
- `docs/04 §4`: 검색/재활성/displayName 활성 표시
- `BETA-RELEASE.md §6`: 35 emit → 36 emit, 미emit 9 → 8 (`ADMIN_USER_UPDATED` 활성)
- `BETA-RELEASE.md §7` line 113: "사용자 검색/재활성/displayName 편집(`ADMIN_USER_UPDATED` emit)/quota..." 표현에서 quota만 v1.x로 남김

## Phase

### P1 — Backend domain (User + Repository)
- `User.changeDisplayName(String)` + 검증 (blank/length<=100). UserTest 신규.
- `UserRepository.findForAdminPageable(String pattern, Pageable)` — q nullable: null/blank → 전체, 아니면 LIKE LOWER pattern. UserRepositoryTest 신규.
- gate: `./gradlew test --tests "*.UserTest" --tests "*.UserRepositoryTest"` GREEN.

### P2 — Backend events + service
- `AdminUserUpdatedEvent (userId, actorId, beforeJson, afterJson)`.
- `AdminUserService`:
  - `list(Pageable, String q)` 시그니처 변경 + 기존 `list(Pageable)` deprecate 또는 호출자 마이그레이션.
  - `changeDisplayName(UUID, String, UUID)` 신규.
  - `reactivate(UUID, UUID)` 신규.
- `AdminUserServiceTest` 매트릭스 보강.
- gate: `./gradlew test --tests "*.AdminUserServiceTest"`.

### P3 — Backend controller + listener
- `AdminUserPatchRequest`: `displayName?` 추가, `isEmpty()` 갱신.
- `AdminUserController`:
  - `list(?q=, page=, size=)` 파라미터 추가 → `service.list(pageable, q)`.
  - `patch`: reactivate 가드 제거. `displayName!=null` → `changeDisplayName`. `isActive=true` → `reactivate`. `isActive=false` → `deactivate`. `role!=null` → `changeRole`.
- `AdminAuditListener.onAdminUserUpdated` (`AFTER_COMMIT`).
- `AdminUserControllerTest` 매트릭스: 200/400/401/403/404 + 새 케이스.
- `AdminAuditListenerTest` (이미 존재 시 보강 또는 신규).
- gate: `./gradlew test`.

### P4 — Frontend api + hooks + queryKeys
- `qk.adminUsersList(page, size, q='')` 시그니처 변경.
- `api.adminListUsers(page, size, q?='')`.
- `AdminUserPatchBody.displayName?`.
- `useAdminUsers(page, size, q='')`.
- 기존 호출부 마이그레이션.

### P5 — Frontend page + tests
- 검색 input(debounce 300ms) + displayName inline edit + 재활성 버튼.
- 자기 row는 displayName 편집 가능, role/active 토글은 비활성 (기존 self-protection 유지).
- vitest: 검색 / displayName 편집 / 재활성 / 자기 row 비활성화 / 401·403.

### P6 — Docs sync + closure + PR
- docs/02 §7.4, docs/04 §4, BETA-RELEASE.md §6/§7.
- `dev/active/admin-user-search-update/` → `dev/completed/`.
- progress.md entry.
- PR.

## Acceptance Criteria

- `cd backend && ./gradlew test` GREEN — 신규 테스트 10+ 추가.
- `cd frontend && pnpm test --run` GREEN — 신규 테스트 4+ 추가.
- `pnpm typecheck && pnpm lint && pnpm build` exit 0.
- `GET /api/admin/users?q=alice` 200 — admin only — 비활성 사용자 포함.
- `PATCH .../{id}` `{displayName: "X"}` 200 + `ADMIN_USER_UPDATED` audit row.
- `PATCH .../{id}` `{isActive: true}` 200 + `ADMIN_USER_UPDATED` audit row + `is_active=true`.
- `PATCH .../{id}` `{isActive: false}` 200 + `ADMIN_USER_DEACTIVATED` audit row (기존).
- BETA §6 36/44 (~82%) 정합.

## 검증 게이트

- backend: junit (`UserTest`, `UserRepositoryTest`, `AdminUserServiceTest`, `AdminUserControllerTest`, `AdminAuditListenerTest`).
- frontend: vitest + typecheck + lint + build.

## 리스크 / 완화

| 리스크 | 완화 |
|---|---|
| `ADMIN_USER_UPDATED` semantic 모호 — 어떤 변경에 emit? | "비제재 일반 속성 변경" 범주로 명시 (displayName, reactivate). role/deactivate는 기존 enum 유지 — plan §"Audit emit 매핑" 명문화 |
| 호출 경로 변경 (`list(Pageable)` → `list(Pageable, String q)`) 회귀 | overload 추가 또는 단일 시그니처 + null-safe. 결정: 단일 시그니처 + q nullable. 모든 호출자가 단일 — 회귀 위험 낮음 |
| Frontend 검색 debounce 미적용 시 backend storm | 300ms debounce + Enter 키만 즉시 트리거 |
| q LIKE-escape 부재 시 wildcard 폭주 | 호출자(repository default 메서드)에서 `\` escape + `%`·`_` 이스케이프 후 wildcard wrap |
