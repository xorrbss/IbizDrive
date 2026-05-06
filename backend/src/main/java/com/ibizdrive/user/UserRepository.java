package com.ibizdrive.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 사용자 조회 repository.
 *
 * <p>이메일 lookup은 (a) lowercase 정규화 후, (b) soft delete 제외 조건과 함께
 * 수행해야 한다 (docs/03 §2.7). DB 측 unique index는
 * {@code lower(email) WHERE deleted_at IS NULL}이므로 동일 조건으로 매칭한다.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * 활성(미삭제) 사용자를 lowercase 이메일로 조회.
     * 호출자는 입력 이메일을 미리 trim·lowercase 적용해야 한다 (docs/03 §2.7).
     */
    @Query("SELECT u FROM User u WHERE LOWER(u.email) = :email AND u.deletedAt IS NULL")
    Optional<User> findActiveByEmail(@Param("email") String emailLowercase);

    /**
     * A14 — 사용자 검색 (share subject picker 용, docs/02 §7.13, ADR #35).
     *
     * <p>{@code pattern}은 호출자가 사전 trim/lowercase/LIKE-escape + wildcard wrap 완료
     * (예: {@code "%alice%"}). ESCAPE 문자는 backslash. 정렬은 {@code displayName ASC, id ASC} —
     * deterministic. {@code WHERE deleted_at IS NULL AND is_active = TRUE}로 휴지통/잠금 제외.
     *
     * <p><b>limit</b>은 {@link Pageable}로 전달 — JPQL은 native LIMIT 미지원. 호출자는
     * {@code PageRequest.of(0, limit)} 또는 {@code Pageable.ofSize(limit)} 사용.
     *
     * <p><b>인덱스 부재 인정</b>: {@code LOWER(display_name)} / {@code LOWER(email)} 함수형 인덱스
     * 미존재 — MVP user count {@literal <} 1k 가정으로 row scan 허용 (ADR #35). 큰 데이터셋 시 별도 마이그.
     */
    @Query("""
        SELECT u FROM User u
        WHERE u.deletedAt IS NULL
          AND u.isActive = TRUE
          AND (LOWER(u.displayName) LIKE :pattern ESCAPE '\\'
               OR LOWER(u.email) LIKE :pattern ESCAPE '\\')
        ORDER BY u.displayName ASC, u.id ASC
        """)
    List<User> searchActive(@Param("pattern") String pattern, Pageable pageable);

    /**
     * 편의 오버로드 — service에서 limit 정수만 갖는 경우 자동 {@link Pageable} 변환.
     * 본 메서드는 default → {@link #searchActive(String, Pageable)} 호출.
     */
    default List<User> searchActive(String pattern, int limit) {
        return searchActive(pattern, Pageable.ofSize(limit));
    }

    /**
     * admin-user-mgmt — `/admin/users` 목록 조회용 페이징 쿼리.
     *
     * <p>soft-delete 제외 (`deleted_at IS NULL`). `is_active=false` (수동 비활성)는 포함 —
     * admin 화면에서 비활성 사용자도 목록에 표시되어 재활성/role 변경 대상이 되어야 한다.
     * 정렬은 `createdAt DESC, id ASC` — deterministic + 신규 가입자가 상단.
     *
     * <p><b>인덱스 부재 인정</b>: V1 인덱스는 `lower(email) WHERE deleted_at IS NULL` partial unique만
     * 존재. `created_at` 인덱스는 부재. MVP user count {@literal <} 1k 가정으로 row scan 허용.
     * 스케일 시 별도 마이그레이션 (`CREATE INDEX users_created_at_desc ON users(created_at DESC) WHERE deleted_at IS NULL`).
     */
    @Query("""
        SELECT u FROM User u
        WHERE u.deletedAt IS NULL
        ORDER BY u.createdAt DESC, u.id ASC
        """)
    Page<User> findAllActivePageable(Pageable pageable);

    /**
     * admin-user-search-update — `/admin/users?q=` 검색 (Wave 1 — T1).
     *
     * <p>{@link #findAllActivePageable}와 짝이지만 검색 패턴 추가. 패턴은 호출자가 사전
     * lowercase + LIKE escape + wildcard wrap 완료 (예: {@code "%ali\\_ce%"}). ESCAPE 문자는 backslash.
     * 빈 패턴(검색어 없음)은 호출자가 {@link #findAllActivePageable}로 분기 — 본 메서드는 항상
     * 패턴 매칭 수행. soft-delete 제외 + 비활성 포함 동일.
     *
     * <p>{@link #searchActive} (share picker 용)와 분리: 후자는 {@code is_active=TRUE} 필터로
     * "공유 대상 가능 사용자"만 노출하지만, admin 검색은 비활성도 포함해야 재활성 대상이 된다.
     */
    @Query("""
        SELECT u FROM User u
        WHERE u.deletedAt IS NULL
          AND (LOWER(u.displayName) LIKE :pattern ESCAPE '\\'
               OR LOWER(u.email) LIKE :pattern ESCAPE '\\')
        ORDER BY u.createdAt DESC, u.id ASC
        """)
    Page<User> findForAdminPageable(@Param("pattern") String pattern, Pageable pageable);
}
