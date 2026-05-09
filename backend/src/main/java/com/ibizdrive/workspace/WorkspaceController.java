package com.ibizdrive.workspace;

import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.workspace.dto.WorkspaceMeResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 사용자의 workspace 목록 read API — Plan A Task 15.
 *
 * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §5.
 * 사이드바 트리 첫 fetch에서 호출. 인증 필수 — {@link IbizDriveUserDetails}로 current user 추출.
 */
@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService svc;

    public WorkspaceController(WorkspaceService svc) {
        this.svc = svc;
    }

    /**
     * {@code GET /api/workspaces/me} — current user의 workspace 목록.
     *
     * @param principal 인증된 사용자 (Spring Security가 주입)
     * @return department(nullable) + teams(possibly empty)
     */
    @GetMapping("/me")
    public WorkspaceMeResponse me(@AuthenticationPrincipal IbizDriveUserDetails principal) {
        UUID userId = principal.getUser().getId();
        WorkspaceListing listing = svc.findForUser(userId);

        WorkspaceMeResponse.WorkspaceRef dept = listing.department()
            .map(d -> new WorkspaceMeResponse.WorkspaceRef(
                d.kind(), d.id(), d.name(), d.rootFolderId()))
            .orElse(null);

        List<WorkspaceMeResponse.WorkspaceRef> teams = listing.teams().stream()
            .map(t -> new WorkspaceMeResponse.WorkspaceRef(
                t.kind(), t.id(), t.name(), t.rootFolderId()))
            .toList();

        return new WorkspaceMeResponse(dept, teams);
    }
}
