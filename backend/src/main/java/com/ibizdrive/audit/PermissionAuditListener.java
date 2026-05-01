package com.ibizdrive.audit;

import com.ibizdrive.permission.PermissionExpiredEvent;
import com.ibizdrive.permission.PermissionGrantedEvent;
import com.ibizdrive.permission.PermissionRevokedEvent;
import com.ibizdrive.permission.RoleChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * A3.4 — {@link RoleChangedEvent} → {@link AuditEventType#PERMISSION_CHANGED} 기록.
 *
 * <p>{@link AuthAuditListener}와 동일 패턴: 권한 변경 비즈니스 로직({@link com.ibizdrive.permission.PermissionService})과
 * 분리된 cross-cutting layer. 이벤트 publish는 호출 측에서 명시적으로 수행하고, 본 listener는
 * audit_log INSERT만 책임진다.
 *
 * <p>IP/User-Agent는 동일 요청 스레드의 {@link WebRequestContextHolder}에서 추출.
 *
 * <p>ADR #24 — audit 실패는 ERROR 로그로 swallow (비즈니스 흐름 보호). {@link AuditService#record}는
 * REQUIRES_NEW 트랜잭션이므로 호출 측 트랜잭션 rollback과 무관하게 보존된다.
 */
@Component
public class PermissionAuditListener {

    private static final Logger log = LoggerFactory.getLogger(PermissionAuditListener.class);

    private final AuditService auditService;

    public PermissionAuditListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @EventListener
    public void onRoleChanged(RoleChangedEvent event) {
        String before = "{\"role\":\"" + event.from().name() + "\"}";
        String after = "{\"role\":\"" + event.to().name() + "\"}";
        try {
            auditService.record(new AuditEvent(
                AuditEventType.PERMISSION_CHANGED,
                event.actorId(),
                WebRequestContextHolder.currentIp(),
                WebRequestContextHolder.currentUserAgent(),
                AuditTargetType.USER,
                event.targetUserId(),
                before,
                after,
                null
            ));
        } catch (RuntimeException ex) {
            log.error("audit emission failed for event={}", AuditEventType.PERMISSION_CHANGED, ex);
        }
    }

    /**
     * A4.4 — {@link PermissionGrantedEvent} → {@link AuditEventType#PERMISSION_GRANTED} 기록.
     *
     * <p>{@code target_type=PERMISSION} / {@code target_id} = grant row 의 PK. before_state 는 NULL,
     * after_state 는 grant 의 핵심 필드 스냅샷 (resource/subject/preset/expires_at). 본 JSON 은 frontend
     * AuditDetail 에서 raw 표시 — Jackson 직렬화 대신 직접 조립 (의존도 최소화 + null 처리 일관성).
     *
     * <p>{@code metadata} 에 resource 컨텍스트(resource_type/resource_id) 를 별도 키로 두어 audit 검색에서
     * resource 기준 필터를 가능하게 한다 (A4.5 이후 audit query 확장 hook).
     */
    @EventListener
    public void onPermissionGranted(PermissionGrantedEvent event) {
        String after = grantStateJson(
            event.resourceType(), event.resourceId(),
            event.subjectType(), event.subjectId(),
            event.preset().wire(), event.expiresAt()
        );
        String metadata = resourceMetadataJson(event.resourceType(), event.resourceId());
        try {
            auditService.record(new AuditEvent(
                AuditEventType.PERMISSION_GRANTED,
                event.actorId(),
                WebRequestContextHolder.currentIp(),
                WebRequestContextHolder.currentUserAgent(),
                AuditTargetType.PERMISSION,
                event.permissionId(),
                null,
                after,
                metadata
            ));
        } catch (RuntimeException ex) {
            log.error("audit emission failed for event={}", AuditEventType.PERMISSION_GRANTED, ex);
        }
    }

    /**
     * A4.4 — {@link PermissionRevokedEvent} → {@link AuditEventType#PERMISSION_REVOKED} 기록.
     *
     * <p>DELETE 의 특성상 row 가 사라지므로 caller 가 캡처한 snapshot 을 그대로 before_state 로 기록.
     * after_state 는 NULL.
     */
    @EventListener
    public void onPermissionRevoked(PermissionRevokedEvent event) {
        String before = grantStateJson(
            event.resourceType(), event.resourceId(),
            event.subjectType(), event.subjectId(),
            event.preset().wire(), event.expiresAt()
        );
        String metadata = resourceMetadataJson(event.resourceType(), event.resourceId());
        try {
            auditService.record(new AuditEvent(
                AuditEventType.PERMISSION_REVOKED,
                event.actorId(),
                WebRequestContextHolder.currentIp(),
                WebRequestContextHolder.currentUserAgent(),
                AuditTargetType.PERMISSION,
                event.permissionId(),
                before,
                null,
                metadata
            ));
        } catch (RuntimeException ex) {
            log.error("audit emission failed for event={}", AuditEventType.PERMISSION_REVOKED, ex);
        }
    }

    /**
     * {@code permissions-expired-cron} — {@link PermissionExpiredEvent} → {@link AuditEventType#PERMISSION_EXPIRED}
     * 기록.
     *
     * <p>{@link #onPermissionRevoked}와 거의 동일하나 actor 컨텍스트 부재 (시스템 트리거):
     * <ul>
     *   <li>{@code actor_id=null} (시스템 트리거)</li>
     *   <li>{@code actor_ip=null}, {@code user_agent=null} — HTTP 요청 컨텍스트 없음</li>
     *   <li>{@code metadata.trigger='system.expiration'} — {@code permission.revoked}와 audit row 시각 분별 가능
     *       (감사 화면에서 {@code metadata.trigger}로 자동/수동 구분)</li>
     *   <li>{@code before_state}는 {@link #grantStateJson} 재사용 — DELETE 후 row가 사라지므로 caller가 캡처한
     *       snapshot이 진실 출처</li>
     * </ul>
     *
     * <p>{@link com.ibizdrive.share.ShareCommandService#expireShare} 동형 패턴 (SHARE_EXPIRED, ADR #34 closure).
     */
    @EventListener
    public void onPermissionExpired(PermissionExpiredEvent event) {
        String before = grantStateJson(
            event.resourceType(), event.resourceId(),
            event.subjectType(), event.subjectId(),
            event.preset().wire(), event.originalExpiresAt()
        );
        String metadata = expirationMetadataJson(event.resourceType(), event.resourceId());
        try {
            auditService.record(new AuditEvent(
                AuditEventType.PERMISSION_EXPIRED,
                null,   // 시스템 트리거 — actor 부재
                null,   // actor IP 부재
                null,   // user-agent 부재
                AuditTargetType.PERMISSION,
                event.permissionId(),
                before,
                null,
                metadata
            ));
        } catch (RuntimeException ex) {
            log.error("audit emission failed for event={}", AuditEventType.PERMISSION_EXPIRED, ex);
        }
    }

    /**
     * {@link #onPermissionExpired}용 metadata helper — 기존 {@link #resourceMetadataJson}에
     * {@code "trigger":"system.expiration"} 키만 추가. 별도 helper로 분리한 이유는 grant/revoke 패스의
     * metadata 형식을 그대로 보존하면서 expired 패스에만 trigger 키를 의도적으로 노출하기 위함.
     */
    private static String expirationMetadataJson(String resourceType, UUID resourceId) {
        return "{\"trigger\":\"system.expiration\""
             + ",\"resource_type\":\"" + resourceType + "\""
             + ",\"resource_id\":\"" + resourceId + "\"}";
    }

    private static String grantStateJson(String resourceType, UUID resourceId,
                                          String subjectType, UUID subjectId,
                                          String presetWire, Instant expiresAt) {
        // 직접 조립 — keys 는 fixed 라 escape 불필요. value 는 enum/UUID/Instant 로 안전한 문자열.
        StringBuilder sb = new StringBuilder(160);
        sb.append("{\"resource_type\":\"").append(resourceType).append("\"")
          .append(",\"resource_id\":\"").append(resourceId).append("\"")
          .append(",\"subject_type\":\"").append(subjectType).append("\"")
          .append(",\"subject_id\":");
        if (subjectId == null) sb.append("null"); else sb.append("\"").append(subjectId).append("\"");
        sb.append(",\"preset\":\"").append(presetWire).append("\"")
          .append(",\"expires_at\":");
        if (expiresAt == null) sb.append("null"); else sb.append("\"").append(expiresAt).append("\"");
        sb.append("}");
        return sb.toString();
    }

    private static String resourceMetadataJson(String resourceType, UUID resourceId) {
        return "{\"resource_type\":\"" + resourceType + "\",\"resource_id\":\"" + resourceId + "\"}";
    }
}
