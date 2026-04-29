package com.ibizdrive.permission;

import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Resource-level 권한 평가 헬퍼 — A4.3.
 *
 * <p>사용자가 특정 리소스(folder/file)에 대해 요구 {@link Permission}을 보유하는지를
 * {@link PermissionRepository#findEffective(UUID, String, UUID)}가 반환한 grant 행 목록에서
 * preset → permission 평면화로 판단한다.
 *
 * <p>본 클래스는 평가만 담당 — 재귀 CTE/조상 폴더/만료/soft-delete/everyone subject 처리는
 * 모두 {@code findEffective}의 native query 안에 캡슐화되어 있다 (A4-data 산출물).
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

    private final PermissionRepository permissionRepository;

    public PermissionResolver(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    /**
     * 사용자가 주어진 리소스에 대해 요구 권한을 보유하는지.
     *
     * @param userId       actor
     * @param resourceType {@code "folder"} 또는 {@code "file"}
     * @param resourceId   리소스 식별자
     * @param required     요구 권한 (non-null)
     * @return 보유하면 true. 보유하지 않거나 grant 행이 없으면 false.
     */
    public boolean isGranted(UUID userId, String resourceType, UUID resourceId, Permission required) {
        if (userId == null || resourceType == null || resourceId == null || required == null) {
            return false;
        }
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
}
