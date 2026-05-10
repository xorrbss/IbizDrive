package com.ibizdrive.admin;

import com.ibizdrive.common.error.ResourceNotFoundException;
import com.ibizdrive.common.normalize.NormalizeUtil;
import com.ibizdrive.team.Team;
import com.ibizdrive.team.TeamMembershipId;
import com.ibizdrive.team.TeamMembershipRepository;
import com.ibizdrive.team.TeamNameConflictException;
import com.ibizdrive.team.TeamRepository;
import com.ibizdrive.team.TeamService;
import com.ibizdrive.team.TeamUpdatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Admin team 관리 서비스 — T8 (design-refresh-admin admin-teams.jsx).
 *
 * <p>endpoints:
 * <ul>
 *   <li>{@link #list} — 모든 팀 (active + archived) summary list</li>
 *   <li>{@link #detail} — 단건 detail</li>
 *   <li>{@link #update} — name/description/color/leadId PATCH</li>
 *   <li>{@link #archive} — DELETE (= soft archive, {@link TeamService#archive} 위임)</li>
 * </ul>
 *
 * <p>audit: PATCH는 {@link TeamUpdatedEvent}를 발행 → {@code TeamAuditListener} 가
 * AFTER_COMMIT으로 audit_log row 작성. archive는 기존 {@code TeamArchivedEvent} 재사용.
 *
 * <p>last-OWNER 가드, 멤버 추가/제거는 일반 {@code /api/teams/*} endpoint(TeamService)를
 * admin도 그대로 사용 — 정책 분리가 의미 없음 (admin도 동일 last-OWNER 보장 필요).
 */
@Service
public class AdminTeamService {

    private final TeamRepository teamRepo;
    private final TeamMembershipRepository memRepo;
    private final TeamService teamSvc;
    private final ApplicationEventPublisher events;

    public AdminTeamService(TeamRepository teamRepo,
                            TeamMembershipRepository memRepo,
                            TeamService teamSvc,
                            ApplicationEventPublisher events) {
        this.teamRepo = teamRepo;
        this.memRepo = memRepo;
        this.teamSvc = teamSvc;
        this.events = events;
    }

    /**
     * 모든 팀 list — active + archived 모두 포함. 디자인 admin-teams.jsx TeamsListPanel은
     * 검색만 제공하고 별도 archive filter UI가 없음 → 모두 반환하고 클라이언트에서 분류.
     *
     * <p>memberCount는 팀별 별도 query (count(*)). N+1 — admin 환경 (팀 수 < 1000) 가정.
     */
    @Transactional(readOnly = true)
    public List<AdminTeamSummaryResponse> list() {
        List<Team> teams = teamRepo.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        List<AdminTeamSummaryResponse> result = new ArrayList<>(teams.size());
        for (Team t : teams) {
            long count = memRepo.countByTeamId(t.getId());
            result.add(AdminTeamSummaryResponse.from(t, count));
        }
        return result;
    }

    /**
     * 팀 단건 detail — admin-teams.jsx TeamDetail 헤더 + StatRow.
     *
     * @throws ResourceNotFoundException teamId 미존재
     */
    @Transactional(readOnly = true)
    public AdminTeamDetailResponse detail(UUID teamId) {
        Team t = teamRepo.findById(teamId)
            .orElseThrow(() -> new ResourceNotFoundException("team not found: " + teamId));
        long count = memRepo.countByTeamId(teamId);
        return AdminTeamDetailResponse.from(t, count);
    }

    /**
     * 팀 메타데이터 PATCH — name/description/color/leadId 중 하나 이상 변경.
     *
     * <p>변경 의미:
     * <ul>
     *   <li>name: rename + normalize + active 충돌 검사 ({@link TeamNameConflictException})</li>
     *   <li>description: blank → null 로 정규화</li>
     *   <li>color: #RRGGBB 검증</li>
     *   <li>leadId: TeamMembership 멤버 검증 후 assign</li>
     * </ul>
     *
     * <p>변경된 필드 wire 이름을 모아 {@link TeamUpdatedEvent}로 발행 (audit_log {@code afterState.changedFields}).
     *
     * @throws ResourceNotFoundException     teamId 미존재
     * @throws TeamNameConflictException     같은 normalized name 활성 팀 존재
     * @throws IllegalArgumentException      필드 검증 실패 (Team 도메인 메서드)
     * @throws AdminBadPatchException        leadId가 팀 멤버가 아님
     */
    @Transactional
    public AdminTeamDetailResponse update(UUID teamId,
                                          String name,
                                          String description,
                                          String color,
                                          UUID leadId,
                                          UUID actorId) {
        Team t = teamRepo.findById(teamId)
            .orElseThrow(() -> new ResourceNotFoundException("team not found: " + teamId));

        List<String> changed = new ArrayList<>();

        if (name != null && !name.isBlank()) {
            String displayName = NormalizeUtil.normalizeFileName(name);
            String normalizedName = NormalizeUtil.normalizedNameForDedup(name);
            if (!normalizedName.equals(t.getNormalizedName())) {
                teamRepo.findActiveByNormalizedName(normalizedName)
                    .ifPresent(existing -> {
                        if (!existing.getId().equals(teamId)) {
                            throw new TeamNameConflictException(displayName);
                        }
                    });
                t.rename(displayName);
                t.setNormalizedName(normalizedName);
                changed.add("name");
            } else if (!displayName.equals(t.getName())) {
                // 동일 normalized이지만 표시 이름만 바뀐 경우 (대소문자/공백)
                t.rename(displayName);
                changed.add("name");
            }
        }

        if (description != null) {
            t.updateDescription(description);
            changed.add("description");
        }

        if (color != null && !color.isBlank()) {
            t.changeColor(color);
            changed.add("color");
        }

        if (leadId != null && !leadId.equals(t.getLeadId())) {
            // 팀 멤버 검증
            TeamMembershipId mid = new TeamMembershipId(teamId, leadId);
            if (memRepo.findById(mid).isEmpty()) {
                throw new AdminBadPatchException("leadId must be an existing team member: " + leadId);
            }
            t.assignLead(leadId);
            changed.add("leadId");
        }

        if (changed.isEmpty()) {
            // 모든 필드가 현재 값과 동일 — 변경 없음, audit emit 안 함
            return AdminTeamDetailResponse.from(t, memRepo.countByTeamId(teamId));
        }

        t.touchUpdatedAt(OffsetDateTime.now());
        events.publishEvent(new TeamUpdatedEvent(teamId, actorId, String.join(",", changed)));

        return AdminTeamDetailResponse.from(t, memRepo.countByTeamId(teamId));
    }

    /**
     * 팀 archive (DELETE 의미) — {@link TeamService#archive} 위임.
     * 기존 TEAM_ARCHIVED audit event 재사용 — 별도 admin 전용 이벤트 만들지 않는다 (KISS).
     *
     * @throws ResourceNotFoundException teamId 미존재
     */
    @Transactional
    public void archive(UUID teamId, UUID actorId) {
        if (teamRepo.findById(teamId).isEmpty()) {
            throw new ResourceNotFoundException("team not found: " + teamId);
        }
        teamSvc.archive(teamId, actorId);
    }

    /**
     * Admin이 archived 팀을 restore — {@link TeamService#restore} 위임.
     * 디자인 admin-teams.jsx에는 명시적 restore 액션이 없지만 아카이브된 팀이 보이므로 backend는 제공.
     */
    @Transactional
    public void restore(UUID teamId, UUID actorId) {
        if (teamRepo.findById(teamId).isEmpty()) {
            throw new ResourceNotFoundException("team not found: " + teamId);
        }
        teamSvc.restore(teamId, actorId);
    }
}
