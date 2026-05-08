package com.ibizdrive.trash;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * trash 패키지의 {@code @ConfigurationProperties} 등록 지점.
 *
 * <p>{@link TrashRetentionProperties} 외 다른 trash 관련 properties record가 추가되면 여기에
 * 함께 등록 ({@link com.ibizdrive.audit.AuditConfig} / {@link com.ibizdrive.storage.StorageConfig}
 * 패턴 동형).
 */
@Configuration
@EnableConfigurationProperties(TrashRetentionProperties.class)
public class TrashConfig {
}
