package com.ibizdrive.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Storage 모듈 properties 등록 — A15 (ADR #36 가칭).
 *
 * <p>{@link StorageProperties}는 {@code ibizdrive.storage.*} 바인딩. 구현체
 * ({@link LocalFsStorageClient})는 {@code @ConditionalOnProperty}로 자체 활성 가드.
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {
}
