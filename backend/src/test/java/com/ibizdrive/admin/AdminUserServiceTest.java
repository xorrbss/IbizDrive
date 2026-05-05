package com.ibizdrive.admin;

import com.ibizdrive.auth.DuplicateEmailException;
import com.ibizdrive.email.EmailService;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdminUserService} 단위 테스트 — m-admin-entry-rewrite P5.
 *
 * <p>SignupServiceTest 패턴 mirror — repo/encoder/publisher/email mock + ArgumentCaptor.
 * 본 service는 SignupService와 달리 (a) mustChangePassword=true, (b) 임시 PW 자동 생성 +
 * email 발송, (c) ADMIN 액터 actor id를 event에 포함, 이 3가지 차별점이 있다.
 */
class AdminUserServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private ApplicationEventPublisher eventPublisher;
    private EmailService emailService;
    private TempPasswordGenerator tempPasswordGenerator;
    private AdminUserService adminUserService;

    private static final UUID ACTOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        emailService = mock(EmailService.class);
        tempPasswordGenerator = mock(TempPasswordGenerator.class);

        adminUserService = new AdminUserService(
            userRepository, passwordEncoder, eventPublisher, emailService, tempPasswordGenerator
        );

        when(passwordEncoder.encode(any())).thenReturn("{bcrypt}$2a$12$adminencoded");
        when(tempPasswordGenerator.generate()).thenReturn("Tmp_aBcD3F4G5H6J");
    }

    @Test
    void invite_createsUserWithMustChangePasswordTrue() {
        when(userRepository.findActiveByEmail("bob@example.com")).thenReturn(Optional.empty());

        adminUserService.invite("bob@example.com", "Bob", Role.MEMBER, ACTOR_ID);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User u = saved.getValue();
        assertThat(u.getEmail()).isEqualTo("bob@example.com");
        assertThat(u.getDisplayName()).isEqualTo("Bob");
        assertThat(u.getRole()).isEqualTo(Role.MEMBER);
        assertThat(u.isActive()).isTrue();
        assertThat(u.isMustChangePassword()).isTrue();
    }

    @Test
    void invite_persistsBcryptHashOfGeneratedPassword() {
        when(userRepository.findActiveByEmail(anyString())).thenReturn(Optional.empty());

        adminUserService.invite("carol@example.com", "Carol", Role.MEMBER, ACTOR_ID);

        // 인코더에 전달된 raw PW가 generator 결과와 동일해야 함.
        verify(passwordEncoder).encode("Tmp_aBcD3F4G5H6J");
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("{bcrypt}$2a$12$adminencoded");
    }

    @Test
    void invite_emailLowercaseAndTrim() {
        when(userRepository.findActiveByEmail("dave@example.com")).thenReturn(Optional.empty());

        adminUserService.invite("  Dave@Example.COM  ", "  Dave  ", Role.AUDITOR, ACTOR_ID);

        verify(userRepository, times(1)).findActiveByEmail("dave@example.com");
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("dave@example.com");
        assertThat(saved.getValue().getDisplayName()).isEqualTo("Dave");
    }

    @Test
    void invite_duplicateEmail_throws409AndDoesNotPersistOrSend() {
        User existing = new User(
            UUID.randomUUID(), "eve@example.com", "Eve",
            "{bcrypt}$2a$12$existing", Role.MEMBER, true, false,
            java.time.OffsetDateTime.now()
        );
        when(userRepository.findActiveByEmail("eve@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> adminUserService.invite("eve@example.com", "Eve", Role.MEMBER, ACTOR_ID))
            .isInstanceOf(DuplicateEmailException.class);

        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(emailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void invite_publishesAdminUserCreatedEvent_withSavedUserIdAndActor() {
        when(userRepository.findActiveByEmail("frank@example.com")).thenReturn(Optional.empty());

        adminUserService.invite("frank@example.com", "Frank", Role.MEMBER, ACTOR_ID);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(event.capture());

        AdminUserCreatedEvent created = event.getAllValues().stream()
            .filter(e -> e instanceof AdminUserCreatedEvent)
            .map(e -> (AdminUserCreatedEvent) e)
            .findFirst()
            .orElseThrow(() -> new AssertionError("AdminUserCreatedEvent not published"));

        assertThat(created.userId()).isEqualTo(saved.getValue().getId());
        assertThat(created.actorId()).isEqualTo(ACTOR_ID);
        assertThat(created.email()).isEqualTo("frank@example.com");
    }

    @Test
    void invite_sendsInviteEmail_withTempPasswordInBody() {
        when(userRepository.findActiveByEmail("grace@example.com")).thenReturn(Optional.empty());

        adminUserService.invite("grace@example.com", "Grace", Role.MEMBER, ACTOR_ID);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(eq("grace@example.com"), anyString(), body.capture());
        assertThat(body.getValue())
            .as("초대 이메일 본문에 임시 비밀번호 포함되어야 함")
            .contains("Tmp_aBcD3F4G5H6J");
    }

    // ── admin-user-mgmt: list / changeRole / deactivate ─────────────────

    @Test
    void list_delegatesToRepository_withPageable() {
        Pageable pageable = PageRequest.of(0, 20);
        User user1 = activeUser(UUID.randomUUID(), "u1@example.com", Role.MEMBER, true);
        User user2 = activeUser(UUID.randomUUID(), "u2@example.com", Role.AUDITOR, true);
        Page<User> page = new PageImpl<>(List.of(user1, user2), pageable, 2);
        when(userRepository.findAllActivePageable(pageable)).thenReturn(page);

        Page<User> result = adminUserService.list(pageable);

        assertThat(result.getContent()).containsExactly(user1, user2);
        verify(userRepository).findAllActivePageable(pageable);
    }

    @Test
    void changeRole_otherUser_publishesEvent_andSaves() {
        UUID targetId = UUID.randomUUID();
        User target = activeUser(targetId, "target@example.com", Role.MEMBER, true);
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));

        adminUserService.changeRole(targetId, Role.AUDITOR, ACTOR_ID);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.AUDITOR);

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(event.capture());
        AdminRoleChangedEvent published = event.getAllValues().stream()
            .filter(e -> e instanceof AdminRoleChangedEvent)
            .map(e -> (AdminRoleChangedEvent) e)
            .findFirst()
            .orElseThrow(() -> new AssertionError("AdminRoleChangedEvent not published"));
        assertThat(published.userId()).isEqualTo(targetId);
        assertThat(published.actorId()).isEqualTo(ACTOR_ID);
        assertThat(published.oldRole()).isEqualTo(Role.MEMBER);
        assertThat(published.newRole()).isEqualTo(Role.AUDITOR);
    }

    @Test
    void changeRole_selfDemoteFromAdmin_throwsSelfProtection() {
        // actor 본인을 ADMIN→AUDITOR — 차단되어야 함.
        User self = activeUser(ACTOR_ID, "admin@example.com", Role.ADMIN, true);
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(self));

        assertThatThrownBy(() -> adminUserService.changeRole(ACTOR_ID, Role.AUDITOR, ACTOR_ID))
            .isInstanceOf(AdminSelfProtectionException.class)
            .hasMessageContaining("self-demote");

        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(AdminRoleChangedEvent.class));
    }

    @Test
    void changeRole_selfToAdmin_isNoOp_whenAlreadyAdmin() {
        // 본인이 본인을 ADMIN→ADMIN — 멱등 (no-op + event 미발행).
        User self = activeUser(ACTOR_ID, "admin@example.com", Role.ADMIN, true);
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(self));

        User result = adminUserService.changeRole(ACTOR_ID, Role.ADMIN, ACTOR_ID);

        assertThat(result.getRole()).isEqualTo(Role.ADMIN);
        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(AdminRoleChangedEvent.class));
    }

    @Test
    void changeRole_targetNotFound_throwsAdminUserNotFound() {
        UUID ghost = UUID.randomUUID();
        when(userRepository.findById(ghost)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.changeRole(ghost, Role.MEMBER, ACTOR_ID))
            .isInstanceOf(AdminUserNotFoundException.class);

        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(AdminRoleChangedEvent.class));
    }

    @Test
    void changeRole_sameRole_isNoOp() {
        UUID targetId = UUID.randomUUID();
        User target = activeUser(targetId, "target@example.com", Role.MEMBER, true);
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));

        adminUserService.changeRole(targetId, Role.MEMBER, ACTOR_ID);

        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(AdminRoleChangedEvent.class));
    }

    @Test
    void deactivate_otherUser_publishesEvent_andSaves() {
        UUID targetId = UUID.randomUUID();
        User target = activeUser(targetId, "target@example.com", Role.MEMBER, true);
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));

        adminUserService.deactivate(targetId, ACTOR_ID);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().isActive()).isFalse();

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(event.capture());
        AdminUserDeactivatedEvent published = event.getAllValues().stream()
            .filter(e -> e instanceof AdminUserDeactivatedEvent)
            .map(e -> (AdminUserDeactivatedEvent) e)
            .findFirst()
            .orElseThrow(() -> new AssertionError("AdminUserDeactivatedEvent not published"));
        assertThat(published.userId()).isEqualTo(targetId);
        assertThat(published.actorId()).isEqualTo(ACTOR_ID);
    }

    @Test
    void deactivate_self_throwsSelfProtection() {
        User self = activeUser(ACTOR_ID, "admin@example.com", Role.ADMIN, true);
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(self));

        assertThatThrownBy(() -> adminUserService.deactivate(ACTOR_ID, ACTOR_ID))
            .isInstanceOf(AdminSelfProtectionException.class)
            .hasMessageContaining("self-deactivate");

        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(AdminUserDeactivatedEvent.class));
    }

    @Test
    void deactivate_alreadyInactive_isNoOp() {
        UUID targetId = UUID.randomUUID();
        User target = activeUser(targetId, "target@example.com", Role.MEMBER, false);
        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));

        adminUserService.deactivate(targetId, ACTOR_ID);

        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(AdminUserDeactivatedEvent.class));
    }

    @Test
    void deactivate_targetNotFound_throwsAdminUserNotFound() {
        UUID ghost = UUID.randomUUID();
        when(userRepository.findById(ghost)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.deactivate(ghost, ACTOR_ID))
            .isInstanceOf(AdminUserNotFoundException.class);

        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(AdminUserDeactivatedEvent.class));
    }

    private static User activeUser(UUID id, String email, Role role, boolean isActive) {
        return new User(
            id,
            email,
            "Display " + email,
            "{bcrypt}$2a$12$dummyhashfortestonlydummyhashfortestonlydummyhashfortest",
            role,
            isActive,
            false,
            OffsetDateTime.now()
        );
    }
}
