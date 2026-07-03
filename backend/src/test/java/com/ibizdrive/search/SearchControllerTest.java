package com.ibizdrive.search;

import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
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
 * A9.3 — {@link SearchController#search} 직접 호출 단위 테스트 (TrashControllerTest 패턴).
 *
 * <p>controller boundary 책임만 검증:
 * <ul>
 *   <li>q/type/ownerId/cursor/limit 파라미터 service에 그대로 전달 (type blank → null normalize)</li>
 *   <li>200 + body echo</li>
 *   <li>service의 IAE는 그대로 propagate (GlobalExceptionHandler 책임)</li>
 * </ul>
 */
class SearchControllerTest {

    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OWNER = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    private SearchQueryService service;
    private SearchController controller;
    private IbizDriveUserDetails memberPrincipal;

    @BeforeEach
    void setUp() {
        service = mock(SearchQueryService.class);
        controller = new SearchController(service);

        User u = new User(
            ACTOR, "user@example.com", "User", "{bcrypt}$2a$12$dummy",
            Role.MEMBER, true, false, OffsetDateTime.now()
        );
        memberPrincipal = new IbizDriveUserDetails(u);
    }

    @Test
    void search_normalCall_passesParamsToService() {
        SearchPage expected = new SearchPage(List.of(), null, 0L);
        when(service.search(eq(ACTOR), eq(Role.MEMBER), eq("foo"), isNull(), isNull(), isNull(), isNull()))
            .thenReturn(expected);

        ResponseEntity<SearchPage> res = controller.search("foo", null, null, null, null, memberPrincipal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isEqualTo(expected);
        verify(service).search(ACTOR, Role.MEMBER, "foo", null, null, null, null);
    }

    @Test
    void search_typeBlank_normalizedToNull() {
        SearchPage expected = new SearchPage(List.of(), null, 0L);
        when(service.search(eq(ACTOR), eq(Role.MEMBER), eq("foo"), isNull(), isNull(), isNull(), isNull()))
            .thenReturn(expected);

        controller.search("foo", "   ", null, null, null, memberPrincipal);

        verify(service).search(ACTOR, Role.MEMBER, "foo", null, null, null, null);
    }

    @Test
    void search_typeFile_passesTypeString() {
        SearchPage expected = new SearchPage(List.of(), null, 0L);
        when(service.search(eq(ACTOR), eq(Role.MEMBER), eq("foo"), eq("file"), isNull(), isNull(), isNull()))
            .thenReturn(expected);

        controller.search("foo", "file", null, null, null, memberPrincipal);

        verify(service).search(ACTOR, Role.MEMBER, "foo", "file", null, null, null);
    }

    @Test
    void search_ownerId_passedThrough() {
        SearchPage expected = new SearchPage(List.of(), null, 0L);
        when(service.search(eq(ACTOR), eq(Role.MEMBER), eq("foo"), isNull(), eq(OWNER), isNull(), isNull()))
            .thenReturn(expected);

        controller.search("foo", null, OWNER, null, null, memberPrincipal);

        verify(service).search(ACTOR, Role.MEMBER, "foo", null, OWNER, null, null);
    }

    @Test
    void search_cursorAndLimit_passedThrough() {
        String cursor = "abc123";
        SearchPage expected = new SearchPage(List.of(), "next", -1L);
        when(service.search(eq(ACTOR), eq(Role.MEMBER), eq("foo"), isNull(), isNull(), eq(cursor), eq(25)))
            .thenReturn(expected);

        ResponseEntity<SearchPage> res = controller.search("foo", null, null, cursor, 25, memberPrincipal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().nextCursor()).isEqualTo("next");
        verify(service).search(ACTOR, Role.MEMBER, "foo", null, null, cursor, 25);
    }

    @Test
    void search_serviceThrowsInvalidQuery_propagates() {
        when(service.search(any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(new IllegalArgumentException("INVALID_SEARCH_QUERY"));

        assertThatThrownBy(() -> controller.search("a", null, null, null, null, memberPrincipal))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("INVALID_SEARCH_QUERY");
    }
}
