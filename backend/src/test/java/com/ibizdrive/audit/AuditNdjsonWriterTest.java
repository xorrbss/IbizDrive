package com.ibizdrive.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * AuditNdjsonWriter — row-per-line JSON 직렬화 (audit-ndjson 트랙).
 *
 * <p>JsonWriter는 array 한 덩어리 vs NdjsonWriter는 row마다 한 줄 (`{...}\n{...}\n`).
 * 마지막 row 뒤에도 LF 발행 — 빈 결과(0 bytes) vs 단일 row 구분이 line count로 결정적.
 */
class AuditNdjsonWriterTest {

    private AuditNdjsonWriter writer;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        writer = new AuditNdjsonWriter(mapper);
    }

    @Test
    void emptyEntriesProducesZeroBytes() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.write(out, List.of());
        // 빈 list → 출력 0 byte. JSON array의 "[]" 와 명확히 다름.
        assertThat(out.toByteArray()).isEmpty();
    }

    @Test
    void singleEntryProducesOneLineWithTrailingLf() throws Exception {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("reason", "BAD_PASSWORD");
        AuditLogEntryDto entry = new AuditLogEntryDto(
            "1",
            OffsetDateTime.parse("2026-05-08T12:00:00+09:00"),
            "user.login.failed",
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            "kim@example.com",
            "user",
            UUID.fromString("00000000-0000-0000-0000-000000000003"),
            "kim@example.com",
            "10.0.0.1",
            meta,
            AuditSeverity.WARN
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.write(out, List.of(entry));

        byte[] bytes = out.toByteArray();
        assertThat(bytes).isNotEmpty();
        assertThat(bytes[bytes.length - 1]).isEqualTo((byte) '\n');

        // 한 줄이라 LF로 split 시 [json, ""] (마지막 trailing LF로 빈 element 한 개 추가됨)
        String[] lines = out.toString("UTF-8").split("\n", -1);
        assertThat(lines).hasSize(2);
        assertThat(lines[1]).isEmpty();

        JsonNode row = mapper.readTree(lines[0]);
        assertThat(row.get("id").asText()).isEqualTo("1");
        assertThat(row.get("eventType").asText()).isEqualTo("user.login.failed");
        assertThat(row.get("metadata").get("reason").asText()).isEqualTo("BAD_PASSWORD");
        // V19 severity — @JsonValue lower-case
        assertThat(row.get("severity").asText()).isEqualTo("warn");
    }

    @Test
    void multipleEntriesProduceLineSeparatedJson() throws Exception {
        AuditLogEntryDto e1 = entry("1", "user.login.failed");
        AuditLogEntryDto e2 = entry("2", "user.login.succeeded");
        AuditLogEntryDto e3 = entry("3", "permission.changed");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.write(out, List.of(e1, e2, e3));

        String[] lines = out.toString("UTF-8").split("\n", -1);
        // 3 row + trailing LF → 4 entries (마지막 빈 element)
        assertThat(lines).hasSize(4);
        assertThat(lines[3]).isEmpty();

        // 각 줄이 독립 JSON object
        assertThat(mapper.readTree(lines[0]).get("id").asText()).isEqualTo("1");
        assertThat(mapper.readTree(lines[1]).get("id").asText()).isEqualTo("2");
        assertThat(mapper.readTree(lines[2]).get("id").asText()).isEqualTo("3");
    }

    @Test
    void noBomEvenForKoreanContent() throws Exception {
        AuditLogEntryDto entry = new AuditLogEntryDto(
            "20",
            OffsetDateTime.parse("2026-05-08T12:00:00+09:00"),
            "user.created",
            null,
            "한글이름",
            null, null, null, null, null,
            AuditSeverity.INFO
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.write(out, List.of(entry));

        // NDJSON도 BOM 미부착. 첫 바이트는 '{'(0x7B).
        byte[] bytes = out.toByteArray();
        assertThat(bytes[0]).isEqualTo((byte) '{');
    }

    @Test
    void severityWireValues_serializeAsLowerCase() throws Exception {
        AuditLogEntryDto warn = entry("100", "permission.revoked", AuditSeverity.WARN);
        AuditLogEntryDto danger = entry("101", "share.created", AuditSeverity.DANGER);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.write(out, List.of(warn, danger));
        String[] lines = out.toString("UTF-8").split("\n", -1);

        assertThat(mapper.readTree(lines[0]).get("severity").asText()).isEqualTo("warn");
        assertThat(mapper.readTree(lines[1]).get("severity").asText()).isEqualTo("danger");
    }

    private static AuditLogEntryDto entry(String id, String eventType) {
        return entry(id, eventType, AuditSeverity.INFO);
    }

    private static AuditLogEntryDto entry(String id, String eventType, AuditSeverity severity) {
        return new AuditLogEntryDto(
            id,
            OffsetDateTime.parse("2026-05-08T12:00:00+09:00"),
            eventType,
            null, null, null, null, null, null, null,
            severity
        );
    }
}
