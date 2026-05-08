package com.ibizdrive.admin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AdminSystemService.toggleCron 단위 테스트 — repository는 mock.
 * 검증 포인트: enabled 변경 + event publish + unknown key 거부.
 */
@ExtendWith(MockitoExtension.class)
class AdminSystemServiceToggleCronTest {

    @Mock
    private CronPolicyRepository cronPolicyRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void enableExistingCronPersistsAndPublishesEvent() throws Exception {
        UUID actor = UUID.randomUUID();
        InetAddress ip = InetAddress.getByName("10.0.0.1");
        String ua = "TestAgent/1.0";
        CronPolicy existing = new CronPolicy("permission.expire", false, Instant.now(), null);
        when(cronPolicyRepository.findById("permission.expire")).thenReturn(Optional.of(existing));
        when(cronPolicyRepository.save(any(CronPolicy.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminSystemService service = new AdminSystemService(cronPolicyRepository, eventPublisher);
        service.toggleCron("permission.expire", true, actor, ip, ua);

        ArgumentCaptor<CronPolicy> savedCaptor = ArgumentCaptor.forClass(CronPolicy.class);
        verify(cronPolicyRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().isEnabled()).isTrue();
        assertThat(savedCaptor.getValue().getUpdatedBy()).isEqualTo(actor);

        ArgumentCaptor<AdminCronToggledEvent> eventCaptor =
            ArgumentCaptor.forClass(AdminCronToggledEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        AdminCronToggledEvent ev = eventCaptor.getValue();
        assertThat(ev.actorId()).isEqualTo(actor);
        assertThat(ev.jobKey()).isEqualTo("permission.expire");
        assertThat(ev.fromEnabled()).isFalse();
        assertThat(ev.toEnabled()).isTrue();
    }

    @Test
    void unknownKeyThrowsIllegalArgument() throws Exception {
        when(cronPolicyRepository.findById("unknown.cron")).thenReturn(Optional.empty());
        AdminSystemService service = new AdminSystemService(cronPolicyRepository, eventPublisher);

        assertThatThrownBy(() ->
            service.toggleCron("unknown.cron", true, UUID.randomUUID(),
                InetAddress.getByName("10.0.0.1"), "UA")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("unknown cron key");

        verify(cronPolicyRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void noOpToggleStillPersistsAndPublishes() throws Exception {
        // 같은 값으로 토글해도 audit emit (관리자 액션 자체는 추적해야 함)
        CronPolicy existing = new CronPolicy("share.expire", true, Instant.now(), null);
        when(cronPolicyRepository.findById("share.expire")).thenReturn(Optional.of(existing));
        when(cronPolicyRepository.save(any(CronPolicy.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminSystemService service = new AdminSystemService(cronPolicyRepository, eventPublisher);
        service.toggleCron("share.expire", true, UUID.randomUUID(),
            InetAddress.getByName("10.0.0.1"), "UA");

        verify(cronPolicyRepository).save(any(CronPolicy.class));
        ArgumentCaptor<AdminCronToggledEvent> eventCaptor =
            ArgumentCaptor.forClass(AdminCronToggledEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().fromEnabled()).isTrue();
        assertThat(eventCaptor.getValue().toEnabled()).isTrue();
    }
}
