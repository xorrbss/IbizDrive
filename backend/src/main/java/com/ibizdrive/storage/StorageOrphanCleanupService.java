package com.ibizdrive.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibizdrive.audit.AuditEvent;
import com.ibizdrive.audit.AuditEventType;
import com.ibizdrive.audit.AuditService;
import com.ibizdrive.audit.AuditTargetType;
import com.ibizdrive.file.FileVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Storage orphan cleanup 본체 (A15 backlog closure).
 *
 * <p>알고리즘:
 * <ol>
 *   <li>liveSet = `file_versions.storage_key` 전수 수집(stream → Set materialize, trash 보호 포함)</li>
 *   <li>{@code storageClient.listOlderThan(graceHours)} lazy walk 소비</li>
 *   <li>each walked object — key 마지막 segment를 UUID parse → liveSet 비포함이면 orphan candidate</li>
 *   <li>{@code maxPerRun} 한도 내에서 {@code storageClient.delete} 호출, per-row try/catch (실패는 ERROR + continue)</li>
 *   <li>완료 시 {@code STORAGE_ORPHAN_CLEANED} summary audit 1건 emission (REQUIRES_NEW)</li>
 * </ol>
 *
 * <p><b>트랜잭션</b>: 전체 메서드를 {@code @Transactional(readOnly=true)}로 감싸 streaming
 * 커서 contract를 만족. liveSet 수집 직후 stream을 close하면 DB cursor 해제 — 이후 storage
 * walk/delete 단계는 connection idle. audit emission은 REQUIRES_NEW로 별도 트랜잭션 사용.
 *
 * <p><b>Per-row failure isolation</b>: 단일 객체 삭제 실패가 잡 전체를 차단하지 않도록 try/catch.
 * {@link com.ibizdrive.share.ShareExpirationService} / {@link com.ibizdrive.permission.PermissionExpirationService}
 * 패턴 답습.
 *
 * <p><b>S3 호환성</b>: 본 service는 {@link StorageClient} 인터페이스만 의존하므로 v1.x S3 도입 시점에
 * {@code S3StorageClient.listOlderThan} + {@code delete}만 구현하면 변경 없이 재사용 가능.
 */
@Service
public class StorageOrphanCleanupService {

    private static final Logger log = LoggerFactory.getLogger(StorageOrphanCleanupService.class);

    private final FileVersionRepository fileVersionRepository;
    private final StorageClient storageClient;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public StorageOrphanCleanupService(FileVersionRepository fileVersionRepository,
                                       StorageClient storageClient,
                                       AuditService auditService,
                                       ObjectMapper objectMapper) {
        this.fileVersionRepository = fileVersionRepository;
        this.storageClient = storageClient;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /**
     * 1회 cleanup run 수행.
     *
     * @param maxPerRun  단일 run에서 삭제할 orphan 객체 최대 수 (양수)
     * @param graceHours mtime이 NOW-graceHours 이전인 객체만 대상 (양수)
     * @return run 결과 (audit after_state와 동일 필드)
     */
    @Transactional(readOnly = true)
    public StorageOrphanCleanupResult runDailyCleanup(int maxPerRun, int graceHours) {
        if (maxPerRun <= 0) throw new IllegalArgumentException("maxPerRun must be > 0");
        if (graceHours <= 0) throw new IllegalArgumentException("graceHours must be > 0");

        UUID runId = UUID.randomUUID();
        long start = System.currentTimeMillis();

        // 1) liveSet 적재 — stream을 즉시 close해 DB cursor 해제.
        Set<UUID> liveSet = new HashSet<>();
        try (Stream<UUID> s = fileVersionRepository.streamActiveStorageKeys()) {
            s.forEach(liveSet::add);
        }

        int scanned = 0;
        int candidates = 0;
        int deleted = 0;
        int failed = 0;
        boolean truncated = false;

        // 2) walk + diff + delete — IO만 수행, DB 쿼리 없음.
        try (Stream<StorageObject> walk = storageClient.listOlderThan(Duration.ofHours(graceHours))) {
            Iterator<StorageObject> it = walk.iterator();
            while (it.hasNext()) {
                StorageObject obj = it.next();
                scanned++;
                UUID key = parseStorageKey(obj.key());
                if (key == null) continue; // listOlderThan에서 이미 필터되지만 defense-in-depth
                if (liveSet.contains(key)) continue;
                candidates++;

                if (deleted >= maxPerRun) {
                    truncated = true;
                    break;
                }
                try {
                    storageClient.delete(obj.key());
                    deleted++;
                } catch (IOException e) {
                    failed++;
                    log.error("storage orphan cleanup — delete failed key={}: {}", obj.key(), e.toString());
                }
            }
        } catch (IOException e) {
            // walk 시작 실패(root 부재 등) — 카운터 0인 채로 audit emit + 그대로 진행.
            log.error("storage orphan cleanup — walk failed: {}", e.toString());
        }

        long duration = System.currentTimeMillis() - start;
        StorageOrphanCleanupResult result = new StorageOrphanCleanupResult(
            runId, scanned, candidates, deleted, failed, truncated, duration);

        emitAudit(result);

        log.info("storage orphan cleanup run={} scanned={} candidates={} deleted={} failed={} truncated={} duration={}ms",
            runId, scanned, candidates, deleted, failed, truncated, duration);

        return result;
    }

    /** {@code {YYYY}/{MM}/{UUID}}의 마지막 segment를 UUID parse. 형식 미일치는 null. */
    private static UUID parseStorageKey(String key) {
        if (key == null) return null;
        int slash = key.lastIndexOf('/');
        String last = slash < 0 ? key : key.substring(slash + 1);
        try {
            return UUID.fromString(last);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void emitAudit(StorageOrphanCleanupResult result) {
        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("runId", result.runId().toString());
        afterState.put("scanned", result.scanned());
        afterState.put("candidates", result.candidates());
        afterState.put("deleted", result.deleted());
        afterState.put("failed", result.failed());
        afterState.put("truncated", result.truncated());
        afterState.put("durationMs", result.durationMs());

        AuditEvent event = new AuditEvent(
            AuditEventType.STORAGE_ORPHAN_CLEANED,
            null,                           // system actor
            null,                           // no IP
            null,                           // no user agent
            AuditTargetType.SYSTEM,
            null,                           // no target id (global)
            null,                           // before
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
