package com.ibizdrive.common.error;

/**
 * 리소스(폴더/파일/권한 grant row 등)를 조회할 수 없을 때 — 404 매핑.
 *
 * <p>endpoint 의 {@code :id} 검증 실패 또는 service 단의 lookup 부재 모두 동일 envelope ({@code NOT_FOUND}) 으로
 * 노출. {@code message} 는 디버깅용 — 운영에서는 envelope message 만 사용 (구체적 ID 노출 회피는 service 호출처가 결정).
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
