package com.ibizdrive.folder;

import java.util.UUID;

public class FolderNameConflictException extends RuntimeException {

    private final UUID parentId;
    private final String normalizedName;

    public FolderNameConflictException(UUID parentId, String normalizedName) {
        super("Folder name conflict: " + normalizedName);
        this.parentId = parentId;
        this.normalizedName = normalizedName;
    }

    public UUID getParentId() {
        return parentId;
    }

    public String getNormalizedName() {
        return normalizedName;
    }
}
