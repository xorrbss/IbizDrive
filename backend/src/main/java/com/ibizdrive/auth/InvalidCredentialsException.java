package com.ibizdrive.auth;

/**
 * 자격 증명 실패 — 이메일 미존재 / PW 불일치 / 계정 비활성·관리자 잠금 모두 동일 응답으로 매핑된다
 * (계정 enumeration 방지, docs/03 §2.3).
 *
 * <p>HTTP 401 + body {@code { code: "UNAUTHORIZED", reason: "INVALID_CREDENTIALS" }}로 변환.
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid credentials");
    }
}
