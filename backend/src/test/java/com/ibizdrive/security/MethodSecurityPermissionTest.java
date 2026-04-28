package com.ibizdrive.security;

import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringJUnitConfig(classes = {
    MethodSecurityConfig.class,
    PermissionService.class,
    IbizDrivePermissionEvaluator.class,
    MethodSecurityPermissionTest.ProbeConfig.class
})
class MethodSecurityPermissionTest {

    @jakarta.annotation.Resource
    private Probe probe;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void hasPermission_allowsAdminThroughMethodSecurity() {
        authenticate(Role.ADMIN);

        assertThat(probe.readFolder("folder_1")).isEqualTo("ok");
    }

    @Test
    void hasPermission_deniesMemberThroughMethodSecurity() {
        authenticate(Role.MEMBER);

        assertThatThrownBy(() -> probe.readFolder("folder_1"))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void hasPermission_deniesInvalidPermissionString() {
        authenticate(Role.ADMIN);

        assertThatThrownBy(() -> probe.invalidPermission("folder_1"))
            .isInstanceOf(AccessDeniedException.class);
    }

    private static void authenticate(Role role) {
        IbizDriveUserDetails principal = principal(role);
        var auth = UsernamePasswordAuthenticationToken.authenticated(
            principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static IbizDriveUserDetails principal(Role role) {
        User u = new User(
            UUID.randomUUID(),
            role.name().toLowerCase() + "@example.com",
            role.name(),
            "{bcrypt}$2a$12$dummyhashvalueformethodsecurity1234567890abcdef",
            role,
            true,
            false,
            OffsetDateTime.now()
        );
        return new IbizDriveUserDetails(u);
    }

    @Configuration
    static class ProbeConfig {
        @Bean
        Probe probe() {
            return new Probe();
        }
    }

    static class Probe {
        @PreAuthorize("hasPermission(#id, 'folder', 'READ')")
        public String readFolder(String id) {
            return "ok";
        }

        @PreAuthorize("hasPermission(#id, 'folder', 'NOT_A_PERMISSION')")
        public String invalidPermission(String id) {
            return "not-ok";
        }
    }
}
