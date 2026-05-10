package com.ibizdrive.admin;

import com.ibizdrive.user.IbizDriveUserDetails;
import jakarta.validation.Valid;
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
 * Admin team REST endpoint — T8 design-refresh-admin admin-teams.jsx 백엔드 페어.
 *
 * <p>{@code @PreAuthorize("hasRole('ADMIN')")}가 보안 진실의 출처 — UX guard(프론트
 * AdminGuard)는 보조. {@link AdminDepartmentController} 패턴 mirror.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/admin/teams} — list (active + archived)</li>
 *   <li>{@code GET /api/admin/teams/{id}} — detail</li>
 *   <li>{@code PATCH /api/admin/teams/{id}} — update (name/description/color/leadId)</li>
 *   <li>{@code DELETE /api/admin/teams/{id}} — archive (soft delete)</li>
 *   <li>{@code POST /api/admin/teams/{id}/restore} — restore (un-archive)</li>
 * </ul>
 *
 * <p>HTTP status 매트릭스:
 * <ul>
 *   <li>200 OK — list / detail / patch 성공</li>
 *   <li>204 No Content — archive / restore 성공</li>
 *   <li>400 VALIDATION_ERROR — Bean Validation 또는 빈 PATCH body 또는 leadId가 멤버 아님</li>
 *   <li>401 / 403 — Spring Security</li>
 *   <li>404 NOT_FOUND — team 미존재</li>
 *   <li>409 TEAM_CONFLICT — 같은 normalized name 활성 팀 존재</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/teams")
public class AdminTeamController {

    private final AdminTeamService svc;

    public AdminTeamController(AdminTeamService svc) {
        this.svc = svc;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<AdminTeamSummaryResponse> list() {
        return svc.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminTeamDetailResponse detail(@PathVariable UUID id) {
        return svc.detail(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminTeamDetailResponse patch(@PathVariable UUID id,
                                         @Valid @RequestBody AdminTeamPatchRequest req,
                                         @AuthenticationPrincipal IbizDriveUserDetails principal) {
        if (req == null || req.isEmpty()) {
            throw new AdminBadPatchException("at least one of name/description/color/leadId must be provided");
        }
        UUID actorId = principal.getUser().getId();
        return svc.update(id, req.name(), req.description(), req.color(), req.leadId(), actorId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> archive(@PathVariable UUID id,
                                        @AuthenticationPrincipal IbizDriveUserDetails principal) {
        svc.archive(id, principal.getUser().getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> restore(@PathVariable UUID id,
                                        @AuthenticationPrincipal IbizDriveUserDetails principal) {
        svc.restore(id, principal.getUser().getId());
        return ResponseEntity.noContent().build();
    }
}
