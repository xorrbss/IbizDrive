package com.ibizdrive.workspace;

import com.ibizdrive.department.Department;
import com.ibizdrive.team.Team;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link DepartmentWorkspace} and {@link TeamWorkspace} adapters.
 * No Spring context — adapters are plain records delegating to domain entities.
 */
class WorkspaceAdaptersTest {

    @Test
    void departmentAdapter_mapsKindAndIdAndRoot() {
        Department d = new Department(UUID.randomUUID(), "Sales", OffsetDateTime.now());
        UUID root = UUID.randomUUID();
        d.attachRootFolder(root);
        Workspace w = new DepartmentWorkspace(d);
        assertThat(w.kind()).isEqualTo(WorkspaceKind.DEPARTMENT);
        assertThat(w.id()).isEqualTo(d.getId());
        assertThat(w.name()).isEqualTo("Sales");
        assertThat(w.rootFolderId()).isEqualTo(root);
        assertThat(w.isActive()).isTrue();
    }

    @Test
    void teamAdapter_mapsKindAndRoot() {
        Team t = new Team(UUID.randomUUID(), "Alpha", "alpha", null,
            Team.Visibility.PRIVATE, UUID.randomUUID(), OffsetDateTime.now());
        UUID root = UUID.randomUUID();
        t.attachRootFolder(root);
        Workspace w = new TeamWorkspace(t);
        assertThat(w.kind()).isEqualTo(WorkspaceKind.TEAM);
        assertThat(w.id()).isEqualTo(t.getId());
        assertThat(w.name()).isEqualTo("Alpha");
        assertThat(w.rootFolderId()).isEqualTo(root);
        assertThat(w.isActive()).isTrue();
    }
}
