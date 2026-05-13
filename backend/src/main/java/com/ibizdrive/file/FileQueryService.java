package com.ibizdrive.file;

import com.ibizdrive.file.dto.FileDetailResponse;
import com.ibizdrive.file.dto.FileDto;
import com.ibizdrive.file.dto.SubjectGrantBriefDto;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.folder.dto.BreadcrumbCrumbDto;
import com.ibizdrive.permission.PermissionService;
import com.ibizdrive.permission.dto.PermissionDto;
import com.ibizdrive.user.UserRepository;
import com.ibizdrive.user.dto.UserBriefDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Phase B P2 + P_panel-A 읽기 전용 service — frontend 파일 상세(RightPanel) wiring 용도.
 *
 * <p>{@link com.ibizdrive.folder.FolderQueryService}와 동일한 책임 분리 패턴: mutation은
 * {@link FileMutationService}, 본 service는 fetch + DTO 매핑만. Audit 미발행 (read-only는 audit
 * 비대상 — docs/03 §4 노출 정책).
 *
 * <p>visibility/권한 게이트는 controller SpEL이 담당. service는 active(soft-delete 제외) 파일만 반환하며,
 * 부재/휴지통 파일은 모두 {@link FileNotFoundException}으로 통일하여 404 envelope으로 매핑된다.
 *
 * <p>P_panel-A — RightPanel detail 응답을 owner + sharedWith + folderPath로 확장. viewCount는
 * {@code FILE_VIEWED} audit emit ADR #9 blocker로 본 트랙 scope 외.
 */
@Service
public class FileQueryService {

    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final FolderRepository folderRepository;
    private final PermissionService permissionService;

    public FileQueryService(
        FileRepository fileRepository,
        UserRepository userRepository,
        FolderRepository folderRepository,
        PermissionService permissionService
    ) {
        this.fileRepository = fileRepository;
        this.userRepository = userRepository;
        this.folderRepository = folderRepository;
        this.permissionService = permissionService;
    }

    /**
     * 활성 파일 상세를 RightPanel envelope으로 반환. 부재 또는 soft-delete 시 {@link FileNotFoundException}.
     *
     * <p>{@link FileDetailResponse#owner} — file.ownerId 의 user lookup. soft-deleted/missing 시 null
     * (FE placeholder).
     * <p>{@link FileDetailResponse#sharedWith} — {@link PermissionService#listPermissions}를 재사용해
     * subjectName 까지 resolve. PERMISSION_ADMIN 게이트가 있는 {@code /permissions} list와 달리 본
     * 응답은 READ 권한자에게 노출되므로 {@code grantedBy} 등 privacy 필드는 {@link SubjectGrantBriefDto}
     * 매핑에서 제외.
     * <p>{@link FileDetailResponse#folderPath} — file.folderId 부터 root 까지 부모 체인 (root → 직접 부모).
     * soft-deleted 폴더 발견 시 break (CLAUDE.md §3 원칙 6: deleted_at IS NULL 일관성).
     */
    @Transactional(readOnly = true)
    public FileDetailResponse loadDetail(UUID id) {
        FileItem entity = fileRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new FileNotFoundException("file not found: " + id));

        FileDto fileDto = FileDto.from(entity);

        UserBriefDto owner = userRepository.findById(entity.getOwnerId())
            .map(UserBriefDto::from)
            .orElse(null);

        List<PermissionDto> grants = permissionService.listPermissions("file", id);
        List<SubjectGrantBriefDto> sharedWith = new ArrayList<>(grants.size());
        for (PermissionDto p : grants) {
            sharedWith.add(new SubjectGrantBriefDto(
                p.subjectType(),
                p.subjectId(),
                resolveSubjectName(p),
                p.preset()
            ));
        }

        List<BreadcrumbCrumbDto> folderPath = buildFolderPath(entity.getFolderId());

        return new FileDetailResponse(fileDto, owner, sharedWith, folderPath);
    }

    /**
     * everyone grant 시 subjectName이 null이지만 FE는 "전체" 라벨이 필요. user/department는
     * {@link PermissionService#listPermissions} 가 batch resolve 한 결과를 그대로 사용 (lookup
     * 실패는 null fallback).
     */
    private static String resolveSubjectName(PermissionDto p) {
        if ("everyone".equals(p.subjectType())) return "전체";
        return p.subjectName();
    }

    /**
     * 파일의 폴더 부모 체인을 root 부터 직접 부모까지 순서대로 반환. soft-deleted 폴더 도달 시 chain break
     * (FE 는 부분 chain을 안전 처리).
     */
    private List<BreadcrumbCrumbDto> buildFolderPath(UUID startFolderId) {
        List<BreadcrumbCrumbDto> chain = new ArrayList<>();
        UUID cursor = startFolderId;
        while (cursor != null) {
            Folder f = folderRepository.findByIdAndDeletedAtIsNull(cursor).orElse(null);
            if (f == null) break;
            chain.add(new BreadcrumbCrumbDto(f.getId(), f.getName(), f.getSlug()));
            cursor = f.getParentId();
        }
        Collections.reverse(chain);
        return chain;
    }
}
