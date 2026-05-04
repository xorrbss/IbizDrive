package com.ibizdrive.auth.dto;

import com.ibizdrive.auth.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/auth/signup} 요청 페이로드 — ADR #41 (self-signup, supersede #18).
 *
 * <p>검증:
 * <ul>
 *   <li>{@code email} — RFC 5322, 1~254자. 서비스에서 trim+lowercase 정규화.</li>
 *   <li>{@code password} — ADR #19 정책 (12~128자, 영·숫 각 1자 이상, 공백 금지). 위반 시
 *       {@code VALIDATION_ERROR { rule: 'min_length'|'max_length'|'missing_alpha'|'missing_digit'|'whitespace' }}.
 *       (auth-password-policy 트랙, 2026-05-04 — ADR #41 정정 closure.)</li>
 *   <li>{@code displayName} — 1~100자. {@code users.display_name} 컬럼 길이 제약과 일치.</li>
 * </ul>
 */
public record SignupRequest(
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @ValidPassword String password,
    @NotBlank @Size(min = 1, max = 100) String displayName
) {}
