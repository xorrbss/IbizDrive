package com.ibizdrive.favorite;

import com.ibizdrive.favorite.dto.FavoriteListResponse;
import com.ibizdrive.user.IbizDriveUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * P2a — file/folder 즐겨찾기 mutation endpoint.
 *
 * <p>4 endpoint:
 * <ul>
 *   <li>{@code POST   /api/files/{id}/star}</li>
 *   <li>{@code DELETE /api/files/{id}/star}</li>
 *   <li>{@code POST   /api/folders/{id}/star}</li>
 *   <li>{@code DELETE /api/folders/{id}/star}</li>
 * </ul>
 *
 * <p>가드 = READ 권한. 별 토글은 본인의 view 상태와 동등 — EDIT/SHARE는 요구하지 않는다
 * (즐겨찾기는 read-side state). controller에서 {@code hasPermission(#id, 'file'|'folder', 'READ')}로
 * 게이트 후 service는 추가 가드 없이 통과.
 *
 * <p>response = 204 No Content (멱등). 호출자는 GET 응답 {@code starred} 필드로 최신 상태 확인.
 * 별도 응답 본문이 없는 이유는 호출자가 이미 starred 상태를 알고 있는 client-driven UI 가정.
 *
 * <p>FE: optimistic update (`useToggleStar` hook은 v1.x 별도 PR에서 wiring).
 */
@RestController
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @PostMapping("/api/files/{id}/star")
    @PreAuthorize("hasPermission(#id, 'file', 'READ')")
    public ResponseEntity<Void> starFile(
        @PathVariable UUID id,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        favoriteService.star(principal.getUser().getId(), "file", id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/files/{id}/star")
    @PreAuthorize("hasPermission(#id, 'file', 'READ')")
    public ResponseEntity<Void> unstarFile(
        @PathVariable UUID id,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        favoriteService.unstar(principal.getUser().getId(), "file", id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/folders/{id}/star")
    @PreAuthorize("hasPermission(#id, 'folder', 'READ')")
    public ResponseEntity<Void> starFolder(
        @PathVariable UUID id,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        favoriteService.star(principal.getUser().getId(), "folder", id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/folders/{id}/star")
    @PreAuthorize("hasPermission(#id, 'folder', 'READ')")
    public ResponseEntity<Void> unstarFolder(
        @PathVariable UUID id,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        favoriteService.unstar(principal.getUser().getId(), "folder", id);
        return ResponseEntity.noContent().build();
    }

    /**
     * v1.x — 현재 사용자의 즐겨찾기 목록 (file/folder 통합, 최신순).
     *
     * <p>경로 {@code /api/me/favorites} — workspace-non-scoped per-user resource (auth만 요구).
     * 가드 = 인증된 사용자 자기 자신만 (path에 user id 미포함, principal 기반).
     *
     * <p>응답: {@link FavoriteListResponse#items} — 활성 resource만 포함 (soft-deleted 자연 제외).
     * 페이지네이션 없음 — v1 단순. 사이드바 count는 frontend가 {@code items.length}로 derive.
     */
    @GetMapping("/api/me/favorites")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FavoriteListResponse> listMyFavorites(
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        return ResponseEntity.ok(favoriteService.listMy(principal.getUser().getId()));
    }
}
