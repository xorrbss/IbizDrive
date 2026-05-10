package com.ibizdrive.file;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import com.ibizdrive.audit.WebRequestContextHolder;
import com.ibizdrive.team.TeamArchiveGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 파일 버전 mutation 도메인 서비스 — M-RP.2.2 (docs/02 §7.6, ADR #39).
 *
 * <p>책임: {@link #restoreVersion} — 명시된 {@code versionId}를 {@code current_version_id}로 재지정.
 *
 * <p><b>복원 의미론 = 옵션 A (RP-1, ADR #39)</b>:
 * <ul>
 *   <li>새 {@code file_versions} row 생성하지 않음.</li>
 *   <li>storage 객체 복제 없음 — 기존 {@code storage_key} 그대로 참조.</li>
 *   <li>{@code files.current_version_id} 재지정 + {@code updated_at} 갱신.</li>
 *   <li><b>denormalized 메타 동기화</b>: {@code files.size_bytes} / {@code files.mime_type}는
 *       {@link FileUploadService}가 신규 version 추가 시 항상 current version의 값으로 갱신한다
 *       (FileUploadService:215-216). 이는 file row(목록 view)가 join 없이 현재 버전 메타를
 *       보여주기 위한 의도적 denormalization이다. 따라서 restore 시에도 target version의
 *       값으로 동기화해야 invariant가 보존된다 — 누락 시 v3(4MB) 업로드 후 v1(1KB) 복원 시
 *       file row가 4MB로 잘못 표시된다.</li>
 *   <li><b>멱등</b>: 이미 current인 version 재호출 시 200 반환 + audit emit X (no-op로 격리).</li>
 * </ul>
 *
 * <p><b>트랜잭션 + lock 정책 (CLAUDE.md §3 원칙 7)</b>: 클래스 레벨 {@link Transactional}로 감싸지고,
 * 대상 파일은 {@link FileRepository#lockByIdAndDeletedAtIsNull}로 PESSIMISTIC_WRITE 잠긴다.
 * 동시 복원 race가 발생해도 마지막 트랜잭션의 currentVersionId가 확정 — partial unique index와 무관한
 * 단순 행 잠금으로 충분.
 *
 * <p><b>cross-file 가드</b>: {@link FileDownloadService#downloadVersion}와 동일한 의미론으로,
 * {@link FileVersion#getFileId()}가 path {@code fileId}와 일치하지 않으면 {@link FileNotFoundException}.
 * 다른 파일의 version으로 우회 복원하려는 시도 차단 — controller의 {@code EDIT} 가드는 path fileId 기준이므로.
 *
 * <p><b>감사 emission 정책 (CLAUDE.md §3 원칙 8)</b>: 비-멱등 분기에서만 {@link AuditEventType#VERSION_RESTORED}
 * emit. {@link AuditService#record}는 REQUIRES_NEW 별도 트랜잭션이라 본 트랜잭션 rollback과 격리된다.
 * 멱등 분기는 audit 폭증 회피 — 같은 결과를 만드는 호출은 새 history line이 아니다.
 *
 * <p>{@link FileMutationService}와 분리한 이유: file rename/move/delete/restore(휴지통)는 file 자체의
 * lifecycle, version restore는 file의 version pointer 갱신으로 도메인 책임이 다르며 두 서비스의
 * 트랜잭션 경계를 섞을 필요가 없다 (KISS, 단일 책임).
 *
 * <p><b>TEAM_ARCHIVED 가드</b> (spec §2.2/§5.4): 대상 파일 fetch 직후, mutation 직전에
 * {@link TeamArchiveGuard#assertNotArchived(com.ibizdrive.folder.ScopeType, UUID)} 호출 — archived
 * 팀에 속한 파일의 버전 복원 차단. DEPARTMENT scope는 가드 내부에서 no-op.
 */
@Service
@Transactional
public class FileVersionMutationService {

    private final FileRepository fileRepository;
    private final FileVersionRepository fileVersionRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final TeamArchiveGuard teamArchiveGuard;

    public FileVersionMutationService(FileRepository fileRepository,
                                      FileVersionRepository fileVersionRepository,
                                      AuditService auditService,
                                      ObjectMapper objectMapper,
                                      TeamArchiveGuard teamArchiveGuard) {
        this.fileRepository = fileRepository;
        this.fileVersionRepository = fileVersionRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.teamArchiveGuard = teamArchiveGuard;
    }

    /**
     * 명시된 version을 file의 current로 재지정한다 (옵션 A).
     *
     * @return 갱신된 (또는 멱등 no-op으로 그대로) {@link FileItem}
     * @throws IllegalArgumentException null 입력
     * @throws FileNotFoundException    파일이 active가 아니거나, version row 부재이거나, version.fileId 불일치
     */
    public FileItem restoreVersion(UUID fileId, UUID versionId, UUID actorId) {
        if (fileId == null) throw new IllegalArgumentException("fileId is required");
        if (versionId == null) throw new IllegalArgumentException("versionId is required");
        if (actorId == null) throw new IllegalArgumentException("actorId is required");

        FileItem file = fileRepository.lockByIdAndDeletedAtIsNull(fileId)
            .orElseThrow(() -> new FileNotFoundException("file not found or deleted: " + fileId));

        // spec §2.2 — archived 팀 파일의 버전 복원 차단 (DEPARTMENT는 가드 내부 no-op).
        // 대상 파일 fetch 후, version lookup/mutation 직전에 1회 호출.
        teamArchiveGuard.assertNotArchived(file.getScopeType(), file.getScopeId());

        FileVersion version = fileVersionRepository.findById(versionId)
            .orElseThrow(() -> new FileNotFoundException("version not found: " + versionId));

        if (!fileId.equals(version.getFileId())) {
            throw new FileNotFoundException(
                "version " + versionId + " does not belong to file " + fileId);
        }

        UUID currentVersionId = file.getCurrentVersionId();
        if (versionId.equals(currentVersionId)) {
            // 멱등: 이미 current — 같은 결과를 만드는 호출은 새 history line이 아니므로 audit emit X.
            return file;
        }

        file.setCurrentVersionId(versionId);
        file.setSizeBytes(version.getSizeBytes());
        file.setMimeType(version.getMimeType());
        file.setUpdatedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        FileItem saved = fileRepository.saveAndFlush(file);

        emitRestoreAudit(saved.getId(), currentVersionId, versionId, actorId);
        return saved;
    }

    private void emitRestoreAudit(UUID fileId, UUID oldVersionId, UUID newVersionId, UUID actorId) {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("versionId", oldVersionId);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("versionId", newVersionId);
        AuditEvent event = new AuditEvent(
            AuditEventType.VERSION_RESTORED,
            actorId,
            WebRequestContextHolder.currentIp(),
            WebRequestContextHolder.currentUserAgent(),
            AuditTargetType.FILE,
            fileId,
            toJson(before),
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
