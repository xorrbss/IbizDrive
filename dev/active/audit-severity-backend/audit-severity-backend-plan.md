# Plan — Audit Severity Backend

Last Updated: 2026-05-12

## 요약

`audit_log.severity` 컬럼을 신설하고 backend 단일 진입점(`AuditService.record`)에서 `AuditEventType → severity` 매핑을 자동 결정해 INSERT, 모든 조회/Export 응답 wire 에 노출. Frontend 의 임시 fallback helper(`auditSeverity.severityOf`)는 type+label 만 남기고 매핑 표는 제거(graceful upgrade 완성).

## 왜 bootstrap 인가

- DB schema (V19) + Java(enum/mapper/service/dto/query/3 writers) + Frontend(types/components) + Docs(02/03/progress) 변경이 동시에 일어나는 cross-cutting 작업.
- 매핑 SQL(backfill) 과 Java mapper 가 정확히 동치여야 함 — 한쪽 표만 보고 작업하면 회귀.
- `audit_log` 는 V4 에서 `REVOKE UPDATE,DELETE` 되어있어 마이그레이션 SQL 작성 시 컬럼 ADD + DEFAULT + 인덱스만 허용. 별도 가드 필요.

## 현재 상태 분석

### Backend
- `audit_log` 컬럼: `event_type, actor_id, actor_ip, user_agent, target_type, target_id, before_state, after_state, metadata` (V3) — **severity 없음**.
- `AuditService.record` 가 단일 INSERT 진입점 (REQUIRES_NEW). 모든 emitter(`@Audited` AOP + listeners) 가 통과.
- DTO `AuditLogEntryDto` 가 frontend `AuditLogEntry` 와 1:1 wire — severity 추가 시 양쪽 동기 필요 (계약).
- `AuditQueryService` `SELECT_COLUMNS` 상수 + `mapRow` — severity 컬럼 SELECT/매핑 갱신.
- Export writers 3종: `AuditCsvWriter`, `AuditJsonWriter`, `AuditNdjsonWriter` (CSV 는 명시 header, JSON/NDJSON 은 ObjectMapper 위임).

### Frontend
- `frontend/src/lib/admin/auditSeverity.ts` — 임시 fallback helper. 주석에 "backend 합류 시 entry 가 severity 직접 노출하면 우선 (graceful upgrade)" 명시. P3 가 이걸 닫는다.
- `SeverityTabs`, `AuditStream`, `AuditTable` 가 `severityOf(entry.eventType)` 호출 추정 — 실제 사용처 grep 으로 확정.
- `frontend/src/types/audit.ts` `AuditLogEntry` 에 severity 필드 추가.

### Docs
- `docs/02-backend-data-model.md` §2.8 (line 264~294) — audit_log 스키마 + REVOKE.
- `docs/03-security-compliance.md` §4 (line 686~) — 감사 정책. severity 분류 기준이 docs 에 없음 (현재 frontend helper 만 정의).

## 목표 상태

1. `audit_log.severity VARCHAR(10) NOT NULL DEFAULT 'info'` + CHECK + 부분 인덱스 (`severity, occurred_at DESC` WHERE severity != 'info' — danger/warn 만 hot path).
2. 기존 row backfill (V19 단일 마이그레이션 내 CASE UPDATE 후 SET NOT NULL — REVOKE 적용 전 V3/V4 가드 검토 필요).
3. `AuditSeverity` enum + `AuditSeverityMapper` (frontend helper 와 정확히 동일 표) — 58 개 event 매핑 완전 커버.
4. `AuditService.record` 가 mapper 호출해 severity INSERT.
5. `AuditLogEntryDto.severity` 노출 + `AuditQueryService` SELECT/RowMapper 갱신.
6. CSV/JSON/NDJSON export 가 severity 컬럼 포함. CSV header 갱신.
7. Frontend `AuditLogEntry.severity` 타입 추가, 컴포넌트가 `entry.severity` 직접 사용. `severityOf` / `SEVERITY_MAP` 삭제, `SEVERITY_LABEL` / `AuditSeverity` 타입은 유지.
8. Docs 02 §2.8 / 03 §4 / progress.md 동기.

## Phase 실행 지도

| Phase | 범위 | 산출물 |
|---|---|---|
| P1 | DB migration | V19__audit_severity.sql |
| P2 | Java domain | AuditSeverity, AuditSeverityMapper, AuditSeverityMapperTest |
| P3 | Service insert | AuditService 갱신 + Test |
| P4 | Query response | DTO+QueryService+RowMapper+Test (Controller/E2E) |
| P5 | Export writers | CSV/JSON/NDJSON + 3 Test |
| P6 | Frontend wire | types/audit.ts + components + helper trim + Vitest |
| P7 | Docs sync | 02 §2.8 / 03 §4 / progress |
| P8 | Verify + PR | gradle test + pnpm typecheck/test + dev-docs-update + PR |

## Acceptance Criteria

- [ ] V19 마이그레이션이 깨끗하게 적용되고 기존 row 가 매핑된 severity 로 backfill 된다.
- [ ] V4 REVOKE 정책과 충돌 없음 (DDL 권한 vs DML 권한 — Flyway 가 DDL 실행 시 별도 role 사용 가정, 동작 확인).
- [ ] `AuditSeverityMapper.of()` 가 58 개 event 모두 매핑된다 (default info). 명시 매핑은 frontend helper 와 동일.
- [ ] `AuditService` INSERT 가 severity 를 함께 기록한다 (AuditServiceTest 새 검증).
- [ ] `GET /api/admin/audit` 응답 entries 가 `severity` 필드를 포함한다 (AuditQueryControllerTest / AuditQueryE2ETest).
- [ ] `GET /api/admin/audit/export?format=csv|json|ndjson` 모두 severity 컬럼 포함 (3 writer test).
- [ ] Frontend `AuditLogEntry.severity` 가 type 에 추가되고 컴포넌트가 entry.severity 직접 사용. `auditSeverity.ts` 는 type + label 만 남음.
- [ ] Frontend Vitest + backend Gradle test 모두 통과.
- [ ] docs/02 §2.8 + docs/03 §4 에 severity 컬럼/매핑 기준 반영.

## 검증 게이트

- **G1**: `./gradlew :backend:test --tests "*Audit*"` 통과 (worktree 격리)
- **G2**: `pnpm --filter frontend typecheck && pnpm --filter frontend test --run`
- **G3**: V19 SQL 을 로컬 PG 에 dry-run (Flyway migrate `--dry-run` 또는 `psql -f` 후 rollback)
- **G4**: `git diff origin/master` Self-Review (REVOKE 정책 위반 없음, 매핑 완전성, 시드 데이터 영향)

## 리스크 & 완화

| 리스크 | 영향 | 완화 |
|---|---|---|
| V4 REVOKE 가 V19 DDL 실행도 막을 수 있음 | 마이그레이션 실패 | Flyway 가 `app_admin` role 로 DDL 실행하는지 application.yml 확인. 안 되면 V19 에서 `SET ROLE` 추가. |
| 매핑이 frontend/SQL/Java 3 곳에서 어긋남 | 회귀 (UI severity 와 DB 불일치) | 단일 sources-of-truth = `AuditSeverityMapper` Java enum 표. V19 backfill SQL 의 CASE 와 mapper test 가 동일 표를 검증 (테스트에서 비교). |
| 기존 row 가 많을 경우 backfill UPDATE 느림 | 마이그레이션 시간 증가 | MVP 환경(베타) — row 수 적음. 인덱스를 SET NOT NULL 직전이 아니라 backfill 후에 생성. |
| `user.login.failed` 가 frontend 에서 'warn' 매핑 — design 은 5회 실패 시 danger. backend 매핑은 단건 기준 | UX/감지 불일치 | 본 PR 은 frontend helper 와 동일 매핑만 적용. "연속 실패 시 danger 승격" 은 별도 alerting 트랙 (TODO 명시). |
| Export writer 3종 모두에 severity 추가 시 frontend export 다운로드 의존 코드 회귀 | 베타 운영 영향 | 각 writer 별 test 가 header/row/CSV 줄 순서를 검증. |

## TODO (out of scope, 별도 트랙)

- 연속 실패/대량 권한 회수 등 동적 severity 승격 (alerting/v1.x++)
- audit_log 파티셔닝 (docs/02 §9.4)
