package com.ibizdrive.admin;

import com.ibizdrive.auth.DuplicateEmailException;
import com.ibizdrive.email.EmailService;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * admin-user-mgmt + admin-user-search-update — `/admin/users` 목록 조회 (검색 옵션).
     *
     * <p>{@code q}가 null/blank이면 {@link UserRepository#findAllActivePageable}로 전체 조회.
     * q가 있으면 lowercase + LIKE escape (`%`, `_`, `\`) + wildcard wrap 후
     * {@link UserRepository#findForAdminPageable}로 검색. 두 분기 모두 soft-delete 제외 +
     * 비활성 포함 (재활성 대상).
     *
     * <p>검색 패턴 사양 (Wave 1 — T1, docs/02 §7.4):
     * <ul>
     *   <li>case-insensitive (LOWER 양쪽 적용)</li>
     *   <li>부분 매칭 (앞뒤 wildcard `%` 자동 wrap)</li>
     *   <li>literal `%`, `_`, `\`는 backslash escape — wildcard 폭주 방지</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public Page<User> list(Pageable pageable, String q) {
        if (q == null || q.isBlank()) {
            return userRepository.findAllActivePageable(pageable);
        }
        String pattern = "%" + escapeLikeWildcards(q.trim().toLowerCase(Locale.ROOT)) + "%";
        return userRepository.findForAdminPageable(pattern, pageable);
    }

    /**
     * LIKE 패턴의 메타문자(`\`, `%`, `_`)를 backslash escape — JPQL `ESCAPE '\\'`와 짝.
     * 호출자가 미리 lowercase 처리된 입력을 받는다고 가정 (대소문자 변환 책임 분리).
     */
    private static String escapeLikeWildcards(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\\' || c == '%' || c == '_') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * 단건 조회 — dual-approval Phase 3b controller가 framework submit 전 currentRole 캡처 용도.
     *
     * @throws AdminUserNotFoundException target 미존재 → 404
     */
    @Transactional(readOnly = true)
    public User get(UUID targetUserId) {
        if (targetUserId == null) throw new IllegalArgumentException("targetUserId must not be null");
        return userRepository.findById(targetUserId)
            .orElseThrow(() -> new AdminUserNotFoundException(targetUserId.toString()));
    }

    /**
     * admin-user-mgmt — 사용자 ROLE 변경. self-demote(ADMIN→non-ADMIN) 차단.
     *
     * <p>{@code com.ibizdrive.permission.PermissionService#changeRole}와 분리 — 후자는 controller
     * 미노출 dead code이며 audit type({@code permission.changed})도 다르다. 본 메서드는
     * {@link AdminRoleChangedEvent} → {@code admin.role.changed} 매핑.
     *
     * <p>변경이 없으면(같은 role) no-op + event 미발행 (멱등). 본인이 본인을 같은 role로
     * 재지정하는 경우는 self-demote 검증 전에 통과 — `actorId == targetId && newRole == ADMIN`은
     * 항상 안전.
     *
     * @throws AdminUserNotFoundException     target 미존재 → 404
     * @throws AdminSelfProtectionException   actor==target && newRole != ADMIN → 403
     */
    @Transactional
    public User changeRole(UUID targetUserId, Role newRole, UUID actorId) {
        if (targetUserId == null) throw new IllegalArgumentException("targetUserId must not be null");
        if (newRole == null) throw new IllegalArgumentException("newRole must not be null");

        User user = userRepository.findById(targetUserId)
            .orElseThrow(() -> new AdminUserNotFoundException(targetUserId.toString()));

        Role oldRole = user.getRole();
        if (oldRole == newRole) {
            return user;
        }
        if (targetUserId.equals(actorId) && newRole != Role.ADMIN) {
            // 본인이 본인을 ADMIN→non-ADMIN — 마지막 ADMIN 0 사태 방지 (단순 self-protection).
            throw new AdminSelfProtectionException("self-demote forbidden");
        }

        user.changeRoleTo(newRole);
        userRepository.save(user);

        eventPublisher.publishEvent(new AdminRoleChangedEvent(targetUserId, actorId, oldRole, newRole));
        return user;
    }

    /**
     * admin-user-mgmt — 사용자 비활성화 (`is_active=false`). self-deactivate 차단.
     *
     * <p>이미 inactive면 멱등 (no-op + event 미발행). 본 트랙은 deactivate만 노출 —
     * reactivate UX는 v1.x.
     *
     * @throws AdminUserNotFoundException     target 미존재 → 404
     * @throws AdminSelfProtectionException   actor==target → 403
     */
    @Transactional
    public User deactivate(UUID targetUserId, UUID actorId) {
        if (targetUserId == null) throw new IllegalArgumentException("targetUserId must not be null");

        User user = userRepository.findById(targetUserId)
            .orElseThrow(() -> new AdminUserNotFoundException(targetUserId.toString()));

        if (targetUserId.equals(actorId)) {
            // 본인이 본인 비활성화 — 즉시 로그인 차단 + 재로그인 불가 → 잠금 회피.
            throw new AdminSelfProtectionException("self-deactivate forbidden");
        }
        if (!user.isActive()) {
            return user; // 이미 비활성 — 멱등.
        }

        user.deactivate();
        userRepository.save(user);

        eventPublisher.publishEvent(new AdminUserDeactivatedEvent(targetUserId, actorId));
        return user;
    }

    /**
     * admin-user-search-update — 비활성화된 계정 재활성화 (`is_active=true`). Wave 1 — T1.
     *
     * <p>이미 active면 멱등 (no-op + event 미발행). self-reactivate는 허용 — 의미상 잠금 회피
     * 위험 없음(active한 사용자가 본인 active 상태를 토글해도 즉시 잠금 없음).
     *
     * <p>audit type은 {@link AuditEventType#ADMIN_USER_UPDATED} ({@link AdminUserUpdatedEvent}) —
     * deactivate ({@code admin.user.deactivated})와 의미 분리 (제재 vs 일반 변경).
     *
     * @throws AdminUserNotFoundException     target 미존재 → 404
     */
    @Transactional
    public User reactivate(UUID targetUserId, UUID actorId) {
        if (targetUserId == null) throw new IllegalArgumentException("targetUserId must not be null");

        User user = userRepository.findById(targetUserId)
            .orElseThrow(() -> new AdminUserNotFoundException(targetUserId.toString()));

        if (user.isActive()) {
            return user; // 이미 활성 — 멱등.
        }

        user.reactivate();
        userRepository.save(user);

        eventPublisher.publishEvent(new AdminUserUpdatedEvent(
            targetUserId,
            actorId,
            "{\"isActive\":false}",
            "{\"isActive\":true}"
        ));
        return user;
    }

    /**
     * admin-user-search-update — displayName 편집. Wave 1 — T1.
     *
     * <p>호출자가 trim 적용 (controller 단계 또는 본 메서드에서 한 번 더). 같은 값이면 멱등
     * (no-op + event 미발행). 자기 자신 displayName 편집은 허용 — self-protection 불필요
     * (제재 의미 없는 단순 표시 이름 변경).
     *
     * <p>도메인 단계 검증은 {@link User#changeDisplayName}이 수행 (blank/length≤100). 본 메서드는
     * 멱등 분기 + audit metadata 생성만 담당.
     *
     * @throws AdminUserNotFoundException     target 미존재 → 404
     * @throws IllegalArgumentException       displayName blank 또는 100자 초과 → 400
     */
    @Transactional
    public User changeDisplayName(UUID targetUserId, String newDisplayName, UUID actorId) {
        if (targetUserId == null) throw new IllegalArgumentException("targetUserId must not be null");
        // null은 도메인이 거부하지만 일관성 위해 service 입구에서도 한 번 차단.
        String trimmed = newDisplayName == null ? null : newDisplayName.trim();

        User user = userRepository.findById(targetUserId)
            .orElseThrow(() -> new AdminUserNotFoundException(targetUserId.toString()));

        String oldDisplayName = user.getDisplayName();
        if (trimmed != null && trimmed.equals(oldDisplayName)) {
            return user; // 같은 값 — 멱등.
        }

        user.changeDisplayName(trimmed); // null/blank/over-100은 여기서 IllegalArgumentException.
        userRepository.save(user);

        eventPublisher.publishEvent(new AdminUserUpdatedEvent(
            targetUserId,
            actorId,
            "{\"displayName\":" + jsonString(oldDisplayName) + "}",
            "{\"displayName\":" + jsonString(trimmed) + "}"
        ));
        return user;
    }

    /**
     * 작은 manual JSON string encoder — Jackson 의존 없이 displayName 같은 임의 문자열 안전 직렬화.
     * 본 service의 audit metadata는 단순 key-value(예: {@code {"displayName":"...."}}) 구조이므로
     * 정밀한 escaping(`"`, `\`, control chars)만 처리하면 충분.
     */
    private static String jsonString(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
