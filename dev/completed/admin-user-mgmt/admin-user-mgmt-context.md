---
Last Updated: 2026-05-05
---

# Context — admin-user-mgmt

## SESSION PROGRESS

- 2026-05-05 세션 시작 — beta-release-sync (PR #56) 머지 직후.
- 본 트랙 dev-docs bootstrap (이 파일 포함 4개).
- P1 완료 — `User.deactivate/reactivate` + `UserRepository.findAllActivePageable` (BUILD SUCCESSFUL).
- P2 완료 — `AdminUserService.list/changeRole/deactivate` + 2 events + 2 exceptions + handler (10 service 테스트 GREEN).
- P3 완료 — `AdminUserController.@GetMapping/@PatchMapping` + 2 listener 메서드 + 2 DTO + `AdminBadPatchException` (admin 패키지 BUILD SUCCESSFUL).
- P4 완료 — `api.adminListUsers/adminUpdateUser` + `qk.adminUsers/adminUsersList` + `useAdminUsers/useAdminUpdateUser` + 2 hook tests + api wire test (12 frontend 테스트 GREEN).
- P5 완료 — `/admin/users` page 확장 (목록 테이블 + role select + 비활성 버튼 + 페이지네이션) + 12 page 테스트 GREEN. 전체 frontend suite 758/758 GREEN, typecheck/lint clean.
- 현재 active phase: P6 (docs sync + closure + PR).

## Current Execution Contract

- 신규 ADR 0. ADR #21(admin shell) + ADR #41(auth-pages) 자연 확장.
- 신규 mutation enum 0 — 기존 `ADMIN_USER_DEACTIVATED`, `ADMIN_ROLE_CHANGED` enum 활성화.
- frontend `/admin/users` 단일 페이지 확장. 신규 라우트 0.
- Self-protection 필수 (last-admin 0 사태 방지) — service 단 + controller 검증.
- TDD: 각 phase는 테스트 GREEN 확인 후 다음 phase.

## 현재 active phase / task

- **P1 — Backend domain (User + Repository)** (in-progress)

## 다음 세션 읽기 순서

1. `dev/active/admin-user-mgmt/admin-user-mgmt-plan.md` — 범위/목표/phase
2. `dev/active/admin-user-mgmt/admin-user-mgmt-tasks.md` — 체크박스 + 참조
3. `backend/src/main/java/com/ibizdrive/admin/AdminUserController.java` (현재 invite only)
4. `backend/src/main/java/com/ibizdrive/admin/AdminAuditListener.java` (현재 onAdminUserCreated only)
5. `backend/src/main/java/com/ibizdrive/user/User.java` (`isActive` 필드 setter 부재)
6. `frontend/src/app/admin/users/page.tsx` (현재 invite form only)

## 핵심 파일과 역할

| 파일 | 역할 |
|---|---|
| `backend/.../admin/AdminUserController.java` | REST endpoint — invite + GET/PATCH 추가 |
| `backend/.../admin/AdminUserService.java` | service — list/changeRole/deactivate 추가 |
| `backend/.../admin/AdminAuditListener.java` | event listener — onDeactivated/onRoleChanged 추가 |
| `backend/.../admin/AdminUserDeactivatedEvent.java` | NEW event DTO |
| `backend/.../admin/AdminRoleChangedEvent.java` | NEW event DTO |
| `backend/.../user/User.java` | domain — deactivate/reactivate 메서드 |
| `backend/.../user/UserRepository.java` | findAllActivePageable @Query |
| `frontend/src/lib/queryKeys.ts` | qk.admin/adminUsers namespace |
| `frontend/src/lib/api.ts` | getAdminUsers/updateAdminUser |
| `frontend/src/hooks/useAdminUsers.ts` | NEW query hook |
| `frontend/src/hooks/useAdminChangeRole.ts` | NEW mutation hook |
| `frontend/src/hooks/useAdminDeactivateUser.ts` | NEW mutation hook |
| `frontend/src/app/admin/users/page.tsx` | 목록 테이블 추가 (invite form 유지) |

## 중요한 의사결정

1. **신규 ADR 발번 거부** — 본 트랙은 기존 ADR 후속. 새 결정 0.
2. **`ADMIN_ROLE_CHANGED` vs `PERMISSION_CHANGED` 의미 분리** — admin user 자체의 role(MEMBER/AUDITOR/ADMIN) 변경 = `ADMIN_ROLE_CHANGED`. 파일/폴더 permission grant/revoke = `PERMISSION_CHANGED` (현행 유지).
3. **Self-protection은 service 단에 강제** — controller에서 actor 추출 후 service에 actorId 인자로 넘김. last-ADMIN 0 사태 방지.
4. **`ADMIN_USER_UPDATED` (displayName 변경)는 본 트랙 scope 밖** — 현 invite 후 displayName 변경 메서드 미존재. v1.x 별도 트랙. emit 카운트 +2만(`DEACTIVATED`+`ROLE_CHANGED`).
5. **`ADMIN_QUOTA_CHANGED`는 quota 시스템 미구현으로 본 트랙 scope 밖**.

## 빠른 재개 안내

```bash
# 1. plan/tasks 확인
cat dev/active/admin-user-mgmt/admin-user-mgmt-{plan,tasks}.md

# 2. 현재 backend 상태 확인
grep -n "ADMIN_USER_" backend/src/main/java/com/ibizdrive/audit/AuditEventType.java
grep -n "@PostMapping\|@GetMapping\|@PatchMapping" backend/src/main/java/com/ibizdrive/admin/AdminUserController.java

# 3. Phase 진행
# P1 → P2 → P3 → P4 → P5 → P6
cd backend && ./gradlew test --tests "*.UserTest" --tests "*.UserRepositoryTest"  # P1
```

## 재개 시 주의

- self-protection 검증을 service 단에 두고 controller는 actorId 추출/전달만. controller 단 검증 중복 금지.
- `AdminAuditListener.onAdminUserDeactivated/.onAdminRoleChanged`는 기존 `onAdminUserCreated` 패턴(`@TransactionalEventListener(AFTER_COMMIT)` + try/catch swallow)을 그대로 복사.
- frontend `useMe` 훅은 이미 admin/layout AdminGuard 진입 시점에 캐시됨 — `/admin/users` 페이지에서 다시 호출하지 말고 `useMe` 재사용.
- audit emit 카운트 +2 (BETA-RELEASE.md §6 정합 필수). `ADMIN_USER_UPDATED` 미포함 명시.
