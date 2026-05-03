package com.ibizdrive.admin;

import com.ibizdrive.auth.DuplicateEmailException;
import com.ibizdrive.email.EmailService;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * Admin이 신규 user를 초대(생성)하는 서비스 — m-admin-entry-rewrite, ADR #21 closure.
 *
 * <p>플로우 ({@link com.ibizdrive.auth.SignupService} mirror, 차이점만 주석):
 * <ol>
 *   <li>email trim+lowercase, displayName trim.</li>
 *   <li>{@link UserRepository#findActiveByEmail} 중복 확인 → {@link DuplicateEmailException}.</li>
 *   <li>{@link TempPasswordGenerator}로 임시 PW 생성 → BCrypt 인코딩.</li>
 *   <li>{@link User} 저장 — <b>mustChangePassword=true</b> (admin 초대는 첫 로그인에서 PW 변경 강제, ADR #21).
 *       role은 호출자 지정 (signup의 첫-user-ADMIN 분기 없음 — admin이 명시적으로 role을 부여).</li>
 *   <li>{@link AdminUserCreatedEvent} publish — {@link AdminAuditListener}가 audit_log INSERT.</li>
 *   <li>{@link EmailService#send} — 임시 PW를 본문에 포함해 발송 (a1.5 비동기 #45 적용 인프라).
 *       응답에는 임시 PW 미포함 (DTO에 필드 부재) — docs/03 §2.8.</li>
 * </ol>
 *
 * <p>{@code @Transactional}: user save + event publish를 단일 단위. AdminAuditListener는
 * AFTER_COMMIT 트리거이므로 audit는 commit 이후 emit된다.
 *
 * <p>email 발송은 {@code @Async}(ADR #45)로 fire-and-forget — caller latency를 SMTP RTT와
 * 분리하고 anti-enumeration timing leak도 완화. 본 service는 send 실패에 의존하지 않는다.
 */
@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final EmailService emailService;
    private final TempPasswordGenerator tempPasswordGenerator;

    public AdminUserService(UserRepository userRepository,
                            PasswordEncoder passwordEncoder,
                            ApplicationEventPublisher eventPublisher,
                            EmailService emailService,
                            TempPasswordGenerator tempPasswordGenerator) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.emailService = emailService;
        this.tempPasswordGenerator = tempPasswordGenerator;
    }

    @Transactional
    public User invite(String rawEmail, String rawDisplayName, Role role, UUID actorId) {
        String email = (rawEmail == null ? "" : rawEmail.trim()).toLowerCase(Locale.ROOT);
        String displayName = rawDisplayName == null ? "" : rawDisplayName.trim();

        if (userRepository.findActiveByEmail(email).isPresent()) {
            throw new DuplicateEmailException();
        }

        String tempPassword = tempPasswordGenerator.generate();
        String hash = passwordEncoder.encode(tempPassword);

        User user = new User(
            UUID.randomUUID(),
            email,
            displayName,
            hash,
            role,
            true,    // isActive
            true,    // mustChangePassword — 첫 로그인 강제 변경 (ADR #21)
            OffsetDateTime.now()
        );
        userRepository.save(user);

        eventPublisher.publishEvent(new AdminUserCreatedEvent(user.getId(), actorId, email));

        // 본문에 raw 임시 PW 포함 — 사용자가 첫 로그인 후 즉시 변경 (mustChangePassword=true).
        // 응답 DTO에는 미포함 (docs/03 §2.8 — 임시 PW는 audit/log/응답 어디에도 노출 금지).
        emailService.send(
            email,
            "[IbizDrive] 계정이 생성되었습니다",
            "안녕하세요 " + displayName + "님,\n\n"
                + "관리자가 IbizDrive 계정을 생성했습니다.\n"
                + "임시 비밀번호: " + tempPassword + "\n\n"
                + "처음 로그인하면 비밀번호를 새로 설정해야 합니다.\n"
        );

        return user;
    }
}
