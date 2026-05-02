package com.ibizdrive.auth;

import com.ibizdrive.auth.dto.LoginResponse;
import com.ibizdrive.permission.PermissionCacheKeyService;
import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * 로그인 인증 흐름 — A1.3, docs/03 §2.3, docs/02 §7.4.
 *
 * <p>플로우:
 * <ol>
 *   <li>email lowercase 정규화</li>
 *   <li>{@link LoginAttemptTracker#isLocked} 사전 검사 → locked → {@link AccountLockedException}</li>
 *   <li>{@link UserRepository#findActiveByEmail} 조회 (soft-delete 자동 필터)</li>
 *   <li>미존재: timing-safe dummy BCrypt verify → 실패 카운트 → {@link InvalidCredentialsException}</li>
 *   <li>비활성 / 관리자 잠금 (locked_at): 동일 응답으로 매핑 (계정 enumeration 방지)</li>
 *   <li>PW 불일치: 실패 카운트 → 동일 응답</li>
 *   <li>성공: 카운터 reset + {@code last_login_at} 갱신 + {@code changeSessionId()} +
 *       세션 attribute set</li>
 * </ol>
 *
 * <p>{@code @Transactional}: last_login_at 갱신 + (후속) audit insert가 단일 단위.
 * 비록 A1.3 시점에 audit insert는 미구현이지만, 트랜잭션 경계는 미리 설정 (docs/02 §7.4 TX 컬럼).
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptTracker tracker;
    private final SecurityContextRepository securityContextRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PermissionCacheKeyService permissionCacheKeyService;

    /**
     * timing attack 회피용 dummy BCrypt 해시 — 미존재/비활성 user에도 동일 시간 소비.
     * 부팅 시점에 {@link PasswordEncoder#encode}로 생성하여 형식이 항상 유효함을 보장
     * (DelegatingPasswordEncoder가 {@code {bcrypt}} 프리픽스를 자동 부여).
     * 잘못된 형식의 상수 hash는 Spring이 verify 시 즉시 false 반환하여 시간 차이가 발생하므로 회피.
     */
    private String dummyHash;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       LoginAttemptTracker tracker,
                       SecurityContextRepository securityContextRepository,
                       ApplicationEventPublisher eventPublisher,
                       PermissionCacheKeyService permissionCacheKeyService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tracker = tracker;
        this.securityContextRepository = securityContextRepository;
        this.eventPublisher = eventPublisher;
        this.permissionCacheKeyService = permissionCacheKeyService;
    }

    @PostConstruct
    void initDummyHash() {
        this.dummyHash = passwordEncoder.encode("dummy-for-timing-only");
    }

    @Transactional
    public LoginResponse login(String emailRaw, String rawPassword, HttpServletRequest req, HttpServletResponse res) {
        String email = (emailRaw == null ? "" : emailRaw.trim()).toLowerCase(Locale.ROOT);

        if (tracker.isLocked(email)) {
            // ADR #24 — cross-cutting 신호로 Spring Security 표준 이벤트 publish (AuthAuditListener 구독).
            Authentication failedAuth = UsernamePasswordAuthenticationToken.unauthenticated(email, "");
            eventPublisher.publishEvent(new AuthenticationFailureLockedEvent(
                failedAuth, new LockedException("locked")));
            throw new AccountLockedException(tracker.getRetryAfterSeconds(email));
        }

        Optional<User> opt = userRepository.findActiveByEmail(email);

        if (opt.isEmpty()) {
            // timing-safe — 미존재 사용자도 BCrypt 한 번 돌린다 (결과 무시).
            passwordEncoder.matches(rawPassword == null ? "" : rawPassword, dummyHash);
            tracker.recordFailure(email);
            Authentication failedAuth = UsernamePasswordAuthenticationToken.unauthenticated(email, "");
            eventPublisher.publishEvent(new AuthenticationFailureBadCredentialsEvent(
                failedAuth, new BadCredentialsException("user-not-found")));
            throw new InvalidCredentialsException();
        }

        User user = opt.get();

        // 비활성·관리자 잠금: 모두 INVALID_CREDENTIALS로 매핑 (enumeration 방지, docs/03 §2.3).
        if (!user.isActive() || user.isLocked()) {
            // PW 검증은 건너뛰지 말고 dummy로 시간 균등화 — 비활성 vs 미존재 구분 회피
            passwordEncoder.matches(rawPassword == null ? "" : rawPassword, dummyHash);
            tracker.recordFailure(email);
            Authentication failedAuth = UsernamePasswordAuthenticationToken.unauthenticated(email, "");
            eventPublisher.publishEvent(new AuthenticationFailureBadCredentialsEvent(
                failedAuth, new BadCredentialsException("inactive-or-locked")));
            throw new InvalidCredentialsException();
        }

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(rawPassword == null ? "" : rawPassword, user.getPasswordHash())) {
            tracker.recordFailure(email);
            Authentication failedAuth = UsernamePasswordAuthenticationToken.unauthenticated(email, "");
            eventPublisher.publishEvent(new AuthenticationFailureBadCredentialsEvent(
                failedAuth, new BadCredentialsException("bad-password")));
            throw new InvalidCredentialsException();
        }

        // 성공 — 카운터 reset, last_login_at 갱신, session fixation 방어
        tracker.recordSuccess(email);
        user.recordLoginAt(OffsetDateTime.now());
        userRepository.save(user);

        LoginResponse response = establishSession(user, req, res);

        // ADR #24 — 표준 success 이벤트 발행 (AuthAuditListener가 audit_log INSERT).
        // SecurityContext가 SecurityContextHolder에 set된 이후 publish하여 listener가
        // 동일 요청 스레드에서 principal을 일관되게 본다.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        eventPublisher.publishEvent(new AuthenticationSuccessEvent(auth));

        return response;
    }

    /**
     * 인증된 사용자에 대한 세션 발급 + SecurityContext 영속화. login 성공 직후 또는 self-signup
     * 직후 자동 로그인 시 공용 (ADR #41 — SignupService가 호출).
     *
     * <p>책임:
     * <ol>
     *   <li>session fixation 방어 — 기존 세션이 없으면 새로 생성, 있으면 sessionId 회전.</li>
     *   <li>{@link IbizDriveUserDetails} principal 기반 Authentication 생성 + SecurityContextHolder 갱신.</li>
     *   <li>{@link SecurityContextRepository#saveContext}로 새 sessionId 기반 HttpSession에 영속화.</li>
     *   <li>permissions cache key 산출 + session attribute에 기록 (validity 검증·invalidation 추적용).</li>
     * </ol>
     *
     * <p>본 메서드는 audit emission을 수행하지 않는다. 호출자가 도메인에 맞는 이벤트
     * (login: {@link AuthenticationSuccessEvent}, signup: {@link UserRegisteredEvent})를 publish해야 한다.
     * 그렇지 않으면 audit_log에 흔적이 남지 않는다.
     */
    public LoginResponse establishSession(User user, HttpServletRequest req, HttpServletResponse res) {
        // session fixation 방어 — 새 sessionId 발급 (docs/03 §2.3, ADR #20).
        // 기존 세션이 없으면 먼저 생성 후 changeSessionId() 호출.
        req.getSession(true);
        req.changeSessionId();

        // SecurityContext 영속화 (Spring Security 6 — auto-save 미지원, 명시 필요).
        // 새 sessionId 발급 후 호출하여 새 세션에 컨텍스트가 저장되도록 한다.
        IbizDriveUserDetails principal = new IbizDriveUserDetails(user);
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(
            principal, null, principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, req, res);

        String cacheKey = permissionCacheKeyService.computeKey(user.getId(), user.getRole());

        HttpSession session = req.getSession();
        session.setAttribute("userId", user.getId().toString());
        session.setAttribute("issuedAt", System.currentTimeMillis());
        session.setAttribute("permissionsCacheKey", cacheKey);

        return LoginResponse.from(user, cacheKey);
    }
}
