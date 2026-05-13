package com.ibizdrive.purge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.file.FileVersionRepository;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.user.UserQuotaEnforcer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A7 hard purge 본체 — {@code purge_after <= now}이고 soft-deleted된 folders/files를
 * DB에서 영구 삭제하고 {@link AuditEventType#SYSTEM_PURGE_EXECUTED} summary audit 1건을 발행한다.
 *
 * <p><b>트랜잭션</b>: 단일 {@code @Transactional}. 예외 시 전체 rollback → audit 미발행 →
 * 다음 cron 재시도. partial purge 미허용.
 *
 * <p><b>순서</b> (FK 정합):
 * <ol>
 *   <li>expired files 조회 → version storage_keys 수집(audit orphan 기록)
 *   <li>{@code file_versions} cascade delete (file_id IN expired) — {@code ON DELETE RESTRICT} 만족
 *   <li>{@code files} hard delete
 *   <li>expired folders 조회 → leaf-first 위상정렬 (parent_id 그래프, Kahn's algorithm)
 *   <li>{@code folders} hard delete (leaf 레이어부터)
 *   <li>audit {@code SYSTEM_PURGE_EXECUTED} emission (REQUIRES_NEW)
 * </ol>
 *
 * <p><b>S3 객체 삭제 부재 (ADR #31)</b>: storage 모듈 milestone으로 deferred. {@code orphanStorageKeys}는
 * audit {@code after_state}에 cap=1000 까지 기록 (cap 초과는 truncated 플래그). storage 모듈 도입 시
 * {@code orphan.detect} 잡(docs/04 §13)이 storage_key 기반 cross-check로 정리.
 *
 * <p><b>Audit summary-only (A6 root-only 패턴 일관)</b>: per-row {@code FILE_PURGED}/{@code FOLDER_PURGED}
 * 발행 안 함. 1 run = 1 audit. {@code FILE_PURGED}/{@code FOLDER_PURGED} enum은 A8
 * {@code /api/trash/:id} (manual purge) 트랙 reserve.
 */
@Service
public class HardPurgeService {

    private static final Logger log = LoggerFactory.getLogger(HardPurgeService.class);

    /** orphan storage_key 수집 cap — audit_log JSONB row 비대화 회피. */
    private static final int ORPHAN_STORAGE_KEYS_CAP = 1000;

    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final FileVersionRepository fileVersionRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final UserQuotaEnforcer userQuotaEnforcer;

    public HardPurgeService(FileRepository fileRepository,
                            FolderRepository folderRepository,
                            FileVersionRepository fileVersionRepository,
                            AuditService auditService,
                            ObjectMapper objectMapper,
                            UserQuotaEnforcer userQuotaEnforcer) {
        this.fileRepository = fileRepository;
        this.folderRepository = folderRepository;
        this.fileVersionRepository = fileVersionRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.userQuotaEnforcer = userQuotaEnforcer;
    }

    /**
     * 만료된 row를 단일 트랜잭션 내에서 hard delete + audit summary 발행.
     *
     * @param maxPerRun files + folders 합산 한도. 초과 시 {@link PurgeResult#truncated()}={@code true}.
     * @return run 결과 (audit after_state와 동일 필드)
     */
    @Transactional
    public PurgeResult runDailyPurge(int maxPerRun) {
        if (maxPerRun <= 0) throw new IllegalArgumentException("maxPerRun must be > 0");

        UUID runId = UUID.randomUUID();
        long start = System.currentTimeMillis();
        Instant now = Instant.now();

        // 1) expired files
        List<UUID> expiredFiles = fileRepository.findExpiredFileIds(now, maxPerRun);

        // 2) version storage_keys 수집 + version cascade delete (file 삭제 선행)
        // quota mutation Phase 6 — DELETE 직전에 owner별 storage_used 합계 수집 (DELETE 후엔 0 반환).
        List<UUID> orphanKeys = new ArrayList<>();
        boolean orphanTruncated = false;
        List<Object[]> ownerSums = List.of();
        if (!expiredFiles.isEmpty()) {
            List<UUID> allKeys = fileVersionRepository.findStorageKeysByFileIds(expiredFiles);
            if (allKeys.size() > ORPHAN_STORAGE_KEYS_CAP) {
                orphanKeys.addAll(allKeys.subList(0, ORPHAN_STORAGE_KEYS_CAP));
                orphanTruncated = true;
            } else {
                orphanKeys.addAll(allKeys);
            }
            ownerSums = fileRepository.sumVersionBytesPerOwnerByFileIds(expiredFiles);
            fileVersionRepository.deleteByFileIds(expiredFiles);
        }

        // 3) files hard delete
        int purgedFiles = expiredFiles.isEmpty() ? 0 : fileRepository.hardDeleteByIds(expiredFiles);

        // 3a) quota mutation Phase 6 — owner별 storage_used 감소 (배치 audit-only 정책 유지, 별도 emit 없음).
        for (Object[] row : ownerSums) {
            UUID ownerId = (UUID) row[0];
            long total = ((Number) row[1]).longValue();
            userQuotaEnforcer.release(ownerId, total);
        }

        // 4) expired folders — files batch에서 사용한 만큼 한도 차감
        int folderBudget = Math.max(0, maxPerRun - expiredFiles.size());
        List<UUID> expiredFolders = folderBudget == 0
            ? List.of()
            : folderRepository.findExpiredFolderIds(now, folderBudget);

        // 5) folders leaf-first 위상정렬 + hard delete
        int purgedFolders = 0;
        if (!expiredFolders.isEmpty()) {
            List<UUID> ordered = leafFirstOrder(expiredFolders);
            if (!ordered.isEmpty()) {
                purgedFolders = folderRepository.hardDeleteByIds(ordered);
            }
        }

        // 한도 초과 여부 — files 또는 folders 어느 쪽이든 limit 도달 시 truncated
        boolean truncated = expiredFiles.size() == maxPerRun
            || (folderBudget > 0 && expiredFolders.size() == folderBudget);

        long duration = System.currentTimeMillis() - start;

        PurgeResult result = new PurgeResult(
            runId, purgedFiles, purgedFolders, orphanKeys, orphanTruncated, duration, truncated);

        emitAudit(result);

        log.info("hard purge run={} files={} folders={} orphans={} truncated={} duration={}ms",
            runId, purgedFiles, purgedFolders, orphanKeys.size(), truncated, duration);

        return result;
    }

    // -----------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------

    /**
     * Kahn's algorithm — batch 내 folder id의 자식 → 부모 순(=leaf-first)으로 위상정렬.
     *
     * <p>parent_id가 batch 외부(NULL이거나 batch에 미포함된 id)인 경우 해당 노드는 children 카운트
     * 0인 leaf로 간주된다. cycle은 schema 상 가능하지 않지만 (A6 cascade 정책에서 자식이 부모보다
     * 먼저 expire되거나 동시 expire), 만약 발생하면 ordered list 길이가 입력보다 작아진다.
     *
     * <p>구현: 각 노드의 "active children in batch" 카운트를 계산 → 0인 노드부터 출력 → 부모의
     * 카운트 -1 반복.
     */
    private List<UUID> leafFirstOrder(Collection<UUID> ids) {
        Set<UUID> idSet = new HashSet<>(ids);

        // id → parentId (batch 외부 parent는 null로 간주)
        Map<UUID, UUID> parentInBatch = new HashMap<>(idSet.size() * 2);
        // id → 자식 수 (batch 내)
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
            log.warn("hard purge topo-sort: {} folder nodes formed cycle, skipping",
                idSet.size() - ordered.size());
        }
        return ordered;
    }

    private void emitAudit(PurgeResult result) {
        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("runId", result.runId().toString());
        afterState.put("purgedFiles", result.purgedFiles());
        afterState.put("purgedFolders", result.purgedFolders());
        afterState.put("orphanStorageKeys", result.orphanStorageKeys());
        afterState.put("orphanStorageKeysTruncated", result.orphanStorageKeysTruncated());
        afterState.put("durationMs", result.durationMs());
        afterState.put("truncated", result.truncated());

        AuditEvent event = new AuditEvent(
            AuditEventType.SYSTEM_PURGE_EXECUTED,
            null,                                 // system actor
            null,                                 // no IP (scheduler context)
            null,                                 // no user agent
            AuditTargetType.SYSTEM,
            null,                                 // no target id (global)
            null,                                 // before
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

    @SuppressWarnings("unused")
    private static Collection<UUID> safeNonEmpty(Collection<UUID> coll) {
        return coll == null ? Collections.emptyList() : coll;
    }
}
