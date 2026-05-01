package com.ibizdrive.department;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * 부서 조회 repository (A16, ADR #36, docs/02 §7.x).
 *
 * <p>{@link com.ibizdrive.user.UserRepository#searchActive} 1:1 패턴 — share subject picker
 * 의 dept lookup 용도. q는 호출자가 사전 trim/lowercase/LIKE-escape + wildcard wrap 완료 (예: {@code "%dev%"}).
 */
public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    /**
     * A16 — 부서 검색 (share subject picker 용, docs/02 §7.x, ADR #36).
     *
     * <p><b>매칭</b>: {@code LOWER(name) LIKE :pattern ESCAPE '\\'} — display name 단일 칼럼.
     * {@code WHERE deleted_at IS NULL}로 휴지통 제외. 정렬 {@code name ASC, id ASC} (deterministic).
     *
     * <p><b>limit</b>은 {@link Pageable}로 전달 — JPQL은 native LIMIT 미지원. 호출자는
     * {@code PageRequest.of(0, limit)} 또는 {@code Pageable.ofSize(limit)} 사용.
     */
    @Query("""
        SELECT d FROM Department d
        WHERE d.deletedAt IS NULL
          AND LOWER(d.name) LIKE :pattern ESCAPE '\\'
        ORDER BY d.name ASC, d.id ASC
        """)
    List<Department> searchActive(@Param("pattern") String pattern, Pageable pageable);

    /**
     * 편의 오버로드 — service에서 limit 정수만 갖는 경우 자동 {@link Pageable} 변환.
     */
    default List<Department> searchActive(String pattern, int limit) {
        return searchActive(pattern, Pageable.ofSize(limit));
    }
}
