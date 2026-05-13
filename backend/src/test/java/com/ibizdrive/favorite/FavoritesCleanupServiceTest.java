package com.ibizdrive.favorite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * v1.x — favorites orphan cleanup service unit test (Mockito).
 *
 * <p>Repo mock, AuditService mock, ObjectMapper 실 인스턴스. cron 트리거 Job은 별도(통합 test 미요).
 */
@ExtendWith(MockitoExtension.class)
class FavoritesCleanupServiceTest {

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private AuditService auditService;

    private FavoritesCleanupService service;

    @BeforeEach
    void setUp() {
        // ObjectMapper는 실 인스턴스 — JSON 직렬화 동작도 함께 검증. @InjectMocks가 non-mock 필드를
        // 인식하지 않으므로 명시 생성.
        service = new FavoritesCleanupService(favoriteRepository, auditService, new ObjectMapper());
    }

    @Test
    void runDailyCleanup_orphan_없으면_count_0_그리고_audit_미발행() {
        when(favoriteRepository.deleteOrphans()).thenReturn(0);

        int deleted = service.runDailyCleanup();

        assertThat(deleted).isEqualTo(0);
        verify(favoriteRepository).deleteOrphans();
        verify(auditService, never()).record(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void runDailyCleanup_orphan_삭제되면_count_반환_그리고_audit_summary_발행() {
        when(favoriteRepository.deleteOrphans()).thenReturn(7);

        int deleted = service.runDailyCleanup();

        assertThat(deleted).isEqualTo(7);
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());

        AuditEvent event = captor.getValue();
        assertThat(event.eventType()).isEqualTo(AuditEventType.FAVORITES_ORPHANS_CLEANED);
        assertThat(event.targetType()).isEqualTo(AuditTargetType.SYSTEM);
        assertThat(event.actorId()).isNull();
        assertThat(event.targetId()).isNull();
        assertThat(event.afterState()).contains("\"deletedRows\":7");
        assertThat(event.afterState()).contains("\"durationMs\"");
    }
}
