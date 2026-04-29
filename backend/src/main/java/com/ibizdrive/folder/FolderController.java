package com.ibizdrive.folder;

import com.ibizdrive.folder.dto.CreateFolderRequest;
import com.ibizdrive.folder.dto.FolderDto;
import com.ibizdrive.folder.dto.MoveFolderRequest;
import com.ibizdrive.folder.dto.RenameFolderRequest;
import com.ibizdrive.user.IbizDriveUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Folder mutation REST endpoint — A4.7 (docs/02 §7.5).
 *
 * <ul>
 *   <li><b>POST</b>  {@code /api/folders}             — create. 201 {@code { folder }}</li>
 *   <li><b>PATCH</b> {@code /api/folders/{id}}        — rename. 200 {@code { folder }}</li>
 *   <li><b>POST</b>  {@code /api/folders/{id}/move}   — move. 200 {@code { folder }}</li>
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

    public FolderController(FolderMutationService folderMutationService) {
        this.folderMutationService = folderMutationService;
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
     * <p>cycle/자기 자신/후손 검사는 service의 {@code IllegalArgumentException}으로 처리되어 400으로 매핑.
     * docs/02 §7.5의 별도 코드(MOVE_INTO_SELF/MOVE_INTO_DESCENDANT)로의 분리는 후속 세션 (frontend errors mirror 정련 시점).
     */
    @PostMapping("/{id}/move")
    @PreAuthorize("hasPermission(#id, 'folder', 'MOVE') and (#req.targetParentId == null ? hasRole('ADMIN') : hasPermission(#req.targetParentId, 'folder', 'EDIT'))")
    public ResponseEntity<Map<String, FolderDto>> move(
        @PathVariable("id") UUID id,
        @RequestBody @Valid MoveFolderRequest req,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        Folder moved = folderMutationService.move(id, req.targetParentId(), principal.getUser().getId());
        return ResponseEntity.ok(Map.of("folder", FolderDto.from(moved)));
    }
}
