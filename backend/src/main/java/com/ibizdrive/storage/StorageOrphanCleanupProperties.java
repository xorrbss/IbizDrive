package com.ibizdrive.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Storage orphan cleanup 잡 설정 (application.yml {@code app.storage.orphan-cleanup.*}).
 *
 * <p>A15 closure에서 명시한 storage orphan(트랜잭션 실패·hard purge 잔존 객체)을 일일 cron으로
 * 정리하는 {@link StorageOrphanCleanupJob}의 설정. schedule/zone/batchSize/maxPerRun/graceHours만
 * 정의. enabled 토글은 admin-cron-toggle 트랙(2026-05-08)에서 {@code cron_policy} DB 테이블로 이관.
 *
 * <p>{@link HardPurgeProperties}와 동일하게 {@code @ConfigurationProperties} record 패턴.
 *
 * @param cron        Spring cron 표현식 (6필드: 초 분 시 일 월 요일). 운영 기본 매일 새벽 1시(A7 hard purge 자정 직후).
 * @param zone        cron 평가 시간대 (java.time.ZoneId 호환).
 * @param maxPerRun   단일 run에서 삭제할 orphan 객체 최대 수. 초과 시 result.truncated=true.
 * @param graceHours  객체 mtime이 NOW-graceHours 이전인 객체만 삭제 후보 — in-flight 업로드 race 회피.
 * @param batchSize   walk stream 소비 배치 크기 (현 단계에서는 reserved — 향후 chunked delete 시 활용).
 */
@ConfigurationProperties(prefix = "app.storage.orphan-cleanup")
public record StorageOrphanCleanupProperties(
    String cron,
    String zone,
    int maxPerRun,
    int graceHours,
    int batchSize
) {
    public StorageOrphanCleanupProperties {
        if (cron == null || cron.isBlank()) cron = "0 0 1 * * *";
        if (zone == null || zone.isBlank()) zone = "Asia/Seoul";
        if (maxPerRun <= 0) maxPerRun = 10000;
        if (graceHours <= 0) graceHours = 24;
        if (batchSize <= 0) batchSize = 200;
    }
}
