package com.ibizdrive.auth.password;

import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import com.ibizdrive.audit.WebRequestContextHolder;
import com.ibizdrive.email.EmailDeliveryException;
import com.ibizdrive.email.EmailService;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private final Clock clock;
    private final String appUrl;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                EmailService emailService,
                                AuditService auditService,
                                Clock clock,
                                @Value("${app.app-url}") String appUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.auditService = auditService;
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
