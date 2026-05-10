package com.ibizdrive.folder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CrossWorkspaceMoveAuditListenerTest {

    private final AuditService auditService = mock(AuditService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final CrossWorkspaceMoveAuditListener listener = new CrossWorkspaceMoveAuditListener(auditService, mapper);

    @Test
    void emitsFolderMovedCrossWorkspaceWithMetadata() {
        UUID actor = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        UUID destScopeId = UUID.randomUUID();
        Instant now = Instant.now();

        listener.onCrossWorkspaceMove(new CrossWorkspaceMoveCompletedEvent(
            "folder", resourceId,
            ScopeType.DEPARTMENT, ScopeType.TEAM, destScopeId,
            3, 5, 2, actor, now
        ));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent ev = captor.getValue();
        assertThat(ev.eventType()).isEqualTo(AuditEventType.FOLDER_MOVED_CROSS_WORKSPACE);
        assertThat(ev.targetType()).isEqualTo(AuditTargetType.FOLDER);
        assertThat(ev.targetId()).isEqualTo(resourceId);
        assertThat(ev.actorId()).isEqualTo(actor);
        assertThat(ev.metadata()).contains("\"subtreeFolderCount\":3");
        assertThat(ev.metadata()).contains("\"subtreeFileCount\":5");
        assertThat(ev.metadata()).contains("\"revokedShareCount\":2");
    }

    @Test
    void emitsFileMovedCrossWorkspace() {
        UUID actor = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID destScopeId = UUID.randomUUID();

        listener.onCrossWorkspaceMove(new CrossWorkspaceMoveCompletedEvent(
            "file", fileId,
            ScopeType.DEPARTMENT, ScopeType.TEAM, destScopeId,
            0, 1, 1, actor, Instant.now()
        ));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent ev = captor.getValue();
        assertThat(ev.eventType()).isEqualTo(AuditEventType.FILE_MOVED_CROSS_WORKSPACE);
        assertThat(ev.targetType()).isEqualTo(AuditTargetType.FILE);
        assertThat(ev.targetId()).isEqualTo(fileId);
    }
}
