# Spec — `audit-format-enum` (audit export format 강타입화)

**Status**: Brainstorming approved (2026-05-08, 자율 진행).
**트랙명**: `audit-format-enum`
**Wave**: Wave 1 T2 follow-up — `audit-export-json` 트랙(2026-05-08, PR #85)의 다음 세션 컨텍스트에 명시된 v1.x 항목.

## 1. 배경

`audit-export-json` 트랙에서 `format` 파라미터를 `"csv" | "json"` String 비교로 처리. 3 군데에 동일 검증/분기:

- `AuditQueryController.export`: `if (!"csv".equals(format) && !"json".equals(format))` 검증
- `AuditExportEvent.format`: `String` 필드, 자유로운 값 허용
- `AuditExportListener.onExport`: `String` 비교로 fallback("csv")

문자열 비교는 typo 위험 + 신규 형식 추가 시 3 군데 동기화 누락 위험 + 컴파일러 도움 못 받음. **enum 강타입화**로 단일 진실 소스 + 컴파일러 검증.

## 2. 비목표

- **신규 형식 추가**(NDJSON 등): 별도 트랙. 본 트랙은 기존 csv/json 2종을 enum으로 정리만.
- **wire 변경 0**: `?format=csv` / `?format=json` query string은 그대로. enum은 internal representation만.
- **audit_log row 변경 0**: metadata JSON `"format":"csv"` 그대로.

## 3. 기능 요구사항

### 3.1 신규 enum

```java
package com.ibizdrive.audit;

/**
 * 감사 로그 export 응답 형식 (Wave 1 T2 follow-up — audit-format-enum, 2026-05-08).
 *
 * <p>wire는 lower-case ({@link #wire()}), audit_log metadata도 동일 lower-case.
 * 새 형식 추가 시(예: NDJSON) 본 enum + AuditCsvWriter/AuditJsonWriter 패턴 따라 추가.
 */
public enum AuditExportFormat {
    CSV, JSON;

    public String wire() {
        return name().toLowerCase();
    }

    /** wire 문자열 → enum. 그 외 값은 IAE → 글로벌 핸들러 400. */
    public static AuditExportFormat from(String wire) {
        if ("csv".equalsIgnoreCase(wire)) return CSV;
        if ("json".equalsIgnoreCase(wire)) return JSON;
        throw new IllegalArgumentException("audit export format must be csv or json");
    }
}
```

### 3.2 변경 대상 (3 파일)

| 파일 | 변경 |
|---|---|
| `AuditExportEvent` | `String format` → `AuditExportFormat format`. javadoc 갱신 |
| `AuditQueryController.export` | `String format` 파라미터는 wire 호환 유지하되 즉시 `AuditExportFormat.from(format)`으로 enum 변환. `isJson`/`extension`/`Content-Type` 분기는 enum switch로 정리. publish 시 enum 그대로 전달 |
| `AuditExportListener.onExport` | `event.format()` enum 직접 사용. `safeFormat` fallback 로직 삭제 — enum이 이미 검증된 값이라 defense-in-depth 불필요. metadata는 `event.format().wire()` |

### 3.3 wire 호환

- 요청: `?format=csv` / `?format=json` 그대로. `?format=`(빈) → defaultValue="csv" → CSV. 그 외 → 400 (기존 동일).
- 응답: Content-Type / 파일 확장자 / metadata `"format"` 모두 그대로.
- audit_log row의 metadata JSON byte-by-byte 동일.

### 3.4 호환성 / 마이그레이션

- DB schema 변경 0.
- audit_log 기존 row metadata 변경 0.
- API wire 변경 0.
- 호출자 0 영향 (event는 internal, controller는 wire 변환 자체).

## 4. 영향 범위

### 4.1 Backend

| 파일 | 변경 |
|---|---|
| `backend/src/main/java/com/ibizdrive/audit/AuditExportFormat.java` (NEW) | enum + wire/from |
| `AuditExportEvent.java` | `String format` → `AuditExportFormat format` |
| `AuditQueryController.java` | enum 변환 + switch 정리 |
| `AuditExportListener.java` | enum 직접 사용 + fallback 삭제 |
| `AuditExportListenerTest.java` | 기존 `String format` 호출을 `AuditExportFormat.CSV/JSON`으로 마이그레이션 |
| `AuditQueryControllerTest.java` (있다면) | wire 호환 그대로, 내부 stub만 업데이트 |
| `AuditExportE2ETest.java` (있다면) | 기존 그대로 (wire 호환) |

### 4.2 Frontend

- 영향 0 (wire 호환).

### 4.3 Docs

- `docs/02 §7.12` 또는 audit export 섹션의 코드 예시에 enum 명시 (작은 1줄 갱신, 또는 미수정).
- `docs/progress.md` 최상단 closure entry.

## 5. 트레이드오프 / 결정

### 5.1 fallback 로직 삭제 vs 유지

- **삭제 선정**: enum이 이미 검증된 값이라 fallback 의미 없음. controller에서 잘못된 값 → 400 → listener까지 안 옴.
- 유지: defense-in-depth. 하지만 enum 자체가 valid 값만 허용 — fallback 도달 가능성 0.

### 5.2 enum 명명 (`AuditExportFormat` vs `ExportFormat`)

- **`AuditExportFormat` 선정**: 도메인 명시. `com.ibizdrive.audit` 패키지에 위치하더라도 fully qualified로 의도 명확.
- 짧은 `ExportFormat`: 다른 export(나중에 file/folder export 등) 추가 시 충돌 위험.

## 6. 검증 / 게이트

- backend `./gradlew test --tests "com.ibizdrive.audit.*"` BUILD SUCCESSFUL
- 기존 wire 테스트(`AuditExportE2ETest` 등) 회귀 0 (wire 호환)
- frontend 영향 0 — typecheck/lint/build 변경 없음, 굳이 재실행 불필요 (sanity로 typecheck만)

## 7. Out-of-scope (별도 트랙)

- NDJSON / CSV variants 추가
- `AuditEventType.AUDIT_EXPORTED` 외 다른 emit
- frontend `getAuditLogsExportUrl` 시그니처 변경 (현재 string 그대로 OK)
