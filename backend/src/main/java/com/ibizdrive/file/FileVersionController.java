package com.ibizdrive.file;

import com.ibizdrive.file.dto.FileDto;
import com.ibizdrive.file.dto.FileVersionDto;
import com.ibizdrive.user.IbizDriveUserDetails;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 파일 버전 REST endpoint — A5.2 + M-RP.2 (docs/02 §7.6, ADR #29 / ADR #39).
 *
 * <ul>
 *   <li><b>GET</b> {@code /api/files/{fileId}/versions} — 200 {@code { versions: [FileVersionDto, ...] }}
 *       (A5.2)</li>
 *   <li><b>GET</b> {@code /api/files/{fileId}/versions/{versionId}/download} — 200 + 바이너리 stream
 *       (M-RP.2.1, {@code VERSION_DOWNLOADED} audit)</li>
 *   <li><b>POST</b> {@code /api/files/{fileId}/versions/{versionId}/restore} — 200 {@code { file: FileDto }}
 *       (M-RP.2.2, {@code VERSION_RESTORED} audit, 옵션 A: current_version_id 재지정 + 멱등)</li>
 * </ul>
 *
 * <p><b>인가</b>: list/download는 {@code READ}, restore는 {@code EDIT}.
 * {@link com.ibizdrive.permission.IbizDrivePermissionEvaluator}가 ADMIN/AUDITOR/grant를 통과시킨다.
 *
 * <p><b>흐름 (list)</b>:
 * <ol>
 *   <li>{@link FileRepository#findByIdAndDeletedAtIsNull(UUID)} — soft-deleted 파일은 404로 차단
 *       (docs/02 §7.6 — "휴지통에서 versions 노출 차단").</li>
 *   <li>{@link FileVersionRepository#findByFileIdOrderByVersionNumberDesc(UUID)} — 최신 버전이 첫 항목.</li>
 *   <li>{@link FileVersionDto#from(FileVersion, UUID)} — {@code isCurrent = (v.id == file.currentVersionId)}.</li>
 * </ol>
 *
 * <p><b>흐름 (download/restore)</b>: service 레이어로 위임 — controller는 SpEL 가드 + principal에서
 * actorId 추출 + 응답 envelope/header 빌드. cross-file 가드, audit emission, 트랜잭션 경계는 service가
 * 책임진다 ({@link FileDownloadService#downloadVersion}, {@link FileVersionMutationService#restoreVersion}).
 *
 * <p><b>응답 헤더 (download)</b>: {@link FileDownloadController}와 동일 — {@code Content-Type},
 * {@code Content-Length}, RFC 5987 {@code Content-Disposition}({@link ContentDispositionHeaders#build}),
 * {@code ETag}={@code "<versionId>"}.
 *
 * <p><b>예외 매핑</b>:
 * <ul>
 *   <li>{@link FileNotFoundException} → 404 NOT_FOUND ({@link com.ibizdrive.common.error.GlobalExceptionHandler})</li>
 *   <li>{@code AccessDeniedException} (SpEL deny) → 403 PERMISSION_DENIED</li>
 *   <li>{@link IllegalStateException} (storage I/O) → 500 INTERNAL_ERROR</li>
 * </ul>
 *
 * <p>read-only list endpoint는 audit emission 없음 ({@code FILE_VIEWED}는 audit_level=strict 폴더 도입과
 * 함께 후속 트랙).
 */
@RestController
@RequestMapping("/api/files/{fileId}/versions")
public class FileVersionController {

    private final FileRepository fileRepository;
    private final FileVersionRepository fileVersionRepository;
    private final FileDownloadService downloadService;
    private final FileVersionMutationService mutationService;

    public FileVersionController(FileRepository fileRepository,
                                 FileVersionRepository fileVersionRepository,
                                 FileDownloadService downloadService,
                                 FileVersionMutationService mutationService) {
        this.fileRepository = fileRepository;
        this.fileVersionRepository = fileVersionRepository;
        this.downloadService = downloadService;
        this.mutationService = mutationService;
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

    /**
     * M-RP.2.1 — 특정 version 핀 다운로드. cross-file 가드는
     * {@link FileDownloadService#downloadVersion}이 재검증한다 (SpEL은 path fileId 기준이므로 충분치 않다).
     */
    @GetMapping("/{versionId}/download")
    @PreAuthorize("hasPermission(#fileId, 'file', 'READ')")
    public ResponseEntity<InputStreamResource> downloadVersion(
        @PathVariable("fileId") UUID fileId,
        @PathVariable("versionId") UUID versionId,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        DownloadHandle handle = downloadService.downloadVersion(fileId, versionId, principal.getUser().getId());
        FileItem file = handle.file();
        FileVersion version = handle.version();

        MediaType contentType = parseContentType(version.getMimeType());
        String contentDisposition = ContentDispositionHeaders.build(file.getName());

        return ResponseEntity.ok()
            .contentType(contentType)
            .contentLength(version.getSizeBytes())
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
            .eTag("\"" + version.getId() + "\"")
            .body(new InputStreamResource(handle.stream()));
    }

    /**
     * M-RP.2.2 — 명시된 version을 file의 current로 재지정 (옵션 A, ADR #39).
     * 이미 current인 version을 재호출하면 멱등 no-op (audit emit X) — service에서 격리.
     *
     * <p>응답 200 {@code { file: FileDto }} — {@code currentVersionId}가 갱신된 신규 상태.
     */
    @PostMapping("/{versionId}/restore")
    @PreAuthorize("hasPermission(#fileId, 'file', 'EDIT')")
    public ResponseEntity<Map<String, FileDto>> restore(
        @PathVariable("fileId") UUID fileId,
        @PathVariable("versionId") UUID versionId,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        FileItem updated = mutationService.restoreVersion(fileId, versionId, principal.getUser().getId());
        return ResponseEntity.ok(Map.of("file", FileDto.from(updated)));
    }

    /**
     * mimeType이 null/blank 또는 파싱 실패 시 {@link MediaType#APPLICATION_OCTET_STREAM}로 안전 폴백.
     * V5 schema는 mime_type nullable이며 정상 업로드 흐름이 set하지만 legacy/보정 row 가능.
     */
    private static MediaType parseContentType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (org.springframework.http.InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
