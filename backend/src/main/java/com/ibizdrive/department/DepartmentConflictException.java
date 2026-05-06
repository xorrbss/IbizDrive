package com.ibizdrive.department;

/**
 * 부서 생성/이름 변경 시 동일 활성 부서명이 존재 — admin-department-crud (Wave 2 T4).
 *
 * <p>HTTP 409 + body {@code { code: "DEPARTMENT_CONFLICT" }}로 변환 ({@link com.ibizdrive.common.error.GlobalExceptionHandler}).
 *
 * <p>DB 제약 (V9 {@code idx_departments_name_active} partial unique on {@code name WHERE deleted_at IS NULL})이
 * 진실의 출처. 서비스 사전 조회는 빠른 경로 — race condition 시 INSERT가 unique 위반으로 실패하면
 * 동일 envelope code로 매핑되어야 한다 (현재는 사전 조회만 — race window 매우 좁아 MVP 허용,
 * {@link com.ibizdrive.auth.DuplicateEmailException}와 동일 정책).
 */
public class DepartmentConflictException extends RuntimeException {
    public DepartmentConflictException() {
        super("Department name conflict");
    }
}
