package com.ibizdrive.permission;

import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * 권한 평가 단일 진입점 (docs/03 §3.4, ADR #17, ADR #26).
 *
 * <p><b>A3 MVP — user-level (Role 기반) 평가만</b>. folder/file 도메인 부재로 resource-level
 * 권한(`permissions` 테이블 + 재귀 CTE 상속 평가)은 A4 이월. 본 service의
 * {@code resource}, {@code resourceId} 인자는 SpEL 호환 시그니처를 위해 받지만 MVP 평가에서는
 * 사용하지 않는다 — A4에서 evaluator 내부만 교체하고 controller {@code @PreAuthorize} 호출처는 보존.
 *
 * <p>평가 정책:
 * <ul>
 *   <li>{@link Role#ADMIN} — 9 permission 모두 grant. {@link Permission#PURGE}는 추가로
 *       {@code @PreAuthorize("hasRole('ADMIN')")}로 이중 가드 (docs/03 §3.5 line 376~378)</li>
 *   <li>{@link Role#AUDITOR} — {@link Permission#READ}만 grant (감사 페이지 등 read-only)</li>
 *   <li>{@link Role#MEMBER} — 모두 deny (A4의 resource-level 권한 도입 시 변경)</li>
 *   <li>{@code null} role / 미인증 — 모두 deny</li>
 * </ul>
 */
@Service
public class PermissionService {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public PermissionService(UserRepository userRepository,
                             ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * @param userId    actor (현재 로그인 사용자) — A4 resource-level 평가에서 활용 예정
     * @param role      actor의 시스템 ROLE
     * @param resource  "folder" / "file" — A4 evaluator가 사용. MVP 미사용
     * @param resourceId 대상 리소스 식별자 — A4 evaluator가 사용. MVP 미사용
     * @param permission 요구 권한
     */
    public boolean check(UUID userId, Role role, String resource, Object resourceId, Permission permission) {
        if (role == null || permission == null) {
            return false;
        }
        // TODO(A4): resource-level 평가 — `permissions` 테이블 + 재귀 CTE로 상속 평가, deny 우선.
        //           본 user-level 분기는 A4에서 fallback (resource 권한 부재 시 role 기반)으로 유지.
        return effectivePermissions(role).contains(permission);
    }

    /**
     * 주어진 role이 보유한 권한 집합 — 거부 시 {@code 403} 응답의 {@code have} 노출용
     * (docs/03 §3.6). MVP는 role 단독 평가, A4 resource-level 도입 시 (role 권한) ∪
     * (resource-level grant) − (resource-level deny) 형태로 확장.
     */
    public Set<Permission> effectivePermissions(Role role) {
        if (role == null) {
            return EnumSet.noneOf(Permission.class);
        }
        return switch (role) {
            case ADMIN -> EnumSet.allOf(Permission.class);
            case AUDITOR -> EnumSet.of(Permission.READ);
            case MEMBER -> EnumSet.noneOf(Permission.class);
        };
    }

    /**
     * 사용자 ROLE 변경 — A3.4. {@link RoleChangedEvent}를 publish하여
     * {@link com.ibizdrive.audit.PermissionAuditListener}가 {@code permission.changed} audit row를
     * INSERT 하도록 한다 (ADR #24).
     *
     * <p>본 메서드는 트랜잭션 경계 안에서 user.role을 수정하고 save한 뒤 이벤트를 publish 한다.
     * {@link com.ibizdrive.audit.AuditService#record}는 {@code REQUIRES_NEW}로 별도 트랜잭션에서
     * INSERT 하므로 호출 측 트랜잭션이 rollback 되어도 audit row는 보존된다 (감사 무결성, ADR #25).
     *
     * <p>변경이 없으면(같은 role) no-op + 이벤트 미발행.
     *
     * @param targetUserId 변경 대상 사용자
     * @param newRole      새 ROLE
     * @param actorId      변경을 수행한 관리자(자기 자신 변경 시 target과 동일 가능). 시스템 자동 변경은 null
     * @throws IllegalArgumentException 대상 user 미존재
     */
    @Transactional
    public void changeRole(UUID targetUserId, Role newRole, UUID actorId) {
        if (targetUserId == null) throw new IllegalArgumentException("targetUserId must not be null");
        if (newRole == null) throw new IllegalArgumentException("newRole must not be null");

        User user = userRepository.findById(targetUserId)
            .orElseThrow(() -> new IllegalArgumentException("user not found: " + targetUserId));

        Role oldRole = user.getRole();
        if (oldRole == newRole) {
            return;
        }
        user.changeRoleTo(newRole);
        userRepository.save(user);

        eventPublisher.publishEvent(new RoleChangedEvent(actorId, targetUserId, oldRole, newRole));
    }
}
