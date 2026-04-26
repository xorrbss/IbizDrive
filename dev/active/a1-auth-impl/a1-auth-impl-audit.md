---
Last Updated: 2026-04-26
Status: gaps_found (1 must-fix, 3 accepted-deviation)
Conclusion: A1 마일스톤 종료 가능 — must-fix #1을 사용자 승인으로 accepted-deviation 전환 시
---

# A1 Auth Implementation — Milestone Audit

## 0. 감사 범위

`dev/active/a1-auth-impl/a1-auth-impl-plan.md` "목표 상태" (line 30~38)를 DoD source of truth로 채택. GSD `.planning/` 구조 부재로 `gsd-audit-milestone` 스킬 미적용 — 본 audit은 dev-docs 기반 수동 수행.

소스:
- 계획: `a1-auth-impl-plan.md`, `a1-auth-impl-tasks.md`, `a1-auth-impl-context.md`
- 코드: `backend/src/main/java/com/ibizdrive/{auth,config,user,common/error}/**`
- 테스트: `backend/src/test/java/com/ibizdrive/**`
- spec 정합성: `docs/00 §5 ADR`, `docs/02 §7.4`, `docs/03 §2`
- 커밋: `master..HEAD` (308c041 → c34e640, A1 관련 6개 commit)

## 1. DoD 항목별 검증 (plan line 30~38)

| # | DoD 항목 | 결과 | 증거 |
|---|---|---|---|
| 1 | 인증 4 endpoint (`POST /login`, `POST /logout`, `GET /me`, `GET /csrf`) 완전 작동 | ✅ pass | `AuthController` (login/me/logout), `CsrfTokenController` (csrf). 5+8+4+1=18 통합 테스트 PASS |
| 2 | 5회 실패 → 15분 lockout (in-memory `ConcurrentHashMap`) 작동, 성공 시 reset | ✅ pass | `LoginAttemptTracker` (LOCKOUT_THRESHOLD=5, LOCKOUT_TTL=15min, lazy expiry, `recordSuccess` reset). 4 단위 + 8 통합 테스트 PASS |
| 3 | CSRF double-submit 강제 (mutation endpoint 모두) | ✅ pass | `SecurityConfig` `CookieCsrfTokenRepository.withHttpOnlyFalse()` + plain `CsrfTokenRequestAttributeHandler`. `SecurityIntegrationTest` POST without CSRF → 403 검증 |
| 4 | **세션 정책: idle 30분 sliding + absolute 8시간 (Spring Session JDBC 백엔드)** | ⚠ **must-fix #1** | `application.yml` `spring.session.timeout: PT8H` — idle=8h. 30분 idle 미구현, 8시간 absolute 강제 (`SessionAttributeFilter`) 미구현. `issuedAt`은 set만 하고 검증 없음 |
| 5 | `gradle test` 전체 PASS (단위 + Testcontainers 통합) | ✅ pass | 9 클래스, 152 tests, 4 skipped (Docker), 0 fail. `./gradlew test` UP-TO-DATE 재확인 |
| 6 | progress.md A1 종료 블록 작성, gsd-audit-milestone 통과 | ⏳ in-progress | A1.2/A1.3/A1.4 세션 블록 존재. A1.5 + 마일스톤 종료 블록은 본 audit 직후 추가 (task #6 대상) |
| 7 | CI 그린 + master ahead-of N 상태로 PR 후보 commit 정렬 | ⏳ pending | branch ahead 23, PR #1 OPEN. `gh pr checks 1`은 push 후 검증 (task #8 대상) |

## 2. ADR 정합성 (코드 ↔ docs/00 §5)

| ADR | 결정 | 코드 매핑 | 결과 |
|---|---|---|---|
| #19 | BCrypt strength=12 + `{bcrypt}` prefix + DelegatingPasswordEncoder | `SecurityConfig.passwordEncoder()` `new BCryptPasswordEncoder(12)` + DelegatingPasswordEncoder("bcrypt", ...) | ✅ pass |
| #20 | idle 30min sliding + absolute 8h + 5/15min lockout | lockout ✅ / 세션 정책 ⚠ (DoD #4와 동일 — must-fix #1로 추적) | partial |
| #22 | `/me` = identity + role + permissionsCacheKey만 (effectivePermissions full resolve 제외) | `LoginResponse.from` (`{user, departments=[], roles=[role], effectivePermissionsCacheKey=userId:role:v0}`). `/me`가 동일 shape 재사용 | ✅ pass |
| #23 | MVP lockout = in-memory ConcurrentHashMap + lazy expiry + 단일 인스턴스 가정 | `LoginAttemptTracker` ConcurrentHashMap, `Clock` 주입, `recordFailure` 내 lazy expiry | ✅ pass |

## 3. 핵심 원칙 (CLAUDE.md §3) 충돌 검사

| 원칙 | 상태 | 비고 |
|---|---|---|
| 6. DB 제약이 진실의 출처 | ✅ | `users.email_lower` UNIQUE + soft-delete partial unique 적용 (V1) |
| 7. 트랜잭션 + SELECT FOR UPDATE | △ | `AuthService.login` `@Transactional`만. SELECT FOR UPDATE는 lockout race 회피 목적 — A1은 in-memory tracker라 무관. 향후 audit insert 도입 시 적용 |
| 8. audit_log append-only | n/a | A1은 audit 미구현 (deviation #2 참조) |
| 9. storage_key UUID | n/a | A1 무관 (storage 미진입) |
| 10. 파괴적 액션 백엔드 재검증 | ✅ | `anyRequest().authenticated()` + CsrfFilter가 모든 mutation 1차 차단. AuthService에서 lockout/active/locked 재검증 |
| 11. 정규화 함수 일관성 | ✅ | email lowercase는 `AuthService.login` + `DbUserDetailsService` 양쪽 동일 (`Locale.ROOT.toLowerCase`) |
| 12. 에러 코드 = 계약 | ✅ | `AuthExceptionHandler` flat shape — INVALID_CREDENTIALS/ACCOUNT_LOCKED 코드 docs/02 §8과 일치 |

## 4. 드리프트 분류

### must-fix (마일스톤 종료 전 해결 또는 accepted-deviation 명시 승인 필요)

#### must-fix #1 — 세션 timeout 정책 미구현 (ADR #20 + plan DoD #4)

| | |
|---|---|
| **위반 spec** | `docs/00 §5 ADR #20`, `docs/03 §2.6`, `a1-auth-impl-plan.md` line 35 |
| **요구사항** | idle 30분 sliding (Spring Session `setMaxInactiveIntervalInSeconds(1800)`) + absolute 8시간 (`SessionAttributeFilter`에서 `issuedAt` 검증) |
| **현재 구현** | `application.yml` `spring.session.timeout: PT8H` → idle=8h. `AuthService.login`에서 `session.setAttribute("issuedAt", ...)`만 set, 검증 필터 부재 |
| **영향** | 보안 정책 완화 (의도보다 16배 긴 idle 허용). 기능 영향 없음. exploit으론 세션 탈취 후 활용 윈도우가 정책보다 크다는 의미 |
| **fix 비용** | application.yml 1줄 + `SessionValidityFilter` 신규 1 클래스 + 단위 테스트 2~3건. 추정 1 phase (A1.6) 또는 본 마일스톤 내 +30~60분 |

**감사관 권고**: 사용자 자율 결정 — 둘 중 하나
- **(A) accepted-deviation 승인 + A1.6 phase 추가** (별도 phase로 분리, A2 진입 전 처리). HANDOFF JSON에서 권한 매트릭스를 별도 phase로 분리한 전례와 동일한 방식.
- **(B) 본 세션 내 즉시 fix** (must-fix 유지). 컨텍스트 여유 시 가능, RED 테스트 + GREEN 구현 + 회귀.

### accepted-deviation (A1 plan에서 명시적으로 deferred — 마일스톤 종료 차단 안 함)

#### deviation #1 — audit_log emission 미구현

| | |
|---|---|
| **관련 spec** | `docs/02 §7.4` line 797 ("REQUIRED audit·loginfail 카운터"), `docs/03 §4` |
| **A1 plan 명시** | `AuthService.login`의 `// (후속) audit insert` 주석. plan 본문 "@Transactional은 last_login_at + (후속) audit insert를 단일 단위" — 후속으로 명시. `audit_log` 테이블/append-only constraint는 A2 backbone 범위 |
| **합리성** | A1 plan 목표 상태에 audit emission 미포함. trace는 미구현이지만 트랜잭션 경계는 미리 설정 (확장 시 코드 수정 최소화) |
| **추적** | A2 마일스톤 (audit + 권한 매트릭스 backbone)에서 처리 |

#### deviation #2 — `400 PASSWORD_CHANGE_REQUIRED` 분기 미구현

| | |
|---|---|
| **관련 spec** | `docs/02 §7.4` line 834 — "mustChangePassword=true인 사용자 — A1.5에서 처리, MVP는 응답 후 클라가 변경 화면 유도. ADR #21" |
| **A1 plan 명시** | A1.4 acceptance criteria: "must_change_password=true 응답에 표시" (display only). A1.5 재정의로 PW change endpoint는 별도 phase |
| **현재 구현** | `LoginResponse.UserInfo.mustChangePassword` 필드로 표시. 별도 400 분기 없음. 클라이언트가 200 응답에서 flag를 읽어 변경 화면 유도하는 모델 |
| **추적** | docs/02 §7.4의 400 분기는 spec 옵션. PW change endpoint phase에서 결정 (현재 미배정) |

#### deviation #3 — `AuthScenarioIntegrationTest` 로컬 SKIP

| | |
|---|---|
| **관련 spec** | `a1-auth-impl-plan.md` "Validation gate: ./gradlew test PASS + git push + gh pr checks 1 그린" |
| **현재 상태** | 로컬 (Windows) Docker 미가용 → `disabledWithoutDocker=true`로 SKIP (1건). CI ubuntu-latest는 Docker 가용 → 실제 실행 |
| **합리성** | A1.0 (UserRepositoryTest 3건)에서 정립한 동일 패턴. plan 본문 line 187 "리스크 및 완화" 섹션에 명시 ("Testcontainers Docker 의존성 — `disabledWithoutDocker=true` 패턴 재사용"). PR push 후 `gh pr checks 1` 그린 확인이 본 deviation을 닫는 게이트 |
| **추적** | task #8 (PR push + CI 그린 확인)에서 검증 완료 시 close |

## 5. 결론

- **마일스톤 종료 가능 여부**: must-fix #1을 (A) accepted-deviation 또는 (B) 즉시 fix 중 하나로 처리하면 종료 가능.
- **권고**: (A) — `must-fix #1`은 보안 정책 완화로 분류되지만 기능 영향이 없고, A2 진입 전 별도 phase (A1.6: SessionValidityFilter)로 추적 가능. A1 마일스톤은 "auth wiring + lockout"이 본질이며, 세션 timeout 강제는 분리해도 의존성 충돌 없음.
- **채택 시 다음 액션**: progress.md A1 마일스톤 종료 블록에 본 deviation 명시 → A1.6 phase를 a1-auth-impl-plan.md에 후속 추가 또는 신규 dev-docs로 분리 → A2 진입 전 처리.

## 6. 통계

- A1 commits (master..HEAD A1 관련): **6개** (308c041 / 0dd2d65 / 10a524b / 06b9238 / ca4e309 / c34e640)
- 테스트: **152 tests** (148 pass + 4 skipped Docker, 0 fail)
- A1 신규 production 클래스: **15** (auth/ 6 + auth/dto/ 2 + config/ 1 + user/ 4 + common/error/ 2)
- A1 신규 테스트 클래스: **5** (`SecurityIntegrationTest`, `LoginAttemptTrackerTest`, `LoginControllerIntegrationTest`, `AuthMeLogoutIntegrationTest`, `AuthScenarioIntegrationTest`)
- ADR 등록: **#19/20/22/23** (모두 docs/00 §5 본문)
- 발견 hidden gap fix: **1** (Spring Security 6 SecurityContext save 누락 — A1.4 commit ca4e309에서 수정)
