package com.ibizdrive.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
