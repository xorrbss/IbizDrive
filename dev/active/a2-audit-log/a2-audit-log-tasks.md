---
Last Updated: 2026-04-27
---

# A2 Audit Log — Tasks

각 task는 RED → GREEN → REFACTOR 순. 완료 시 `[x]`, 부분 완료 `[-]` + 잔여 1줄.

## A2.0 — 스키마 + 마이그레이션 + ADR

- [x] RED: `AuditLogSchemaTest` (Testcontainers) — `audit_log` 테이블 + 컬럼 + CHECK + 인덱스 4개 검증 (information_schema)
- [x] RED: `AuditLogAppendOnlyTest` (Testcontainers) — `app_user` role로 `UPDATE audit_log` → SQLState `42501`, `DELETE` 동일
- [x] GREEN: `V3__audit_log.sql` (docs/02 §2.8 스키마 + `target_type` CHECK 7개 — `audit` 포함)
- [x] GREEN: `V4__audit_log_revoke.sql` (`app_user` role 생성 idempotent + REVOKE/GRANT)
- [x] GREEN: docs/00 §5 ADR #24 추가 (Emission 위치)
- [x] GREEN: docs/00 §5 ADR #25 추가 (DB role 분리)
- [x] GREEN: docs/02 §2.8 본문 `target_type` CHECK에 `audit` 추가 (frontend 동기)
- [x] commit: `feat(A2.0): audit_log V3+V4 마이그레이션 + ADR #24~25` (`440b0b0`, CI 24960561196 ✅)

## A2.1a — AuditEvent + Enums + AuditService (REQUIRES_NEW 검증)

- [x] RED: `AuditServiceTest` — `record(AuditEvent)` → audit_log row 검증 (모든 컬럼)
- [x] RED: `AuditServiceTest` — system 이벤트(actor null) 정상 INSERT
- [x] RED: `AuditServiceTest` — REQUIRES_NEW: 호출자 트랜잭션 rollback해도 audit row 보존
- [x] GREEN: `AuditEvent` record (Java 21, eventType/targetType NOT NULL 검증)
- [x] GREEN: `AuditEventType` enum (38 values, FE 1:1 동기, @JsonValue/@JsonCreator wire 변환)
- [x] GREEN: `AuditTargetType` enum (7 values, V3 CHECK와 1:1)
- [x] GREEN: `AuditService.record(AuditEvent)` JdbcTemplate INSERT + `?::jsonb`/`?::inet` 캐스트 + `@Transactional(REQUIRES_NEW)`
- [x] commit: `feat(A2.1a): AuditService + AuditEvent + Enums (38 events, 7 targets)` (`fd28368`, push 완료)
- [x] CI 그린 확인 — run 24961391600 ✅ (FK 가시성 + inet 표기 2건 fix, commits `1196e11`, `cf0be93`)

## A2.1b — @Audited AOP + WebRequestContextHolder (다음 사이클)

- [ ] RED: `AuditedAspectTest` — `@Audited` 메서드 정상 종료 → record 호출 1회
- [ ] RED: `AuditedAspectTest` — 메서드 throw → record 호출 0회
- [ ] RED: `AuditedAspectTest` — SpEL target ID 추출 (`#fileId`, `#result.id`)
- [ ] GREEN: `@Audited` annotation (event, target SpEL)
- [ ] GREEN: `AuditedAspect` (`@AfterReturning`, SpEL evaluator)
- [ ] GREEN: `WebRequestContextHolder` (IP/UA from RequestAttributes)
- [ ] commit: `feat(A2.1b): @Audited AOP + WebRequestContext`

## A2.2 — Append-only 검증

- [ ] A2.0 RED 테스트들이 실제 GREEN 통과 확인
- [ ] Testcontainers fixture에 `app_user` role 자동 생성 확인 (V4가 처리)
- [ ] CI 그린

## A2.3 — Read API + 권한

- [ ] RED: `AuditQueryControllerTest` (`@WebMvcTest`) — ADMIN/AUDITOR 200 전체, MEMBER scope=self, 익명 401
- [ ] RED: 필터 테스트 — `eventType` 정확매칭, `actorQuery` 부분매칭(대소문자무시), `fromDate/toDate` inclusive, `page` 1-indexed, `occurredAt DESC`
- [ ] RED: 빈 결과 — `actorQuery=__no__` → entries=[], total=0
- [ ] GREEN: `AuditQueryController` `GET /api/admin/audit?fromDate&toDate&actorQuery&eventType&page&pageSize`
- [ ] GREEN: `AuditQueryService.search(filters, page, size, principal)` — Specification + role 분기
- [ ] GREEN: DTO `AuditLogPageDto`, `AuditLogEntryDto` (frontend 동치)
- [ ] GREEN: `@PreAuthorize` 또는 service 레벨 분기 (MEMBER는 actor_id=self 강제)
- [ ] GREEN: docs/02 §7.3 audit endpoint 표 갱신
- [ ] commit: `feat(A2.3): GET /api/admin/audit + role-based scope`

## A2.4 — A1 인증 이벤트 emission

- [ ] RED: `AuthAuditListenerTest` — `AuthenticationSuccessEvent` → `user.login.success` row + actorId/IP/UA 검증
- [ ] RED: `BadCredentialsEvent` → `user.login.failed` + metadata.reason
- [ ] RED: `AuthenticationFailureLockedEvent` → `user.login.failed` + metadata.reason='locked'
- [ ] RED: `LogoutSuccessEvent` → `user.logout`
- [ ] RED: `git diff` 검증 — `AuthService.java`, `LoginAttemptTracker.java`, `AuthController.java` 변경 0줄 (침투 0)
- [ ] GREEN: `AuthAuditListener` (`@EventListener`)
- [ ] GREEN: failed 이벤트의 actorId 추출 (event.getAuthentication().getName() → email → users.id 조회 또는 null)
- [ ] commit: `feat(A2.4): A1 auth events → audit_log (listener only, AuthService 침투 0)`

## A2.5 — 통합 테스트

- [ ] `AuthAuditE2ETest` (`@SpringBootTest` + Testcontainers + HttpClient5)
  - 정상 로그인 → audit_log에 `user.login.success` 1건, actor_id 매칭
  - 잘못된 PW 5회 → `user.login.failed` 5건, 5번째는 metadata.reason 변경 가능 (lockout)
  - 로그아웃 → `user.logout` 1건
- [ ] `AuditQueryE2ETest` — 위 시나리오 후 ADMIN으로 `/api/admin/audit?eventType=user.login.failed` 호출 → 5건
- [ ] `Serializable` 검증 — `IbizDriveUserDetails`에 신규 필드 추가 안 했음을 명시 (회귀 방지)
- [ ] commit: `test(A2.5): full E2E auth → audit_log persistence`

## A2.6 — Frontend fetch 교체

- [ ] `api.getAuditLogs` 본문 `MOCK_AUDIT_LOGS` 분기 제거, fetch 호출
- [ ] `MOCK_AUDIT_LOGS`, `MOCK_ACTORS`, `makeAuditLogs()` 블록 제거 (api.ts 주석 명시 따라)
- [ ] `api.audit.test.ts` — 기존 MSW 또는 `vi.fn(global.fetch)` 모킹으로 전환, 모든 케이스 PASS 유지
- [ ] dev seed (Flyway repeatable 또는 별도 SQL) — audit_log 60건 (mock 분포 모방)
- [ ] `frontend/e2e/audit.e2e.ts` 신규 — admin 로그인 → /admin/audit/logs → 필터/페이지 동작
- [ ] `auditCsv.ts` export 이벤트 self-emit 정책 결정 (deferred — enum만 유지)
- [ ] commit: `feat(A2.6): frontend audit mock → real fetch`

## A2.7 — 종료 정리

- [ ] docs/progress.md A2 종료 블록
- [ ] code-review (`superpowers:requesting-code-review`)
- [ ] gh pr create → master
- [ ] PR description: 변경 요약, 테스트 증명(42501 + listener 침투 0), DoD 10항목 체크
- [ ] (게이트 유지) `gh pr merge` — 사용자 승인

## 신규 발견 task (작업 중 추가)

(empty)
