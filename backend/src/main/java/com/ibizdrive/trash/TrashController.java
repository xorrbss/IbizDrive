package com.ibizdrive.trash;

import com.ibizdrive.user.IbizDriveUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 휴지통 REST endpoint — A8 (docs/02 §7.11, ADR #32).
 *
 * <ul>
 *   <li><b>GET</b>    {@code /api/trash}                — list (A8.1). 200 {@link TrashPage}</li>
 *   <li><b>DELETE</b> {@code /api/trash/{type}/{id}}    — manual purge (A8.2, ADMIN). 204</li>
 * </ul>
 *
 * <p>Restore endpoint은 본 controller가 아니라 per-resource:
 * {@code POST /api/files/:id/restore} (FileController) + {@code POST /api/folders/:id/restore}
 * (FolderController) — A6에서 도입.
 *
 * <p>Bulk DELETE {@code /api/trash}는 미구현 (별도 트랙, ADR #32) — {@code purge.expired} daily
 * batch(A7) + manual single(A8) 조합으로 운영 충분.
 */
@RestController
@RequestMapping("/api/trash")
public class TrashController {

    private final TrashQueryService trashQueryService;

    public TrashController(TrashQueryService trashQueryService) {
        this.trashQueryService = trashQueryService;
    }

    // ──────────────────────────────────────────────────────────────────
    // GET /api/trash
    // ──────────────────────────────────────────────────────────────────

    /**
     * 휴지통 list — A8.1. 응답은 사용자가 {@code DELETE} 권한을 보유한 항목으로 후처리 필터됨
     * (ADR #32). 인증된 모든 사용자에 대해 열린 endpoint.
     *
     * <p>{@code type} 미지정 = file + folder 양쪽. {@code limit}은 default 50, hard cap 100.
     * {@code cursor}는 직전 응답의 {@code nextCursor}를 echo back — 첫 페이지에서는 미지정.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TrashPage> list(
        @RequestParam(value = "cursor", required = false) String cursor,
        @RequestParam(value = "type", required = false) String type,
        @RequestParam(value = "limit", required = false) Integer limit,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        TrashItemType parsed = parseType(type);
        TrashPage page = trashQueryService.list(
            principal.getUser().getId(),
            principal.getUser().getRole(),
            cursor,
            parsed,
            limit
        );
        return ResponseEntity.ok(page);
    }

    private static TrashItemType parseType(String wire) {
        if (wire == null || wire.isBlank()) {
            return null;
        }
        return TrashItemType.from(wire); // invalid → IllegalArgumentException → 400
    }
}
