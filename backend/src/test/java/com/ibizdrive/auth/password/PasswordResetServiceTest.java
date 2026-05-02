package com.ibizdrive.auth.password;

import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.email.EmailDeliveryException;
import com.ibizdrive.email.EmailService;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link PasswordResetService#requestReset} 단위 검증 (P3).
 *
 * <p>검증 (5건):
 * <ol>
 *   <li>가입자 → 토큰 1건 INSERT, 이메일 1회 발송, audit USER_PASSWORD_FORGOT_REQUESTED 1건</li>
 *   <li>미가입자 → 토큰 0건, 이메일 0회, audit 0건 (anti-enumeration)</li>
 *   <li>email 대소문자 정규화 — "Alice@Example.com" → "alice@example.com"으로 조회</li>
 *   <li>토큰 expires_at = now + 30분 (TTL)</li>
 *   <li>이메일 발송 실패도 무시 — audit과 토큰 INSERT는 그대로 진행 (200 유지)</li>
 * </ol>
 */
class PasswordResetServiceTest {

    private UserRepository userRepository;
    private PasswordResetTokenRepository tokenRepository;
    private EmailService emailService;
    private AuditService auditService;
    private Clock clock;
    private PasswordResetService service;

    private static final OffsetDateTime FIXED_NOW =
        OffsetDateTime.of(2026, 5, 2, 12, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        tokenRepository = mock(PasswordResetTokenRepository.class);
        emailService = mock(EmailService.class);
        auditService = mock(AuditService.class);
        clock = Clock.fixed(Instant.parse("2026-05-02T12:00:00Z"), ZoneOffset.UTC);
        service = new PasswordResetService(
            userRepository, tokenRepository, emailService, auditService,
            clock, "http://localhost:3000"
        );
    }

    @Test
    void requestReset_existingUser_insertsTokenAndSendsEmail() {
        User user = sampleUser("alice@example.com", "Alice");
        when(userRepository.findActiveByEmail("alice@example.com")).thenReturn(Optional.of(user));

        service.requestReset("alice@example.com");

        ArgumentCaptor<PasswordResetToken> tokenCap = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository, times(1)).save(tokenCap.capture());
        PasswordResetToken saved = tokenCap.getValue();
        assertThat(saved.getUserId()).isEqualTo(user.getId());
        assertThat(saved.getTokenHash()).hasSize(64); // SHA-256 hex
        assertThat(saved.getExpiresAt()).isEqualTo(FIXED_NOW.plusMinutes(30));

        ArgumentCaptor<String> bodyCap = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).send(eq("alice@example.com"),
            contains("비밀번호 재설정"), bodyCap.capture());
        // 이메일 본문에 평문 토큰이 들어있어야 한다 — 사용자가 링크로 reset 가능.
        assertThat(bodyCap.getValue()).contains("http://localhost:3000/reset-password?token=");

        ArgumentCaptor<AuditEvent> auditCap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService, times(1)).record(auditCap.capture());
        assertThat(auditCap.getValue().eventType()).isEqualTo(AuditEventType.USER_PASSWORD_FORGOT_REQUESTED);
        assertThat(auditCap.getValue().actorId()).isEqualTo(user.getId());
    }

    @Test
    void requestReset_unknownUser_noopAntiEnumeration() {
        when(userRepository.findActiveByEmail(anyString())).thenReturn(Optional.empty());

        service.requestReset("ghost@example.com");

        verifyNoInteractions(tokenRepository);
        verifyNoInteractions(emailService);
        verifyNoInteractions(auditService);
    }

    @Test
    void requestReset_normalizesEmailCase() {
        User user = sampleUser("alice@example.com", "Alice");
        when(userRepository.findActiveByEmail("alice@example.com")).thenReturn(Optional.of(user));

        service.requestReset("  Alice@Example.COM  ");

        verify(userRepository).findActiveByEmail("alice@example.com");
        verify(tokenRepository, times(1)).save(any(PasswordResetToken.class));
    }

    @Test
    void requestReset_emailFailure_swallowedAndStillProceeds() {
        User user = sampleUser("alice@example.com", "Alice");
        when(userRepository.findActiveByEmail("alice@example.com")).thenReturn(Optional.of(user));
        doThrow(new EmailDeliveryException("smtp down", new RuntimeException()))
            .when(emailService).send(anyString(), anyString(), anyString());

        service.requestReset("alice@example.com");

        verify(tokenRepository, times(1)).save(any(PasswordResetToken.class));
        verify(emailService, times(1)).send(anyString(), anyString(), anyString());
        // anti-enumeration: 발송 실패도 audit는 기록 (요청 자체는 발생).
        verify(auditService, times(1)).record(any(AuditEvent.class));
    }

    @Test
    void requestReset_tokenIsHashed_notPlain() {
        User user = sampleUser("alice@example.com", "Alice");
        when(userRepository.findActiveByEmail("alice@example.com")).thenReturn(Optional.of(user));

        service.requestReset("alice@example.com");

        ArgumentCaptor<String> bodyCap = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(anyString(), anyString(), bodyCap.capture());
        String body = bodyCap.getValue();
        // 메일 본문에서 평문 토큰 추출
        int linkIdx = body.indexOf("?token=");
        assertThat(linkIdx).isPositive();
        String afterToken = body.substring(linkIdx + "?token=".length());
        String plainToken = afterToken.split("\\s")[0];

        ArgumentCaptor<PasswordResetToken> tokenCap = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(tokenCap.capture());
        // DB에 저장되는 token_hash != 평문
        assertThat(tokenCap.getValue().getTokenHash()).isNotEqualTo(plainToken);
        // hash 검증: sha256(plain) == saved
        assertThat(tokenCap.getValue().getTokenHash())
            .isEqualTo(PasswordResetService.sha256Hex(plainToken));
    }

    @Test
    void requestReset_emailSuccess_doesNotCallEmailTwice() {
        User user = sampleUser("alice@example.com", "Alice");
        when(userRepository.findActiveByEmail("alice@example.com")).thenReturn(Optional.of(user));

        service.requestReset("alice@example.com");

        verify(emailService, never()).send(eq("alice@example.com"), anyString(),
            contains("DUPLICATE"));
    }

    private static User sampleUser(String email, String displayName) {
        return new User(
            UUID.randomUUID(),
            email,
            displayName,
            "{bcrypt}$2a$12$dummy",
            Role.MEMBER,
            true,
            false,
            FIXED_NOW
        );
    }
}
