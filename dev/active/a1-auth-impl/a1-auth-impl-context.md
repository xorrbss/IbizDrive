---
Last Updated: 2026-04-26
---

# A1 Auth Implementation — Context

## SESSION PROGRESS

- 2026-04-26 [세션 1, 완료, commit 10a524b]: dev/active bootstrap. A1.2 SecurityConfig 본 wiring + CsrfTokenController + SecurityIntegrationTest 5건 PASS. CSRF deferred 함정(saveToken 명시 호출) 발견 + 해결.
- 2026-04-26 [세션 2, 완료, commit 06b9238]: A1.3 LoginController + AuthService + LoginAttemptTracker (in-memory ConcurrentHashMap, ADR #23). 5/15min lockout, timing-safe (@PostConstruct dummy hash, 비활성·잠금 INVALID_CREDENTIALS 매핑), session fixation defended (changeSessionId), last_login_at 갱신. AuthExceptionHandler → flat error shape (docs/02 §7.4). LoginAttemptTrackerTest 4/4 + LoginControllerIntegrationTest 8/8 PASS. ADR #23 docs/00 §5 등록. 컨텍스트 9% 한계로 dev sync는 다음 세션 이월.
- 2026-04-26 [세션 3, 완료, commits 6591d1b/730c978/ca4e309]: dev-docs-update + HANDOFF cleanup → A1.4 (/me + /logout) 구현. **A1.3 hidden gap 발견 + 수정**: Spring Security 6의 SecurityContextHolderFilter는 load-only(5.x auto-save 제거). AuthService.login이 SecurityContext를 영속화하지 않아 후속 /me는 항상 401. SecurityConfig에 DelegatingSecurityContextRepository 빈 + securityContext() wire 추가, AuthService.login에 HttpServletResponse + saveContext 명시 호출. /me는 LoginResponse 재사용 (KISS, docs/02 §7.4 shape 동일). 테스트 4 클래스 22건 PASS. 컨텍스트 71%로 A1.5 미진입, pause-work.

## Current Execution Contract

- 사용자 큐: A1.4 → A1.5 (잔여 2 phase 자율 실행)
- 모드: autonomous (spec/plan 승인 게이트 없음)
- TDD 강제: superpowers:test-driven-development RED → GREEN
- 디버깅: superpowers:systematic-debugging
- commit 전: superpowers:verification-before-completion
- 매 phase commit 직전 `./gradlew test` PASS 강제
- 컨텍스트 35% → 현재 phase wrap-up, 25% → /gsd-pause-work 자체 실행. 본 세션 사용자 지시: A1.4 종료 시점에 65% 초과면 자동 pause-work, 아니면 A1.5까지 이어감
- 중단 기준: ADR/CLAUDE.md §3 위반, DB breaking change, endpoint 매트릭스 외 신규, 외부 자격증명 필요, 같은 에러 3회 실패

## 현재 active task

**A1.5 — 통합 시나리오 + 마일스톤 종료**

next concrete actions (다음 세션 첫 작업):
1. `docs/progress.md` 최상단에 A1.4 세션 블록 추가 (commit ca4e309, hidden gap fix 강조, /me=LoginResponse 재사용 결정)
2. `AuthScenarioIntegrationTest` (@SpringBootTest + Testcontainers Postgres 15) 1건 종합 시나리오 — CSRF 발급 → 로그인 → /me → 4회 실패 → 5회 실패→423 → lockout 만료 (Clock fake) → 다시 성공 → /logout → /me=401
3. `./gradlew test` 전체 PASS
4. `gsd-audit-milestone A1` 실행 (드리프트 0 검증)
5. `progress.md` A1 마일스톤 종료 블록
6. commit `test(A1): integration scenarios`
7. `git push` + `gh pr checks` 그린 또는 PR 생성

### 이전 active (A1.4) — 완료

**A1.4 — `GET /api/auth/me` + `POST /api/auth/logout`** ✅ ca4e309

next concrete actions:
1. `docs/02 §7.4` (/me, /logout) + ADR #22 (`/me` 응답 최소화) 정독
2. `AuthMeLogoutIntegrationTest` 5건 RED 작성 (@WebMvcTest slice, A1.3 LoginControllerIntegrationTest 패턴 참조 — `LoginAttemptTracker` singleton 격리는 본 테스트엔 무관, /me /logout는 tracker 미사용)
3. `MeResponse` DTO 신규
4. `AuthController`에 `me`, `logout` 메서드 추가 (login은 그대로 유지)
5. `/me` 응답 (docs/02 §7.4 line 847-868) = `{ user: { id, email, name, kind, mustChangePassword }, departments: [], roles: [role], effectivePermissionsCacheKey }`. departments는 M2-M3 phase까지 빈 배열 stub. kind는 User entity에 없음 → 'human' 하드코딩 (서비스 계정은 docs/03 §2.8 후속). roles는 User.role 단일값을 배열로 wrap. name은 User.displayName. cacheKey는 `userId + ":" + role.name() + ":v0"`. 미인증 → 401 `{ reason: 'NO_SESSION' }` flat shape + `Set-Cookie SESSION=; Max-Age=0`
6. `/logout` (docs/02 §7.4 line 836-845): isAuthenticated 가드, CSRF 필수, 204 응답, `Set-Cookie SESSION=; Max-Age=0; Path=/`. 구현: `request.getSession(false).invalidate()`. 미인증 시 401 (anyRequest authenticated가 자동 처리). CSRF 실패 시 403 CSRF_MISMATCH (Spring 자동)
7. SecurityConfig 매처 변경 불필요 — `/api/auth/me`, `/api/auth/logout` 모두 `anyRequest authenticated`로 자동 커버. permitAll 매처 추가 금지
8. `./gradlew test` PASS
9. commit `feat(A1.4): /api/auth/me + logout`

## 다음 세션 읽기 순서

1. `a1-auth-impl-plan.md` — 전체 phase 지도 + acceptance
2. `a1-auth-impl-tasks.md` — 체크박스 + 파일 참조
3. `a1-auth-impl-context.md` — 본 파일 (SESSION PROGRESS + 결정)
4. (필요 시) `docs/03 §2`, `docs/02 §7.4`, `docs/00 §5 ADR #22, #23` 본문

## 핵심 파일과 역할

| 파일 | 역할 |
|---|---|
| `backend/src/main/java/com/ibizdrive/config/SecurityConfig.java` | A1.2 완료. A1.4에서 logout 매처 검토 필요 |
| `backend/src/main/java/com/ibizdrive/auth/CsrfTokenController.java` | A1.2 완료 |
| `backend/src/main/java/com/ibizdrive/auth/AuthController.java` | A1.3 완료 (login). A1.4에서 me/logout 추가 |
| `backend/src/main/java/com/ibizdrive/auth/AuthService.java` | A1.3 완료. A1.4에선 변경 없음 (logout은 controller에서 직접 session.invalidate) |
| `backend/src/main/java/com/ibizdrive/auth/LoginAttemptTracker.java` | A1.3 완료. A1.4 무관 |
| `backend/src/main/java/com/ibizdrive/auth/dto/LoginRequest.java` / `LoginResponse.java` | A1.3 완료 |
| `backend/src/main/java/com/ibizdrive/common/error/ErrorResponse.java` | A1.3 완료. JsonInclude.NON_NULL |
| `backend/src/main/java/com/ibizdrive/common/error/AuthExceptionHandler.java` | A1.3 완료 |
| `backend/src/main/java/com/ibizdrive/auth/{Invalid,AccountLocked}Exception.java` | A1.3 완료 |
| `backend/src/main/java/com/ibizdrive/user/User.java` | A1.0/A1.1 + A1.3 `recordLoginAt` 추가 완료 |
| `backend/src/main/java/com/ibizdrive/user/DbUserDetailsService.java` | A1.1 완료 |
| `backend/src/main/java/com/ibizdrive/user/IbizDriveUserDetails.java` | A1.1 완료 |
| **신규** `backend/src/main/java/com/ibizdrive/auth/dto/MeResponse.java` | A1.4 |
| **신규** `backend/src/test/java/com/ibizdrive/auth/AuthMeLogoutIntegrationTest.java` | A1.4 |

## 중요한 의사결정

### ADR #23 (A1.3에서 docs/00 §5에 등록 완료)

Lockout backing store = in-memory `ConcurrentHashMap`. 단일 인스턴스 가정, 다중 인스턴스/Redis 도입 시점에 `LoginAttemptTracker` interface 교체. 키는 lowercased email. 5회 실패 → 15분 잠금. 만료는 lazy 검증 (조회 시점에 시계 기반 판정, `Clock` 주입).

### A1.3 timing-safe dummy hash (동적 생성)

`@PostConstruct`에서 `passwordEncoder.encode("dummy-timing-safe-value")` 호출하여 dummy hash 생성. 정적 상수는 형식 오류 시 BCrypt iterations 미실행 → timing leak 위험. 동적 생성으로 항상 유효 hash 보장. 부팅 +200ms 수용.

### A1.3 비활성·관리자 잠금도 INVALID_CREDENTIALS

docs/03 §2.3 enumeration 방지. `is_active=false` / `locked_at != null` 케이스에도 `passwordEncoder.matches`(dummy verify) 호출하여 시간 균등화. 응답은 INVALID_CREDENTIALS (계정 상태 누설 금지). 잠금 자체는 별개 — 5회 누적 시 ACCOUNT_LOCKED + retryAfterSec.

### A1.3 에러 응답 = flat shape

docs/02 §7.4 인증 specific 응답 = flat (`code`, `reason`, `retryAfterSec`). 일반 envelope §7.2와 별개. ErrorResponse는 `JsonInclude.NON_NULL`로 retryAfterSec 옵션 처리.

### A1.3 @Transactional은 AuthService.login

`last_login_at` UPDATE + (후속) audit insert를 단일 단위로. docs/02 §7.4 TX=REQUIRED 컬럼 충족.

### A1.3 LoginControllerIntegrationTest 격리 패턴

@WebMvcTest slice에서 `LoginAttemptTracker`는 singleton bean. 테스트 간 상태 격리 위해 `@BeforeEach`에서 사용 예정 키들에 대해 `tracker.recordSuccess()` 호출하여 카운터 리셋. A1.4도 tracker를 직접 사용하진 않지만 `AuthController` 빈 컨텍스트가 tracker 빈을 끌어오므로 주의.

### `effectivePermissionsCacheKey` 임시값

A1.4 `/me` 응답에 포함되는 cacheKey는 권한 매트릭스 백엔드 (A1.5 후속) 도입 전까지 `userId + ":" + role.name() + ":v0"` 문자열로 단순화. ADR 불필요 (내부 캐시 키, 외부 계약 변경 없음).

### A1.2 CSRF endpoint 위치

`/api/auth/csrf`는 `CsrfTokenController`로 분리. login/logout/me는 `AuthController`로 통합. CSRF는 인증 전에 호출되며 permitAll 매처라 책임 경계 분리.

### A1.2 CSRF deferred 함정

Spring Security 6 deferred 모드에서 `csrfToken.getToken()` 호출만으로 cookie 자동 발급 보장 안 됨 → controller에서 `CookieCsrfTokenRepository.saveToken(token, req, res)` 명시 호출 필수.

### A1.2 timing attack 회피 (개념 → A1.3에서 구현)

미존재 email에도 dummy BCrypt hash로 `passwordEncoder.matches` 호출 → 실제 verify와 동일 시간 소비. dummy hash는 `AuthService` 인스턴스 필드 (@PostConstruct 동적 생성).

### A1.2 session fixation

`HttpServletRequest.changeSessionId()`를 로그인 성공 직후 호출. Spring Session JDBC가 새 ID로 row를 INSERT. A1.3에서 구현 완료.

### A1.5 재정의 (사용자 자율 결정)

HANDOFF.json의 A1.5 (권한 매트릭스 백엔드 권위 — `@PreAuthorize` + `PermissionService`)는 이번 작업에서 통합 시나리오 + 마일스톤 종료로 재정의. 권한 매트릭스 백엔드는 별도 phase (A1.5 후속 또는 A2 직전)로 분리.

## 빠른 재개 안내

세션이 끊겼다면:
1. `git log --oneline -10`로 마지막 commit 확인 (06b9238 = A1.3)
2. `tasks.md` 체크박스 보고 마지막 완료 항목의 다음 항목으로 진입
3. `dev/process/*.md`에 다른 세션 작업 파일이 없는지 확인 (현재 비어 있음)
4. 진행 중이던 RED 테스트가 commit 안 되었으면 `git status` 확인 후 재작성
5. `./gradlew test`로 현재 상태 검증
