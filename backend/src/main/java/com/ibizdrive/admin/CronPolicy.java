package com.ibizdrive.admin;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Cron 운영 정책 — admin-cron-policy-toggle 트랙 (Wave 2 closure 후속).
 *
 * <p>5 row 정적 테이블 (V11 시드 4 + V21 ADR #47 Phase 3d 추가 1). PK는 cron 식별자
 * ({@code purge.expired} / {@code share.expire} / {@code permission.expire} /
 * {@code storage.orphan.cleanup} / {@code admin.approval.expire}) — application.yml의 식별자와 동일.
 *
 * <p>{@code enabled}는 매 cron tick 진입 시 lookup된다 (in-tick guard). 변경은
 * {@link AdminSystemService#toggleCron}만 거치며 audit_log {@code admin.cron.toggled}로 추적.
 */
@Entity
@Table(name = "cron_policy")
public class CronPolicy {

    @Id
    @Column(name = "key", length = 64, nullable = false)
    private String key;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected CronPolicy() {}

    public CronPolicy(String key, boolean enabled, Instant updatedAt, UUID updatedBy) {
        this.key = key;
        this.enabled = enabled;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    public String getKey() { return key; }
    public boolean isEnabled() { return enabled; }
    public Instant getUpdatedAt() { return updatedAt; }
    public UUID getUpdatedBy() { return updatedBy; }

    public void update(boolean enabled, UUID actorId) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }
}
