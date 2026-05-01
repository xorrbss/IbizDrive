package com.ibizdrive.user;

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
 * A14.2 — {@link UserSearchController#search} 직접 호출 단위 테스트 (SearchControllerTest 패턴).
 *
 * <p>controller boundary 책임만 검증:
 * <ul>
 *   <li>q/limit 파라미터 service에 그대로 전달</li>
 *   <li>200 + body echo (`UserSearchResponse`)</li>
 *   <li>service의 IAE 그대로 propagate</li>
 * </ul>
 */
class UserSearchControllerTest {

    private UserSearchService service;
    private UserSearchController controller;

    @BeforeEach
    void setUp() {
        service = mock(UserSearchService.class);
        controller = new UserSearchController(service);
    }

    @Test
    void search_normalCall_passesParamsToService() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UserSummaryDto dto = new UserSummaryDto(id, "Alice", "alice@example.com");
        when(service.search(eq("alice"), isNull())).thenReturn(List.of(dto));

        ResponseEntity<UserSearchResponse> res = controller.search("alice", null);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().items()).hasSize(1);
        assertThat(res.getBody().items().get(0).id()).isEqualTo(id);
        assertThat(res.getBody().items().get(0).displayName()).isEqualTo("Alice");
        assertThat(res.getBody().items().get(0).email()).isEqualTo("alice@example.com");
        verify(service).search("alice", null);
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

        ResponseEntity<UserSearchResponse> res = controller.search("xyz", null);

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
