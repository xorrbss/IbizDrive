package com.ibizdrive.permission;

import com.ibizdrive.file.FileItem;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.folder.ScopeType;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Resource-level 권한 평가 헬퍼 — A4.3, Plan A Task 23.
 *
 * <p>사용자가 특정 리소스(folder/file)에 대해 요구 {@link Permission}을 보유하는지를
 * 두 단계로 평가한다:
 *
 * <ol>
 *   <li><b>Explicit/share grants 단계</b> — {@link PermissionRepository#findEffective(UUID, String, UUID)}가
 *       반환한 grant 행 목록에서 preset → permission 평면화로 판단.
 *       재귀 CTE/조상 폴더/만료/soft-delete/everyone subject 처리는 모두
 *       {@code findEffective}의 native query 안에 캡슐화되어 있다 (A4-data 산출물).</li>
 *   <li><b>Workspace membership 단계</b> (Plan A Task 23) — explicit/share deny 시 폴더 또는 파일의
 *       {@code scopeType}/{@code scopeId}를 조회해 {@link WorkspaceMembershipResolver#resolve}로
 *       묵시적 권한을 확인. 리소스 미존재 시 이 단계를 건너뛴다.</li>
 * </ol>
 *
 * <p>호출처는 {@link IbizDrivePermissionEvaluator} 내부 1군데. {@link PermissionService}는
 * A3 ROLE 평가 경로를 그대로 유지하고 본 헬퍼와 별개로 동작한다 (ADR #26 — SpEL 시그니처 무영향).
 *
 * <p>주의: {@link Preset#SHARE}는 {@code shares} 테이블 도입 전이므로 V5 CHECK 제약에서 제외되어
 * 있다 (PermissionRow.java 주석, ADR #28 line 22). 따라서 {@code findEffective} 결과의 preset 문자열은
 * read|upload|edit|admin 4종만 등장 — {@link Preset#from} 호출이 IllegalArgumentException을 던지면
 * 데이터 불일치 (V5 CHECK 위반) 또는 신규 preset 도입 누락 신호이므로 fail-fast 의도로 그대로 전파한다.
 */
@Component
public class PermissionResolver {

    private static final String RESOURCE_TYPE_FOLDER = "folder";
    private static final String RESOURCE_TYPE_FILE = "file";

    private final PermissionRepository permissionRepository;
    private final WorkspaceMembershipResolver membershipResolver;
    private final FolderRepository folderRepository;
    private final FileRepository fileRepository;

    public PermissionResolver(PermissionRepository permissionRepository,
                              WorkspaceMembershipResolver membershipResolver,
                              FolderRepository folderRepository,
                              FileRepository fileRepository) {
        this.permissionRepository = permissionRepository;
        this.membershipResolver = membershipResolver;
        this.folderRepository = folderRepository;
        this.fileRepository = fileRepository;
    }

    /**
     * 사용자가 주어진 리소스에 대해 요구 권한을 보유하는지.
     *
     * <p>1차: explicit/share grants (기존 PermissionRepository.findEffective 경로 유지).
     * 1차에서 granted이면 2차 skip (short-circuit).
     * 2차: workspace membership grants — FolderRepository/FileRepository로 scope 조회 후
     * WorkspaceMembershipResolver.resolve.
     *
     * @param userId       actor
     * @param resourceType {@code "folder"} 또는 {@code "file"}
     * @param resourceId   리소스 식별자
     * @param required     요구 권한 (non-null)
     * @return 보유하면 true. 보유하지 않거나 grant 행이 없고 membership도 없으면 false.
     */
    public boolean isGranted(UUID userId, String resourceType, UUID resourceId, Permission required) {
        if (userId == null || resourceType == null || resourceId == null || required == null) {
            return false;
        }

        // 1) Explicit/share grants — 기존 A4.3 경로 (재귀 CTE/만료/everyone subject 처리).
        if (hasExplicitOrSharedGrant(userId, resourceType, resourceId, required)) {
            return true;
        }

        // 2) Workspace membership grants — Plan A Task 23 신규 경로.
        return hasMembershipGrant(userId, resourceType, resourceId, required);
    }

    private boolean hasExplicitOrSharedGrant(UUID userId, String resourceType, UUID resourceId,
                                              Permission required) {
        List<PermissionRow> rows = permissionRepository.findEffective(userId, resourceType, resourceId);
        if (rows.isEmpty()) {
            return false;
        }
        Set<Permission> union = EnumSet.noneOf(Permission.class);
        for (PermissionRow row : rows) {
            // Preset.from은 unknown wire 시 IllegalArgumentException — V5 CHECK 위반 신호로 전파.
            Preset preset = Preset.from(row.getPreset());
            union.addAll(preset.permissions());
            if (union.contains(required)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMembershipGrant(UUID userId, String resourceType, UUID resourceId,
                                       Permission required) {
        ResourceScope scope = resolveScope(resourceType, resourceId);
        if (scope == null) {
            return false;
        }
        return membershipResolver.resolve(userId, scope.scopeType(), scope.scopeId()).contains(required);
    }

    private ResourceScope resolveScope(String resourceType, UUID resourceId) {
        if (RESOURCE_TYPE_FOLDER.equals(resourceType)) {
            return folderRepository.findById(resourceId)
                .map(f -> new ResourceScope(f.getScopeType(), f.getScopeId()))
                .orElse(null);
        }
        if (RESOURCE_TYPE_FILE.equals(resourceType)) {
            return fileRepository.findById(resourceId)
                .map(f -> new ResourceScope(f.getScopeType(), f.getScopeId()))
                .orElse(null);
        }
        return null;
    }

    private record ResourceScope(ScopeType scopeType, UUID scopeId) {}
}
