package com.ibizdrive.auth;

import com.ibizdrive.auth.dto.LoginResponse;
import com.ibizdrive.auth.dto.SignupRequest;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ADR #41 — {@link SignupService} 단위 테스트.
 *
 * <p>5건 RED → GREEN:
 * <ol>
 *   <li>정상 가입: 영속화 + LoginResponse 반환 + establishSession 호출</li>
 *   <li>중복 email → {@link DuplicateEmailException} (save/establishSession 미호출)</li>
 *   <li>첫 user (count=0) → {@link Role#ADMIN}</li>
 *   <li>두 번째부터 (count>0) → {@link Role#MEMBER}</li>
 *   <li>{@link UserRegisteredEvent} publish 검증</li>
 * </ol>
 *
 * <p>Bean Validation(약한 PW, 잘못된 email, blank displayName)은 controller 진입 시점에 적용되므로
 * {@link AuthControllerSignupTest}에서 검증한다 — 본 unit test는 service 행위에 집중.
 */
class SignupServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private AuthService authService;
    private ApplicationEventPublisher eventPublisher;
    private SignupService signupService;

    private HttpServletRequest httpReq;
    private HttpServletResponse httpRes;

    private static final LoginResponse STUB_RESPONSE = new LoginResponse(
        new LoginResponse.UserInfo("00000000-0000-0000-0000-000000000000", "x@example.com", "X", "human", false),
        List.of(),
        List.of("MEMBER"),
        "deadbeefdeadbeef"
    );

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        authService = mock(AuthService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        httpReq = mock(HttpServletRequest.class);
        httpRes = mock(HttpServletResponse.class);

        signupService = new SignupService(userRepository, passwordEncoder, authService, eventPublisher);

        // 기본 stub: hash, establishSession은 항상 STUB_RESPONSE 반환.
        when(passwordEncoder.encode(any())).thenReturn("{bcrypt}$2a$12$encodedhash");
        when(authService.establishSession(any(), any(), any())).thenReturn(STUB_RESPONSE);
    }

    @Test
    void signup_validRequest_persistsUserAndReturnsLoginResponse() {
        when(userRepository.findActiveByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.count()).thenReturn(5L);  // 신규 가입자 — 비-첫 user

        SignupRequest req = new SignupRequest("alice@example.com", "Sup3rSecret_Pw_12", "Alice");

        LoginResponse response = signupService.signup(req, httpReq, httpRes);

        assertThat(response).isSameAs(STUB_RESPONSE);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User u = saved.getValue();
        assertThat(u.getEmail()).isEqualTo("alice@example.com");
        assertThat(u.getDisplayName()).isEqualTo("Alice");
        assertThat(u.getPasswordHash()).isEqualTo("{bcrypt}$2a$12$encodedhash");
        assertThat(u.getRole()).isEqualTo(Role.MEMBER);
        assertThat(u.isActive()).isTrue();
        assertThat(u.isMustChangePassword()).isFalse();
        assertThat(u.getId()).isNotNull();

        verify(authService).establishSession(saved.getValue(), httpReq, httpRes);
    }

    @Test
    void signup_duplicateEmail_throwsAndDoesNotPersist() {
        User existing = new User(
            java.util.UUID.randomUUID(),
            "alice@example.com",
            "Alice",
            "{bcrypt}$2a$12$existinghash",
            Role.MEMBER,
            true,
            false,
            java.time.OffsetDateTime.now()
        );
        when(userRepository.findActiveByEmail("alice@example.com")).thenReturn(Optional.of(existing));

        SignupRequest req = new SignupRequest("alice@example.com", "Sup3rSecret_Pw_12", "Alice");

        assertThatThrownBy(() -> signupService.signup(req, httpReq, httpRes))
            .isInstanceOf(DuplicateEmailException.class);

        verify(userRepository, never()).save(any());
        verify(authService, never()).establishSession(any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void signup_firstUser_assignsAdminRole() {
        when(userRepository.findActiveByEmail("first@example.com")).thenReturn(Optional.empty());
        when(userRepository.count()).thenReturn(0L);

        SignupRequest req = new SignupRequest("first@example.com", "Sup3rSecret_Pw_12", "First");
        signupService.signup(req, httpReq, httpRes);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void signup_subsequentUser_assignsMemberRole() {
        when(userRepository.findActiveByEmail("second@example.com")).thenReturn(Optional.empty());
        when(userRepository.count()).thenReturn(1L);

        SignupRequest req = new SignupRequest("second@example.com", "Sup3rSecret_Pw_12", "Second");
        signupService.signup(req, httpReq, httpRes);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.MEMBER);
    }

    @Test
    void signup_publishesUserRegisteredEvent_withSavedUserId() {
        when(userRepository.findActiveByEmail("bob@example.com")).thenReturn(Optional.empty());
        when(userRepository.count()).thenReturn(2L);

        SignupRequest req = new SignupRequest("bob@example.com", "Sup3rSecret_Pw_12", "Bob");
        signupService.signup(req, httpReq, httpRes);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(event.capture());

        UserRegisteredEvent registered = event.getAllValues().stream()
            .filter(e -> e instanceof UserRegisteredEvent)
            .map(e -> (UserRegisteredEvent) e)
            .findFirst()
            .orElseThrow(() -> new AssertionError("UserRegisteredEvent not published"));

        assertThat(registered.userId()).isEqualTo(saved.getValue().getId());
        assertThat(registered.email()).isEqualTo("bob@example.com");
    }

    @Test
    void signup_emailLowercasedAndTrimmed() {
        when(userRepository.findActiveByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.count()).thenReturn(1L);

        SignupRequest req = new SignupRequest("  Alice@Example.COM  ", "Sup3rSecret_Pw_12", "  Alice  ");
        signupService.signup(req, httpReq, httpRes);

        verify(userRepository, times(1)).findActiveByEmail("alice@example.com");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getValue().getDisplayName()).isEqualTo("Alice");
    }
}
