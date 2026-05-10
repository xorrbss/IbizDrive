package com.ibizdrive.team;

import com.ibizdrive.team.dto.TeamMemberResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * 팀 멤버십 조회 repository — team-centric pivot Plan A (Task 11).
 *
 * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §1.1.
 * 세 가지 조회 쿼리만 제공 — 단건 조회는 {@link JpaRepository#findById(Object)}에
 * {@link TeamMembershipId}를 직접 전달하면 충분하므로 중복 정의하지 않는다 (YAGNI).
 *
 * <p><b>peer pattern</b>: {@link TeamRepository} — 동일한 {@code @Query}/{@code @Param}
 * 스타일, active-only 필터가 없다는 점에서 멤버십은 항상 존재 여부로만 판단.
 *
 * <p><b>last-OWNER 가드</b>: {@link #countByTeamIdAndRole}가 이 가드의 기반이 되나,
 * 실제 가드 로직(last OWNER 제거 차단)은 Plan A2 service layer 이월.
 */
public interface TeamMembershipRepository
        extends JpaRepository<TeamMembership, TeamMembershipId> {

    /**
     * 특정 사용자가 속한 모든 멤버십을 반환한다.
     *
     * <p>반환 순서는 미지정 — 순서가 필요한 경우 호출자가 정렬.
     * 결과가 비어 있으면 사용자가 어떤 팀에도 속하지 않음을 의미한다.
     *
     * @param userId 조회할 사용자 ID (null 불가)
     * @return 해당 사용자의 {@link TeamMembership} 목록; 없으면 빈 리스트
     */
    @Query("SELECT m FROM TeamMembership m WHERE m.id.userId = :user")
    List<TeamMembership> findByUserId(@Param("user") UUID userId);

    /**
     * 특정 팀에서 주어진 role을 가진 멤버 수를 반환한다.
     *
     * <p><b>주요 사용처 — last-OWNER 가드 (Plan A2)</b>: {@code role = OWNER}로 호출하면
     * 팀에 남은 OWNER 수를 확인할 수 있다. 이 값이 1일 때 해당 OWNER를 제거하거나
     * MEMBER로 강등하면 팀이 무주공산 상태가 되므로, service layer는 이 메서드로 선제 검사 후
     * 거부 처리해야 한다. 실제 가드 구현은 Plan A2 이월.
     *
     * @param teamId 조회할 팀 ID (null 불가)
     * @param role   집계할 role (null 불가)
     * @return 해당 팀에서 지정 role을 가진 멤버 수; 없으면 0
     */
    @Query("SELECT COUNT(m) FROM TeamMembership m " +
           "WHERE m.id.teamId = :team AND m.role = :role")
    long countByTeamIdAndRole(@Param("team") UUID teamId, @Param("role") TeamMembership.Role role);

    /**
     * 특정 팀의 총 멤버 수 — admin team list/detail summary용 (T8).
     */
    @Query("SELECT COUNT(m) FROM TeamMembership m WHERE m.id.teamId = :team")
    long countByTeamId(@Param("team") UUID teamId);

    /**
     * 특정 팀의 모든 멤버십을 반환한다.
     *
     * <p>반환 순서는 미지정 — 순서가 필요한 경우 호출자가 정렬.
     * 결과가 비어 있으면 팀에 멤버가 없음(정상 데이터에서는 발생하지 않아야 한다 — 팀 생성 시
     * 생성자가 OWNER로 함께 등록되므로).
     *
     * @param teamId 조회할 팀 ID (null 불가)
     * @return 해당 팀의 {@link TeamMembership} 목록; 없으면 빈 리스트
     */
    @Query("SELECT m FROM TeamMembership m WHERE m.id.teamId = :team")
    List<TeamMembership> findByTeamId(@Param("team") UUID teamId);

    /**
     * 팀 멤버 목록을 user 정보(displayName, email)와 함께 DTO로 직접 반환한다 — Plan F T2.
     *
     * <p>JPQL constructor projection으로 entity 측 User 매핑 추가 없이 read-only JOIN.
     * spec docs/superpowers/specs/2026-05-10-team-centric-pivot-plan-f-team-member-mgmt-design.md §3.4.
     *
     * @param teamId 조회할 팀 ID (null 불가)
     * @return TeamMemberResponse list — joinedAt 오름차순. team이 없거나 멤버가 없으면 빈 리스트.
     */
    @Query("""
        SELECT new com.ibizdrive.team.dto.TeamMemberResponse(
            m.id.userId, u.displayName, u.email, m.role, m.joinedAt
        )
        FROM TeamMembership m, com.ibizdrive.user.User u
        WHERE m.id.teamId = :team AND m.id.userId = u.id
        ORDER BY m.joinedAt ASC
        """)
    List<TeamMemberResponse> findMembersWithUser(@Param("team") UUID teamId);
}
