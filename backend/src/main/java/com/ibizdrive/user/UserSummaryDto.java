package com.ibizdrive.user;

import java.util.UUID;

/**
 * A14 — 사용자 검색 응답 DTO. {@code GET /api/users/search} 결과 row.
 *
 * <p>Minimal surface — id/displayName/email만 노출. role/createdAt/department 등은 share scope
 * 결정에 불요 (ADR #35). subject picker UI는 displayName 매칭 + email tooltip 패턴.
 */
public record UserSummaryDto(UUID id, String displayName, String email) {

    public static UserSummaryDto fromEntity(User user) {
        return new UserSummaryDto(user.getId(), user.getDisplayName(), user.getEmail());
    }
}
