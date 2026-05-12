package com.ibizdrive.audit;

import java.util.EnumMap;
import java.util.Map;

/**
 * 감사 이벤트 타입 → 심각도 매핑 단일 진실 (docs/03 §4, P3 — Audit severity backend).
 *
 * <p>{@link AuditService#record(AuditEvent)} 가 INSERT 시 본 매퍼를 호출해 severity 컬럼을
 * 자동 결정한다. {@code V19__audit_severity.sql} 의 backfill CASE 와 정확히 동치이며,
 * {@code AuditSeverityMapperTest} 가 두 표의 동치성을 검증한다.
 *
 * <p>frontend {@code frontend/src/lib/admin/auditSeverity.ts} 의 {@code SEVERITY_MAP} 는 본 PR
 * 이전까지 임시 fallback 이었으며 (graceful upgrade), 본 클래스 도입 이후 그 표는 폐기되고
 * frontend 는 wire 응답의 {@code severity} 를 그대로 사용한다.
 *
 * <p>분류 기준은 {@link AuditSeverity} 의 javadoc 참조. "단건 vs 연속 실패" 같은 동적 승격
 * (예: 5회 연속 로그인 실패 → danger) 은 alerting 트랙으로 분리 (out of scope).
 */
public final class AuditSeverityMapper {

    /** 명시 매핑 — 본 표에 없는 event type 은 {@link AuditSeverity#INFO} 로 fallback. */
    private static final Map<AuditEventType, AuditSeverity> EXPLICIT;

    static {
        Map<AuditEventType, AuditSeverity> m = new EnumMap<>(AuditEventType.class);

        // danger — 외부 노출 가능성 / 정책 위반 가능성 (즉시 검토)
        m.put(AuditEventType.SHARE_CREATED, AuditSeverity.DANGER);

        // warn — 외부 도메인 / 권한 회수 / 권한 만료 / 대량 삭제·purge / 관리자 정책 변경
        m.put(AuditEventType.USER_LOGIN_FAILED, AuditSeverity.WARN);
        m.put(AuditEventType.PERMISSION_REVOKED, AuditSeverity.WARN);
        m.put(AuditEventType.PERMISSION_EXPIRED, AuditSeverity.WARN);
        m.put(AuditEventType.SHARE_REVOKED, AuditSeverity.WARN);
        m.put(AuditEventType.SHARE_EXPIRED, AuditSeverity.WARN);
        m.put(AuditEventType.ADMIN_LEGAL_HOLD_PLACED, AuditSeverity.WARN);
        m.put(AuditEventType.ADMIN_LEGAL_HOLD_RELEASED, AuditSeverity.WARN);
        m.put(AuditEventType.ADMIN_USER_DEACTIVATED, AuditSeverity.WARN);
        m.put(AuditEventType.ADMIN_ROLE_CHANGED, AuditSeverity.WARN);
        m.put(AuditEventType.ADMIN_CRON_TOGGLED, AuditSeverity.WARN);
        m.put(AuditEventType.FILE_DELETED, AuditSeverity.WARN);
        m.put(AuditEventType.FILE_PURGED, AuditSeverity.WARN);
        m.put(AuditEventType.FOLDER_DELETED, AuditSeverity.WARN);
        m.put(AuditEventType.FOLDER_PURGED, AuditSeverity.WARN);
        m.put(AuditEventType.TEAM_ARCHIVED, AuditSeverity.WARN);
        m.put(AuditEventType.SYSTEM_PURGE_EXECUTED, AuditSeverity.WARN);

        EXPLICIT = Map.copyOf(m);
    }

    private AuditSeverityMapper() {
        // 정적 매핑 표 — 인스턴스화 금지.
    }

    /**
     * 명시 매핑이 있으면 해당 값을, 없으면 {@link AuditSeverity#INFO} 를 반환한다.
     *
     * @param type 감사 이벤트 타입. {@code null} 입력은 호출자 책임 (NPE).
     */
    public static AuditSeverity of(AuditEventType type) {
        return EXPLICIT.getOrDefault(type, AuditSeverity.INFO);
    }
}
