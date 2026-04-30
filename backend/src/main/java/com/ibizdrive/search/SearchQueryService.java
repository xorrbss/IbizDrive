package com.ibizdrive.search;

import com.ibizdrive.common.normalize.NormalizeUtil;
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
 * A9.2 — {@code GET /api/search} 처리 (docs/02 §7.8, ADR #33).
 *
 * <p><b>책임</b>:
 * <ul>
 *   <li>q normalize ({@link NormalizeUtil#normalizeForSearch}) + minLen 2 검증</li>
 *   <li>type 분기 → file/folder repository LIKE 호출 (limit+1 over-fetch)</li>
 *   <li>type=null 케이스 in-memory merge sort {@code (updatedAt DESC, type DESC, id DESC)}</li>
 *   <li>per-row {@link Permission#READ} 후처리 필터 — actor의 ROLE 또는 resource grant 평가</li>
 *   <li>nextCursor 발급 + totalEstimate (첫 페이지 only)</li>
 * </ul>
 *
 * <p><b>cursor 적용 방식</b>: type=all에서 페이지 N+1을 가져올 때 동일 cursor tuple을 양 source
 * 쿼리에 전달 — 각 테이블에서 (updatedAt, id) tuple < cursor tuple인 row만 fetch한 뒤 merge.
 * cursor의 {@code type} 필드는 정렬 tiebreaker (동일 ms+id 충돌은 사실상 0).
 *
 * <p><b>LIKE escape</b>: 정규화된 q에서 wildcard 의미 문자({@code \ % _})를 backslash escape — 호출자
 * 입력이 SQL injection 경로가 아니므로 단순 의미 escape만 처리. ESCAPE 절은 native query에 박제.
 *
 * <p><b>권한 후처리 정책 (ADR #33)</b>: TrashQueryService와 동일 패턴. ADMIN/AUDITOR는 ROLE 단계에서
 * READ 보유 → short-circuit. 일반 사용자는 PermissionResolver로 resource-level grant 평가.
 * 후처리로 page size가 흔들 수 있으나 acceptance는 "row 수 ≤ limit".
 */
@Service
public class SearchQueryService {

    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_LIMIT = 50;
    private static final int MIN_QUERY_LENGTH = 2;

    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final PermissionService permissionService;
    private final PermissionResolver permissionResolver;

    public SearchQueryService(FileRepository fileRepository,
                              FolderRepository folderRepository,
                              PermissionService permissionService,
                              PermissionResolver permissionResolver) {
        this.fileRepository = fileRepository;
        this.folderRepository = folderRepository;
        this.permissionService = permissionService;
        this.permissionResolver = permissionResolver;
    }

    /**
     * @param actorId    인증된 사용자 — READ 후처리 평가 base
     * @param role       actor의 ROLE — ADMIN/AUDITOR short-circuit에 사용
     * @param rawQuery   클라이언트 raw 입력 — 서버에서 정규화 (idempotent하나 안전을 위해 재실행)
     * @param type       null = file + folder 양쪽
     * @param cursorWire opaque cursor (null = 첫 페이지)
     * @param limitOpt   null이면 {@link #DEFAULT_LIMIT}, MAX_LIMIT 초과 시 cap
     */
    @Transactional(readOnly = true)
    public SearchPage search(UUID actorId, Role role, String rawQuery, String type,
                             String cursorWire, Integer limitOpt) {
        String normalized = NormalizeUtil.normalizeForSearch(rawQuery == null ? "" : rawQuery);
        if (normalized.length() < MIN_QUERY_LENGTH) {
            throw new IllegalArgumentException("INVALID_SEARCH_QUERY");
        }
        validateType(type);

        SearchCursor cursor = SearchCursor.decode(cursorWire);
        Instant cursorUpdatedAt = cursor != null ? Instant.ofEpochMilli(cursor.updatedAtEpochMs()) : null;
        UUID cursorId = cursor != null ? cursor.id() : null;
        int limit = clampLimit(limitOpt);
        int fetchSize = limit + 1;

        String pattern = "%" + escapeLike(normalized) + "%";

        boolean wantFile = type == null || "all".equals(type) || "file".equals(type);
        boolean wantFolder = type == null || "all".equals(type) || "folder".equals(type);

        List<SearchResultDto> merged = new ArrayList<>(fetchSize * 2);
        if (wantFile) {
            for (FileItem f : fileRepository.searchByNormalizedName(pattern, cursorUpdatedAt, cursorId, fetchSize)) {
                merged.add(SearchResultDto.fromFile(f));
            }
        }
        if (wantFolder) {
            for (Folder fd : folderRepository.searchByNormalizedName(pattern, cursorUpdatedAt, cursorId, fetchSize)) {
                merged.add(SearchResultDto.fromFolder(fd));
            }
        }

        // 정렬: updatedAt DESC, type DESC ("folder" > "file"), id DESC — repo 정렬 키와 align
        merged.sort(
            Comparator.comparing(SearchResultDto::updatedAt, Comparator.reverseOrder())
                .thenComparing(SearchResultDto::type, Comparator.reverseOrder())
                .thenComparing(SearchResultDto::id, Comparator.reverseOrder())
        );

        // READ 후처리 필터
        List<SearchResultDto> visible = new ArrayList<>(merged.size());
        for (SearchResultDto item : merged) {
            if (canRead(actorId, role, item.id(), item.type())) {
                visible.add(item);
            }
        }

        boolean hasMore = visible.size() > limit;
        List<SearchResultDto> page = hasMore ? visible.subList(0, limit) : visible;
        String nextCursor = null;
        if (hasMore) {
            SearchResultDto last = page.get(page.size() - 1);
            nextCursor = SearchCursor.encode(
                last.updatedAt().toEpochMilli(),
                last.type(),
                last.id()
            );
        }

        long totalEstimate = (cursor == null)
            ? computeTotalEstimate(pattern, wantFile, wantFolder)
            : -1L;

        return new SearchPage(List.copyOf(page), nextCursor, totalEstimate);
    }

    private long computeTotalEstimate(String pattern, boolean wantFile, boolean wantFolder) {
        long total = 0;
        if (wantFile) {
            total += fileRepository.countByNormalizedName(pattern);
        }
        if (wantFolder) {
            total += folderRepository.countByNormalizedName(pattern);
        }
        return total;
    }

    private boolean canRead(UUID actorId, Role role, UUID resourceId, String type) {
        // 1) ROLE short-circuit — ADMIN/AUDITOR/MEMBER 등 ROLE에 READ가 있으면 통과
        if (permissionService.effectivePermissions(role).contains(Permission.READ)) {
            return true;
        }
        // 2) Resource-level grant
        return permissionResolver.isGranted(actorId, type, resourceId, Permission.READ);
    }

    private static void validateType(String type) {
        if (type == null) {
            return;
        }
        if (!"file".equals(type) && !"folder".equals(type) && !"all".equals(type)) {
            throw new IllegalArgumentException("INVALID_SEARCH_QUERY");
        }
    }

    /**
     * SQL LIKE wildcard escape — backslash, percent, underscore 의미 제거. ESCAPE 절은 repo native
     * query에 backslash로 박제되어 있어야 한다 (FileRepository/FolderRepository 참조).
     */
    static String escapeLike(String input) {
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

    private static int clampLimit(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }
}
