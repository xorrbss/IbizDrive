package com.ibizdrive.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

/**
 * A14.1 — {@link UserSearchService} 단위 테스트. repository 모킹으로
 * service 내부 (q normalize → minLen 검증 → LIKE escape → limit clamp → DTO map) 흐름 검증.
 *
 * <p>실제 Postgres LIKE 동작은 {@link UserRepositoryTest}가 검증.
 */
class UserSearchServiceTest {

    private UserRepository userRepository;
    private UserSearchService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new UserSearchService(userRepository);
    }

    // ── q normalize + minLength ────────────────────────────────────────

    @Test
    void search_minLengthViolation_throws() {
        assertThatThrownBy(() -> service.search("a", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("INVALID_SEARCH_QUERY");
        verifyNoInteractions(userRepository);
    }

    @Test
    void search_blankQuery_throws() {
        assertThatThrownBy(() -> service.search("   ", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("INVALID_SEARCH_QUERY");
        verifyNoInteractions(userRepository);
    }

    @Test
    void search_nullQuery_throws() {
        assertThatThrownBy(() -> service.search(null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("INVALID_SEARCH_QUERY");
        verifyNoInteractions(userRepository);
    }

    @Test
    void search_trimsAndLowercasesQuery_beforeMinLengthCheck() {
        // " A " → "a" (len=1) → violation
        assertThatThrownBy(() -> service.search(" A ", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("INVALID_SEARCH_QUERY");
    }

    // ── LIKE escape ────────────────────────────────────────────────────

    @Test
    void search_escapesLikeWildcards() {
        when(userRepository.searchActive(anyString(), anyInt())).thenReturn(List.of());
        service.search("50%_off", null);

        ArgumentCaptor<String> patternCap = forClass(String.class);
        verify(userRepository).searchActive(patternCap.capture(), anyInt());
        // % and _ should be backslash-escaped, surrounded by % wildcards, lowercased
        assertThat(patternCap.getValue()).isEqualTo("%50\\%\\_off%");
    }

    @Test
    void search_lowercasesPatternForCaseInsensitiveMatch() {
        when(userRepository.searchActive(anyString(), anyInt())).thenReturn(List.of());
        service.search("Alice", null);

        ArgumentCaptor<String> patternCap = forClass(String.class);
        verify(userRepository).searchActive(patternCap.capture(), anyInt());
        assertThat(patternCap.getValue()).isEqualTo("%alice%");
    }

    // ── limit clamp ────────────────────────────────────────────────────

    @Test
    void search_limitNull_usesDefault20() {
        when(userRepository.searchActive(anyString(), anyInt())).thenReturn(List.of());
        service.search("test", null);
        verify(userRepository).searchActive(anyString(), org.mockito.ArgumentMatchers.eq(20));
    }

    @Test
    void search_limitOverCap_clampsTo50() {
        when(userRepository.searchActive(anyString(), anyInt())).thenReturn(List.of());
        service.search("test", 999);
        verify(userRepository).searchActive(anyString(), org.mockito.ArgumentMatchers.eq(50));
    }

    @Test
    void search_limitZeroOrNegative_usesDefault20() {
        when(userRepository.searchActive(anyString(), anyInt())).thenReturn(List.of());
        service.search("test", 0);
        verify(userRepository).searchActive(anyString(), org.mockito.ArgumentMatchers.eq(20));

        service.search("test", -5);
        verify(userRepository, org.mockito.Mockito.times(2))
            .searchActive(anyString(), org.mockito.ArgumentMatchers.eq(20));
    }

    @Test
    void search_limitInRange_passesThrough() {
        when(userRepository.searchActive(anyString(), anyInt())).thenReturn(List.of());
        service.search("test", 10);
        verify(userRepository).searchActive(anyString(), org.mockito.ArgumentMatchers.eq(10));
    }

    // ── DTO mapping ────────────────────────────────────────────────────

    @Test
    void search_mapsUserToSummaryDto() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        User u = new User(id, "Alice@Example.com", "Alice", null, Role.MEMBER, true, false, OffsetDateTime.now());
        when(userRepository.searchActive(anyString(), anyInt())).thenReturn(List.of(u));

        List<UserSummaryDto> result = service.search("alice", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(id);
        assertThat(result.get(0).displayName()).isEqualTo("Alice");
        assertThat(result.get(0).email()).isEqualTo("Alice@Example.com");
    }
}
