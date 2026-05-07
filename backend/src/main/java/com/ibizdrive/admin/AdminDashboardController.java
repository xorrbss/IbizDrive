package com.ibizdrive.admin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * admin-dashboard — `/admin` 진입 KPI 단일 read-only 엔드포인트.
 *
 * <p>{@link AdminDepartmentController} 패턴 답습. method-level
 * {@code @PreAuthorize("hasRole('ADMIN')")}가 보안 진실의 출처 — UX guard는 보조.
 *
 * <p>HTTP status 매트릭스:
 * <ul>
 *   <li>200 OK — KPI envelope</li>
 *   <li>401 — 미인증 (Spring Security entry point)</li>
 *   <li>403 — 인증되었으나 ROLE_ADMIN 부재 ({@code @PreAuthorize})</li>
 *   <li>500 — count/sum 중 일부 실패 (부분 응답 ❌, envelope 오염 방지)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminDashboardSummaryResponse summary() {
        AdminDashboardSummaryResponse.SummaryData data = adminDashboardService.getSummary();
        return new AdminDashboardSummaryResponse(data);
    }
}
