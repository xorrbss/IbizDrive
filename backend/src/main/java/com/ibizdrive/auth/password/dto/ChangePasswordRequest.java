package com.ibizdrive.auth.password.dto;

import com.ibizdrive.auth.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 인증된 사용자의 비밀번호 변경 요청 (a1.5 P5).
 *
 * <p>{@code currentPassword} — 현재 비밀번호 (BCrypt 검증). 미일치 시 401 INVALID_CREDENTIALS.
 * {@code newPassword} — ADR #19 정책 (12~128자, 영·숫 각 1자 이상, 공백 금지). reset과 동일, docs/03 §2.7.
 * (auth-password-policy 트랙, 2026-05-04 — ADR #41 정정 closure.)
 *
 * <p>현재/새 동일성 체크는 별도 트랙에서 추가 — KISS.
 */
public record ChangePasswordRequest(
    @NotBlank @Size(max = 200) String currentPassword,
    @NotBlank @ValidPassword String newPassword
) {}
