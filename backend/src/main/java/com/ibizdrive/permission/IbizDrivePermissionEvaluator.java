package com.ibizdrive.permission;

import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.Role;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;
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
}
