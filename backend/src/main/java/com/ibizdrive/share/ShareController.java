package com.ibizdrive.share;

import com.ibizdrive.user.IbizDriveUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shares REST endpoint — A10.5 (docs/02 §7.9, ADR #34).
 *
 * <ul>
 *   <li><b>POST</b>   {@code /api/files/{fileId}/share}     — N subjects → N shares (A10.2). 201 + {@code { shares: ShareDto[] }}</li>
 *   <li><b>DELETE</b> {@code /api/shares/{shareId}}         — owner/ADMIN revoke (A10.3). 204</li>
 *   <li><b>GET</b>    {@code /api/shares/by-me}             — actor가 만든 active share (A10.4). 200 {@link SharePage}</li>
 *   <li><b>GET</b>    {@code /api/shares/with-me}           — actor가 받은 active share (A10.4). 200 {@link SharePage}</li>
 * </ul>
 *
 * <p><b>인가</b>:
 * <ul>
 *   <li>POST: {@code @PreAuthorize("hasPermission(#fileId, 'file', 'SHARE')")} — 대상 파일에 SHARE preset 보유.</li>
 *   <li>DELETE: {@code @PreAuthorize("@shareCommandService.canRevoke(#shareId, principal)")} — 본인 share 또는 ADMIN.</li>
 *   <li>GET by-me / with-me: {@code @PreAuthorize("isAuthenticated()")} — 인증된 모든 사용자.</li>
 * </ul>
 *
 * <p><b>에러 매핑</b> (GlobalExceptionHandler):
 * <ul>
 *   <li>입력 검증 실패 (preset/subject wire, expiresAt 과거, message > 1000자, invalid cursor) → 400</li>
 *   <li>file/share 미존재 → 404 (ResourceNotFoundException)</li>
 *   <li>중복 grant → 409 (PermissionConflictException, V5 unique 인덱스 위반)</li>
 *   <li>인가 거부 → 403 (PermissionDenyContext envelope)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class ShareController {

    private final ShareCommandService shareCommandService;
    private final ShareQueryService shareQueryService;

    public ShareController(ShareCommandService shareCommandService,
                           ShareQueryService shareQueryService) {
        this.shareCommandService = shareCommandService;
        this.shareQueryService = shareQueryService;
    }

    // ──────────────────────────────────────────────────────────────────
    // POST /api/files/{fileId}/share
    // ──────────────────────────────────────────────────────────────────

    /**
     * file 공유 생성 — A10.2. service가 N subjects → N shares를 단일 트랜잭션에서 처리.
     *
     * <p>응답 envelope은 {@code { shares: ShareDto[] }} — 단일/다중 일관 형태 (PermissionController.grant의
     * {@code { permission: ... }} 동형 패턴, 그러나 여기는 N건이므로 list).
     */
    @PostMapping("/files/{fileId}/share")
    @PreAuthorize("hasPermission(#fileId, 'file', 'SHARE')")
    public ResponseEntity<Map<String, List<ShareDto>>> create(
        @PathVariable("fileId") UUID fileId,
        @RequestBody ShareCreateRequest body,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        List<Share> created = shareCommandService.createShares(
            fileId, body, principal.getUser().getId()
        );
        List<ShareDto> dtos = new ArrayList<>(created.size());
        for (Share s : created) dtos.add(ShareDto.from(s));
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(Map.of("shares", List.copyOf(dtos)));
    }

    // ──────────────────────────────────────────────────────────────────
    // DELETE /api/shares/{shareId}
    // ──────────────────────────────────────────────────────────────────

    /**
     * share revoke — A10.3. SpEL이 owner/ADMIN 평가 후 service는 권한 재검사 없이 lock + snapshot + delete.
     *
     * <p>존재하지 않거나 이미 revoke된 share는 service에서 {@link com.ibizdrive.common.error.ResourceNotFoundException}
     * → 404. 인가 통과 후 row가 사이에 사라진 race도 동일하게 404 — 멱등성 자연.
     */
    @DeleteMapping("/shares/{shareId}")
    @PreAuthorize("@shareCommandService.canRevoke(#shareId, principal)")
    public ResponseEntity<Void> revoke(
        @PathVariable("shareId") UUID shareId,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        shareCommandService.revokeShare(shareId, principal.getUser().getId());
        return ResponseEntity.noContent().build();
    }

    // ──────────────────────────────────────────────────────────────────
    // GET /api/shares/by-me
    // ──────────────────────────────────────────────────────────────────

    /**
     * actor가 만든 active share — A10.4. cursor + limit echo. invalid cursor → 400.
     */
    @GetMapping("/shares/by-me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SharePage> listByMe(
        @RequestParam(value = "cursor", required = false) String cursor,
        @RequestParam(value = "limit", required = false) Integer limit,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        SharePage page = shareQueryService.listByMe(principal.getUser().getId(), cursor, limit);
        return ResponseEntity.ok(page);
    }

    // ──────────────────────────────────────────────────────────────────
    // GET /api/shares/with-me
    // ──────────────────────────────────────────────────────────────────

    /**
     * actor가 받은 active share — A10.4. MVP는 {@code subject_type='user'}만. department/role/everyone은
     * backlog ADR #34.
     */
    @GetMapping("/shares/with-me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SharePage> listWithMe(
        @RequestParam(value = "cursor", required = false) String cursor,
        @RequestParam(value = "limit", required = false) Integer limit,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        SharePage page = shareQueryService.listWithMe(principal.getUser().getId(), cursor, limit);
        return ResponseEntity.ok(page);
    }
}
