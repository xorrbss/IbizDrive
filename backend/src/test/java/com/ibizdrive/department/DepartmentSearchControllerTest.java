package com.ibizdrive.department;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A16 — {@link DepartmentSearchController#search} 직접 호출 단위 테스트.
 * {@link com.ibizdrive.user.UserSearchControllerTest} 1:1 답습.
 */
class DepartmentSearchControllerTest {

    private DepartmentSearchService service;
    private DepartmentSearchController controller;

    @BeforeEach
    void setUp() {
        service = mock(DepartmentSearchService.class);
        controller = new DepartmentSearchController(service);
    }

    @Test
    void search_normalCall_passesParamsToService() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        DepartmentSummaryDto dto = new DepartmentSummaryDto(id, "Engineering");
        when(service.search(eq("eng"), isNull())).thenReturn(List.of(dto));

        ResponseEntity<DepartmentSearchResponse> res = controller.search("eng", null);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().items()).hasSize(1);
        assertThat(res.getBody().items().get(0).id()).isEqualTo(id);
        assertThat(res.getBody().items().get(0).name()).isEqualTo("Engineering");
        verify(service).search("eng", null);
    }

    @Test
    void search_limitPassedThrough() {
        when(service.search(eq("foo"), eq(10))).thenReturn(List.of());

        controller.search("foo", 10);

        verify(service).search("foo", 10);
    }

    @Test
    void search_emptyResults_returns200WithEmptyList() {
        when(service.search(eq("xyz"), isNull())).thenReturn(List.of());

        ResponseEntity<DepartmentSearchResponse> res = controller.search("xyz", null);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().items()).isEmpty();
    }

    @Test
    void search_serviceThrowsInvalidQuery_propagates() {
        when(service.search(any(), any())).thenThrow(new IllegalArgumentException("INVALID_SEARCH_QUERY"));

        assertThatThrownBy(() -> controller.search("a", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("INVALID_SEARCH_QUERY");
    }
}
