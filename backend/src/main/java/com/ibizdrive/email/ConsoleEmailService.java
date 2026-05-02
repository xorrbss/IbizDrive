package com.ibizdrive.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * dev/default 프로파일용 EmailService — 실제 SMTP를 거치지 않고 본문을 stdout/log에 dump.
 *
 * <p>{@code @Profile("!prod")}로 prod 프로파일에서는 비활성. {@link SmtpEmailService}가 prod에서
 * 본 빈을 대체한다(두 빈이 동시에 등록되지 않도록 프로파일 분리만으로 충돌 회피).
 *
 * <p>비밀번호 재설정 토큰처럼 평문 토큰이 본문에 노출되는 메시지를 그대로 출력하므로 운영 환경에서
 * 본 빈이 활성화되면 안 된다. {@code application-prod.yml}이 prod 프로파일을 강제하므로
 * BETA-RELEASE 게이트와 동일한 보호가 적용된다.
 */
@Service
@Profile("!prod")
public class ConsoleEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(ConsoleEmailService.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("[ConsoleEmailService] to={} subject={}\n----- BODY -----\n{}\n----- END -----",
                to, subject, body);
    }
}
