package com.ibizdrive.admin;

import com.ibizdrive.user.Role;

/**
 * Admin user patch 요청 body — admin-user-mgmt, docs/02 §7.4.
 *
 * <p>두 필드 모두 nullable — 둘 중 하나만 보내거나 둘 다 보낼 수 있다. 둘 다 null이면
 * controller 단계에서 {@code 400 VALIDATION_ERROR}로 거부 (Bean Validation은 cross-field
 * 검증을 record 위에서 표현하기 까다로우므로 controller에서 명시적으로 검사).
 *
 * <p>{@code isActive=false}만 의미가 있다 (UX는 비활성화만 노출 — v1.x 재활성). {@code true}는
 * 받더라도 {@link AdminUserService#deactivate}가 {@code false}만 처리하므로 no-op으로 흡수.
 */
public record AdminUserPatchRequest(
    Role role,
    Boolean isActive
) {
    /**
     * 둘 다 null이면 의미 없는 요청 — controller에서 호출.
     */
    public boolean isEmpty() {
        return role == null && isActive == null;
    }
}
