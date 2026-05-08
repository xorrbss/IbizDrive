package com.ibizdrive.purge;

import com.ibizdrive.admin.CronPolicyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A7 hard purge 스케줄 트리거 (docs/04 §13 {@code purge.expired}).
 *
 * <p>cron expression / 시간대 / 한도는 모두 {@link HardPurgeProperties}에서 read.
 *
 * <p>{@code enabled} 토글은 {@link CronPolicyRepository#isEnabled} (DB 단일 row lookup) — 본 잡은 매 tick
 * DB 조회 후 비활성이면 즉시 return. yml의 {@code app.purge.enabled}는 시드 후 효력 없음
 * (admin-cron-policy-toggle 트랙, V11 이후).
 *
 * <p><b>예외 처리</b>: {@code HardPurgeService.runDailyPurge}는 트랜잭션 본체 실패 시 전체
 * rollback + 예외 throw. 본 잡은 catch-all로 ERROR 로그만 남기고 다음 cron으로 재시도 (DB 일시
 * 장애 대비). audit summary는 service 내부 REQUIRES_NEW이므로 부분 진행 후 audit만 누락되는
 * 상황은 없음 (rollback이면 audit 미발행, audit 시도가 실패하면 service 트랜잭션도 함께 rollback).
 */
@Component
public class HardPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(HardPurgeJob.class);

    private final HardPurgeService service;
    private final HardPurgeProperties props;
    private final CronPolicyRepository cronPolicyRepository;

    public HardPurgeJob(HardPurgeService service,
                        HardPurgeProperties props,
                        CronPolicyRepository cronPolicyRepository) {
        this.service = service;
        this.props = props;
        this.cronPolicyRepository = cronPolicyRepository;
    }

    @Scheduled(cron = "${app.purge.cron}", zone = "${app.purge.zone}")
    public void run() {
        if (!cronPolicyRepository.isEnabled("purge.expired")) {
            log.debug("cron purge.expired disabled, skipping tick");
            return;
        }
        try {
            PurgeResult result = service.runDailyPurge(props.maxPerRun());
            if (result.truncated()) {
                log.warn("hard purge truncated — maxPerRun={} reached, run={} files={} folders={}",
                    props.maxPerRun(), result.runId(), result.purgedFiles(), result.purgedFolders());
            }
        } catch (RuntimeException e) {
            // 트랜잭션은 service 단에서 이미 rollback. 잡 자체는 다음 cron 재시도하기 위해 swallow.
            log.error("hard purge run failed — will retry on next schedule", e);
        }
    }
}
