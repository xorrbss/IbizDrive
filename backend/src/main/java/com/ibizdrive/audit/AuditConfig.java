package com.ibizdrive.audit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * audit 패키지의 {@code @ConfigurationProperties} 등록 지점.
 *
 * <p>{@link AuditExportProperties} 외 다른 audit 관련 properties record가 추가되면 여기에 함께
 * 등록 ({@link com.ibizdrive.storage.StorageConfig} 패턴 동형).
 */
@Configuration
@EnableConfigurationProperties({AuditExportProperties.class, AuditAppendOnlyProperties.class})
public class AuditConfig {
}
