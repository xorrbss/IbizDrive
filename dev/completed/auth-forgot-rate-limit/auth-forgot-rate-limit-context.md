# auth-forgot-rate-limit — Context

Last Updated: 2026-05-03

## SESSION PROGRESS

- 2026-05-03 P0 — worktree `feature/auth-forgot-rate-limit` 생성(@origin/master fdb57c7), dev-docs 3파일 + dev/process ownership 작성, bootstrap commit 대기.

## Current Execution Contract

- **자율 모드 + 게이트** (memory: feedback_autonomous_mode). G1 트랙 선정 ack 완료(2026-05-03). 다음 게이트는 P3 종료 시점(controller 통합 GREEN) — 사용자 sign-off 후 P4 docs sync 진입.
- **TDD 강제** (superpowers:test-driven-development). 모든 phase는 RED → GREEN → REFACTOR.
- **원자적 변경** (CLAUDE.md user-global). phase별 독립 커밋, 1 PR.
- **외부 의존 0** — `LoginAttemptTracker` 패턴 답습으로 신규 라이브러리 도입 금지.

## 현재 active task

**P0 — bootstrap commit 대기**. 다음: P1 (`ForgotPasswordRateLimiterTest` RED).

## 다음 세션 읽기 순서

1. `dev/active/auth-forgot-rate-limit/auth-forgot-rate-limit-plan.md` — 범위·목표·acceptance.
2. `dev/active/auth-forgot-rate-limit/auth-forgot-rate-limit-tasks.md` — phase 상태 + 다음 task의 작업 전 필독·참조.
3. 본 파일 SESSION PROGRESS — 직전 세션 결정.
4. `backend/src/main/java/com/ibizdrive/auth/LoginAttemptTracker.java` — mirror 대상 패턴.
5. `backend/src/main/java/com/ibizdrive/auth/password/PasswordController.java:55-59` — limiter 삽입 지점.
6. `backend/src/main/java/com/ibizdrive/common/error/AuthExceptionHandler.java:31-35` + `ErrorResponse.java` — 429 응답 mirror 대상.

## 핵심 파일과 역할

| 파일 | 역할 | 상태 |
|---|---|---|
| `backend/src/main/java/com/ibizdrive/auth/password/ForgotPasswordRateLimiter.java` | (신설) email/IP 키 분당 1회 in-memory tracker | P2에서 생성 |
| `backend/src/test/java/com/ibizdrive/auth/password/ForgotPasswordRateLimiterTest.java` | (신설) tracker 단위 테스트 6+ 케이스 | P1에서 생성 (RED) |
| `backend/src/main/java/com/ibizdrive/auth/password/RateLimitExceededException.java` | (신설) `retryAfterSeconds` 보유 RuntimeException | P2 |
| `backend/src/main/java/com/ibizdrive/common/error/AuthExceptionHandler.java` | (수정) `@ExceptionHandler(RateLimitExceededException)` → 429 + `Retry-After` | P2 |
| `backend/src/main/java/com/ibizdrive/common/error/ErrorResponse.java` | (수정) `rateLimitExceeded(long retryAfterSeconds)` factory 추가 | P2 |
| `backend/src/main/java/com/ibizdrive/auth/password/PasswordController.java` | (수정) `forgot()`에 limiter check 1줄 + `HttpServletRequest` 주입(IP 추출) | P3 |
| `backend/src/test/java/com/ibizdrive/auth/password/PasswordControllerForgotRateLimitTest.java` | (신설) MockMvc 통합 테스트 6+ 케이스 | P3 (RED+GREEN) |
| `docs/02-backend-data-model.md` §7.4 forgot | (수정) 응답 코드 표에 429 행 추가 | P4 |
| `docs/03-security-compliance.md` §2 | (수정) 1줄 — “forgot 분당 1회 email+IP” | P4 |
| `docs/00-overview.md` §5 ADR | (수정) ADR #44 신규 — in-memory rate limit, single-instance MVP | P4 |
| `BETA-RELEASE.md` §5 | (수정) 인증 보강 항목 1행 | P4 |

## 중요한 의사결정

1. **2 키 독립 버킷 (email + IP)** — 어느 한쪽 한도 초과면 차단. AND 조합은 NAT 환경에서 회피 쉬움(다른 사용자 가장), OR 조합은 NAT 오탐 위험이지만 사내 베타 가정 + 분당 1회로 정상 사용 마찰 없음.
2. **고정 윈도우 60s, 한도 1회** — KISS. 슬라이딩/토큰 버킷은 YAGNI.
3. **In-memory + single-instance 가정** — `LoginAttemptTracker` 패턴 답습(ADR #23 정합). 인터페이스 추출하지 않음(YAGNI). v1.x 다중 인스턴스 진입 시점에 일괄 추출.
4. **Audit 미발행, 로그만** — rate-limited 이벤트는 리소스 도메인이 아니라 메타. `WARN` 로그(email + ip 마스킹) 1줄. audit_log 테이블 오염 회피.
5. **HttpServletRequest IP 추출**: `X-Forwarded-For` 첫 값 우선, 없으면 `getRemoteAddr()`. 사내 베타에서 reverse proxy 경유 가정. ADR에 한계 명시(spoofable in dev, prod에서는 trusted proxy 정책 별도 트랙).
6. **Anti-enumeration 영향 없음** — 429는 “이 email/IP가 최근 시도되었음”만 노출. 가입 여부 무관 (per-key 독립 버킷, 가입자/미가입자 동일 카운트).

## 빠른 재개 안내

```
cd C:/project/IbizDrive/.claude/worktrees/auth-forgot-rate-limit
git status                                   # clean 확인
cat dev/active/auth-forgot-rate-limit/auth-forgot-rate-limit-tasks.md   # 현재 phase + 다음 task
./gradlew test --tests *LoginAttemptTracker* # baseline 패턴 확인
```

다음 작업자 첫 액션: `tasks.md`에서 미완료 phase 첫 항목 진입. P1이면 `ForgotPasswordRateLimiterTest` RED부터.

## 블로커

- PR #49 (be-test-regression-m38-m42) admin merge 대기 중. 본 트랙 surface와 무관(SecurityFilterChain·테스트 격리 영역 다름)이므로 병행 진행. 머지 시 origin/master rebase 1회로 동기화.
