package com.ibizdrive.config;

import com.ibizdrive.permission.PermissionExpirationProperties;
import com.ibizdrive.purge.HardPurgeProperties;
import com.ibizdrive.share.ShareExpirationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring scheduling + 스케줄 잡들의 properties record 등록 지점 (A7.3, SHARE_EXPIRED cron,
 * permissions-expired-cron).
 *
 * <p>{@code @EnableScheduling}은 무조건 활성 — 개별 잡({@link com.ibizdrive.purge.HardPurgeJob},
 * {@link com.ibizdrive.share.ShareExpirationJob},
 * {@link com.ibizdrive.permission.PermissionExpirationJob})은 각자 {@code @ConditionalOnProperty}로
 * enabled 게이트. 잡 빈이 하나도 등록되지 않으면 single-thread scheduler는 idle 상태로 비용 무시 가능.
 *
 * <p>이전(A7) 구현은 본 config 자체에 {@code @ConditionalOnProperty(app.purge.enabled)}를 두었으나,
 * SHARE_EXPIRED 도입으로 다중 잡 지원이 필요해 활성화 조건을 잡-개별 가드로 위임 (ADR #34 backlog closure).
 *
 * <p>스케줄러는 단일 thread로 충분 (현재 잡 3개, 모두 짧은 batch). 다중 잡이 동시에 무거워지면 별도
 * {@code TaskScheduler} pool 빈 도입 검토.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({
    HardPurgeProperties.class,
    ShareExpirationProperties.class,
    PermissionExpirationProperties.class
})
public class SchedulingConfig {
}
