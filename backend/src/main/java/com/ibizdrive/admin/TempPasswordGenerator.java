package com.ibizdrive.admin;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 관리자 초대 시 사용할 임시 비밀번호 생성기 (m-admin-entry-rewrite, ADR #21).
 *
 * <p>16자 길이 + 영숫자({@code [A-Za-z0-9]}) + 소량 특수({@code !@#$%^&*}). Unicode block
 * 문자를 회피하고 BCrypt 인코더가 안전하게 처리할 수 있는 ASCII만 사용.
 *
 * <p>{@link SecureRandom}을 사용해 예측 불가능 보장. 16자 알파벳 ~70 entropy → BCrypt(strength=12)
 * 와 결합 시 brute-force 위협은 무시 가능. 본 PW는 mustChangePassword=true 정책에 의해
 * 사용자가 첫 로그인 직후 즉시 변경하므로 짧은 lifecycle에 한정된다.
 *
 * <p>ADR #19 정책 보증 (auth-password-policy, 2026-05-04): 출력은 항상 영문 ≥1 + 숫자 ≥1을 만족하도록
 * 첫 자리에 영문, 둘째 자리에 숫자를 주입한 뒤 Fisher-Yates shuffle로 위치를 섞는다. 공백 문자는
 * alphabet 자체에 부재 → whitespace 규칙도 자동 만족. 따라서 항상 {@code PasswordPolicyValidator}를 통과.
 *
 * <p>component로 주입 — 테스트는 mock으로 결정적 값을 반환하게 하여 검증 가능.
 */
@Component
public class TempPasswordGenerator {

    private static final int LENGTH = 16;
    private static final char[] ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*".toCharArray();
    private static final char[] ALPHA =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final char[] DIGIT = "0123456789".toCharArray();

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        char[] out = new char[LENGTH];
        // 영문 1자 + 숫자 1자를 강제 주입하여 ADR #19 missing_alpha/missing_digit 위반 회피.
        out[0] = ALPHA[random.nextInt(ALPHA.length)];
        out[1] = DIGIT[random.nextInt(DIGIT.length)];
        for (int i = 2; i < LENGTH; i++) {
            out[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        // Fisher-Yates shuffle — 강제 주입 위치 노출 회피.
        for (int i = LENGTH - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = out[i];
            out[i] = out[j];
            out[j] = tmp;
        }
        return new String(out);
    }
}
