package com.ibizdrive.folder;

import com.ibizdrive.favorite.FavoriteRepository;
import com.ibizdrive.file.FileItem;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.folder.dto.BreadcrumbCrumbDto;
import com.ibizdrive.folder.dto.FolderDetailResponse;
import com.ibizdrive.folder.dto.FolderDto;
import com.ibizdrive.folder.dto.FolderItemDto;
import com.ibizdrive.folder.dto.FolderItemsResponse;
import com.ibizdrive.folder.dto.FolderNodeDto;
import com.ibizdrive.folder.dto.ScopeRef;
import com.ibizdrive.permission.PermissionRepository;
import com.ibizdrive.user.IbizDriveUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Phase A/B 읽기 전용 service — frontend 트리/상세/items 화면 wiring 용도.
 *
 * <p>책임 분리: mutation은 {@link FolderMutationService}, 본 service는 fetch + DTO 조립만.
 * Audit 미발행 (read-only는 audit 비대상 — docs/03 §4 노출 정책).
 *
 * <p>visibility: 현재 모든 활성 폴더 반환 — Phase A 단순화 (TODO Phase B+: grant-based 필터).
 * controller layer가 인증/READ 권한을 SpEL로 게이트하므로 endpoint 별 분리는 controller 책임.
 */
@Service
public class FolderQueryService {

    private final FolderRepository folderRepository;
    private final FileRepository fileRepository;
    private final PermissionRepository permissionRepository;
    private final FavoriteRepository favoriteRepository;

    public FolderQueryService(
        FolderRepository folderRepository,
        FileRepository fileRepository,
        PermissionRepository permissionRepository,
        FavoriteRepository favoriteRepository
    ) {
        this.folderRepository = folderRepository;
        this.fileRepository = fileRepository;
        this.permissionRepository = permissionRepository;
        this.favoriteRepository = favoriteRepository;
    }

    /**
     * 활성 폴더 전체를 fetch한 뒤 부모-자식 관계로 in-memory 트리 조립.
     * 결과는 top-level (parent_id == null) 노드 리스트.
     *
     * <p>parent_id가 결과 set에 없는 orphan 행은 트리에 포함시키지 않음 — race나 일관성 깨짐
     * 시 안전한 fallback (빈 children 트리는 정상 표시, 이상 노드는 숨김).
     */
    @Transactional(readOnly = true)
    public List<FolderNodeDto> loadTree() {
        List<Folder> all = folderRepository.findAllByDeletedAtIsNull();
        if (all.isEmpty()) {
            return List.of();
        }

        // 단계 1: 각 id에 대한 mutable children 리스트를 만든다.
        Map<UUID, List<FolderNodeDto>> childrenById = new HashMap<>();
        for (Folder f : all) {
            childrenById.put(f.getId(), new ArrayList<>());
        }

        // 단계 2: 각 폴더에 대한 DTO를 children 리스트와 연결해 생성. parent의 children에 push.
        List<FolderNodeDto> roots = new ArrayList<>();
        for (Folder f : all) {
            FolderNodeDto node = new FolderNodeDto(
                f.getId(),
                f.getParentId(),
                f.getName(),
                f.getSlug(),
                ScopeRef.of(f.getScopeType(), f.getScopeId()),
                childrenById.get(f.getId())
            );
            UUID parentId = f.getParentId();
            if (parentId == null) {
                roots.add(node);
            } else {
                List<FolderNodeDto> siblings = childrenById.get(parentId);
                if (siblings != null) {
                    siblings.add(node);
                }
                // siblings == null이면 부모가 결과 set 외 → orphan, 트리에서 제외.
            }
        }
        return roots;
    }

    /**
     * 폴더 상세 + breadcrumb. 부모 체인은 root까지 따라 올라가며 build, 이후 reverse.
     *
     * @throws FolderNotFoundException id에 해당하는 활성 폴더가 없을 때
     */
    @Transactional(readOnly = true)
    public FolderDetailResponse loadDetail(UUID id) {
        Folder self = folderRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new FolderNotFoundException("folder not found: " + id));

        List<BreadcrumbCrumbDto> chain = new ArrayList<>();
        Folder cursor = self;
        while (cursor != null) {
            chain.add(new BreadcrumbCrumbDto(cursor.getId(), cursor.getName(), cursor.getSlug()));
            UUID parentId = cursor.getParentId();
            if (parentId == null) break;
            cursor = folderRepository.findByIdAndDeletedAtIsNull(parentId).orElse(null);
        }
        Collections.reverse(chain);

        return new FolderDetailResponse(FolderDto.from(self), chain);
    }

    /**
     * 폴더 안의 자식 폴더 + 파일을 단일 items 리스트로 합본하여 반환 (Phase B).
     *
     * <p>정렬 정책:
     * <ul>
     *   <li>type-first: 폴더 그룹이 항상 파일 그룹보다 앞 (sort/dir 무관).</li>
     *   <li>그룹 내 정렬: {@link SortKey} × {@link SortDir} 조합 적용.</li>
     *   <li>{@code SIZE} 정렬 시 폴더 그룹은 size 컬럼이 없으므로 {@code name asc} fallback.</li>
     * </ul>
     *
     * @throws FolderNotFoundException parent가 없거나 soft-delete 상태일 때
     */
    @Transactional(readOnly = true)
    public FolderItemsResponse loadItems(UUID id, SortKey sort, SortDir dir) {
        // soft-delete된 parent에 대해 자식 노출 금지 — race 방지 + 명시적 404 신호.
        folderRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new FolderNotFoundException("folder not found: " + id));

        List<Folder> subFolders = folderRepository.findByParentIdAndDeletedAtIsNull(id);
        List<FileItem> subFiles = fileRepository.findByFolderIdAndDeletedAtIsNull(id);

        Comparator<Folder> folderCmp = folderComparator(sort, dir);
        Comparator<FileItem> fileCmp = fileComparator(sort, dir);

        List<Folder> sortedFolders = new ArrayList<>(subFolders);
        sortedFolders.sort(folderCmp);
        List<FileItem> sortedFiles = new ArrayList<>(subFiles);
        sortedFiles.sort(fileCmp);

        // P2c — batch share-count per resource. items가 비어있으면 IN() invalid SQL 회피 (empty 가드).
        // Map miss → null → FolderItemDto.shareCount=null → JsonInclude로 키 omit → FE 배지 미표시.
        Map<UUID, Integer> folderShareCount = sortedFolders.isEmpty()
            ? Map.of()
            : toCountMap(permissionRepository.countActiveByResources(
                "folder",
                sortedFolders.stream().map(Folder::getId).toList()
            ));
        Map<UUID, Integer> fileShareCount = sortedFiles.isEmpty()
            ? Map.of()
            : toCountMap(permissionRepository.countActiveByResources(
                "file",
                sortedFiles.stream().map(FileItem::getId).toList()
            ));

        // P2d — batch items-count per subfolder. subFolders가 비어있으면 자식 count 자체 불필요.
        // 자식 폴더가 빈 폴더라도 itemsCount=0을 명시 반환 — FE typeof === 'number' 검사에서 0 노출 허용.
        // FolderRepository + FileRepository 각 1쿼리 후 sum.
        Map<UUID, Integer> folderItemsCount;
        if (sortedFolders.isEmpty()) {
            folderItemsCount = Map.of();
        } else {
            List<UUID> folderIds = sortedFolders.stream().map(Folder::getId).toList();
            folderItemsCount = new HashMap<>(folderIds.size() * 2);
            // 자식 폴더 + 자식 파일 count 모두 같은 Map에 누적 (parent_id 별 합산).
            for (Object[] row : folderRepository.countByParentIdInGroupedActive(folderIds)) {
                folderItemsCount.merge((UUID) row[0], ((Number) row[1]).intValue(), Integer::sum);
            }
            for (Object[] row : fileRepository.countByFolderIdInGroupedActive(folderIds)) {
                folderItemsCount.merge((UUID) row[0], ((Number) row[1]).intValue(), Integer::sum);
            }
        }

        // P2a — batch starred per resource per current user. user 인증 부재 시 모든 starred=null.
        // file-badge P2c countActiveByResources 패턴 답습 — empty 가드 + Map miss → null.
        UUID currentUserId = resolveCurrentUserId();
        Set<UUID> starredFolderIds = currentUserId == null || sortedFolders.isEmpty()
            ? Set.of()
            : new HashSet<>(favoriteRepository.findStarredResourceIds(
                currentUserId, "folder",
                sortedFolders.stream().map(Folder::getId).toList()
            ));
        Set<UUID> starredFileIds = currentUserId == null || sortedFiles.isEmpty()
            ? Set.of()
            : new HashSet<>(favoriteRepository.findStarredResourceIds(
                currentUserId, "file",
                sortedFiles.stream().map(FileItem::getId).toList()
            ));

        List<FolderItemDto> items = new ArrayList<>(sortedFolders.size() + sortedFiles.size());
        for (Folder f : sortedFolders) {
            // 빈 폴더는 Map miss → 0 으로 명시 (FE typeof === 'number' 검사에서 "0개" 표시).
            int count = folderItemsCount.getOrDefault(f.getId(), 0);
            items.add(FolderItemDto.fromFolder(
                f, folderShareCount.get(f.getId()), count,
                starredFolderIds.contains(f.getId()) ? Boolean.TRUE : null
            ));
        }
        for (FileItem fi : sortedFiles) {
            items.add(FolderItemDto.fromFile(
                fi, fileShareCount.get(fi.getId()),
                starredFileIds.contains(fi.getId()) ? Boolean.TRUE : null
            ));
        }

        return new FolderItemsResponse(items);
    }

    /**
     * P2a — 현재 인증된 사용자 id. 미인증 시 null (controller-level isAuthenticated 가드 통과
     * 가정이지만 service test 등 일부 경로에서 null 가능 — defensive).
     */
    private static UUID resolveCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof IbizDriveUserDetails details) {
            return details.getUser().getId();
        }
        return null;
    }

    /**
     * {@link PermissionRepository#countActiveByResources}의 {@code List<Object[]{UUID, Long}>}을
     * {@code Map<UUID, Integer>}로 평탄화. count는 항상 1 이상이므로 0/null 항목은 자연 미포함.
     */
    private static Map<UUID, Integer> toCountMap(List<Object[]> rows) {
        Map<UUID, Integer> result = new HashMap<>(rows.size() * 2);
        for (Object[] row : rows) {
            result.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return result;
    }

    /**
     * 폴더 그룹 비교자. SIZE는 size 컬럼이 없으므로 name asc로 fallback (sort/dir에 영향받지 않음).
     */
    private static Comparator<Folder> folderComparator(SortKey sort, SortDir dir) {
        Comparator<Folder> base = switch (sort) {
            case NAME -> Comparator.comparing(Folder::getName, Comparator.nullsLast(Comparator.naturalOrder()));
            case UPDATED_AT -> Comparator.comparing(Folder::getUpdatedAt, Comparator.nullsLast(Instant::compareTo));
            case SIZE -> Comparator.comparing(Folder::getName, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        if (sort == SortKey.SIZE) {
            // SIZE 폴더 그룹은 항상 name asc fallback — dir 무시.
            return base;
        }
        return dir == SortDir.DESC ? base.reversed() : base;
    }

    private static Comparator<FileItem> fileComparator(SortKey sort, SortDir dir) {
        Comparator<FileItem> base = switch (sort) {
            case NAME -> Comparator.comparing(FileItem::getName, Comparator.nullsLast(Comparator.naturalOrder()));
            case UPDATED_AT -> Comparator.comparing(FileItem::getUpdatedAt, Comparator.nullsLast(Instant::compareTo));
            case SIZE -> Comparator.comparingLong(FileItem::getSizeBytes);
        };
        return dir == SortDir.DESC ? base.reversed() : base;
    }
}
