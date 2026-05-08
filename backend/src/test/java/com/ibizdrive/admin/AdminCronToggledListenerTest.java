package com.ibizdrive.admin;

import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
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
 * AdminCronToggledListener — admin.cron.toggled audit_log emit 패턴 검증.
 * AdminDepartmentAuditListener 패턴 미러.
 */
@ExtendWith(MockitoExtension.class)
class AdminCronToggledListenerTest {

    @Mock
    private AuditService auditService;

    @Test
    void enableEmitsMetadataWithFromFalseToTrue() throws Exception {
        AdminCronToggledListener listener = new AdminCronToggledListener(auditService);
        UUID actor = UUID.randomUUID();
        AdminCronToggledEvent event = new AdminCronToggledEvent(
            actor,
            InetAddress.getByName("10.0.0.1"),
            "TestAgent/1.0",
            "permission.expire",
            false,
            true
        );

        listener.onToggled(event);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent emitted = captor.getValue();
        assertThat(emitted.eventType()).isEqualTo(AuditEventType.ADMIN_CRON_TOGGLED);
        assertThat(emitted.actorId()).isEqualTo(actor);
        assertThat(emitted.metadata())
            .contains("\"key\":\"permission.expire\"")
            .contains("\"fromEnabled\":false")
            .contains("\"toEnabled\":true");
    }

    @Test
    void disableEmitsMetadataWithFromTrueToFalse() throws Exception {
        AdminCronToggledListener listener = new AdminCronToggledListener(auditService);
        AdminCronToggledEvent event = new AdminCronToggledEvent(
            UUID.randomUUID(),
            InetAddress.getByName("10.0.0.1"),
            "TestAgent/1.0",
            "purge.expired",
            true,
            false
        );

        listener.onToggled(event);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().metadata())
            .contains("\"key\":\"purge.expired\"")
            .contains("\"fromEnabled\":true")
            .contains("\"toEnabled\":false");
    }
}
