package com.ibizdrive.auth.password.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/auth/password/forgot} 요청 페이로드.
 *
 * <p>응답은 항상 200 + 동일 메시지 (anti-enumeration). 이메일 형식 검증은 클라이언트 UX 보조 —
 * 정상 형식이지만 미가입인 경우와 가입자 모두 동일 응답.
 */
public record ForgotPasswordRequest(
    @NotBlank @Email @Size(max = 254) String email
) {}
