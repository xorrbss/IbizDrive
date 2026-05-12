package com.ibizdrive.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.dto.AuditLogEntryDto;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AuditCsvWriter} 단위 테스트.
 *
 * <p>frontend {@code lib/auditCsv.test.ts}와 동일 규칙(헤더 순서, BOM, RFC 4180 quoting,
 * {@code \r\n}) 검증. 양 구현이 어긋나면 server-side와 client-side(legacy) export 결과가
 * 달라지므로 동일 픽스처가 동일 출력이 되도록 강제.
 */
class AuditCsvWriterTest {

    private final AuditCsvWriter writer = new AuditCsvWriter(new ObjectMapper());

    @Test
    void writesHeaderAndBomForEmptyList() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.write(out, List.of());

        String text = out.toString(StandardCharsets.UTF_8);

        assertThat(text).startsWith("\uFEFF");
        // V19 \u2014 severity \uCEEC\uB7FC\uC774 metadata \uC9C1\uC804.
        assertThat(text).isEqualTo(
            "\uFEFFid,occurredAt,eventType,actorId,actorName,resourceType,resourceId,resourceName,ip,severity,metadata\r\n"
        );
    }

    @Test
    void writesSimpleRowWithIsoTimestampAndUuids() throws Exception {
        UUID actor = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID target = UUID.fromString("22222222-2222-2222-2222-222222222222");
        AuditLogEntryDto entry = new AuditLogEntryDto(
            "42",
            OffsetDateTime.of(2026, 5, 6, 1, 2, 3, 0, ZoneOffset.UTC),
            "audit.exported",
            actor,
            "Alice",
            "audit",
            target,
            null,
            "10.0.0.1",
            Map.of("rowCount", 7),
            AuditSeverity.INFO
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.write(out, List.of(entry));

        String[] lines = out.toString(StandardCharsets.UTF_8).split("\r\n");
        assertThat(lines).hasSize(2);
        // metadata JSON은 `"` 를 포함하므로 RFC 4180에 의해 전체 인용 + 내부 " → ""로 escape.
        // severity 컬럼은 metadata 직전 (V19).
        assertThat(lines[1]).isEqualTo(
            "42,2026-05-06T01:02:03Z,audit.exported," + actor + ",Alice,audit," + target +
            ",,10.0.0.1,info,\"{\"\"rowCount\"\":7}\""
        );
    }

    @Test
    void writesSeverityWireValue_warnAndDanger() throws Exception {
        // V19 — DTO 의 AuditSeverity enum 은 wire() 로 lower-case 출력.
        AuditLogEntryDto warn = new AuditLogEntryDto(
            "100", OffsetDateTime.parse("2026-05-08T00:00:00Z"),
            "permission.revoked", null, null, "permission", null, null, null, null,
            AuditSeverity.WARN
        );
        AuditLogEntryDto danger = new AuditLogEntryDto(
            "101", OffsetDateTime.parse("2026-05-08T00:00:01Z"),
            "share.created", null, null, "share", null, null, null, null,
            AuditSeverity.DANGER
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.write(out, List.of(warn, danger));
        String[] lines = out.toString(StandardCharsets.UTF_8).split("\r\n");

        assertThat(lines[1]).contains(",warn,");
        assertThat(lines[2]).contains(",danger,");
    }

    @Test
    void quotesCellsContainingDelimitersAndQuotes() {
        // 셀에 콤마, 큰따옴표, 개행 → 전체 인용 + 내부 " 는 "" 로 escape (RFC 4180).
        assertThat(AuditCsvWriter.cell("hello, world")).isEqualTo("\"hello, world\"");
        assertThat(AuditCsvWriter.cell("she said \"hi\"")).isEqualTo("\"she said \"\"hi\"\"\"");
        assertThat(AuditCsvWriter.cell("line1\nline2")).isEqualTo("\"line1\nline2\"");
        assertThat(AuditCsvWriter.cell("plain")).isEqualTo("plain");
        assertThat(AuditCsvWriter.cell(null)).isEmpty();
    }

    @Test
    void preservesMetadataKeyOrderForDeterministicOutput() throws Exception {
        // LinkedHashMap → Jackson이 입력 순서를 보존. 테스트 결정성 확보.
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("filters", Map.of());
        meta.put("rowCount", 3);
        meta.put("truncated", false);

        AuditLogEntryDto entry = new AuditLogEntryDto(
            "1", OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            "audit.exported", null, null, "audit", null, null, null, meta,
            AuditSeverity.INFO
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.write(out, List.of(entry));
        String csv = out.toString(StandardCharsets.UTF_8);
        // metadata는 JSON으로 직렬화되어 셀에 들어간다. JSON에 콤마가 있으므로 인용된다.
        assertThat(csv).contains("\"{\"\"filters\"\":{},\"\"rowCount\"\":3,\"\"truncated\"\":false}\"");
    }

    @Test
    void handlesNullActorAndTarget() throws Exception {
        // 시스템 이벤트(actor 없음) — null 필드는 빈 셀로. severity 도 null 허용 (방어적 — 실제
        // DB row 는 NOT NULL 이지만 fixture 자체는 nullable 유지로 graceful).
        AuditLogEntryDto entry = new AuditLogEntryDto(
            "9", OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            "system.purge.executed", null, null, "system", null, null, null, null,
            null
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.write(out, List.of(entry));
        String[] lines = out.toString(StandardCharsets.UTF_8).split("\r\n");

        // OffsetDateTime#toString은 seconds==0 && nanos==0이면 ":00"을 생략 (java.time 사양).
        // 결과는 여전히 valid ISO-8601이며 frontend(JSON serializer)와 동일 포맷이므로 양 측 호환.
        // 컬럼 11개 — severity 와 metadata 모두 null → 둘 다 빈 셀 (콤마 사이 공백 없음).
        assertThat(lines[1]).isEqualTo(
            "9,2026-01-01T00:00Z,system.purge.executed,,,system,,,,,"
        );
    }
}
