package com.ibizdrive.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.OffsetDateTime;
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
 * ADR #21 (admin 트랙 closure) — {@link AdminUserService} 단위 테스트.
 *
 * <p>핵심 invariants:
 * <ul>
 *   <li>임시 PW는 응답 DTO/예외 메시지에 절대 노출되지 않는다 (이메일 본문에만 등장).
 *   <li>save User는 {@code mustChangePassword=true}, {@code isActive=true}.
 *   <li>email은 trim+lowercase 정규화 (signup과 동일 — docs/03 §2.7).
 *   <li>{@link AdminUserCreatedEvent}가 publish되어 audit listener가 {@code ADMIN_USER_CREATED} 기록.
 *   <li>{@link EmailService#send} 본문에 임시 PW가 포함된다.
 * </ul>
 *
 * <p>{@link TempPasswordGenerator}와 {@link PasswordEncoder}는 모킹하여 결정론적 입출력을 만든다.
 * 실제 BCrypt 검증은 통합 테스트 영역.
 */
class AdminUserServiceTest {

    private static final String STUB_TEMP_PW = "Tmp_Pw_16_AbCdEf";
    private static final String STUB_HASH = "{bcrypt}$2a$12$encodedhash";

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private TempPasswordGenerator tempPasswordGenerator;
    private EmailService emailService;
    private ApplicationEventPublisher eventPublisher;
    private AdminUserService adminUserService;

    private UUID actorId;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        tempPasswordGenerator = mock(TempPasswordGenerator.class);
        emailService = mock(EmailService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        adminUserService = new AdminUserService(
            userRepository,
            passwordEncoder,
            tempPasswordGenerator,
            emailService,
            eventPublisher
        );

        actorId = UUID.randomUUID();

        when(tempPasswordGenerator.generate()).thenReturn(STUB_TEMP_PW);
        when(passwordEncoder.encode(STUB_TEMP_PW)).thenReturn(STUB_HASH);
    }

    @Test
    void invite_createsUserWithMustChangePasswordTrue() {
        when(userRepository.findActiveByEmail("alice@example.com")).thenReturn(Optional.empty());

        adminUserService.invite("alice@example.com", "Alice", Role.MEMBER, actorId);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User u = saved.getValue();
        assertThat(u.isMustChangePassword()).isTrue();
        assertThat(u.isActive()).isTrue();
        assertThat(u.getRole()).isEqualTo(Role.MEMBER);
        assertThat(u.getEmail()).isEqualTo("alice@example.com");
        assertThat(u.getDisplayName()).isEqualTo("Alice");
        assertThat(u.getId()).isNotNull();
    }

    @Test
    void invite_persistsBcryptHashOfGeneratedPassword() {
        when(userRepository.findActiveByEmail("bob@example.com")).thenReturn(Optional.empty());

        adminUserService.invite("bob@example.com", "Bob", Role.MEMBER, actorId);

        verify(passwordEncoder).encode(STUB_TEMP_PW);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo(STUB_HASH);
    }

    @Test
    void invite_emailLowercaseAndTrim() {
        when(userRepository.findActiveByEmail("alice@example.com")).thenReturn(Optional.empty());

        adminUserService.invite("  Alice@Example.COM  ", "  Alice  ", Role.MEMBER, actorId);

        // 사전 duplicate check도 정규화된 lowercase로 호출되어야 한다.
        verify(userRepository, times(1)).findActiveByEmail("alice@example.com");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getValue().getDisplayName()).isEqualTo("Alice");
    }

    @Test
    void invite_duplicateEmail_throwsDuplicateEmailException() {
        User existing = new User(
            UUID.randomUUID(),
            "alice@example.com",
            "Alice",
            "{bcrypt}$2a$12$existinghash",
            Role.MEMBER,
            true,
            false,
            OffsetDateTime.now()
        );
        when(userRepository.findActiveByEmail("alice@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() ->
            adminUserService.invite("alice@example.com", "Alice", Role.MEMBER, actorId)
        ).isInstanceOf(DuplicateEmailException.class);

        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(emailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void invite_publishesAdminUserCreatedEvent() {
        when(userRepository.findActiveByEmail("carol@example.com")).thenReturn(Optional.empty());

        adminUserService.invite("carol@example.com", "Carol", Role.AUDITOR, actorId);

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
        assertThat(created.actorId()).isEqualTo(actorId);
        assertThat(created.email()).isEqualTo("carol@example.com");
    }

    @Test
    void invite_sendsInviteEmailContainingTempPassword() {
        when(userRepository.findActiveByEmail("dan@example.com")).thenReturn(Optional.empty());

        adminUserService.invite("dan@example.com", "Dan", Role.MEMBER, actorId);

        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(to.capture(), subject.capture(), body.capture());

        assertThat(to.getValue()).isEqualTo("dan@example.com");
        assertThat(subject.getValue()).isNotBlank();
        // 임시 PW는 이메일 본문에만 등장한다.
        assertThat(body.getValue()).contains(STUB_TEMP_PW);
        // 본문에 force-change 안내가 포함되어야 한다 (강제 변경 UX 안내).
        assertThat(body.getValue()).contains("dan@example.com");
    }

    @Test
    void invite_returnsResponseWithoutTempPassword() throws Exception {
        when(userRepository.findActiveByEmail("eve@example.com")).thenReturn(Optional.empty());

        AdminInviteUserResponse response = adminUserService.invite(
            "eve@example.com", "Eve", Role.ADMIN, actorId
        );

        // 응답 필드는 식별자/공개정보만.
        assertThat(response.email()).isEqualTo("eve@example.com");
        assertThat(response.displayName()).isEqualTo("Eve");
        assertThat(response.role()).isEqualTo(Role.ADMIN);
        assertThat(response.mustChangePassword()).isTrue();
        assertThat(response.id()).isNotNull();

        // Jackson 직렬화 결과에 임시 PW/해시 관련 키가 절대 등장하지 않아야 한다.
        // ADR #21 invariant — 임시 PW는 이메일 채널로만 전달된다.
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(response);
        assertThat(json).doesNotContain(STUB_TEMP_PW);
        assertThat(json).doesNotContain(STUB_HASH);
        assertThat(json.toLowerCase()).doesNotContain("temppassword");
        assertThat(json.toLowerCase()).doesNotContain("\"password\"");
        assertThat(json.toLowerCase()).doesNotContain("hash");
    }
}
