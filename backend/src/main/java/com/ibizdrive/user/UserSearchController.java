package com.ibizdrive.user;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A14 — 사용자 검색 REST endpoint (docs/02 §7.13, ADR #35).
 *
 * <ul>
 *   <li><b>GET</b> {@code /api/users/search?q=&limit=} — list. 200 {@link UserSearchResponse}</li>
 * </ul>
 *
 * <p>인증된 모든 사용자에 대해 열린 endpoint — share subject picker 의 user lookup 용도.
 * privacy 정책은 ADR #35 (사내 시스템 + ADR #18 self-registration 금지로 user list 노출 허용).
 *
 * <p>q invalid는 {@link IllegalArgumentException("INVALID_SEARCH_QUERY")}로 escalate되어
 * GlobalExceptionHandler에서 400으로 매핑 (A9 일관).
 */
@RestController
@RequestMapping("/api/users/search")
public class UserSearchController {

    private final UserSearchService service;

    public UserSearchController(UserSearchService service) {
        this.service = service;
    }

    /**
     * 사용자 검색 list. {@code q}는 required (서비스 단에서 trim/lowercase + minLen 2 검증).
     * {@code limit} default 20, hard cap 50.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserSearchResponse> search(
        @RequestParam("q") String q,
        @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return ResponseEntity.ok(new UserSearchResponse(service.search(q, limit)));
    }
}
