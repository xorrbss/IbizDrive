package com.ibizdrive.favorite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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
}
