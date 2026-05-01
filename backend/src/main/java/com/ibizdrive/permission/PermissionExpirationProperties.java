package com.ibizdrive.permission;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code permissions-expired-cron} 설정 (application.yml {@code app.permission.expiration.*}).
 *
 * <p>운영 기본값은 비활성({@code enabled=false}) — 명시적 활성화 후 운영 투입
 * ({@code ShareExpirationProperties} / {@code HardPurgeProperties} 동형).
 *
 * @param enabled   {@link PermissionExpirationJob} 빈 등록 여부. {@code false}면 cron 트리거 자체 부재.
 * @param batchSize 단일 run 처리 상한 — repo {@code findExpiredActiveIds(limit=batchSize)}.
 * @param cron      Spring cron 표현식 (6필드). 운영 기본 5분 주기.
 * @param zone      cron 평가 시간대 (java.time.ZoneId 호환).
 */
@ConfigurationProperties(prefix = "app.permission.expiration")
public record PermissionExpirationProperties(
    boolean enabled,
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
