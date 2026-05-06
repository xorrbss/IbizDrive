package com.ibizdrive.admin;

import com.ibizdrive.department.Department;
import com.ibizdrive.user.IbizDriveUserDetails;
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
 * Admin department endpoint — admin-department-crud (Wave 2 T4), docs/02 §7.x.
 *
 * <p>{@link AdminUserController} 패턴 1:1 mirror. method-level {@code @PreAuthorize("hasRole('ADMIN')")}
 * 가 보안 진실의 출처 — UX guard(프론트 {@code AdminGuard})는 보조.
 *
 * <p>HTTP status 매트릭스 (docs/02 §7.x):
 * <ul>
 *   <li>200 OK — list / create / patch 성공</li>
 *   <li>400 VALIDATION_ERROR — Bean Validation 또는 빈 PATCH body</li>
 *   <li>401 — 미인증 (Spring Security entry point)</li>
 *   <li>403 — 인증되었으나 ROLE_ADMIN 부재 ({@code @PreAuthorize})</li>
 *   <li>404 NOT_FOUND — patch target 부서 미존재
 *       ({@link com.ibizdrive.common.error.ResourceNotFoundException}
 *        → {@link com.ibizdrive.common.error.GlobalExceptionHandler})</li>
 *   <li>409 DEPARTMENT_CONFLICT — 같은 name 활성 부서 충돌
 *       ({@link com.ibizdrive.department.DepartmentConflictException})</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/departments")
public class AdminDepartmentController {

    private final AdminDepartmentService adminDepartmentService;

    public AdminDepartmentController(AdminDepartmentService adminDepartmentService) {
        this.adminDepartmentService = adminDepartmentService;
    }

    /**
     * admin 부서 목록 — paginated. {@code q}는 부서명 부분 일치(LIKE), 생략 시 전체.
     *
     * <p>{@code page} default 0, {@code size} default 50, max 200 — {@link AdminUserController#list}
     * 와 동일 한도.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<AdminDepartmentSummaryResponse> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size,
        @RequestParam(required = false) String q
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 200);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        return adminDepartmentService.list(pageable, q).map(AdminDepartmentSummaryResponse::from);
    }

    /**
     * admin 부서 신규 생성. body validation 통과 후 service에서 trim + 충돌 검사 + audit emit.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AdminDepartmentSummaryResponse create(
        @Valid @RequestBody AdminDepartmentCreateRequest req,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        Department created = adminDepartmentService.create(req.name(), principal.getUser().getId());
        return AdminDepartmentSummaryResponse.from(created);
    }

    /**
     * admin 부서 부분 수정. body는 {@code {name?, isActive?}} — 둘 다 null이면 400.
     *
     * <p>둘 다 보내면 rename → activate/deactivate 순서로 적용. 두 service 호출은
     * 별도 트랜잭션이므로 각각 audit row 분리 emit (의도, {@link AdminUserController#patch} 동일 패턴).
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminDepartmentSummaryResponse patch(
        @PathVariable UUID id,
        @Valid @RequestBody AdminDepartmentPatchRequest req,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        if (req == null || req.isEmpty()) {
            throw new AdminBadPatchException("at least one of name/isActive must be provided");
        }

        UUID actorId = principal.getUser().getId();
        Department updated = null;
        if (req.name() != null && !req.name().isBlank()) {
            updated = adminDepartmentService.rename(id, req.name(), actorId);
        }
        if (Boolean.FALSE.equals(req.isActive())) {
            updated = adminDepartmentService.deactivate(id, actorId);
        } else if (Boolean.TRUE.equals(req.isActive())) {
            updated = adminDepartmentService.reactivate(id, actorId);
        }
        return AdminDepartmentSummaryResponse.from(updated);
    }
}
