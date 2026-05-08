package com.ibizdrive.admin.trash;

import com.ibizdrive.file.FileItem;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.trash.TrashCursor;
import com.ibizdrive.trash.TrashItemType;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Wave 2 T9 — admin global trash listing service (spec §4, plan §P2).
 *
 * <p>책임:
 * <ul>
 *   <li>q 정규화 (trim/lowercase/LIKE escape/wildcard wrap)</li>
 *   <li>limit clamping (DEFAULT 50, MAX 100)</li>
 *   <li>type 필터로 file/folder 양쪽 native query 조건부 호출 (limit+1 fetch)</li>
 *   <li>UUID set으로 owner email + originalParent name batch lookup (N+1 회피)</li>
 *   <li>{@code (deletedAt DESC, id DESC)} 통합 merge sort 후 limit으로 trim</li>
 *   <li>마지막 항목 기준 nextCursor 인코딩 (hasMore일 때만)</li>
 * </ul>
 */
@Service
public class AdminTrashService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final int MAX_Q_LENGTH = 200;

    private final AdminTrashRepository adminRepo;
    private final UserRepository userRepository;
    private final FolderRepository folderRepository;

    public AdminTrashService(AdminTrashRepository adminRepo,
                             UserRepository userRepository,
                             FolderRepository folderRepository) {
        this.adminRepo = adminRepo;
        this.userRepository = userRepository;
        this.folderRepository = folderRepository;
    }

    @Transactional(readOnly = true)
    public AdminTrashPage list(AdminTrashFilters filters, String cursorWire, Integer limitOpt) {
        if (filters.q() != null && filters.q().length() > MAX_Q_LENGTH) {
            throw new IllegalArgumentException("q too long");
        }
        // 날짜 범위 정합 — 양쪽 모두 적용된 경우만 검사. 동일하면 빈 결과 보장이 아니라
        // 의미가 모호하므로(상한 exclusive 정책), 잘못된 입력으로 거부.
        if (filters.deletedFromMin() != null
            && filters.deletedToMax() != null
            && !filters.deletedFromMin().isBefore(filters.deletedToMax())) {
            throw new IllegalArgumentException("deletedFrom must be before deletedTo");
        }

        TrashCursor cursor = TrashCursor.decode(cursorWire);
        Instant cursorAt = cursor != null ? cursor.deletedAt() : null;
        UUID cursorId = cursor != null ? cursor.id() : null;
        int limit = clampLimit(limitOpt);
        int fetchSize = limit + 1;

        String qPattern = normalizeQ(filters.q());
        UUID ownerId = filters.ownerId();
        TrashItemType type = filters.type();
        Instant deletedFromMin = filters.deletedFromMin();
        Instant deletedToMax = filters.deletedToMax();

        List<FileItem> files = (type == null || type == TrashItemType.FILE)
            ? adminRepo.findTrashedFilesAdminPage(
                qPattern, ownerId, deletedFromMin, deletedToMax, cursorAt, cursorId, fetchSize)
            : List.of();
        List<Folder> folders = (type == null || type == TrashItemType.FOLDER)
            ? adminRepo.findTrashedFoldersAdminPage(
                qPattern, ownerId, deletedFromMin, deletedToMax, cursorAt, cursorId, fetchSize)
            : List.of();

        // V10 — deletedBy도 동일 batch lookup에 합류 (1회 query 유지). owner와 동일 user pool이라
        // 별도 RPC로 분리하지 않는다.
        Set<UUID> userIds = new HashSet<>();
        Set<UUID> parentIds = new HashSet<>();
        for (FileItem f : files) {
            userIds.add(f.getOwnerId());
            if (f.getDeletedBy() != null) userIds.add(f.getDeletedBy());
            if (f.getOriginalFolderId() != null) parentIds.add(f.getOriginalFolderId());
        }
        for (Folder fd : folders) {
            userIds.add(fd.getOwnerId());
            if (fd.getDeletedBy() != null) userIds.add(fd.getDeletedBy());
            if (fd.getOriginalParentId() != null) parentIds.add(fd.getOriginalParentId());
        }

        Map<UUID, String> userEmailById = new HashMap<>();
        for (User u : userRepository.findAllById(userIds)) {
            userEmailById.put(u.getId(), u.getEmail());
        }
        Map<UUID, String> parentNameById = new HashMap<>();
        for (Folder p : folderRepository.findAllById(parentIds)) {
            parentNameById.put(p.getId(), p.getName());
        }

        List<AdminTrashItemDto> merged = new ArrayList<>(files.size() + folders.size());
        for (FileItem f : files) {
            UUID deletedBy = f.getDeletedBy();
            merged.add(new AdminTrashItemDto(
                f.getId(), f.getName(), TrashItemType.FILE,
                f.getDeletedAt(), f.getPurgeAfter(),
                f.getOwnerId(), userEmailById.get(f.getOwnerId()),
                f.getOriginalFolderId(),
                f.getOriginalFolderId() != null ? parentNameById.get(f.getOriginalFolderId()) : null,
                f.getSizeBytes(),
                deletedBy,
                deletedBy != null ? userEmailById.get(deletedBy) : null
            ));
        }
        for (Folder fd : folders) {
            UUID deletedBy = fd.getDeletedBy();
            merged.add(new AdminTrashItemDto(
                fd.getId(), fd.getName(), TrashItemType.FOLDER,
                fd.getDeletedAt(), fd.getPurgeAfter(),
                fd.getOwnerId(), userEmailById.get(fd.getOwnerId()),
                fd.getOriginalParentId(),
                fd.getOriginalParentId() != null ? parentNameById.get(fd.getOriginalParentId()) : null,
                null,
                deletedBy,
                deletedBy != null ? userEmailById.get(deletedBy) : null
            ));
        }

        merged.sort(
            Comparator.comparing(AdminTrashItemDto::deletedAt, Comparator.reverseOrder())
                .thenComparing(AdminTrashItemDto::id, Comparator.reverseOrder())
        );

        boolean hasMore = merged.size() > limit;
        List<AdminTrashItemDto> page = hasMore ? merged.subList(0, limit) : merged;
        String nextCursor = null;
        if (hasMore) {
            AdminTrashItemDto last = page.get(page.size() - 1);
            nextCursor = TrashCursor.encode(last.deletedAt(), last.id());
        }
        return new AdminTrashPage(List.copyOf(page), nextCursor);
    }

    private static String normalizeQ(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        String escaped = trimmed.toLowerCase()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static int clampLimit(Integer requested) {
        if (requested == null || requested <= 0) return DEFAULT_LIMIT;
        return Math.min(requested, MAX_LIMIT);
    }
}
