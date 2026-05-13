package com.ibizdrive.favorite;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * v1.x favorites orphan cleanup 설정 (application.yml {@code app.favorites.cleanup.*}).
 *
 * <p>schedule/zone만 정의. enabled 토글은 {@code cron_policy} 테이블의 {@code favorites.cleanup}
 * key가 단일 source (admin-cron-policy-toggle 정합). yml의 enabled 필드는 두지 않는다.
 *
 * <p>batch limit 미정의 — v1.x 가정상 favorites 규모 작음. 늘어나면 V_next에 max-per-run 추가.
 *
 * @param cron Spring cron 표현식 (6필드). 운영 기본 매일 새벽 2시 (purge 자정 + orphan 1시 다음).
 * @param zone cron 평가 시간대.
 */
@ConfigurationProperties(prefix = "app.favorites.cleanup")
public record FavoritesCleanupProperties(
    String cron,
    String zone
) {
    public FavoritesCleanupProperties {
        if (cron == null || cron.isBlank()) cron = "0 0 2 * * *";
        if (zone == null || zone.isBlank()) zone = "Asia/Seoul";
    }
}
