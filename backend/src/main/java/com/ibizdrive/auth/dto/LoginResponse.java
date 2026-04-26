package com.ibizdrive.auth.dto;

import com.ibizdrive.user.User;

import java.util.List;

/**
 * {@code POST /api/auth/login} 응답 페이로드 — docs/02 §7.4 + ADR #22.
 *
 * <p>{@code effectivePermissionsCacheKey}는 권한 변경 시 invalidate trigger.
 * MVP에서는 {@code "userId:role:v0"} 단순 문자열 (권한 매트릭스 백엔드 도입 시 hash로 교체).
 * 본 임시값 결정은 dev/active/a1-auth-impl/a1-auth-impl-context.md에 기록.
 *
 * <p>{@code departments}는 A1.5 후속 (부서 모델) 도입 전까지 빈 배열.
 */
public record LoginResponse(
    UserInfo user,
    List<DepartmentInfo> departments,
    List<String> roles,
    String effectivePermissionsCacheKey
) {

    public record UserInfo(
        String id,
        String email,
        String name,
        String kind,
        boolean mustChangePassword
    ) {}

    public record DepartmentInfo(String id, String name, String path) {}

    /** 도메인 User 객체로부터 응답 빌드 — A1 범위에서는 부서·다중 role 미지원. */
    public static LoginResponse from(User u) {
        UserInfo info = new UserInfo(
            u.getId().toString(),
            u.getEmail(),
            u.getDisplayName(),
            "human",            // ADR #21 — A1은 human 계정만, service 계정은 A4
            u.isMustChangePassword()
        );
        String cacheKey = u.getId() + ":" + u.getRole().name() + ":v0";
        return new LoginResponse(info, List.of(), List.of(u.getRole().name()), cacheKey);
    }
}
