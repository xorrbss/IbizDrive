package com.ibizdrive.auth.password;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 비밀번호 재설정 토큰 repository.
 *
 * <p>조회는 항상 {@code token_hash}로만 수행 — 평문 토큰을 DB로 흘려보내지 않는다.
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
}
