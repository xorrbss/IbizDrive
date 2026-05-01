package com.ibizdrive.share;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    private ShareQueryService service;

    @BeforeEach
    void setUp() {
        shareRepository = mock(ShareRepository.class);
        service = new ShareQueryService(shareRepository);
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
        when(shareRepository.findActiveWithMeBySubjectUser(eq(ACTOR), isNull(), isNull(), eq(11)))
            .thenReturn(List.of(s));

        SharePage page = service.listWithMe(ACTOR, null, 10);

        assertThat(page.items()).hasSize(1);
        ShareDto dto = page.items().get(0);
        assertThat(dto.id()).isEqualTo(s.getId());
        assertThat(dto.permissionId()).isEqualTo(permId);
        assertThat(dto.message()).isEqualTo("hello");
        assertThat(dto.createdAt()).isEqualTo(s.getCreatedAt());
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
}
