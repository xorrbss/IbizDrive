package com.ibizdrive.trash;

import com.ibizdrive.folder.ScopeType;
import com.ibizdrive.user.IbizDriveUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 휴지통 REST endpoint — A8 (docs/02 §7.11, ADR #32) + Plan E T2 (workspace scope split).
 *
 * <ul>
 *   <li><b>GET</b>    {@code /api/trash?scopeType=&scopeId=}  — list (A8.1, scope-required). 200 {@link TrashPage}</li>
 *   <li><b>DELETE</b> {@code /api/trash/{type}/{id}}          — manual purge (A8.2, ADMIN). 204</li>
 * </ul>
 *
 * <p>Restore endpoint은 본 controller가 아니라 per-resource:
 * {@code POST /api/files/:id/restore} (FileController) + {@code POST /api/folders/:id/restore}
 * (FolderController) — A6에서 도입.
 *
 * <p>Bulk DELETE {@code /api/trash}는 미구현 (별도 트랙, ADR #32) — {@code purge.expired} daily
 * batch(A7) + manual single(A8) 조합으로 운영 충분.
 *
 * <p><b>Plan E T2 — workspace scope</b>: {@code GET /api/trash}는 {@code scopeType} +
 * {@code scopeId}를 필수 파라미터로 요구한다 (spec §3.1, §5.1). frontend는 항상 어떤 workspace의
 * 휴지통을 보고 있는지를 명시하며, server는 해당 workspace 멤버십을 검증한 뒤 scope 안에 있는
 * trashed row만 반환한다. admin은 별도 endpoint(`/api/admin/trash`)에서 cross-workspace 조회.
 */
@RestController
@RequestMapping("/api/trash")
public class TrashController {

    private final TrashQueryService trashQueryService;
    private final TrashPurgeService trashPurgeService;

    public TrashController(TrashQueryService trashQueryService,
                           TrashPurgeService trashPurgeService) {
        this.trashQueryService = trashQueryService;
        this.trashPurgeService = trashPurgeService;
    }

    // ──────────────────────────────────────────────────────────────────
    // GET /api/trash
    // ──────────────────────────────────────────────────────────────────

    /**
     * 휴지통 list — A8.1 + Plan E T2. {@code scopeType}/{@code scopeId}는 필수 — 해당 workspace의
     * 멤버이면서 row에 대해 {@code DELETE} 권한을 보유한 항목만 후처리 필터를 통과한다 (ADR #32).
     *
     * <p>{@code scopeType} 허용 값: {@code "department"} 또는 {@code "team"} (case-insensitive).
     * 누락/blank/invalid는 모두 {@link IllegalArgumentException} → GlobalExceptionHandler 400.
     *
     * <p>{@code type} 미지정 = file + folder 양쪽. {@code limit}은 default 50, hard cap 100.
     * {@code cursor}는 직전 응답의 {@code nextCursor}를 echo back — 첫 페이지에서는 미지정.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TrashPage> list(
        @RequestParam("scopeType") String scopeType,
        @RequestParam("scopeId") UUID scopeId,
        @RequestParam(value = "cursor", required = false) String cursor,
        @RequestParam(value = "type", required = false) String type,
        @RequestParam(value = "limit", required = false) Integer limit,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        ScopeType parsedScope = parseScopeType(scopeType);
        TrashItemType parsedType = parseType(type);
        TrashPage page = trashQueryService.list(
            principal.getUser().getId(),
            principal.getUser().getRole(),
            parsedScope,
            scopeId,
            cursor,
            parsedType,
            limit
        );
        return ResponseEntity.ok(page);
    }

    /**
     * Wire 표현({@code department}/{@code team})을 {@link ScopeType}으로 변환. case-insensitive.
     * 누락 / blank / unknown은 {@link IllegalArgumentException} → 400 BAD_REQUEST.
     *
     * <p>참고: {@link ScopeType#valueOf(String)}는 enum 상수 이름(대문자)에만 매치되므로
     * {@code toUpperCase()}로 정규화한 뒤 호출한다.
     */
    private static ScopeType parseScopeType(String wire) {
        if (wire == null || wire.isBlank()) {
            throw new IllegalArgumentException("scopeType must not be blank");
        }
        try {
            return ScopeType.valueOf(wire.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid scopeType: " + wire, ex);
        }
    }

    private static TrashItemType parseType(String wire) {
        if (wire == null || wire.isBlank()) {
            return null;
        }
        return TrashItemType.from(wire); // invalid → IllegalArgumentException → 400
    }

    // ──────────────────────────────────────────────────────────────────
    // DELETE /api/trash/{type}/{id}
    // ──────────────────────────────────────────────────────────────────

    /**
     * 휴지통 단건 manual purge — A8.2 (ADR #32). ADMIN 전용. {@code type}이 invalid이거나
     * 휴지통에 row가 부재하면 각각 400/404로 매핑(GlobalExceptionHandler).
     *
     * <p>folder cascade는 후손까지 hard delete되며 audit는 root 기준 1건만 발행된다 (A6 root-only
     * 패턴 일관). SSE FILE_PURGED/FOLDER_PURGED는 SSE 인프라 milestone 도입 시 1줄 추가로 활성화.
     */
    @DeleteMapping("/{type}/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> purge(
        @PathVariable("type") String type,
        @PathVariable("id") UUID id,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        TrashItemType parsed = TrashItemType.from(type); // invalid → 400
        UUID actorId = principal.getUser().getId();
        switch (parsed) {
            case FILE -> trashPurgeService.purgeFile(id, actorId);
            case FOLDER -> trashPurgeService.purgeFolder(id, actorId);
        }
        return ResponseEntity.noContent().build();
    }
}
