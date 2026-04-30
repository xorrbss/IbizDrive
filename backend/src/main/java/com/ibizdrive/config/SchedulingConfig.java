package com.ibizdrive.config;

import com.ibizdrive.purge.HardPurgeProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring scheduling 활성화 + {@link HardPurgeProperties} 등록 (A7.3).
 *
 * <p>{@code app.purge.enabled=false}일 때 본 구성 자체를 import하지 않으므로
 * {@code @EnableScheduling}이 비활성화되고 {@code HardPurgeJob} 빈도 미등록된다 (이중 가드 —
 * job 클래스에도 동일 {@code @ConditionalOnProperty} 적용).
 *
 * <p>스케줄러는 단일 cron 잡이므로 별도 {@code TaskScheduler} 빈 정의는 불필요. Spring Boot 기본
 * single-thread scheduler로 충분 (다중 잡 도입 시 별도 ADR로 검토).
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(HardPurgeProperties.class)
@ConditionalOnProperty(name = "app.purge.enabled", havingValue = "true")
public class SchedulingConfig {
}
