package com.ibizdrive.security;

import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.Role;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

/**
 * 권한 평가 단일 진입점.
 *
 * <p>A3 범위는 시스템 ROLE 기반의 얇은 seam만 제공한다. 폴더/파일별 권한 row와 상속 평가는
 * 실제 도메인 테이블 도입 후 이 클래스 내부에서 확장한다.
 */
@Service
public class PermissionService {

    private static final Set<String> SUPPORTED_TARGET_TYPES = Set.of("folder", "file");

    public boolean check(IbizDriveUserDetails principal,
                         String targetType,
                         String targetId,
                         Permission permission) {
        if (principal == null || permission == null || !isSupportedTargetType(targetType) || isBlank(targetId)) {
            return false;
        }

        Role role = principal.getUser().getRole();
        if (role == Role.ADMIN) {
            return true;
        }
        if (role == Role.AUDITOR) {
            return permission == Permission.READ;
        }
        return false;
    }

    private static boolean isSupportedTargetType(String targetType) {
        if (targetType == null) {
            return false;
        }
        return SUPPORTED_TARGET_TYPES.contains(targetType.toLowerCase(Locale.ROOT));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
