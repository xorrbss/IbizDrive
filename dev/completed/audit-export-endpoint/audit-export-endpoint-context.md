# Wave 1 — T2: audit export endpoint (context)

## 출발점

T1(`admin-user-search-update`, #59) 머지 직후 master = 8841487. 이 시점의 audit 인프라:

- `AuditEventType.AUDIT_EXPORTED("audit.exported")` 이미 enum line 78 등재 (44개 중 1개)
- emit listener / endpoint **부재** (deferred). docs/04 §7.2 line 203, BETA §6 line 101 모두 deferred 표기.
- 프론트 `/admin/audit/logs`에서 `toAuditCsvBlob`로 current-page만 client-side export 중

이 작업은 deferred 상태(36 emit / 8 미emit)를 (37 / 7)로 줄이고, server-side full-result export endpoint를 신설하여 docs/04 §7.2의 두 deferred 체크박스를 [x]로 갱신한다.

## 영향받는 파일

### Backend (수정/추가)

- **AuditQueryController.java** — `exportCsv()` 메서드 추가. 기존 `list()` 계약 변경 없음. `@PreAuthorize("hasRole('AUDITOR') or hasRole('ADMIN')")`.
- **AuditQueryService.java** — `exportAll(filters, viewerRole)` 추가. WHERE 빌더 헬퍼는 `search()`와 공유(공통 추출). row cap 10k.
- **AuditCsvWriter.java** (NEW) — `AuditLogEntryDto` → CSV bytes (BOM + RFC 4180). 단위 테스트 가능한 순수 함수.
- **AuditExportEvent.java** (NEW) — `actorId`, `filtersJson`, `rowCount`, `truncated` record.
- **AuditExportListener.java** (NEW) — `@EventListener` → `AuditService.record(...)` 호출 (REQUIRES_NEW가 이미 보장).

### Backend (테스트)

- **AuditExportE2ETest.java** (NEW) — 권한 가드(MEMBER 403, AUDITOR/ADMIN 200), CSV 본문, AUDIT_EXPORTED row.
- **AuditCsvWriterTest.java** (NEW) — quoting / escape / BOM / metadata JSON 직렬화.

### Frontend

- **app/admin/audit/logs/page.tsx** — `handleExport`를 server endpoint 호출로 교체 (anchor `download` + 현재 필터 query string).
- **lib/api.ts** — `getAuditLogsExportUrl(filters)` 헬퍼 추가 (URL builder).
- **app/admin/audit/logs/page.test.tsx** — 새 동작 검증 (anchor href 포함 query string + click 시 트리거).

### Docs (sync)

- **BETA-RELEASE.md** §6 line 101 (36→37, 8→7), line 115 deferred 라인 제거 또는 갱신.
- **docs/02-backend-data-model.md** §7.12 표 + 본문 endpoint 사양 추가.
- **docs/04-admin-operations.md** §7.2 두 체크박스 [x] 처리 + 본문 정정.
- **docs/audit-emit-gap-mapping.md** AUDIT_EXPORTED 항목 deferred → emit 상태로 이동.

## 주요 패턴 / 규약

### 가드

- `SecurityConfig.anyRequest().authenticated()` + 메서드 단위 `@PreAuthorize`. ADR #21 admin endpoint 패턴 동형.
- 익명 = 401 (Spring Security default), MEMBER = 403 (`@PreAuthorize` 차단).

### Audit emit (REQUIRES_NEW)

- `AuditService.record(AuditEvent)`는 이미 `@Transactional(propagation = REQUIRES_NEW)` — caller가 read-only여도 INSERT 별도 commit.
- emit 실패는 ERROR 로그만 — caller 흐름 보호 (PermissionAuditListener와 동형, ADR #24).

### CSV

- `\uFEFF` BOM 선두 (Excel UTF-8)
- `\r\n` line terminator (RFC 4180)
- 셀에 `,` `"` `\n` `\r` 포함 시 전체 인용 + 내부 `"` → `""`
- frontend `toAuditCsv` 함수와 동일 컬럼/순서

### Row cap (10,000)

- `LIMIT 10001` 페치 → 10001번째 row 존재 = `truncated=true`. 응답에는 처음 10,000건만, 헤더 `X-Audit-Export-Truncated: true`.
- AUDIT_EXPORTED metadata에 `truncated` 키 포함.

## 함정 / 주의

1. **CSV 출력 순서 결정성**: `AuditQueryService`의 `ORDER BY a.occurred_at DESC, a.id DESC`를 export에서도 그대로 적용. tie-breaker 미적용 시 테스트 flaky.
2. **WebRequestContextHolder**: AUDIT_EXPORTED emit 시 actor IP/UA가 `WebRequestContextHolder.currentIp()/currentUserAgent()`에서 오므로, 응답 본문 commit **이전**에 캡처해두거나 `ApplicationEventPublisher.publishEvent`를 controller 메서드 내부(요청 스레드)에서 호출 → listener가 즉시 `WebRequestContextHolder` 읽도록. **Controller에서 publish (요청 스레드 내)** 채택.
3. **MEMBER 권한 엣지**: query endpoint는 MEMBER도 self 조회 허용이지만, export는 정책상 더 엄격하게 ADMIN/AUDITOR만(요건 명시). 두 endpoint 가드가 다르다는 점을 docs/02 §7.12 표에 명시.
4. **Date filter 경계**: `AuditQueryService` 기존 정책(toDate inclusive — 다음날 00:00 UTC exclusive) 그대로 재사용. 별도 처리 추가하면 두 endpoint 결과가 어긋남.
5. **CSV injection**: v1은 quoting만. `=`/`+`/`-`/`@` prefix 셀 검사는 v1.x로 미룸 (위험은 외부 첨부가 아닌 내부 audit 데이터 → 노출 표면 작음).

## 검증 방법

- Backend: `cd backend && mvn -pl . test -Dtest='AuditExport*'` (신규) + 회귀로 `AuditQueryE2ETest` 통과.
- Frontend: `cd frontend && pnpm test src/app/admin/audit/logs/page.test.tsx`.
- 전체: `cd backend && mvn test` / `cd frontend && pnpm test && pnpm typecheck`.
