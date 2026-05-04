# 진행 상황 (Progress Log)

> 각 세션이 완료될 때마다 **최상단에 추가**합니다. 기존 내용은 보존.
> 양식: `CLAUDE.md §7` 또는 각 BRIEF의 회고 섹션 참조.

---

## 2026-05-05 — 🏁 beta-release-sync 트랙 종료 (BETA-RELEASE.md drift 정렬, docs-only)

### 범위

`master` 5f143c6 기준으로 `BETA-RELEASE.md` last-updated(`2026-05-02` → `2026-05-05`) + Source 인용(5건 신규 closure) + §1 frontend 카운트(647 → 738) + §5 4행 추가(password policy / mustChangePassword / admin invite / email async) + §6 audit emit coverage(29/42 → 32/42) + §7 admin frontend 표현 정렬. 누락된 dev/process 스테일 파일 3건 housekeeping 삭제. 코드 0 변경.

### 회고

- **commits**: 2개(bootstrap + sync) + closure.
  - `wip` dev-docs bootstrap (plan/context/tasks)
  - `docs` BETA-RELEASE.md sync + dev/process housekeeping
  - + closure commit (progress entry + dev-docs archive)
- **production 신설/수정**: 0 (docs-only).
- **docs sync**:
  - `BETA-RELEASE.md`:
    - header `Last Updated: 2026-05-05` + Source에 `auth-must-change-pw`/`auth-forgot-rate-limit`/`m-admin-entry-rewrite`/`auth-password-policy`/`email-async` 5건 인용.
    - §1 frontend `647/647` → `738/738` (auth-password-policy closure 풀세트, 93 files).
    - §5 신규 4행: 비밀번호 정책(ADR #19 본문 회복) / mustChangePassword UX(ADR #21 §2.7) / 운영자 초대 endpoint(ADR #21 closure) / 이메일 비동기(`@Async`, ADR #45).
    - §6 audit emit `29 emit (69%)` → `32 emit (76%)` 정정 + 신규 emit 3종(`USER_PASSWORD_FORGOT_REQUESTED` / `USER_PASSWORD_RESET` / `ADMIN_USER_CREATED`) cross-link.
    - §7 admin frontend 표현 정정 — admin shell + `/admin/users` 초대 폼 활성화 반영, 사용자 목록/role 변경만 v1.x.
  - `docs/progress.md`: 본 entry.
- **housekeeping**:
  - `dev/process/{a1.5-email-infra,auth-forgot-rate-limit,email-async}.md` 3개 삭제 — 모두 `status: closed` + `working_files: []`로 dev 스킬 ⓪ 규칙대로 closure 시 삭제됐어야 하나 누락.
  - `.claude/worktrees/{auth-password-policy,email-async,m-admin-entry}` 워크트리 정리 (PR 머지 후).
- **dev-docs**: `dev/active/beta-release-sync/` (3파일) → closure 후 `dev/completed/beta-release-sync/`.
- **test**: 코드 0 변경 → 로컬 회귀 검증 불필요. CI(frontend vitest + backend junit) GREEN을 PR 게이트로.

### 핵심 결정 (beta-release-sync 트랙)

1. **신규 ADR 발번 거부**: 본 트랙은 결정 0의 docs alignment. ADR #45(email-async)는 PR #53로 이미 master 진입.
2. **last-updated = 본 트랙 closure 일자(2026-05-05)** — sync 행위 자체의 날짜로 기록. 인용된 closure 일자(2026-05-03/05-04)는 Source 라인에 보존.
3. **stale dev/process housekeeping을 본 트랙에서 일괄 처리** — 별도 housekeeping 트랙 신설은 YAGNI. 향후 트랙은 closure 시 `dev/process/[task].md` 삭제 의무 재확인.

### 다음 세션 컨텍스트

- BETA-RELEASE.md §2 인프라 게이트(HTTPS / 시크릿 / managed Postgres / 백업 정책) + §8 모니터링 — 운영자 책임. 코드 측 변경 없이 staging/prod 인프라 셋업 시점에 채워짐.
- 잔여 코드 트랙 후보: `audit-emit-coverage-closure` (32/42 → 더 높은 비율, 미사용 enum emit 활성). v1.x scope.
- BETA GO/NO-GO 코드 게이트는 PASS 유지 — 인프라 sign-off 대기 상태.

---

## 2026-05-03 — 🏁 email-async 트랙 종료 (`@Async` EmailService — anti-enumeration timing leak 완화, ADR #45)

### 범위

`dev/active/email-async/` bootstrap (plan/context/tasks 3파일) → P1 `EmailAsyncConfig` 신설 (`@EnableAsync` + `emailExecutor` `ThreadPoolTaskExecutor` corePool=2/maxPool=4/queue=100/prefix `email-async-`) → P2 `EmailService.send()`에 `@Async("emailExecutor")` 부착 + `SmtpEmailService` `MailException` → ERROR 로그 흡수(throw 제거) → P3 `PasswordResetService.requestReset()` try/catch 제거 + 미사용 import/Logger 정리 + dead 테스트 1건 삭제 → P4 `EmailAsyncIntegrationTest` 신설 (caller < 50ms vs stub 200ms sleep + thread name `email-async-` 검증) → P5 docs sync (ADR #45 + 03 §2.7 갱신) + closure.

### 회고

- **commits**: 5개 + closure.
  - `80ffd18` dev-docs bootstrap
  - `64cc370` feat — P1 EmailAsyncConfig
  - `d68c2c5` feat — P2 @Async + SmtpEmailService 예외 내부화
  - `3e3f590` feat — P3 PasswordResetService try/catch 제거 + dead test 정리
  - `af71e1e` feat — P4 EmailAsyncIntegrationTest (2건 GREEN)
  - + closure commit (docs sync + progress + dev-docs archive)
- **production 신설/수정**:
  - backend 신설: `EmailAsyncConfig` (configuration, 41 lines), `EmailAsyncIntegrationTest` (Spring proxy 활성, 105 lines, 2 케이스).
  - backend 수정: `EmailService` (`@Async("emailExecutor")` 부착 + javadoc 갱신), `SmtpEmailService` (`MailException` ERROR 로그 흡수, throw 제거), `PasswordResetService` (try/catch + EmailDeliveryException import + Logger 제거, javadoc 갱신).
  - backend 테스트: `PasswordResetServiceTest` dead 케이스 1건 삭제(`requestReset_emailFailure_swallowedAndStillProceeds`).
- **docs sync**:
  - `docs/00 §5`: ADR #45 (`@Async` on interface method, executor 풀 크기, 예외 정책, 거부 옵션 2종, 한계).
  - `docs/03 §2.7`: 이메일 인프라 절을 ADR #42 + #45 공동 참조로 갱신, anti-enumeration timing leak 한계가 ADR #45로 완화됨을 명시.
- **테스트**: backend 전체 GREEN (BUILD SUCCESSFUL 2m 7s). 신규 2건 + 회귀 0.
- **보안 효과**: 가입자/미가입자 forgot caller latency 동일 — SMTP RTT 변동(±수백ms)이 더 이상 timing side channel 노출하지 않음. 통합 테스트가 caller < 50ms 임계로 회귀 차단.
- **잔여**: `EmailDeliveryException` 클래스 자체는 본 트랙에서 보존(사용처 0). 별도 cleanup 트랙에서 삭제 검토.

### 다음 세션 컨텍스트

- queue=100 포화 시 default `CallerRunsPolicy`로 caller block(latency 회귀) — BETA 도달 불가 가정. v1.x 트래픽 증가 시 `RejectedExecutionHandler` 재검토.
- 다중 인스턴스 시 thread pool은 노드별 독립(stateless send) — 영향 0.
- ADR #42 `EmailDeliveryException` deprecated 표시 + cleanup 트랙은 backlog.

---

## 2026-05-04 — 🏁 auth-password-policy 트랙 종료 (ADR #19 본문 회복, signup/reset/change 통합)

### 범위

ADR #41(auth-pages)이 self-signup MVP 진입 마찰 회피 명목으로 임시 완화한 password min=8을 ADR #19 본문(min 12 + 영문+숫자 + 공백 금지)으로 회복. 백엔드 3 endpoint(signup/reset/change) DTO + 프론트 3 페이지 일괄 정렬, 공통 validator 추출, FE/BE identical logic(핵심 원칙 11) 보장.

P1 backend 공통 validator (TDD: `PasswordPolicyValidator` + `@ValidPassword` + 22 unit tests + `AuthExceptionHandler.ruleOf` 분기로 ValidPassword violation을 rule code 노출) → P2 backend 3 DTO 적용 + integration param tests (5규칙 × 3 endpoint) + `TempPasswordGenerator` 알고리즘 강화(영문/숫자 강제 주입 + Fisher-Yates shuffle) + 200 sample 회귀 가드 → P3 frontend `lib/password.ts` mirror + 25 unit tests → P4 3 페이지(signup/reset-password/account/password) 사전검증 교체 + rule별 한국어 메시지 + 페이지 레벨 테스트 + useSignup jsdoc 갱신 → P5 docs sync(ADR #19/#41 closure 메모 + §2.7 closure 헤더 + progress entry) + dev-docs archive.

### 회고

- **production 신설/수정**:
  - backend 신설: `auth/validation/ValidPassword` (Bean Validation annotation, validatedBy = PasswordPolicyValidator), `auth/validation/PasswordPolicyValidator` (5규칙 우선순위, ASCII letter/digit + Unicode whitespace+spaceChar로 frontend `\s` NBSP 정렬).
  - backend 수정: `auth/dto/SignupRequest` / `auth/password/dto/ResetPasswordRequest` / `auth/password/dto/ChangePasswordRequest` — `@Size(min=8, max=128)` → `@ValidPassword`. `common/error/AuthExceptionHandler.ruleOf(FieldError)` — ValidPassword violation일 때 `defaultMessage`(rule code 주입)를 우선, 그 외 annotation은 기존 `code` 사용. `admin/TempPasswordGenerator` — 영문 1자 + 숫자 1자 강제 주입 + Fisher-Yates shuffle로 ADR #19 항상 통과 보장.
  - backend 신규 테스트: `auth/validation/PasswordPolicyValidatorTest` (22 케이스 — 5규칙 × 경계값 + 우선순위), `common/error/AuthExceptionHandlerTest` (4 케이스 — ruleOf 분기), `admin/TempPasswordGeneratorTest` (200 sample × 4 회귀 가드 + 길이 RepeatedTest). `AuthControllerSignupTest` / `PasswordControllerResetTest` / `PasswordControllerChangeTest` — `@ParameterizedTest @CsvSource`로 5규칙 거부 매트릭스 + `details.rule` jsonPath 검증.
  - frontend 신설: `lib/password.ts` (`PasswordRule` type + `validatePassword` + `getPasswordRuleMessage` 한국어), `lib/password.test.ts` (25 케이스 — backend 매트릭스 미러).
  - frontend 수정: `app/(auth)/signup/page.tsx` / `app/(auth)/reset-password/page.tsx` / `app/(explorer)/account/password/page.tsx` — `password.length < 8` 분기를 `validatePassword` + rule 메시지로 교체, label/minLength `8` → `12자 이상, 영문·숫자 포함`. `hooks/useSignup.ts` jsdoc — `<8자` → ADR #19 5규칙. `app/(auth)/signup/page.test.tsx` 신설 (6 케이스), `app/(auth)/reset-password/page.test.tsx` 신설 (6 케이스), `app/(explorer)/account/password/page.test.tsx` 추가 4 케이스 + 라벨 정규식 정정.
- **docs sync**:
  - `docs/00 §5` ADR #19: closure 메모 추가 (backend validator + frontend mirror + 3 endpoint/페이지 적용 + TempPasswordGenerator 회귀 가드 + 핵심 원칙 11 정렬).
  - `docs/00 §5` ADR #41: password Validation을 `@NotBlank @ValidPassword`로 정정 + 인라인 closure 메모.
  - `docs/03 §2.7`: closure 블록 헤더 추가 (단일 진실의 출처 명시 + 구현 매핑).
  - `docs/03 §2.8 self-signup`: password Validation을 `@NotBlank @ValidPassword`로 정정 + 5규칙 cross-link.
- **dev-docs**: `dev/active/auth-password-policy/` (3파일) → closure 후 `dev/completed/auth-password-policy/`로 이동.
- **test**:
  - backend `./gradlew test` BUILD SUCCESSFUL — 전체 sweep GREEN, 회귀 0.
  - frontend `npx tsc --noEmit && npx next lint && npx vitest run` — 738 tests pass, 93 files, lint/typecheck clean.

### 핵심 결정 (auth-password-policy 트랙)

1. **별도 ADR 신설 거부 — ADR #19 본문 closure + ADR #41 정정**: 본 트랙은 ADR #19로의 회귀이므로 새로운 결정이 아님. ADR #41이 임시 완화한 정책을 본문으로 되돌리는 reconciliation. KISS 원칙으로 신규 ADR 발번 회피, 양 ADR row에 closure 메모만 추가.
2. **단일 violation 우선순위 보고**: 5규칙을 우선순위(whitespace > max_length > min_length > missing_alpha > missing_digit)로 정렬해 첫 위반만 노출. 다중 violation 동시 표시는 UX 노이즈로 판단 — backend `PasswordPolicyValidator`는 일찍 return + frontend `validatePassword`도 동일 short-circuit. backend는 `disableDefaultConstraintViolation()` + `buildConstraintViolationWithTemplate(rule).addConstraintViolation()`로 rule code를 `defaultMessage`에 주입, `AuthExceptionHandler.ruleOf`가 `ValidPassword` annotation 분기에서 이 message를 rule로 surfacing(다른 annotation 회귀 보호 위해 `code.equals("ValidPassword")` 가드).
3. **FE/BE 동일 ASCII letter/digit 채택**: backend `Character.isLetter`(Unicode 한글/한자 포함)와 frontend `[A-Za-z]`(ASCII) drift 발견 → ADR #19 "영문자" 정의를 ASCII로 통일하고 backend를 frontend에 정렬(핵심 원칙 11). Whitespace는 반대로 frontend `\s`가 NBSP 등 Unicode를 포함하므로 backend에 `Character.isSpaceChar` 추가하여 정렬.
4. **`TempPasswordGenerator` 알고리즘 강화**: 기존 random alphabet 기반 16자는 통계적으로 ~10% 확률로 missing_alpha/missing_digit 위반 가능. 영문 1자 + 숫자 1자 강제 주입 후 Fisher-Yates shuffle로 위치 노출 회피 + 항상 통과 보장. 200 sample 단위 테스트로 회귀 가드.

### 다음 세션 컨텍스트

- ADR #19/#41 reconciliation 종료. 잔여 password 정책 항목(zxcvbn/HIBP 사전 공격 방지)은 ADR #19 본문대로 v1.x reserve.
- 다음 트랙 후보: A1.5 잔여(EmailService prod SMTP 통합) 또는 마일스톤 1 frontend 핵심 (folderId 라우팅 + FolderTree).

---

## 2026-05-03 — 🏁 m-admin-entry-rewrite 트랙 종료 (admin shell + invite endpoint, ADR #21 closure)

### 범위

폐기 PR #45(`m-admin-entry`, admin frontend skeleton + AdminGuard) + PR #51(`admin-invite-email`, `POST /api/admin/users`)을 현 master 기준으로 통합 재작성. ADR #21 잔여 closure(self-signup + first-user-ADMIN + 강제 변경 UX는 ADR #41/auth-must-change-pw로 이미 활성, 운영자 초대 endpoint + admin shell만 잔여).

P0 부트스트랩 → P1 AdminGuard FE TDD (3 케이스) → P2 admin layout + AdminSideNav (deferred 8 항목 disabled 배지) → P3 `/admin` landing (가용 카드 2 + deferred 섹션) → P4 (explorer) UserMenu admin 링크 (ADMIN/MEMBER 분기) → P5 AdminUserService BE TDD (TempPasswordGenerator 16자 + AdminUserCreatedEvent + AdminAuditListener AFTER_COMMIT/REQUIRES_NEW + EmailService.send 호출) → P6 AdminUserController BE TDD (200/400/401/403/409 매트릭스 + 임시 PW 응답 부재 회귀 가드 jsonPath) → P7 frontend api/hook (`api.adminInviteUser` + `useAdminInviteUser`) → P8 `/admin/users` invite form (email/displayName/role select + 성공 안내 + 폼 리셋 + 409 인라인 에러 + PW 단어 부재 회귀 가드) → P9 closure (docs sync 7 파일 + progress + dev-docs archive + housekeeping + PR).

### 회고

- **commits**: 9개 + closure.
  - `7535499 wip(m-admin-entry-rewrite): dev-docs bootstrap (plan/context/tasks)`
  - `a747564 feat(m-admin-entry-rewrite): P1 AdminGuard (TDD)`
  - `075d8fc feat(m-admin-entry-rewrite): P2 admin layout + AdminSideNav`
  - `2e411af feat(m-admin-entry-rewrite): P3 /admin landing`
  - `a1f65c0 feat(m-admin-entry-rewrite): P4 UserMenu admin 링크 (TDD)`
  - `0e7b170 feat(m-admin-entry-rewrite): P5 AdminUserService + temp PW (TDD)`
  - `540e98f feat(m-admin-entry-rewrite): P6 AdminUserController + 200/400/401/403/409 (TDD)`
  - `ad0a37b feat(m-admin-entry-rewrite): P7 api/hook adminInviteUser (TDD)`
  - `72eb1dc feat(m-admin-entry-rewrite): P8 /admin/users invite form (TDD)`
  - + closure commit (docs sync 7 + progress + dev-docs archive)
- **production 신설/수정**:
  - backend 신설: `admin/AdminInviteUserRequest` (record + Bean Validation), `admin/AdminInviteUserResponse` (record — **tempPassword 필드 부재**, 회귀 가드 javadoc), `admin/AdminUserController` (`@PreAuthorize("hasRole('ADMIN')")` + `@AuthenticationPrincipal IbizDriveUserDetails`), `admin/AdminUserService` (`@Transactional` invite — email lower/trim + duplicate → `DuplicateEmailException` + BCrypt hash + User save + event publish + EmailService.send), `admin/TempPasswordGenerator` (16자 SecureRandom alnum+소량특수), `admin/AdminUserCreatedEvent` (record), `admin/AdminAuditListener` (`@TransactionalEventListener AFTER_COMMIT` + AuditService.record).
  - backend 신규 테스트: `AdminUserServiceTest` (6+ 케이스), `AdminUserControllerTest` (7 케이스 — 200 with 회귀 jsonPath tempPassword.doesNotExist + 400 invalid email + 400 blank displayName + 400 null role + 401 unauth + 403 MEMBER + 409 duplicate).
  - frontend 신설: `components/auth/AdminGuard` + 테스트 3 / `components/admin/AdminSideNav` / `app/admin/layout.tsx` (UPDATE — AuthGuard+AdminGuard 중첩 + 사이드 nav) / `app/admin/page.tsx` (landing) / `components/auth/UserMenu.tsx` (UPDATE — admin 링크 + 테스트) / `lib/api.ts` (UPDATE — adminInviteUser 메서드 + AdminInviteUserParams/AdminInvitedUser 타입) / `lib/api.adminInviteUser.test.ts` (4 케이스) / `hooks/useAdminInviteUser.ts` + `.test.tsx` (2 케이스) / `app/admin/users/page.tsx` + `.test.tsx` (4 케이스).
- **docs sync**:
  - `docs/00 §5`: ADR #21 본문 closure 메모 추가 (별도 ADR 신설 X — admin invite 활성화 + admin shell + audit emit + 임시 PW 비노출 4채널 + Prod SMTP는 v1.x).
  - `docs/02 §7.12`: `POST /api/admin/users` 행 + request/response 블록(검증 + side-effects + 에러 envelope + audit emission cross-link).
  - `docs/03 §2.7`: 운영자 초대 cross-link (활성화 완료 + 첫 로그인 force UX 진입 명시).
  - `docs/03 §2.8`: 상태 헤더 flip ("v1.x reserve" → "활성화 완료") + 본문 endpoint 추가 + 임시 PW 비노출 4채널(응답/로그/audit/예외) 정책 명시 + 첫 로그인 흐름 + 에러 envelope + 프론트 진입점 명시.
  - `docs/03 §2.10`: audit 표 +1 (`admin.user.created`).
  - `docs/04 §1`: §1.1 "가드 분리 — UX 게이트 vs 보안 게이트" 절 신설 (AdminGuard 책임 + `@PreAuthorize` 진실 + AuthGuard 중첩 순서 + SecurityConfig permitAll 회귀 가드).
  - `docs/04 §2`: 라우트 트리에 활성/v1.x deferred 명시 (활성: `/admin`, `/admin/audit/logs`, `/admin/users`).
- **dev-docs**: `dev/active/m-admin-entry-rewrite/` (3파일) → closure 후 `dev/completed/`로 이동. `dev/active/auth-must-change-pw/` 잔존도 같은 시점에 `dev/completed/`로 housekeeping 이동(PR #48 closure 시 누락분).
- **test**:
  - backend `./gradlew test` BUILD SUCCESSFUL — 신규 14+ 케이스(Service 6 + Controller 7) GREEN, 회귀 0.
  - frontend `pnpm typecheck && pnpm lint && pnpm vitest run` 풀세트 GREEN — 신규 케이스(P1 AdminGuard 3 + P4 UserMenu 분기 + P7 6 + P8 4) 합산.

### 핵심 결정 (m-admin-entry-rewrite 트랙)

1. **별도 ADR 신설 거부 — ADR #21 본문에 closure 기록**: 본 트랙은 ADR #21의 잔여 closure이므로 새로운 결정이 아님. ADR #41이 self-signup으로 supersede하면서 운영자 초대를 v1.x reserve로 보류했던 것을 m-admin-entry-rewrite로 활성화. 결정의 의미적 출처는 ADR #21 그대로 유지하고 본문에 closure 메모만 추가(별도 #46+ 발번 회피, KISS).
2. **임시 PW 비노출 4채널 회귀 가드**: 응답 DTO record에 `tempPassword` 필드 자체 부재 (컴파일 강제) + 컨트롤러 테스트 `jsonPath("$.tempPassword").doesNotExist()` + audit_log payload null + 서비스 어디에도 PW 평문 INFO/DEBUG 로그 없음. AdminInviteUserResponse javadoc에 정책을 명시해 향후 변경 시 가드 활성화.
3. **AdminAuditListener `AFTER_COMMIT` + `REQUIRES_NEW`**: user save가 rollback되었는데 audit만 남는 상황 회피. AuthAuditListener와 동일 패턴(ADR #24 §2 cross-cutting layer 분리). ApplicationEventPublisher.publish는 트랜잭션 동기화로 commit 후에만 listener 호출.
4. **AdminGuard = UX 가드만, 보안은 백엔드 `@PreAuthorize`**: 프론트 가드 강도와 보안 강도를 혼동하지 않도록 docs/04 §1.1에 명시 + 회귀 가드(SecurityConfig permitAll 목록에 `/api/admin/**` 포함 금지). 두 가드 중첩 순서는 `<AuthGuard><AdminGuard>` (인증 → 역할).
5. **사용자 목록 미구현, 초대 폼만**: ADR #21 closure 범위는 "초대 endpoint + 진입 shell"까지. 사용자 목록/검색/role 변경은 v1.x admin 트랙. mutation 후 cache invalidate 없음(invalidate 대상 query 자체 부재).
6. **Prod SMTP 도입은 v1.x**: 본 트랙은 ConsoleEmailService(`@Profile("!prod")`) stdout으로 dev/test 검증만 활성. SmtpEmailService(`@Profile("prod")`)는 a1.5 트랙에서 인터페이스만 도입된 상태 — 이메일 템플릿/암호화 채널/비동기 큐는 v1.x 인프라 트랙에서 별도 결정.
7. **audit emit coverage 31 → 32**: `ADMIN_USER_CREATED("admin.user.created")` enum이 a1.5 closure 시점에 정의되어 있었으나 사용처 0이었음. 본 트랙으로 emit 활성. 42 enum 중 32 emit (76%).

### 다음 세션 컨텍스트

- **사용자 목록/검색/role 변경** — `/admin/users` 페이지에 list view 추가 (v1.x admin 트랙).
- **이메일 비동기 큐** — `EmailService.send`를 `@Async` + `TaskExecutor`로 fire-and-forget화 (ADR #45 적용 인프라 도입 시점).
- **Prod SMTP 활성화** — SmtpEmailService 본문 구현 + 이메일 템플릿(HTML/i18n) + 비동기 큐(v1.x 인프라 트랙).
- **role 변경 시 useMe 즉시 invalidate** — 백엔드에서 role이 바뀐 직후 frontend stale 회피. v1.x admin user mgmt에서 invalidate 추가.

---

## 2026-05-03 — 🏁 auth-forgot-rate-limit 트랙 종료 (forgot 분당 1회 rate-limit, ADR #44)

### 범위

`dev/active/auth-forgot-rate-limit/` bootstrap (plan/context/tasks 3파일) → P1 RED (limiter 단위 테스트 8건 + controller MockMvc 6건 컴파일 RED) → P2 GREEN (`ForgotPasswordRateLimiter` Component, `RateLimitExceededException`, `ErrorResponse.rateLimitExceeded` 팩토리, `AuthExceptionHandler` 429 + `Retry-After` 매핑, `PasswordController.forgot` IP 추출 + lower email + tryAcquire 게이트 + WARN 로그 with email mask) → P3 sibling 테스트 회귀 봉합 (`PasswordControllerForgotTest` 기본 통과 stub 추가, Reset/Change 테스트는 `@MockBean ForgotPasswordRateLimiter`만 추가) → P4 docs sync (ADR #44 + 02 §7.4 + 03 §2.7 + BETA §5) → P5 closure (progress + dev/active→completed 이동 + PR).

### 회고

- **commits**: 3개 + closure.
  - `2a06246` dev-docs bootstrap
  - `a17f951` feat — limiter + 429 매핑 + controller wire (10 files / 492 insertions / TDD GREEN)
  - `bb4e3b1` docs sync — ADR #44 + endpoint contract
  - + closure commit (progress + dev-docs archive)
- **production 신설/수정**:
  - backend 신설: `ForgotPasswordRateLimiter` (Clock 주입 가능, 두 키 OR-block, lazy 만료) + `RateLimitExceededException` + 단위 테스트 8건 + 컨트롤러 MockMvc 테스트 6건.
  - backend 수정: `ErrorResponse` (rateLimitExceeded 팩토리 — `RATE_LIMIT_EXCEEDED` 코드 + `retryAfterSec` 필드, 신규 에러 코드 0), `AuthExceptionHandler` (429 + `Retry-After` 헤더 매핑), `PasswordController` (limiter 의존 주입 + `HttpServletRequest` 인자 + IP 추출 + email lower + 마스킹 WARN), 기존 `PasswordControllerForgotTest`/`ResetTest`/`ChangeTest` (`@MockBean` 추가).
- **docs sync**:
  - `docs/00 §5`: ADR #44 (in-memory single-instance, OR-block, X-Forwarded-For 첫값, anti-enumeration 정합, audit 미발행, login surface 비범위, 한계 3종).
  - `docs/02 §7.4`: forgot endpoint rate-limit 절 + 429 + `Retry-After` (기존 `RATE_LIMIT_EXCEEDED` §8 재사용 — 신규 에러 코드 0).
  - `docs/03 §2.7`: forgot row 정정(rate-limit 명시) + rate-limit 정책 1줄 (reset/change 미적용 사유).
  - `BETA-RELEASE.md §5`: forgot rate-limit 행 추가 (✓ ADR #44).
- **테스트**: 815 / 0 fail / 0 err / 214 skip. 신규 14건 GREEN.
- **a1.5 closure 잔여 항목**: `PasswordResetService.java:45` deferred 코멘트가 가리키던 forgot rate-limit 트랙 close.

### 다음 세션 컨텍스트

- single-instance 한계는 ADR #44 명시. 다중 인스턴스 도입 시점에 인터페이스 추출 + Redis 백엔드 트랙 (v1.x).
- `X-Forwarded-For` spoof — trusted proxy whitelist 트랙(별도, v1.x 인프라 셋업 시점).
- reset/change rate-limit는 토큰/세션 가드로 보호되므로 v1.x 별도 결정 — 현 트랙 범위 외.

---

## 2026-05-03 — 🏁 auth-must-change-pw 트랙 종료 (ADR #21 mustChangePassword UX 강제 closure)

### 범위

`dev/active/auth-must-change-pw/` bootstrap → P1 backend TDD (PasswordResetService.change()/reset()이 PW hash 갱신 직후 `User.clearMustChangePassword()` 호출 — 단일 트랜잭션 내 영속화. 이 클리어 없으면 프론트 enforce가 무한 redirect 루프) → P2 frontend LoginPage TDD (`postLoginTarget` 헬퍼로 redirect 단일화 — `me.user.mustChangePassword=true` 시 `next` 무시하고 `/account/password?force=1`) → P3 AuthGuard TDD (`usePathname` 도입 + 로그인 사용자라도 `mustChangePassword=true && pathname!=='/account/password'`이면 bounce, `/account/password` 자체에서는 통과로 무한 루프 회피) → P4 `/account/password` force UI TDD (`?force=1` 시 amber 배너 `role=alert` + "돌아가기" hide + 성공 시 `router.replace('/files')`, `usePasswordChange.onSuccess`가 `qk.authMe()` invalidate) → P5 closure (docs/03 §2.7 "강제 비밀번호 변경 UX" 절 신설 + §2.8 운영자 초대 reservation note 보강 + 본 entry + dev-docs archive + stacked PR).

### 회고

- **commits**: 3개 + closure.
  - `1b9d360 wip(auth-must-change-pw): dev-docs bootstrap (plan/context/tasks)`
  - `7e8e4cc feat(auth-must-change-pw): P1 backend change/reset clear mustChangePassword (TDD)` — PasswordResetServiceTest +2 (change_clearsMustChangePasswordFlag, reset_clearsMustChangePasswordFlag) + sampleUserWithMustChange 헬퍼
  - `cab34c1 feat(auth-must-change-pw): P2+P3+P4 frontend mustChangePassword UX (TDD)` — LoginPage/AuthGuard/account/password page + 3 신규 테스트 파일(11 케이스)
  - + closure commit (docs/03/progress/archive)
- **production 신설/수정**:
  - backend: `User.clearMustChangePassword()` 신설 + `PasswordResetService.change()/reset()` 양쪽 호출 추가.
  - frontend: `LoginPage` (`postLoginTarget` 헬퍼 + 두 redirect 분기 분기), `AuthGuard` (`usePathname` + mustChangePassword guard + `data` undefined 가드), `/account/password/page` (Suspense 경계 + `useSearchParams('force')` + 배너 + 돌아가기 조건부 렌더 + force 모드 redirect), `usePasswordChange` (onSuccess invalidate `qk.authMe`).
  - tests 신설: `PasswordResetServiceTest +2` / `LoginPage page.test.tsx (3)` / `AuthGuard.test.tsx (3)` / `account/password page.test.tsx (5)` = 합 13.
- **docs sync**:
  - `docs/03 §2.7` 끝에 "강제 비밀번호 변경 UX (auth-must-change-pw 트랙, 2026-05-03)" 절 신설 — 백엔드 클리어 + 프론트 LoginPage·AuthGuard·force UI·invalidation flow 명시.
  - `docs/03 §2.8` 운영자 초대 reservation note 보강 — 강제 변경 UX는 §2.7로 분리되어 활성화 완료, admin invite endpoint만 도입하면 즉시 동작.
- **dev-docs**: `dev/active/auth-must-change-pw/` (3파일) → closure 후 `dev/completed/`로 이동 + `dev/process/auth-must-change-pw.md` 정리.
- **test**:
  - backend `./gradlew test` BUILD SUCCESSFUL — PasswordResetServiceTest 신규 2 + 기존 통과, 회귀 0.
  - frontend `pnpm typecheck && pnpm lint && pnpm vitest run` — 685/685 통과 (681 → +13 케이스 일부 기존 테스트 통합 후 net +4 테스트 파일, 신규 11 → total 685).

### 핵심 결정 (auth-must-change-pw 트랙)

1. **백엔드 클리어가 선결조건**: 프론트 enforce(redirect)만 추가하면 `change()`/`reset()` 후에도 플래그가 true로 남아 무한 redirect. `clearMustChangePassword()`를 mutator로 도입(boolean setter 노출 회피, idempotent — 자발적 변경 false→false 무해).
2. **`reset()`도 클리어**: ADR #21 §2.8 "사용자가 reset link로 PW 설정 → mustChangePassword=false 처리"와 일치. 자발적 분실 reset은 원래 false였을 가능성 높지만 idempotent라 무해.
3. **`postLoginTarget` 헬퍼**: useEffect(이미 로그인) + onSubmit(신규 로그인) 두 경로의 redirect 결정 로직을 단일 함수로 통합 — 분기 누락 위험 제거.
4. **`usePathname` 도입 vs 기존 `window.location`**: 새 가드 분기에서만 `usePathname()`을 쓰고, 기존 401 분기의 `next` 구성은 `window.location.search`까지 포함하므로 그대로 유지. 변경 최소화 원칙(KISS).
5. **`/account/password`가 force redirect의 종착점**: AuthGuard에서 `pathname === '/account/password'` 예외 처리로 무한 루프 회피. force=1 query는 정보 표시(배너)와 변경 성공 후 `/files` redirect 트리거 용도. 진실의 출처는 `me.user.mustChangePassword`(useMe staleTime 60s + invalidate).
6. **`usePasswordChange.onSuccess`에서 useMe invalidate**: force 모드 → `/files` 전환 시 AuthGuard가 stale 플래그(true)로 bounce하지 않도록. `await qc.invalidateQueries({ queryKey: qk.authMe() })`로 refetch 완료 후 `router.replace('/files')` 실행.
7. **audit 변동 없음**: `USER_PASSWORD_CHANGED`/`USER_PASSWORD_RESET`이 이미 emit. 플래그 변화에 별도 이벤트 추가 안 함 (audit emit coverage 31/42 유지).

### 다음 세션 컨텍스트

- **password 정책 강화** — min=8 → min=12 + zxcvbn/HIBP, /signup·/reset·/change 양쪽 적용 (ADR #19 본문 정합 회복).
- **이메일 송신 비동기화** — `@Async` + `TaskExecutor`로 forgot 응답 latency 균일화.
- **이메일 초대 endpoint** (`POST /api/admin/users` invite-by-email) — ADR #21 잔여 admin 트랙. 강제 UX는 본 트랙으로 닫혔으므로 endpoint만 추가하면 end-to-end flow 완성.
- **rate limit on /forgot** — 동일 email/IP 분당 1회 등 (브루트포스 enumeration 추가 방어).

---

## 2026-05-03 — 🏁 m8.1-permission-list-frontend 트랙 closure 마무리 (PR #46 머지 후 docs/archive 정합)

### 범위

PR #46 (`fdb57c7` `feat(m8.1-permission-list-frontend): wire BE permission list into PermissionsTab`)는 2026-05-02에 이미 master 머지되었으나, dev-docs 측 closure(progress entry + active→completed archive + tasks G2 표시)가 누락된 채 `dev/active/m8-permission-list-frontend/`에 남아 있었음. 본 세션은 production 코드 변경 0의 closure-only 정합 작업.

### 회고

- **production 신설/수정**: 0 (이미 PR #46로 머지됨).
- **docs sync**: 본 entry 1건.
- **dev-docs**: `dev/active/m8-permission-list-frontend/` → `dev/completed/`. `tasks.md` G2 (PR 생성) 체크 표시.
- **test**: 변경 없음. PR #46 시점 기준 frontend 670 tests / 82 files (M8.1에서 +23) 그린 유지.

### 핵심 결정 (m8.1-permission-list-frontend closure)

1. **별도 회고 작성 X**: PR #46 머지 시점 closure가 적시 수행되지 못한 행정 누락. M8.1 본 작업의 회고/결정사항은 `dev/completed/m8-permission-list-frontend/` 의 `plan.md`/`context.md`/`tasks.md` 본문이 진실의 출처(verbatim 보존).
2. **PR/머지 사실만 기록**: 본 entry는 "어디로 갔는지" 정합용 minimum entry. 본문 회고는 dev-docs 참조.

### 다음 세션 컨텍스트

- 권한 목록 후속(검색/필터/페이지네이션, grant 행 액션 — revoke/edit/expiry 변경)은 별도 트랙. 현재는 read-only 목록만.
- folder 권한 read-only list — 본 트랙은 PermissionsTab(file) 우선이고 folder 영역은 보류였음. 후속 트랙으로 이관 가능.

---

## 2026-05-02 — 🏁 a1.5-email-infra 트랙 종료 (Spring Mail + password reset/change + 3 endpoints + 3 pages, ADR #42·#43)

### 범위

`dev/active/a1.5-email-infra/` bootstrap → P1 EmailService 추상화 (`EmailService` interface + `ConsoleEmailService @Profile("!prod")` + `SmtpEmailService @Profile("prod")` + spring-boot-starter-mail) → P2 V8 `password_reset_tokens` migration (token_hash CHAR(64) UNIQUE + user_id FK + expires_at + used_at + created_at, idx_password_reset_tokens_user_id) + JPA entity/repo → P3 POST `/api/auth/password/forgot` TDD (anti-enumeration 200 always + audit user.password.forgot_requested) → P4 POST `/api/auth/password/reset` TDD (token verify + bcrypt update + 모든 세션 invalidate + audit user.password.reset) → P5 POST `/api/auth/password/change` TDD (current pw verify + 다른 세션만 invalidate + audit user.password.changed) → P6 frontend (api 3 메서드 + 3 hooks + `(auth)/forgot-password` + `(auth)/reset-password?token=` + `(explorer)/account/password` + login 링크 + UserMenu 링크) → P7 closure (ADR #42·#43 + docs/03 §2.7 password reset 절 + docs/02 §7.4 endpoint 표·request/response 블록 + audit 이벤트 동기화 + 본 entry + archive + stacked PR).

### 회고

- **commits**: 6개 + closure.
  - `f...` P1 EmailService abstraction (Console/Smtp + Profile dispatch)
  - `673fbdc` P2 V8 password_reset_tokens + entity/repo
  - `6dd4488` P3 POST /api/auth/password/forgot (TDD)
  - `95e8a3c` P4 POST /api/auth/password/reset (TDD)
  - `8ade712` P5 POST /api/auth/password/change (TDD)
  - `d82b271` P6 frontend pages /forgot|reset|account/password
  - + closure commit (ADR/docs/03/docs/02/progress/archive)
- **production 신설/수정**:
  - backend 신설: `EmailService` interface + `ConsoleEmailService` + `SmtpEmailService` + V8 migration + `PasswordResetToken` entity/repo + `PasswordResetService` (forgot/reset/change) + `PasswordController` + 3 DTO + 신규 `InvalidTokenException` + tests 4 (controller 3 + service).
  - backend 수정: `SecurityConfig` (`/api/auth/password/forgot|reset` permitAll + CSRF ignore), `AuditEventType` (USER_PASSWORD_FORGOT_REQUESTED + USER_PASSWORD_RESET 추가, USER_PASSWORD_CHANGED 활성), `AuthExceptionHandler` (INVALID_TOKEN 매핑).
  - frontend 신설: 3 hooks (`usePasswordForgot`/`usePasswordReset`/`usePasswordChange`) + 3 pages (`(auth)/forgot-password`/`(auth)/reset-password`/`(explorer)/account/password`).
  - frontend 수정: `api.ts` (3 메서드 — forgot/reset CSRF skip, change CSRF 사용), `(auth)/login/page` (잊으셨나요 링크), `UserMenu` (비밀번호 변경 링크).
- **docs sync**:
  - `docs/00 §5`: ADR #42 (EmailService Profile 분기), ADR #43 (token SHA-256 hash + 30분 TTL + 1회 사용 + anti-enumeration).
  - `docs/03 §2.7` 직후 password reset/change 절 신설(엔드포인트·토큰·세션 invalidation 정책·이메일 인프라).
  - `docs/03 §2.10` audit 표 +2 (`user.password.forgot_requested`, `user.password.reset`).
  - `docs/03 §4.1` AuditEventType TS union +2 (ADR #43 활성).
  - `docs/02 §7.4` endpoint 표 +3 + request/response 블록 +3.
- **dev-docs**: `dev/active/a1.5-email-infra/` → closure 후 `dev/completed/`.
- **test**:
  - backend `./gradlew test` BUILD SUCCESSFUL — 신규 PasswordControllerForgotTest(3) + PasswordControllerResetTest(4) + PasswordControllerChangeTest(5) + PasswordResetServiceTest(8) = 20개, 회귀 0.
  - frontend `pnpm typecheck && pnpm lint && pnpm build` clean — 3 신규 페이지 정적 prerender.

### 핵심 결정 (a1.5)

1. **ADR #42 — EmailService Profile 분기**: dev/test=`ConsoleEmailService`(stdout 로그), prod=`SmtpEmailService`(JavaMailSender). 인터페이스 단일 진입점 `sendPasswordReset(email, rawToken)`. SMTP 설정은 `application-prod.yml` (host/port/username/password 환경변수).
2. **ADR #43 — Password reset token 정책**: 평문 토큰은 응답·DB 저장 X. SHA-256 hex hash만 저장. TTL 30분. 1회 사용(used_at 기록). 만료/사용/미존재 모두 동일 INVALID_TOKEN 응답(side-channel 차단).
3. **Anti-enumeration**: `/forgot` 응답·메시지 동일, audit에는 `found` 플래그로 분리 기록. latency는 best-effort(이메일 송신 비동기화는 v1.x).
4. **세션 invalidation 비대칭**: `/reset` = 모든 세션 종료(분실·탈취 가정), `/change` = 현재 세션만 보존(다른 디바이스만 종료). `FindByIndexNameSessionRepository.findByPrincipalName` 사용.
5. **CSRF asymmetry**: `/forgot`·`/reset`은 SecurityConfig `ignoringRequestMatchers` (anonymous), `/change`는 인증 + double-submit 유지.
6. **mustChangePassword 활성 시점**: ADR #21 잔여 — 첫 로그인 강제 변경 UX는 v1.x. 현 트랙은 자발적 `/account/password` + 잊었을 때 `/forgot-password` 두 경로만 활성.
7. **audit emit coverage 29 → 31**: USER_PASSWORD_FORGOT_REQUESTED + USER_PASSWORD_RESET 활성 (USER_PASSWORD_CHANGED는 ADR #41 트랙에서 enum만 추가, 본 트랙에서 emit).

### 다음 세션 컨텍스트

- **mustChangePassword UX 강제** — 로그인 직후 `/account/password` redirect (ADR #21 잔여, v1.x).
- **password 정책 강화** — min=8 → min=12 + zxcvbn/HIBP, /reset·/change 양쪽 적용.
- **이메일 송신 비동기화** — `@Async` + `TaskExecutor`로 forgot 응답 latency 균일화(현재는 동기 송신 — dev console은 무비용, prod SMTP는 best-effort).
- **이메일 초대 endpoint** (`POST /api/admin/users` invite-by-email) — ADR #21 잔여, EmailService 재사용.
- **rate limit on /forgot** — 동일 email/IP 분당 1회 등(브루트포스 enumeration 추가 방어).

---

## 2026-05-02 — 🏁 auth-pages 트랙 종료 (셀프 가입 + first-user-ADMIN + /login·/signup + 401 가드, ADR #41)

### 범위

`dev/active/auth-pages/` bootstrap (plan/context/tasks 3파일) → P1 backend signup TDD (SignupService 6 RED→GREEN + AuthControllerSignupTest 6 + AuthService.establishSession extract + AuditEventType.USER_REGISTERED + UserRegisteredEvent + AuthAuditListener.onRegistered + DuplicateEmailException + AuthExceptionHandler) → P2 SecurityConfig (`/api/auth/signup` permitAll + CSRF ignore) → P3 frontend api/hooks (api.signup/login/logout/me + ensureCsrfToken + buildApiError flat envelope + qk.authMe + useMe/useLogin/useSignup/useLogout + types/auth.ts) → P4 pages (`/login` Suspense wrap + `/signup` + `(auth)` 미니멀 layout + `(explorer)` AuthGuard + UserMenu) → P5 closure(ADR #21 supersede + ADR #41 + docs/03 §2.8/§4.1 + BETA-RELEASE §1·§5·§6 + 본 entry + archive + PR).

### 회고

- **commits**: 3개 + closure.
  - `70662bb feat(auth-pages): P1+P2 backend signup + SecurityConfig` — TDD GREEN, 6+6 신규 + 기존 13개 @MockBean SignupService 추가
  - `8ca3540 feat(auth-pages): P3 frontend api/hooks (signup/login/logout/me)` — typecheck/lint/test 통과
  - `334cf8d feat(auth-pages): P4 /login·/signup pages + (explorer) 401 guard` — typecheck/lint/test/build 통과
  - + closure commit (ADR/docs/BETA-RELEASE/progress/archive)
- **production 파일 수정/신설**: backend 신설 5 (SignupRequest/SignupService/UserRegisteredEvent/DuplicateEmailException + tests 2) + 수정 6 (AuthController/AuthService/AuditEventType/AuthAuditListener/AuthExceptionHandler/ErrorResponse/SecurityConfig + 기존 2 tests `@MockBean`), frontend 신설 7 (types/auth + 4 hooks + 2 components) + 신설 3 pages (login/signup/auth-layout) + 수정 3 (api/queryKeys/(explorer)layout/audit-types).
- **docs sync**: `docs/00 §5` ADR #41 추가 + ADR #21 Status: Superseded, `docs/03 §2.8` 셀프 가입 정책 재작성 + `§4.1` USER_REGISTERED enum 추가 + `§2.10` user.registered 이벤트 추가, `BETA-RELEASE` Source/§1/§5/§6 갱신.
- **dev-docs**: `dev/active/auth-pages/` (3파일) — closure 후 `dev/completed/`로 이동.
- **test**:
  - backend `./gradlew test` BUILD SUCCESSFUL (signup TDD +12 신규, 회귀 0).
  - frontend `pnpm test --run` **647/647** (M-RP 트랙 baseline 유지 — auth pages/hooks는 thin wrappers + Suspense 의존성으로 typecheck/lint/build 검증으로 대체).
  - frontend `pnpm typecheck && pnpm lint && pnpm build` clean (login/signup 정적 prerender 3.3kB 각).

### 핵심 결정 (auth-pages 트랙)

1. **ADR #41 — 셀프 가입 + first-user-ADMIN supersede ADR #21**: BETA 진입 차단(첫 가입자 부재) 해소. `userRepository.count() == 0`이면 ADMIN 부여, 그 외 MEMBER. race는 MVP single-instance + tx 직렬화로 차단(엄밀 보장은 advisory lock — v1.x).
2. **CSRF asymmetry**: `/api/auth/signup`은 `permitAll()` + `csrf().ignoringRequestMatchers` (첫 호출 token preflight 마찰 회피). login/logout은 ADR #12 그대로 double-submit 유지.
3. **AuthService.establishSession extract**: login 기존 세션 발급 로직을 public 헬퍼로 추출 → signup이 동일 helper 호출. `changeSessionId()` 호출 동일, AuthenticationSuccessEvent는 caller 책임(login만 발행, signup은 별도 USER_REGISTERED).
4. **password min=8 정정**: ADR #19(min 12)을 가입 진입 마찰 최소화로 8로 정정. v1.x 정책 강화 트랙으로 분리.
5. **useMe 401→null 매핑**: AuthGuard와 비로그인 페이지가 동일 hook으로 분기. retry false + staleTime 60s. AuthGuard는 `useSearchParams` 의존 제거(window.location in useEffect)로 prerender Suspense 마찰 회피.
6. **useLogout intent-driven**: onSettled에서 `qc.clear()` — 401/네트워크 실패도 사용자 의도가 로그아웃이므로 캐시는 비운다.
7. **audit emit coverage 28 → 29**: USER_REGISTERED 활성화. 42 enum 중 29 emit (69%).

### 다음 세션 컨텍스트

- **운영자 user 초대 endpoint** (`POST /api/admin/users`, ADR #21 잔여 부분) — v1.x reserve. 사내 도메인 배포 시 admin이 user를 사전 생성하는 수요는 BETA 운영 진입 후 결정.
- **email 인증 + 이메일 초대 (A1.5)** — 이메일 인프라 도입 시점에 self-signup에 verification 추가.
- **password 정책 강화 트랙** — min=8 → min=12, zxcvbn/HIBP 사전 공격 방지 (ADR #19 본문은 그대로).
- **first-user-ADMIN advisory lock 보강** — 다중 인스턴스 도입 시점에 PostgreSQL `pg_advisory_xact_lock(<key>)`로 race 엄밀 보장.

---

## 2026-05-02 — 🏁 m-rp-rightpanel-completion 트랙 종료 (RightPanel 4탭 완성 + 버전 다운로드/복원)

### 범위

`dev/active/m-rp-rightpanel-completion/` bootstrap (plan/context/tasks 3파일) → M-RP.1 versions 탭 read-only(G1) → M-RP.2 버전별 download/restore endpoint + UI(G2 사용자 sign-off 옵션 A + denormalized 메타 동기화 자체 리뷰 보강) → M-RP.3 permissions 탭 wiring(G3) → M-RP.4 activity 탭 wiring + AuditQueryFilters 확장(G4 closure).

### 회고

- **commits**: 4개 + closure.
  - `fa24169 wip(m-rp): rightpanel versions tab snapshot` — M-RP.1 작업 스냅샷
  - `71ee56b feat(m-rp.2): version download/restore + audit emit + denorm sync` — M-RP.2 (ADR #39)
  - `b91e28d feat(m-rp.3): RightPanel permissions 탭 wiring` — M-RP.3
  - `cc9c886 feat(m-rp.4): file activity timeline + RP-2 audit scope` — M-RP.4 (ADR #40)
  - + closure commit (BETA-RELEASE/progress/ADR/archive)
- **production 파일 수정/신설**: backend 6 (FileVersionController/Service + AuditQueryFilters/Service/Controller + tests), frontend 신설 7 + 수정 다수 (VersionsTab/PermissionsTab/ActivityTab + 훅 + api/queryKeys + RightPanel 통합).
- **docs sync**: `docs/01 §17.5` (RightPanel 4탭 활성화), `docs/02 §7.6/§7.12` (version + audit endpoint), `docs/03 §4` (VERSION_* emit + RP-2 정책), `docs/00 §5` (ADR #39, #40 추가).
- **신설**: `BETA-RELEASE.md §10·§11` (RightPanel 4탭 + 버전 관리 — 본 closure로 추가).
- **dev-docs**: `dev/active/m-rp-rightpanel-completion/` (3파일) — closure 후 `dev/completed/`로 이동.
- **test**:
  - backend `./gradlew test` BUILD SUCCESSFUL (M-RP.4 audit filter + RP-2 정책 신규 검증 포함, 회귀 0).
  - frontend `pnpm test --run` **647/647** (M-RP 트랙 누계 +84 tests vs mvp-qa-security baseline 563/563).
  - typecheck/lint clean.

### 핵심 결정 (M-RP 트랙)

1. **ADR #39 — 버전 복원 의미론 = 옵션 A (current_version_id 재지정)**: 새 version row 생성하지 않고 `files.current_version_id`만 재지정. 멱등(같은 versionId 재호출 시 audit 0). denormalized 메타(`files.size_bytes`/`files.mime_type`)도 동기화 — `FileUploadService:214-217` invariant 보존(자체 리뷰에서 발견 + 보강).
2. **ADR #40 — RP-2 정책 (activity 탭 권한 범위)**: `targetType="file"` + `targetId` 지정 + 호출자가 해당 파일 `READ` 보유 시 actor 제한 우회(다른 사용자 활동 노출 허용). 그 외 기존 정책(자기 actor만) 유지. RP-2 = "파일 단위 활동 timeline은 파일 권한 보유자에게 모두 보여야 한다"는 UX 요구를 충족하면서 audit 노출 최소화.
3. **모든 탭 fetch gate**: 비활성 탭은 `enabled: tab === 'X'`로 fetch 차단 — 불필요 네트워크 0, RightPanel 마운트 비용 최소화.
4. **M12 audit logs 페이지 회귀 0**: AuditQueryFilters 신규 필드는 nullable additive — 기존 호출부 무영향. 테스트로 명시 검증.
5. **VERSION_DOWNLOADED / VERSION_RESTORED audit emit 활성화**: audit emit coverage 26 → 28 enum (63% → 68%).

### 다음 세션 컨텍스트

- M-RP 트랙 closure로 RightPanel 4탭 완전 wiring. 다음 작업 후보:
  - admin frontend (사용자/부서/권한/스토리지/정책/시스템 페이지) — v1.x 후순위
  - audit emit 추가 13 enum (대부분 `ADMIN_*` + ADR #9 audit_level + ADR #18 MFA 의존) — v1.x
  - RightPanel "더보기" 페이지네이션 (현재 activity/versions 첫 페이지만) — UX backlog
- BETA-RELEASE.md §1 코드 게이트 ✓ 유지 — 베타 readiness 변동 없음 (인프라 게이트 운영팀 sign-off 대기).

### 블로커

없음.

---

## 2026-05-02 — 🏁 mvp-prod-profile 트랙 종료 (application-prod.yml + cron 4종 활성화 + cookie.secure)

### 범위

Phase 0 (WIP `wip/m-rp-rightpanel-completion` 분기 + master를 origin/df7cc97에 rebase — `docs/progress.md` additive 충돌 1건 해소: mvp-qa-security/M-Download top entry 양쪽 보존, mvp-qa-security를 새 HEAD로 위, M-Download 아래) → Phase 1 (`backend/src/main/resources/application-prod.yml` 신설: cron 4종 `enabled=true` + `server.servlet.session.cookie.secure=true` override + `ProdProfileConfigTest` 회귀 차단 2 케이스) → Phase 2 closure (`BETA-RELEASE.md` §1·§2.2·§3·§8·§9 PASS 갱신, Slack appender 항목은 외부 log shipper 운영자 책임/v1.x로 재분류).

### 회고

- **commits**: 본 closure commit 1개 (worktree branch `feature/mvp-prod-profile`) → squash-merge 예정. PR single, 회귀 0.
- **production 파일**: 신설 1.
  - `backend/src/main/resources/application-prod.yml` (32줄) — `app.{purge,share.expiration,permission.expiration,storage.orphan-cleanup}.enabled=true` + `server.servlet.session.cookie.secure=true`. dev/test/CI는 default profile 유지로 무영향.
- **test**: 신설 1 / 수정 0. `backend/src/test/java/com/ibizdrive/config/ProdProfileConfigTest.java` 2 케이스 (prod profile 활성/default profile 유지 회귀 양방향). backend `./gradlew test` GREEN — 회귀 0 (mvp-qa-security baseline 75 classes / 723 tests / 522 PASS / 201 skip + 2 신규 = 525 PASS / 201 skip).
- **docs sync**: `BETA-RELEASE.md` §2.2 cookie.secure ✓ / §3 cron 표 prod profile 컬럼 ✓ / §8 Slack 항목 운영자 책임 마커 / §9 GO 결정 §3 ✓ + §8 외부 shipper로 갱신.

### 핵심 결정 (mvp-prod-profile 트랙)

1. **`application-prod.yml` 분리** — `application.yml`은 dev safe default 유지(cron `false` + cookie `secure=false`). prod 활성화는 `SPRING_PROFILES_ACTIVE=prod` 환경 변수 1회 셋업으로 4 cron + cookie secure 자동 ON. 단일 파일에 prod-only 키만 두어 default 환경 회귀 표면적 0.
2. **TDD via `ApplicationContextRunner`** — 4 cron + cookie property 5개 키를 prod profile context에서 평가, default profile에서 false 유지를 양방향 검증. `@SpringBootTest` 미사용 — DB/Flyway/Web 무의존, `CorsConfigContextBootstrapTest` 패턴 답습. yaml 키 오타 회귀 차단.
3. **Slack appender 드롭 (KISS / YAGNI)** — `BETA-RELEASE.md §8`에서 "권장 (사내 베타 최소)"였던 in-process logback appender는 ① 권장 항목, ② ERROR-당-POST 모델은 incident rate-limit 위험, ③ 운영 표준은 fluent-bit/Promtail/Datadog 외부 shipper. 본 트랙은 application-prod.yml 단일 책임만 닫고 Slack은 v1.x 관측성 트랙 + 운영자 책임으로 재분류.
4. **rebase 충돌 해소 정책** — `docs/progress.md`는 reverse-chronological. 같은 날짜 양쪽 entry는 양쪽 보존, 새 HEAD가 위, 기존 origin이 아래. 추가 entry는 그 위에 stack.

### 파급 영향

- **`BETA-RELEASE.md §1·§2.2·§3·§8·§9` 갱신**. 코드 측 추가 게이트 항목 0건 (이번 트랙으로 §3·§2.2 닫힘, 잔여 §2/§8은 운영자 셋업).
- **dev/process/beta-release-prod-profile.md** session marker 사용 — m-rp-2 working_files와 0건 겹침 확인.
- **m-rp 트랙 영향**: `wip/m-rp-rightpanel-completion` 브랜치에 보존 (커밋 1개 — 13 files, +1124). master 머지 시점은 사용자 결정. *(2026-05-02 후속: M-RP.2~4 commit 추가 후 본 트랙 closure로 master 머지 — 위 entry 참조)*

---

## 2026-05-02 — 🏁 mvp-qa-security 트랙 종료 (Week 11-12 MVP QA + 보안 점검 + 베타 readiness)

### 범위

`dev/active/mvp-qa-security-week-11-12/` bootstrap (plan/context/tasks 3파일 + `findings/` 6개 보고서) → Phase 1 베이스라인 + Inventory(G1) → Phase 2 STRIDE Gap Analysis + 핵심 원칙 11개 conformance(G2 사용자 sign-off A안) → Phase 3 Triage + Remediation(MVP-fix 2건 + v1.x deferred 마커 1건, G3) → Phase 4 docs/03 §5-§10 + docs/04 §3-§14 본문화 또는 deferred 마커 + ROOT `BETA-RELEASE.md` 신설 + closure(G4).

### 회고

- **commits**: 본 closure commit 1개 (master 직접, worktree 미사용 — dev-docs + 작은 코드 fix만).
- **production 파일 수정**: 2개.
  - `backend/src/main/resources/application.yml` — `server.error.{include-stacktrace=never, include-message=on-param, include-binding-errors=on-param}` 3줄 (production stacktrace leak 차단)
  - `backend/src/main/java/com/ibizdrive/config/SecurityConfig.java` — `.headers(h -> h.contentTypeOptions().frameOptions(deny).cacheControl())` chain 추가 (Spring Security 7+ 호환성 + 보안 정책 가시성)
- **docs sync**: 2개.
  - `docs/03-security-compliance.md` — §5.3/§5.4 Phase 3 결정 반영 + §5.1·§5.2·§6·§7·§8·§9·§10 빈 체크박스에 운영/v1.x/v2.x 마커
  - `docs/04-admin-operations.md` — §3·§4·§5·§6·§8·§9·§10·§11·§12·§13·§14 빈 체크박스에 v1.x/운영/구현됨 마커 + §13 cron 표에 상태 컬럼 추가
- **신설**: 1개. `BETA-RELEASE.md` (사내 베타 GO/NO-GO 단일 페이지 체크리스트 — 코드/인프라/cron/보안 헤더/인증/감사/모니터링).
- **dev-docs**: `dev/active/mvp-qa-security-week-11-12/` (3파일 + findings 6개) — closure 후 `dev/completed/`로 이동.
- **test**: 회귀 0. backend `./gradlew test` 75 classes / 723 tests / 522 PASS / 201 skip(no Docker IT) / **0 fail / 0 error** — Phase 1 baseline과 동일. frontend 변경 없음.
- **`.gitignore`**: gradle 임시 디렉터리 5개 패턴 추가(`.gradle-user-home*/`, `.g3/`, `.g4/`, `.g5/`, `.tmp-gradle-root-get/`).

### 핵심 결정 (mvp-qa-security 트랙)

1. **베타 = 사내 베타** — 외부 일반 출시는 본 트랙 범위 밖. SSO/MFA/SAST/외부 모의해킹/Legal Hold/quota는 v1.x 또는 v2.x.
2. **신규 ADR 0건** — deferred 결정은 docs inline 마커 + `findings/triage-decisions.md`로 흡수. ADR은 본문 변경 동반 시에만 사용 (#39부터 다음 트랙).
3. **MVP-blocker 트리아지 (G2 A안 sign-off)**:
   - 확장자 화이트리스트 = **v1.x deferred** (Content-Disposition: attachment + nosniff 1차 방어 충분)
   - production stacktrace = **MVP-fix** (`application.yml` 1줄)
   - Spring Security 헤더 = **MVP-fix** (Spring Security 6 default 명시화 — 동작 변화 0, 7+ 호환성 보험)
4. **`docs/03 §1.3` STRIDE 28행** — 18 구현됨 / 3 부분 / 5 v1.x deferred / 2 운영 책임 / **0 FAIL**. 핵심 원칙 11개 — 8 PASS / 3 PARTIAL(frontend grep 한계) / 0 FAIL.
5. **audit emit coverage** — 41 enum 중 26 emit (63%). 미사용 15는 모두 `ADMIN_*`(admin frontend v1.x) + ADR #9(audit_level) + ADR #18(MFA) 등 deferred 항목에 정합.
6. **운영 cron 4종 (`enabled=false` default)** — `purge.expired`, `share.expire`, `permission.expire`, `storage.orphan.cleanup`. 베타 출시 시 staging/prod에서 명시 enable. 단일 인스턴스 가정.
7. **`BETA-RELEASE.md`로 인프라 게이트 분리** — 코드 게이트(§1)는 master 시점 PASS, 인프라 게이트(§2/§3/§8)는 운영팀 sign-off 필요. 현재 = "코드 readiness 완료, 인프라 미정 = NO-GO".

### 다음 트랙 후보

- 운영팀 인프라 셋업 (HTTPS/HSTS/managed Postgres/시크릿/cron enable) — `BETA-RELEASE.md` 게이트 채움
- admin frontend (사용자/부서/권한/스토리지/정책 페이지) — `ADMIN_*` audit emit 활성화
- ADR #9 audit_level + 파티션 — audit_log 폭증 측정 후 결정
- ADR #18 MFA / refresh rotation / SCIM — 외부 출시 트리거링 시점

---

## 2026-05-02 — 🏁 M-Download 트랙 종료 (BulkActionBar 다운로드 와이어링 — A15 frontend gap closure)

### 범위

DL.0 (worktree `feature/m-download-wire` from `4e3da46` master + dev-docs bootstrap `dev/active/m-download-wire/` 3파일) → DL.1 RED→GREEN (`api.downloadFile(id)` programmatic anchor click helper — `<a href="/api/files/{id}/download">` + body append + click + remove. `BulkActionBar.handleDownload` file-only 가드 — `count===1 && singleItem.type==='file'`. 폴더는 "파일만 다운로드 가능" tooltip, 다중은 "단일 파일 선택 시 사용 가능", 캐시 미스 disabled 폴백 — rename/share 패턴 일관. `console.warn` 스텁 제거. 신규 테스트: `frontend/src/lib/api.downloadFile.test.ts` 3 케이스 + `BulkActionBar.test.tsx` 다운로드 describe 4 케이스) → DL.2 (closure: `docs/01 §9.5` 다운로드 단락 신설, `docs/progress.md` top entry, dev-docs archive, PR + master squash-merge).

### 회고

- **commits**: 2 on top of `4e3da46` master (worktree branch `feature/m-download-wire`) → squash-merge 예정. PR single, 회귀 0.
- **production 파일**: 신설 0 / 수정 2.
  - 수정: `frontend/src/lib/api.ts` (`api.downloadFile` 메서드 추가, +18줄)
  - 수정: `frontend/src/components/files/BulkActionBar.tsx` (`downloadEnabled` + `downloadTitle` + `handleDownload` 와이어링 + 버튼 disabled/title/aria-disabled props, 스텁 제거)
- **test**: 신설 1 / 수정 1. `api.downloadFile.test.ts` 3 GREEN; `BulkActionBar.test.tsx` 4 케이스 추가(12→16). 최종 frontend `pnpm test --run` GREEN 601/601 (baseline 594 → +7 다운로드 케이스). `pnpm typecheck` + `pnpm lint` + `pnpm build` clean.
- **docs sync**: `docs/01-frontend-design.md §9.5` 다운로드 단락 신설 (anchor click 채택 근거 + file-only 가드 정책 + ADR #36 권한 backlink).

### 핵심 결정 (M-Download 트랙)

1. **anchor `<a>` click 채택** (XHR/fetch + Blob 대신) — cookie 인증 same-origin 자동 동봉, RFC 5987 Content-Disposition을 backend가 처리(docs/02 §7.6.1)하므로 파일명 자동 적용, 100MB까지의 파일을 메모리에 적재하지 않고 스트림 → 디스크. 진행률은 브라우저 다운로드 매니저 책임 → UI 추가 0. 함수 5줄. KISS / YAGNI 충족.
2. **file-only 가드** — backend `GET /api/files/{id}/download`은 파일 단건만 지원(폴더 zip은 별도 트랙). BulkActionBar에서 폴더 선택 시 비활성 + tooltip "파일만 다운로드 가능"으로 사용자 혼란 차단.
3. **fire-and-forget** — 다운로드 후 토스트 / 진행률 / 결과 처리 없음. 브라우저 매니저가 사용자 피드백 책임. 추가하면 YAGNI 위배. 실패는 브라우저 표시(404/403도 attachment 응답이 아니라 JSON envelope이라 다운로드 매니저가 파일로 저장하긴 하지만 — 권한 게이트는 `usePermission().DOWNLOAD`가 1차 차단).
4. **DOWNLOAD enum 미사용** — backend `hasPermission('file', 'READ')` 가드(ADR #36). frontend `usePermission().DOWNLOAD`는 UX 게이트, 진실의 출처는 backend READ.
5. **rename/share 패턴 답습** — `count === 1 && !!singleItem` 가드 + `xEnabled` boolean + `disabled/title/aria-disabled` props 동일 형태. 새 추상화 0.

### 파급 영향

- **`docs/01 §9.5`**: 다운로드 단락 신설.
- **frontend backlog 정리**: A15 closure entry의 "BulkActionBar download 스텁" 항목 closed. 잔여(다중 zip 다운로드, preview/inline 분기, 진행률 UI)는 별도 트랙.
- **DB/스키마/backend**: 변경 0 — frontend-only 트랙. backend A15.5는 이미 완전 구현.

---

## 2026-05-02 — 🏁 M16VK 트랙 종료 (Grid View 2D 키보드 wrap — M16V backlog closure)

### 범위

M16VK.0 (worktree `feature/m16v-grid-keyboard-wrap` from `90274c7` master + dev-docs bootstrap `dev/active/m16v-grid-keyboard-wrap/` 3파일) → M16VK.1 (`frontend/src/lib/gridNav.ts` pure helper `computeNextIndex(prev, key, view, columns, length, isPending)` + `gridNav.test.ts` 25 vitest 케이스 — list 8 + grid 14 + initial focus(prev=-1) 3) → M16VK.2 (FileTable.tsx handleKeyDown switch에서 ArrowDown/Up/Left/Right 4 case를 helper 단일 분기로 통합. list ←/→는 helper no-op + preventDefault 스킵으로 상위 핸들러 보존. FileTable.test.tsx에 useGridColumns mock + 6 items 기반 Grid 2D 통합 케이스 6개 추가) → M16VK.3 (closure: `docs/01 §12.1` 키맵 표 List/Grid 분리 + Grid 2D 보강 주, `docs/progress.md` top entry, dev-docs archive, PR + master squash-merge).

### 회고

- **commits**: 3 on top of `90274c7` master (worktree branch `feature/m16v-grid-keyboard-wrap`) → squash-merge 예정. PR single, 회귀 0.
- **production 파일**: 신설 1 / 수정 1.
  - 신설: `frontend/src/lib/gridNav.ts` (`computeNextIndex` pure helper 67줄)
  - 수정: `frontend/src/components/files/FileTable.tsx` (handleKeyDown ArrowKey 4 case → helper 1 분기 + 주석 갱신)
- **test**: 신설 1 / 수정 1. `gridNav.test.ts` 25 GREEN; `FileTable.test.tsx` 6 케이스 추가(3→9). 최종 frontend `pnpm test --run` GREEN 594/594(baseline 588 → +6 wrap 케이스). `pnpm typecheck` + `pnpm lint` + `pnpm build` clean.
- **docs sync**: `docs/01-frontend-design.md §12.1` 키맵 표 List/Grid 컬럼 분리 + Grid 2D 정책 주(↓ overshoot clamp, ↑ 첫행 stay, pending stride skip).

### 핵심 결정 (M16VK 트랙)

1. **Pure helper 분리** — handleKeyDown은 이미 130+라인. `lib/gridNav.ts`로 핵심 인덱스 계산을 추출해 ResizeObserver mock 없이 unit test 가능. CLAUDE.md ULTIMATE INVARIANT 5(확장 전 검토) 충족 — 추상화 정당화는 "ResizeObserver 의존 회피 + 25 케이스 vitest 단순화".
2. **↑/↓ 정책 = column stride** — `prev ± gridSafeColumns`. ↑ overshoot(prev<columns)는 stay(첫 행 wrap 없음), ↓ overshoot은 last partial row에 항목 있으면 `length-1`로 clamp, 없으면 stay. Windows Explorer / macOS Finder 동작 답습.
3. **←/→ 정책 = ±1 + row 경계 자연 wrap** — list 모드 ↑/↓ 1D 패턴을 그대로 차용. 사용자 멘탈 모델 일관.
4. **List 모드 변경 0** — ←/→는 List에서 helper 안에서 no-op으로 처리 + `preventDefault` 스킵으로 상위 핸들러(textbox 캐럿 이동 등)에 영향 없음. 회귀 테스트 GREEN.
5. **prev=-1 초기 focus** — ↓/→는 첫 non-pending로 진입(기존 List 동작 보존), ↑/←는 stay. helper 진입부에서 단일 분기로 처리해 view 무관 통일.
6. **pending skip = stride 방향** — ↑/↓는 column stride(±columns), ←/→는 1-step. helper 내 `walk(prev, step, length, isPending)` 단일 함수로 통합 — step 부호/크기만 호출자가 결정.
7. **shift 범위 확장은 모든 방향 일관** — selectRange anchor 유지. 사용자 입장에서 ↑↓←→ 모두 동일 모델.
8. **`useGridColumns` 변경 0** — ResizeObserver 기반 columns 계산은 그대로 사용. 입력 1개(columns) 추가만으로 helper 동작.

### 파급 영향

- **`docs/01 §12.1`**: 키맵 표 List/Grid 컬럼 분리 + Grid 2D 정책 주 추가.
- **frontend backlog 정리**: M16V closure entry(`docs/progress.md`)의 "Grid 2D 키보드 wrap" 항목 closed. 잔여(Grid DnD, 가변 카드 높이, 썸네일 이미지)는 유지.
- **DB/스키마/backend**: 변경 0 — frontend-only 트랙.

---

## 2026-05-02 — 🏁 storage-orphan-cleanup 트랙 종료 (Storage Orphan Cleanup daily cron — A15/A7 backlog closure)

### 범위

OC.0 (worktree `feature/storage-orphan-cleanup` from `65e5cd3` A15 closure + dev-docs bootstrap `dev/active/storage-orphan-cleanup/`, 3파일 commit `941b6d5`) → OC.1 (`AuditEventType.STORAGE_ORPHAN_CLEANED("storage.orphan.cleaned")` enum + wire 추가, `StorageOrphanCleanupProperties` `@ConfigurationProperties("app.storage.orphan-cleanup")` record `{enabled, cron, zone, maxPerRun, graceHours, batchSize}` + `SchedulingConfig` `@EnableConfigurationProperties` 등록 + `application.yml` 블록 추가, `frontend/src/types/audit.ts` union에 `'storage.orphan.cleaned'` 추가) → OC.2 (`StorageObject` record `(String key, Instant lastModified)` 신설, `StorageClient.listOlderThan(Duration grace)` interface 확장, `LocalFsStorageClient.listOlderThan` impl — `Files.walk(root)` 기반 lazy stream + UUID regex match + mtime grace 컷오프 + 비-UUID name skip + WARN log, 6 RED→GREEN 테스트) → OC.3 (`FileVersionRepository.streamActiveStorageKeys()` `@Query("SELECT v.storageKey FROM FileVersion v")` + `@QueryHints(fetchSize=200, readOnly=true)`. **Plan 정정**: 원안 `JOIN files WHERE deleted_at IS NULL`은 trash file의 storage 보호 invariant 위반(soft-delete 30일 grace 내 storage 객체가 orphan으로 분류되어 삭제 → 복원 시 데이터 손실)이라 단순 stream으로 변경 — A7 hard purge cascade가 file_versions row를 cascade 삭제하므로 다음 cron에서 자연 orphan 분류. 3 신규 `@Transactional` 테스트) → OC.4 (`StorageOrphanCleanupResult` record `(runId, scanned, candidates, deleted, failed, truncated, durationMs)` + `StorageOrphanCleanupService.runDailyCleanup(maxPerRun, graceHours)` 5-stage 파이프라인 — liveSet 적재 → walk → diff → per-row delete(IOException isolation) → audit emit `STORAGE_ORPHAN_CLEANED`. `@Transactional(readOnly=true)` outer + `AuditService.record` REQUIRES_NEW. 8 Mockito 유닛 테스트 — happy/empty/cap/per-row 실패 isolation/non-uuid skip/audit JSON 7-field/invalid args/walk IOException) → OC.5 (`StorageOrphanCleanupJob @Component @ConditionalOnProperty + @Scheduled` — props 기반 cron/zone, RuntimeException catch-all + truncated WARN log, `StorageOrphanCleanupJobDisabledIntegrationTest` `@TestPropertySource(enabled=false)` bean 부재 검증, `StorageOrphanCleanupIntegrationTest` E2E with real Postgres + LocalFs `@TempDir` — live key + orphan(grace 통과) + orphan(in-flight) 3-객체 시나리오로 deletes orphans only + preserves live and in-flight 검증) → OC.6 (closure: ADR #38 + docs/02 §5.6 + docs/04 §13 row + docs/03 §4.1 audit type + progress entry + dev-docs archive + PR + master squash-merge. **참고**: master에 A16(ADR #37)이 먼저 머지되어 본 트랙 ADR은 #38로 재번호).

### 회고

- **commits**: 6 on top of `65e5cd3` A15 closure (worktree branch `feature/storage-orphan-cleanup`) → squash-merge 예정. PR single, 회귀 0.
- **production 파일**: 신설 6 / 수정 5.
  - 신설(backend): `StorageOrphanCleanupProperties.java`, `StorageObject.java`, `StorageOrphanCleanupResult.java`, `StorageOrphanCleanupService.java`, `StorageOrphanCleanupJob.java`
  - 수정(backend): `StorageClient.java` (listOlderThan 추가), `LocalFsStorageClient.java` (listOlderThan impl + UUID_PATTERN), `FileVersionRepository.java` (streamActiveStorageKeys), `AuditEventType.java` (enum 추가), `SchedulingConfig.java` (Properties 등록), `application.yml` (`app.storage.orphan-cleanup.*` 블록)
  - 수정(frontend): `types/audit.ts` (union 추가)
- **test 파일**: 신설 4 + 수정 1. `LocalFsStorageClientTest`(+6 listOlderThan 케이스), `FileVersionRepositoryTest`(+3 streamActiveStorageKeys), `StorageOrphanCleanupServiceTest`(8 Mockito), `StorageOrphanCleanupJobDisabledIntegrationTest`(1 disabled bean 검증), `StorageOrphanCleanupIntegrationTest`(2 E2E — bean registered + deletes orphans only). Testcontainers `disabledWithoutDocker=true` Docker 미가용 환경 자동 skip. backend `./gradlew test` GREEN, frontend 527/527 + typecheck/lint clean.
- **docs sync**: `docs/00-overview.md` ADR #38 신규 row, `docs/02-backend-data-model.md` §5.6 신규 섹션 (storage orphan cleanup 알고리즘 + properties + S3 확장 hook), `docs/04-admin-operations.md` §13 표 행 1개(`storage.orphan.cleanup` daily 01:00) + 각주 [§], `docs/03-security-compliance.md` §4.1 union에 `'storage.orphan.cleaned'` 추가. DB/스키마 변경 0.

### 핵심 결정 (storage-orphan-cleanup 트랙, 확정 → ADR #38)

1. **트리거 = daily cron(`0 0 1 * * *` Asia/Seoul) + 운영자 enable** — A7 hard purge / share-expired-cron / permissions-expired-cron 일관 (`enabled=false` default). A7 purge가 자정에 돌므로 1시간 격차로 trash purge → orphan 발생 → orphan 잡 처리 순.
2. **DB live set = `file_versions.storage_key` 전체 (NO `JOIN files WHERE deleted_at IS NULL`)** — Plan 원안의 JOIN은 trash 30일 grace 내 file의 storage 객체를 orphan으로 잘못 분류 → 복원 데이터 손실 위험. 단순 stream으로 정정 (CLAUDE.md §3 원칙 9 — 문제 은폐 금지). A7 hard purge cascade가 file_versions row 삭제 시점에 자연 orphan으로 분류되어 다음 cron에서 회수.
3. **Walk 대상 = LocalFs `{root}/{YYYY}/{MM}/*` 트리, `{UUID}` leaf만 candidate** — 비-UUID name / 디렉토리 leaf / symlink는 skip + WARN log. UUID regex 검증 후 mtime 비교.
4. **grace = mtime > NOW-24h skip** — in-flight 업로드(트랜잭션 timeout < 5분 가정) race 회피. `app.storage.orphan-cleanup.grace-hours=24` (default).
5. **삭제 cap = `max-per-run:10000`** — A7 `MAX_PURGE_PER_RUN` 패턴. 도달 시 `truncated=true` + 다음 cron 재시도. `truncated` 플래그 시 WARN log.
6. **per-row 실패 isolation** — 객체 1개 delete IOException → ERROR log + 다음 candidate 진행. counters에 `failed` 증가. 전체 잡 실패로 번지지 않음.
7. **Audit = summary 1건/run** = `STORAGE_ORPHAN_CLEANED` (target_type=`system`, target_id=`NULL`, actor_id=`NULL`, metadata=`{runId, scanned, candidates, deleted, failed, truncated, durationMs}`). A7 `SYSTEM_PURGE_EXECUTED` 일관 — per-row spam 회피. `REQUIRES_NEW` 트랜잭션으로 read-only outer trx와 분리.
8. **Lock = `@SchedulerLock` 미도입** — MVP single-instance 가정 (A7 패턴 일관). 멀티-인스턴스화 시 별도 ADR.
9. **Properties 네임스페이스 = `app.storage.orphan-cleanup.*`** — `app.*`(job-related) 일관, `ibizdrive.storage.*`(client config)와 분리. cron job은 `app:`, storage I/O 설정은 `ibizdrive:` — 기존 분리 답습.
10. **신규 enum 추가만으로 호환** — V3 `audit_log.event_type` CHECK 미존재 (VARCHAR(50) 자유). 마이그레이션 0.
11. **`StorageClient.listOlderThan` 시그니처 = `Stream<StorageObject> listOlderThan(Duration grace)`** — Stream lazy 보장으로 큰 트리에서 메모리 폭증 회피. caller(`try-with-resources`)로 close 책임. S3 impl(v1.x)은 ListObjectsV2 paginator로 자연 매핑(LastModified 비교).
12. **권한 트리거 = 시스템 잡 only** — HTTP endpoint 미도입(운영 트리거는 backlog). ROI 검증 후 admin endpoint 별도 ADR.

### 파급 영향

- **`docs/00 §5 ADR`**: #38 신규 row.
- **`docs/02 §5.6`**: 신규 섹션 (cleanup 알고리즘 + properties + S3 확장 hook).
- **`docs/04 §13`**: 배치 작업 표 행 1개(`storage.orphan.cleanup`) + 각주 [§].
- **`docs/03 §4.1`**: audit event union에 `'storage.orphan.cleaned'` 추가.
- **DB/스키마**: 변경 0.
- **Backend backlog**: S3StorageClient `listOlderThan` impl(ListObjectsV2 paginator), `@SchedulerLock`(멀티 인스턴스화 시), 운영자 트리거 admin endpoint(ROI 검증 후).
- **Frontend**: 인터페이스 변경 0 — `'storage.orphan.cleaned'` audit union 추가만, 사용처 0건(unknown 이벤트는 default 분기).

---

## 2026-05-02 — 🏁 A16 트랙 종료 (Department Subject Picker — Domain 도입 + 3-way picker)

### 범위

A16.0 (worktree `feature/a16-department-subject-picker` from `ab45e7d` BulkActionBar fix + dev-docs bootstrap) → A16.1 (V7 마이그레이션 — `departments` 테이블 + `users.department_id` FK + ltree extension + Department 도메인 6파일 entity/Repository/Service/Controller/2 DTOs + V7MigrationIT 9 + DepartmentRepositoryTest 5 + DepartmentSearchServiceTest 11 + DepartmentSearchControllerTest 4) → A16.2 (`PermissionRepository.findEffective` SQL에 dept 매칭 subquery 추가; PermissionRepositoryTest +6) → A16.3 (`ShareDto.subjectName` 필드 추가 + factory 갱신 + ShareCommandService.resolveSubjectName 단건 helper + ShareQueryService.fetchSubjectNames batch helper + everyone/lookup-miss null fallback; ShareCommand/QueryServiceTest +8 + ShareControllerTest fixture 14필드 갱신) → A16.4 (`frontend/src/types/department.ts` 신설 + `lib/api.ts:searchDepartments` + `lib/queryKeys.ts:qk.departments()`/`qk.departmentsSearch` + `types/share.ts ShareDto.subjectName` + 8 fixture `subjectName: null` 보강; `api.departments.test.ts` 7 GREEN) → A16.5 (`useDepartmentSearch` hook — useUserSearch 1:1 답습; 5 tests GREEN) → A16.6 (`DepartmentSearchCombobox.tsx` — UserSearchCombobox 1:1 답습, 표시 필드만 name; 12 tests GREEN) → A16.7 (ShareDialog — subjectType 3-way 라디오 + DepartmentSearchCombobox 마운트 + dept submit 분기 + `subjectLabel(type, id, subjectName)` subjectName-first fallback; +7 tests GREEN) → A16.8 (docs sync `docs/00 ADR #37` + `docs/02 §2.1/§2.2/§7.9/§7.15` + `docs/03 §3.3/§3.4/§3.5` + `docs/01 §14.4` + 본 progress entry + PR + master squash-merge + closure archive). **참고**: master에 A15가 먼저 머지되며 ADR #36을 점유 — A16 ADR은 #37로 재번호.

### 회고

- **commits**: 8 on top of `ab45e7d` (worktree branch `feature/a16-department-subject-picker`) → squash-merge 예정. PR single, 회귀 0.
- **production 파일**: 신설 12 / 수정 8.
  - backend 신설: `Department.java`, `DepartmentRepository.java`, `DepartmentSearchService.java`, `DepartmentSearchController.java`, `DepartmentSummaryDto.java`, `DepartmentSearchResponse.java`, `V7__departments_users_dept.sql`
  - backend 수정: `User.java`, `PermissionRepository.java`, `ShareDto.java`, `ShareCommandService.java`, `ShareQueryService.java`
  - frontend 신설: `types/department.ts`, `hooks/useDepartmentSearch.ts`, `components/shares/DepartmentSearchCombobox.tsx`
  - frontend 수정: `lib/api.ts`, `lib/queryKeys.ts`, `types/share.ts`, `components/shares/ShareDialog.tsx`
- **test**: backend +35 (V7MigrationIT 9 + DepartmentRepositoryTest 5 + DepartmentSearchServiceTest 11 + DepartmentSearchControllerTest 4 + PermissionRepositoryTest +6); frontend `api.departments.test.ts` 7 + `useDepartmentSearch.test.tsx` 5 + `DepartmentSearchCombobox.test.tsx` 12 + `ShareDialog.test.tsx` +7. 최종 backend `./gradlew test` GREEN 666 tests, frontend `pnpm test --run` GREEN 565/565 (baseline 533 → +32). `pnpm typecheck` + `pnpm lint` + `pnpm build` clean.
- **docs sync**: ADR #37 신설 / `docs/02 §2.2 V7 + §7.9 subjectName + §7.15 dept search` / `docs/03 §3.3 dept subject + §3.4 findEffective dept 분기 + §3.5 dept search guard` / `docs/01 §14.4 ShareDialog 3-way picker + DepartmentSearchCombobox + subjectName fallback`.

### 핵심 결정 (A16 트랙, 확정 → ADR #37)

1. **Department 도메인 도입 (V7)** — A14 결정 시점("V_ 마이그레이션 0")의 제약 해소. `departments` 테이블 + `users.department_id` FK 활성화. LTREE 컬럼은 schema 도입만, 애플리케이션은 flat 사용 (KISS, 트리 v1.x deferred).
2. **권한 평가 SQL 변경 필수** — A14 결정 #4 검증 정정. `PermissionRepository.findEffective`에 `subject_type='department' AND subject_id = (SELECT department_id FROM users WHERE id=:userId AND active)` OR 분기 추가. NULL/비활성 사용자는 unmatched(false). dept 후손 자동 포함은 v1.x deferred.
3. **ShareDto.subjectName = backend join (A13 패턴 답습)** — nullable 필드 1개 추가. user→users.display_name, department→departments.name, everyone/lookup miss → null. POST는 트랜잭션 내 단건 helper, by-me/with-me는 페이지 결정 후 batch helper(type별 1회 IN 절). 단건 lookup endpoint 미추가.
4. **Frontend = F6 답습 1:1** — `useDepartmentSearch`/`DepartmentSearchCombobox`는 user 변형의 동형. 일반화 거부 — 추상화 정당화 3+ 규칙 미충족 (KISS, ULTIMATE INVARIANT 5).
5. **Role share UI 보류** — schema impedance(role enum vs role-grant lookup). picker 라디오 3종(everyone/user/department)으로 한정. backend `subject_type='role'` persistable 유지 — v1.x role-share 트랙으로 분리.
6. **`subjectLabel` subjectName-first fallback** — `subjectName != null`이면 그대로 표시. null fallback 시 type+UUID 머릿8자(기존 동작 보존). everyone은 항상 "모든 사용자".

### 파급 영향

- **DB**: V7 마이그레이션 추가. 기존 V1~V6 무변경. `departments` 테이블 신설 + `users.department_id` FK 활성화.
- **wire**: ShareDto 13 → 14 필드 (`subjectName: string|null` 추가). Frontend fixture 8 위치 보강.
- **권한 평가**: `findEffective` OR 분기 추가. user/everyone/role grant는 기존 동작 보존, dept grant 신규 매칭.
- **frontend backlog**: F6 closure의 "department 옵션 backlog" 항목 closed. role 옵션은 v1.x backlog로 잔존.

---

## 2026-05-02 — 🏁 A15 트랙 종료 (Storage 모듈 + 파일 업로드/다운로드 endpoint)

### 범위

A15.0 (worktree `feature/a15-file-upload-download` from `09d4b52` A14 closure + dev-docs bootstrap `dev/active/a15-file-upload-download/`, 3파일 commit `b98b044` — ADR #13 재정정/ADR #36 신규 초안은 closure로 이월) → A15.1 (`backend/src/main/java/com/ibizdrive/storage/{StorageClient,LocalFsStorageClient,StorageProperties}.java` 신설 — interface `write(key,in,size)`/`read(key)`/`delete(key)` + LocalFs impl `{root}/{YYYY}/{MM}/{UUID}` 객체 키, `@ConfigurationProperties("app.storage")` `{type, root}`, `application.yml` 추가; `LocalFsStorageClientTest` 9 RED→GREEN — write/read roundtrip + 객체 키 포맷 + delete idempotent + 부재 read NoSuchFileException) → A15.2 (`backend/src/main/java/com/ibizdrive/file/{UploadResolution,UploadResult}.java` enum/record 신설 + `FileUploadService` skeleton + 7 Testcontainers RED — Docker 미가용 시 skip; audit emission은 file/ 패키지 기존 `emitAudit` direct convention 답습 — listener 미도입(ADR #36 채택)) → A15.3 (`FileUploadService.upload(...)` GREEN — folder lock + UNIQUE 이중 가드 + storage write + INSERT files+versions + UPDATE current_version_id + emitAudit FILE_UPLOADED, `FileVersionRepository.findMaxVersionNumberByFileId` + `FileRepository.lockActiveByFolderAndNormalizedName` 추가, RENAME 자동 suffix `(N)`) → A15.4 (`FileUploadController` POST `/api/files` + `UploadResponse` DTO + multipart 활성화 100MB cap; `FileUploadControllerTest` 8/8 GREEN — wire `resolution: new_version|rename|null`) → A15.5 (`FileDownloadService` + `FileDownloadController` + `DownloadHandle` 신설 — RFC 5987 Content-Disposition (`filename=` ASCII fallback + `filename*=UTF-8''` percent-encode) + `ETag("<versionId>")` + Content-Type fallback `application/octet-stream`, audit FILE_DOWNLOADED, 권한 = file `READ` (별도 `DOWNLOAD` enum 미도입); 7 service + 6 controller GREEN) → A15.6 (`frontend/src/lib/api.ts` `uploadFile` 분기를 실 `XMLHttpRequest`로 교체 — `POST /api/files` multipart + `withCredentials=true`, `frontend/src/lib/{fakeXhr.ts,fakeXhr.test.ts}` 삭제, `useUpload.ts` 타입 `XhrLike = XMLHttpRequest` 전환 + 409 envelope `{error:{code,message,details:{fileId,fileName}}}` 파싱(폴백 `conflictWith=undefined` → `UploadConflictDialog`가 `task.file.name`로 폴백 — 검증 `UploadConflictDialog.tsx:27`), MOCK_FILES side-effect 제거(backend authoritative), 신규 `api.upload.test.ts` 4 + `useUpload.test.ts` 9 (`vi.stubGlobal('XMLHttpRequest', MockXHR)` 패턴 + 파일명→응답 정적 테이블), 최종 527/527 GREEN + typecheck/lint clean) → A15.7 (ADR #13 supersede 마커 + 신규 ADR #36 (storage abstraction + multipart MVP), `docs/02 §7.6` 표에 POST `/api/files` 행 추가 + `download` guard `'DOWNLOAD'` → `'READ'` 정정 + 신규 §7.6.1 multipart spec(요청 form parts/응답 status/409 envelope/Audit)/download 헤더(RFC 5987/ETag/Content-Type fallback), §7.7 tus는 supersede 마커로 보존(MVP 미구현), `docs/progress.md` 본 entry + dev-docs archive + PR + master squash-merge).

### 회고

- **commits**: 7 on top of `09d4b52` A14 closure (worktree branch `feature/a15-file-upload-download`) → squash-merge 예정. PR single, 회귀 0.
- **production 파일**: 신설 8 / 수정 5.
  - 신설(backend): `StorageClient.java`, `LocalFsStorageClient.java`, `StorageProperties.java`, `FileUploadService.java`, `FileUploadController.java`, `FileDownloadService.java`, `FileDownloadController.java`, `DownloadHandle.java`, `UploadResolution.java`, `UploadResult.java`, `UploadResponse.java`
  - 수정(backend): `FileRepository.java` (lock/exists 헬퍼), `FileVersionRepository.java` (findMaxVersionNumberByFileId), `application.yml` (`app.storage.*` + multipart 한도)
  - 수정(frontend): `lib/api.ts` (실 XHR), `hooks/useUpload.ts` (XHR 타입 + 409 envelope details 파싱)
  - 삭제(frontend): `lib/fakeXhr.ts`, `lib/fakeXhr.test.ts`
- **test 파일**: 신설 6. `LocalFsStorageClientTest`(9), `FileUploadServiceTest`(7 Testcontainers), `FileUploadControllerTest`(8), `FileDownloadServiceTest`(7), `FileDownloadControllerTest`(6), `api.upload.test.ts`(4), `useUpload.test.ts`(9, 재작성). 최종 backend `./gradlew test` GREEN (Testcontainers Docker 가용 환경 한정), frontend `pnpm test --run` **527/527 GREEN** + `pnpm typecheck` + `pnpm lint` clean.
- **docs sync**: `docs/00-overview.md` ADR #13 supersede 마커 + 신규 ADR #36 (A15 정책 묶음), `docs/02-backend-data-model.md` §7.6 표 + 신규 §7.6.1 multipart/download spec + §7.7 supersede 마커. DB/스키마 변경 0 (storage 추상화는 코드 레벨, files/file_versions 테이블 재사용).

### 핵심 결정 (A15 트랙, 확정)

1. **MVP = 단일-POST multipart** — tus 프로토콜 v1.x 재이월. ADR #13 supersede(`docs/00 §5 ADR #36`) 명시. KISS+YAGNI: tus-java-server + 재개 토큰 lifecycle + S3 multipart 라이브러리 위임 비용이 100MB cap·평균 분포에서 ROI 역전. tus는 §7.7로 spec 보존(v1.x 재개 시 백업).
2. **Storage 추상화 = `StorageClient` interface + `LocalFsStorageClient` MVP impl** — S3 impl은 v1.x. AWS SDK v2 의존성 미추가. 객체 키 `{YYYY}/{MM}/{UUID}` (ADR #5 storage_key UUID 정합 — 원본 파일명은 DB에만).
3. **권한 = upload `UPLOAD` / download `READ`** — 별도 `DOWNLOAD` enum 미도입. `READ`가 view+download 모두 grant — KISS, docs/03 §3 권한 매트릭스 단순화. 업로드는 부모 folder의 `UPLOAD` 위임.
4. **Audit emission = file/ 패키지 직접 호출 convention** — `FileMutationService:298-315` `emitAudit` 헬퍼 답습. share/permission/auth는 listener 패턴이지만 file/ 패키지 내부 일관성을 위해 직접 호출 채택 — 동일 패키지에 두 패턴 혼재 회피 (KISS+§3). FILE_UPLOADED/FILE_DOWNLOADED 활성화.
5. **Conflict resolution wire format = `new_version` | `rename` | unset(409)** — M5 frontend 인터페이스 1:1. 자동 RENAME suffix `(N)` 부여(예: `report.pdf` → `report (2).pdf`).
6. **응답 status 분기**: 신규 파일 INSERT → **201 Created**, NEW_VERSION 분기 → **200 OK**. body = `UploadResponse{file: FileDto, versionId, versionNumber, newFile, resolution}`.
7. **409 envelope = `{error:{code:'RENAME_CONFLICT', message, details:{fileId, fileName}}}`** — `UploadConflictDialog`는 `details` 부재 시 `task.file.name` 폴백(검증 `UploadConflictDialog.tsx:27`).
8. **Download 헤더 = RFC 5987 + ETag(versionId) + Content-Type fallback** — `Content-Disposition: attachment; filename="<ascii-fallback>"; filename*=UTF-8''<percent-encoded>` (UTF-8 + 비ASCII 안전), `Content-Type` null/invalid → `application/octet-stream`, `ETag: "<versionId>"`, `Content-Length: version.sizeBytes`.
9. **Storage orphan = MVP 한정 알려진 한계** — 트랜잭션 실패 시 storage 객체 잔존 가능. cleanup job은 별도 트랙(편법 아닌 명시적 deferred — CLAUDE.md §3 원칙 9). storage 모듈 v1.x에서 `S3StorageClient` 추상화 + orphan detect 잡 cross-check 도입.
10. **Frontend FakeXHR 모듈 삭제** — `api.uploadFile`이 유일한 production importer였으므로 dev-only stub 격리 대신 완전 삭제. 테스트는 `vi.stubGlobal('XMLHttpRequest', MockXHR)` + 파일명→응답 정적 테이블로 FakeXHR 시절 magic-filename 계약 보존.

### 파급 영향

- **`docs/00 §5 ADR`**: #13 row에 Superseded 마커, #36 신규 row 추가.
- **`docs/02 §7.6`**: POST `/api/files` 행 신규 + `download` guard `'DOWNLOAD'` → `'READ'` 정정 + 신규 §7.6.1 multipart/download spec.
- **`docs/02 §7.7`**: tus spec은 supersede 마커와 함께 보존 (MVP 미구현, v1.x 백업).
- **DB/스키마**: 변경 0 — `files`/`file_versions` 테이블 재사용. `current_version_id` NULL 허용은 그대로(MVP 단일-버전 가정 유지).
- **Backend backlog**: storage orphan cleanup, S3 impl, Testcontainers Docker 부재 환경에서 service-level 단위 테스트(현재 7 케이스는 통합), tus 재개 업로드(v1.x ADR #13 재오픈).
- **Frontend**: 인터페이스 변경 0 — `UploadDock`/`upload store`/`ConflictDialog` 무수정. 시각적/기능적 회귀 0.

---

## 2026-05-01 — fix: BulkActionBar — 폴더 단일 선택 시 공유 버튼 활성화

### 변경

`frontend/src/components/files/BulkActionBar.tsx` — 공유 버튼의 `singleItem.type === 'file'` 가드 제거 + `handleShare` `kind: singleItem.type` 분기 + tooltip "단일 파일 선택 시 사용 가능" → "단일 항목 선택 시 사용 가능". 폴더 공유 endpoint(A12) + ShareDialog folder 분기(F5.2) + useCreateShare folder 변형이 모두 closed 상태이므로 BulkActionBar의 file-only 가드만 남아 있던 drift를 정정.

### 회고

- **production 파일**: 수정 1 (`BulkActionBar.tsx` — 3 line edit).
- **test 파일**: 수정 1 (`BulkActionBar.test.tsx` — 신규 describe 블록 + 4 케이스: file-kind 진입, folder-kind 진입, 다중 비활성, cache-miss 비활성).
- **검증**: `pnpm test --run` **533/533 GREEN** (baseline 529 → +4). `pnpm typecheck` + `pnpm lint` clean.
- **트랙 단위**: dev-docs 미생성 — 단일 atomic 변경(CLAUDE.md OPERATIONAL RULES "원자적 변경"). worktree 미생성, master 위 단일 commit.

### 핵심 결정 (확정)

1. **다중(2+) 공유는 비활성 유지** — wire는 `POST /api/{files|folders}/:id/share` 단건. 다중 공유는 (a) 클라이언트 반복 호출 (b) batch endpoint 신설 정책 미정 → 별도 트랙. 본 변경 scope는 단일 선택만.
2. **F5 closure backlog 글 정정** — "폴더 다중 선택 자체 부재"는 부정확. selection store(`Set<string>`) + FileTable 통합 row 모델로 폴더 다중 선택은 F4 시점부터 동작 중이었음. 진짜 missing은 BulkActionBar 공유 버튼의 file-only 가드 1줄.
3. **dev-docs 트랙 미생성** — 변경 단위가 1 컴포넌트 / 3 line / 4 테스트 케이스. 트랙 prelude(plan/context/tasks 3파일 + worktree + 6 phase)는 over-engineering.

### 파급 영향

- **frontend backlog 정리**: F5 closure의 "폴더 다중 선택 BulkActionBar 공유 액션" 항목 → folder-kind 단일 진입 부분 closed. 다중 공유는 backlog 잔존.
- **DB/스키마/wire**: 변경 없음.

---

## 2026-05-01 — 🏁 F6 트랙 종료 (Frontend Share Subject Picker — User)

### 범위

F6.0 (worktree `feature/f6-user-search-picker` from `09d4b52` A14 closure + dev-docs bootstrap `dev/active/f6-user-search-picker/`) → F6.1 (`UserSummary` 타입 신설 + `qk.users()`/`qk.usersSearch(normalized, limit)` 키 팩토리 + `api.searchUsers({q, limit?}, {signal?})` — q < 2 자체 short-circuit, default limit=20, `searchFiles` wire 패턴 그대로 답습; `api.users.test.ts` 7 케이스 GREEN) → F6.2 (`useUserSearch(rawQuery, {limit?})` — `useDebounce(300ms)` + `q.trim().toLowerCase()` (A14 ADR #35 — `normalizeForSearch` NFC collapse 미적용) + `useQuery` enabled `normalized.length >= 2` + `keepPreviousData` + `staleTime 30_000` + signal 전파; `useUserSearch.test.tsx` 5 케이스 GREEN) → F6.3 (`UserSearchCombobox.tsx` 신설 — WAI-ARIA 1.2 Combobox + Listbox self-contained, controlled `value: UserSummary|null` + `onChange`, internal `rawInput`/`isOpen`/`activeIndex` state, ArrowDown/Up wrap-around + Enter commit + Esc close + Click(`onMouseDown.preventDefault` + `onClick.commit`), 선택 후 재입력 시 `onChange(null)` (RenameDialog input-as-state 패턴), `aria-controls`/`aria-expanded`/`aria-activedescendant` 정합; 12 케이스 GREEN) → F6.4 (`ShareDialog.tsx` 통합 — `subjectType: 'everyone'|'user'` state default `'everyone'` + `selectedUser: UserSummary|null` state, 라디오 그룹 `모든 사용자 | 특정 사용자`, `subjectType==='user'` 분기로 Combobox 마운트, submit 분기: everyone → `{type:'everyone'}` / user+selected null → `toast.error('공유할 사용자를 선택해 주세요')` + return / user+selected → `{type:'user', id: selectedUser.id}`, dialog 재오픈 시 reset; `ShareDialog.test.tsx` +5 케이스 GREEN) → F6.5 (`docs/01 §14.4`에 subjectType 라디오 흐름 + `UserSearchCombobox`/`useUserSearch` 등재 + `docs/progress.md` 본 entry + dev-docs archive + PR + master squash-merge).

### 회고

- **commits**: 6 on top of `09d4b52` A14 closure (worktree branch `feature/f6-user-search-picker`) → squash-merge 예정. PR single, 회귀 0.
- **production 파일**: 신설 4 / 수정 3.
  - 신설: `frontend/src/types/user.ts`, `frontend/src/hooks/useUserSearch.ts`, `frontend/src/components/shares/UserSearchCombobox.tsx`
  - 신설(api 메서드): `api.searchUsers` (`frontend/src/lib/api.ts` 증분)
  - 수정: `frontend/src/lib/api.ts`, `frontend/src/lib/queryKeys.ts`, `frontend/src/components/shares/ShareDialog.tsx`
- **test 파일**: 신설 4. `api.users.test.ts`(7), `useUserSearch.test.tsx`(5), `UserSearchCombobox.test.tsx`(12), `ShareDialog.test.tsx`(+5). 최종 `pnpm test --run` **529/529 GREEN** (baseline 500 → +29). `pnpm typecheck` + `pnpm lint` + `pnpm build` clean.
- **docs sync**: `docs/01-frontend-design.md §14.4` — subject picker user 옵션 + Combobox 등재 + subjectType 라디오 흐름. DB/스키마 변경 0 (frontend 전용 트랙).

### 핵심 결정 (F6 트랙, 확정)

1. **`useUserSearch` normalize = `trim().toLowerCase()` only** — A14 ADR #35 정책. `normalizeForSearch`(NFC collapse + 다이아크리틱 제거)는 file/folder name 검색 전용. 사용자 displayName/email은 1:1 매칭 의도 — Unicode collapse는 false-positive 위험.
2. **Combobox는 self-contained (외부 a11y 라이브러리 거부)** — KISS + 의존성 최소. WAI-ARIA 1.2 Combobox + Listbox 패턴 직접 구현. 키보드 1D + wrap-around로 충분, 스크롤-into-view 미구현(option 10개 cap 가정).
3. **선택 = `value` controlled, input 텍스트 = internal state** — RenameDialog input-as-state 패턴 차용. 재입력 시 `onChange(null)`로 이전 선택 무효화 → submit 시 race 방지.
4. **단일 선택만 (multi-chip 없음)** — A14 wire가 `subjects[]`(다중)을 지원하지만 본 트랙 scope 외. 다중은 v1.x.
5. **subjectType radio default `'everyone'`** — 기존 ShareDialog UX 보존. 사용자가 명시적으로 `특정 사용자` 선택해야 picker 마운트.
6. **submit guard = inline toast** — `selectedUser=null + subjectType=user` 시 toast.error로 재시도 유도. dialog 닫지 않음.
7. **dialog 재오픈 시 reset** — open useEffect 안에서 `setSubjectType('everyone')` + `setSelectedUser(null)` — 이전 선택 누수 방지.
8. **`Esc` propagation 차단** — Combobox 자체 Esc는 `e.stopPropagation()` 후 listbox close만 수행. ShareDialog의 Esc 닫기와 충돌 방지(listbox 열림 상태에서 Esc → dialog까지 닫히면 UX 손실).

### 파급 영향

- **A14 wire 활용**: A14가 등재한 `GET /api/users/search` (q minLen 2, limit 1~50 default 20 cap 50) 1:1 사용. backend 변경 0.
- **frontend backlog**: department/role subject picker(별도 endpoint 필요), 다중 선택 chip, 사용자 lookup 캐시 공유(`qk.usersSearch` staleTime 30s 활용), Combobox 외부-클릭 close(현재 옵션 click + Esc만 close — modal 안에서 충분).
- **DB/스키마**: 변경 없음.

---

## 2026-05-01 — 🏁 M16 follow-up Grid 가상화 종료 (`useGridColumns` + row 단위 `useVirtualizer`)

### 범위

M16V.0 (worktree `feature/m16-grid-virtual` from `9cba282` A13 closure + dev-docs bootstrap `dev/active/m16-grid-virtual/`) → M16V.1 (`frontend/src/hooks/useGridColumns.ts` 신설 — container width를 ResizeObserver로 구독해 `Math.max(1, floor((width+gap)/(min+gap)))` columns 산출, SSR 안전 초기 1) → M16V.2 (`FileTable.tsx` grid 분기 row 단위 가상화 — 별도 `gridContainerRef` + `gridVirtualizer` 인스턴스, `count = ceil(items / columns)`, `estimateSize = 168`(고정), virtualRow 안에서 inline `gridTemplateColumns: repeat(N, minmax(0,1fr))`로 슬라이스 렌더, `data-grid-virtual="true"` 마커, `aria-rowcount`를 `gridRowCount`로 정정) → M16V.3 (`handleKeyDown` ArrowUp/Down 시 `view==='grid'` 분기로 `gridVirtualizer.scrollToIndex(Math.floor(idx/columns))` 호출, list 분기는 무수정) → M16V.4 (vitest `useGridColumns.test.ts` 4 케이스 + `FileTable.test.tsx`에 `@tanstack/react-virtual`/`ResizeObserver` mock + grid 마커 검증 1 추가, 기존 list/grid 시나리오 무수정) → M16V.5 (`docs/01 §18 row 16` footnote에 가상화 closed 마커 + `docs/progress.md` 본 entry + dev-docs archive).

### 회고

- **commits**: 1 on top of `9cba282` A13 closure (worktree branch `feature/m16-grid-virtual`) → squash-merge 예정. 단일 PR.
- **production 파일**: 신설 1 / 수정 1.
  - 신설: `frontend/src/hooks/useGridColumns.ts`
  - 수정: `frontend/src/components/files/FileTable.tsx`
- **test 파일**: 신설 1 / 수정 1. `frontend/src/hooks/useGridColumns.test.ts`(4) + `FileTable.test.tsx` (+1 신규 `data-grid-virtual` 마커 검증, mock 인프라 추가).
- **검증**: `pnpm test` **66 files / 500 tests GREEN** (baseline 65/495 → +1/+5). `pnpm typecheck` + `pnpm lint` clean.
- **docs sync**: `docs/01 §18 row 16` footnote 가상화 closed 마커 + v1.x 잔여 명시.

### 핵심 결정 (M16V 트랙, 확정)

1. **두 개의 virtualizer 인스턴스(list/grid) — view 분기 안에서만 active** — 단일 인스턴스의 view-aware count 분기는 dependency 폭발/coupling 야기. 분기는 서로 다른 컴포넌트 트리이므로 자연 격리(unmount/mount).
2. **`CARD_ROW_HEIGHT = 168` 고정 estimate** — 가변 높이(`measureElement`)는 v1.x. KISS — 카드 내부 layout 안정.
3. **inline style `gridTemplateColumns`** — Tailwind dynamic class(`grid-cols-${n}`)는 JIT 미스. `gap`도 inline. KISS.
4. **키보드 1D 유지** — `focusedIndex ±1`. grid 모드는 `Math.floor(idx/columns)`로 row index scroll만 추가. 좌/우 wrap (2D 네비게이션)은 v1.x.
5. **list 분기 코드 zero-touch** — 회귀 0 강제. 추가 변경은 (a) grid 전용 ref/virtualizer 추가, (b) focus selector view-aware fallback 1 line, (c) `handleKeyDown` 내 scroll 분기 helper 1개로 격리.
6. **`aria-rowcount` 정정** — 기존 grid는 `items.length`로 잘못 표기. `gridRowCount`(=ceil(items/columns))가 ARIA 의미상 정확.
7. **테스트 mock 전략** — `@tanstack/react-virtual` 테스트 파일 단위 mock(전 항목 visible 반환)으로 jsdom 0-viewport 한계 우회. ResizeObserver는 인라인 클래스 stub. **전역 setup 변경 없음** — 영향 격리.
8. **lockfile 미포함** — repo가 `pnpm-lock.yaml`을 트랙 안 함(.gitignore 미설정이지만 master 부재). 본 PR도 미포함 정책 유지.

### 파급 영향

- **M16 본체(2026-04-29) 비범위 절 진행**: M16 closure 시 `가상화 / 2D 키보드 / DnD / 썸네일`을 v1.x로 분리한 결정 중 **가상화만** closed. 나머지 3개는 그대로 v1.x 잔여.
- **M16V scope 외 backlog**: Grid 2D 키보드 wrap (좌/우 + columns 기반 ↑/↓), Grid DnD (list useDraggable 재사용 + drop target 시각화), 썸네일 이미지 (backend thumbnail API 후), 가변 카드 높이 (`measureElement`).
- **DB/스키마**: 변경 없음.
- **다른 계약 파일(`queryKeys`, `audit.ts` 등)**: 변경 없음.

---

## 2026-05-01 — 🏁 A13 트랙 종료 (`ShareDto` ↔ `permissions` join 복원, subject/preset surface)

### 범위

A13.0 (worktree `feature/a13-shares-permissions-join` from `544afc9` F5 closure 후 `fe9e963` permissions-expired-cron 위로 rebase + dev-docs bootstrap `dev/active/a13-shares-permissions-join/`) → A13.1 (`ShareDto` record에 `subjectType`/`subjectId`/`preset` 3 필드 복구, Jackson record 직렬화 wire 정합) → A13.2 (`ShareCommandService.createFileShares` / `createFolderShares` POST 응답 트랜잭션 내 `PermissionRow` 그대로 매핑 — 추가 SELECT 없음) → A13.3 (`ShareQueryService.listSharesByMe` / `listSharesWithMe` 페이지 결정 후 `permissionRepository.findAllById(ids)` **1회 IN-batch** N+1 회피, MAX_LIMIT=100 → 페이지당 최대 100 IN) → A13.4 (`ShareControllerTest` wire JSON 3 필드 surface 검증 강화 + `ShareQueryServiceTest` Mockito `grantRow` 로컬 변수 추출로 nested-stubbing exception 회피 + `ShareCommandServiceTest` POST 응답 매핑 단위 테스트 보강) → A13.5 (frontend `ShareDto` interface 3 필드 + `SharesTable` 4열 복원 `항목 | 공유한 사람 | 권한 | 만료` + `ShareDialog` 기존-share 행 `subjectLabel · presetLabel · 만료/무기한 + 해제` + `presetLabel` / `subjectLabel` helper) → A13.6 (`docs/00 §5 ADR #34` A13 closure 마커 + `docs/01 §14.4` 4열·subjectLabel·presetLabel 반영 + `docs/02 §7.9` POST/by-me/with-me 응답 schema 3 필드 + N+1 회피 IN-batch 명시) → A13.7 (PR #32 squash-merge `393e38f` + dev-docs archive).

### 회고

- **commits**: 1 on top of `fe9e963` permissions-expired-cron closure (worktree branch `feature/a13-shares-permissions-join`) → squash-merge `393e38f` on `master`. PR #32 single, frontend vitest + backend junit 모두 1회 GREEN. rebase 시 `docs/00-overview.md` ADR #34 cell 충돌 발생(master의 permissions-expired-cron closure marker vs A13 closure marker 동일 cell 확장) → 둘 다 보존하는 형태로 수동 머지.
- **production 파일**: 신설 0 / 수정 8.
  - backend: `ShareDto.java`, `ShareCommandService.java`, `ShareController.java`, `ShareQueryService.java`
  - frontend: `frontend/src/types/share.ts`, `frontend/src/components/shares/SharesTable.tsx`, `frontend/src/components/shares/ShareDialog.tsx`
- **test 파일**: 신설 0 / 수정 9. `ShareCommandServiceTest`, `ShareControllerTest`, `ShareQueryServiceTest` + frontend `useSharesByMe.test.tsx`, `useSharesWithMe.test.tsx`, `useCreateShare.test.tsx`, `api.shares.test.ts`, `SharesTable.test.tsx`, `ShareDialog.test.tsx`. backend share + audit GREEN, frontend 65 files / 495 tests GREEN.
- **docs sync**: `docs/00 §5 ADR #34` (A13 closure + permissions-expired-cron closure 양립), `docs/01 §14.4`, `docs/02 §7.9`. DB/V_ 마이그레이션 변경 0.

### 핵심 결정 (A13 트랙, 확정)

1. **POST 응답 = 트랜잭션 내 `PermissionRow` 그대로 매핑 (추가 SELECT 0)** — INSERT 시점에 grant row 객체가 이미 메모리에 존재 → `ShareDto` 생성 시 그 자리에서 reuse. by-me/with-me는 페이지 결정 후 `findAllById` IN-batch 1회. 두 경로의 패턴 차이는 의식적 — POST는 단일 row, query는 페이지(N rows)이므로 배치 대상이 다름.
2. **N+1 회피 = `findAllById` (Spring Data 표준 IN 절)** — 별도 JPQL/native query 추가 거부. MAX_LIMIT=100이 IN 절 길이 상한 보장 → 별도 chunking 불요.
3. **V6 FK CASCADE invariant** — active share row의 `permission_id`가 가리키는 permission row는 항상 존재. 누락 시 `IllegalStateException` (operationally unreachable, defense-in-depth만). N+1 회피 batch에서 `Map<UUID, PermissionRow>` lookup 시 missing key는 invariant 위반.
4. **frontend SharesTable 4열 복원** — F5.1에서 3열로 단순화한 결정을 정정. F5.1 당시는 backend wire에 preset이 없어 frontend 표기 불가 → 본 트랙에서 wire 복원과 동시에 컬럼 복원. presetLabel 한글화(read→읽기 / upload→업로드 / edit→편집 / admin→관리).
5. **subjectLabel = `everyone` → "모든 사용자", 나머지 → `{type} {id.slice(0,8)}`** — UUID 머릿8자 노출은 가독성 vs 식별 가능성 trade-off. department/role 이름 lookup은 별도 트랙(권한 관리 UI) 영역.
6. **ShareControllerTest wire JSON 검증 강화** — 3 필드 surface는 record 필드 추가만으로 자동 직렬화되지만, drift 방지 위해 controller test에서 `subjectType`/`subjectId`/`preset` JSON path 명시 단언.
7. **Mockito `grantRow` 로컬 변수 추출** — `ShareQueryServiceTest`에서 `when(permissionRepository.findAllById(...)).thenReturn(List.of(grantRow(...)))` 패턴이 nested-stubbing(`grantRow` 내부 stubbing 호출이 외부 `when` 진행 중에 발생) → `UnfinishedStubbingException`. 해결: `PermissionRow grant = grantRow(...)`로 outer stubbing 진입 전 evaluate.
8. **rebase 충돌 정책 = 둘 다 보존** — `permissions-expired-cron closure`(master) + `A13 closure`(branch) 모두 ADR #34 cell의 시간순 closure marker 누적이 의도. 한쪽 drop 거부.

### 파급 영향

- **F5 wire drift 정정**: F5.1에서 의식적으로 제거(`backend wire에 surface 못함`)했던 3 필드가 본 트랙으로 복구. 남은 `permissions` 직접 grant UX(만료 시각, message)는 별도 트랙.
- **ADR #34 누적**: A10 → A12 → SHARE_EXPIRED → permissions-expired-cron → A13 5개 closure marker가 동일 cell에 누적. 다음 트랙도 cell 분할이 아닌 marker append 형태 유지.
- **frontend audit.ts / queryKeys 등 다른 계약 파일**: 변경 없음.
- **DB/스키마**: 변경 없음 (V_ 마이그레이션 0개).
- **잔여 backlog (A13 scope 외)**: department/role subject name lookup, ShareDialog에서 subject picker(현재는 `everyone`만 default UI), `permissions.message` 컬럼 도입 시 ShareDto에 `message` 필드 별도 노출 검토.

---

## 2026-05-01 — 🏁 permissions-expired-cron 트랙 종료 (`permissions.expires_at` 만료 cron + `permission.expired` audit)

### 범위

PE.0 (worktree `feature/permissions-expired-cron` from `544afc9` F5 closure + dev-docs bootstrap `dev/active/permissions-expired-cron/`) → PE.1 (`PermissionExpiredEvent` 신규 record + `PermissionRepository.lockById(UUID)` PESSIMISTIC_WRITE + `findExpiredActiveIds(Instant, Pageable)` JPQL + `PermissionService.expirePermission(UUID)` lock→snapshot→DELETE→publish) → PE.2 (`AuditEventType.PERMISSION_EXPIRED("permission.expired")` + `PermissionAuditListener.onPermissionExpired` system metadata helper + `frontend/src/types/audit.ts` union 추가) → PE.3 (`PermissionExpirationProperties` record + `PermissionExpirationJob @ConditionalOnProperty + @Scheduled` + `SchedulingConfig` 등록 + `application.yml` `app.permission.expiration.*` 블록 default disabled) → PE.4 (Mockito-only `PermissionExpirationJobTest` 6개 + `PermissionServiceExpireTest` 5개 + `PermissionAuditListenerTest.onPermissionExpired_*` 3개 + Testcontainers `PermissionRepositoryTest.findExpiredActiveIds_*` 4개) → PE.5 (`docs/00 §5 ADR #34` closure 마커 추가 + `docs/02 §2.6` 본문 1줄 + 신규 `§7.10.1` 9-row 정책 표 + `docs/03 §4.1` enum mirror + `docs/04 §13` 배치 표 row + `[‡‡]` footnote) → PE.6 (PR #31 fix commit 1 후 squash-merge `00d05d6` + dev-docs archive).

### 회고

- **commits**: 2 on top of `544afc9` F5 closure (worktree branch `feature/permissions-expired-cron`) → squash-merge `00d05d6` on `master`. PR #31 single, 초기 backend CI 1 fail (`PermissionRepositoryTest.findExpiredActiveIds_returnsOnlyExpiredOldestFirst` — `idx_permissions_unique` 위반: 동일 `(folder, user, "user", user)` tuple에 read/edit/admin 3 row 시도) → fix commit `4e7b3a5` (subject 3명 분리). 재실행 frontend vitest + backend junit 모두 GREEN.
- **production 파일**: 신설 3 / 수정 7.
  - 신설: `PermissionExpiredEvent.java`, `PermissionExpirationProperties.java`, `PermissionExpirationJob.java`
  - 수정: `PermissionRepository.java`, `PermissionService.java`, `AuditEventType.java`, `PermissionAuditListener.java`, `SchedulingConfig.java`, `application.yml`, `frontend/src/types/audit.ts`
- **test 파일**: 신설 2 / 수정 2. `PermissionExpirationJobTest`(6) + `PermissionServiceExpireTest`(5) + `PermissionAuditListenerTest`(+3) + `PermissionRepositoryTest`(+4 + helper). 597/597 GREEN.
- **docs sync**: `docs/00 §5 ADR #34`, `docs/02 §2.6` + 신규 `§7.10.1`, `docs/03 §4.1`, `docs/04 §13`. grep `permission.expired|permissions-expired-cron|PERMISSION_EXPIRED|permission.expire` — 4개 docs + `audit.ts` + `AuditEventType.java` 일관.

### 핵심 결정 (permissions-expired-cron 트랙, 확정)

1. **DELETE only (soft-delete 불가)** — `permissions` 테이블에 `revoked_at` 컬럼 부재. SHARE_EXPIRED는 `shares.revoked_at=NOW()` + `permissions` row delete의 2단계지만, 본 트랙은 `permissions` row 단일 DELETE. 향후 soft-delete 필요 시 별도 마이그레이션.
2. **lockById 단일 조건 lock** — SHARE의 `lockByIdAndRevokedAtIsNull`(조건부) 대비 단순. race 시(다른 cron 인스턴스 / 사용자 직접 revoke) lock-then-query miss → `ResourceNotFoundException` → job swallow. 분산락 별도 도입 불요.
3. **revoke와 expire helper 추출 거부** — KISS. lock 메서드 다름(`findById` vs `lockById`) + event 타입 다름(`PermissionRevokedEvent` vs `PermissionExpiredEvent`) → helper 추출이 가독성 ↓. SHARE 트랙에서도 동일 판단.
4. **`expirationMetadataJson` 별도 helper** — grant/revoke의 `resourceMetadataJson` 형식 보존하면서 `"trigger":"system.expiration"` 키만 추가. listener 책임 안에 응집.
5. **cron의 가치 = (a) DB cleanup, (b) audit row** — `findEffective`가 이미 `expires_at > NOW()` 필터링하므로 cron이 없어도 만료 grant는 보안 평가에서 제외됨. 따라서 cron의 보안적 효과는 0, 운영적 효과(테이블 비대 방지 + 만료 추적성)만 의미.
6. **default disabled** — SHARE 트랙과 동일 패턴. staging/prod에서 명시적으로 `app.permission.expiration.enabled=true`로 활성화.
7. **테스트 unique 위반 수정 = subject 분리** — `idx_permissions_unique=(resource_type, resource_id, subject_type, subject_id)`는 `preset`을 키에 포함하지 않음. 동일 (resource, subject) 위에 다른 preset row를 만들 수 없음. 테스트는 owner/subject1/subject2/subject3 4명 user 분리로 해결.

### 파급 영향

- **ADR #34 backlog**: SHARE_EXPIRED(2026-05-01) closure 시 잔여로 표기됐던 `permissions.expires_at` 직접 grant 만료 cron이 본 트랙으로 closed. 두 트랙 모두 audit row `metadata.trigger='system.expiration'`로 system 트리거 분별. SSE emission(`permission.expired`)은 ADR #14 인프라 milestone까지 그대로 deferred.
- **frontend**: `audit.ts` union member 1줄 추가만 — 향후 audit log UI(M12)에서 자동 인식. 별도 UI 작업 불필요.
- **운영 가이드 (`docs/04 §13`)**: 배치 작업 표에 `permission.expire` row 추가 — `app.permission.expiration.{enabled, batch-size, cron, zone}` 4 properties 명시.
- **DB/스키마**: 변경 없음 (V_ 마이그레이션 0개).

---

## 2026-05-01 — 🏁 F5 마일스톤 종료 (Frontend Folder Share UI 확장 + ShareDto wire 정합)

### 범위

F5.0 (worktree `feature/f5-frontend-folder-share-ui` from `7c179d1` F4 closure + dev-docs bootstrap `dev/active/f5-frontend-folder-share-ui/`) → F5.1 (ShareDto wire 10필드 정합 — file_id/folder_id XOR + revokedAt/revokedBy 노출 + subjectType/subjectId/preset 제거 = backend `com.ibizdrive.share.ShareDto` record와 1:1, `api.createShares` → `createFileShares`/`createFolderShares` 분리 + `postShareCreate` 헬퍼, `useShareUiStore` `target: ShareTarget` discriminator 도입, `BulkActionBar`는 `{kind:'file'}` 명시, ShareDialog는 target 기반 + 기존공유 행 표시 단순화(만료+해제만), SharesTable 컬럼 4→3 + folder/file 아이콘 분기) → F5.2 (`useCreateShare` Vars `{target, req}` discriminated 전환 + target.kind 분기 mutationFn, ShareDialog `components/files/` → `components/shares/` 이동 + folder kind 분기 활성 + kind-aware 부제/NOT_FOUND toast, `ClientFilesPage.tsx` import 경로 갱신) → F5.3 (`Breadcrumb` 우측 폴더 공유 진입점 — 비루트 + `can.SHARE` 게이트, `Breadcrumb.test.tsx` 신규 +3 케이스) → F5.4 (`docs/01 §14.4` F4→F5 확장 sync — ShareDto 10필드/매칭식/wire 부재 항목 명시) → F5.5 (PR #30 squash-merge `abb8506` + dev-docs archive).

### 회고

- **commits**: 1 on top of `6f4377f` M12 closure (worktree branch `feature/f5-frontend-folder-share-ui`) → squash-merge `abb8506` on `master`. PR #30 single, CI green (frontend vitest + backend junit 모두 SUCCESS, fix commit 0).
- **production 파일**: 수정 9 / 이동 1(2파일) / 신설 1.
  - 수정: `frontend/src/types/share.ts`, `frontend/src/stores/shareUi.ts`, `frontend/src/lib/api.ts`, `frontend/src/hooks/useCreateShare.ts`, `frontend/src/components/shares/SharesTable.tsx`, `frontend/src/components/folders/Breadcrumb.tsx`, `frontend/src/components/files/BulkActionBar.tsx`, `frontend/src/app/(explorer)/files/[...parts]/ClientFilesPage.tsx`, `docs/01-frontend-design.md` §14.4
  - 이동: `frontend/src/components/files/ShareDialog.{tsx,test.tsx}` → `frontend/src/components/shares/ShareDialog.{tsx,test.tsx}` (소유 경계 정합 — file 전용 컴포넌트 아님)
  - 신설: `frontend/src/components/folders/Breadcrumb.test.tsx`
- **test 파일**: 수정 6 / 신설 1. wire-aligned 10필드 fixture 일괄 갱신 + folder kind 케이스 추가. 최종 494/494 GREEN.
- **docs sync**: `docs/01 §14.4` F4→F5 확장 (트리거 분기 file/folder, target discriminated, mutation 분기 라우트, ShareDto 10필드 명시, SharesTable 3컬럼 정정, A13 backlog 등록).

### 핵심 결정 (f5-frontend-folder-share-ui 트랙, 확정)

1. **ShareDto wire 진실 = backend record** — F4 시점 frontend types가 `subjectType/subjectId/preset` 가정 + `folderId/revokedAt/revokedBy` 누락 = drift. A안 채택(frontend types를 wire에 정렬, ShareDialog 기존공유 행 표시 단순화 + SharesTable preset 컬럼 제거). 복원은 A13(backend join) backlog.
2. **`createShares` → `createFileShares`/`createFolderShares` 분리** — backend 라우트 분리(POST /api/files|folders/{id}/share)와 1:1 KISS. 단일 메서드 통합 시 endpoint 분기 로직이 클라이언트로 새는 안티패턴.
3. **useCreateShare 단일 hook 유지, Vars만 discriminated** — 호출자 관점 동일 액션, `qk.shares()` 무효화도 동일. hook 두 개로 쪼개면 무효화 중복.
4. **ShareDialog 위치 이동 (`files/` → `shares/`)** — 더 이상 file 전용이 아님. 소유 경계 정합.
5. **폴더 진입점은 Breadcrumb 우측 작은 액션** — 현재 폴더 = URL `folderId`이므로 §19 원칙 1과 정합. 비루트 + `can.SHARE` 게이트. FolderTree row 우클릭 컨텍스트 메뉴는 별도 트랙(범용 폴더 액션 시스템 신설 필요).
6. **`revokedAt`/`revokedBy` 미노출** — backend가 active 행에서 항상 null. UI 가치 0. 향후 admin 화면 재사용을 위해 wire 노출은 유지하되 UI는 표시 안 함 (YAGNI).
7. **with-me revoke 미노출 유지** — F4 보수 정책 그대로 (수신자 자진 반납 사양 미정).
8. **루트 폴더 공유 진입점 차단** — `breadcrumb.length > 1`로 게이트 (정책: 시스템 루트 = 공유 대상 아님).

### 파급 영향

- **frontend backlog**: 폴더 다중 선택 BulkActionBar 공유 액션(folder 다중 선택 자체 부재 → 함께 트랙 필요), FolderTree row 우클릭 컨텍스트 메뉴(범용 액션 시스템), subject picker UI(user/department/role 목록 endpoint 부재).
- **backend backlog**: **A13 (가칭) — `ShareDto` ↔ `permissions` join** (`subject_type`/`subject_id`/`preset`을 ShareDto에 join → ShareDialog 기존공유 행 풍부화 + SharesTable preset 컬럼 복원). `ShareControllerTest` wire JSON 필드 검증 보강(현 갭).
- **DB/스키마**: 변경 없음.

---

## 2026-05-01 — 🏁 M12 closure (Audit Log UI — A2.6 wired status 표기)

### 범위

M12 트랙(2026-04-25 mock 도입, `/admin/audit/logs` Filters/Table/Pagination/CSV)은 A2.6(2026-04-26)에서 backend `GET /api/admin/audit` 실연결로 교체되며 사실상 closed. 단 (1) `page.tsx:14-20` docblock은 "M12 mock + 백엔드 연결 없음" stale 문구를 그대로 유지 (2) `docs/04 §7`은 모든 항목이 미체크 상태로 mock-time 잔존 — 이 두 표기 정합만 누락. 본 closure는 (1) docblock 정정 + (2) docs §7 status 표기 갱신으로 트랙 종료.

### 회고

- **commits**: 1 on top of `17eac0e` SHARE_EXPIRED closure (worktree branch `feature/m12-audit-ui-closure`).
- **production 파일**: 수정 1 — `frontend/src/app/admin/audit/logs/page.tsx` docblock(M12 mock → M12 A2.6 wired + CSV export 동작/v1.x deferred 명시).
- **test 파일**: 미터치(JSDoc 변경만, 회귀 0).
- **docs sync**:
  - `docs/04 §7` Status quote 추가(M12 wired 2026-05-01 closure marker)
  - 7.1: `dateFrom`/`dateTo`/`actorId`/`eventType` 4 필터 활성 표기 + `대상 리소스`/`IP 주소`는 v1.x deferred(frontend filter + backend query param 미수용)
  - 7.2: CSV export 활성 + server-side full-result 스트리밍 / `audit.exported` runtime emission / JSON download 모두 v1.x deferred 명시
  - 7.3: before/after diff + 관련 이벤트 연결 v1.x deferred 명시
- **A2.6 wiring 사실**: `api.getAuditLogs`가 `fetch('/api/admin/audit?...', { credentials: 'include' })` 직접 호출(`frontend/src/lib/api.ts:493-553`). M12 mock 분기 + 60-row generator는 A2.6에서 완전 제거됨.

### 핵심 결정 (m12-audit-ui-closure 트랙, 확정)

1. **closure-only 트랙** — backend/frontend 코드 변경 0. 표기 정합만 처리. 새 기능 추가 거부(YAGNI).
2. **server export + `audit.exported` runtime emission은 v1.x deferred 유지** — current-page CSV는 운영 충분(필터 좁힘 + 페이지네이션). 전체 export는 별도 backend endpoint(streaming + audit emission) 도입 시점.
3. **`대상 리소스`/`IP 주소` 필터는 v1.x deferred** — `AuditLogFilters` 타입 + backend query param 양쪽 추가 필요. 현재 운영 필터(시간/행위자/이벤트)로 충분.
4. **`page.tsx` JSDoc 외 코드 미변경** — 실제 구현은 이미 GREEN(484+ frontend tests). closure는 의미 표기만.

### 파급 영향

- **frontend backlog**: 대상 리소스/IP 필터 + JSON download + 상세 뷰 diff(v1.x).
- **backend backlog**: server-side audit export endpoint + `audit.exported` emission(v1.x). docs/03 §4.1 enum에 `audit.exported`는 정의되어 있으므로 emission만 활성화하면 됨.
- **DB/스키마**: 변경 없음.

---

## 2026-05-01 — 🏁 SHARE_EXPIRED cron 트랙 종료 (ADR #34 backlog closure)

### 범위

SE.0 (worktree `feature/share-expired-cron` from `7c179d1` F4 closure + dev-docs bootstrap `dev/active/share-expired-cron/`) → SE.1 (`ShareCommandService.expireShare` 신규 + `lockAndCascadeRevoke` + `Snapshot` record helper 추출 + `revokeShare` helper 사용형 재작성 + `ShareExpiredEvent` record + `ShareAuditListener.onShareExpired`) → SE.2 (`ShareRepository.findExpiredActiveIds(Instant, Pageable)` JPQL + `ShareExpirationProperties` + `ShareExpirationJob @Scheduled(cron, zone) @ConditionalOnProperty` + `application.yml` `app.share.expiration.*` block + `SchedulingConfig` 다중 잡 진입점화 — `@ConditionalOnProperty(app.purge.enabled)` 제거) → SE.3 (`ShareCommandServiceTest` +4 / `ShareAuditListenerTest` +3 / `ShareExpirationJobTest` 신규) → SE.4 (00 §5 ADR #34 closure marker + 02 §7.9.1 만료 cron 정책 표 신규 + 03 §4.1 `share.expired` 활성화 마커 + 04 §13 `share.expire` 행 정정/footnote) → SE.5 (PR #28 squash-merge `bda5158` + dev-docs archive).

### 회고

- **commits**: 2 on top of `7c179d1` F4 close (worktree branch `feature/share-expired-cron`) → squash-merge `bda5158` on `master`. PR #28 single, CI green (backend junit 3m17s + frontend vitest 1m32s 모두 SUCCESS, fix commit 1회 — `HardPurgeJobDisabledIntegrationTest` SchedulingConfig 단언 정합).
- **production 파일**: 수정 5 / 신설 3.
  - 수정: `ShareCommandService.java`(expireShare + lockAndCascadeRevoke + Snapshot + revokeShare 재작성), `ShareAuditListener.java`(onShareExpired 추가), `ShareRepository.java`(findExpiredActiveIds), `SchedulingConfig.java`(다중 잡 진입점화), `application.yml`(app.share.expiration block).
  - 신설: `ShareExpiredEvent.java`(record, actorId 부재), `ShareExpirationProperties.java`(`@ConfigurationProperties("app.share.expiration")` record + 기본값 sanitization), `ShareExpirationJob.java`(`@Scheduled` + per-row 트랜잭션 + 실패 격리).
- **test 파일**: 수정 3 / 신설 1 — `ShareCommandServiceTest.java`(+4 expireShare 정상/race/folder/null guard), `ShareAuditListenerTest.java`(+3 onShareExpired 시스템 메타/folder variant/audit failure swallow), `HardPurgeJobDisabledIntegrationTest.java`(SchedulingConfig 빈 단언 → 존재 단언 정정), `ShareExpirationJobTest.java`(빈/N건/per-row 실패 격리/race ResourceNotFoundException/scan 실패 swallow/batchSize 전달). 회귀 0.
- **docs sync**: 00 §5 ADR #34 본문 closure 표기, 02 §7.9 인용문 수정 + §7.9.1 신규 정책 표(빈 등록/스케줄/한 회 한도/처리 단위/만료 동작/이벤트/audit row/다중 인스턴스/실패 격리/로그 9행), 03 §4.1 enum block에 `share.expired` 활성화 marker(actor_id=NULL/trigger 메모), 04 §13 `share.expire` 행을 default 5분/`share-expired-cron` 트랙으로 정정 + `[‡]` footnote(properties + 단위 처리 + audit + 다중 인스턴스 안전).
- **frontend 미터치**: `share.expired` audit row는 M12(Audit Log UI)가 자연 노출 — 별도 UI 변경 불요.

### 핵심 결정 (share-expired-cron 트랙, 확정)

1. **별도 `ShareExpiredEvent` record** — `ShareRevokedEvent`에 `bySystem` flag 추가 대안 거부. listener 분기 단순화 + payload 시그니처 의미 명료(시스템 트리거는 `actorId` 부재 = compile-time 보증). file/folder XOR invariant compact constructor 동형.
2. **`SchedulingConfig` 다중 잡 진입점화** — 기존 `@ConditionalOnProperty(app.purge.enabled)` gate 제거. `@EnableScheduling`은 무조건 활성, 잡-개별 `@ConditionalOnProperty`가 활성화 담당. 잡 빈 0개 시 single-thread scheduler는 idle(비용 무시).
3. **공통 helper 추출** — `lockAndCascadeRevoke(shareId, revokedBy)` + `Snapshot` private record. `revokeShare`/`expireShare` 두 메서드는 이 helper 호출 + 각자 다른 event publish만 담당 — DRY + 향후 변형(예: 트래시 만료 등) 추가 시 helper 재사용.
4. **`metadata.trigger='system.expiration'`** — audit consumer가 사용자 revoke(`actor_id` 존재)와 자동 만료(`actor_id=NULL` + `trigger='system.expiration'`)를 구분 가능하도록 보존. 추후 다른 시스템 트리거(예: `legal_hold.released_share_revoke`) 도입 시 trigger value만 분기.
5. **다중 인스턴스 안전성 = V6 row-level pessimistic lock** — 분산락(SchedulerLock 등) 도입 거부. 두 인스턴스가 동일 `shareId`로 동시 호출 시 한쪽만 lock 통과, 다른 쪽은 `revoked_at IS NOT NULL`로 lock query miss → `ResourceNotFoundException` swallow. 운영 단순화 + 인프라 추가 0.
6. **per-row 실패 격리** — 단일 row 예외는 ERROR 로그 + 다음 row 진행. 배치 전체 차단 없음. `ResourceNotFoundException`(사용자 동시 revoke race)도 같은 경로로 swallow.
7. **운영 기본 비활성** — `app.share.expiration.enabled=false`(`HardPurgeJob` 동형). staging/prod에서 명시적 활성화 후 투입. 실수로 dev 환경에서 share 자동 회수 방지.
8. **`permissions.expires_at`(직접 grant) 만료는 별도 트랙** — A10 scope에 직접 만료 케이스 부재. ShareCommand과 PermissionCommand는 책임 분리(SRP) — share 만료가 permission 만료를 강제하지 않음. 직접 grant 만료 트랙 도입 시 `PermissionCommandService.expirePermission` + 별도 cron 추가.

### 파급 영향

- **frontend**: 미터치. M12 Audit Log UI 트랙이 `share.expired` row 노출 책임 — 단순 enum mirror만 확인하면 됨(이미 03 §4.1 enum에 정의 존재).
- **backend backlog**: `permissions.expires_at` 만료 cron 트랙 분리(필요 시점에 별도 ADR). SSE `ShareExpiredEvent` emission은 ADR #14 인프라 milestone까지 그대로 deferred(audit-only).
- **DB/스키마**: 변경 없음. V6 schema 그대로 사용(`shares.expires_at`, `shares.revoked_at`, `shares.revoked_by`).
- **운영**: staging/prod에서 `app.share.expiration.enabled=true` + 필요 시 cron/zone/batch-size override. 단일 인스턴스 가정 해제(분산락 불요).

### 다음 세션 컨텍스트

- M12 Audit Log UI 트랙은 closure-only로 별도 dev-docs(`dev/active/m12-audit-ui-closure/`) 보존 중 — backend `GET /api/admin/audit` + frontend `api.getAuditLogs` 이미 GREEN, stale docblock 정정 + docs sync + 스모크 테스트만 남음.

---

## 2026-05-01 — 🏁 F4 마일스톤 종료 (Frontend Shares UI 실연결)

### 범위

F4.0 (worktree `feature/f4-frontend-shares-ui` from `e0957e5` M9 closure + dev-docs bootstrap `dev/active/f4-frontend-shares-ui/`) → F4.1 (`types/share.ts` + `qk.shares()/sharesByMe()/sharesWithMe()` + `invalidations.afterShareCreate/afterShareRevoke` 단일 prefix + 6 tests) → F4.2 (`api.{createShares,revokeShare,listSharesByMe,listSharesWithMe}` 4 메서드 + 19 wire-level 테스트) → F4.3 (`useCreateShare`/`useRevokeShare`/`useSharesByMe`/`useSharesWithMe` 4 hook + 9 테스트) → F4.4 (`ShareDialog` 전면 재작성 + `/shares` 페이지 + `SharesTable` + `SharesLink` + `(explorer)/layout.tsx` mount + docs/01 §6.1/§6.2/§14.4/§17 sync + 15 테스트) → F4.5 (PR #27 squash-merge `d6ab9aa` + dev-docs archive).

### 회고

- **commits**: 5 on top of `e0957e5` M9 close (worktree branch `feature/f4-frontend-shares-ui`) → squash-merge `d6ab9aa` on `master`. PR #27 single, CI green (backend junit 33s + frontend vitest 1m35s 모두 SUCCESS).
- **production 파일**: 수정 4 / 신설 8.
  - 수정: `lib/queryKeys.ts`(qk.shares + invalidations), `lib/api.ts`(4 메서드 + fetchSharePage helper), `app/(explorer)/layout.tsx`(SharesLink mount, TrashLink 위), `components/files/ShareDialog.tsx`(mock placeholder → 실연결 전면 재작성).
  - 신설: `types/share.ts`, `hooks/useCreateShare.ts`, `hooks/useRevokeShare.ts`, `hooks/useSharesByMe.ts`, `hooks/useSharesWithMe.ts`, `components/shares/SharesLink.tsx`, `components/shares/SharesTable.tsx`, `app/(explorer)/shares/page.tsx`+`ClientSharesPage.tsx`.
- **test 파일**: 수정 1 / 신설 7 — `ShareDialog.test.tsx`(전면 재작성 8건), `api.shares.test.ts`(19), 4 hook 테스트(2/2/3/2), `SharesLink.test.tsx`(2), `SharesTable.test.tsx`(5). 합계 +49 GREEN. 회귀 484/484.
- **build /shares ○ Static** — useSearchParams 호출 부재 + StatusBar Suspense 기존 wrap(M11)으로 SSG 회귀 0. typecheck/lint clean.
- **docs/01 sync**: §6.1 `qk.shares()/sharesByMe()/sharesWithMe()` 등재, §6.2 invalidation 매트릭스 +2행(공유 생성/해제), §14.4 ShareDialog backlink('everyone' MVP + preset 4값 + datetime-local + canRevoke 위임), §17 `/shares` 라우팅 등재.
- **backend 미터치**: A10 share endpoint 4종이 이미 GREEN — F4는 frontend 단독 트랙.

### 핵심 결정 (F4 트랙, 확정)

1. **subject = 'everyone' MVP only** — frontend user/department/role 목록 endpoint 부재(A-future 백로그). ShareDialog는 'everyone' 라벨 고정. subject picker는 후속 트랙에서 typeahead+chip로 도입.
2. **`/shares` 별도 페이지** — `/trash` 미러 패턴. `/files/[...parts]` 뷰에 share filter 통합 대안은 거부 — `/files`는 storage view, `/shares`는 access view로 분리. Sidebar `SharesLink`는 **TrashLink 위**(positive nav 위, destructive nav 아래).
3. **revoke = backend 위임** — `canRevoke`(sharedBy==me ‖ ADMIN)는 backend 진실의 출처. ShareDialog는 by-me share만 노출(자기 share=revoke 가능 자동 보장). `/shares`(with-me)는 revoke 버튼 미노출(보수 정책 — 수신자가 자기 권한 자진 반납은 사양 결정 후).
4. **preset 4값** — `read | upload | edit | admin` (ADR #34, V5 CHECK는 SHARE 미지원이라 wire에서 제외). 추후 SHARE 도입 시 backend 먼저 V_ migration + ADR 갱신.
5. **invalidations 단일 prefix** — `afterShareCreate/Revoke` 모두 `qk.shares()` 1회 → by-me/with-me 동시 갱신. KISS(co-located in queryKeys.ts, 별도 파일 미생성).
6. **expiresAt 변환** — HTML5 `datetime-local` 입력 → `new Date(v).toISOString()`(NaN check + 한국어 toast). timezone은 브라우저 로컬 → ISO 8601 UTC.
7. **에러 envelope code 분기** — `PERMISSION_CONFLICT`(이미 같은 대상…), `PERMISSION_DENIED`(권한 없음), `NOT_FOUND`(파일 없음), 그 외 폴백. backend `docs/02 §8` 에러 코드 계약 1:1 매핑.

### 파급 영향

- **frontend backlog**: subject picker UI(F-future, A-future 의존). with-me revoke(수신자 자진 반납) UI는 backend 사양 결정 후. folder share UI는 별도 트랙(A12 closure progress entry 참조).
- **backend**: 미터치. A10/A11/A12 endpoint 4종이 그대로 이번 frontend의 backbone.
- **DB/스키마**: 변경 없음.

---

## 2026-05-01 — 🏁 A12 마일스톤 종료 (Backend folder share endpoint)

### 범위

A12.0 (worktree `feature/a12-folder-shares` from `e0957e5` M9 closure + dev-docs bootstrap `dev/active/a12-folder-shares-endpoint/`) → A12.1 (`POST /api/folders/{folderId}/share` endpoint + `ShareCommandService.createFolderShares`(FolderRepository 주입) + `ShareCreatedEvent`/`ShareRevokedEvent` `folderId` XOR invariant + `ShareAuditListener` `nodeKey` 분기 + 테스트 갱신/추가) → A12.2 (`ShareQueryServiceTest` folder share 자연 노출 회귀 — by-me/with-me/mixed XOR per-row) → A12.3 (docs/00 ADR #34 활성화 + docs/02 §2.7/§7.9 folder POST + docs/03 §3 backlink) → A12.4 (PR #26 squash-merge `e076a1b` + dev-docs archive).

### 회고

- **commits**: 3 on top of `e0957e5` M9 close (worktree branch `feature/a12-folder-shares`) → squash-merge `e076a1b` on `master`. PR #26 single, CI green (backend junit + frontend vitest 모두 SUCCESS — 약 4분).
- **production 파일**: 수정 5 / 신설 0.
  - 수정: `share/ShareController.java`(folder POST 라우트), `share/ShareCommandService.java`(`createFolderShares` 추가, `revokeShare` snapshot folderId 캡처), `share/ShareCreatedEvent.java`/`ShareRevokedEvent.java`(folderId 필드 + XOR 컴팩트 컨스트럭터), `audit/ShareAuditListener.java`(nodeKey 분기로 file_id/folder_id JSON 키 출현).
- **test 파일**: 수정 3 / 신설 0 — `ShareAuditListenerTest`(기존 6개 이벤트 호출 folderId=null 인자 + folder variant 2건), `ShareCommandServiceTest`(@Mock FolderRepository + createFolderShares 7건 + revokeShare folder 변형 1건), `ShareControllerTest`(createFolderShare 4건), `ShareQueryServiceTest`(folder 자연 노출 3건). 합계 +20 GREEN.
- **schema 변경 0**: V6 `shares` 테이블 `file_id`/`folder_id` XOR CHECK가 이미 양립 — 컬럼/CHECK 추가 없음. ADR #34 backlog 항목(folder share endpoint 미도입) 명시적 closure.
- **backend 회귀 0**: 전체 `./gradlew test` GREEN. A10 file path 테스트 모두 통과.
- **frontend 미터치**: A12 backend stack only — frontend folder share UI는 docs/01 §6/§14 미명시 → 별도 backlog.

### 핵심 결정 (A12 트랙, 확정)

1. **`createFolderShares` 별도 메서드** — `createShares`(file)와 통합 abstraction(`createForNode(nodeType, nodeId, ...)`) 대안은 거부. KISS — A10 시그니처 보존 + 호출부 명시성 우선. 공통 검증(parsePreset / validateMessage / expiresAt 미래)은 private static helper로 재사용.
2. **`Share*Event`에 `folderId` 필드 추가 + XOR 컴팩트 컨스트럭터** — `nodeId` + `nodeType` 통합 대안은 거부. V6 `shares` 테이블이 이미 file_id/folder_id 양립이므로 event payload도 1:1 정합. 컴팩트 컨스트럭터 `(fileId == null) == (folderId == null)` invariant이 잘못된 발행을 차단.
3. **`ShareAuditListener` `nodeKey` 분기로 `file_id`/`folder_id` JSON 키 분리** — 통합 키(`node_id` + `node_type`) 대안은 거부. 기존 audit row 호환 유지 — A10 시점 audit_log row가 `file_id` 키를 사용하므로 query/조회 backward compat.
4. **`ShareQueryService` SQL 분기 없음** — by-me/with-me/DELETE는 file/folder share 모두 자연 노출. repository 쿼리는 `shared_by` 또는 `subject_id` 매칭만 — `file_id IS NOT NULL` 같은 분기 필터 부재. A12.2가 이 가정을 회귀 테스트로 박제.
5. **`@PreAuthorize("hasPermission(#folderId, 'folder', 'SHARE')")`** — file 변형(`'file'`)과 동형. PermissionEvaluator가 `nodeType` 분기 자동 처리(A4 evaluator).

### 파급 영향

- **frontend**: 미터치. folder share UI는 별도 트랙(`m_-folder-share-ui` 가칭, docs/01 §6/§14 추가 후 진행).
- **backend**: A10 `permission.granted` audit + `share.created` audit 이중 발행 패턴 그대로. `permission_id`로 grant 추적 가능.
- **DB**: V6 schema 변경 없음. 향후 `SHARE_EXPIRED` cron + SSE emission도 trigger 추가 없이 application 레벨에서 가능.

---

## 2026-05-01 — 🏁 M9 마일스톤 종료 (Frontend 휴지통 통합)

### 범위

M9.0 (`qk.trash()` + prep 키 + `invalidations.afterDelete/afterRestore/afterPurge` — filesListPrefix + trash + folderTree + search 4건 일괄) → M9.1 (`types/trash.ts` + `api.{getTrash,restoreFile,restoreFolder,purgeTrashItem,softDeleteFile,softDeleteFolder}` 실 backend fetch + `useDeleteBulk` Mock 제거 → 시그니처 `ids: string[]` → `items: {id,type}[]` 마이그) → M9.2 (`useTrashList`/`useRestoreItem`/`usePurgeTrashItem` hooks + 14 GREEN) → M9.3 (`/trash` 페이지 + `TrashTable`/`TrashRowActions`/`TrashLink` + `(explorer)/layout.tsx` Sidebar mount + `findFolderPath` 유틸 + 9 GREEN) → M9.4 (BulkActionBar `useDeleteBulk` onSuccess에 sonner `action: { label: '되돌리기', duration: 5000 }` + `undoDelete` 헬퍼 — type 분기 `restoreFile/restoreFolder` Promise.all + RESTORE_CONFLICT 분기 + 4 GREEN) → 중간 게이트(rebase onto master + PR #16 mock trash hooks orphan 정리 + M14 권한 정합 `admin → PURGE`) → M9.5 (PR #22 squash-merge `1927c56` + dev-docs archive).

### 회고

- **commits**: 10 on top of A10 close `24a78b2` (worktree branch `feature/m9-frontend-trash`, 중간에 origin/master 9 commits 흡수 — PR #16/F1.1/A9/A10) → 최종 rebase onto F2 close `69ab2e6` → squash-merge `1927c56` on `master`. PR #22 single, CI green (backend junit 36s + frontend vitest 1m25s 모두 SUCCESS).
- **production 파일**: 추가 8 / 수정 3.
  - 추가: `app/(explorer)/trash/{page,ClientTrashPage}.tsx`, `components/trash/{TrashTable,TrashRowActions,TrashLink}.tsx`, `hooks/{useTrashList,useRestoreItem,usePurgeTrashItem}.ts`, `types/trash.ts`, `lib/folderTreeUtils.ts`(findFolderPath).
  - 수정: `lib/queryKeys.ts`(qk.trash + 무효화 3건), `lib/api.ts`(trash + soft-delete 6 메서드 + `buildApiError` helper), `components/files/BulkActionBar.tsx`(Undo toast wiring), `app/(explorer)/layout.tsx`(TrashLink mount).
- **test 파일**: 신설 5 / 수정 2 — 42건 GREEN 신설(api.trash 15 / hooks 14 / TrashTable 7 / TrashLink 2 / BulkActionBar Undo 4). `useDeleteBulk.test.ts` softDelete*로 mock 교체.
- **frontend 회귀 0**: 전체 `pnpm test` 47 files / 439 tests GREEN. `pnpm typecheck` + `pnpm lint` clean. `pnpm build` GREEN(8/8 SSG) — pre-existing `/trash` Suspense fail은 PR #24(`ed89353`)에서 별도 fix 후 본 트랙 머지.

### 핵심 결정 (M9 트랙, 확정)

1. **`useDeleteBulk` 시그니처 마이그 (`ids[]` → `items[{id,type}]`)** — backend가 file/folder 분기 endpoint(`DELETE /api/{files,folders}/:id`)이라 호출부에서 type 동봉 필요. cache miss 시 `'file'` 폴백 — 404 response 시 onError에서 selection 복원.
2. **무효화 매트릭스 4건 일괄** — soft-delete/restore 모두 `filesListPrefix + trash + folderTree + search` 한 번에. folderTree 포함 이유: folder cascade restore/soft-delete 시 사이드바 stale 방지.
3. **`originalParentId` → folderTree path 해석 (N+1 회피)** — `useFolderTree()` 캐시 + `findFolderPath` 유틸로 해석. 부모도 trashed면 "원위치 폴더 삭제됨" 폴백. backend가 originalPath를 응답에 포함하도록 변경 시 endpoint patch 필요.
4. **PURGE는 M14 권한 hook의 ADMIN-only flag** — `usePermission().PURGE` (docs/03 §3.2). 초기엔 stub `usePermission().admin` 불리언이었으나 PR #16(M14) 머지 직후 `admin → PURGE` 일괄 정합. 의미적으로도 정확(영구 삭제 권한).
5. **Undo toast 5초 단일 sonner action** — `toast.success(msg, { duration:5000, action:{ label:'되돌리기', onClick } })`. 5초 시한 + 다중 action UX는 별도 디자인.
6. **RESTORE_CONFLICT 409 — toast.error 폴백** — MVP는 사용자가 폴더에서 충돌 항목 정리 후 재시도. ConflictDialog는 v1.x 보류.
7. **Bulk purge 미구현 (ADR #32)** — UI에서 "전체 비우기" 버튼 노출 안 함. backend `DELETE /api/trash` 트랙 별도화.
8. **`pnpm-lock.yaml` 미커밋** — F2와 동일 정책. 프로젝트는 `package-lock.json` 추적, pnpm 워크트리 lockfile은 로컬 artifact.
9. **PR #16 mock trash hooks orphan 정리** — PR #16이 mock 기반 `useRestoreBulk`/`usePurgeBulk`/`useTrashHooks.test.tsx`를 추가했으나 본 트랙이 real-backend `useRestoreItem`/`usePurgeTrashItem`/`useTrashList`로 대체했으므로 `488ca5a`에서 orphan 삭제. 트랙 충돌 시 mock 잔존 hooks는 squash 후 즉시 제거가 표준.

### 다음 트랙 후보

- **F4 — Frontend Shares UI** (A10 share endpoint 노출). docs/01 §6/§14 신설 후 진입.
- **F3 — `useStorageQuota` 실연결** — backend quota API 미신설 → 백엔드 트랙 선행 필요.
- **A12 — folder 공유** (A10 ADR #34 backlog).
- **A11 후속 — `SHARE_EXPIRED` 자동 전환 배치**.
- **M10 — SSE/WebSocket 실시간 동기화** (휴지통 다른 탭 변경 push 무효화 — docs/01 §15).

---

## 2026-05-01 — 🏁 F2 마일스톤 종료 (Frontend usePermission 실연결)

### 범위

F2.0 (dev-docs bootstrap — `dev/active/f2-frontend-permissions-realconnect/` plan/context/tasks 3 파일, A9→F1 직렬화 패턴 미러) → F2.1 (`api.getEffectivePermissions(nodeId?)` 본체 mock(80ms + admin preset 8 권한 하드코딩) → `fetch('/api/me/effective-permissions')` + inline `{permissions}.permissions` 매핑 + 비-OK 시 `status` 필드 가진 Error throw + `api.permissions.test.ts` fetch wire 9 케이스 RED→GREEN) → F2.2 (PR #25 squash-merge `76dda90` + dev-docs archive).

### 회고

- **commits**: 2 on top of A11 close `097e904` (worktree branch `feature/f2-frontend-permissions-realconnect`) → squash-merge `76dda90` on `master`. PR #25 single, CI green (backend junit 36s + frontend vitest 1m27s 모두 SUCCESS).
- **production 파일**: 1 수정 — `frontend/src/lib/api.ts` `getEffectivePermissions` 본체 교체 (14줄 → 18줄). 시그니처 `(nodeId?: string) => Promise<Permission[]>` 무수정 — 호출부(`usePermission.ts`, `BulkActionBar.tsx` consumer 등) drift 0.
- **test 파일**: 1 갱신 — `api.permissions.test.ts` 전면 재작성(fetch wire 계약 + 응답 매핑 + 401/404/5xx + qk 키 9건). 이전 mock 한정 2건 → fetch wire 9건.
- **frontend 회귀 0**: 전체 `pnpm test` 427/427 GREEN (55 suites). `pnpm typecheck` + `pnpm lint` clean.

### 핵심 결정 (F2 트랙, 확정)

1. **호출부 시그니처 무수정** (drift 0) — `usePermission` / `PermissionFlags` Record 변환 / consumer 전부 무수정. F1 패턴 그대로.
2. **매핑 inline (KISS)** — 별도 `permissionsMapper.ts` 파일 신설 회피. `api.ts` 내부 단일 라인(`(await res.json()).permissions`) 매핑.
3. **에러 envelope 글로벌 위임** — fetch 함수는 `!res.ok` 시 `status` 필드 가진 Error throw만. 401/403 화면 분기는 글로벌 `QueryCache.onError`에 위임 (F1 패턴 동일). 본 트랙에서 envelope handler 추가 변경 없음.
4. **AbortSignal 미전파** — 현 호출부(`queryFn: () => api.getEffectivePermissions(nodeId)`)가 signal 미전달이라 api 시그니처에 `options.signal` 추가 안 함. 미래 hook 갱신 시 backward-compat으로 추가 가능.
5. **PURGE 정책 동일** — backend `IbizDrivePermissionEvaluator.resolveAll`이 PURGE를 Preset 미포함 사유로 skip. 이전 mock도 PURGE 제외였으므로 frontend 동작 변화 없음.
6. **`pnpm-lock.yaml` 미커밋** — 프로젝트는 `package-lock.json` 추적(npm). 워크트리 셋업 시 `pnpm install`이 생성한 lockfile은 로컬 artifact로 untracked 유지.
7. **A9→F1 직렬화 패턴 재확인** — backend endpoint(A11) → frontend swap(F2) 직렬화. F1.1처럼 단일 mock body 교체 + 호출부 drift 0이 다음 mock→fetch swap의 표준.

### 다음 트랙 후보

- **F4 — Frontend Shares UI** (A10 share endpoint 노출). docs/01 §6/§14 신설 필요 → 설계 추가 후 진입.
- **A12 — folder 공유** (A10 ADR #34 backlog).
- **A11 후속 — `SHARE_EXPIRED` 자동 전환 배치** (cron + `expires_at` 도과 row → `revoked_at` set + audit emit).
- **F3 — useStorageQuota 실연결** (`api.getStorageQuota` mock → 실제 endpoint, 단 backend quota API 미신설). 백엔드 트랙 선행 필요.

---

## 2026-05-01 — 🏁 A11 마일스톤 종료 (Effective Permissions Endpoint Backend)

### 범위

A11.0 (dev-docs bootstrap — `dev/active/a11-effective-permissions-endpoint/` plan/context/tasks 3파일, F2 트랙 분리 결정 — 백엔드 endpoint 부재 발견 후 A11→F2 직렬화) → A11.1 (`IbizDrivePermissionEvaluator.resolveAll(user, resourceType, resourceId): Set<Permission>` 추가, role∪resource grant 합산 + ADMIN early return + role-already-granted skip + PURGE skip — TDD 9 케이스 RED→GREEN) → A11.2 (`PermissionController.myEffectivePermissions(@RequestParam UUID nodeId)` 신설, `GET /api/me/effective-permissions` `@PreAuthorize("isAuthenticated()")` + folder/file 양 테이블 lookup + 404/401 envelope — TDD 5 케이스 RED→GREEN) → A11.3 (docs/02 §7.10 응답 schema 본문 보강 + 에러 코드 `404` → `400, 401, 404` 정정) → A11.4 (PR #23 squash-merge `13b8c45` + dev-docs archive).

### 회고

- **commits**: 4 on top of A10 close `3b6906a` (worktree branch `feature/a11-effective-permissions-endpoint`) → squash-merge `13b8c45` on `master`. PR #23 single, CI green (backend junit 3m16s + frontend vitest 1m40s 모두 SUCCESS).
- **production 파일**: 2 수정 — `permission/IbizDrivePermissionEvaluator.java` `resolveAll` 메서드 추가(EnumSet 도입), `permission/PermissionController.java` `myEffectivePermissions` endpoint + 4-arg 생성자(evaluator 주입). 기존 grant/revoke endpoint 무수정.
- **test 파일**: 2 갱신 — `IbizDrivePermissionEvaluatorTest` 9 케이스(`resolveAll` null/ADMIN/AUDITOR/MEMBER × role-only/resource-level), `PermissionControllerTest` 5 케이스(noNodeId MEMBER/ADMIN, folder/file lookup, both-missing 404).
- **A1~A10 회귀 0**: 전체 백엔드 GREEN. F2 frontend 트랙은 별도 PR로 분리(다음 트랙).
- **F2 분리 결정**: 원래 사용자 요청은 F2 (frontend mock→fetch swap)였으나, 사전조사에서 `GET /api/me/effective-permissions` 백엔드 endpoint 부재 발견 → A11 백엔드 트랙으로 분리 후 F2 frontend 트랙 후속화. A9→F1 패턴과 동일한 직렬화.

### 핵심 결정 (A11 트랙, 확정)

1. **`resolveAll` 위치는 evaluator** — `PermissionService`에 두면 `PermissionResolver` 의존 추가로 순환 위험. evaluator는 이미 양쪽 의존이라 자연스러움. service는 ROLE 단계 진실의 출처로 유지.
2. **9× CTE 호출 최적화** — role이 이미 grant한 권한 + `PURGE`(Preset 미포함, docs/03 line 331~334) 는 resolver 미호출. ADMIN=0 / AUDITOR=7 / MEMBER=8 호출.
3. **node 존재 검증 — folder 우선 short-circuit** — `folderRepository.findByIdAndDeletedAtIsNull` 먼저, 부재 시 `fileRepository`. 양 테이블 모두 부재 → 404. V5 별도 시퀀스로 UUID 충돌은 운용상 불가능.
4. **401/400은 Spring 기본에 위임** — `@PreAuthorize("isAuthenticated()")` 미인증 401, `@RequestParam UUID` 변환 실패 400. KISS — `GlobalExceptionHandler` 추가 분기 회피.
5. **응답 정렬 = Permission enum natural order** — `set.stream().sorted().toList()` 단일 라인. 프론트는 set 비교만 해도 결정적.
6. **`PermissionDenyContext` 미기록** — read-only 정보 조회는 deny envelope 미생성. evaluator `hasPermission` 경로와 분리.
7. **frontend 시그니처 contract 확정** — `api.getEffectivePermissions(nodeId?: string): Promise<Permission[]>`. F2에서 mock body를 fetch+inline mapping으로 교체만 하면 호출부(`usePermission.ts`) drift 0.

### 다음 트랙 후보

- **F2 — useEffectivePermissions 실연결** (즉시 진입 가능). `frontend/src/lib/api.ts` `getEffectivePermissions` mock body → `fetch('/api/me/effective-permissions?nodeId=...')` 교체. `usePermission.ts` 무수정. F1 패턴(PR #20) 미러.
- **F4 — Frontend Shares UI** (A10 share endpoint 노출). 별도 트랙.
- **A12 — folder 공유** (A10 ADR #34 backlog).

---

## 2026-05-01 — 🏁 A10 마일스톤 종료 (Shares Endpoint Backend)

### 범위

A10.0 (ADR #34 신설 + docs/02 §2.7 `shares` 테이블 SQL에 `expires_at` 추가 + §7.9 spec 4 endpoints 정합 + docs/03 §3 backlink + Preset 4값 V5 check 일관) → A10.1 (V6 마이그레이션 `shares` 테이블 + `Share` entity + `ShareRepository` + Testcontainers V6MigrationIT 1건) → A10.2 (`ShareDto`/`ShareCreateRequest` + `ShareCommandService.createShares` 1 request → N subjects) → A10.3 (`ShareCommandService.revokeShare` soft-revoke + `canRevoke` SpEL + `ShareAuditListener` `share.created`/`share.revoked` emit — **audit 첫 활성화**) → A10.4 (`ShareQueryService` by-me/with-me + `ShareCursor` `{createdAt}|{id}` base64) → A10.5 (`ShareController` 4 endpoints + `@PreAuthorize` + 400/403/404 envelope + Mockito 통합 테스트) → A10.6 (PR #21 squash-merge `24a78b2` + dev-docs archive).

### 회고

- **commits**: 7 on top of F1 close `9875fe9` (worktree branch `feature/a10-shares`) → squash-merge `24a78b2` on `master`. PR #21 single, CI green (backend junit 3m7s + frontend vitest 1m33s 모두 SUCCESS).
- **production 파일**: 12 신설 — `share/Share.java`, `share/ShareCommandService.java`, `share/ShareController.java`, `share/ShareCreateRequest.java`, `share/ShareCreatedEvent.java`, `share/ShareCursor.java`, `share/ShareDto.java`, `share/SharePage.java`, `share/ShareQueryService.java`, `share/ShareRepository.java`, `share/ShareRevokedEvent.java`, `audit/ShareAuditListener.java` + V6 마이그레이션(`db/migration/V6__shares.sql`).
- **test 파일**: 6 신설 — `ShareCommandServiceTest`, `ShareControllerTest`, `ShareCursorTest`, `ShareQueryServiceTest`, `ShareAuditListenerTest`, `V6MigrationIT` (Testcontainers).
- **ADR 신설**: #34 — Shares endpoint 채택. file 한정(folder 공유 backlog), subject 4종(user/department/role/everyone, MVP 후처리 user 1차) + ADR #28 preset 4값 정합(V5 `permissions_preset_check`).
- **Audit 첫 활성화**: `AuditEventType.SHARE_CREATED`/`SHARE_REVOKED` enum 정의(이전까지 사용처 0) → `ShareAuditListener` `@TransactionalEventListener` REQUIRES_NEW로 첫 emit. `SHARE_EXPIRED` 배치 트랙은 deferred.
- **A1~A9 회귀 0**: 전체 백엔드 535 tests GREEN.

### 핵심 결정 (A10 트랙, 확정)

1. **shares 테이블 = share 메타, permission row = 권한 자체** (ADR #34) — share row가 `permission_id` 참조. revoke 시 share `revoked_at` set + permission row delete + `share.revoked` audit 단일 발행 (이중 audit 회피, KISS).
2. **subject 4종 채택, 후처리는 MVP user 한정** — wire format `user`/`department`/`role`/`everyone` 모두 수신. with-me 매칭은 `subject_type='user' AND subject_id=actorId` 1차. department/role/everyone 후처리는 별도 ADR로 박제.
3. **`expires_at` 컬럼 추가** (V6) — frontend permission expiresAt UX 패리티 + 향후 `SHARE_EXPIRED` 배치 자동 전환 hook. 컨트롤러 진입 시 미래 시각 검증.
4. **`canRevoke` SpEL** — `@PreAuthorize("@shareCommandService.canRevoke(#shareId, principal)")` (owner OR sharer OR ADMIN). audit 회피용 단일 진입점.
5. **Cursor `{createdAt}|{id}` base64 url-safe** — A8 TrashCursor / A9 SearchCursor 패턴 변형. by-me/with-me 둘 다 `created_at DESC, id DESC` 정렬.
6. **Preset 4값 정합** (`VIEW`/`COMMENT`/`EDIT`/`MANAGE`) — V5 `permissions_preset_check` 단일 진실. ADR #28 본문 5-preset drift 정정.
7. **bulk revoke / SSE / folder 공유 / 외부 토큰 / SHARE_EXPIRED 배치 / Frontend UI는 별도 트랙** — backend stack only, ADR #34 backlog 박제.

### 다음 트랙 후보

- **F2 — useEffectivePermissions 실연결** (M8 권한 UI mock → A4 `/api/permissions` real). A10 share row를 permission UI에 노출하려면 `/api/permissions/effective` 응답에 share-derived 권한 포함 여부 결정 선행.
- **F4 — Frontend Shares UI** (Right Panel `Shares` tab + 공유 다이얼로그). docs/01 §6/§14 신설 필요 → 설계 추가 후 트랙 진입.
- **A11 — `SHARE_EXPIRED` 자동 전환 배치** (A7 `purge.expired` 미러). cron + `expires_at` 도과 row → `revoked_at` set + audit emit.
- **A12 — folder 공유** (`POST /api/folders/:id/share`). 현재 shares 테이블은 `file_id`/`folder_id` 양립 — endpoint만 추가.

---

## 2026-05-01 — 🏁 F1 마일스톤 종료 (Frontend Search 실연결)

### 범위

F1.0 (dev-docs bootstrap — `dev/active/frontend-search-realconnect/` plan/context/tasks 3 파일) → F1.1 (`api.searchFiles` 본체 MOCK_FILES filter mock → `fetch('/api/search?q=...')` 직접 호출 교체 + `SearchPage{items,nextCursor,totalEstimate}` → `{items: FileItem[]}` inline 매핑 + `api.search.test.ts` fetch mock 패턴 14 케이스 GREEN + `useSearch.test.tsx` integration fetch stub 갱신 + `api.trash.test.ts` 휴지통 제외 시나리오 제거) → F1.2 (PR #20 squash-merge `f9200dc` + dev-docs archive).

### 회고

- **commits**: 1 on top of PR #16 close `f77f886` (worktree branch `feature/f1-frontend-search-realconnect`) → squash-merge `f9200dc` on `master`. PR #20 single, CI green (backend junit 28s + frontend vitest 1m32s 모두 SUCCESS).
- **production 파일**: 1 수정 — `frontend/src/lib/api.ts` `searchFiles` 본체 교체 + `normalizeForSearch` import 정리. 시그니처 무수정(`{q,filters},{signal}→{items: FileItem[]}`) — 호출부 drift 0.
- **test 파일**: 3 갱신 — `api.search.test.ts` 전면 재작성(fetch wire 계약 + 매핑 file/folder/mixed/null edge + 401/403/5xx + abort 14건), `useSearch.test.tsx` integration `vi.stubGlobal('fetch', ...)`로 전환, `api.trash.test.ts` 휴지통 제외 시나리오 제거(이제 backend `deleted_at IS NULL` 책임).
- **frontend 회귀 0**: 전체 `pnpm test` 422/422 GREEN (55 suites). `pnpm typecheck` + `pnpm lint` clean.

### 핵심 결정 (F1 트랙, 확정)

1. **호출부 시그니처 무수정** (drift 0) — `useSearch` / `SearchBar` / `SearchResults` / `FileItem` 타입 전부 무수정. `api.searchFiles` 본체만 교체. 후속 endpoint mock→real 전환 시 동일 패턴 적용.
2. **filters 인자는 보존하되 무시** — backend가 `type` 외 필터(mime/owner/date) 미지원 (ADR #33). 향후 추가 시 `searchFiles` 내부 `URLSearchParams`만 확장.
3. **매핑 inline (KISS)** — 별도 `searchMapper.ts` 파일 신설 회피. `api.ts` 내부 단일 화살표 함수로 처리(50줄 이하).
4. **`updatedBy: ''` 빈 문자열** — backend `SearchResultDto` actor 필드 미반환. SearchResults UI는 secondary info(아이콘 옆 작은 텍스트)이므로 빈 표시 허용. 후속 backend 확장(`updatedByName`) 시 매핑만 보강.
5. **에러 envelope 일관** — audit 패턴 그대로(`Error & {status}` throw). `QueryCache` 글로벌 onError가 401/403 분기 — endpoint별 분기 코드 추가 0.
6. **AbortSignal 전파 = fetch native** — 기존 mock의 setTimeout+manual abort hookup 폐기. `fetch(..., { signal })` 사용으로 코드량 감소 + DOMException AbortError 자연 전파.
7. **휴지통 제외 = backend 책임** — `SearchQueryService` repo 쿼리에 `deleted_at IS NULL` WHERE 절 박제. frontend api.ts 경계에서 더 이상 검증 안 함.

### 다음 트랙 후보

- **F2 — useEffectivePermissions 실연결** (`api.getEffectivePermissions` mock → backend endpoint). 백엔드 미존재 — A10 또는 별도 트랙으로 endpoint 신설 선행 필요.
- **F3 — useStorageQuota 실연결** (M15 StorageBar). backend quota API 미존재 — endpoint 신설 선행.
- **A10 — Shares §7.9** (backend share/permission API 신설). 권한 매트릭스 docs/03 §3 + share endpoint family.
- **B1 — full-text/trigram 검색** (ADR #33 후속 트랙, postponed). `pg_trgm` extension + GIN index 마이그레이션 + `tsvector` 또는 trigram 전환. 항목 수 임계 도달 시 활성화.

---

## 2026-04-30 — 🏁 A9 마일스톤 종료 (Search Endpoint Backend)

### 범위

A9.0 (docs/00 ADR #33 신설 + docs/02 §7.8 spec 보강 (q/type/cursor/limit + 6단계 처리 + SearchResultDto schema) + docs/01 §10 backlink) → A9.1 (`SearchResultDto`/`SearchPage`/`SearchCursor` + base64 url-safe codec `{updatedAtEpochMs}|{type}|{id}` + 11건 GREEN) → A9.2 (`FileRepository`/`FolderRepository.searchByNormalizedName` LIKE+ESCAPE + `SearchQueryService` (q normalize→minLen 2→escapeLike→merge sort→READ 후처리→cursor) + 12건 GREEN) → A9.3 (`SearchController` GET /api/search + `@PreAuthorize("isAuthenticated()")` + IAE→400 + 5건 GREEN) → A9.4 (PR #19 squash-merge `73a8f01` + dev-docs archive).

### 회고

- **commits**: 5 on top of A8 close `a952f78` (worktree branch `a9-search-endpoint`) → squash-merge `73a8f01` on `master`. PR #19 single, CI green (backend junit + frontend vitest 모두 SUCCESS).
- **production 파일**: 5 신설 + 2 수정 — `search/SearchController.java` NEW, `search/SearchQueryService.java` NEW, `search/SearchResultDto.java` NEW (record), `search/SearchPage.java` NEW (record), `search/SearchCursor.java` NEW (codec), `FileRepository`/`FolderRepository`에 `searchByNormalizedName` + `countByNormalizedName` 추가.
- **test 파일**: 3 신설 — `SearchCursorTest` (11건, encode/decode round-trip + edge timestamps + invalid base64/format/type/uuid), `SearchQueryServiceTest` (12건, minLen/type/empty/file/folder/merge/READ filter/cursor round-trip/cursor page no-count/invalid cursor/escape), `SearchControllerTest` (5건, 정상/type blank/type=file/cursor+limit echo/IAE propagate).
- **ADR 신설**: #33 — 검색 알고리즘 = LIKE on normalized_name (MVP), tsvector full-text + pg_trgm fuzzy + owner/modifiedFrom/To 필터 + SEARCH_QUERIED audit emission 보류.
- **A1~A8 회귀 0**: 전체 `./gradlew test` 476/476 GREEN (51 suites).

### 핵심 결정 (A9 트랙, 확정)

1. **LIKE on normalized_name** (ADR #33) — full-text(tsvector + GIN) / trigram(`pg_trgm`)는 extension/index 마이그레이션 + 별도 ADR 필요. MVP 항목 수 가정 < 10k. 후속 트랙으로 박제.
2. **type 필터 = file/folder/all** — frontend 현재 미사용이지만 spec 보존. owner/modifiedFrom/To는 controller param + service overload만으로 확장 가능하게 hook (KISS, YAGNI).
3. **Cursor `{updatedAtEpochMs}|{type}|{id}` base64 url-safe** — A8 `TrashCursor` 패턴 변형. `updated_at DESC, id DESC` 정렬 키 + type tiebreaker(merge sort). round-trip 테스트로 계약 고정.
4. **READ 후처리 필터** — A8 TrashQueryService와 동일 패턴. ROLE 단계 short-circuit (`PermissionService.effectivePermissions(role).contains(READ)`) → `PermissionResolver.isGranted(actorId, type, id, READ)` fallback.
5. **DTO discriminated union** — `SearchResultDto` record + `type: "file"|"folder"` discriminator + `@JsonInclude(NON_NULL)` per-type field. 정적 팩토리 `fromFile(FileItem)` / `fromFolder(Folder)`.
6. **min length 2 = normalize 후 기준** — `NormalizeUtil.normalizeForSearch(q).length() < 2` → `IllegalArgumentException("INVALID_SEARCH_QUERY")` → 400 envelope (`GlobalExceptionHandler` IAE→`BAD_REQUEST` + message).
7. **LIKE pattern escape** — `\`, `%`, `_` backslash escape + repo native query에 `ESCAPE '\'` 박제. `SearchQueryService.escapeLike` package-private (테스트용 노출).
8. **totalEstimate 첫 페이지 only** — cursor 페이지에서는 -1 (재집계 비용 회피). count 쿼리는 cursor==null일 때만 발사.
9. **Pure Mockito (no Testcontainers)** — A8 KISS 패턴 일관. service boundary + repository contract + 권한 후처리 verify는 Mockito로 충분.

### accepted-deviation (후속 backlog)

- **Frontend `useSearch` 백엔드 연결** — 현재 `api.searchFiles` mock. PR #16 머지 후 본체 fetch로 교체.
- **Full-text / trigram** — tsvector + GIN index 또는 `pg_trgm` 마이그레이션 (ADR #33 deferred).
- **Filter 확장** — owner / modifiedFrom / modifiedTo. controller param + service overload hook 이미 마련.
- **SEARCH_QUERIED audit** — 검색 패턴 분석/개인정보 우려 별도 보안 트랙 (ADR #33 deferred).

### DoD 7/7

1. ✅ ADR #33 신설 + docs/02 §7.8 spec 보강 (q/type/cursor/limit + 6단계 처리 + SearchResultDto schema) + docs/01 §10 backlink.
2. ✅ `GET /api/search` — `@PreAuthorize("isAuthenticated()")` + q minLen 2 + type ∈ {file,folder,all} + cursor base64 + limit default 50/cap 100.
3. ✅ `SearchQueryService` — q normalize → escapeLike → repo LIKE(limit+1) → merge sort → READ 후처리 → nextCursor + totalEstimate.
4. ✅ Repository — `FileRepository`/`FolderRepository.searchByNormalizedName` (LIKE :pattern ESCAPE '\\' + cursor tuple predicate + WHERE deleted_at IS NULL) + `countByNormalizedName`.
5. ✅ Cursor codec — base64 url-safe `{updatedAtEpochMs}|{type}|{id}` round-trip + invalid → IAE → 400.
6. ✅ 테스트 28건 GREEN (cursor 11 + service 12 + controller 5) + A1~A8 회귀 0 (총 476 tests).
7. ✅ PR #19 CI green (backend junit + frontend vitest) + master squash-merge `73a8f01` + dev-docs `dev/active/a9-search-endpoint/` → `dev/completed/a9-search-endpoint/` archive.

### 다음 단계

- **Frontend `useSearch` 실연결** — backend `/api/search` fetch + cursor pagination + minLength 2 일관 검증. PR #16 (M11) 머지 후 진입 권장.
- **A10 (TBD)** — 후속 백엔드 마일스톤 미정. 후보: shares 본체(7.9), permissions 본체(7.10), 또는 Frontend M14 SSE/실시간.

---

## 2026-04-30 — 🏁 A8 마일스톤 종료 (Trash Listing + Manual Purge)

### 범위

A8.0 (docs/00 ADR #32 신설 + docs/02 §7.11 패치 + docs/02 §7.13.1 audit 정합 + docs/01 §13 backlink) → A8.1 (`GET /api/trash` cursor + type filter + 권한 후처리 + `TrashItemDto`/`TrashItemType`/`TrashCursor` + 12건 GREEN) → A8.2 (`DELETE /api/trash/:type/:id` ADMIN-only + `TrashPurgeService` (file: lock→version cascade→hard delete→`FILE_PURGED` audit / folder: BFS+leaf-first topo+single root `FOLDER_PURGED` audit) + 8건 GREEN) → A8.3 (PR #18 squash-merge `0c806c1` + dev-docs archive).

### 회고

- **commits**: 4 on top of A7 close `d539640` (worktree branch `feature/a8-trash-manage`) → squash-merge `0c806c1` on `master`. PR #18 single, CI green (backend junit + frontend vitest 모두 SUCCESS).
- **production 파일**: 9 신설 + 5 수정 — `trash/TrashController.java` NEW, `trash/TrashQueryService.java` NEW, `trash/TrashPurgeService.java` NEW, `trash/TrashItemDto.java` NEW (record), `trash/TrashItemType.java` NEW (enum), `trash/TrashPage.java` NEW (record), `trash/TrashCursor.java` NEW, `audit/AuditEventType.java` `FOLDER_PURGED("folder.purged")` 추가 (38→39), `FileRepository`/`FolderRepository`/`FileVersionRepository` 보조 query 확장, `frontend/src/types/audit.ts` mirror, `docs/03 §4.1` mirror.
- **test 파일**: 5 신설 — `TrashControllerTest` (9건, list 6 + purge 3), `TrashQueryServiceTest` (6건, cursor/type/권한), `TrashPurgeServiceTest` (5건, file 3 + folder 2 cascade leaf-first 검증), `FileTestFixtures` / `FolderTestFixtures` (package-protected entity constructor 우회).
- **ADR 신설**: #32 — manual purge URL `:type/:id`, per-row audit (`FILE_PURGED`/`FOLDER_PURGED`), bulk endpoint deferred, SSE emission infra milestone deferred.
- **A1~A7 회귀 0**: 전체 `./gradlew test` 448/448 GREEN.

### 핵심 결정 (A8 트랙, 확정)

1. **URL 패턴 `:type/:id`** — REST 자원 명시 (`/api/trash/file/:id`, `/api/trash/folder/:id`). 단일 endpoint `:id`보다 명시적이며, type 분기 dispatch가 service layer에서 단순.
2. **per-row audit** (ADR #32) — A7 summary-only(`SYSTEM_PURGE_EXECUTED`)와 대비. manual purge는 actor가 명시적 ADMIN 의도이므로 `FILE_PURGED`/`FOLDER_PURGED` 1건씩 발행. before_state에 name/folderId/storageKeys 보존.
3. **folder cascade audit는 root-only** — A6/A7 패턴 일관. 후손 folder/file은 개별 audit 미발행. root audit before_state에 `descendantFolders`/`descendantFiles` 카운트 + `storageKeys` 리스트(cap=1000+`storageKeysTruncated` flag).
4. **Leaf-first topo-sort 인라인** — A7 `HardPurgeService` Kahn's algorithm을 service 내부에 인라인 (`leafFirstOrder` + `findIdAndParentIdByIds`). 별도 helper class 미신설 (KISS).
5. **bulk delete deferred** — `DELETE /api/trash` (전체 비우기) 미구현. 트랜잭션 길이 + 부분 실패 정책 + audit 폭주가 단일 PR 범위 초과. ADR #32에 backlog 박제.
6. **Cursor opaque base64** — `{deletedAt}|{id}` url-safe base64. `TrashCursor.encode/decode` round-trip 테스트로 계약 고정. invalid → 400 GlobalExceptionHandler.
7. **Pure Mockito (no Testcontainers)** — A6/A7가 DB-level FK 위반 시나리오 이미 커버. service boundary + repository contract + audit emit verify는 Mockito로 충분. KISS — 이중 가드 비용 회피.
8. **SSE emission deferred** (ADR #32) — `// TODO: SSE emit` 주석만 박제. 실시간 동기화는 별도 인프라 milestone에서 일괄 회수 (A6/A7/A8 누적 부채).

### accepted-deviation (후속 backlog)

- **Frontend M9 (휴지통 UI 통합)** — 본 closure 직후 진입. backend endpoint(`GET /api/trash`, `DELETE /api/trash/:type/:id`) + restore endpoint(A6) + Undo(5초) 통합.
- **Bulk purge** — `DELETE /api/trash` 전체 비우기 (ADR #32 deferred).
- **SSE emission** — `file.purged` / `folder.purged` 실시간 push (별도 인프라 milestone).
- **Storage key 실삭제** — orphan storage_keys는 audit `before_state`에만 기록, 실제 S3 객체 cleanup은 `orphan.detect` 잡 (ADR #31).

### DoD 7/7

1. ✅ ADR #32 신설 + docs/02 §7.11 patch (per-resource restore + DELETE `:type/:id` + bulk strikethrough) + docs/02 §7.13.1 audit 정합 + docs/01 §13 backlink.
2. ✅ `GET /api/trash` — `@PreAuthorize("isAuthenticated()")` + cursor base64 + type filter + 권한 후처리 + 응답 스키마 docs/02 정합.
3. ✅ `DELETE /api/trash/:type/:id` — `@PreAuthorize("hasRole('ADMIN')")` + file/folder dispatch + 204.
4. ✅ `TrashPurgeService` — file (lock→version→hard delete→audit) + folder (BFS→version→file→leaf-first→folder→single root audit) + 404 매핑.
5. ✅ Audit `FILE_PURGED`/`FOLDER_PURGED` 발행 + before_state JSON 보존(name, folderId, storageKeys, descendantFolders/Files).
6. ✅ 테스트 20건 GREEN (controller 9 + query 6 + purge 5) + A1~A7 회귀 0 (총 448 tests).
7. ✅ PR #18 CI green (backend junit + frontend vitest) + master squash-merge `0c806c1` + dev-docs `dev/active/a8-trash-manage/` → `dev/completed/a8-trash-manage/` archive.

### 다음 단계

- **Frontend M9 bootstrap** — `feature/m9-frontend-trash` worktree + plan/context/tasks 3파일 + 사용자 plan 리뷰 게이트. 본 세션에서 이어서 진입.
- **SSE 실시간 동기화 (별도 인프라 milestone)** — A6/A7/A8 누적 SSE TODO 일괄 회수.
- **Search endpoint backend** — M11 frontend search 미연결.
- **Audit query export** — `/admin/audit-logs` 필터 + CSV.

---

## 2026-04-30 — 🏁 A7 마일스톤 종료 (Hard Purge Job — purge.expired)

### 범위

A7.0 (docs/00 ADR #31 + docs/02 line 37 + §7.11.1 + docs/04 §13 patch) → A7.1 (Repository 8메서드 + Testcontainers 7건 GREEN) → A7.2 (`HardPurgeService` 트랜잭션 본체 + audit `SYSTEM_PURGE_EXECUTED` summary emit + Kahn's algorithm leaf-first 위상정렬 + 7건 테스트) → A7.3 (`HardPurgeProperties` + `SchedulingConfig` + `HardPurgeJob` + 통합 4건 + `application.yml` `app.purge` 섹션) → A7.4 (PR #17 squash-merge `5c22e23` + dev-docs archive).

### 회고

- **commits**: 4 on top of A6 close `fdeb610` (worktree branch `feature/a7-hard-purge`) → squash-merge `5c22e23` on `master`. PR #17 single, CI green (backend junit 3m6s + frontend vitest 1m6s 모두 SUCCESS).
- **production 파일**: 6 신설 + 4 수정 — `purge/HardPurgeService.java` NEW, `purge/PurgeResult.java` NEW (record), `purge/HardPurgeJob.java` NEW, `purge/HardPurgeProperties.java` NEW, `config/SchedulingConfig.java` NEW, `application.yml` `app.purge` 섹션 추가, `FileRepository`/`FolderRepository`/`FileVersionRepository` 각각 hard purge 보조 메서드 확장.
- **test 파일**: 4 신설 — `HardPurgeRepositoryTest` (7건, V5 schema + cascade 정합), `HardPurgeServiceTest` (7건, 트랜잭션 본체 + audit JSON), `HardPurgeJobIntegrationTest` (3건, enabled 시나리오), `HardPurgeJobDisabledIntegrationTest` (1건, disabled 빈 미등록).
- **ADR 신설**: #31 — A7 = DB-only, S3 객체 삭제는 storage 모듈 milestone 으로 deferred.
- **A6 회귀 0**: 전체 `./gradlew test` BUILD SUCCESSFUL.

### 핵심 결정 (A7 트랙, 확정)

1. **DB-only (ADR #31)** — backend storage 모듈 0개 시점. `purge_after` 경과 row의 DB hard delete만 A7 범위. orphan storage_keys는 audit `after_state.orphanStorageKeys`(cap=1000)에 기록만 — storage 모듈 도입 시 `orphan.detect` 잡(docs/04 §13)이 storage_key cross-check로 정리.
2. **Audit summary-only** — A6 root-only 패턴 일관. 1 run = 1 `SYSTEM_PURGE_EXECUTED` audit. per-row `FILE_PURGED`/`FOLDER_PURGED` enum은 정의되어 있으나 발행 없이 A8 manual purge `/api/trash/:id` 트랙으로 reserve.
3. **file_versions cascade hard delete** — docs/02 line 37 정책 갱신: 일반은 영구 보존이지만 file row hard purge 시점에는 cascade 삭제 (FK `ON DELETE RESTRICT` 만족). storage_key는 audit orphan 기록 후 삭제.
4. **Kahn's algorithm in-memory** — schema에 depth 컬럼 부재. parent_id 그래프로 batch 내 leaf-first 위상정렬. cycle 발생 시 ordered list 길이 < 입력으로 자연스러운 skip.
5. **MAX_PURGE_PER_RUN 합산 한도** — files+folders 합산 (기본 10000). 초과 시 `truncated=true`로 다음 run 이월 (잡 자체는 정상 완료, 가장 오래된 row 우선).
6. **단일 트랜잭션** — `@Transactional` 본체. partial purge 미허용. 예외 시 전체 rollback → audit 미발행 → 다음 cron 재시도. audit emit만 REQUIRES_NEW.
7. **운영 기본 비활성** — `app.purge.enabled=false`. staging/prod에서 명시적으로 `true` 설정 후 투입. dev/test는 `@TestPropertySource` override 패턴.
8. **No ShedLock** — 단일 인스턴스 운영 가정. 다중 인스턴스 도입 시 별도 ADR.

### accepted-deviation (후속 backlog)

- **S3 객체 삭제** — storage 모듈 도입 + `orphan.detect` 잡 (ADR #31).
- **A8 manual purge** — `/api/trash/:id` 단건 hard delete endpoint + per-row `FILE_PURGED`/`FOLDER_PURGED` audit emit.
- **Legal Hold 통합** — A7 cron 트랜잭션이 legal_holds 테이블 조회 후 hold된 row 제외 (docs/03 §6.3 후속).
- **Monitoring metric** — `purge.expired` 잡 실행 횟수 / 처리 row / 실패 카운터 (별도 backlog).

### DoD 10/10

1. ✅ Repository 확장 — `findExpiredFileIds/FolderIds`, `hardDeleteByIds`, `findStorageKeysByFileIds`, `deleteByFileIds`, `findIdAndParentIdByIds` (8 메서드).
2. ✅ `HardPurgeService.runDailyPurge` 단일 트랜잭션 본체 + 위상정렬 + audit summary emit (`PurgeResult` record).
3. ✅ Kahn's algorithm leaf-first 위상정렬 (parent_id 그래프, cycle 안전).
4. ✅ `HardPurgeProperties` + `SchedulingConfig` + `HardPurgeJob` (`@ConditionalOnProperty` 이중 가드) + cron 트리거 → service 위임.
5. ✅ `application.yml` `app.purge` 섹션 (운영 기본 enabled=false).
6. ✅ Repository 7건 + Service 7건 + Job 통합 4건 GREEN (총 18건 신규).
7. ✅ 회귀 0 — `./gradlew test` BUILD SUCCESSFUL.
8. ✅ ADR #31 본문 게재 + status: accepted, docs/02 line 37 cascade 정책 갱신, §7.11.1 신설, docs/04 §13 footnote.
9. ✅ PR #17 CI green (backend junit + frontend vitest) + master squash-merge `5c22e23`.
10. ✅ dev-docs `dev/active/a7-hard-purge/` → `dev/completed/a7-hard-purge/` archive.

### 다음 단계

- **A8 후보**: manual purge `/api/trash/:id` endpoint + frontend 휴지통 UI (docs/01 §13).
- **docs/03 §5~§8**: 저장소 보안 / Legal Hold / 데이터 보호 / 보안 회귀 가드.
- **docs/04 본문**: 관리자 페이지 / 쿼터 / 백업.

---

## 2026-04-29 — 🏁 M16 Grid View (FileCard + FileTable view 분기)

### 범위
docs/01 §18 row 16 — `FileTable에 grid 모드 추가 (썸네일 카드형). M14의 ViewSwitch에서 토글`. M15.2의 `?view=grid`를 FileTable이 소비.

### 변경
- **lib/fileIcon.ts (M16.1 사전)**: M14에서 FileRow에 인라인이던 `fileIconFor(item)` 추출. FileRow는 import으로 교체. KISS — FileCard와의 중복 방지.
- **FileCard (M16.1)**: 신규. `Lucide 아이콘(36px) + 이름(line-clamp-2) + 메타(폴더|크기)`. selection ring(`ring-2 ring-accent` + `bg-accent-soft`), `onClick`/`onDoubleClick`, `aria-selected`/`aria-disabled`(pending). 가상화/DnD 없음 (KISS, MVP).
- **FileTable (M16.2)**: `useViewParam` 통합. `view==='grid'`일 때 새 분기 — `role=grid` `aria-label="파일 그리드"` 컨테이너 + `grid-cols-[repeat(auto-fill,minmax(140px,1fr))]` + `items.map(FileCard)`. 키보드 핸들러는 list와 동일 (`handleKeyDown` 재사용 — 1D index ArrowUp/Down 동작). list 분기 무수정.

### 검증
- `npx vitest run`: **55 files / 415 tests passed** (M16 신규 7 — FileCard 5 + FileTable view 분기 2, 회귀 0).
- `npx tsc --noEmit`: clean.
- `npx eslint`: clean.

### 핵심 결정
- **`fileIconFor` lib 추출**: M14에선 FileRow 내 헬퍼였음. M16에서 FileCard와 공유 필요 → DRY. `lib/fileIcon.ts`로 분리, FileRow 재import.
- **FileCard는 별도 컴포넌트 (FileRow 재사용 X)**: gridCols 5-col table layout이 정사각 카드와 호환 안됨. FileRow 분기 추가는 가독성 ↓ → KISS, 분리.
- **Grid 모드 가상화 없음 (MVP)**: 폴더 당 100+ 항목 시 성능 이슈 가능 → v1.x 트랙. 현재 mock 데이터/실사용 패턴은 50 미만이므로 충분.
- **Grid 모드 키보드는 1D**: 좌/우 wrap navigation은 v1.x. M16 시점엔 list와 동일하게 ArrowUp/Down만 동작 (인덱스 ±1).
- **Grid 모드 DnD 없음**: list 모드에서만 이동 가능 — Grid는 마우스 클릭 + 더블클릭만. M15.2 ViewSwitch는 단순 토글이므로 DnD 필요시 list로 전환 권장.

### 비범위 (후속)
- Grid 모드 가상화 (TanStack Virtual grid) — v1.x
- Grid 모드 2D 키보드 wrap (좌우 + 상하) — v1.x
- Grid 모드 DnD — v1.x
- 썸네일 미리보기 (실제 이미지) — backend thumbnail API 후
- Grid `aria-rowcount`/`aria-rowindex` — 1D 인덱스라 부적합. v1.x grid 2D 네비 도입 시 재검토

### 다음 세션 컨텍스트
- 시퀀스 M11→M9→M8→M14→M15→M16 **전부 완료**. PR #16 6-마일스톤 번들.
- 다음 사용자 지시 대기. (자율 실행 모드 시퀀스 끝)

---

## 2026-04-29 — 🏁 M15 Layout Extras (SortChip + ViewSwitch + StorageBar + RightPanel 탭)

### 범위
docs/01 §18 row 15 — `SortChip + ViewSwitch + StorageBar + RightPanel 탭`. 모두 docs/01 §1.1 진실 출처 규칙 (URL 우선) 준수.

### 변경
- **useSortParams (M15.1)**: 기존 read-only → `setSort(key, dir?)` 추가. 같은 key 재선택 시 asc/desc 토글, 다른 key 선택 시 asc reset. `router.replace` + `URLSearchParams` 보존.
- **SortChip (M15.1)**: 신규. FolderToolbar 우측 정렬 드롭다운. `name/updatedAt/size`, `aria-haspopup="menu"` + `menuitemradio`. outside click 시 닫힘. label "정렬: {sort} {dir}".
- **useViewParam + ViewSwitch (M15.2)**: 신규. URL `?view=list|grid` (default list 시 param 제거). `aria-pressed` + `aria-label`. Grid 본체는 M16.
- **api.getStorageQuota + useStorageQuota + StorageBar (M15.3)**: mock — 50 GB total / 75% used 고정 placeholder. Sidebar 하단 (TrashLink 아래) 마운트. `role=progressbar` + `aria-valuenow`. 80%+ warn / 95%+ danger 색.
- **RightPanel 4-tab (M15.4)**: 헤더 아래 `role=tablist` 추가. detail/versions/activity/permissions. detail은 기존 `dl` 그대로(회귀 보호). 나머지는 `<ComingSoon>` placeholder. fileId 변경 시 detail 탭으로 자동 리셋.
- **qk.storageQuota 추가**, **api.ts getStorageQuota 추가**, **(explorer)/layout.tsx StorageBar 마운트**.
- **회귀 정합**: BulkActionBar.test.tsx 3곳 / StatusBar.test.tsx 1곳 — `useSortParams` mock에 `setSort: vi.fn()` 보강 (typecheck 호환).

### 검증
- `npx vitest run`: **53 files / 408 tests passed** (M15 신규 11 — SortChip 3 + ViewSwitch 3 + StorageBar 3 + RightPanel 탭 2, 회귀 0).
- `npx tsc --noEmit`: clean.
- `npx eslint`: clean (변경 파일 17개 0 issue).

### 핵심 결정
- **URL 진실 출처**: SortChip/ViewSwitch 모두 `router.replace` + searchParams. Zustand 복제 X (docs/01 §1.1).
- **정렬 토글 규칙**: 같은 key 재선택 → dir 토글, 다른 key → asc reset. KISS, 명시적 dir override 가능.
- **ViewSwitch state는 URL 단독**: 새로고침/공유 시 view 보존. M15 시점엔 FileTable이 무관심하게 통과 → M16에서 소비.
- **StorageBar는 mock placeholder**: 75% 고정. 실제 quota API 신설 시 `api.getStorageQuota`만 fetch로 교체 (UI/hook 무수정). invalidate(업로드/삭제) 미구현 — staleTime 5분으로 우회.
- **RightPanel 탭은 단일 컴포넌트 useState**: KISS — 별도 파일 분리 X. URL 동기화 X (패널 자체가 `?file`에 종속이고 탭 상태 deep-link 요구는 v1.x).
- **세부정보 외 탭은 명확한 "준비 중" placeholder**: 가짜 컨텐츠 X — 백엔드 API(versions: A5 진행중 / audit: 기존 / permissions: 임시 mock) 통합은 별도 트랙.

### 비범위 (후속)
- Grid View 본체 (FileRow 카드 모드, FileTable 분기) — **M16**
- 버전/활동/권한 탭 실내용 — 백엔드 API 별도 트랙
- StorageBar 실수치 + 업로드/삭제 후 invalidate — 백엔드 quota API 후
- SortChip/ViewSwitch 키보드 단축키 — KISS

### 다음 세션 컨텍스트
- 시퀀스 다음: **M16 Grid View** (FileTable grid 모드 + ViewSwitch 토글 통합).

---

## 2026-04-29 — 🏁 M14 Visual Identity (Lucide + Avatar + StatusBar)

### 범위
docs/01 §4 트리 + 시각적 통일. SearchBar 아이콘 / TopBar Avatar / FileRow 이모지 → Lucide / 하단 StatusBar.

### 변경
- **SearchBar (M14.1)**: input prefix `Search` Lucide 아이콘 (16px, fg-muted). `pl-8` padding 조정.
- **Avatar (M14.2)**: 신규 컴포넌트 — `initial`/`displayName` props (default `"U"`/`"사용자"`). 28px circle, accent bg, `aria-label=displayName`. TopBar 우측 액션 영역에 마운트.
- **FileRow (M14.3)**: 이모지(`📁/📄/...`) → Lucide (`Folder`(accent) / `File` / `FileText` / `FileImage` / `FileSpreadsheet`(fg-muted)). mime 기반 분기 함수 `fileIconFor`. size 16, currentColor.
- **StatusBar (M14.4)**: 신규 — `<footer role="contentinfo">` 좌측 항목 수(`useFilesInFolder`) + 우측 선택 카운트(`useSelectionStore`, 0일 때 숨김, `aria-live=polite`). h-7 / border-t / surface-1 bg.
- **(explorer)/layout.tsx**: `<StatusBar />` main 하단 마운트.

### 검증
- `npx vitest run`: 50 files / 397 tests passed (M14 신규 6 — Avatar 2 + StatusBar 4, 회귀 0).
- `npx tsc --noEmit`: clean.
- `npx eslint`: clean (변경 파일 8개 0 issue).

### 핵심 결정
- **Lucide 단일 아이콘 라이브러리**: 이모지/SVG 혼재 제거. 색상은 `currentColor` + `text-*` 유틸로 일관 제어. 폴더만 `text-accent` 강조.
- **Avatar는 stub만**: 실제 user/session API 미정 → props 기반 placeholder. M16+ 또는 백엔드 auth 후 교체.
- **StatusBar 최소 정보**: 항목 수 + 선택 카운트만. 저장 용량/SSE 동기화/정렬 표시는 M15+에서 확장.
- **선택 카운트 aria-live**: 선택 변동을 스크린리더에 안내. 0일 땐 DOM 자체에서 숨겨 카운트 음성 잡음 방지.

### 비범위 (후속)
- StorageBar / SortChip / ViewSwitch — M15
- Avatar 실제 사용자 데이터 연결 — auth 백엔드 후
- StatusBar 동기화 상태(SSE) — M15+

### 다음 세션 컨텍스트
- 시퀀스 다음: **M15 Layout Extras** (SortChip + ViewSwitch + StorageBar + RightPanel 탭).

---

## 2026-04-29 — 🏁 M8 권한 UI + ShareDialog

### 범위
docs/01 §14 권한 훅 + 조건부 렌더링 + 단일 파일 공유. M8.0 bootstrap → M8.1 api/qk → M8.2 usePermission useQuery + BulkActionBar 마이그레이션 → M8.3 ShareDialog.

### 변경
- **api/qk (M8.1)**: `qk.permissions(nodeId)` (nodeId 없으면 `qk.effectivePermissions()`와 동일). `api.getEffectivePermissions(nodeId?)` mock — admin preset 8 권한(`READ/UPLOAD/EDIT/MOVE/DOWNLOAD/DELETE/SHARE/PERMISSION_ADMIN`, `PURGE` 제외 — docs/03 §3.2).
- **usePermission (M8.2)**: 기존 stub(lowercase 모두 true) → `useQuery` 기반, `Record<Permission, boolean>` 반환 (UPPER_SNAKE_CASE — `types/permission.ts` 미러). 로딩 중 모든 플래그 false (보수 디폴트, 깜빡임 방지). staleTime 60s.
- **BulkActionBar (M8.2/M8.3)**: 4개 필드 `download/move/edit/delete` → `DOWNLOAD/MOVE/EDIT/DELETE` 마이그레이션. 신규 SHARE 버튼 (단일 **파일** 선택 시만 활성, 폴더 공유는 v1.x).
- **stores/shareUi (M8.3)**: `useShareUiStore` (open/close + fileId + fileName).
- **ShareDialog (M8.3)**: focus trap (close 버튼) + Esc + 닫기. 링크 placeholder `https://ibiz.example/share/{fileId}` + `navigator.clipboard.writeText` + sonner 토스트. 만료/권한 옵션은 v1.x.
- **ClientFilesPage**: ShareDialog 마운트.

### 검증
- `npx vitest run`: 48 files / 391 tests passed (M8 신규 20 — api.permissions 4 + usePermission 4 + shareUi 3 + ShareDialog 6 + BulkActionBar 공유 3, 회귀 0).
- `npx tsc --noEmit`: clean.
- `npx eslint .`: clean.

### 핵심 결정
- **권한 enum 단일 진실**: `types/permission.ts` (UPPER_SNAKE_CASE 백엔드 미러)가 계약. usePermission이 그 enum 그대로 키로 노출. 기존 lowercase API는 정리.
- **로딩 중 모든 false**: docs/01 §14.3 보수적 패턴. 깜빡임은 staleTime 60s + admin preset mock 80ms로 거의 없음.
- **vi.mock 권장 패턴**: 기존 컴포넌트 테스트는 `vi.mock('@/hooks/usePermission')`로 admin preset 고정 → 권한 검증과 무관한 본 테스트 의도 보존. usePermission 자체는 별도 dedicated test.
- **단일 파일만 공유**: 폴더/다중 공유는 v1.x — 백엔드 endpoint 미정.
- **clipboard 폴백**: `navigator.clipboard?.writeText` optional chaining + try/catch — jsdom safe.

### 비범위 (후속)
- 403 글로벌 핸들러 (toast + qk.permissions invalidate) — api fetch wrapper 정리 후 별도 PR
- ShareDialog 만료/권한 옵션 — 백엔드 `POST /api/files/:id/share` endpoint 신설 후
- 폴더 공유 / 다중 파일 공유 — v1.x
- FileRow 우클릭/단축키 공유 진입점 — KISS

### 다음 세션 컨텍스트
- 시퀀스 다음: **M14 Visual Identity** (TopBar 정비 + Lucide 아이콘 + StatusBar + FileRow 밀도).

---

## 2026-04-29 — 🏁 M9 휴지통 + 5초 Undo + /trash 페이지

### 범위
soft-delete 기반 휴지통 (frontend-only mock). M9.0 bootstrap → M9.1 mock api soft-delete → M9.2 hooks → M9.3 BulkActionBar Undo → M9.4 /trash + Sidebar TrashLink.

### 변경
- **types/api (M9.1)**: `FileItem.deletedAt? + originalParentId?` 추가. `api.deleteBulk` hard splice → soft delete (`deletedAt = now`, `originalParentId = parentId` 스냅샷). `api.listTrash` (deletedAt 내림차순) / `restoreBulk` (clear deletedAt + parentId restore from originalParentId, root fallback if parent missing) / `purgeBulk` (hard splice) 신설. `getFilesInFolder`/`searchFiles`에 `!f.deletedAt` 필터 추가.
- **queryKeys (M9.1)**: `qk.trash() / qk.trashList()` 추가. `invalidations.afterDelete` → `[filesListPrefix(folder), trash(), search()]` 확장. `afterRestore(opts.folderIds[])` / `afterPurge` 신설.
- **hooks (M9.2)**: `useTrashList` (staleTime: 0) + `useRestoreBulk({ids, originalParentIds?})` + `usePurgeBulk({ids})` — 옵션 onSuccess/onError forward, invalidations 자동 호출.
- **UI (M9.3)**: `BulkActionBar` Delete onSuccess 시 `toast.success(..., {duration: 5000, action: {label: '되돌리기', onClick: restoreMut.mutate({ids, originalParentIds:[folderIdAtStart]})}})`. onError 시 `toast.error`.
- **UI (M9.4)**: `/app/(explorer)/trash/page.tsx` (section header + TrashTable) + `TrashTable` (role=grid, 컬럼: 이름/삭제 시각/원위치/액션, 로딩/에러/빈 분기, 복원 + 영구삭제 confirm) + `TrashLink` (Sidebar 하단, usePathname 기반 active aria-current). `(explorer)/layout.tsx`에 `<TrashLink />` 추가 (mt-auto pt-2 border-t).

### 검증
- `npm run test`: 44 files / 371 tests passed (M9 신규 27 — api.trash 7 + qk/invalidations 4 + hooks 4 + BulkActionBar Undo 3 + TrashTable 6 + TrashLink 3).
- `npm run typecheck`: clean.
- `npm run lint`: clean.

### 핵심 결정
- **소프트 삭제 = mock 한정** — backend A6 cascade 정책(folder 단위)과 frontend mock(file 단위)은 별개 트랙. 백엔드 file delete endpoint 신설 시 api.deleteBulk fetch만 교체, hook/UI 무수정.
- **Undo는 BulkActionBar 경유 시만** — FileTable Delete 단축키 Undo는 KISS로 분리 (별도 PR).
- **restoreBulk parent fallback** — `originalParentId` 가리키는 폴더가 (사용자 액션으로) 사라진 경우 root로 복원. backend는 `FolderNotFoundException`(A6)으로 강제하지만 frontend mock은 UX 우선.
- **vi.hoisted로 옵션 캡처** — `useDeleteBulk(opts)` 내부에서 `optionsCapture.current = opts`로 저장 → 테스트가 onSuccess 콜백 직접 트리거 가능 (mutate spy + onSuccess 분리 검증).

### 다음 세션 컨텍스트
- 시퀀스 다음: **M8 share dialog + 권한 확장** (docs/01 §14, backend A3 권한 매트릭스 활용).

---

## 2026-04-29 — 🏁 M11 검색 (debounce + normalize + AbortController)

### 범위
TopBar 글로벌 검색 (frontend-only). M11.0 bootstrap → M11.1 lib infra → M11.2 useSearch → M11.3 SearchBar/SearchResults UI.

### 변경
- **lib infra (M11.1)**: `qk.search()` / `qk.searchResults(normalized, filters)` 추가; `api.searchFiles({q, filters}, {signal})` mock (200ms latency + `normalizeForSearch` + AbortSignal); `useDebounce<T>(value, delayMs)` 신설.
- **useSearch (M11.2)**: 300ms debounce → `normalizeForSearch` → `useQuery` (`enabled: normalized.length>=2`, `placeholderData: keepPreviousData`, `staleTime: 30s`, signal forward).
- **UI (M11.3)**: `SearchBar` (searchbox role, `/` 단축키 focus via `FOCUS_SEARCH_EVENT`, Esc → clear+close, blur 120ms 지연); `SearchResults` (1자/에러/로딩/빈/파일·폴더 분기 — 파일 → `useOpenFile().open(id)`, 폴더 → `router.push(buildCanonicalPath)`); `TopBar` 좌측 슬롯 통합 (justify-end → justify-between).

### 검증
- `npm run test`: 40 files / 344 tests passed (신규 28 — useDebounce 4 + api.search 6 + qk.search 2 + useSearch 5 + SearchBar 4 + SearchResults 7).
- `npm run typecheck`: clean.
- `npm run lint`: clean (eslint exit 0).

### 핵심 결정
- mock api는 setTimeout 200ms + AbortSignal 처리 → fake-timer 환경에서는 `vi.spyOn(api, 'searchFiles').mockResolvedValue(...)`로 즉시 resolve, 별도 integration 1건만 real timers로 실제 mock 동작 검증.
- `useGlobalShortcuts`는 이미 `/` 키 → `FOCUS_SEARCH_EVENT` 디스패치 + input focus 가드 보유 → 추가 작업 없음.
- 백엔드 `/api/search` 도입 시 api 내부 fetch 교체만으로 호환 (계약 동일).

### 다음 세션 컨텍스트
- 시퀀스 다음: M9 휴지통 페이지 + Undo (api.listTrash mock 이미 존재 가능성 확인 필요).

---

## 2026-04-30 — 🏁 A6 마일스톤 종료 (Folder Delete/Restore + Descendant Cascade)

### 범위

A6.0 (docs/02 §7.5 cascade 정책 + restore-self 본문 정합, no-code) → A6.1+A6.2+A6.3 통합 (`FolderMutationService.delete/restore` + 후손 BFS cascade(folder + file batch UPDATE) + `FolderController` DELETE/restore endpoint + `FolderRestoreConflictException` + `RESTORE_CONFLICT` envelope) → refactor (cascade 후손 `originalParentId` 스냅샷 + restore가 soft-deleted parent 위로 시도 시 일관 NotFound) → A6.4 (PR #15 squash-merge `4111990` + dev-docs archive).

### 회고

- **commits**: 4 on top of A5 close `d23270e` (worktree branch `feature/a6-folder-mutation-delete`) → squash-merge `4111990` on `master`. PR #15 single, CI green (backend junit 3m9s + frontend vitest 1m10s 모두 SUCCESS).
- **production 파일**: 5 수정 + 1 신설 (`FolderMutationService` +182 lines, `FolderRepository` +36, `FolderController` +endpoints, `FileRepository` +24, `GlobalExceptionHandler` +handler, `FolderRestoreConflictException` NEW). frontend 무수정.
- **test 파일**: 2 수정 (`FolderMutationServiceTest` +5 cases — cascade/not-found/restore/conflict/cascade-child-restore-not-found, `FolderControllerTest` +2 cases — delete/restore endpoint).
- **endpoint 신규**: 2개 — `DELETE /api/folders/{id}` (204) + `POST /api/folders/{id}/restore` (200).
- **envelope code**: `RESTORE_CONFLICT` 매핑 신설 (docs/02 §8 line 1221에 이미 등록되어 있어 본문 patch 불필요).
- **A4 회귀 0**: PermissionEvaluatorIntegrationTest 13/13 GREEN 유지.

### 핵심 결정 (A6 트랙, 확정)

1. **Audit root만** — cascade 후손 FOLDER_DELETED 미발행, root 1건에 `descendantFolders/Files` 카운트 보존 (audit_log 폭증 회피, docs/02 §7.5 + CLAUDE.md §3 원칙 8).
2. **Restore self만** — 자기 자신만 복원, 후손 휴지통 잔존. `original_parent_id`가 soft-deleted면 404 ("부모 먼저 복원" UX 강제).
3. **Service 레벨 BFS** — `assertNoCycle` 패턴 일관성, MAX_CASCADE_NODES=100k 안전 한도. 성능 이슈 시 WITH RECURSIVE 전환 + ADR.
4. **Cascade 후손 originalParentId 스냅샷** — `FileRepository.softDeleteByFolderIds`가 `originalFolderId = folderId`를 set하는 것과 대칭. 후손 폴더도 개별 restore 시도 가능 (소프트-삭제된 부모 위로 복원 시도하면 `FolderNotFoundException`).
5. **Integration class 미신설 (KISS)** — `FolderControllerIntegrationTest` 별도 작성 안 함. PermissionEvaluatorIntegrationTest 13/13가 SpEL `hasPermission(folder, DELETE)` 동일 evaluator 경로 보장 + 회귀 0이 곧 권한 매트릭스 정합.
6. **단일 PR / 통합 commit** — A6.1~A6.3 테스트 상호의존(controller endpoint 미구현 시 controller test 컴파일 실패) → 단일 feature commit으로 처리.
7. **File mutation 트랙은 cascade 미참여** — A4.8(`4e720eb`)에서 닫힘. `FileMutationService.delete` 미호출, `FileRepository.softDeleteByFolderIds` batch UPDATE만 사용 (audit 정책 일관성).

### accepted-deviation (후속 backlog)

- **Hard purge job** — `purge_after` 경과 row 영구 삭제 + S3 객체 삭제. docs/04 §13 배치 트랙.
- **후손 cascade restore endpoint** — `?cascade=true` 또는 별도 path. UX 결정 후 신설.
- **Frontend 휴지통 UI** (docs/01 §13) — backend 계약 안정화 완료, 진입 가능.

### DoD 10/10

1. ✅ `FolderMutationService.delete` + 후손 BFS cascade + `MAX_CASCADE_NODES` 안전 한도
2. ✅ `FolderRepository.softDeleteByIds` (`originalParentId` 스냅샷 포함) + `FileRepository.softDeleteByFolderIds`
3. ✅ Audit root만 발행, `after_state.descendantFolders/Files` 카운트 보존
4. ✅ `FolderMutationService.restore` + parent 활성 검증 + `existsActiveByParentAndNormalizedNameExcludingId` 충돌 검사
5. ✅ `FolderRestoreConflictException` 신설 + `GlobalExceptionHandler` → 409 `RESTORE_CONFLICT` 매핑
6. ✅ `FolderController.delete` (204) + `restore` (200) endpoint + `@PreAuthorize("hasPermission(#id, 'folder', 'DELETE')")`
7. ✅ FolderMutationServiceTest 5 cases + FolderControllerTest 2 cases GREEN
8. ✅ A4 PermissionEvaluatorIntegrationTest 13/13 GREEN, frontend test 회귀 0
9. ✅ docs/02 §7.5 cascade 정책 + restore-self 본문 정합 (line 881~922)
10. ✅ PR #15 CI green + master squash-merge `4111990` + dev-docs `dev/active/a6-folder-mutation-delete/` → `dev/completed/`

### 다음 단계

- **A7 후보**: hard purge job (docs/04 §13) 또는 frontend 휴지통/탐색기 UI (docs/01 §13).
- **docs/03 §5~§8**: 저장소 보안 / Legal Hold / 데이터 보호 / 보안 회귀 가드 (코드 0줄 트랙).
- **docs/04 본문**: 관리자 페이지 / 쿼터 / 백업.

---

## 2026-04-29 — A6.0 docs/02 §7.5 cascade 정책 + restore-self 명시 (no-code)

### 범위
A6 마일스톤 진입점. folder delete/restore 트랙의 §7.5 응답 본문 정합 patch.

### 변경
- `docs/02 §7.5` `DELETE /api/folders/:id` 행 SoftDel 컬럼에 `(재귀: 후손 폴더/파일 cascade — root 1회 audit)` 보강.
- `docs/02 §7.5` `POST /api/folders/:id/restore` 행 SoftDel 컬럼에 `(자기 자신만 복원, 후손 잔존)` 보강.
- `docs/02 §7.5` 응답 본문(line ~915) DELETE/restore TX 의사코드에 cascade BFS + audit root-only + restore-self 정책 명시.

### 검증
- A6.0 commit 자체는 docs 1파일만 staged (코드 0줄).
- §8 `RESTORE_CONFLICT`는 line 1221에 이미 등록(A4 마일스톤 closure 시점) — 별도 patch 불필요.
- 다음 phase A6.1 `FolderMutationService.delete` 구현이 본 §7.5 본문과 1:1 정합.

---

## 2026-04-29 — 🏁 A5 마일스톤 종료 (FileVersion Domain — entity + GET /versions)

### 범위

A5.0 (docs/02 §7.6 응답 스키마 + ADR #29 트리거 마커, no-code) → A5.1 (`VersionScanStatus` enum + converter + `FileVersion` @Entity + `FileVersionRepository` + Testcontainers 7건) → A5.2 (`FileVersionDto` record + `FileVersionController` GET `/api/files/{fileId}/versions` + `@PreAuthorize` READ + 권한/404 매트릭스 통합 테스트 7건) → A5.3 (CI green wait + squash-merge `5155e00` + ADR #29 closed + dev-docs archive).

### 회고

- **commits**: 7 on top of A4 close `48e23a3` (worktree branch `feature/a5-file-versions`) → squash-merge `5155e00` on `master`. PR #13 single, CI green (backend junit + frontend vitest 모두 SUCCESS).
- **production 파일**: 5 신설 (backend `file/VersionScanStatus.java`, `file/VersionScanStatusConverter.java`, `file/FileVersion.java`, `file/FileVersionRepository.java`, `file/dto/FileVersionDto.java`, `file/FileVersionController.java`). frontend 무수정.
- **test 파일**: 2 신설 (`FileVersionRepositoryTest` 7건, `FileVersionControllerTest` 7건). A4 401건 테스트 회귀 0.
- **endpoint 신규**: 1개 — `GET /api/files/{fileId}/versions` (DESC 정렬, `isCurrent` 계산, soft-delete 404, `@PreAuthorize` READ).
- **ADR 변경**: **#29 closed** (deferred → "closed (A5, squash-merge `5155e00`)"). FileVersion entity/repo + GET versions까지 도입. POST `/versions` (업로드 commit) + restore는 A6+ 이월.
- **schema-validation issue**: 최초 commit에서 `FileVersion.checksumSha256`을 `columnDefinition="char(64)"`로 매핑 → CI에서 Hibernate logical type VARCHAR ↔ Postgres bpchar(CHAR) 불일치로 ApplicationContext 부트 실패 (Testcontainers 125건 cascade FAILED). `@JdbcTypeCode(SqlTypes.CHAR)` 주입 fix 후 CI green.
- **camelCase 정합**: `FileVersionDto`는 기존 `FileDto`/`FolderDto`와 동일하게 camelCase JSON 키. docs/02 §7.6 본문 예시는 snake_case로 적혀있어 closure에서 §7.6 본문 정합 patch (별도 commit).

### 핵심 결정 (A5 트랙, 확정)

1. **FileVersion entity Lombok-free** — A4 entity 패턴(`Folder`/`FileItem`) 보존. JPA AttributeConverter (`autoApply=false`) + `VersionScanStatusConverter`로 DB lowercase ↔ Java UPPERCASE 변환.
2. **`scan_result JSONB` entity 미매핑** — A5 list endpoint 응답 스키마 미포함 + JSONB↔JPA 의존성 회피 (audit 모듈과 동일 — JdbcTemplate 분리). 스캐너 워커 도입 시점에 별도 매핑 검토 (KISS — YAGNI).
3. **FileItem.currentVersionId 매핑 = UUID 컬럼** — `@OneToOne` 대신 단순 UUID. lazy proxy 비용/cycle 위험 회피 + service layer가 명시적 fetch.
4. **GET /versions 만 도입** — POST `/versions` (업로드 commit), restore, comment 수정 등은 A6+ 이월. ADR #29 deferred는 GET까지로 close 처리.
5. **CHAR vs VARCHAR 매핑 표준화** — `CHAR(N)` 컬럼은 entity에서 `@JdbcTypeCode(SqlTypes.CHAR) + @Column(length=N)`로 매핑. `columnDefinition` string은 logical type 결정에 무력 — 회귀 가드.

### accepted-deviation (A6+ 이월)

- POST `/api/files/{fileId}/versions` (업로드 commit + 멀티파트 + scan-status='pending' enqueue) — A4.8 file mutation 트랙과 별개로 진입 시점 미정.
- `FileVersion.scanResult` JSONB 매핑 — 스캐너 워커 도입과 동시 (위 결정 #2).
- 버전 restore (`PATCH /api/files/{id}/restore-version`) — 사용자 주도 롤백 UX와 함께 후속.
- docs/02 §7.6 본문 snake_case 예시 → camelCase 정합 patch — 본 closure commit에서 동시 처리.

### DoD 10/10

1. ✅ `VersionScanStatus` enum + converter — DB lowercase ↔ Java UPPERCASE 라운드트립 GREEN
2. ✅ `FileVersion` @Entity + V5 schema-validation GREEN (`ddl-auto=validate`)
3. ✅ `FileVersionRepository.findByFileIdOrderByVersionNumberDesc` + `existsByStorageKey` — Testcontainers 7건 GREEN
4. ✅ `FileVersionDto` record + `from(v, currentVersionId)` factory — `isCurrent` 계산 정확
5. ✅ `FileVersionController` GET endpoint + `@PreAuthorize("hasPermission(#fileId, 'file', 'READ')")` + soft-delete 404
6. ✅ Controller 통합 테스트 7건 — ADMIN/AUDITOR/MEMBER(no-grant 403)/MEMBER(grant 200)/missing 404/soft-deleted 404/currentVersionId NULL → 모두 GREEN
7. ✅ A4 evaluator 13건 + audit 회귀 0, audit_log REVOKE 정책 무영향 (read-only endpoint, audit emission 없음)
8. ✅ ADR #29 status "deferred" → "closed (A5, `5155e00`)" 정합. docs/00 §5 갱신
9. ✅ PR #13 CI green (backend junit + frontend vitest 모두 SUCCESS) + master squash-merge `5155e00`
10. ✅ dev-docs `dev/active/a5-file-versions/` → `dev/completed/` archive + docs/02 §7.6 camelCase/`NOT_FOUND` envelope 정합 patch

### 다음 단계 — A6 또는 docs/03~04 본문

- **A6 (이미 active)**: folder delete/restore + descendant cascade soft-delete + RESTORE_CONFLICT envelope (별도 worktree `dev/active/a6-folder-mutation-delete/`).
- **A5 잔여 → A6+ 이월**: POST `/versions` (업로드 commit), 버전 restore, scan worker, scanResult JSONB 매핑.
- **docs/03 §5~§8 본문**: 저장소 보안(KMS/presigned TTL), Legal Hold, 데이터 보호, 보안 회귀 가드 (코드 0줄 트랙).
- **docs/04 본문**: 관리자 페이지·쿼터·백업 (별도 트랙).

---

## 2026-04-29 — A5.0 docs/02 §7.6 + ADR #29 트리거 마커 (no-code)

### 범위
A5 마일스톤 진입점. ADR #29 deferred 클리어를 위한 docs 정합 patch.

### 변경
- `docs/02 §7.6 (Files)` GET `/api/files/:id/versions` 응답 스키마 본문 보강 (`versions` 배열 + `is_current` 플래그 + 정렬(version_number DESC)·soft-delete 404 정책 명시). _주: dev-docs(plan/tasks)는 §7.7로 표기되어 있으나 실제 표 위치는 §7.6 — A5 후속 phase에서 dev-docs drift 정정 예정._
- `docs/00 §5 ADR #29` 본문에 "A5 진입 (2026-04-29)" 트리거 마커 1줄 추가 (close는 A5.3 commit hash로 갱신 예정).

### 검증
- A5.0 commit 자체는 docs 3파일만 staged (코드 0줄). _작업 트리에 별도 세션 발 backend 변경 잔재 발견 — 본 commit 범위 외, 사용자 확인 대기._
- 다음 phase A5.1 FileVersion entity 작성이 본 §7.6 스키마와 1:1 정합.

---

## 2026-04-29 — 🏁 A4 마일스톤 종료 (Folder/File Domain + Resource-Level Permissions)

### 범위

A4.0 (docs/02 §2.3/§2.6/§7.10 정합 patch + ADR #27/#28/#29) → A4.1 (V5 마이그레이션 — folders/files/file_versions/permissions + UNIQUE COALESCE + DEFERRABLE FK + V5MigrationIT) → A4.2 (file/permission entity·repo + 재귀 CTE `findEffective`, Folder는 ownership 충돌로 A4.5 흡수) → A4.3 (`IbizDrivePermissionEvaluator` 내부 resource-level 교체 + 상속, A3 13/13 회귀 보존) → A4.4 (PermissionController 4 endpoint + `permission.granted/revoked` 실 emission, ADR #26 close) → A4.5 (Folder JPA entity + FolderRepository, A4.2 deferred 흡수) → A4.6 (FolderMutationService + create/rename/move + audit emit + cycle 가드 — delete/restore는 A6로 이월) → A4.7 (FolderController + 3 REST endpoint + RENAME_CONFLICT envelope + ADR #30 root=ADMIN-only).

### 회고

- **commits**: 9 (no-merges) on top of A3 close `6f0820d` → master HEAD `48e23a3`. PR 6건 (#6 A4-data, #7 evaluator, #8 perm-endpoint, #9 folder-entity, #10 mutation-service, #11 folder-endpoint) 모두 squash-merged + CI green.
- **production 파일**: 26 신설/변경 (backend `folder/` 11 + `permission/` 11 + `file/` 2 + `audit/` 1 + `common/error/` 1) + frontend는 enum mirror 무수정 (A3 1:1 mirror 그대로 유효).
- **test 파일**: 13 신설 (A4 신규 — `V5MigrationIT`, `FolderRepositoryTest`, `FolderMutationServiceTest`, `FolderControllerTest`, `PermissionRepositoryTest`, `PermissionResolverTest`, `IbizDrivePermissionEvaluatorTest`, `PermissionControllerTest`, `PermissionServiceGrantRevokeTest`, `PermissionAuditListenerTest` 등). A3 13개 evaluator 통합 테스트 회귀 보존.
- **마이그레이션**: V5 — `folders` (parent UNIQUE = `COALESCE(parent_id, ZERO_UUID)` 보강, soft delete `WHERE deleted_at IS NULL`) + `files` (`folder_id` FK, normalized_name UNIQUE) + `file_versions` (테이블만 도입, entity는 ADR #29로 A5 이월) + `permissions` (preset 단일 컬럼, ADR #28).
- **endpoint 신규**: 7개 — `POST/DELETE/GET /api/{resource}/{id}/permissions`, `GET /api/me/effective-permissions` (A4.4) + `POST /api/folders`, `PATCH /api/folders/{id}`, `POST /api/folders/{id}/move` (A4.7).
- **ADR 변경**: **#26 closed** (A4.4 — `permission.granted/revoked` 실 emission + 4 endpoint 도입), **#27/#28/#29 신규** (A4 PR 분할 / preset 단일 / FileVersion A5 이월), **#30 신규** (A4.7 — root parent 작업은 ROLE ADMIN-only SpEL 삼항 분기).
- **재귀 CTE**: `PermissionRepository.findEffective(userId, resourceType, resourceId)` — 부모→자식 상속 + grant 우선 lookup (ADR #28). evaluator는 SpEL 시그니처 `hasPermission(#id, 'folder', 'READ')` 그대로 보존, 내부만 교체 (ADR #26 보장사항 충족).
- **트랜잭션**: 모든 mutation = `@Transactional` + `SELECT FOR UPDATE` (FolderMutationService). audit emit은 `REQUIRES_NEW`로 분리 (ADR #24 보존). audit append-only `42501` 회귀 0 (V4 REVOKE 무영향).

### 핵심 결정 (A4 트랙 6+1, 확정)

1. **PR 2개 분할** — A4-data + A4-controllers, 의존 단방향. 추가로 A4-controllers 내부도 evaluator/perm-endpoint/folder-entity/mutation-service/folder-endpoint 5 sub-track으로 분기 (ADR #27)
2. **permissions = preset 단일 컬럼**, deny semantics v1.x 이월 (ADR #28). evaluator는 grant 우선 lookup만 (allow 발견 → true, 어디서도 grant 없으면 false)
3. **FileVersion entity/repo/CRUD A5 이월** — V5 schema는 테이블 + DEFERRABLE FK만 (ADR #29). `current_version_id` NULL 허용 유지
4. **root parent 작업 = ROLE ADMIN-only** — SpEL 삼항 `#req.parentId == null ? hasRole('ADMIN') : hasPermission(...)` (ADR #30). 노드 admin preset이 root 트리 구조 변경으로 번지지 않게 차단
5. **SpEL 호출 시그니처 0변경** — A3 `hasPermission(#id, 'folder', 'READ')` 그대로, evaluator 내부만 user-level → resource-level. controller `@PreAuthorize` 회귀 0 (ADR #26 보장사항 충족)
6. **Folder ownership 충돌 → A4.5 흡수** — A4.2에서 file/permission만 진행, master worktree `dev/process/20260428-a3-folder-mutation-service.md` ownership 해제 후 a4-folder-entity 세션에서 정리 + Folder JPA entity 신설

### accepted-deviation (A5 이월)

- `file_versions` entity/repository/CRUD endpoint — 버전 이력 UI/API는 별도 기능 (ADR #29). V5 schema는 테이블만 도입했으므로 A5는 entity/repo/endpoint만 추가
- 명시 deny semantics — `permissions.deny BOOLEAN` 또는 별도 `denies` 테이블 (ADR #28). 현 schema 호환 (컬럼 추가만으로 확장)
- LTREE 부서 계층 + `includeDescendants` — 부서 모델 자체가 A1.5 후속 (track 미정)
- File mutation/CRUD endpoint — A4는 Folder MVP까지만, File mutation은 A5 또는 별도 트랙 (5 endpoint 미구현)

### DoD 11/11

1. ✅ V5 마이그레이션 GREEN — 4테이블 + UNIQUE COALESCE + DEFERRABLE FK + REVOKE 무충돌 (V5MigrationIT 7+ 케이스)
2. ✅ `Folder`/`FileItem`/`PermissionRow` entity + repository + 재귀 CTE `findEffective`
3. ✅ A3 `PermissionEvaluatorIntegrationTest` 13/13 GREEN 회귀 보존 + resource-level 신규 테스트 GREEN
4. ✅ `PermissionService.grantPermission/revokePermission` + `PermissionGrantedEvent/RevokedEvent` + `PermissionAuditListener` 확장 — `permission.granted/revoked` 실 emission (REQUIRES_NEW)
5. ✅ `PermissionController` 4 endpoint + 가드 + 403 envelope `PERMISSION_DENIED`
6. ✅ `FolderMutationService` create/rename/move — `@Transactional` + `SELECT FOR UPDATE` + cycle 가드 (delete/restore는 A6로 이월)
7. ✅ `FolderController` 3 endpoint (create/rename/move) + `RENAME_CONFLICT` 409 envelope
8. ✅ ADR #26 status "deferred" → "closed (A4.4)" 표기 정합. ADR #27/#28/#29/#30 docs/00 §5 등록
9. ✅ A2 audit append-only 회귀 0 — V4 REVOKE 정책 무영향, `42501` 가드 보존
10. ✅ PR #6/#7/#8/#9/#10/#11 모두 CI green (backend junit + frontend vitest 둘 다 SUCCESS) + master squash-merge
11. ✅ dev-docs `dev/active/a4-folder-file-domain/` + sub-track 5건 → `dev/completed/` archive (parent + 5 sub-tracks 통합 closure)

### 다음 단계 — A5 또는 docs/03~04 본문

- **A5 후보**: `FileVersion` entity/repo + 버전 생성/조회/복원 endpoint (ADR #29 deferred 클리어). File mutation endpoint(rename/move/delete/restore) 흡수 검토.
- **docs/03 §5~§8 본문**: 저장소 보안(KMS/presigned TTL), Legal Hold, 데이터 보호, 보안 회귀 가드 (코드 0줄 트랙).
- **docs/04 본문**: 관리자 페이지·쿼터·백업 (별도 트랙).
- **stale PR 정리**: #3 (codex/a3-mutation-domain), #4 (codex/a3-folder-mutation-service) — A3 머지가 다른 경로로 끝났으므로 close 예정.

---

## 2026-04-29 — A4.0 docs/02 정합 patch (no-code)

### 범위
A4-data 트랙 진입점. ADR #27/#28/#29 결정을 docs/02 본문에 반영.

### 변경
- `docs/02 §2.3 folders` UNIQUE 인덱스: `parent_id` → `COALESCE(parent_id, ZERO_UUID)` 보강 + 보강 사유 주석 1줄 (root parent 다중 NULL 차단, ADR #27 보장사항).
- `docs/02 §2.6 permissions` `preset` 컬럼 코멘트에 "preset 단일 — deny v1.x 이월, ADR #28" 주석 1줄.
- `docs/02 §7.10` POST `/api/:resource/:id/permissions` Guard `'ADMIN'` → `'PERMISSION_ADMIN'` 정합 (구표기 alias 명시).

### 검증
- `git diff --stat backend/ frontend/` → 비어있음 (코드 0줄).
- 다음 phase A4.1 V5 SQL이 본 §2.3/§2.6 본문과 1:1 정합.

---

## 2026-04-29 — 🏁 A3 마일스톤 종료 (Permission Matrix + PermissionService)

### 범위
A3.0 (docs/03 §3 정합화 + ADR #26) → A3.1 (`Permission`/`Preset` enum + frontend 1:1 mirror) → A3.2 (`PermissionService` + `IbizDrivePermissionEvaluator` + 403 envelope) → A3.3 (`effectivePermissionsCacheKey` 정적값 → SHA-256 hex prefix 16자) → A3.4 (`permission.changed` audit emission via `RoleChangedEvent` + listener) → A3.5 (full `@SpringBootTest` + Testcontainers E2E — 매트릭스 + role change).

### 회고
- **commits**: 8개 (`ff5156c` dev-docs/ADR #26 → `aec7b74` docs/03 §3 표기 정합 + `permission.ts` placeholder → `4458feb` A3.1 enum + frontend mirror → `e1083e4` A3.2~A3.4 backbone (cache key hash + permission.changed) → `ccd766d` A3.5 E2E + closure 2건). diff vs origin/master 예상: backend +production 7파일 / +test 9파일, frontend +1파일.
- **테스트**: backend +6 단위 클래스 (`PermissionEnumTest`, `PresetMappingTest`, `PermissionServiceTest`, `PermissionServiceChangeRoleTest`, `PermissionCacheKeyServiceTest`, `PermissionAuditListenerTest`) + 1 슬라이스 (`PermissionEvaluatorIntegrationTest` 13 케이스) + 2 E2E (`PermissionEndpointE2ETest` 11 + `RoleChangeE2ETest` 2). 게이트 1 235 → 게이트 2 248 → A3.5 261 (총 +26). frontend 305 → 316 (`permission.test.ts` 11 케이스).
- **production 클래스**: 7 (permission/ 6 + common/error/ 1). `Permission`(9 values) / `Preset`(5 values + `permissions()`) / `PermissionService`(check + changeRole) / `IbizDrivePermissionEvaluator` / `PermissionCacheKeyService` / `PermissionDenyContext` / `RoleChangedEvent` / `MethodSecurityConfig` / `GlobalExceptionHandler.handleAccessDenied` (`PERMISSION_DENIED` envelope).
- **마이그레이션**: 0 — A3는 user-level (Role 기반) MVP, `permissions` 테이블 미도입 (resource-level은 A4 의존).
- **endpoint 신규**: 0 production (권한 grant/revoke endpoint는 file/folder 도메인 의존 → A4 이월). 기존 `/api/auth/login` + `/api/auth/me` 응답의 `effectivePermissionsCacheKey`만 hash로 wire 변경 (shape 동일, 값만 hex).
- **ADR 등록**: **#26** (`PermissionEvaluator` MVP 범위 — user-level만, resource-level 평가는 A4에서 evaluator 내부만 교체, SpEL 호출 시그니처 `hasPermission(#id, 'folder', 'READ')`는 docs/02 §7.10 그대로 채택, `permission.granted/revoked` emit도 A4 이월, `effectivePermissionsCacheKey`는 SHA-256 hex prefix 16자 — A1 deviation #2 해소).
- **frontend 통합**: `frontend/src/types/permission.ts` 신설 (1:1 mirror). 사용처는 UX 게이트 한정 (보안 boundary 아님 — CLAUDE.md §3 원칙 10). `effectivePermissionsCacheKey` 응답 shape 동일 — opaque token으로만 사용.

### 핵심 결정 (트랙 5+1, 확정)
1. user-level (Role 기반) MVP — `permissions` 테이블 + 재귀 CTE 상속 평가는 A4 이월. SpEL 호출 시그니처는 docs/02 §7.10 그대로 동결 (ADR #26)
2. PURGE 가드 = `hasRole('ADMIN')` — `Preset.admin`이 PURGE를 의도적으로 제외해 `hasPermission` 경로로는 어떤 role도 통과 불가 (docs/03 §3.2 line 333). 회귀 가드는 `PermissionEndpointE2ETest`의 `/purge` 매트릭스로 락
3. cacheKey = SHA-256 hex prefix 16자 — 입력 `<userId>:<ROLE>:v1` (`MATRIX_VERSION` bump → 일괄 invalidate hook). frontend는 opaque token으로만 사용 — 역파싱 금지 보장으로 매트릭스 변경 안전 (ADR #26)
4. emission = `RoleChangedEvent` + `PermissionAuditListener` (ADR #24 동형 — `AuthAuditListener` 패턴 1:1 채택). `AuditService.record`의 REQUIRES_NEW로 audit 무결성 (ADR #25 보존)
5. 403 envelope = `{ error: { code: 'PERMISSION_DENIED', message, details: { required, have } } }` — `PermissionDenyContext` (ThreadLocal) 1회 consume으로 evaluator → exception handler 정보 전달 (docs/03 §3.6)
6. enum 단일 진실 = 백엔드, frontend는 mirror — A2 패턴 동일

### accepted-deviation (A4 이월)
- `permission.granted` / `permission.revoked` emission — resource-level grant endpoint (POST/DELETE `/api/:resource/:id/permissions`)는 file/folder 도메인 부재로 A4. 본 phase는 `permission.changed`(role 변경)만 실 emit. enum 자체는 A2에서 등록 완료 (ADR #26)
- LTREE 부서 계층 + `includeDescendants` 평가 — 부서 모델 자체가 A1.5 후속 (A4)
- 권한 상속 재귀 CTE (docs/03 §3.4) — folder tree 부재 (A4)
- `effectivePermissions` resource-level 캐시 store — A3는 user-level hash key만, 실제 캐시 store는 v1.x

### DoD 11/11
1. ✅ docs/03 §3.1~§3.6 표기 정합 + 본문이 docs/02 §7 Guard 컬럼과 1:1 일치 (`aec7b74`)
2. ✅ `Permission` enum 9 values (`PermissionEnumTest`)
3. ✅ `Preset` enum 5 values + preset→permission set §3.2 표 동치 (`PresetMappingTest`)
4. ✅ `frontend/src/types/permission.ts` 1:1 mirror (`permission.test.ts` 11 케이스)
5. ✅ `PermissionService.check` 단일 진입점 + `IbizDrivePermissionEvaluator` SpEL hook (`PermissionEvaluatorIntegrationTest` 13 + `PermissionEndpointE2ETest` 11)
6. ✅ user-level MVP 평가 정책 (ADMIN=all, AUDITOR=READ, MEMBER=∅, PURGE는 hasRole(ADMIN) 가드)
7. ✅ `effectivePermissionsCacheKey` SHA-256 hex prefix 16자 — deterministic + collision-free (`PermissionCacheKeyServiceTest` 7 케이스)
8. ✅ `permission.changed` emission policy + 실 emit (`PermissionAuditListenerTest` 2 + `RoleChangeE2ETest` 2). granted/revoked는 ADR #26로 A4 이월 명시
9. ✅ `@SpringBootTest` E2E 매트릭스 (ADMIN/AUDITOR/MEMBER × READ/EDIT/PURGE) + role change scenario
10. ✅ `gradle test` + `pnpm test` 로컬 GREEN + PR #5 CI 그린 최종 확정 (run `25075778972`: backend junit + frontend vitest 둘 다 SUCCESS, commit `8ecff7d`)
11. ✅ ADR #26 docs/00 §5에 등록 (`ff5156c`)

### 다음 단계 — A4 진입점
- 폴더/파일 도메인 (`folders`, `files`, `permissions` 테이블 + UNIQUE 제약 + LTREE)
- POST/DELETE `/api/:resource/:id/permissions` endpoint → `permission.granted/revoked` 실 emit 호출처
- `IbizDrivePermissionEvaluator` 내부 교체 — resource-level grant + 재귀 CTE 상속 평가 (SpEL 호출 시그니처는 보존)
- `effectivePermissions` resource-level 캐시 store (v1.x 후보)

---

## 2026-04-29 — A3.2~A3.4 (게이트 2)

### 완료
- **A3.2** PermissionService + IbizDrivePermissionEvaluator + 403 envelope
  - `PermissionService.check(userId, role, resource, resourceId, permission)` user-level MVP (ADMIN=ALL, AUDITOR=READ, MEMBER=∅)
  - `IbizDrivePermissionEvaluator implements PermissionEvaluator` — Spring Security `@PreAuthorize("hasPermission(#id,'folder','READ')")` SpEL hook
  - `MethodSecurityConfig` (`@EnableMethodSecurity` + `DefaultMethodSecurityExpressionHandler` 빈에 evaluator 주입)
  - `PermissionDenyContext` (ThreadLocal) — evaluator deny 판정 시 required/have set을 1회 consume 형식으로 ExceptionHandler에 전달
  - `ApiError` (docs/02 §7.2 envelope) + `GlobalExceptionHandler.handleAccessDenied` → 403 `PERMISSION_DENIED` + `details.required`/`details.have`
  - `TestPermissionController` (`src/test/java`) + `PermissionEvaluatorIntegrationTest` 10 케이스 (ADMIN/AUDITOR/MEMBER × READ/EDIT/PURGE + 익명 401)
- **A3.3** effectivePermissionsCacheKey hash 교체
  - `PermissionCacheKeyService.computeKey(userId, role)` — SHA-256 hex prefix 16자 (lowercase), 입력 `<userId>:<ROLE>:v1` (`MATRIX_VERSION` bump → 일괄 invalidate hook). 7 unit tests
  - `LoginResponse.from(User, String cacheKey)`로 시그니처 변경, `AuthService.login` / `AuthController.me`에 service 주입 + 사전 산출 키 사용 (session attribute도 동일 키)
  - `AuthMeLogoutIntegrationTest` plaintext assertion → `[0-9a-f]{16}` regex로 교체
- **A3.4** permission.changed audit emission
  - `RoleChangedEvent(actorId, targetUserId, from, to)` record (publish는 트랜잭션 커밋 직전)
  - `PermissionAuditListener` — `@EventListener` for RoleChangedEvent → `AuditEventType.PERMISSION_CHANGED` row + `before`/`after` JSON `{"role":"..."}` (REQUIRES_NEW 보존, swallow + ERROR 로그)
  - `PermissionService.changeRole(targetUserId, newRole, actorId)` (`@Transactional`) — user.role 갱신 + repository.save + event publish, 같은 role no-op
  - `User.changeRoleTo(Role)` 도메인 mutator
  - 4 unit (`PermissionServiceChangeRoleTest`) + 2 unit (`PermissionAuditListenerTest`) + `PermissionServiceTest` constructor 적응

### 검증
- backend `./gradlew test`: **248 tests, 0 failures, 100% successful** (게이트 1 235 → +13)
- frontend `pnpm test`: **316 tests, 0 failures**

### accepted-deviation
- `permission.granted` / `permission.revoked` emission은 A4 (resource-level grant endpoint 도입 시점). 본 phase는 `permission.changed`만 실 emit (ADR #26)

### 다음 단계 (게이트 2 OK 대기 → A3.5)
- A3.5 E2E: ADMIN→MEMBER 변경 후 다음 요청 403 + audit `permission.changed` 1건 (full SpringBootTest + Testcontainers)
- 권한 매트릭스 전체 E2E: ADMIN/AUDITOR/MEMBER × hasPermission READ/EDIT/PURGE

---

## 2026-04-29 — A3.0 docs 정합 + ADR #26 (no-code phase)

### 완료
- **docs/03-security-compliance.md** 헤더 `현재 상태: 스켈레톤` → `§1·§2·§3·§4 본문 활성, §5·§6·§7·§8 일부 본문 진행 중` (line 4) — §3 본문은 이미 §3.1~§3.6 작성 완료 상태였으나 헤더가 stale했던 표기 정합
- **CLAUDE.md** §2 라우팅 표 "권한 매트릭스 (작성 예정)" → "권한 매트릭스 (§3)" / §4 계약 파일 표 `src/types/permission.ts` "(예정)" 제거
- **docs/00-overview.md** §5에 **ADR #26** 추가 — `PermissionEvaluator` MVP는 user-level (Role 기반) 평가만, resource-level은 A4 이월. SpEL 호출 시그니처(`hasPermission(#id, 'folder', 'READ')`)는 docs/02 §7.10 그대로 채택해 A4에서 evaluator 내부만 교체. `permission.granted/revoked` emit도 A4 이월 (A3는 `permission.changed`만), `effectivePermissionsCacheKey` SHA-256 hex prefix 16자 (A1 deviation #2 해소 예고)
- **frontend/src/types/permission.ts** placeholder 신설 (`export {}` + JSDoc backlink)

### 검증
- `pnpm typecheck` PASS, `pnpm lint` PASS

### 다음 단계 (A3.1 진입)
- backend `com.ibizdrive.permission.Permission` enum 9 values + `Preset` enum 5 values + frontend 1:1 mirror (RED→GREEN)

---

## 2026-04-28 — 🏁 A2 마일스톤 종료 (Audit Log Backbone)

### 범위
A2.0 (V3 audit_log 스키마 + V4 REVOKE) → A2.1a (AuditService + Enums + REQUIRES_NEW) → A2.1b (`@Audited` AOP + WebRequestContextHolder) → A2.2 (append-only 강제 검증, A2.0과 통합) → A2.3 (`GET /api/admin/audit` + role-based scope) → A2.4 (A1 인증 이벤트 emission via `ApplicationEventPublisher` + listener) → A2.5 (E2E 통합 — auth → audit_log → query) → A2.6 (frontend mock → real fetch).

### 회고
- **commits**: 20개 (a6076f0 dev-docs/ADR → 440b0b0 V3+V4 → fd28368/1196e11/cf0be93 A2.1a + fix → a0f9f7e A2.1b → 2fdad2d A2.3 → 7aaea19 A2.4 → 14cec52/3fd8c57/f1ab7a6/b8cc4f2 A2.5 + CI 부트스트랩 → 36896a8 A2.6 → 진행 doc 5건). diff vs origin/master: +3,372 / -131, 36 파일.
- **테스트**: backend +9 클래스 (`AuditLogSchemaTest`, `AuditLogAppendOnlyTest`, `AuditServiceTest`, `AuditedAspectTest`, `AuthAuditListenerTest`, `AuditQueryControllerTest`, `AuditQueryServiceTest`, `AuthAuditE2ETest`, `AuditQueryE2ETest`) — 단위/슬라이스/E2E 모두 그린. frontend `api.audit.test.ts` 7 케이스로 재작성 후 305/305 PASS.
- **production 클래스**: 13 (audit/ 11 + audit/dto/ 2). `AuditEvent`/`AuditEventType`(38) /`AuditTargetType`(7)/`AuditService`/`AuditQueryController`/`AuditQueryFilters`/`AuditQueryService`/`Audited`/`AuditedAspect`/`AuthAuditListener`/`WebRequestContextHolder` + `AuditLogEntryDto`/`AuditLogPageDto`.
- **마이그레이션 2종**: `V3__audit_log.sql` (스키마 + `target_type` CHECK 7값 + 인덱스 4개), `V4__audit_log_revoke.sql` (`app_user` role 생성 idempotent + REVOKE UPDATE/DELETE + GRANT INSERT/SELECT).
- **endpoint 1종**: `GET /api/admin/audit?fromDate&toDate&actorQuery&eventType&page&pageSize` — ADMIN/AUDITOR 전체, MEMBER scope=self, 익명 401 (service 단일 분기, controller `isAuthenticated()`).
- **ADR 등록**: **#24** (AOP `@Audited` + Spring Security `ApplicationEvent` 하이브리드 — `publishEvent(...)` 호출은 cross-cutting 신호로 침투 허용, 비즈니스 로직 0줄), **#25** (DB role 분리 + REVOKE UPDATE/DELETE → `42501`로 append-only 증명).
- **frontend 통합**: `api.getAuditLogs` mock 분기 + 60-row generator 완전 제거 → `fetch('/api/admin/audit?...', { credentials: 'include' })`. `next.config.ts` rewrite로 dev에서 same-origin 쿠키 흐름. wire shape는 M12 mock 표면과 1:1 동치.
- **DoD 10/10 충족**: (1) audit_log + 4 인덱스 ✅ (2) `42501` 증명 (`AuditLogAppendOnlyTest`) ✅ (3) Java enum 38값 = ts mirror 1:1 ✅ (4) `AuditService.record()` 단일 진입점 + AOP + listener 하이브리드 ✅ (5) AuthService 비즈니스 로직 0줄 변경 (publish 4지점만 추가, ADR #24 갱신) ✅ (6) role 기반 scope 분기 ✅ (7) `api.audit.test.ts` 7 케이스 PASS ✅ (8) ADR #24/#25 docs/00 §5 등록 ✅ (9) `gradle test` + `pnpm test` CI 그린 (run 25023235347) ✅ (10) backup 브랜치 `backup/pre-reset-20260427-0036` 보존 ✅.

### 핵심 결정 (트랙 5+1, 확정)
1. emission 위치 = AOP `@Audited`(`@AfterReturning`) + Spring Security `ApplicationEventPublisher` 하이브리드 — annotation grep 가능, 트랜잭션 롤백 자동 처리, AuthService 침투는 publish 호출만 (ADR #24)
2. 보존 3년 + Legal Hold 무기한 — docs/03 §4.3 명시값 채택, 월별 파티셔닝은 v1.x로 deferred (단일 테이블 MVP)
3. append-only 강제 = DB role 분리 (`app_user` INSERT/SELECT only) + REVOKE — `42501` SQLState로 RED 증명 (ADR #25)
4. read 권한 = `Role` enum 재사용. ADMIN/AUDITOR 전체, MEMBER `actor_id=self` — A3 권한 시스템 비의존
5. enum 단일 진실 = 백엔드, ts는 mirror — CI lint는 후속(MVP는 수동 동기 + frontend `audit.test.ts` 계약으로 회귀 가드)
6. frontend는 `api.getAuditLogs`만 fetch 교체 — UI/테스트 변경 0, M12 표면 보존

### 잔여 accepted-deviation 5건 (후속 phase 추적)
| # | 항목 | 추적 phase |
|---|---|---|
| 1 | 월별 파티셔닝 자동화 (현재 단일 테이블, 파티션 SQL 함수만 docs/02 §9.4 주석 보존) | v1.x |
| 2 | 콜드 스토리지 아카이빙 cron | v1.x |
| 3 | `audit.exported` runtime emission (CSV export endpoint 자체가 v1.x — enum만 정의) | v1.x |
| 4 | `file.viewed` emission (`audit_level=strict` 폴더 도입 필요 → 폴더 테이블 부재) | A4 |
| 5 | `user.password.changed` (PW 변경 endpoint 자체가 v1.x) | v1.x |
| 6 | dev seed 60건 + `frontend/e2e/audit.e2e.ts` (admin 로그인 UI 의존) | A1 frontend 인증 + admin 라우팅 선행 |

### 핵심 함정 회고
- **REQUIRES_NEW + `@DataJpaTest` visibility**: 외부 트랜잭션 commit 전 INSERT는 별도 connection에서 미가시 → FK 23503 위반. 해결: seed를 `TransactionTemplate(REQUIRES_NEW)`로 즉시 commit (A2.1a `1196e11`)
- **PostgreSQL inet 호스트 표기**: `203.0.113.42/32` 입력 → `/32` mask 자동 생략. 가정 금지, 실제 SELECT로 검증 (`cf0be93`)
- **CI testLogging 기본 격차**: 로컬 lenient / CI strict (Hibernate Validator email RFC 5321 64자 local-part). `testLogging.exceptionFormat = FULL` 영구 적용으로 향후 회귀 디버깅 도움 (`f1ab7a6` → `b8cc4f2`)
- **AuthService 표준 이벤트 부재**: custom flow(`AuthenticationManager` 미사용)라 자동 publish 없음. Option D = `publishEvent(...)` 호출 명시 추가 + ADR #24 갱신본 (cross-cutting 신호, 비즈니스 로직 0줄)

### 다음 마일스톤 안내
- **A3 — 권한 매트릭스 + `PermissionService`**:
  - `@PreAuthorize` + 권한 시스템 백엔드 권위 (HANDOFF 미정의)
  - `effectivePermissionsCacheKey` 단순 문자열 → 권한 변경 trigger 기반 hash로 교체
  - `permission.granted/revoked/changed` audit 이벤트 emit 시점 (현재 enum만)
- **A4 — 폴더/파일 도메인 + `audit_level=strict` (file.viewed)**
- A3 진입 전 본 PR(#tbd) 머지 + master 동기화

---

## 2026-04-26 — 🏁 A1 마일스톤 종료 (Backend Authentication)

### 범위
A1.0 (User schema + JPA) → A1.1 (PasswordEncoder + DbUserDetailsService) → A1.2 (SecurityConfig 본 wiring + CSRF) → A1.3 (LoginController + in-memory lockout) → A1.4 (`/me` + `/logout` + SecurityContext gap fix) → A1.5 (통합 시나리오 + dev-docs 기반 audit) → **A1.6 (session timeout 정책 — must-fix #1 close)**.

### 회고
- **commits**: 7개 (308c041 / 0dd2d65 / 10a524b / 06b9238 / ca4e309 / c34e640 / A1.6 신규)
- **테스트**: **156 tests** (152 pass + 4 Docker SKIP, 0 fail) — 6 클래스 (`SecurityIntegrationTest` 5 / `LoginAttemptTrackerTest` 4 / `LoginControllerIntegrationTest` 8 / `AuthMeLogoutIntegrationTest` 5 / `AuthScenarioIntegrationTest` 1 / `SessionValidityFilterTest` 4) + slice + repository (152 합산)
- **production 클래스**: 16 (auth/ 7 + auth/dto/ 2 + config/ 1 + user/ 4 + common/error/ 2)
- **endpoint 4종**: `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`, `GET /api/auth/csrf` (docs/02 §7.4 매트릭스 충족)
- **ADR 등록**: #19 (BCrypt strength=12 + DelegatingPasswordEncoder), #20 (idle 30m sliding + absolute 8h + 5/15min lockout), #22 (`/me` shape — identity + role + permissionsCacheKey), #23 (in-memory lockout backing — MVP 단일 인스턴스). #20은 A1.6에서 full pass.
- **hidden gap fix 1건**: Spring Security 6 `SecurityContextHolderFilter` load-only 동작으로 `AuthService.login`의 명시 `saveContext` 누락 (A1.4 ca4e309에서 수정)
- **must-fix → resolved 1건**: must-fix #1 (세션 timeout 정책) — A1.6에서 `SessionValidityFilter` + `application.yml PT30M`로 close

### 잔여 accepted-deviation 3건 (후속 phase 추적)
| # | 항목 | 추적 phase |
|---|---|---|
| 1 | `audit_log` emission 미구현 — A1 plan에서 명시 deferred (`AuthService.login` 주석 `// (후속) audit insert`) | **A2** (audit + 권한 매트릭스 backbone) |
| 2 | `400 PASSWORD_CHANGE_REQUIRED` 분기 미구현 (현재 `mustChangePassword=true` flag만 응답에 포함) — ADR #21 | PW change endpoint phase (미배정) |
| 3 | `AuthScenarioIntegrationTest` 로컬 SKIP — Windows Docker 미가용. CI ubuntu-latest에서 실행 | PR push 후 `gh pr checks 1` 그린 = close 게이트 |

### 다음 마일스톤 안내 (A2)
- **A2 — Audit log backbone + 권한 매트릭스**:
  - `audit_log` 테이블 + append-only constraint (REVOKE UPDATE/DELETE — docs/03 §4 + CLAUDE.md §3 원칙 8)
  - `AuthService.login`의 `// (후속) audit insert`를 실제 emission으로 채움
  - `@PreAuthorize` + `PermissionService` (HANDOFF의 권한 매트릭스 백엔드 권위)
  - `effectivePermissionsCacheKey` 단순 문자열(`userId:role:v0`) → 권한 변경 trigger 기반 hash로 교체
- A2 진입 전 본 PR(#1) 머지 + master 동기화

---

## 2026-04-26 — A1.6 Session Timeout Policy (must-fix #1 close, TDD)

### 완료
- [A1.6] **`SessionValidityFilter`** — `OncePerRequestFilter`, `Clock` 주입(`Clock systemUTC` `@Bean` 신규). `req.getSession(false)` → 세션 부재면 pass-through. `issuedAt` attribute 부재(또는 Long 아님)면 pass-through (인증 전 요청). `(clock.millis() - issuedAt) >= 8h(ABSOLUTE_TTL_MS)` → `session.invalidate()` + `res.sendError(401)` + chain 차단.
- [A1.6] **`SecurityConfig` 변경** — `Clock` `@Bean`(systemUTC) + `addFilterAfter(sessionValidityFilter, SecurityContextHolderFilter.class)`. SecurityContext 로드 직후 absolute 만료 검사 → 만료 세션의 인증 컨텍스트가 다운스트림 인가 필터에 노출되는 것 회피.
- [A1.6] **`application.yml`** — `spring.session.timeout: PT8H` → `PT30M` (idle 진실 출처). 주석으로 분담 명시 (idle=Spring Session JDBC, absolute=SessionValidityFilter).
- [A1.6] **`SessionValidityFilterTest` 4건** — 단위 (Mockito + mutable `Clock`):
  1. 세션 없음 → pass-through, 응답 미터치
  2. 세션 있고 `issuedAt` 없음 → pass-through, invalidate 호출 안 됨
  3. 7h59m59s 경과 → pass-through, invalidate 호출 안 됨
  4. 정확히 8h 경과 → invalidate(1) + sendError(401) + chain 미호출
- [A1.6] **회귀** — 152 → **156 tests**, 0 fail, 0 err, 4 Docker SKIP 동일

### 핵심 결정 (filter SoT 정책)
- **idle 만료 진실 출처 = `application.yml` `spring.session.timeout: PT30M`** — Spring Session JDBC가 매 요청마다 `lastAccessedTime`을 갱신, 30분 무활동 시 자동 invalidate. 컨테이너 레벨에서 sliding 처리 → 별도 코드 불필요 (KISS).
- **absolute 만료 진실 출처 = `SessionValidityFilter`** — `AuthService.login`이 이미 set 중인 `issuedAt`(epoch millis) attribute + 8h 경계. yml만으로는 ADR #20 absolute 한도 강제 불가 → 본 필터가 must-fix #1의 정확 사유 close.
- **Clock 주입** — production은 `Clock.systemUTC()` `@Bean`, 테스트는 mutable `Clock`을 단위 테스트에서 직접 주입(필터 단독 생성). `@SpringBootTest` 통합 추가 안 함 — 8h 경계를 SpringBootTest로 검증하면 비용만 큼 (단위 4건으로 logic full coverage).
- **`OncePerRequestFilter` + `addFilterAfter(SecurityContextHolderFilter)` 위치** — SecurityContext 로드 직후, 인가 필터 이전. 만료 세션의 `Authentication`이 컨트롤러까지 도달하지 않음.

### 변경 파일
- `backend/src/main/java/com/ibizdrive/auth/SessionValidityFilter.java` (신규)
- `backend/src/main/java/com/ibizdrive/config/SecurityConfig.java` (Clock @Bean + filter wire + SecurityContextHolderFilter import)
- `backend/src/main/resources/application.yml` (timeout PT8H → PT30M + 주석)
- `backend/src/test/java/com/ibizdrive/auth/SessionValidityFilterTest.java` (신규)
- `dev/active/a1-auth-impl/a1-auth-impl-plan.md` (A1.6 phase 추가)
- `dev/active/a1-auth-impl/a1-auth-impl-audit.md` (must-fix #1 RESOLVED 갱신, 통계 +4 tests)

### 블로커
- 없음

### 다음 세션 컨텍스트
- A1 마일스톤 종료 commit (`chore(A1): close milestone`) + PR body draft 사용자 확인 → push → CI 그린 확인 → A2 진입

---

## 2026-04-26 — A1.5 통합 시나리오 + 마일스톤 audit

### 완료
- [A1.5] **`AuthScenarioIntegrationTest`** — `@SpringBootTest` + Testcontainers Postgres 15-alpine. 9-step 종합 시나리오: CSRF 발급 → 로그인(200) → `/me`(200) → 4회 wrong PW(401×4) → 5회째(401)+카운터 5 → 6회째(423) → mutable Clock 16분 진행 → 재시도 성공(200, lockout TTL 만료 lazy 해제) → logout(204) → 로그아웃 후 `/me`(401). `disabledWithoutDocker=true` (로컬 Windows SKIP / CI ubuntu 실행). `LoginAttemptTracker`를 `@TestConfiguration` `@Primary` 빈으로 mutable Clock 주입 — 운영 빈 영향 0.
- [A1.5] **회귀** — `./gradlew test` 152 tests (148 pass + 4 Docker SKIP, 0 fail). commit `c34e640`
- [A1.5] **dev-docs 기반 수동 audit** — `gsd-audit-milestone` 스킬은 GSD `.planning/ROADMAP.md` + `phases/*/VERIFICATION.md` 구조 전제. 본 프로젝트는 dev-docs(Superpowers) 구조 → 스킬 강행 시 모든 step fail. 사용자 승인으로 plan 목표 상태(line 30~38)를 DoD source로 채택, audit 산출물 `dev/active/a1-auth-impl/a1-auth-impl-audit.md` 작성.
- [A1.5] **audit 결과** — DoD 5/7 pass + must-fix #1 (세션 timeout 정책 미구현) + accepted-deviation 3건. ADR #19/22/23 ✅ / #20 partial. 사용자 결정 (B): A1.6에서 즉시 fix → A1.6에서 #20 full pass + must-fix #1 RESOLVED.

### 핵심 결정
- **dev-docs 기반 수동 audit** — `.planning/` GSD 구조 부재로 `gsd-audit-milestone` / `gsd-sdk` 호출 불가. CLAUDE.md ULTIMATE INVARIANT #10 ("중단 및 보고")에 따라 강행 대신 dev-docs 구조에 맞춘 수동 audit 채택. audit 산출물 위치 = `dev/active/a1-auth-impl/a1-auth-impl-audit.md` (plan/context/tasks와 동일 디렉토리, 마일스톤 archive 시 함께 이동).
- **anti-pattern 기록** — HANDOFF의 `next_action`을 검증 없이 그대로 실행하는 패턴 (이전 HANDOFF가 작성한 `gsd-audit-milestone A1`이 본 프로젝트에서 fail). resume 후 next_action 첫 단계는 도구/구조 가용성 검증부터 (CLAUDE.md #10).

### 블로커
- 없음 (must-fix #1은 사용자 결정으로 A1.6 즉시 fix 채택 → A1.6에서 close)

### 다음 세션 컨텍스트 (A1.6)
- 사용자 결정 (B) — must-fix #1 즉시 fix. SessionValidityFilter RED+GREEN+회귀 + audit.md must-fix #1 RESOLVED 갱신.

---

## 2026-04-26 — A1.4 `/api/auth/me` + `/api/auth/logout` (+ A1.3 SecurityContext gap fix)

### 완료
- [A1.4] **`GET /api/auth/me`** — `AuthController.me(@AuthenticationPrincipal IbizDriveUserDetails)`. 응답 shape는 `LoginResponse` 재사용 (docs/02 §7.4 line 847-868: `{user, departments, roles, effectivePermissionsCacheKey}` — login과 동일). 미인증 → `anyRequest().authenticated()` + `HttpStatusEntryPoint(401)` 자동 차단
- [A1.4] **`POST /api/auth/logout`** — `session.invalidate()` + `SecurityContextHolder.clearContext()` + `Set-Cookie SESSION=; Max-Age=0; Path=/; HttpOnly`. 응답 204 (docs/02 §7.4 line 836-845). CSRF 미제공 → CsrfFilter 403, 미인증 → 401
- [A1.4] **A1.3 hidden gap 수정** — Spring Security 6의 `SecurityContextHolderFilter`는 load-only (5.x의 `SecurityContextPersistenceFilter` auto-save 제거). 인증 메커니즘이 `SecurityContextRepository.saveContext`를 명시 호출해야 세션에 컨텍스트가 영속화됨. `AuthService.login`은 session attribute만 set하여 후속 `/me`가 항상 401이 되는 hidden bug. `DelegatingSecurityContextRepository` 빈(HttpSession + RequestAttribute 동시 저장) + `HttpSecurity.securityContext()` wire + `AuthService`에 `SecurityContextRepository` 주입하여 `changeSessionId()` 직후 `saveContext` 호출
- [A1.4] **`AuthMeLogoutIntegrationTest` 5건** — `@WebMvcTest` slice. `/me` 인증/미인증, `/logout` 인증+CSRF / 인증+CSRF 미제공 / 미인증+CSRF (4 클래스, 22 테스트 PASS, 1m33s)
- [A1.4] commit `feat(A1.4): /api/auth/me + /api/auth/logout` (ca4e309)

### 핵심 결정
- **`/me`는 `LoginResponse` 재사용 (`MeResponse` 미생성)** — docs/02 §7.4 line 847-868의 `/me` 응답 shape는 `LoginResponse`(`{user, departments, roles, effectivePermissionsCacheKey}`)와 완전 동일. 별도 DTO 생성은 YAGNI 위반 (CLAUDE.md ULTIMATE INVARIANTS 원칙 3 — 기존 구조 우선). 추후 `/me` 전용 필드 도입 시점에 분리
- **`DelegatingSecurityContextRepository(HttpSession + RequestAttribute)`** — HttpSession은 영속, RequestAttribute는 단일 요청 캐시. 같은 요청 내 후속 필터/핸들러가 동일 컨텍스트를 일관되게 본다. `AuthService.login`이 `changeSessionId()` 직후 `saveContext(req, res)` 호출하여 새 세션에 컨텍스트 저장
- **logout: `session.invalidate` + `clearContext` + `Cookie SESSION; Max-Age=0`** — `clearContext`는 현재 요청 thread-local 정리. `Set-Cookie`는 클라이언트 브라우저 SESSION 쿠키 즉시 만료. `anyRequest().authenticated()` 가드로 미인증→401, CsrfFilter가 CSRF 미제공→403 자동 처리

### 다음 세션 컨텍스트 (A1.5)
- **A1.5** — `AuthScenarioIntegrationTest` `@SpringBootTest` + Testcontainers Postgres 15-alpine 1건 (`disabledWithoutDocker=true`, 로컬 SKIP / CI ubuntu 실행). CSRF 발급→로그인→/me→5회 wrong PW(401×5)→6회째=423→Clock 16분 진행→재시도 성공→logout(204)→/me=401. `LoginAttemptTracker`는 `@TestConfiguration` `@Primary` 빈으로 mutable Clock 주입
- 마일스톤 종료: `gsd-audit-milestone A1` + `progress.md` 마일스톤 블록 + commit + push + `gh pr checks` 그린

---

## 2026-04-26 — A1.3 LoginController + in-memory lockout (ADR #23)

### 완료
- [A1.3] **`POST /api/auth/login`** — `AuthController` + `AuthService.login` (`@Transactional`). 성공 시 `last_login_at` UPDATE + `changeSessionId()` (session fixation 방어). 실패 시 5/15min lockout 카운트
- [A1.3] **`LoginAttemptTracker`** — in-memory `ConcurrentHashMap<String, Attempt>`, key=lowercased email, 5회 실패 → 15분 잠금. `Clock` 주입 (테스트 시간 제어). 만료는 lazy 검증 (`isLocked` 호출 시점에 시계 기반 판정)
- [A1.3] **timing-safe BCrypt verify** — `@PostConstruct`에서 `passwordEncoder.encode()`로 dummy hash 동적 생성. 미존재 user / `is_active=false` / `locked_at != null` 모두 dummy verify 호출 → 실제 verify와 동일 시간. 응답은 INVALID_CREDENTIALS (계정 상태 누설 금지, docs/03 §2.3 enumeration 방지)
- [A1.3] **flat error shape** — `ErrorResponse(code, reason, retryAfterSec)` + `JsonInclude.NON_NULL`. `AuthExceptionHandler(@RestControllerAdvice)` → InvalidCredentialsException → 401, AccountLockedException → 423 + retryAfterSec. docs/02 §7.4 인증 specific 응답 (일반 envelope §7.2와 별개)
- [A1.3] **`User.recordLoginAt(OffsetDateTime)`** entity 메서드 — `last_login_at` setter (V2 컬럼)
- [A1.3] **`LoginAttemptTrackerTest` 4건 + `LoginControllerIntegrationTest` 8건** — 모두 PASS. @WebMvcTest slice. tracker singleton 격리는 `@BeforeEach`에서 `recordSuccess(테스트 키들)` 호출
- [A1.3] **ADR #23 docs/00 §5 등록** — lockout backing = in-memory ConcurrentHashMap (MVP 단일 인스턴스 가정). 다중 인스턴스/Redis 도입 시 `LoginAttemptTracker` interface 교체

### 핵심 결정
- **timing-safe dummy hash 동적 생성** — 정적 상수는 형식 오류 시 BCrypt iterations 미실행 → timing leak 위험. 부팅 +200ms 수용
- **비활성·관리자 잠금도 INVALID_CREDENTIALS로 매핑** — docs/03 §2.3 enumeration 방지. 잠금 누적은 별개로 ACCOUNT_LOCKED + retryAfterSec 노출
- **@Transactional은 AuthService.login에 적용** — last_login_at UPDATE + (후속) audit insert 단일 단위, docs/02 §7.4 TX=REQUIRED 충족
- **컨텍스트 9% 한계** — 본 세션은 commit만 수행. dev sync (context.md/tasks.md SESSION PROGRESS 갱신)는 다음 세션 시작 시 dev-docs-update로 처리

### 다음 세션 컨텍스트 (A1.4 ~ A1.5)
- **A1.4** — `GET /api/auth/me` (docs/02 §7.4 line 847-868: `{user, departments, roles, effectivePermissionsCacheKey}`. departments는 빈 배열 stub, kind='human' 하드코딩, roles=`[role]`, cacheKey=`userId:role:v0`) + `POST /api/auth/logout` (`session.invalidate()` + `Set-Cookie SESSION=; Max-Age=0`). 모두 `anyRequest authenticated`로 SecurityConfig 매처 변경 불필요
- **A1.5** — Testcontainers Postgres @SpringBootTest 종합 시나리오 1건 + `gsd-audit-milestone A1` + PR 머지

### 진행 정책
- 자율 모드 유지. 사용자 큐: A1.4 종료 시점 컨텍스트 65% 초과 시 자동 pause-work, 아니면 A1.5까지 이어감
- 다음 세션 첫 작업: dev-docs-update (이번 세션의 A1.3 SESSION PROGRESS 동기화) → A1.4 진입 (TDD RED 5건부터)

### 블로커
- 없음

---

## 2026-04-26 — A1.2 SecurityConfig 본 wiring (TDD, dev + Superpowers 첫 적용)

### 완료
- [A1.2] **`SecurityConfig` 본 wiring** — httpBasic/formLogin/logout 모두 disable, custom AuthController(A1.3+) 자리 마련. 매처 분리: `/api/health` + `/api/auth/csrf` + `/api/auth/login` permitAll, anyRequest authenticated. `HttpStatusEntryPoint(401)` — SPA용 (redirect 대신 401, docs/03 §2.4)
- [A1.2] **`CsrfTokenRepository` bean 추출** — `CookieCsrfTokenRepository.withHttpOnlyFalse()`. `CsrfTokenController`에서 `saveToken()` 명시 호출하여 deferred 모드 우회
- [A1.2] **`CsrfTokenController`** (`GET /api/auth/csrf`) — permitAll, `XSRF-TOKEN` cookie + `{ csrfToken }` body 동시 반환. Spring Security 6 deferred CSRF 함정(`getToken()`만으로는 cookie 자동 발급 보장 안 됨) 해결 — `repo.saveToken(token, req, res)` 명시 호출
- [A1.2] **`SecurityIntegrationTest` 5건** (@WebMvcTest slice, DB 무관) — getCsrf+cookie / POST without CSRF→403 / POST valid CSRF→401 / GET /me→401 / GET /health→200. **모두 PASS**
- [A1.2] **dev/active/a1-auth-impl/{plan,context,tasks}.md** + dev/process/{session}.md (dev 스킬 첫 bootstrap)

### 핵심 결정
- **CSRF plain handler** (`CsrfTokenRequestAttributeHandler`) — XOR mask 비활성화. docs/02 §7.1 평문 토큰 계약과 일치 (cookie ↔ header 단순 비교)
- **deferred CSRF 우회** — Spring Security 6에서 GET 응답에 cookie 자동 발급은 보장 안 됨 → controller가 `csrfRepo.saveToken()`을 명시 호출
- **로컬 logout disable** — A1.4에서 자체 endpoint로 처리 (Spring 기본 `LogoutFilter`는 form 기반)
- **A1.5 재정의** — HANDOFF.json의 권한 매트릭스 백엔드 권위는 별도 phase로 분리, A1.5 = 통합 시나리오 + 마일스톤 종료 (사용자 자율 결정)

### 다음 세션 컨텍스트 (A1.3 ~ A1.5)
- **A1.3** — LoginController + in-memory `LoginAttemptTracker` (5회 실패/15분 lockout, ConcurrentHashMap, Clock 주입). **ADR #23 docs/00 §5 추가 필요** (lockout backing store). timing attack 회피 (미존재 user에도 dummy BCrypt verify). `User.recordLoginAt(OffsetDateTime)` setter 추가
- **A1.4** — `GET /me` (ADR #22 응답 — `effectivePermissionsCacheKey = "userId:role:v0"` 임시) + `POST /logout` (HttpServletRequest.session.invalidate())
- **A1.5** — Testcontainers Postgres @SpringBootTest 종합 시나리오 1건 + gsd-audit-milestone

### 진행 정책
- 새 환경: dev(4) + Superpowers(12, TDD 강제) + GSD context-only. 트리거 경합 0
- 자율 모드 유지 — A1.3~A1.5는 새 세션(컨텍스트 클린)에서 동일 정책으로 진입

### 블로커
- 없음. A1.3 진입 시 ADR #23만 기록하면 됨

---

### 완료 (옵션 3 +α — 하이브리드 사이클)
- [A] **backend/ Spring Boot 3.3.4 + Java 21 scaffold** — `build.gradle.kts` (Kotlin DSL, toolchain 21), `settings.gradle.kts`, `gradle.properties`, `.gitignore`. 의존성: spring-boot-starter-web/security/data-jpa/validation, **spring-session-jdbc** (Redis 아님, ADR #12), postgresql, flyway-core + flyway-database-postgresql, software.amazon.awssdk:s3:2.28.16, jackson-databind. test workingDir = `projectDir`로 fixtures 상대경로(`../docs/`) 안정화
- [A] **`IbizDriveApplication.java`** — `@SpringBootApplication` 메인. **`HealthController`** `GET /api/health` → `{"status":"ok"}` (보안 설정 검증용)
- [A] **`SecurityConfig` skeleton** — `CookieCsrfTokenRepository.withHttpOnlyFalse()` (XSRF-TOKEN 쿠키 ↔ X-CSRF-Token 헤더, ADR #11), `/api/health` permitAll, 그 외 authenticated, httpBasic placeholder (A1에서 form/SSO 교체)
- [A] **`CorsConfig`** — `allowCredentials=true`, allowedOrigins from `${ibizdrive.cors.allowed-origins}`, exposed: `Tus-Resumable`/`Upload-Offset`/`Location`/`X-Request-Id`/`X-RateLimit-Remaining`. allowed: `Content-Type`/`X-CSRF-Token`/`X-Request-Id`/Tus 헤더 4종
- [A] **`application.yml`** — Spring Session JDBC (table-name `SPRING_SESSION`, **initialize-schema: never** = Flyway가 schema 관리), datasource localhost:5432/ibizdrive, S3 → MinIO localhost:9000, multipart **disabled** (tus 사용, ADR #13), session timeout `PT8H`, cookie `http-only=true`/`secure=false (dev)`/`same-site=lax`/name=`SESSION`
- [A] **`db/migration/V1__init.sql`** — Spring Session JDBC 스키마(`SPRING_SESSION` + `SPRING_SESSION_ATTRIBUTES`, 인덱스 IX1/IX2/IX3, ON DELETE CASCADE) + `users` stub(id UUID PK, email/display_name, password_hash nullable for SSO, deleted_at, partial unique index on `lower(email) WHERE deleted_at IS NULL`). 도메인 테이블은 V2+
- [A] **`docker-compose.yml`** — postgres:15-alpine + minio (RELEASE.2024-10-13) + minio-init 원샷(버킷 자동 생성). **Redis 없음** (JDBC 세션 + in-process SSE). healthcheck 포함
- [A] **A0: backend `NormalizeUtil.java`** — 7-step pipeline 완전 구현. `normalizeFileName` (1-2-3-6-7), `normalizedNameForDedup` (1-2-3-5-6-7, Locale.ROOT), `normalizeForSearch` (1-2-3-5-4-6, collapse). 제어문자 처리: NUL→throw / C0→space / DEL/C1/ZWSP(0x200B-200F)/BIDI(0x202A-202E)/WJ(0x2060)/BOM(0xFEFF) drop. 공백 통일: NBSP/U+2000-200A/202F/205F/3000→space. 검증: empty/length 255/`/`·`\\` 금지/trailing dot/예약어(CON/PRN/AUX/NUL/COM1-9/LPT1-9, base name 기준 case-insensitive). `NormalizationException` (code 필드, fixtures errorCodes 일치)
- [A] **A0: backend `NormalizeUtilTest`** — JUnit `@TestFactory` 동적 테스트, Jackson으로 `../docs/normalize-fixtures.json` 로드(IDE/Gradle 둘 다 동작하도록 fallback 경로 3종), 38 fixtures × 3 함수 = 114 dynamic test
- [A] **A0: frontend `src/lib/normalize.ts` 정식 작성** — backend `NormalizeUtil.java` 1:1 미러. 같은 7-step, 같은 에러 상수 6종, `NormalizationError extends Error` (code 필드)
- [A] **A0: frontend `src/lib/normalize.test.ts`** — Vitest, `readFileSync(resolve(__dirname, '../../../docs/normalize-fixtures.json'))`로 fixtures 로드, 38×3 = **114 tests PASS** (54ms)
- [A] **`.github/workflows/ci.yml`** — frontend(Node 20 + npm ci + typecheck/lint/test) + backend(Temurin 21 + gradle test) 두 잡. ADR #16 게이트: 어느 한쪽이든 fixtures mismatch면 머지 차단
- [A] **검증** — frontend: typecheck PASS · lint PASS · normalize 114 tests PASS. backend: 로컬 JDK 없어 컴파일 미실행 → CI에서 검증

### 핵심 구현 결정
- **Spring Session JDBC, Redis 미도입** — 사용자 명시(ADR #12). docker-compose에서 Redis 제거, application.yml에서 `store-type: jdbc` + `initialize-schema: never` (Flyway가 권위)
- **fixtures workingDir 처리** — Gradle test는 `workingDir = projectDir`(=`backend/`)로 고정, JUnit 테스트는 `../docs/normalize-fixtures.json` + IDE 케이스 대비 2개 fallback. 단일 진실 출처가 빌드 도구 따라 깨지지 않도록
- **NUL은 step 2에서 throw, step 7 검증 전 차단** — fixtures `control_001` 입력 `"a\u0000b.txt"` → ERR_NUL_CHAR. 빈 문자열로 만든 뒤 ERR_EMPTY로 떨어지면 디버깅 불편
- **JS `for...of` + `codePointAt`** — surrogate pair 안전. 공백 통일/제어문자 strip 모두 동일 패턴, Java `Character.charCount` 루프와 1:1 대응
- **터키어 İ 케이스(`case_002`)는 JS `toLowerCase()` 기본 동작과 Java `toLowerCase(Locale.ROOT)` 결과 일치** — `i̇`(i + U+0307). fixtures가 양쪽 cross-validation으로 보장

### 다음 세션 컨텍스트
- **A1 (인증) 진입 — 수동 모드 유지** — Spring Security `UserDetailsService`, BCrypt, login form, CSRF Cookie 전달, 8h 세션, audit 이벤트(LOGIN_SUCCESS/FAIL/LOGOUT). users 테이블 V2 확장(role/locked_at/last_login_at)
- **CI 미검증 영역** — backend Gradle test가 실제 GitHub Actions에서 통과하는지 확인 전. 첫 push 시 워크플로 실패하면 V1__init.sql/Flyway 의존성 정렬 점검 필요
- **로컬 개발 가이드 미작성** — `docker-compose up -d postgres minio minio-init` → `cd backend && ./gradlew bootRun`. README 또는 docs/00 §6에 추가 검토
- **A1.5 권한 매트릭스 코드화** — docs/03 §3 PermissionEnum 9종을 `com.ibizdrive.security.Permission` enum + Preset 매트릭스로 옮기고 `@PreAuthorize` SpEL helper 작성

### 블로커
- 없음. 사용자가 A1 spec 승인하면 진행

---

## 2026-04-25 — Track A 큐 #3: 정규화 spec + fixtures + SSE enum + PURGE 권한

### 완료 (큐 #3 — docs only)
- [A] **docs/00 §5 ADR 7건 추가** (#11~#17): Spring Boot, 쿠키 세션, tus-java-server, SSE, .env build-time, 정규화 fixtures 공유, 권한 매트릭스 백엔드 권위. ADR #7/#8은 §5.1 Superseded 섹션에 standard ADR 패턴으로 보존
- [A] **docs/00 §1.3 스택 갱신** — TBD → Spring Boot 3.x + Java 21 + 부속 스택 명시
- [A] **docs/00 §4.4 백엔드 마일스톤(A0~A7) 신설** — A6→A1.5 흡수, M5.1→A4 흡수, M12→A4 후반 통합
- [A] **docs/02 §7 endpoint 매트릭스 전면 재작성** — Auth/Folders/Files/Upload(tus)/Search/Shares/Permissions/Trash/Admin/SSE 그룹화. Guard(@PreAuthorize)/TX(REQUIRED + FOR UPDATE)/Norm(NormalizeUtil 적용 지점)/SoftDel(WHERE deleted_at)/Errors 5개 컬럼. tus finalize 7-step TX 상세 명시. 인증 헤더 JWT → 쿠키 세션 + CSRF double-submit 갱신
- [A] **docs/02 §3 정규화 spec 정식 명세화** — 7-step pipeline (NFC → 제어문자 strip → 공백 통일 → collapse → lowercase → trim → validate). 함수 분리표(filename/dedup/search), 단계별 제어문자 표(NUL/C0-C1/ZWSP/BIDI/BOM), 길이/금지문자/예약어/trailing dot 검증 규칙
- [A] **docs/normalize-fixtures.json 신규** (38 케이스): nfc(4) / whitespace(8) / control(4) / case(3) / extension(2) / forbidden(3) / reserved(4) / length(3) / dot(2) / unicode(3) / search(2). errorCodes enum 6종. Vitest+JUnit 양쪽 검증 게이트 (CLAUDE.md §3 원칙 11)
- [A] **docs/01 §15 SSE 본문 재작성** — ADR #14에 의해 폴링 폐기, MVP부터 SSE. SseEventType enum 16종(FILE 7 / FOLDER 6 / PERMISSION 3), useRealtimeSync 훅 구현, 이벤트→queryKeys 무효화 매트릭스, 연결 정책(자동 재연결/heartbeat/다중 폴더 구독)
- [A] **docs/02 §7.13.1 SSE enum 동기화** — FOLDER_UPDATED 분리(RENAMED), PERMISSION_GRANTED/REVOKED 추가, FILE_VERSION_CREATED/FOLDER_PURGED 신설
- [A] **docs/03 §3 권한 매트릭스 정식 명세화** — 권한 enum 9종(READ/UPLOAD/EDIT/MOVE/DOWNLOAD/DELETE/SHARE/PERMISSION_ADMIN/**PURGE**), Preset×권한 매트릭스, 시스템 ROLE(MEMBER/AUDITOR/ADMIN), 권한 상속(deny 우선 재귀 CTE), `@PreAuthorize` 패턴 예시, 403 응답 포맷
- [A] **검증** — 코드 변경 없음 → typecheck PASS · lint PASS · 190 tests PASS (회귀 없음)

### 핵심 설계 결정
- **PURGE는 ROLE ADMIN 전용** — 노드 admin preset에도 부여하지 않음. 노드 단위 권한 위임이 영구 삭제로 번지지 않도록 이중 안전장치
- **fixtures 단일 진실 출처** — `docs/normalize-fixtures.json`을 frontend(Vitest) + backend(JUnit) 양쪽이 로드, CI 게이트로 드리프트 차단. 38 케이스로 NFC/공백/제어/대소문자/예약어/길이/다국어 커버
- **터키어 İ는 Locale.ROOT lowercase** — `i̇`(i + U+0307 combining dot above) 결과. JS `toLowerCase()`와 Java `toLowerCase(Locale.ROOT)` 동일 동작 (fixtures `case_002`)
- **SSE 낙관적 setQueriesData 안 씀** — race 회피, 단일 무효화 경로(`invalidateQueries`)로 일관 처리
- **FILE_MOVED는 source+target 양쪽 fan-out** — `scope.folderIds` 배열에 두 폴더 ID 동시 포함, 클라가 양쪽 모두 무효화

### 다음 세션 컨텍스트
- **사용자 결정 대기** — 옵션 1: 로컬 인프라(docker-compose + JDK 21) → #4~#12 자율 / 옵션 2: 백엔드 팀 인계 / 옵션 3: 하이브리드(#4 scaffold만)
- **fixtures 누락 영역** — 현재 38 케이스에서 다루지 않은 영역: combining mark RTL 텍스트 시퀀스, surrogate pair, 일본어 가나/탁점 분리. 백엔드 구현 단계(A0)에서 NormalizeUtil 작성하며 추가 케이스 발견 시 fixtures 확장
- **A0 진입 시 검증** — `frontend/src/lib/normalize.ts` 신규 작성 + 기존 fixtures 로드 테스트. 현 frontend는 `normalizeFileName` 등이 별도 라이브러리에 없음 (mock backend가 모든 정규화 처리) — A0에서 분리 필요

### 블로커
- 없음 (큐 #4 진입은 사용자 결정 게이트)

---

## 2026-04-25 — Track H: docs/03 §1·§2 보강 (위협 모델 + 인증 흐름)

### 완료
- [H] **docs/03 §1 위협 모델** — Assets 표(A1~A7), Trust Boundary 다이어그램, **STRIDE 매트릭스**(Spoofing/Tampering/Repudiation/Info Disclosure/DoS/Elevation 카테고리별 자산·위협·완화책 표), 잔여 위험(out of scope) 명시
- [H] **docs/03 §2 인증** — 인증 방식(SSO/자체+MFA), 토큰 모델(access/refresh/sessionId 표), **시퀀스 다이어그램 3종**(로그인 SSO 콜백 / Access 만료+Refresh 회전 / 로그아웃), 비활성 정책 표, 서비스 계정 정책, audit 이벤트 동기화 메모
- [H] 백엔드 스택 미정 부분은 **"TBD: A 트랙에서 확정"** 으로 명시 (RateLimiter 구현체 / NestJS vs Spring / users.external_id / API 키 회전 주기)
- [H] **검증** — 코드 변경 없음 → typecheck PASS · lint PASS · 190 tests PASS (회귀 없음 확인)

### 핵심 설계 결정
- **클레임 최소화** — JWT는 sub/role/exp/iat/sessionId만, 권한 평가는 항상 DB. 토큰 단독 신뢰 금지(Spoofing 완화)
- **Refresh 1회용 회전 + replay 감지** — 이미 사용된 refresh가 다시 들어오면 세션 전체 강제 종료 + audit
- **app role과 audit role 분리** — DB 레벨 REVOKE UPDATE/DELETE on audit_log (CLAUDE.md §3 원칙 8과 일치)
- **STRIDE를 표 중심으로 정리** — 자산 ID(A1~A7) 참조 가능, 향후 §3 권한 매트릭스/§4 감사 정책에서 cross-link 용이

### 다음 세션 컨텍스트
- **A 트랙 (백엔드 합류)** — TBD 항목 채우기: NestJS or Spring 결정 → users.external_id 매핑 / RateLimiter 구현체 / API 키 회전 정책 / sessions 테이블 스키마
- **§3 권한 매트릭스** — 본 위협 모델의 "프론트 권한 우회"·"권한 상속 버그" 위협을 실제 엔드포인트×권한 매트릭스로 매핑 (현재 §3.1 preset만 존재)
- **§4 감사 정책 보강** — `user.session.revoked` 이벤트 추가 + audit_level=strict 폴더 정의 가이드

### 블로커
- 없음 (A 트랙 합류 전까지는 TBD 표기로 지연 가능)

---

## 2026-04-25 — Track G: BulkActionBar "이름 변경" 버튼

### 완료
- [G] **BulkActionBar 이름 변경 버튼** — 단일 선택 시만 활성, 클릭 시 F2와 동일한 `openRename(id, name)` 호출
- [G] **단일 항목 name lookup** — `useFilesInFolder(folderId, sort, dir)` 캐시에서 단일 선택 항목의 이름 조회. `useSortParams`로 현재 정렬 키 사용 → FileTable과 동일 캐시 슬롯 hit
- [G] **disabled UX** — 다중 선택 시 disabled + `title="단일 선택 시 사용 가능"` tooltip + aria-disabled
- [G] **테스트 4건 신규** — 단일 활성+다이얼로그 오픈 / 다중 비활성+tooltip / cache miss 비활성 / 폴더 단일 활성 (정책)
- [G] **검증** — typecheck PASS · lint PASS · 190 tests PASS (186→+4)

### 핵심 설계 결정
- **정책: count===1 활성, 폴더/파일 구분 없음** — RenameDialog와 백엔드(`api.renameFile`/`renameFile.test`)가 양쪽을 모두 지원하므로 BulkActionBar에서 추가로 막을 이유 없음. 추후 권한 모델(03 §3) 확정 시 `usePermission().edit` 게이트 외 추가 분기 필요 여부 재검토
- **Cache miss 시 안전 비활성** — items=undefined(로딩 중)일 때 `singleItem`이 없으면 disabled로 폴백. 로딩 끝나면 자동으로 활성화
- **`can.edit` 게이트만 사용** — 권한 없는 사용자에게는 버튼 자체 미노출 (BulkActionBar 다른 액션과 동일 패턴)

### 다음 세션 컨텍스트
- **권한 매트릭스(03 §3) 확정 후** — `can.edit` semantic 재검토 (folder rename은 `move` 권한? `edit`?)
- **Rename 외 단일 액션 추가 시** — 같은 패턴(`useFilesInFolder` lookup + count===1 게이트)으로 확장. 다수 단일 액션이면 `useSelectedSingleItem()` 헬퍼 추출 검토

### 블로커
- 없음

---

## 2026-04-25 — Track F: 다크 모드 토글 (TopBar Sun/Moon)

### 완료
- [F] **lib/theme.ts** — `THEME_STORAGE_KEY`/`getStoredTheme`/`getSystemTheme`/`getInitialTheme`/`applyTheme`/`persistTheme`. SSR 안전(window/document 가드), localStorage 에러 swallow (11 tests)
- [F] **hooks/useTheme** — mount effect로 SSR 동기화, toggle/setTheme/theme 반환
- [F] **components/topbar/ThemeToggle** — `lucide-react` Sun/Moon 아이콘, button + aria-pressed + aria-label, 키보드(Enter/Space) 동작 (5 tests)
- [F] **components/topbar/TopBar** — main 상단 banner, 우측 정렬에 ThemeToggle
- [F] **(explorer)/layout** — TopBar 마운트
- [F] **app/layout** — FOUC 방지 inline script (hydration 전 동기 [data-theme] 적용)
- [F] **`lucide-react` 추가** — frontend/package.json dependencies
- [F] **검증** — typecheck PASS · lint PASS · 186 tests PASS (170→+16)

### 핵심 설계 결정
- **`[data-theme="dark"]` on `<html>`** — globals.css가 이미 :root와 [data-theme="dark"]를 정의해 둠. JS는 attribute 토글만 담당
- **localStorage 우선 + prefers-color-scheme fallback** — 사용자가 한 번이라도 토글하면 그 선택을 영속, 그 전까지는 시스템 설정 따라감
- **FOUC 방지 inline script** — Next.js App Router는 RSC 첫 페인트 시점에 React 마운트 전 단계가 있음. `<head>`의 `dangerouslySetInnerHTML` 동기 스크립트로 해결 (theme.ts 로직과 동일 규칙 inline 복제)
- **role=switch 대신 button + aria-pressed** — eslint jsx-a11y 가 role=switch에 aria-checked 요구. button + aria-pressed가 토글 패턴에서 더 보편 (WAI-ARIA Button pattern)
- **lucide-react 도입** — 기존 의존성에 아이콘 라이브러리 없었음. 향후 다른 곳에서도 활용 가능

### 다음 세션 컨텍스트
- **시스템 prefers-color-scheme 변화 감지 미구현** — 사용자가 OS에서 라이트→다크 전환 시 자동 동기화 X. 필요 시 `matchMedia.addEventListener('change', ...)`를 useTheme에 추가
- **다크 모드 시각 검수 필요** — globals.css의 [data-theme="dark"] 토큰이 실제 컴포넌트(FileTable/Breadcrumb/UploadOverlay 등)에서 의도대로 보이는지 e2e 또는 수동 QA 필요
- **/admin 페이지에도 TopBar 적용?** — 현재는 (explorer)/layout만. admin/layout.tsx는 별도 헤더 — 통일 시 공용 컴포넌트로 승격 검토

### 블로커
- 없음

---

## 2026-04-25 — Track D: e2e Playwright 도입

### 완료
- [M_e2e] **@playwright/test 도입** + `playwright.config.ts` (chromium만, webServer 자동 기동, baseURL 3000, retries CI=2)
- [M_e2e] **e2e/routing.e2e.ts** — / → /files 리다이렉트 / 사이드바·breadcrumb / FolderTree 클릭 → URL+breadcrumb 갱신 / `/` 키 → app:focus-search 디스패치 (4 specs)
- [M_e2e] **e2e/move.e2e.ts** — BulkActionBar 이동 다이얼로그 → 대상 선택 → 토스트 / source 폴더 disabled (2 specs)
- [M_e2e] **e2e/trash.e2e.ts** — BulkActionBar 휴지통으로 → 토스트 + bar 사라짐 (1 spec)
- [M_e2e] **e2e/tsconfig.json** — main tsconfig에서 e2e/ 제외 + Playwright types 별도 설정
- [M_e2e] **package.json** — `test:e2e`, `test:e2e:ui` 스크립트 추가
- [M_e2e] **.gitignore** — playwright-report / test-results / playwright/.cache

### 핵심 설계 결정
- **chromium only (v1.0)** — 키바인딩/DnD 안정성 우선, firefox/webkit은 프로덕션 안정화 후
- **DnD 시나리오 → 다이얼로그 경로로 대체** — dnd-kit PointerSensor activationConstraint(distance:5px) + Playwright mouse 시퀀싱이 flaky. DnD 통합은 후속 (E2E_dnd_followup) — `page.mouse.move`를 50ms 단위 다단계 + `force: true` 필요
- **검색 input UI 미구현** — '/' 키 디스패치까지만 검증. 검색 input 도입 (M_search) 후 input.focus 검증 추가
- **휴지통 Undo 미구현** — soft delete + 토스트만. 복원 흐름은 M_trash 후속

### 다음 세션 컨텍스트
- **브라우저 미설치** — 첫 실행 전 `npx playwright install chromium` 필요. CI 통합 시 `actions/setup-node` 후 install step 추가
- **CI 통합은 후속** — `.github/workflows/e2e.yml`에서 webServer reuseExistingServer:false + retries 2
- **mock 데이터 기반** — MOCK_FILES/MOCK_TREE를 변경하면 e2e 셀렉터(영업팀/인사팀/내 드라이브) 동기화 필요
- **vitest 단위 테스트와 분리** — testMatch는 `*.e2e.ts`, vitest는 `*.test.ts(x)` — 충돌 없음

### 블로커
- 검색 input UI / 휴지통 Undo 미구현 — 시나리오 부분 적용. 후속 마일스톤에서 보강 예정

---

## 2026-04-25 — Track B: M12 감사 로그 페이지 (mock)

### 완료
- [M12] **types/audit.ts** — `AuditEventType` (docs/03 §4.1 mirror, audit.exported 포함) + `AuditLogEntry` + `AuditLogFilters` + `AuditLogPage`
- [M12] **api.getAuditLogs** — filters/page/pageSize → 정렬(occurredAt desc) + 60-entry mock 데이터 (6 tests)
- [M12] **lib/auditCsv.ts** — RFC 4180 quoting + `toAuditCsvBlob` (UTF-8 BOM, text/csv MIME) (6 tests)
- [M12] **hooks/useAuditLogs** — useQuery wrapper, queryKey에 filters/page 포함 → 자동 재요청 (2 tests)
- [M12] **/admin/audit/logs** — Filters + Table + Pagination + CSV export 페이지 (docs/04 §7)
- [M12] **components/audit** — AuditFilters (4 tests), AuditTable (6 tests, aria-rowcount/rowindex 포함), AuditPagination
- [M12] **/admin/layout.tsx** — 관리자 헤더 (감사 로그 링크)
- [M12] **docs/03 §4.1** — 클라이언트 mirror 표시 + audit.exported 추가
- [M12] **검증** — typecheck PASS · lint PASS · 170 tests PASS (M10 기준 146 → +24 신규)

### 핵심 설계 결정
- **mock 우선, 백엔드 분리 (A 트랙)** — getAuditLogs는 클라이언트 mock. 실제 연결 시 audit.exported 서버 기록 필요 (docs/04 §7.2)
- **CSV는 현재 페이지만 export (mock)** — 백엔드 연결 후 전체 필터 결과 서버 스트리밍으로 교체
- **필터 변경 시 자동 setPage(1)** — UX 일관성. 페이지네이션 키에 filters 포함되어 자동 refetch
- **UTF-8 BOM Blob** — Excel에서 한글 깨짐 방지. BOM 자체는 toAuditCsv 결과에 prefix
- **resourceType=null 허용** — 시스템 이벤트(system.backup.completed) 처리. UI는 `[type]` 또는 dash로 표시
- **즉시 반영 폼 (Apply 버튼 없음)** — onChange로 즉시 부모 setState. 디바운스는 actorQuery 입력 압박 시 부모에서 추가

### 다음 세션 컨텍스트
- **백엔드 연결 (A 트랙)** — `api.getAuditLogs`를 fetch로 교체 + `audit.exported` 서버 기록 추가
- **상세 뷰 (docs/04 §7.3)** — before/after diff 표시 + 같은 세션 이벤트 연결은 v1.x
- **IP/리소스 필터 (docs/04 §7.1)** — mock 범위 외. 백엔드 연결 후 UI 추가
- **권한 체크** — 감사 로그 접근은 admin role 필수. ProtectedRoute는 §3 권한 매트릭스 작성 후
- **/admin 다른 페이지** — dashboard / users / departments 등 docs/04 §2 라우트 v1.x

### 블로커
- 없음

---

## 2026-04-25 — Track C: 회고/리팩토링 (sonner 분리 + 무효화 헬퍼 + mock 공통화)

### 완료
- [C-1] **sonner 도입** — providers.tsx `<Toaster position="bottom-right" richColors />`
- [C-1] **hooks 토스트 분리** — `useDeleteBulk`/`useMoveBulk`/`useRenameFile`은 결과만 반환, 토스트는 호출부 (`Options.onSuccess/onError`)
- [C-1] **호출부 마이그레이션** — BulkActionBar / MoveFolderDialog / DndProvider / RenameDialog 모두 hook-level 콜백으로 토스트 호출
- [C-2] **lib/queryKeys.ts invalidations** — `afterFilesMoved` / `afterRename` / `afterDelete` 헬퍼. 무효화 매트릭스를 한 곳으로 집결 (3 hooks가 같은 룰 공유)
- [C-2] **filesListPrefix(folderId)** — sort/dir 변종 전체 prefix 매칭용 키 추가 (직접 단일 read는 filesInFolder)
- [C-3] **test/setup.ts** — sonner 글로벌 vi.mock (Toaster=null + toast methods=vi.fn)
- [C-3] **test/mocks/sonner.ts** — `toastSpy(method)` / `resetSonnerToastMock()` 공통 헬퍼
- [C-3] **호출부 테스트 갱신** — MoveFolderDialog.test (toast success/error 추가) / RenameDialog.test (toast success + inline-error-no-toast)
- [C] **검증** — typecheck PASS · lint PASS · 146 tests PASS

### 핵심 설계 결정
- **hook-level Options 패턴** — 호출부가 매번 mutate options을 넘기지 않고 hook 호출 시점에 콜백 등록. mutation 완료 시 컴포넌트가 mount되어 있어야 콜백이 발화 (TanStack Query v5의 onSuccess는 observer 기반)
- **MoveFolderDialog `close()` 즉시 호출** — ClientFilesPage에서 항상 mount되므로 다이얼로그가 isOpen=false로 null 반환해도 hook은 살아있음 → 콜백 발화 보장
- **RenameDialog는 onSuccess만 hook-level** — 실패는 inline alert(setError)로 다이얼로그 유지가 UX 우선
- **vi.mock 글로벌 setup** — vi.mock 팩토리는 호이스트되어 import된 심볼 참조 불가 → setup.ts에서 inline 팩토리로 처리. 헬퍼는 vi.mocked(toast)로 사후 접근

### v1.x 후속 (이번 스코프 제외)
- **ApiError 타입화** — `unknown` throws → 구조화 타입 (status/code/details)
- **vi.hoisted 표준화** — 일부 테스트의 vi.mock 패턴 통일
- **useStorageQuota env-driven** — quota 표시 컴포넌트의 환경변수 readout

### 다음 세션 컨텍스트
- **mutation hooks 추가 시 invalidations 헬퍼 재사용** — 새 패턴 발생 시 헬퍼에 추가
- **ApiError 타입은 백엔드 연결 (A 트랙) 시 필수** — 현재 mock은 `{ status, code }` plain object를 throw

### 블로커
- 없음

---

## 2026-04-25 — M10 완료 (고급 키보드 + 접근성 마무리)

### 완료
- [M10] **api.renameFile mock** — VALIDATION_ERROR (빈 이름) / RENAME_CONFLICT (같은 부모 정규화 중복) / NOT_FOUND, 폴더 시 MOCK_TREE도 갱신 (6 tests)
- [M10] **에러 코드 정합성** — docs/02 §8의 기존 코드 `RENAME_CONFLICT`(409) / `VALIDATION_ERROR`(400) 그대로 사용 (원칙 #12). 신규 코드 추가 없이 계약 유지
- [M10] **stores/renameUi.ts** — `{ isOpen, targetId, targetName, error }` + open/close/setError (3 tests)
- [M10] **useRenameFile** — markPending → renameFile → invalidate(filesInFolder/fileDetail/+folderTree+folder if folder) → unmarkPending + close, 실패 시 setError (5 tests)
- [M10] **RenameDialog** — role=dialog aria-modal, input focus + select-all, 이전 focus 복귀, role=alert 에러, 동일/빈 이름 disabled (7 tests)
- [M10] **useGlobalShortcuts** — `/` 키 → window CustomEvent 'app:focus-search' (input/textarea/contenteditable/modifier 시 무시, JSDOM contentEditable 폴백 포함) (7 tests)
- [M10] **FileTable handleKeyDown 확장** — Shift+↑↓ (anchor 유지 selectRange), Ctrl/Meta+↑↓ (focus only), F2 (단일 선택 또는 focus → openRename), Delete (selection or focus → confirm → useDeleteBulk)
- [M10] **ClientFilesPage 마운트** — RenameDialog + useGlobalShortcuts() 호출
- [M10] **검증** — typecheck PASS · lint PASS · 136 tests PASS (M7 기준 108 → +28 신규)
- [M10] **로드맵** — docs/01 §18 M10 행에 완료 마커(2026-04-25)

### 핵심 설계 결정
- **F2 다이얼로그 (인라인 X)** — 가상화 컨테이너에서 인라인 편집은 row 리렌더링으로 입력 휘발 위험. 다이얼로그는 MoveFolderDialog 패턴 그대로 재사용
- **Shift+↑↓ anchor 안정성** — selectRange가 lastClickedId를 변경하지 않으므로 진동 없음. M4 anchor 폴백(null/pending/폴더 변경) 그대로 동작
- **Ctrl/Meta+↑↓ alias** — 현재 ↑↓가 이미 focus-only이므로 modifier는 §12.1 키맵 명세 만족용 별칭
- **Delete native confirm** — 브라우저 내장 포커스 트랩/스크린리더 지원, MVP zero-cost. 디자인 일관성 필요해지면 ConfirmDialog로 교체 (M14/M15)
- **`/` 트리거는 lazy 이벤트** — 검색 입력 컴포넌트(M11/M14)와 디커플. listener 없으면 no-op
- **원칙 #6 (서버가 진실)** — RenameDialog는 빈 입력 외 client validation X. 충돌은 서버 RENAME_CONFLICT → setError로 다이얼로그 유지
- **에러 코드 계약 준수** — spec 작성 시 `NAME_CONFLICT`/`INVALID_NAME` 가정했으나 docs/02 §8에 이미 `RENAME_CONFLICT`/`VALIDATION_ERROR`가 동일 의미로 존재 → 코드를 docs에 정합 (원칙 #12)

### 다음 세션 컨텍스트
- 검색 입력 컴포넌트(M11) 마운트 시 `useEffect`에서 `window.addEventListener(FOCUS_SEARCH_EVENT, onFocus)` 등록 잊지 말 것 (`useGlobalShortcuts`가 이미 디스패치 중)
- ConfirmDialog 디자인 시스템 컴포넌트화는 M14 Visual Identity와 함께 검토 (현재 native confirm)
- 다중 선택 + F2 일괄 이름 변경(prefix 등)은 v1.x 범위
- 백엔드 연결 시 `api.renameFile`는 `PATCH /files/:id` 또는 `POST /folders/:id/rename`으로 교체
- BulkActionBar에 "이름 변경" 버튼 추가는 M14 Visual Identity와 함께 (단일 선택 시만 활성화)

### 블로커
- 없음

### 마일스톤 상태 (docs/01 §18)
- ✅ M1~M7, M10, M13 완료. M8 (권한 UI)는 docs/03 §3 작성 후 진입 가능. M11 검색은 M10의 `app:focus-search` 트리거 활용 가능.

---

## 2026-04-25 — M7 완료 (DnD 이동: dnd-kit + 다이얼로그 듀얼 경로)

### 완료
- [M7] **@dnd-kit/core 6.x 설치** — 이동 전용 (업로드 native DnD와 분리, 원칙 #7)
- [M7] **lib/folderTreeUtils.ts** — `findNode`/`containsNode`/`isSelfOrDescendantOfAny` 순수 유틸 (12 tests)
- [M7] **api.moveFiles mock** — MOCK_TREE/MOCK_FILES 상태 갱신, self/descendant/target 검증, 3 에러 코드 throw (5 tests)
- [M7] **에러 코드 추가 (docs/02 §8)** — `MOVE_INTO_SELF` / `MOVE_INTO_DESCENDANT` / `TARGET_NOT_FOUND` (원칙 #12)
- [M7] **stores/moveUi.ts** — Zustand 다이얼로그 슬라이스 (`isMoveDialogOpen`/`moveIds`/`moveSourceFolderId`) (3 tests)
- [M7] **components/dnd/types.ts** — `MoveDragData` 타입 + droppable id prefix
- [M7] **useDragPayload** — selection vs single-row 결정, containsFolderIds 캐시 조회 (4 tests)
- [M7] **useMoveBulk** — markPending → moveFiles → invalidate(source/target/folderTree/fileDetail) → unmarkPending (3 tests)
- [M7] **MoveDragOverlay** — `role="status" aria-live="polite"` 카운트 배지 (행 복제 X)
- [M7] **useFolderDroppable** — useDndContext로 active 읽음, isInvalid/isSameFolder/isOver/isDragging 플래그
- [M7] **DndProvider** — PointerSensor distance:5px (클릭 vs 드래그 구분), DragEnd 시 self/descendant/같은-폴더 no-op
- [M7] **explorer/layout.tsx** — DndProvider 마운트 (sidebar+main 모두 droppable 영역)
- [M7] **FolderTree / Breadcrumb / FileRow(폴더)** — drop 타겟 통합 + 시각화
- [M7] **FileRow draggable** — useDraggable + dragData 합성, hook 호출 순서 안정화 위해 비폴더 행도 droppable hook 호출 (`__not_a_target__`)
- [M7] **BulkActionBar 이동 버튼** — 스텁 제거, openMoveDialog(ids, folderId) 호출
- [M7] **MoveFolderDialog** — radiogroup 폴더 트리 picker, source/self/descendant disabled, Esc/Enter, role="dialog" aria-modal (5 tests)
- [M7] **ClientFilesPage 마운트** — UploadConflictDialog 옆에 MoveFolderDialog 마운트
- [M7] **린트 정리** — useDragPayload/useMoveBulk 테스트 wrapper에 displayName 부여
- [M7] **검증** — typecheck PASS · lint PASS · 108 tests PASS (M5 기준 76 → +32)
- [M7] **로드맵** — docs/01 §18 M7 행에 완료 마커(2026-04-25) + 핵심 DoD 추가

### 핵심 설계 결정
- **듀얼 진입**: 마우스(DnD) + 키보드(BulkActionBar 다이얼로그). 두 경로 모두 `useMoveBulk` mutation으로 수렴 (단일 책임)
- **DragOverlay = 카운트 배지** (`📎 N개 항목 이동 중`). 행 복제 X — 가상화/접근성 충돌 회피
- **자기/후손 차단 3중 방어**: ① useFolderDroppable.disabled (드롭 자체 불가) ② DndProvider.handleDragEnd 재검증 ③ api.moveFiles에서 throw
- **낙관적 업데이트 X** (원칙 #3): selection.markPending → mutation 완료 후 invalidate. 실패 시 unmarkPending만.
- **무효화 매트릭스 (원칙 #6)**: source/target `filesInFolder` (모든 sort/dir 변종, prefix 매치) + `folderTree` + 각 id의 `fileDetail`
- **`__not_a_target__` 트릭**: FileRow에서 폴더가 아닌 행도 useFolderDroppable을 호출해 React Hook 순서 안정화. 트리에 없는 id이므로 자동으로 드롭 비대상.

### 다음 세션 컨텍스트
- 백엔드 연결 시 api.moveFiles는 `POST /folders/:id/move` 또는 `POST /files/move-bulk`로 교체 (mock fakeXHR 패턴 유지)
- DragOverlay 카운트 배지의 i18n 처리는 v1.x 검색 마일스톤(M11)과 함께 처리
- Task 17 DnDProvider 통합 테스트는 jsdom DnD 한계로 스킵 — Playwright e2e에서 검증 (관련 항목은 M10 접근성/키보드 마일스톤에 함께)
- M8 권한 UI는 `usePermission()` 훅 이미 BulkActionBar에 사용 중 → 권한 매트릭스 (docs/03 §3) 작성 필요

### 블로커
- 없음

### 마일스톤 상태 (docs/01 §18)
- ✅ M1~M7 완료. M8(권한 UI)는 docs/03 §3 작성 후 진입 가능. M13(디자인 토큰) 별도 완료.

---

## 2026-04-25 — M13 완료 (Claude 디자인 시스템 토큰 적용)

### 완료
- [M13] `design-reference/` 번들 추가 (Claude 핸드오프: `IbizDrive.html` + `styles.css` 1318줄 + `README.md` + 4 jsx 참고용)
- [M13] `docs/design-system.md` 갱신 — §5에 M5 업로드 컴포넌트 매핑 섹션 추가, §10 Open Questions(다크 모드 토글, variant 범위, 폰트 로딩, 6열 확장)
- [M13] M5 업로드 컴포넌트 4종 className만 styles.css 치수에 맞춰 조정 (JSX/props/handlers/aria 변경 없음):
  - `UploadButton`: h-7 px-2.5 + border-accent (`.btn-primary` 매핑)
  - `UploadOverlay`: inset-2 + backdrop-blur-[2px] + accent 8% + rounded-lg (`.drop-overlay`)
  - `UploadQueueDock`: w-[340px] max-h-[420px] + border-border-strong + header bg-surface-2 + progress h-[2px] bg-surface-3
  - `UploadConflictDialog`: max-w-[460px] + 백드롭 rgba(0,0,0,.32)
- [M13] `.gitignore` 초기 생성 (`.tmp/`, `.claude/`, `node_modules/`, `.next/`, etc.)

### DoD
- ✅ typecheck / lint / test 76/76 통과
- ✅ SSR HTML 검증: `bg-bg`, `bg-surface-1`, `bg-accent`, `text-fg` 등 토큰 클래스 렌더링 확인
- ✅ CSS 번들 검증: `.bg-accent { background-color: var(--accent); }` 등 13종 토큰 유틸 정상 생성
- ✅ 코드베이스 전수 검사: `bg-white` / `bg-gray-*` / `text-gray-*` 0건
- ✅ 사용자 시각 확인 (스크린샷): 따뜻한 회색 배경, 인디고 accent 버튼, surface-1 사이드바 모두 토큰 값 적용 확인

### 원칙 체크
- ✅ 디자인 진실 출처: `design-reference/styles.css` → `globals.css` (이미 prior 세션 0315a04에서 적용) → `@theme inline`으로 Tailwind 유틸 노출
- ✅ "토큰만, 구조 변경 없음" 원칙 준수: JSX 트리, props, handlers, aria 속성 모두 그대로

### 사용자 결정 (2026-04-25 세션)
시각적 임팩트가 큰 추가 작업(TopBar / Lucide 아이콘 / FileRow 밀도 / StatusBar / SortChip / ViewSwitch / 6열 테이블 / RightPanel 탭)은 M13 범위에서 명시적으로 제외하고 후속 마일스톤으로 분리:
- **M14 Visual Identity** — TopBar + Lucide 아이콘 + FileRow 밀도 + StatusBar
- **M15 Layout Extras** — SortChip + ViewSwitch + StorageBar + RightPanel 탭
- **M16 Grid View** — FileTable grid 모드 (M14 ViewSwitch 의존)

→ `docs/01-frontend-design.md §18` 로드맵에 추가됨.

### 다음 세션 컨텍스트
**M14 진입 시 필요**
- 의존성 1개 추가 검토: `lucide-react` (아이콘) — 사용자 confirm 필요
- TopBar는 새 컴포넌트 (`src/components/layout/TopBar.tsx`) — `app/(explorer)/layout.tsx` grid를 `grid-rows-[48px_1fr]`로 재구성
- FileRow는 emoji → SVG 아이콘 매핑 테이블 도입 (mime → 아이콘)
- StatusBar는 사이드바 하단 또는 main 하단 고정 — 디자인 결정 필요
- 테스트 영향: FileRow 테스트가 emoji assertion을 쓰면 수정 필요 (현재 없음 확인 → 영향 0)

**브랜드 다크 모드 토글 UI**
- M13에서 토큰만 정의됨. 토글 UI는 M14 TopBar에 포함 검토.

### 블로커
- 없음

---

## 2026-04-25 — M5 완료 (업로드: multipart + 충돌 + 실패 분류)

### 완료
- [M5] `lib/uploadErrors.ts` 5종 분류 (network/permission/quota/server/conflict) + 단위 테스트 8개
- [M5] `lib/fakeXhr.ts` + 매직 파일명 6종 (normal/conflict.pdf/huge.bin/deny.txt/srv_500.any/net_fail.any) + 단위 테스트 7개
- [M5] `lib/api.ts#uploadFile` — FakeXHR 반환 (교체 경계: 실제 XHR로 교체 시 소비자 변경 없음)
- [M5] `stores/upload.ts` — queue/applyToAll/enqueue/updateTask/resolveConflict/retry/cancel/clearDone + 단위 테스트 8개
- [M5] `hooks/useUpload.ts` — store subscribe 기반 XHR orchestration + done시 `filesInFolder` invalidate + 단위 테스트 8개 (DoD o 포함)
- [M5] `hooks/useNativeFileDrop.ts` — `types.includes('Files')` 가드로 dnd-kit 분리 (원칙 #7), depth counter + 단위 테스트 4개
- [M5] `hooks/useUploadBeforeUnload.ts` — pending(`queued|uploading|conflict`) > 0 시 경고
- [M5] UI 5개: `UploadButton` / `FolderToolbar` / `UploadOverlay` / `UploadQueueDock` / `UploadConflictDialog`
  - UploadOverlay (2 tests), UploadQueueDock (4 tests), UploadConflictDialog (5 tests) — a11y (aria-modal, aria-labelledby/describedby, Esc=skip, Tab 포커스트랩, applyToAll)
- [M5] `FileTable` — `containerRef` + `useNativeFileDrop` + `UploadOverlay` 통합, early return을 body 변수로 리팩토링하여 Empty/Error/Forbidden 상태에도 drop 동작
- [M5] `ClientFilesPage` — `FolderToolbar` + `UploadQueueDock` + `UploadConflictDialog` + `useUploadBeforeUnload` 통합
- [M5] `FileTableEmpty` — `UploadButton` CTA 삽입 (재사용)
- [M5] `docs/01 §5.3` 구현 노트 추가 (paused/tusUrl/overwrite 제외 사유, pendingCount/enqueue/applyToAll/cancel 의미론)

### DoD
- ✅ typecheck / lint / test 전체 통과 (기존 30 + M5 46 = 76 passing)
- ⏳ 수동 검증 a~l, n — 사용자 브라우저 확인 대기 (pnpm dev → /files/root → 시나리오 12종)

### 원칙 체크
- ✅ #1 URL folderId canonical — `task.targetFolderId`는 `enqueue` 시점의 folderId 스냅샷 (Zustand는 "무엇을" 올릴지만 가짐)
- ✅ #3 낙관적 업데이트 비파괴적만 — 업로드 결과 낙관 append 금지, done 시 `invalidateQueries({ queryKey: [...qk.files(), 'list', folderId] })`로 prefix match
- ✅ #7 DnD 분리 — native는 `FileTable` 컨테이너만, `types.includes('Files')` 가드로 dnd-kit 이벤트 무시
- ✅ #12 에러 코드 — `lib/uploadErrors.ts`가 docs/02 §8의 status 코드 매핑 유지

### 다음 세션 컨텍스트
**M5.1 (tus 재개 업로드)**
- `UploadTask`에 `tusUrl`, `paused` 상태 도입
- `useUpload`의 transport만 교체 (store/UI 변경 없음)

**실제 백엔드 연결**
- `api.uploadFile` 내부를 실제 `XMLHttpRequest`로 교체 (인터페이스 동일)
- `FakeXHR` 파일 및 매직 파일명 테스트는 제거 또는 e2e로 이관

**M7 DnD 이동 + 드롭 타겟 확장**
- `useNativeFileDrop`을 `FolderTree` 노드에도 연결 (dnd-kit 이동과 공존)
- 동일 가드(`types.includes('Files')`)로 분리 유지

### 블로커
- 없음

---

## 2026-04-25 — 디자인 시스템 적용 (M5 업로드 구현 진입 전)

### 완료
- [DS] `docs/design-system.md` 신규 — 토큰 2-layer 전략 (base CSS vars + `@theme inline` 매핑), 컴포넌트별 클래스 매핑
- [DS] `frontend/src/app/globals.css` 재작성 — color/typography/radius/shadow/spacing 토큰, `[data-theme="dark"]` 다크 모드, focus-visible 전역 링, 스크롤바 스타일
- [DS] `(explorer)/layout.tsx` — 3-col 레이아웃 + 사이드바 브랜드 마크 (22px accent 사각형 + "IbizDrive" 14px semibold)
- [DS] `FolderTree.tsx` — active: `bg-accent-soft text-accent font-medium`, inactive: `hover:bg-surface-2 hover:text-fg`, 깊이 들여쓰기 유지
- [DS] `Breadcrumb.tsx` — 마지막 노드 `text-[15px] font-semibold text-fg`, 구분자 `›` `text-fg-subtle`
- [DS] `BulkActionBar.tsx` — `bg-accent-soft border-y` 바, `h-7 px-2.5 rounded` 버튼 패턴, 위험: `hover:bg-[color-mix(in_oklch,var(--danger)_12%,transparent)] hover:text-danger`
- [DS] `FileTable.tsx` — `GRID_COLS` 상수 추출 후 FileRow에 prop 전달, 헤더 `h-[30px] bg-surface-1 text-[11px] uppercase tracking-[0.04em]`
- [DS] `FileRow.tsx` — `gridCols` prop 수신, 상태별 class 토큰화 (pending/selected/hover), focus-visible은 globals.css 전역 링이 담당
- [DS] `RightPanel.tsx` — `w-[360px] bg-surface-1 border-l`, 상세 그리드 `grid-cols-[80px_1fr] text-[12px]`, 에러 `text-danger`
- [DS] Empty/Error/Forbidden/Skeleton 4개 상태 컴포넌트 — `flex-1 flex flex-col items-center justify-center gap-3 py-[60px]` 공통, danger 변형은 `bg-[color-mix(in_oklch,var(--danger)_10%,transparent)]`
- [DS] `ClientFilesPage.tsx` — 2-pane 래퍼 `flex flex-1 min-h-0`, 메인에 `bg-bg`
- [DS] route-level states — `loading.tsx` / `error.tsx` / `not-found.tsx` 모두 center-layout 상태 패턴으로 통일

### 원칙 준수 체크
- ✅ 구조·로직 변경 없음 (className만 수정)
- ✅ aria 속성 전원 유지 (aria-rowcount/rowindex, role=grid/row/gridcell, role=toolbar, aria-live, role=alert)
- ✅ focus-visible 링 가시성 유지 (globals.css에서 전역 `:focus-visible` 스타일)
- ✅ §19 원칙 1~5 영향 없음 (URL 진실 출처, query-param RightPanel, pending 낙관, DnD 분리 원칙 모두 그대로)
- ✅ 새 의존성 추가 없음 (폰트/아이콘 패키지 생략, 시스템 폰트 + 이모지 유지)

### 토큰 설계 요약
- Base vars는 `:root`에 raw 값(`oklch`/`#hex`)으로 정의, `[data-theme="dark"]`가 오버라이드
- Tailwind 4의 `@theme inline`이 base vars를 util class로 노출: `--color-bg`, `--color-surface-1`, `--color-accent`, `--color-fg-muted` 등
- 결과: `bg-bg` / `bg-surface-1` / `text-fg-muted` / `text-accent` 같은 utility가 `var(--bg)`를 참조 → 다크 모드 전환 시 className 변경 0건

### DoD
- typecheck: 통과
- lint: 통과
- test: 30/30 통과 (기존 테스트 regressions 없음 — className 전용 변경이라 snapshot/DOM 쿼리 영향 없음)
- 브라우저 시각 검증: 대기 (사용자 확인 권장)

### 다음 세션 컨텍스트
**M5 (업로드) 구현 재개**
- 승인된 spec: `docs/superpowers/specs/2026-04-25-m5-upload-design.md`
- 다음 단계: writing-plans skill → implementation plan 작성 후 구현 진입
- 새 UI 요소(UploadButton, UploadToasts, ConflictDialog)는 이번 디자인 토큰 체계 위에 구축

**다크 모드 활성화**
- 현재 `[data-theme="dark"]` 셀렉터로 정의됨. 토글 UI는 M9(설정)로 보류
- 테스트: DevTools에서 `<html data-theme="dark">` 수동 설정 시 전환 확인 가능

### 블로커
- 없음

---

## 2026-04-25 — M6 완료 (RightPanel + useOpenFile + ?file= 자동 제거) ✅ 브라우저 검증 통과

### 완료
- [M6] `hooks/useOpenFile.ts` — §17.5 설계 그대로. `?file=` query param 진실 출처. open/close/fileId 반환, replace + scroll:false, 다른 param 보존
- [M6] `hooks/useFileDetail.ts` — `qk.fileDetail(id)` 캐시 키, enabled:Boolean(id), staleTime 30s
- [M6] `api.getFileDetail(id)` MOCK — 404 throw 포함
- [M6] `components/files/RightPanel.tsx` — role=complementary, 이름/유형/크기/수정일/수정자 dl, 로딩 스켈레톤, 에러 role=alert, 닫기 버튼, document keydown Esc 리스너 (fileId 있을 때만 등록)
- [M6] `ClientFilesPage` 2-pane 레이아웃: 좌측 Breadcrumb+BulkActionBar+FileTable, 우측 RightPanel (fileId 없으면 null 반환해 공간 미차지)
- [M6] `FileTable.handleOpen` 리팩터 — 인라인 URL 조작 제거, `useOpenFile().open()` 사용. 분기 확정: folder → router.push, file → openFile(id)
- [M6] `hooks/useCloseFileOnFolderChange.ts` — 폴더 변경 시 `?file=` 자동 제거. prevRef로 초기 마운트는 건너뜀 (딥링크 보존). M3 "folderId 변경 시 focus/selection reset"과 대칭
- [M6] test/setup.ts에 afterEach cleanup 추가 (testing-library v16 + globals:false 조합에서 자동 cleanup 미작동 → 문서 레벨 리스너/DOM 누적 버그 방지)

### 계약 파일 추가/수정
- frontend/src/hooks/useOpenFile.ts                          신규 (docs/01 §17.5)
- frontend/src/hooks/useFileDetail.ts                        신규 (docs/01 §6.1 키 사용)
- frontend/src/hooks/useCloseFileOnFolderChange.ts           신규 (M3 대칭 정책)
- frontend/src/hooks/useOpenFile.test.ts                     신규 (5 tests)
- frontend/src/hooks/useCloseFileOnFolderChange.test.ts      신규 (5 tests)
- frontend/src/components/files/RightPanel.tsx               신규 (docs/01 §11, §12.1)
- frontend/src/components/files/RightPanel.test.tsx          신규 (5 tests)
- frontend/src/lib/api.ts                                    수정 (getFileDetail 추가)
- frontend/src/components/files/FileTable.tsx                수정 (useOpenFile로 refactor)
- frontend/src/app/(explorer)/files/[...parts]/ClientFilesPage.tsx 수정 (RightPanel 통합, 2-pane, useCloseFileOnFolderChange 호출)
- frontend/src/test/setup.ts                                 수정 (cleanup 추가)

### 미결 결정 사항 (이번 세션 확정)
1. **폴더 이동 시 `?file=` 자동 제거: YES** — 파일은 특정 폴더 컨텍스트이므로 폴더가 바뀌면 패널은 의미 없음. M3 focus/selection reset과 대칭. 딥링크는 prevRef=null 조건으로 보존
2. **단일 클릭 = 패널 열기: 유지 (현재 Enter/더블클릭만)** — 단일 클릭은 M4에서 "선택"으로 합의됨. Windows/Mac 파일 탐색기 표준 UX와 일치. 빠른 미리보기는 향후 M10 별도 설계

### 원칙 준수 체크
- ✅ §19 원칙 2 (RightPanel은 query param, parallel route 아님) — `?file=` 유지
- ✅ §19 원칙 1 (URL이 "어디"를 소유) — 파일 선택 상태도 URL query에 존재
- ✅ Esc 정책 (§12.1) — RightPanel 전역 리스너가 닫기 담당. FileTable grid의 Esc(선택 해제)는 독립. 둘 다 누르면 각자 동작 (상호 방해 없음)

### DoD
- typecheck: 통과
- lint: 통과
- test: 30/30 통과 (기존 15 + useOpenFile 5 + RightPanel 5 + useCloseFileOnFolderChange 5 = M6 15개 신규)
- 브라우저 수동 검증: 통과 (A~F 섹션 15 시나리오)

### 회고 — testing-library v16 + vitest globals:false cleanup 이슈
- 증상: RightPanel 테스트에서 "Found multiple elements with role button and name 패널 닫기"
- 원인: vitest.config의 `globals: false` → `@testing-library/react`의 자동 afterEach cleanup이 비활성. 테스트 간 DOM이 `document.body`에 누적되어 이전 테스트의 aside가 남아 있었음. 동시에 RightPanel의 `document.addEventListener('keydown')` 리스너도 누적됨
- 해결: `test/setup.ts`에서 `afterEach(cleanup)` 수동 등록. 앞으로 컴포넌트 테스트 추가 시 자동 적용됨
- 교훈: `globals: false`를 선호한다면 cleanup/matchers는 setup에 명시적으로 넣어야 함

### 회고 — 폴더 변경 시 ?file= 자동 제거 설계
- 자연 네비게이션(FolderTree/Breadcrumb Link 클릭, handleOpen router.push)은 이미 ?file=을 자동 탈락시킴. 따라서 `useCloseFileOnFolderChange`는 엣지 케이스(back/forward, 프로그래밍 navigation, 딥링크 뒤 이동)를 위한 안전망 역할
- 딥링크(`/files/foo?file=x` 초기 마운트) 보존을 위해 `prevRef.current === null`일 때는 건너뜀. 이후 이동부터 동작
- 훅 분리 이유: 단일 callsite이지만 ref+초기 스킵 로직이 비자명 → 단위 테스트로 회귀 방지 (CLAUDE.md의 "premature abstraction 금지" 원칙에 근접하나 테스트 가치로 상쇄)

### 다음 세션 컨텍스트
**M5 (백엔드 API 연결)**
- MOCK(api.ts) 전체 → 실제 fetch로 교체. 계약은 docs/02 §7
- `getFileDetail`은 현재 `FileItem` 그대로 반환. 백엔드 계약에서 권한·버전·공유자 등 포함하면 `FileDetail` 타입 분리 필요
- 에러 코드 (docs/02 §8) 매핑 후 RightPanel 에러 상태 메시지 세분화

**M7 (권한)**
- RightPanel에 권한 기반 액션 버튼 (다운로드/공유/이름변경) 추가 자리 있음
- usePermission 실제 훅 교체 후 확장

**M10 (고급 키보드)**
- 단일 클릭 = 빠른 미리보기 UX 재검토 지점 (이번 세션에서 유지 결정)
- Space로 패널 토글 등 탐색기 스타일 옵션 검토

**후속 개선 (우선순위 낮음)**
- ClientFilesPage의 canonical redirect가 현재 pathname만 비교, `router.replace(canonical)`에서 query를 전부 탈락시킴. `?file=` 및 `?sort=` 등이 의도치 않게 날아갈 가능성. 재현되면 canonical redirect에 query 보존 로직 추가

### 블로커
- 없음

---

## 2026-04-25 — M4 완료 (선택 모델 + BulkActionBar) ✅ 브라우저 검증 통과

### 완료
- [M4] selection store (stores/selection.ts) + Vitest 단위 테스트 12개
  - §5.1 스펙 + markPending이 selection에서 자동 제거 (상호배제)
  - selectRange 앵커 폴백 3케이스 (null / pending / 폴더 외)
- [M4] usePermission 스텁 훅 (§14.2 시그니처, M7 교체 예정)
- [M4] useDeleteBulk 훅 + 3케이스 단위 테스트 (성공 / 실패+같은폴더 / 실패+다른폴더)
- [M4] BulkActionBar (role=toolbar, aria-live=polite, count===0 숨김)
- [M4] FileRow: aria-selected 실제 연결, pending 시 opacity+스피너+aria-disabled, onClick(item, MouseEvent) 시그니처
- [M4] FileTable: aria-multiselectable 복원, 키보드 Space/Ctrl+A/Esc clear, ArrowUp/Down pending 스킵, markPending focus 보정 useEffect, 폴더 변경 시 clear
- [M4] Vitest + jsdom + @testing-library/react 테스트 인프라 세팅
- [M4] api.deleteBulk mock 추가
- [M4] eslint.config.mjs: .next/build ignore 추가 (lint 오경보 수정)
- [M4] BulkActionBar selector 무한 렌더 버그 수정 (a589032)
- [M4] next.config.ts allowedDevOrigins 추가 (127.0.0.1 dev 접근 허용)

### 계약 파일 추가/수정
- frontend/src/stores/selection.ts               신규 (docs/01 §5.1)
- frontend/src/hooks/usePermission.ts            신규 (docs/01 §14.2 스텁)
- frontend/src/hooks/useDeleteBulk.ts            신규 (설계안 §2.5)
- frontend/src/components/files/BulkActionBar.tsx 신규 (docs/01 §8.2)
- frontend/src/components/files/FileRow.tsx      수정 (Props 시그니처 변경)
- frontend/src/components/files/FileTable.tsx    수정 (selection 연결)
- frontend/src/lib/api.ts                        수정 (deleteBulk 추가)
- frontend/src/app/(explorer)/files/[...parts]/ClientFilesPage.tsx  수정 (BulkActionBar 렌더)

### 설계 문서 업데이트
- docs/01 §5.1 하단에 구현 노트 (상호배제, 앵커 폴백) 추가
- docs/superpowers/specs/2026-04-25-m4-selection-bulkactionbar-design.md 신규 작성
- docs/superpowers/plans/2026-04-25-m4-selection-bulkactionbar.md 신규 작성

### DoD
- typecheck: 통과
- lint: 통과
- test: 15/15 통과 (selection 12 + useDeleteBulk 3)
- **브라우저 수동 검증: 12/12 시나리오 통과** (클릭/Ctrl+click/Shift+click/Space/Ctrl+A/Esc/폴더이동 clear/휴지통 pending→삭제→invalidate 포함)

### 버그 재발 방지 — Zustand v5 selector 무한 렌더
- **증상**: "Maximum update depth exceeded" (BulkActionBar 마운트 직후)
- **루트 원인**: `useSelectionStore((s) => Array.from(s.ids))` — selector가 매 snapshot 호출마다 **새 배열 참조** 반환
- **메커니즘**: Zustand v5는 `useSyncExternalStore` 기반. React가 매 render마다 이전/신규 snapshot 참조 비교 → 항상 "변경됨" 판정 → 무한 재렌더
- **올바른 패턴**: selector는 store에 **이미 존재하는 안정 참조**(Set/Map/객체 자체)만 반환. 파생 변환(Array.from, filter, map, spread)은 selector 밖 render 본문에서 수행
  ```tsx
  // ❌ 금지
  const ids = useSelectionStore((s) => Array.from(s.ids))
  // ✅ 권장
  const idsSet = useSelectionStore((s) => s.ids)
  const ids = Array.from(idsSet)
  ```
- **예외**: 변환이 꼭 필요하면 `useShallow` 또는 `zustand/shallow` equality 함수 명시. 하지만 대부분은 위 패턴이 충분하고 단순함

### 다음 세션 컨텍스트
**M5 (백엔드 API 연결)**
- MOCK(api.ts)을 실제 fetch로 교체. 계약은 docs/02 §7
- useDeleteBulk 실패 경로: 현재 console.warn → 토스트 라이브러리 통합 (sonner 후보)
- 에러 코드 (docs/02 §8) 매핑: 409 CONFLICT / 403 FORBIDDEN / 423 LOCKED 등 UI 메시지

**M6 (DnD 이동)**
- BulkActionBar 이동 버튼: 현재 스텁 → dnd-kit + 이동 다이얼로그
- 업로드 DnD(window native)와 컨텍스트 분리 원칙(§19) 유지
- pendingIds 재사용 (이동 중인 row는 pending UI)

**M7 (권한)**
- usePermission 스텁 → `useQuery + api.getEffectivePermissions(nodeId)` 로 교체 (docs/01 §14.2)
- BulkActionBar 버튼 표시 조건(can.download/move/delete) 실제 동작
- docs/03 §3 권한 매트릭스 확정 선행 필요 (현재 스켈레톤)

**M10 (고급 키보드)**
- Shift+↑↓ 범위 선택, Ctrl+↑↓ 포커스-only 이동, F2 rename, Delete 삭제, `/` 검색 포커스
- 설계 §12 참고, M4에서 anchor/pending 인프라 이미 확보됨

### 블로커
- 없음 (M5 진입 가능. docs/03 §3 권한 매트릭스는 M7 시작 전 확정 필요)

---

## 2026-04-25 — M3 브라우저 검증 완료

### 검증 결과
- /files/root — 5개 항목 정상 렌더링 (📁영업팀, 📁인사팀, 📄제안서, 📊예산안, 📝회의록)
- /files/folder_contracts — 계약서 2개 정상
- /files/folder_hr — empty state ("이 폴더는 비어 있습니다") 정상
- 키보드 ↑↓ Enter Esc 모두 정상 동작
- hydration 에러는 브라우저 확장(testim) 주입 문제, 코드 무관

### 다음 세션 컨텍스트 (M4: 선택 모델 + BulkActionBar)
- FileRow aria-selected 항상 false → M4에서 useSelectionStore 연결
- FileRow onClick → M4에서 selectOnly/toggle/range 로직 연결
- aria-multiselectable={true} → M4에서 grid에 다시 추가
- 컬럼 헤더 정렬 토글 UI 미구현 (sort/dir은 URL param으로 동작)
- F2/Delete/Ctrl+A/검색(/) 키보드는 M10으로 미룸
- 3000~4000 포트 대역은 다른 앱이 점유 중 → dev 서버는 4100+ 사용 권장

---

## 2026-04-24 — M3 구현

### 완료
- [M3] FileTable — TanStack Virtual 가상화 (overscan: 10, 10k+ 행 대응)
- [M3] 4가지 상태 컴포넌트: Skeleton, Empty, Error(onRetry), Forbidden(403)
- [M3] FileRow — 아이콘, 크기/날짜 포맷, roving tabIndex
- [M3] WAI-ARIA grid 패턴: role="grid/row/gridcell/columnheader", aria-rowcount/rowindex/selected
- [M3] 기본 키보드: ↑↓ 포커스 이동 + DOM focus 동기화, Enter 열기, Esc 해제
- [M3] useFilesInFolder 훅 (qk.filesInFolder 캐시 키)
- [M3] useSortParams 훅 (URL searchParams에서 sort/dir 읽기)
- [M3] ClientFilesPage에 FileTable 통합

### 계약 파일 추가/수정
- src/types/file.ts            (FileItem, SortKey 타입)
- src/lib/queryKeys.ts 수정     (files, filesInFolder, fileDetail 키 추가)
- src/lib/api.ts 수정           (MOCK_FILES + getFilesInFolder 추가)

### 코드 리뷰 후 수정
- role="grid"를 외부 컨테이너로 이동 (header row가 grid 안에 포함되도록)
- aria-multiselectable 제거 (M4 선택 기능 전까지 premature)
- folderId 변경 시 focusedIndex 리셋 useEffect 추가
- 포커스 시 DOM .focus() 호출 추가 (스크린 리더 대응)
- role="gridcell", role="columnheader" 추가

### 다음 세션 컨텍스트 (M4: 선택 모델 + BulkActionBar)
- FileRow의 aria-selected는 현재 항상 false. M4에서 useSelectionStore 연결 필요
- FileRow onClick prop은 현재 focusedIndex만 설정. M4에서 selectOnly/toggle/range 연결
- aria-multiselectable={true}는 M4에서 다시 추가
- 컬럼 헤더 정렬 토글 UI 미구현 (sort/dir은 URL param으로 동작)
- api.ts mock 데이터: folder_sales/folder_hr가 MOCK_TREE와 MOCK_FILES에 이중 존재 — 실제 API 계약 확정 시 정리 필요
- F2/Delete/Ctrl+A/검색(/) 키보드는 M10으로 미룸

### 블로커
- 없음

### 설계 문서 업데이트 필요
- 없음 (docs/01 §4, §6, §11, §12 스펙 그대로 반영)

---

## 2026-04-24 — M1 완료

### 완료
- [M1] folderId 중심 catch-all 라우팅 (`/files/[...parts]`)
- [M1] FolderTree / Breadcrumb URL 동기화
- [M1] canonical redirect (decodeURI 비교, 한글 URL 대응)
- [M1] 프로젝트 기본 셋업 (Providers, 훅, 스토어)
- [M1] `/files` → `/files/root` 리다이렉트
- [M1] Explorer 레이아웃 (사이드바 + 메인)
- [M1] loading / error / not-found 상태 페이지

### 계약 파일 추가
- src/lib/normalize.ts      (docs/02 §3)
- src/lib/queryKeys.ts       (docs/01 §6.1)
- src/lib/folderPath.ts      (docs/01 §17.3)
- src/lib/api.ts             (MOCK — M5에서 실제 API로 교체)

### 다음 세션 컨텍스트 (M2: FolderTree 심화 + TrashLink + QuickAccess 또는 M3: FileTable)
- api.ts는 현재 mock. 백엔드 나오면 실제 fetch로 교체. 계약은 docs/02 §7.3
- 서버 컴포넌트 전환은 M3에서 (notFound/redirect 조합)
- canonical redirect는 클라이언트에서 useEffect. 깜빡임 있으면 M3에서 서버 redirect로
- Next.js 16에서 Windows pnpm EPERM 이슈 발생 → 15.3.2로 다운그레이드, npm 사용
- next-env.d.ts의 `.next/types/routes.d.ts` import는 Next.js 16 전용 → 15에서는 무시됨

### 블로커
- 없음

### 설계 문서 업데이트 필요
- 없음 (코드 템플릿 그대로 반영)

---

## 2026-04-26 — A1.0 완료 (User schema + JPA) + 드리프트 정리

### 완료
- [드리프트 정리] ADR #12 Spring Session(Redis) → JDBC 정정 4곳 (docs/00 §1.3/§4.4/§5 + docs/02 §7.1) — Redis는 MVP 인프라 제외, JDBC 단일 백엔드로 통일
- [드리프트 정리] 권한 enum 9종 (READ/UPLOAD/EDIT/MOVE/DOWNLOAD/DELETE/SHARE/PERMISSION_ADMIN/PURGE) docs/00 §3.2 ↔ docs/03 §3.1 동기화
- [드리프트 정리] users 테이블 컬럼 정렬: V1 stub의 display_name 유지 + docs/02 §2.1 동일 컬럼명으로 정렬 + role 대문자(MEMBER/AUDITOR/ADMIN)
- [신규 ADR] #18~#22 추가: MVP 인증 범위 / BCrypt 정책 / 세션 만료·잠금 / 관리자 초대 only / `/me` 응답 최소화
- [docs/02 §7.4] /api/auth/login·logout·me·csrf endpoint 상세 (CSRF 헤더, side-effects, 423 ACCOUNT_LOCKED 등)
- [docs/02 §8] 423 LOCKED → ACCOUNT_LOCKED + FILE_LOCKED 분리
- [docs/03 §2] TBD 스캐폴딩 → 본 스펙 (시퀀스 4종, §2.6 만료·잠금, §2.7 BCrypt 정책, §2.8 관리자 초대, §2.10 audit 매트릭스)
- [A1.0] V2 마이그레이션: users 5컬럼 추가 (role + is_active + last_login_at + locked_at + must_change_password)
- [A1.0] User @Entity + Role enum + UserRepository (findActiveByEmail, lowercase 정규화는 caller 책임)
- [A1.0] UserRepositoryTest (Testcontainers Postgres 15-alpine, `disabledWithoutDocker=true`로 로컬 dev pass)
- gradle 8.14.4 → 8.10 정렬 (#4 wrapper 후속)

### 다음 세션 컨텍스트
- A1.1 (PasswordEncoder + UserDetailsService) 진입 — `BCryptPasswordEncoder(12)` 래핑한 `DelegatingPasswordEncoder` + User→UserDetails 어댑터
- A1 잔여: A1.1 → A1.2 (SecurityConfig: CSRF double-submit + 세션 필터 + /api/auth/csrf) → A1.3 (LoginController + lockout) → A1.4 (Logout + /me)
- PR 분기점은 A1.2 종료 시점 권장 (Security 인프라 단위)

### 블로커
- A1.3 진입 전 해결 필요: lockout 카운터 backing store 결정 (docs/03 §2.3 footnote). ADR #12에서 Redis MVP 제외 → 후보 (a) in-memory `ConcurrentHashMap` (b) DB `login_failures` 테이블 (c) v1.x Redis ADR 선결정. A1.1 작업 중 ADR로 결정.

### 설계 문서 업데이트 필요
- docs/03 §2.3/§2.6 — lockout 카운터 Redis 표기를 결정된 backing 표기로 정정 (A1.1 ADR 후)
- docs/03 §4.1 audit enum — `user.login.success/failed/logout/session.expired/locked/unlocked` 추가 (A1.3 진입 시)
