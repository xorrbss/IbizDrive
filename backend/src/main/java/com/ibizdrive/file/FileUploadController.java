package com.ibizdrive.file;

import com.ibizdrive.file.dto.UploadResponse;
import com.ibizdrive.user.IbizDriveUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * 파일 업로드 endpoint — A15 (docs/02 §6.1, §7.6).
 *
 * <ul>
 *   <li><b>POST</b> {@code /api/files} (multipart/form-data) — 새 파일 INSERT 또는 충돌 시 분기.</li>
 * </ul>
 *
 * <p><b>요청 (multipart parts)</b>:
 * <ul>
 *   <li>{@code folderId} (form field, UUID, required) — 업로드 대상 폴더</li>
 *   <li>{@code resolution} (form field, optional) — 충돌 시 정책: {@code "new_version"} | {@code "rename"}.
 *       부재 시 충돌 발생하면 409 RENAME_CONFLICT.</li>
 *   <li>{@code file} (file part, required) — 바이너리</li>
 * </ul>
 *
 * <p><b>응답</b>:
 * <ul>
 *   <li>201 Created + {@link UploadResponse} ({@code newFile=true}) — 신규 파일 INSERT</li>
 *   <li>200 OK + {@link UploadResponse} ({@code newFile=false}) — NEW_VERSION 분기로 기존 파일에 새 version 추가</li>
 * </ul>
 *
 * <p><b>인가 (docs/03 §3, ADR #30)</b>: 폴더 {@code UPLOAD} 권한. SpEL은 {@code IbizDrivePermissionEvaluator}가
 * resolve.
 *
 * <p><b>예외 매핑</b> (GlobalExceptionHandler 위임):
 * <ul>
 *   <li>{@link com.ibizdrive.folder.FolderNotFoundException} → 404 NOT_FOUND</li>
 *   <li>{@link FileNameConflictException} → 409 RENAME_CONFLICT</li>
 *   <li>{@code IllegalArgumentException} → 400 BAD_REQUEST</li>
 *   <li>{@code AccessDeniedException} → 403 PERMISSION_DENIED</li>
 *   <li>{@code MaxUploadSizeExceededException} (Spring multipart) → 413 (default Spring) — 별도 매핑은
 *       추가 closure ADR #36에서 결정</li>
 * </ul>
 *
 * <p><b>책임 분리 (KISS, FileController 패턴)</b>: controller는 SpEL 가드 + multipart 파싱 + principal에서
 * actorId 추출 + DTO/HTTP status. 정규화/충돌/storage write/audit emission은 {@link FileUploadService}.
 */
@RestController
@RequestMapping("/api/files")
public class FileUploadController {

    private final FileUploadService service;

    public FileUploadController(FileUploadService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasPermission(#folderId, 'folder', 'UPLOAD')")
    public ResponseEntity<UploadResponse> upload(
        @RequestParam("folderId") UUID folderId,
        @RequestParam(value = "resolution", required = false) String resolution,
        @RequestPart("file") MultipartFile file,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file part is required");
        }
        UploadResolution res = parseResolution(resolution);

        try (InputStream in = file.getInputStream()) {
            UploadResult result = service.upload(
                folderId,
                principal.getUser().getId(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                in,
                res
            );
            HttpStatus status = result.newFile() ? HttpStatus.CREATED : HttpStatus.OK;
            return ResponseEntity.status(status).body(UploadResponse.from(result));
        }
    }

    /**
     * wire format ({@code "new_version"} | {@code "rename"})을 enum으로 매핑. null/빈 문자열은 null 반환.
     * 알 수 없는 값은 400 BAD_REQUEST로 변환되도록 IllegalArgumentException.
     */
    private static UploadResolution parseResolution(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return switch (raw.trim().toLowerCase()) {
            case "new_version" -> UploadResolution.NEW_VERSION;
            case "rename" -> UploadResolution.RENAME;
            default -> throw new IllegalArgumentException(
                "unknown resolution (expected new_version|rename): " + raw);
        };
    }
}
