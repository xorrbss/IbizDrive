package com.ibizdrive.auth.password.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/auth/password/reset} 요청 페이로드.
 *
 * <p>{@code token}은 forgot 흐름에서 발급된 평문 토큰(64자, UUID 2개 join). 서버는 SHA-256 해시로
 * 변환하여 저장된 token_hash와 매칭.
 *
 * <p>{@code newPassword}: signup과 동일한 길이 정책(8~128자). 정책 강화는 별도 트랙.
 */
public record ResetPasswordRequest(
    @NotBlank @Size(max = 200) String token,
    @NotBlank @Size(min = 8, max = 128) String newPassword
) {}
