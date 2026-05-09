package com.ibizdrive.team;

import java.util.UUID;

/**
 * 팀에서 마지막 OWNER를 제거하거나 OWNER를 다른 역할로 강등할 때 발생.
 *
 * <p>spec §2.2 팀 라이프사이클 — 팀은 언제나 최소 한 명의 OWNER를 가져야 한다.
 * {@code TeamService.remove} 또는 {@code TeamService.changeRole}이 이 불변을 깨뜨리려 할 때 throw.
 *
 * <p>HTTP 400 + envelope code {@code TEAM_OWNER_REQUIRED} 로 변환 ({@link com.ibizdrive.common.error.GlobalExceptionHandler}).
 * spec §5.4 참조 — spec 문서에서는 {@code ERR_TEAM_OWNER_REQUIRED} 로 표기하나, wire format은 접두사 없이
 * {@code TEAM_OWNER_REQUIRED} (peer: {@code DEPARTMENT_CONFLICT}, {@code RENAME_CONFLICT}).
 */
public class LastOwnerRequiredException extends RuntimeException {

    /**
     * @param teamId 최소 OWNER 보장 불변이 위반될 뻔한 팀의 ID
     */
    public LastOwnerRequiredException(UUID teamId) {
        super("team requires at least one OWNER: " + teamId);
    }
}
