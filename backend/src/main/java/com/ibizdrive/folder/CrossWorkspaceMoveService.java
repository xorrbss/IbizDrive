package com.ibizdrive.folder;

import com.ibizdrive.file.FileItem;
import com.ibizdrive.file.FileNameConflictException;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.permission.Permission;
import com.ibizdrive.permission.PermissionRepository;
import com.ibizdrive.permission.PermissionResolver;
import com.ibizdrive.share.ShareRepository;
import org.springframework.context.ApplicationEventPublisher;
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
 * Plan D — cross-workspace (cross-scope) 이동 서비스.
 *
 * <p>Phase 3 전체 흐름(7 steps)을 단일 {@code @Transactional}로 묶는다.
 * 본 task(Task 11)는 step 1~2를 구현한다:
 * <ol>
 *   <li>source/destination 잠금 + 권한 검증</li>
 *   <li>이름 충돌 검사</li>
 *   <li>scope 재할당 (Task 12)</li>
 *   <li>permission 정리 (Task 13)</li>
 *   <li>share 정리 (Task 13)</li>
 *   <li>subtree cascade scope 갱신 (Task 14)</li>
 *   <li>이동 확정 + 이벤트 발행 (Task 15)</li>
 * </ol>
 *
 * <p>constructor parameter 설계: Tasks 12~15에서 추가될 {@link PermissionRepository}와
 * {@link ShareRepository}를 미리 포함해 constructor 변경 최소화 (Plan D Task 11 결정 (a)).
 */
@Service
@Transactional
public class CrossWorkspaceMoveService {

    private static final int ANCESTOR_WALK_LIMIT = 1000;

    private final FolderRepository folderRepo;
    private final FileRepository fileRepo;
    private final PermissionResolver permissionResolver;
    private final ApplicationEventPublisher eventPublisher;
    @SuppressWarnings("unused") // Tasks 13~15에서 사용
    private final PermissionRepository permRepo;
    @SuppressWarnings("unused") // Tasks 13~15에서 사용
    private final ShareRepository shareRepo;

    public CrossWorkspaceMoveService(FolderRepository folderRepo,
                                     FileRepository fileRepo,
                                     PermissionResolver permissionResolver,
                                     ApplicationEventPublisher eventPublisher,
                                     PermissionRepository permRepo,
                                     ShareRepository shareRepo) {
        this.folderRepo = folderRepo;
        this.fileRepo = fileRepo;
        this.permissionResolver = permissionResolver;
        this.eventPublisher = eventPublisher;
        this.permRepo = permRepo;
        this.shareRepo = shareRepo;
    }

    /**
     * Cross-workspace folder 이동 — step 1~3 구현, step 4~7은 Tasks 13~15에서 추가.
     *
     * @param folderId            이동할 폴더 id
     * @param destinationFolderId 목적지 폴더 id
     * @param actorId             요청 사용자 id
     * @return 이동된 (혹은 이동 준비된) Folder entity — Tasks 13~15에서 실제 이동 후 반환으로 교체
     */
    public Folder moveFolder(UUID folderId, UUID destinationFolderId, UUID actorId) {
        if (destinationFolderId == null) {
            throw new InvalidMoveDestinationException("destinationFolderId is required");
        }

        Folder source = folderRepo.lockByIdAndDeletedAtIsNull(folderId)
            .orElseThrow(() -> new FolderNotFoundException("source folder not found: " + folderId));
        Folder destination = folderRepo.lockByIdAndDeletedAtIsNull(destinationFolderId)
            .orElseThrow(() -> new FolderNotFoundException("destination folder not found: " + destinationFolderId));

        if (folderId.equals(destinationFolderId)) {
            throw new InvalidMoveDestinationException("cannot move folder into itself");
        }
        if (isDescendant(folderId, destinationFolderId)) {
            throw new InvalidMoveDestinationException("destination cannot be a descendant of source");
        }

        // 같은 scope면 cross-workspace 분기에 들어올 이유가 없음 — 호출자 실수 방지
        if (source.getScopeType() == destination.getScopeType()
            && source.getScopeId().equals(destination.getScopeId())) {
            throw new IllegalArgumentException(
                "use FolderMutationService.move for same-scope moves; cross-workspace path requires distinct scopes");
        }

        // step 1: 권한 검증
        Set<Permission> sourcePerms = permissionResolver.resolveFor(actorId, "folder", folderId);
        if (!sourcePerms.contains(Permission.EDIT) || !sourcePerms.contains(Permission.SHARE)) {
            throw new DestWorkspaceDeniedException("source folder requires EDIT and SHARE");
        }
        Set<Permission> destPerms = permissionResolver.resolveFor(actorId, "folder", destinationFolderId);
        if (!destPerms.contains(Permission.UPLOAD)) {
            throw new DestWorkspaceDeniedException("destination folder requires UPLOAD");
        }

        // step 2: 이름 충돌
        if (folderRepo.existsActiveByParentAndNormalizedNameExcludingId(
                destinationFolderId, source.getNormalizedName(), source.getId())) {
            throw new FolderNameConflictException(
                "folder name already exists at destination: " + source.getNormalizedName());
        }

        // step 3: subtree scope update
        List<UUID> subtreeFolderIds = new ArrayList<>();
        subtreeFolderIds.add(source.getId());
        subtreeFolderIds.addAll(collectDescendantFolderIds(source.getId()));
        List<UUID> subtreeFileIds = subtreeFolderIds.isEmpty()
            ? Collections.emptyList()
            : fileRepo.findActiveIdsByFolderIdIn(subtreeFolderIds);

        String destScopeType = destination.getScopeType().dbValue();
        UUID destScopeId = destination.getScopeId();

        folderRepo.updateScopeBatch(subtreeFolderIds, destScopeType, destScopeId);
        if (!subtreeFileIds.isEmpty()) {
            fileRepo.updateScopeBatch(subtreeFileIds, destScopeType, destScopeId);
        }

        // step 4~7: Tasks 13~15에서 구현 — 컴파일 통과용 placeholder return
        return source;
    }

    /**
     * Cross-workspace file 이동 — Task 18 (Phase 4)에서 구현.
     */
    public FileItem moveFile(UUID fileId, UUID destinationFolderId, UUID actorId) {
        throw new UnsupportedOperationException(
            "CrossWorkspaceMoveService.moveFile — implemented in Plan D Task 18");
    }

    // ── private helpers ──

    /**
     * {@code candidateAncestorId}가 {@code nodeId}의 조상(또는 자기 자신)인지 확인 — BFS ancestor walk.
     *
     * <p>{@link MovePreviewService#isAncestor} 패턴 동일: cursor가 node에서 root 방향으로 올라가며
     * candidateAncestorId와 일치하는지 검사. visited Set으로 cycle 방지, hops 상한으로 무한 루프 방지.
     *
     * @param candidateAncestorId 조상 후보 (source folder)
     * @param nodeId              확인 대상 (destination folder)
     * @return true이면 candidateAncestor가 node의 조상 → 이동 허용 불가
     */
    private boolean isDescendant(UUID candidateAncestorId, UUID nodeId) {
        UUID cursor = nodeId;
        Set<UUID> visited = new HashSet<>();
        int hops = 0;
        while (cursor != null && hops++ < ANCESTOR_WALK_LIMIT) {
            if (cursor.equals(candidateAncestorId)) return true;
            if (!visited.add(cursor)) return false;
            cursor = folderRepo.findByIdAndDeletedAtIsNull(cursor)
                .map(Folder::getParentId)
                .orElse(null);
        }
        return false;
    }

    /**
     * source 폴더의 후손 folder id BFS 수집 — root 자신은 제외 (호출자가 별도 추가).
     *
     * <p>{@link MovePreviewService#collectDescendantFolderIds} 패턴 동일. YAGNI: 공용 utility 추출
     * 없이 두 곳 중복 허용 (plan D 명시 결정).
     */
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
            }
        }
        return descendants;
    }
}
