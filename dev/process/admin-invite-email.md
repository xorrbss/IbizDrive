---
task: admin-invite-email
last_updated: 2026-05-03
working_files:
  - (P3 next) frontend/src/lib/api.ts
  - (P3 next) frontend/src/lib/api.adminInviteUser.test.ts
  - (P3 next) frontend/src/hooks/useAdminInviteUser.ts
  - (P3 next) frontend/src/hooks/useAdminInviteUser.test.tsx
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

## working_files (P3 active)

- frontend/src/lib/api.ts (수정 — `adminInviteUser` 메서드 추가)
- frontend/src/lib/api.adminInviteUser.test.ts (신규)
- frontend/src/hooks/useAdminInviteUser.ts (신규)
- frontend/src/hooks/useAdminInviteUser.test.tsx (신규)

## Notes

- P3는 `passwordChange` (api.ts:1065+) + `usePasswordChange` 패턴 재사용.
- CSRF token 헬퍼 (`getCsrfToken`) 재사용 — 신규 helper 작성 X.
- 409 → `ApiError(code=CONFLICT, reason=DUPLICATE_EMAIL)` 매핑 검증.
- P4 frontend page, P5 closure (docs + archive + PR).
