package com.ibizdrive.auth.password;

/**
 * 비밀번호 재설정 토큰이 유효하지 않음 (만료/사용/미존재). 호출자가 400 INVALID_TOKEN으로 매핑.
 */
public class InvalidPasswordResetTokenException extends RuntimeException {

    public InvalidPasswordResetTokenException(String reason) {
        super(reason);
    }
}
