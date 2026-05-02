package com.ibizdrive.admin;

import com.ibizdrive.user.IbizDriveUserDetails;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin user management endpoint — ADR #21 (admin 트랙 closure), docs/02 §7.4 / docs/03 §2.8.
 *
 * <p>본 컨트롤러는 {@code POST /api/admin/users} 한 endpoint만 노출한다 — 사용자 목록/role 변경 등은
 * 본 트랙 범위 외 (v1.x).
 *
 * <p>가드: {@code @PreAuthorize("hasRole('ADMIN')")} — Spring Security 표준 RoleVoter가 평가.
 * {@code SecurityConfig}는 {@code anyRequest().authenticated()}로 미인증 401을 차단하고, 본
 * method-level 가드가 MEMBER/AUDITOR 등 비-ADMIN 인증 사용자에 대해 403을 반환한다.
 *
 * <p>CSRF: 표준 double-submit (XSRF-TOKEN 쿠키 + X-CSRF-Token 헤더). signup/forgot/reset과 달리
 * 인증된 admin 호출이므로 토큰 보유 가정이 자연스러움 — SecurityConfig의 ignore 매처에 포함하지 않는다.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminInviteUserResponse invite(@Valid @RequestBody AdminInviteUserRequest req,
                                          @AuthenticationPrincipal IbizDriveUserDetails principal) {
        return adminUserService.invite(
            req.email(),
            req.displayName(),
            req.role(),
            principal.getUser().getId()
        );
    }
}
