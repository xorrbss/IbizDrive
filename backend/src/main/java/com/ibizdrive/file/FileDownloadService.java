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
 * 파일 다운로드 도메인 서비스 — A15.5 / M-RP.2.1 (docs/02 §6.1, §7.6).
 *
 * <p>책임:
 * <ul>
 *   <li>{@link #download} — 활성 파일의 {@code current_version_id} 다운로드 ({@code FILE_DOWNLOADED})</li>
 *   <li>{@link #downloadVersion} — 명시된 versionId 다운로드 (M-RP.2.1, {@code VERSION_DOWNLOADED}).
 *       version의 {@code file_id}와 path {@code fileId} 일치를 service에서 재검증해 cross-file 우회 차단</li>
 *   <li>{@link StorageClient#read} stream open → {@link DownloadHandle}로 묶어 반환</li>
 *   <li>{@link AuditService#record} 호출 — REQUIRES_NEW 별도 트랜잭션이라 본 트랜잭션 rollback과 격리</li>
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

        InputStream stream = openStorage(version);

        emitDownloadAudit(AuditEventType.FILE_DOWNLOADED, file.getId(), version.getId(), actorId);

        return new DownloadHandle(file, version, stream);
    }

    /**
     * 특정 version을 핀해서 다운로드한다 — M-RP.2.1 (docs/02 §7.6).
     *
     * <p>{@link #download}와의 차이:
     * <ul>
     *   <li>{@code current_version_id} 대신 caller가 명시한 {@code versionId}를 로드.</li>
     *   <li>cross-file 참조 차단 — {@link FileVersion#getFileId()}가 path의 {@code fileId}와
     *       일치하지 않으면 404 ({@link FileNotFoundException}). 다른 파일의 version을 본 파일 컨텍스트로
     *       임의 다운로드하는 우회를 막는 핵심 가드 — controller의 READ 가드는 path의 fileId 기준이므로,
     *       version의 실제 소속을 service가 재검증해야 한다.</li>
     *   <li>audit emit 시 {@link AuditEventType#VERSION_DOWNLOADED} 사용 — current 다운로드와 구분.</li>
     * </ul>
     *
     * <p>file은 {@link FileRepository#findByIdAndDeletedAtIsNull}로 활성 검증 — soft-deleted 파일의
     * 옛 version 다운로드는 차단(휴지통 노출 차단 정책 일관). version 자체는 soft-delete 컬럼이 없으며
     * 영구 보존(docs/02 §1.3) — file이 활성이면 모든 과거 version 다운로드 가능.
     *
     * @throws FileNotFoundException 파일이 active가 아니거나, version row 부재이거나, version.fileId 불일치
     * @throws IllegalStateException storage I/O 실패
     */
    public DownloadHandle downloadVersion(UUID fileId, UUID versionId, UUID actorId) {
        if (fileId == null) throw new IllegalArgumentException("fileId is required");
        if (versionId == null) throw new IllegalArgumentException("versionId is required");
        if (actorId == null) throw new IllegalArgumentException("actorId is required");

        FileItem file = fileRepository.findByIdAndDeletedAtIsNull(fileId)
            .orElseThrow(() -> new FileNotFoundException("file not found or deleted: " + fileId));

        FileVersion version = fileVersionRepository.findById(versionId)
            .orElseThrow(() -> new FileNotFoundException("version not found: " + versionId));

        if (!fileId.equals(version.getFileId())) {
            throw new FileNotFoundException(
                "version " + versionId + " does not belong to file " + fileId);
        }

        InputStream stream = openStorage(version);

        emitDownloadAudit(AuditEventType.VERSION_DOWNLOADED, file.getId(), version.getId(), actorId);

        return new DownloadHandle(file, version, stream);
    }

    private InputStream openStorage(FileVersion version) {
        try {
            return storageClient.read(version.getStorageKey().toString());
        } catch (IOException e) {
            throw new IllegalStateException(
                "storage read failed: " + version.getStorageKey(), e);
        }
    }

    private void emitDownloadAudit(AuditEventType eventType, UUID fileId, UUID versionId, UUID actorId) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("versionId", versionId);
        AuditEvent event = new AuditEvent(
            eventType,
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
