package com.ibizdrive.admin;

import com.ibizdrive.department.Department;
import com.ibizdrive.department.DepartmentRepository;
import com.ibizdrive.file.FileItem;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.permission.PermissionRepository;
import com.ibizdrive.permission.PermissionRow;
import com.ibizdrive.user.User;
import com.ibizdrive.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Wave 2 T5 — admin 권한 매트릭스 서비스.
 *
 * <p>{@code GET /api/admin/permissions} service 진입점. 5종 filter 검증 → repository 조회 →
 * subject/resource/grantedBy name batch 해석 → DTO 변환 + isExpired derive.
 *
 * <p><b>filter 검증 정책</b>:
 * <ul>
 *   <li>{@code subjectType} ∈ {user, department, role, everyone} (혹은 null = 무시).</li>
 *   <li>{@code subjectId} 단독 입력 (subjectType 미지정) → 400. type-id mismatch 차단.</li>
 *   <li>{@code subjectType=everyone} + subjectId 비어있지 않음 → 400.</li>
 *   <li>{@code resourceType} ∈ {folder, file}.</li>
 *   <li>{@code preset} ∈ {read, upload, edit, admin}.</li>
 * </ul>
 *
 * <p>q 정규화: {@code trim().toLowerCase()} + LIKE escape (\\, %, _) + {@code %...%} wrap.
 * blank → null (전체).
 */
@Service
public class AdminPermissionService {

    private static final Set<String> ALLOWED_SUBJECT_TYPES = Set.of("user", "department", "role", "everyone");
    private static final Set<String> ALLOWED_RESOURCE_TYPES = Set.of("folder", "file");
    private static final Set<String> ALLOWED_PRESETS = Set.of("read", "upload", "edit", "admin");

    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final FolderRepository folderRepository;
    private final FileRepository fileRepository;

    public AdminPermissionService(PermissionRepository permissionRepository,
                                  UserRepository userRepository,
                                  DepartmentRepository departmentRepository,
                                  FolderRepository folderRepository,
                                  FileRepository fileRepository) {
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.folderRepository = folderRepository;
        this.fileRepository = fileRepository;
    }

    /**
     * admin 권한 매트릭스 조회. 본 메서드는 read-only — audit emit 없음.
     */
    @Transactional(readOnly = true)
    public Page<AdminPermissionRowResponse> list(Filters filters, Pageable pageable) {
        validate(filters);
        String qLike = toLikePattern(filters.q());

        Page<PermissionRow> page = permissionRepository.findAllForAdminPageable(
            blankToNull(filters.subjectType()),
            filters.subjectId(),
            blankToNull(filters.resourceType()),
            blankToNull(filters.preset()),
            qLike,
            pageable
        );

        if (page.isEmpty()) {
            return page.map(row -> mapRow(row, Map.of(), Map.of(), Map.of(), Map.of()));
        }

        // batch name resolution — N+1 방지.
        Set<UUID> userIds = new HashSet<>();
        Set<UUID> deptIds = new HashSet<>();
        Set<UUID> folderIds = new HashSet<>();
        Set<UUID> fileIds = new HashSet<>();
        Set<UUID> grantedByIds = new HashSet<>();
        for (PermissionRow row : page.getContent()) {
            if ("user".equals(row.getSubjectType()) && row.getSubjectId() != null) {
                userIds.add(row.getSubjectId());
            } else if ("department".equals(row.getSubjectType()) && row.getSubjectId() != null) {
                deptIds.add(row.getSubjectId());
            }
            if ("folder".equals(row.getResourceType())) {
                folderIds.add(row.getResourceId());
            } else if ("file".equals(row.getResourceType())) {
                fileIds.add(row.getResourceId());
            }
            grantedByIds.add(row.getGrantedBy());
        }

        Map<UUID, String> userNames = lookupUserNames(unionIds(userIds, grantedByIds));
        Map<UUID, String> deptNames = lookupDeptNames(deptIds);
        Map<UUID, String> folderNames = lookupFolderNames(folderIds);
        Map<UUID, String> fileNames = lookupFileNames(fileIds);

        return page.map(row -> mapRow(row, userNames, deptNames, folderNames, fileNames));
    }

    private static AdminPermissionRowResponse mapRow(PermissionRow row,
                                                     Map<UUID, String> userNames,
                                                     Map<UUID, String> deptNames,
                                                     Map<UUID, String> folderNames,
                                                     Map<UUID, String> fileNames) {
        String subjectName = resolveSubjectName(row, userNames, deptNames);
        String resourceName = "folder".equals(row.getResourceType())
            ? folderNames.get(row.getResourceId())
            : fileNames.get(row.getResourceId());
        String grantedByName = userNames.get(row.getGrantedBy());
        boolean isExpired = row.getExpiresAt() != null && !row.getExpiresAt().isAfter(Instant.now());

        return new AdminPermissionRowResponse(
            row.getId(),
            row.getSubjectType(),
            row.getSubjectId(),
            subjectName,
            row.getResourceType(),
            row.getResourceId(),
            resourceName,
            row.getPreset(),
            row.getGrantedBy(),
            grantedByName,
            row.getCreatedAt(),
            row.getExpiresAt(),
            isExpired
        );
    }

    private static String resolveSubjectName(PermissionRow row,
                                             Map<UUID, String> userNames,
                                             Map<UUID, String> deptNames) {
        return switch (row.getSubjectType()) {
            case "user" -> row.getSubjectId() != null ? userNames.get(row.getSubjectId()) : null;
            case "department" -> row.getSubjectId() != null ? deptNames.get(row.getSubjectId()) : null;
            case "everyone" -> "전사";
            case "role" -> row.getSubjectId() != null ? row.getSubjectId().toString() : null;
            default -> null;
        };
    }

    private Map<UUID, String> lookupUserNames(Set<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<UUID, String> out = new HashMap<>(ids.size());
        for (User u : userRepository.findAllById(ids)) {
            out.put(u.getId(), u.getDisplayName());
        }
        return out;
    }

    private Map<UUID, String> lookupDeptNames(Set<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<UUID, String> out = new HashMap<>(ids.size());
        for (Department d : departmentRepository.findAllById(ids)) {
            out.put(d.getId(), d.getName());
        }
        return out;
    }

    private Map<UUID, String> lookupFolderNames(Set<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<UUID, String> out = new HashMap<>(ids.size());
        for (Folder f : folderRepository.findAllById(ids)) {
            out.put(f.getId(), f.getName());
        }
        return out;
    }

    private Map<UUID, String> lookupFileNames(Set<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<UUID, String> out = new HashMap<>(ids.size());
        for (FileItem fi : fileRepository.findAllById(ids)) {
            out.put(fi.getId(), fi.getName());
        }
        return out;
    }

    private static Set<UUID> unionIds(Set<UUID> a, Set<UUID> b) {
        Set<UUID> out = new HashSet<>(a.size() + b.size());
        out.addAll(a);
        out.addAll(b);
        return out;
    }

    private static void validate(Filters f) {
        if (f.subjectType() != null && !ALLOWED_SUBJECT_TYPES.contains(f.subjectType())) {
            throw new IllegalArgumentException(
                "subjectType must be one of user|department|role|everyone");
        }
        if (f.resourceType() != null && !ALLOWED_RESOURCE_TYPES.contains(f.resourceType())) {
            throw new IllegalArgumentException("resourceType must be one of folder|file");
        }
        if (f.preset() != null && !ALLOWED_PRESETS.contains(f.preset())) {
            throw new IllegalArgumentException("preset must be one of read|upload|edit|admin");
        }
        if (f.subjectId() != null && f.subjectType() == null) {
            throw new IllegalArgumentException("subjectType is required when subjectId is provided");
        }
        if ("everyone".equals(f.subjectType()) && f.subjectId() != null) {
            throw new IllegalArgumentException("subjectId must be null when subjectType=everyone");
        }
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String toLikePattern(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) return null;
        return "%" + escapeLike(trimmed) + "%";
    }

    private static String escapeLike(String input) {
        StringBuilder sb = new StringBuilder(input.length() + 8);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\\' || c == '%' || c == '_') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Filter input record. controller가 query string 파싱 후 본 record로 service에 전달.
     */
    public record Filters(
        String subjectType,
        UUID subjectId,
        String resourceType,
        String preset,
        String q
    ) {
    }
}
