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
import com.ibizdrive.team.TeamArchiveGuard;
import com.ibizdrive.trash.TrashRetentionProperties;
import jakarta.annotation.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * File mutation 도메인 서비스 — A4.8 (docs/02 §2.4, §6 — files mirror of A4.6 folder service).
 *
 * <p>책임:
 * <ul>
 *   <li>{@link #rename} — 활성 파일 이름 변경 (동일 폴더 내)</li>
 *   <li>{@link #move}   — 활성 파일 부모 폴더 변경</li>
 *   <li>{@link #delete} — 활성 파일 soft-delete (휴지통 이동)</li>
 *   <li>{@link #restore} — 휴지통 파일을 {@code original_folder_id}로 복원</li>
 * </ul>
 *
 * <p>{@code create}는 파일 업로드 흐름(A4.9 tus)에 종속되어 본 서비스에 두지 않는다 — 업로드 완료 시점에
 * 별도 트랜잭션이 INSERT를 수행하며 동일한 conflict 이중 가드를 적용할 예정 (A4.9 범위).
 *
 * <p><b>트랜잭션 + lock 정책 (CLAUDE.md §3 원칙 7)</b>: 모든 mutation은 클래스 레벨
 * {@link Transactional}로 감싸지고, 대상 파일은 진입 시점에 PESSIMISTIC_WRITE 잠긴다 —
 * rename/move/delete는 {@link FileRepository#lockByIdAndDeletedAtIsNull}, restore는
 * {@link FileRepository#lockByIdAndDeletedAtIsNotNull}.
 *
 * <p><b>충돌 검사 이중 가드 (CLAUDE.md §3 원칙 6)</b>: 사전 native query
 * ({@link FileRepository#existsActiveByFolderAndNormalizedName(UUID, String)} /
 * {@link FileRepository#existsActiveByFolderAndNormalizedNameExcludingId(UUID, String, UUID)})와
 * UPDATE 시점 V5 unique index 위반({@link DataIntegrityViolationException}) 양쪽 모두
 * {@link FileNameConflictException}로 변환. 진실의 출처는 항상 DB unique index.
 *
 * <p><b>감사 emission 정책 (CLAUDE.md §3 원칙 8)</b>: {@link AuditService#record}는 {@code REQUIRES_NEW}
 * 별도 트랜잭션에서 INSERT만 수행하므로 본 서비스의 비즈니스 트랜잭션이 rollback되어도 audit row는
 * 보존된다.
 *
 * <p><b>FolderRepository 의존성</b>: move/restore가 대상 폴더의 활성 여부를 검증하므로
 * {@link FolderRepository#findByIdAndDeletedAtIsNull}을 호출. 폴더 자체의 lock은 잡지 않는다 —
 * 파일의 mutation은 폴더 메타데이터를 변경하지 않으며, FK 제약이 최종 가드.
 */
@Service
@Transactional
public class FileMutationService {

    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    /** 휴지통 보존 기간(일) — application.yml {@code app.trash.retention-days} (docs/02 §6.5). */
    private final TrashRetentionProperties retention;
    /** Plan E T5 — restore 진입점에서 archived 팀 차단 (spec §2.2/§5.4). */
    private final TeamArchiveGuard teamArchiveGuard;

    public FileMutationService(FileRepository fileRepository,
                               FolderRepository folderRepository,
                               AuditService auditService,
                               ObjectMapper objectMapper,
                               TrashRetentionProperties retention,
                               TeamArchiveGuard teamArchiveGuard) {
        this.fileRepository = fileRepository;
        this.folderRepository = folderRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.retention = retention;
        this.teamArchiveGuard = teamArchiveGuard;
    }

    // ──────────────────────────────────────────────────────────────────
    // rename
    // ──────────────────────────────────────────────────────────────────

    /**
     * 활성 파일의 이름을 변경한다. {@code newName}이 정규화 후 기존 값과 동일하면 no-op (audit 미발행).
     *
     * @throws FileNotFoundException     fileId가 활성 파일이 아님 (soft-deleted 포함)
     * @throws FileNameConflictException 같은 폴더 내 다른 활성 파일과 normalized_name 충돌
     */
    public FileItem rename(UUID fileId, String newName, UUID actorId) {
        if (fileId == null) throw new IllegalArgumentException("fileId is required");
        if (newName == null) throw new IllegalArgumentException("newName is required");

        FileItem target = fileRepository.lockByIdAndDeletedAtIsNull(fileId)
            .orElseThrow(() -> new FileNotFoundException("file not found: " + fileId));

        String newDisplay = NormalizeUtil.normalizeFileName(newName);
        String newNormalized = NormalizeUtil.normalizedNameForDedup(newName);

        // short-circuit: 정규화 결과 동일 + display 동일이면 audit 미발행. display만 다르면 의미 있음.
        if (newNormalized.equals(target.getNormalizedName()) && newDisplay.equals(target.getName())) {
            return target;
        }

        if (fileRepository.existsActiveByFolderAndNormalizedNameExcludingId(
                target.getFolderId(), newNormalized, target.getId())) {
            throw new FileNameConflictException(
                "file name already exists under folder: " + newNormalized);
        }

        String oldDisplay = target.getName();
        String oldNormalized = target.getNormalizedName();

        target.setName(newDisplay);
        target.setNormalizedName(newNormalized);
        target.setUpdatedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));

        FileItem saved;
        try {
            saved = fileRepository.saveAndFlush(target);
        } catch (DataIntegrityViolationException ex) {
            throw new FileNameConflictException(
                "file name conflict at rename: " + newNormalized, ex);
        }

        emitAudit(
            AuditEventType.FILE_RENAMED,
            saved.getId(),
            actorId,
            Map.of("name", oldDisplay, "normalizedName", oldNormalized),
            Map.of("name", newDisplay, "normalizedName", newNormalized)
        );
        return saved;
    }

    // ──────────────────────────────────────────────────────────────────
    // move
    // ──────────────────────────────────────────────────────────────────

    /**
     * 활성 파일의 부모 폴더를 변경한다. 파일은 항상 폴더에 속하므로 {@code newFolderId}는 NOT NULL.
     *
     * @throws IllegalArgumentException  newFolderId == null
     * @throws FileNotFoundException     fileId가 활성 파일이 아님
     * @throws FolderNotFoundException   newFolderId가 활성 폴더가 아님
     * @throws FileNameConflictException 새 폴더 안에 동일 normalized_name 활성 파일 존재
     */
    public FileItem move(UUID fileId, UUID newFolderId, UUID actorId) {
        if (fileId == null) throw new IllegalArgumentException("fileId is required");
        if (newFolderId == null) throw new IllegalArgumentException("newFolderId is required");

        FileItem target = fileRepository.lockByIdAndDeletedAtIsNull(fileId)
            .orElseThrow(() -> new FileNotFoundException("file not found: " + fileId));

        UUID currentFolder = target.getFolderId();
        if (newFolderId.equals(currentFolder)) {
            return target;                                  // short-circuit
        }

        // 새 폴더가 활성인지 확인. 폴더 자체의 lock은 잡지 않음 — 파일 이동은 폴더 메타데이터를 변경하지 않으며
        // FK가 최종 가드.
        folderRepository.findByIdAndDeletedAtIsNull(newFolderId)
            .orElseThrow(() -> new FolderNotFoundException(
                "target folder not found: " + newFolderId));

        if (fileRepository.existsActiveByFolderAndNormalizedNameExcludingId(
                newFolderId, target.getNormalizedName(), target.getId())) {
            throw new FileNameConflictException(
                "file name already exists under target folder: " + target.getNormalizedName());
        }

        target.setFolderId(newFolderId);
        target.setUpdatedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));

        FileItem saved;
        try {
            saved = fileRepository.saveAndFlush(target);
        } catch (DataIntegrityViolationException ex) {
            throw new FileNameConflictException(
                "file name conflict at move: " + target.getNormalizedName(), ex);
        }

        Map<String, Object> beforeState = new LinkedHashMap<>();
        beforeState.put("folderId", currentFolder);
        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("folderId", newFolderId);
        emitAudit(AuditEventType.FILE_MOVED, saved.getId(), actorId, beforeState, afterState);
        return saved;
    }

    // ──────────────────────────────────────────────────────────────────
    // delete (soft)
    // ──────────────────────────────────────────────────────────────────

    /**
     * 활성 파일을 휴지통으로 이동(soft-delete). {@code deleted_at}/{@code purge_after}/{@code original_folder_id}
     * 세 컬럼을 동시에 set — V5 CHECK 제약 {@code (deleted_at IS NULL) = (purge_after IS NULL)} 준수.
     *
     * <p>{@code folder_id}는 변경하지 않는다 — V5에서 NOT NULL이며 휴지통에서도 원래 폴더 정보가 필요.
     * {@code original_folder_id}는 복원 시 사용할 destination 스냅샷.
     *
     * @throws FileNotFoundException fileId가 활성 파일이 아님 (이미 휴지통 또는 미존재)
     */
    public FileItem delete(UUID fileId, UUID actorId) {
        if (fileId == null) throw new IllegalArgumentException("fileId is required");

        FileItem target = fileRepository.lockByIdAndDeletedAtIsNull(fileId)
            .orElseThrow(() -> new FileNotFoundException("file not found: " + fileId));

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        target.setDeletedAt(now);
        target.setPurgeAfter(now.plus(retention.days(), ChronoUnit.DAYS));
        target.setOriginalFolderId(target.getFolderId());
        // V10 — admin global trash UI에서 cross-owner 복원 시 deleter 식별용 (audit_log 별도 lookup 우회).
        target.setDeletedBy(actorId);
        target.setUpdatedAt(now);

        FileItem saved = fileRepository.saveAndFlush(target);

        Map<String, Object> beforeState = new LinkedHashMap<>();
        beforeState.put("folderId", saved.getFolderId());
        beforeState.put("name", saved.getName());
        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("deletedAt", now.toString());
        afterState.put("purgeAfter", saved.getPurgeAfter().toString());
        afterState.put("originalFolderId", saved.getOriginalFolderId());
        emitAudit(AuditEventType.FILE_DELETED, saved.getId(), actorId, beforeState, afterState);
        return saved;
    }

    // ──────────────────────────────────────────────────────────────────
    // restore
    // ──────────────────────────────────────────────────────────────────

    /**
     * 휴지통 파일을 {@code original_folder_id} 로 복원한다. tombstone 컬럼 3종을 모두 NULL 로 클리어.
     *
     * <p>복원 destination 이 활성 폴더가 아니면(원래 폴더가 그 사이 삭제됨) 복원 실패 — 명시적
     * destination 변경은 본 endpoint 범위 외 (frontend 가 별도 폴더 선택 후 move 를 결합하는 흐름).
     *
     * <p>{@code newName} 처리 (v1.x RestoreConflictDialog 트랙):
     * <ul>
     *   <li>{@code newName == null} — 기존 이름 그대로 복원. 충돌 시 {@link FileRestoreConflictException}
     *       (envelope {@code RESTORE_CONFLICT}) — frontend 가 RestoreConflictDialog 띄움.</li>
     *   <li>{@code newName != null} — NFC 정규화 + UNIQUE 재검사 후 새 이름으로 복원. 충돌 시
     *       {@link FileNameConflictException} (envelope {@code RENAME_CONFLICT}) — frontend 가
     *       다이얼로그의 inline alert 로 분기.</li>
     * </ul>
     *
     * <p><b>archived 팀 차단 (Plan E T5 — spec §2.2/§5.4)</b>: target lock 직후, original folder 검증
     * 이전에 {@link TeamArchiveGuard#assertNotArchived}를 호출해 archived 팀 콘텐츠 복원을 차단한다
     * ({@link com.ibizdrive.team.TeamArchivedException}, HTTP 423 {@code TEAM_ARCHIVED}).
     * V13 NOT NULL 제약으로 soft-deleted row의 scope 정보가 보존되므로 target.scope로 그대로 검증.
     *
     * <p><b>cross-scope mismatch (Plan E T5 — spec §3.4/§5.2/§5.3)</b>: original folder가 다른
     * workspace로 이동된 경우(cross-workspace 데이터 재배치 후 발생 가능), 복원하면 자식이 원래
     * workspace를 떠나 §1.2 invariant 위반. {@link FileRestoreConflictException.Reason#SCOPE_MISMATCH}
     * (envelope {@code RESTORE_CONFLICT} HTTP 409 + body {@code reason='scope_mismatch'})로 차단.
     *
     * @throws FileNotFoundException        fileId 가 휴지통 파일이 아님 (이미 활성 또는 미존재 또는 originalFolder 부재)
     * @throws FolderNotFoundException      original folder 가 활성이 아님 (Plan E T5 — T4 패턴 답습)
     * @throws com.ibizdrive.team.TeamArchivedException scope=TEAM 이고 해당 Team 이 archived 상태
     * @throws FileRestoreConflictException newName 미지정 + 원본 이름이 원위치에서 충돌,
     *                                      또는 cross-scope mismatch ({@code SCOPE_MISMATCH})
     * @throws FileNameConflictException    newName 지정 + 새 이름이 원위치에서 충돌
     */
    public FileItem restore(UUID fileId, UUID actorId) {
        return restore(fileId, actorId, null);
    }

    public FileItem restore(UUID fileId, UUID actorId, @Nullable String newName) {
        if (fileId == null) throw new IllegalArgumentException("fileId is required");

        FileItem target = fileRepository.lockByIdAndDeletedAtIsNotNull(fileId)
            .orElseThrow(() -> new FileNotFoundException("trashed file not found: " + fileId));

        // Plan E T5 / spec §2.2/§5.4 — archived 팀 콘텐츠는 read-only. soft-deleted row의
        // scope_type/scope_id는 V13 NOT NULL 제약으로 preserve되므로 target.scope로 그대로 검증.
        // name conflict / cross-scope mismatch 검사 이전에 단락 — 시도 자체가 write.
        teamArchiveGuard.assertNotArchived(target.getScopeType(), target.getScopeId());

        UUID originalFolderId = target.getOriginalFolderId();
        if (originalFolderId == null) {
            // V5 schema 상 nullable이지만 delete()가 항상 set — 도달 시 데이터 corruption.
            throw new FileNotFoundException(
                "trashed file has no original folder snapshot: " + fileId);
        }
        // 원래 폴더가 여전히 활성인지 확인. soft-deleted 폴더로의 복원은 불허.
        // Plan E T5 — T4 패턴 답습: original folder lookup 결과를 후속 cross-scope 검증에 재사용.
        // Active 폴더 부재는 FolderNotFoundException으로 통일 (T4 reviewer 인정한 NOT_FOUND UX 일치).
        Folder originalFolder = folderRepository.findByIdAndDeletedAtIsNull(originalFolderId)
            .orElseThrow(() -> new FolderNotFoundException(
                "original folder is not active: " + originalFolderId));
        // Plan E T5 — cross-scope mismatch: original folder가 활성이지만 다른 workspace로
        // 이동된 경우 (cross-workspace 데이터 재배치 후 발생 가능). 복원 시 자식이 원래 workspace를
        // 떠나면 §1.2 invariant 위반이므로 차단. wire body의 reason='scope_mismatch'.
        // details map의 키는 "reason" / "resourceId" 회피 (handler가 silent overwrite — T3 reviewer 권고).
        if (originalFolder.getScopeType() != target.getScopeType()
                || !originalFolder.getScopeId().equals(target.getScopeId())) {
            Map<String, Object> mismatch = new LinkedHashMap<>();
            mismatch.put("expectedScopeType", target.getScopeType().dbValue());
            mismatch.put("expectedScopeId", target.getScopeId().toString());
            mismatch.put("actualScopeType", originalFolder.getScopeType().dbValue());
            mismatch.put("actualScopeId", originalFolder.getScopeId().toString());
            throw new FileRestoreConflictException(
                FileRestoreConflictException.Reason.SCOPE_MISMATCH,
                target.getId(),
                "original folder moved to a different workspace",
                mismatch);
        }

        // newName 정규화 (지정 시) — rename 패턴 미러.
        String oldDisplay = target.getName();
        String oldNormalized = target.getNormalizedName();
        String resolvedDisplay;
        String resolvedNormalized;
        boolean renaming = newName != null;
        if (renaming) {
            resolvedDisplay = NormalizeUtil.normalizeFileName(newName);
            resolvedNormalized = NormalizeUtil.normalizedNameForDedup(newName);
        } else {
            resolvedDisplay = oldDisplay;
            resolvedNormalized = oldNormalized;
        }

        if (fileRepository.existsActiveByFolderAndNormalizedNameExcludingId(
                originalFolderId, resolvedNormalized, target.getId())) {
            if (renaming) {
                throw new FileNameConflictException(
                    "file name already exists under original folder: " + resolvedNormalized);
            }
            throw new FileRestoreConflictException(
                "file name already exists under original folder: " + resolvedNormalized);
        }

        Instant deletedAtBefore = target.getDeletedAt();
        target.setFolderId(originalFolderId);
        if (renaming) {
            target.setName(resolvedDisplay);
            target.setNormalizedName(resolvedNormalized);
        }
        target.setDeletedAt(null);
        target.setPurgeAfter(null);
        target.setOriginalFolderId(null);
        // V10 — restore 시 deleter 정보도 클리어 (CHECK 단방향: 활성 row는 deleted_by IS NULL).
        target.setDeletedBy(null);
        target.setUpdatedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));

        FileItem saved;
        try {
            saved = fileRepository.saveAndFlush(target);
        } catch (DataIntegrityViolationException ex) {
            // partial unique index가 deleted_at IS NULL인 행에만 적용되므로 NULL로 클리어하는
            // UPDATE 시점에 race로 충돌 가능 — 사전 검사 이중 가드.
            if (renaming) {
                throw new FileNameConflictException(
                    "file name conflict at restore: " + resolvedNormalized, ex);
            }
            throw new FileRestoreConflictException(
                "file name conflict at restore: " + resolvedNormalized, ex);
        }

        Map<String, Object> beforeState = new LinkedHashMap<>();
        beforeState.put("deletedAt", deletedAtBefore == null ? null : deletedAtBefore.toString());
        beforeState.put("originalFolderId", originalFolderId);
        if (renaming) {
            beforeState.put("name", oldDisplay);
            beforeState.put("normalizedName", oldNormalized);
        }
        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("folderId", originalFolderId);
        afterState.put("deletedAt", null);
        if (renaming) {
            afterState.put("name", resolvedDisplay);
            afterState.put("normalizedName", resolvedNormalized);
        }
        emitAudit(AuditEventType.FILE_RESTORED, saved.getId(), actorId, beforeState, afterState);
        return saved;
    }

    // ──────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────

    /**
     * 단일 emission 진입점. ObjectMapper 직렬화 실패는 RuntimeException으로 승격하지만
     * AuditService가 REQUIRES_NEW 트랜잭션에서 받아 INSERT한다 (rollback 격리).
     */
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
