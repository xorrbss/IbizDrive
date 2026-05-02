package com.ibizdrive.auth.password.dto;

/**
 * 일반 메시지 응답 (forgot/reset/change endpoint 공용).
 *
 * <p>forgot은 가입/미가입 모두 동일 메시지. reset/change는 성공 시 같은 shape로 반환되어
 * 프론트가 단일 success path로 처리 가능.
 */
public record MessageResponse(String message) {

    public static MessageResponse of(String message) {
        return new MessageResponse(message);
    }
}
