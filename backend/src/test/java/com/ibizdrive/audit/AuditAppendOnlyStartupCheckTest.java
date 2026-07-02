package com.ibizdrive.audit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AuditAppendOnlyStartupCheck} 실패 정책 단위 테스트 (ADR #49).
 *
 * <p>권한 조회 결과(변조 가능/불가/조회 실패) × enforce(true/false) 매트릭스 검증.
 * has_table_privilege SQL 자체의 semantics는 {@link AuditLogAppendOnlyTest}(Testcontainers)가
 * app_user/superuser 실 connection으로 통합 검증한다.
 */
class AuditAppendOnlyStartupCheckTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final DefaultApplicationArguments noArgs = new DefaultApplicationArguments();

    @Test
    void mutableConnection_enforceTrue_failsBoot() {
        stubPrivilegeQuery("postgres", true);
        var check = new AuditAppendOnlyStartupCheck(jdbc, new AuditAppendOnlyProperties(true));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> check.run(noArgs));
        assertTrue(ex.getMessage().contains("postgres"), "위반 계정명이 메시지에 포함되어야 함");
        assertTrue(ex.getMessage().contains("app_user"), "app_user 전환 안내가 메시지에 포함되어야 함");
    }

    @Test
    void mutableConnection_enforceFalse_warnsOnly() {
        stubPrivilegeQuery("postgres", true);
        var check = new AuditAppendOnlyStartupCheck(jdbc, new AuditAppendOnlyProperties(false));

        assertDoesNotThrow(() -> check.run(noArgs));
    }

    @Test
    void appendOnlyConnection_enforceTrue_passes() {
        stubPrivilegeQuery("app_user", false);
        var check = new AuditAppendOnlyStartupCheck(jdbc, new AuditAppendOnlyProperties(true));

        assertDoesNotThrow(() -> check.run(noArgs));
    }

    @Test
    void queryFailure_enforceTrue_failsBoot() {
        when(jdbc.queryForObject(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any()))
            .thenThrow(new DataAccessResourceFailureException("connection refused"));
        var check = new AuditAppendOnlyStartupCheck(jdbc, new AuditAppendOnlyProperties(true));

        assertThrows(IllegalStateException.class, () -> check.run(noArgs));
    }

    @Test
    void queryFailure_enforceFalse_warnsOnly() {
        when(jdbc.queryForObject(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any()))
            .thenThrow(new DataAccessResourceFailureException("connection refused"));
        var check = new AuditAppendOnlyStartupCheck(jdbc, new AuditAppendOnlyProperties(false));

        assertDoesNotThrow(() -> check.run(noArgs));
    }

    /** RowMapper 스텁 — 실제 구현이 (current_user, canMutate) 2컬럼을 매핑하는 것과 동형. */
    private void stubPrivilegeQuery(String user, boolean canMutate) {
        when(jdbc.queryForObject(eq(AuditAppendOnlyStartupCheck.PRIVILEGE_QUERY),
            org.mockito.ArgumentMatchers.<RowMapper<Object>>any()))
            .thenAnswer(invocation -> {
                RowMapper<?> mapper = invocation.getArgument(1);
                ResultSet rs = mock(ResultSet.class);
                try {
                    when(rs.getString(1)).thenReturn(user);
                    when(rs.getBoolean(2)).thenReturn(canMutate);
                    return mapper.mapRow(rs, 0);
                } catch (SQLException e) {
                    throw new AssertionError(e);
                }
            });
    }
}
