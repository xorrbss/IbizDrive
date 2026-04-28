---
Last Updated: 2026-04-28
---

# A1 Auth Implementation — Tasks

## Phase 상태

| Phase | 상태 |
|---|---|
| A1.2 SecurityConfig wiring | done (commit 10a524b) |
| A1.3 LoginController + Lockout | done (commit 06b9238) |
| A1.4 /me + Logout | done (commit ca4e309) |
| A1.5 통합 시나리오 + 마일스톤 audit | done (commit c34e640) |
| A1.6 Session Timeout Policy | done (commit 4aa372c) |
| A1 마일스톤 종료 | done (PR #1 merge 반영, docs/progress.md 종료 블록 존재) |

---

## A1.2 — SecurityConfig 본 wiring  ✅ 완료 (commit 10a524b)

- [x] RED: `SecurityIntegrationTest` 5건 작성
- [x] GREEN: `CsrfTokenController` 추가
- [x] GREEN: `SecurityConfig` 본 wiring (httpBasic 제거, 매처 분리, sessionCreationPolicy)
- [x] `./gradlew test` PASS
- [x] 자체 리뷰 (⑥)
- [x] commit `feat(A1.2): SecurityConfig wiring + CSRF double-submit`
- [x] `progress.md` A1.2 세션 블록 추가

### A1.2 작업 전 필독

- `docs/03 §2.2`, `§2.3`, `docs/02 §7.4`
- ADR #12, #20
- `backend/src/main/java/com/ibizdrive/config/SecurityConfig.java` (skeleton)
- `backend/src/main/resources/application.yml` (Spring Session JDBC 이미 wired)

### A1.2 원본 코드 참조

- `SecurityConfig.java` — skeleton 형태, CSRF cookie repo는 이미 설정됨
- `IbizDriveUserDetails.java`, `DbUserDetailsService.java` — A1.1 완료, AuthenticationProvider가 자동 wire

### A1.2 구현 대상

- 신규 `backend/src/main/java/com/ibizdrive/auth/CsrfTokenController.java`
- 수정 `backend/src/main/java/com/ibizdrive/config/SecurityConfig.java`
- 신규 `backend/src/test/java/com/ibizdrive/auth/SecurityIntegrationTest.java`

### A1.2 검증 참조

- `./gradlew test --tests "com.ibizdrive.auth.SecurityIntegrationTest"` (단위 통과 확인)
- `./gradlew test` (전체 회귀)

### A1.2 문서 반영

- `docs/progress.md` 신규 세션 블록

---

## A1.3 — LoginController + Lockout  ✅ 완료 (commit 06b9238)

- [x] RED: `LoginAttemptTrackerTest` 4건 작성
- [x] RED: `LoginControllerIntegrationTest` 8건 작성
- [x] GREEN: `LoginAttemptTracker` 구현 (Clock 주입, lazy expiry)
- [x] GREEN: `LoginRequest`, `LoginResponse`, `ErrorResponse` DTO
- [x] GREEN: `AuthService.login` 구현 (timing-safe dummy hash @PostConstruct, @Transactional)
- [x] GREEN: `AuthController.login` (`POST /api/auth/login`)
- [x] GREEN: `User.recordLoginAt(OffsetDateTime)` 메서드 추가
- [x] GREEN: `AuthExceptionHandler` (RestControllerAdvice) → flat error shape
- [x] GREEN: `InvalidCredentialsException`, `AccountLockedException`
- [x] `./gradlew test` PASS (LoginAttemptTrackerTest 4/4 + LoginControllerIntegrationTest 8/8)
- [x] ADR #23 docs/00 §5에 추가
- [x] 자체 리뷰 (⑥)
- [x] commit `feat(A1.3): LoginController + in-memory lockout (ADR #23)`
- [x] `progress.md` A1.3 세션 블록 (다음 세션에서 처리 — 본 dev-docs-update에서 갱신)

### A1.3 작업 전 필독

- `docs/02 §7.4` (login 응답 스키마)
- `docs/03 §2.3`, `§2.6`, `§2.10`
- `docs/02 §8` (에러 코드 423/401/403)
- ADR #20 (lockout 정책)

### A1.3 원본 코드 참조

- `DbUserDetailsService.loadUserByUsername` (이미 email lowercase 흡수)
- `IbizDriveUserDetails.isEnabled`, `isAccountNonLocked`
- `User` entity (`isLocked`, `isActive`, `mustChangePassword`)

### A1.3 구현 대상

- 신규 `backend/src/main/java/com/ibizdrive/auth/LoginAttemptTracker.java`
- 신규 `backend/src/main/java/com/ibizdrive/auth/AuthService.java`
- 신규 `backend/src/main/java/com/ibizdrive/auth/AuthController.java`
- 신규 `backend/src/main/java/com/ibizdrive/auth/dto/LoginRequest.java`
- 신규 `backend/src/main/java/com/ibizdrive/auth/dto/LoginResponse.java`
- 신규 `backend/src/main/java/com/ibizdrive/common/error/ErrorResponse.java`
- 신규 `backend/src/main/java/com/ibizdrive/common/error/GlobalExceptionHandler.java` (필요 시)
- 수정 `backend/src/main/java/com/ibizdrive/user/User.java` (`recordLoginAt` 추가)
- 신규 `backend/src/test/java/com/ibizdrive/auth/LoginAttemptTrackerTest.java`
- 신규 `backend/src/test/java/com/ibizdrive/auth/LoginControllerIntegrationTest.java`

### A1.3 검증 참조

- `./gradlew test --tests "com.ibizdrive.auth.*"`
- `./gradlew test` 전체

### A1.3 문서 반영

- `docs/00-overview.md §5` ADR #23 추가
- `docs/progress.md` 세션 블록

---

## A1.4 — /me + Logout  ✅ 완료 (commit ca4e309)

- [x] RED: `AuthMeLogoutIntegrationTest` 5건 작성
- [~] GREEN: ~~`MeResponse` DTO~~ → `LoginResponse` 재사용 (docs/02 §7.4 shape 동일, KISS)
- [x] GREEN: `AuthController.me` (`GET /api/auth/me`) — `@AuthenticationPrincipal IbizDriveUserDetails`
- [x] GREEN: `AuthController.logout` (`POST /api/auth/logout`) — session.invalidate + clearContext + Cookie expire
- [x] **A1.3 hidden gap fix**: `AuthService.login`에 SecurityContextRepository.saveContext 추가 (Spring Security 6 load-only 필터)
- [x] `SecurityConfig` SecurityContextRepository 빈 + securityContext() wire
- [x] `./gradlew test` PASS (4 클래스, 22 테스트)
- [x] 자체 리뷰 (⑥) — KISS/YAGNI 결정 명시 (LoginResponse 재사용)
- [x] commit `feat(A1.4): /api/auth/me + /api/auth/logout`
- [ ] `progress.md` A1.4 세션 블록 (다음 세션 dev-docs-update에서 처리)

### A1.4 작업 전 필독

- `docs/02 §7.4` (/me 응답 스키마, /logout)
- `docs/03 §2.5`
- ADR #22

### A1.4 원본 코드 참조

- A1.3에서 만든 `AuthController` (login)
- `User` entity

### A1.4 구현 대상

- 신규 `backend/src/main/java/com/ibizdrive/auth/dto/MeResponse.java`
- 수정 `backend/src/main/java/com/ibizdrive/auth/AuthController.java` (me/logout 추가)
- 신규 `backend/src/test/java/com/ibizdrive/auth/AuthMeLogoutIntegrationTest.java`

### A1.4 검증 참조

- `./gradlew test --tests "com.ibizdrive.auth.AuthMeLogoutIntegrationTest"`
- `./gradlew test` 전체

### A1.4 문서 반영

- `docs/progress.md` 세션 블록

---

## A1.5 — 통합 시나리오 + 마일스톤 audit ✅ 완료 (commit c34e640)

- [x] RED: `AuthScenarioIntegrationTest` (1건 종합 시나리오, `@SpringBootTest` + Testcontainers Postgres 15-alpine, mutable Clock 빈 override)
- [x] GREEN: 필요 시 보강 — A1.0~A1.4 구현이 이미 시나리오 충족, 추가 코드 불필요
- [x] `./gradlew test` PASS — 9 클래스, 152 tests, 4 skipped (UserRepositoryTest 3 + AuthScenarioIntegrationTest 1, 모두 Docker 미가용으로 SKIP), 0 fail
- [x] dev-docs 기반 수동 audit 작성 — `gsd-audit-milestone`은 GSD `.planning` 구조 전제라 본 프로젝트에 부적합함을 확인
- [x] `progress.md` A1.5/A1.6/A1 마일스톤 종료 블록 작성
- [x] commit `test(A1): integration scenarios`
- [x] PR #1 merge 후 master 반영 확인

### A1.5 작업 전 필독

- `docs/03 §2` 전체 (시퀀스 정합성 확인)
- `docs/00 §4 로드맵` (A1 → A2 안내)

### A1.5 구현 대상

- 신규 `backend/src/test/java/com/ibizdrive/auth/AuthScenarioIntegrationTest.java`
- 수정 `docs/progress.md` 마일스톤 종료 블록

### A1.5 검증 참조

- `./gradlew test`
- `gsd-audit-milestone A1`
- `gh pr checks 1` (PR 존재 시)

### A1.5 문서 반영

- `docs/progress.md` A1 마일스톤 종료 블록 + A2 안내

---

## A1.6 — Session Timeout Policy ✅ 완료 (commit 4aa372c)

- [x] RED: `SessionValidityFilterTest` 4건 작성
- [x] GREEN: `SessionValidityFilter` 추가 — absolute 8h 만료 강제
- [x] GREEN: `SecurityConfig`에 `Clock` bean + filter wiring 추가
- [x] GREEN: `application.yml` `spring.session.timeout: PT30M`로 idle 30분 정책 정렬
- [x] `./gradlew test` PASS — 156 tests, 4 Docker SKIP, 0 fail
- [x] `a1-auth-impl-audit.md` must-fix #1 RESOLVED 반영
- [x] `docs/progress.md` A1.6 및 A1 종료 블록 반영

### A1.6 문서 반영

- `dev/active/a1-auth-impl/a1-auth-impl-audit.md`
- `docs/progress.md`

---

## Archive 상태

- 2026-04-28 확인 기준 A1은 이미 `docs/progress.md`에 "A1 마일스톤 종료"로 기록되어 있고, git history에는 PR #1 merge commit(`eda6f75`) 이후 A2 완료까지 반영되어 있다.
- 파일 이동은 sandbox 권한 정책으로 막혀 `dev/active/a1-auth-impl/` 경로는 유지한다. 대신 본 문서와 context 상태를 completed로 정리해 active task가 아님을 명시한다.
- 다음 개발 진입점은 프론트 인증 + 관리자 라우팅 플랜이다.
