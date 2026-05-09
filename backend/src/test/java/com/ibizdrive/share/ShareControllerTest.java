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
        UUID subjectId = UUID.randomUUID();
        ShareCreateRequest req = new ShareCreateRequest(
            List.of(new ShareCreateRequest.Subject("user", subjectId)),
            "edit", null, null
        );
        // A13 — service 반환은 join이 끝난 ShareDto.
        ShareDto dto = makeFileDto(fileId, "user", subjectId, "edit");
        when(commandService.createShares(eq(fileId), eq(req), eq(ACTOR)))
            .thenReturn(List.of(dto));

        ResponseEntity<Map<String, List<ShareDto>>> res = controller.create(fileId, req, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody()).containsKey("shares");
        List<ShareDto> dtos = res.getBody().get("shares");
        assertThat(dtos).hasSize(1);
        ShareDto out = dtos.get(0);
        assertThat(out.id()).isEqualTo(dto.id());
        // A13 wire 검증 — 3 join 필드가 envelope에 포함.
        assertThat(out.subjectType()).isEqualTo("user");
        assertThat(out.subjectId()).isEqualTo(subjectId);
        assertThat(out.preset()).isEqualTo("edit");
        // A16 — subjectName 14번째 필드 envelope 노출.
        assertThat(out.subjectName()).isEqualTo("Display Name");
        verify(commandService).createShares(fileId, req, ACTOR);
    }

    @Test
    void create_multipleSubjects_returnsAllSharesInOrder() {
        UUID fileId = UUID.randomUUID();
        UUID s1Subject = UUID.randomUUID();
        UUID s2Subject = UUID.randomUUID();
        ShareCreateRequest req = new ShareCreateRequest(
            List.of(
                new ShareCreateRequest.Subject("user", s1Subject),
                new ShareCreateRequest.Subject("department", s2Subject)
            ),
            "read", null, "msg"
        );
        ShareDto d1 = makeFileDto(fileId, "user", s1Subject, "read");
        ShareDto d2 = makeFileDto(fileId, "department", s2Subject, "read");
        when(commandService.createShares(eq(fileId), eq(req), eq(ACTOR)))
            .thenReturn(List.of(d1, d2));

        ResponseEntity<Map<String, List<ShareDto>>> res = controller.create(fileId, req, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().get("shares")).hasSize(2);
        assertThat(res.getBody().get("shares").get(0).id()).isEqualTo(d1.id());
        assertThat(res.getBody().get("shares").get(1).id()).isEqualTo(d2.id());
        // A13 — 두번째 row의 department subject 노출 확인.
        assertThat(res.getBody().get("shares").get(1).subjectType()).isEqualTo("department");
        // A16 — dept share row에 dept name이 envelope에 노출.
        assertThat(res.getBody().get("shares").get(1).subjectName()).isEqualTo("Engineering");
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

    // ── POST /api/folders/{folderId}/share — A12 ───────────────────────

    @Test
    void createFolderShare_returns201WithSharesEnvelope() {
        UUID folderId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        ShareCreateRequest req = new ShareCreateRequest(
            List.of(new ShareCreateRequest.Subject("user", subjectId)),
            "edit", null, null
        );
        // A13 — service 반환은 join이 끝난 ShareDto (folder XOR).
        ShareDto dto = makeFolderDto(folderId, "user", subjectId, "edit");
        when(commandService.createFolderShares(eq(folderId), eq(req), eq(ACTOR)))
            .thenReturn(List.of(dto));

        ResponseEntity<Map<String, List<ShareDto>>> res =
            controller.createFolderShare(folderId, req, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody()).containsKey("shares");
        List<ShareDto> dtos = res.getBody().get("shares");
        assertThat(dtos).hasSize(1);
        ShareDto out = dtos.get(0);
        assertThat(out.id()).isEqualTo(dto.id());
        assertThat(out.folderId()).isEqualTo(folderId);
        assertThat(out.fileId()).isNull();
        // A13 wire 검증.
        assertThat(out.subjectType()).isEqualTo("user");
        assertThat(out.subjectId()).isEqualTo(subjectId);
        assertThat(out.preset()).isEqualTo("edit");
        // file POST의 createShares가 호출되지 않아야 — folder path 분기 확인.
        verify(commandService).createFolderShares(folderId, req, ACTOR);
    }

    @Test
    void createFolderShare_multipleSubjects_returnsAllSharesInOrder() {
        UUID folderId = UUID.randomUUID();
        UUID s1Subject = UUID.randomUUID();
        UUID s2Subject = UUID.randomUUID();
        ShareCreateRequest req = new ShareCreateRequest(
            List.of(
                new ShareCreateRequest.Subject("user", s1Subject),
                new ShareCreateRequest.Subject("department", s2Subject)
            ),
            "read", null, "msg"
        );
        ShareDto d1 = makeFolderDto(folderId, "user", s1Subject, "read");
        ShareDto d2 = makeFolderDto(folderId, "department", s2Subject, "read");
        when(commandService.createFolderShares(eq(folderId), eq(req), eq(ACTOR)))
            .thenReturn(List.of(d1, d2));

        ResponseEntity<Map<String, List<ShareDto>>> res =
            controller.createFolderShare(folderId, req, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().get("shares")).hasSize(2);
        assertThat(res.getBody().get("shares").get(0).id()).isEqualTo(d1.id());
        assertThat(res.getBody().get("shares").get(1).id()).isEqualTo(d2.id());
    }

    @Test
    void create_teamSubject_201() {
        UUID fileId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        ShareCreateRequest req = new ShareCreateRequest(
            List.of(new ShareCreateRequest.Subject("team", teamId)),
            "read", null, null
        );
        ShareDto dto = makeFileDto(fileId, "team", teamId, "read");
        when(commandService.createShares(eq(fileId), eq(req), eq(ACTOR)))
            .thenReturn(List.of(dto));

        ResponseEntity<Map<String, List<ShareDto>>> res = controller.create(fileId, req, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody()).containsKey("shares");
        List<ShareDto> dtos = res.getBody().get("shares");
        assertThat(dtos).hasSize(1);
        ShareDto out = dtos.get(0);
        assertThat(out.subjectType()).isEqualTo("team");
        assertThat(out.subjectId()).isEqualTo(teamId);
        assertThat(out.preset()).isEqualTo("read");
        // A16 — team share row에 team name이 envelope에 노출.
        assertThat(out.subjectName()).isEqualTo("ProjectAlpha");
        verify(commandService).createShares(fileId, req, ACTOR);
    }

    @Test
    void createFolderShare_teamSubject_201() {
        UUID folderId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        ShareCreateRequest req = new ShareCreateRequest(
            List.of(new ShareCreateRequest.Subject("team", teamId)),
            "edit", null, null
        );
        ShareDto dto = makeFolderDto(folderId, "team", teamId, "edit");
        when(commandService.createFolderShares(eq(folderId), eq(req), eq(ACTOR)))
            .thenReturn(List.of(dto));

        ResponseEntity<Map<String, List<ShareDto>>> res =
            controller.createFolderShare(folderId, req, principal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody()).containsKey("shares");
        List<ShareDto> dtos = res.getBody().get("shares");
        assertThat(dtos).hasSize(1);
        ShareDto out = dtos.get(0);
        assertThat(out.folderId()).isEqualTo(folderId);
        assertThat(out.fileId()).isNull();
        assertThat(out.subjectType()).isEqualTo("team");
        assertThat(out.subjectId()).isEqualTo(teamId);
        assertThat(out.preset()).isEqualTo("edit");
        // A16 — team share row에 team name이 envelope에 노출.
        assertThat(out.subjectName()).isEqualTo("ProjectAlpha");
        verify(commandService).createFolderShares(folderId, req, ACTOR);
    }

    @Test
    void createFolderShare_propagatesResourceNotFound() {
        UUID folderId = UUID.randomUUID();
        ShareCreateRequest req = new ShareCreateRequest(
            List.of(new ShareCreateRequest.Subject("user", UUID.randomUUID())),
            "read", null, null
        );
        when(commandService.createFolderShares(any(), any(), any()))
            .thenThrow(new ResourceNotFoundException("folder not found"));

        assertThatThrownBy(() -> controller.createFolderShare(folderId, req, principal))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createFolderShare_propagatesIllegalArgument() {
        UUID folderId = UUID.randomUUID();
        ShareCreateRequest req = new ShareCreateRequest(
            List.of(new ShareCreateRequest.Subject("user", UUID.randomUUID())),
            "share", null, null
        );
        when(commandService.createFolderShares(any(), any(), any()))
            .thenThrow(new IllegalArgumentException("preset 'share' is not persistable"));

        assertThatThrownBy(() -> controller.createFolderShare(folderId, req, principal))
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

    /**
     * A13 — service 반환이 ShareDto이므로 controller 테스트 fixture도 DTO로 직접 구성. file XOR (folderId=null).
     * A16 — subjectName 14번째 필드 (user면 displayName, dept면 dept.name, team이면 team.name, everyone이면 null).
     */
    private static ShareDto makeFileDto(UUID fileId, String subjectType, UUID subjectId, String preset) {
        return new ShareDto(
            UUID.randomUUID(),  // id
            fileId,
            null,               // folderId — file XOR
            UUID.randomUUID(),  // permissionId
            ACTOR,              // sharedBy
            null,               // message
            null,               // expiresAt
            Instant.now(),      // createdAt
            null,               // revokedAt
            null,               // revokedBy
            subjectType,
            subjectId,
            preset,
            "user".equals(subjectType) ? "Display Name"
                : "department".equals(subjectType) ? "Engineering"
                : "team".equals(subjectType) ? "ProjectAlpha"
                : null
        );
    }

    /** folder XOR (fileId=null). A16 — subjectName 14번째 필드. */
    private static ShareDto makeFolderDto(UUID folderId, String subjectType, UUID subjectId, String preset) {
        return new ShareDto(
            UUID.randomUUID(),  // id
            null,               // fileId — folder XOR
            folderId,
            UUID.randomUUID(),  // permissionId
            ACTOR,              // sharedBy
            null,               // message
            null,               // expiresAt
            Instant.now(),      // createdAt
            null,               // revokedAt
            null,               // revokedBy
            subjectType,
            subjectId,
            preset,
            "user".equals(subjectType) ? "Display Name"
                : "department".equals(subjectType) ? "Engineering"
                : "team".equals(subjectType) ? "ProjectAlpha"
                : null
        );
    }
}
