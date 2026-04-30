package com.ibizdrive.folder;

import java.time.Instant;
import java.util.UUID;

/**
 * 패키지 외부 테스트(예: trash, search)에서 {@link Folder} 인스턴스를 생성할 수 있게 하는 fixture —
 * 엔티티의 protected no-arg 생성자에 접근하기 위해 본 클래스가 같은 패키지에 위치한다.
 */
public final class FolderTestFixtures {

    private FolderTestFixtures() {}

    public static Folder trashedFolder(UUID id, UUID parentId, UUID ownerId, String name, Instant deletedAt) {
        Folder fd = new Folder();
        fd.setId(id);
        fd.setParentId(parentId);
        fd.setOriginalParentId(parentId);
        fd.setName(name);
        fd.setNormalizedName(name);
        fd.setSlug(name);
        fd.setOwnerId(ownerId);
        fd.setAuditLevel("standard");
        fd.setDeletedAt(deletedAt);
        fd.setPurgeAfter(deletedAt.plusSeconds(60L * 60 * 24 * 30));
        fd.setCreatedAt(deletedAt);
        fd.setUpdatedAt(deletedAt);
        return fd;
    }
}
