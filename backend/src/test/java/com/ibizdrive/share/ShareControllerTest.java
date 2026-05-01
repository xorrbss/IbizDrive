package com.ibizdrive.share;

import com.ibizdrive.common.error.ResourceNotFoundException;
import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.Role;
import com.ibizdrive.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A10.5 — {@link ShareController} 직접 호출 단위 테스트 (TrashControllerTest 패턴).
 *
 * <p>controller 책임만 검증: service delegation 인자 + 응답 status/envelope. SpEL @PreAuthorize 평가는
 * Spring Security 컨텍스트 통합 테스트 책임 (별도). 본 테스트는 controller boundary만 본다.
 */
class ShareControllerTest {

    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private ShareCommandService commandService;
    private ShareQueryService queryService;
    private ShareController controller;
    private IbizDriveUserDetails principal;

    @BeforeEach
    void setUp() {
        commandService = mock(ShareCommandService.class);
        queryService = mock(ShareQueryService.class);
        controller = new ShareController(commandService, queryService);

        User u = new User(
            ACTOR, "user@example.com", "User", "{bcrypt}$2a$12$dummy",
            Role.MEMBER, true, false, OffsetDateTime.now()
        );
        principal = new IbizDriveUserDetails(u);
    }

    // ── POST /api/files/{fileId}/share ──────────────────────────────────

    @Test
    void create_returns201WithSharesEnvelope() {
        UUID fileId = UUID.randomUUID();
        ShareCreateRequest req = new ShareCreateRequest(
            List.of(new ShareCreateRequest.Subject("user", UUID.randomUUID())),
            "edit", null, null
        );
        Share s = makeShare(fileId);
        when(commandService.createShares(eq(fileId), eq(req), eq(ACTOR)))
            .thenReturn(List.of(s));

        ResponseEntity<Map<String, List<ShareDto>>> res = controller.create(fileId, req, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody()).containsKey("shares");
        List<ShareDto> dtos = res.getBody().get("shares");
        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).id()).isEqualTo(s.getId());
        verify(commandService).createShares(fileId, req, ACTOR);
    }

    @Test
    void create_multipleSubjects_returnsAllSharesInOrder() {
        UUID fileId = UUID.randomUUID();
        ShareCreateRequest req = new ShareCreateRequest(
            List.of(
                new ShareCreateRequest.Subject("user", UUID.randomUUID()),
                new ShareCreateRequest.Subject("department", UUID.randomUUID())
            ),
            "read", null, "msg"
        );
        Share s1 = makeShare(fileId);
        Share s2 = makeShare(fileId);
        when(commandService.createShares(eq(fileId), eq(req), eq(ACTOR)))
            .thenReturn(List.of(s1, s2));

        ResponseEntity<Map<String, List<ShareDto>>> res = controller.create(fileId, req, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().get("shares")).hasSize(2);
        assertThat(res.getBody().get("shares").get(0).id()).isEqualTo(s1.getId());
        assertThat(res.getBody().get("shares").get(1).id()).isEqualTo(s2.getId());
    }

    @Test
    void create_propagatesResourceNotFound() {
        UUID fileId = UUID.randomUUID();
        ShareCreateRequest req = new ShareCreateRequest(
            List.of(new ShareCreateRequest.Subject("user", UUID.randomUUID())),
            "read", null, null
        );
        when(commandService.createShares(any(), any(), any()))
            .thenThrow(new ResourceNotFoundException("file not found"));

        assertThatThrownBy(() -> controller.create(fileId, req, principal))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_propagatesIllegalArgument() {
        UUID fileId = UUID.randomUUID();
        ShareCreateRequest req = new ShareCreateRequest(
            List.of(new ShareCreateRequest.Subject("user", UUID.randomUUID())),
            "share", null, null
        );
        when(commandService.createShares(any(), any(), any()))
            .thenThrow(new IllegalArgumentException("preset 'share' is not persistable"));

        assertThatThrownBy(() -> controller.create(fileId, req, principal))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ── DELETE /api/shares/{shareId} ────────────────────────────────────

    @Test
    void revoke_returns204() {
        UUID shareId = UUID.randomUUID();

        ResponseEntity<Void> res = controller.revoke(shareId, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(commandService).revokeShare(shareId, ACTOR);
    }

    @Test
    void revoke_propagatesNotFound() {
        UUID shareId = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("share not found"))
            .when(commandService).revokeShare(shareId, ACTOR);

        assertThatThrownBy(() -> controller.revoke(shareId, principal))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── GET /api/shares/by-me ───────────────────────────────────────────

    @Test
    void listByMe_returns200WithPage() {
        SharePage expected = new SharePage(List.of(), null);
        when(queryService.listByMe(eq(ACTOR), isNull(), isNull())).thenReturn(expected);

        ResponseEntity<SharePage> res = controller.listByMe(null, null, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isSameAs(expected);
        verify(queryService).listByMe(ACTOR, null, null);
    }

    @Test
    void listByMe_passesCursorAndLimitThrough() {
        SharePage expected = new SharePage(List.of(), "next");
        when(queryService.listByMe(eq(ACTOR), eq("opaque-cursor"), eq(25))).thenReturn(expected);

        ResponseEntity<SharePage> res = controller.listByMe("opaque-cursor", 25, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().nextCursor()).isEqualTo("next");
        verify(queryService).listByMe(ACTOR, "opaque-cursor", 25);
    }

    @Test
    void listByMe_propagatesInvalidCursor() {
        when(queryService.listByMe(any(), any(), any()))
            .thenThrow(new IllegalArgumentException("invalid cursor"));

        assertThatThrownBy(() -> controller.listByMe("bad", null, principal))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ── GET /api/shares/with-me ─────────────────────────────────────────

    @Test
    void listWithMe_returns200WithPage() {
        SharePage expected = new SharePage(List.of(), null);
        when(queryService.listWithMe(eq(ACTOR), isNull(), isNull())).thenReturn(expected);

        ResponseEntity<SharePage> res = controller.listWithMe(null, null, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isSameAs(expected);
        verify(queryService).listWithMe(ACTOR, null, null);
    }

    @Test
    void listWithMe_passesCursorAndLimitThrough() {
        SharePage expected = new SharePage(List.of(), "next");
        when(queryService.listWithMe(eq(ACTOR), eq("c2"), eq(10))).thenReturn(expected);

        controller.listWithMe("c2", 10, principal);

        verify(queryService).listWithMe(ACTOR, "c2", 10);
    }

    // ── helpers ────────────────────────────────────────────────────────

    private static Share makeShare(UUID fileId) {
        Share s = new Share();
        s.setId(UUID.randomUUID());
        s.setFileId(fileId);
        s.setFolderId(null);
        s.setPermissionId(UUID.randomUUID());
        s.setSharedBy(ACTOR);
        s.setCreatedAt(Instant.now());
        return s;
    }
}
