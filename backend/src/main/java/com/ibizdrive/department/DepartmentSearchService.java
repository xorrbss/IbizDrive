package com.ibizdrive.department;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * A16 — {@code GET /api/departments/search} 처리 (docs/02 §7.x, ADR #36).
 *
 * <p>{@link com.ibizdrive.user.UserSearchService} 1:1 답습:
 * <ul>
 *   <li>q normalize ({@code trim().toLowerCase()}) + minLen 2 검증</li>
 *   <li>LIKE wildcard escape ({@code %} {@code _} {@code \})</li>
 *   <li>limit clamp (default 20, cap 50)</li>
 *   <li>{@link DepartmentRepository#searchActive} 호출 + {@link DepartmentSummaryDto} map</li>
 * </ul>
 *
 * <p><b>에러 코드</b>: q invalid → {@code IllegalArgumentException("INVALID_SEARCH_QUERY")} —
 * A14의 코드 재사용. GlobalExceptionHandler가 400으로 매핑.
 */
@Service
public class DepartmentSearchService {

    private static final int MIN_QUERY_LENGTH = 2;
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final DepartmentRepository departmentRepository;

    public DepartmentSearchService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @Transactional(readOnly = true)
    public List<DepartmentSummaryDto> search(String rawQuery, Integer limitOpt) {
        String normalized = normalize(rawQuery);
        if (normalized.length() < MIN_QUERY_LENGTH) {
            throw new IllegalArgumentException("INVALID_SEARCH_QUERY");
        }
        int limit = clampLimit(limitOpt);
        String pattern = "%" + escapeLike(normalized) + "%";

        return departmentRepository.searchActive(pattern, limit).stream()
            .map(DepartmentSummaryDto::fromEntity)
            .toList();
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase();
    }

    /**
     * SQL LIKE wildcard escape — backslash, percent, underscore 의미 제거. ESCAPE 절은
     * {@link DepartmentRepository#searchActive}에 backslash로 박제. A14 패턴 일관.
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
