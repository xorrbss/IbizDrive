package com.ibizdrive.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * {@code ibizdrive.cors.*} 속성 바인딩 — Spring Binder API 사용.
 *
 * <p>이전 구현은 {@link org.springframework.beans.factory.annotation.Value @Value("${ibizdrive.cors.allowed-origins}")}
 * 로 {@link List}{@code <}{@link String}{@code >}을 직접 주입했으나, application.yml의 YAML list
 * 구문은 환경에 indexed 속성 ({@code allowed-origins[0]}, {@code [1]} ...)으로 노출되어
 * {@code @SpringBootTest} 부트스트랩 경로에서 placeholder resolver가 flat 키
 * {@code ibizdrive.cors.allowed-origins}를 못 찾아 {@code Could not resolve placeholder}
 * 로 실패. CI ubuntu(Testcontainers + full ApplicationContext)에서 재현됨.
 *
 * <p>{@code @ConfigurationProperties}는 Spring의 {@code Binder} API를 통해 indexed 속성을
 * 직접 List로 바인딩하므로 placeholder resolver를 거치지 않고 환경 차이에 영향받지 않음.
 *
 * @param allowedOrigins CORS 허용 출처 목록. application.yml {@code ibizdrive.cors.allowed-origins}
 */
@ConfigurationProperties(prefix = "ibizdrive.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
