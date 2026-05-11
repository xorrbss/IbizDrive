package com.ibizdrive.trash;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

/**
 * {@link TrashPolicyService} 단위 테스트 — trash-retention-mutation Phase B.
 *
 * <p>Mockito mock repo + event publisher로 Postgres 의존성 없이 service 흐름만 검증:
 * <ul>
 *   <li>{@link TrashPolicyService#ensureSingletonRow}: row 부재 → yml default INSERT,
 *       row 존재 → no-op, race(DataIntegrityViolation) → swallow.</li>
 *   <li>{@link TrashPolicyService#getRetentionDays}: row 존재 → days 반환, 부재 → IllegalStateException.</li>
 *   <li>{@link TrashPolicyService#updateRetentionDays}: 7..90 검증, no-op (audit 미발행),
 *       정상 변경 → row 갱신 + event publish, actor null → IllegalArgumentException.</li>
 * </ul>
 */
class TrashPolicyServiceTest {

    private TrashPolicyRepository repository;
    private ApplicationEventPublisher events;
    private TrashRetentionProperties yml;
    private TrashPolicyService service;

    @BeforeEach
    void setup() {
        repository = mock(TrashPolicyRepository.class);
        events = mock(ApplicationEventPublisher.class);
        yml = new TrashRetentionProperties(30);
        service = new TrashPolicyService(repository, yml, events);
    }

    // ──────────────────────────────────────────────────────────────────
    // ensureSingletonRow
    // ──────────────────────────────────────────────────────────────────

    @Test
    void ensureSingletonRow_insertsYmlDefault_whenRowAbsent() {
        when(repository.existsById(TrashPolicy.SINGLETON_ID)).thenReturn(false);

        service.ensureSingletonRow();

        ArgumentCaptor<TrashPolicy> captor = ArgumentCaptor.forClass(TrashPolicy.class);
        verify(repository).save(captor.capture());
        TrashPolicy saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(TrashPolicy.SINGLETON_ID);
        assertThat(saved.getRetentionDays()).isEqualTo(30);
        assertThat(saved.getUpdatedBy()).isNull();
    }

    @Test
    void ensureSingletonRow_noOp_whenRowPresent() {
        when(repository.existsById(TrashPolicy.SINGLETON_ID)).thenReturn(true);

        service.ensureSingletonRow();

        verify(repository, never()).save(any());
    }

    @Test
    void ensureSingletonRow_swallowsDataIntegrityViolation_raceWithOtherInstance() {
        when(repository.existsById(TrashPolicy.SINGLETON_ID)).thenReturn(false);
        when(repository.save(any(TrashPolicy.class)))
            .thenThrow(new DataIntegrityViolationException("race: other instance inserted first"));

        // race가 발생해도 service는 throw하지 않음 (idempotency).
        service.ensureSingletonRow();

        verify(repository).save(any(TrashPolicy.class));
    }

    // ──────────────────────────────────────────────────────────────────
    // getRetentionDays
    // ──────────────────────────────────────────────────────────────────

    @Test
    void getRetentionDays_returnsDays_whenRowPresent() {
        TrashPolicy row = new TrashPolicy(TrashPolicy.SINGLETON_ID, 14, Instant.now(), null);
        when(repository.findById(TrashPolicy.SINGLETON_ID)).thenReturn(Optional.of(row));

        assertThat(service.getRetentionDays()).isEqualTo(14);
    }

    @Test
    void getRetentionDays_throwsIllegalState_whenRowMissing() {
        when(repository.findById(TrashPolicy.SINGLETON_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRetentionDays())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("trash_policy");
    }

    // ──────────────────────────────────────────────────────────────────
    // updateRetentionDays
    // ──────────────────────────────────────────────────────────────────

    @Test
    void updateRetentionDays_appliesAndPublishesEvent() {
        UUID actor = UUID.randomUUID();
        TrashPolicy row = new TrashPolicy(TrashPolicy.SINGLETON_ID, 30, Instant.now(), null);
        when(repository.findById(TrashPolicy.SINGLETON_ID)).thenReturn(Optional.of(row));
        when(repository.save(any(TrashPolicy.class))).thenAnswer(inv -> inv.getArgument(0));

        int applied = service.updateRetentionDays(14, actor);

        assertThat(applied).isEqualTo(14);
        assertThat(row.getRetentionDays()).isEqualTo(14);
        assertThat(row.getUpdatedBy()).isEqualTo(actor);
        ArgumentCaptor<RetentionPolicyChangedEvent> evt = ArgumentCaptor.forClass(RetentionPolicyChangedEvent.class);
        verify(events).publishEvent(evt.capture());
        assertThat(evt.getValue().beforeDays()).isEqualTo(30);
        assertThat(evt.getValue().afterDays()).isEqualTo(14);
        assertThat(evt.getValue().actorId()).isEqualTo(actor);
    }

    @Test
    void updateRetentionDays_noOp_whenSameValue() {
        UUID actor = UUID.randomUUID();
        TrashPolicy row = new TrashPolicy(TrashPolicy.SINGLETON_ID, 30, Instant.now(), null);
        when(repository.findById(TrashPolicy.SINGLETON_ID)).thenReturn(Optional.of(row));

        int applied = service.updateRetentionDays(30, actor);

        assertThat(applied).isEqualTo(30);
        verify(repository, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void updateRetentionDays_throwsIllegalArgument_whenBelowMin() {
        assertThatThrownBy(() -> service.updateRetentionDays(6, UUID.randomUUID()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("between 7 and 90");
        verify(events, never()).publishEvent(any());
    }

    @Test
    void updateRetentionDays_throwsIllegalArgument_whenAboveMax() {
        assertThatThrownBy(() -> service.updateRetentionDays(91, UUID.randomUUID()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("between 7 and 90");
        verify(events, never()).publishEvent(any());
    }

    @Test
    void updateRetentionDays_throwsIllegalArgument_whenActorNull() {
        assertThatThrownBy(() -> service.updateRetentionDays(14, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("actorId");
        verify(events, never()).publishEvent(any());
    }

    @Test
    void updateRetentionDays_throwsIllegalState_whenRowMissing() {
        when(repository.findById(TrashPolicy.SINGLETON_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRetentionDays(14, UUID.randomUUID()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("trash_policy");
    }

    @Test
    void updateRetentionDays_acceptsBoundaryValues() {
        UUID actor = UUID.randomUUID();
        TrashPolicy row = new TrashPolicy(TrashPolicy.SINGLETON_ID, 30, Instant.now(), null);
        when(repository.findById(TrashPolicy.SINGLETON_ID)).thenReturn(Optional.of(row));
        when(repository.save(any(TrashPolicy.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.updateRetentionDays(7, actor)).isEqualTo(7);
        assertThat(service.updateRetentionDays(90, actor)).isEqualTo(90);
        verify(events, times(2)).publishEvent(any(RetentionPolicyChangedEvent.class));
    }
}
