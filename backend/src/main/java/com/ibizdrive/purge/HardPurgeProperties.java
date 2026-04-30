package com.ibizdrive.purge;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * A7 hard purge 설정 (application.yml {@code app.purge.*}).
 *
 * <p>운영 기본값은 비활성({@code enabled=false}) — 명시적 활성화 후 운영 투입.
 * 테스트는 {@code @TestPropertySource("app.purge.enabled=...")}로 override.
 *
 * <p>{@link CorsProperties}와 동일하게 {@code @ConfigurationProperties} record 패턴 — placeholder
 * resolver 없이 Binder API로 안전 바인딩.
 *
 * @param enabled    job + scheduling 활성 여부. {@code false}면 {@link HardPurgeJob} 빈 미등록.
 * @param maxPerRun  단일 run에서 처리할 files+folders 합산 한도. 초과 시 {@link PurgeResult#truncated()}=true.
 * @param cron       Spring cron 표현식 (6필드: 초 분 시 일 월 요일). 운영 기본 매일 자정.
 * @param zone       cron 평가 시간대 (java.time.ZoneId 호환).
 */
@ConfigurationProperties(prefix = "app.purge")
public record HardPurgeProperties(
    boolean enabled,
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
