package com.ibizdrive.purge;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * A7 hard purge 설정 (application.yml {@code app.purge.*}).
 *
 * <p>schedule/zone/maxPerRun만 정의. enabled 토글은 admin-cron-toggle 트랙(2026-05-08)에서
 * {@code cron_policy} DB 테이블로 이관 — {@link HardPurgeJob}이 매 tick {@code CronPolicyRepository}
 * 를 lookup해 skip 여부를 결정한다.
 *
 * <p>{@code @ConfigurationProperties} record 패턴 — placeholder resolver 없이 Binder API로 안전 바인딩.
 *
 * @param maxPerRun  단일 run에서 처리할 files+folders 합산 한도. 초과 시 {@link PurgeResult#truncated()}=true.
 * @param cron       Spring cron 표현식 (6필드: 초 분 시 일 월 요일). 운영 기본 매일 자정.
 * @param zone       cron 평가 시간대 (java.time.ZoneId 호환).
 */
@ConfigurationProperties(prefix = "app.purge")
public record HardPurgeProperties(
    int maxPerRun,
    String cron,
    String zone
) {
    public HardPurgeProperties {
        if (maxPerRun <= 0) maxPerRun = 10000;
        if (cron == null || cron.isBlank()) cron = "0 0 0 * * *";
        if (zone == null || zone.isBlank()) zone = "Asia/Seoul";
    }
}
