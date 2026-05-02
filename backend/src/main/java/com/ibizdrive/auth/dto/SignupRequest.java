package com.ibizdrive.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/auth/signup} 요청 페이로드 — ADR #41 (self-signup, supersede #18).
 *
 * <p>검증:
 * <ul>
 *   <li>{@code email} — RFC 5322, 1~254자. 서비스에서 trim+lowercase 정규화.</li>
 *   <li>{@code password} — 8~128자. 정책 강화는 향후 별도 트랙(zxcvbn 등). MVP는 길이만.</li>
 *   <li>{@code displayName} — 1~100자. {@code users.display_name} 컬럼 길이 제약과 일치.</li>
 * </ul>
 */
public record SignupRequest(
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(min = 8, max = 128) String password,
    @NotBlank @Size(min = 1, max = 100) String displayName
) {}
