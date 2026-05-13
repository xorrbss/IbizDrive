package com.ibizdrive.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UserQuotaEnforcer} 단위 — quota mutation Phase 5 (`docs/04 §6.1`).
 *
 * <p>{@link com.ibizdrive.admin.AdminUserQuotaServiceTest} 패턴 답습. Mockito mock repo + User로
 * Postgres 의존성 없이 enforcer 흐름만 검증. 동시성 시나리오는 별도 integration test
 * ({@code FileUploadServiceTest}의 quota 케이스가 실제 lock을 검증).
 */
class UserQuotaEnforcerTest {

    private UserRepository userRepository;
    private UserQuotaEnforcer enforcer;
    private UUID userId;

    @BeforeEach
    void setup() {
        userRepository = mock(UserRepository.class);
        enforcer = new UserQuotaEnforcer(userRepository);
        userId = UUID.randomUUID();
    }

    @Test
    void consumeOrThrow_throwsIllegalArgument_whenUserIdNull() {
        assertThatThrownBy(() -> enforcer.consumeOrThrow(null, 100))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("userId");
        verify(userRepository, never()).lockActiveById(any());
    }

    @Test
    void consumeOrThrow_throwsIllegalArgument_whenDeltaNegative() {
        assertThatThrownBy(() -> enforcer.consumeOrThrow(userId, -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("delta");
        verify(userRepository, never()).lockActiveById(any());
    }

    @Test
    void consumeOrThrow_throwsIllegalState_whenUserMissing() {
        when(userRepository.lockActiveById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> enforcer.consumeOrThrow(userId, 100))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void consumeOrThrow_throwsQuotaExceeded_whenOverQuota() {
        User user = mock(User.class);
        when(user.getStorageQuota()).thenReturn(1000L);
        when(user.getStorageUsed()).thenReturn(900L);
        when(userRepository.lockActiveById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> enforcer.consumeOrThrow(userId, 200))
            .isInstanceOf(QuotaExceededException.class)
            .satisfies(ex -> {
                QuotaExceededException qex = (QuotaExceededException) ex;
                assertThat(qex.getUserId()).isEqualTo(userId);
                assertThat(qex.getCurrentUsed()).isEqualTo(900L);
                assertThat(qex.getQuota()).isEqualTo(1000L);
                assertThat(qex.getRequestedDelta()).isEqualTo(200L);
            });

        verify(user, never()).consumeStorage(org.mockito.ArgumentMatchers.anyLong());
        verify(userRepository, never()).save(any());
    }

    @Test
    void consumeOrThrow_appliesDelta_whenWithinQuota() {
        User user = mock(User.class);
        when(user.getStorageQuota()).thenReturn(1000L);
        when(user.getStorageUsed()).thenReturn(500L);
        when(userRepository.lockActiveById(userId)).thenReturn(Optional.of(user));

        enforcer.consumeOrThrow(userId, 300);

        verify(user).consumeStorage(300L);
        verify(userRepository).save(user);
    }

    @Test
    void consumeOrThrow_acceptsExactQuotaBoundary() {
        // used + delta == quota → 통과 (`>` strict).
        User user = mock(User.class);
        when(user.getStorageQuota()).thenReturn(1000L);
        when(user.getStorageUsed()).thenReturn(900L);
        when(userRepository.lockActiveById(userId)).thenReturn(Optional.of(user));

        enforcer.consumeOrThrow(userId, 100);

        verify(user).consumeStorage(100L);
        verify(userRepository).save(user);
    }

    @Test
    void consumeOrThrow_zeroDelta_skipsSaveButStillLocks() {
        User user = mock(User.class);
        when(user.getStorageQuota()).thenReturn(1000L);
        when(user.getStorageUsed()).thenReturn(1000L); // 정확히 한도
        when(userRepository.lockActiveById(userId)).thenReturn(Optional.of(user));

        enforcer.consumeOrThrow(userId, 0);

        verify(userRepository).lockActiveById(userId);
        verify(user, never()).consumeStorage(org.mockito.ArgumentMatchers.anyLong());
        verify(userRepository, never()).save(any());
    }

    @Test
    void consumeOrThrow_overQuotaShrinkScenario_blocksNewUpload() {
        // admin이 한도를 1000→500으로 축소했고 기존 used=800 (over-quota 상태).
        // 신규 업로드(100 byte)는 차단되어야 한다 — Phase 3 결정: 한도 축소 허용 + 신규만 차단.
        User user = mock(User.class);
        when(user.getStorageQuota()).thenReturn(500L);
        when(user.getStorageUsed()).thenReturn(800L);
        when(userRepository.lockActiveById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> enforcer.consumeOrThrow(userId, 100))
            .isInstanceOf(QuotaExceededException.class);
    }

    // ──────────────────────────────────────────────────────────────────
    // release — quota mutation Phase 6 (hard delete decrement)
    // ──────────────────────────────────────────────────────────────────

    @Test
    void release_throwsIllegalArgument_whenUserIdNull() {
        assertThatThrownBy(() -> enforcer.release(null, 100))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("userId");
        verify(userRepository, never()).lockActiveById(any());
    }

    @Test
    void release_throwsIllegalArgument_whenDeltaNegative() {
        assertThatThrownBy(() -> enforcer.release(userId, -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("delta");
    }

    @Test
    void release_zeroDelta_noLockNoSave() {
        enforcer.release(userId, 0);

        verify(userRepository, never()).lockActiveById(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void release_userMissing_isNoOp_doesNotThrow() {
        when(userRepository.lockActiveById(userId)).thenReturn(Optional.empty());

        // user 없음/soft-deleted — hard delete는 사용자 라이프사이클과 무관하게 진행되어야 하므로
        // release는 silent skip + warn.
        enforcer.release(userId, 100);

        verify(userRepository, never()).save(any());
    }

    @Test
    void release_normalPath_callsReleaseStorageAndSaves() {
        User user = mock(User.class);
        when(user.releaseStorage(500L)).thenReturn(false); // not clamped
        when(userRepository.lockActiveById(userId)).thenReturn(Optional.of(user));

        enforcer.release(userId, 500);

        verify(user).releaseStorage(500L);
        verify(userRepository).save(user);
    }

    @Test
    void release_clampedPath_stillSavesAndLogsWarn() {
        // releaseStorage가 clamp(true) 반환 — 정상 흐름이지만 운영 로그만 추가.
        User user = mock(User.class);
        when(user.releaseStorage(9999L)).thenReturn(true);
        when(userRepository.lockActiveById(userId)).thenReturn(Optional.of(user));

        enforcer.release(userId, 9999);

        verify(user).releaseStorage(9999L);
        verify(userRepository).save(user);
    }
}
