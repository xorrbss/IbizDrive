---
Last Updated: 2026-05-03
---

# Tasks — admin-invite-email

## Phase 상태

| Phase | 상태 |
|---|---|
| P1 — backend AdminUserService + temp PW + audit emission (TDD) | ✅ 완료 (2026-05-03) |
| P2 — backend AdminUserController + Security 가드 (TDD) | ⬜ 대기 |
| P3 — frontend api + hook (TDD) | ⬜ 대기 |
| P4 — frontend `/admin/users` page (TDD) | ⬜ 대기 |
| P5 — closure (docs sync + archive + PR) | ⬜ 대기 |

---

## 현재 active phase: **P2**

---

## P1 — backend AdminUserService + temp PW + audit emission

### 작업 항목

- [x] `backend/src/test/java/com/ibizdrive/admin/AdminUserServiceTest.java` 신설
  - [x] `invite_createsUserWithMustChangePasswordTrue`
  - [x] `invite_persistsBcryptHashOfGeneratedPassword`
  - [x] `invite_emailLowercaseAndTrim`
  - [x] `invite_duplicateEmail_throwsDuplicateEmailException`
  - [x] `invite_publishesAdminUserCreatedEvent`
  - [x] `invite_sendsInviteEmailContainingTempPassword`
  - [x] `invite_returnsResponseWithoutTempPassword`
- [x] `backend/.../admin/TempPasswordGenerator.java` 신설 (16자 SecureRandom alnum + 특수 일부 `!@#$%&`)
- [x] `backend/.../admin/AdminUserService.java` 신설 (`@Transactional invite(email, displayName, role, actorId)`)
- [x] `backend/.../admin/AdminUserCreatedEvent.java` 신설 (record `userId/actorId/email`)
- [x] `backend/.../admin/AdminAuditListener.java` 신설 (`@EventListener` `AuditService` REQUIRES_NEW)
- [x] `backend/.../admin/AdminInviteUserResponse.java` 신설 (id/email/displayName/role/mustChangePassword — tempPassword 없음)
- [x] `./gradlew test` BUILD SUCCESSFUL (회귀 0, branch=`wip/admin-invite-email` from master)
- [x] commit: `feat(admin-invite-email): P1 backend AdminUserService + temp PW + audit emission (TDD)`

### 작업 전 필독

- `dev/active/admin-invite-email/admin-invite-email-plan.md` §"P1"
- `dev/active/admin-invite-email/admin-invite-email-context.md` §"중요한 의사결정" 1, 2, 3, 4
- `backend/.../auth/SignupService.java` (tx 패턴)
- `backend/.../audit/AuthAuditListener.java` (`@EventListener` REQUIRES_NEW 패턴)
- `backend/.../audit/AuditEventType.java:64` (`ADMIN_USER_CREATED` 이미 존재)
- `backend/.../email/EmailService.java`

### 원본 코드 참조

- 패턴 베이스: `SignupService.signup(...)`, `AuthAuditListener.onRegistered(...)`
- 재사용: `User` 생성자 + `clearMustChangePassword`(여기서는 호출 X — 본 트랙은 true로 생성), `UserRepository.findActiveByEmail`, `EmailService.send`
- 임시 PW 비노출 검증: `AdminInviteUserResponse` reflection으로 `tempPassword`/`password`/`hash` 필드 부재 확인 또는 Jackson 직렬화 결과 JSON에 키 부재 검증.

### 구현 대상

- `AdminUserService.invite(AdminInviteUserRequest req, UUID actorId)`
  1. email = trim+lowercase
  2. duplicate check (`findActiveByEmail`) → `DuplicateEmailException`
  3. tempPw = `TempPasswordGenerator.generate()` (16자)
  4. hash = `passwordEncoder.encode(tempPw)`
  5. `User` 신규 생성 (mustChangePassword=true, isActive=true, role=req.role)
  6. `userRepository.save(user)`
  7. `eventPublisher.publishEvent(new AdminUserCreatedEvent(user.id, actorId, user.email))`
  8. `emailService.send(email, subject, body(displayName, tempPw, loginUrl))`
  9. `return new AdminInviteUserResponse(user)` — tempPw 누락 보장
- `AdminAuditListener.onAdminUserCreated(@EventListener REQUIRES_NEW)` → `AuditService.write(ADMIN_USER_CREATED, actorId=event.actorId, targetType="user", targetId=event.userId, reason="invite")`
- `TempPasswordGenerator.generate()` — alphabet `A-Z a-z 0-9 ! @ # $ % &`, 길이 16, `SecureRandom`. (특수문자 일부만 — 메일 transit 깨짐/사용자 입력 에러 최소화)

### 검증 참조

- `./gradlew test` (`-Dtest=AdminUserServiceTest`도 사전 RED 확인)
- 응답에 임시 PW 비노출: 테스트에서 `objectMapper.writeValueAsString(response)` 결과를 직접 grep — `assertThat(json).doesNotContain(tempPw, "tempPassword", "password")`.

### 문서 반영

- 본 phase에서는 docs 미수정. P5에서 일괄 동기화.

---

## P2 — backend AdminUserController + Security 가드

### 작업 항목

- [ ] `backend/src/test/java/com/ibizdrive/admin/AdminUserControllerTest.java` 신설
  - [ ] `invite_200_admin`
  - [ ] `invite_401_anonymous`
  - [ ] `invite_403_member`
  - [ ] `invite_400_validation` (email 누락/형식, displayName blank, role invalid)
  - [ ] `invite_409_duplicateEmail`
- [ ] `backend/.../admin/AdminUserController.java` 신설 (`@PostMapping("/api/admin/users")`, `@PreAuthorize("hasRole('ADMIN')")`)
- [ ] `backend/.../admin/AdminInviteUserRequest.java` 신설 (Bean Validation: `@NotBlank @Email @Size(max=254)` email, `@NotBlank @Size(min=1, max=100)` displayName, `@NotNull` role)
- [ ] `DuplicateEmailException` 핸들러 재사용 확인 (`AuthExceptionHandler` 또는 `@ControllerAdvice`)
- [ ] `./gradlew test` BUILD SUCCESSFUL
- [ ] commit: `feat(admin-invite-email): P2 backend AdminUserController + admin role guard (TDD)`

### 작업 전 필독

- `backend/.../config/SecurityConfig.java` (변경 불필요 확인)
- `backend/.../auth/AuthController.java` (controller 테스트 패턴)
- `backend/.../auth/AuthExceptionHandler.java` (DuplicateEmailException 매핑)

### 원본 코드 참조

- `AuthController` (`@PostMapping`/CSRF 처리)
- `PermissionController` 또는 `TrashController` (`@PreAuthorize("hasRole('ADMIN')")` 패턴)

### 구현 대상

- `AdminUserController.invite(@Valid @RequestBody AdminInviteUserRequest, Authentication auth)` → `AdminInviteUserResponse`
- 인증된 admin actor의 ID는 `Authentication.getPrincipal()` → `IbizDriveUserDetails.getUser().getId()` 추출.

### 검증 참조

- 401/403 매트릭스: `@WithMockUser(roles="MEMBER")` vs anonymous vs `@WithMockUser(roles="ADMIN")`.
- 400 validation: 표준 envelope (`code=VALIDATION_ERROR`).
- 409: auth flat envelope (`{code:"CONFLICT", reason:"DUPLICATE_EMAIL"}`) — signup과 동일 매핑 재사용.

### 문서 반영

- P5에서 일괄.

---

## P3 — frontend api + hook (TDD)

### 작업 항목

- [ ] `frontend/src/lib/api.adminInviteUser.test.ts` 신설
- [ ] `frontend/src/lib/api.ts`에 `adminInviteUser({email, displayName, role})` 메서드 추가
- [ ] `frontend/src/hooks/useAdminInviteUser.ts` + `.test.tsx` 신설
- [ ] `pnpm typecheck && pnpm lint && pnpm vitest run` 통과
- [ ] commit: `feat(admin-invite-email): P3 frontend api + hook (TDD)`

### 작업 전 필독

- `frontend/src/lib/api.ts:1065+` (`passwordChange` 패턴)
- `frontend/src/hooks/usePasswordChange.ts` (mutation hook 패턴)
- `frontend/src/lib/api.audit.test.ts` (api 테스트 패턴)

### 원본 코드 참조

- `passwordChange` (CSRF token + JSON POST)
- `usePasswordChange` (`useMutation` + onSuccess invalidate 없는 단순 패턴)

### 구현 대상

- `api.adminInviteUser({email, displayName, role}): Promise<AdminInviteUserResponseDto>`
  - CSRF token 사용 (`getCsrfToken()` 헬퍼 재사용)
  - 409 → `ApiError` with `code=CONFLICT, reason=DUPLICATE_EMAIL`
  - 403 → `ApiError`
- `useAdminInviteUser`: `useMutation`, onSuccess는 페이지 레벨에서 처리 (hook은 캐시 무효화 안 함).

### 검증 참조

- `pnpm vitest run frontend/src/lib/api.adminInviteUser.test.ts`
- `pnpm vitest run frontend/src/hooks/useAdminInviteUser.test.tsx`

### 문서 반영

- P5에서 일괄.

---

## P4 — frontend `/admin/users` page (TDD)

### 작업 항목

- [ ] `frontend/src/app/admin/users/page.test.tsx` 신설
- [ ] `frontend/src/app/admin/users/page.tsx` 신설 (form: email/displayName/role select, submit, 성공/실패 표시)
- [ ] `pnpm typecheck && pnpm lint && pnpm vitest run` 통과
- [ ] commit: `feat(admin-invite-email): P4 /admin/users invite form (TDD)`

### 작업 전 필독

- `frontend/src/app/admin/audit/logs/page.tsx` (admin 페이지 layout 일관)
- `frontend/src/app/admin/layout.tsx` (admin 레이아웃)
- `frontend/src/app/(explorer)/account/password/page.tsx` (form + 성공/실패 처리 패턴)

### 원본 코드 참조

- 폼 패턴: `(auth)/signup/page.tsx` 또는 `(explorer)/account/password/page.tsx`
- ROLE select 옵션: `MEMBER` / `AUDITOR` / `ADMIN` (Role.java)

### 구현 대상

- 단일 form (email, displayName, role select)
- 제출 → `useAdminInviteUser.mutate`
- onSuccess → 폼 reset + "초대 메일을 발송했습니다 ({email})" 토스트/안내
- onError → 409면 "이미 가입된 이메일입니다" 인라인, 403이면 "권한이 없습니다", 그 외 "초대에 실패했습니다"

### 검증 참조

- `pnpm vitest run frontend/src/app/admin/users/page.test.tsx`
- 수동: ADMIN 로그인 → `/admin/users` → invite → 콘솔에 stdout 메일 본문 확인.

### 문서 반영

- P5에서 일괄.

---

## P5 — closure (docs sync + archive + PR)

### 작업 항목

- [ ] `docs/00-overview.md` ADR #21 본문에 admin 트랙 closure 메모 추가 (별도 ADR 신설 X)
- [ ] `docs/02-backend-data-model.md` §7.4 endpoint 표 +1 (`POST /api/admin/users`) + request/response 블록 +1
- [ ] `docs/03-security-compliance.md` §2.7 끝에 "초대 흐름 활성화 완료(2026-05-03)" cross-link
- [ ] `docs/03-security-compliance.md` §2.8 "v1.x reserve" → "활성화 완료(2026-05-03)" + endpoint 본문(요청/응답/에러)
- [ ] `docs/03-security-compliance.md` §2.10 audit 표 +1 (`admin.user.created`)
- [ ] `docs/progress.md` 본 트랙 entry 최상단 추가 (audit emit coverage 31/42 → 32/42 명시)
- [ ] `dev/active/admin-invite-email/` → `dev/completed/`로 이동
- [ ] `dev/process/admin-invite-email.md` 정리 (있으면)
- [ ] PR open: `feat(admin-invite-email): POST /api/admin/users (ADR #21 admin 트랙 closure)` — stacked on master 또는 PR #48 머지 후
- [ ] PR open 직전 사용자 confirm (게이트)

### 작업 전 필독

- 본 `tasks.md` Phase 1~4 완료 체크 확인
- `dev/completed/auth-must-change-pw/` (있다면 closure 패턴 참고)
- `dev/process/auth-must-change-pw.md` (있다면 정리 패턴 참고)

### 원본 코드 참조

- 본 phase는 코드 변경 없음. 문서 동기화 + PR open만.

### 구현 대상

- 문서 6개 + dev-docs 이동 + PR.

### 검증 참조

- `git status`로 dev/active/* 비움 확인
- `git log --oneline -10`로 phase 별 commit 1줄씩 + closure 1줄 확인
- PR template `CLAUDE.md §8 체크리스트` 통과

### 문서 반영

- 본 phase 자체가 문서 반영.

---

## 미완료 task별 참조 블록 (요약)

각 phase의 "작업 전 필독" / "원본 코드 참조" / "구현 대상" / "검증 참조" / "문서 반영" 블록은 위 phase 절에 inline. 미완료 phase는 위 §"Phase 상태" 표에서 ⬜.
