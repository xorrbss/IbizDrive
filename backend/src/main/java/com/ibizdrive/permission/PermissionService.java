package com.ibizdrive.permission;

import com.ibizdrive.common.error.ResourceNotFoundException;
import com.ibizdrive.department.Department;
import com.ibizdrive.department.DepartmentRepository;
import com.ibizdrive.permission.dto.PermissionDto;
import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private final PermissionRepository permissionRepository;
    private final DepartmentRepository departmentRepository;
    private final ApplicationEventPublisher eventPublisher;

    public PermissionService(UserRepository userRepository,
                             PermissionRepository permissionRepository,
                             DepartmentRepository departmentRepository,
                             ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.departmentRepository = departmentRepository;
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

    /**
     * Resource-level 권한 grant — A4.4, ADR #26 close 의 INSERT 경로.
     *
     * <p>{@link PermissionRow} 를 새 UUID + 현재 시각으로 채워 저장한 뒤 {@link PermissionGrantedEvent} 를 publish.
     * audit 기록은 {@link com.ibizdrive.audit.PermissionAuditListener} 가 REQUIRES_NEW 분리 트랜잭션에서 처리하므로
     * 본 메서드 트랜잭션이 rollback 되어도 audit row 는 보존된다 (ADR #24 동형).
     *
     * <p><b>입력 검증</b>:
     * <ul>
     *   <li>{@code resourceType} ∈ {"folder", "file"} (V5 CHECK 일치)</li>
     *   <li>{@code subjectType} ∈ {"user", "department", "role", "everyone"}</li>
     *   <li>{@code subjectId == null} ↔ {@code subjectType.equals("everyone")} (V5 CHECK 일치)</li>
     *   <li>{@code expiresAt} 은 NULL 또는 미래 시각</li>
     * </ul>
     *
     * <p><b>중복 grant</b>: V5 의 {@code idx_permissions_unique} (resource, subject) 가 두 번째 INSERT 를 차단 →
     * {@link DataIntegrityViolationException} 을 catch 하여 {@link PermissionConflictException} 으로 변환.
     * 매핑은 {@code GlobalExceptionHandler} 에서 409 PERMISSION_CONFLICT envelope.
     *
     * @return 생성된 grant row (id + createdAt 채워진 상태) — controller 가 DTO 매핑에 사용
     */
    @Transactional
    public PermissionRow grantPermission(String resourceType,
                                          UUID resourceId,
                                          String subjectType,
                                          UUID subjectId,
                                          Preset preset,
                                          Instant expiresAt,
                                          UUID actorId) {
        validateGrantInput(resourceType, resourceId, subjectType, subjectId, preset, expiresAt, actorId);

        UUID newId = UUID.randomUUID();
        PermissionRow row = new PermissionRow();
        row.setId(newId);
        row.setResourceType(resourceType);
        row.setResourceId(resourceId);
        row.setSubjectType(subjectType);
        row.setSubjectId(subjectId);
        row.setPreset(preset.wire());
        row.setGrantedBy(actorId);
        row.setExpiresAt(expiresAt);
        row.setCreatedAt(Instant.now());

        try {
            permissionRepository.saveAndFlush(row);
        } catch (DataIntegrityViolationException ex) {
            // V5 unique index — 동일 (resource, subject) 중복 grant 차단.
            throw new PermissionConflictException(
                "permission already exists for resource=" + resourceType + ":" + resourceId
                    + " subject=" + subjectType + ":" + subjectId,
                ex
            );
        }

        eventPublisher.publishEvent(new PermissionGrantedEvent(
            actorId, newId, resourceType, resourceId, subjectType, subjectId, preset, expiresAt
        ));
        return row;
    }

    /**
     * Resource-level 권한 revoke — A4.4, ADR #26 close 의 DELETE 경로.
     *
     * <p>row 를 먼저 조회하여 snapshot 을 캡처한 뒤 DELETE → {@link PermissionRevokedEvent} publish.
     * snapshot 이 필요한 이유는 listener 가 {@code before_state} JSON 본문을 만들어야 하기 때문.
     *
     * <p>row 가 존재하지 않으면 {@link ResourceNotFoundException}. {@code GlobalExceptionHandler} 가 404 로 매핑한다.
     *
     * @param permissionId 삭제 대상 grant 의 PK
     * @param actorId      revoke 를 수행한 사용자 (일반적으로 PERMISSION_ADMIN 또는 ADMIN role)
     */
    @Transactional
    public void revokePermission(UUID permissionId, UUID actorId) {
        if (permissionId == null) throw new IllegalArgumentException("permissionId must not be null");

        PermissionRow row = permissionRepository.findById(permissionId)
            .orElseThrow(() -> new ResourceNotFoundException("permission not found: " + permissionId));

        Preset preset;
        try {
            preset = Preset.from(row.getPreset());
        } catch (IllegalArgumentException ex) {
            // DB row 에 알 수 없는 preset wire 가 저장되어 있다면 schema/migration 결함 — fail-fast.
            throw new IllegalStateException("invalid preset stored: " + row.getPreset(), ex);
        }

        permissionRepository.delete(row);

        eventPublisher.publishEvent(new PermissionRevokedEvent(
            actorId,
            row.getId(),
            row.getResourceType(),
            row.getResourceId(),
            row.getSubjectType(),
            row.getSubjectId(),
            preset,
            row.getExpiresAt()
        ));
    }

    /**
     * 시스템 자동 만료 — {@code permissions-expired-cron} 진입점.
     *
     * <p>{@link PermissionRepository#lockById}로 row를 잠근 뒤 snapshot 캡처 → DELETE →
     * {@link PermissionExpiredEvent} publish. {@link com.ibizdrive.audit.PermissionAuditListener}가
     * {@code permission.expired} audit row INSERT (REQUIRES_NEW 분리 트랜잭션, ADR #24).
     *
     * <p><b>controller 매핑 없음 / @PreAuthorize 불요</b>: 본 메서드는 {@link PermissionExpirationJob}이
     * 시스템 트리거로만 호출. 외부 API에서 진입할 경로 없음. {@link #revokePermission}과의 차이는 actor
     * 부재 + audit row의 {@code metadata.trigger='system.expiration'}.
     *
     * <p><b>race protection</b>:
     * <ul>
     *   <li>두 번째 cron 인스턴스가 동시에 같은 id 처리 시 → 한 쪽이 row-level lock 획득, 다른 쪽은 lock
     *       대기 후 query 시점에 row 부재 → {@link ResourceNotFoundException} → job 레벨에서 swallow.</li>
     *   <li>사용자가 {@link #revokePermission}으로 직접 삭제한 직후 cron 만료 시도 → 동일 패턴으로 race-safe.</li>
     * </ul>
     *
     * <p><b>{@link #revokePermission}과의 코드 중복</b>: 두 메서드는 (lock/find → snapshot → DELETE → event
     * publish) 라는 4-step 골격이 동일하나 이벤트 타입과 lock 메서드가 달라 helper 추출이 오히려 가독성을
     * 해친다 — KISS. 변경 시 두 곳을 함께 수정.
     *
     * @param permissionId 만료 대상 grant 의 PK
     * @throws ResourceNotFoundException row 가 없거나 다른 인스턴스/사용자 트랜잭션이 이미 삭제 (race)
     */
    @Transactional
    public void expirePermission(UUID permissionId) {
        if (permissionId == null) throw new IllegalArgumentException("permissionId must not be null");

        PermissionRow row = permissionRepository.lockById(permissionId)
            .orElseThrow(() -> new ResourceNotFoundException("permission not found: " + permissionId));

        Preset preset;
        try {
            preset = Preset.from(row.getPreset());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("invalid preset stored: " + row.getPreset(), ex);
        }

        permissionRepository.delete(row);

        eventPublisher.publishEvent(new PermissionExpiredEvent(
            row.getId(),
            row.getResourceType(),
            row.getResourceId(),
            row.getSubjectType(),
            row.getSubjectId(),
            preset,
            row.getGrantedBy(),
            row.getCreatedAt(),
            row.getExpiresAt()
        ));
    }

    /**
     * DELETE {@code /api/permissions/:permissionId} 의 SpEL 가드 (docs/02 §7.10).
     *
     * <p>호출 형태: {@code @PreAuthorize("@permissionService.canRevokePermission(#permissionId, principal)")}.
     *
     * <p><b>MVP 평가</b>: ROLE 기반 — {@link Role#ADMIN} 만 허용. A4.3 evaluator 머지 후 본 메서드 내부를
     * `evaluator.hasPermission(auth, row.resourceId, row.resourceType, PERMISSION_ADMIN)` 로 교체 가능 — 호출처
     * 시그니처는 보존 (ADR #26 호출처 보존 정책 동형).
     *
     * <p>{@code permissionId} 가 존재하지 않더라도 본 메서드는 false 만 반환 — 404 는 controller 본체에서 분리 판정.
     * (Spring Security 가 false 시 throw {@link org.springframework.security.access.AccessDeniedException} → 403)
     */
    public boolean canRevokePermission(UUID permissionId, IbizDriveUserDetails currentUser) {
        if (currentUser == null) return false;
        Role role = currentUser.getUser().getRole();
        return role == Role.ADMIN;
    }

    /**
     * M8.0 — resource-level grant 목록 조회. {@code GET /api/{resource}/{id}/permissions}.
     *
     * <p>{@link PermissionRepository#findByResourceTypeAndResourceIdOrderByCreatedAtAscIdAsc} 로 row를
     * 가져온 뒤 subject 표시명을 batch resolve. {@code subject_type='user'} → user batch fetch,
     * {@code subject_type='department'} → department batch fetch, {@code subject_type='everyone'} →
     * subjectName=null. user/department 인데 lookup 실패(soft-delete 등) 시에도 null fallback
     * (A16/ShareQueryService 동형 정책 — frontend 가 "(삭제된 사용자)" 등 placeholder 표시 가능).
     *
     * <p>readOnly 트랜잭션 — DB write 없음. controller 에서 PERMISSION_ADMIN 게이트가 통과한 호출만 도달.
     */
    @Transactional(readOnly = true)
    public List<PermissionDto> listPermissions(String resourceType, UUID resourceId) {
        if (resourceType == null || !ALLOWED_RESOURCE_TYPES.contains(resourceType)) {
            throw new IllegalArgumentException("resourceType must be 'folder' or 'file'");
        }
        if (resourceId == null) throw new IllegalArgumentException("resourceId must not be null");

        List<PermissionRow> rows = permissionRepository
            .findByResourceTypeAndResourceIdOrderByCreatedAtAscIdAsc(resourceType, resourceId);
        if (rows.isEmpty()) return List.of();

        Map<UUID, String> subjectNames = fetchSubjectNames(rows);

        List<PermissionDto> dtos = new ArrayList<>(rows.size());
        for (PermissionRow row : rows) {
            String name = row.getSubjectId() != null ? subjectNames.get(row.getSubjectId()) : null;
            dtos.add(PermissionDto.from(row, name));
        }
        return dtos;
    }

    /**
     * 페이지 grants 에서 user/department subject_id 를 분리 수집 → 1회씩 batch fetch → id → name 맵.
     * {@code everyone} 은 subject_id NULL 로 자연 제외. ROLE subject 는 frontend 도메인 부재(ADR #36)로
     * 표시명 미해결 — 본 메서드는 매핑하지 않으며 호출자가 null fallback.
     *
     * <p>{@link com.ibizdrive.share.ShareQueryService#fetchSubjectNames} 와 동일 패턴 — Share 트랙에서
     * 동작 검증된 구조. 두 곳을 합치는 helper 추출은 의존성 방향(share → permission)을 깨므로 보류.
     */
    private Map<UUID, String> fetchSubjectNames(List<PermissionRow> rows) {
        Set<UUID> userIds = new LinkedHashSet<>();
        Set<UUID> deptIds = new LinkedHashSet<>();
        for (PermissionRow r : rows) {
            if (r.getSubjectId() == null) continue;
            if ("user".equals(r.getSubjectType())) {
                userIds.add(r.getSubjectId());
            } else if ("department".equals(r.getSubjectType())) {
                deptIds.add(r.getSubjectId());
            }
        }
        Map<UUID, String> map = new HashMap<>(userIds.size() + deptIds.size());
        if (!userIds.isEmpty()) {
            for (User u : userRepository.findAllById(userIds)) {
                map.put(u.getId(), u.getDisplayName());
            }
        }
        if (!deptIds.isEmpty()) {
            for (Department d : departmentRepository.findAllById(deptIds)) {
                map.put(d.getId(), d.getName());
            }
        }
        return map;
    }

    private static final Set<String> ALLOWED_RESOURCE_TYPES = Set.of("folder", "file");
    private static final Set<String> ALLOWED_SUBJECT_TYPES =
        Set.of("user", "department", "role", "everyone");

    private static void validateGrantInput(String resourceType,
                                           UUID resourceId,
                                           String subjectType,
                                           UUID subjectId,
                                           Preset preset,
                                           Instant expiresAt,
                                           UUID actorId) {
        if (resourceType == null || !ALLOWED_RESOURCE_TYPES.contains(resourceType)) {
            throw new IllegalArgumentException("resourceType must be 'folder' or 'file'");
        }
        if (resourceId == null) throw new IllegalArgumentException("resourceId must not be null");
        if (subjectType == null || !ALLOWED_SUBJECT_TYPES.contains(subjectType)) {
            throw new IllegalArgumentException(
                "subjectType must be one of user|department|role|everyone");
        }
        boolean isEveryone = "everyone".equals(subjectType);
        if (isEveryone && subjectId != null) {
            throw new IllegalArgumentException("subjectId must be null when subjectType='everyone'");
        }
        if (!isEveryone && subjectId == null) {
            throw new IllegalArgumentException("subjectId is required when subjectType != 'everyone'");
        }
        if (preset == null) throw new IllegalArgumentException("preset must not be null");
        if (actorId == null) throw new IllegalArgumentException("actorId must not be null");
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException("expiresAt must be in the future");
        }
    }
}
