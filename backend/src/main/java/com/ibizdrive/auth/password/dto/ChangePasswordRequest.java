package com.ibizdrive.auth.password.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 인증된 사용자의 비밀번호 변경 요청 (a1.5 P5).
 *
 * <p>{@code currentPassword} — 현재 비밀번호 (BCrypt 검증). 미일치 시 401 INVALID_CREDENTIALS.
 * {@code newPassword} — 새 비밀번호. 정책: 8~128자 (reset과 동일, docs/03 §2.7).
 *
 * <p>현재/새 동일성 체크는 정책 강화 트랙에서 추가 — KISS (a1.5 범위 외).
 */
public record ChangePasswordRequest(
    @NotBlank @Size(max = 200) String currentPassword,
    @NotBlank @Size(min = 8, max = 128) String newPassword
) {}
