package com.ibizdrive.team;

import java.util.UUID;

/**
 * archived 팀 소속 콘텐츠에 write(create/upload/move/rename/delete/restore) 시도 시 발생.
 *
 * <p>spec §2.2 — archived 팀의 콘텐츠는 read-only.
 * spec §5.4 — wire format {@code TEAM_ARCHIVED} (HTTP 423 Locked). spec 표기 {@code ERR_TEAM_ARCHIVED}는 spec 내부.
 *
 * <p>HTTP 423 + envelope code {@code TEAM_ARCHIVED} 매핑은 {@link com.ibizdrive.common.error.GlobalExceptionHandler}.
 */
public class TeamArchivedException extends RuntimeException {

    private final UUID teamId;

    public TeamArchivedException(UUID teamId) {
        super("team is archived: " + teamId);
        this.teamId = teamId;
    }

    public UUID getTeamId() {
        return teamId;
    }
}
