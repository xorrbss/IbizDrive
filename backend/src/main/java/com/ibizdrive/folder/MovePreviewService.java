package com.ibizdrive.folder;

import com.ibizdrive.file.FileItem;
import com.ibizdrive.file.FileNotFoundException;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.folder.dto.MovePreviewResponse;
import com.ibizdrive.folder.dto.PermissionRef;
import com.ibizdrive.folder.dto.ShareRef;
import com.ibizdrive.permission.Permission;
import com.ibizdrive.permission.PermissionRepository;
import com.ibizdrive.permission.PermissionRow;
import com.ibizdrive.permission.WorkspaceMembershipResolver;
import com.ibizdrive.share.Share;
import com.ibizdrive.share.ShareRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * spec §5.6 — {@code /move/preview} 엔드포인트 진입점. DB 변경 없음 (멱등, {@code @Transactional(readOnly = true)}).
 *
 * <p>folder는 subtree 전체 (BFS), file은 자기 1개. 결과 4종(itemCount, removedPermissions,
 * revokedShares, targetMembershipDefaults)과 nameConflict 검사를 묶어 반환.
 *
 * <p>Empty resourceIds guard: {@link PermissionRepository#findActiveByResourceIn}와
 * {@link ShareRepository#findActiveByResourceIn}은 빈 컬렉션 입력 시 native IN(...) 문법 오류이므로
 * 본 service에서 호출 전 분기.
 */
@Service
@Transactional(readOnly = true)
public class MovePreviewService {

    private static final int MAX_CASCADE_NODES = 100_000;

    private final FolderRepository folderRepo;
    private final FileRepository fileRepo;
    private final PermissionRepository permRepo;
    private final ShareRepository shareRepo;
    private final WorkspaceMembershipResolver membershipResolver;

    public MovePreviewService(FolderRepository folderRepo,
                              FileRepository fileRepo,
                              PermissionRepository permRepo,
                              ShareRepository shareRepo,
                              WorkspaceMembershipResolver membershipResolver) {
        this.folderRepo = folderRepo;
        this.fileRepo = fileRepo;
        this.permRepo = permRepo;
        this.shareRepo = shareRepo;
        this.membershipResolver = membershipResolver;
    }

    public MovePreviewResponse previewFolder(UUID folderId, UUID destinationFolderId, UUID actorId) {
        if (destinationFolderId == null) {
            throw new InvalidMoveDestinationException("destinationFolderId is required");
        }
        Folder source = folderRepo.findByIdAndDeletedAtIsNull(folderId)
            .orElseThrow(() -> new FolderNotFoundException("source folder not found: " + folderId));
        Folder destination = folderRepo.findByIdAndDeletedAtIsNull(destinationFolderId)
            .orElseThrow(() -> new FolderNotFoundException("destination folder not found: " + destinationFolderId));

        // self / descendant cycle
        if (folderId.equals(destinationFolderId)) {
            throw new InvalidMoveDestinationException("destination cannot be source itself");
        }
        if (isAncestor(folderId, destinationFolderId)) {
            throw new InvalidMoveDestinationException("destination cannot be a descendant of source");
        }

        // subtree id 수집
        List<UUID> subtreeFolderIds = new ArrayList<>();
        subtreeFolderIds.add(source.getId());
        subtreeFolderIds.addAll(collectDescendantFolderIds(source.getId()));
        List<UUID> subtreeFileIds = subtreeFolderIds.isEmpty()
            ? Collections.emptyList()
            : fileRepo.findActiveIdsByFolderIdIn(subtreeFolderIds);

        int itemCount = subtreeFolderIds.size() + subtreeFileIds.size();

        // permissions 정리 후보
        List<PermissionRow> folderGrants = permRepo.findActiveByResourceIn("folder", subtreeFolderIds);
        List<PermissionRow> fileGrants = subtreeFileIds.isEmpty()
            ? Collections.emptyList()
            : permRepo.findActiveByResourceIn("file", subtreeFileIds);

        List<PermissionRef> removedPermissions = new ArrayList<>();
        for (PermissionRow r : folderGrants) removedPermissions.add(toPermissionRef(r));
        for (PermissionRow r : fileGrants) removedPermissions.add(toPermissionRef(r));

        // shares 정리 후보
        List<Share> folderShares = shareRepo.findActiveByResourceIn("folder", subtreeFolderIds);
        List<Share> fileShares = subtreeFileIds.isEmpty()
            ? Collections.emptyList()
            : shareRepo.findActiveByResourceIn("file", subtreeFileIds);
        List<ShareRef> revokedShares = new ArrayList<>();
        for (Share s : folderShares) revokedShares.add(toShareRef(s));
        for (Share s : fileShares) revokedShares.add(toShareRef(s));

        // membership defaults
        Set<Permission> defaults = membershipResolver.resolve(actorId, destination.getScopeType(), destination.getScopeId());

        // 이름 충돌
        String nameConflict = null;
        if (folderRepo.existsActiveByParentAndNormalizedNameExcludingId(
                destination.getId(), source.getNormalizedName(), source.getId())) {
            nameConflict = source.getName();
        }

        return new MovePreviewResponse(
            itemCount,
            removedPermissions,
            revokedShares,
            new ArrayList<>(defaults),
            nameConflict
        );
    }

    public MovePreviewResponse previewFile(UUID fileId, UUID destinationFolderId, UUID actorId) {
        if (destinationFolderId == null) {
            throw new InvalidMoveDestinationException("destinationFolderId is required");
        }
        FileItem source = fileRepo.findByIdAndDeletedAtIsNull(fileId)
            .orElseThrow(() -> new FileNotFoundException("source file not found: " + fileId));
        Folder destination = folderRepo.findByIdAndDeletedAtIsNull(destinationFolderId)
            .orElseThrow(() -> new FolderNotFoundException("destination folder not found: " + destinationFolderId));

        List<PermissionRow> grants = permRepo.findActiveByResourceIn("file", List.of(fileId));
        List<Share> shares = shareRepo.findActiveByResourceIn("file", List.of(fileId));

        List<PermissionRef> removedPermissions = new ArrayList<>();
        for (PermissionRow r : grants) removedPermissions.add(toPermissionRef(r));
        List<ShareRef> revokedShares = new ArrayList<>();
        for (Share s : shares) revokedShares.add(toShareRef(s));

        Set<Permission> defaults = membershipResolver.resolve(actorId, destination.getScopeType(), destination.getScopeId());

        String nameConflict = null;
        if (fileRepo.existsActiveByFolderAndNormalizedNameExcludingId(
                destination.getId(), source.getNormalizedName(), fileId)) {
            nameConflict = source.getName();
        }

        return new MovePreviewResponse(
            1,
            removedPermissions,
            revokedShares,
            new ArrayList<>(defaults),
            nameConflict
        );
    }

    // ── helpers ──

    private boolean isAncestor(UUID candidateAncestorId, UUID nodeId) {
        UUID cursor = nodeId;
        Set<UUID> visited = new HashSet<>();
        int hops = 0;
        while (cursor != null && hops++ < 1000) {
            if (cursor.equals(candidateAncestorId)) return true;
            if (!visited.add(cursor)) return false;
            cursor = folderRepo.findByIdAndDeletedAtIsNull(cursor).map(Folder::getParentId).orElse(null);
        }
        return false;
    }

    private List<UUID> collectDescendantFolderIds(UUID rootId) {
        List<UUID> descendants = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        visited.add(rootId);
        Deque<UUID> frontier = new ArrayDeque<>();
        frontier.add(rootId);
        while (!frontier.isEmpty()) {
            UUID current = frontier.pollFirst();
            List<UUID> children = folderRepo.findIdsByParentIdAndDeletedAtIsNull(current);
            for (UUID childId : children) {
                if (!visited.add(childId)) continue;
                descendants.add(childId);
                frontier.addLast(childId);
                if (descendants.size() > MAX_CASCADE_NODES) {
                    throw new IllegalStateException("subtree size exceeded safety limit at " + childId);
                }
            }
        }
        return descendants;
    }

    private static PermissionRef toPermissionRef(PermissionRow r) {
        return new PermissionRef(r.getId(), r.getSubjectType(), r.getSubjectId(), r.getPreset());
    }

    private static ShareRef toShareRef(Share s) {
        // resourceType/resourceId는 nullable(plan KISS) — share-level info만 노출.
        return new ShareRef(s.getId(), null, null, s.getSharedBy());
    }
}
