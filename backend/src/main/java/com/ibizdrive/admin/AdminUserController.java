package com.ibizdrive.admin;

import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.User;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

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

    /**
     * admin-user-mgmt — 사용자 목록 (paginated). docs/02 §7.4.
     *
     * <p>{@code page} default 0, {@code size} default 50, max 200 — 무한 페이지 방지.
     * {@code Pageable} 자동 주입 대신 수동 변환 — Spring의 `spring.data.web.pageable.*`
     * 글로벌 설정 의존을 줄이고 본 endpoint의 한도를 명시적으로 코드에 노출.
     *
     * <p>응답 본문은 {@link Page#map}으로 entity → DTO 변환된 {@link Page} 직렬화 결과
     * ({@code content[]}, {@code totalElements}, {@code totalPages}, {@code number}, {@code size}).
     * 프론트는 {@code content}를 사용하고 {@code totalElements}로 페이지네이션 계산.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<AdminUserSummaryResponse> list(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "50") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 200);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        return adminUserService.list(pageable).map(AdminUserSummaryResponse::from);
    }

    /**
     * admin-user-mgmt — 사용자 ROLE/활성 상태 변경. docs/02 §7.4.
     *
     * <p>body는 {@code {role?, isActive?}} — 둘 다 옵션이지만 둘 다 null이면
     * {@link AdminBadPatchException} → 400 VALIDATION_ERROR. 둘 다 보내면 role 먼저 적용 후
     * deactivate 적용 — UI상 동시 변경 케이스는 드물지만 race-free 단일 트랜잭션 보장은
     * service 단계에서 각각 별도 트랜잭션이므로 두 audit row가 분리 emit된다 (의도).
     *
     * <p>{@code isActive=false}만 의미 — {@code true}는 (재활성 미지원이므로) no-op으로 흡수
     * (service deactivate는 false→true 전환 미지원).
     *
     * <p>HTTP 매트릭스:
     * <ul>
     *   <li>200 OK — 적용 후 최종 사용자 상태 ({@link AdminUserSummaryResponse})</li>
     *   <li>400 VALIDATION_ERROR — body 빈 객체 ({@code role}/{@code isActive} 둘 다 null)</li>
     *   <li>401 — 미인증</li>
     *   <li>403 SELF_PROTECTION — actor==target self-demote/self-deactivate</li>
     *   <li>404 USER_NOT_FOUND — target 미존재</li>
     * </ul>
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserSummaryResponse patch(@PathVariable UUID id,
                                          @RequestBody AdminUserPatchRequest req,
                                          @AuthenticationPrincipal IbizDriveUserDetails principal) {
        if (req == null || req.isEmpty()) {
            throw new AdminBadPatchException("at least one of role/isActive must be provided");
        }
        // 본 트랙은 deactivate만 노출 — isActive=true(재활성)는 미지원, 명시적 400으로 차단.
        // 클라이언트가 의도적으로 토글하려는 경우 v1.x에서 별도 endpoint로 노출 예정.
        if (Boolean.TRUE.equals(req.isActive())) {
            throw new AdminBadPatchException("reactivation (isActive=true) is not supported");
        }

        UUID actorId = principal.getUser().getId();
        User updated = null;
        if (req.role() != null) {
            updated = adminUserService.changeRole(id, req.role(), actorId);
        }
        if (Boolean.FALSE.equals(req.isActive())) {
            updated = adminUserService.deactivate(id, actorId);
        }
        // 위 두 분기 중 하나는 반드시 진입 — isEmpty + isActive==true 가드로 보장됨.
        return AdminUserSummaryResponse.from(updated);
    }
}
