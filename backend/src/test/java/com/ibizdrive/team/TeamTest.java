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

    @Test
    void archive_setsArchivedAtAndBy_andUpdatesUpdatedAt() {
        Team team = newTeam("Engineering");
        UUID actor = UUID.randomUUID();
        OffsetDateTime before = team.getUpdatedAt();
        OffsetDateTime now = before.plusSeconds(10);

        team.archive(actor, now);

        assertThat(team.isActive()).isFalse();
        assertThat(team.getArchivedAt()).isEqualTo(now);
        assertThat(team.getArchivedBy()).isEqualTo(actor);
        assertThat(team.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void archive_isNoOp_whenAlreadyArchived() {
        Team team = newTeam("Engineering");
        UUID firstActor = UUID.randomUUID();
        OffsetDateTime firstTime = OffsetDateTime.now();
        team.archive(firstActor, firstTime);

        UUID secondActor = UUID.randomUUID();
        OffsetDateTime secondTime = firstTime.plusSeconds(60);
        team.archive(secondActor, secondTime);

        assertThat(team.getArchivedAt()).isEqualTo(firstTime);
        assertThat(team.getArchivedBy()).isEqualTo(firstActor);
        assertThat(team.getUpdatedAt()).isEqualTo(firstTime);
    }

    @Test
    void archive_throwsIllegalArgument_whenActorIdNull() {
        Team team = newTeam("Engineering");

        assertThatThrownBy(() -> team.archive(null, OffsetDateTime.now()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("actorId");
    }

    @Test
    void archive_throwsIllegalArgument_whenNowNull() {
        Team team = newTeam("Engineering");

        assertThatThrownBy(() -> team.archive(UUID.randomUUID(), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("now");
    }

    @Test
    void restore_clearsArchivedAtAndBy_andUpdatesUpdatedAt() {
        Team team = newTeam("Engineering");
        UUID actor = UUID.randomUUID();
        OffsetDateTime archiveTime = OffsetDateTime.now();
        team.archive(actor, archiveTime);

        OffsetDateTime restoreTime = archiveTime.plusSeconds(30);
        team.restore(restoreTime);

        assertThat(team.isActive()).isTrue();
        assertThat(team.getArchivedAt()).isNull();
        assertThat(team.getArchivedBy()).isNull();
        assertThat(team.getUpdatedAt()).isEqualTo(restoreTime);
    }

    @Test
    void restore_isNoOp_whenAlreadyActive() {
        Team team = newTeam("Engineering");
        OffsetDateTime originalUpdatedAt = team.getUpdatedAt();

        team.restore(originalUpdatedAt.plusSeconds(10));

        assertThat(team.isActive()).isTrue();
        assertThat(team.getArchivedAt()).isNull();
        assertThat(team.getArchivedBy()).isNull();
        assertThat(team.getUpdatedAt()).isEqualTo(originalUpdatedAt);
    }

    @Test
    void restore_throwsIllegalArgument_whenNowNull() {
        Team team = newTeam("Engineering");

        assertThatThrownBy(() -> team.restore(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("now");
    }

    // ==================== V16 admin metadata (T8-P3) ====================

    @Test
    void backwardCompatConstructor_setsDefaultColorAndLeadIdToCreator() {
        UUID creator = UUID.randomUUID();
        Team team = new Team(
            UUID.randomUUID(), "Eng", "eng", null,
            Team.Visibility.PRIVATE, creator, OffsetDateTime.now()
        );
        assertThat(team.getColor()).isEqualTo(Team.DEFAULT_COLOR);
        assertThat(team.getLeadId()).isEqualTo(creator);
    }

    @Test
    void fullConstructor_acceptsExplicitColorAndLead() {
        UUID creator = UUID.randomUUID();
        UUID lead = UUID.randomUUID();
        Team team = new Team(
            UUID.randomUUID(), "Eng", "eng", "팀 설명",
            "#C16A8B", lead,
            Team.Visibility.PRIVATE, creator, OffsetDateTime.now()
        );
        assertThat(team.getColor()).isEqualTo("#C16A8B");
        assertThat(team.getLeadId()).isEqualTo(lead);
        assertThat(team.getCreatedBy()).isEqualTo(creator);
    }

    @Test
    void fullConstructor_rejectsInvalidColor() {
        UUID creator = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        assertThatThrownBy(() -> new Team(
            UUID.randomUUID(), "Eng", "eng", null,
            "blue", creator,
            Team.Visibility.PRIVATE, creator, now
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("hex");
    }

    @Test
    void fullConstructor_rejectsNullLeadId() {
        UUID creator = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        assertThatThrownBy(() -> new Team(
            UUID.randomUUID(), "Eng", "eng", null,
            "#5B7FCC", null,
            Team.Visibility.PRIVATE, creator, now
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("leadId");
    }

    @Test
    void changeColor_acceptsValidHex() {
        Team team = newTeam("Eng");
        team.changeColor("#abcdef");
        assertThat(team.getColor()).isEqualTo("#abcdef");
        team.changeColor("#5BA08A");
        assertThat(team.getColor()).isEqualTo("#5BA08A");
    }

    @Test
    void changeColor_rejectsInvalidFormats() {
        Team team = newTeam("Eng");
        assertThatThrownBy(() -> team.changeColor(null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> team.changeColor("blue"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> team.changeColor("#fff"))   // shorthand 미허용
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> team.changeColor("5B7FCC")) // # 누락
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assignLead_setsLeadId_rejectsNull() {
        Team team = newTeam("Eng");
        UUID newLead = UUID.randomUUID();
        team.assignLead(newLead);
        assertThat(team.getLeadId()).isEqualTo(newLead);

        assertThatThrownBy(() -> team.assignLead(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("leadId");
    }

    @Test
    void updateDescription_normalizesNullBlankAndTrims() {
        Team team = newTeam("Eng");

        team.updateDescription("  설명 텍스트  ");
        assertThat(team.getDescription()).isEqualTo("설명 텍스트");

        team.updateDescription(null);
        assertThat(team.getDescription()).isNull();

        team.updateDescription("");
        assertThat(team.getDescription()).isNull();

        team.updateDescription("   ");
        assertThat(team.getDescription()).isNull();
    }

    @Test
    void updateDescription_rejectsTooLong() {
        Team team = newTeam("Eng");
        String longDesc = "x".repeat(1001);
        assertThatThrownBy(() -> team.updateDescription(longDesc))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("1000");
    }

    @Test
    void touchUpdatedAt_setsField_rejectsNull() {
        Team team = newTeam("Eng");
        OffsetDateTime later = team.getUpdatedAt().plusSeconds(60);
        team.touchUpdatedAt(later);
        assertThat(team.getUpdatedAt()).isEqualTo(later);

        assertThatThrownBy(() -> team.touchUpdatedAt(null))
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
