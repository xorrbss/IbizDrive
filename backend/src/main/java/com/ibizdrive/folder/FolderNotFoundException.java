package com.ibizdrive.folder;

import java.util.UUID;

public class FolderNotFoundException extends RuntimeException {

    private final UUID folderId;

    public FolderNotFoundException(UUID folderId) {
        super("Folder not found: " + folderId);
        this.folderId = folderId;
    }

    public UUID getFolderId() {
        return folderId;
    }
}
