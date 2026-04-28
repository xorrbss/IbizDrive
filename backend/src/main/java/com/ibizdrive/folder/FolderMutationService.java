package com.ibizdrive.folder;

import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditTargetType;
import com.ibizdrive.audit.Audited;
import com.ibizdrive.common.normalize.NormalizeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class FolderMutationService {

    private final FolderRepository folderRepository;

    public FolderMutationService(FolderRepository folderRepository) {
        this.folderRepository = folderRepository;
    }

    @Transactional
    @Audited(event = AuditEventType.FOLDER_CREATED,
        targetType = AuditTargetType.FOLDER,
        target = "#result.id")
    public Folder create(UUID parentId, String name, UUID ownerId) {
        if (parentId != null) {
            folderRepository.lockActiveById(parentId)
                .orElseThrow(() -> new FolderNotFoundException(parentId));
        }

        String normalizedName = NormalizeUtil.normalizedNameForDedup(name);
        rejectConflict(parentId, normalizedName, null);

        String displayName = NormalizeUtil.normalizeFileName(name);
        Folder folder = new Folder(UUID.randomUUID(), parentId, displayName, normalizedName, displayName, ownerId);
        return folderRepository.saveAndFlush(folder);
    }

    @Transactional
    @Audited(event = AuditEventType.FOLDER_RENAMED,
        targetType = AuditTargetType.FOLDER,
        target = "#id")
    public Folder rename(UUID id, String name) {
        Folder folder = folderRepository.lockActiveById(id)
            .orElseThrow(() -> new FolderNotFoundException(id));

        String normalizedName = NormalizeUtil.normalizedNameForDedup(name);
        rejectConflict(folder.getParentId(), normalizedName, folder.getId());

        String displayName = NormalizeUtil.normalizeFileName(name);
        folder.rename(displayName, normalizedName, displayName);
        return folderRepository.saveAndFlush(folder);
    }

    private void rejectConflict(UUID parentId, String normalizedName, UUID selfId) {
        folderRepository.findActiveSibling(parentId, normalizedName)
            .filter(sibling -> selfId == null || !selfId.equals(sibling.getId()))
            .ifPresent(sibling -> {
                throw new FolderNameConflictException(parentId, normalizedName);
            });
    }
}
