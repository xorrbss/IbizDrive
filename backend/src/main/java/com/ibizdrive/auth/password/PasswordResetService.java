package com.ibizdrive.auth.password;

import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import com.ibizdrive.audit.WebRequestContextHolder;
import com.ibizdrive.auth.InvalidCredentialsException;
import com.ibizdrive.email.EmailDeliveryException;
import com.ibizdrive.email.EmailService;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * 비밀번호 재설정 비즈 로직 (a1.5).
 *
 * <p>{@code requestReset(email)}:
 * <ol>
 *   <li>email trim + lowercase 정규화 (login/signup과 동일).</li>
 *   <li>{@link UserRepository#findActiveByEmail}로 활성 사용자 조회.</li>
 *   <li>가입자: 평문 토큰 생성(UUID 2개 join, 64자) → SHA-256 해시 저장 → 이메일 발송.</li>
 *   <li>미가입자: no-op. 응답은 호출자(controller)가 동일 200으로 반환 (anti-enumeration).</li>
 * </ol>
 *
 * <p>TTL은 {@link #TOKEN_TTL} (30분). 사용자별 다중 토큰 허용 — 마지막에 발급된 토큰만 유효한 것은 아니며
 * 만료 전 모든 active 토큰이 사용 가능. rate-limit은 본 트랙 범위 외 (별도 트랙에서 추가).
 *
 * <p>이메일 발송 실패는 {@link EmailDeliveryException}로 표면화되지만, 호출자(controller)는 200을
 * 유지하기 위해 swallow + audit로 기록 — anti-enumeration 일관 응답을 우선한다.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final Clock clock;
    private final String appUrl;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                EmailService emailService,
                                AuditService auditService,
                                PasswordEncoder passwordEncoder,
                                FindByIndexNameSessionRepository<? extends Session> sessionRepository,
                                Clock clock,
                                @Value("${app.app-url}") String appUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
        this.sessionRepository = sessionRepository;
        this.clock = clock;
        this.appUrl = appUrl;
    }

    /**
     * 가입자라면 토큰 생성 + 이메일 발송 + audit 기록. 미가입자는 no-op.
     * 호출자는 결과 무관 200 응답 (anti-enumeration).
     */
    @Transactional
    public void requestReset(String emailRaw) {
        String email = normalize(emailRaw);
        Optional<User> opt = userRepository.findActiveByEmail(email);
        if (opt.isEmpty()) {
            // anti-enumeration — 미가입자는 토큰/이메일/audit 모두 발생시키지 않는다.
            // timing leak은 본 트랙 범위 외 (ADR에 한계 명시).
            return;
        }

        User user = opt.get();
        String plainToken = generatePlainToken();
        String tokenHash = sha256Hex(plainToken);
        OffsetDateTime now = OffsetDateTime.now(clock);

        tokenRepository.save(new PasswordResetToken(
            user.getId(),
            tokenHash,
            now.plus(TOKEN_TTL),
            now
        ));

        String subject = "[IbizDrive] 비밀번호 재설정 요청";
        String link = appUrl + "/reset-password?token=" + plainToken;
        String body = """
            안녕하세요 %s님,
            비밀번호 재설정을 요청하셨습니다. 아래 링크에서 30분 내에 새 비밀번호를 설정하세요.

            %s

            본인이 요청하지 않으셨다면 본 메일을 무시하셔도 됩니다.
            """.formatted(user.getDisplayName(), link);

        try {
            emailService.send(user.getEmail(), subject, body);
        } catch (EmailDeliveryException e) {
            // 발송 실패도 응답은 200 유지 — anti-enumeration. 운영자가 ERROR 로그로 인지.
            log.error("password reset email send failed userId={} email={}",
                user.getId(), user.getEmail(), e);
        }

        auditService.record(new AuditEvent(
            AuditEventType.USER_PASSWORD_FORGOT_REQUESTED,
            user.getId(),
            WebRequestContextHolder.currentIp(),
            WebRequestContextHolder.currentUserAgent(),
            AuditTargetType.USER,
            user.getId(),
            null,
            null,
            null
        ));
    }

    /**
     * 토큰 검증 후 비밀번호 갱신 + 모든 세션 invalidate + 토큰 used_at 마킹.
     *
     * <p>토큰 검증 실패(미존재/만료/사용됨) 시 {@link InvalidPasswordResetTokenException} 발생,
     * 호출 측 controller가 400 INVALID_TOKEN으로 매핑. 사유는 응답에 노출되지 않는다 — 토큰 enumeration 방지.
     *
     * <p>비밀번호 정책 검증은 DTO의 {@link jakarta.validation.constraints.Size}가 담당하므로 본 메서드는
     * 정책 위반 분기 없음. 정책 강화 트랙에서 별도 validator 분리 검토.
     *
     * <p>같은 사용자의 모든 Spring Session 항목을 삭제 — 비밀번호 변경이 모든 기존 세션을 무효화하는
     * 보안 정책. {@link #change}는 현재 세션만 유지하고 나머지를 invalidate (P5).
     */
    @Transactional
    public void reset(String plainToken, String newPassword) {
        String tokenHash = sha256Hex(plainToken);
        PasswordResetToken token = tokenRepository.findByTokenHash(tokenHash)
            .orElseThrow(() -> new InvalidPasswordResetTokenException("not-found"));

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (token.isUsed()) {
            throw new InvalidPasswordResetTokenException("used");
        }
        if (token.isExpired(now)) {
            throw new InvalidPasswordResetTokenException("expired");
        }

        User user = userRepository.findById(token.getUserId())
            .orElseThrow(() -> new InvalidPasswordResetTokenException("user-missing"));

        user.changePasswordHash(passwordEncoder.encode(newPassword));
        // ADR #21 — admin이 임시 PW + reset link를 함께 발급한 케이스에 mustChangePassword 클리어.
        // 자발적 reset(원래 false)에는 idempotent.
        user.clearMustChangePassword();
        userRepository.save(user);

        token.markUsed(now);
        tokenRepository.save(token);

        invalidateAllSessions(user.getEmail(), null);

        auditService.record(new AuditEvent(
            AuditEventType.USER_PASSWORD_RESET,
            user.getId(),
            WebRequestContextHolder.currentIp(),
            WebRequestContextHolder.currentUserAgent(),
            AuditTargetType.USER,
            user.getId(),
            null,
            null,
            null
        ));
    }

    /**
     * 인증된 사용자의 비밀번호 변경 (P5).
     *
     * <p>현재 비밀번호 BCrypt 검증 → 새 비밀번호로 갱신 → 다른 모든 세션 invalidate.
     * 현재 세션({@code currentSessionId})은 보존하여 사용자가 강제 로그아웃되지 않는다.
     *
     * <p>currentPassword 미일치 시 {@link InvalidCredentialsException} → 401
     * (login 실패와 동일 코드 — enumeration/UX 일관성).
     *
     * <p>비밀번호 정책 검증은 DTO의 {@link jakarta.validation.constraints.Size}가 담당.
     */
    @Transactional
    public void change(User user, String currentPassword, String newPassword, String currentSessionId) {
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        user.changePasswordHash(passwordEncoder.encode(newPassword));
        // ADR #21 — admin invite/임시 PW 사용자가 첫 변경을 마치면 강제 redirect 루프 종결.
        user.clearMustChangePassword();
        userRepository.save(user);

        // 현재 세션은 보존, 그 외 모든 세션 invalidate.
        invalidateAllSessions(user.getEmail(), currentSessionId);

        auditService.record(new AuditEvent(
            AuditEventType.USER_PASSWORD_CHANGED,
            user.getId(),
            WebRequestContextHolder.currentIp(),
            WebRequestContextHolder.currentUserAgent(),
            AuditTargetType.USER,
            user.getId(),
            null,
            null,
            null
        ));
    }

    /**
     * Spring Session JDBC의 principal name 인덱스로 사용자 세션을 일괄 invalidate.
     * {@code keepSessionId}가 null이면 전부 삭제 (reset). non-null이면 해당 세션만 보존 (change).
     */
    void invalidateAllSessions(String principalName, String keepSessionId) {
        if (principalName == null || principalName.isBlank()) return;
        var sessions = sessionRepository.findByPrincipalName(principalName);
        for (String sessionId : sessions.keySet()) {
            if (keepSessionId != null && keepSessionId.equals(sessionId)) continue;
            sessionRepository.deleteById(sessionId);
        }
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 평문 토큰 — UUID 2개 join (64자, 8*4 + 4*4 + 4*4 + 4*4 + 12*4 = 충분한 엔트로피).
     * 이메일 본문/링크에만 노출되며 DB는 SHA-256 해시만 저장.
     */
    private static String generatePlainToken() {
        return UUID.randomUUID().toString().replace("-", "")
             + UUID.randomUUID().toString().replace("-", "");
    }

    static String sha256Hex(String plain) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(plain.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // JDK 표준 — 실패 불가
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
