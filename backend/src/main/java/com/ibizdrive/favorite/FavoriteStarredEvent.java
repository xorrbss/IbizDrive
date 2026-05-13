package com.ibizdrive.favorite;

import java.util.UUID;

/**
 * P2a — favorites star/unstar AFTER_COMMIT audit emit trigger.
 *
 * <p>{@code AdminQuotaService} 패턴 답습. {@code @TransactionalEventListener(AFTER_COMMIT)}이
 * 본 record를 받아 audit_log row 1건을 별도 REQUIRES_NEW 트랜잭션으로 INSERT.
 *
 * <p>{@code starred=true} → FILE_STARRED/FOLDER_STARRED, {@code starred=false} → FILE_UNSTARRED/FOLDER_UNSTARRED.
 */
public record FavoriteStarredEvent(
    UUID actorId,
    String resourceType,
    UUID resourceId,
    boolean starred
) {}
