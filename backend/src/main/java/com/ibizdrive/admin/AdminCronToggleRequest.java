package com.ibizdrive.admin;

import jakarta.validation.constraints.NotNull;

/**
 * `PUT /api/admin/system/cron/{key}` 요청 body — admin-cron-policy-toggle.
 *
 * <p>{@code enabled}는 boolean이므로 명시적 값 강제(NotNull). 누락 시 글로벌 핸들러가 400 반환.
 */
public record AdminCronToggleRequest(
    @NotNull Boolean enabled
) {
}
