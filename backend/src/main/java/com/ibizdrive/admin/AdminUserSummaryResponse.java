package com.ibizdrive.admin;

import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Admin user list 응답 항목 — admin-user-mgmt, docs/02 §7.4.
 *
 * <p>{@link AdminInviteUserResponse}와 별개 record — invite 응답은
 * {@code mustChangePassword}만 노출하지만, 목록에서는 {@code isActive}/{@code lastLoginAt}이
 * UI 결정(재활성/role 변경 가능 여부)에 필요하다. 두 DTO를 합치지 않는 이유: invite 응답
 * 의미(첫 로그인 강제 변경 안내)와 list 항목 의미(현재 상태 스냅샷)가 다르다.
 *
 * <p><b>비밀번호 hash 비노출</b> (docs/03 §2.8): {@code passwordHash} 필드 부재 — list/admin 노출 응답
 * 어디에도 포함 금지.
 *
 * <p>quota mutation Phase 4 (`docs/04 §6.1`): {@code storageQuota}/{@code storageUsed} 추가 —
 * `/admin/members` 페이지의 quota 컬럼 + 인라인 editor에서 사용. Phase 3 endpoint
 * `GET /api/admin/users/{id}/quota`와 동일 값을 list 응답에 미리 실어 행 수 만큼의 추가 fetch를
 * 회피한다. mutation 후에는 admin list 캐시 prefix 무효화로 갱신.
 */
public record AdminUserSummaryResponse(
    UUID id,
    String email,
    String displayName,
    Role role,
    boolean isActive,
    OffsetDateTime createdAt,
    OffsetDateTime lastLoginAt,
    long storageQuota,
    long storageUsed
) {
    public static AdminUserSummaryResponse from(User u) {
        return new AdminUserSummaryResponse(
            u.getId(),
            u.getEmail(),
            u.getDisplayName(),
            u.getRole(),
            u.isActive(),
            u.getCreatedAt(),
            u.getLastLoginAt(),
            u.getStorageQuota(),
            u.getStorageUsed()
        );
    }
}
