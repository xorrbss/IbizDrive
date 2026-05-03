# auth-forgot-rate-limit — Tasks

Last Updated: 2026-05-03

## Phase 상태

- P0 (worktree + dev-docs bootstrap): **in_progress**
- P1 (`ForgotPasswordRateLimiterTest` RED): pending
- P2 (tracker + exception + handler GREEN): pending
- P3 (controller 통합 + MockMvc TDD): pending
- P4 (docs sync + ADR): pending
- P5 (closure + PR open): pending

---

### P0 worktree + dev-docs

- [x] worktree `C:/project/IbizDrive/.claude/worktrees/auth-forgot-rate-limit` 생성, branch `feature/auth-forgot-rate-limit` (base `origin/master @fdb57c7`)
- [x] `dev/active/auth-forgot-rate-limit/` 3파일(`plan`/`context`/`tasks`) 생성
- [ ] `dev/process/auth-forgot-rate-limit.md` ownership 파일 생성
- [ ] bootstrap commit (`docs(auth-forgot-rate-limit): dev-docs bootstrap`)

---

### P1 `ForgotPasswordRateLimiterTest` (RED)

- [ ] `backend/src/test/java/com/ibizdrive/auth/password/ForgotPasswordRateLimiterTest.java` 신설.
- [ ] 케이스 ≥6:
  - (a) 신규 email/IP 1회차 `tryAcquire` → true (통과).
  - (b) 같은 email/IP 60s 내 2회차 `tryAcquire` → false (차단).
  - (c) 차단 후 `getRetryAfterSeconds(email, ip)` ∈ (0, 60].
  - (d) 60s 경과 후 동일 키 → true (lazy 만료).
  - (e) email-only 일치(다른 IP) → false. ip-only 일치(다른 email) → false.
  - (f) 둘 다 미초과(완전 새로운 email + 새로운 IP) → true.
  - (g) Clock 진행 시 `retryAfter` 감소 (LoginAttemptTracker 테스트 패턴 답습).
- [ ] `gradlew test --tests *ForgotPasswordRateLimiterTest*` → **RED** 명시(클래스 미존재 컴파일 실패도 RED로 인정 — 단, 이후 P2에서 GREEN으로 전환).

#### 작업 전 필독
- `backend/src/test/java/com/ibizdrive/auth/LoginAttemptTrackerTest.java` (테스트 구조 + Clock 진행 패턴) — 존재 확인 후 답습.
- `backend/src/main/java/com/ibizdrive/auth/LoginAttemptTracker.java` (mirror 대상).

#### 원본 코드 참조
- `backend/src/main/java/com/ibizdrive/auth/LoginAttemptTracker.java:55-115` (recordFailure / isLocked / getRetryAfterSeconds / Attempt record).

#### 검증 참조
- `cd backend && ./gradlew test --tests *ForgotPasswordRateLimiterTest*`

#### 문서 반영
- 없음 (P1은 RED 명시만).

---

### P2 tracker + exception + handler (GREEN)

- [ ] `backend/src/main/java/com/ibizdrive/auth/password/ForgotPasswordRateLimiter.java`:
  - `@Component`. `Clock` 주입(no-arg ctor → `Clock.systemUTC()`, package-private ctor → 테스트용).
  - `Map<String, Instant> emailHits`, `Map<String, Instant> ipHits` 분리.
  - `boolean tryAcquire(String emailKey, String ipKey)` — 두 맵 모두 `now - last < 60s` 판정. 통과 시 두 맵 모두 갱신, 차단 시 갱신 없음(차단 자체는 윈도우를 늘리지 않음).
  - `long getRetryAfterSeconds(String emailKey, String ipKey)` — 두 키 잔여시간 중 큰 값.
  - lazy 만료: `Instant`만 저장하므로 `now - last >= 60s`이면 자연 통과 + 갱신.
- [ ] `backend/src/main/java/com/ibizdrive/auth/password/RateLimitExceededException.java`:
  - `extends RuntimeException`. `final long retryAfterSeconds` + getter. 메시지 placeholder.
- [ ] `backend/src/main/java/com/ibizdrive/common/error/ErrorResponse.java`:
  - `public static ErrorResponse rateLimitExceeded(long retryAfterSeconds)` factory. `accountLocked` mirror.
- [ ] `backend/src/main/java/com/ibizdrive/common/error/AuthExceptionHandler.java`:
  - `@ExceptionHandler(RateLimitExceededException.class)` → `ResponseEntity.status(429).header("Retry-After", String.valueOf(ex.getRetryAfterSeconds())).body(ErrorResponse.rateLimitExceeded(ex.getRetryAfterSeconds()))`.
- [ ] P1 테스트 모두 GREEN. 기존 backend 테스트 회귀 0.

#### 작업 전 필독
- `backend/src/main/java/com/ibizdrive/auth/AccountLockedException.java` — exception 패턴.
- `backend/src/main/java/com/ibizdrive/common/error/ErrorResponse.java` — factory 시그니처/필드 노출 패턴.
- `backend/src/main/java/com/ibizdrive/common/error/AuthExceptionHandler.java:31-35` — 423 LOCKED + retryAfter 패턴 (429 mirror).

#### 원본 코드 참조
- `LoginAttemptTracker.java` 전체.

#### 구현 대상
- 위 4파일 (1 신설 + 3 수정).

#### 검증 참조
- `cd backend && ./gradlew test` → P1 + 회귀 0.
- `cd backend && ./gradlew compileJava` clean.

#### 문서 반영
- 없음 (P4에서 일괄).

---

### P3 controller 통합 + MockMvc TDD

- [ ] `backend/src/test/java/com/ibizdrive/auth/password/PasswordControllerForgotRateLimitTest.java` 신설.
- [ ] 케이스 ≥6 (MockMvc + `FixedClock` 또는 advance 헬퍼):
  - (a) 1회차 `POST /api/auth/password/forgot` (email=A, IP=X) → 200 + 기존 메시지.
  - (b) 60s 내 2회차 동일 (A, X) → 429 + `Retry-After` 헤더 정수 + body `code=RATE_LIMIT_EXCEEDED` + `retryAfterSeconds` ∈ (0, 60].
  - (c) 60s 경과 advance 후 (A, X) → 200 회복.
  - (d) (A, Y — 다른 IP) → email 한도로 429.
  - (e) (B, X — 다른 email) → ip 한도로 429.
  - (f) (B, Y — 둘 다 새 키) → 200.
  - (g) 미가입자 email로 (a)~(c) 반복 → 가입자와 동일 코드 경로 (anti-enumeration 정합).
- [ ] `PasswordController.forgot()` 수정:
  - `HttpServletRequest httpReq` 파라미터 추가.
  - `String emailKey = req.email().trim().toLowerCase(Locale.ROOT)` (login/signup 정규화 정합).
  - `String ipKey = resolveClientIp(httpReq)` — 헬퍼 신설. `X-Forwarded-For` 첫 값 → fallback `getRemoteAddr()`.
  - `if (!rateLimiter.tryAcquire(emailKey, ipKey)) throw new RateLimitExceededException(rateLimiter.getRetryAfterSeconds(emailKey, ipKey));`
  - 통과 시 기존 `requestReset` 위임.
- [ ] `WARN` 로그 1줄 — rate-limited 시 `email`은 마스킹(`a***@b.com`) + IP는 그대로(사내 베타 가정).

#### 작업 전 필독
- `backend/src/test/java/com/ibizdrive/auth/password/PasswordControllerTest.java` 또는 a1.5 closure 시 신설된 forgot 테스트(존재 확인) — MockMvc 패턴.
- `backend/src/main/java/com/ibizdrive/auth/password/PasswordController.java:55-59` — 수정 대상.
- `backend/src/main/java/com/ibizdrive/auth/SecurityConfig.java` — `/api/auth/password/forgot` permitAll/csrf 설정 변경 없음 확인.

#### 구현 대상
- `PasswordController.java` (수정), `PasswordControllerForgotRateLimitTest.java` (신설).

#### 검증 참조
- `cd backend && ./gradlew test --tests *PasswordControllerForgotRateLimit*`
- `cd backend && ./gradlew test` 전체 회귀 0.

#### 문서 반영
- 없음 (P4).

---

### P4 docs sync + ADR

- [ ] `docs/02-backend-data-model.md` §7.4 `POST /api/auth/password/forgot` 응답 코드 표에 `429 RATE_LIMIT_EXCEEDED` 행 추가 + `Retry-After` 헤더 명시.
- [ ] `docs/03-security-compliance.md` §2 (인증 흐름 또는 §2.7 비밀번호 분실)에 1줄: “forgot은 email + IP 분당 1회. 초과 시 429 + Retry-After. in-memory single-instance(ADR #44).”
- [ ] `docs/00-overview.md` §5 ADR — ADR #44 추가:
  - 제목: `forgot rate limit (in-memory, email+IP, 분당 1회)`.
  - 결정: in-memory ConcurrentHashMap + Clock + lazy 만료. 두 키 독립 버킷, OR 차단.
  - 대안 거부: Bucket4j(외부 의존), Redis(v1.x), 슬라이딩 윈도우(YAGNI).
  - 한계: 다중 인스턴스 시 카운터 N배 — 인터페이스 추출은 v1.x.
- [ ] `BETA-RELEASE.md` §5 인증/세션 표에 1행: `forgot 분당 1회 ✓ (ADR #44)`.

#### 작업 전 필독
- `docs/02-backend-data-model.md` §7.4 (라인 grep으로 위치 특정 후 view).
- `docs/00-overview.md` §5 (ADR #43까지 확인 → #44 위치).
- `docs/03-security-compliance.md` §2.7 (a1.5 closure 시 추가된 섹션).

#### 검증 참조
- 코드 ↔ docs 정합 — 응답 코드, 헤더, 키 정책.

---

### P5 closure + PR open

- [ ] `docs/progress.md` 최상단에 본 트랙 entry 추가 (a1.5/auth-pages closure 형식 답습).
- [ ] `dev/active/auth-forgot-rate-limit/` → `dev/completed/auth-forgot-rate-limit/` 이동.
- [ ] `dev/process/auth-forgot-rate-limit.md` 종료 마커.
- [ ] commit + push + `gh pr create`.
- [ ] PR 본문에 acceptance criteria 체크리스트 + ADR #44 링크 + 회귀 0 명시.

#### 검증 참조
- `cd backend && ./gradlew test` final GREEN.
- `gh pr view` open 확인.

#### 문서 반영
- `docs/progress.md` (closure entry).

---

## 게이트

- **G1 (트랙 선정)**: ✓ 2026-05-03 사용자 ack.
- **G2 (P3 종료)**: controller 통합 GREEN 후 사용자 sign-off → P4 진입.
- **G3 (P5 closure)**: PR open 후 사용자 review/merge.
