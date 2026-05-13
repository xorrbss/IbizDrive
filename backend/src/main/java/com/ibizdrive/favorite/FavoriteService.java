package com.ibizdrive.favorite;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * P2a — favorites 도메인 mutation service.
 *
 * <p>{@link #star}/{@link #unstar} 모두 멱등 — 이미 starred/unstarred 상태이면 no-op (audit 미발행).
 * controller-level {@code @PreAuthorize}가 READ 권한 가드를 담당 (별 클릭 자체가 READ가 보장된
 * 상태에서 발생하므로 service는 추가 가드 없이 통과).
 *
 * <p>AFTER_COMMIT event publish는 {@link FavoriteStarredEvent} → {@link FavoriteAuditListener} →
 * audit_log INSERT 별도 REQUIRES_NEW 트랜잭션. `AdminUserQuotaService` 패턴 답습.
 */
@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final ApplicationEventPublisher eventPublisher;

    public FavoriteService(
        FavoriteRepository favoriteRepository,
        ApplicationEventPublisher eventPublisher
    ) {
        this.favoriteRepository = favoriteRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 즐겨찾기 추가. 이미 starred면 no-op (audit 미발행).
     *
     * @param actorId 현재 인증된 사용자
     * @param resourceType "file" 또는 "folder"
     * @param resourceId 대상 resource id
     * @return true=새로 starred, false=이미 starred (no-op)
     */
    @Transactional
    public boolean star(UUID actorId, String resourceType, UUID resourceId) {
        boolean exists = favoriteRepository
            .existsByIdUserIdAndIdResourceTypeAndIdResourceId(actorId, resourceType, resourceId);
        if (exists) return false;

        favoriteRepository.save(Favorite.of(actorId, resourceType, resourceId));
        eventPublisher.publishEvent(
            new FavoriteStarredEvent(actorId, resourceType, resourceId, true)
        );
        return true;
    }

    /**
     * 즐겨찾기 제거. 이미 unstarred면 no-op (audit 미발행).
     *
     * @return true=실제 제거, false=이미 unstarred (no-op)
     */
    @Transactional
    public boolean unstar(UUID actorId, String resourceType, UUID resourceId) {
        FavoriteId id = new FavoriteId(actorId, resourceType, resourceId);
        if (!favoriteRepository.existsById(id)) return false;

        favoriteRepository.deleteById(id);
        eventPublisher.publishEvent(
            new FavoriteStarredEvent(actorId, resourceType, resourceId, false)
        );
        return true;
    }
}
