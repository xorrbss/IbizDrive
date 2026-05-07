package com.ibizdrive.admin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * admin-storage-overview — `GET /api/admin/storage/overview` 단일 read-only 엔드포인트.
 *
 * <p>{@link AdminDepartmentController} 패턴 답습. method-level
 * {@code @PreAuthorize("hasRole('ADMIN')")}이 보안 진실의 출처 — UX guard(프론트
 * {@code AdminGuard})는 보조 (CLAUDE.md §3 원칙 10).
 *
 * <p>HTTP status 매트릭스:
 * <ul>
 *   <li>200 OK — 합계 + orphan cleanup summary</li>
 *   <li>401 — 미인증 (Spring Security entry point)</li>
 *   <li>403 — 인증되었으나 ROLE_ADMIN 부재</li>
 * </ul>
 *
 * <p>read-only이므로 4xx body 외 별도 에러 코드 없음 (audit 미발행, mutation 없음).
 */
@RestController
@RequestMapping("/api/admin/storage")
public class AdminStorageController {

    private final AdminStorageService adminStorageService;

    public AdminStorageController(AdminStorageService adminStorageService) {
        this.adminStorageService = adminStorageService;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminStorageOverviewResponse getOverview() {
        return new AdminStorageOverviewResponse(adminStorageService.loadOverview());
    }
}
