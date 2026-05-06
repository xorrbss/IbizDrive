# Wave 1 — T2: audit export endpoint (plan)

> Branch: `wave1-t2-audit-export` (worktree `.claude/worktrees/wave1-t2-audit-export`, base 8841487)
> Owner: solo (autonomous), 시작 2026-05-06

## 목표

감사 로그 CSV 내보내기를 **server-side full-result**로 정착시킨다.

- 신규 endpoint `GET /api/admin/audit/export` (`text/csv` 스트리밍)
- 가드: `@PreAuthorize("hasRole('AUDITOR') or hasRole('ADMIN')")`
- 기존 `GET /api/admin/audit` 필터 파라미터(eventType, actorId/actorQuery, targetType, targetId, fromDate, toDate) 그대로 재사용
- export 성공 시 `AUDIT_EXPORTED`(audit.exported) audit row 1건 emit (deferred → emit 활성화)
- 프론트 `/admin/audit/logs` "CSV 내보내기" 버튼을 client-side current-page → server-side 전체 결과로 교체

## 비목표

- JSON export (docs/04 §7.2 v1.x deferred 유지)
- before/after diff 표시 (별도 트랙)
- 진정한 streaming SQL (rowCallbackHandler 기반 무제한) — v1.x deferred. **v1에서는 row cap (10,000) + 단일 SELECT 페치**로 단순화 (KISS)

## 핵심 결정

### D1. 별도 endpoint 채택 (`format=csv` 분기 X)

| 측면 | `format=csv` 분기 | 별도 endpoint |
|---|---|---|
| Response type | application/json vs text/csv 혼재 | 단일 |
| 권한 가드 | controller 분기 필요 | `@PreAuthorize` 단일 적용 |
| Side-effect | export emit 분기 필요 | export endpoint만 emit |
| Spring MediaType 협상 | 어색함 | 자연스러움 |

→ **별도 endpoint**가 KISS 부합 (CLAUDE.md §3 원칙 1).

### D2. Streaming vs 전체 페치

`StreamingResponseBody`는 Servlet 스레드 점유 회피용. 그러나 row 수가 cap(10k) 이하라 메모리도 통제됨. **Spring `StreamingResponseBody` + `ResponseEntity<StreamingResponseBody>`로 응답 + 내부에서는 단일 SELECT → in-memory 리스트 → CSV writer로 단순화**. 진정한 SQL streaming(`RowCallbackHandler` + `setFetchSize`)은 v1.x deferred.

### D3. AUDIT_EXPORTED emit 시점/트랜잭션

- Export endpoint 자체는 **read-only** (`@Transactional(readOnly = true)`)
- 응답 stream **flush 완료 후** `AuditService.record(...)` 호출 — REQUIRES_NEW가 이미 보장됨
- 실패 시 ERROR 로그만 (기존 listener 패턴 동형)
- Metadata: `{filters: {...wired filters...}, rowCount: N, truncated: bool, format: "csv"}`

### D4. Row cap 정책

- 하드 cap **10,000 rows**
- 초과 시 처음 10,000건만 반환 + 응답 헤더 `X-Audit-Export-Truncated: true` 추가
- AUDIT_EXPORTED metadata에 `truncated: true` 기록
- 추가 페이징은 v1.x deferred (대용량 export는 별도 BG job으로 옮길 예정 → docs/04 §13)

### D5. 가드 위치

- `AuditQueryController.list()`는 service에서 분기(MEMBER self) — 변경 없음
- `AuditQueryController.exportCsv()`는 메서드 단위 `@PreAuthorize` — MEMBER 차단 (403)
- 별도 controller로 분리하지 않음 (1 endpoint 1 controller는 과분리; 같은 도메인이므로 응집)

### D6. CSV 컬럼 — 기존 frontend `auditCsv.ts`와 동치

```text
id, occurredAt, eventType, actorId, actorName, resourceType, resourceId, resourceName, ip, metadata
```

- `resourceName`은 frontend와 동일하게 항상 빈 문자열(현 service가 `null`로 채움 — RowMapper 주석 참조)
- BOM `\uFEFF` 선두 + RFC 4180 quoting + line terminator `\r\n`
- 기존 frontend `toAuditCsv` 함수와 동등 출력 — 양 측 테스트 케이스 공유 가능

## 리스크

1. **메모리**: 10k rows × ~500B = ~5MB. 동시 export 다발 시 압박. v1 cap이 좁음 — 모니터링 후 조정.
2. **CSV injection**: 셀이 `=`/`+`/`-`/`@`로 시작하면 Excel formula 위험. v1은 quoting만 적용(공통 권고는 prefix `'` 추가지만 KISS — 추후 강화).
3. **AUDIT_EXPORTED emit 실패 누락**: ERROR 로그만 → alert 미연동 (기존 패턴 동일, ADR #24 trade-off 수용).

## 수용 기준 (Acceptance)

- [x] `GET /api/admin/audit/export` 200 OK + `Content-Type: text/csv;charset=UTF-8` + `Content-Disposition: attachment; filename="audit_logs_<date>.csv"` 헤더
- [x] MEMBER 호출 → 403, 익명 → 401
- [x] 필터 파라미터 적용 결과만 export (eventType / actorQuery / targetType / targetId / fromDate / toDate)
- [x] 응답 본문 1행 = header, 이후 row, BOM 포함, RFC 4180 quoting
- [x] export 성공 시 audit_log에 `audit.exported` 1건 (actor=호출자, target_type=AUDIT, metadata에 filters/rowCount)
- [x] frontend "CSV 내보내기" 버튼이 server endpoint로 다운로드 트리거 (현재 필터 query string 전달)
- [x] BETA-RELEASE.md §6 emit coverage 36→37, 미emit 8→7 갱신
- [x] docs/02 §7.12 + docs/04 §7.2 동기화

## 참고

- ADR #24: audit infrastructure (write 경로, REQUIRES_NEW)
- ADR #21: admin endpoints + @PreAuthorize 패턴
- T1 머지 헤드: 8841487 (admin-user-search-update)
- 충돌 검사: T4(`wave2-t4`)와 파일 겹침 없음 (AuditEventType.java는 T4가 DEPARTMENT_*만 추가, T2는 수정 X)
