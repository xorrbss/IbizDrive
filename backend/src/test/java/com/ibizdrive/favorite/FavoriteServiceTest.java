package com.ibizdrive.favorite;

import com.ibizdrive.favorite.dto.FavoriteItemDto;
import com.ibizdrive.favorite.dto.FavoriteListResponse;
import com.ibizdrive.file.FileItem;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.folder.ScopeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private FileRepository fileRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private FavoriteService service;

    private UUID actorId;
    private UUID resourceId;

    @BeforeEach
    void setUp() {
        actorId = UUID.randomUUID();
        resourceId = UUID.randomUUID();
    }

    @Test
    void star_새로_등록_시_save_와_event_publish() {
        when(favoriteRepository
            .existsByIdUserIdAndIdResourceTypeAndIdResourceId(actorId, "file", resourceId))
            .thenReturn(false);

        boolean changed = service.star(actorId, "file", resourceId);

        assertThat(changed).isTrue();
        verify(favoriteRepository).save(any(Favorite.class));
        ArgumentCaptor<FavoriteStarredEvent> captor =
            ArgumentCaptor.forClass(FavoriteStarredEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        FavoriteStarredEvent event = captor.getValue();
        assertThat(event.actorId()).isEqualTo(actorId);
        assertThat(event.resourceType()).isEqualTo("file");
        assertThat(event.resourceId()).isEqualTo(resourceId);
        assertThat(event.starred()).isTrue();
    }

    @Test
    void star_이미_등록된_경우_멱등_save와_event_미발행() {
        when(favoriteRepository
            .existsByIdUserIdAndIdResourceTypeAndIdResourceId(actorId, "file", resourceId))
            .thenReturn(true);

        boolean changed = service.star(actorId, "file", resourceId);

        assertThat(changed).isFalse();
        verify(favoriteRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void unstar_존재하는_row_삭제_와_event_publish() {
        FavoriteId id = new FavoriteId(actorId, "folder", resourceId);
        when(favoriteRepository.existsById(id)).thenReturn(true);

        boolean changed = service.unstar(actorId, "folder", resourceId);

        assertThat(changed).isTrue();
        verify(favoriteRepository).deleteById(id);
        ArgumentCaptor<FavoriteStarredEvent> captor =
            ArgumentCaptor.forClass(FavoriteStarredEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        FavoriteStarredEvent event = captor.getValue();
        assertThat(event.resourceType()).isEqualTo("folder");
        assertThat(event.starred()).isFalse();
    }

    @Test
    void unstar_존재하지_않는_경우_멱등_delete와_event_미발행() {
        FavoriteId id = new FavoriteId(actorId, "folder", resourceId);
        when(favoriteRepository.existsById(id)).thenReturn(false);

        boolean changed = service.unstar(actorId, "folder", resourceId);

        assertThat(changed).isFalse();
        verify(favoriteRepository, never()).deleteById(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ─────────────────── listMy ───────────────────

    @Test
    void listMy_즐겨찾기_없으면_빈_response_반환() {
        when(favoriteRepository.findByIdUserIdOrderByCreatedAtDesc(actorId))
            .thenReturn(List.of());

        FavoriteListResponse res = service.listMy(actorId);

        assertThat(res.items()).isEmpty();
        verify(fileRepository, never()).findAllByIdInAndDeletedAtIsNull(any());
        verify(folderRepository, never()).findAllByIdInAndDeletedAtIsNull(any());
    }

    @Test
    void listMy_file_folder_혼재_시간순_desc_반환() {
        UUID fileId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID parentFolderId = UUID.randomUUID();
        Instant t1 = Instant.parse("2026-05-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-05-10T00:00:00Z");
        // entity mock은 when() 외부에서 먼저 생성해야 함 — 내부 stub 호출이 unfinished stubbing 발생.
        FileItem file = file(fileId, parentFolderId, "report.pdf");
        Folder folder = folder(folderId, null, "영업팀");
        // repo가 이미 created_at DESC로 반환 — 본 service는 그 순서 보존.
        Favorite fav2 = new Favorite(actorId, "folder", folderId, t2);
        Favorite fav1 = new Favorite(actorId, "file", fileId, t1);
        when(favoriteRepository.findByIdUserIdOrderByCreatedAtDesc(actorId))
            .thenReturn(List.of(fav2, fav1));
        when(fileRepository.findAllByIdInAndDeletedAtIsNull(List.of(fileId)))
            .thenReturn(List.of(file));
        when(folderRepository.findAllByIdInAndDeletedAtIsNull(List.of(folderId)))
            .thenReturn(List.of(folder));

        FavoriteListResponse res = service.listMy(actorId);

        assertThat(res.items()).hasSize(2);
        FavoriteItemDto first = res.items().get(0);
        assertThat(first.resourceType()).isEqualTo("folder");
        assertThat(first.resourceId()).isEqualTo(folderId);
        assertThat(first.name()).isEqualTo("영업팀");
        assertThat(first.starredAt()).isEqualTo(t2);

        FavoriteItemDto second = res.items().get(1);
        assertThat(second.resourceType()).isEqualTo("file");
        assertThat(second.resourceId()).isEqualTo(fileId);
        assertThat(second.parentId()).isEqualTo(parentFolderId);
        assertThat(second.starredAt()).isEqualTo(t1);
    }

    @Test
    void listMy_soft_deleted_resource는_응답에서_제외() {
        // favorites 행은 살아있으나 file/folder는 soft-delete → batch lookup이 빈 리스트 반환 →
        // service가 자연 skip.
        UUID liveId = UUID.randomUUID();
        UUID deadId = UUID.randomUUID();
        Instant t = Instant.parse("2026-05-01T00:00:00Z");
        FileItem liveFile = file(liveId, UUID.randomUUID(), "live.txt");
        Favorite live = new Favorite(actorId, "file", liveId, t);
        Favorite dead = new Favorite(actorId, "file", deadId, t);
        when(favoriteRepository.findByIdUserIdOrderByCreatedAtDesc(actorId))
            .thenReturn(List.of(live, dead));
        // batch lookup에 liveId만 반환 (deadId는 soft-deleted라 빠짐).
        when(fileRepository.findAllByIdInAndDeletedAtIsNull(List.of(liveId, deadId)))
            .thenReturn(List.of(liveFile));

        FavoriteListResponse res = service.listMy(actorId);

        assertThat(res.items()).hasSize(1);
        assertThat(res.items().get(0).resourceId()).isEqualTo(liveId);
    }

    /**
     * FileItem 생성자가 package-private이라 다른 패키지에서 직접 생성 불가. Mockito mock으로 대체.
     * 본 service test가 호출하는 getter만 stub.
     */
    private static FileItem file(UUID id, UUID folderId, String name) {
        FileItem f = org.mockito.Mockito.mock(FileItem.class);
        when(f.getId()).thenReturn(id);
        when(f.getFolderId()).thenReturn(folderId);
        when(f.getName()).thenReturn(name);
        when(f.getScopeType()).thenReturn(ScopeType.DEPARTMENT);
        when(f.getScopeId()).thenReturn(UUID.randomUUID());
        return f;
    }

    private static Folder folder(UUID id, UUID parentId, String name) {
        Folder f = org.mockito.Mockito.mock(Folder.class);
        when(f.getId()).thenReturn(id);
        when(f.getParentId()).thenReturn(parentId);
        when(f.getName()).thenReturn(name);
        when(f.getScopeType()).thenReturn(ScopeType.DEPARTMENT);
        when(f.getScopeId()).thenReturn(UUID.randomUUID());
        return f;
    }
}
