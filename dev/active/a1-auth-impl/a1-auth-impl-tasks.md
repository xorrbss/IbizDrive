---
Last Updated: 2026-04-26
---

# A1 Auth Implementation — Tasks

## Phase 상태

| Phase | 상태 |
|---|---|
| A1.2 SecurityConfig wiring | in-progress |
| A1.3 LoginController + Lockout | not-started |
| A1.4 /me + Logout | not-started |
| A1.5 통합 시나리오 + 마일스톤 종료 | not-started |

---

## A1.2 — SecurityConfig 본 wiring

- [ ] RED: `SecurityIntegrationTest` 5건 작성
- [ ] GREEN: `CsrfTokenController` 추가
- [ ] GREEN: `SecurityConfig` 본 wiring (httpBasic 제거, 매처 분리, sessionCreationPolicy)
- [ ] `./gradlew test` PASS
- [ ] 자체 리뷰 (⑥)
- [ ] commit `feat(A1.2): SecurityConfig wiring + CSRF double-submit`
- [ ] `progress.md` A1.2 세션 블록 추가

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

## A1.3 — LoginController + Lockout

- [ ] RED: `LoginAttemptTrackerTest` 4건 작성
- [ ] RED: `LoginControllerIntegrationTest` 8건 작성
- [ ] GREEN: `LoginAttemptTracker` 구현
- [ ] GREEN: `LoginRequest`, `LoginResponse`, `ErrorResponse` DTO
- [ ] GREEN: `AuthService.login` 구현
- [ ] GREEN: `AuthController.login` (`POST /api/auth/login`)
- [ ] GREEN: `User.recordLoginAt(OffsetDateTime)` 메서드 추가 (`last_login_at` setter — entity)
- [ ] `./gradlew test` PASS
- [ ] ADR #23 docs/00 §5에 추가
- [ ] 자체 리뷰 (⑥)
- [ ] commit `feat(A1.3): LoginController + in-memory lockout (ADR #23)`
- [ ] `progress.md` A1.3 세션 블록

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

## A1.4 — /me + Logout

- [ ] RED: `AuthMeLogoutIntegrationTest` 5건 작성
- [ ] GREEN: `MeResponse` DTO
- [ ] GREEN: `AuthController.me` (`GET /api/auth/me`)
- [ ] GREEN: `AuthController.logout` (`POST /api/auth/logout`)
- [ ] `./gradlew test` PASS
- [ ] 자체 리뷰 (⑥)
- [ ] commit `feat(A1.4): /api/auth/me + logout`
- [ ] `progress.md` A1.4 세션 블록

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

## A1.5 — 통합 시나리오 + 마일스톤 종료

- [ ] RED: `AuthScenarioIntegrationTest` (1건 종합 시나리오)
- [ ] GREEN: 필요 시 보강
- [ ] `./gradlew test` PASS
- [ ] gsd-audit-milestone 실행
- [ ] `progress.md` A1 마일스톤 종료 블록
- [ ] commit `test(A1): integration scenarios`
- [ ] `git push` + `gh pr checks 1` (CI 그린 확인)

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
