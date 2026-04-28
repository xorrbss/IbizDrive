package com.ibizdrive.permission;

import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
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
 * A3.4 — {@link PermissionService#changeRole} 단위 테스트.
 *
 * <p>책임:
 * <ul>
 *   <li>대상 사용자 role 변경 + repository.save</li>
 *   <li>{@link RoleChangedEvent} publish (actorId/from/to)</li>
 *   <li>같은 role → no-op + event 미발행</li>
 *   <li>대상 user 미존재 → IllegalArgumentException</li>
 * </ul>
 *
 * <p>실제 audit row 기록은 {@link com.ibizdrive.audit.PermissionAuditListener}가 담당
 * (단위 테스트 분리). 본 테스트는 publish 행위만 검증.
 */
class PermissionServiceChangeRoleTest {

    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TARGET = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private UserRepository userRepository;
    private ApplicationEventPublisher publisher;
    private PermissionService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        service = new PermissionService(userRepository, publisher);
    }

    private User userWithRole(Role role) {
        return new User(
            TARGET, "t@example.com", "Target", "{bcrypt}$2a$12$dummy",
            role, true, false, OffsetDateTime.now()
        );
    }

    @Test
    void changesRole_savesUser_andPublishesEvent() {
        User user = userWithRole(Role.MEMBER);
        when(userRepository.findById(TARGET)).thenReturn(Optional.of(user));

        service.changeRole(TARGET, Role.AUDITOR, ACTOR);

        assertThat(user.getRole()).isEqualTo(Role.AUDITOR);
        verify(userRepository).save(user);

        ArgumentCaptor<RoleChangedEvent> captor = ArgumentCaptor.forClass(RoleChangedEvent.class);
        verify(publisher).publishEvent(captor.capture());
        RoleChangedEvent ev = captor.getValue();
        assertThat(ev.actorId()).isEqualTo(ACTOR);
        assertThat(ev.targetUserId()).isEqualTo(TARGET);
        assertThat(ev.from()).isEqualTo(Role.MEMBER);
        assertThat(ev.to()).isEqualTo(Role.AUDITOR);
    }

    @Test
    void sameRole_isNoop_noEvent() {
        User user = userWithRole(Role.ADMIN);
        when(userRepository.findById(TARGET)).thenReturn(Optional.of(user));

        service.changeRole(TARGET, Role.ADMIN, ACTOR);

        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        verify(userRepository, never()).save(any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void missingUser_throws() {
        when(userRepository.findById(TARGET)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeRole(TARGET, Role.AUDITOR, ACTOR))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("user not found");

        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void nullArguments_throw() {
        assertThatThrownBy(() -> service.changeRole(null, Role.AUDITOR, ACTOR))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.changeRole(TARGET, null, ACTOR))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
