---
Last Updated: 2026-05-03
---

# Context — admin-invite-email

## SESSION PROGRESS

- 2026-05-03: bootstrap. 5 phase 계획 고정 (P1 backend service+TDD / P2 backend controller+security TDD / P3 frontend api+hook TDD / P4 frontend page TDD / P5 closure).
- 코드 변경 0. dev-docs 3파일만.
- 2026-05-03: P1 완료. branch `wip/admin-invite-email` (from master). `AdminUserServiceTest` 7건 + 구현 5파일(`AdminUserService`, `TempPasswordGenerator`, `AdminUserCreatedEvent`, `AdminAuditListener`, `AdminInviteUserResponse`). `./gradlew test` BUILD SUCCESSFUL (회귀 0). 임시 PW 비노출 invariant 테스트로 강제 (Jackson JSON에 tempPassword/password/hash 키 부재 검증). audit emit coverage 31/42 → 32/42 (코드 경로 추가, P5 docs 표 갱신 예정).
- 2026-05-03: P2 완료. `AdminUserControllerTest` 7건(200/401/403/400×3/409) + 구현 2파일(`AdminUserController`, `AdminInviteUserRequest`). `@PreAuthorize("hasRole('ADMIN')")` + Bean Validation. `DuplicateEmailException` → 409 매핑 `AuthExceptionHandler` 기존 핸들러 재사용 (신규 핸들러 0). `SecurityConfig` 변경 0. `./gradlew test` BUILD SUCCESSFUL (회귀 0). 응답 JSON에 tempPassword/password/passwordHash 키 부재 jsonPath 검증.

## Current Execution Contract

- **모드**: 자율 실행. 게이트는 "머지 / master push / force push"만. 그 외는 멈추지 않음.
- **TDD 강제**: 각 phase는 빨강 → 초록 → 회귀 순. test 먼저 commit하지 않아도 되지만, 동일 커밋 내 test+impl 묶음 필수.
- **임시 PW 절대 노출 금지**: 응답 DTO/로그/예외 메시지/git 히스토리/git commit body. EmailService 본문에만 등장.
- **변경 최소화**: 사용자 목록/admin 사이드바 네비/role 변경 페이지 등은 **본 트랙 범위 외**. invite endpoint + 단일 invite form만.
- **ADR 신설 안 함**: ADR #21 자체의 잔여 closure이므로 ADR 본문에 closure 메모만 추가. 새 ADR 번호 부여 불필요.

## 현재 active task

P3 — 프론트엔드: `frontend/src/lib/api.ts` `adminInviteUser()` + `useAdminInviteUser` hook, TDD.

진입점: `dev/active/admin-invite-email/admin-invite-email-tasks.md` Phase 3.

## 다음 세션 읽기 순서

1. 본 `context.md` (이 파일) — SESSION PROGRESS + Current Execution Contract.
2. `admin-invite-email-tasks.md` 의 **현재 active phase** 섹션 (체크박스로 진행 상황 즉시 파악).
3. `admin-invite-email-plan.md` §"Phase별 실행 지도" 의 해당 phase 절.
4. (필요 시) 아래 §"핵심 파일과 역할" 표.

## 핵심 파일과 역할

### 재사용 (수정 금지)

| 파일 | 역할 | 본 트랙 사용 |
|---|---|---|
| `backend/.../user/User.java:73-211` | `mustChangePassword` 필드 + `clearMustChangePassword()` | invite 시 `true` 로 생성 |
| `backend/.../email/EmailService.java` | send(to, subject, body) | invite 메일 발송 |
| `backend/.../audit/AuditEventType.java:64` | `ADMIN_USER_CREATED("admin.user.created")` | enum 그대로, emit만 신규 |
| `backend/.../auth/SignupService.java` | self-signup tx 패턴 | invite tx 작성 시 참고 (복붙 X) |
| `backend/.../audit/AuthAuditListener.java` | `UserRegisteredEvent` → audit_log | `AdminUserCreatedEvent` listener 작성 시 패턴 |
| `backend/.../user/UserRepository.java` | `findActiveByEmail` | duplicate check |
| `frontend/src/lib/api.ts:1065+` | `passwordChange` 메서드 | CSRF token 사용 패턴 참고 |
| `frontend/src/hooks/usePasswordChange.ts` | mutation hook 패턴 | `useAdminInviteUser` 작성 시 참고 |
| `frontend/src/app/(explorer)/account/password/page.tsx` | force UI 페이지 | 본 트랙은 invite UX 종착이 아니라 **출발점** — 사용자가 임시 PW로 로그인 후 자동으로 이 페이지로 bounce되는 chain 확인용 |

### 신규 (본 트랙에서 생성)

| 파일 | 역할 |
|---|---|
| `backend/.../admin/AdminUserService.java` | invite() — duplicate check + temp PW 생성 + BCrypt + User save + event publish + email send |
| `backend/.../admin/TempPasswordGenerator.java` | SecureRandom 16자 alnum + 일부 특수 |
| `backend/.../admin/AdminUserController.java` | `POST /api/admin/users` + `@PreAuthorize("hasRole('ADMIN')")` |
| `backend/.../admin/AdminInviteUserRequest.java` | DTO (email/displayName/role) + Bean Validation |
| `backend/.../admin/AdminInviteUserResponse.java` | DTO (id, email, displayName, role, mustChangePassword=true) — **tempPassword 필드 없음** |
| `backend/.../admin/AdminUserCreatedEvent.java` | record(userId, actorId, email) |
| `backend/.../admin/AdminAuditListener.java` | `@EventListener` → `AuditService` REQUIRES_NEW (`AuthAuditListener` 패턴) |
| `backend/src/test/.../admin/AdminUserServiceTest.java` | 6+ unit |
| `backend/src/test/.../admin/AdminUserControllerTest.java` | 5+ controller |
| `frontend/src/lib/api.adminInviteUser.test.ts` | api 메서드 테스트 |
| `frontend/src/lib/api.ts` (수정) | `adminInviteUser` 메서드 추가 (CSRF token) |
| `frontend/src/hooks/useAdminInviteUser.ts` + `.test.tsx` | mutation hook + 테스트 |
| `frontend/src/app/admin/users/page.tsx` + `page.test.tsx` | invite form |

### 문서 (P5에서 동기화)

| 문서 | 변경 |
|---|---|
| `docs/00-overview.md` ADR #21 | admin 트랙 closure 메모 (ADR 신설 X, 본문에 추가) |
| `docs/02-backend-data-model.md` §7.4 | endpoint 표 +1 (`POST /api/admin/users`) + request/response 블록 |
| `docs/03-security-compliance.md` §2.7 | "초대 흐름 활성화 완료" cross-link |
| `docs/03-security-compliance.md` §2.8 | "v1.x reserve" → "활성화 완료(2026-05-03)" + endpoint 본문 |
| `docs/03-security-compliance.md` §2.10 | audit 표 +1 (`admin.user.created`) |
| `docs/progress.md` | 본 트랙 entry + audit emit coverage 31/42 → 32/42 |

## 중요한 의사결정

1. **invite 방식 = 임시 PW 직접 발급**, **토큰 기반 X**. 이유:
   - ADR #21 본문(`§2.8`)이 이미 "임시 PW + must_change_password=true" 명시 — 토큰으로 바꾸면 ADR 본문 수정 + 신규 마이그레이션 + 추가 endpoint 필요. KISS.
   - 강제 변경 UX(auth-must-change-pw)가 이미 활성 — 임시 PW로 첫 로그인 직후 강제 변경되므로 토큰 방식 대비 보안 차이 미미.
   - `password_reset_tokens` 테이블 재사용은 의미 혼동(분실 reset과 admin invite는 의도가 다름).
2. **tempPassword 응답에 절대 포함 X**. EmailService 본문에만. Console 프로파일에서 stdout으로 dump.
3. **`AdminUserCreatedEvent` listener 분리**. `AuthAuditListener`에 메서드를 추가하지 않고 `AdminAuditListener`를 신설 — domain bounded context 일관 (admin/ 모듈 내부).
4. **DTO에 tempPassword 절대 X**. 테스트로 강제 (`assertThat(response).doesNotHaveJsonPath("$.tempPassword")` 또는 객체 reflection 검증).
5. **frontend ROLE 가드 미적용**. 백엔드 403만 신뢰. 프론트 라우트 가드는 별도 cross-cutting 트랙.
6. **사이드바 네비 추가 X**. URL `/admin/users` 직접 접근만. UI 디스커버리는 별도 트랙.
7. **`SecurityConfig` 변경 최소**. 기본 authenticated에 `@PreAuthorize` 가드만. `/api/admin/**` 별도 matcher 추가 안 함.
8. **이메일 본문은 plain text + 한글**. HTML/i18n은 v1.x.
9. **CSRF**: `/api/admin/users`는 표준 double-submit 사용 (signup/forgot/reset과 달리 인증된 admin 호출이므로 토큰 보유 가정).

## 빠른 재개 안내

다음 세션 진입 시:

```bash
# 1) 현재 위치 확인
git status
git log --oneline -5

# 2) tasks.md의 마지막 미완 체크박스 확인
# (admin-invite-email-tasks.md → "현재 active phase" 섹션)

# 3) 빨강부터 시작 (TDD)
# P1이면: backend AdminUserServiceTest 케이스 1개 작성 → ./gradlew test (RED) → 구현 → GREEN
```

## 게이트 / 일시정지 트리거

- PR open / 머지 요청 직전 — 사용자 confirm 필요.
- master push / force push — 사용자 confirm 필요.
- 그 외 모든 phase 진행 / 커밋 / 테스트 / 문서 수정 — **자율**.

## 컨텍스트 절약 메모

- 큰 docs 파일(`01-frontend-design.md`, `02-backend-data-model.md`)은 섹션 라인 번호 찾아 `Read offset+limit`. 본 트랙은 docs/02 §7.4와 docs/03 §2.7/§2.8/§2.10만 수정.
- `User.java`, `EmailService.java`, `AuditEventType.java`는 짧으므로 전체 read 가능.
- 이미 alignment된 사항(임시 PW 직접 발급, 응답 비노출 등)은 본 context.md에 고정 — 다음 세션에서 재논의 금지.
