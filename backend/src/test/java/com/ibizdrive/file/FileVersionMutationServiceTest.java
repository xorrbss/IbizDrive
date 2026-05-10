package com.ibizdrive.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import com.ibizdrive.team.TeamArchiveGuard;
import com.ibizdrive.team.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M-RP.2.2 — {@link FileVersionMutationService} 단위 테스트 (mock-only).
 *
 * <p>옵션 A (RP-1, ADR #39) 검증 매트릭스:
 * <ul>
 *   <li>정상 복원 → currentVersionId 변경 + {@code VERSION_RESTORED} 1건 (before=oldId, after=newId)</li>
 *   <li>같은 versionId 재호출 → 멱등 no-op + audit emit X</li>
 *   <li>cross-file version → 404 {@link FileNotFoundException}</li>
 *   <li>file inactive (lock 결과 부재) → 404</li>
 *   <li>version row 부재 → 404</li>
 *   <li>null 입력 → IllegalArgumentException</li>
 * </ul>
 */
class FileVersionMutationServiceTest {

    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FILE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID VERSION_OLD = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID VERSION_NEW = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID FOLDER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private FileRepository fileRepository;
    private FileVersionRepository fileVersionRepository;
    private AuditService auditService;
    private FileVersionMutationService service;

    @BeforeEach
    void setUp() {
        fileRepository = mock(FileRepository.class);
        fileVersionRepository = mock(FileVersionRepository.class);
        auditService = mock(AuditService.class);
        // 기존 매트릭스는 DEPARTMENT scope fixture만 사용 — TeamArchiveGuard는 가드 내부에서 no-op.
        // T5 archived-team 회귀는 FileUploadArchivedTeamGuardTest가 별도로 검증.
        TeamArchiveGuard teamArchiveGuard = new TeamArchiveGuard(mock(TeamRepository.class));
        service = new FileVersionMutationService(
            fileRepository, fileVersionRepository, auditService, new ObjectMapper(), teamArchiveGuard);
    }

    @Test
    void restoreVersion_setsCurrentAndEmitsAudit() {
        FileItem file = newFile(FILE_ID, FOLDER_ID, "doc.txt", VERSION_OLD);
        // file row currently denormalized from old version (4MB pdf 가정)
        file.setSizeBytes(4_000_000L);
        file.setMimeType("application/pdf");
        FileVersion target = newVersion(VERSION_NEW, FILE_ID);
        // target version은 1KB text/plain — restore 후 file row가 이 값으로 동기화돼야 한다.
        target.setSizeBytes(1024L);
        target.setMimeType("text/plain");
        when(fileRepository.lockByIdAndDeletedAtIsNull(FILE_ID)).thenReturn(Optional.of(file));
        when(fileVersionRepository.findById(VERSION_NEW)).thenReturn(Optional.of(target));
        when(fileRepository.saveAndFlush(file)).thenReturn(file);

        FileItem result = service.restoreVersion(FILE_ID, VERSION_NEW, ACTOR);

        assertThat(result.getCurrentVersionId()).isEqualTo(VERSION_NEW);
        // denormalized 메타 동기화 — FileUploadService:214-217 invariant 보존 (ADR #39).
        assertThat(result.getSizeBytes()).isEqualTo(1024L);
        assertThat(result.getMimeType()).isEqualTo("text/plain");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.eventType()).isEqualTo(AuditEventType.VERSION_RESTORED);
        assertThat(event.actorId()).isEqualTo(ACTOR);
        assertThat(event.targetType()).isEqualTo(AuditTargetType.FILE);
        assertThat(event.targetId()).isEqualTo(FILE_ID);
        assertThat(event.beforeState()).contains(VERSION_OLD.toString());
        assertThat(event.afterState()).contains(VERSION_NEW.toString());
    }

    @Test
    void restoreVersion_alreadyCurrent_isIdempotent_noAudit() {
        FileItem file = newFile(FILE_ID, FOLDER_ID, "doc.txt", VERSION_NEW);
        FileVersion target = newVersion(VERSION_NEW, FILE_ID);
        when(fileRepository.lockByIdAndDeletedAtIsNull(FILE_ID)).thenReturn(Optional.of(file));
        when(fileVersionRepository.findById(VERSION_NEW)).thenReturn(Optional.of(target));

        FileItem result = service.restoreVersion(FILE_ID, VERSION_NEW, ACTOR);

        assertThat(result).isSameAs(file);
        assertThat(result.getCurrentVersionId()).isEqualTo(VERSION_NEW);
        verify(fileRepository, never()).saveAndFlush(any(FileItem.class));
        verify(auditService, never()).record(any(AuditEvent.class));
    }

    @Test
    void restoreVersion_currentVersionIdNullToNewId_emitsAuditWithNullBefore() {
        FileItem file = newFile(FILE_ID, FOLDER_ID, "orphan.txt", null);
        FileVersion target = newVersion(VERSION_NEW, FILE_ID);
        when(fileRepository.lockByIdAndDeletedAtIsNull(FILE_ID)).thenReturn(Optional.of(file));
        when(fileVersionRepository.findById(VERSION_NEW)).thenReturn(Optional.of(target));
        when(fileRepository.saveAndFlush(file)).thenReturn(file);

        service.restoreVersion(FILE_ID, VERSION_NEW, ACTOR);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        // before.versionId == null이 직렬화되어 포함되는지 확인 — JSON literal "null" 노출.
        assertThat(captor.getValue().beforeState()).contains("null");
        assertThat(captor.getValue().afterState()).contains(VERSION_NEW.toString());
    }

    @Test
    void restoreVersion_fileInactive_throwsFileNotFound_noAudit() {
        when(fileRepository.lockByIdAndDeletedAtIsNull(FILE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restoreVersion(FILE_ID, VERSION_NEW, ACTOR))
            .isInstanceOf(FileNotFoundException.class);

        verify(auditService, never()).record(any(AuditEvent.class));
    }

    @Test
    void restoreVersion_versionMissing_throwsFileNotFound_noAudit() {
        FileItem file = newFile(FILE_ID, FOLDER_ID, "doc.txt", VERSION_OLD);
        when(fileRepository.lockByIdAndDeletedAtIsNull(FILE_ID)).thenReturn(Optional.of(file));
        when(fileVersionRepository.findById(VERSION_NEW)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restoreVersion(FILE_ID, VERSION_NEW, ACTOR))
            .isInstanceOf(FileNotFoundException.class)
            .hasMessageContaining("version not found");

        verify(auditService, never()).record(any(AuditEvent.class));
    }

    @Test
    void restoreVersion_crossFileVersion_throwsFileNotFound_noAudit() {
        FileItem file = newFile(FILE_ID, FOLDER_ID, "doc.txt", VERSION_OLD);
        UUID otherFileId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        FileVersion stray = newVersion(VERSION_NEW, otherFileId);
        when(fileRepository.lockByIdAndDeletedAtIsNull(FILE_ID)).thenReturn(Optional.of(file));
        when(fileVersionRepository.findById(VERSION_NEW)).thenReturn(Optional.of(stray));

        assertThatThrownBy(() -> service.restoreVersion(FILE_ID, VERSION_NEW, ACTOR))
            .isInstanceOf(FileNotFoundException.class)
            .hasMessageContaining("does not belong to file");

        verify(fileRepository, never()).saveAndFlush(any(FileItem.class));
        verify(auditService, never()).record(any(AuditEvent.class));
    }

    @Test
    void restoreVersion_nullArgs_throwIllegalArgument() {
        assertThatThrownBy(() -> service.restoreVersion(null, VERSION_NEW, ACTOR))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.restoreVersion(FILE_ID, null, ACTOR))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.restoreVersion(FILE_ID, VERSION_NEW, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ───── helpers ─────────────────────────────────────────────────────

    private FileItem newFile(UUID id, UUID folderId, String name, UUID currentVersionId) {
        FileItem f = new FileItem();
        f.setId(id);
        f.setFolderId(folderId);
        f.setName(name);
        f.setNormalizedName(name.toLowerCase());
        f.setOwnerId(ACTOR);
        f.setSizeBytes(0L);
        f.setCurrentVersionId(currentVersionId);
        Instant now = Instant.now();
        f.setCreatedAt(now);
        f.setUpdatedAt(now);
        f.assignScope(com.ibizdrive.folder.ScopeType.DEPARTMENT, java.util.UUID.randomUUID());
        return f;
    }

    private FileVersion newVersion(UUID id, UUID fileId) {
        FileVersion v = new FileVersion();
        v.setId(id);
        v.setFileId(fileId);
        v.setVersionNumber(1);
        v.setStorageKey(UUID.randomUUID());
        v.setSizeBytes(1024L);
        v.setChecksumSha256("0".repeat(64));
        v.setMimeType("text/plain");
        v.setScanStatus(VersionScanStatus.CLEAN);
        v.setUploadedBy(ACTOR);
        v.setUploadedAt(Instant.now());
        return v;
    }
}
