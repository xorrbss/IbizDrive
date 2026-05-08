package com.ibizdrive.storage;

import com.ibizdrive.admin.CronPolicyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Storage orphan cleanup 스케줄 트리거 (A15 backlog closure).
 *
 * <p>cron / zone / 한도는 모두 {@link StorageOrphanCleanupProperties}에서 read.
 *
 * <p>{@code enabled} 토글은 {@link CronPolicyRepository#isEnabled} (DB 단일 row lookup) — 본 잡은 매 tick
 * DB 조회 후 비활성이면 즉시 return. yml의 {@code app.storage.orphan-cleanup.enabled}는 시드 후 효력 없음
 * (admin-cron-policy-toggle 트랙, V11 이후).
 *
 * <p><b>예외 처리</b>: service의 외부 작용(storage delete)은 transactional rollback 대상이 아님 —
 * 일부 객체가 이미 삭제된 후 다른 객체에서 실패해도 이미 삭제된 객체는 복구 불가. service는 per-row
 * try/catch로 부분 실패 isolation을 보장하고, 본 잡은 catch-all로 transient 예외(예: walk 시작 실패
 * 직전 IO error)를 swallow + ERROR log → 다음 cron 재시도.
 *
 * <p>{@link com.ibizdrive.purge.HardPurgeJob} / {@link com.ibizdrive.share.ShareExpirationJob} 패턴 답습.
 */
@Component
public class StorageOrphanCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(StorageOrphanCleanupJob.class);

    private final StorageOrphanCleanupService service;
    private final StorageOrphanCleanupProperties props;
    private final CronPolicyRepository cronPolicyRepository;

    public StorageOrphanCleanupJob(StorageOrphanCleanupService service,
                                   StorageOrphanCleanupProperties props,
                                   CronPolicyRepository cronPolicyRepository) {
        this.service = service;
        this.props = props;
        this.cronPolicyRepository = cronPolicyRepository;
    }

    @Scheduled(
        cron = "${app.storage.orphan-cleanup.cron}",
        zone = "${app.storage.orphan-cleanup.zone}"
    )
    public void run() {
        if (!cronPolicyRepository.isEnabled("storage.orphan.cleanup")) {
            log.debug("cron storage.orphan.cleanup disabled, skipping tick");
            return;
        }
        try {
            StorageOrphanCleanupResult result = service.runDailyCleanup(
                props.maxPerRun(), props.graceHours());
            if (result.truncated()) {
                log.warn("storage orphan cleanup truncated — maxPerRun={} reached, run={} deleted={} candidates={}",
                    props.maxPerRun(), result.runId(), result.deleted(), result.candidates());
            }
        } catch (RuntimeException e) {
            // service는 per-row IOException isolation 처리 — RuntimeException은 transient 예상.
            // 다음 cron 재시도를 위해 swallow.
            log.error("storage orphan cleanup run failed — will retry on next schedule", e);
        }
    }
}
