---
Last Updated: 2026-04-27
---

# A2 Audit Log Backbone — Plan

## 요약

`audit_log` 테이블 + AOP/Event 기반 emission + DB 레벨 append-only 강제 + read API + 기존 frontend mock(M12) → 실 fetch 교체. 트랙 종료 조건: docs/03 §4 감사 정책의 backbone이 코드로 구현되고, A1 인증 이벤트 4종(`user.login.success/failed/logout`, `user.password.changed`)이 실제 emit·persist되며, append-only가 RED 테스트(`42501`)로 증명됨.

## 현재 상태 분석

| 영역 | 상태 |
|---|---|
| V1, V2 마이그레이션 (Spring Session, users) | ✅ A0~A1에서 완료 |
| `audit_log` 테이블 / 마이그레이션 | ❌ — A2.0에서 V3 신설 |
| `Role` enum (`MEMBER`/`AUDITOR`/`ADMIN`) | ✅ V2에 도입됨 — read 권한 분기에 재사용 |
| AuthService / Spring Security event publishing | ✅ 표준 `AuthenticationSuccessEvent` 등 발행 중 — listener만 추가하면 됨 |
| `frontend/src/types/audit.ts` (mirror, 41 events + `audit.exported`) | ✅ M12에서 완성 — 백엔드 enum이 이를 따라가야 함 |
| `api.getAuditLogs` mock (60 rows, FakeXHR) | ✅ M12 mock — A2.6에서 fetch 교체 |
| `frontend/src/components/audit/*` (Table, Filters, Pagination) | ✅ UI 완성 — 변경 없음, 계약 표면만 유지 |
| docs/03 §4 audit policy 본문 | ✅ 보존 기간/레벨/불변성 명세 존재 — A2가 이를 구현 |

## 목표 상태 (DoD)

A2 PR 머지 시점에 다음이 모두 true:

1. `audit_log` 테이블 존재 (docs/02 §2.8 스키마 동치) + 4개 인덱스
2. `app_user` DB role은 `INSERT/SELECT only`, `UPDATE/DELETE` 시도는 `42501` (insufficient_privilege) — 통합 테스트로 증명
3. `AuditEventType` Java enum 38개 값, frontend `types/audit.ts`와 1:1 일치 (CI lint 또는 fixtures 검증)
4. `AuditService.record(event)` 단일 진입점 + `@Audited` AOP + Spring Security event listener 하이브리드
5. AuthService 코드 수정 0줄 — listener만 신규 (A1 침투 0)
6. `GET /api/admin/audit?...` endpoint: ADMIN/AUDITOR 전체, MEMBER는 `actor_id=self`만 (`@PreAuthorize`)
7. `api.getAuditLogs` mock → fetch 교체 후 기존 `api.audit.test.ts` 모두 PASS (계약 동일성 보증)
8. ADR #24 (emission 위치), #25 (DB role 분리) docs/00 §5 추가
9. `gradle test` + `pnpm test` 모두 PASS, CI 그린
10. backup 브랜치 `backup/pre-reset-20260427-0036` 보존 (롤백 경로)

## Phase 실행 지도

### A2.0 — 스키마 + 마이그레이션 + ADR (TDD)

**Spec 근거**: docs/02 §2.8, §9.4, ADR #25 신규.

**RED**:
- `AuditLogSchemaTest` (Testcontainers): V3 적용 후 `audit_log` 컬럼/제약/인덱스 존재 검증 (information_schema 조회).
- `AuditLogAppendOnlyTest` (Testcontainers): `app_user` role로 접속 시 `UPDATE audit_log SET ...` → SQLState `42501`. `DELETE` 동일.

**GREEN**:
- `V3__audit_log.sql`: docs/02 §2.8 스키마 + 4개 인덱스 + `target_type` CHECK
- `V4__audit_log_revoke.sql`: `app_user` role 생성 (없으면) + `REVOKE UPDATE, DELETE ON audit_log FROM app_user` + `GRANT INSERT, SELECT`
- ADR #24 (AOP+Event 하이브리드), #25 (DB role 분리) docs/00 §5 추가

### A2.1 — Emission 인프라 (AOP + Event Listener)

**Spec 근거**: ADR #24.

**RED**:
- `AuditServiceTest`: `record(AuditEvent)` 호출 시 audit_log INSERT, actor_id/ip/event_type/metadata 모두 채워짐.
- `AuditedAspectTest`: `@Audited(event="file.deleted", target="#fileId")` 어노테이션 메서드 정상 종료 → `record()` 호출.
- `AuditedAspectTest`: 메서드 throw → `record()` 호출 안 됨 (성공한 액션만 기록).

**GREEN**:
- `AuditEvent` record (Java 21 record): eventType, actorId, actorIp, userAgent, targetType, targetId, beforeState, afterState, metadata
- `AuditEventType` enum (38 values, frontend 동기화)
- `AuditTargetType` enum
- `AuditService` (record + repo)
- `AuditLogRepository` (JPA, INSERT only가 아닌 read도 허용 — 4번 결정 read API용)
- `@Audited` annotation + `AuditedAspect` (`@AfterReturning`)
- `WebRequestContext` (HttpServletRequest에서 IP/UserAgent 추출)

### A2.2 — Append-only DB 강제 (Testcontainers role 분리 검증)

A2.0 RED 테스트가 실제 GREEN으로 통과하는지 확인. `app_user` role 시드를 Testcontainers fixture에 추가. 재검증.

### A2.3 — Read API + 권한 매트릭스

**Spec 근거**: 4번 결정, A1 Role enum.

**RED**:
- `AuditQueryControllerTest` (`@WebMvcTest`): ADMIN → 전체 200, AUDITOR → 전체 200, MEMBER → 자기 actor_id만, 익명 → 401.
- 필터 테스트: `eventType`, `actorQuery`(부분매칭), `fromDate/toDate` (inclusive), `page/pageSize` 동작 — frontend `api.audit.test.ts` 계약과 1:1.

**GREEN**:
- `AuditQueryController` (`GET /api/admin/audit`): query params → `AuditQueryService.search()`
- `AuditQueryService`: filters → JPA Specification, ADMIN/AUDITOR 분기, MEMBER scope=self
- `@PreAuthorize("hasAnyRole('ADMIN','AUDITOR') or principal.userId == #actorId")` (또는 service 레벨 분기)
- DTO: `AuditLogPageDto`, `AuditLogEntryDto` — frontend `AuditLogPage`/`AuditLogEntry` 동치

### A2.4 — A1 인증 이벤트 emission (Listener)

**Spec 근거**: docs/03 §4.1 인증 이벤트 5종.

**RED**:
- `AuthAuditListenerTest`: `AuthenticationSuccessEvent` publish → audit_log에 `user.login.success` row.
- `AbstractAuthenticationFailureEvent` (BadCredentials, Locked) publish → `user.login.failed` row + metadata에 reason.
- `LogoutSuccessEvent` publish → `user.logout` row.
- AuthService 변경 0줄 (Git diff 검증).

**GREEN**:
- `AuthAuditListener` (`@EventListener`)
- `password.changed` 이벤트는 PW 변경 endpoint가 v1.x 예정이라 본 phase 제외 (ADR #21 — must_change_password 강제 흐름은 A1.6 후속)

### A2.5 — 통합 테스트 (A1 교훈 적용)

- `@WebMvcTest` 슬라이스 테스트: AuditQueryController, 권한 분기, JSON 직렬화 — 빠른 피드백
- `@SpringBootTest` 진짜 통합: 로그인 → audit_log row 검증 (Testcontainers, HttpClient5)
- Spring Session JDBC 영향 entity 모두 처음부터 `Serializable` (A1 967b0bc 교훈)

### A2.6 — Frontend fetch 교체

- `api.ts` `getAuditLogs` 본문: `MOCK_AUDIT_LOGS` 분기 제거, fetch 호출로 교체
- `MOCK_AUDIT_LOGS` 블록 제거 (api.ts 주석 명시)
- 기존 `api.audit.test.ts` (mock 시그니처 검증) → 그대로 유지하되 MSW 또는 `vi.fn(global.fetch)` 모킹으로 전환
- E2E (`frontend/e2e/audit.e2e.ts` 신규): admin 로그인 → /admin/audit/logs → 60 row seed → 필터/페이지 동작
- backend는 dev seed로 audit_log 60건 삽입 (mock과 동일 표면)

### A2.7 — 종료 정리

- progress.md A2 종료 블록
- code-review (`superpowers:requesting-code-review` 1회)
- gh pr create → master 대상

## 비명시 (deferred / out-of-scope)

- 월별 파티셔닝 자동화 (MVP는 단일 테이블, 파티션 SQL 함수만 docs/02 §9.4 주석에 보존)
- 콜드 스토리지 아카이빙 자동화 (cron은 v1.x)
- Legal Hold UI / 지정 endpoint (docs/03 §6.3 — A4 또는 v1.x)
- view 이벤트 (`file.viewed`) emission — `audit_level=strict` 폴더 도입(폴더 테이블 부재) 필요 → A4
- 비밀번호 변경 이벤트 (`user.password.changed`) — endpoint 자체가 v1.x

## ADR 신규

- **#24**: Emission 위치 = AOP `@Audited` + Spring Security ApplicationEvent 하이브리드
- **#25**: append-only 강제 = DB role 분리 (`app_user` INSERT/SELECT only) + REVOKE UPDATE/DELETE
