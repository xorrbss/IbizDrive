package com.ibizdrive.folder;

import com.ibizdrive.file.FileItem;
import com.ibizdrive.file.FileNameConflictException;
import com.ibizdrive.file.FileNotFoundException;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.permission.Permission;
import com.ibizdrive.permission.PermissionRepository;
import com.ibizdrive.permission.PermissionResolver;
import com.ibizdrive.share.Share;
import com.ibizdrive.share.ShareRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
 * Tasks 11~14가 step 1~6을 구현했다:
 * <ol>
 *   <li>source/destination 잠금 + 권한 검증 (Task 11)</li>
 *   <li>이름 충돌 검사 (Task 11)</li>
 *   <li>scope 재할당 (Task 12)</li>
 *   <li>permission 정리 (Task 13)</li>
 *   <li>share 정리 (Task 14)</li>
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
    private static final int MAX_CASCADE_NODES = 100_000;

    private final FolderRepository folderRepo;
    private final FileRepository fileRepo;
    private final PermissionResolver permissionResolver;
    private final ApplicationEventPublisher eventPublisher;
    private final PermissionRepository permRepo;
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
     * Cross-workspace folder 이동 — step 1~7 구현 (Task 15로 완성).
     *
     * @param folderId            이동할 폴더 id
     * @param destinationFolderId 목적지 폴더 id
     * @param actorId             요청 사용자 id
     * @return 이동된 Folder entity (parent_id가 destination으로 변경된 상태)
     */
    public Folder moveFolder(UUID folderId, UUID destinationFolderId, UUID actorId) {
        if (destinationFolderId == null) {
            throw new InvalidMoveDestinationException("destinationFolderId is required");
        }

        Folder source = folderRepo.lockByIdAndDeletedAtIsNull(folderId)
            .orElseThrow(() -> new FolderNotFoundException("source folder not found: " + folderId));
        Folder destination = folderRepo.lockByIdAndDeletedAtIsNull(destinationFolderId)
            .orElseThrow(() -> new FolderNotFoundException("destination folder not found: " + destinationFolderId));

        // Capture before step 3 batch update mutates the JPA-managed entity's scopeType in-place
        ScopeType sourceScopeTypeBeforeMove = source.getScopeType();

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

        // step 5 (collected EARLY due to V6 ON DELETE CASCADE on shares.permission_id —
        // perm delete in step 4 cascade-deletes share rows; collect IDs before they vanish):
        Instant now = Instant.now();
        List<Share> folderShares =
            shareRepo.findActiveByResourceIn("folder", subtreeFolderIds);
        List<Share> fileShares = subtreeFileIds.isEmpty()
            ? Collections.emptyList()
            : shareRepo.findActiveByResourceIn("file", subtreeFileIds);
        List<UUID> allShareIds = new ArrayList<>();
        for (var s : folderShares) allShareIds.add(s.getId());
        for (var s : fileShares) allShareIds.add(s.getId());
        if (!allShareIds.isEmpty()) {
            shareRepo.revokeByIds(allShareIds, actorId, now);
        }

        // step 4: 명시 권한 정리 (cascade-deletes the just-revoked share rows via V6 FK)
        permRepo.deleteByResourceIn("folder", subtreeFolderIds);
        if (!subtreeFileIds.isEmpty()) {
            permRepo.deleteByResourceIn("file", subtreeFileIds);
        }

        // step 6: parent_id 변경
        // NOTE: step 3 native @Modifying updateScopeBatch가 DB의 source folder scope를 갱신했지만
        // in-memory entity는 stale. saveAndFlush가 stale scope를 덮어쓰지 않도록 새 값으로 동기화.
        source.assignScope(destination.getScopeType(), destination.getScopeId());
        source.setParentId(destination.getId());
        source.setUpdatedAt(now);
        folderRepo.saveAndFlush(source);

        // step 7: invariant assert
        // step 7-a: scope 일관성 (folder + file)
        int badFolders = folderRepo.countByIdInAndScopeNotMatching(subtreeFolderIds, destScopeType, destScopeId);
        if (badFolders > 0) {
            throw new IllegalStateException("invariant violation: " + badFolders + " folders not in destination scope");
        }
        if (!subtreeFileIds.isEmpty()) {
            int badFiles = fileRepo.countByIdInAndScopeNotMatching(subtreeFileIds, destScopeType, destScopeId);
            if (badFiles > 0) {
                throw new IllegalStateException("invariant violation: " + badFiles + " files not in destination scope");
            }
        }
        // step 7-b: permissions 0 (subtree 내)
        int permLeft = permRepo.findActiveByResourceIn("folder", subtreeFolderIds).size()
                     + (subtreeFileIds.isEmpty() ? 0 : permRepo.findActiveByResourceIn("file", subtreeFileIds).size());
        if (permLeft > 0) {
            throw new IllegalStateException("invariant violation: " + permLeft + " explicit permissions remain");
        }
        // step 7-c: active shares 0
        int sharesLeft = shareRepo.findActiveByResourceIn("folder", subtreeFolderIds).size()
                       + (subtreeFileIds.isEmpty() ? 0 : shareRepo.findActiveByResourceIn("file", subtreeFileIds).size());
        if (sharesLeft > 0) {
            throw new IllegalStateException("invariant violation: " + sharesLeft + " active shares remain");
        }

        // step 8: event publish
        eventPublisher.publishEvent(new CrossWorkspaceMoveCompletedEvent(
            "folder",
            source.getId(),
            sourceScopeTypeBeforeMove,
            destination.getScopeType(),
            destScopeId,
            subtreeFolderIds.size(),
            subtreeFileIds.size(),
            allShareIds.size(),
            actorId,
            now
        ));
        return source;
    }

    /**
     * Cross-workspace file 이동 — Task 18 (Phase 4).
     *
     * <p>subtree 없음. 7-step 흐름 (moveFolder의 file-단건 특수화):
     * <ol>
     *   <li>source file + destination folder 잠금</li>
     *   <li>권한 검증 (source: EDIT+SHARE, dest: UPLOAD)</li>
     *   <li>이름 충돌 검사</li>
     *   <li>scope update (single file)</li>
     *   <li>permissions 정리 — <b>CASCADE 주의</b>: shares.permission_id ON DELETE CASCADE 이므로
     *       share id 수집을 permissions 삭제 이전에 수행한다.</li>
     *   <li>folder_id 변경</li>
     *   <li>invariant assert + event publish</li>
     * </ol>
     *
     * @param fileId              이동할 파일 id
     * @param destinationFolderId 목적지 폴더 id
     * @param actorId             요청 사용자 id
     * @return 이동된 FileItem entity (folder_id + scope가 destination 값으로 변경된 상태)
     */
    public FileItem moveFile(UUID fileId, UUID destinationFolderId, UUID actorId) {
        if (destinationFolderId == null) {
            throw new InvalidMoveDestinationException("destinationFolderId is required");
        }
        FileItem source = fileRepo.lockByIdAndDeletedAtIsNull(fileId)
            .orElseThrow(() -> new FileNotFoundException("source file not found: " + fileId));
        Folder destination = folderRepo.lockByIdAndDeletedAtIsNull(destinationFolderId)
            .orElseThrow(() -> new FolderNotFoundException("destination folder not found: " + destinationFolderId));

        if (source.getScopeType() == destination.getScopeType()
            && source.getScopeId().equals(destination.getScopeId())) {
            throw new IllegalArgumentException(
                "use FileMutationService.move for same-scope moves; cross-workspace path requires distinct scopes");
        }

        // step 1: 권한 검증
        Set<Permission> sourcePerms = permissionResolver.resolveFor(actorId, "file", fileId);
        if (!sourcePerms.contains(Permission.EDIT) || !sourcePerms.contains(Permission.SHARE)) {
            throw new DestWorkspaceDeniedException("source file requires EDIT and SHARE");
        }
        Set<Permission> destPerms = permissionResolver.resolveFor(actorId, "folder", destinationFolderId);
        if (!destPerms.contains(Permission.UPLOAD)) {
            throw new DestWorkspaceDeniedException("destination folder requires UPLOAD");
        }

        // step 2: 이름 충돌
        if (fileRepo.existsActiveByFolderAndNormalizedNameExcludingId(
                destinationFolderId, source.getNormalizedName(), fileId)) {
            throw new FileNameConflictException(
                "file name already exists at destination: " + source.getNormalizedName());
        }

        String destScopeType = destination.getScopeType().dbValue();
        UUID destScopeId = destination.getScopeId();
        ScopeType sourceScopeTypeBeforeMove = source.getScopeType();

        // step 3: scope update (single file)
        fileRepo.updateScopeBatch(List.of(fileId), destScopeType, destScopeId);

        // Collect active share ids BEFORE deleting permissions — shares.permission_id has
        // ON DELETE CASCADE (V6 migration), so permission deletion will cascade-delete share rows.
        // We capture the count here for the event and skip revokeByIds (rows will be gone).
        List<Share> activeSharesBefore = shareRepo.findActiveByResourceIn("file", List.of(fileId));
        List<UUID> shareIds = new ArrayList<>();
        for (Share s : activeSharesBefore) shareIds.add(s.getId());

        // step 4: permissions 정리 (cascade-deletes associated share rows via FK)
        permRepo.deleteByResourceIn("file", List.of(fileId));

        // step 5 + step 6 share a single timestamp
        Instant now = Instant.now();

        // step 5: shares — rows already deleted by cascade; revokeByIds is a no-op but
        // calling it explicitly guards against schema changes that remove CASCADE in future.
        if (!shareIds.isEmpty()) {
            shareRepo.revokeByIds(shareIds, actorId, now);
        }

        // step 6: folder_id 변경
        // NOTE: step 3 native @Modifying updateScopeBatch가 DB의 scope_type/scope_id를 갱신했지만
        // in-memory entity는 stale 상태. saveAndFlush가 JPA dirty-check로 모든 컬럼을 UPDATE에 포함하면
        // stale scope가 DB의 새 값을 덮어쓴다 → invariant 실패. 명시적으로 entity scope를 새 값으로 동기화.
        source.assignScope(destination.getScopeType(), destination.getScopeId());
        source.setFolderId(destinationFolderId);
        source.setUpdatedAt(now);
        fileRepo.saveAndFlush(source);

        // step 7: invariant assert
        int badFiles = fileRepo.countByIdInAndScopeNotMatching(List.of(fileId), destScopeType, destScopeId);
        if (badFiles > 0) throw new IllegalStateException("invariant: file scope mismatch");
        int permLeft = permRepo.findActiveByResourceIn("file", List.of(fileId)).size();
        if (permLeft > 0) throw new IllegalStateException("invariant: permissions remain");
        int sharesLeft = shareRepo.findActiveByResourceIn("file", List.of(fileId)).size();
        if (sharesLeft > 0) throw new IllegalStateException("invariant: shares remain");

        // step 8: event publish
        eventPublisher.publishEvent(new CrossWorkspaceMoveCompletedEvent(
            "file",
            fileId,
            sourceScopeTypeBeforeMove,
            destination.getScopeType(),
            destScopeId,
            0,                  // subtreeFolderCount — file has no subtree
            1,                  // subtreeFileCount
            shareIds.size(),    // revokedShareCount (collected before cascade delete)
            actorId,
            now
        ));
        return source;
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
                if (descendants.size() > MAX_CASCADE_NODES) {
                    throw new IllegalStateException("subtree size exceeded safety limit at " + childId);
                }
            }
        }
        return descendants;
    }
}
