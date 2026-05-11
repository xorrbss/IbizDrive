package com.ibizdrive.admin;

import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import com.ibizdrive.user.UserStorageQuotaChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdminUserQuotaService} 단위 테스트 — quota mutation Phase 3.
 *
 * <p>{@link com.ibizdrive.trash.TrashPolicyServiceTest} 패턴 답습. Mockito mock repo + event
 * publisher + User로 Postgres 의존성 없이 service 흐름만 검증:
 * <ul>
 *   <li>{@code getQuota}: user 존재 → DTO 반환, soft-deleted → AdminUserNotFoundException.</li>
 *   <li>{@code updateQuota}: 음수 거부, actor null 거부, no-op (audit 미발행), 정상 변경 →
 *       row 갱신 + event publish, soft-deleted → 404 매핑.</li>
 * </ul>
 */
class AdminUserQuotaServiceTest {

    private static final long DEFAULT_QUOTA = 10_737_418_240L;       // 10GB
    private static final long DOUBLED_QUOTA = 21_474_836_480L;       // 20GB

    private UserRepository userRepository;
    private ApplicationEventPublisher events;
    private AdminUserQuotaService service;

    @BeforeEach
    void setup() {
        userRepository = mock(UserRepository.class);
        events = mock(ApplicationEventPublisher.class);
        service = new AdminUserQuotaService(userRepository, events);
    }

    // ──────────────────────────────────────────────────────────────────
    // getQuota
    // ──────────────────────────────────────────────────────────────────

    @Test
    void getQuota_returnsDto_whenUserActive() {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        when(user.isDeleted()).thenReturn(false);
        when(user.getStorageQuota()).thenReturn(DEFAULT_QUOTA);
        when(user.getStorageUsed()).thenReturn(1_000L);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        AdminUserQuotaDto dto = service.getQuota(userId);

        assertThat(dto.storageQuota()).isEqualTo(DEFAULT_QUOTA);
        assertThat(dto.storageUsed()).isEqualTo(1_000L);
    }

    @Test
    void getQuota_throws404_whenUserMissing() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getQuota(userId))
            .isInstanceOf(AdminUserNotFoundException.class);
    }

    @Test
    void getQuota_throws404_whenUserSoftDeleted() {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        when(user.isDeleted()).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.getQuota(userId))
            .isInstanceOf(AdminUserNotFoundException.class);
    }

    // ──────────────────────────────────────────────────────────────────
    // updateQuota — invariant guards
    // ──────────────────────────────────────────────────────────────────

    @Test
    void updateQuota_throwsIllegalArgument_whenQuotaNegative() {
        assertThatThrownBy(() -> service.updateQuota(UUID.randomUUID(), -1L, UUID.randomUUID()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(">= 0");
        verify(events, never()).publishEvent(any());
    }

    @Test
    void updateQuota_throwsIllegalArgument_whenActorNull() {
        assertThatThrownBy(() -> service.updateQuota(UUID.randomUUID(), DEFAULT_QUOTA, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("actorId");
        verify(events, never()).publishEvent(any());
    }

    @Test
    void updateQuota_throws404_whenUserMissing() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateQuota(userId, DEFAULT_QUOTA, UUID.randomUUID()))
            .isInstanceOf(AdminUserNotFoundException.class);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void updateQuota_throws404_whenUserSoftDeleted() {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        when(user.isDeleted()).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.updateQuota(userId, DEFAULT_QUOTA, UUID.randomUUID()))
            .isInstanceOf(AdminUserNotFoundException.class);
        verify(events, never()).publishEvent(any());
    }

    // ──────────────────────────────────────────────────────────────────
    // updateQuota — happy path / idempotent
    // ──────────────────────────────────────────────────────────────────

    @Test
    void updateQuota_appliesAndPublishesEvent() {
        UUID userId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        User user = mock(User.class);
        when(user.isDeleted()).thenReturn(false);
        when(user.getStorageQuota()).thenReturn(DEFAULT_QUOTA, DOUBLED_QUOTA); // before then after
        when(user.getStorageUsed()).thenReturn(0L);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        AdminUserQuotaDto dto = service.updateQuota(userId, DOUBLED_QUOTA, actor);

        verify(user).changeStorageQuota(DOUBLED_QUOTA);
        verify(userRepository).save(user);
        assertThat(dto.storageQuota()).isEqualTo(DOUBLED_QUOTA);
        assertThat(dto.storageUsed()).isEqualTo(0L);

        ArgumentCaptor<UserStorageQuotaChangedEvent> evt =
            ArgumentCaptor.forClass(UserStorageQuotaChangedEvent.class);
        verify(events).publishEvent(evt.capture());
        assertThat(evt.getValue().targetUserId()).isEqualTo(userId);
        assertThat(evt.getValue().beforeQuota()).isEqualTo(DEFAULT_QUOTA);
        assertThat(evt.getValue().afterQuota()).isEqualTo(DOUBLED_QUOTA);
        assertThat(evt.getValue().actorId()).isEqualTo(actor);
    }

    @Test
    void updateQuota_noOp_whenSameValue() {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        when(user.isDeleted()).thenReturn(false);
        when(user.getStorageQuota()).thenReturn(DEFAULT_QUOTA);
        when(user.getStorageUsed()).thenReturn(500L);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        AdminUserQuotaDto dto = service.updateQuota(userId, DEFAULT_QUOTA, UUID.randomUUID());

        assertThat(dto.storageQuota()).isEqualTo(DEFAULT_QUOTA);
        assertThat(dto.storageUsed()).isEqualTo(500L);
        verify(user, never()).changeStorageQuota(org.mockito.ArgumentMatchers.anyLong());
        verify(userRepository, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void updateQuota_acceptsZeroAndLargeValues() {
        UUID userId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        User user = mock(User.class);
        when(user.isDeleted()).thenReturn(false);
        when(user.getStorageQuota()).thenReturn(DEFAULT_QUOTA, 0L, 0L, Long.MAX_VALUE);
        when(user.getStorageUsed()).thenReturn(0L);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        // 0 (한도 zero — 모든 신규 업로드 차단 시나리오 Phase 5)
        service.updateQuota(userId, 0L, actor);
        // Long.MAX_VALUE (사실상 무한 — 운영 grace)
        service.updateQuota(userId, Long.MAX_VALUE, actor);

        verify(events, times(2)).publishEvent(any(UserStorageQuotaChangedEvent.class));
    }

    @Test
    void updateQuota_allowsOverQuota_whenNewBelowUsed() {
        // 한도 축소로 over-quota가 되는 케이스 — Phase 5 enforcement에서 신규만 차단,
        // 한도 변경 자체는 허용 (운영 grace).
        UUID userId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        User user = mock(User.class);
        when(user.isDeleted()).thenReturn(false);
        when(user.getStorageQuota()).thenReturn(DOUBLED_QUOTA, 1_000L);
        when(user.getStorageUsed()).thenReturn(5_000L); // already over the new limit
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        AdminUserQuotaDto dto = service.updateQuota(userId, 1_000L, actor);

        verify(user).changeStorageQuota(1_000L);
        assertThat(dto.storageQuota()).isEqualTo(1_000L);
        assertThat(dto.storageUsed()).isEqualTo(5_000L); // 변경 안 됨
    }
}
