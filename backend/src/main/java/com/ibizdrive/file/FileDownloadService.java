package com.ibizdrive.file;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import com.ibizdrive.audit.WebRequestContextHolder;
import com.ibizdrive.storage.StorageClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 파일 다운로드 도메인 서비스 — A15.5 (docs/02 §6.1, §7.6).
 *
 * <p>책임:
 * <ul>
 *   <li>활성 파일 조회 ({@link FileRepository#findByIdAndDeletedAtIsNull}) — soft-deleted 파일은 404</li>
 *   <li>{@code current_version_id} 로드 — 부재 시 데이터 corruption 신호로 404 매핑</li>
 *   <li>{@link StorageClient#read} stream open → {@link DownloadHandle}로 묶어 반환</li>
 *   <li>{@link AuditEventType#FILE_DOWNLOADED} emission — {@link AuditService#record}는
 *       REQUIRES_NEW 별도 트랜잭션이므로 본 트랜잭션 rollback과 격리</li>
 * </ul>
 *
 * <p>{@link Transactional}({@code readOnly=true})로 read-only 힌트. storage I/O는 트랜잭션 외부 작용이지만
 * stream open만 수행하고 실제 byte 전송은 controller가 트랜잭션 종료 후 수행 — caller가
 * {@link DownloadHandle#stream()} close 책임을 지므로 leak 회피.
 *
 * <p>권한 검증은 controller layer({@code @PreAuthorize hasPermission(#id, 'file', 'READ')})에서
 * 이미 통과된 상태로 호출됨.
 */
@Service
@Transactional(readOnly = true)
public class FileDownloadService {

    private final FileRepository fileRepository;
    private final FileVersionRepository fileVersionRepository;
    private final StorageClient storageClient;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public FileDownloadService(FileRepository fileRepository,
                               FileVersionRepository fileVersionRepository,
                               StorageClient storageClient,
                               AuditService auditService,
                               ObjectMapper objectMapper) {
        this.fileRepository = fileRepository;
        this.fileVersionRepository = fileVersionRepository;
        this.storageClient = storageClient;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /**
     * 활성 파일의 current version을 storage에서 열어 {@link DownloadHandle}로 반환한다.
     *
     * <p>호출자(controller)는 반환된 {@link DownloadHandle#stream()}을 try-with-resources 또는
     * Spring의 {@code InputStreamResource}로 close해야 한다.
     *
     * @throws FileNotFoundException 활성 파일이 아니거나 current_version_id가 가리키는 version row 부재
     * @throws IllegalStateException storage read 실패 (객체 부재 또는 I/O 오류)
     */
    public DownloadHandle download(UUID fileId, UUID actorId) {
        if (fileId == null) throw new IllegalArgumentException("fileId is required");
        if (actorId == null) throw new IllegalArgumentException("actorId is required");

        FileItem file = fileRepository.findByIdAndDeletedAtIsNull(fileId)
            .orElseThrow(() -> new FileNotFoundException("file not found or deleted: " + fileId));

        UUID currentVersionId = file.getCurrentVersionId();
        if (currentVersionId == null) {
            // V5 schema에서 nullable이지만 정상 업로드 흐름이 항상 set — 도달 시 데이터 corruption.
            throw new FileNotFoundException("file has no current version: " + fileId);
        }
        FileVersion version = fileVersionRepository.findById(currentVersionId)
            .orElseThrow(() -> new FileNotFoundException(
                "current version not found: " + currentVersionId));

        InputStream stream;
        try {
            stream = storageClient.read(version.getStorageKey().toString());
        } catch (IOException e) {
            throw new IllegalStateException(
                "storage read failed: " + version.getStorageKey(), e);
        }

        emitDownloadAudit(file.getId(), version.getId(), actorId);

        return new DownloadHandle(file, version, stream);
    }

    private void emitDownloadAudit(UUID fileId, UUID versionId, UUID actorId) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("versionId", versionId);
        AuditEvent event = new AuditEvent(
            AuditEventType.FILE_DOWNLOADED,
            actorId,
            WebRequestContextHolder.currentIp(),
            WebRequestContextHolder.currentUserAgent(),
            AuditTargetType.FILE,
            fileId,
            null,
            toJson(after),
            null
        );
        auditService.record(event);
    }

    private String toJson(Map<String, ?> state) {
        if (state == null) return null;
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("audit state serialization failed", e);
        }
    }
}
