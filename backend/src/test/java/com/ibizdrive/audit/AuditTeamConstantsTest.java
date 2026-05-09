package com.ibizdrive.audit;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AuditTeamConstantsTest {

    @Test
    void auditTargetType_includesTeam() {
        assertThat(AuditTargetType.TEAM.wire()).isEqualTo("team");
    }

    @Test
    void auditEventType_includesTeamLifecycleEvents() {
        assertThat(AuditEventType.TEAM_CREATED.wire()).isEqualTo("team.created");
        assertThat(AuditEventType.TEAM_MEMBER_ADDED.wire()).isEqualTo("team.member.added");
        assertThat(AuditEventType.TEAM_MEMBER_REMOVED.wire()).isEqualTo("team.member.removed");
    }
}
