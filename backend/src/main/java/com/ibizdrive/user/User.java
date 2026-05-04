package com.ibizdrive.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 사용자 엔티티 — Flyway V1 + V2 schema에 mapping.
 *
 * <p>DB schema가 진실의 출처이며 본 클래스는 그것을 reflect한다 (CLAUDE.md §3 원칙 6).
 * application.yml의 {@code spring.jpa.hibernate.ddl-auto: validate} 설정으로
 * 컬럼 불일치 시 부팅이 실패한다.
 *
 * <p>인증 관련 필드:
 * <ul>
 *   <li>{@code passwordHash} — BCrypt(strength=12), {@link DelegatingPasswordEncoder}
 *       프리픽스 호환. SSO 사용자는 NULL (ADR #19).
 *   <li>{@code role} — 시스템 ROLE (MEMBER/AUDITOR/ADMIN). docs/03 §3.2.5.
 *   <li>{@code isActive} — 관리자 계정 비활성화 플래그.
 *   <li>{@code lastLoginAt} — 로그인 성공 시 갱신. audit 보조.
 *   <li>{@code lockedAt} — 관리자 수동 잠금 (ADR #20). NULL = 미잠금.
 *   <li>{@code mustChangePassword} — 관리자 초대 후 첫 로그인 PW 변경 강제 (ADR #21).
 *   <li>{@code deletedAt} — soft delete (이메일 unique partial index와 짝).
 * </ul>
 *
 * <p>Lombok 미사용 — Spring Boot 표준 의존성만 사용 (CLAUDE.md §3 원칙 5).
 */
@Entity
@Table(name = "users")
public class User implements Serializable {

    /**
     * Spring Session JDBC가 SecurityContext의 principal({@link IbizDriveUserDetails} → {@code User})을
     * Java 직렬화로 SPRING_SESSION_ATTRIBUTES에 저장한다 (ADR #12 + #20). serialVersionUID를 명시하여
     * 클래스 진화 시 직렬화 호환성을 확정한다 — 필드 추가 시에도 기존 세션이 깨지지 않도록 유지.
     */
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    private Role role;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "locked_at")
    private OffsetDateTime lockedAt;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    /**
     * A16 — 부서 FK (V7, ADR #36, docs/02 §2.x). nullable — V7 적용 시 기존 row는 NULL.
     * 별도 setter로 노출하여 기존 생성자 시그니처(테스트 포함)를 보존.
     */
    @Column(name = "department_id")
    private UUID departmentId;

    protected User() {
        // JPA
    }

    public User(UUID id,
                String email,
                String displayName,
                String passwordHash,
                Role role,
                boolean isActive,
                boolean mustChangePassword,
                OffsetDateTime createdAt) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.isActive = isActive;
        this.mustChangePassword = mustChangePassword;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return isActive;
    }

    public OffsetDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public OffsetDateTime getLockedAt() {
        return lockedAt;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    /**
     * A16 — dept 배정/해제 (관리자 작업 트랙으로 분리, ADR #36). null 허용 (미배정 user).
     */
    public void setDepartmentId(UUID departmentId) {
        this.departmentId = departmentId;
    }

    public boolean isLocked() {
        return lockedAt != null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * 로그인 성공 시 호출 — {@code last_login_at} 컬럼 갱신.
     * 호출자는 동일 트랜잭션 내에서 {@link UserRepository#save}로 flush 책임을 진다.
     */
    public void recordLoginAt(OffsetDateTime at) {
        this.lastLoginAt = at;
    }

    /**
     * ROLE 변경 — A3.4. {@link com.ibizdrive.permission.PermissionService#changeRole}가 호출하고
     * 동일 트랜잭션 내에서 {@link UserRepository#save}로 flush 한다.
     * 감사 emission은 {@link com.ibizdrive.permission.RoleChangedEvent} publish로 분리된다 (ADR #24).
     */
    public void changeRoleTo(Role newRole) {
        if (newRole == null) {
            throw new IllegalArgumentException("newRole must not be null");
        }
        this.role = newRole;
    }

    /**
     * a1.5 — 비밀번호 재설정/변경 시 hash 갱신. 호출자는 미리 BCrypt 인코딩 완료한 hash를 전달하고
     * 동일 트랜잭션 내에서 {@link UserRepository#save}로 flush 한다.
     */
    public void changePasswordHash(String newHash) {
        if (newHash == null || newHash.isBlank()) {
            throw new IllegalArgumentException("newHash must not be blank");
        }
        this.passwordHash = newHash;
    }

    /**
     * auth-must-change-pw — 비밀번호 강제 변경 플래그 클리어 (ADR #21).
     * change/reset 흐름에서 PW 갱신 직후 호출하여 강제 redirect 루프를 종결시킨다.
     * 이미 false인 경우(자발적 변경)에는 무해 — idempotent.
     */
    public void clearMustChangePassword() {
        this.mustChangePassword = false;
    }

    /**
     * admin-user-mgmt — 관리자에 의한 계정 비활성화 (`is_active=false`).
     * 호출자(서비스)는 self-deactivate 차단 + audit emit + save flush 책임을 진다.
     * 이미 inactive인 경우 idempotent (서비스 단에서 no-op 분기).
     */
    public void deactivate() {
        this.isActive = false;
    }

    /**
     * admin-user-mgmt — 비활성화된 계정 재활성화 (`is_active=true`).
     * 본 트랙은 PATCH endpoint에서 호출 경로 미노출 — v1.x reactivate UX 트랙에서 사용.
     * 이미 active인 경우 idempotent.
     */
    public void reactivate() {
        this.isActive = true;
    }
}
