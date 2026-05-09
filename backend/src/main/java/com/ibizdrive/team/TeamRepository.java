package com.ibizdrive.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * 팀 조회 repository — team-centric pivot Plan A (Task 10).
 *
 * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §1.1.
 * 단건 active 이름 조회만 제공 — 아카이브 관련 ops는 Plan A2 이월.
 *
 * <p><b>peer pattern</b>: {@link com.ibizdrive.department.DepartmentRepository#findActiveByName}
 * — soft-archive/soft-delete 제외 + active-name 단건 lookup이라는 동일 의도.
 */
public interface TeamRepository extends JpaRepository<Team, UUID> {

    /**
     * active 팀을 normalized name으로 단건 조회.
     *
     * <p><b>active 정의</b>: {@code archived_at IS NULL} ({@link Team#isActive()}와 동일 시맨틱).
     *
     * <p><b>대소문자 구분</b>: 동등 비교(JPQL {@code =}) — 호출자가 사전에 정규화된 소문자 문자열을 넘겨야 한다.
     * 비정규화 입력(예: {@code "Alpha"}) 전달 시 silent empty 반환. V12 partial unique
     * {@code idx_teams_name_active}와 동일 조건 — service의 사전 충돌 검사가 race window를 좁힌다.
     *
     * @param name 정규화된 팀 이름 (null 불가)
     * @return active 팀; 없거나 archived면 empty
     */
    @Query("SELECT t FROM Team t WHERE t.normalizedName = :name AND t.archivedAt IS NULL")
    Optional<Team> findActiveByNormalizedName(@Param("name") String name);
}
