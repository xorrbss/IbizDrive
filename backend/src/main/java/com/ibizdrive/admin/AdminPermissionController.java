package com.ibizdrive.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin permission matrix endpoint — admin-permission-matrix (Wave 2 T5), docs/02 §7.x.
 *
 * <p>read-only viewer — audit emit 없음. method-level {@code @PreAuthorize("hasRole('ADMIN')")}
 * 가 보안 진실의 출처 — UX guard(프론트 {@code AdminGuard})는 보조.
 *
 * <p>HTTP status 매트릭스:
 * <ul>
 *   <li>200 OK — list 성공</li>
 *   <li>400 VALIDATION_ERROR — filter 검증 실패 (서비스 {@code IllegalArgumentException}
 *       → {@link com.ibizdrive.common.error.GlobalExceptionHandler})</li>
 *   <li>401 — 미인증 (Spring Security entry point)</li>
 *   <li>403 — 인증되었으나 ROLE_ADMIN 부재 ({@code @PreAuthorize})</li>
 * </ul>
 *
 * <p>page default 0, size default 20, max 100 — {@link AdminDepartmentController#list} 보다
 * 좁은 한도 (row가 LEFT JOIN heavy → 응답 비용 보호).
 */
@RestController
@RequestMapping("/api/admin/permissions")
public class AdminPermissionController {

    private final AdminPermissionService adminPermissionService;

    public AdminPermissionController(AdminPermissionService adminPermissionService) {
        this.adminPermissionService = adminPermissionService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<AdminPermissionRowResponse> list(
        @RequestParam(required = false) String subjectType,
        @RequestParam(required = false) UUID subjectId,
        @RequestParam(required = false) String resourceType,
        @RequestParam(required = false) String preset,
        @RequestParam(required = false) String q,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        AdminPermissionService.Filters filters = new AdminPermissionService.Filters(
            subjectType, subjectId, resourceType, preset, q
        );
        return adminPermissionService.list(filters, pageable);
    }
}
