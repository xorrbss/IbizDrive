package com.ibizdrive.trash;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A8.1 — {@link TrashController#list} 직접 호출 단위 테스트 (FolderControllerTest 패턴).
 *
 * <p>controller가 가진 책임만 검증한다:
 * <ul>
 *   <li>type 파라미터 파싱 (null/file/folder/invalid → 400)</li>
 *   <li>service delegation 인자 전달 (actorId, role, cursor, type, limit)</li>
 *   <li>200 + body echo</li>
 * </ul>
 *
 * <p>service 내부의 cursor decode, merge sort, 권한 후처리 필터는 별도 테스트(`TrashQueryServiceTest`)
 * 또는 통합 테스트 책임 — 본 테스트는 controller boundary만 본다.
 */
class TrashControllerTest {

    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private TrashQueryService service;
    private TrashPurgeService purgeService;
    private TrashController controller;
    private IbizDriveUserDetails adminPrincipal;

    @BeforeEach
    void setUp() {
        service = mock(TrashQueryService.class);
        purgeService = mock(TrashPurgeService.class);
        controller = new TrashController(service, purgeService);

        User u = new User(
            ACTOR, "admin@example.com", "Admin", "{bcrypt}$2a$12$dummy",
            Role.ADMIN, true, false, OffsetDateTime.now()
        );
        adminPrincipal = new IbizDriveUserDetails(u);
    }

    // ── type 파싱 ────────────────────────────────────────────────────────

    @Test
    void list_typeNull_passesNullToService() {
        TrashPage expected = new TrashPage(List.of(), null);
        when(service.list(eq(ACTOR), eq(Role.ADMIN), isNull(), isNull(), isNull()))
            .thenReturn(expected);

        ResponseEntity<TrashPage> res = controller.list(null, null, null, adminPrincipal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isEqualTo(expected);
        verify(service).list(ACTOR, Role.ADMIN, null, null, null);
    }

    @Test
    void list_typeBlank_treatedAsNull() {
        TrashPage expected = new TrashPage(List.of(), null);
        when(service.list(eq(ACTOR), eq(Role.ADMIN), isNull(), isNull(), isNull()))
            .thenReturn(expected);

        controller.list(null, "   ", null, adminPrincipal);

        verify(service).list(ACTOR, Role.ADMIN, null, null, null);
    }

    @Test
    void list_typeFile_passesEnum() {
        TrashPage expected = new TrashPage(List.of(), null);
        when(service.list(eq(ACTOR), eq(Role.ADMIN), isNull(), eq(TrashItemType.FILE), isNull()))
            .thenReturn(expected);

        controller.list(null, "file", null, adminPrincipal);

        verify(service).list(ACTOR, Role.ADMIN, null, TrashItemType.FILE, null);
    }

    @Test
    void list_typeFolder_passesEnum() {
        TrashPage expected = new TrashPage(List.of(), null);
        when(service.list(eq(ACTOR), eq(Role.ADMIN), isNull(), eq(TrashItemType.FOLDER), isNull()))
            .thenReturn(expected);

        controller.list(null, "folder", null, adminPrincipal);

        verify(service).list(ACTOR, Role.ADMIN, null, TrashItemType.FOLDER, null);
    }

    @Test
    void list_invalidType_throwsIllegalArgument() {
        assertThatThrownBy(() -> controller.list(null, "bogus", null, adminPrincipal))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ── cursor + limit echo ─────────────────────────────────────────────

    @Test
    void list_cursorAndLimit_passedThrough() {
        String cursor = "abc123";
        TrashPage expected = new TrashPage(List.of(), "next");
        when(service.list(eq(ACTOR), eq(Role.ADMIN), eq(cursor), isNull(), eq(25)))
            .thenReturn(expected);

        ResponseEntity<TrashPage> res = controller.list(cursor, null, 25, adminPrincipal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().nextCursor()).isEqualTo("next");
        verify(service).list(ACTOR, Role.ADMIN, cursor, null, 25);
    }

    // ── DELETE /api/trash/{type}/{id} ──────────────────────────────────

    @Test
    void purge_typeFile_delegatesToPurgeFile() {
        UUID fileId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        ResponseEntity<Void> res = controller.purge("file", fileId, adminPrincipal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(purgeService).purgeFile(fileId, ACTOR);
    }

    @Test
    void purge_typeFolder_delegatesToPurgeFolder() {
        UUID folderId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

        ResponseEntity<Void> res = controller.purge("folder", folderId, adminPrincipal);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(purgeService).purgeFolder(folderId, ACTOR);
    }

    @Test
    void purge_invalidType_throwsIllegalArgument() {
        UUID id = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        assertThatThrownBy(() -> controller.purge("image", id, adminPrincipal))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
