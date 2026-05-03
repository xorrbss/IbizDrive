package com.ibizdrive.admin;

import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;

import java.util.UUID;

/**
 * Admin invite 응답 DTO — m-admin-entry-rewrite P6, docs/02 §7.4.
 *
 * <p><b>임시 PW 비노출 정책 (docs/03 §2.8)</b>: 본 record에는 {@code tempPassword} 필드가 부재한다.
 * 임시 비밀번호는 invite email 본문에만 포함되며 응답/로그/audit detail/예외 메시지 어디에도 노출되지 않는다.
 * 본 정책은 {@link AdminUserServiceTest}와 {@code AdminUserControllerTest}의 회귀 테스트로 강제된다.
 */
public record AdminInviteUserResponse(
    UUID id,
    String email,
    String displayName,
    Role role,
    boolean mustChangePassword
) {
    public static AdminInviteUserResponse from(User u) {
        return new AdminInviteUserResponse(
            u.getId(),
            u.getEmail(),
            u.getDisplayName(),
            u.getRole(),
            u.isMustChangePassword()
        );
    }
}
