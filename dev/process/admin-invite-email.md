---
task: admin-invite-email
last_updated: 2026-05-03
working_files:
  - (P4 next) frontend/src/app/admin/users/page.tsx
  - (P4 next) frontend/src/app/admin/users/page.test.tsx
---

# Session ownership — admin-invite-email

## P1 완료 (2026-05-03)

- branch: `wip/admin-invite-email` (from master)
- 신규 파일:
  - backend/src/main/java/com/ibizdrive/admin/AdminUserService.java
  - backend/src/main/java/com/ibizdrive/admin/TempPasswordGenerator.java
  - backend/src/main/java/com/ibizdrive/admin/AdminUserCreatedEvent.java
  - backend/src/main/java/com/ibizdrive/admin/AdminAuditListener.java
  - backend/src/main/java/com/ibizdrive/admin/AdminInviteUserResponse.java
  - backend/src/test/java/com/ibizdrive/admin/AdminUserServiceTest.java

## P2 완료 (2026-05-03)

- 신규 파일:
  - backend/src/main/java/com/ibizdrive/admin/AdminUserController.java
  - backend/src/main/java/com/ibizdrive/admin/AdminInviteUserRequest.java
  - backend/src/test/java/com/ibizdrive/admin/AdminUserControllerTest.java
- `SecurityConfig` 변경 0. `DuplicateEmailException → 409` 기존 매핑 재사용.

## P3 완료 (2026-05-03)

- 수정:
  - frontend/src/lib/api.ts (`adminInviteUser` 메서드 + AdminInviteUserParams/Response 타입 export)
- 신규:
  - frontend/src/lib/api.adminInviteUser.test.ts (4 tests)
  - frontend/src/hooks/useAdminInviteUser.ts
  - frontend/src/hooks/useAdminInviteUser.test.tsx (2 tests)

## working_files (P4 active)

- frontend/src/app/admin/users/page.tsx (신규)
- frontend/src/app/admin/users/page.test.tsx (신규)

## Notes

- P4: form (email/displayName/role select) + `useAdminInviteUser.mutate` + onSuccess(reset+안내) / onError(409=duplicate, 403=permission, etc).
- 참고: `frontend/src/app/(explorer)/account/password/page.tsx` (form + 성공/실패 처리), `frontend/src/app/admin/audit/logs/page.tsx` (admin 페이지 layout).
- P5 closure (docs + archive + PR).
