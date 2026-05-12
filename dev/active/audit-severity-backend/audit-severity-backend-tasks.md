# Tasks — Audit Severity Backend

Last Updated: 2026-05-12

## Phase 상태

- [x] **P1** V19 migration — V19__audit_severity.sql + AuditLogSchemaTest 4 케이스
- [x] **P2** AuditSeverity enum + AuditSeverityMapper — 8 케이스 (17건 explicit + default + roundtrip)
- [x] **P3** AuditService.record() insert severity — record 검증 2 케이스 + 기존 FILE_UPLOADED 검증에 severity 'info' 추가
- [x] **P4** AuditLogEntryDto + AuditQueryService expose severity — SELECT 컬럼 + RowMapper + entry.severity == INFO 검증
- [x] **P5** Export writers (CSV/JSON/NDJSON) — CSV HEADERS + toRow + 3 writer test severity 직렬화 검증
- [x] **P6** Frontend wire-up — severityOf 폐기 + AuditLogEntry.severity + 6 fixture 갱신 + api.ts wire fallback
- [x] **P7** Docs sync — 02 §2.8 + 03 §4.5 신규 + progress + v1x-backlog closure
- [ ] **P8** Local verify + dev-docs-update + PR — typecheck PASS; vitest 진행중; gradle SKIPPED(CI); commit/push/PR 잔여

---

## P1 — V19__audit_severity.sql

### 작업 전 필독
- `backend/src/main/resources/db/migration/V3__audit_log.sql`
- `backend/src/main/resources/db/migration/V4__audit_log_revoke.sql`
- `frontend/src/lib/admin/auditSeverity.ts` (SEVERITY_MAP 표 — backfill CASE 와 동치)

### 구현 대상
- `backend/src/main/resources/db/migration/V19__audit_severity.sql`
  - `ALTER TABLE audit_log ADD COLUMN severity VARCHAR(10);`
  - `UPDATE audit_log SET severity = CASE event_type ... END;`
  - `ALTER TABLE audit_log ALTER COLUMN severity SET NOT NULL;`
  - `ALTER TABLE audit_log ALTER COLUMN severity SET DEFAULT 'info';`
  - `ALTER TABLE audit_log ADD CONSTRAINT audit_log_severity_check CHECK (severity IN ('info','warn','danger'));`
  - `CREATE INDEX idx_audit_severity_occurred ON audit_log(severity, occurred_at DESC) WHERE severity != 'info';`

### 검증 참조
- `backend/src/test/java/com/ibizdrive/audit/AuditLogSchemaTest.java` — schema test 갱신.

### 문서 반영
- docs/02 §2.8 컬럼 추가 (P7 에서 일괄).

---

## P2 — AuditSeverity enum + Mapper

### 작업 전 필독
- `backend/src/main/java/com/ibizdrive/audit/AuditEventType.java` (58 개)
- `frontend/src/lib/admin/auditSeverity.ts` (SEVERITY_MAP)

### 원본 코드 참조
- `AuditEventType` enum 패턴 (`UPPER_SNAKE_CASE` + `wire()` lower).

### 구현 대상
- `backend/src/main/java/com/ibizdrive/audit/AuditSeverity.java`
  - enum INFO/WARN/DANGER + `wire()` (lower).
- `backend/src/main/java/com/ibizdrive/audit/AuditSeverityMapper.java`
  - `public static AuditSeverity of(AuditEventType type)` — switch / Map.
  - frontend helper 와 동일 매핑.
- `backend/src/test/java/com/ibizdrive/audit/AuditSeverityMapperTest.java`
  - 명시 매핑 검증 (share.created → DANGER 등)
  - `Stream.of(AuditEventType.values()).forEach` — 58 개 모두 mapping 반환 (default info)

### 검증 참조
- `./gradlew :backend:test --tests "AuditSeverityMapperTest"`

### 문서 반영
- docs/03 §4 매핑표 (P7).

---

## P3 — AuditService.record() insert severity

### 작업 전 필독
- `backend/src/main/java/com/ibizdrive/audit/AuditService.java`
- `backend/src/test/java/com/ibizdrive/audit/AuditServiceTest.java`
- `backend/src/test/java/com/ibizdrive/audit/AuditLogSchemaTest.java`

### 구현 대상
- AuditService: INSERT SQL 에 `severity` 컬럼 + bind. mapper 로 자동 결정.
- AuditServiceTest: 다양한 event type 으로 record 호출 후 row.severity 검증.
- AuditLogSchemaTest: severity 컬럼/CHECK/index 검증.

### 검증 참조
- `./gradlew :backend:test --tests "AuditServiceTest" --tests "AuditLogSchemaTest"`

---

## P4 — DTO + QueryService

### 작업 전 필독
- `backend/src/main/java/com/ibizdrive/audit/dto/AuditLogEntryDto.java`
- `backend/src/main/java/com/ibizdrive/audit/AuditQueryService.java` (SELECT_COLUMNS + mapRow)
- `backend/src/test/java/com/ibizdrive/audit/AuditQueryServiceTest.java`
- `backend/src/test/java/com/ibizdrive/audit/AuditQueryControllerTest.java`
- `backend/src/test/java/com/ibizdrive/audit/AuditQueryE2ETest.java`

### 구현 대상
- `AuditLogEntryDto`: `String severity` 추가 (record canonical 순서 — 끝에 추가).
- `AuditQueryService`:
  - `SELECT_COLUMNS` 에 `a.severity` 추가.
  - `mapRow` 에 severity 매핑.
- frontend `AuditLogEntry` 동기는 P6 에서.

### 검증 참조
- `./gradlew :backend:test --tests "AuditQuery*"`

---

## P5 — Export writers

### 작업 전 필독
- `backend/src/main/java/com/ibizdrive/audit/AuditCsvWriter.java`
- `backend/src/main/java/com/ibizdrive/audit/AuditJsonWriter.java`
- `backend/src/main/java/com/ibizdrive/audit/AuditNdjsonWriter.java`
- 각 Writer Test 3종

### 구현 대상
- AuditCsvWriter: header 추가, row 직렬화에 severity 포함. 컬럼 위치는 readability — `event_type` 다음에.
- AuditJsonWriter / AuditNdjsonWriter: DTO 직접 직렬화면 자동, 누락 시 test 로 검증 후 수정.
- 3 Writer Test: severity 출력 검증.

### 검증 참조
- `./gradlew :backend:test --tests "*WriterTest"`

---

## P6 — Frontend wire-up

### 작업 전 필독
- `frontend/src/types/audit.ts`
- `frontend/src/lib/admin/auditSeverity.ts`
- `frontend/src/components/audit/SeverityTabs.tsx`
- `frontend/src/components/audit/AuditStream.tsx`
- `frontend/src/components/audit/AuditTable.tsx`

### 구현 대상
- `types/audit.ts`: `AuditLogEntry.severity: AuditSeverity` 추가 (NOT optional — backend 가 항상 반환).
- `lib/admin/auditSeverity.ts`: `AuditSeverity` 타입 + `SEVERITY_LABEL` 만 유지. `SEVERITY_MAP`, `severityOf` 삭제 + 모듈 주석을 "label/타입만 보유 — 매핑은 backend 진실" 로 갱신.
- `SeverityTabs` / `AuditStream` / `AuditTable`: `severityOf(entry.eventType)` → `entry.severity` 로 교체.
- Vitest: AuditFilters/AuditTable 테스트 fixture 에 severity 필드 추가.

### 검증 참조
- `pnpm --filter frontend typecheck`
- `pnpm --filter frontend test --run`

---

## P7 — Docs sync

### 구현 대상
- `docs/02-backend-data-model.md` §2.8 audit_log 스키마에 severity 컬럼/CHECK/index 추가.
- `docs/03-security-compliance.md` §4 에 severity 분류 기준 (info/warn/danger) + 매핑표 + 결정 근거. (frontend 의 임시 매핑 폐기 사실 명시.)
- `docs/progress.md` 에 P3 closure 엔트리.

### 검증 참조
- 자체 검토 — Self Review.

---

## P8 — Local verify + dev-docs-update + PR

### 구현 대상
- `./gradlew :backend:test --tests "*Audit*"` (or full audit slice)
- `pnpm --filter frontend typecheck && pnpm --filter frontend test --run`
- `git diff --stat origin/master` 검토 (REVOKE 정책 위반 검사 — Self Review)
- `dev-docs-update` 로 plan/context/tasks 갱신 (Phase 상태 + 핵심 결정 + blocker)
- `dev/process/2026-05-12-audit-severity-backend.md` 삭제
- `gh pr create` — title `feat(audit-severity): backend severity column + emitter + query + export wire`
  - Body: Summary + Test plan + 매핑표 + 회귀 위험 + Frontend cleanup 명시.

### 검증 참조
- PR CI 그린.
- Memory: "Local Docker-skip CI gap" — Testcontainers slice 가 로컬에서 SKIP 되므로 CI 첫 결과 확인.
