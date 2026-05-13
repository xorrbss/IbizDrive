package com.ibizdrive.audit;

import com.ibizdrive.favorite.FavoriteStarredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * P2a — favorites star/unstar audit listener.
 *
 * <p>{@link com.ibizdrive.favorite.FavoriteService#star}/{@link com.ibizdrive.favorite.FavoriteService#unstar}이
 * publish하는 {@link FavoriteStarredEvent}를 AFTER_COMMIT으로 받아 audit_log row 1건 INSERT.
 *
 * <p>매핑:
 * <ul>
 *   <li>file + starred=true  → {@link AuditEventType#FILE_STARRED}, target=FILE/resource_id</li>
 *   <li>file + starred=false → {@link AuditEventType#FILE_UNSTARRED}</li>
 *   <li>folder + starred=true  → {@link AuditEventType#FOLDER_STARRED}, target=FOLDER/resource_id</li>
 *   <li>folder + starred=false → {@link AuditEventType#FOLDER_UNSTARRED}</li>
 * </ul>
 *
 * <p>{@link UserQuotaAuditListener} 패턴 답습. 실패는 ERROR 로그 후 swallow (ADR #24).
 *
 * <p>before/after/metadata는 빈 문자열이 아닌 {@code null} — favorites는 단순 binary 토글로
 * meta 가치가 낮음 (event type 자체가 binary 의미 보유). actor_ip / user_agent도 미수집 —
 * RP-2 정책상 view audit과 동일 trade-off (volume vs forensic).
 */
@Component
public class FavoriteAuditListener {

    private static final Logger log = LoggerFactory.getLogger(FavoriteAuditListener.class);

    private final AuditService auditService;

    public FavoriteAuditListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFavoriteStarred(FavoriteStarredEvent event) {
        try {
            AuditEventType eventType = resolveEventType(event);
            AuditTargetType targetType = resolveTargetType(event.resourceType());
            auditService.record(new AuditEvent(
                eventType,
                event.actorId(),
                null, null,
                targetType,
                event.resourceId(),
                null, null, null
            ));
        } catch (IllegalArgumentException ex) {
            // resource_type이 'file'/'folder' 외 값일 때 — V22 CHECK으로 차단되지만
            // service layer 인자 오용에 대한 방어 (audit만 skip, 비즈니스 흐름 보호).
            log.error("audit emission skipped — invalid resourceType={} resourceId={}",
                event.resourceType(), event.resourceId(), ex);
        } catch (RuntimeException ex) {
            log.error("audit emission failed for favorite event resourceType={} resourceId={} starred={}",
                event.resourceType(), event.resourceId(), event.starred(), ex);
        }
    }

    private static AuditEventType resolveEventType(FavoriteStarredEvent event) {
        return switch (event.resourceType()) {
            case "file" -> event.starred() ? AuditEventType.FILE_STARRED : AuditEventType.FILE_UNSTARRED;
            case "folder" -> event.starred() ? AuditEventType.FOLDER_STARRED : AuditEventType.FOLDER_UNSTARRED;
            default -> throw new IllegalArgumentException(
                "favorite resourceType must be 'file' or 'folder': " + event.resourceType());
        };
    }

    private static AuditTargetType resolveTargetType(String resourceType) {
        return switch (resourceType) {
            case "file" -> AuditTargetType.FILE;
            case "folder" -> AuditTargetType.FOLDER;
            default -> throw new IllegalArgumentException(
                "favorite resourceType must be 'file' or 'folder': " + resourceType);
        };
    }
}
