package com.ibizdrive.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Admin department 생성 요청 body — admin-department-crud (Wave 2 T4), docs/02 §7.x.
 *
 * <p>{@code name}은 trim 후 1~100자 (V7 컬럼 length=100). 검증 실패는 Bean Validation으로
 * 400 VALIDATION_ERROR로 자동 매핑 ({@link com.ibizdrive.common.error.GlobalExceptionHandler}).
 *
 * <p>실제 trim은 service에서 다시 수행 — null/blank만 Bean Validation에서 차단하고
 * 도메인 정규화(trim)는 service 책임으로 분리 ({@link AdminDepartmentService#create}).
 */
public record AdminDepartmentCreateRequest(
    @NotBlank
    @Size(max = 100)
    String name
) {
}
