package com.ibizdrive.team;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Team} 도메인 메서드 단위 테스트 — Plan A Task 5 (team-centric pivot).
 *
 * <p>입력 검증과 상태 전이만 다룬다. 충돌 검출/normalize/audit emit은 service layer 책임 (별도 테스트).
 */
class TeamTest {

    @Test
    void newActiveTeamHasNoArchivedAt() {
        Team team = newTeam("Engineering");

        assertTrue(team.isActive());
        assertNull(team.getArchivedAt());
        assertNull(team.getArchivedBy());
    }

    @Test
    void renameTrimsAndValidates() {
        Team team = newTeam("Old");

        team.rename("  New Team  ");
        assertEquals("New Team", team.getName());

        IllegalArgumentException nullEx = assertThrows(
            IllegalArgumentException.class, () -> team.rename(null)
        );
        assertTrue(nullEx.getMessage().contains("null"));

        IllegalArgumentException blankEx = assertThrows(
            IllegalArgumentException.class, () -> team.rename("   ")
        );
        assertTrue(blankEx.getMessage().contains("blank"));
    }

    @Test
    void rejectVisibilityNull() {
        Team team = newTeam("Eng");

        assertThrows(IllegalArgumentException.class, () -> team.changeVisibility(null));
    }

    @Test
    void changeVisibilityStoresLowercase() {
        Team team = newTeam("Eng");
        // 생성자가 PRIVATE → 'private'으로 저장. 토글로 INTERNAL → 'internal' 저장 검증.
        team.changeVisibility(Team.Visibility.INTERNAL);

        assertEquals(Team.Visibility.INTERNAL, team.getVisibility());
        assertEquals("internal", Team.Visibility.INTERNAL.dbValue());
        assertEquals("private", Team.Visibility.PRIVATE.dbValue());
    }

    @Test
    void attachRootFolderIsOneShot() {
        Team team = newTeam("Eng");
        UUID root = UUID.randomUUID();

        team.attachRootFolder(root);
        assertEquals(root, team.getRootFolderId());

        assertThrows(IllegalStateException.class, () -> team.attachRootFolder(UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> {
            Team fresh = newTeam("Other");
            fresh.attachRootFolder(null);
        });
    }

    @Test
    void archiveIsIdempotent() {
        Team team = newTeam("Eng");
        UUID actor = UUID.randomUUID();
        OffsetDateTime first = OffsetDateTime.now();

        team.archive(actor, first);
        assertFalse(team.isActive());
        assertEquals(first, team.getArchivedAt());
        assertEquals(actor, team.getArchivedBy());

        // 두 번째 호출은 최초 archive 시점/actor를 보존.
        team.archive(UUID.randomUUID(), first.plusSeconds(60));
        assertEquals(first, team.getArchivedAt());
        assertEquals(actor, team.getArchivedBy());

        team.unarchive();
        assertTrue(team.isActive());
        assertNull(team.getArchivedAt());
        assertNull(team.getArchivedBy());
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
