---
task: auth-must-change-pw
last_updated: 2026-05-02
working_files:
  - backend/src/main/java/com/ibizdrive/user/User.java
  - backend/src/main/java/com/ibizdrive/auth/password/PasswordResetService.java
  - backend/src/test/java/com/ibizdrive/auth/password/PasswordResetServiceTest.java
  - backend/src/test/java/com/ibizdrive/auth/password/PasswordControllerChangeTest.java
  - frontend/src/app/(auth)/login/page.tsx
  - frontend/src/components/auth/AuthGuard.tsx
  - frontend/src/app/(explorer)/account/password/page.tsx
  - frontend/src/hooks/usePasswordChange.ts
  - docs/03-security-compliance.md
  - docs/progress.md
  - dev/active/auth-must-change-pw/auth-must-change-pw-plan.md
  - dev/active/auth-must-change-pw/auth-must-change-pw-context.md
  - dev/active/auth-must-change-pw/auth-must-change-pw-tasks.md
---

# Session ownership — auth-must-change-pw
