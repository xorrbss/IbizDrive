package com.ibizdrive.permission;

import com.ibizdrive.user.Role;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A3.3 — {@link PermissionCacheKeyService} 단위 테스트.
 *
 * <p>{@code effectivePermissionsCacheKey}는 frontend가 권한 변경을 감지하여
 * Query 캐시를 invalidate 하는 trigger (docs/01 §6, docs/02 §7.4).
 *
 * <p>요건:
 * <ul>
 *   <li>결정적 — 같은 (userId, role) → 같은 key</li>
 *   <li>충돌 회피 — 다른 role/user → 다른 key</li>
 *   <li>형식 — SHA-256 hex prefix 16자, lowercase</li>
 *   <li>안전 — 원본 노출 회피 (실제 userId/role 문자열 노출 금지)</li>
 * </ul>
 */
class PermissionCacheKeyServiceTest {

    private final PermissionCacheKeyService service = new PermissionCacheKeyService();

    private static final UUID U1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID U2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void deterministic_sameInput_sameKey() {
        assertThat(service.computeKey(U1, Role.ADMIN))
            .isEqualTo(service.computeKey(U1, Role.ADMIN));
    }

    @Test
    void differentRole_yieldsDifferentKey() {
        assertThat(service.computeKey(U1, Role.ADMIN))
            .isNotEqualTo(service.computeKey(U1, Role.MEMBER));
    }

    @Test
    void differentUser_yieldsDifferentKey() {
        assertThat(service.computeKey(U1, Role.ADMIN))
            .isNotEqualTo(service.computeKey(U2, Role.ADMIN));
    }

    @Test
    void format_is16HexCharsLowercase() {
        String key = service.computeKey(U1, Role.AUDITOR);
        assertThat(key).hasSize(16).matches("[0-9a-f]{16}");
    }

    @Test
    void doesNotExposeRawIdentifiers() {
        String key = service.computeKey(U1, Role.ADMIN);
        assertThat(key).doesNotContain(U1.toString());
        assertThat(key).doesNotContain("ADMIN");
    }

    @Test
    void nullUserId_throws() {
        assertThatThrownBy(() -> service.computeKey(null, Role.ADMIN))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullRole_throws() {
        assertThatThrownBy(() -> service.computeKey(U1, null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
