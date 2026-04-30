package com.ibizdrive.trash;

import com.ibizdrive.file.FileItem;
import com.ibizdrive.file.FileRepository;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.permission.Permission;
import com.ibizdrive.permission.PermissionResolver;
import com.ibizdrive.permission.PermissionService;
import com.ibizdrive.user.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * A8.1 — {@code GET /api/trash} list 트랙 (docs/02 §7.11, ADR #32).
 *
 * <p><b>책임</b>:
 * <ul>
 *   <li>cursor 파싱 + per-source page query (file / folder / both)</li>
 *   <li>type=null 케이스 in-memory merge sort</li>
 *   <li>per-row {@link Permission#DELETE} 후처리 필터 — actor의 ROLE 또는 resource grant로 평가</li>
 *   <li>nextCursor 계산</li>
 * </ul>
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

    public TrashQueryService(FileRepository fileRepository,
                             FolderRepository folderRepository,
                             PermissionService permissionService,
                             PermissionResolver permissionResolver) {
        this.fileRepository = fileRepository;
        this.folderRepository = folderRepository;
        this.permissionService = permissionService;
        this.permissionResolver = permissionResolver;
    }

    /**
     * @param actorId      현재 인증된 사용자
     * @param role         actor의 ROLE — ADMIN/AUDITOR ROLE 경로 평가에 사용
     * @param cursorWire   opaque base64 cursor (null = 첫 페이지)
     * @param type         null = file + folder 양쪽
     * @param limitOpt     null이면 {@link #DEFAULT_LIMIT}, 100 초과 시 100으로 cap
     */
    @Transactional(readOnly = true)
    public TrashPage list(UUID actorId, Role role, String cursorWire, TrashItemType type, Integer limitOpt) {
        TrashCursor cursor = TrashCursor.decode(cursorWire);
        Instant cursorAt = cursor != null ? cursor.deletedAt() : null;
        UUID cursorId = cursor != null ? cursor.id() : null;
        int limit = clampLimit(limitOpt);

        // page query — over-fetch 1 to detect hasMore
        int fetchSize = limit + 1;

        List<TrashItemDto> merged = new ArrayList<>(fetchSize * 2);
        if (type == null || type == TrashItemType.FILE) {
            for (FileItem f : fileRepository.findTrashedPage(cursorAt, cursorId, fetchSize)) {
                merged.add(new TrashItemDto(
                    f.getId(), f.getName(), TrashItemType.FILE,
                    f.getDeletedAt(), f.getPurgeAfter(), f.getOriginalFolderId()
                ));
            }
        }
        if (type == null || type == TrashItemType.FOLDER) {
            for (Folder fd : folderRepository.findTrashedPage(cursorAt, cursorId, fetchSize)) {
                merged.add(new TrashItemDto(
                    fd.getId(), fd.getName(), TrashItemType.FOLDER,
                    fd.getDeletedAt(), fd.getPurgeAfter(), fd.getOriginalParentId()
                ));
            }
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

    private boolean canDelete(UUID actorId, Role role, UUID resourceId, TrashItemType type) {
        // 1) ROLE 경로 — ADMIN은 모든 권한, MEMBER는 DELETE 미보유 (preset에서만)
        if (permissionService.effectivePermissions(role).contains(Permission.DELETE)) {
            return true;
        }
        // 2) Resource-level grant — file/folder에 직접 부여된 권한
        return permissionResolver.isGranted(actorId, type.wire(), resourceId, Permission.DELETE);
    }

    private static int clampLimit(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }
}
