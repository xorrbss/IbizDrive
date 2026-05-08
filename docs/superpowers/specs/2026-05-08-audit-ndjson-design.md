# Spec — `audit-ndjson` (감사 로그 NDJSON 형식)

**Status**: Self-approved (자율 모드, 2026-05-08).
**트랙명**: `audit-ndjson`
**Wave**: Wave 1 T2 follow-up — `audit-export-json` 트랙(2026-05-08, PR #85) "다음 세션 컨텍스트"의 v1.x 항목.

## 1. 배경

`/api/admin/audit/export`는 현재 `format=csv|json` 2종. NDJSON(Newline-Delimited JSON)은:

- 한 줄에 한 row JSON. JSON array의 closing 없이 streaming 가능.
- `jq`/`grep`/`split` 같은 line-oriented 도구 친화적.
- 외부 SIEM 적재 시 row 단위 ingest에 표준.

직전 `audit-format-enum` 트랙으로 enum 패턴이 이미 깔려있어 NDJSON 추가는 enum 한 줄 + Writer + Content-Type 분기만 필요.

## 2. 비목표

- 진정한 SQL streaming(메모리 cap 제거): v1.x++. 본 트랙은 형식 추가만 — 기존 cap 10,000 그대로.
- frontend UI 추가(/admin/audit/logs에 "NDJSON 내보내기" 버튼): v1.x++. 본 트랙은 backend wire 노출만.
- audit_log metadata 변경: 기존 `"format":"csv"|"json"`에 `"ndjson"` 한 항목 추가만.

## 3. 기능 요구사항

### 3.1 enum 확장

```java
public enum AuditExportFormat {
    CSV, JSON, NDJSON;

    public String wire() { return name().toLowerCase(); }

    public static AuditExportFormat from(String wire) {
        if ("csv".equalsIgnoreCase(wire)) return CSV;
        if ("json".equalsIgnoreCase(wire)) return JSON;
        if ("ndjson".equalsIgnoreCase(wire)) return NDJSON;
        throw new IllegalArgumentException("audit export format must be csv, json or ndjson");
    }
}
```

### 3.2 신규 `AuditNdjsonWriter`

```java
@Component
public class AuditNdjsonWriter {
    private final ObjectMapper objectMapper;
    // 한 row마다 한 줄. row 사이는 LF (RFC 7464 미적용 — 단순 `\n` 구분).
    public void write(OutputStream out, List<AuditLogEntryDto> entries) throws IOException {
        for (AuditLogEntryDto e : entries) {
            objectMapper.writeValue(out, e);  // writeValue는 trailing newline 미발행
            out.write('\n');
        }
    }
}
```

### 3.3 `AuditQueryController.export` 분기

기존 `if (isJson) jsonWriter else csvWriter` → switch:

```java
switch (parsedFormat) {
    case CSV    -> csvWriter.write(out, result.entries());
    case JSON   -> jsonWriter.write(out, result.entries());
    case NDJSON -> ndjsonWriter.write(out, result.entries());
}
```

Content-Type / 확장자도 switch 또는 helper:

| Format | Content-Type | extension |
|---|---|---|
| CSV | `text/csv; charset=UTF-8` | `csv` |
| JSON | `application/json; charset=UTF-8` | `json` |
| NDJSON | `application/x-ndjson; charset=UTF-8` | `ndjson` |

### 3.4 frontend 호환

- `getAuditLogsExportUrl(filters, format)`의 `format` 타입은 현재 `'csv' \| 'json'` 가능성 → `'csv' \| 'json' \| 'ndjson'`로 확장. UI 버튼 추가는 본 트랙 범위 외 (직접 URL 호출하는 운영자 시나리오만 wire 노출).

## 4. 영향 범위

### 4.1 Backend

| 파일 | 변경 |
|---|---|
| `AuditExportFormat.java` | `NDJSON` 멤버 + `from` 분기 + 에러 메시지 |
| `AuditNdjsonWriter.java` (NEW) | row-per-line 직렬화 |
| `AuditQueryController.java` | `ndjsonWriter` 주입, switch에 NDJSON, Content-Type/extension switch |
| `AuditExportListenerTest` | NDJSON 케이스 1개 추가 |
| `AuditNdjsonWriterTest` (NEW) | 빈 list / 단일 row / 다중 row + LF 구분 검증 |
| `AuditQueryControllerTest` (있다면) | format=ndjson 200 + Content-Type / 응답 마지막 LF / format=invalid 400 |

### 4.2 Frontend

| 파일 | 변경 |
|---|---|
| `frontend/src/lib/api.ts` `getAuditLogsExportUrl` | format 타입 확장 (`'csv' \| 'json' \| 'ndjson'`). 기존 호출자 영향 0(default csv). |
| `api.getAuditLogsExportUrl.test.ts` | ndjson URL 송신 케이스 1개 추가 |

### 4.3 Docs

- `docs/02 §7.12` audit/export row에 ndjson 추가 + Content-Type 표.
- `docs/04 §7.2` Format 옵션에 NDJSON.
- `docs/progress.md` 최상단 closure entry.

## 5. 결정 / 트레이드오프

- **Content-Type `application/x-ndjson`** — 사실상 표준(RFC 7464는 `application/json-seq`로 다른 포맷). NDJSON은 Vendor extension(`x-`) 관행 따름.
- **Trailing newline 발행** — 마지막 row 뒤에도 `\n`. 빈 파일 vs 단일 row 구분 + 라인 oriented 도구 정합. RFC POSIX text file 관행.
- **frontend UI 미추가** — wire만 열어둠. 사용자 트리거는 직접 URL 또는 v1.x++ UI 추가.

## 6. 검증 / 게이트

- backend `./gradlew test --tests "com.ibizdrive.audit.*"` BUILD SUCCESSFUL
- frontend `pnpm typecheck` exit 0 + `pnpm test --run api.getAuditLogsExportUrl` GREEN

## 7. Out-of-scope

- 진정한 streaming (cap 제거)
- /admin/audit/logs UI에 "NDJSON 내보내기" 버튼
- application/json-seq (RFC 7464)
