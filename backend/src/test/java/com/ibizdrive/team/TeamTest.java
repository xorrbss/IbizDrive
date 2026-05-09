package com.ibizdrive.team;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link Team} 도메인 메서드 단위 테스트 — Plan A Task 5 (team-centric pivot).
 *
 * <p>입력 검증과 상태 전이만 다룬다. 충돌 검출/normalize/audit emit은 service layer 책임 (별도 테스트).
 */
class TeamTest {

    @Test
    void newActiveTeamHasNoArchivedAt() {
        Team team = newTeam("Engineering");

        assertThat(team.isActive()).isTrue();
        assertThat(team.getArchivedAt()).isNull();
        assertThat(team.getArchivedBy()).isNull();
    }

    @Test
    void renameTrimsAndValidates() {
        Team team = newTeam("Old");

        team.rename("  New Team  ");
        assertThat(team.getName()).isEqualTo("New Team");

        assertThatThrownBy(() -> team.rename(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("null");

        assertThatThrownBy(() -> team.rename("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank");
    }

    @Test
    void rejectVisibilityNull() {
        Team team = newTeam("Eng");

        assertThatThrownBy(() -> team.changeVisibility(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changeVisibilityStoresLowercase() {
        Team team = newTeam("Eng");
        // 생성자가 PRIVATE → 'private'으로 저장. 토글로 INTERNAL → 'internal' 저장 검증.
        team.changeVisibility(Team.Visibility.INTERNAL);

        assertThat(team.getVisibility()).isEqualTo(Team.Visibility.INTERNAL);
        assertThat(Team.Visibility.INTERNAL.dbValue()).isEqualTo("internal");
        assertThat(Team.Visibility.PRIVATE.dbValue()).isEqualTo("private");
    }

    @Test
    void attachRootFolderIsOneShot() {
        Team team = newTeam("Eng");
        UUID root = UUID.randomUUID();

        team.attachRootFolder(root);
        assertThat(team.getRootFolderId()).isEqualTo(root);

        assertThatThrownBy(() -> team.attachRootFolder(UUID.randomUUID()))
            .isInstanceOf(IllegalStateException.class);

        Team fresh = newTeam("Other");
        assertThatThrownBy(() -> fresh.attachRootFolder(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static Team newTeam(String name) {
        return new Team(
            UUID.randomUUID(),
            name,
            name.toLowerCase(),
            null,
            Team.Visibility.PRIVATE,
            UUID.randomUUID(),
            OffsetDateTime.now()
        );
    }
}
