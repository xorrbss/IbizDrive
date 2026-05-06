# Wave 1 — T2: tasks

> Status: **shipping** (2026-05-06). 모든 P1~P7 완료 — 본 문서는 PR 머지 후 `dev/completed/`로 이동.

## P1 — AuditCsvWriter 유틸 + 단위 테스트
- [x] `AuditCsvWriter.write(OutputStream, List<AuditLogEntryDto>)` — `OutputStreamWriter(UTF-8)` + BOM + `\r\n`
- [x] BOM + RFC 4180 quoting + `\r\n`
- [x] metadata는 JSON serialize (Jackson `ObjectMapper` 주입; null → 빈 셀, 직렬화 실패 시 IllegalStateException)
- [x] 헤더 상수 = frontend `AUDIT_CSV_HEADERS`와 1:1 (10 컬럼)
- [x] `AuditCsvWriterTest` — 빈 리스트, 정상 row, 특수문자 RFC 4180 quoting, metadata key 순서, null actor

## P2 — AuditQueryService.exportAll + endpoint
- [x] `AuditQueryService.exportAll(filters, viewerId, viewerRole)` — viewerRole이 ADMIN/AUDITOR가 아니면 IllegalStateException(방어 가드)
- [x] WHERE 빌더 `appendFilterClauses(...)` private helper로 추출 → `search()`가 재사용 (필터 시맨틱 drift 방지)
- [x] `LIMIT cap+1` (cap=10,000) → 결과가 cap+1이면 truncated=true, subList(0, cap)
- [x] return record `AuditExportResult(List<AuditLogEntryDto> entries, boolean truncated)`
- [x] `AuditQueryController.exportCsv(...)` — `@PreAuthorize("hasRole('AUDITOR') or hasRole('ADMIN')")`, `@GetMapping("/export")`, `ResponseEntity<StreamingResponseBody>`
- [x] 응답 헤더: `Content-Type: text/csv;charset=UTF-8`, `Content-Disposition: attachment; filename="audit_logs_<yyyy-MM-dd>.csv"`, truncated 시 `X-Audit-Export-Truncated: true`
- [x] 응답 stream 작성 후 `applicationEventPublisher.publishEvent(new AuditExportEvent(...))`

## P3 — AUDIT_EXPORTED emit
- [x] `AuditExportEvent(UUID actorId, InetAddress actorIp, String userAgent, String filtersJson, int rowCount, boolean truncated)` record — IP/UA를 이벤트에 캐리(StreamingResponseBody가 다른 스레드에서 실행될 때 ThreadLocal 손실 방지)
- [x] `AuditExportListener.onExport(...)` → `AuditService.record(...)` (REQUIRES_NEW, ADR #24)
- [x] metadataJson = `{"filters":{...},"rowCount":N,"truncated":bool,"format":"csv"}` — `LinkedHashMap` 결정적 순서
- [x] try/catch RuntimeException → ERROR 로그 (PermissionAuditListener와 동형)

## P4 — E2E 테스트
- [x] `AuditExportE2ETest`:
  - [x] anonymous → 401
  - [x] MEMBER 로그인 → 403 (`@PreAuthorize`)
  - [x] AUDITOR 로그인 → 200 + Content-Type=text/csv + BOM + 행 수 일치 (5 wrong-PW seed → ≥6 lines)
  - [x] ADMIN 로그인 + `eventType=user.login.failed` 필터 → 매칭 row만
  - [x] 호출 후 audit_log에 `audit.exported` 1건 (actor_id=호출자, target_type=audit, metadata.rowCount/format/truncated/eventType 일치)
- [x] 기존 `AuditQueryE2ETest` / `AuthAuditE2ETest` / `AuthScenarioIntegrationTest` 회귀 없음 (Audit 패키지 전체 그린)

## P5 — Frontend
- [x] `lib/api.ts`에 `getAuditLogsExportUrl(filters)` — `URLSearchParams`로 `eventType/fromDate/toDate/actorQuery` 빌드
- [x] `app/admin/audit/logs/page.tsx` — `<a href download="">` anchor pattern, fetch 미사용
- [x] dead code (`lib/auditCsv.ts`, `auditCsv.test.ts`) 삭제 (KISS / YAGNI)
- [x] `page.test.tsx` 신규 — 기본 URL, eventType 필터 반영, click 시 fetch 미발생 검증

## P6 — Docs
- [x] BETA-RELEASE.md — 37/44 emit, 미emit 7, T2 트랙 추가, line 115 정정
- [x] docs/02-backend-data-model.md §7.12 — `/api/admin/audit/export` 행 추가
- [x] docs/04-admin-operations.md §7.2 — server-side 스트리밍 / audit.exported runtime emission [x] 전환
- [x] docs/audit-emit-gap-mapping.md — 이미 `dev/completed/` 아카이브, BETA-RELEASE.md 동기화로 충분

## P7 — Wrap up
- [x] git diff 자체 리뷰 + 적대적 챌린지 — 권한·CSV injection (legacy parity, 별도 트랙 권장)·메모리(10k cap)·트랜잭션 경계(REQUIRES_NEW)·동시성(read-only tx) 점검 통과
- [x] backend `./gradlew test --tests "com.ibizdrive.audit.*"` — 전체 그린
- [x] frontend `pnpm exec vitest run` — 769/769 그린, `pnpm exec tsc --noEmit` 0 error, `pnpm exec next lint` clean
- [x] dev-docs-update — 본 문서 + plan/context 갱신
- [x] `dev/process/wave1-t2-2026-05-06.md` 삭제
- [x] 단일 commit + PR (xorrbss/IbizDrive#TBD)

## 검증 결과 요약 (2026-05-06)

| 검증 | 도구 | 결과 |
|------|------|------|
| AuditCsvWriter 단위 | `./gradlew test --tests com.ibizdrive.audit.AuditCsvWriterTest` | 5/5 PASS |
| AuditQueryController 단위 | `./gradlew test --tests com.ibizdrive.audit.AuditQueryControllerTest` | PASS |
| AuditExportE2ETest | `./gradlew test --tests com.ibizdrive.audit.AuditExportE2ETest` | 5/5 PASS |
| Audit package 전체 | `./gradlew test --tests "com.ibizdrive.audit.*"` | PASS (cache hit) |
| Frontend tests | `pnpm exec vitest run` | 769/769 PASS, 96 files |
| Frontend typecheck | `pnpm exec tsc --noEmit` | 0 error |
| Frontend lint | `pnpm exec next lint` | No warnings/errors |

## 적대적 리뷰 결과 / 보존 리스크

- **CSV formula injection** (`=cmd|...` 같은 셀이 Excel에서 실행될 위험): 현재 `AuditCsvWriter.cell()`은 RFC 4180 quoting만 적용하며, 선두 `=/+/-/@` 이스케이프는 미적용. 단, 이는 삭제된 legacy `lib/auditCsv.ts`와 동일 동작 → **회귀 아님**. 별도 트랙(보안 강화)에서 양쪽 동시에 보강 권장. T2 atomic scope 외.
- **Memory**: 10k cap × ~10 컬럼 × 평균 ~몇백 byte = 수 MB 한도 — 인-메모리 List 적재 후 stream OK. 진정한 row-by-row stream(JdbcTemplate `RowCallbackHandler`)은 v1.x로 deferred.
- **Transaction boundary**: read-only `exportAll()` tx → controller 메서드 종료 후 `StreamingResponseBody` lambda 실행 → `publishEvent` → REQUIRES_NEW listener tx INSERT. append-only 불변식 유지 (ADR #24).
- **IP/UA capture**: `WebRequestContextHolder` ThreadLocal을 controller scope에서 읽어 `AuditExportEvent`에 캐리 — async 스레드에서도 안전.
