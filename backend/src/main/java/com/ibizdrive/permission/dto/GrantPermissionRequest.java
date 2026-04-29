package com.ibizdrive.permission.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code POST /api/{resource}/{id}/permissions} 요청 본문 (docs/02 §7.10).
 *
 * <p>shape: {@code { subject: { type: 'user'|'department'|'role'|'everyone', id?: UUID }, preset: 'read'|'upload'|'edit'|'admin', expiresAt?: ISO-8601 }}.
 *
 * <p>{@code subject.id} 는 {@code subject.type='everyone'} 일 때만 NULL — 매핑 후 service 가 V5 CHECK 와 정합 검증.
 *
 * <p>preset 은 {@link com.ibizdrive.permission.Preset#wire()} 와 동일 lower-case 표기. 잘못된 값은 service 에서
 * {@code IllegalArgumentException} → 400 BAD_REQUEST.
 */
public record GrantPermissionRequest(
    @Valid @NotNull SubjectRef subject,
    @NotBlank String preset,
    Instant expiresAt
) {
    public record SubjectRef(@NotBlank String type, UUID id) {}
}
