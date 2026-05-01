package com.ibizdrive.file;

import com.ibizdrive.user.IbizDriveUserDetails;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 파일 다운로드 endpoint — A15.5 (docs/02 §6.1, §7.6).
 *
 * <ul>
 *   <li><b>GET</b> {@code /api/files/{id}/download} — 200 + 바이너리 stream</li>
 * </ul>
 *
 * <p><b>응답 헤더</b>:
 * <ul>
 *   <li>{@code Content-Type}: version.mimeType (없으면 {@code application/octet-stream})</li>
 *   <li>{@code Content-Length}: version.sizeBytes</li>
 *   <li>{@code Content-Disposition}: {@code attachment; filename="<ascii-fallback>"; filename*=UTF-8''<percent-encoded>}
 *       — RFC 5987. UTF-8 파일명 / 비ASCII 파일명을 모든 모던 브라우저에서 정상 노출.</li>
 *   <li>{@code ETag}: {@code "<versionId>"} — same file 다른 version 간 cache buster.</li>
 * </ul>
 *
 * <p><b>Body</b>: {@link InputStreamResource}로 streaming. Spring MVC가 InputStream → response body
 * 복사를 처리하며 close까지 책임 (DownloadHandle stream 누수 방지).
 *
 * <p><b>인가 (docs/03 §3, ADR #30)</b>: 파일 {@code READ} 권한. ADR-level 단순화 — DOWNLOAD enum 별도
 * 도입 안 함 (READ가 view + download 모두 grant). closure ADR에서 재확인.
 *
 * <p><b>예외 매핑</b> (GlobalExceptionHandler 위임):
 * <ul>
 *   <li>{@link FileNotFoundException} → 404 NOT_FOUND</li>
 *   <li>{@code AccessDeniedException} (SpEL deny) → 403 PERMISSION_DENIED</li>
 *   <li>{@link IllegalStateException} (storage I/O) → 500 INTERNAL_ERROR</li>
 * </ul>
 *
 * <p><b>책임 분리 (KISS)</b>: controller는 SpEL 가드 + principal에서 actorId 추출 + 응답 헤더 빌드.
 * 활성 검증/version load/storage open/audit emission은 {@link FileDownloadService}.
 *
 * <p>{@link FileUploadController}와 동일하게 별도 controller 분리 — 단일 책임 + multipart consumer와
 * stream producer는 cross-cutting 관심사 부재.
 */
@RestController
@RequestMapping("/api/files")
public class FileDownloadController {

    private final FileDownloadService service;

    public FileDownloadController(FileDownloadService service) {
        this.service = service;
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasPermission(#id, 'file', 'READ')")
    public ResponseEntity<InputStreamResource> download(
        @PathVariable("id") UUID id,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        DownloadHandle handle = service.download(id, principal.getUser().getId());

        FileItem file = handle.file();
        FileVersion version = handle.version();

        MediaType contentType = parseContentType(version.getMimeType());
        String contentDisposition = buildContentDisposition(file.getName());

        return ResponseEntity.ok()
            .contentType(contentType)
            .contentLength(version.getSizeBytes())
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
            .eTag("\"" + version.getId() + "\"")
            .body(new InputStreamResource(handle.stream()));
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

    /**
     * RFC 5987 {@code Content-Disposition: attachment; filename="..."; filename*=UTF-8''...}.
     *
     * <p>{@code filename}은 비ASCII/특수문자 안전 ASCII fallback (legacy UA 호환). {@code filename*}은
     * UTF-8 percent-encoded — 모던 브라우저가 우선시.
     *
     * <p>{@link URLEncoder#encode}는 form encoding(공백 → {@code +})이므로 {@code +}는 RFC 3986 percent
     * 표현({@code %20})으로 후처리.
     */
    private static String buildContentDisposition(String displayName) {
        String safeAscii = sanitizeAscii(displayName);
        String utf8Pct = URLEncoder.encode(displayName, StandardCharsets.UTF_8)
            .replace("+", "%20");
        return "attachment; filename=\"" + safeAscii + "\"; filename*=UTF-8''" + utf8Pct;
    }

    /**
     * 비ASCII 문자를 {@code _}로 치환하고, RFC 6266 token 깨짐을 유발하는 {@code "}/백슬래시도 제거 —
     * legacy UA용 fallback. 모던 UA는 filename* 사용.
     */
    private static String sanitizeAscii(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x20 || c >= 0x7F || c == '"' || c == '\\') {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
