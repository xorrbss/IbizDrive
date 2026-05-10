package com.ibizdrive.team;

import com.ibizdrive.team.dto.TeamCreateRequest;
import com.ibizdrive.team.dto.TeamMemberInviteRequest;
import com.ibizdrive.team.dto.TeamMemberResponse;
import com.ibizdrive.team.dto.TeamMemberRoleUpdateRequest;
import com.ibizdrive.team.dto.TeamResponse;
import com.ibizdrive.user.IbizDriveUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 팀 REST API — Plan A Task 19.
 *
 * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §5.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/teams} — create (인증 사용자 누구나)</li>
 *   <li>{@code POST /api/teams/{teamId}/members} — invite (OWNER만)</li>
 *   <li>{@code DELETE /api/teams/{teamId}/members/{userId}} — remove (OWNER 또는 자기 자신)</li>
 *   <li>{@code GET /api/teams/{teamId}/members} — list members (팀 멤버만)</li>
 *   <li>{@code PATCH /api/teams/{teamId}/members/{userId}} — change role (OWNER만)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService svc;

    public TeamController(TeamService svc) {
        this.svc = svc;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TeamResponse> create(@Valid @RequestBody TeamCreateRequest req,
                                               @AuthenticationPrincipal IbizDriveUserDetails principal) {
        UUID actor = principal.getUser().getId();
        Team.Visibility visibility = req.visibility() == null ? Team.Visibility.PRIVATE : req.visibility();
        Team t = svc.create(req.name(), req.description(), visibility, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(TeamResponse.of(t));
    }

    @PostMapping("/{teamId}/members")
    @PreAuthorize("@teamAuthz.isOwner(#teamId, principal)")
    public ResponseEntity<Void> invite(@PathVariable UUID teamId,
                                       @Valid @RequestBody TeamMemberInviteRequest req,
                                       @AuthenticationPrincipal IbizDriveUserDetails principal) {
        UUID actor = principal.getUser().getId();
        svc.invite(teamId, req.userId(), actor);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{teamId}/members/{userId}")
    @PreAuthorize("@teamAuthz.isOwnerOrSelf(#teamId, #userId, principal)")
    public ResponseEntity<Void> remove(@PathVariable UUID teamId,
                                       @PathVariable UUID userId,
                                       @AuthenticationPrincipal IbizDriveUserDetails principal) {
        UUID actor = principal.getUser().getId();
        svc.remove(teamId, userId, actor);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{teamId}/members")
    @PreAuthorize("@teamAuthz.isMember(#teamId, principal)")
    public ResponseEntity<List<TeamMemberResponse>> listMembers(@PathVariable UUID teamId) {
        return ResponseEntity.ok(svc.listMembers(teamId));
    }

    @PatchMapping("/{teamId}/members/{userId}")
    @PreAuthorize("@teamAuthz.isOwner(#teamId, principal)")
    public ResponseEntity<Void> changeRole(@PathVariable UUID teamId,
                                           @PathVariable UUID userId,
                                           @Valid @RequestBody TeamMemberRoleUpdateRequest req,
                                           @AuthenticationPrincipal IbizDriveUserDetails principal) {
        UUID actor = principal.getUser().getId();
        svc.changeRole(teamId, userId, req.role(), actor);
        return ResponseEntity.noContent().build();
    }
}
