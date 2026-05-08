package com.ibizdrive.common.dto;

import jakarta.annotation.Nullable;

/**
 * 휴지통 복원 endpoint(`POST /api/files/{id}/restore`, `POST /api/folders/{id}/restore`) 의
 * optional body — v1.x M9 후속 (RestoreConflictDialog).
 *
 * <p>body 누락 또는 {@code name == null} 이면 원본 이름 그대로 복원 (기존 동작 유지).
 * {@code name != null} 이면 NFC 정규화 + UNIQUE 재검사 + 충돌 시 {@code RENAME_CONFLICT},
 * 정상 시 새 이름으로 복원.
 *
 * <p>설계: docs/02 §7.5 / §7.6.
 */
public record RestoreRequest(@Nullable String name) {
}
