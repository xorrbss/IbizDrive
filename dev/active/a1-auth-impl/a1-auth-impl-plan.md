---
Last Updated: 2026-04-26
---

# A1 Auth Implementation — Plan (A1.2 ~ A1.6)

## 요약

Spring Security 6 + Spring Session JDBC 기반 자체 ID/PW 인증을 A1.2~A1.5 단위로 본 wiring한다. 현재 A1.0(스키마/JPA), A1.1(PasswordEncoder + DbUserDetailsService) 완료. 이번 작업은 SecurityConfig 본 wiring → LoginController + lockout → /me + logout → 통합 시나리오 + 마일스톤 종료까지.

## 현재 상태 분석

| 영역 | 상태 |
|---|---|
| `users` 테이블 (V1 + V2) | ✅ 완료 (308c041) — `password_hash`, `role`, `is_active`, `locked_at`, `must_change_password`, `last_login_at`, `deleted_at` |
| `User` JPA entity + `Role` enum | ✅ 완료 |
| `UserRepository.findActiveByEmail` (Testcontainers 검증) | ✅ 완료 |
| `PasswordEncoder` bean (BCrypt strength=12, `{bcrypt}` 프리픽스, ADR #19) | ✅ 완료 (0dd2d65) |
| `IbizDriveUserDetails` 어댑터 (도메인 User 노출) | ✅ 완료 |
| `DbUserDetailsService` (email lowercase, isLocked/isActive 게이트) | ✅ 완료 |
| `SecurityConfig` skeleton (`/api/health` permitAll, anyRequest authenticated, httpBasic, CSRF cookie repo) | ⚠ skeleton만 존재 — A1.2 본 wiring 필요 |
| `application.yml` (`spring.session.store-type=jdbc`, `initialize-schema=never`, timeout 8h, SESSION 쿠키 정책) | ✅ 완료 |
| `/api/auth/csrf`, `/login`, `/logout`, `/me` endpoint | ❌ 미구현 |
| Lockout backing store | ❌ 미결정 → ADR #23로 in-memory ConcurrentHashMap 채택 (사용자 자율 결정) |
| 통합 시나리오 테스트 | ❌ 미작성 |
| 마일스톤 종료 audit | ❌ 미수행 |

## 목표 상태

A1 마일스톤 종료 시점:

- 인증 4 endpoint (`POST /login`, `POST /logout`, `GET /me`, `GET /csrf`) 완전 작동
- 5회 실패 → 15분 lockout (in-memory `ConcurrentHashMap`) 작동, 성공 시 reset
- CSRF double-submit 강제 (mutation endpoint 모두)
- 세션 정책: idle 30분 sliding + absolute 8시간 (Spring Session JDBC 백엔드)
- `gradle test` 전체 PASS (단위 + Testcontainers 통합)
- progress.md A1 종료 블록 작성, gsd-audit-milestone 통과
- CI 그린 + master ahead-of N 상태로 PR 후보 commit 정렬

## Phase 실행 지도

### A1.2 — SecurityConfig 본 wiring (TDD)

**Spec 근거**: docs/03 §2.2 (세션 모델), §2.3 (로그인 시퀀스), docs/02 §7.4 (auth endpoint 표), ADR #12, #20.

**RED — 통합 테스트 먼저** (`SecurityIntegrationTest`):
1. `GET /api/auth/csrf` → 200 + JSON `csrfToken` + `XSRF-TOKEN` 쿠키 (HttpOnly=false)
2. `POST /api/some-mutation-stub` (CSRF 누락) → 403
3. `POST /api/some-mutation-stub` (CSRF 정상) → 통과 (anyRequest authenticated이므로 401 이지만 CSRF는 통과)
4. `GET /api/auth/me` (미인증) → 401
5. `GET /api/health` (anonymous) → 200

**GREEN — 구현**:
- `CsrfTokenController` (`/api/auth/csrf` GET — Spring Security `CsrfToken` 직접 반환)
- `SecurityConfig.securityFilterChain`:
  - `httpBasic` 제거
  - `formLogin` 미사용 (custom LoginController로 대체할 예정 — A1.2에서는 정의만)
  - 매처 분리: `/api/health`, `/api/auth/csrf` permitAll, 나머지 authenticated
  - `sessionManagement`: `IF_REQUIRED` 유지 (Spring Session이 timeout 관리)
  - CSRF 정책 유지 (`CookieCsrfTokenRepository.withHttpOnlyFalse()`)

**Acceptance criteria**:
- [ ] 통합 테스트 5건 모두 PASS
- [ ] httpBasic 제거됨
- [ ] `/api/auth/csrf` endpoint 응답 검증
- [ ] CSRF 토큰 누락 POST → 403

**Validation gate**: `./gradlew test` 전체 PASS.

**Commit**: `feat(A1.2): SecurityConfig wiring + CSRF double-submit`

### A1.3 — LoginController + Lockout (TDD)

**Spec 근거**: docs/02 §7.4, docs/03 §2.3, §2.6, §2.10, ADR #20, **ADR #23 (신규)**.

**ADR #23 (자율 채택)**:
> Lockout 카운터 backing store = in-memory `ConcurrentHashMap`. MVP 단일 인스턴스 가정. 다중 인스턴스/Redis 도입 시점에 별도 ADR로 교체. 키는 lowercased email. 5회 실패 → 15분 잠금. 잠금 만료는 시계 기반 lazy 검증 (조회 시점에 만료 판정).

**RED — LoginController 통합 테스트** (`LoginControllerIntegrationTest`):
1. POST `/api/auth/login` 정상 → 200 + Set-Cookie `SESSION` + 응답 body
2. 잘못된 PW → 401 `INVALID_CREDENTIALS` + 카운터 INCR
3. 미존재 email → 401 `INVALID_CREDENTIALS` (timing-safe — BCrypt dummy verify)
4. 5회 실패 후 6회째 → 423 `ACCOUNT_LOCKED` + `retryAfterSec`
5. 비활성 계정 (`is_active=false`) → 401 (UserDetails.isEnabled false → DisabledException → 동일 응답)
6. soft-deleted (`deleted_at IS NOT NULL`) → 401 (UserRepository에서 안 뜸 → email 미존재 처리)
7. `must_change_password=true` 응답에 표시
8. CSRF 위반 → 403

**RED — LoginAttemptTracker 단위 테스트**:
1. 새 키 0 → INCR → 1
2. 5번 INCR → isLocked() true, retryAfter > 0
3. 시간 경과 후 (clock fake) → isLocked() false, count reset
4. 성공 reset → count = 0

**GREEN — 구현**:
- `LoginAttemptTracker` (in-memory, `Clock` 주입 testable)
- `LoginRequest` DTO + Bean Validation
- `LoginResponse` DTO (`{ user, departments=[], roles, effectivePermissionsCacheKey }`)
- `AuthService.login(email, password)`:
  - email lowercase
  - lockout 검사 → locked → 423
  - UserRepository.findActiveByEmail
  - 미존재 → 더미 BCrypt verify (timing) → 401 + INCR
  - PasswordEncoder.matches
  - 실패 → INCR + 401
  - 성공 → reset + `last_login_at` 갱신 + session attribute set + `changeSessionId()` 호출 (HttpServletRequest)
- `LoginController` (`@PostMapping("/api/auth/login")`)
- `errors.ErrorResponse` (코드 매핑 헬퍼)

**Acceptance criteria**:
- [ ] 통합 테스트 8건 PASS
- [ ] LoginAttemptTracker 단위 테스트 4건 PASS
- [ ] timing attack 회피 (미존재 user에도 BCrypt verify 더미 수행)
- [ ] session fixation 방어 (`changeSessionId`)
- [ ] ADR #23 docs/00 §5에 추가됨

**Validation gate**: `./gradlew test` PASS.

**Commit**: `feat(A1.3): LoginController + in-memory lockout (ADR #23)`

### A1.4 — /me + Logout (TDD)

**Spec 근거**: docs/02 §7.4, docs/03 §2.5, ADR #22.

**RED — 통합 테스트** (`AuthControllerIntegrationTest`):
1. GET `/api/auth/me` 인증 → 200 + 스키마 (`{ user: {id, email, name, kind, mustChangePassword}, departments: [], roles: [], effectivePermissionsCacheKey }`)
2. GET `/me` 미인증 → 401
3. POST `/logout` 인증 → 204 + Set-Cookie `SESSION=; Max-Age=0`
4. logout 후 동일 SESSION 재사용 GET `/me` → 401
5. CSRF 위반 logout → 403

**GREEN — 구현**:
- `AuthController` (LoginController와 통합) — `/me`, `/logout`
- `MeResponse` DTO (`/me` 전용)
- `effectivePermissionsCacheKey` — A1 범위 미정. ADR #22 본문상 권한 변경 시 invalidate trigger. MVP는 user `id + role + updated_at` 기반 hash 또는 단순 `userId:role` 문자열로 시작. 별도 PermissionService 미존재 시점이라 안정적 문자열로 충분 (A1.5 권한 매트릭스 진입 시 갱신).
  - 결정: `cacheKey = userId + ":" + role.name() + ":v0"` — 권한 변경 endpoint 도입 시점에 hash로 교체. 본 결정 plan 본문에 기록 (별도 ADR 불필요 — 내부 캐시 키 정책).
- 로그아웃: `HttpServletRequest.getSession(false).invalidate()` + 쿠키 만료 응답

**Acceptance criteria**:
- [ ] 통합 테스트 5건 PASS
- [ ] `/me` 응답이 ADR #22 형식과 일치 (effectivePermissions full resolve 미포함)
- [ ] logout 후 세션 재사용 차단

**Validation gate**: `./gradlew test` PASS.

**Commit**: `feat(A1.4): /api/auth/me + logout`

### A1.5 — 통합 시나리오 + 마일스톤 종료

> **Note**: HANDOFF.json의 A1.5 (권한 매트릭스 백엔드 권위)는 본 작업에서 **재정의**됨 — 사용자 자율 결정. 권한 매트릭스 백엔드(@PreAuthorize + PermissionService)는 별도 마일스톤(A1.5 후속 또는 A2 직전)으로 분리. 본 A1.5는 통합 시나리오 + 종료 audit.

**Spec 근거**: docs/03 §2 전체.

**구현**:
- `AuthScenarioIntegrationTest` (@SpringBootTest + Testcontainers Postgres) — 1건의 종합 시나리오:
  1. CSRF 발급
  2. 로그인 성공
  3. `/me` 200
  4. 4회 실패 → 카운터 4
  5. 5회 실패 → 423
  6. lockout TTL 경과 (Clock fake) → 다시 시도 → 200 (성공)
  7. 로그아웃 → 204
  8. 로그아웃 후 `/me` → 401

**Acceptance criteria**:
- [ ] 통합 시나리오 PASS
- [ ] `progress.md` A1 마일스톤 종료 블록 작성
- [ ] gsd-audit-milestone 통과 (드리프트 0)

**Validation gate**: `./gradlew test` PASS + `git push` + `gh pr checks 1` 그린 또는 PR 미존재 안내.

**Commit**: `test(A1): integration scenarios`

### A1.6 — Session Timeout Policy (TDD) [must-fix #1 close]

**배경**: A1.5 audit (`a1-auth-impl-audit.md` §4 must-fix #1)에서 idle 30분 sliding + absolute 8h가 미구현으로 분류. 사용자 결정으로 (A) accepted-deviation 대신 **본 phase에서 즉시 fix** 채택.

**Spec 근거**: docs/00 §5 ADR #20, docs/03 §2.6, audit §4 must-fix #1.

**RED — `SessionValidityFilterTest` (단위, Mockito)**:
1. 세션 없음 (`req.getSession(false) == null`) → 필터 pass-through (`FilterChain.doFilter` 호출, status 미변경)
2. 세션은 있으나 `issuedAt` 속성 없음 → pass-through (인증 전 요청)
3. `now - issuedAt < 8h` → pass-through, sliding 갱신은 Spring HttpSession이 자동 (`lastAccessedTime`은 컨테이너 관리)
4. `now - issuedAt >= 8h` → `session.invalidate()` + `response.sendError(401)` + `FilterChain.doFilter` 미호출
5. `Clock` 주입으로 시간 진행 검증 (8h 경계 한 틱 전/후)

**GREEN — 구현**:
- `SessionValidityFilter` (신규 `OncePerRequestFilter`):
  - `Clock` 주입 (생성자, `@Component` + Spring이 `Clock` 빈 주입)
  - `doFilterInternal`:
    1. `HttpSession session = req.getSession(false)`
    2. `session == null` → `chain.doFilter` → return
    3. `Object issuedAtObj = session.getAttribute("issuedAt")`
    4. `issuedAtObj == null` (또는 Long 아님) → `chain.doFilter` → return
    5. `long age = clock.millis() - (Long) issuedAtObj`
    6. `age >= ABSOLUTE_TTL_MS` (8h) → `session.invalidate()` + `res.sendError(401)` → return (chain 미호출)
    7. else → `chain.doFilter`
  - 상수: `ABSOLUTE_TTL_MS = Duration.ofHours(8).toMillis()`
- `SecurityConfig`:
  - `Clock` `@Bean` 추가 (운영 = `Clock.systemUTC()`)
  - `addFilterAfter(sessionValidityFilter, SecurityContextHolderFilter.class)` — SecurityContext 로드 직후 절대 만료 검사
- `application.yml`:
  - `spring.session.timeout: PT8H` → `PT30M` (idle 30분, Spring Session JDBC가 자동 invalidate)
- `AuthService.login`의 `issuedAt` 세션 attribute는 이미 set 중 (변경 불필요).

**Acceptance criteria**:
- [ ] 단위 테스트 4건 (RED #1~#4) PASS
- [ ] 기존 152 tests 회귀 0
- [ ] `application.yml` `spring.session.timeout` 변경 commit 포함
- [ ] `audit.md` must-fix #1 → RESOLVED 갱신 (별도 commit, A1 close 블록)
- [ ] `SecurityConfig`에 `Clock` `@Bean` + filter wiring 추가

**설계 결정 (filter SoT)**:
- **idle 만료** = Spring Session JDBC (`spring.session.timeout: PT30M`)가 진실 출처. `lastAccessedTime` 비교는 컨테이너가 매 요청마다 자동 처리.
- **absolute 만료** = `SessionValidityFilter`가 진실 출처. `issuedAt` 속성 + 8h 경계.
- yml은 idle 정책의 적용 매개. filter 없이 yml만 두면 ADR #20 absolute 한도가 강제되지 않음 (audit must-fix #1 정확 사유).

**Validation gate**: `./gradlew test` PASS.

**Commit**: `feat(A1.6): session timeout policy (idle 30m sliding + absolute 8h)`

## 검증 게이트 (모든 phase 공통)

1. `./gradlew test` (단위 + Testcontainers 통합) — 전체 PASS
2. `./gradlew build -x test` — 컴파일 OK
3. CLAUDE.md §3 핵심 원칙 11개 충돌 없음
4. 새 endpoint = endpoint 매트릭스(docs/02 §7.4)에 존재
5. DB 스키마 변경 없음 (V2 컬럼만 사용)

## 리스크 및 완화

| 리스크 | 완화 |
|---|---|
| Spring Session JDBC 부팅 시 schema 미존재로 부팅 실패 | V1 Flyway에 `SPRING_SESSION` 테이블 이미 포함됨 (`initialize-schema=never`로 부팅 시 schema 생성 비활성화) — 308c041에서 검증 |
| Testcontainers Docker 의존성 (CI/로컬 환경 차이) | A1.0에서 정립한 `disabledWithoutDocker=true` 패턴 재사용 (Docker 없는 환경에서 SKIP) |
| timing attack 회피 (미존재 user 응답 시간 일관성) | 미존재 시에도 BCrypt encoder.matches에 더미 hash 검증 호출 |
| session fixation | Spring Security 기본 + 명시적 `changeSessionId()` |
| in-memory lockout이 다중 인스턴스에서 무력화 | MVP 단일 인스턴스 가정 — ADR #23에 명시. v1.x Redis 도입 시 `LoginAttemptTracker` interface 교체 |
| `effectivePermissionsCacheKey` 미정의 | 임시로 `userId:role:v0` 문자열, A2 권한 매트릭스 진입 시 hash로 교체 |
| 컨텍스트 35%/25% 도달 | 사용자 정책: 35%에서 phase wrap-up, 25%에서 자체 /gsd-pause-work 실행 |
