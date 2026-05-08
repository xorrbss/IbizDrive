package com.ibizdrive.admin.trash;

import com.ibizdrive.file.FileItem;
import com.ibizdrive.file.FileMutationService;
import com.ibizdrive.file.FileNameConflictException;
import com.ibizdrive.file.FileNotFoundException;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderMutationService;
import com.ibizdrive.folder.FolderNotFoundException;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.folder.FolderRestoreConflictException;
import com.ibizdrive.trash.TrashCursor;
import com.ibizdrive.trash.TrashItemType;
import com.ibizdrive.trash.TrashPurgeService;
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
    /** bulk endpoint cap. q 길이 cap과 정합, 단일 request lock window 보호 (spec §3.7). */
    static final int BULK_MAX_ITEMS = 200;

    private final AdminTrashRepository adminRepo;
    private final UserRepository userRepository;
    private final FolderRepository folderRepository;
    private final FileMutationService fileMutationService;
    private final FolderMutationService folderMutationService;
    private final TrashPurgeService trashPurgeService;

    public AdminTrashService(AdminTrashRepository adminRepo,
                             UserRepository userRepository,
                             FolderRepository folderRepository,
                             FileMutationService fileMutationService,
                             FolderMutationService folderMutationService,
                             TrashPurgeService trashPurgeService) {
        this.adminRepo = adminRepo;
        this.userRepository = userRepository;
        this.folderRepository = folderRepository;
        this.fileMutationService = fileMutationService;
        this.folderMutationService = folderMutationService;
        this.trashPurgeService = trashPurgeService;
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

        // folder DTO sizeBytes — page에 노출된 trashed folder들의 subtree size를 한 번에 조회.
        // file은 자기 size_bytes 그대로 사용. 빈 폴더는 0 (CTE의 COALESCE).
        Map<UUID, Long> folderSubtreeSizes = subtreeSizesFor(folders);

        // originalParentPath — 부모(originalFolderId/originalParentId)들에 대해 root까지의 경로
        // 일괄 조회 (full-path-resolve follow-up). 부모가 root인 폴더는 "/<name>", 깊으면
        // "/<g1>/.../<parent>". chain 종착 못한(데이터 corruption) 부모는 결과 누락 → 호출자가
        // originalParentName 단일 segment를 fallback으로 가질 수 있게 함.
        Map<UUID, String> parentPathById = parentPathsFor(parentIds);

        List<AdminTrashItemDto> merged = new ArrayList<>(files.size() + folders.size());
        for (FileItem f : files) {
            UUID deletedBy = f.getDeletedBy();
            UUID parentId = f.getOriginalFolderId();
            merged.add(new AdminTrashItemDto(
                f.getId(), f.getName(), TrashItemType.FILE,
                f.getDeletedAt(), f.getPurgeAfter(),
                f.getOwnerId(), userEmailById.get(f.getOwnerId()),
                parentId,
                parentId != null ? parentNameById.get(parentId) : null,
                parentId != null ? parentPathById.get(parentId) : null,
                f.getSizeBytes(),
                deletedBy,
                deletedBy != null ? userEmailById.get(deletedBy) : null
            ));
        }
        for (Folder fd : folders) {
            UUID deletedBy = fd.getDeletedBy();
            UUID parentId = fd.getOriginalParentId();
            merged.add(new AdminTrashItemDto(
                fd.getId(), fd.getName(), TrashItemType.FOLDER,
                fd.getDeletedAt(), fd.getPurgeAfter(),
                fd.getOwnerId(), userEmailById.get(fd.getOwnerId()),
                parentId,
                parentId != null ? parentNameById.get(parentId) : null,
                parentId != null ? parentPathById.get(parentId) : null,
                folderSubtreeSizes.get(fd.getId()),
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

    /**
     * folder DTO {@code sizeBytes}를 채우기 위한 subtree size batch lookup.
     * 빈 입력은 short-circuit (Postgres {@code IN ()} 문법 오류 방지). repo 결과의
     * {@code Object[1]}은 Postgres NUMERIC → JDBC {@link Number} → {@link Number#longValue()}로
     * Long 변환 (안전한 widening, NULL은 CTE의 {@code COALESCE(..., 0)}이 차단).
     */
    private Map<UUID, Long> subtreeSizesFor(List<Folder> folders) {
        if (folders.isEmpty()) return Map.of();
        Set<UUID> rootIds = new HashSet<>(folders.size());
        for (Folder fd : folders) rootIds.add(fd.getId());
        Map<UUID, Long> sizes = new HashMap<>(rootIds.size());
        for (Object[] row : adminRepo.findFolderSubtreeSizes(rootIds)) {
            sizes.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return sizes;
    }

    /**
     * originalParentPath batch lookup — full-path-resolve follow-up.
     * 빈 입력은 short-circuit. 결과 누락(부모 chain 종착 실패 — 데이터 corruption 또는 depth
     * 100 초과) 시 해당 parent는 map에 없으며 호출자가 fallback({@code originalParentName})으로
     * 처리한다.
     */
    private Map<UUID, String> parentPathsFor(Set<UUID> parentIds) {
        if (parentIds.isEmpty()) return Map.of();
        Map<UUID, String> paths = new HashMap<>(parentIds.size());
        for (Object[] row : adminRepo.findFolderAncestorPaths(parentIds)) {
            paths.put((UUID) row[0], (String) row[1]);
        }
        return paths;
    }

    // ──────────────────────────────────────────────────────────────────
    // bulk restore/purge — Wave 2 T9 follow-up (spec §3)
    // ──────────────────────────────────────────────────────────────────

    /**
     * 여러 휴지통 항목을 한 번에 복원 또는 영구삭제 (spec §3).
     *
     * <p><b>트랜잭션 모델</b>: 본 메서드는 트랜잭션을 열지 않는다. 항목별로 단건
     * mutation service({@link FileMutationService#restore}, {@link FolderMutationService#restore},
     * {@link TrashPurgeService#purgeFile}/{@link TrashPurgeService#purgeFolder})가
     * 자기 트랜잭션을 가진다 — 한 항목 실패가 다른 항목 처리를 막지 않는 부분 실패 모델
     * (spec §3.4 / §5.2).
     *
     * <p><b>예외 → wire 문자열 매핑</b>: per-item 단건 service의 도메인 예외는
     * {@code failed[].error}의 안정적 enum-like 문자열로 변환 (docs/02 §8 정합).
     *
     * @param action {@code "restore"} | {@code "purge"} (그 외 → IAE → 글로벌 핸들러 400)
     * @param items  1..200개. 0 또는 201+ → IAE → 400
     * @param actorId SecurityContext에서 추출한 ADMIN user id — 단건 service에 전파, audit
     *                emit 시 actor_id로 기록되며 V10 {@code deleted_by} restore 흐름에서 클리어됨
     */
    public AdminTrashBulkResponseDto bulk(String action,
                                          List<AdminTrashBulkRequestDto.Item> items,
                                          UUID actorId) {
        if (action == null) {
            throw new IllegalArgumentException("action is required");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items must be 1..200");
        }
        if (items.size() > BULK_MAX_ITEMS) {
            throw new IllegalArgumentException("items must be 1..200");
        }
        BulkAction parsedAction = BulkAction.from(action);

        List<AdminTrashBulkResponseDto.Item> succeeded = new ArrayList<>();
        List<AdminTrashBulkResponseDto.FailedItem> failed = new ArrayList<>();

        for (AdminTrashBulkRequestDto.Item item : items) {
            // 항목 자체의 형태 검증(type/id null) 실패는 failed로 누적 — 한 항목의 잘못된 입력이
            // 전체 batch를 막지 않는다 (부분 실패 모델). action/cap 검증과는 분리.
            if (item == null || item.type() == null || item.id() == null) {
                failed.add(new AdminTrashBulkResponseDto.FailedItem(
                    null, item == null ? null : item.id(), "INVALID_ITEM"));
                continue;
            }
            TrashItemType type;
            try {
                type = TrashItemType.from(item.type());
            } catch (IllegalArgumentException ex) {
                failed.add(new AdminTrashBulkResponseDto.FailedItem(
                    null, item.id(), "INVALID_TYPE"));
                continue;
            }
            try {
                dispatch(parsedAction, type, item.id(), actorId);
                succeeded.add(new AdminTrashBulkResponseDto.Item(type, item.id()));
            } catch (FileNotFoundException | FolderNotFoundException ex) {
                failed.add(new AdminTrashBulkResponseDto.FailedItem(type, item.id(), "NOT_FOUND"));
            } catch (FileNameConflictException | FolderRestoreConflictException ex) {
                failed.add(new AdminTrashBulkResponseDto.FailedItem(type, item.id(), "NAME_CONFLICT"));
            }
            // 그 외 RuntimeException은 의도적으로 잡지 않음 — 인프라/프로그래밍 오류는
            // 글로벌 핸들러에서 500으로 처리되어야 한다(부분 실패 모델은 도메인 예외 한정).
        }

        return new AdminTrashBulkResponseDto(succeeded, failed);
    }

    private void dispatch(BulkAction action, TrashItemType type, UUID id, UUID actorId) {
        switch (action) {
            case RESTORE -> {
                switch (type) {
                    case FILE -> fileMutationService.restore(id, actorId);
                    case FOLDER -> folderMutationService.restore(id, actorId);
                }
            }
            case PURGE -> {
                switch (type) {
                    case FILE -> trashPurgeService.purgeFile(id, actorId);
                    case FOLDER -> trashPurgeService.purgeFolder(id, actorId);
                }
            }
        }
    }

    /**
     * bulk action wire 문자열 → 내부 enum. 그 외 값은 IAE → 글로벌 핸들러 400.
     */
    enum BulkAction {
        RESTORE, PURGE;

        static BulkAction from(String wire) {
            if ("restore".equals(wire)) return RESTORE;
            if ("purge".equals(wire)) return PURGE;
            throw new IllegalArgumentException("invalid action (expected 'restore' or 'purge'): " + wire);
        }
    }
}
