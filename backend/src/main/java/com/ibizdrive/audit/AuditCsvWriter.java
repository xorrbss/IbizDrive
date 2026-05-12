package com.ibizdrive.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.dto.AuditLogEntryDto;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 감사 로그 CSV 직렬화 — {@code GET /api/admin/audit/export} 응답 본문 작성기.
 *
 * <p>frontend 의 client-side CSV writer 는 서버 export 로 완전 대체되어 현재 frontend 측
 * 동위 모듈은 존재하지 않는다. 본 writer 가 export 의 단일 진실 (RFC 4180 quoting,
 * UTF-8 BOM, {@code \r\n} 구분자).
 *
 * <p><b>설계 결정</b>: 별도 라이브러리(opencsv 등) 미도입. 컬럼 10개 + 단순 quoting이라 직접
 * 구현이 의존성·성능 모두 우위. KISS (CLAUDE.md §3 원칙 1).
 */
@Component
public class AuditCsvWriter {

    /** UTF-8 BOM — Excel에서 한글 깨짐 방지 (frontend BOM과 동일). */
    private static final String BOM = "\uFEFF";

    /** RFC 4180 line terminator. frontend `\r\n`과 일치. */
    private static final String LINE_TERMINATOR = "\r\n";

    /**
     * 헤더 — V19 에서 severity 추가 (metadata 직전 — 분석/필터에 자주 쓰는 컬럼은 자유 형식
     * metadata 보다 앞).
     */
    static final String[] HEADERS = {
        "id", "occurredAt", "eventType", "actorId", "actorName",
        "resourceType", "resourceId", "resourceName", "ip", "severity", "metadata"
    };

    private final ObjectMapper objectMapper;

    public AuditCsvWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * CSV 본문을 {@code out}에 기록한다. BOM → 헤더 → 각 row 순서.
     *
     * <p>flush/close는 호출자 책임 (StreamingResponseBody가 OutputStream 수명을 관리).
     */
    public void write(OutputStream out, List<AuditLogEntryDto> entries) throws IOException {
        Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        w.write(BOM);
        w.write(String.join(",", HEADERS));
        w.write(LINE_TERMINATOR);
        for (AuditLogEntryDto e : entries) {
            w.write(toRow(e));
            w.write(LINE_TERMINATOR);
        }
        w.flush();
    }

    /** 단일 row → CSV 한 줄 (terminator 미포함). 패키지-프라이빗 — 테스트 가시성. */
    String toRow(AuditLogEntryDto e) {
        return String.join(",",
            cell(e.id()),
            cell(formatOccurredAt(e.occurredAt())),
            cell(e.eventType()),
            cell(e.actorId() == null ? null : e.actorId().toString()),
            cell(e.actorName()),
            cell(e.resourceType()),
            cell(e.resourceId() == null ? null : e.resourceId().toString()),
            cell(e.resourceName()),
            cell(e.ip()),
            cell(e.severity() == null ? null : e.severity().wire()),
            cell(metadataToJson(e.metadata()))
        );
    }

    /** OffsetDateTime → ISO 8601 (frontend는 string 그대로). null → null. */
    private static String formatOccurredAt(OffsetDateTime ts) {
        return ts == null ? null : ts.toString();
    }

    /**
     * Map → JSON 텍스트. null → null (빈 셀로 출력). 직렬화 실패는 RuntimeException — export 도중
     * 한 row의 metadata가 손상되면 전체 응답을 실패시키는 편이 안전 (반쪽 export 회피).
     */
    private String metadataToJson(Map<String, Object> metadata) {
        if (metadata == null) return null;
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("audit metadata serialization failed", ex);
        }
    }

    /**
     * RFC 4180 cell — null → 빈 셀, 특수문자({@code "}, {@code ,}, CR, LF) 포함 시 전체 인용 +
     * 내부 {@code "}는 {@code ""}로 escape. frontend {@code csvCell}과 동일 규칙.
     */
    static String cell(String value) {
        if (value == null) return "";
        boolean needsQuoting = value.indexOf(',') >= 0
            || value.indexOf('"') >= 0
            || value.indexOf('\n') >= 0
            || value.indexOf('\r') >= 0;
        if (!needsQuoting) return value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
