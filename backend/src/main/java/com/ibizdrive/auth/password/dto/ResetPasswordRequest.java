package com.ibizdrive.auth.password.dto;

import com.ibizdrive.auth.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/auth/password/reset} 요청 페이로드.
 *
 * <p>{@code token}은 forgot 흐름에서 발급된 평문 토큰(64자, UUID 2개 join). 서버는 SHA-256 해시로
 * 변환하여 저장된 token_hash와 매칭.
 *
 * <p>{@code newPassword}: ADR #19 정책 (12~128자, 영·숫 각 1자 이상, 공백 금지) — signup과 동일.
 * (auth-password-policy 트랙, 2026-05-04 — ADR #41 정정 closure.)
 */
public record ResetPasswordRequest(
    @NotBlank @Size(max = 200) String token,
    @NotBlank @ValidPassword String newPassword
) {}
