package com.ibizdrive.folder.dto;

import com.ibizdrive.permission.Permission;

import java.util.List;

/**
 * spec §5.6 — preview 응답 envelope.
 *
 * <ul>
 *   <li>{@code itemCount}: 이동될 폴더+파일 총 개수 (folder는 subtree 포함, file은 1).</li>
 *   <li>{@code removedPermissions}: subtree 내에서 정리될 권한 grant.</li>
 *   <li>{@code revokedShares}: subtree resource source인 active share.</li>
 *   <li>{@code targetMembershipDefaults}: 이동 후 destination workspace 멤버십 기본권 (호출자 기준).</li>
 *   <li>{@code nameConflict}: destination 안에 동일 normalized_name 활성 항목 존재 시 그 이름 (없으면 null).</li>
 * </ul>
 */
public record MovePreviewResponse(
    int itemCount,
    List<PermissionRef> removedPermissions,
    List<ShareRef> revokedShares,
    List<Permission> targetMembershipDefaults,
    String nameConflict
) {}
