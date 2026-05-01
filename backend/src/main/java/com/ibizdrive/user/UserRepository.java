package com.ibizdrive.user;

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
}
