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
