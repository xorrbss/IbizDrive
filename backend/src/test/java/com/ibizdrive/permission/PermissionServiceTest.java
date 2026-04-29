package com.ibizdrive.permission;

import com.ibizdrive.user.Role;
import com.ibizdrive.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * {@link PermissionService} 단위 테스트 (ADR #26 — user-level MVP).
 *
 * <p>folder/file 도메인 부재 상태에서 user-level (Role 기반) 평가만 수행한다.
 * resource-level 평가는 A4 — 본 phase의 SpEL 인자 ({@code resource}, {@code resourceId})는
 * evaluator가 받지만 service 평가에서는 미사용 + TODO.
 */
class PermissionServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final PermissionService service = new PermissionService(
        mock(UserRepository.class),
        mock(PermissionRepository.class),
        mock(ApplicationEventPublisher.class)
    );

    @Test
    void admin_grants_all_nine_permissions() {
        for (Permission p : Permission.values()) {
            assertTrue(
                service.check(USER_ID, Role.ADMIN, "folder", "any-id", p),
                "ADMIN must hold " + p
            );
        }
    }

    @Test
    void auditor_grants_only_read() {
        for (Permission p : Permission.values()) {
            boolean expected = (p == Permission.READ);
            assertEquals(
                expected,
                service.check(USER_ID, Role.AUDITOR, "folder", "any-id", p),
                "AUDITOR " + p + " expected=" + expected
            );
        }
    }

    @Test
    void member_denies_all_resource_level_absent() {
        for (Permission p : Permission.values()) {
            assertFalse(
                service.check(USER_ID, Role.MEMBER, "folder", "any-id", p),
                "MEMBER must not hold " + p + " (resource-level deferred to A4)"
            );
        }
    }

    @Test
    void null_role_denies_all() {
        assertFalse(service.check(USER_ID, null, "folder", "any-id", Permission.READ));
    }

    @Test
    void resource_arguments_are_ignored_in_mvp() {
        // A4 hook — resource/resourceId가 다르더라도 동일 평가. 회귀 가드로 명시.
        assertTrue(service.check(USER_ID, Role.ADMIN, "file", "x", Permission.EDIT));
        assertTrue(service.check(USER_ID, Role.ADMIN, "folder", "y", Permission.EDIT));
        assertTrue(service.check(USER_ID, Role.ADMIN, null, null, Permission.EDIT));
    }
}
