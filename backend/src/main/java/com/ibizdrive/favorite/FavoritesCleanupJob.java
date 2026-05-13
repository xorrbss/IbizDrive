package com.ibizdrive.favorite;

import com.ibizdrive.admin.CronPolicyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * v1.x — favorites orphan cleanup cron 트리거. {@link HardPurgeJob} 패턴 답습.
 *
 * <p>{@code @Scheduled} cron tick마다 진입 → {@link CronPolicyRepository#isEnabled} ("favorites.cleanup")
 * 로 게이트 → 비활성이면 즉시 return. 활성이면 {@link FavoritesCleanupService#runDailyCleanup}.
 *
 * <p>예외는 service 단에서 rollback + ERROR 로그 후 next-tick 재시도 (HardPurgeJob와 동형).
 */
@Component
public class FavoritesCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(FavoritesCleanupJob.class);

    private final FavoritesCleanupService service;
    private final CronPolicyRepository cronPolicyRepository;

    public FavoritesCleanupJob(
        FavoritesCleanupService service,
        CronPolicyRepository cronPolicyRepository
    ) {
        this.service = service;
        this.cronPolicyRepository = cronPolicyRepository;
    }

    @Scheduled(cron = "${app.favorites.cleanup.cron}", zone = "${app.favorites.cleanup.zone}")
    public void run() {
        if (!cronPolicyRepository.isEnabled("favorites.cleanup")) {
            log.debug("cron favorites.cleanup disabled, skipping tick");
            return;
        }
        try {
            service.runDailyCleanup();
        } catch (RuntimeException e) {
            log.error("favorites cleanup run failed — will retry on next schedule", e);
        }
    }
}
