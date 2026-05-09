package com.ibizdrive.file;

import com.ibizdrive.common.dto.RestoreRequest;
import com.ibizdrive.file.dto.FileDto;
import com.ibizdrive.file.dto.MoveFileRequest;
import com.ibizdrive.file.dto.RenameFileRequest;
import com.ibizdrive.folder.MovePreviewService;
import com.ibizdrive.folder.dto.MovePreviewRequest;
import com.ibizdrive.folder.dto.MovePreviewResponse;
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

import java.util.Map;
import java.util.UUID;

/**
 * File mutation REST endpoint — A4.8 (docs/02 §7.5 — files mirror of A4.7 folder).
 *
 * <ul>
 *   <li><b>PATCH</b>  {@code /api/files/{id}}           — rename. 200 {@code { file }}</li>
 *   <li><b>POST</b>   {@code /api/files/{id}/move}      — move. 200 {@code { file }}</li>
 *   <li><b>DELETE</b> {@code /api/files/{id}}           — soft-delete. 204</li>
 *   <li><b>POST</b>   {@code /api/files/{id}/restore}   — restore from trash. 200 {@code { file }}</li>
 * </ul>
 *
 * <p><b>인가 (ADR #30, docs/02 §7.5)</b>:
 * <ul>
 *   <li>rename:  대상 EDIT</li>
 *   <li>move:    대상 MOVE AND 새 폴더 EDIT</li>
 *   <li>delete:  대상 DELETE</li>
 *   <li>restore: 대상 DELETE (별도 RESTORE 권한 enum 부재 — Permission.java의 9개 값에 RESTORE 미정의)</li>
 * </ul>
 * SpEL 평가는 {@link com.ibizdrive.permission.IbizDrivePermissionEvaluator}가 수행 — {@code targetType='file'}을
 * 이미 인식 (PermissionResolver의 폴더 상속 CTE 통해 grant).
 *
 * <p><b>책임 분리 (KISS)</b>: controller는 SpEL 가드 + {@code @Valid} body 검증 + principal에서 actorId
 * 추출 + DTO 매핑/HTTP status. 정규화/충돌 검사/audit emission은 {@link FileMutationService}.
 *
 * <p><b>예외 매핑</b>:
 * <ul>
 *   <li>{@link FileNotFoundException}            → 404 (GlobalExceptionHandler ResourceNotFound)</li>
 *   <li>{@link FileNameConflictException}        → 409 RENAME_CONFLICT</li>
 *   <li>{@code IllegalArgumentException}         → 400 BAD_REQUEST</li>
 *   <li>{@code AccessDeniedException} (SpEL deny)→ 403 PERMISSION_DENIED</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileMutationService fileMutationService;
    private final FileQueryService fileQueryService;
    private final MovePreviewService movePreviewService;

    public FileController(FileMutationService fileMutationService,
                          FileQueryService fileQueryService,
                          MovePreviewService movePreviewService) {
        this.fileMutationService = fileMutationService;
        this.fileQueryService = fileQueryService;
        this.movePreviewService = movePreviewService;
    }

    // ──────────────────────────────────────────────────────────────────
    // GET /api/files/{id}
    // ──────────────────────────────────────────────────────────────────

    /**
     * 파일 상세 조회 — frontend RightPanel wiring (Phase B P2). 활성 파일만 반환.
     * 부재/soft-deleted 시 {@link FileNotFoundException} → 404.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(#id, 'file', 'READ')")
    public ResponseEntity<Map<String, FileDto>> get(
        @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(Map.of("file", fileQueryService.loadDetail(id)));
    }

    // ──────────────────────────────────────────────────────────────────
    // PATCH /api/files/{id}
    // ──────────────────────────────────────────────────────────────────

    /**
     * 파일 이름 변경. 정규화 후 기존 값과 동일하면 service가 no-op (audit 미발행) — 응답은 동일하게 200.
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission(#id, 'file', 'EDIT')")
    public ResponseEntity<Map<String, FileDto>> rename(
        @PathVariable("id") UUID id,
        @RequestBody @Valid RenameFileRequest req,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        FileItem renamed = fileMutationService.rename(id, req.name(), principal.getUser().getId());
        return ResponseEntity.ok(Map.of("file", FileDto.from(renamed)));
    }

    // ──────────────────────────────────────────────────────────────────
    // POST /api/files/{id}/move
    // ──────────────────────────────────────────────────────────────────

    /**
     * 파일 이동 — 새 폴더로 옮긴다. {@code targetFolderId}는 NOT NULL ({@code MoveFileRequest @NotNull}).
     *
     * <p>{@code allowCrossScope: true} 시 cross-workspace move 경로로 분기 (Plan D §5.6).
     * 이 경우 source 파일의 EDIT+SHARE와 destination 폴더의 UPLOAD가 추가로 요구된다.
     * 파일은 root 이동 개념 없으므로 folder의 {@code targetParentId == null} 분기가 없다.
     */
    @PostMapping("/{id}/move")
    @PreAuthorize("hasPermission(#id, 'file', 'MOVE') and ("
        + "#req.allowCrossScopeOrFalse() ? "
            + "(hasPermission(#id, 'file', 'EDIT') and hasPermission(#id, 'file', 'SHARE') and hasPermission(#req.targetFolderId, 'folder', 'UPLOAD')) : "
            + "hasPermission(#req.targetFolderId, 'folder', 'EDIT')"
        + ")")
    public ResponseEntity<Map<String, FileDto>> move(
        @PathVariable("id") UUID id,
        @RequestBody @Valid MoveFileRequest req,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        FileItem moved = fileMutationService.move(
            id, req.targetFolderId(), principal.getUser().getId(), req.allowCrossScopeOrFalse());
        return ResponseEntity.ok(Map.of("file", FileDto.from(moved)));
    }

    // ──────────────────────────────────────────────────────────────────
    // DELETE /api/files/{id}
    // ──────────────────────────────────────────────────────────────────

    /**
     * 파일 soft-delete (휴지통 이동). 204 No Content — body 없음. 영구 purge는 별도 ADMIN endpoint(A4 외).
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(#id, 'file', 'DELETE')")
    public ResponseEntity<Void> delete(
        @PathVariable("id") UUID id,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        fileMutationService.delete(id, principal.getUser().getId());
        return ResponseEntity.noContent().build();
    }

    // ──────────────────────────────────────────────────────────────────
    // POST /api/files/{id}/restore
    // ──────────────────────────────────────────────────────────────────

    /**
     * 휴지통 파일을 {@code original_folder_id}로 복원. 200 {@code { file }} — restored entity의 활성 상태
     * (deletedAt=null) 표현.
     *
     * <p>SpEL guard는 DELETE 권한 — RESTORE 권한 enum이 별도 존재하지 않으므로 동일한 양수 권한
     * (휴지통 이동을 허용한 권한이 복원도 허용)을 사용한다.
     */
    @PostMapping("/{id}/restore")
    @PreAuthorize("hasPermission(#id, 'file', 'DELETE')")
    public ResponseEntity<Map<String, FileDto>> restore(
        @PathVariable("id") UUID id,
        @RequestBody(required = false) RestoreRequest body,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        String newName = body != null ? body.name() : null;
        FileItem restored = fileMutationService.restore(id, principal.getUser().getId(), newName);
        return ResponseEntity.ok(Map.of("file", FileDto.from(restored)));
    }

    // ──────────────────────────────────────────────────────────────────
    // POST /api/files/{id}/move/preview
    // ──────────────────────────────────────────────────────────────────

    /**
     * spec §5.6 — file 단건 cross-workspace move 미리보기.
     *
     * <p>SpEL 가드: source 파일 {@code EDIT+SHARE}.
     */
    @PostMapping("/{id}/move/preview")
    @PreAuthorize("hasPermission(#id, 'file', 'EDIT') and hasPermission(#id, 'file', 'SHARE')")
    public ResponseEntity<MovePreviewResponse> movePreview(
        @PathVariable("id") UUID id,
        @RequestBody @Valid MovePreviewRequest req,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        return ResponseEntity.ok(
            movePreviewService.previewFile(id, req.destinationFolderId(), principal.getUser().getId())
        );
    }
}
