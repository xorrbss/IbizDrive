package com.ibizdrive.permission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for {@code permissions} table (docs/02 §2.6, V5 마이그레이션).
 *
 * <p>이름은 {@code Permission}이 아닌 {@code PermissionRow} — 같은 패키지의 enum {@link Permission}
 * (9-value 권한 wire enum, ADR #17)과의 type clash를 회피.
 *
 * <p>컬럼 매핑 정책:
 * <ul>
 *   <li>{@code resourceType}, {@code subjectType}, {@code preset}는 {@code String} (wire form: lower-case).
 *       enum 변환은 service 레이어에서 — DB CHECK 제약(V5)이 1차 boundary, JPA enum 매핑은 마이그레이션
 *       대비 안전한 기본값(`String`)을 사용.</li>
 *   <li>{@code preset}은 docs/02 §2.6 + ADR #28 — 4값({@code read|upload|edit|admin}). {@link Preset}
 *       enum의 {@code SHARE}는 별도 {@code shares} 테이블에서 관리(docs/02 §2.7) — 본 컬럼 미사용.</li>
 *   <li>{@code subjectId}는 {@code subjectType='everyone'}일 때 {@code NULL} 허용
 *       (V5 CHECK 제약이 강제).</li>
 * </ul>
 *
 * <p>본 entity는 직접 CRUD endpoint(A4.4)와 권한 평가 (A4.3 evaluator 내부 교체)에서 사용된다.
 */
@Entity
@Table(name = "permissions")
public class PermissionRow {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** {@code folder} 또는 {@code file} (V5 CHECK). */
    @Column(name = "resource_type", nullable = false, length = 20)
    private String resourceType;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    /** {@code user|department|role|everyone} (V5 CHECK). */
    @Column(name = "subject_type", nullable = false, length = 20)
    private String subjectType;

    /** {@code subjectType='everyone'}이면 {@code NULL} (V5 CHECK 제약). */
    @Column(name = "subject_id")
    private UUID subjectId;

    /** {@code read|upload|edit|admin} (ADR #28 — 4값, deny 제외). */
    @Column(name = "preset", nullable = false, length = 20)
    private String preset;

    @Column(name = "granted_by", nullable = false)
    private UUID grantedBy;

    /** {@code NULL} = 무기한. service 레벨에서 만료 검사. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PermissionRow() {
        // JPA
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public void setResourceId(UUID resourceId) {
        this.resourceId = resourceId;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(UUID subjectId) {
        this.subjectId = subjectId;
    }

    public String getPreset() {
        return preset;
    }

    public void setPreset(String preset) {
        this.preset = preset;
    }

    public UUID getGrantedBy() {
        return grantedBy;
    }

    public void setGrantedBy(UUID grantedBy) {
        this.grantedBy = grantedBy;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
