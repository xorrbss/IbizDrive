package com.ibizdrive.purge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A7 hard purge 스케줄 트리거 (docs/04 §13 {@code purge.expired}).
 *
 * <p>cron expression / 시간대 / 한도는 모두 {@link HardPurgeProperties}에서 read.
 * {@code app.purge.enabled=false}일 때 {@link SchedulingConfig}와 본 빈 모두 미등록되어
 * 잡 자체가 비활성화된다.
 *
 * <p><b>예외 처리</b>: {@code HardPurgeService.runDailyPurge}는 트랜잭션 본체 실패 시 전체
 * rollback + 예외 throw. 본 잡은 catch-all로 ERROR 로그만 남기고 다음 cron으로 재시도 (DB 일시
 * 장애 대비). audit summary는 service 내부 REQUIRES_NEW이므로 부분 진행 후 audit만 누락되는
 * 상황은 없음 (rollback이면 audit 미발행, audit 시도가 실패하면 service 트랜잭션도 함께 rollback).
 */
@Component
@ConditionalOnProperty(name = "app.purge.enabled", havingValue = "true")
public class HardPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(HardPurgeJob.class);

    private final HardPurgeService service;
    private final HardPurgeProperties props;

    public HardPurgeJob(HardPurgeService service, HardPurgeProperties props) {
        this.service = service;
        this.props = props;
    }

    @Scheduled(cron = "${app.purge.cron}", zone = "${app.purge.zone}")
    public void run() {
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
