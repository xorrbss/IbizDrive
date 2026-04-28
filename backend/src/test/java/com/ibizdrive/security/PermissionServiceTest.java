package com.ibizdrive.security;

import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionServiceTest {

    private final PermissionService service = new PermissionService();

    @ParameterizedTest
    @EnumSource(Permission.class)
    void admin_hasEveryPermission(Permission permission) {
        assertThat(service.check(principal(Role.ADMIN), "folder", "folder_1", permission)).isTrue();
    }

    @Test
    void auditor_hasReadOnly() {
        IbizDriveUserDetails auditor = principal(Role.AUDITOR);

        assertThat(service.check(auditor, "folder", "folder_1", Permission.READ)).isTrue();
        assertThat(service.check(auditor, "folder", "folder_1", Permission.DOWNLOAD)).isFalse();
        assertThat(service.check(auditor, "folder", "folder_1", Permission.PURGE)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(Permission.class)
    void member_hasNoRoleLevelPermission(Permission permission) {
        assertThat(service.check(principal(Role.MEMBER), "folder", "folder_1", permission)).isFalse();
    }

    @Test
    void check_deniesUnknownTargetType() {
        assertThat(service.check(principal(Role.ADMIN), "unknown", "folder_1", Permission.READ)).isFalse();
    }

    @Test
    void check_deniesMissingPrincipalOrPermission() {
        assertThat(service.check((IbizDriveUserDetails) null, "folder", "folder_1", Permission.READ)).isFalse();
        assertThat(service.check(principal(Role.ADMIN), "folder", "folder_1", null)).isFalse();
    }

    private static IbizDriveUserDetails principal(Role role) {
        User u = new User(
            UUID.randomUUID(),
            role.name().toLowerCase() + "@example.com",
            role.name(),
            "{bcrypt}$2a$12$dummyhashvalueforpermissiontests1234567890abcdef",
            role,
            true,
            false,
            OffsetDateTime.now()
        );
        return new IbizDriveUserDetails(u);
    }
}
