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
 * AuditExportListener — AUDIT_EXPORTED metadata의 {@code format}/{@code rowCap} 필드가
 * {@link AuditExportEvent}의 값을 그대로 반영하는지 검증.
 *
 * <p>이력:
 * <ul>
 *   <li>audit-format-enum (2026-05-08): {@code format} String → enum 마이그. 이전
 *       {@code unknownFormatFallsBackToCsv} 테스트는 enum이 컴파일 단계에서 invalid 값을
 *       차단해 삭제.</li>
 *   <li>audit-export-cap-metadata (2026-05-08): {@code rowCap} 필드 추가 — 운영 디버깅용.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AuditExportListenerTest {

    @Mock
    private AuditService auditService;

    private static final int CAP = 10_000;

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
            AuditExportFormat.CSV,
            CAP
        );

        listener.onExport(event);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().metadata())
            .contains("\"format\":\"csv\"")
            .contains("\"rowCap\":10000");
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
            AuditExportFormat.JSON,
            CAP
        );

        listener.onExport(event);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().metadata())
            .contains("\"format\":\"json\"")
            .contains("\"rowCount\":7")
            .contains("\"truncated\":true")
            .contains("\"rowCap\":10000");
    }

    @Test
    void ndjsonFormatEventEmitsMetadataFormatNdjson() throws Exception {
        AuditExportListener listener = new AuditExportListener(auditService);
        AuditExportEvent event = new AuditExportEvent(
            UUID.randomUUID(),
            InetAddress.getByName("10.0.0.1"),
            "TestAgent/1.0",
            "{}",
            3,
            false,
            AuditExportFormat.NDJSON,
            CAP
        );

        listener.onExport(event);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().metadata())
            .contains("\"format\":\"ndjson\"")
            .contains("\"rowCount\":3")
            .contains("\"rowCap\":10000");
    }

    @Test
    void rowCapInMetadataReflectsEventValue() throws Exception {
        // 운영자가 application.yml로 cap을 5000으로 줄인 시나리오.
        AuditExportListener listener = new AuditExportListener(auditService);
        AuditExportEvent event = new AuditExportEvent(
            UUID.randomUUID(),
            InetAddress.getByName("10.0.0.1"),
            "TestAgent/1.0",
            "{}",
            5_000,
            true,
            AuditExportFormat.CSV,
            5_000
        );

        listener.onExport(event);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().metadata()).contains("\"rowCap\":5000");
    }
}
