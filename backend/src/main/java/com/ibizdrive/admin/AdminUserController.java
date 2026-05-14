package com.ibizdrive.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.approval.ApprovalRequiredException;
import com.ibizdrive.approval.PendingAdminApproval;
import com.ibizdrive.approval.PendingApprovalService;
import com.ibizdrive.approval.handlers.RoleChangeApprovalHandler;
import com.ibizdrive.approval.handlers.RoleChangePayload;
import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.User;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    /** dual-approval framework default TTL (ADR #47). */
    private static final int APPROVAL_TTL_DAYS = 7;

    private final AdminUserService adminUserService;
    private final PendingApprovalService approvalService;
    private final ObjectMapper objectMapper;
    private final boolean dualApprovalRoleChangeEnabled;

    public AdminUserController(
        AdminUserService adminUserService,
        PendingApprovalService approvalService,
        ObjectMapper objectMapper,
        @Value("${app.dual-approval.role-change.enabled:false}") boolean dualApprovalRoleChangeEnabled
    ) {
        this.adminUserService = adminUserService;
        this.approvalService = approvalService;
        this.objectMapper = objectMapper;
        this.dualApprovalRoleChangeEnabled = dualApprovalRoleChangeEnabled;
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
     * admin-user-mgmt + admin-user-search-update — 사용자 목록 (paginated, 검색 옵션).
     * docs/02 §7.4.
     *
     * <p>{@code page} default 0, {@code size} default 50, max 200 — 무한 페이지 방지.
     * {@code Pageable} 자동 주입 대신 수동 변환 — Spring의 `spring.data.web.pageable.*`
     * 글로벌 설정 의존을 줄이고 본 endpoint의 한도를 명시적으로 코드에 노출.
     *
     * <p>{@code q}는 옵션. blank/null이면 전체 목록. 비어있지 않으면 email/displayName 부분
     * 매칭(case-insensitive). LIKE escape는 service 단계에서 처리 (Wave 1 — T1).
     *
     * <p>응답 본문은 {@link Page#map}으로 entity → DTO 변환된 {@link Page} 직렬화 결과
     * ({@code content[]}, {@code totalElements}, {@code totalPages}, {@code number}, {@code size}).
     * 프론트는 {@code content}를 사용하고 {@code totalElements}로 페이지네이션 계산.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<AdminUserSummaryResponse> list(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "50") int size,
                                               @RequestParam(required = false) String q) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 200);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        return adminUserService.list(pageable, q).map(AdminUserSummaryResponse::from);
    }

    /**
     * admin-user-mgmt + admin-user-search-update — 사용자 ROLE/활성 상태/표시 이름 변경.
     * docs/02 §7.4. (Wave 1 — T1)
     *
     * <p>body는 {@code {role?, isActive?, displayName?}} — 모든 필드 옵션이지만 모두 null이면
     * {@link AdminBadPatchException} → 400 VALIDATION_ERROR. 여러 필드를 동시에 보내면 순서대로
     * 적용 (role → isActive → displayName) — 각 단계가 별도 service 메서드/트랜잭션이므로
     * 두 audit row가 분리 emit된다 (의도). UI상 동시 변경 케이스는 드물지만 의미 명확.
     *
     * <p>분기:
     * <ul>
     *   <li>{@code role}: changeRole → {@code admin.role.changed} audit. self-demote 차단.</li>
     *   <li>{@code isActive=true}: reactivate → {@code admin.user.updated} audit (재활성).</li>
     *   <li>{@code isActive=false}: deactivate → {@code admin.user.deactivated} audit (제재). self 차단.</li>
     *   <li>{@code displayName}: changeDisplayName → {@code admin.user.updated} audit (편집).</li>
     * </ul>
     *
     * <p>HTTP 매트릭스:
     * <ul>
     *   <li>200 OK — 적용 후 최종 사용자 상태 ({@link AdminUserSummaryResponse})</li>
     *   <li>400 VALIDATION_ERROR — body 모든 필드 null 또는 displayName blank/length>100</li>
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
            throw new AdminBadPatchException("at least one of role/isActive/displayName must be provided");
        }

        UUID actorId = principal.getUser().getId();
        User updated = null;
        if (req.role() != null) {
            if (dualApprovalRoleChangeEnabled) {
                // ADR #47 Phase 3b — 게이트 활성 시 role 변경은 단독 mutation이어야 한다.
                // isActive/displayName 동반은 mixed-response 모호성을 만들어 거부 (400).
                if (req.isActive() != null || req.displayName() != null) {
                    throw new AdminBadPatchException(
                        "when dual-approval for role_change is enabled, role must be the only field "
                      + "in the request body");
                }
                User current = adminUserService.get(id);
                String payloadJson = serializeRolePayload(id, current.getRole(), req.role(), /*reason*/ null);
                PendingAdminApproval pending = approvalService.submit(
                    RoleChangeApprovalHandler.ACTION_TYPE,
                    payloadJson,
                    actorId,
                    APPROVAL_TTL_DAYS);
                throw new ApprovalRequiredException(pending.getId(), pending.getExpiresAt());
            }
            updated = adminUserService.changeRole(id, req.role(), actorId);
        }
        if (Boolean.TRUE.equals(req.isActive())) {
            updated = adminUserService.reactivate(id, actorId);
        } else if (Boolean.FALSE.equals(req.isActive())) {
            updated = adminUserService.deactivate(id, actorId);
        }
        if (req.displayName() != null) {
            updated = adminUserService.changeDisplayName(id, req.displayName(), actorId);
        }
        // 위 분기 중 하나는 반드시 진입 — isEmpty 가드로 보장됨.
        return AdminUserSummaryResponse.from(updated);
    }

    /**
     * admin-user-lock-unlock — 관리자 수동 사용자 잠금. docs/02 §7.12.
     *
     * <p>body 없음 (KISS — v1.x는 reason 미수집). 멱등: 이미 lock된 user는 NOP + 200.
     * 본인이 본인 잠금 시도는 SELF_PROTECTION 403 — 즉시 로그아웃 + 재로그인 불가 회피.
     *
     * <ul>
     *   <li>200 OK — 잠금 적용 후 최종 사용자 상태 ({@link AdminUserSummaryResponse})</li>
     *   <li>401 — 미인증</li>
     *   <li>403 — ROLE_ADMIN 부재 (@PreAuthorize) 또는 SELF_PROTECTION (self-lock)</li>
     *   <li>404 USER_NOT_FOUND — target 미존재</li>
     * </ul>
     */
    @PostMapping("/{id}/lock")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminUserSummaryResponse lock(@PathVariable UUID id,
                                         @AuthenticationPrincipal IbizDriveUserDetails principal) {
        User locked = adminUserService.lockUser(id, principal.getUser().getId());
        return AdminUserSummaryResponse.from(locked);
    }

    /**
     * admin-user-lock-unlock — 관리자 수동 사용자 잠금 해제. docs/02 §7.12.
     *
     * <p>self-protection 불필요 — 본인 unlock 케이스는 본인이 lock된 상태에선 도달 불가. 멱등.
     *
     * <ul>
     *   <li>204 No Content — 잠금 해제 적용 (응답 body 없음)</li>
     *   <li>401 / 403 / 404 — lock과 동일</li>
     * </ul>
     */
    @DeleteMapping("/{id}/lock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> unlock(@PathVariable UUID id,
                                       @AuthenticationPrincipal IbizDriveUserDetails principal) {
        adminUserService.unlockUser(id, principal.getUser().getId());
        return ResponseEntity.noContent().build();
    }

    private String serializeRolePayload(UUID userId,
                                        com.ibizdrive.user.Role fromRole,
                                        com.ibizdrive.user.Role toRole,
                                        String reason) {
        try {
            return objectMapper.writeValueAsString(new RoleChangePayload(userId, fromRole, toRole, reason));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("role_change payload serialize failed", e);
        }
    }
}
