---
Last Updated: 2026-04-26
---

# A1 Auth Implementation — Context

## SESSION PROGRESS

- 2026-04-26 [세션 1, 진행 중]: dev/active bootstrap. A1.2 RED 테스트 작성 진입.

## Current Execution Contract

- 사용자 큐: A1.2 → A1.3 → A1.4 → A1.5 (4 phase 자율 실행)
- 모드: autonomous (spec/plan 승인 게이트 없음)
- TDD 강제: superpowers:test-driven-development RED → GREEN
- 디버깅: superpowers:systematic-debugging
- commit 전: superpowers:verification-before-completion
- 매 phase commit 직전 `./gradlew test` PASS 강제
- 컨텍스트 35% → 현재 phase wrap-up, 25% → /gsd-pause-work 자체 실행
- 중단 기준: ADR/CLAUDE.md §3 위반, DB breaking change, endpoint 매트릭스 외 신규, 외부 자격증명 필요, 같은 에러 3회 실패

## 현재 active task

**A1.2 — SecurityConfig 본 wiring**

next concrete actions:
1. `dev/process/[session-id].md` 작업 파일 만들기
2. `SecurityIntegrationTest` 통합 테스트 RED 작성 (5건)
3. `CsrfTokenController` + `SecurityConfig` 갱신으로 GREEN
4. `./gradlew test` PASS 확인
5. commit

## 다음 세션 읽기 순서

1. `a1-auth-impl-plan.md` — 전체 phase 지도 + acceptance
2. `a1-auth-impl-tasks.md` — 체크박스 + 파일 참조
3. `a1-auth-impl-context.md` — 본 파일 (SESSION PROGRESS + 결정)
4. (필요 시) `docs/03 §2`, `docs/02 §7.4`, `docs/00 §5 ADR` 본문

## 핵심 파일과 역할

| 파일 | 역할 |
|---|---|
| `backend/src/main/java/com/ibizdrive/config/SecurityConfig.java` | A1.2 본 wiring 대상 |
| `backend/src/main/java/com/ibizdrive/user/DbUserDetailsService.java` | A1.1 완료, AuthenticationProvider가 이걸 사용 |
| `backend/src/main/java/com/ibizdrive/user/IbizDriveUserDetails.java` | UserDetails 어댑터 |
| `backend/src/main/java/com/ibizdrive/user/User.java` | 도메인 entity, `last_login_at` setter는 추후 추가 필요 |
| `backend/src/main/java/com/ibizdrive/user/UserRepository.java` | `findActiveByEmail` 사용 |
| `backend/src/main/resources/application.yml` | Spring Session JDBC 설정 (이미 wired) |
| **신규** `backend/src/main/java/com/ibizdrive/auth/CsrfTokenController.java` | A1.2 |
| **신규** `backend/src/main/java/com/ibizdrive/auth/AuthController.java` | A1.3 + A1.4 (login/logout/me 통합) |
| **신규** `backend/src/main/java/com/ibizdrive/auth/AuthService.java` | A1.3 |
| **신규** `backend/src/main/java/com/ibizdrive/auth/LoginAttemptTracker.java` | A1.3 (in-memory lockout) |
| **신규** `backend/src/main/java/com/ibizdrive/auth/dto/*.java` | LoginRequest, LoginResponse, MeResponse |
| **신규** `backend/src/main/java/com/ibizdrive/common/error/ErrorResponse.java` | docs/02 §8 매핑 |

## 중요한 의사결정

### ADR #23 (자율 채택, A1.3에서 docs/00 §5에 등록)

Lockout backing store = in-memory `ConcurrentHashMap`. 단일 인스턴스 가정, 다중 인스턴스/Redis 도입 시점에 `LoginAttemptTracker` interface 교체. 키는 lowercased email. 5회 실패 → 15분 잠금. 만료는 lazy 검증 (조회 시점에 시계 기반 판정).

### A1.5 재정의 (사용자 자율 결정)

HANDOFF.json의 A1.5 (권한 매트릭스 백엔드 권위 — `@PreAuthorize` + `PermissionService`)는 이번 작업에서 통합 시나리오 + 마일스톤 종료로 재정의. 권한 매트릭스 백엔드는 별도 phase (A1.5 후속 또는 A2 직전)로 분리.

### `effectivePermissionsCacheKey` 임시값

A1.4 `/me` 응답에 포함되는 cacheKey는 권한 매트릭스 백엔드 (A1.5 후속) 도입 전까지 `userId + ":" + role.name() + ":v0"` 문자열로 단순화. ADR 불필요 (내부 캐시 키, 외부 계약 변경 없음).

### A1.2 CSRF endpoint 위치

`/api/auth/csrf`는 `CsrfTokenController`로 분리 (login/logout/me는 `AuthController`로 통합). 이유: CSRF는 인증 전에 호출되며 permitAll 매처라 다른 보호 endpoint와 책임 경계 분리.

### timing attack 회피

미존재 email에도 dummy BCrypt hash로 `passwordEncoder.matches` 호출 → 실제 verify와 동일 시간 소비. dummy hash는 `LoginAttemptTracker` 또는 `AuthService` 상수로 보관.

### session fixation

`HttpServletRequest.changeSessionId()`를 로그인 성공 직후 호출. Spring Session JDBC가 새 ID로 row를 INSERT.

## 빠른 재개 안내

세션이 끊겼다면:
1. `git log --oneline -10`로 마지막 commit 확인
2. `tasks.md` 체크박스 보고 마지막 완료 항목의 다음 항목으로 진입
3. `dev/process/*.md`에 다른 세션 작업 파일이 없는지 확인
4. 진행 중이던 RED 테스트가 commit 안 되었으면 `git status` 확인 후 재작성
5. `./gradlew test`로 현재 상태 검증
