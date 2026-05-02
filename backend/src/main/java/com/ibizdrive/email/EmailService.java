package com.ibizdrive.email;

/**
 * 이메일 전송 추상화 (a1.5).
 *
 * <p>dev/default 프로파일: {@link ConsoleEmailService} — stdout으로 본문 dump. SMTP 서버 의존 없음.
 * prod 프로파일: {@link SmtpEmailService} — Spring {@code JavaMailSender}로 실제 발송.
 *
 * <p>호출 측은 본 인터페이스에만 의존하며, 발송 실패 시 {@link EmailDeliveryException}을 던진다.
 * 비밀번호 재설정 흐름(forgot/reset)에서 사용되며, anti-enumeration을 위해 발송 실패도 호출자가
 * 200으로 응답할지 5xx로 응답할지 정책 결정 — 본 인터페이스는 raw 실패만 노출.
 */
public interface EmailService {
    void send(String to, String subject, String body);
}
