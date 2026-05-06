package com.ibizdrive.user;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link User} 도메인 메서드 단위 테스트.
 *
 * <p>본 클래스는 admin-user-mgmt에서 추가한 {@link User#deactivate()} /
 * {@link User#reactivate()} 상태 전이를 검증한다. 다른 도메인 메서드
 * ({@code changeRoleTo}, {@code clearMustChangePassword} 등)는 service 통합
 * 테스트에서 간접 커버되므로 본 클래스에는 포함하지 않는다.
 */
class UserTest {

    @Test
    void deactivate_setsIsActiveToFalse() {
        User user = activeUser();
        assertThat(user.isActive()).isTrue();

        user.deactivate();

        assertThat(user.isActive()).isFalse();
    }

    @Test
    void deactivate_isIdempotent_whenAlreadyInactive() {
        User user = activeUser();
        user.deactivate();
        user.deactivate(); // 재호출 — 예외 없이 false 유지

        assertThat(user.isActive()).isFalse();
    }

    @Test
    void reactivate_setsIsActiveToTrue() {
        User user = activeUser();
        user.deactivate();
        assertThat(user.isActive()).isFalse();

        user.reactivate();

        assertThat(user.isActive()).isTrue();
    }

    @Test
    void reactivate_isIdempotent_whenAlreadyActive() {
        User user = activeUser();
        user.reactivate(); // 이미 active — 예외 없이 true 유지

        assertThat(user.isActive()).isTrue();
    }

    // ── changeDisplayName (admin-user-search-update, Wave 1 — T1) ─────────────

    @Test
    void changeDisplayName_replacesValue() {
        User user = activeUser();

        user.changeDisplayName("Renamed User");

        assertThat(user.getDisplayName()).isEqualTo("Renamed User");
    }

    @Test
    void changeDisplayName_rejectsNull() {
        User user = activeUser();
        assertThatThrownBy(() -> user.changeDisplayName(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank");
    }

    @Test
    void changeDisplayName_rejectsBlank() {
        User user = activeUser();
        assertThatThrownBy(() -> user.changeDisplayName("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank");
    }

    @Test
    void changeDisplayName_rejectsOver100Chars() {
        User user = activeUser();
        String tooLong = "a".repeat(101);
        assertThatThrownBy(() -> user.changeDisplayName(tooLong))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("100");
    }

    @Test
    void changeDisplayName_acceptsExactly100Chars() {
        User user = activeUser();
        String boundary = "a".repeat(100);

        user.changeDisplayName(boundary);

        assertThat(user.getDisplayName()).isEqualTo(boundary);
    }

    private static User activeUser() {
        return new User(
            UUID.randomUUID(),
            "test@example.com",
            "Test User",
            "{bcrypt}$2a$12$dummyhashfortestonlydummyhashfortestonlydummyhashfortest",
            Role.MEMBER,
            true,
            false,
            OffsetDateTime.now()
        );
    }
}
