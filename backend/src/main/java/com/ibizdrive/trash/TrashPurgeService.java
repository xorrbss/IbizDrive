package com.ibizdrive.trash;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import com.ibizdrive.audit.WebRequestContextHolder;
import com.ibizdrive.file.FileItem;
import com.ibizdrive.file.FileNotFoundException;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.file.FileVersionRepository;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderNotFoundException;
import com.ibizdrive.folder.FolderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A8.2 — {@code DELETE /api/trash/:type/:id} manual purge 본체 (ADR #32).
 *
 * <p><b>책임</b>: 휴지통의 file/folder row를 단건(또는 folder 단위 cascade) hard delete + per-row audit
 * ({@link AuditEventType#FILE_PURGED} / {@link AuditEventType#FOLDER_PURGED}) 발행. A7
 * {@link com.ibizdrive.purge.HardPurgeService}는 batch 자동 발행(`SYSTEM_PURGE_EXECUTED`) 트랙 —
 * 본 service는 사용자 트리거 단건 트랙으로 enum/audit 정책이 다르다.
 *
 * <p><b>트랜잭션</b>: 단일 {@code @Transactional}. 예외 시 전체 rollback → audit 미발행. AuditService는
 * REQUIRES_NEW로 별도 트랜잭션에 INSERT하므로 commit된 audit는 회수 불가.
 *
 * <p><b>SSE emission deferred (ADR #32)</b>: SSE infra (`EventBus`/`SseEmitter`) 부재 — A8은 audit-only.
 * 인프라 milestone에서 1줄 추가로 활성화 예정. 본 service의 commit 시점에 hook 주석 표시.
 *
 * <p><b>folder cascade 정책 (A6 root-only audit 패턴 재사용)</b>: cascade 전체에 root
 * {@code FOLDER_PURGED} 1건만 발행. after_state에 descendant 카운트 + storageKeys 요약 보존.
 */
@Service
public class TrashPurgeService {

    private static final Logger log = LoggerFactory.getLogger(TrashPurgeService.class);

    /** orphan storage_key 수집 cap — A7과 동형. audit_log JSONB row 비대화 회피. */
    private static final int ORPHAN_STORAGE_KEYS_CAP = 1000;
    /** cascade 후손 수 안전 한도 — 정상 운영에서 도달하지 않음 (A6 MAX_CASCADE_NODES와 동형). */
    private static final int MAX_CASCADE_NODES = 100_000;

    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final FileVersionRepository fileVersionRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public TrashPurgeService(FileRepository fileRepository,
                             FolderRepository folderRepository,
                             FileVersionRepository fileVersionRepository,
                             AuditService auditService,
                             ObjectMapper objectMapper) {
        this.fileRepository = fileRepository;
        this.folderRepository = folderRepository;
        this.fileVersionRepository = fileVersionRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    // ──────────────────────────────────────────────────────────────────
    // file purge
    // ──────────────────────────────────────────────────────────────────

    /**
     * 휴지통 file 단건 hard delete + {@code FILE_PURGED} audit.
     *
     * <p>순서: lock → version storage_keys 수집 → file_versions delete → files hard delete → audit.
     * {@code current_version_id} self-FK는 V5에서 {@code DEFERRABLE INITIALLY DEFERRED} (docs/02 §2.5)
     * 이므로 트랜잭션 내 순서 자유.
     *
     * @throws FileNotFoundException 활성 파일이거나 미존재 (휴지통 row 부재)
     */
    @Transactional
    public void purgeFile(UUID fileId, UUID actorId) {
        if (fileId == null) throw new IllegalArgumentException("fileId is required");

        FileItem file = fileRepository.lockByIdAndDeletedAtIsNotNull(fileId)
            .orElseThrow(() -> new FileNotFoundException("trashed file not found: " + fileId));

        List<UUID> storageKeysAll = fileVersionRepository.findStorageKeysByFileIds(List.of(fileId));
        List<String> storageKeys = new ArrayList<>(Math.min(storageKeysAll.size(), ORPHAN_STORAGE_KEYS_CAP));
        boolean truncated = storageKeysAll.size() > ORPHAN_STORAGE_KEYS_CAP;
        int sliceEnd = truncated ? ORPHAN_STORAGE_KEYS_CAP : storageKeysAll.size();
        for (int i = 0; i < sliceEnd; i++) {
            storageKeys.add(storageKeysAll.get(i).toString());
        }

        fileVersionRepository.deleteByFileIds(List.of(fileId));
        fileRepository.hardDeleteByIds(List.of(fileId));

        Map<String, Object> beforeState = new LinkedHashMap<>();
        beforeState.put("name", file.getName());
        beforeState.put("folderId", file.getFolderId());
        beforeState.put("originalFolderId", file.getOriginalFolderId());
        beforeState.put("deletedAt", file.getDeletedAt() == null ? null : file.getDeletedAt().toString());
        beforeState.put("storageKeys", storageKeys);
        beforeState.put("storageKeysTruncated", truncated);

        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("purgedAt", Instant.now().toString());

        emitAudit(AuditEventType.FILE_PURGED, AuditTargetType.FILE, fileId, actorId, beforeState, afterState);
        // TODO: SSE FILE_PURGED emit — SSE 인프라 milestone에서 1줄 활성화 (ADR #32)
        log.info("manual purge file={} actor={} versions={} truncated={}",
            fileId, actorId, storageKeysAll.size(), truncated);
    }

    // ──────────────────────────────────────────────────────────────────
    // folder purge (cascade)
    // ──────────────────────────────────────────────────────────────────

    /**
     * 휴지통 folder 단건 + 후손 cascade hard delete + root {@code FOLDER_PURGED} audit 1건.
     *
     * <p>cascade 순서 (FK 정합):
     * <ol>
     *   <li>root soft-deleted folder lock</li>
     *   <li>BFS — 후손 soft-deleted folder id 수집</li>
     *   <li>해당 folder들에 속한 soft-deleted file id 수집</li>
     *   <li>file_versions storage_keys 수집(audit before_state) → file_versions delete → files hard delete</li>
     *   <li>folders leaf-first 위상정렬 → folders hard delete</li>
     *   <li>audit FOLDER_PURGED (root 기준 1건)</li>
     * </ol>
     *
     * <p>후손 active row가 corruption으로 남아 있으면 (4) 또는 (5)에서 FK
     * {@code ON DELETE RESTRICT} 위반 → 트랜잭션 rollback → 500 (의도적 fail-fast, 데이터 corruption
     * 감지 경로). 정상 운영에서는 A6 cascade soft-delete가 트리 전체를 함께 soft-delete 했으므로 발생 없음.
     *
     * @throws FolderNotFoundException 활성 폴더이거나 미존재 (휴지통 row 부재)
     */
    @Transactional
    public void purgeFolder(UUID folderId, UUID actorId) {
        if (folderId == null) throw new IllegalArgumentException("folderId is required");

        Folder root = folderRepository.lockByIdAndDeletedAtIsNotNull(folderId)
            .orElseThrow(() -> new FolderNotFoundException("trashed folder not found: " + folderId));

        // 1) 후손 folder id 수집 (root 미포함, BFS)
        List<UUID> descendantFolderIds = collectDeletedDescendantFolderIds(root.getId());

        // 2) 모든 folder id에서 soft-deleted file id 수집
        List<UUID> allFolderIds = new ArrayList<>(descendantFolderIds.size() + 1);
        allFolderIds.add(root.getId());
        allFolderIds.addAll(descendantFolderIds);

        List<UUID> fileIds = new ArrayList<>();
        for (UUID fid : allFolderIds) {
            fileIds.addAll(fileRepository.findIdsByFolderIdAndDeletedAtIsNotNull(fid));
            if (fileIds.size() > MAX_CASCADE_NODES) {
                throw new IllegalStateException(
                    "cascade file count exceeded safety limit at folder " + fid);
            }
        }

        // 3) storage_keys 수집 + version cascade
        List<String> storageKeys = new ArrayList<>();
        boolean storageKeysTruncated = false;
        if (!fileIds.isEmpty()) {
            List<UUID> allKeys = fileVersionRepository.findStorageKeysByFileIds(fileIds);
            int cap = Math.min(allKeys.size(), ORPHAN_STORAGE_KEYS_CAP);
            for (int i = 0; i < cap; i++) {
                storageKeys.add(allKeys.get(i).toString());
            }
            storageKeysTruncated = allKeys.size() > ORPHAN_STORAGE_KEYS_CAP;
            fileVersionRepository.deleteByFileIds(fileIds);
            fileRepository.hardDeleteByIds(fileIds);
        }

        // 4) folders leaf-first 위상정렬 후 hard delete
        List<UUID> orderedFolders = leafFirstOrder(allFolderIds);
        if (!orderedFolders.isEmpty()) {
            folderRepository.hardDeleteByIds(orderedFolders);
        }

        // 5) audit (root 기준)
        Map<String, Object> beforeState = new LinkedHashMap<>();
        beforeState.put("name", root.getName());
        beforeState.put("parentId", root.getParentId());
        beforeState.put("originalParentId", root.getOriginalParentId());
        beforeState.put("deletedAt", root.getDeletedAt() == null ? null : root.getDeletedAt().toString());
        beforeState.put("descendantFolders", descendantFolderIds.size());
        beforeState.put("descendantFiles", fileIds.size());
        beforeState.put("storageKeys", storageKeys);
        beforeState.put("storageKeysTruncated", storageKeysTruncated);

        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("purgedAt", Instant.now().toString());
        afterState.put("descendantFolders", descendantFolderIds.size());
        afterState.put("descendantFiles", fileIds.size());

        emitAudit(AuditEventType.FOLDER_PURGED, AuditTargetType.FOLDER, root.getId(), actorId,
            beforeState, afterState);
        // TODO: SSE FOLDER_PURGED emit — SSE 인프라 milestone에서 1줄 활성화 (ADR #32)
        log.info("manual purge folder={} actor={} descendants={}+{} truncated={}",
            folderId, actorId, descendantFolderIds.size(), fileIds.size(), storageKeysTruncated);
    }

    // ──────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────

    /** soft-deleted root에서 BFS로 soft-deleted 후손 folder id 수집 (root 미포함). */
    private List<UUID> collectDeletedDescendantFolderIds(UUID rootId) {
        List<UUID> descendants = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        visited.add(rootId);
        Deque<UUID> frontier = new ArrayDeque<>();
        frontier.add(rootId);

        while (!frontier.isEmpty()) {
            UUID current = frontier.pollFirst();
            List<UUID> children = folderRepository.findIdsByParentIdAndDeletedAtIsNotNull(current);
            for (UUID childId : children) {
                if (!visited.add(childId)) continue;
                descendants.add(childId);
                frontier.addLast(childId);
                if (descendants.size() > MAX_CASCADE_NODES) {
                    throw new IllegalStateException(
                        "cascade descendant count exceeded safety limit at " + childId);
                }
            }
        }
        return descendants;
    }

    /**
     * Kahn's algorithm leaf-first — A7 {@link com.ibizdrive.purge.HardPurgeService#leafFirstOrder}와
     * 동형. 본 service는 단건 트리거이므로 batch 외부 parent도 수용 (root의 parentId는 active 또는
     * 다른 trash root 일 수 있음 — 모두 leaf 카운트 0으로 간주하면 충분).
     */
    private List<UUID> leafFirstOrder(Collection<UUID> ids) {
        Set<UUID> idSet = new HashSet<>(ids);
        Map<UUID, UUID> parentInBatch = new HashMap<>(idSet.size() * 2);
        Map<UUID, Integer> childCount = new HashMap<>(idSet.size() * 2);
        for (UUID id : idSet) {
            parentInBatch.put(id, null);
            childCount.put(id, 0);
        }
        for (Object[] row : folderRepository.findIdAndParentIdByIds(idSet)) {
            UUID id = (UUID) row[0];
            UUID parent = (UUID) row[1];
            if (parent != null && idSet.contains(parent)) {
                parentInBatch.put(id, parent);
                childCount.merge(parent, 1, Integer::sum);
            }
        }
        List<UUID> ordered = new ArrayList<>(idSet.size());
        ArrayList<UUID> queue = new ArrayList<>();
        for (Map.Entry<UUID, Integer> e : childCount.entrySet()) {
            if (e.getValue() == 0) queue.add(e.getKey());
        }
        while (!queue.isEmpty()) {
            UUID leaf = queue.remove(queue.size() - 1);
            ordered.add(leaf);
            UUID parent = parentInBatch.get(leaf);
            if (parent != null) {
                int remain = childCount.merge(parent, -1, Integer::sum);
                if (remain == 0) queue.add(parent);
            }
        }
        if (ordered.size() < idSet.size()) {
            log.warn("manual folder purge topo-sort: {} folder nodes formed cycle, skipping",
                idSet.size() - ordered.size());
        }
        return ordered;
    }

    private void emitAudit(AuditEventType eventType,
                           AuditTargetType targetType,
                           UUID targetId,
                           UUID actorId,
                           Map<String, ?> beforeState,
                           Map<String, ?> afterState) {
        AuditEvent event = new AuditEvent(
            eventType,
            actorId,
            WebRequestContextHolder.currentIp(),
            WebRequestContextHolder.currentUserAgent(),
            targetType,
            targetId,
            toJson(beforeState),
            toJson(afterState),
            null
        );
        auditService.record(event);
    }

    private String toJson(Map<String, ?> state) {
        if (state == null) return null;
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("audit state serialization failed", e);
        }
    }

}
