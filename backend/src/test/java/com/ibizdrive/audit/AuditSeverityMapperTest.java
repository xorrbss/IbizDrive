package com.ibizdrive.audit;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AuditSeverityMapper} 매핑 검증 (P3 — Audit severity backend).
 *
 * <p>핵심 검증:
 * <ol>
 *   <li>명시 매핑 (danger 1건 + warn 16건) 정확 일치.</li>
 *   <li>그 외 모든 {@link AuditEventType} 은 {@link AuditSeverity#INFO} 로 fallback.</li>
 *   <li>{@code AuditEventType.values()} 58 개 모두에 대해 NPE/Exception 없이 매핑 반환.</li>
 *   <li>{@code AuditSeverity} wire format 이 frontend 와 일치 (info/warn/danger).</li>
 * </ol>
 *
 * <p>본 테스트는 매핑이 frontend `auditSeverity.ts` 와 동치임을 보장하지 못한다 (별도 언어). 단,
 * V19 backfill SQL 의 CASE 와 본 매퍼는 같은 표를 사용하므로 양쪽이 단일 진실. frontend 는 본
 * PR 에서 자체 매핑을 폐기하고 wire 응답의 severity 를 사용하므로 비교 대상이 사라진다.
 */
class AuditSeverityMapperTest {

    @Test
    void allEventTypes_mapToSomeSeverity() {
        // values() 58 개 전부에 대해 mapper 가 non-null 반환
        for (AuditEventType type : AuditEventType.values()) {
            AuditSeverity sev = AuditSeverityMapper.of(type);
            assertNotNull(sev, "AuditSeverityMapper.of(" + type + ") must not be null");
        }
    }

    @Test
    void shareCreated_isDanger() {
        // share.created — 외부 공유, 정책 위반 가능성
        assertEquals(AuditSeverity.DANGER, AuditSeverityMapper.of(AuditEventType.SHARE_CREATED));
    }

    @Test
    void explicitWarnMappings() {
        // V19 backfill SQL 의 warn CASE 와 1:1 일치해야 함
        Map<AuditEventType, AuditSeverity> warn = new EnumMap<>(AuditEventType.class);
        warn.put(AuditEventType.USER_LOGIN_FAILED, AuditSeverity.WARN);
        warn.put(AuditEventType.PERMISSION_REVOKED, AuditSeverity.WARN);
        warn.put(AuditEventType.PERMISSION_EXPIRED, AuditSeverity.WARN);
        warn.put(AuditEventType.SHARE_REVOKED, AuditSeverity.WARN);
        warn.put(AuditEventType.SHARE_EXPIRED, AuditSeverity.WARN);
        warn.put(AuditEventType.ADMIN_LEGAL_HOLD_PLACED, AuditSeverity.WARN);
        warn.put(AuditEventType.ADMIN_LEGAL_HOLD_RELEASED, AuditSeverity.WARN);
        warn.put(AuditEventType.ADMIN_USER_DEACTIVATED, AuditSeverity.WARN);
        warn.put(AuditEventType.ADMIN_ROLE_CHANGED, AuditSeverity.WARN);
        warn.put(AuditEventType.ADMIN_CRON_TOGGLED, AuditSeverity.WARN);
        warn.put(AuditEventType.FILE_DELETED, AuditSeverity.WARN);
        warn.put(AuditEventType.FILE_PURGED, AuditSeverity.WARN);
        warn.put(AuditEventType.FOLDER_DELETED, AuditSeverity.WARN);
        warn.put(AuditEventType.FOLDER_PURGED, AuditSeverity.WARN);
        warn.put(AuditEventType.TEAM_ARCHIVED, AuditSeverity.WARN);
        warn.put(AuditEventType.SYSTEM_PURGE_EXECUTED, AuditSeverity.WARN);

        for (Map.Entry<AuditEventType, AuditSeverity> e : warn.entrySet()) {
            assertEquals(e.getValue(), AuditSeverityMapper.of(e.getKey()),
                e.getKey() + " 매핑이 warn 이어야 함");
        }
    }

    @Test
    void uncategorizedEvents_defaultToInfo() {
        // 명시 매핑이 없는 이벤트는 모두 info (대부분의 일상 운영 이벤트)
        assertEquals(AuditSeverity.INFO, AuditSeverityMapper.of(AuditEventType.FILE_VIEWED));
        assertEquals(AuditSeverity.INFO, AuditSeverityMapper.of(AuditEventType.FILE_UPLOADED));
        assertEquals(AuditSeverity.INFO, AuditSeverityMapper.of(AuditEventType.FILE_DOWNLOADED));
        assertEquals(AuditSeverity.INFO, AuditSeverityMapper.of(AuditEventType.FILE_RENAMED));
        assertEquals(AuditSeverity.INFO, AuditSeverityMapper.of(AuditEventType.FOLDER_CREATED));
        assertEquals(AuditSeverity.INFO, AuditSeverityMapper.of(AuditEventType.PERMISSION_GRANTED));
        assertEquals(AuditSeverity.INFO, AuditSeverityMapper.of(AuditEventType.USER_LOGIN_SUCCESS));
        assertEquals(AuditSeverity.INFO, AuditSeverityMapper.of(AuditEventType.SYSTEM_BACKUP_COMPLETED));
        assertEquals(AuditSeverity.INFO, AuditSeverityMapper.of(AuditEventType.AUDIT_EXPORTED));
    }

    @Test
    void severityWireFormat_matchesFrontend() {
        // frontend 의 `AuditSeverity` 유니언 ('info' | 'warn' | 'danger') 과 1:1.
        assertEquals("info", AuditSeverity.INFO.wire());
        assertEquals("warn", AuditSeverity.WARN.wire());
        assertEquals("danger", AuditSeverity.DANGER.wire());
    }

    @Test
    void severityFrom_roundTrips() {
        for (AuditSeverity s : AuditSeverity.values()) {
            assertEquals(s, AuditSeverity.from(s.wire()),
                "AuditSeverity.from(" + s.wire() + ") round-trip 실패");
        }
    }

    @Test
    void severityFrom_unknownWire_throws() {
        assertThrows(IllegalArgumentException.class, () -> AuditSeverity.from("critical"));
    }

    @Test
    void totalExplicitMappings_matchExpectedCount() {
        // 정확히 17 개 명시 매핑 (danger 1 + warn 16). 나머지 41 개는 default info.
        long explicit = 0;
        for (AuditEventType type : AuditEventType.values()) {
            if (AuditSeverityMapper.of(type) != AuditSeverity.INFO) {
                explicit++;
            }
        }
        assertEquals(17, explicit, "명시 매핑 수가 17 이 아니면 V19 backfill 과 어긋남");
    }
}
