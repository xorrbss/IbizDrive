package com.ibizdrive.admin;

import com.ibizdrive.department.Department;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Admin department list/응답 항목 — admin-department-crud (Wave 2 T4), docs/02 §7.x.
 *
 * <p>{@link AdminUserSummaryResponse}와 1:1 패턴 mirror — list/생성/수정 응답 공용으로 사용.
 * {@code isActive}는 {@link Department#isActive()}로 도출 ({@code deletedAt} 기준).
 *
 * <p>{@code deletedAt}은 일부러 비공개 — UI는 {@code isActive} boolean만 사용.
 * (audit_log에 저장되는 도메인 상태와 분리, frontend 단일 진실 원칙.)
 */
public record AdminDepartmentSummaryResponse(
    UUID id,
    String name,
    boolean isActive,
    OffsetDateTime createdAt
) {
    public static AdminDepartmentSummaryResponse from(Department d) {
        return new AdminDepartmentSummaryResponse(
            d.getId(),
            d.getName(),
            d.isActive(),
            d.getCreatedAt()
        );
    }
}
