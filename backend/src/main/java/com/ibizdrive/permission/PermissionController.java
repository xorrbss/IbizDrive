package com.ibizdrive.permission;

import com.ibizdrive.common.error.ResourceNotFoundException;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.permission.dto.GrantPermissionRequest;
import com.ibizdrive.permission.dto.PermissionDto;
import com.ibizdrive.user.IbizDriveUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
 * {@code resource='folder'} → {@link FolderRepository#findByIdAndDeletedAtIsNull(UUID)} (A4.5에서 entity layer 도입).
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
    private final FolderRepository folderRepository;
    private final IbizDrivePermissionEvaluator evaluator;

    public PermissionController(PermissionService permissionService,
                                FileRepository fileRepository,
                                FolderRepository folderRepository,
                                IbizDrivePermissionEvaluator evaluator) {
        this.permissionService = permissionService;
        this.fileRepository = fileRepository;
        this.folderRepository = folderRepository;
        this.evaluator = evaluator;
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
     * M8.0 — resource-level grant 목록 조회 (docs/02 §7.10).
     *
     * <p><b>인가</b>: {@code @PreAuthorize("hasPermission(#id, #resource, 'PERMISSION_ADMIN')")} —
     * POST/DELETE 와 대칭. grant 분포는 권한 관리 정보이므로 일반 READ 권한으로는 노출하지 않는다 (베타 GO
     * 게이트, m8 트랙 closure 의 핵심 결정).
     *
     * <p>{@code resource} path 는 {@link #normalizeResourceType} 로 단수형 정규화. 리소스 미존재 시
     * {@link ResourceNotFoundException} → 404. service 가 created_at ASC, id ASC 정렬 + subjectName
     * batch resolve 한 결과를 {@code { items: PermissionDto[] }} 로 wrap.
     *
     * <p><b>페이지네이션 미적용</b> — 단일 리소스의 grant 수가 작다는 가정 (docs/02 §7.10). 향후 운영 데이터
     * 누적 후 cursor 도입은 별도 트랙.
     */
    @GetMapping("/{resource}/{id}/permissions")
    @PreAuthorize("hasPermission(#id, #resource, 'PERMISSION_ADMIN')")
    public ResponseEntity<Map<String, List<PermissionDto>>> list(
        @PathVariable("resource") String resource,
        @PathVariable("id") UUID id
    ) {
        String resourceType = normalizeResourceType(resource);
        ensureResourceExists(resourceType, id);

        List<PermissionDto> items = permissionService.listPermissions(resourceType, id);
        return ResponseEntity.ok(Map.of("items", items));
    }

    /**
     * A11 — 현재 사용자의 effective 권한 조회 (docs/02 §7.10 line 1173).
     *
     * <p>{@code nodeId} 미지정 시 ROLE 기반 권한만 반환 (전역 권한 컨텍스트). 지정 시 해당 노드(folder
     * 또는 file)에 대한 ROLE 권한 ∪ resource-level grant. {@link IbizDrivePermissionEvaluator#resolveAll}
     * 에 위임 — 평가 정책은 {@code hasPermission}과 동일하나 9 권한 전체를 한 번에 산출.
     *
     * <p>응답 본문: {@code { "permissions": ["READ", "DOWNLOAD", ...] }}. 권한은 {@link Permission}
     * enum 정의 순(natural order)으로 정렬. frontend {@code api.getEffectivePermissions} 의
     * {@code Promise<Permission[]>} 시그니처에 맞춤 (CLAUDE.md §4 계약 파일 표).
     *
     * <p><b>가드</b>: {@code @PreAuthorize("isAuthenticated()")} — 인증된 사용자면 모두 호출 가능
     * (자기 권한 조회는 보안 정보 노출이 아님). 미인증은 401.
     *
     * <p><b>노드 존재 검증</b>: nodeId 지정 시 folder/file 모두에서 활성 row를 lookup. 둘 다 부재 시
     * {@link ResourceNotFoundException} → 404. {@code resourceType}은 발견된 type으로 결정 (먼저 folder
     * 조회 — 양 테이블 UUID 충돌은 V5 별도 시퀀스라 운용상 불가능).
     */
    @GetMapping("/me/effective-permissions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, List<Permission>>> myEffectivePermissions(
        @RequestParam(value = "nodeId", required = false) UUID nodeId,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        String resourceType = null;
        if (nodeId != null) {
            boolean folderExists = folderRepository.findByIdAndDeletedAtIsNull(nodeId).isPresent();
            boolean fileExists = !folderExists
                && fileRepository.findByIdAndDeletedAtIsNull(nodeId).isPresent();
            if (!folderExists && !fileExists) {
                throw new ResourceNotFoundException("node not found: " + nodeId);
            }
            resourceType = folderExists ? "folder" : "file";
        }

        Set<Permission> set = evaluator.resolveAll(principal, resourceType, nodeId);
        List<Permission> sorted = set.stream().sorted().toList();
        return ResponseEntity.ok(Map.of("permissions", sorted));
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
     * <p>A4.6에서 folder 분기를 {@link FolderRepository}로 교체 — 이전에는 entity layer 부재로
     * {@code JdbcTemplate}로 직접 SELECT했으나 PR #9 (A4.5)이 entity/repository를 도입하여 file과
     * 동일한 JPA 경로로 통일.
     */
    private void ensureResourceExists(String resourceType, UUID id) {
        switch (resourceType) {
            case "file" -> fileRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("file not found: " + id));
            case "folder" -> folderRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("folder not found: " + id));
            default -> throw new IllegalArgumentException("unsupported resourceType: " + resourceType);
        }
    }
}
