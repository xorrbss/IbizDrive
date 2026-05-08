package com.ibizdrive.config;

import com.ibizdrive.permission.PermissionExpirationProperties;
import com.ibizdrive.purge.HardPurgeProperties;
import com.ibizdrive.share.ShareExpirationProperties;
import com.ibizdrive.storage.StorageOrphanCleanupProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring scheduling + 스케줄 잡들의 properties record 등록 지점 (A7.3, SHARE_EXPIRED cron,
 * permissions-expired-cron).
 *
 * <p>{@code @EnableScheduling}은 무조건 활성 — 4 cron job({@link com.ibizdrive.purge.HardPurgeJob},
 * {@link com.ibizdrive.share.ShareExpirationJob},
 * {@link com.ibizdrive.permission.PermissionExpirationJob},
 * {@link com.ibizdrive.storage.StorageOrphanCleanupJob}) 빈은 항상 등록되며 매 tick마다 진입한다.
 *
 * <p>잡-개별 enabled 게이트는 {@link com.ibizdrive.admin.CronPolicyRepository#isEnabled} (DB
 * 단일 row lookup, V11 admin-cron-policy-toggle 트랙)로 위임 — 각 잡의 {@code run()} 첫 줄에서
 * 호출해 false면 즉시 return. yml의 {@code app.*.enabled} 필드는 V11 시드 이후 dead config
 * (cleanup v1.x). 토글은 ADMIN UI(`/admin/system`) → `PUT /api/admin/system/cron/{key}`로
 * 재기동 없이 즉시 반영.
 *
 * <p>스케줄러는 단일 thread로 충분 (현재 잡 4개, 모두 짧은 batch). 다중 잡이 동시에 무거워지면 별도
 * {@code TaskScheduler} pool 빈 도입 검토.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({
    HardPurgeProperties.class,
    ShareExpirationProperties.class,
    PermissionExpirationProperties.class,
    StorageOrphanCleanupProperties.class
})
public class SchedulingConfig {
}
