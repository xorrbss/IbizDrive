package com.ibizdrive.team;

import com.ibizdrive.folder.ScopeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link TeamArchiveGuard} 단위 검증 — Spring 컨텍스트 없이 Mockito로 TeamRepository를 stub.
 *
 * <p>spec §2.2 archive 라이프사이클 가드 — write 진입점에서 archived 팀 콘텐츠 변경을 차단.
 */
@ExtendWith(MockitoExtension.class)
class TeamArchiveGuardTest {

    @Mock
    private TeamRepository teamRepository;

    private TeamArchiveGuard guard;

    @BeforeEach
    void setUp() {
        guard = new TeamArchiveGuard(teamRepository);
    }

    @Test
    void assertNotArchived_isNoOp_whenScopeIsDepartment() {
        UUID scopeId = UUID.randomUUID();

        guard.assertNotArchived(ScopeType.DEPARTMENT, scopeId);

        verifyNoInteractions(teamRepository);
    }

    @Test
    void assertNotArchived_passes_whenTeamActive() {
        UUID teamId = UUID.randomUUID();
        Team active = new Team(teamId, "Engineering", "engineering", null,
            Team.Visibility.PRIVATE, UUID.randomUUID(), OffsetDateTime.now());
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(active));

        guard.assertNotArchived(ScopeType.TEAM, teamId);

        verify(teamRepository).findById(teamId);
    }

    @Test
    void assertNotArchived_throws_whenTeamArchived() {
        UUID teamId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Team archived = new Team(teamId, "Engineering", "engineering", null,
            Team.Visibility.PRIVATE, UUID.randomUUID(), OffsetDateTime.now());
        archived.archive(actorId, OffsetDateTime.now());
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(archived));

        assertThatThrownBy(() -> guard.assertNotArchived(ScopeType.TEAM, teamId))
            .isInstanceOf(TeamArchivedException.class)
            .satisfies(ex -> assertThat(((TeamArchivedException) ex).getTeamId()).isEqualTo(teamId));
    }

    @Test
    void assertNotArchived_silentPass_whenTeamNotFound() {
        UUID scopeId = UUID.randomUUID();
        when(teamRepository.findById(scopeId)).thenReturn(Optional.empty());

        guard.assertNotArchived(ScopeType.TEAM, scopeId);

        // dangling scope_id (정상 invariant 위반) — 다른 검증 경로에 위임 (KISS)
        verify(teamRepository).findById(scopeId);
        verify(teamRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
