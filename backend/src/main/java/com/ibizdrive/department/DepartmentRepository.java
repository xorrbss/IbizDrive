package com.ibizdrive.department;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
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

    /**
     * admin-department-crud (Wave 2 T4) — admin 부서 목록 조회.
     *
     * <p>{@link #searchActive}와 차이: <b>비활성 부서를 포함</b> ({@code deleted_at IS NOT NULL}도 보임).
     * admin은 비활성 부서를 재활성화/이름 변경할 수 있어야 하므로 share-picker용 query를 재사용하지 않는다.
     *
     * <p><b>q semantics</b>: null 또는 blank → 전체 반환. 값 있으면 {@code LOWER(name) LIKE :pattern ESCAPE '\\'}로 매칭.
     * 호출자(서비스)가 trim/lowercase + LIKE-escape + wildcard wrap을 완료해 넘긴다 (A14 패턴).
     *
     * <p>정렬: {@code deletedAt} ASC NULLS FIRST → {@code name} ASC, {@code id} ASC.
     * 활성 부서가 먼저 보이고, 같은 활성 상태 안에서는 이름순. {@code deletedAt} ASC는 PostgreSQL 기본
     * NULLS FIRST와 일치 (KISS — 명시 NULLS FIRST 추가는 H2 호환에 비용 발생, MVP는 PostgreSQL 단일 타깃).
     */
    @Query("""
        SELECT d FROM Department d
        WHERE (:pattern IS NULL OR LOWER(d.name) LIKE :pattern ESCAPE '\\')
        ORDER BY
            CASE WHEN d.deletedAt IS NULL THEN 0 ELSE 1 END ASC,
            d.name ASC,
            d.id ASC
        """)
    Page<Department> findAllForAdminPageable(@Param("pattern") String pattern, Pageable pageable);

    /**
     * admin-department-crud (Wave 2 T4) — 활성 부서 이름으로 단건 조회 (충돌 사전 검사용).
     *
     * <p>V9 partial unique({@code idx_departments_name_active})와 정확히 동일 조건 — service의
     * 사전 충돌 검사가 race window를 좁힌다. 실제 진실의 출처는 DB INSERT의 unique 위반.
     */
    @Query("""
        SELECT d FROM Department d
        WHERE d.deletedAt IS NULL AND d.name = :name
        """)
    Optional<Department> findActiveByName(@Param("name") String name);
}
