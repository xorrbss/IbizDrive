package com.ibizdrive.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;

/**
 * 감사 로그 단일 진입점 (ADR #24, docs/03 §4).
 *
 * <p>호출 경로 두 가지:
 * <ol>
 *   <li>{@code @Audited} AOP에서 자동 호출 (비즈니스 메서드 정상 종료 시 — A2.1b)
 *   <li>Spring Security event listener에서 호출 (인증 이벤트 — A2.4)
 * </ol>
 *
 * <p><b>REQUIRES_NEW 트랜잭션</b>: 호출자 비즈니스 트랜잭션이 rollback되어도 audit row는
 * 별도 트랜잭션에서 commit되어 보존된다. 감사 무결성 우선 (감사 누락 < 비즈니스 일관성 위반의
 * 추적 가능성). 단, audit insert 자체가 실패하면 원 트랜잭션도 영향 없이 ERROR 로그 남김 —
 * MVP는 로그만, 추후 alert 연동 (context.md §함정 2).
 *
 * <p><b>JSONB 캐스트</b>: PostgreSQL은 {@code text → jsonb} 자동 캐스트 안 함. INSERT문에
 * {@code ?::jsonb} 명시. JdbcTemplate으로 직접 INSERT하여 JPA의 JSONB 매핑 의존성 회피.
 */
@Service
public class AuditService {

    private final JdbcTemplate jdbc;

    public AuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEvent event) {
        jdbc.update(
            "INSERT INTO audit_log " +
            "(event_type, actor_id, actor_ip, user_agent, target_type, target_id, " +
            " before_state, after_state, metadata) " +
            "VALUES (?, ?, ?::inet, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb)",
            event.eventType().wire(),
            event.actorId(),
            ipString(event.actorIp()),
            event.userAgent(),
            event.targetType().wire(),
            event.targetId(),
            event.beforeState(),
            event.afterState(),
            event.metadata()
        );
    }

    private static String ipString(InetAddress addr) {
        return addr == null ? null : addr.getHostAddress();
    }
}
