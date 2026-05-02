package com.ibizdrive.admin;

import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;

import java.util.UUID;

/**
 * Admin invite 응답 DTO — ADR #21 (admin 트랙 closure).
 *
 * <p><b>임시 PW 비노출 invariant</b>: 본 record에는 {@code tempPassword}/{@code password}/{@code hash}
 * 필드를 추가하면 안 된다. 임시 PW는 이메일 채널로만 전달되며 응답/로그/예외 메시지/git 히스토리에
 * 절대 등장하지 않는다 (context.md §"중요한 의사결정" 2). {@link AdminUserServiceTest#invite_returnsResponseWithoutTempPassword}가
 * 직렬화 결과에 PW 관련 키 부재를 강제한다.
 */
public record AdminInviteUserResponse(
    UUID id,
    String email,
    String displayName,
    Role role,
    boolean mustChangePassword
) {
    public static AdminInviteUserResponse from(User user) {
        return new AdminInviteUserResponse(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getRole(),
            user.isMustChangePassword()
        );
    }
}
