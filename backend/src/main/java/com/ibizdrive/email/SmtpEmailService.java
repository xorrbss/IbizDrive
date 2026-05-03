package com.ibizdrive.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>{@code app.email.from}으로 발신자 주소 강제. 발송 실패({@link MailException})는 fire-and-forget
 * 정책(ADR #45)에 따라 본 impl 내부에서 ERROR 로그로 흡수되며 caller에 전파되지 않는다 —
 * 비동기 호출이므로 어차피 caller에 도달 불가.
 */
@Service
@Profile("prod")
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

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
            // ADR #45 — fire-and-forget: 비동기 호출이므로 caller에 도달 불가. 운영자가 ERROR 로그로 인지.
            log.error("SMTP send failed to={}", to, e);
        }
    }
}
