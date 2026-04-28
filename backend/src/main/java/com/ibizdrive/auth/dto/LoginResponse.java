package com.ibizdrive.auth.dto;

import com.ibizdrive.user.User;

import java.util.List;

/**
 * {@code POST /api/auth/login} 응답 페이로드 — docs/02 §7.4 + ADR #22 + ADR #26.
 *
 * <p>{@code effectivePermissionsCacheKey}는 권한 변경 시 invalidate trigger.
 * A3.3부터 {@link com.ibizdrive.permission.PermissionCacheKeyService}가 산출한
 * SHA-256 hex prefix(16자)를 사용한다. 이전 {@code "userId:role:v0"} 평문 포맷은
 * 원본 식별자 노출 + 매트릭스 정의 변경 시 일괄 무효화 hook 부재 문제로 폐기 (ADR #26).
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

    /**
     * 도메인 User + 사전 계산된 cache key로 응답 빌드.
     * cache key 산출은 호출자가 {@link com.ibizdrive.permission.PermissionCacheKeyService}로 수행.
     * A1 범위에서는 부서·다중 role 미지원.
     */
    public static LoginResponse from(User u, String cacheKey) {
        UserInfo info = new UserInfo(
            u.getId().toString(),
            u.getEmail(),
            u.getDisplayName(),
            "human",            // ADR #21 — A1은 human 계정만, service 계정은 A4
            u.isMustChangePassword()
        );
        return new LoginResponse(info, List.of(), List.of(u.getRole().name()), cacheKey);
    }
}
