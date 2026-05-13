package com.ibizdrive.approval.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.admin.AdminUserService;
import com.ibizdrive.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RoleChangeApprovalHandler} 단위 — ADR #47 Phase 3b.
 * payload deserialize + {@link AdminUserService#changeRole} 위임 검증.
 */
class RoleChangeApprovalHandlerTest {

    private AdminUserService adminUserService;
    private RoleChangeApprovalHandler handler;

    @BeforeEach
    void setUp() {
        adminUserService = mock(AdminUserService.class);
        handler = new RoleChangeApprovalHandler(adminUserService, new ObjectMapper());
    }

    @Test
    void actionType_returnsRoleChange() {
        assertThat(handler.actionType()).isEqualTo("role_change");
    }

    @Test
    void execute_deserializesPayloadAndDelegatesToService() {
        UUID secondary = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        String payload = String.format(
            "{\"userId\":\"%s\",\"fromRole\":\"MEMBER\",\"toRole\":\"ADMIN\",\"reason\":\"승급\"}",
            target);

        handler.execute(payload, secondary);

        verify(adminUserService).changeRole(target, Role.ADMIN, secondary);
    }

    @Test
    void execute_propagatesServiceException_forRollback() {
        UUID secondary = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        when(adminUserService.changeRole(target, Role.ADMIN, secondary))
            .thenThrow(new RuntimeException("downstream fail"));

        String payload = String.format(
            "{\"userId\":\"%s\",\"fromRole\":\"MEMBER\",\"toRole\":\"ADMIN\",\"reason\":null}",
            target);

        assertThatThrownBy(() -> handler.execute(payload, secondary))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("downstream fail");
    }

    @Test
    void execute_corruptPayload_throwsIllegalState() {
        assertThatThrownBy(() -> handler.execute("not-json", UUID.randomUUID()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("role_change payload deserialize failed");
    }

    @Test
    void execute_acceptsMemberRoleTransition() {
        // ADMIN→MEMBER 강등도 지원 (audit_log에 from/to 기록은 service 책임).
        UUID secondary = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        String payload = String.format(
            "{\"userId\":\"%s\",\"fromRole\":\"ADMIN\",\"toRole\":\"MEMBER\"}",
            target);

        handler.execute(payload, secondary);

        verify(adminUserService).changeRole(target, Role.MEMBER, secondary);
    }
}
