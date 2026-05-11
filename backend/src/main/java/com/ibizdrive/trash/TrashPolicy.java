package com.ibizdrive.trash;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 휴지통 보존 정책 single-row entity (V17 — trash-retention-mutation).
 *
 * <p>운영자가 무중단 변경 가능한 보존 일수를 보유한다. {@link TrashPolicyService}만이 read/update
 * 진입점 — JPA repository 직접 접근은 service에서만.
 *
 * <p>제약:
 * <ul>
 *   <li>{@code id = 1} (DB CHECK) — 다중 row 차단. {@code @Id} 타입은 {@code Short}.</li>
 *   <li>{@code retention_days BETWEEN 7 AND 90} (DB CHECK) — docs/04 §8.1 spec과 동기.</li>
 *   <li>{@code updated_by ON DELETE SET NULL} — 운영자 hard-delete 시 정책 row 보존.</li>
 * </ul>
 *
 * <p>이력은 audit_log({@code RETENTION_POLICY_CHANGED})가 보존. 별도 trash_policy_history
 * 테이블 미도입 (YAGNI).
 */
@Entity
@Table(name = "trash_policy")
public class TrashPolicy {

    public static final short SINGLETON_ID = 1;

    @Id
    @Column(name = "id")
    private Short id;

    @Column(name = "retention_days", nullable = false)
    private int retentionDays;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected TrashPolicy() {
        // JPA
    }

    public TrashPolicy(short id, int retentionDays, Instant updatedAt, UUID updatedBy) {
        this.id = id;
        this.retentionDays = retentionDays;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    public Short getId() {
        return id;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }
}
