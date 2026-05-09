package com.ibizdrive.folder;

import com.ibizdrive.file.FileItem;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.folder.dto.BreadcrumbCrumbDto;
import com.ibizdrive.folder.dto.FolderDetailResponse;
import com.ibizdrive.folder.dto.FolderDto;
import com.ibizdrive.folder.dto.FolderItemDto;
import com.ibizdrive.folder.dto.FolderItemsResponse;
import com.ibizdrive.folder.dto.FolderNodeDto;
import com.ibizdrive.folder.dto.ScopeRef;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public FolderQueryService(FolderRepository folderRepository, FileRepository fileRepository) {
        this.folderRepository = folderRepository;
        this.fileRepository = fileRepository;
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

        List<FolderItemDto> items = new ArrayList<>(sortedFolders.size() + sortedFiles.size());
        for (Folder f : sortedFolders) items.add(FolderItemDto.fromFolder(f));
        for (FileItem fi : sortedFiles) items.add(FolderItemDto.fromFile(fi));

        return new FolderItemsResponse(items);
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
