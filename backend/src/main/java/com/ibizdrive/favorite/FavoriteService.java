package com.ibizdrive.favorite;

import com.ibizdrive.favorite.dto.FavoriteItemDto;
import com.ibizdrive.favorite.dto.FavoriteListResponse;
import com.ibizdrive.file.FileItem;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * P2a — favorites 도메인 mutation service.
 *
 * <p>{@link #star}/{@link #unstar} 모두 멱등 — 이미 starred/unstarred 상태이면 no-op (audit 미발행).
 * controller-level {@code @PreAuthorize}가 READ 권한 가드를 담당 (별 클릭 자체가 READ가 보장된
 * 상태에서 발생하므로 service는 추가 가드 없이 통과).
 *
 * <p>AFTER_COMMIT event publish는 {@link FavoriteStarredEvent} → {@link FavoriteAuditListener} →
 * audit_log INSERT 별도 REQUIRES_NEW 트랜잭션. `AdminUserQuotaService` 패턴 답습.
 */
@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public FavoriteService(
        FavoriteRepository favoriteRepository,
        FileRepository fileRepository,
        FolderRepository folderRepository,
        ApplicationEventPublisher eventPublisher
    ) {
        this.favoriteRepository = favoriteRepository;
        this.fileRepository = fileRepository;
        this.folderRepository = folderRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 즐겨찾기 추가. 이미 starred면 no-op (audit 미발행).
     *
     * @param actorId 현재 인증된 사용자
     * @param resourceType "file" 또는 "folder"
     * @param resourceId 대상 resource id
     * @return true=새로 starred, false=이미 starred (no-op)
     */
    @Transactional
    public boolean star(UUID actorId, String resourceType, UUID resourceId) {
        boolean exists = favoriteRepository
            .existsByIdUserIdAndIdResourceTypeAndIdResourceId(actorId, resourceType, resourceId);
        if (exists) return false;

        favoriteRepository.save(Favorite.of(actorId, resourceType, resourceId));
        eventPublisher.publishEvent(
            new FavoriteStarredEvent(actorId, resourceType, resourceId, true)
        );
        return true;
    }

    /**
     * 즐겨찾기 제거. 이미 unstarred면 no-op (audit 미발행).
     *
     * @return true=실제 제거, false=이미 unstarred (no-op)
     */
    @Transactional
    public boolean unstar(UUID actorId, String resourceType, UUID resourceId) {
        FavoriteId id = new FavoriteId(actorId, resourceType, resourceId);
        if (!favoriteRepository.existsById(id)) return false;

        favoriteRepository.deleteById(id);
        eventPublisher.publishEvent(
            new FavoriteStarredEvent(actorId, resourceType, resourceId, false)
        );
        return true;
    }

    /**
     * 사용자별 즐겨찾기 목록 — v1.x {@code GET /api/me/favorites}.
     *
     * <p>즐겨찾기 행을 created_at DESC로 fetch → file/folder별 partition → batch active lookup으로
     * 살아있는 resource만 join. soft-deleted 항목은 응답에서 자연 제외 (favorites 행 자체는 DB 유지).
     *
     * <p>페이지네이션 없음 (v1 가정: 한 user 즐겨찾기 ~100건 미만). 늘어나면 cursor 도입.
     *
     * <p>읽기 전용 — audit 미발행.
     */
    @Transactional(readOnly = true)
    public FavoriteListResponse listMy(UUID actorId) {
        List<Favorite> favs = favoriteRepository.findByIdUserIdOrderByCreatedAtDesc(actorId);
        if (favs.isEmpty()) return new FavoriteListResponse(List.of());

        List<UUID> fileIds = new ArrayList<>();
        List<UUID> folderIds = new ArrayList<>();
        for (Favorite fv : favs) {
            if ("file".equals(fv.getResourceType())) fileIds.add(fv.getResourceId());
            else if ("folder".equals(fv.getResourceType())) folderIds.add(fv.getResourceId());
            // 그 외 resource_type은 V22 CHECK로 차단되므로 도달 불가.
        }

        Map<UUID, FileItem> filesById = fileIds.isEmpty() ? Map.of()
            : indexById(fileRepository.findAllByIdInAndDeletedAtIsNull(fileIds), FileItem::getId);
        Map<UUID, Folder> foldersById = folderIds.isEmpty() ? Map.of()
            : indexById(folderRepository.findAllByIdInAndDeletedAtIsNull(folderIds), Folder::getId);

        List<FavoriteItemDto> items = new ArrayList<>(favs.size());
        for (Favorite fv : favs) {
            if ("file".equals(fv.getResourceType())) {
                FileItem fi = filesById.get(fv.getResourceId());
                if (fi != null) items.add(FavoriteItemDto.fromFile(fi, fv.getCreatedAt()));
            } else if ("folder".equals(fv.getResourceType())) {
                Folder fo = foldersById.get(fv.getResourceId());
                if (fo != null) items.add(FavoriteItemDto.fromFolder(fo, fv.getCreatedAt()));
            }
        }
        return new FavoriteListResponse(items);
    }

    private static <T> Map<UUID, T> indexById(List<T> rows, java.util.function.Function<T, UUID> keyFn) {
        Map<UUID, T> result = new HashMap<>(rows.size() * 2);
        for (T row : rows) result.put(keyFn.apply(row), row);
        return result;
    }
}
