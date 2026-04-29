package com.ibizdrive.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code PATCH /api/files/{id}} 요청 본문 (docs/02 §7.5 — files mirror).
 *
 * <p>shape: {@code { name: string }}. 정규화 후 기존 값과 동일하면 service가 no-op 반환 (audit 미발행).
 */
public record RenameFileRequest(
    @NotBlank @Size(max = 255) String name
) {}
