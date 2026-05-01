package com.ibizdrive.department;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

/**
 * A16 — {@link DepartmentSearchService} 단위 테스트. {@link com.ibizdrive.user.UserSearchServiceTest}
 * 1:1 답습 — q normalize → minLen → LIKE escape → limit clamp → DTO map.
 */
class DepartmentSearchServiceTest {

    private DepartmentRepository departmentRepository;
    private DepartmentSearchService service;

    @BeforeEach
    void setUp() {
        departmentRepository = mock(DepartmentRepository.class);
        service = new DepartmentSearchService(departmentRepository);
    }

    // ── q normalize + minLength ────────────────────────────────────────

    @Test
    void search_minLengthViolation_throws() {
        assertThatThrownBy(() -> service.search("a", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("INVALID_SEARCH_QUERY");
        verifyNoInteractions(departmentRepository);
    }

    @Test
    void search_blankQuery_throws() {
        assertThatThrownBy(() -> service.search("   ", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("INVALID_SEARCH_QUERY");
        verifyNoInteractions(departmentRepository);
    }

    @Test
    void search_nullQuery_throws() {
        assertThatThrownBy(() -> service.search(null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("INVALID_SEARCH_QUERY");
        verifyNoInteractions(departmentRepository);
    }

    @Test
    void search_trimsAndLowercasesQuery_beforeMinLengthCheck() {
        assertThatThrownBy(() -> service.search(" A ", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("INVALID_SEARCH_QUERY");
    }

    // ── LIKE escape ────────────────────────────────────────────────────

    @Test
    void search_escapesLikeWildcards() {
        when(departmentRepository.searchActive(anyString(), anyInt())).thenReturn(List.of());
        service.search("50%_team", null);

        ArgumentCaptor<String> patternCap = forClass(String.class);
        verify(departmentRepository).searchActive(patternCap.capture(), anyInt());
        assertThat(patternCap.getValue()).isEqualTo("%50\\%\\_team%");
    }

    @Test
    void search_lowercasesPatternForCaseInsensitiveMatch() {
        when(departmentRepository.searchActive(anyString(), anyInt())).thenReturn(List.of());
        service.search("Engineering", null);

        ArgumentCaptor<String> patternCap = forClass(String.class);
        verify(departmentRepository).searchActive(patternCap.capture(), anyInt());
        assertThat(patternCap.getValue()).isEqualTo("%engineering%");
    }

    // ── limit clamp ────────────────────────────────────────────────────

    @Test
    void search_limitNull_usesDefault20() {
        when(departmentRepository.searchActive(anyString(), anyInt())).thenReturn(List.of());
        service.search("test", null);
        verify(departmentRepository).searchActive(anyString(), org.mockito.ArgumentMatchers.eq(20));
    }

    @Test
    void search_limitOverCap_clampsTo50() {
        when(departmentRepository.searchActive(anyString(), anyInt())).thenReturn(List.of());
        service.search("test", 999);
        verify(departmentRepository).searchActive(anyString(), org.mockito.ArgumentMatchers.eq(50));
    }

    @Test
    void search_limitZeroOrNegative_usesDefault20() {
        when(departmentRepository.searchActive(anyString(), anyInt())).thenReturn(List.of());
        service.search("test", 0);
        verify(departmentRepository).searchActive(anyString(), org.mockito.ArgumentMatchers.eq(20));

        service.search("test", -5);
        verify(departmentRepository, org.mockito.Mockito.times(2))
            .searchActive(anyString(), org.mockito.ArgumentMatchers.eq(20));
    }

    @Test
    void search_limitInRange_passesThrough() {
        when(departmentRepository.searchActive(anyString(), anyInt())).thenReturn(List.of());
        service.search("test", 10);
        verify(departmentRepository).searchActive(anyString(), org.mockito.ArgumentMatchers.eq(10));
    }

    // ── DTO mapping ────────────────────────────────────────────────────

    @Test
    void search_mapsDepartmentToSummaryDto() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Department d = new Department(id, "Engineering", OffsetDateTime.now());
        when(departmentRepository.searchActive(anyString(), anyInt())).thenReturn(List.of(d));

        List<DepartmentSummaryDto> result = service.search("eng", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(id);
        assertThat(result.get(0).name()).isEqualTo("Engineering");
    }
}
