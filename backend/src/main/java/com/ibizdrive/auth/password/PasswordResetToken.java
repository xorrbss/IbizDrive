package com.ibizdrive.auth.password;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 비밀번호 재설정 토큰 (a1.5, V8 마이그레이션 매핑).
 *
 * <p>{@code token_hash} = sha256(평문 토큰)을 hex로 인코딩한 64자. 평문 토큰은 이메일에만 노출되며
 * DB에는 해시만 저장된다 — 토큰 노출 시에도 직접 reset 불가능 (rainbow table 우회 위협 모델은
 * 64자 랜덤 평문 → 무차별 공격 비현실적).
 *
 * <p>1회용: {@link #markUsed(OffsetDateTime)}로 used_at 마킹 후 재사용 시 invalid 처리.
 * TTL 30분: 발급 시각 + 30분이 {@link #expiresAt}.
 *
 * <p>userId는 {@link com.ibizdrive.user.User} 엔티티와 동일하게 UUID.
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected PasswordResetToken() {
        // JPA
    }

    public PasswordResetToken(UUID userId,
                              String tokenHash,
                              OffsetDateTime expiresAt,
                              OffsetDateTime createdAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getUsedAt() {
        return usedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired(OffsetDateTime now) {
        return !now.isBefore(expiresAt);
    }

    /**
     * 1회용 마킹. 호출자는 동일 트랜잭션 내에서 repository.save로 flush한다.
     */
    public void markUsed(OffsetDateTime at) {
        this.usedAt = at;
    }
}
