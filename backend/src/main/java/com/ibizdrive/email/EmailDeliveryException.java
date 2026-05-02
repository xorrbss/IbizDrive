package com.ibizdrive.email;

/**
 * 이메일 전송 실패. 호출 측이 응답 정책(200 일관 vs 5xx)을 결정할 수 있도록 raw 실패만 표면화한다.
 */
public class EmailDeliveryException extends RuntimeException {
    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
