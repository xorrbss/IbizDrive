package com.ibizdrive.folder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * spec §5.6 step 7 — cross-workspace move audit emission. listener 분리 이유: service가 동기
 * REQUIRES_NEW로 audit 발행하지만, 본 케이스는 metadata 조립이 풍부해 별도 listener로 책임 분리 (KISS).
 */
@Component
public class CrossWorkspaceMoveAuditListener {

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public CrossWorkspaceMoveAuditListener(AuditService auditService, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @EventListener
    public void onCrossWorkspaceMove(CrossWorkspaceMoveCompletedEvent ev) {
        AuditEventType eventType = "folder".equals(ev.resourceType())
            ? AuditEventType.FOLDER_MOVED_CROSS_WORKSPACE
            : AuditEventType.FILE_MOVED_CROSS_WORKSPACE;
        AuditTargetType targetType = "folder".equals(ev.resourceType())
            ? AuditTargetType.FOLDER
            : AuditTargetType.FILE;

        Map<String, Object> beforeState = new LinkedHashMap<>();
        beforeState.put("scopeType", ev.fromScopeType().dbValue());

        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("scopeType", ev.toScopeType().dbValue());
        afterState.put("scopeId", ev.toScopeId());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("subtreeFolderCount", ev.subtreeFolderCount());
        metadata.put("subtreeFileCount", ev.subtreeFileCount());
        metadata.put("revokedShareCount", ev.revokedShareCount());

        AuditEvent audit = new AuditEvent(
            eventType,
            ev.actorId(),
            null, null,                // ip/UA — request context 없음 (event listener는 비동기 가능성)
            targetType,
            ev.sourceResourceId(),
            toJson(beforeState),
            toJson(afterState),
            toJson(metadata)
        );
        auditService.record(audit);
    }

    private String toJson(Map<String, ?> m) {
        if (m == null) return null;
        try {
            return objectMapper.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("audit serialization failed", e);
        }
    }
}
