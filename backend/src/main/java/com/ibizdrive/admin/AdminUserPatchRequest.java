package com.ibizdrive.admin;

import com.ibizdrive.user.Role;

/**
 * Admin user patch 요청 body — admin-user-mgmt + admin-user-search-update (Wave 1 — T1).
 *
 * <p>모든 필드 nullable — 호출자는 변경하려는 필드만 채워 보낸다. 모두 null이면
 * controller 단계에서 {@code 400 VALIDATION_ERROR}로 거부 (Bean Validation은 cross-field
 * 검증을 record 위에서 표현하기 까다로우므로 controller에서 명시적으로 검사).
 *
 * <p>분기 (Wave 1 — T1, docs/02 §7.4):
 * <ul>
 *   <li>{@code role}: {@link AdminUserService#changeRole} → {@code admin.role.changed} audit</li>
 *   <li>{@code isActive=false}: {@link AdminUserService#deactivate} → {@code admin.user.deactivated} audit (제재)</li>
 *   <li>{@code isActive=true}: {@link AdminUserService#reactivate} → {@code admin.user.updated} audit (재활성)</li>
 *   <li>{@code displayName}: {@link AdminUserService#changeDisplayName} → {@code admin.user.updated} audit (편집)</li>
 * </ul>
 */
public record AdminUserPatchRequest(
    Role role,
    Boolean isActive,
    String displayName
) {
    /**
     * 모두 null이면 의미 없는 요청 — controller에서 호출.
     */
    public boolean isEmpty() {
        return role == null && isActive == null && displayName == null;
    }
}
