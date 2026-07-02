package com.ibizdrive.audit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V4__audit_log_revoke.sql append-only 강제 검증 (ADR #25, docs/03 §4.4).
 *
 * <p>전제: V4가 'app_user' role을 생성하고 audit_log에 대해 INSERT/SELECT만 허용,
 * UPDATE/DELETE는 REVOKE된 상태.
 *
 * <p>검증:
 * <ul>
 *   <li>app_user로 connect한 connection이 INSERT/SELECT는 정상 수행
 *   <li>UPDATE 시도 → SQLState '42501' (insufficient_privilege)
 *   <li>DELETE 시도 → SQLState '42501' (insufficient_privilege)
 * </ul>
 *
 * <p>주의: superuser connection(default Testcontainers 사용자)은 REVOKE 영향을 받지 않으므로
 * 별도 DriverManager.getConnection으로 app_user 명의 connection을 만들어 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class AuditLogAppendOnlyTest {

    private static final String APP_USER = "app_user";
    private static final String APP_PASS = "app_pass"; // V4와 동기화

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private JdbcTemplate jdbc; // superuser connection — 사전 데이터 삽입용

    @Test
    void appUser_canInsert_canSelect() throws SQLException {
        try (Connection conn = appUserConnection();
             Statement stmt = conn.createStatement()) {
            // INSERT — 통과해야 함
            int inserted = stmt.executeUpdate(
                "INSERT INTO audit_log(event_type, target_type) VALUES ('test.insert', 'system')"
            );
            assertEquals(1, inserted, "app_user는 INSERT 가능해야 함");

            // SELECT — 통과해야 함
            assertDoesNotThrow(() -> stmt.executeQuery("SELECT id FROM audit_log LIMIT 1"));
        }
    }

    @Test
    void appUser_cannotUpdate() throws SQLException {
        // 사전: superuser로 row 1개 INSERT
        jdbc.update("INSERT INTO audit_log(event_type, target_type) VALUES ('seed.update', 'system')");

        try (Connection conn = appUserConnection();
             Statement stmt = conn.createStatement()) {
            SQLException ex = assertThrows(
                SQLException.class,
                () -> stmt.executeUpdate(
                    "UPDATE audit_log SET event_type='tampered' WHERE event_type='seed.update'"
                )
            );
            assertEquals("42501", ex.getSQLState(),
                "app_user의 UPDATE 시도는 SQLState 42501(insufficient_privilege)이어야 함, 실제: "
                    + ex.getSQLState() + " — " + ex.getMessage());
        }
    }

    @Test
    void appUser_cannotDelete() throws SQLException {
        // 사전: superuser로 row 1개 INSERT
        jdbc.update("INSERT INTO audit_log(event_type, target_type) VALUES ('seed.delete', 'system')");

        try (Connection conn = appUserConnection();
             Statement stmt = conn.createStatement()) {
            SQLException ex = assertThrows(
                SQLException.class,
                () -> stmt.executeUpdate("DELETE FROM audit_log WHERE event_type='seed.delete'")
            );
            assertEquals("42501", ex.getSQLState(),
                "app_user의 DELETE 시도는 SQLState 42501(insufficient_privilege)이어야 함, 실제: "
                    + ex.getSQLState() + " — " + ex.getMessage());
        }
    }

    @Test
    void appUser_cannotTruncate() throws SQLException {
        try (Connection conn = appUserConnection();
             Statement stmt = conn.createStatement()) {
            SQLException ex = assertThrows(
                SQLException.class,
                () -> stmt.executeUpdate("TRUNCATE audit_log")
            );
            assertEquals("42501", ex.getSQLState(),
                "app_user의 TRUNCATE 시도도 거부되어야 함 (REVOKE ALL 효과), 실제: "
                    + ex.getSQLState());
        }
    }

    @Test
    void privilegeQuery_detectsMutableAndAppendOnlyConnections() throws SQLException {
        // ADR #49 — AuditAppendOnlyStartupCheck의 has_table_privilege SQL semantics 통합 검증.
        // superuser(테스트 기본 계정)는 변조 가능으로, app_user는 append-only로 판정되어야 한다.
        Boolean superuserCanMutate = jdbc.queryForObject(
            AuditAppendOnlyStartupCheck.PRIVILEGE_QUERY, (rs, i) -> rs.getBoolean(2));
        assertEquals(Boolean.TRUE, superuserCanMutate,
            "superuser 연결은 audit_log 변조 가능으로 감지되어야 함");

        try (Connection conn = appUserConnection();
             Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery(AuditAppendOnlyStartupCheck.PRIVILEGE_QUERY)) {
            assertTrue(rs.next());
            assertEquals(APP_USER, rs.getString(1));
            assertFalse(rs.getBoolean(2), "app_user 연결은 append-only로 판정되어야 함");
        }
    }

    @Test
    void appUser_canSelectPostV4Tables() throws SQLException {
        // V25 GRANT 캐치업 회귀 가드 — V8+ 신규 테이블에 app_user 접근 가능해야
        // "app_user만 application 사용" 게이트(BETA-RELEASE §2.4)가 실제로 이행 가능하다.
        try (Connection conn = appUserConnection();
             Statement stmt = conn.createStatement()) {
            for (String table : new String[] {
                "password_reset_tokens", "cron_policy", "teams",
                "pending_admin_approvals", "favorites"}) {
                assertDoesNotThrow(
                    () -> stmt.executeQuery("SELECT 1 FROM " + table + " LIMIT 1").close(),
                    "app_user는 " + table + " SELECT 가능해야 함 (V25)");
            }
        }
    }

    private Connection appUserConnection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), APP_USER, APP_PASS);
    }
}
