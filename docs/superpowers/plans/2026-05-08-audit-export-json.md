# audit-export-json Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `GET /api/admin/audit/export`에 `format=csv|json` 쿼리 파라미터를 도입해 ADMIN/AUDITOR가 동일 필터로 JSON 배열 형식의 감사 로그를 다운로드할 수 있게 한다. CSV 호환성 무영향, AUDIT_EXPORTED audit emit metadata는 `format` 동적화.

**Architecture:** 신규 `AuditJsonWriter`(Spring `@Component`) + 기존 `AuditQueryController.exportCsv` → `export`로 rename + format 파라미터 분기. `AuditExportEvent`에 `format` 필드 추가 → `AuditExportListener`가 metadata에 동적 출력. `AuditQueryService.exportAll`은 무수정(format-independent). 프론트엔드는 `getAuditLogsExportUrl(filters, format)` 시그니처 확장 + "JSON 내보내기" anchor 추가.

**Tech Stack:** Spring Boot 3 / Jackson `ObjectMapper` / `@WebMvcTest` + JUnit 5 + Testcontainers(E2E) / Next.js 15 + Vitest + Testing Library. 작업 디렉토리: BE는 `cd backend && ./gradlew ...`, FE는 `cd frontend && npm run ...`.

**설계 근거:** `docs/superpowers/specs/2026-05-07-audit-export-json-design.md`

---

## 파일 구조

### 신규 파일 (backend)
| 경로 | 책임 |
|---|---|
| `backend/src/main/java/com/ibizdrive/audit/AuditJsonWriter.java` | `List<AuditLogEntryDto> → OutputStream` JSON 배열 직렬화 (`@Component`) |
| `backend/src/test/java/com/ibizdrive/audit/AuditJsonWriterTest.java` | 단위 테스트 (빈 배열, 일반 결과, null 필드, nested metadata) |
| `backend/src/test/java/com/ibizdrive/audit/AuditExportListenerTest.java` | listener metadata format 전파 단위 테스트 (mock `AuditService`) |

### 수정 파일 (backend)
| 경로 | 변경 |
|---|---|
| `backend/src/main/java/com/ibizdrive/audit/AuditExportEvent.java` | `String format` 필드 추가 (record 7번째 매개변수) |
| `backend/src/main/java/com/ibizdrive/audit/AuditExportListener.java` | metadata `"format":"csv"` 하드코딩 → `event.format()` 동적, defensive guard |
| `backend/src/main/java/com/ibizdrive/audit/AuditQueryController.java` | `exportCsv` → `export` rename, `format` 파라미터 + 분기, `AuditJsonWriter` 주입, `AuditExportEvent` publish 시 format 전달 |
| `backend/src/test/java/com/ibizdrive/audit/AuditQueryControllerTest.java` | `@WebMvcTest` slice — `csvWriter` mock 옆 `jsonWriter` mock 추가, format 분기 호출 검증 |
| `backend/src/test/java/com/ibizdrive/audit/AuditExportE2ETest.java` | format=json E2E 테스트 케이스 추가, format=csv 회귀 유지 |

### 신규 파일 (frontend)
| 경로 | 책임 |
|---|---|
| `frontend/src/lib/api.getAuditLogsExportUrl.test.ts` | format 파라미터 단위 테스트 (csv default, json branch, 필터 결합) |

### 수정 파일 (frontend)
| 경로 | 변경 |
|---|---|
| `frontend/src/lib/api.ts` | `getAuditLogsExportUrl(filters, format='csv')` 시그니처 확장 + `format` query param 합성 |
| `frontend/src/app/admin/audit/logs/page.tsx` | "JSON 내보내기" anchor 1개 추가, 두 anchor가 별도 export URL 사용 |
| `frontend/src/app/admin/audit/logs/page.test.tsx` | 두 anchor 존재 + 각각 href 패턴 + 필터 결합 검증, 기존 testid 회귀 |

### 수정 파일 (docs)
| 경로 | 변경 |
|---|---|
| `docs/02-backend-data-model.md` §7.12 | `format` 행 추가, 응답 모양 절에 JSON 배열 예시 |
| `docs/04-admin-operations.md` §7.2 | line 263~264 갱신 — `format=csv\|json` 분기, JSON nested metadata |
| `BETA-RELEASE.md` §7 | "audit log JSON export endpoint" 라인 제거 + audit-export-json 트랙 closure 명시 |
| `docs/progress.md` | 트랙 closure 엔트리 (최상단 추가) |
| `dev/active/audit-export-json/README.md` | 트랙 메타 (spec/plan 링크, 시작일) |

---

## Pre-flight: dev/active 부트스트랩

**Files:**
- Create: `dev/active/audit-export-json/README.md`

- [ ] **Step 1: dev/active 디렉토리 + README 생성**

`dev/active/audit-export-json/README.md`:

```markdown
# audit-export-json (Wave 1 T2 후속)

- spec: docs/superpowers/specs/2026-05-07-audit-export-json-design.md
- plan: docs/superpowers/plans/2026-05-08-audit-export-json.md
- 시작: 2026-05-08
- 머지 후 dev/completed/ 로 이동
```

- [ ] **Step 2: 커밋**

```bash
cd C:/project/IbizDrive/.claude/worktrees/audit-export-json
git add dev/active/audit-export-json/README.md
git commit -m "chore(audit-export-json): bootstrap dev directory"
```

---

## P1 (BE-1): AuditExportEvent format 필드 + AuditExportListener 동적화

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/audit/AuditExportEvent.java`
- Modify: `backend/src/main/java/com/ibizdrive/audit/AuditExportListener.java`
- Create: `backend/src/test/java/com/ibizdrive/audit/AuditExportListenerTest.java`

### Task P1.1: Listener 단위 테스트 (RED)

- [ ] **Step 1: 테스트 신규 작성 (RED 우선)**

`backend/src/test/java/com/ibizdrive/audit/AuditExportListenerTest.java`:

```java
package com.ibizdrive.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * AuditExportListener — AUDIT_EXPORTED metadata의 `format` 필드가
 * AuditExportEvent.format() 값을 그대로 반영하는지 검증 (audit-export-json 트랙).
 */
@ExtendWith(MockitoExtension.class)
class AuditExportListenerTest {

    @Mock
    private AuditService auditService;

    @Test
    void csvFormatEventEmitsMetadataFormatCsv() throws Exception {
        AuditExportListener listener = new AuditExportListener(auditService);
        AuditExportEvent event = new AuditExportEvent(
            UUID.randomUUID(),
            InetAddress.getByName("10.0.0.1"),
            "TestAgent/1.0",
            "{\"eventType\":\"user.login.failed\"}",
            42,
            false,
            "csv"
        );

        listener.onExport(event);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().metadata()).contains("\"format\":\"csv\"");
    }

    @Test
    void jsonFormatEventEmitsMetadataFormatJson() throws Exception {
        AuditExportListener listener = new AuditExportListener(auditService);
        AuditExportEvent event = new AuditExportEvent(
            UUID.randomUUID(),
            InetAddress.getByName("10.0.0.1"),
            "TestAgent/1.0",
            "{}",
            7,
            true,
            "json"
        );

        listener.onExport(event);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().metadata())
            .contains("\"format\":\"json\"")
            .contains("\"rowCount\":7")
            .contains("\"truncated\":true");
    }

    @Test
    void unknownFormatFallsBackToCsv() throws Exception {
        AuditExportListener listener = new AuditExportListener(auditService);
        AuditExportEvent event = new AuditExportEvent(
            UUID.randomUUID(),
            InetAddress.getByName("10.0.0.1"),
            "TestAgent/1.0",
            "{}",
            0,
            false,
            "xml"   // controller 가드를 우회한 가상의 잘못된 값
        );

        listener.onExport(event);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        // listener의 defensive fallback: 지원하지 않는 format은 "csv"로 안전 기록
        assertThat(captor.getValue().metadata()).contains("\"format\":\"csv\"");
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `cd backend && ./gradlew test --tests AuditExportListenerTest`
Expected: COMPILATION FAILURE (`AuditExportEvent` 7-arg 생성자 없음, `event.format()` 메서드 없음)

### Task P1.2: AuditExportEvent에 format 필드 추가

- [ ] **Step 1: record에 format 매개변수 추가**

`backend/src/main/java/com/ibizdrive/audit/AuditExportEvent.java` 전체 교체:

```java
package com.ibizdrive.audit;

import java.net.InetAddress;
import java.util.UUID;

/**
 * 감사 로그 export 1회를 표현하는 도메인 이벤트.
 *
 * <p>{@link AuditQueryController#export}가 응답 stream을 모두 작성한 뒤 publish하면
 * {@link AuditExportListener}가 {@code AUDIT_EXPORTED} ({@code audit.exported}) audit_log row를
 * REQUIRES_NEW 트랜잭션으로 기록한다 (ADR #24).
 *
 * <p><b>actor IP/User-Agent를 이벤트가 직접 들고 가는 이유</b>: {@link WebRequestContextHolder}는
 * ThreadLocal 기반인데, 응답 본문이 {@code StreamingResponseBody}로 비동기 처리될 가능성이 있어
 * publish 시점에 다른 스레드일 수 있다. controller에서 요청 스레드 값을 캡처해 이벤트에 동봉하면
 * 실행 스레드와 무관하게 IP/UA가 보존된다.
 *
 * @param actorId      호출자 user id (인증된 ADMIN/AUDITOR)
 * @param actorIp      호출자 IP — controller 캡처
 * @param userAgent    호출자 UA — controller 캡처
 * @param filtersJson  적용된 필터의 JSON 문자열 (audit metadata에 그대로 들어감)
 * @param rowCount     실제로 export된 행 수 (cap 적용 후)
 * @param truncated    cap 초과로 잘렸는지 여부
 * @param format       응답 직렬화 형식 ({@code "csv"} 또는 {@code "json"}). controller에서 검증된 값
 */
public record AuditExportEvent(
    UUID actorId,
    InetAddress actorIp,
    String userAgent,
    String filtersJson,
    int rowCount,
    boolean truncated,
    String format
) {
}
```

- [ ] **Step 2: 컴파일만 빠르게 확인 (전체 컴파일 통과)**

Run: `cd backend && ./gradlew compileJava`
Expected: `AuditQueryController` 호출부 컴파일 실패(`new AuditExportEvent(...)` 6-arg → 7-arg 필요). 다음 task에서 controller 수정 시 같이 해결.

### Task P1.3: AuditExportListener 동적 metadata 적용

- [ ] **Step 1: 코드 수정 — format 동적화 + defensive guard**

`backend/src/main/java/com/ibizdrive/audit/AuditExportListener.java`의 `onExport(...)` 메서드 본문 교체:

```java
    @EventListener
    public void onExport(AuditExportEvent event) {
        // metadata 키 순서를 결정적으로 유지해 audit row diff·테스트 용이하게 한다.
        // format은 controller에서 검증되지만, listener는 defense-in-depth로
        // 지원되지 않는 값은 "csv"로 fallback (audit_log 무결성 우선).
        String safeFormat = ("csv".equals(event.format()) || "json".equals(event.format()))
            ? event.format()
            : "csv";
        if (!safeFormat.equals(event.format())) {
            log.warn("AuditExportEvent.format='{}' is unsupported; falling back to 'csv' for audit metadata",
                event.format());
        }
        String metadata = "{\"filters\":" + (event.filtersJson() == null ? "null" : event.filtersJson())
            + ",\"rowCount\":" + event.rowCount()
            + ",\"truncated\":" + event.truncated()
            + ",\"format\":\"" + safeFormat + "\"}";
        try {
            auditService.record(new AuditEvent(
                AuditEventType.AUDIT_EXPORTED,
                event.actorId(),
                event.actorIp(),
                event.userAgent(),
                AuditTargetType.AUDIT,
                null,
                null,
                null,
                metadata
            ));
        } catch (RuntimeException ex) {
            log.error("audit emission failed for event={}", AuditEventType.AUDIT_EXPORTED, ex);
        }
    }
```

> 위 메서드 외 import/필드/생성자는 변경 없음.

- [ ] **Step 2: Listener 테스트만 실행 — 모든 테스트 GREEN**

Run: `cd backend && ./gradlew test --tests AuditExportListenerTest`
Expected: 3 tests PASS (csv, json, fallback)

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/ibizdrive/audit/AuditExportEvent.java \
        backend/src/main/java/com/ibizdrive/audit/AuditExportListener.java \
        backend/src/test/java/com/ibizdrive/audit/AuditExportListenerTest.java
git commit -m "feat(audit-export-json): add format field to AuditExportEvent + dynamic listener metadata"
```

---

## P2 (BE-2): AuditJsonWriter 신규 + 단위 테스트

**Files:**
- Create: `backend/src/main/java/com/ibizdrive/audit/AuditJsonWriter.java`
- Create: `backend/src/test/java/com/ibizdrive/audit/AuditJsonWriterTest.java`

### Task P2.1: AuditJsonWriterTest (RED)

- [ ] **Step 1: 단위 테스트 작성**

`backend/src/test/java/com/ibizdrive/audit/AuditJsonWriterTest.java`:

```java
package com.ibizdrive.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.ibizdrive.audit.dto.AuditLogEntryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AuditJsonWriter — JSON 배열 직렬화. CSV writer mirror, 단 nested metadata는 객체 그대로
 * (이중 직렬화 회피). UTF-8 BOM 미부착.
 */
class AuditJsonWriterTest {

    private AuditJsonWriter writer;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        // OffsetDateTime을 ISO 문자열로 직렬화하기 위해 JavaTimeModule이 필요할 수 있다.
        mapper.findAndRegisterModules();
        writer = new AuditJsonWriter(mapper);
    }

    @Test
    void emptyEntriesProducesEmptyArray() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.write(out, List.of());
        assertThat(out.toString("UTF-8")).isEqualTo("[]");
    }

    @Test
    void singleEntryHasAllTenFields() throws Exception {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("reason", "BAD_PASSWORD");
        AuditLogEntryDto entry = new AuditLogEntryDto(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            OffsetDateTime.parse("2026-05-08T12:00:00+09:00"),
            "user.login.failed",
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            "kim@example.com",
            "user",
            UUID.fromString("00000000-0000-0000-0000-000000000003"),
            "kim@example.com",
            "10.0.0.1",
            meta
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.write(out, List.of(entry));

        JsonNode arr = mapper.readTree(out.toByteArray());
        assertThat(arr.isArray()).isTrue();
        assertThat(arr).hasSize(1);
        JsonNode row = arr.get(0);
        assertThat(row.get("id").asText()).isEqualTo("00000000-0000-0000-0000-000000000001");
        assertThat(row.get("eventType").asText()).isEqualTo("user.login.failed");
        assertThat(row.get("ip").asText()).isEqualTo("10.0.0.1");
        assertThat(row.get("metadata").isObject()).isTrue();
        assertThat(row.get("metadata").get("reason").asText()).isEqualTo("BAD_PASSWORD");
    }

    @Test
    void nullFieldsSerializeAsJsonNull() throws Exception {
        AuditLogEntryDto entry = new AuditLogEntryDto(
            UUID.fromString("00000000-0000-0000-0000-000000000010"),
            OffsetDateTime.parse("2026-05-08T12:00:00+09:00"),
            "system.cron.tick",
            null, null, null, null, null, null, null
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.write(out, List.of(entry));

        JsonNode row = mapper.readTree(out.toByteArray()).get(0);
        assertThat(row.get("actorId").isNull()).isTrue();
        assertThat(row.get("actorName").isNull()).isTrue();
        assertThat(row.get("metadata").isNull()).isTrue();
    }

    @Test
    void noBomEvenForKoreanContent() throws Exception {
        AuditLogEntryDto entry = new AuditLogEntryDto(
            UUID.fromString("00000000-0000-0000-0000-000000000020"),
            OffsetDateTime.parse("2026-05-08T12:00:00+09:00"),
            "user.created",
            null,
            "한글이름",
            null, null, null, null, null
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.write(out, List.of(entry));

        // JSON 표준은 BOM 미부착. 첫 바이트는 '['(0x5B).
        byte[] bytes = out.toByteArray();
        assertThat(bytes[0]).isEqualTo((byte) '[');
    }
}
```

- [ ] **Step 2: 테스트 실행 — RED 확인**

Run: `cd backend && ./gradlew test --tests AuditJsonWriterTest`
Expected: COMPILATION FAILURE (`AuditJsonWriter` 클래스 없음)

### Task P2.2: AuditJsonWriter 구현 (GREEN)

- [ ] **Step 1: 클래스 생성**

`backend/src/main/java/com/ibizdrive/audit/AuditJsonWriter.java`:

```java
package com.ibizdrive.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.dto.AuditLogEntryDto;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * 감사 로그 JSON 직렬화 — {@code GET /api/admin/audit/export?format=json} 응답 본문 작성기.
 *
 * <p>{@link AuditCsvWriter}와 동위 역할이지만 출력 모양은 다르다:
 * <ul>
 *   <li>CSV: BOM + 헤더 + RFC 4180 quoted rows, metadata는 JSON 문자열로 인라인된 단일 cell</li>
 *   <li>JSON: BOM 없음, plain array {@code [{...}, {...}]}, metadata는 nested object 그대로</li>
 * </ul>
 *
 * <p><b>설계 결정</b>: SequenceWriter / streaming token API는 도입하지 않는다. 현재 구현은
 * cap(10,000) 적용 후 메모리에 모두 적재하므로 standard {@code writeValue} 1회 호출이 가장
 * 단순. 진정한 SQL streaming + JSON streaming은 v1.x (KISS, CLAUDE.md §3 원칙 1).
 */
@Component
public class AuditJsonWriter {

    private final ObjectMapper objectMapper;

    public AuditJsonWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * JSON 배열을 {@code out}에 기록한다. flush/close는 호출자 책임
     * ({@code StreamingResponseBody}가 OutputStream 수명을 관리).
     */
    public void write(OutputStream out, List<AuditLogEntryDto> entries) throws IOException {
        objectMapper.writeValue(out, entries);
    }
}
```

- [ ] **Step 2: 단위 테스트 GREEN**

Run: `cd backend && ./gradlew test --tests AuditJsonWriterTest`
Expected: 4 tests PASS

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/ibizdrive/audit/AuditJsonWriter.java \
        backend/src/test/java/com/ibizdrive/audit/AuditJsonWriterTest.java
git commit -m "feat(audit-export-json): add AuditJsonWriter (List<AuditLogEntryDto> → JSON array)"
```

---

## P3 (BE-3): Controller export 분기 + slice 테스트 + E2E json 케이스

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/audit/AuditQueryController.java`
- Modify: `backend/src/test/java/com/ibizdrive/audit/AuditQueryControllerTest.java`
- Modify: `backend/src/test/java/com/ibizdrive/audit/AuditExportE2ETest.java`

### Task P3.1: Controller 코드 수정

- [ ] **Step 1: AuditQueryController 수정**

기존 `exportCsv` 메서드(라인 111~164)를 다음으로 교체. 클래스 멤버 추가 및 생성자 인수 1개 추가.

`backend/src/main/java/com/ibizdrive/audit/AuditQueryController.java`:

1. **클래스 필드** — `csvWriter` 옆에 추가:

```java
    private final AuditCsvWriter csvWriter;
    private final AuditJsonWriter jsonWriter;          // 신규
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
```

2. **생성자** — `jsonWriter` 매개변수 추가:

```java
    public AuditQueryController(AuditQueryService queryService,
                                AuditCsvWriter csvWriter,
                                AuditJsonWriter jsonWriter,
                                ApplicationEventPublisher eventPublisher,
                                ObjectMapper objectMapper) {
        this.queryService = queryService;
        this.csvWriter = csvWriter;
        this.jsonWriter = jsonWriter;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }
```

3. **`exportCsv` 메서드 → `export`로 rename + format 분기**:

```java
    /**
     * 감사 로그 다운로드 — CSV(default) 또는 JSON 배열.
     *
     * <p>{@code format} 쿼리 파라미터가 미지정이거나 {@code "csv"}이면 BOM + RFC 4180 CSV,
     * {@code "json"}이면 plain JSON 배열을 반환한다. 그 외 값은
     * {@link IllegalArgumentException}을 던져 {@code GlobalExceptionHandler}가 400
     * {@code BAD_REQUEST}로 변환한다.
     *
     * <p>본 v1 구현은 service에서 결과를 한 번에 페치(cap 10,000행 적용)하여 메모리에 보관 후
     * stream에 기록한다 — 진정한 SQL streaming은 v1.x deferred (KISS).
     *
     * <p>응답 본문 작성 후 {@link AuditExportEvent}를 publish한다 — listener가
     * REQUIRES_NEW 트랜잭션으로 {@code AUDIT_EXPORTED} audit_log row를 기록한다.
     */
    @PreAuthorize("hasRole('AUDITOR') or hasRole('ADMIN')")
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(
        @RequestParam(name = "fromDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @RequestParam(name = "toDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
        @RequestParam(name = "actorQuery", required = false) String actorQuery,
        @RequestParam(name = "eventType", required = false) String eventType,
        @RequestParam(name = "targetType", required = false) String targetType,
        @RequestParam(name = "targetId", required = false) UUID targetId,
        @RequestParam(name = "format", required = false, defaultValue = "csv") String format,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        if (!"csv".equals(format) && !"json".equals(format)) {
            throw new IllegalArgumentException("audit export format must be csv or json");
        }
        AuditQueryFilters filters = new AuditQueryFilters(
            fromDate,
            toDate,
            blankToNull(actorQuery),
            blankToNull(eventType),
            blankToNull(targetType),
            targetId
        );
        UUID actorId = principal.getUser().getId();
        AuditQueryService.AuditExportResult result =
            queryService.exportAll(filters, actorId, principal.getUser().getRole());

        java.net.InetAddress actorIp = WebRequestContextHolder.currentIp();
        String userAgent = WebRequestContextHolder.currentUserAgent();
        String filtersJson = serializeFilters(filters);
        boolean isJson = "json".equals(format);

        StreamingResponseBody body = out -> {
            try {
                if (isJson) {
                    jsonWriter.write(out, result.entries());
                } else {
                    csvWriter.write(out, result.entries());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            eventPublisher.publishEvent(new AuditExportEvent(
                actorId, actorIp, userAgent, filtersJson,
                result.entries().size(), result.truncated(), format
            ));
        };

        HttpHeaders headers = new HttpHeaders();
        if (isJson) {
            headers.setContentType(new MediaType("application", "json", java.nio.charset.StandardCharsets.UTF_8));
        } else {
            headers.setContentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8));
        }
        String extension = isJson ? "json" : "csv";
        headers.setContentDisposition(org.springframework.http.ContentDisposition.attachment()
            .filename("audit_logs_" + LocalDate.now().format(FILENAME_DATE) + "." + extension)
            .build());
        if (result.truncated()) {
            headers.add(HEADER_TRUNCATED, "true");
        }
        return new ResponseEntity<>(body, headers, org.springframework.http.HttpStatus.OK);
    }
```

> 주의: 메서드 이름이 `exportCsv`에서 `export`로 바뀌었다. 이 클래스 외부에서 직접 호출하는 곳은 없음(URL `/export`로 연결되며, MockMvc·E2E 테스트만 영향). 슬라이스 테스트는 P3.2에서 수정.

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava compileTestJava`
Expected: 컴파일은 통과(테스트는 아직 RED 가능 — 다음 task에서 갱신).

### Task P3.2: AuditQueryControllerTest slice — jsonWriter mock 추가 + format 분기 검증

- [ ] **Step 1: 기존 mock 필드 옆에 jsonWriter 추가**

`backend/src/test/java/com/ibizdrive/audit/AuditQueryControllerTest.java` 라인 60~70 부근(`@MockBean`/`@Mock` 선언부)에 다음 필드 추가:

```java
    @MockBean
    private AuditCsvWriter csvWriter;                  // 기존 — 본 슬라이스의 /export 미사용 mock 유지

    @MockBean
    private AuditJsonWriter jsonWriter;                // 신규 — 동일 사유
```

> 본 슬라이스 테스트는 `/export`를 호출하지 않으므로 두 writer는 mock으로만 존재. format 분기 행동은 §P3.3 E2E 테스트에서 검증.

- [ ] **Step 2: 슬라이스 테스트 실행 — 회귀 GREEN**

Run: `cd backend && ./gradlew test --tests AuditQueryControllerTest`
Expected: 기존 9개 케이스 모두 PASS (jsonWriter mock은 호출되지 않음).

### Task P3.3: AuditExportE2ETest — format=json 케이스 추가 + format=csv 회귀 유지

- [ ] **Step 1: 기존 파일 검토**

`backend/src/test/java/com/ibizdrive/audit/AuditExportE2ETest.java`를 읽어 다음을 확인:
- 기존 테스트들이 어떤 helper(예: 인증 세션 setup, audit row 시드)를 사용하는지
- metadata.format=csv 검증 위치 (`assertThat(row.get("format")).isEqualTo("csv")`)

- [ ] **Step 2: 두 개의 신규 `@Test` 추가**

기존 export 성공 케이스(metadata format=csv 검증 포함, 라인 ~210~250) 바로 뒤에 다음 두 케이스를 추가한다. 시드 데이터·인증 헬퍼는 기존 케이스와 동일 패턴을 따른다.

(a) `format=json` 응답 본문이 JSON 배열 + Content-Type + 파일명 + audit metadata.format=json:

```java
    @Test
    void exportJson_returnsJsonArrayAndEmitsFormatJsonInAuditMetadata() throws Exception {
        loginAsAdmin();
        seedAuditRow("user.login.failed", "user");

        MvcResult result = mockMvc.perform(get("/api/admin/audit/export")
                .queryParam("format", "json")
                .session(session))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type",
                org.hamcrest.Matchers.startsWith("application/json")))
            .andExpect(header().string("Content-Disposition",
                org.hamcrest.Matchers.containsString(".json")))
            .andReturn();

        // 응답 본문 — JSON 배열 검증
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).startsWith("[").endsWith("]");
        com.fasterxml.jackson.databind.JsonNode arr =
            new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
        assertThat(arr.isArray()).isTrue();
        assertThat(arr.size()).isGreaterThanOrEqualTo(1);

        // audit_log metadata.format = json 검증
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT metadata->>'format' AS format, " +
            "       (metadata->>'rowCount')::int AS row_count, " +
            "       (metadata->>'truncated')::boolean AS truncated " +
            "FROM audit_log WHERE event_type='audit.exported' ORDER BY occurred_at DESC LIMIT 1");
        assertThat(row.get("format")).isEqualTo("json");
        assertThat((Integer) row.get("row_count")).isGreaterThanOrEqualTo(1);
    }
```

(b) `format=invalid` → 400 BAD_REQUEST:

```java
    @Test
    void exportInvalidFormat_returns400BadRequest() throws Exception {
        loginAsAdmin();

        mockMvc.perform(get("/api/admin/audit/export")
                .queryParam("format", "xml")
                .session(session))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
```

> 주의: 위 두 테스트의 helper(`loginAsAdmin`, `seedAuditRow`, `session`, `jdbcTemplate`, `mockMvc`)는 기존 케이스와 동일 이름이어야 한다. 차이가 있으면 기존 케이스의 setup을 그대로 따른다.

- [ ] **Step 3: E2E 실행 (Docker 필요)**

Run: `cd backend && ./gradlew test --tests AuditExportE2ETest`
Expected:
- 기존 케이스 모두 PASS (format=csv 회귀)
- 신규 2 케이스 PASS

> Docker가 환경에 없으면 `disabledWithoutDocker = true`로 자동 skip — CI에서는 모두 실행됨.

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/ibizdrive/audit/AuditQueryController.java \
        backend/src/test/java/com/ibizdrive/audit/AuditQueryControllerTest.java \
        backend/src/test/java/com/ibizdrive/audit/AuditExportE2ETest.java
git commit -m "feat(audit-export-json): add format=json branch to /api/admin/audit/export controller"
```

---

## P4 (FE-1): api.getAuditLogsExportUrl format 파라미터 + 단위 테스트

**Files:**
- Modify: `frontend/src/lib/api.ts` (라인 884~904)
- Create: `frontend/src/lib/api.getAuditLogsExportUrl.test.ts`

### Task P4.1: 단위 테스트 (RED)

- [ ] **Step 1: 테스트 신규 작성**

`frontend/src/lib/api.getAuditLogsExportUrl.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { api } from '@/lib/api'

describe('api.getAuditLogsExportUrl', () => {
  it('format 미지정이면 default csv (호환성)', () => {
    const url = api.getAuditLogsExportUrl({})
    expect(url).toBe('/api/admin/audit/export?format=csv')
  })

  it('format=csv 명시도 동일하게 csv', () => {
    const url = api.getAuditLogsExportUrl({}, 'csv')
    expect(url).toBe('/api/admin/audit/export?format=csv')
  })

  it('format=json은 query에 format=json 포함', () => {
    const url = api.getAuditLogsExportUrl({}, 'json')
    expect(url).toBe('/api/admin/audit/export?format=json')
  })

  it('필터와 format 결합', () => {
    const url = api.getAuditLogsExportUrl(
      { fromDate: '2026-05-01', eventType: 'user.login.failed' },
      'json'
    )
    // URLSearchParams 직렬화 순서는 set 호출 순서대로 — 본 함수 구현에서 fromDate → eventType → format 순.
    expect(url).toContain('fromDate=2026-05-01')
    expect(url).toContain('eventType=user.login.failed')
    expect(url).toContain('format=json')
  })

  it('actorQuery는 trim된 후 추가', () => {
    const url = api.getAuditLogsExportUrl({ actorQuery: '  kim  ' }, 'json')
    expect(url).toContain('actorQuery=kim')
    expect(url).toContain('format=json')
  })
})
```

- [ ] **Step 2: 테스트 실행 — RED 확인**

Run: `cd frontend && npm test -- --run api.getAuditLogsExportUrl`
Expected: FAIL — 함수 시그니처가 1-arg이고 format 미반영, default csv 누락.

### Task P4.2: api.ts 수정

- [ ] **Step 1: getAuditLogsExportUrl 시그니처 + 본문 교체**

`frontend/src/lib/api.ts`의 라인 884~904 메서드 교체:

```ts
  /**
   * 감사 로그 server-side export URL 빌더 (Wave 1 — T2, audit-export-json 트랙으로 format 추가).
   *
   * <p>실제 다운로드는 anchor element의 `href`/`download`로 트리거 — fetch가 아닌 브라우저
   * navigation을 사용해야 Content-Disposition을 그대로 인식한다. 호출 측은 본 URL을
   * `<a href>`에 그대로 넣고 click한다.
   *
   * <p>backend `GET /api/admin/audit/export` 가드: `@PreAuthorize(AUDITOR or ADMIN)` — MEMBER는 403.
   * `format`은 `'csv'` 또는 `'json'`. backend에서 그 외 값은 400 BAD_REQUEST.
   * 본 함수는 URL만 빌드하므로 권한 가드는 backend가 단독 책임 (UX는 호출 측 페이지가 분기).
   */
  getAuditLogsExportUrl(
    filters: AuditLogFilters = {},
    format: 'csv' | 'json' = 'csv'
  ): string {
    const params = new URLSearchParams()
    if (filters.fromDate) params.set('fromDate', filters.fromDate)
    if (filters.toDate) params.set('toDate', filters.toDate)
    if (filters.actorQuery && filters.actorQuery.trim()) {
      params.set('actorQuery', filters.actorQuery.trim())
    }
    if (filters.eventType) params.set('eventType', filters.eventType)
    params.set('format', format)
    return `/api/admin/audit/export?${params.toString()}`
  },
```

- [ ] **Step 2: 테스트 GREEN**

Run: `cd frontend && npm test -- --run api.getAuditLogsExportUrl`
Expected: 5 tests PASS

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/lib/api.ts frontend/src/lib/api.getAuditLogsExportUrl.test.ts
git commit -m "feat(audit-export-json): add format param to api.getAuditLogsExportUrl (default csv)"
```

---

## P5 (FE-2): page.tsx — JSON 내보내기 anchor 추가 + 페이지 테스트

**Files:**
- Modify: `frontend/src/app/admin/audit/logs/page.tsx`
- Modify: `frontend/src/app/admin/audit/logs/page.test.tsx`

### Task P5.1: 페이지 테스트 갱신 (RED)

- [ ] **Step 1: 기존 테스트 검토 + 신규/수정 케이스 추가**

`frontend/src/app/admin/audit/logs/page.test.tsx`의 기존 케이스를 다음 패턴으로 갱신/추가한다.

기존:
- "CSV 내보내기" 버튼은 anchor element이며 href가 `/api/admin/audit/export`로 시작 → 갱신: `format=csv`도 포함.

신규 케이스 — testid 기반:

```ts
  it('CSV anchor가 format=csv를 포함', () => {
    render(/* page */)
    const link = screen.getByTestId('audit-export-link-csv')
    expect(link.tagName).toBe('A')
    expect(link.getAttribute('href')).toContain('/api/admin/audit/export?')
    expect(link.getAttribute('href')).toContain('format=csv')
  })

  it('JSON anchor가 format=json을 포함', () => {
    render(/* page */)
    const link = screen.getByTestId('audit-export-link-json')
    expect(link.tagName).toBe('A')
    expect(link.getAttribute('href')).toContain('/api/admin/audit/export?')
    expect(link.getAttribute('href')).toContain('format=json')
  })

  it('필터 적용 후 두 anchor 모두 query에 필터 + 각자 format 반영', async () => {
    // 기존 helper로 eventType='user.login.failed' 입력
    // ...
    const csv = screen.getByTestId('audit-export-link-csv').getAttribute('href')!
    const json = screen.getByTestId('audit-export-link-json').getAttribute('href')!
    expect(csv).toContain('eventType=user.login.failed')
    expect(csv).toContain('format=csv')
    expect(json).toContain('eventType=user.login.failed')
    expect(json).toContain('format=json')
  })
```

> 기존 `data-testid="audit-export-link"` 검증은 `audit-export-link-csv`로 rename된다. 존재하던 단일 anchor 케이스의 selector를 함께 갱신.

- [ ] **Step 2: 테스트 실행 — RED 확인**

Run: `cd frontend && npm test -- --run page.test.tsx`
Expected: FAIL — `audit-export-link-json` testid 없음, `audit-export-link-csv` rename 미반영.

### Task P5.2: page.tsx 수정 — JSON anchor 추가 + testid 분리

- [ ] **Step 1: 기존 anchor → 두 anchor로 교체**

`frontend/src/app/admin/audit/logs/page.tsx`의 export anchor 영역을 다음으로 교체:

```tsx
        <div className="flex items-center gap-2">
          <a
            href={api.getAuditLogsExportUrl(filters, 'csv')}
            download=""
            className="h-8 px-3 inline-flex items-center rounded bg-accent text-accent-fg text-[12.5px] font-medium hover:opacity-90"
            data-testid="audit-export-link-csv"
          >
            CSV 내보내기
          </a>
          <a
            href={api.getAuditLogsExportUrl(filters, 'json')}
            download=""
            className="h-8 px-3 inline-flex items-center rounded border border-border text-fg text-[12.5px] font-medium hover:bg-bg-subtle"
            data-testid="audit-export-link-json"
          >
            JSON 내보내기
          </a>
        </div>
```

> 이전 `const exportUrl = api.getAuditLogsExportUrl(filters)` 변수 선언 라인은 제거(인라인 호출로 대체).

- [ ] **Step 2: 테스트 GREEN**

Run: `cd frontend && npm test -- --run page.test.tsx`
Expected: 신규 3 케이스 + 갱신된 기존 케이스 모두 PASS

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/app/admin/audit/logs/page.tsx \
        frontend/src/app/admin/audit/logs/page.test.tsx
git commit -m "feat(audit-export-json): add JSON 내보내기 anchor to /admin/audit/logs (split testids)"
```

---

## P6 (Docs): 설계 문서 + BETA + progress

**Files:**
- Modify: `docs/02-backend-data-model.md` (§7.12)
- Modify: `docs/04-admin-operations.md` (§7.2 — line 263~264)
- Modify: `BETA-RELEASE.md` (§7)
- Modify: `docs/progress.md` (최상단 추가)

### Task P6.1: docs/02 §7.12 — format 행 추가

- [ ] **Step 1: §7.12 endpoint 표 + 응답 예시에 format 반영**

`docs/02-backend-data-model.md`의 `/api/admin/audit/export` row(라인 1578) 인접부에 다음을 추가/갱신:
- 쿼리 파라미터 표에 `format` 행: `csv | json (default csv)` — 미지원 값은 400 `BAD_REQUEST`.
- 응답 모양 절: csv는 BOM + RFC 4180 (기존), json은 `[{...}, {...}]` 배열, `metadata`는 nested object.
- audit_log metadata 절: `format` 값은 `"csv"` 또는 `"json"`.

(섹션의 정확한 라인 위치는 작업자가 `grep -n "audit/export" docs/02-backend-data-model.md`로 직접 확인.)

### Task P6.2: docs/04 §7.2 — line 263~264 갱신

- [ ] **Step 1: 라인 263~264 교체**

기존:

```
- [x] **server-side full-result 스트리밍** — Wave 1 T2: `GET /api/admin/audit/export` (`StreamingResponseBody`), 하드 캡 10,000행 + `LIMIT cap+1` truncation 감지, 초과 시 `X-Audit-Export-Truncated: true` 헤더
- [x] **`audit.exported` runtime emission** — Wave 1 T2: `AuditExportListener` (`@EventListener` + `@Transactional(REQUIRES_NEW)`), metadata에 `filters` / `rowCount` / `truncated` / `format=csv` (append-only 보존을 위해 export read 트랜잭션과 분리)
```

신규:

```
- [x] **server-side full-result 스트리밍** — Wave 1 T2: `GET /api/admin/audit/export` (`StreamingResponseBody`), 하드 캡 10,000행 + `LIMIT cap+1` truncation 감지, 초과 시 `X-Audit-Export-Truncated: true` 헤더. `format=csv|json` 분기(audit-export-json 트랙, 2026-05-08) — default csv, json은 plain array(metadata는 nested object).
- [x] **`audit.exported` runtime emission** — Wave 1 T2: `AuditExportListener` (`@EventListener` + `@Transactional(REQUIRES_NEW)`), metadata에 `filters` / `rowCount` / `truncated` / `format`(`"csv"` 또는 `"json"`, audit-export-json 트랙) — append-only 보존을 위해 export read 트랜잭션과 분리.
```

### Task P6.3: BETA-RELEASE.md §7 — JSON export 라인 제거 + closure 명시

- [ ] **Step 1: §7 v1.x deferred 마지막 라인 제거**

기존(115행 부근):

```
- audit log JSON export endpoint — docs/04 §7.2 v1.x (CSV server-side export는 Wave 1 T2에서 ship — `GET /api/admin/audit/export`, `AUDIT_EXPORTED` emit 활성)
```

신규: 이 라인을 통째로 제거.

- [ ] **Step 2: 4행 Source 체인에 트랙 closure 추가**

기존 line 4 끝:

```
... + `wave2-t9-admin-global-trash` (Wave 2 T9 — `GET /api/admin/trash` + `/admin/trash/all` viewer, 2026-05-07)
```

신규 line 4 끝:

```
... + `wave2-t9-admin-global-trash` (Wave 2 T9 — `GET /api/admin/trash` + `/admin/trash/all` viewer, 2026-05-07) + `audit-export-json` (Wave 1 T2 follow-up — `GET /api/admin/audit/export?format=csv|json`, 2026-05-08)
```

- [ ] **Step 3: §6 audit emit 행에 `format=json` 활성화 명시 (필요 시)**

`audit.exported` 관련 행(101행 부근, "audit emit coverage")의 enum 카운트는 변경 없음(0). 메모만 추가:

```
... + audit-export-json 트랙으로 `metadata.format` 동적화(`csv|json`, 2026-05-08).
```

### Task P6.4: docs/progress.md — 트랙 closure 엔트리 (최상단)

- [ ] **Step 1: 최상단(`---` 다음 첫 `## ...` 직전)에 새 엔트리 삽입**

```markdown
## 2026-05-08 — 🏁 audit-export-json 트랙 종료 (Wave 1 T2 후속 — `GET /api/admin/audit/export?format=csv|json`)

### 범위

Wave 1 T2(`audit-export-endpoint`)의 CSV-only 다운로드를 `format=csv|json` 분기로 확장. 기존 호출자 호환성 유지(default csv) + AUDIT_EXPORTED audit metadata `format` 동적화. 신규 `AuditJsonWriter`(plain JSON 배열, BOM 미부착, nested metadata) + `AuditExportEvent.format` 필드 + Listener defensive guard.

### 변경 핵심

**Backend:**
- `AuditJsonWriter` 신규 — `ObjectMapper.writeValue(out, entries)`. SequenceWriter 미사용(KISS).
- `AuditExportEvent`에 `String format` 필드 추가(7-arg record).
- `AuditExportListener` — metadata `"format":"csv"` 하드코딩 → `event.format()` 동적, "csv"/"json" 외 값은 "csv" fallback + WARN 로그(defense-in-depth).
- `AuditQueryController.exportCsv` → `export`로 rename + `format` 파라미터(default csv) 분기. `format` 검증 실패 시 `IllegalArgumentException` → `GlobalExceptionHandler.handleBadRequest`가 400 `BAD_REQUEST`.
- 테스트: `AuditExportListenerTest`(신규, 3 케이스), `AuditJsonWriterTest`(신규, 4 케이스), `AuditExportE2ETest` 2 케이스 추가(format=json + format=invalid 400).

**Frontend:**
- `api.getAuditLogsExportUrl(filters, format='csv')` 시그니처 확장 + `format` query param 합성. 단위 테스트 5 케이스(`api.getAuditLogsExportUrl.test.ts`).
- `/admin/audit/logs` 페이지 — "CSV 내보내기" + "JSON 내보내기" 두 anchor. testid는 `audit-export-link-csv` / `audit-export-link-json`로 분리. page.test.tsx 갱신.

**Docs:**
- `docs/02 §7.12` `format` 행 추가 + JSON 응답 예시.
- `docs/04 §7.2` line 263~264 `format=csv|json` 분기 + nested metadata 표기.
- `BETA-RELEASE.md §7` v1.x deferred "audit log JSON export endpoint" 라인 제거 + Source 체인에 audit-export-json 트랙 추가.

### 검증

- `cd backend && ./gradlew test` BUILD SUCCESSFUL (Listener/Writer/Controller slice/E2E 모두 GREEN)
- `cd frontend && npm run typecheck && npm run lint && npm test -- --run` 모두 exit 0
- AUDIT_EXPORTED enum 변경 0 (47 enum 유지). 새 에러 코드 0.

### 다음 세션 컨텍스트

- 진정한 SQL → JSON streaming(메모리 cap 제거)은 v1.x.
- `format` enum 강타입화는 v1.x — 현재는 string check + 400 으로 충분.
- NDJSON 형식 / `application/x-ndjson`은 별도 트랙 후보.
```

### Task P6.5: 4개 docs 변경 한 커밋

- [ ] **Step 1: 커밋**

```bash
git add docs/02-backend-data-model.md \
        docs/04-admin-operations.md \
        BETA-RELEASE.md \
        docs/progress.md
git commit -m "docs(audit-export-json): document format=csv|json branch + progress closure entry"
```

---

## P7: 게이트 + PR

**Files:** N/A (검증·배포 단계)

### Task P7.1: 전체 게이트

- [ ] **Step 1: backend test (전체)**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL — 신규 케이스 모두 GREEN, 기존 회귀 0건.

- [ ] **Step 2: frontend gates**

Run:
```bash
cd frontend
npm run typecheck
npm run lint
npm test -- --run
npm run build
```

Expected: 모두 exit 0. test는 신규 case 포함 GREEN.

- [ ] **Step 3: 핵심 원칙 11개 검토 (CLAUDE.md §3)**

직접 확인 — 본 트랙은 read-only endpoint 확장이므로 1·3·6·10번 N/A. 8(audit append-only) 위반 없음(기존 emit 그대로). 12(에러 코드 계약) 위반 없음(신규 enum 0).

### Task P7.2: PR 생성

- [ ] **Step 1: 푸시**

```bash
git push -u origin feat/audit-export-json
```

- [ ] **Step 2: PR 생성**

```bash
gh pr create --title "feat(audit-export-json): GET /api/admin/audit/export?format=csv|json (Wave 1 T2 follow-up)" --body "$(cat <<'EOF'
## Summary
- Wave 1 T2(`audit-export-endpoint`) 후속. `GET /api/admin/audit/export`에 `format=csv|json` 파라미터 추가. default csv로 호환성 유지.
- 신규 `AuditJsonWriter`(plain JSON 배열, nested metadata) + `AuditExportEvent.format` + Listener 동적 metadata.
- Frontend `/admin/audit/logs`에 "JSON 내보내기" anchor 1개 추가.

## Test plan
- [x] `cd backend && ./gradlew test` GREEN (Listener 3 / Writer 4 / E2E json+invalid 2 신규)
- [x] `cd frontend && npm run typecheck && npm run lint && npm test -- --run && npm run build` 모두 exit 0
- [x] CLAUDE.md §3 11개 핵심 원칙 위반 0
- [x] 새 audit enum 0, 새 에러 코드 0

## Refs
- spec: `docs/superpowers/specs/2026-05-07-audit-export-json-design.md`
- plan: `docs/superpowers/plans/2026-05-08-audit-export-json.md`
- BETA-RELEASE.md §7 v1.x deferred "audit log JSON export endpoint" 항목 활성화
EOF
)"
```

- [ ] **Step 3: CI 대기 + 머지 게이트 (사용자 확인)**

```bash
gh pr checks <PR#> --watch --interval 30
```

CI 둘 다 GREEN 후 사용자 확인을 받아 squash-merge. archive PR은 머지 후 별도 트랙으로 진행.

---

## 자체 검토 (writing-plans Self-Review)

**1. Spec coverage:**
- §3.1 endpoint contract → P3.1 controller (format param + 분기 + 400 매핑)
- §3.2 JSON 응답 모양 (plain array, nested metadata) → P2 (writer) + P3.3 E2E (body 검증)
- §3.3 audit_log metadata format 동적화 → P1 (event field + listener) + P3.3 E2E (metadata 검증)
- §4.1 AuditJsonWriter → P2
- §4.2 Controller / Event / Listener 수정 → P1 + P3
- §4.3 Tests → P1 (Listener) + P2 (Writer) + P3.2 (slice mock) + P3.3 (E2E)
- §5.1 frontend api 시그니처 → P4
- §5.1 frontend page anchor → P5
- §5.2 frontend tests → P4 + P5
- §6 docs 갱신 → P6
- §7 핵심 원칙 → P7.1 Step 3
- §8 비범위 → 본 plan에 미포함(의도)

빠진 항목 없음.

**2. Placeholder scan:** "TBD"/"TODO" 없음. 모든 step에 실제 코드/명령. P6.1은 "정확한 라인 위치는 작업자가 grep으로 확인"이라고 명시했으나 갱신 내용은 구체적이므로 placeholder 아님.

**3. Type consistency:**
- `AuditExportEvent` 7-arg record — P1.2 정의, P1.3·P3.1 사용 일치.
- `AuditJsonWriter.write(OutputStream, List<AuditLogEntryDto>)` — P2.2 정의, P3.1 사용 일치.
- `getAuditLogsExportUrl(filters, format='csv')` — P4.2 정의, P5.2 사용 일치.
- testid `audit-export-link-csv` / `audit-export-link-json` — P5.1 테스트와 P5.2 컴포넌트 일치.
