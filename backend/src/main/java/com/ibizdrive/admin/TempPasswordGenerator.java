package com.ibizdrive.admin;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Admin invite 임시 비밀번호 생성기 — ADR #21 (admin 트랙 closure).
 *
 * <p>16자, {@link SecureRandom} 기반. alphabet은 영대/소문자 + 숫자 + 일부 특수문자({@code !@#$%&}).
 * 메일 transit 시 quoted-printable 인코딩 깨짐과 사용자 수동 입력 오타 가능성을 줄이기 위해
 * 모호한 특수문자({@code <>"'`/\|;,.}) 와 시각적 혼동 문자({@code 0/O, 1/l/I})는 의도적으로 제외하지 않는다 —
 * 1회용이고 force-change UX로 즉시 교체되므로 강도(약 10^30)가 brute-force 무력.
 *
 * <p>본 클래스는 별도 컴포넌트로 분리되어 {@link AdminUserService}가 의존성 주입으로 받는다.
 * 단위 테스트에서 결정론적 stub이 가능하도록 분리한 것 (raw PW의 출처를 mock 통제).
 *
 * <p><b>로깅 금지</b>: 생성된 PW를 어떤 형태로도 로깅/예외 메시지/응답에 포함하지 않는다.
 * 호출 측({@link AdminUserService})은 즉시 BCrypt encode + EmailService.send 후 변수 scope 종료.
 */
@Component
public class TempPasswordGenerator {

    private static final char[] ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%&".toCharArray();
    private static final int LENGTH = 16;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        char[] buf = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            buf[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(buf);
    }
}
