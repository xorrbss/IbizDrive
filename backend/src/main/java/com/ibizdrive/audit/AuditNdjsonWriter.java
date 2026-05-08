package com.ibizdrive.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.dto.AuditLogEntryDto;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * 감사 로그 NDJSON 직렬화 — {@code GET /api/admin/audit/export?format=ndjson} 응답 본문 작성기.
 *
 * <p>{@link AuditJsonWriter}와의 차이: JSON은 array 한 덩어리 (`[{...},{...}]`), NDJSON은
 * row마다 한 줄 (`{...}\n{...}\n`). line-oriented 도구(`jq`/`grep`/`split`)와 SIEM ingest에
 * 친화적이다.
 *
 * <p>마지막 row 뒤에도 `\n` 발행 — POSIX text file 관행 + 빈 결과(0 bytes) vs 단일 row 구분이
 * line count로 결정적.
 *
 * <p><b>구현 노트</b>: {@code ObjectMapper.writeValue(OutputStream, ...)}는 내부 {@code JsonGenerator}
 * close 시 {@code AUTO_CLOSE_TARGET}(기본 enabled)으로 OutputStream까지 닫는다 — 반복 호출이
 * 두 번째부터 IOException. 이를 회피하기 위해 {@link ObjectMapper#writeValueAsBytes} → {@code out.write}
 * 패턴을 쓴다. row 단위 추가 메모리는 cap 10,000 한도에서 무시할 만함 (KISS).
 */
@Component
public class AuditNdjsonWriter {

    private final ObjectMapper objectMapper;

    public AuditNdjsonWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * NDJSON 본문을 {@code out}에 기록한다. flush/close는 호출자 책임
     * ({@code StreamingResponseBody}가 OutputStream 수명을 관리).
     */
    public void write(OutputStream out, List<AuditLogEntryDto> entries) throws IOException {
        for (AuditLogEntryDto e : entries) {
            byte[] bytes = objectMapper.writeValueAsBytes(e);
            out.write(bytes);
            out.write('\n');
        }
    }
}
