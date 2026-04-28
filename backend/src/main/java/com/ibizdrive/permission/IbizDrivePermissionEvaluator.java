package com.ibizdrive.permission;

import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.Role;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * Spring Security {@link PermissionEvaluator} 구현 — SpEL {@code hasPermission(target, permission)}
 * 및 {@code hasPermission(targetId, targetType, permission)} 호출의 backing logic.
 *
 * <p>호출 시그니처는 docs/02 §7.10의 Guard 패턴 ({@code hasPermission(#id, 'folder', 'READ')})에
 * 맞춘다. A3 MVP에서는 {@code targetType}/{@code targetId}를 받지만 {@link PermissionService}로
 * 위임하는 user-level 평가 (ADR #26) — A4에서 본 클래스 내부만 교체하면 호출처는 그대로.
 *
 * <p>Deny 판정 시 {@link PermissionDenyContext}에 {@code required}/{@code have}를 기록 →
 * {@code GlobalExceptionHandler}가 {@code 403 PERMISSION_DENIED} 본문(docs/03 §3.6) 구성에 사용.
 */
@Component
public class IbizDrivePermissionEvaluator implements PermissionEvaluator {

    private final PermissionService permissionService;

    public IbizDrivePermissionEvaluator(PermissionService permissionService) {
        this.permissionService = permissionService;
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
        boolean granted = permissionService.check(
            uds.getUser().getId(), role, targetType, targetId, required
        );
        if (!granted) {
            PermissionDenyContext.record(required, role, permissionService.effectivePermissions(role));
        }
        return granted;
    }
}
