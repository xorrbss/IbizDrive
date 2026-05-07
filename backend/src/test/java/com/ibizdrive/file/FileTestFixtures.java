package com.ibizdrive.file;

import java.time.Instant;
import java.util.UUID;

/**
 * 패키지 외부 테스트(예: trash, search)에서 {@link FileItem} 인스턴스를 생성할 수 있게 하는 fixture —
 * 엔티티의 protected no-arg 생성자에 접근하기 위해 본 클래스가 같은 패키지에 위치한다.
 *
 * <p>프로덕션 시각화/직렬화 경로와 무관 — 테스트에서만 호출되는 정적 팩토리.
 */
public final class FileTestFixtures {

    private FileTestFixtures() {}

    /**
     * 활성(미삭제) FileItem fixture — folder/items P1 정렬 테스트 등에서 호출.
     * mimeType은 "application/octet-stream" 기본, deletedAt/purgeAfter는 null.
     */
    public static FileItem activeFile(
        UUID id, UUID folderId, UUID ownerId, String name, long sizeBytes, Instant updatedAt
    ) {
        FileItem f = new FileItem();
        f.setId(id);
        f.setFolderId(folderId);
        f.setName(name);
        f.setNormalizedName(name);
        f.setOwnerId(ownerId);
        f.setSizeBytes(sizeBytes);
        f.setMimeType("application/octet-stream");
        f.setCreatedAt(updatedAt);
        f.setUpdatedAt(updatedAt);
        return f;
    }

    public static FileItem trashedFile(UUID id, UUID folderId, UUID ownerId, String name, Instant deletedAt) {
        FileItem f = new FileItem();
        f.setId(id);
        f.setFolderId(folderId);
        f.setOriginalFolderId(folderId);
        f.setName(name);
        f.setNormalizedName(name);
        f.setOwnerId(ownerId);
        f.setSizeBytes(0);
        f.setDeletedAt(deletedAt);
        f.setPurgeAfter(deletedAt.plusSeconds(60L * 60 * 24 * 30));
        f.setCreatedAt(deletedAt);
        f.setUpdatedAt(deletedAt);
        return f;
    }
}
