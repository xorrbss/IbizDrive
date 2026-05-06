package com.ibizdrive.admin;

import jakarta.validation.constraints.Size;

/**
 * Admin department PATCH 요청 body — admin-department-crud (Wave 2 T4), docs/02 §7.x.
 *
 * <p>{@link AdminUserPatchRequest} 패턴 mirror — 두 필드 모두 nullable. 둘 다 null이면
 * controller에서 {@link AdminBadPatchException} → 400 VALIDATION_ERROR.
 *
 * <p><b>의미</b>:
 * <ul>
 *   <li>{@code name} != null → rename ({@link AdminDepartmentService#rename})</li>
 *   <li>{@code isActive=false} → deactivate ({@link AdminDepartmentService#deactivate})</li>
 *   <li>{@code isActive=true}  → reactivate ({@link AdminDepartmentService#reactivate})</li>
 * </ul>
 *
 * <p>user의 deactivate-only 정책과 다른 점: 부서는 재활성화도 노출한다 — 부서 lifecycle은
 * 인증 세션과 무관해 재활성에 보안 부담이 없고, 충돌(같은 활성 name)은 service가 사전 차단.
 */
public record AdminDepartmentPatchRequest(
    @Size(max = 100)
    String name,
    Boolean isActive
) {
    public boolean isEmpty() {
        return (name == null || name.isBlank()) && isActive == null;
    }
}
