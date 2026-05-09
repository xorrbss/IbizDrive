package com.ibizdrive.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.team.TeamCreatedEvent;
import com.ibizdrive.team.TeamMemberAddedEvent;
import com.ibizdrive.team.TeamMemberRemovedEvent;
import com.ibizdrive.team.TeamMemberRoleChangedEvent;
import com.ibizdrive.team.TeamMembership;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Plan A Task 28 — TeamAuditListener Mockito unit test.
 * 각 이벤트 → 정확한 AuditEvent로 변환되는지 + 실패는 swallow되는지 확인.
 */
class TeamAuditListenerTest {

    private AuditService auditService;
    private TeamAuditListener listener;

    @BeforeEach
    void setUp() {
        auditService = Mockito.mock(AuditService.class);
        listener = new TeamAuditListener(auditService, new ObjectMapper());
    }

    @Test
    void onTeamCreated_recordsAuditEvent_withAfterStateContainingName() {
        UUID teamId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        listener.onTeamCreated(new TeamCreatedEvent(teamId, actor, "Alpha"));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent recorded = captor.getValue();
        assertThat(recorded.eventType()).isEqualTo(AuditEventType.TEAM_CREATED);
        assertThat(recorded.targetType()).isEqualTo(AuditTargetType.TEAM);
        assertThat(recorded.targetId()).isEqualTo(teamId);
        assertThat(recorded.actorId()).isEqualTo(actor);
        assertThat(recorded.afterState()).contains("\"name\":\"Alpha\"");
        assertThat(recorded.beforeState()).isNull();
        assertThat(recorded.metadata()).isNull();
    }

    @Test
    void onTeamMemberAdded_recordsAuditEvent_withAfterStateContainingUserId() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID inviter = UUID.randomUUID();

        listener.onTeamMemberAdded(new TeamMemberAddedEvent(teamId, userId, inviter));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent recorded = captor.getValue();
        assertThat(recorded.eventType()).isEqualTo(AuditEventType.TEAM_MEMBER_ADDED);
        assertThat(recorded.targetType()).isEqualTo(AuditTargetType.TEAM);
        assertThat(recorded.targetId()).isEqualTo(teamId);
        assertThat(recorded.actorId()).isEqualTo(inviter);
        assertThat(recorded.afterState()).contains("\"userId\":\"" + userId + "\"");
    }

    @Test
    void onTeamMemberRemoved_recordsAuditEvent_withBeforeStateContainingUserId() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        listener.onTeamMemberRemoved(new TeamMemberRemovedEvent(teamId, userId, actor));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent recorded = captor.getValue();
        assertThat(recorded.eventType()).isEqualTo(AuditEventType.TEAM_MEMBER_REMOVED);
        assertThat(recorded.targetType()).isEqualTo(AuditTargetType.TEAM);
        assertThat(recorded.targetId()).isEqualTo(teamId);
        assertThat(recorded.actorId()).isEqualTo(actor);
        assertThat(recorded.beforeState()).contains("\"userId\":\"" + userId + "\"");
        assertThat(recorded.afterState()).isNull();
    }

    @Test
    void onTeamMemberRoleChanged_recordsAuditWithBeforeAndAfterRoles() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        listener.onTeamMemberRoleChanged(new TeamMemberRoleChangedEvent(
            teamId, userId, TeamMembership.Role.MEMBER, TeamMembership.Role.OWNER, actor
        ));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent recorded = captor.getValue();
        assertThat(recorded.eventType()).isEqualTo(AuditEventType.TEAM_MEMBER_ROLE_CHANGED);
        assertThat(recorded.targetType()).isEqualTo(AuditTargetType.TEAM);
        assertThat(recorded.targetId()).isEqualTo(teamId);
        assertThat(recorded.actorId()).isEqualTo(actor);
        assertThat(recorded.beforeState()).contains("\"role\":\"MEMBER\"");
        assertThat(recorded.afterState()).contains("\"role\":\"OWNER\"");
        assertThat(recorded.metadata()).isNull();
    }

    @Test
    void onTeamCreated_swallowsAuditFailure_doesNotPropagate() {
        UUID teamId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        doThrow(new RuntimeException("audit failed")).when(auditService).record(Mockito.any());

        assertThatCode(() ->
            listener.onTeamCreated(new TeamCreatedEvent(teamId, actor, "Alpha"))
        ).doesNotThrowAnyException();
    }
}
