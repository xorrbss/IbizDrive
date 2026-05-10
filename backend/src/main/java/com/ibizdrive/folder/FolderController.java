package com.ibizdrive.folder;

import com.ibizdrive.common.dto.RestoreRequest;
import com.ibizdrive.folder.dto.CreateFolderRequest;
import com.ibizdrive.folder.dto.FolderDetailResponse;
import com.ibizdrive.folder.dto.FolderDto;
import com.ibizdrive.folder.dto.FolderItemsResponse;
import com.ibizdrive.folder.dto.FolderNodeDto;
import com.ibizdrive.folder.dto.MoveFolderRequest;
import com.ibizdrive.folder.dto.MovePreviewRequest;
import com.ibizdrive.folder.dto.MovePreviewResponse;
import com.ibizdrive.folder.dto.RenameFolderRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Folder mutation REST endpoint — A4.7 (docs/02 §7.5).
 *
 * <ul>
 *   <li><b>POST</b>  {@code /api/folders}                — create. 201 {@code { folder }}</li>
 *   <li><b>PATCH</b> {@code /api/folders/{id}}           — rename. 200 {@code { folder }}</li>
 *   <li><b>POST</b>  {@code /api/folders/{id}/move}      — move. 200 {@code { folder }}</li>
 *   <li><b>DELETE</b> {@code /api/folders/{id}}          — soft-delete + cascade. 204 (A6.3)</li>
 *   <li><b>POST</b>  {@code /api/folders/{id}/restore}   — restore from trash. 200 {@code { folder }} (A6.3)</li>
 * </ul>
 *
 * <p><b>인가 (ADR #30, docs/02 §7.5)</b>:
 * <ul>
 *   <li>create: {@code parentId == null} → ROLE {@code ADMIN}, 그 외 → 부모 EDIT</li>
 *   <li>rename: 대상 EDIT</li>
 *   <li>move:   대상 MOVE AND ({@code targetParentId == null} → ROLE {@code ADMIN}, 그 외 → 새 부모 EDIT)</li>
 * </ul>
 * SpEL 평가는 {@link com.ibizdrive.permission.IbizDrivePermissionEvaluator}가 수행.
 *
 * <p><b>책임 분리 (KISS)</b>: controller는 (a) SpEL 가드 선언, (b) {@code @Valid} body 1차 검증,
 * (c) principal에서 {@code ownerId}/{@code actorId} 추출, (d) DTO 매핑·HTTP status 표현. 정규화/충돌
 * 검사/audit emission은 모두 {@link FolderMutationService} 책임.
 *
 * <p><b>response envelope</b>: PermissionController와 동일하게 {@code Map.of("folder", FolderDto)}.
 * breadcrumb은 본 세션 범위 외(FolderQueryService 부재) — docs/02 §7.5의 응답 본문에서 누락된 부분은
 * 후속 세션에서 추가될 예정.
 *
 * <p><b>예외 매핑</b>:
 * <ul>
 *   <li>{@link FolderNotFoundException}             → 404 (GlobalExceptionHandler ResourceNotFound)</li>
 *   <li>{@link FolderNameConflictException}         → 409 RENAME_CONFLICT (A4.7 신규 매핑)</li>
 *   <li>{@code IllegalArgumentException} (cycle 등) → 400 BAD_REQUEST</li>
 *   <li>{@code AccessDeniedException} (SpEL deny)   → 403 PERMISSION_DENIED</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/folders")
public class FolderController {

    /** service의 ALLOWED_AUDIT_LEVELS와 동일 default. NULL 입력을 controller에서 보강. */
    private static final String DEFAULT_AUDIT_LEVEL = "standard";

    private final FolderMutationService folderMutationService;
    private final FolderQueryService folderQueryService;
    private final MovePreviewService movePreviewService;

    public FolderController(FolderMutationService folderMutationService,
                            FolderQueryService folderQueryService,
                            MovePreviewService movePreviewService) {
        this.folderMutationService = folderMutationService;
        this.folderQueryService = folderQueryService;
        this.movePreviewService = movePreviewService;
    }

    // ──────────────────────────────────────────────────────────────────
    // GET /api/folders/tree
    // ──────────────────────────────────────────────────────────────────

    /**
     * 활성 폴더 전체 트리 — frontend FolderTree wiring (Phase A).
     *
     * <p>인가: 현재 모든 인증 사용자가 트리 구조를 볼 수 있음 — 노출 정책은 후속 Phase에서
     * grant 기반 필터로 좁힐 예정 (CLAUDE.md §3 원칙 14의 read scope). 각 노드의 실제 접근은
     * detail/items endpoint에서 별도 SpEL로 게이트되므로 트리 노출 자체는 메타데이터 레벨.
     */
    @GetMapping("/tree")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, List<FolderNodeDto>>> tree() {
        return ResponseEntity.ok(Map.of("tree", folderQueryService.loadTree()));
    }

    // ──────────────────────────────────────────────────────────────────
    // GET /api/folders/{id}
    // ──────────────────────────────────────────────────────────────────

    /**
     * 폴더 상세 + breadcrumb (root → self) — frontend Breadcrumb / 헤더 wiring (Phase A).
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(#id, 'folder', 'READ')")
    public ResponseEntity<FolderDetailResponse> detail(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(folderQueryService.loadDetail(id));
    }

    // ──────────────────────────────────────────────────────────────────
    // GET /api/folders/{id}/items
    // ──────────────────────────────────────────────────────────────────

    /**
     * 폴더에 들어있는 자식 폴더 + 파일 합본 listing — frontend FileTable wiring (Phase B).
     *
     * <p>{@code sort}/{@code dir} 미지정 시 default {@code NAME asc}. 잘못된 enum 값은
     * Spring이 {@code MethodArgumentTypeMismatchException} → 400 으로 매핑.
     *
     * <p>frontend는 'root' 가상 폴더에 대해 본 endpoint를 호출하지 않음 — Phase A 정책 답습
     * (root는 frontend에서 tree로부터 합성). backend는 항상 실제 UUID만 받는다.
     */
    @GetMapping("/{id}/items")
    @PreAuthorize("hasPermission(#id, 'folder', 'READ')")
    public ResponseEntity<FolderItemsResponse> items(
        @PathVariable("id") UUID id,
        @RequestParam(name = "sort", required = false, defaultValue = "NAME") SortKey sort,
        @RequestParam(name = "dir", required = false, defaultValue = "ASC") SortDir dir
    ) {
        return ResponseEntity.ok(folderQueryService.loadItems(id, sort, dir));
    }

    // ──────────────────────────────────────────────────────────────────
    // POST /api/folders
    // ──────────────────────────────────────────────────────────────────

    /**
     * 새 폴더 생성. {@code parentId == null}이면 root 폴더 — SpEL이 ROLE ADMIN으로 분기 (ADR #30).
     *
     * <p>{@code ownerId}는 본문에서 받지 않고 인증된 principal로 통일 — 사용자가 임의 ownerId를 지정하지
     * 못하도록(보안). admin 위임 케이스(다른 사용자 소유 root 생성)는 후속 세션에서 별도 endpoint로 분리.
     */
    @PostMapping
    @PreAuthorize("#req.parentId == null ? hasRole('ADMIN') : hasPermission(#req.parentId, 'folder', 'EDIT')")
    public ResponseEntity<Map<String, FolderDto>> create(
        @RequestBody @Valid CreateFolderRequest req,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        UUID actorId = principal.getUser().getId();
        String auditLevel = req.auditLevel() != null ? req.auditLevel() : DEFAULT_AUDIT_LEVEL;

        Folder created = folderMutationService.create(
            req.parentId(),
            req.name(),
            actorId,                  // ownerId = principal (본문 미수신)
            auditLevel,
            actorId
        );

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(Map.of("folder", FolderDto.from(created)));
    }

    // ──────────────────────────────────────────────────────────────────
    // PATCH /api/folders/{id}
    // ──────────────────────────────────────────────────────────────────

    /**
     * 폴더 이름 변경. 정규화 후 기존 값과 동일하면 service가 no-op (audit 미발행) — 응답은 동일하게 200.
     *
     * <p>SpEL {@code #id}는 path-bound — {@code @PathVariable("id")}와 일치해야 평가 시점에 resolve.
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission(#id, 'folder', 'EDIT')")
    public ResponseEntity<Map<String, FolderDto>> rename(
        @PathVariable("id") UUID id,
        @RequestBody @Valid RenameFolderRequest req,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        Folder renamed = folderMutationService.rename(id, req.name(), principal.getUser().getId());
        return ResponseEntity.ok(Map.of("folder", FolderDto.from(renamed)));
    }

    // ──────────────────────────────────────────────────────────────────
    // POST /api/folders/{id}/move
    // ──────────────────────────────────────────────────────────────────

    /**
     * 폴더 이동. {@code targetParentId == null}이면 root로 이동 — SpEL 삼항이 ROLE ADMIN으로 분기 (ADR #30).
     *
     * <p>{@code allowCrossScope: true} 시 cross-workspace move 경로로 분기 (Plan D §5.6).
     * 이 경우 source 폴더의 EDIT+SHARE와 destination 폴더의 UPLOAD가 추가로 요구된다.
     *
     * <p>cycle/자기 자신/후손 검사는 service의 {@code IllegalArgumentException}으로 처리되어 400으로 매핑.
     */
    @PostMapping("/{id}/move")
    @PreAuthorize("hasPermission(#id, 'folder', 'MOVE') and ("
        + "#req.targetParentId == null ? hasRole('ADMIN') : "
        + "(#req.allowCrossScopeOrFalse() ? "
            + "(hasPermission(#id, 'folder', 'EDIT') and hasPermission(#id, 'folder', 'SHARE') and hasPermission(#req.targetParentId, 'folder', 'UPLOAD')) : "
            + "hasPermission(#req.targetParentId, 'folder', 'EDIT')"
        + ")"
        + ")")
    public ResponseEntity<Map<String, FolderDto>> move(
        @PathVariable("id") UUID id,
        @RequestBody @Valid MoveFolderRequest req,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        Folder moved = folderMutationService.move(
            id, req.targetParentId(), principal.getUser().getId(), req.allowCrossScopeOrFalse());
        return ResponseEntity.ok(Map.of("folder", FolderDto.from(moved)));
    }

    // ──────────────────────────────────────────────────────────────────
    // DELETE /api/folders/{id}
    // ──────────────────────────────────────────────────────────────────

    /**
     * 폴더와 후손(폴더+파일)을 휴지통으로 이동(soft-delete) — A6.3 (docs/02 §7.5).
     *
     * <p>cascade 정책은 service 책임 — 후손 폴더/파일은 동일 트랜잭션에서 batch UPDATE되며
     * audit는 root에 대해 1건만 발행 (FOLDER_DELETED). 응답은 204 NO_CONTENT (본문 없음).
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(#id, 'folder', 'DELETE')")
    public ResponseEntity<Void> delete(
        @PathVariable("id") UUID id,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        folderMutationService.delete(id, principal.getUser().getId());
        return ResponseEntity.noContent().build();
    }

    // ──────────────────────────────────────────────────────────────────
    // POST /api/folders/{id}/restore
    // ──────────────────────────────────────────────────────────────────

    /**
     * 휴지통 폴더를 원위치(original_parent_id)로 복원 — A6.3 (docs/02 §7.5).
     *
     * <p>복원 범위: 자기 자신만 (후손 잔존). original_parent_id가 soft-deleted면 404,
     * 원위치에 동일 normalized_name 활성 폴더 존재 시 409 RESTORE_CONFLICT envelope.
     */
    @PostMapping("/{id}/restore")
    @PreAuthorize("hasPermission(#id, 'folder', 'DELETE')")
    public ResponseEntity<Map<String, FolderDto>> restore(
        @PathVariable("id") UUID id,
        @RequestBody(required = false) RestoreRequest body,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        String newName = body != null ? body.name() : null;
        Folder restored = folderMutationService.restore(id, principal.getUser().getId(), newName);
        return ResponseEntity.ok(Map.of("folder", FolderDto.from(restored)));
    }

    // ──────────────────────────────────────────────────────────────────
    // POST /api/folders/{id}/move/preview
    // ──────────────────────────────────────────────────────────────────

    /**
     * spec §5.6 — cross-workspace move 다이얼로그 진입 시 영향 미리보기 (idempotent).
     *
     * <p>SpEL 가드: source 폴더 {@code EDIT+SHARE} (share 정리 동반).
     */
    @PostMapping("/{id}/move/preview")
    @PreAuthorize("hasPermission(#id, 'folder', 'EDIT') and hasPermission(#id, 'folder', 'SHARE')")
    public ResponseEntity<MovePreviewResponse> movePreview(
        @PathVariable("id") UUID id,
        @RequestBody @Valid MovePreviewRequest req,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        return ResponseEntity.ok(
            movePreviewService.previewFolder(id, req.destinationFolderId(), principal.getUser().getId())
        );
    }
}
