---
task: admin-invite-email
last_updated: 2026-05-03
working_files:
  - (P2 next) backend/src/main/java/com/ibizdrive/admin/AdminUserController.java
  - (P2 next) backend/src/main/java/com/ibizdrive/admin/AdminInviteUserRequest.java
  - (P2 next) backend/src/test/java/com/ibizdrive/admin/AdminUserControllerTest.java
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

## working_files (P2 active)

- backend/src/main/java/com/ibizdrive/admin/AdminUserController.java
- backend/src/main/java/com/ibizdrive/admin/AdminInviteUserRequest.java
- backend/src/test/java/com/ibizdrive/admin/AdminUserControllerTest.java

## Notes

- P1 service signature는 primitive params (`invite(email, displayName, role, actorId)`).
  P2 controller가 `AdminInviteUserRequest` DTO를 unpacking해서 service 호출.
- P3/P4 frontend.
- P5 closure (docs + archive + PR).
