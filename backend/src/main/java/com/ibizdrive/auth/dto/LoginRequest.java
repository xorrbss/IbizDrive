package com.ibizdrive.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/auth/login} 요청 페이로드 — docs/02 §7.4.
 *
 * <p>검증:
 * <ul>
 *   <li>{@code email} — RFC 5322 형식, 1~254자 (lowercase 정규화는 service 계층에서)</li>
 *   <li>{@code password} — 1~128자 (정책 검증은 등록·변경 시점에만, 로그인은 길이만 체크)</li>
 * </ul>
 */
public record LoginRequest(
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(max = 128) String password
) {}
