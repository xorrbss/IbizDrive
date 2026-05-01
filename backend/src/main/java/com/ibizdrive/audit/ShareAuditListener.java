package com.ibizdrive.audit;

import com.ibizdrive.share.ShareCreatedEvent;
import com.ibizdrive.share.ShareRevokedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * A10.3 — Share 도메인 audit 매핑.
 *
 * <p>{@link com.ibizdrive.audit.PermissionAuditListener}와 동형 패턴 — 비즈니스 로직과 cross-cutting
 * audit emission 분리. {@link ShareCreatedEvent}/{@link ShareRevokedEvent} 모두 본 listener가 수신해
 * audit_log에 INSERT.
 *
 * <p>{@link AuditEventType#SHARE_CREATED}/{@link AuditEventType#SHARE_REVOKED}는 enum이 정의되어 있었으나
 * 실 사용처가 부재했음 — A10이 첫 활성화 (ADR #34 본문에 박제).
 *
 * <p>ADR #24 — audit 실패는 ERROR 로그로 swallow (비즈니스 흐름 보호). {@link AuditService#record}는
 * REQUIRES_NEW 트랜잭션이므로 호출 측 트랜잭션 rollback과 무관하게 보존된다.
 */
@Component
public class ShareAuditListener {

    private static final Logger log = LoggerFactory.getLogger(ShareAuditListener.class);

    private final AuditService auditService;

    public ShareAuditListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @EventListener
    public void onShareCreated(ShareCreatedEvent event) {
        String after = createStateJson(
            event.fileId(), event.permissionId(),
            event.subjectType(), event.subjectId(),
            event.preset().wire(), event.expiresAt(), event.message()
        );
        String metadata = "{\"file_id\":\"" + event.fileId() + "\""
            + ",\"permission_id\":\"" + event.permissionId() + "\"}";
        try {
            auditService.record(new AuditEvent(
                AuditEventType.SHARE_CREATED,
                event.actorId(),
                WebRequestContextHolder.currentIp(),
                WebRequestContextHolder.currentUserAgent(),
                AuditTargetType.SHARE,
                event.shareId(),
                null,
                after,
                metadata
            ));
        } catch (RuntimeException ex) {
            log.error("audit emission failed for event={}", AuditEventType.SHARE_CREATED, ex);
        }
    }

    @EventListener
    public void onShareRevoked(ShareRevokedEvent event) {
        // V6 CASCADE로 share row가 사라지므로 snapshot이 유일한 기록 — before_state로 보존.
        String before = revokeStateJson(
            event.fileId(), event.permissionId(),
            event.originalSharedBy(),
            event.originalCreatedAt(), event.originalExpiresAt(),
            event.originalMessage()
        );
        String metadata = "{\"file_id\":\"" + event.fileId() + "\""
            + ",\"permission_id\":\"" + event.permissionId() + "\""
            + ",\"original_shared_by\":\"" + event.originalSharedBy() + "\"}";
        try {
            auditService.record(new AuditEvent(
                AuditEventType.SHARE_REVOKED,
                event.actorId(),
                WebRequestContextHolder.currentIp(),
                WebRequestContextHolder.currentUserAgent(),
                AuditTargetType.SHARE,
                event.shareId(),
                before,
                null,
                metadata
            ));
        } catch (RuntimeException ex) {
            log.error("audit emission failed for event={}", AuditEventType.SHARE_REVOKED, ex);
        }
    }

    private static String createStateJson(UUID fileId, UUID permissionId,
                                          String subjectType, UUID subjectId,
                                          String presetWire, Instant expiresAt, String message) {
        StringBuilder sb = new StringBuilder(200);
        sb.append("{\"file_id\":\"").append(fileId).append("\"")
          .append(",\"permission_id\":\"").append(permissionId).append("\"")
          .append(",\"subject_type\":\"").append(subjectType).append("\"")
          .append(",\"subject_id\":");
        if (subjectId == null) sb.append("null"); else sb.append("\"").append(subjectId).append("\"");
        sb.append(",\"preset\":\"").append(presetWire).append("\"")
          .append(",\"expires_at\":");
        if (expiresAt == null) sb.append("null"); else sb.append("\"").append(expiresAt).append("\"");
        sb.append(",\"message\":");
        if (message == null) sb.append("null"); else sb.append(escapeJsonString(message));
        sb.append("}");
        return sb.toString();
    }

    private static String revokeStateJson(UUID fileId, UUID permissionId, UUID originalSharedBy,
                                          Instant createdAt, Instant expiresAt, String message) {
        StringBuilder sb = new StringBuilder(200);
        sb.append("{\"file_id\":\"").append(fileId).append("\"")
          .append(",\"permission_id\":\"").append(permissionId).append("\"")
          .append(",\"shared_by\":\"").append(originalSharedBy).append("\"")
          .append(",\"created_at\":");
        if (createdAt == null) sb.append("null"); else sb.append("\"").append(createdAt).append("\"");
        sb.append(",\"expires_at\":");
        if (expiresAt == null) sb.append("null"); else sb.append("\"").append(expiresAt).append("\"");
        sb.append(",\"message\":");
        if (message == null) sb.append("null"); else sb.append(escapeJsonString(message));
        sb.append("}");
        return sb.toString();
    }

    /**
     * Conservative JSON string escape — 사용자 입력 message가 JSON 본문에 들어가므로 minimal escape 필수.
     * Jackson 도입 회피 (PermissionAuditListener의 직접 조립 패턴 일관성).
     */
    private static String escapeJsonString(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() + 2);
        sb.append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
