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
 * AuditExportListener — AUDIT_EXPORTED metadata의 {@code format} 필드가
 * AuditExportEvent.format() 값을 그대로 반영하는지 검증.
 *
 * <p>audit-format-enum 트랙(2026-05-08)으로 String → {@link AuditExportFormat} enum 마이그.
 * 이전 String 시절의 {@code unknownFormatFallsBackToCsv} 테스트는 enum 도입으로 컴파일 단계에서
 * invalid 값이 차단되어 삭제 (spec §5.1).
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
            AuditExportFormat.CSV
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
            AuditExportFormat.JSON
        );

        listener.onExport(event);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().metadata())
            .contains("\"format\":\"json\"")
            .contains("\"rowCount\":7")
            .contains("\"truncated\":true");
    }
}
