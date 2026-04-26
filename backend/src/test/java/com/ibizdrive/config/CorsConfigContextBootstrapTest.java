package com.ibizdrive.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.filter.CorsFilter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CorsConfig}가 Spring Boot의 정식 ConfigData 부트스트랩 (application.yml 로드)
 * + 일반 ApplicationContext refresh 흐름에서 정상 빈 등록되는지 검증 — Docker/CI 무관.
 *
 * <p>회귀 배경: A1 close push 단계에서 CI ubuntu runner의 {@code AuthScenarioIntegrationTest}
 * (유일한 {@code @SpringBootTest})가 ApplicationContext 로드 실패. 진짜 메시지 (ci.yml에
 * --info --stacktrace 임시 추가 후 캡처):
 *
 * <pre>
 * Caused by: BeanCreationException: Error creating bean with name 'corsConfig':
 *            Injection of autowired dependencies failed
 * Caused by: IllegalArgumentException: Could not resolve placeholder
 *            'ibizdrive.cors.allowed-origins' in value "${ibizdrive.cors.allowed-origins}"
 * </pre>
 *
 * <p>원인: {@code @Value("${ibizdrive.cors.allowed-origins}") List<String>}는 Spring의
 * {@code PropertySourcesPlaceholderConfigurer}가 flat string 키를 찾는 방식인데,
 * application.yml의 YAML list 구문은 환경에 indexed 속성 ({@code [0], [1] ...})으로만
 * 노출되어 flat 키 미존재 → placeholder 미해결.
 *
 * <p>이전 시도 ({@link YamlPropertySourceLoader} 직접 + {@code AnnotationConfigApplicationContext})
 * 는 우연히 PASS — 그 경로는 Spring Boot의 정식 ConfigData 처리를 거치지 않아 일부
 * 자동 평탄화 동작이 달랐다. 본 테스트는 {@link ConfigDataApplicationContextInitializer}로
 * @SpringBootTest와 동일한 환경 셋업을 재현 → CI fail 그대로 재현 가능.
 *
 * <p>Fix 후: {@code @ConfigurationProperties}로 마이그레이션 — Spring Binder API가
 * YAML list를 List<String>으로 직접 바인딩 (placeholder resolver 비경유).
 */
class CorsConfigContextBootstrapTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withUserConfiguration(CorsConfig.class);

    @Test
    void corsFilter_isCreated_underSpringBootConfigDataBootstrap() {
        runner.run(context -> assertThat(context)
            .as("CorsConfig must wire from real application.yml under Spring Boot ConfigData bootstrap")
            .hasNotFailed()
            .hasSingleBean(CorsFilter.class));
    }
}
