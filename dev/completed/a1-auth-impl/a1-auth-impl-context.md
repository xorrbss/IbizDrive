---
Last Updated: 2026-04-28 (CLOSED — A1 마일스톤 종료, A2/A3 핸드오프 완료)
Status: ✅ CLOSED
---

# A1 Auth Implementation — Context (CLOSED)

## 종료 상태

A1 backend authentication 종료 — PR #1 squash merged (`eda6f75`), master CI 그린.
A2 (Audit Log Backbone) 종료 후 (`dd372d7`, 2026-04-28), 본 디렉터리는 archive 대상.

세부 회고 단일 진실 출처: `docs/progress.md` 2026-04-26 A1 마일스톤 종료 블록 (line 57~84).
DoD 항목별 검증: `a1-auth-impl-audit.md` (보존).

## A1 → 후속 마일스톤 핸드오프

A1에서 발생한 accepted-deviation의 후속 처리 흐름:

| Deviation | 후속 phase | 상태 |
|---|---|---|
| `audit_log` emission 미구현 (`// (후속) audit insert` 주석) | A2 | ✅ A2.4에서 `ApplicationEventPublisher` + `AuthAuditListener`로 emission 완료 (`dd372d7`) |
| `effectivePermissionsCacheKey = userId:role:v0` 단순 문자열 | A3 | ⏳ A3에서 권한 변경 trigger 기반 hash로 교체 예정 |
| `400 PASSWORD_CHANGE_REQUIRED` 분기 미구현 (flag만 응답) | PW change endpoint phase | ⏳ 미배정 (v1.x 추적) |
| `AuthScenarioIntegrationTest` 로컬 SKIP — Windows Docker 미가용 | PR #1 머지 | ✅ PR #1 머지로 close |

## 외부 진실 출처 (참조)

- `docs/progress.md` 2026-04-26 A1 마일스톤 종료 블록 — 마일스톤 회고 단일 진실 출처
- `docs/00-overview.md` §5 ADR #19 (BCrypt strength=12), #20 (idle 30m + absolute 8h + 5/15min lockout), #21 (registration), #22 (`/me` shape), #23 (in-memory lockout backing)
- `docs/02-backend-data-model.md` §7.4 (auth endpoint 매트릭스), §8 (에러 코드)
- `docs/03-security-compliance.md` §2 (인증 — 세션·로그인·만료·비밀번호 정책)
- `backend/src/main/java/com/ibizdrive/{auth,config,user,common/error}/**` (16 production 클래스)
- `backend/src/test/java/com/ibizdrive/**` (6 클래스 22 테스트 + Testcontainers 통합)

## 빠른 인덱스 (코드 ↔ ADR)

| 파일 | ADR / 사양 |
|---|---|
| `SecurityConfig.java` | ADR #19 (BCrypt) + ADR #20 (filter chain) + `SecurityContextRepository` 명시 wire |
| `LoginAttemptTracker.java` | ADR #20 (5/15min) + ADR #23 (in-memory + Clock) |
| `SessionValidityFilter.java` | ADR #20 (absolute 8h, idle은 yml `PT30M`) |
| `AuthService.login` | docs/03 §2.3, timing-safe dummy hash, `changeSessionId`, SecurityContext saveContext |
| `AuthController.{login,me,logout}` | docs/02 §7.4 |
| `CsrfTokenController` | docs/03 §2.3, deferred 모드 saveToken 명시 호출 |

## 회고 핵심 결정 (참조용)

- timing-safe dummy hash는 `@PostConstruct` 동적 생성 (정적 상수는 형식 오류 시 BCrypt iterations 미실행 → leak)
- 비활성·잠금도 `INVALID_CREDENTIALS` 매핑 (계정 상태 enumeration 방지, docs/03 §2.3)
- error 응답 = flat shape (docs/02 §7.4) — 일반 envelope §7.2와 별개
- `/me`는 `LoginResponse` 재사용 (KISS, docs/02 §7.4 shape 동일)
- Spring Security 6 `SecurityContextHolderFilter`는 load-only → `AuthService.login`에 명시 saveContext 필요 (A1.4 hidden gap fix)
