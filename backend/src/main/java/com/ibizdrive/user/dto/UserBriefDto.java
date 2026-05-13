package com.ibizdrive.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ibizdrive.user.User;

import java.util.UUID;

/**
 * {@link User} 의 wire brief — 표시명 + email 만 노출. RightPanel detail에서 file owner 등 user 참조를
 * frontend로 내보낼 때 사용 (full {@link com.ibizdrive.admin.AdminUserSummaryResponse}의 admin-전용
 * 컬럼은 제외).
 *
 * <p>RBAC 일반 사용자가 보는 데이터 — email + displayName만 안전 (role / quota / lastLoginAt 등 admin 전용
 * 컬럼은 누출 금지).
 *
 * <p>soft-deleted/missing user lookup 실패 시 호출자가 {@code null} 응답 (FE는 placeholder 표시).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserBriefDto(
    UUID id,
    String displayName,
    String email
) {
    public static UserBriefDto from(User u) {
        return new UserBriefDto(u.getId(), u.getDisplayName(), u.getEmail());
    }
}
