package com.ibizdrive.email;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR #45 — {@link EmailService#send}가 {@code @Async("emailExecutor")} fire-and-forget으로 동작함을
 * 통합적으로 검증. caller 스레드는 SMTP RTT(stub의 {@code Thread.sleep(200)})와 무관하게 즉시 반환되어야 한다.
 *
 * <p>최소 컨텍스트({@link EmailAsyncConfig} + {@link TestEmailServiceConfig})만 부팅해 DB/Flyway 의존을
 * 회피한다. 본 트랙의 보안 목표(가입자/미가입자 caller latency 동일 → anti-enumeration timing leak 완화)는
 * caller latency assertion에서 fail-fast 한다.
 */
@SpringBootTest(classes = EmailAsyncIntegrationTest.TestEmailServiceConfig.class)
class EmailAsyncIntegrationTest {

    /** 비동기 stub의 sleep 시간(ms). caller 측정값이 이 값에 근접하면 동기 실행 의심 → fail. */
    private static final long STUB_SLEEP_MS = 200;
    /** caller 허용 상한(ms). Windows CI runner jitter 감안 보수값. 정상 흐름은 < 5ms. */
    private static final long CALLER_THRESHOLD_MS = 50;

    @Autowired
    private EmailService emailService;

    /** Spring proxy로 감싼 빈은 인터페이스 타입으로만 노출된다. 테스트 검증을 위한 stub state는
     *  static singleton holder로 공유 — bean factory가 같은 인스턴스를 proxy 대상으로 사용. */
    private static final RecordingEmailService stub = new RecordingEmailService();

    @Test
    void callerReturnsImmediately_evenWhenSendBlocks() throws InterruptedException {
        stub.reset();

        long start = System.nanoTime();
        emailService.send("alice@example.com", "subj", "body");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // caller는 stub의 200ms sleep과 무관하게 즉시 반환.
        assertThat(elapsedMs)
            .as("caller latency (ms) — Spring @Async proxy 미활성 시 fail")
            .isLessThan(CALLER_THRESHOLD_MS);

        // 백그라운드 stub 호출 도달 확인.
        boolean reached = stub.latch.await(5, TimeUnit.SECONDS);
        assertThat(reached).as("stub should be invoked within 5s on background thread").isTrue();
    }

    @Test
    void sendRunsOnEmailExecutorThread() throws InterruptedException {
        stub.reset();

        emailService.send("bob@example.com", "subj", "body");

        boolean reached = stub.latch.await(5, TimeUnit.SECONDS);
        assertThat(reached).isTrue();

        String threadName = stub.threadName.get();
        assertThat(threadName)
            .as("스레드 이름이 emailExecutor prefix로 시작해야 함 — 다른 풀에서 실행되면 영역 분리 실패")
            .startsWith("email-async-");
    }

    @Configuration
    @Import(EmailAsyncConfig.class)
    static class TestEmailServiceConfig {
        @Bean
        EmailService emailService() {
            return stub;
        }
    }

    /**
     * Test stub — sleep으로 SMTP RTT를 흉내내고 호출된 thread 이름을 캡처한다.
     * Spring 빈으로 등록되어 {@link EmailService} 인터페이스의 {@code @Async} proxy가 본 인스턴스를 감싼다.
     */
    static class RecordingEmailService implements EmailService {
        volatile CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> threadName = new AtomicReference<>();

        void reset() {
            latch = new CountDownLatch(1);
            threadName.set(null);
        }

        @Override
        public void send(String to, String subject, String body) {
            threadName.set(Thread.currentThread().getName());
            try {
                Thread.sleep(STUB_SLEEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            latch.countDown();
        }
    }
}
