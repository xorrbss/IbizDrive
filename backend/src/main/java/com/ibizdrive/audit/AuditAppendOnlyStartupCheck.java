package com.ibizdrive.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 부팅 시 런타임 datasource 계정이 audit_log를 변조할 수 없는지 검증 (ADR #25/#49, docs/03 §4.4).
 *
 * <p>V4의 {@code REVOKE UPDATE, DELETE}는 {@code app_user} role에만 적용된다. 운영자가
 * datasource를 table owner나 superuser로 연결하면 append-only 보증이 어떤 오류도 없이
 * 무력화되며, 기존에는 BETA-RELEASE §2.4의 수동 체크리스트만이 이를 막고 있었다.
 * 본 검증이 그 갭을 코드로 닫는다:
 *
 * <ul>
 *   <li>{@code enforce=true} (prod): UPDATE/DELETE/TRUNCATE 권한 감지 시 부팅 중단 (fail-fast)
 *   <li>{@code enforce=false} (dev 기본): WARN 로그만 — 로컬 postgres superuser 개발 흐름 유지
 * </ul>
 *
 * <p>{@code has_table_privilege}는 superuser·owner에 대해 항상 true를 반환하므로 REVOKE 우회
 * 경로(owner 접속, superuser 접속)를 모두 감지한다. Flyway가 먼저 실행되므로(ApplicationRunner는
 * context ready 이후) audit_log 테이블은 항상 존재한다.
 */
@Component
public class AuditAppendOnlyStartupCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AuditAppendOnlyStartupCheck.class);

    /** AuditLogAppendOnlyTest가 app_user/superuser 양쪽 semantics를 통합 검증 (package-visible). */
    static final String PRIVILEGE_QUERY = """
        SELECT current_user,
               has_table_privilege(current_user, 'audit_log', 'UPDATE')
            OR has_table_privilege(current_user, 'audit_log', 'DELETE')
            OR has_table_privilege(current_user, 'audit_log', 'TRUNCATE')
        """;

    private final JdbcTemplate jdbc;
    private final AuditAppendOnlyProperties properties;

    public AuditAppendOnlyStartupCheck(JdbcTemplate jdbc, AuditAppendOnlyProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        final ConnectionPrivilege result;
        try {
            result = jdbc.queryForObject(
                PRIVILEGE_QUERY,
                (rs, rowNum) -> new ConnectionPrivilege(rs.getString(1), rs.getBoolean(2))
            );
        } catch (DataAccessException e) {
            if (properties.enforce()) {
                throw new IllegalStateException(
                    "audit_log append-only 검증을 수행할 수 없습니다 (enforce=true) — 권한 조회 실패", e);
            }
            log.warn("audit_log append-only 검증을 수행하지 못했습니다 (enforce=false, 부팅 계속): {}",
                e.getMessage());
            return;
        }

        if (result != null && result.canMutate()) {
            String message = String.format(
                "런타임 DB 계정 '%s'이(가) audit_log UPDATE/DELETE/TRUNCATE 권한을 보유 — "
                    + "append-only 보증(ADR #25) 위반. V4의 app_user 계정으로 접속하도록 "
                    + "SPRING_DATASOURCE_USERNAME/PASSWORD를 설정하세요 (BETA-RELEASE.md §2.4).",
                result.user());
            if (properties.enforce()) {
                throw new IllegalStateException(message);
            }
            log.warn(message);
        } else {
            log.info("audit_log append-only 연결 검증 통과 — 런타임 계정에 변조 권한 없음");
        }
    }

    private record ConnectionPrivilege(String user, boolean canMutate) {
    }
}
