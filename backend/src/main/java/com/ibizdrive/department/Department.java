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

    /**
     * admin-department-crud (Wave 2 T4) — 활성 여부 도출.
     *
     * <p>V7 schema의 `departments` 테이블은 `is_active` 컬럼을 갖지 않으며 {@code deleted_at}만 존재한다.
     * 부서 도메인에서는 "비활성화" = "soft-delete"로 통합한다 (KISS, dept는 user처럼 인증/세션 lifecycle이
     * 없어 별도 boolean이 필요 없음). admin이 비활성화한 부서는 share-picker / admin list / dept resolution에서
     * 모두 동등하게 제외된다.
     */
    public boolean isActive() {
        return deletedAt == null;
    }

    /**
     * admin-department-crud (Wave 2 T4) — 부서 이름 변경.
     *
     * <p>호출자(서비스)는 충돌(`UNIQUE INDEX idx_departments_name_active` — V9) 검출과 audit emit
     * 책임을 진다. 도메인 메서드는 입력 정규화(trim) + 길이 검증(1~100자, V7 컬럼 length=100)만 담당.
     *
     * @throws IllegalArgumentException newName이 null/blank 또는 trim 후 1~100자 범위 밖
     */
    public void rename(String newName) {
        if (newName == null) {
            throw new IllegalArgumentException("name must not be null");
        }
        String trimmed = newName.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException("name must be at most 100 characters");
        }
        this.name = trimmed;
    }

    /**
     * admin-department-crud (Wave 2 T4) — 부서 비활성화 (soft-delete).
     *
     * <p>이미 비활성이면 idempotent — `deletedAt`을 갱신하지 않고 최초 비활성 시각을 보존한다.
     */
    public void deactivate() {
        if (this.deletedAt == null) {
            this.deletedAt = OffsetDateTime.now();
        }
    }

    /**
     * admin-department-crud (Wave 2 T4) — 부서 재활성화.
     *
     * <p>이미 활성이면 idempotent — no-op.
     */
    public void reactivate() {
        this.deletedAt = null;
    }
}
