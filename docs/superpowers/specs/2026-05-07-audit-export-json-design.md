# Design — audit-export-json (Wave 1 T2 후속)

> **목표**: ADMIN/AUDITOR가 `GET /api/admin/audit/export` 호출 시 `format=json` 파라미터로 동일 필터 결과를 JSON 배열로 다운로드할 수 있도록 한다. 기존 CSV 경로는 default(`format=csv`)로 그대로 유지하여 호출자 호환성 무영향.

- 트랙 명: `audit-export-json`
- 분류: Wave 1 T2 (`audit-export-endpoint`) follow-up — `BETA-RELEASE.md §7` "audit log JSON export endpoint" v1.x deferred 항목 활성화
- 시작: 2026-05-07
- 선행 closure: `audit-export-endpoint` (Wave 1 T2, 2026-05-06 PR #60)

---

## 1. 배경 / 문제

Wave 1 T2(`audit-export-endpoint`)에서 `GET /api/admin/audit/export`가 server-side full-result CSV 스트리밍으로 ship되었다. CSV는 사용자(Excel) 친화적이지만 외부 SIEM/jq 파이프라인 등 기계 처리 도구는 JSON을 선호한다. `docs/03 §4` audit event 명세는 이미 `'audit.exported' // docs/04 §7.2 — CSV/JSON 내보내기 자체도 감사 기록`으로 JSON branch를 예고하고 있고, `BETA-RELEASE.md §7` 마지막 항목에 "audit log JSON export endpoint — docs/04 §7.2 v1.x"로 명시 deferred되어 있다.

본 트랙은 해당 deferred 항목을 활성화한다. 기존 controller·service·event 구조를 그대로 재사용하고, 차이는 (1) 응답 직렬화기 1개 신규, (2) controller 분기, (3) audit metadata `format` 필드 동적화 3곳뿐이다.

---

## 2. 비기능 / 호환성 요구

| 항목 | 결정 |
|---|---|
| 기존 호출자 호환 | `format` 파라미터 미지정 시 CSV 응답 — 기존 anchor (`<a href="/api/admin/audit/export?...">`) 무수정 동작 |
| 가드 | `@PreAuthorize("hasRole('AUDITOR') or hasRole('ADMIN')")` 그대로. JSON 권한 분리 없음 (감사권 = 동일) |
| 캡 / truncation | CSV와 동일 (`LIMIT cap+1` cap=10,000, `X-Audit-Export-Truncated` 헤더). `AuditQueryService.exportAll`은 파일 형식과 무관 |
| AUDIT_EXPORTED emit | enum 변경 0. `AuditExportEvent`에 `format` 필드 추가, `AuditExportListener`가 metadata에 `format=csv\|json` 동적 출력 |
| 스트리밍 모델 | `StreamingResponseBody` 그대로. 진정한 SQL streaming은 v1.x deferred (CSV와 동치) |
| 인코딩 | UTF-8. JSON은 BOM **미부착**(JSON 표준), CSV는 BOM 유지(Excel 한글) |
| Content-Type | csv → `text/csv;charset=UTF-8`, json → `application/json;charset=UTF-8` |
| 파일명 | `audit_logs_<YYYY-MM-DD>.csv` / `audit_logs_<YYYY-MM-DD>.json` |
| 응답 envelope | **plain JSON 배열** `[{...}, {...}]` — wrapping 객체 없음. `truncated`는 헤더로 전달, CSV가 헤더 외 wrapping 없는 것과 동치 |
| 새 audit enum | 0 |
| 새 에러 코드 | 0. 미지원 format은 400 + `BAD_REQUEST`(`GlobalExceptionHandler.handleBadRequest`가 `IllegalArgumentException`을 표준 매핑) — 기본값 fallback이 아닌 명시적 거절 |

---

## 3. 외부 계약

### 3.1 Endpoint

```
GET /api/admin/audit/export
    ?fromDate=YYYY-MM-DD
    &toDate=YYYY-MM-DD
    &actorQuery=<string>
    &eventType=<string>
    &targetType=<string>
    &targetId=<UUID>
    &format=csv|json     # 신규, default=csv
```

- 200 OK 응답 본문: format=csv → BOM + CSV (RFC 4180), format=json → JSON 배열
- 응답 헤더: `Content-Type` / `Content-Disposition` / `X-Audit-Export-Truncated` (cap 초과 시 `true`)
- 401: 미인증, 403: MEMBER 권한, 400: `format`이 `csv\|json` 외 값

### 3.2 JSON 응답 모양

배열 원소 1개당 `AuditLogEntryDto`와 동일한 10 필드:

```json
[
  {
    "id": "01935b2a-...-...-...-...",
    "occurredAt": "2026-05-07T09:12:34.567+09:00",
    "eventType": "user.login.failed",
    "actorId": "0a1b...",
    "actorName": "kim@example.com",
    "resourceType": "user",
    "resourceId": "0a1b...",
    "resourceName": "kim@example.com",
    "ip": "10.0.0.1",
    "metadata": { "reason": "BAD_PASSWORD", "attempt": 3 }
  },
  ...
]
```

- 필드 명칭 = `AuditLogEntryDto` record (camelCase)
- `null` 필드는 `null`로 직렬화 (Jackson 기본)
- `metadata`는 `Map<String, Object>` 그대로 — CSV는 이를 JSON 문자열 cell로 인라인하지만, JSON branch는 nested 객체로 그대로 둔다 (이중 직렬화 회피)
- 빈 결과: `[]` (헤더는 동일, `Content-Disposition: attachment` 유지)

### 3.3 audit_log metadata (AUDIT_EXPORTED)

기존:

```json
{"filters":{...},"rowCount":N,"truncated":bool,"format":"csv"}
```

신규: `format` 값이 `"csv"` 또는 `"json"`. 키 순서·구조 불변.

---

## 4. Backend 구현

### 4.1 신규 파일

| 경로 | 책임 |
|---|---|
| `backend/src/main/java/com/ibizdrive/audit/AuditJsonWriter.java` | `List<AuditLogEntryDto> → OutputStream` JSON 배열 직렬화. `ObjectMapper` 주입 재사용 |
| `backend/src/test/java/com/ibizdrive/audit/AuditJsonWriterTest.java` | 단위 테스트 — 빈 결과·일반 결과·`metadata` nested·`null` 필드·`InetAddress` 미사용 검증 |

`AuditJsonWriter` 설계:

```java
@Component
public class AuditJsonWriter {
    private final ObjectMapper objectMapper;

    public AuditJsonWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * JSON 배열을 {@code out}에 기록. Jackson SequenceWriter 미사용 — 캡(10k) 결과는 메모리에서
     * 한 번에 직렬화해도 충분 (CsvWriter와 동일 모델, KISS).
     */
    public void write(OutputStream out, List<AuditLogEntryDto> entries) throws IOException {
        objectMapper.writeValue(out, entries);
    }
}
```

> **설계 결정**: SequenceWriter / streaming token API는 도입하지 않는다. 현재 구현은 cap 적용 후 메모리에 모두 적재하므로 standard `writeValue` 1회 호출이 가장 단순. 진정한 SQL streaming + JSON streaming은 v1.x.

### 4.2 수정 파일

#### `AuditQueryController.java`

- 메서드 시그니처 변경: `exportCsv(...)` → `export(...)` (rename), 신규 `@RequestParam(name="format", required=false, defaultValue="csv") String format`
- `format` 검증: `"csv"` / `"json"` 외 → `IllegalArgumentException("audit export format must be csv or json")` throw. `GlobalExceptionHandler.handleBadRequest`가 `ApiError.of("BAD_REQUEST", ex.getMessage(), null)` + HTTP 400으로 변환(기존 매핑, 본 트랙은 변경 없음). 추후 enum으로 강타입화는 v1.x.
- `csvWriter` 의존성 옆에 `AuditJsonWriter jsonWriter` 추가
- `StreamingResponseBody body = ...` 분기:
  - `format.equals("csv")` → 기존 CSV 본문, 헤더 `text/csv` + `.csv` 파일명
  - `format.equals("json")` → JSON 본문, 헤더 `application/json` + `.json` 파일명
- `AuditExportEvent publish` 시 `format` 인자 전달

#### `AuditExportEvent.java`

```java
public record AuditExportEvent(
    UUID actorId,
    InetAddress actorIp,
    String userAgent,
    String filtersJson,
    int rowCount,
    boolean truncated,
    String format         // 신규
) {}
```

#### `AuditExportListener.java`

```java
String metadata = "{\"filters\":" + ... 
    + ",\"format\":\"" + event.format() + "\"}";   // "csv" 하드코딩 → event 값
```

`format` 값은 controller에서 검증된 후 전달되므로 추가 escape 불필요. 방어적으로 `"csv".equals(event.format()) || "json".equals(event.format())` 가드 추가 (그 외 → "csv" fallback + WARN 로그 — listener는 swallow 정책).

### 4.3 수정 / 신규 테스트

| 경로 | 변경 |
|---|---|
| `backend/src/test/java/com/ibizdrive/audit/AuditJsonWriterTest.java` | 신규 (위 §4.1 참조) |
| `backend/src/test/java/com/ibizdrive/audit/AuditQueryControllerTest.java` | json branch 신규 케이스 — `format=json` 200 OK + Content-Type + `.json` 파일명 + body 배열, `format=csv` 회귀, `format=invalid` 400 |
| `backend/src/test/java/com/ibizdrive/audit/AuditExportListenerTest.java` | metadata `format` 동적화 검증 — `format="json"` event → metadata에 `"format":"json"` 포함 |

---

## 5. Frontend 구현

### 5.1 수정 파일

#### `frontend/src/lib/api.ts`

기존 `buildAuditExportUrl(filters: AuditLogFilters): string` → `(filters, format: 'csv' | 'json' = 'csv'): string`. 빈/누락 시 default `csv`. JSDoc에 format 의미 추가.

#### `frontend/src/app/admin/audit/logs/page.tsx`

기존 "CSV 내보내기" anchor 옆에 "JSON 내보내기" anchor 1개 추가. 같은 컴포넌트 패턴(anchor + `download` 속성 비움 — backend가 Content-Disposition 결정). format query param 차이만:

```tsx
<a href={api.buildAuditExportUrl(currentFilters, 'csv')} ...>CSV 내보내기</a>
<a href={api.buildAuditExportUrl(currentFilters, 'json')} ...>JSON 내보내기</a>
```

> **UX 설계 결정**: select(드롭다운) 미도입. anchor 2개가 단순하고 단일 클릭 다운로드. 향후 형식이 3개 이상 늘면 select 도입 검토.

### 5.2 수정 / 신규 테스트

| 경로 | 변경 |
|---|---|
| `frontend/src/app/admin/audit/logs/page.test.tsx` | "JSON 내보내기" anchor 존재 + href에 `format=json` 포함 + 필터 입력 시 query string 합성 검증. 기존 CSV 케이스 회귀(format=csv 또는 미지정 모두 default csv) |
| `frontend/src/lib/api.audit.test.ts`(or 해당 위치) | `buildAuditExportUrl(filters, 'json')` URL 형태 단위 테스트. 기존 단위 테스트가 있으면 추가, 없으면 신규 |

---

## 6. Docs 갱신

| 경로 | 변경 |
|---|---|
| `docs/02-backend-data-model.md` §7.12 | `format` 행 추가(`csv\|json`, default csv). 응답 모양 절에 JSON 배열 예시 |
| `docs/04-admin-operations.md` §7.2 | line 263~264 갱신 — `format=csv\|json` 분기, JSON nested metadata 표기 |
| `docs/03-security-compliance.md` §4 | `audit.exported` 코멘트는 이미 "CSV/JSON 내보내기 자체도 감사 기록"으로 적혀 있어 변경 없음 (확인만) |
| `BETA-RELEASE.md` §7 | "audit log JSON export endpoint — docs/04 §7.2 v1.x" 라인 제거. §6 audit emit 행에 `format=json` 활성화 명시 |
| `docs/progress.md` | 트랙 closure 엔트리 |

---

## 7. 핵심 원칙 충돌 점검 (CLAUDE.md §3)

| 원칙 | 검토 |
|---|---|
| 1 URL이 어디 소유 | N/A — admin export 페이지 자체는 url 변경 0 |
| 3 낙관적 업데이트는 비파괴적만 | N/A — 다운로드 endpoint, mutation 아님 |
| 6 DB 제약이 진실의 출처 | N/A — read-only |
| 8 audit_log append-only | ✓ — `AUDIT_EXPORTED`는 INSERT only, REQUIRES_NEW 분리 유지 |
| 10 파괴적 액션 BE 재검증 | N/A — read-only |
| 12 에러 코드 계약 | `format` 검증 실패는 기존 `BAD_REQUEST`(GlobalExceptionHandler IllegalArgumentException 매핑) 그대로 — 신규 enum 추가 없음 |

위반 없음.

---

## 8. 범위 / 비범위

### 범위 안

- `format=json` JSON 배열 응답
- `AuditExportEvent.format` 필드 추가 + listener 동적 metadata
- frontend "JSON 내보내기" anchor
- 기존 CSV 회귀 (테스트로 보장)
- 문서 갱신 5곳

### 범위 밖 (v1.x 또는 별도 트랙)

- NDJSON / `application/x-ndjson` (라인 단위 streaming)
- 진정한 SQL → JSON streaming (cap 제거 + 메모리 이탈)
- format-별 권한 분리 (예: AUDITOR는 JSON 금지)
- 외부 SIEM webhook push 모델
- audit log retention policy / 파티션
- format 파라미터의 enum 강타입화 (방어적 string check + 400 처리로 충분)

---

## 9. 참조

- 선행 트랙: `dev/completed/audit-export-endpoint/` (Wave 1 T2, 2026-05-06)
- BETA-RELEASE.md §7 v1.x deferred — "audit log JSON export endpoint" 항목
- docs/03 §4 — `audit.exported` 코멘트가 이미 JSON branch 예고
- ADR #24 — audit emit REQUIRES_NEW 분리
- 동형 테스트 패턴: `dev/completed/audit-export-endpoint/audit-export-endpoint-tasks.md`
