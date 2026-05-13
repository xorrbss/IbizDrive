package com.ibizdrive.audit;

import com.ibizdrive.favorite.FavoriteStarredEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class FavoriteAuditListenerTest {

    @Mock
    private AuditService auditService;

    @InjectMocks
    private FavoriteAuditListener listener;

    @Test
    void file_starred_시_FILE_STARRED_audit_emit() {
        UUID actor = UUID.randomUUID();
        UUID resource = UUID.randomUUID();

        listener.onFavoriteStarred(new FavoriteStarredEvent(actor, "file", resource, true));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.eventType()).isEqualTo(AuditEventType.FILE_STARRED);
        assertThat(event.targetType()).isEqualTo(AuditTargetType.FILE);
        assertThat(event.targetId()).isEqualTo(resource);
        assertThat(event.actorId()).isEqualTo(actor);
    }

    @Test
    void file_unstarred_시_FILE_UNSTARRED_audit_emit() {
        UUID actor = UUID.randomUUID();
        UUID resource = UUID.randomUUID();

        listener.onFavoriteStarred(new FavoriteStarredEvent(actor, "file", resource, false));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(AuditEventType.FILE_UNSTARRED);
    }

    @Test
    void folder_starred_시_FOLDER_STARRED_audit_emit() {
        UUID actor = UUID.randomUUID();
        UUID resource = UUID.randomUUID();

        listener.onFavoriteStarred(new FavoriteStarredEvent(actor, "folder", resource, true));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.eventType()).isEqualTo(AuditEventType.FOLDER_STARRED);
        assertThat(event.targetType()).isEqualTo(AuditTargetType.FOLDER);
    }

    @Test
    void folder_unstarred_시_FOLDER_UNSTARRED_audit_emit() {
        UUID actor = UUID.randomUUID();
        UUID resource = UUID.randomUUID();

        listener.onFavoriteStarred(new FavoriteStarredEvent(actor, "folder", resource, false));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(AuditEventType.FOLDER_UNSTARRED);
    }

    @Test
    void invalid_resourceType_은_audit_skip_없이_swallow() {
        UUID actor = UUID.randomUUID();
        UUID resource = UUID.randomUUID();

        listener.onFavoriteStarred(new FavoriteStarredEvent(actor, "team", resource, true));

        // resourceType이 file/folder 외 — audit emission 자체 skip (방어).
        verifyNoInteractions(auditService);
    }

    @Test
    void audit_record_실패_시_swallow_비즈니스_흐름_보호() {
        UUID actor = UUID.randomUUID();
        UUID resource = UUID.randomUUID();
        doThrow(new RuntimeException("DB connection lost"))
            .when(auditService).record(any(AuditEvent.class));

        // 예외가 listener 밖으로 전파되면 outer commit이 이미 끝났는데 audit 실패가 비즈니스
        // mutation을 되돌리지 못함 → swallow가 일관성 유지 (ADR #24).
        listener.onFavoriteStarred(new FavoriteStarredEvent(actor, "file", resource, true));
    }
}
