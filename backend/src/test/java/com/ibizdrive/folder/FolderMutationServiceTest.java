package com.ibizdrive.folder;

import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditTargetType;
import com.ibizdrive.audit.Audited;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FolderMutationServiceTest {

    @Mock
    private FolderRepository folderRepository;

    @Test
    void create_locksParent_normalizesName_andSavesFolder() {
        UUID parentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Folder parent = folder(parentId, null, "Parent", "parent", ownerId);
        FolderMutationService service = new FolderMutationService(folderRepository);

        when(folderRepository.lockActiveById(parentId)).thenReturn(Optional.of(parent));
        when(folderRepository.findActiveSibling(parentId, "sales plan")).thenReturn(Optional.empty());
        when(folderRepository.saveAndFlush(any(Folder.class))).thenAnswer(inv -> inv.getArgument(0));

        Folder created = service.create(parentId, "  Sales\u00A0Plan  ", ownerId);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getParentId()).isEqualTo(parentId);
        assertThat(created.getOwnerId()).isEqualTo(ownerId);
        assertThat(created.getName()).isEqualTo("Sales Plan");
        assertThat(created.getSlug()).isEqualTo("Sales Plan");
        assertThat(created.getNormalizedName()).isEqualTo("sales plan");

        InOrder order = inOrder(folderRepository);
        order.verify(folderRepository).lockActiveById(parentId);
        order.verify(folderRepository).findActiveSibling(parentId, "sales plan");
        order.verify(folderRepository).saveAndFlush(any(Folder.class));
    }

    @Test
    void create_rejectsMissingParent() {
        UUID parentId = UUID.randomUUID();
        FolderMutationService service = new FolderMutationService(folderRepository);

        when(folderRepository.lockActiveById(parentId)).thenReturn(Optional.empty());

        assertThrows(FolderNotFoundException.class,
            () -> service.create(parentId, "Sales", UUID.randomUUID()));
        verify(folderRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_rejectsActiveSiblingNameConflict() {
        UUID parentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Folder parent = folder(parentId, null, "Parent", "parent", ownerId);
        Folder conflict = folder(UUID.randomUUID(), parentId, "Sales", "sales", ownerId);
        FolderMutationService service = new FolderMutationService(folderRepository);

        when(folderRepository.lockActiveById(parentId)).thenReturn(Optional.of(parent));
        when(folderRepository.findActiveSibling(parentId, "sales")).thenReturn(Optional.of(conflict));

        assertThrows(FolderNameConflictException.class,
            () -> service.create(parentId, "Sales", ownerId));
        verify(folderRepository, never()).saveAndFlush(any());
    }

    @Test
    void rename_locksSource_normalizesName_andSavesFolder() {
        UUID folderId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Folder source = folder(folderId, parentId, "Old", "old", ownerId);
        FolderMutationService service = new FolderMutationService(folderRepository);

        when(folderRepository.lockActiveById(folderId)).thenReturn(Optional.of(source));
        when(folderRepository.findActiveSibling(parentId, "new name")).thenReturn(Optional.of(source));
        when(folderRepository.saveAndFlush(source)).thenReturn(source);

        Folder renamed = service.rename(folderId, "  New\u00A0Name  ");

        assertSame(source, renamed);
        assertThat(renamed.getName()).isEqualTo("New Name");
        assertThat(renamed.getSlug()).isEqualTo("New Name");
        assertThat(renamed.getNormalizedName()).isEqualTo("new name");
        assertThat(renamed.getUpdatedAt()).isNotNull();

        InOrder order = inOrder(folderRepository);
        order.verify(folderRepository).lockActiveById(folderId);
        order.verify(folderRepository).findActiveSibling(parentId, "new name");
        order.verify(folderRepository).saveAndFlush(source);
    }

    @Test
    void rename_rejectsMissingSourceFolder() {
        UUID folderId = UUID.randomUUID();
        FolderMutationService service = new FolderMutationService(folderRepository);

        when(folderRepository.lockActiveById(folderId)).thenReturn(Optional.empty());

        assertThrows(FolderNotFoundException.class, () -> service.rename(folderId, "New"));
        verify(folderRepository, never()).saveAndFlush(any());
    }

    @Test
    void rename_rejectsConflictWithAnotherActiveSibling() {
        UUID folderId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Folder source = folder(folderId, parentId, "Old", "old", ownerId);
        Folder conflict = folder(UUID.randomUUID(), parentId, "New", "new", ownerId);
        FolderMutationService service = new FolderMutationService(folderRepository);

        when(folderRepository.lockActiveById(folderId)).thenReturn(Optional.of(source));
        when(folderRepository.findActiveSibling(parentId, "new")).thenReturn(Optional.of(conflict));

        assertThrows(FolderNameConflictException.class, () -> service.rename(folderId, "New"));
        verify(folderRepository, never()).saveAndFlush(any());
    }

    @Test
    void createAndRename_areTransactionalAndAudited() throws NoSuchMethodException {
        Method create = FolderMutationService.class.getMethod("create", UUID.class, String.class, UUID.class);
        Method rename = FolderMutationService.class.getMethod("rename", UUID.class, String.class);

        assertThat(create.getAnnotation(Transactional.class)).isNotNull();
        Audited createAudit = create.getAnnotation(Audited.class);
        assertThat(createAudit.event()).isEqualTo(AuditEventType.FOLDER_CREATED);
        assertThat(createAudit.targetType()).isEqualTo(AuditTargetType.FOLDER);
        assertThat(createAudit.target()).isEqualTo("#result.id");

        assertThat(rename.getAnnotation(Transactional.class)).isNotNull();
        Audited renameAudit = rename.getAnnotation(Audited.class);
        assertThat(renameAudit.event()).isEqualTo(AuditEventType.FOLDER_RENAMED);
        assertThat(renameAudit.targetType()).isEqualTo(AuditTargetType.FOLDER);
        assertThat(renameAudit.target()).isEqualTo("#id");
    }

    private static Folder folder(UUID id, UUID parentId, String name, String normalizedName, UUID ownerId) {
        return new Folder(id, parentId, name, normalizedName, name, ownerId);
    }
}
