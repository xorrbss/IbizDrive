---
Last Updated: 2026-05-06
---

# Context — admin-user-search-update

## 의존 트랙

- `admin-user-mgmt` (PR #57) — 본 트랙의 직접 부모. AdminUserController/Service/AuditListener 패턴 확립.
- `audit-emit-gap-mapping` (PR #58) — 미emit 9개 deferred 매핑. 본 트랙 closure 시 `ADMIN_USER_UPDATED` 활성 → 미emit 9 → 8.
- `m-admin-entry-rewrite` (PR ADR #21) — invite endpoint + admin shell.
- `auth-must-change-pw` (ADR #21 §2.7) — `User.clearMustChangePassword()` 패턴 (도메인 메서드 + service 호출).

## 핵심 파일 backlink

| 파일 | 역할 | 본 트랙에서 |
|---|---|---|
| `User.java` | 도메인 — `deactivate/reactivate` 이미 존재 | `changeDisplayName(String)` 추가 |
| `UserRepository.java` | `findAllActivePageable` admin 목록 query | admin search 쿼리 추가 |
| `AdminUserService.java` | `list/invite/changeRole/deactivate` | `list(Pageable, String)`로 시그니처 확장, `changeDisplayName/reactivate` 추가 |
| `AdminUserController.java` | GET 목록, POST invite, PATCH | `?q=` + `displayName` + `isActive=true` 모두 처리 |
| `AdminAuditListener.java` | 3 emit | `onAdminUserUpdated` 추가 |
| `AdminUserPatchRequest.java` | `{role, isActive}` | `displayName?` 추가 |
| `AuditEventType.ADMIN_USER_UPDATED` | enum 존재, emit 0 | 첫 emit 활성 |

## 패턴 참조

- `@Async` 없음. PATCH는 동기 (HTTP 응답 대기).
- audit emit은 `@TransactionalEventListener(AFTER_COMMIT)` — JdbcTemplate가 Hibernate auto-flush 미트리거하기 때문.
- self-protection은 service 단계에서 검증 (controller는 단순 dispatch).
- `before/after` JSON은 수동 직렬화 (Jackson 의존 없이) — `AdminAuditListener.onAdminRoleChanged` 패턴.

## 위험 회피

- `searchActive` (share picker)와 admin 검색의 차이: 전자는 `isActive=TRUE` 필터, 후자는 비활성 포함 + soft-delete만 제외. **같은 메서드 재사용 금지**.
- `permission.PermissionService.changeRole` (dead code) vs admin role 변경 — 본 트랙은 후자만.
- `Role.MEMBER/AUDITOR/ADMIN` enum 변경 없음 — frontend mirror도 변경 없음.
