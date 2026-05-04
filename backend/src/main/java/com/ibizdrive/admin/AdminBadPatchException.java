package com.ibizdrive.admin;

/**
 * Admin user PATCH 요청 본문이 의미없거나 미지원 — admin-user-mgmt.
 *
 * <p>발생 케이스:
 * <ul>
 *   <li>body의 {@code role}/{@code isActive} 둘 다 null — 적용할 변경 없음.</li>
 *   <li>{@code isActive=true} (재활성) — 본 트랙은 deactivate만 노출, v1.x로 연기.</li>
 * </ul>
 *
 * <p>HTTP 400 + body {@code { code: "VALIDATION_ERROR", details: { field: "body", rule: <reason> } }}로
 * 매핑된다 ({@link com.ibizdrive.common.error.AdminExceptionHandler}). field={@code "body"}는
 * 단일 필드 위반이 아닌 cross-field 위반임을 표현 — 기존 Bean Validation 응답 스키마(field/rule)
 * 와 호환.
 */
public class AdminBadPatchException extends RuntimeException {
    public AdminBadPatchException(String reason) {
        super(reason);
    }
}
