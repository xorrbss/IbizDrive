package com.ibizdrive.file;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import com.ibizdrive.audit.WebRequestContextHolder;
import com.ibizdrive.common.normalize.NormalizeUtil;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderNotFoundException;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.storage.StorageClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 파일 업로드 도메인 서비스 — A15 (docs/02 §6.1).
 *
 * <p>책임:
 * <ul>
 *   <li>대상 폴더 lock + 활성 검증 ({@link FolderRepository#lockByIdAndDeletedAtIsNull})</li>
 *   <li>충돌 사전 검사 ({@link FileRepository#existsActiveByFolderAndNormalizedName}) + V5 partial unique
 *       index 위반 catch (이중 가드, CLAUDE.md §3 원칙 6)</li>
 *   <li>{@link UploadResolution} 분기에 따른 신규 파일 INSERT 또는 새 version append</li>
 *   <li>storage 객체 키(UUID, ADR #5)로 {@link StorageClient#write} — DB 트랜잭션 안에서 호출하되
 *       commit 실패 시 storage orphan은 MVP 한정 알려진 한계 (cleanup job 별도 트랙)</li>
 *   <li>{@link AuditEventType#FILE_UPLOADED} (신규/RENAME) 또는 {@link AuditEventType#VERSION_CREATED}
 *       (NEW_VERSION) emission</li>
 * </ul>
 *
 * <p>{@link FileMutationService}의 패턴(클래스 레벨 {@link Transactional} + emitAudit helper +
 * {@link DataIntegrityViolationException} 변환)과 동일.
 *
 * <p>권한 검증은 controller layer({@code @PreAuthorize hasPermission(folderId, 'folder', 'UPLOAD')})에서
 * 이미 통과된 상태로 호출됨 — service는 폴더 활성성과 충돌만 책임.
 */
@Service
@Transactional
public class FileUploadService {

    /** RENAME 자동 suffix 생성 시도 한도 — `(N).ext`까지. */
    private static final int RENAME_MAX_TRIES = 1000;

    private final FileRepository fileRepository;
    private final FileVersionRepository fileVersionRepository;
    private final FolderRepository folderRepository;
    private final StorageClient storageClient;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public FileUploadService(FileRepository fileRepository,
                             FileVersionRepository fileVersionRepository,
                             FolderRepository folderRepository,
                             StorageClient storageClient,
                             AuditService auditService,
                             ObjectMapper objectMapper) {
        this.fileRepository = fileRepository;
        this.fileVersionRepository = fileVersionRepository;
        this.folderRepository = folderRepository;
        this.storageClient = storageClient;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public UploadResult upload(UUID folderId,
                               UUID actorId,
                               String filename,
                               String contentType,
                               long sizeBytes,
                               InputStream content,
                               UploadResolution resolution) {
        if (folderId == null) throw new IllegalArgumentException("folderId is required");
        if (actorId == null) throw new IllegalArgumentException("actorId is required");
        if (filename == null) throw new IllegalArgumentException("filename is required");
        if (sizeBytes < 0) throw new IllegalArgumentException("sizeBytes must be >= 0");
        if (content == null) throw new IllegalArgumentException("content is required");

        // NormalizeUtil — V5 unique 컬럼 정합. 빈/예약어/금지문자는 NormalizationException 던짐.
        String displayName = NormalizeUtil.normalizeFileName(filename);
        String normalizedName = NormalizeUtil.normalizedNameForDedup(filename);

        // 폴더 lock (활성 only) — soft-deleted/미존재 → 404 매핑.
        folderRepository.lockByIdAndDeletedAtIsNull(folderId)
            .orElseThrow(() -> new FolderNotFoundException("folder not found: " + folderId));

        boolean conflict = fileRepository.existsActiveByFolderAndNormalizedName(folderId, normalizedName);

        if (conflict && resolution == null) {
            throw new FileNameConflictException(
                "file already exists under folder: " + normalizedName);
        }

        if (conflict && resolution == UploadResolution.NEW_VERSION) {
            return appendNewVersion(folderId, actorId, normalizedName, contentType, sizeBytes, content);
        }

        // (1) RENAME 분기는 normalized 이름 재배정 후 신규 INSERT 흐름과 합류.
        if (conflict && resolution == UploadResolution.RENAME) {
            String[] resolved = resolveRename(folderId, displayName);
            displayName = resolved[0];
            normalizedName = resolved[1];
        }

        return insertNewFile(folderId, actorId, displayName, normalizedName,
            contentType, sizeBytes, content);
    }

    // ──────────────────────────────────────────────────────────────────
    // branches
    // ──────────────────────────────────────────────────────────────────

    private UploadResult insertNewFile(UUID folderId,
                                       UUID actorId,
                                       String displayName,
                                       String normalizedName,
                                       String contentType,
                                       long sizeBytes,
                                       InputStream content) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        UUID storageKey = UUID.randomUUID();

        // storage write 먼저 — DB INSERT 실패 시 orphan 가능성은 MVP 알려진 한계 (docs/02 §6.2).
        // 객체 key는 UUID 그대로 (ADR #5: 원본 파일명 미저장). 디렉터리 prefix는 LocalFs/S3 어댑터 책임.
        writeToStorage(storageKey, content, sizeBytes, contentType);

        // V13 scope: file은 소속 folder의 scope를 그대로 상속. lock은 upload() 진입 시 이미 획득
        // 됐으므로 first-level cache hit으로 동일 트랜잭션 내 추가 query 비용 무시 가능.
        Folder folder = folderRepository.findByIdAndDeletedAtIsNull(folderId)
            .orElseThrow(() -> new FolderNotFoundException("folder vanished mid-upload: " + folderId));

        FileItem file = new FileItem();
        file.setId(UUID.randomUUID());
        file.setFolderId(folderId);
        file.setName(displayName);
        file.setNormalizedName(normalizedName);
        file.setOwnerId(actorId);
        file.setSizeBytes(sizeBytes);
        file.setMimeType(contentType);
        file.assignScope(folder.getScopeType(), folder.getScopeId());
        file.setCreatedAt(now);
        file.setUpdatedAt(now);

        FileItem savedFile;
        try {
            savedFile = fileRepository.saveAndFlush(file);
        } catch (DataIntegrityViolationException ex) {
            throw new FileNameConflictException(
                "file name conflict at upload: " + normalizedName, ex);
        }

        FileVersion version = new FileVersion();
        version.setId(UUID.randomUUID());
        version.setFileId(savedFile.getId());
        version.setVersionNumber(1);
        version.setStorageKey(storageKey);
        version.setSizeBytes(sizeBytes);
        version.setChecksumSha256(placeholderChecksum());
        version.setMimeType(contentType);
        version.setScanStatus(VersionScanStatus.PENDING);
        version.setUploadedBy(actorId);
        version.setUploadedAt(now);
        FileVersion savedVersion = fileVersionRepository.saveAndFlush(version);

        savedFile.setCurrentVersionId(savedVersion.getId());
        savedFile = fileRepository.saveAndFlush(savedFile);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("folderId", folderId);
        after.put("name", displayName);
        after.put("normalizedName", normalizedName);
        after.put("sizeBytes", sizeBytes);
        after.put("versionId", savedVersion.getId());
        emitAudit(AuditEventType.FILE_UPLOADED, savedFile.getId(), actorId, null, after);

        return new UploadResult(savedFile, savedVersion, true);
    }

    private UploadResult appendNewVersion(UUID folderId,
                                          UUID actorId,
                                          String normalizedName,
                                          String contentType,
                                          long sizeBytes,
                                          InputStream content) {
        FileItem target = fileRepository
            .lockActiveByFolderAndNormalizedName(folderId, normalizedName)
            .orElseThrow(() -> new FileNameConflictException(
                "conflict target disappeared during upload: " + normalizedName));

        Integer maxVersion = fileVersionRepository.findMaxVersionNumberByFileId(target.getId());
        int nextVersion = (maxVersion == null ? 0 : maxVersion) + 1;

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        UUID storageKey = UUID.randomUUID();

        writeToStorage(storageKey, content, sizeBytes, contentType);

        FileVersion version = new FileVersion();
        version.setId(UUID.randomUUID());
        version.setFileId(target.getId());
        version.setVersionNumber(nextVersion);
        version.setStorageKey(storageKey);
        version.setSizeBytes(sizeBytes);
        version.setChecksumSha256(placeholderChecksum());
        version.setMimeType(contentType);
        version.setScanStatus(VersionScanStatus.PENDING);
        version.setUploadedBy(actorId);
        version.setUploadedAt(now);
        FileVersion savedVersion = fileVersionRepository.saveAndFlush(version);

        target.setCurrentVersionId(savedVersion.getId());
        target.setSizeBytes(sizeBytes);
        target.setMimeType(contentType);
        target.setUpdatedAt(now);
        FileItem savedFile = fileRepository.saveAndFlush(target);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("versionId", savedVersion.getId());
        after.put("versionNumber", nextVersion);
        after.put("sizeBytes", sizeBytes);
        emitAudit(AuditEventType.VERSION_CREATED, savedFile.getId(), actorId, null, after);

        return new UploadResult(savedFile, savedVersion, false);
    }

    // ──────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────

    /**
     * RENAME 자동 suffix — `Name (1).ext`, `(2).ext`, ... 충돌 없는 첫 후보 반환. 1000회 시도해도
     * 자리가 없으면 conflict로 fail-fast — 동일 폴더에 1000개 동명 파일이 있는 비현실적 상황.
     */
    private String[] resolveRename(UUID folderId, String displayName) {
        int dot = displayName.lastIndexOf('.');
        String base = dot > 0 ? displayName.substring(0, dot) : displayName;
        String ext = dot > 0 ? displayName.substring(dot) : "";

        for (int i = 1; i <= RENAME_MAX_TRIES; i++) {
            String candidate = base + " (" + i + ")" + ext;
            String candidateNormalized = NormalizeUtil.normalizedNameForDedup(candidate);
            if (!fileRepository.existsActiveByFolderAndNormalizedName(folderId, candidateNormalized)) {
                return new String[]{NormalizeUtil.normalizeFileName(candidate), candidateNormalized};
            }
        }
        throw new FileNameConflictException(
            "rename suffix exhausted under folder: " + displayName);
    }

    private void writeToStorage(UUID storageKey, InputStream content, long sizeBytes, String contentType) {
        try {
            storageClient.write(storageKey.toString(), content, sizeBytes, contentType);
        } catch (IOException e) {
            // 트랜잭션 rollback 유도 — 상위 컨트롤러가 5xx로 매핑.
            throw new IllegalStateException("storage write failed: " + storageKey, e);
        }
    }

    /**
     * MVP placeholder — A15에서는 SHA-256 계산 미수행 (docs/02 §6.1 line "checksum_sha256 …
     * 스캐너 워커가 갱신"). V5 NOT NULL + CHECK length 64 충족용 zero filler.
     */
    private String placeholderChecksum() {
        return "0".repeat(64);
    }

    private void emitAudit(AuditEventType eventType,
                           UUID targetId,
                           UUID actorId,
                           Map<String, ?> beforeState,
                           Map<String, ?> afterState) {
        AuditEvent event = new AuditEvent(
            eventType,
            actorId,
            WebRequestContextHolder.currentIp(),
            WebRequestContextHolder.currentUserAgent(),
            AuditTargetType.FILE,
            targetId,
            toJson(beforeState),
            toJson(afterState),
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
