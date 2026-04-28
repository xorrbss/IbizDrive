package com.ibizdrive.permission;

import com.ibizdrive.user.Role;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * A3.3 — {@code effectivePermissionsCacheKey} 산출 (docs/02 §7.4, ADR #26).
 *
 * <p>{@link com.ibizdrive.auth.dto.LoginResponse}에 포함되어 frontend가 권한 변경을 감지·캐시
 * invalidate 하는 trigger 역할. 원본 식별자(userId/role 문자열) 노출 회피를 위해 SHA-256 해시
 * prefix 16자(lowercase hex)를 사용한다.
 *
 * <p>입력 도메인은 {@code "<userId>:<ROLE>:v1"} — 버전 토큰 {@code v1}은 권한 매트릭스 정의 자체가
 * 변경될 때(예: Preset 재정의, role→permission 매핑 변경) 한꺼번에 invalidate 시키기 위한 hook.
 * MVP는 {@code v1} 고정. 향후 변경 시 ADR로 기록하고 같은 (userId, role)이라도 새 key를
 * 발급하여 모든 클라이언트 캐시를 일괄 무효화한다.
 *
 * <p>본 서비스는 stateless·순수함수. ADR #26에 따라 A3는 user-level 권한만 다루므로 입력 인자에
 * resourceId가 없다.
 */
@Service
public class PermissionCacheKeyService {

    /** 권한 매트릭스 자체의 버전 토큰. 매트릭스 정의 변경 시 bump → 모든 캐시 일괄 무효화. */
    private static final String MATRIX_VERSION = "v1";

    /** Hex prefix 길이 — 64bit (충돌 확률 < 10^-9 @ 1억 사용자). */
    private static final int KEY_LENGTH = 16;

    public String computeKey(UUID userId, Role role) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        String input = userId + ":" + role.name() + ":" + MATRIX_VERSION;
        byte[] digest = sha256(input.getBytes(StandardCharsets.UTF_8));
        return toHex(digest, KEY_LENGTH);
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 JDK 표준 — 발생 불가.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String toHex(byte[] bytes, int length) {
        StringBuilder sb = new StringBuilder(length);
        int byteCount = (length + 1) / 2;
        for (int i = 0; i < byteCount && sb.length() < length; i++) {
            int b = bytes[i] & 0xFF;
            sb.append(Character.forDigit(b >>> 4, 16));
            if (sb.length() < length) {
                sb.append(Character.forDigit(b & 0x0F, 16));
            }
        }
        return sb.toString();
    }
}
