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
