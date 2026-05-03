package com.ibizdrive.admin;

import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.User;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoint — m-admin-entry-rewrite P6, ADR #21 closure. docs/02 §7.4.
 *
 * <p>UX 가드(프론트 {@code AdminGuard})와 별개로 본 controller가 진실의 출처(보안 가드).
 * {@code @PreAuthorize("hasRole('ADMIN')")}로 method-level 가드 — MEMBER/AUDITOR/anonymous는 모두 차단.
 *
 * <p>HTTP status 매트릭스 (docs/02 §7.4):
 * <ul>
 *   <li>200 OK — 정상 invite ({@link AdminInviteUserResponse})</li>
 *   <li>400 VALIDATION_ERROR — Bean Validation 실패 ({@link AdminInviteUserRequest} 위반)</li>
 *   <li>401 — 미인증 (Spring Security {@code HttpStatusEntryPoint})</li>
 *   <li>403 — 인증되었으나 ROLE_ADMIN 부재 ({@code @PreAuthorize})</li>
 *   <li>409 CONFLICT/DUPLICATE_EMAIL — {@link com.ibizdrive.auth.DuplicateEmailException}
 *       ({@link com.ibizdrive.common.error.AuthExceptionHandler})</li>
 * </ul>
 *
 * <p>actor id는 {@link AuthenticationPrincipal}에서 주입된 {@link IbizDriveUserDetails}로부터 추출하여
 * {@link AdminUserService#invite}에 전달 — audit_log row의 actor_id로 기록.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AdminInviteUserResponse invite(@Valid @RequestBody AdminInviteUserRequest req,
                                          @AuthenticationPrincipal IbizDriveUserDetails principal) {
        User created = adminUserService.invite(
            req.email(), req.displayName(), req.role(), principal.getUser().getId()
        );
        return AdminInviteUserResponse.from(created);
    }
}
