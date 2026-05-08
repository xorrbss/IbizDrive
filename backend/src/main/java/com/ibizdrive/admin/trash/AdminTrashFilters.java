package com.ibizdrive.admin.trash;

import com.ibizdrive.trash.TrashItemType;

import java.time.Instant;
import java.util.UUID;

/**
 * Wave 2 T9 — admin trash filters. 모든 필드 nullable (= 미적용).
 *
 * <p>{@code q}는 raw 입력 (서비스에서 정규화/escape/wrap). controller에서 길이 cap(예: 200)
 * 미준수 시 400. {@code type}/{@code ownerId}는 controller에서 파싱 실패 시 400.
 *
 * <p>{@code deletedFromMin} (inclusive 하한) / {@code deletedToMax} (exclusive 상한) — 날짜 범위
 * 필터 (Wave 2 T9 follow-up). controller에서 {@code YYYY-MM-DD} → UTC instant 경계로 변환.
 * 양수 모두 적용 시 {@code deletedFromMin < deletedToMax}여야 함 — 위반 시 service에서 400.
 */
public record AdminTrashFilters(
    String q,
    TrashItemType type,
    UUID ownerId,
    Instant deletedFromMin,
    Instant deletedToMax
) {}
