package com.ibizdrive.approval;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ADMIN_APPROVAL_EXPIRED cron 설정 (application.yml {@code app.admin-approval.expiration.*}).
 *
 * <p>schedule/zone/batchSize만 정의. enabled 토글은 admin-cron-toggle 트랙(2026-05-08)에서
 * {@code cron_policy} DB 테이블로 일원화 — {@link PendingAdminApprovalExpirationJob}이 매 tick
 * lookup해 skip 결정. yml의 {@code enabled} 필드는 두지 않는다 (yml-enabled-cleanup 정합).
 *
 * @param batchSize 단일 run 처리 상한 — service {@code findExpired(cap=batchSize)}.
 * @param cron      Spring cron 표현식 (6필드). 기본 매 5분 — share/permission cron과 정합.
 * @param zone      cron 평가 시간대 (java.time.ZoneId 호환).
 */
@ConfigurationProperties(prefix = "app.admin-approval.expiration")
public record PendingAdminApprovalExpirationProperties(
    int batchSize,
    String cron,
    String zone
) {
    public PendingAdminApprovalExpirationProperties {
        if (batchSize <= 0) batchSize = 200;
        if (cron == null || cron.isBlank()) cron = "0 */5 * * * *";
        if (zone == null || zone.isBlank()) zone = "Asia/Seoul";
    }
}
