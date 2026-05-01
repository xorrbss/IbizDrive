package com.ibizdrive.permission;

import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.Role;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Spring Security {@link PermissionEvaluator} 구현 — SpEL {@code hasPermission(target, permission)}
 * 및 {@code hasPermission(targetId, targetType, permission)} 호출의 backing logic.
 *
 * <p>호출 시그니처는 docs/02 §7.10의 Guard 패턴 ({@code hasPermission(#id, 'folder', 'READ')})에
 * 맞춘다 (ADR #26 — A3 → A4 전환에서 본 클래스 내부만 교체, 호출처는 그대로).
 *
 * <p>A4 평가 순서:
 * <ol>
 *   <li><b>ROLE 경로 (A3 보존)</b> — {@link PermissionService#effectivePermissions(Role)}이
 *       요구 권한을 포함하면 grant. ADMIN(전체)/AUDITOR(READ)는 이 경로에서 즉시 grant.
 *       PURGE는 ROLE ADMIN만 보유 (Preset 매트릭스에 PURGE 없음 — Preset.java:51).</li>
 *   <li><b>Resource-level 경로 (A4 신규)</b> — ROLE deny + {@code targetType ∈ {folder, file}} +
 *       {@code targetId}가 UUID로 파싱 가능할 때 {@link PermissionResolver}에 위임. Resolver가
 *       재귀 CTE 상속/만료/everyone subject 처리 결과를 반환.</li>
 *   <li><b>그 외</b> — deny.</li>
 * </ol>
 *
 * <p>Deny 판정 시 {@link PermissionDenyContext}에 {@code required}/{@code have}를 기록 →
 * {@code GlobalExceptionHandler}가 {@code 403 PERMISSION_DENIED} 본문(docs/03 §3.6) 구성에 사용.
 * 기록은 최종 deny 시 1회만.
 */
@Component
public class IbizDrivePermissionEvaluator implements PermissionEvaluator {

    private static final String RESOURCE_TYPE_FOLDER = "folder";
    private static final String RESOURCE_TYPE_FILE = "file";

    private final PermissionService permissionService;
    private final PermissionResolver permissionResolver;

    public IbizDrivePermissionEvaluator(PermissionService permissionService,
                                        PermissionResolver permissionResolver) {
        this.permissionService = permissionService;
        this.permissionResolver = permissionResolver;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Object target, Object permission) {
        // 단일 인자 형태 — 본 프로젝트 docs/02 §7 패턴은 항상 (id, type, permission) 3-인자형이라
        // 미사용. 안전 fallback으로 deny.
        return false;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId,
                                 String targetType, Object permission) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof IbizDriveUserDetails uds)) {
            return false;
        }

        Permission required;
        try {
            required = Permission.from(String.valueOf(permission));
        } catch (IllegalArgumentException ex) {
            // SpEL이 잘못된 권한 문자열을 전달 — 코드 버그 가능성. 안전상 deny + ThreadLocal 미기록.
            return false;
        }

        Role role = uds.getUser().getRole();

        // 1) ROLE 경로 — A3 보존. ADMIN(all) / AUDITOR(READ) 즉시 grant.
        if (permissionService.effectivePermissions(role).contains(required)) {
            return true;
        }

        // 2) Resource-level 경로 — folder/file + UUID 파싱 가능 시.
        if (isResourceTarget(targetType)) {
            UUID resourceId = parseUuid(targetId);
            if (resourceId != null) {
                boolean granted = permissionResolver.isGranted(
                    uds.getUser().getId(), targetType, resourceId, required
                );
                if (granted) {
                    return true;
                }
            }
        }

        // 3) 최종 deny — DenyContext 기록 후 false.
        PermissionDenyContext.record(required, role, permissionService.effectivePermissions(role));
        return false;
    }

    private static boolean isResourceTarget(String targetType) {
        return RESOURCE_TYPE_FOLDER.equals(targetType) || RESOURCE_TYPE_FILE.equals(targetType);
    }

    /**
     * SpEL이 전달하는 {@code targetId}는 {@code String} (PathVariable) 또는 {@code UUID}
     * (route param 타입에 따라 달라짐). 둘 다 흡수하고 그 외는 null로 응답해 resource-level
     * 평가를 skip한다 — A3 통합 테스트의 비-UUID id ("abc") 호환.
     */
    private static UUID parseUuid(Serializable targetId) {
        if (targetId instanceof UUID uuid) {
            return uuid;
        }
        if (targetId instanceof String str) {
            try {
                return UUID.fromString(str);
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
        return null;
    }

    /**
     * A11 — 사용자×노드의 effective 권한 집합 평가 (docs/02 §7.10 line 1173).
     *
     * <p>{@code hasPermission}이 권한 1개 단위 boolean을 반환하는 반면, 본 메서드는 9개 {@link Permission}
     * 전체를 평가하여 grant된 것만 모은 {@link Set}을 반환한다. 평가 정책은 {@code hasPermission}과
     * 동일하나 9× 반복 비용을 줄이기 위해 다음 단축을 적용한다:
     * <ul>
     *   <li>{@code user == null} → empty set (미인증 안전).</li>
     *   <li>{@link Role#ADMIN} → role 단계에서 9개 모두 grant이므로 {@link PermissionResolver} 미호출 early return.</li>
     *   <li>role-only 모드 ({@code resourceType == null || resourceId == null}) → role 권한만 반환.</li>
     *   <li>resource-level 모드 — role이 이미 grant한 권한은 resolver 미호출 (skip).
     *       {@link Permission#PURGE}는 Preset에 미포함 (docs/03 line 331~334) → resource grant로 부여 불가하므로 skip.</li>
     * </ul>
     *
     * <p>본 메서드는 {@link PermissionDenyContext}에 기록하지 않는다 — 본 endpoint는 read-only 정보 조회이며
     * 거부 envelope을 만들지 않는다.
     */
    public Set<Permission> resolveAll(IbizDriveUserDetails user, String resourceType, UUID resourceId) {
        if (user == null) {
            return EnumSet.noneOf(Permission.class);
        }

        Role role = user.getUser().getRole();
        Set<Permission> rolePermissions = permissionService.effectivePermissions(role);

        // role-only 모드 또는 ADMIN early return.
        if (resourceType == null || resourceId == null || role == Role.ADMIN) {
            EnumSet<Permission> snapshot = EnumSet.noneOf(Permission.class);
            snapshot.addAll(rolePermissions);
            return snapshot;
        }

        EnumSet<Permission> result = EnumSet.noneOf(Permission.class);
        result.addAll(rolePermissions);
        UUID userId = user.getUser().getId();
        for (Permission p : Permission.values()) {
            if (result.contains(p)) continue;            // role이 이미 grant.
            if (p == Permission.PURGE) continue;         // Preset 미포함 — resource grant로 부여 불가.
            if (permissionResolver.isGranted(userId, resourceType, resourceId, p)) {
                result.add(p);
            }
        }
        return result;
    }
}
