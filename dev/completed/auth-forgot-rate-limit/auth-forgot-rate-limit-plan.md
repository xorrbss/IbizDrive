# auth-forgot-rate-limit — Plan

Last Updated: 2026-05-03

## 요약

`POST /api/auth/password/forgot`에 email + IP 단위 rate limit(분당 1회)을 도입한다. 외부 의존 없이 기존 `LoginAttemptTracker` 패턴(in-memory `ConcurrentHashMap` + `Clock` + lazy 만료, ADR #23)을 답습. 한도 초과 시 429 + `Retry-After` 헤더 + `RATE_LIMIT_EXCEEDED`. a1.5 트랙이 명시적으로 deferred 처리한 “rate-limit on /forgot” 잔여 항목 closure (`PasswordResetService.java:45` 주석 참조).

## 현재 상태 분석

- `backend/src/main/java/com/ibizdrive/auth/password/PasswordController.java:55-59`
  `forgot(@Valid ForgotPasswordRequest)` → `passwordResetService.requestReset(req.email())` → 동일 200 응답.
  무제한 호출 가능. SMTP cost amplification + per-email timing/측정 신호 표면.
- `backend/src/main/java/com/ibizdrive/auth/password/PasswordResetService.java:45`
  `// rate-limit은 본 트랙 범위 외 (별도 트랙에서 추가)` — 본 트랙이 closure.
- `backend/src/main/java/com/ibizdrive/auth/LoginAttemptTracker.java`
  in-memory ConcurrentHashMap + Clock + lazy 만료 + 잠금 잔여시간 조회. 본 트랙 패턴 원본.
- `backend/src/main/java/com/ibizdrive/common/error/AuthExceptionHandler.java:31-35`
  `AccountLockedException` → 423 LOCKED + retryAfter. 429 패턴 mirror 대상.
- `backend/src/main/java/com/ibizdrive/common/error/ErrorResponse.java`
  `accountLocked(retryAfterSeconds)` factory 존재 — `rateLimitExceeded(retryAfterSeconds)` factory 추가 위치.
- `docs/02-backend-data-model.md:1687`
  `429 | RATE_LIMIT_EXCEEDED | 요청 한도 초과 | 지수 백오프 재시도` 이미 정의 — 신규 에러 코드 추가 0건.
- `BETA-RELEASE.md` §5 인증/세션 게이트 ✓ 상태. 본 트랙은 §5 보강(추가 표면 차단), GO 차단 해제 트랙 아님.
- 의존하는 트랙: a1.5-email-infra (PR merged: #47, master @fdb57c7), auth-pages (PR merged: #43).
- 무관: PR #49 (be-test-regression-m38-m42, admin merge 대기) — surface 0 겹침. 머지 후 origin/master rebase 1회.

## 목표 상태

1. `ForgotPasswordRateLimiter` (LoginAttemptTracker mirror) — `tryAcquire(emailKey, ipKey)` / `getRetryAfterSeconds(...)`. 두 키 독립 버킷, 어느 한쪽이라도 한도 초과면 차단.
2. `RateLimitExceededException(long retryAfterSeconds)` + `AuthExceptionHandler.rateLimited()` → 429 + `Retry-After` 헤더 + `ErrorResponse.rateLimitExceeded(retryAfterSeconds)`.
3. `PasswordController.forgot()` 진입 시 limiter check. 통과 시 기존 `requestReset` 위임. 차단 시 위 예외.
4. `dev` 환경에서 동일 email/IP 60초 내 2회 호출 시 2회차 → 429.
5. docs sync: `docs/03 §2.x` rate limit 정책 1줄, `BETA-RELEASE.md §5` 보강 표기, ADR 신규 1건(in-memory + 키 정책).
6. **scope 외 (명시적 비범위)**: /login (`LoginAttemptTracker`로 5/15min 보호 중), /reset, /change, /signup. 별도 트랙으로 분리.

## Phase별 실행 지도

| Phase | 산출물 | 게이트 |
|---|---|---|
| P0 | worktree + dev-docs(`plan`/`context`/`tasks`) + dev/process owner + bootstrap commit | git status clean |
| P1 | TDD RED — `ForgotPasswordRateLimiterTest` (`tryAcquire` 1회 통과 / 2회차 차단 / 60s 후 통과 / email-only 차단 / ip-only 차단 / Clock 진행으로 lazy 만료) | RED 명시 (테스트 fail) |
| P2 | GREEN — `ForgotPasswordRateLimiter` 구현 + `RateLimitExceededException` + `AuthExceptionHandler.rateLimited` + `ErrorResponse.rateLimitExceeded` | P1 RED → GREEN |
| P3 | TDD RED+GREEN — `PasswordControllerForgotRateLimitTest` (1회차 200 + 2회차 429 + Retry-After 헤더 + body code=`RATE_LIMIT_EXCEEDED` + 60s 후 200 회복 + 다른 IP는 통과 + 다른 email은 통과) | 6 케이스 GREEN, 회귀 0 |
| P4 | docs sync — `docs/03 §2` 1줄, `BETA-RELEASE.md §5` 표 1행, ADR(`docs/00 §5`) 1건, `docs/02 §7.4 forgot` 응답 코드에 429 추가 | docs/code 정합 |
| P5 | closure — `docs/progress.md` entry, `dev/active/` → `dev/completed/`, PR open | PR open + 본 plan acceptance criteria 모두 ✓ |

## Acceptance Criteria

- (A) 동일 email + 동일 IP로 60s 내 2회 호출 시 1회차 200, 2회차 429.
- (B) 429 응답: HTTP `Retry-After` 헤더(초 단위 정수) + body `{ code: "RATE_LIMIT_EXCEEDED", message, retryAfterSeconds }`.
- (C) 60s 경과 후 동일 email/IP로 다시 호출 → 200 회복 (lazy 만료).
- (D) email-only 한도 초과(다른 IP, 같은 email) → 429. ip-only 한도 초과(같은 IP, 다른 email) → 429. 둘 다 미초과 → 200.
- (E) `LoginAttemptTracker` 등 기존 동작 회귀 0. backend 전체 테스트 GREEN.
- (F) anti-enumeration 영향 없음 — 429는 “이 email/IP가 최근 시도되었음”만 노출, 가입 여부와 무관 (per-key 독립).
- (G) docs/02 §8 `RATE_LIMIT_EXCEEDED` 행과 코드의 응답 정합. 신규 에러 코드 추가 0건.

## 검증 게이트

- `cd backend && ./gradlew test` GREEN, 회귀 0 (a1.5 baseline).
- 신규 테스트: `ForgotPasswordRateLimiterTest` ≥6 케이스, `PasswordControllerForgotRateLimitTest` ≥6 케이스.
- frontend 영향 0 (forgot 페이지는 200/4xx 통일 envelope, 429 추가는 기존 toast 분기 재사용).

## 리스크 / 완화

- **분산 환경 부정합**: in-memory 키. 다중 인스턴스 시 인스턴스별 독립 카운터 → 한도 N배. → MVP 단일 인스턴스 가정(ADR #23 답습), 본 트랙 ADR에 “v1.x Redis 교체 진입점은 `ForgotPasswordRateLimiter` 인터페이스 추출”로 명시.
- **NAT/공유 IP 오탐**: 사내망 다수 사용자가 같은 외부 IP에서 시도 시 한 명만 통과. → 사내 베타 가정 + 분당 1회는 정상 사용에 마찰 없음(같은 사용자 60s 내 재시도 자체가 비정상). NAT 영향은 ADR에 limitation으로 기록.
- **timing-based 가입 enumeration 잔존**: rate limit은 SMTP cost/호출량 차단이지 a1.5 anti-enumeration의 “가입자/미가입자 응답 시간차”는 별도 문제. → 본 트랙 비범위. a1.5 plan §리스크에 이미 한계 명시. 본 트랙은 “호출 빈도 제한”만 책임.
- **테스트 시계 의존**: `Clock` 주입 필수. `Clock.fixed` 또는 advance 헬퍼 사용. LoginAttemptTracker 테스트 패턴 답습.

## 비범위 (명시)

- /login (LoginAttemptTracker 별도 보호 중), /reset, /change, /signup rate limit.
- Redis/분산 카운터 (v1.x).
- 슬라이딩 윈도우 / 토큰 버킷 정교화 (KISS — 분당 1회 fixed window로 충분).
- HIBP/zxcvbn (별도 password-policy 트랙).
