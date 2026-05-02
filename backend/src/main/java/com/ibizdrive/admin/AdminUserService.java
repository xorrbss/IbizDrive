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
 * Admin invite 트랜잭션 — ADR #21 (admin 트랙 closure).
 *
 * <p>플로우:
 * <ol>
 *   <li>email trim+lowercase 정규화 (signup과 동일 — docs/03 §2.7).</li>
 *   <li>{@link UserRepository#findActiveByEmail}로 중복 확인 → 존재 시 {@link DuplicateEmailException}.</li>
 *   <li>{@link TempPasswordGenerator#generate}로 16자 임시 PW 생성.</li>
 *   <li>BCrypt encode 후 {@link User} 저장 ({@code isActive=true}, {@code mustChangePassword=true}).</li>
 *   <li>{@link AdminUserCreatedEvent} publish — {@link AdminAuditListener}가 {@code admin.user.created} INSERT.</li>
 *   <li>{@link EmailService#send}로 임시 PW + 로그인 안내 메일 발송 (force-change UX 자동 redirect).</li>
 * </ol>
 *
 * <p><b>임시 PW 비노출 invariant</b>: raw 임시 PW는 메서드 내 지역 변수로만 보유되며 BCrypt encode 직후
 * 이메일 본문에만 등장한다. {@link AdminInviteUserResponse}/예외/로그에 절대 포함되지 않는다
 * (context.md §"중요한 의사결정" 2; {@link AdminUserServiceTest}가 강제).
 *
 * <p>{@code @Transactional}: user save + event publish를 단일 단위로 묶는다. audit는
 * {@link com.ibizdrive.audit.AuditService} REQUIRES_NEW로 별도 commit (ADR #24).
 * EmailService.send는 트랜잭션 밖에 가까운 의미이지만 동기 호출로 단순화 (a1.5 trade-off; 실패 시 트랜잭션 롤백).
 */
@Service
public class AdminUserService {

    private static final String INVITE_SUBJECT = "[IbizDrive] 계정이 생성되었습니다 — 임시 비밀번호 안내";
    private static final String LOGIN_URL_HINT = "/login";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TempPasswordGenerator tempPasswordGenerator;
    private final EmailService emailService;
    private final ApplicationEventPublisher eventPublisher;

    public AdminUserService(UserRepository userRepository,
                            PasswordEncoder passwordEncoder,
                            TempPasswordGenerator tempPasswordGenerator,
                            EmailService emailService,
                            ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tempPasswordGenerator = tempPasswordGenerator;
        this.emailService = emailService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AdminInviteUserResponse invite(String emailRaw,
                                          String displayNameRaw,
                                          Role role,
                                          UUID actorId) {
        String email = (emailRaw == null ? "" : emailRaw.trim()).toLowerCase(Locale.ROOT);
        String displayName = displayNameRaw == null ? "" : displayNameRaw.trim();

        if (userRepository.findActiveByEmail(email).isPresent()) {
            throw new DuplicateEmailException();
        }

        // raw 임시 PW는 본 메서드 지역 스코프에만 존재 — 즉시 BCrypt encode 후 이메일 본문에만 사용.
        String tempPw = tempPasswordGenerator.generate();
        String hash = passwordEncoder.encode(tempPw);

        User user = new User(
            UUID.randomUUID(),
            email,
            displayName,
            hash,
            role,
            true,   // isActive
            true,   // mustChangePassword — ADR #21 admin 초대 시 강제 변경 요구
            OffsetDateTime.now()
        );
        userRepository.save(user);

        eventPublisher.publishEvent(new AdminUserCreatedEvent(user.getId(), actorId, email));

        emailService.send(email, INVITE_SUBJECT, buildInviteBody(displayName, email, tempPw));

        return AdminInviteUserResponse.from(user);
    }

    /**
     * 초대 메일 본문 — plain text 한글. HTML/i18n은 v1.x.
     * 호출자만 사용. raw 임시 PW를 받지만 즉시 본문 String 안으로만 흐른다.
     */
    private static String buildInviteBody(String displayName, String email, String tempPw) {
        return String.join("\n",
            displayName + "님, 안녕하세요.",
            "",
            "IbizDrive 계정이 생성되었습니다.",
            "",
            "로그인 이메일: " + email,
            "임시 비밀번호: " + tempPw,
            "",
            "최초 로그인 후 비밀번호 변경 페이지로 자동 이동합니다.",
            "임시 비밀번호는 1회용이며, 새 비밀번호로 변경한 뒤에는 사용할 수 없습니다.",
            "",
            "로그인: " + LOGIN_URL_HINT
        );
    }
}
