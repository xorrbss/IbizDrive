package com.ibizdrive.department;

import java.util.UUID;

/**
 * A16 — 부서 검색 응답 DTO. {@code GET /api/departments/search} 결과 row.
 *
 * <p>Minimal surface — id/name만 노출 (ADR #36). subject picker UI는 name 매칭.
 */
public record DepartmentSummaryDto(UUID id, String name) {

    public static DepartmentSummaryDto fromEntity(Department department) {
        return new DepartmentSummaryDto(department.getId(), department.getName());
    }
}
