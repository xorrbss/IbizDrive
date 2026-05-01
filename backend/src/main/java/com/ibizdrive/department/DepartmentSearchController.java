package com.ibizdrive.department;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A16 — 부서 검색 REST endpoint (docs/02 §7.x, ADR #36).
 *
 * <ul>
 *   <li><b>GET</b> {@code /api/departments/search?q=&limit=} — list. 200 {@link DepartmentSearchResponse}</li>
 * </ul>
 *
 * <p>인증된 모든 사용자에 대해 열린 endpoint — share subject picker 의 dept lookup 용도.
 * privacy 정책은 ADR #36 (조직도 정보는 사내 공개로 가정 — A14 user search와 동등 정책).
 *
 * <p>q invalid는 {@link IllegalArgumentException("INVALID_SEARCH_QUERY")}로 escalate되어
 * GlobalExceptionHandler에서 400으로 매핑 (A14 일관).
 */
@RestController
@RequestMapping("/api/departments/search")
public class DepartmentSearchController {

    private final DepartmentSearchService service;

    public DepartmentSearchController(DepartmentSearchService service) {
        this.service = service;
    }

    /**
     * 부서 검색 list. {@code q}는 required (서비스 단에서 trim/lowercase + minLen 2 검증).
     * {@code limit} default 20, hard cap 50.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DepartmentSearchResponse> search(
        @RequestParam("q") String q,
        @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return ResponseEntity.ok(new DepartmentSearchResponse(service.search(q, limit)));
    }
}
