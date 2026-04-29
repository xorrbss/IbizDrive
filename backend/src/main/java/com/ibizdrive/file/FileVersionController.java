package com.ibizdrive.file;

import com.ibizdrive.file.dto.FileVersionDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 파일 버전 조회 REST endpoint — A5.2 (docs/02 §7.6, ADR #29).
 *
 * <ul>
 *   <li><b>GET</b> {@code /api/files/{fileId}/versions} — 200 {@code { versions: [FileVersionDto, ...] }}</li>
 * </ul>
 *
 * <p><b>인가</b>: {@code @PreAuthorize("hasPermission(#fileId, 'file', 'READ')")} —
 * {@link com.ibizdrive.permission.IbizDrivePermissionEvaluator}가 ADMIN/AUDITOR/READ-grant를 통과시킨다.
 *
 * <p><b>흐름</b>:
 * <ol>
 *   <li>{@link FileRepository#findByIdAndDeletedAtIsNull(UUID)} — soft-deleted 파일은 404로 차단
 *       (docs/02 §7.6 — "휴지통에서 versions 노출 차단").</li>
 *   <li>{@link FileVersionRepository#findByFileIdOrderByVersionNumberDesc(UUID)} — 최신 버전이 첫 항목.</li>
 *   <li>{@link FileVersionDto#from(FileVersion, UUID)} — {@code isCurrent = (v.id == file.currentVersionId)}.</li>
 * </ol>
 *
 * <p><b>예외 매핑</b>:
 * <ul>
 *   <li>{@link FileNotFoundException} → 404 NOT_FOUND ({@link com.ibizdrive.common.error.GlobalExceptionHandler})</li>
 *   <li>{@code AccessDeniedException} (SpEL deny) → 403 PERMISSION_DENIED</li>
 * </ul>
 *
 * <p>FolderController와 동일한 envelope 정책 — read-only endpoint이므로 audit emission 없음
 * ({@code FILE_VIEWED}는 audit_level=strict 폴더 도입과 함께 후속 트랙).
 */
@RestController
@RequestMapping("/api/files/{fileId}/versions")
public class FileVersionController {

    private final FileRepository fileRepository;
    private final FileVersionRepository fileVersionRepository;

    public FileVersionController(FileRepository fileRepository,
                                 FileVersionRepository fileVersionRepository) {
        this.fileRepository = fileRepository;
        this.fileVersionRepository = fileVersionRepository;
    }

    @GetMapping
    @PreAuthorize("hasPermission(#fileId, 'file', 'READ')")
    public ResponseEntity<Map<String, List<FileVersionDto>>> list(@PathVariable("fileId") UUID fileId) {
        FileItem file = fileRepository.findByIdAndDeletedAtIsNull(fileId)
            .orElseThrow(() -> new FileNotFoundException("file not found or deleted: " + fileId));

        UUID currentVersionId = file.getCurrentVersionId();
        List<FileVersionDto> versions = fileVersionRepository.findByFileIdOrderByVersionNumberDesc(fileId)
            .stream()
            .map(v -> FileVersionDto.from(v, currentVersionId))
            .toList();

        return ResponseEntity.ok(Map.of("versions", versions));
    }
}
