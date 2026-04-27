package com.ibizdrive.audit;

import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.core.Authentication;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A2.4 RED: AuthAuditListener — 4종 이벤트가 audit_log INSERT를 트리거하고 actor/reason이 정확히 매핑.
 */
@ExtendWith(MockitoExtension.class)
class AuthAuditListenerTest {

    @Mock private AuditService auditService;
    @Mock private UserRepository userRepository;

    @InjectMocks private AuthAuditListener listener;

    private static User userOf(UUID id, String email) {
        return new User(id, email, "Test User", "{bcrypt}xxx", Role.MEMBER, true, false, OffsetDateTime.now());
    }

    @Test
    void success_records_user_login_success_with_actorId_from_principal() {
        UUID id = UUID.randomUUID();
        IbizDriveUserDetails uds = new IbizDriveUserDetails(userOf(id, "alice@example.com"));
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(uds, null, uds.getAuthorities());

        listener.onSuccess(new AuthenticationSuccessEvent(auth));

        ArgumentCaptor<AuditEvent> cap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(cap.capture());
        AuditEvent ev = cap.getValue();
        assertThat(ev.eventType()).isEqualTo(AuditEventType.USER_LOGIN_SUCCESS);
        assertThat(ev.actorId()).isEqualTo(id);
        assertThat(ev.targetType()).isEqualTo(AuditTargetType.USER);
        assertThat(ev.targetId()).isEqualTo(id);
    }

    @Test
    void bad_credentials_records_user_login_failed_with_reason_metadata() {
        UUID id = UUID.randomUUID();
        when(userRepository.findActiveByEmail("alice@example.com"))
            .thenReturn(Optional.of(userOf(id, "alice@example.com")));
        Authentication auth = UsernamePasswordAuthenticationToken.unauthenticated("alice@example.com", "");

        listener.onFailure(new AuthenticationFailureBadCredentialsEvent(
            auth, new BadCredentialsException("bad-password")));

        ArgumentCaptor<AuditEvent> cap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(cap.capture());
        AuditEvent ev = cap.getValue();
        assertThat(ev.eventType()).isEqualTo(AuditEventType.USER_LOGIN_FAILED);
        assertThat(ev.actorId()).isEqualTo(id);
        assertThat(ev.metadata()).contains("\"reason\":\"bad-password\"");
    }

    @Test
    void unknown_user_failure_records_with_null_actorId() {
        when(userRepository.findActiveByEmail("ghost@example.com")).thenReturn(Optional.empty());
        Authentication auth = UsernamePasswordAuthenticationToken.unauthenticated("ghost@example.com", "");

        listener.onFailure(new AuthenticationFailureBadCredentialsEvent(
            auth, new BadCredentialsException("user-not-found")));

        ArgumentCaptor<AuditEvent> cap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(cap.capture());
        AuditEvent ev = cap.getValue();
        assertThat(ev.eventType()).isEqualTo(AuditEventType.USER_LOGIN_FAILED);
        assertThat(ev.actorId()).isNull();
        assertThat(ev.metadata()).contains("\"reason\":\"user-not-found\"");
    }

    @Test
    void locked_failure_records_with_reason_locked() {
        when(userRepository.findActiveByEmail("alice@example.com")).thenReturn(Optional.empty());
        Authentication auth = UsernamePasswordAuthenticationToken.unauthenticated("alice@example.com", "");

        listener.onFailure(new AuthenticationFailureLockedEvent(auth, new LockedException("locked")));

        ArgumentCaptor<AuditEvent> cap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(cap.capture());
        AuditEvent ev = cap.getValue();
        assertThat(ev.eventType()).isEqualTo(AuditEventType.USER_LOGIN_FAILED);
        assertThat(ev.metadata()).contains("\"reason\":\"locked\"");
    }

    @Test
    void logout_records_user_logout_with_actorId_from_principal() {
        UUID id = UUID.randomUUID();
        IbizDriveUserDetails uds = new IbizDriveUserDetails(userOf(id, "alice@example.com"));
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(uds, null, uds.getAuthorities());

        listener.onLogout(new LogoutSuccessEvent(auth));

        ArgumentCaptor<AuditEvent> cap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(cap.capture());
        AuditEvent ev = cap.getValue();
        assertThat(ev.eventType()).isEqualTo(AuditEventType.USER_LOGOUT);
        assertThat(ev.actorId()).isEqualTo(id);
    }

}
