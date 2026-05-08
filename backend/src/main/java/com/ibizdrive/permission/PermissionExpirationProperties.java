package com.ibizdrive.permission;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code permissions-expired-cron} 설정 (application.yml {@code app.permission.expiration.*}).
 *
 * <p>schedule/zone/batchSize만 정의. enabled 토글은 admin-cron-toggle 트랙(2026-05-08)에서
 * {@code cron_policy} DB 테이블로 이관 — {@link PermissionExpirationJob}이 매 tick lookup해 skip 결정.
 *
 * @param batchSize 단일 run 처리 상한 — repo {@code findExpiredActiveIds(limit=batchSize)}.
 * @param cron      Spring cron 표현식 (6필드). 운영 기본 5분 주기.
 * @param zone      cron 평가 시간대 (java.time.ZoneId 호환).
 */
@ConfigurationProperties(prefix = "app.permission.expiration")
public record PermissionExpirationProperties(
    int batchSize,
    String cron,
    String zone
) {
    public PermissionExpirationProperties {
        if (batchSize <= 0) batchSize = 200;
        if (cron == null || cron.isBlank()) cron = "0 */5 * * * *";
        if (zone == null || zone.isBlank()) zone = "Asia/Seoul";
    }
}
