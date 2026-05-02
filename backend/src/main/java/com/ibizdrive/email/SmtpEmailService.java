package com.ibizdrive.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * prod 프로파일용 EmailService — Spring {@link JavaMailSender}로 실제 SMTP 발송.
 *
 * <p>{@code spring.mail.*} 환경변수가 운영자 주입(`BETA-RELEASE.md` 게이트와 동일 흐름)되어야
 * {@link JavaMailSender} 빈이 등록된다. 미주입 시 부팅 실패 — 운영에서 무음 발송 방지.
 *
 * <p>{@code app.email.from}으로 발신자 주소 강제. 발송 실패는 {@link EmailDeliveryException}로
 * 래핑되어 호출자가 audit + 5xx 결정을 내릴 수 있다.
 */
@Service
@Profile("prod")
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailService(JavaMailSender mailSender,
                            @Value("${app.email.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        try {
            mailSender.send(msg);
        } catch (MailException e) {
            throw new EmailDeliveryException("SMTP send failed: to=" + to, e);
        }
    }
}
