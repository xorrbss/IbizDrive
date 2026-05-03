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
 * <p>component로 주입 — 테스트는 mock으로 결정적 값을 반환하게 하여 검증 가능.
 */
@Component
public class TempPasswordGenerator {

    private static final int LENGTH = 16;
    private static final char[] ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*".toCharArray();

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        char[] out = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            out[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(out);
    }
}
