package com.ibizdrive.auth;

import com.ibizdrive.auth.dto.LoginResponse;
import com.ibizdrive.auth.dto.SignupRequest;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * Self-signup 트랜잭션 — ADR #41 (supersedes #18).
 *
 * <p>플로우:
 * <ol>
 *   <li>email trim+lowercase 정규화 (login 정규화와 동일 — docs/03 §2.7).</li>
 *   <li>{@link UserRepository#findActiveByEmail}로 중복 확인 → 존재 시 {@link DuplicateEmailException}.</li>
 *   <li>{@link UserRepository#count} == 0 ⇒ 첫 user → {@link Role#ADMIN} (시스템 부트스트랩).
 *       이외 → {@link Role#MEMBER}. 동시 가입 race window는 좁고 의미 미미 — KISS.</li>
 *   <li>BCrypt(strength=12)로 password hash + {@link User} 저장 (is_active=true, must_change_password=false).</li>
 *   <li>{@link UserRegisteredEvent} publish — {@link com.ibizdrive.audit.AuthAuditListener}가 audit_log INSERT.</li>
 *   <li>{@link AuthService#establishSession}으로 자동 로그인 (세션 발급 + SecurityContext 영속화).
 *       login.success 이벤트는 별도 publish하지 않음 — signup audit 1건이 가입 + 세션 발급을 모두 표상.</li>
 * </ol>
 *
 * <p>{@code @Transactional}: user save + audit publish + 세션 발급을 단일 단위로 묶는다.
 * audit는 {@link com.ibizdrive.audit.AuditService} REQUIRES_NEW로 별도 commit (ADR #24).
 *
 * <p>본 클래스는 SecurityConfig에서 {@code /api/auth/signup} 경로를 permitAll + CSRF 면제로
 * 노출하므로, 호출자(브라우저)는 비로그인 상태에서 직접 invoke 가능. 이는 의도된 계약 (docs/03 §2 self-signup).
 */
@Service
public class SignupService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final ApplicationEventPublisher eventPublisher;

    public SignupService(UserRepository userRepository,
                         PasswordEncoder passwordEncoder,
                         AuthService authService,
                         ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public LoginResponse signup(SignupRequest req, HttpServletRequest httpReq, HttpServletResponse httpRes) {
        String email = (req.email() == null ? "" : req.email().trim()).toLowerCase(Locale.ROOT);
        String displayName = req.displayName() == null ? "" : req.displayName().trim();

        if (userRepository.findActiveByEmail(email).isPresent()) {
            throw new DuplicateEmailException();
        }

        // 첫 user: ADMIN. count()는 soft-deleted 포함 — 모든 사용자가 삭제된 시스템 부트스트랩
        // 재실행 시에도 첫 가입자는 ADMIN으로 의도된 동작 (operationally rare).
        Role role = userRepository.count() == 0L ? Role.ADMIN : Role.MEMBER;

        User user = new User(
            UUID.randomUUID(),
            email,
            displayName,
            passwordEncoder.encode(req.password()),
            role,
            true,    // isActive
            false,   // mustChangePassword (self-signup이므로 강제 변경 불필요 — ADR #21은 admin invite 흐름)
            OffsetDateTime.now()
        );
        userRepository.save(user);

        // ADR #24 — 도메인 이벤트 publish, listener가 audit_log INSERT.
        eventPublisher.publishEvent(new UserRegisteredEvent(user.getId(), user.getEmail()));

        // 자동 로그인 — ADR #41 UX. login.success는 별도 publish 안 함 (signup audit가 대표).
        return authService.establishSession(user, httpReq, httpRes);
    }
}
