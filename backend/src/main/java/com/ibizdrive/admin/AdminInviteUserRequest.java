package com.ibizdrive.admin;

import com.ibizdrive.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Admin invite 요청 DTO — ADR #21 (admin 트랙 closure), P2.
 *
 * <p>Bean Validation 제약은 {@code AuthExceptionHandler#validation}이
 * 400 {@code VALIDATION_ERROR} envelope (`details.field`)으로 매핑한다.
 *
 * <ul>
 *   <li>{@code email}: blank 금지, RFC 822 email 형식, 254자 이하 (DB 컬럼 길이와 일치).</li>
 *   <li>{@code displayName}: blank 금지(공백만 입력 차단), 1~100자 (User.display_name 컬럼).</li>
 *   <li>{@code role}: null 금지. Jackson이 wire string → enum 디코드 — 잘못된 값은
 *       {@code HttpMessageNotReadableException}으로 별도 처리(GlobalExceptionHandler).</li>
 * </ul>
 *
 * <p><b>임시 PW 비포함</b>: 본 DTO에는 password 관련 필드가 없다. 임시 PW는
 * {@link TempPasswordGenerator}가 서버 측에서 생성한다.
 */
public record AdminInviteUserRequest(
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(max = 100) String displayName,
    @NotNull Role role
) {
}
