package com.ibizdrive.favorite;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * v1.x — favorites orphan cleanup. file/folder가 hard-purge되어 더 이상 존재하지 않는
 * resource_id를 참조하는 favorites 행을 일괄 삭제하고 {@link AuditEventType#FAVORITES_ORPHANS_CLEANED}
 * summary audit 1건을 발행한다.
 *
 * <p>{@link HardPurgeService} 패턴 답습 — 단일 {@code @Transactional}, summary-only audit (per-row 미발행),
 * 예외 시 전체 rollback → audit 미발행 → 다음 cron 재시도.
 *
 * <p>soft-deleted(휴지통) resource는 보존 (사용자가 복원 시 favorite 재노출). hard-purge된 row만 제거.
 *
 * <p>cleanup count == 0이면 audit 미발행 — 노이즈 회피 (HardPurgeService와 동형 정책 아니지만,
 * favorites cleanup은 평소 idle 가능성 높아 KISS).
 */
@Service
public class FavoritesCleanupService {

    private static final Logger log = LoggerFactory.getLogger(FavoritesCleanupService.class);

    private final FavoriteRepository favoriteRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public FavoritesCleanupService(
        FavoriteRepository favoriteRepository,
        AuditService auditService,
        ObjectMapper objectMapper
    ) {
        this.favoriteRepository = favoriteRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /**
     * orphan favorites row를 삭제하고 audit 1건 발행.
     *
     * @return 삭제된 row 수 (0이면 audit 미발행)
     */
    @Transactional
    public int runDailyCleanup() {
        long started = System.currentTimeMillis();
        int deleted = favoriteRepository.deleteOrphans();
        long durationMs = System.currentTimeMillis() - started;

        if (deleted == 0) {
            log.debug("favorites cleanup: 0 orphans (durationMs={})", durationMs);
            return 0;
        }

        log.info("favorites cleanup: deleted {} orphan rows (durationMs={})", deleted, durationMs);
        emitAudit(deleted, durationMs);
        return deleted;
    }

    private void emitAudit(int deleted, long durationMs) {
        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("deletedRows", deleted);
        afterState.put("durationMs", durationMs);

        AuditEvent event = new AuditEvent(
            AuditEventType.FAVORITES_ORPHANS_CLEANED,
            null,                          // system actor
            null,                          // no IP
            null,                          // no UA
            AuditTargetType.SYSTEM,
            null,                          // global
            null,                          // before
            toJson(afterState),
            null
        );
        auditService.record(event);
    }

    private String toJson(Map<String, ?> state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("audit state serialization failed", e);
        }
    }
}
