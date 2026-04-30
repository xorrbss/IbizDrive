package com.ibizdrive.search;

import com.ibizdrive.user.IbizDriveUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 검색 REST endpoint — A9 (docs/02 §7.8, ADR #33).
 *
 * <ul>
 *   <li><b>GET</b> {@code /api/search?q=&type=&cursor=&limit=} — list (A9.3). 200 {@link SearchPage}</li>
 * </ul>
 *
 * <p>인증된 모든 사용자에 대해 열린 endpoint — 결과는 service 단의 READ 권한 후처리로 필터된다
 * (보안 검증은 백엔드, CLAUDE.md §3 원칙 10). q minLength/type invalid/cursor invalid는
 * {@link IllegalArgumentException}으로 escalate되어 GlobalExceptionHandler에서 400으로 매핑.
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchQueryService searchQueryService;

    public SearchController(SearchQueryService searchQueryService) {
        this.searchQueryService = searchQueryService;
    }

    /**
     * 검색 list. {@code q}는 required. {@code type} ∈ {file,folder,all}, default=all (null 동등).
     * {@code cursor}는 직전 응답 {@code nextCursor} echo back. {@code limit} default 50, hard cap 100.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SearchPage> search(
        @RequestParam("q") String q,
        @RequestParam(value = "type", required = false) String type,
        @RequestParam(value = "cursor", required = false) String cursor,
        @RequestParam(value = "limit", required = false) Integer limit,
        @AuthenticationPrincipal IbizDriveUserDetails principal
    ) {
        SearchPage page = searchQueryService.search(
            principal.getUser().getId(),
            principal.getUser().getRole(),
            q,
            normalizeType(type),
            cursor,
            limit
        );
        return ResponseEntity.ok(page);
    }

    /**
     * type 파라미터 normalize — blank/null은 service에 null로 전달 (default=all). 실제 invalid 값
     * 검증은 service에서 일관되게 처리 (TrashItemType 패턴과 달리 wire 값을 enum으로 끌어올리지 않음 —
     * service 입력이 String이라 KISS).
     */
    private static String normalizeType(String wire) {
        if (wire == null || wire.isBlank()) {
            return null;
        }
        return wire;
    }
}
