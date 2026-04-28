package com.ibizdrive.security;

import com.ibizdrive.user.IbizDriveUserDetails;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * Spring Security {@code hasPermission(...)} bridge.
 */
@Component
public class IbizDrivePermissionEvaluator implements PermissionEvaluator {

    private final PermissionService permissionService;

    public IbizDrivePermissionEvaluator(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        return false;
    }

    @Override
    public boolean hasPermission(Authentication authentication,
                                 Serializable targetId,
                                 String targetType,
                                 Object permission) {
        Permission parsedPermission = parsePermission(permission);
        IbizDriveUserDetails principal = principal(authentication);
        return permissionService.check(
            principal,
            targetType,
            targetId == null ? null : targetId.toString(),
            parsedPermission
        );
    }

    private static IbizDriveUserDetails principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof IbizDriveUserDetails principal)) {
            return null;
        }
        return principal;
    }

    private static Permission parsePermission(Object permission) {
        if (permission instanceof Permission p) {
            return p;
        }
        if (!(permission instanceof String raw)) {
            return null;
        }
        try {
            return Permission.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
