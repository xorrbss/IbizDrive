package com.ibizdrive.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.storage.StorageClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
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
 *   <li>{@link com.ibizdrive.audit.AuditEventType#FILE_UPLOADED} (신규/RENAME) 또는
 *       {@link com.ibizdrive.audit.AuditEventType#VERSION_CREATED} (NEW_VERSION) emission</li>
 * </ul>
 *
 * <p>{@link FileMutationService}의 패턴(클래스 레벨 {@link Transactional} + emitAudit helper +
 * {@link org.springframework.dao.DataIntegrityViolationException} 변환)과 동일.
 *
 * <p>권한 검증은 controller layer({@code @PreAuthorize hasPermission(folderId, 'folder', 'UPLOAD')})에서
 * 이미 통과된 상태로 호출됨 — service는 폴더 활성성과 충돌만 책임.
 */
@Service
@Transactional
public class FileUploadService {

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

    /**
     * 파일 업로드 commit. multipart 진입점에서 호출 — InputStream은 호출자가 닫는다.
     *
     * @param folderId   업로드 대상 폴더 (활성 필수)
     * @param actorId    업로드 사용자 (audit 주체 + ownerId)
     * @param filename   원본 표시 이름 (정규화 전)
     * @param contentType MIME (선언 — 서버는 신뢰하지 않고 스캐너가 검증, 본 트랙은 단순 보존)
     * @param sizeBytes  바이트 길이
     * @param content    바이트 스트림
     * @param resolution 충돌 시 해결 정책. {@code null}이면 충돌 시 conflict 던짐.
     */
    public UploadResult upload(UUID folderId,
                               UUID actorId,
                               String filename,
                               String contentType,
                               long sizeBytes,
                               InputStream content,
                               UploadResolution resolution) {
        throw new UnsupportedOperationException("A15.3 GREEN — not yet implemented");
    }
}
