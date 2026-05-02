---
Last Updated: 2026-05-03
---

# Plan — admin-invite-email

## 요약

ADR #21의 잔여 admin 트랙을 닫는다. `POST /api/admin/users` (admin invite endpoint)를 신설하여 운영자가 신규 사용자를 임시 PW + `mustChangePassword=true` 로 생성하고, 임시 PW가 담긴 초대 이메일을 발송한다. `auth-must-change-pw` 트랙(2026-05-03 closure)이 강제 UX를 이미 활성화했으므로 endpoint만 추가하면 end-to-end flow가 즉시 동작.

## 현재 상태 분석

### 이미 보유 (재사용)

- **`User.mustChangePassword` 필드** (`backend/.../user/User.java:73`) + `clearMustChangePassword()` mutator.
- **강제 UX 활성**: `(auth)/login`, `AuthGuard`, `(explorer)/account/password` (force=1 배너) — auth-must-change-pw 트랙에서 닫힘 (docs/03 §2.7 §"강제 비밀번호 변경 UX").
- **`EmailService` 추상화** (`backend/.../email/EmailService.java`) — `ConsoleEmailService(@Profile("!prod"))` + `SmtpEmailService(@Profile("prod"))`. send(to, subject, body) 한 메서드만 노출.
- **`AuditEventType.ADMIN_USER_CREATED("admin.user.created")`** — enum은 이미 존재(line 64), wire-up만 필요. emit coverage 31/42 → 32/42.
- **`SignupService` 패턴**: email trim+lowercase, duplicate check via `findActiveByEmail`, BCrypt encode, save, event publish — 동일 트랜잭션 (참고 가능).
- **권한 가드 패턴**: `@PreAuthorize("hasRole('ADMIN')")` (`TrashController:93`, `PermissionService` 등).

### 누락 (이 트랙의 범위)

1. **백엔드 admin 모듈 미존재**: `backend/src/main/java/com/ibizdrive/` 트리에 `admin/` 패키지 없음. 신규 모듈 추가.
2. **`POST /api/admin/users` endpoint 없음**.
3. **`ADMIN_USER_CREATED` audit emission 없음** — enum만 있고 어디서도 publish/save 안 함.
4. **임시 PW 생성 로직 없음** — secure random 16자(영문+숫자+특수, 운영자 전달용) 신설.
5. **invite 이메일 본문 템플릿 없음** — plain text, 한글 본문, 임시 PW 포함, force change UX 안내.
6. **frontend admin user 페이지 없음** — `frontend/src/app/admin/audit/logs/`만 존재. `/admin/users` 페이지 + invite form 신설.
7. **문서**: docs/03 §2.8 "v1.x reserve" 표기를 "활성화 완료"로 flip, docs/02 §7.4 endpoint 표 +1, docs/03 §2.10 audit 표 +1.

## 목표 상태

- 운영자(`ROLE_ADMIN`)가 `POST /api/admin/users {email, displayName, role}` 호출 → 임시 PW 16자 생성 → User 저장(`isActive=true`, `mustChangePassword=true`) → `ADMIN_USER_CREATED` audit emission → `EmailService.send`로 임시 PW + 로그인 안내 이메일 발송.
- 응답: `{id, email, displayName, role, mustChangePassword:true}` — **임시 PW는 응답에 포함하지 않음** (이메일 채널로만 전달, 로그/응답 노출 금지). 콘솔 프로파일에서는 stdout으로 dump.
- 사용자: 메일 수신 → `/login`으로 임시 PW 로그인 → 강제 UX(auth-must-change-pw)가 `/account/password?force=1`로 redirect → 새 PW 설정 → `mustChangePassword=false` 클리어 → `/files` 진입.
- 프론트 `/admin/users` 페이지: 단순한 invite form(email/displayName/role select) + 성공/실패 토스트. 사용자 목록은 본 트랙 범위 외(v1.x).
- 문서 동기화: ADR #21 admin 트랙 closure 명시, docs/02 §7.4 endpoint 표 갱신, docs/03 §2.10 audit row 추가, 본 entry + progress.md.

## Phase별 실행 지도

### P1 — 백엔드: AdminUserService.invite() + temp PW 생성 (TDD)

- 빨강: `AdminUserServiceTest` 신설
  - `invite_createsUserWithMustChangePasswordTrue`
  - `invite_persistsBcryptHashOfGeneratedPassword` (rawPw가 hash와 일치 → BCrypt encode 검증)
  - `invite_emailLowercaseAndTrim`
  - `invite_duplicateEmail_throws409`
  - `invite_publishesAdminUserCreatedAudit` (Mockito spy on AuditService 또는 ApplicationEventPublisher)
  - `invite_sendsInviteEmail` (EmailService 모킹 + body에 임시 PW 포함 검증)
  - `invite_returnsResponseWithoutTempPassword`
- 초록: `AdminUserService` 신설 (`backend/.../admin/AdminUserService.java`)
  - `TempPasswordGenerator` 신설 (16자, `SecureRandom`, alphabet=alnum+소량 특수, `Character.UnicodeBlock` 회피)
  - `@Transactional` invite() — duplicate check → BCrypt encode → User 저장 → `ApplicationEventPublisher` publish AdminUserCreatedEvent → `EmailService.send`
  - `AdminUserCreatedEvent(userId, actorId, email)` record + listener (`AdminAuditListener`) → `AuditService` REQUIRES_NEW로 `ADMIN_USER_CREATED` 기록 (기존 `AuthAuditListener` 패턴 일관)
- 회귀: backend `./gradlew test` BUILD SUCCESSFUL 유지

### P2 — 백엔드: AdminUserController + Security 설정 (TDD)

- 빨강: `AdminUserControllerTest`
  - `POST /api/admin/users 200 OK` (인증된 ADMIN)
  - `POST /api/admin/users 401` (비로그인)
  - `POST /api/admin/users 403` (MEMBER 인증)
  - `POST /api/admin/users 400 VALIDATION_ERROR` (email 누락 / 형식 위반 / displayName blank / role invalid)
  - `POST /api/admin/users 409 DUPLICATE_EMAIL`
- 초록: `AdminUserController` (`@PreAuthorize("hasRole('ADMIN')")`) + `AdminInviteUserRequest` DTO + `AdminInviteUserResponse` DTO + `AuthExceptionHandler`/공통 핸들러로 `DuplicateEmailException` → 409 매핑(이미 존재하므로 재사용).
- `SecurityConfig`: 별도 추가 불필요 — `/api/admin/**` 기본 authenticated, `@PreAuthorize`로 role 가드.

### P3 — 프론트: api + hook (TDD)

- 빨강: `frontend/src/lib/api.adminInviteUser.test.ts`
  - 200 응답 시 정상 반환
  - 409 시 `DUPLICATE_EMAIL` ApiError 매핑
  - 403 시 ApiError 매핑
- 초록: `api.ts`에 `adminInviteUser({email, displayName, role})` 메서드 추가 (CSRF token 사용 — `passwordChange` 패턴 동일).
- `useAdminInviteUser` 훅 (`frontend/src/hooks/useAdminInviteUser.ts` + 테스트) — `useMutation` + onSuccess 시 토스트(있으면)는 페이지 레벨에서.

### P4 — 프론트: /admin/users 페이지 (TDD)

- 빨강: `frontend/src/app/admin/users/page.test.tsx`
  - 폼 렌더 (email/displayName/role select)
  - 제출 성공 → 성공 메시지 표시 + 폼 reset
  - 409 → `DUPLICATE_EMAIL` 인라인 에러
  - 403 (route 자체) → 라우트 가드는 별도 트랙 (현재는 ADR #21에 따라 백엔드 가드만 신뢰, 프론트는 단순 안내)
- 초록: `frontend/src/app/admin/users/page.tsx` 신설 — minimal form, ROLE select(`MEMBER`/`AUDITOR`/`ADMIN`), 성공 시 "초대 메일을 발송했습니다" 안내. 사용자 목록은 본 트랙 범위 외.
- 사이드바/네비게이션 진입점: `/admin/audit/logs` 옆에 추가하지 않음(별도 admin 네비 트랙). 직접 URL 접근.

### P5 — Closure (docs sync + archive + PR)

- `docs/03-security-compliance.md`
  - §2.8 "v1.x reserve" 표기 → "활성화 완료(2026-05-03, admin-invite-email 트랙)"으로 flip + endpoint 본문(요청/응답) 명시.
  - §2.10 audit 표 +1 (`admin.user.created` row).
  - §2.7 §"강제 비밀번호 변경 UX" 끝에 "초대 흐름 활성화 완료" cross-link.
- `docs/02-backend-data-model.md`
  - §7.4 endpoint 표에 `POST /api/admin/users` 추가 + request/response 블록 +1.
- `docs/00-overview.md`
  - ADR #21 본문에 admin 트랙 closure 메모 추가 (별도 ADR 신설은 안 함 — ADR #21 자체의 잔여 closure이므로).
- `docs/progress.md` 본 entry (audit emit coverage 31/42 → 32/42 명시).
- `dev/active/admin-invite-email/` → `dev/completed/`로 이동, `dev/process/admin-invite-email.md` 정리.
- PR `feat(admin-invite-email): POST /api/admin/users (ADR #21 admin 트랙 closure)` — stacked on master(또는 #48 머지 후 master).

## Acceptance Criteria

- [ ] `AdminUserServiceTest` 6+ 케이스 신규 + BUILD SUCCESSFUL
- [ ] `AdminUserControllerTest` 5+ 케이스 신규 + 200/400/401/403/409 매트릭스
- [ ] `ADMIN_USER_CREATED` audit emit 코드 경로 + emit coverage 31/42 → 32/42
- [ ] 임시 PW가 응답/로그/예외 메시지에 절대 노출되지 않음 (테스트로 검증: 응답 JSON에 `tempPassword` 키 없음, 예외 메시지에 hash/plain 둘 다 없음)
- [ ] EmailService.send 호출 1회 + body에 임시 PW + force-change 안내 포함
- [ ] frontend api/hook/page 테스트 모두 통과
- [ ] 수동 시나리오: ADMIN 로그인 → `/admin/users`에서 invite → ConsoleEmailService stdout에서 본문 확인 → 임시 PW로 신규 사용자 로그인 → `/account/password?force=1`로 자동 bounce → 새 PW 설정 → `/files` 정상 진입
- [ ] `pnpm typecheck && pnpm lint && pnpm vitest run` 통과
- [ ] `./gradlew test` BUILD SUCCESSFUL
- [ ] docs/02 §7.4, docs/03 §2.8 §2.10 동기화

## 검증 게이트

- 각 phase 종료 시 해당 layer 전체 테스트 (`./gradlew test` 또는 `pnpm vitest run`) 그린.
- P5 직전 통합 수동 시나리오 1회.
- PR open 전 `pnpm typecheck && pnpm lint && pnpm vitest run && cd backend && ./gradlew test` 풀세트.

## 리스크와 완화 전략

| 리스크 | 완화 |
|---|---|
| 임시 PW가 응답/로그에 새는 사고 | 응답 DTO에 `tempPassword` 키 없음을 테스트로 강제. 서비스에서 raw pw는 method-local 변수 + 즉시 BCrypt encode + EmailService.send 후 변수 scope 종료. 로깅 금지 (특히 `log.info(req)` 같은 reflection-style 금지). |
| 동기 SMTP 발송 → admin 요청 latency | a1.5와 동일 trade-off. admin-initiated이므로 사용자 가시 latency 영향 작음. v1.x async 트랙으로 분리. |
| ConsoleEmailService stdout이 운영 환경에 새는 사고 | `@Profile("!prod")` 분기로 차단. prod에서는 SmtpEmailService 강제. CI 환경은 `@SpringBootTest`에서 명시적 EmailService 모킹. |
| ADMIN role 가드 우회 | `@PreAuthorize("hasRole('ADMIN')")` + Spring Security default deny + 컨트롤러 테스트로 401/403 매트릭스 검증. |
| 동일 email duplicate race | DB UNIQUE partial index `(email) WHERE deleted_at IS NULL` (V1) — 진실의 출처. application 레벨 check는 UX용. tx 충돌 시 409 반환. |
| 임시 PW 강도 부족 | 16자 alnum + 일부 특수, `SecureRandom`. 1회 사용 의도이므로 brute force window는 force-change 직후 닫힘. v1.x rate-limit으로 추가 방어. |
| frontend `/admin/users` 라우트 가드 미흡 | MVP는 백엔드 가드만 신뢰(403 응답 시 안내). 프론트 ROLE 가드는 ADR #21 cross-cutting 트랙(별도). |

## 참고 ADR / 문서

- ADR #21 (admin invite only — 본 트랙으로 잔여 closure)
- ADR #41 (auth-pages — self-signup supersede 본문에 admin 트랙 closure 메모 추가)
- ADR #42 (EmailService Profile 분기 — 재사용)
- ADR #43 (password reset token — 별도, 본 트랙에서는 토큰 미사용 방식 선택: 임시 PW 직접 발급)
- docs/03 §2.7 (강제 비밀번호 변경 UX), §2.8 (사용자 등록), §2.10 (audit 이벤트), §3 (권한 매트릭스)
- docs/02 §7.4 (auth/admin endpoint 계약)
