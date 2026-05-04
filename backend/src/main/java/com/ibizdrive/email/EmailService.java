package com.ibizdrive.email;

import org.springframework.scheduling.annotation.Async;

/**
 * 이메일 전송 추상화 (a1.5, ADR #45 비동기화).
 *
 * <p>dev/default 프로파일: {@link ConsoleEmailService} — stdout으로 본문 dump. SMTP 서버 의존 없음.
 * prod 프로파일: {@link SmtpEmailService} — Spring {@code JavaMailSender}로 실제 발송.
 *
 * <p><b>fire-and-forget 의미론</b> — {@link #send}는 {@code @Async("emailExecutor")}로
 * 호출 시점에 즉시 반환되며 실제 SMTP 발송은 {@code emailExecutor} 풀에서 비동기 수행된다
 * (ADR #45, {@link EmailAsyncConfig}). 호출자 스레드의 트랜잭션 컨텍스트와 분리되며,
 * 발송 실패는 impl 내부에서 ERROR 로그로 흡수 — caller에 어떤 형태로도 도달하지 않는다.
 *
 * <p>이 설계의 목적은 forgot/reset 흐름에서 가입자/미가입자 caller latency를 동일하게 유지하여
 * anti-enumeration timing leak을 완화하는 데 있다 (docs/03 §2.7).
 *
 * <p>{@link EmailDeliveryException}은 본 인터페이스 계약에서 노출되지 않으며 보존만 유지된다
 * (사용처 0). 후속 cleanup 트랙에서 제거 검토.
 */
public interface EmailService {

    @Async("emailExecutor")
    void send(String to, String subject, String body);
}
