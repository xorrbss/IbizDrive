package com.ibizdrive.trash;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import com.ibizdrive.file.FileItem;
import com.ibizdrive.file.FileNotFoundException;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.file.FileTestFixtures;
import com.ibizdrive.file.FileVersionRepository;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderNotFoundException;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.folder.FolderTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A8.2 — {@link TrashPurgeService} 단위 테스트. repository + audit 모킹으로
 * file/folder purge 흐름 (lock → versions cascade → hard delete → audit emit)을 검증.
 */
class TrashPurgeServiceTest {

    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PARENT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant DELETED_AT = Instant.parse("2026-04-30T10:00:00Z");

    private FileRepository fileRepository;
    private FolderRepository folderRepository;
    private FileVersionRepository fileVersionRepository;
    private AuditService auditService;
    private ObjectMapper objectMapper;
    private TrashPurgeService service;

    @BeforeEach
    void setUp() {
        fileRepository = mock(FileRepository.class);
        folderRepository = mock(FolderRepository.class);
        fileVersionRepository = mock(FileVersionRepository.class);
        auditService = mock(AuditService.class);
        objectMapper = new ObjectMapper();
        service = new TrashPurgeService(
            fileRepository, folderRepository, fileVersionRepository, auditService, objectMapper
        );
    }

    // ── purgeFile ───────────────────────────────────────────────────────

    @Test
    void purgeFile_happyPath_deletesVersionsAndEmitsFilePurgedAudit() {
        UUID fileId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID storageKey = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        FileItem file = FileTestFixtures.trashedFile(fileId, PARENT, ACTOR, "doc.pdf", DELETED_AT);

        when(fileRepository.lockByIdAndDeletedAtIsNotNull(fileId)).thenReturn(Optional.of(file));
        when(fileVersionRepository.findStorageKeysByFileIds(List.of(fileId)))
            .thenReturn(List.of(storageKey));
        when(fileVersionRepository.deleteByFileIds(List.of(fileId))).thenReturn(1);
        when(fileRepository.hardDeleteByIds(List.of(fileId))).thenReturn(1);

        service.purgeFile(fileId, ACTOR);

        // version + file 모두 cascade
        verify(fileVersionRepository).deleteByFileIds(List.of(fileId));
        verify(fileRepository).hardDeleteByIds(List.of(fileId));

        // audit 1건 — FILE_PURGED + FILE target + storageKey 보존
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent ev = captor.getValue();
        assertThat(ev.eventType()).isEqualTo(AuditEventType.FILE_PURGED);
        assertThat(ev.targetType()).isEqualTo(AuditTargetType.FILE);
        assertThat(ev.targetId()).isEqualTo(fileId);
        assertThat(ev.actorId()).isEqualTo(ACTOR);
        assertThat(ev.beforeState()).contains(storageKey.toString());
        assertThat(ev.beforeState()).contains("\"name\":\"doc.pdf\"");
    }

    @Test
    void purgeFile_notTrashed_throws404Mapping() {
        UUID fileId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        when(fileRepository.lockByIdAndDeletedAtIsNotNull(fileId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.purgeFile(fileId, ACTOR))
            .isInstanceOf(FileNotFoundException.class);

        // mutation/audit 발생 없음 — fail-fast
        verify(fileRepository, never()).hardDeleteByIds(any());
        verify(auditService, never()).record(any());
    }

    @Test
    void purgeFile_nullId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.purgeFile(null, ACTOR))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ── purgeFolder ─────────────────────────────────────────────────────

    @Test
    void purgeFolder_leafFolder_emitsRootAuditOnly() {
        UUID folderId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        Folder folder = FolderTestFixtures.trashedFolder(folderId, PARENT, ACTOR, "team", DELETED_AT);

        when(folderRepository.lockByIdAndDeletedAtIsNotNull(folderId)).thenReturn(Optional.of(folder));
        // 후손 없음
        when(folderRepository.findIdsByParentIdAndDeletedAtIsNotNull(folderId)).thenReturn(List.of());
        // 자기 자신에 file 없음
        when(fileRepository.findIdsByFolderIdAndDeletedAtIsNotNull(folderId)).thenReturn(List.of());
        // topo-sort용 parent 매핑
        when(folderRepository.findIdAndParentIdByIds(anyCollection()))
            .thenReturn(List.<Object[]>of(new Object[] { folderId, PARENT }));
        when(folderRepository.hardDeleteByIds(anyCollection())).thenReturn(1);

        service.purgeFolder(folderId, ACTOR);

        // 단일 folder hardDelete + version/file 호출 없음
        verify(folderRepository).hardDeleteByIds(anyCollection());
        verify(fileVersionRepository, never()).deleteByFileIds(any());
        verify(fileRepository, never()).hardDeleteByIds(any());

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        AuditEvent ev = captor.getValue();
        assertThat(ev.eventType()).isEqualTo(AuditEventType.FOLDER_PURGED);
        assertThat(ev.targetType()).isEqualTo(AuditTargetType.FOLDER);
        assertThat(ev.targetId()).isEqualTo(folderId);
        assertThat(ev.beforeState()).contains("\"descendantFolders\":0");
        assertThat(ev.beforeState()).contains("\"descendantFiles\":0");
    }

    @Test
    void purgeFolder_withDescendantsAndFiles_cascadeAndSingleAudit() throws Exception {
        UUID rootId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        UUID childId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        UUID fileId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        UUID storageKey = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

        Folder root = FolderTestFixtures.trashedFolder(rootId, PARENT, ACTOR, "team", DELETED_AT);
        when(folderRepository.lockByIdAndDeletedAtIsNotNull(rootId)).thenReturn(Optional.of(root));
        // root → child 후손 1개
        when(folderRepository.findIdsByParentIdAndDeletedAtIsNotNull(rootId)).thenReturn(List.of(childId));
        when(folderRepository.findIdsByParentIdAndDeletedAtIsNotNull(childId)).thenReturn(List.of());
        // child 안에 file 1개
        when(fileRepository.findIdsByFolderIdAndDeletedAtIsNotNull(rootId)).thenReturn(List.of());
        when(fileRepository.findIdsByFolderIdAndDeletedAtIsNotNull(childId)).thenReturn(List.of(fileId));
        when(fileVersionRepository.findStorageKeysByFileIds(List.of(fileId)))
            .thenReturn(List.of(storageKey));
        when(fileVersionRepository.deleteByFileIds(List.of(fileId))).thenReturn(1);
        when(fileRepository.hardDeleteByIds(List.of(fileId))).thenReturn(1);
        // topo-sort: child의 parent=root, root의 parent=PARENT(외부)
        when(folderRepository.findIdAndParentIdByIds(anyCollection()))
            .thenReturn(List.<Object[]>of(
                new Object[] { childId, rootId },
                new Object[] { rootId, PARENT }
            ));
        when(folderRepository.hardDeleteByIds(anyCollection())).thenReturn(2);

        service.purgeFolder(rootId, ACTOR);

        // file cascade 발생
        verify(fileVersionRepository).deleteByFileIds(List.of(fileId));
        verify(fileRepository).hardDeleteByIds(List.of(fileId));
        // folders는 leaf-first: child 먼저, root 나중
        ArgumentCaptor<List<UUID>> orderedCaptor = ArgumentCaptor.captor();
        verify(folderRepository).hardDeleteByIds(orderedCaptor.capture());
        List<UUID> ordered = orderedCaptor.getValue();
        assertThat(ordered).containsExactly(childId, rootId);

        // audit 1건 — root 기준
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(auditCaptor.capture());
        AuditEvent ev = auditCaptor.getValue();
        assertThat(ev.eventType()).isEqualTo(AuditEventType.FOLDER_PURGED);
        assertThat(ev.targetId()).isEqualTo(rootId);
        // before_state에 카운트와 storageKey 포함
        Map<String, Object> before = objectMapper.readValue(ev.beforeState(), Map.class);
        assertThat(before.get("descendantFolders")).isEqualTo(1);
        assertThat(before.get("descendantFiles")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<String> keys = (List<String>) before.get("storageKeys");
        assertThat(keys).containsExactly(storageKey.toString());
    }

    @Test
    void purgeFolder_notTrashed_throws() {
        UUID folderId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        when(folderRepository.lockByIdAndDeletedAtIsNotNull(folderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.purgeFolder(folderId, ACTOR))
            .isInstanceOf(FolderNotFoundException.class);

        verify(folderRepository, never()).hardDeleteByIds(any());
        verify(auditService, never()).record(any());
    }
}
