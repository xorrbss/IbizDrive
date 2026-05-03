package com.ibizdrive.auth.password;

import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.auth.InvalidCredentialsException;
import com.ibizdrive.email.EmailDeliveryException;
import com.ibizdrive.email.EmailService;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private PasswordEncoder passwordEncoder;
    @SuppressWarnings("rawtypes")
    private FindByIndexNameSessionRepository sessionRepository;
    private Clock clock;
    private PasswordResetService service;

    private static final OffsetDateTime FIXED_NOW =
        OffsetDateTime.of(2026, 5, 2, 12, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userRepository = mock(UserRepository.class);
        tokenRepository = mock(PasswordResetTokenRepository.class);
        emailService = mock(EmailService.class);
        auditService = mock(AuditService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        sessionRepository = mock(FindByIndexNameSessionRepository.class);
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "{bcrypt}$2a$12$" + inv.getArgument(0));
        when(sessionRepository.findByPrincipalName(anyString())).thenReturn(Map.of());
        clock = Clock.fixed(Instant.parse("2026-05-02T12:00:00Z"), ZoneOffset.UTC);
        service = new PasswordResetService(
            userRepository, tokenRepository, emailService, auditService,
            passwordEncoder, sessionRepository,
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

    // ─────────────────────────────────────────────────────────────────────
    // P4 reset() — 토큰 검증 + PW 갱신 + 세션 invalidate
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void reset_validToken_updatesPasswordAndInvalidatesSessions() {
        User user = sampleUser("alice@example.com", "Alice");
        String plain = "abcd1234efgh5678ijkl9012mnop3456qrst7890uvwx1234yz567890123456ab";
        String hash = PasswordResetService.sha256Hex(plain);
        PasswordResetToken token = new PasswordResetToken(user.getId(), hash,
            FIXED_NOW.plusMinutes(15), FIXED_NOW.minusMinutes(15));

        when(tokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        // 두 개의 활성 세션 — 모두 삭제되어야 함
        MapSession s1 = new MapSession();
        MapSession s2 = new MapSession();
        when(sessionRepository.findByPrincipalName("alice@example.com"))
            .thenReturn(Map.of(s1.getId(), s1, s2.getId(), s2));

        service.reset(plain, "NewSecret123!");

        // PW 갱신
        assertThat(user.getPasswordHash()).startsWith("{bcrypt}$2a$12$NewSecret123!");
        verify(userRepository).save(user);
        // token used_at 마킹
        assertThat(token.isUsed()).isTrue();
        verify(tokenRepository).save(token);
        // 모든 세션 invalidate
        verify(sessionRepository).deleteById(s1.getId());
        verify(sessionRepository).deleteById(s2.getId());
        // audit
        ArgumentCaptor<AuditEvent> auditCap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(auditCap.capture());
        assertThat(auditCap.getValue().eventType()).isEqualTo(AuditEventType.USER_PASSWORD_RESET);
    }

    @Test
    void reset_unknownToken_throwsInvalidToken() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reset("nonexistent", "NewSecret123!"))
            .isInstanceOf(InvalidPasswordResetTokenException.class);

        verifyNoInteractions(userRepository);
        verifyNoInteractions(auditService);
    }

    @Test
    void reset_expiredToken_throwsInvalidToken() {
        UUID userId = UUID.randomUUID();
        String plain = "expiredtoken";
        String hash = PasswordResetService.sha256Hex(plain);
        // 31분 전 발급, 1분 전 만료
        PasswordResetToken token = new PasswordResetToken(userId, hash,
            FIXED_NOW.minusMinutes(1), FIXED_NOW.minusMinutes(31));
        when(tokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.reset(plain, "NewSecret123!"))
            .isInstanceOf(InvalidPasswordResetTokenException.class);

        verifyNoInteractions(userRepository);
    }

    @Test
    void reset_alreadyUsedToken_throwsInvalidToken() {
        UUID userId = UUID.randomUUID();
        String plain = "usedtoken";
        String hash = PasswordResetService.sha256Hex(plain);
        PasswordResetToken token = new PasswordResetToken(userId, hash,
            FIXED_NOW.plusMinutes(15), FIXED_NOW.minusMinutes(5));
        token.markUsed(FIXED_NOW.minusMinutes(1));
        when(tokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.reset(plain, "NewSecret123!"))
            .isInstanceOf(InvalidPasswordResetTokenException.class);

        verifyNoInteractions(userRepository);
    }

    // ─────────────────────────────────────────────────────────────────────
    // P5 change() — 인증 사용자 PW 변경 + 다른 세션만 invalidate
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void change_validCurrentPassword_updatesAndInvalidatesOtherSessions() {
        User user = sampleUser("alice@example.com", "Alice");
        when(passwordEncoder.matches(eq("CurrentSecret123!"), eq(user.getPasswordHash())))
            .thenReturn(true);

        // 3개 세션 — 그중 "current-session-id" 만 보존되어야 함
        MapSession sCurrent = new MapSession();
        // MapSession ID는 자동 생성 — 우리 시나리오에 맞게 알려진 값으로 변경
        String currentId = "current-session-id";
        MapSession s1 = new MapSession();
        MapSession s2 = new MapSession();
        when(sessionRepository.findByPrincipalName("alice@example.com"))
            .thenReturn(Map.of(currentId, sCurrent, s1.getId(), s1, s2.getId(), s2));

        service.change(user, "CurrentSecret123!", "NewSecret456!", currentId);

        // PW 갱신
        assertThat(user.getPasswordHash()).startsWith("{bcrypt}$2a$12$NewSecret456!");
        verify(userRepository).save(user);
        // 현재 세션 보존, 다른 세션 삭제
        verify(sessionRepository, never()).deleteById(currentId);
        verify(sessionRepository).deleteById(s1.getId());
        verify(sessionRepository).deleteById(s2.getId());
        // audit
        ArgumentCaptor<AuditEvent> auditCap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(auditCap.capture());
        assertThat(auditCap.getValue().eventType()).isEqualTo(AuditEventType.USER_PASSWORD_CHANGED);
        assertThat(auditCap.getValue().actorId()).isEqualTo(user.getId());
    }

    @Test
    void change_wrongCurrentPassword_throwsInvalidCredentials() {
        User user = sampleUser("alice@example.com", "Alice");
        when(passwordEncoder.matches(eq("WrongPassword!"), eq(user.getPasswordHash())))
            .thenReturn(false);

        assertThatThrownBy(() ->
            service.change(user, "WrongPassword!", "NewSecret456!", "session-id"))
            .isInstanceOf(InvalidCredentialsException.class);

        // PW 미변경, save·session·audit 모두 미호출
        assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}$2a$12$dummy");
        verifyNoInteractions(userRepository);
        verifyNoInteractions(auditService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void change_nullCurrentSessionId_invalidatesAllSessions() {
        // 호출자(controller)가 session 없는 환경에서 null 전달 — 모든 세션 invalidate (보수적 fallback).
        User user = sampleUser("alice@example.com", "Alice");
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        MapSession s1 = new MapSession();
        MapSession s2 = new MapSession();
        when(sessionRepository.findByPrincipalName("alice@example.com"))
            .thenReturn(Map.of(s1.getId(), s1, s2.getId(), s2));

        service.change(user, "CurrentSecret123!", "NewSecret456!", null);

        verify(sessionRepository).deleteById(s1.getId());
        verify(sessionRepository).deleteById(s2.getId());
    }

    // ─────────────────────────────────────────────────────────────────────
    // auth-must-change-pw — change()/reset()이 mustChangePassword 플래그를 클리어 (ADR #21)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void change_clearsMustChangePasswordFlag() {
        // Admin invite 또는 첫 사용자 강제 변경 시나리오 — flag=true로 시작.
        // 변경 성공 후 false로 클리어되어야 무한 redirect 회피 + ADR #21 닫힘.
        User user = sampleUserWithMustChange("alice@example.com", "Alice");
        assertThat(user.isMustChangePassword()).isTrue();
        when(passwordEncoder.matches(eq("TempSecret123!"), eq(user.getPasswordHash())))
            .thenReturn(true);
        when(sessionRepository.findByPrincipalName("alice@example.com"))
            .thenReturn(Map.of());

        service.change(user, "TempSecret123!", "NewSecret456!", "session-id");

        assertThat(user.isMustChangePassword()).isFalse();
        verify(userRepository).save(user);
    }

    @Test
    @SuppressWarnings("unchecked")
    void reset_clearsMustChangePasswordFlag() {
        // Admin이 임시 PW 발급(mustChangePassword=true) + reset link 동시 제공한 케이스.
        // reset 흐름으로 PW 설정 시에도 플래그 클리어되어야 ADR #21 §2.8과 일치.
        User user = sampleUserWithMustChange("alice@example.com", "Alice");
        assertThat(user.isMustChangePassword()).isTrue();
        String plain = "abcd1234efgh5678ijkl9012mnop3456qrst7890uvwx1234yz567890123456ab";
        String hash = PasswordResetService.sha256Hex(plain);
        PasswordResetToken token = new PasswordResetToken(user.getId(), hash,
            FIXED_NOW.plusMinutes(15), FIXED_NOW.minusMinutes(15));
        when(tokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(sessionRepository.findByPrincipalName("alice@example.com"))
            .thenReturn(Map.of());

        service.reset(plain, "NewSecret123!");

        assertThat(user.isMustChangePassword()).isFalse();
        verify(userRepository).save(user);
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

    private static User sampleUserWithMustChange(String email, String displayName) {
        return new User(
            UUID.randomUUID(),
            email,
            displayName,
            "{bcrypt}$2a$12$dummy",
            Role.MEMBER,
            true,
            true,
            FIXED_NOW
        );
    }
}
