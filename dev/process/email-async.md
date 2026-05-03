---
task: email-async
last_updated: 2026-05-03
working_files:
  - backend/src/main/java/com/ibizdrive/email/EmailAsyncConfig.java (new)
  - backend/src/main/java/com/ibizdrive/email/EmailService.java
  - backend/src/main/java/com/ibizdrive/email/ConsoleEmailService.java
  - backend/src/main/java/com/ibizdrive/email/SmtpEmailService.java
  - backend/src/main/java/com/ibizdrive/auth/password/PasswordResetService.java
  - backend/src/test/java/com/ibizdrive/email/EmailAsyncIntegrationTest.java (new)
  - dev/active/email-async/email-async-plan.md (new)
  - dev/active/email-async/email-async-context.md (new)
  - dev/active/email-async/email-async-tasks.md (new)
  - docs/00-overview.md (ADR #45)
  - docs/03-security-compliance.md (§2.7)
  - docs/progress.md
---

# Session ownership — email-async

## working_files
- (P1) backend/src/main/java/com/ibizdrive/email/EmailAsyncConfig.java
- (P2) backend/src/main/java/com/ibizdrive/email/EmailService.java (`@Async` on send)
- (P2) backend/src/main/java/com/ibizdrive/email/ConsoleEmailService.java (exception handling internal)
- (P2) backend/src/main/java/com/ibizdrive/email/SmtpEmailService.java (exception handling internal)
- (P3) backend/src/main/java/com/ibizdrive/auth/password/PasswordResetService.java (try/catch removal)
- (P4) backend/src/test/java/com/ibizdrive/email/EmailAsyncIntegrationTest.java
- (P5) docs/00-overview.md ADR #45 + docs/03 §2.7 + docs/progress.md
