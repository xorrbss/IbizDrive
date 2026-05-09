package com.ibizdrive.team;

import com.ibizdrive.folder.ScopeType;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Folder/FileItem write 진입점에서 호출하는 archive 가드.
 *
 * <p>spec §2.2 — archived 팀의 콘텐츠는 read-only. write 진입점(create/upload/move/rename/delete/restore/restoreVersion)이
 * mutation 직전에 본 가드를 호출해 {@link TeamArchivedException} (HTTP 423 + {@code TEAM_ARCHIVED})을 던진다.
 *
 * <p><b>scope 분기</b>:
 * <ul>
 *   <li>{@link ScopeType#DEPARTMENT}: no-op (부서 deactivate는 별도 정책 — 본 가드 적용 X).
 *   <li>{@link ScopeType#TEAM}: TeamRepository로 active 여부 검증.
 * </ul>
 *
 * <p><b>dangling scope_id</b>: Team이 미존재인 경우(정상 invariant 위반) silent pass — 다른 검증 경로에 위임 (KISS).
 *
 * <p>peer pattern: {@link com.ibizdrive.folder.CrossScopeMoveException} — 가드 helper는 진입점에서 1회 호출.
 */
@Service
public class TeamArchiveGuard {

    private final TeamRepository teamRepository;

    public TeamArchiveGuard(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    /**
     * @throws TeamArchivedException scope=TEAM이고 해당 Team이 archived (archivedAt != null) 상태일 때
     */
    public void assertNotArchived(ScopeType scopeType, UUID scopeId) {
        if (scopeType != ScopeType.TEAM) {
            return;
        }
        teamRepository.findById(scopeId).ifPresent(team -> {
            if (!team.isActive()) {
                throw new TeamArchivedException(scopeId);
            }
        });
    }
}
