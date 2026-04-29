package com.ibizdrive.permission;

import com.ibizdrive.common.error.ResourceNotFoundException;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.permission.dto.GrantPermissionRequest;
import com.ibizdrive.permission.dto.PermissionDto;
import com.ibizdrive.user.IbizDriveUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Resource-level 권한 grant/revoke endpoint — A4.4 (docs/02 §7.10).
 *
 * <p><b>POST</b> {@code /api/{resource}/{id}/permissions} — 새 grant. 응답 {@code 201 { permission: PermissionDto }}.
 * <br><b>DELETE</b> {@code /api/permissions/{permissionId}} — grant 삭제. 응답 {@code 204 No Content}.
 *
 * <p><b>인가</b>:
 * <ul>
 *   <li>POST: {@code @PreAuthorize("hasPermission(#id, #resource, 'PERMISSION_ADMIN')")} —
 *       대상 노드의 {@code admin} preset 보유자만 (또는 시스템 ROLE ADMIN). 평가는
 *       {@link IbizDrivePermissionEvaluator} 위임 (A4.3 머지 후 resource-level 동작).</li>
 *   <li>DELETE: {@code @PreAuthorize("@permissionService.canRevokePermission(#permissionId, principal)")} —
 *       grant row 의 resource 에 대한 PERMISSION_ADMIN 평가. MVP 는 ROLE.ADMIN 만 허용 (A4.3 머지 후 교체).</li>
 * </ul>
 *
 * <p><b>리소스 존재 검증</b>: SpEL 평가는 {@code id} 만 받고 존재 여부는 검사하지 않으므로 본체에서 추가 lookup —
 * {@code resource='file'} → {@link FileRepository#findByIdAndDeletedAtIsNull(UUID)},
 * {@code resource='folder'} → {@code folders} 테이블 직접 조회 (Folder entity 부재, A4.5 이월).
 * 부재 시 {@link ResourceNotFoundException} → 404.
 *
 * <p><b>에러 매핑</b>:
 * <ul>
 *   <li>입력 검증 실패 (subject_id ↔ everyone, expiresAt 과거, preset 미지원 wire) → 400 BAD_REQUEST</li>
 *   <li>리소스 미존재 (folder/file/permissionId) → 404 NOT_FOUND</li>
 *   <li>중복 grant (동일 resource×subject) → 409 PERMISSION_CONFLICT (V5 unique 인덱스 위반)</li>
 *   <li>인가 거부 → 403 PERMISSION_DENIED (envelope 은 evaluator 의 ThreadLocal 컨텍스트 사용)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class PermissionController {

    private final PermissionService permissionService;
    private final FileRepository fileRepository;
    private final JdbcTemplate jdbcTemplate;

    public PermissionController(PermissionService permissionService,
                                FileRepository fileRepository,
                                JdbcTemplate jdbcTemplate) {
        this.permissionService = permissionService;
        this.fileRepository = fileRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 권한 grant — docs/02 §7.10. {@code id} (path) 와 {@code resource} (path: "folders"|"files")
     * 가 SpEL 의 {@code #id}/{@code #resource} 로 전달된다. {@code resource} 는 evaluator/service 와
     * 일치하도록 단수형(folder/file)으로 정규화하여 service 에 넘긴다.
     *
     * <p>SpEL 의 {@code #resource} 는 path 그대로(복수형) 평가되지만, MVP evaluator 는 user-level 만 보고
     * type 을 미사용 → 호환. A4.3 resource-level 진입 시 evaluator 내부에서 단수형 정규화 권장.
     */
    @PostMapping("/{resource}/{id}/permissions")
    @PreAuthorize("hasPermission(#id, #resource, 'PERMISSION_ADMIN')")
    public ResponseEntity<Map<String, PermissionDto>> grant(
        @PathVariable("resource") String resource,
        @PathVariable("id") UUID id,
        @RequestBody @Valid GrantPermissionRequest body,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        String resourceType = normalizeResourceType(resource);
        ensureResourceExists(resourceType, id);

        Preset preset = Preset.from(body.preset());

        UUID subjectId = body.subject().id();
        String subjectType = body.subject().type();

        PermissionRow saved = permissionService.grantPermission(
            resourceType,
            id,
            subjectType,
            subjectId,
            preset,
            body.expiresAt(),
            principal.getUser().getId()
        );

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(Map.of("permission", PermissionDto.from(saved)));
    }

    /**
     * 권한 revoke — docs/02 §7.10. {@code @permissionService.canRevokePermission} 가 deny → 403.
     *
     * <p>row 미존재 시 service 가 {@link ResourceNotFoundException} → 404. 인가는 통과했으나 row 가
     * 사이에 삭제된 race 케이스 — 멱등성 측면에서도 404 가 자연스럽다.
     */
    @DeleteMapping("/permissions/{permissionId}")
    @PreAuthorize("@permissionService.canRevokePermission(#permissionId, principal)")
    public ResponseEntity<Void> revoke(
        @PathVariable("permissionId") UUID permissionId,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        permissionService.revokePermission(permissionId, principal.getUser().getId());
        return ResponseEntity.noContent().build();
    }

    private static final Set<String> RESOURCE_PATH_SINGULAR = Set.of("folder", "file");
    private static final Map<String, String> RESOURCE_PATH_PLURAL =
        Map.of("folders", "folder", "files", "file");

    /**
     * URL path segment 를 service 의 {@code resourceType} (단수형) 으로 정규화.
     * 양쪽 형태(단수/복수)를 허용 — 향후 라우팅 컨벤션이 바뀌어도 controller 단에서 흡수.
     */
    private static String normalizeResourceType(String path) {
        if (RESOURCE_PATH_SINGULAR.contains(path)) return path;
        String mapped = RESOURCE_PATH_PLURAL.get(path);
        if (mapped != null) return mapped;
        throw new IllegalArgumentException("resource path must be one of folder/folders/file/files");
    }

    /**
     * 대상 리소스가 활성 상태로 존재하는지 확인. 부재 시 {@link ResourceNotFoundException} → 404.
     *
     * <p>{@code folder} 는 Folder entity 가 A4.5 까지 미존재 (A4.5 a4-crud 세션 이월) — V5 의 {@code folders} 테이블에
     * 직접 SELECT. {@code file} 은 {@link FileRepository#findByIdAndDeletedAtIsNull(UUID)} 사용.
     */
    private void ensureResourceExists(String resourceType, UUID id) {
        switch (resourceType) {
            case "file" -> fileRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("file not found: " + id));
            case "folder" -> {
                Integer found = jdbcTemplate.query(
                    "SELECT 1 FROM folders WHERE id = ? AND deleted_at IS NULL",
                    rs -> rs.next() ? 1 : null,
                    id
                );
                if (found == null) {
                    throw new ResourceNotFoundException("folder not found: " + id);
                }
            }
            default -> throw new IllegalArgumentException("unsupported resourceType: " + resourceType);
        }
    }
}
