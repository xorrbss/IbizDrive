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
            "1",
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
        assertThat(row.get("id").asText()).isEqualTo("1");
        assertThat(row.get("eventType").asText()).isEqualTo("user.login.failed");
        assertThat(row.get("ip").asText()).isEqualTo("10.0.0.1");
        assertThat(row.get("metadata").isObject()).isTrue();
        assertThat(row.get("metadata").get("reason").asText()).isEqualTo("BAD_PASSWORD");
    }

    @Test
    void nullFieldsSerializeAsJsonNull() throws Exception {
        AuditLogEntryDto entry = new AuditLogEntryDto(
            "10",
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
            "20",
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
