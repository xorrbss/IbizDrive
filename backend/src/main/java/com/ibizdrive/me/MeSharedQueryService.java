package com.ibizdrive.me;

import com.ibizdrive.file.FileItem;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.me.dto.MySharedWithMeItem;
import com.ibizdrive.permission.PermissionRepository;
import com.ibizdrive.permission.PermissionRow;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * User Home Dashboard SharedWithMeCard 의 read-side query service.
 *
 * <p>{@link PermissionRepository#findGrantsToUserPaged} 결과를 frontend 가 소비할 수 있는 형태로
 * 보강한다 — file/folder 이름 + granter user 이름 batch resolve (N+1 회피).
 *
 * <p>resource 가 soft-delete 된 경우 응답에서 제외. dangling grant 는 admin matrix 와 다른 정책 —
 * 사용자 시각화에선 의미 없는 row 가 보이지 않게 하는 게 우선.
 */
@Service
public class MeSharedQueryService {

    private final PermissionRepository permissionRepository;
    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;

    public MeSharedQueryService(PermissionRepository permissionRepository,
                                FileRepository fileRepository,
                                FolderRepository folderRepository,
                                UserRepository userRepository) {
        this.permissionRepository = permissionRepository;
        this.fileRepository = fileRepository;
        this.folderRepository = folderRepository;
        this.userRepository = userRepository;
    }

    public List<MySharedWithMeItem> list(UUID userId, int limit) {
        List<PermissionRow> rows = permissionRepository.findGrantsToUserPaged(
            userId, PageRequest.of(0, limit));
        if (rows.isEmpty()) {
            return List.of();
        }

        Set<UUID> fileIds = new HashSet<>();
        Set<UUID> folderIds = new HashSet<>();
        Set<UUID> granterIds = new HashSet<>();
        for (PermissionRow r : rows) {
            if ("file".equals(r.getResourceType())) fileIds.add(r.getResourceId());
            else if ("folder".equals(r.getResourceType())) folderIds.add(r.getResourceId());
            granterIds.add(r.getGrantedBy());
        }

        Map<UUID, String> fileNames = new HashMap<>();
        for (FileItem f : fileRepository.findAllByIdInAndDeletedAtIsNull(fileIds)) {
            fileNames.put(f.getId(), f.getName());
        }
        Map<UUID, String> folderNames = new HashMap<>();
        for (Folder f : folderRepository.findAllByIdInAndDeletedAtIsNull(folderIds)) {
            folderNames.put(f.getId(), f.getName());
        }
        Map<UUID, String> granterNames = new HashMap<>();
        for (User u : userRepository.findAllById(granterIds)) {
            granterNames.put(u.getId(), u.getDisplayName());
        }

        List<MySharedWithMeItem> items = new ArrayList<>(rows.size());
        for (PermissionRow r : rows) {
            String name = "file".equals(r.getResourceType())
                ? fileNames.get(r.getResourceId())
                : folderNames.get(r.getResourceId());
            if (name == null) {
                // soft-delete 또는 dangling row — 응답 제외
                continue;
            }
            String granterName = granterNames.getOrDefault(r.getGrantedBy(), "");
            items.add(new MySharedWithMeItem(
                r.getId(),
                r.getResourceType(),
                r.getResourceId(),
                name,
                r.getPreset(),
                r.getCreatedAt(),
                new MySharedWithMeItem.Granter(r.getGrantedBy(), granterName)
            ));
        }
        return items;
    }
}
