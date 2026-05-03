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
import org.springframework.security.crypto.password.PasswordEncoder;

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
}
