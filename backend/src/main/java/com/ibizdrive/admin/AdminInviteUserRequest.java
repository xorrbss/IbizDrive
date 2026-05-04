package com.ibizdrive.admin;

import com.ibizdrive.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Admin invite 요청 DTO — m-admin-entry-rewrite P6, docs/02 §7.4 {@code POST /api/admin/users}.
 *
 * <p>email은 trim+lowercase, displayName은 trim 처리를 service ({@link AdminUserService#invite})가 수행.
 * 본 DTO는 형식/필수 검증만 담당. role은 admin이 명시 지정 (signup의 first-user-ADMIN 분기 없음).
 *
 * <p>임시 PW는 서버가 {@link TempPasswordGenerator}로 생성 — request에 포함하지 않는다 (docs/03 §2.8).
 */
public record AdminInviteUserRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(max = 100) String displayName,
    @NotNull Role role
) {
}
