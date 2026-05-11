package com.ibizdrive.trash;

import com.ibizdrive.file.FileItem;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.folder.ScopeType;
import com.ibizdrive.permission.Permission;
import com.ibizdrive.permission.PermissionResolver;
import com.ibizdrive.permission.PermissionService;
import com.ibizdrive.permission.WorkspaceMembershipResolver;
import com.ibizdrive.user.Role;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A8.1 + Plan E T2 — {@code GET /api/trash} list 트랙 (docs/02 §7.11, ADR #32, spec §3.1/§5.1).
 *
 * <p><b>책임</b>:
 * <ul>
 *   <li>workspace 멤버십 fast-fail 가드 (Plan E T2) — non-member ⇒
 *       {@link AccessDeniedException} 401/403 (GlobalExceptionHandler 매핑).
 *       시스템 ROLE.ADMIN은 bypass (spec §3 권한 평가 1순위).</li>
 *   <li>cursor 파싱 + per-source scope-filtered page query (file / folder / both)</li>
 *   <li>type=null 케이스 in-memory merge sort</li>
 *   <li>per-row {@link Permission#DELETE} 후처리 필터 — actor의 ROLE 또는 resource grant로 평가
 *       (Plan A 이후 {@link PermissionResolver}가 내부에서 workspace membership step을 자동 적용,
 *       TEAM MEMBER+ → DELETE 묵시 통과)</li>
 *   <li>nextCursor 계산</li>
 * </ul>
 *
 * <p><b>scope_type wire 계약</b>: T1 repo {@code findTrashedPageByScope}는 {@code scope_type}을
 * lower-case 문자열({@code "department"}/{@code "team"})로 비교한다. {@link ScopeType} enum을
 * 직접 전달하면 {@code name()} 대문자가 들어가 silent 0-row 매칭이 발생하므로 반드시
 * {@link ScopeType#dbValue()}를 호출해 변환한다.
 *
 * <p><b>cursor null invariant</b>: 본 service는 {@code cursorAt}/{@code cursorId}를 항상 함께
 * NULL이거나 함께 NOT NULL이도록 유지한다. 한쪽만 NULL인 상태가 repo로 전달되면 native query의
 * cursor 술부가 NULL OR 가지로 short-circuit되어 의도치 않게 첫 페이지가 반환될 수 있다 — 본
 * service는 {@link TrashCursor#decode}가 record 1쌍을 반환하도록 보장하므로 단일 출처에서 invariant
 * 유지가 자연스럽다.
 *
 * <p><b>권한 평가 정책 (ADR #32)</b>: DB-level join은 MVP overkill — trash 데이터셋이 소규모(30일
 * grace × 평균 트래시율, 수만건 미만 가정)이므로 in-memory 후처리. 큰 dataset 시 별도 ADR.
 * 후처리 필터는 page size를 흔들 수 있으나 acceptance 기준은 "row 수 < 페이지 한도".
 *
 * <p><b>cursor edge case</b>: 동일 {@code deleted_at}을 가진 file/folder가 페이지 경계를 가로지를 때
 * 단일 cursor 모델은 매우 드문 환경에서 row 누락/중복을 만들 수 있다 (file ID와 folder ID는 별도
 * 테이블이라 충돌은 없지만 순서가 source별로 갈림). MVP는 같은 ms 단위 timestamp 충돌 가정 부재 —
 * 발생 시 frontend dedup (id+type 키)으로 흡수. 큰 부담이 되면 source별 cursor 분리 ADR.
 */
@Service
public class TrashQueryService {

    /** docs/02 §7.11 묵시 약속 — page size가 너무 크면 단일 응답이 비대해지므로 hard cap. */
    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_LIMIT = 50;

    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final PermissionService permissionService;
    private final PermissionResolver permissionResolver;
    private final WorkspaceMembershipResolver membershipResolver;

    public TrashQueryService(FileRepository fileRepository,
                             FolderRepository folderRepository,
                             PermissionService permissionService,
                             PermissionResolver permissionResolver,
                             WorkspaceMembershipResolver membershipResolver) {
        this.fileRepository = fileRepository;
        this.folderRepository = folderRepository;
        this.permissionService = permissionService;
        this.permissionResolver = permissionResolver;
        this.membershipResolver = membershipResolver;
    }

    /**
     * @param actorId      현재 인증된 사용자
     * @param role         actor의 ROLE — ADMIN이면 workspace 멤버십 bypass + 모든 row DELETE 통과
     * @param scopeType    {@link ScopeType#DEPARTMENT} 또는 {@link ScopeType#TEAM} (필수)
     * @param scopeId      workspace id (필수)
     * @param cursorWire   opaque base64 cursor (null = 첫 페이지)
     * @param type         null = file + folder 양쪽
     * @param limitOpt     null이면 {@link #DEFAULT_LIMIT}, 100 초과 시 100으로 cap
     * @throws AccessDeniedException ADMIN이 아닌 사용자가 비멤버 workspace를 요청한 경우
     */
    @Transactional(readOnly = true)
    public TrashPage list(UUID actorId, Role role,
                          ScopeType scopeType, UUID scopeId,
                          String cursorWire, TrashItemType type, Integer limitOpt) {
        // (1) workspace 멤버십 fast-fail — ADMIN bypass + non-member ⇒ AccessDeniedException.
        if (!isWorkspaceMember(actorId, role, scopeType, scopeId)) {
            throw new AccessDeniedException(
                "User is not a member of " + scopeType.dbValue() + ":" + scopeId
            );
        }

        TrashCursor cursor = TrashCursor.decode(cursorWire);
        Instant cursorAt = cursor != null ? cursor.deletedAt() : null;
        UUID cursorId = cursor != null ? cursor.id() : null;
        int limit = clampLimit(limitOpt);

        // page query — over-fetch 1 to detect hasMore
        int fetchSize = limit + 1;
        // T1 repo wire 계약 — ScopeType.name() 대문자가 들어가지 않도록 dbValue() 사용 (Javadoc 참조).
        String scopeTypeRaw = scopeType.dbValue();

        List<FileItem> files = (type == null || type == TrashItemType.FILE)
            ? fileRepository.findTrashedPageByScope(scopeTypeRaw, scopeId, cursorAt, cursorId, fetchSize)
            : List.of();
        List<Folder> folders = (type == null || type == TrashItemType.FOLDER)
            ? folderRepository.findTrashedPageByScope(scopeTypeRaw, scopeId, cursorAt, cursorId, fetchSize)
            : List.of();

        // 원위치 path batch 조회 — file/folder 양쪽의 NOT NULL parent id 모두 합쳐 1회 CTE로 해소.
        // 빈 set은 short-circuit (Postgres IN ()는 문법 오류). page 한도 100 row, parent 평균 1개
        // 가정이라 batch 비용 작음. admin trash와 동일 source 사용.
        Set<UUID> parentIds = new HashSet<>();
        for (FileItem f : files) {
            if (f.getOriginalFolderId() != null) parentIds.add(f.getOriginalFolderId());
        }
        for (Folder fd : folders) {
            if (fd.getOriginalParentId() != null) parentIds.add(fd.getOriginalParentId());
        }
        Map<UUID, String> parentPathById = parentPathsFor(parentIds);

        List<TrashItemDto> merged = new ArrayList<>(fetchSize * 2);
        for (FileItem f : files) {
            UUID pid = f.getOriginalFolderId();
            merged.add(new TrashItemDto(
                f.getId(), f.getName(), TrashItemType.FILE,
                f.getDeletedAt(), f.getPurgeAfter(), pid,
                pid != null ? parentPathById.get(pid) : null
            ));
        }
        for (Folder fd : folders) {
            UUID pid = fd.getOriginalParentId();
            merged.add(new TrashItemDto(
                fd.getId(), fd.getName(), TrashItemType.FOLDER,
                fd.getDeletedAt(), fd.getPurgeAfter(), pid,
                pid != null ? parentPathById.get(pid) : null
            ));
        }

        // 정렬: deletedAt DESC, id DESC — repo 정렬과 동일 키
        merged.sort(
            Comparator.comparing(TrashItemDto::deletedAt, Comparator.reverseOrder())
                .thenComparing(TrashItemDto::id, Comparator.reverseOrder())
        );

        // 권한 후처리 필터 — DELETE 보유 항목만 노출
        List<TrashItemDto> visible = new ArrayList<>(merged.size());
        for (TrashItemDto item : merged) {
            if (canDelete(actorId, role, item.id(), item.type())) {
                visible.add(item);
            }
        }

        // limit 적용 + nextCursor 계산
        boolean hasMore = visible.size() > limit;
        List<TrashItemDto> page = hasMore ? visible.subList(0, limit) : visible;
        String nextCursor = null;
        if (hasMore) {
            TrashItemDto last = page.get(page.size() - 1);
            nextCursor = TrashCursor.encode(last.deletedAt(), last.id());
        }

        return new TrashPage(List.copyOf(page), nextCursor);
    }

    /**
     * 시스템 ROLE.ADMIN은 모든 workspace를 bypass 가능 (spec §3 권한 평가 1순위 — 시스템 ROLE 검사).
     * 그 외 사용자는 {@link WorkspaceMembershipResolver}가 비-empty 권한 집합을 반환하면 멤버로 간주.
     */
    private boolean isWorkspaceMember(UUID actorId, Role role, ScopeType scopeType, UUID scopeId) {
        if (role == Role.ADMIN) {
            return true;
        }
        return !membershipResolver.resolve(actorId, scopeType, scopeId).isEmpty();
    }

    private boolean canDelete(UUID actorId, Role role, UUID resourceId, TrashItemType type) {
        // 1) ROLE 경로 — ADMIN은 모든 권한, MEMBER는 DELETE 미보유 (preset에서만)
        if (permissionService.effectivePermissions(role).contains(Permission.DELETE)) {
            return true;
        }
        // 2) Resource-level grant — file/folder에 직접 부여된 권한 (Plan A 이후 PermissionResolver
        //    내부에 workspace membership step이 추가되어 TEAM MEMBER+ → DELETE 묵시 통과 자동 반영).
        return permissionResolver.isGranted(actorId, type.wire(), resourceId, Permission.DELETE);
    }

    private static int clampLimit(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }

    /**
     * 원위치 부모 폴더의 절대 경로 batch lookup — admin trash와 동일 source
     * ({@link FolderRepository#findAncestorPaths}). 빈 입력은 short-circuit. 결과 누락 시
     * map에 없으며 호출자는 NULL로 폴백 — frontend가 "원위치 미상" 표시로 처리.
     */
    private Map<UUID, String> parentPathsFor(Set<UUID> parentIds) {
        if (parentIds.isEmpty()) return Map.of();
        Map<UUID, String> paths = new HashMap<>(parentIds.size());
        for (Object[] row : folderRepository.findAncestorPaths(parentIds)) {
            paths.put((UUID) row[0], (String) row[1]);
        }
        return paths;
    }
}
