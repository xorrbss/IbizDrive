package com.ibizdrive.team;

/**
 * 동일 normalized name으로 active team이 이미 존재할 때 발생 — Plan A Task 16.
 *
 * <p>V12 partial unique {@code idx_teams_name_active}와 service 레벨 사전 검사에서 같은 시그니처 사용.
 * race window는 DB unique 제약이 최종 가드.
 */
public class TeamNameConflictException extends RuntimeException {
    public TeamNameConflictException(String name) {
        super("active team with name already exists: " + name);
    }
}
