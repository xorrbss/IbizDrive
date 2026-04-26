package com.ibizdrive.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link DbUserDetailsService} 단위 테스트 — {@link UserRepository}는 Mockito로 모킹.
 *
 * <p>검증 영역:
 * <ul>
 *   <li>이메일 lowercase 정규화 (caller 책임 — 본 서비스가 trim + lowercase 적용)</li>
 *   <li>미존재 사용자 → {@link UsernameNotFoundException}</li>
 *   <li>{@code IbizDriveUserDetails} 상태 매핑 — locked_at, deleted_at, is_active, role</li>
 * </ul>
 */
class DbUserDetailsServiceTest {

    private UserRepository userRepository;
    private DbUserDetailsService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new DbUserDetailsService(userRepository);
    }

    @Test
    void loadUserByUsername_normalizesEmail_toLowercaseAndTrim() {
        User user = newUser(Role.MEMBER);
        when(userRepository.findActiveByEmail(eq("alice@example.com")))
            .thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("  Alice@Example.COM  ");

        assertEquals("alice@example.com", details.getUsername());
    }

    @Test
    void loadUserByUsername_nullEmail_throwsUsernameNotFound() {
        when(userRepository.findActiveByEmail(eq(""))).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
            () -> service.loadUserByUsername(null));
    }

    @Test
    void loadUserByUsername_userNotFound_throws() {
        when(userRepository.findActiveByEmail(eq("ghost@example.com")))
            .thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
            () -> service.loadUserByUsername("ghost@example.com"));
    }

    @Test
    void loadUserByUsername_activeMember_mapsAllStateAccessors() {
        User user = newUser(Role.MEMBER);
        when(userRepository.findActiveByEmail(eq("alice@example.com")))
            .thenReturn(Optional.of(user));

        UserDetails d = service.loadUserByUsername("alice@example.com");

        assertEquals("alice@example.com", d.getUsername());
        assertEquals("{bcrypt}hash", d.getPassword());
        assertTrue(d.isEnabled());
        assertTrue(d.isAccountNonLocked());
        assertTrue(d.isAccountNonExpired());
        assertTrue(d.isCredentialsNonExpired());
        assertTrue(d.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_MEMBER")));
    }

    @Test
    void loadUserByUsername_lockedUser_setsAccountNonLockedFalse() throws Exception {
        User user = newUser(Role.MEMBER);
        setField(user, "lockedAt", OffsetDateTime.now());
        when(userRepository.findActiveByEmail(eq("alice@example.com")))
            .thenReturn(Optional.of(user));

        UserDetails d = service.loadUserByUsername("alice@example.com");

        assertFalse(d.isAccountNonLocked());
    }

    @Test
    void loadUserByUsername_inactiveUser_setsEnabledFalse() throws Exception {
        User user = newUser(Role.MEMBER);
        setField(user, "isActive", false);
        when(userRepository.findActiveByEmail(eq("alice@example.com")))
            .thenReturn(Optional.of(user));

        UserDetails d = service.loadUserByUsername("alice@example.com");

        assertFalse(d.isEnabled());
    }

    @Test
    void loadUserByUsername_softDeletedUser_setsEnabledFalse() throws Exception {
        // 실무에선 findActiveByEmail이 deleted를 거른다. 본 테스트는 어댑터 게이트가
        // 이중 안전장치임을 검증.
        User user = newUser(Role.MEMBER);
        setField(user, "deletedAt", OffsetDateTime.now());
        when(userRepository.findActiveByEmail(eq("alice@example.com")))
            .thenReturn(Optional.of(user));

        UserDetails d = service.loadUserByUsername("alice@example.com");

        assertFalse(d.isEnabled());
    }

    @Test
    void loadUserByUsername_admin_mapsToRoleAdminAuthority() {
        User user = newUser(Role.ADMIN);
        when(userRepository.findActiveByEmail(eq("admin@example.com")))
            .thenReturn(Optional.of(user));
        // email 필드를 admin 이메일로 갱신
        try {
            setField(user, "email", "admin@example.com");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        UserDetails d = service.loadUserByUsername("admin@example.com");

        assertTrue(d.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    /** Test helper — 표준 active user 생성. 추가 상태는 setField로 주입. */
    private User newUser(Role role) {
        return new User(
            UUID.randomUUID(),
            "alice@example.com",
            "Alice",
            "{bcrypt}hash",
            role,
            true,    // isActive
            false,   // mustChangePassword
            OffsetDateTime.now()
        );
    }

    /** 테스트 한정 필드 주입 — User entity의 setter를 추가하지 않기 위함. */
    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }
}
