package com.ibizdrive.folder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for {@code folders} table (docs/02 §2.3, V5 마이그레이션).
 *
 * <p>A4-data PR #6에서 cross-session ownership 충돌로 deferred 처리되었던 항목을
 * A4.5(a4-folder-entity) 세션에서 닫기 위한 entity layer.
 *
 * <p>관계 매핑 정책 (FileItem과 동일 — 본 세션 결정 2026-04-29):
 * <ul>
 *   <li>{@code parentId} / {@code originalParentId} / {@code ownerId}는 모두 {@code UUID} 단순 컬럼.
 *       {@code @ManyToOne}으로 매핑하지 않음 — 자기참조 lazy proxy 비용 + cycle 위험 + A4.5 contract
 *       최소화 원칙. service layer가 필요할 때 명시적으로 fetch한다.</li>
 *   <li>DB 레벨 FK ({@code folders.parent_id REFERENCES folders(id)},
 *       {@code folders.owner_id REFERENCES users(id)})는 V5에서 이미 강제됨 — 진실의 출처는 schema.</li>
 * </ul>
 *
 * <p>Soft delete는 명시적 query (e.g., {@code WHERE deletedAt IS NULL})로 처리 — Hibernate
 * {@code @SQLDelete}/{@code @Where}는 mutation 시점에 audit emission 누락 위험이 있어 회피
 * (FileItem과 동일 정책).
 *
 * <p>{@code auditLevel}은 String — DB CHECK ({@code 'standard'|'strict'})가 검증 책임.
 * Java enum 승격은 A4.6(FolderMutationService)에서 service contract와 함께 도입 예정.
 *
 * <p>{@code createdAt}/{@code updatedAt}는 V5에서 {@code DEFAULT NOW()} — entity persist 시
 * 명시적 set이 권장된다 (FileItem과 동일 패턴). DB 기본값 의존은 {@code ddl-auto=validate}
 * 부팅에는 영향 없으나 application 레벨 일관성을 위해 명시 set한다.
 */
@Entity
@Table(name = "folders")
public class Folder {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** {@code NULL} = root folder. ZERO_UUID COALESCE는 V5 partial unique index가 처리 (ADR #27). */
    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 255)
    private String normalizedName;

    @Column(name = "slug", nullable = false, length = 255)
    private String slug;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    /** {@code 'standard'} | {@code 'strict'} — DB CHECK가 enforce. enum 승격은 A4.6 이월. */
    @Column(name = "audit_level", nullable = false, length = 20)
    private String auditLevel;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** {@code deleted_at}과 짝 — V5 CHECK ({@code (deleted_at IS NULL) = (purge_after IS NULL)}). */
    @Column(name = "purge_after")
    private Instant purgeAfter;

    /**
     * V10 — soft-delete를 수행한 actor user id (cross-owner 복원 추적). FileItem.deletedBy와
     * 동일 정책 (단방향 CHECK, ON DELETE SET NULL, backfill 미실시).
     */
    @Column(name = "deleted_by")
    private UUID deletedBy;

    /** 휴지통 복원용 — 삭제 시점의 부모 폴더 id 보존 (docs/02 §2.3). */
    @Column(name = "original_parent_id")
    private UUID originalParentId;

    /**
     * V13 — workspace scope 종류 ({@code 'department'} | {@code 'team'}). DB CHECK가 enforce.
     * Team.visibility와 동일한 raw-String + enum-via-getter 패턴.
     */
    @Column(name = "scope_type", nullable = false, length = 20)
    private String scopeTypeRaw;

    /** V13 — scope의 entity id (departments.id 또는 teams.id). */
    @Column(name = "scope_id", nullable = false)
    private UUID scopeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Folder() {
        // JPA
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getParentId() {
        return parentId;
    }

    public void setParentId(UUID parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public void setNormalizedName(String normalizedName) {
        this.normalizedName = normalizedName;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public String getAuditLevel() {
        return auditLevel;
    }

    public void setAuditLevel(String auditLevel) {
        this.auditLevel = auditLevel;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Instant getPurgeAfter() {
        return purgeAfter;
    }

    public void setPurgeAfter(Instant purgeAfter) {
        this.purgeAfter = purgeAfter;
    }

    public UUID getDeletedBy() {
        return deletedBy;
    }

    public void setDeletedBy(UUID deletedBy) {
        this.deletedBy = deletedBy;
    }

    public UUID getOriginalParentId() {
        return originalParentId;
    }

    public void setOriginalParentId(UUID originalParentId) {
        this.originalParentId = originalParentId;
    }

    public ScopeType getScopeType() {
        return scopeTypeRaw == null ? null : ScopeType.fromDb(scopeTypeRaw);
    }

    public UUID getScopeId() {
        return scopeId;
    }

    /**
     * Workspace scope 할당 — type/id 모두 non-null 필수. V13 NOT NULL 제약과 일치.
     *
     * @throws IllegalArgumentException type 또는 id가 null
     */
    public void assignScope(ScopeType type, UUID id) {
        if (type == null) {
            throw new IllegalArgumentException("scopeType must not be null");
        }
        if (id == null) {
            throw new IllegalArgumentException("scopeId must not be null");
        }
        this.scopeTypeRaw = type.dbValue();
        this.scopeId = id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
