package com.ibizdrive.admin.trash;

import com.ibizdrive.trash.TrashItemType;

import java.time.Instant;
import java.util.UUID;

/**
 * Wave 2 T9 — admin global trash row DTO (spec §4.4).
 *
 * <p>user-facing {@code TrashItemDto}와 분리 — admin 전용 메타(owner, originalParent name,
 * sizeBytes) 노출. {@code originalParentId == null} = root.
 *
 * <p>{@code sizeBytes}는 file만 not-null. folder는 자기 자신 메타만 보여주므로 항상 null
 * (subtree size는 v1.x deferred — spec §4.4).
 */
public record AdminTrashItemDto(
    UUID id,
    String name,
    TrashItemType type,
    Instant deletedAt,
    Instant purgeAfter,
    UUID ownerId,
    String ownerEmail,
    UUID originalParentId,
    String originalParentName,
    Long sizeBytes
) {}
