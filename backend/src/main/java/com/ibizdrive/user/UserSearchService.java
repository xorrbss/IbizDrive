package com.ibizdrive.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * A14 — {@code GET /api/users/search} 처리 (docs/02 §7.13, ADR #35).
 *
 * <p><b>책임</b>:
 * <ul>
 *   <li>q normalize ({@code trim().toLowerCase()}) + minLen 2 검증</li>
 *   <li>LIKE wildcard escape ({@code %} {@code _} {@code \})</li>
 *   <li>limit clamp (default 20, cap 50)</li>
 *   <li>{@link UserRepository#searchActive} 호출 + {@link UserSummaryDto} map</li>
 * </ul>
 *
 * <p><b>scope (ADR #35)</b>: subject_type='user' lookup만. department 엔티티 부재(V_ 마이그
 * 미존재) + Role enum 고정값 + everyone 리터럴 — 셋 모두 lookup 불요.
 *
 * <p><b>privacy 정책 (ADR #18 / #35)</b>: 사내 시스템 + 관리자 초대 only → 모든 인증 사용자가 user
 * list 접근 가능. 일반 회원 시스템이었다면 admin only로 제한.
 *
 * <p><b>에러 코드</b>: q invalid → {@code IllegalArgumentException("INVALID_SEARCH_QUERY")} —
 * A9의 코드 재사용. GlobalExceptionHandler가 400으로 매핑.
 */
@Service
public class UserSearchService {

    private static final int MIN_QUERY_LENGTH = 2;
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final UserRepository userRepository;

    public UserSearchService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<UserSummaryDto> search(String rawQuery, Integer limitOpt) {
        String normalized = normalize(rawQuery);
        if (normalized.length() < MIN_QUERY_LENGTH) {
            throw new IllegalArgumentException("INVALID_SEARCH_QUERY");
        }
        int limit = clampLimit(limitOpt);
        String pattern = "%" + escapeLike(normalized) + "%";

        return userRepository.searchActive(pattern, limit).stream()
            .map(UserSummaryDto::fromEntity)
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
     * {@link UserRepository#searchActive}에 backslash로 박제. A9 패턴 일관.
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
