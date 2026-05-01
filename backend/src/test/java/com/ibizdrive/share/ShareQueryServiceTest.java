package com.ibizdrive.share;

import com.ibizdrive.permission.PermissionRepository;
import com.ibizdrive.permission.PermissionRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A10.4 — {@link ShareQueryService} unit (Mockito only, no Spring context).
 *
 * <p>패턴: {@link com.ibizdrive.trash.TrashQueryService} 동형 — limit + 1 over-fetch + nextCursor 발급.
 *
 * <p>주의: {@link Share}는 mutable getters/setters라 mock하지 않고 실 인스턴스 사용. cursor encoding은
 * round-trip으로 검증.
 */
class ShareQueryServiceTest {

    private static final UUID ACTOR = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private ShareRepository shareRepository;
    private PermissionRepository permissionRepository;
    private ShareQueryService service;

    @BeforeEach
    void setUp() {
        shareRepository = mock(ShareRepository.class);
        permissionRepository = mock(PermissionRepository.class);
        service = new ShareQueryService(shareRepository, permissionRepository);
        // 기본 stub: 어떤 id 집합이 와도 자동으로 매칭되는 grant 하나씩 만들어서 반환.
        // 케이스에서 명시적으로 stubbing이 필요하면 override.
        when(permissionRepository.findAllById(anyIterable())).thenAnswer(inv -> {
            Iterable<UUID> ids = inv.getArgument(0);
            List<PermissionRow> rows = new ArrayList<>();
            for (UUID id : ids) rows.add(grantRow(id, "user", UUID.randomUUID(), "read"));
            return rows;
        });
    }

    // ----------------- listByMe -----------------

    @Test
    void listByMe_emptyResult_returnsEmptyPageWithNullCursor() {
        when(shareRepository.findActiveBySharedBy(eq(ACTOR), isNull(), isNull(), eq(51)))
            .thenReturn(Collections.emptyList());

        SharePage page = service.listByMe(ACTOR, null, null);

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void listByMe_lessThanLimit_returnsAllItemsAndNoCursor() {
        List<Share> rows = List.of(makeShare("2026-04-30T12:00:00Z"), makeShare("2026-04-29T12:00:00Z"));
        when(shareRepository.findActiveBySharedBy(eq(ACTOR), isNull(), isNull(), eq(11)))
            .thenReturn(rows);

        SharePage page = service.listByMe(ACTOR, null, 10);

        assertThat(page.items()).hasSize(2);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void listByMe_overFetch_emitsNextCursorFromLastReturnedItem() {
        // limit = 2 → fetchSize = 3. repo returns 3 → hasMore=true, page=[r0, r1], nextCursor from r1.
        Share r0 = makeShare("2026-04-30T12:00:00Z");
        Share r1 = makeShare("2026-04-29T12:00:00Z");
        Share r2 = makeShare("2026-04-28T12:00:00Z");
        when(shareRepository.findActiveBySharedBy(eq(ACTOR), isNull(), isNull(), eq(3)))
            .thenReturn(new ArrayList<>(List.of(r0, r1, r2)));

        SharePage page = service.listByMe(ACTOR, null, 2);

        assertThat(page.items()).hasSize(2);
        assertThat(page.nextCursor()).isNotNull();
        ShareCursor decoded = ShareCursor.decode(page.nextCursor());
        assertThat(decoded.createdAt()).isEqualTo(r1.getCreatedAt());
        assertThat(decoded.id()).isEqualTo(r1.getId());
    }

    @Test
    void listByMe_decodesCursorAndForwardsToRepo() {
        Share s = makeShare("2026-04-30T00:00:00Z");
        String wire = ShareCursor.encode(s.getCreatedAt(), s.getId());
        when(shareRepository.findActiveBySharedBy(any(), any(), any(), any(int.class)))
            .thenReturn(Collections.emptyList());

        service.listByMe(ACTOR, wire, 10);

        ArgumentCaptor<Instant> at = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<UUID> id = ArgumentCaptor.forClass(UUID.class);
        verify(shareRepository).findActiveBySharedBy(eq(ACTOR), at.capture(), id.capture(), eq(11));
        assertThat(at.getValue()).isEqualTo(s.getCreatedAt());
        assertThat(id.getValue()).isEqualTo(s.getId());
    }

    @Test
    void listByMe_clampsLimit_defaultAndMax() {
        when(shareRepository.findActiveBySharedBy(eq(ACTOR), isNull(), isNull(), any(int.class)))
            .thenReturn(Collections.emptyList());

        service.listByMe(ACTOR, null, null);
        service.listByMe(ACTOR, null, 0);
        // 둘 다 default 50 → +1 = 51 fetchSize
        verify(shareRepository, times(2))
            .findActiveBySharedBy(eq(ACTOR), isNull(), isNull(), eq(51));

        service.listByMe(ACTOR, null, 999);
        // cap 100 → +1
        verify(shareRepository).findActiveBySharedBy(eq(ACTOR), isNull(), isNull(), eq(101));
    }

    @Test
    void listByMe_invalidCursor_propagatesIllegalArgument() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> service.listByMe(ACTOR, "!!!not-base64!!!", 10)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    // ----------------- listWithMe -----------------

    @Test
    void listWithMe_emptyResult_returnsEmptyPage() {
        when(shareRepository.findActiveWithMeBySubjectUser(eq(ACTOR), isNull(), isNull(), eq(51)))
            .thenReturn(Collections.emptyList());

        SharePage page = service.listWithMe(ACTOR, null, null);

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void listWithMe_overFetch_emitsNextCursor() {
        Share r0 = makeShare("2026-04-30T12:00:00Z");
        Share r1 = makeShare("2026-04-29T12:00:00Z");
        Share r2 = makeShare("2026-04-28T12:00:00Z");
        when(shareRepository.findActiveWithMeBySubjectUser(eq(ACTOR), isNull(), isNull(), eq(3)))
            .thenReturn(new ArrayList<>(List.of(r0, r1, r2)));

        SharePage page = service.listWithMe(ACTOR, null, 2);

        assertThat(page.items()).hasSize(2);
        ShareCursor decoded = ShareCursor.decode(page.nextCursor());
        assertThat(decoded.createdAt()).isEqualTo(r1.getCreatedAt());
        assertThat(decoded.id()).isEqualTo(r1.getId());
    }

    @Test
    void listWithMe_dtoMappingPreservesShareFields() {
        Share s = makeShare("2026-04-30T00:00:00Z");
        s.setMessage("hello");
        UUID permId = UUID.randomUUID();
        s.setPermissionId(permId);
        UUID subjectId = UUID.randomUUID();
        // A13 — explicit stub으로 join된 grant 메타 검증.
        // 주의: grantRow() 안에서 when().thenReturn() 호출이 일어나므로 outer stub의 thenReturn() 인자
        // 안에서 직접 호출하면 Mockito가 nested stubbing으로 판단해 UnfinishedStubbingException 발생.
        // → 반드시 local 변수에 먼저 빌드한 뒤 outer stub에 넘긴다.
        PermissionRow grant = grantRow(permId, "user", subjectId, "edit");
        when(permissionRepository.findAllById(anyIterable())).thenReturn(List.of(grant));
        when(shareRepository.findActiveWithMeBySubjectUser(eq(ACTOR), isNull(), isNull(), eq(11)))
            .thenReturn(List.of(s));

        SharePage page = service.listWithMe(ACTOR, null, 10);

        assertThat(page.items()).hasSize(1);
        ShareDto dto = page.items().get(0);
        assertThat(dto.id()).isEqualTo(s.getId());
        assertThat(dto.permissionId()).isEqualTo(permId);
        assertThat(dto.message()).isEqualTo("hello");
        assertThat(dto.createdAt()).isEqualTo(s.getCreatedAt());
        // A13 join 필드.
        assertThat(dto.subjectType()).isEqualTo("user");
        assertThat(dto.subjectId()).isEqualTo(subjectId);
        assertThat(dto.preset()).isEqualTo("edit");
    }

    // ----------------- A13 — permissions join coverage -----------------

    @Test
    void listByMe_batchFetchesPermissions_oncePerPage() {
        // 페이지 3건 → permissionRepository.findAllById 1회 호출 (N+1 회피 보장).
        Share r0 = makeShare("2026-04-30T12:00:00Z");
        Share r1 = makeShare("2026-04-29T12:00:00Z");
        Share r2 = makeShare("2026-04-28T12:00:00Z");
        when(shareRepository.findActiveBySharedBy(eq(ACTOR), isNull(), isNull(), eq(11)))
            .thenReturn(List.of(r0, r1, r2));

        service.listByMe(ACTOR, null, 10);

        verify(permissionRepository, times(1)).findAllById(anyIterable());
    }

    @Test
    void listByMe_throwsIllegalState_whenPermissionMissing() {
        // V6 FK 보증 위반 시뮬레이션 — permission row가 사라진 share active row.
        Share s = makeShare("2026-04-30T12:00:00Z");
        when(shareRepository.findActiveBySharedBy(eq(ACTOR), isNull(), isNull(), eq(11)))
            .thenReturn(List.of(s));
        when(permissionRepository.findAllById(anyIterable()))
            .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.listByMe(ACTOR, null, 10))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("dangling permission");
    }

    @Test
    void listByMe_emptyPage_doesNotCallPermissionRepo() {
        when(shareRepository.findActiveBySharedBy(eq(ACTOR), isNull(), isNull(), eq(51)))
            .thenReturn(Collections.emptyList());

        service.listByMe(ACTOR, null, null);

        verify(permissionRepository, times(0)).findAllById(anyIterable());
    }

    @Test
    void listByMe_everyoneSubject_propagatesNullSubjectIdToDto() {
        Share s = makeShare("2026-04-30T12:00:00Z");
        UUID permId = s.getPermissionId();
        // everyone grant — V5 CHECK 위배 회피로 subjectId=NULL.
        // 주의: nested stubbing 회피를 위해 grant mock을 먼저 local에 빌드.
        PermissionRow grant = grantRow(permId, "everyone", null, "read");
        when(permissionRepository.findAllById(anyIterable())).thenReturn(List.of(grant));
        when(shareRepository.findActiveBySharedBy(eq(ACTOR), isNull(), isNull(), eq(11)))
            .thenReturn(List.of(s));

        SharePage page = service.listByMe(ACTOR, null, 10);

        ShareDto dto = page.items().get(0);
        assertThat(dto.subjectType()).isEqualTo("everyone");
        assertThat(dto.subjectId()).isNull();
        assertThat(dto.preset()).isEqualTo("read");
    }

    // ----------------- A12 — folder share natural exposure -----------------

    @Test
    void listByMe_folderShare_surfacesWithFolderIdAndNullFileId() {
        // A12 — repository SQL은 file/folder 분기 없음. folder share row가 by-me에 자연 노출되어야.
        Share folderShare = makeFolderShare("2026-04-30T12:00:00Z");
        when(shareRepository.findActiveBySharedBy(eq(ACTOR), isNull(), isNull(), eq(11)))
            .thenReturn(List.of(folderShare));

        SharePage page = service.listByMe(ACTOR, null, 10);

        assertThat(page.items()).hasSize(1);
        ShareDto dto = page.items().get(0);
        assertThat(dto.fileId()).isNull();
        assertThat(dto.folderId()).isEqualTo(folderShare.getFolderId());
        assertThat(dto.permissionId()).isEqualTo(folderShare.getPermissionId());
    }

    @Test
    void listWithMe_folderShare_surfacesWithFolderIdAndNullFileId() {
        Share folderShare = makeFolderShare("2026-04-29T12:00:00Z");
        when(shareRepository.findActiveWithMeBySubjectUser(eq(ACTOR), isNull(), isNull(), eq(11)))
            .thenReturn(List.of(folderShare));

        SharePage page = service.listWithMe(ACTOR, null, 10);

        assertThat(page.items()).hasSize(1);
        ShareDto dto = page.items().get(0);
        assertThat(dto.fileId()).isNull();
        assertThat(dto.folderId()).isEqualTo(folderShare.getFolderId());
    }

    @Test
    void listByMe_mixedFileAndFolderShares_preservesXorPerRow() {
        Share fileShare = makeShare("2026-04-30T12:00:00Z");
        Share folderShare = makeFolderShare("2026-04-29T12:00:00Z");
        when(shareRepository.findActiveBySharedBy(eq(ACTOR), isNull(), isNull(), eq(11)))
            .thenReturn(List.of(fileShare, folderShare));

        SharePage page = service.listByMe(ACTOR, null, 10);

        assertThat(page.items()).hasSize(2);
        ShareDto first = page.items().get(0);
        ShareDto second = page.items().get(1);
        // 첫 row: file path, 두번째 row: folder path. XOR per row.
        assertThat(first.fileId()).isEqualTo(fileShare.getFileId());
        assertThat(first.folderId()).isNull();
        assertThat(second.fileId()).isNull();
        assertThat(second.folderId()).isEqualTo(folderShare.getFolderId());
    }

    // ----------------- helpers -----------------

    private static Share makeShare(String createdAtIso) {
        Share s = new Share();
        s.setId(UUID.randomUUID());
        s.setFileId(UUID.randomUUID());
        s.setFolderId(null);
        s.setPermissionId(UUID.randomUUID());
        s.setSharedBy(ACTOR);
        s.setCreatedAt(Instant.parse(createdAtIso));
        s.setRevokedAt(null);
        s.setRevokedBy(null);
        return s;
    }

    /**
     * A13 — PermissionRow ctor가 package-protected → mock 사용. service.toPage가
     * row.getId()/getSubjectType()/getSubjectId()/getPreset()를 읽음.
     */
    private static PermissionRow grantRow(UUID id, String subjectType, UUID subjectId, String preset) {
        PermissionRow row = mock(PermissionRow.class);
        when(row.getId()).thenReturn(id);
        when(row.getSubjectType()).thenReturn(subjectType);
        when(row.getSubjectId()).thenReturn(subjectId);
        when(row.getPreset()).thenReturn(preset);
        return row;
    }

    private static Share makeFolderShare(String createdAtIso) {
        Share s = new Share();
        s.setId(UUID.randomUUID());
        s.setFileId(null);                       // V6 XOR — folder share
        s.setFolderId(UUID.randomUUID());
        s.setPermissionId(UUID.randomUUID());
        s.setSharedBy(ACTOR);
        s.setCreatedAt(Instant.parse(createdAtIso));
        s.setRevokedAt(null);
        s.setRevokedBy(null);
        return s;
    }
}
