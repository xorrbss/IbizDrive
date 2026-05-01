package com.ibizdrive.department;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 부서 엔티티 — Flyway V7 schema에 mapping (A16, ADR #36, docs/02 §2.x).
 *
 * <p>DB schema가 진실의 출처 (CLAUDE.md §3 원칙 6). {@code spring.jpa.hibernate.ddl-auto: validate}로
 * 컬럼 불일치 시 부팅 실패. 단, application 미사용 컬럼은 entity에서 생략 가능 (validate는 entity의 컬럼이
 * DB에 존재하는지만 검증, 역방향은 무시).
 *
 * <p><b>의도적 생략 (KISS, ADR #36)</b>:
 * <ul>
 *   <li>{@code parent_id} — 조직도 트리는 v1.x 미사용 (flat list 가정).</li>
 *   <li>{@code path} (LTREE) — 트리 쿼리 미사용. schema는 v1.x re-migration 회피용으로 보유.</li>
 * </ul>
 *
 * <p>application은 직속 dept matching만 처리 — A16의 share resolution은 user.department_id 직접 비교
 * (PermissionRepository.findEffective JPQL JOIN).
 */
@Entity
@Table(name = "departments")
public class Department {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    protected Department() {
        // JPA
    }

    public Department(UUID id, String name, OffsetDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
