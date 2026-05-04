package com.ibizdrive.email;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 이메일 발송용 비동기 실행자 설정 (ADR #45).
 *
 * <p>{@link EmailService#send}는 fire-and-forget 의미로 호출되며 본 설정이 제공하는
 * {@code emailExecutor}에서 백그라운드 실행된다. 호출자(예: {@code PasswordResetService.requestReset})는
 * SMTP RTT와 무관하게 즉시 반환되어, 가입자/미가입자 응답 latency가 동일해지는 것이 본 설정의 목적이다
 * (anti-enumeration timing leak 완화 — docs/03 §2.7).
 *
 * <p>풀 크기: corePool=2, maxPool=4, queue=100. 사내 베타 트래픽 + forgot 분당 1회/email rate-limit
 * (ADR #44) 가정에서 동시 실행 N≪100. 풀 포화 시 default {@code CallerRunsPolicy} —
 * caller 스레드에서 직접 실행되어 latency 회귀가 발생할 수 있으나 BETA 도달 불가 한계로 명시.
 *
 * <p>{@code AsyncConfigurer} 미구현(global default executor 미설정) — 다른 {@code @Async}
 * 도입 시 영역 분리를 위해 항상 {@code @Async("emailExecutor")} 명시 사용.
 */
@Configuration
@EnableAsync
public class EmailAsyncConfig {

    @Bean("emailExecutor")
    public Executor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("email-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
