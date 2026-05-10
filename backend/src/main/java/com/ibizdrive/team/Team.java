package com.ibizdrive.team;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 팀(임시 워크스페이스) JPA entity — Flyway V12 schema에 mapping.
 *
 * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §1.1.
 * 사용자가 자율 생성하는 평면(parent 없음) 워크스페이스로, 부서와 동등한 scope 단위.
 *
 * <p>DB schema가 진실의 출처 (CLAUDE.md §3 원칙 6). {@code spring.jpa.hibernate.ddl-auto: validate}로
 * 컬럼 불일치 시 부팅 실패.
 *
 * <p><b>visibility 저장 전략 (의도)</b>:
 * V12 CHECK 제약은 lowercase 문자열({@code 'private'}, {@code 'internal'})만 허용한다.
 * {@code @Enumerated(EnumType.STRING)}을 쓰면 enum 상수명(uppercase)이 그대로 저장되어 CHECK 위반이
 * 발생하므로, {@code visibilityRaw} String 컬럼으로 직접 매핑하고 {@link #getVisibility()} /
 * {@link #changeVisibility(Visibility)}에서 enum과 변환한다 (CHECK가 진실의 출처). Folder.auditLevel과
 * 동일 패턴 — Java 표현형은 enum, DB 표현형은 lowercase String.
 *
 * <p><b>관계 매핑 정책</b>: {@code createdBy}, {@code archivedBy}, {@code rootFolderId}는 단순
 * {@code UUID} 컬럼 — Folder/FileItem과 동일 정책 (자기참조/cycle 회피, service layer가 명시적 fetch).
 * DB 레벨 FK는 V12에서 강제됨.
 *
 * <p><b>{@code rootFolderId} 일회성 보장</b>: V12는 {@code root_folder_id}를 nullable로 두어 FK만
 * 강제하고 일회성은 강제하지 않는다(팀 생성 → root folder 생성 순서 때문). 본 엔티티에서
 * {@link #attachRootFolder(UUID)}가 이미 설정된 경우 {@link IllegalStateException}으로 가드한다.
 */
@Entity
@Table(name = "teams")
public class Team {

    /** V12 visibility CHECK가 허용하는 두 값. enum constant name 대신 lowercase로 저장된다. */
    public enum Visibility {
        PRIVATE("private"),
        INTERNAL("internal");

        private final String dbValue;

        Visibility(String dbValue) {
            this.dbValue = dbValue;
        }

        public String dbValue() {
            return dbValue;
        }

        public static Visibility fromDbValue(String value) {
            if (value == null) {
                throw new IllegalArgumentException("visibility db value must not be null");
            }
            for (Visibility v : values()) {
                if (v.dbValue.equals(value)) {
                    return v;
                }
            }
            throw new IllegalArgumentException("unknown visibility db value: " + value);
        }
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 100)
    private String normalizedName;

    @Column(name = "description")
    private String description;

    /** lowercase 'private'|'internal' — V12 CHECK가 enforce. enum 변환은 도메인 메서드 책임. */
    @Column(name = "visibility", nullable = false, length = 20)
    private String visibilityRaw;

    /** 7-char hex (#RRGGBB) — V16 CHECK가 enforce. UI swatch (admin-teams.jsx CreateTeamModal). */
    @Column(name = "color", nullable = false, length = 7)
    private String color;

    /** 단일 designated team lead — UI label용 (멤버십 role과 독립). FK users(id), V16에서 NOT NULL. */
    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @Column(name = "root_folder_id")
    private UUID rootFolderId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    @Column(name = "archived_by")
    private UUID archivedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** V16 default — admin-teams.jsx TEAM_COLORS[0]. */
    public static final String DEFAULT_COLOR = "#5B7FCC";

    private static final java.util.regex.Pattern COLOR_PATTERN =
        java.util.regex.Pattern.compile("^#[0-9A-Fa-f]{6}$");

    protected Team() {
        // JPA
    }

    /**
     * 신규 팀 생성 (backward-compat 7-arg) — color는 {@link #DEFAULT_COLOR}, leadId는 createdBy로
     * 기본 설정. 기존 호출자(TeamService.create)와 단위 테스트가 그대로 사용한다.
     */
    public Team(
        UUID id,
        String name,
        String normalizedName,
        String description,
        Visibility visibility,
        UUID createdBy,
        OffsetDateTime createdAt
    ) {
        this(id, name, normalizedName, description, DEFAULT_COLOR, createdBy,
            visibility, createdBy, createdAt);
    }

    /**
     * 신규 팀 생성 (full 9-arg) — service layer가 호출. id/normalizedName/color/leadId/timestamp는
     * 호출자가 결정한다.
     *
     * @param color 7-char hex (#RRGGBB) — V16 CHECK enforce
     * @param leadId 단일 designated lead (보통 = createdBy 또는 명시 지정)
     * @throws IllegalArgumentException name이 null/blank/100자 초과, color 형식 위반, 또는 필수 인자 null
     */
    public Team(
        UUID id,
        String name,
        String normalizedName,
        String description,
        String color,
        UUID leadId,
        Visibility visibility,
        UUID createdBy,
        OffsetDateTime createdAt
    ) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (normalizedName == null || normalizedName.isEmpty()) {
            throw new IllegalArgumentException("normalizedName must not be null or empty");
        }
        if (visibility == null) {
            throw new IllegalArgumentException("visibility must not be null");
        }
        if (createdBy == null) {
            throw new IllegalArgumentException("createdBy must not be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        if (leadId == null) {
            throw new IllegalArgumentException("leadId must not be null");
        }
        validateColor(color);
        // rename으로 name 검증 + 할당
        this.id = id;
        this.normalizedName = normalizedName;
        this.description = description;
        this.color = color;
        this.leadId = leadId;
        this.visibilityRaw = visibility.dbValue();
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        rename(name);
    }

    private static void validateColor(String color) {
        if (color == null) {
            throw new IllegalArgumentException("color must not be null");
        }
        if (!COLOR_PATTERN.matcher(color).matches()) {
            throw new IllegalArgumentException(
                "color must match #RRGGBB hex format (got: " + color + ")");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    /** service layer가 name 변경 후 정규화 결과를 반영. */
    public void setNormalizedName(String normalizedName) {
        if (normalizedName == null || normalizedName.isEmpty()) {
            throw new IllegalArgumentException("normalizedName must not be null or empty");
        }
        this.normalizedName = normalizedName;
    }

    public String getDescription() {
        return description;
    }

    public String getColor() {
        return color;
    }

    public UUID getLeadId() {
        return leadId;
    }

    public Visibility getVisibility() {
        return Visibility.fromDbValue(visibilityRaw);
    }

    public UUID getRootFolderId() {
        return rootFolderId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public OffsetDateTime getArchivedAt() {
        return archivedAt;
    }

    public UUID getArchivedBy() {
        return archivedBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * archive 여부 — {@code archivedAt}이 비어있으면 active.
     * 부서의 {@code isActive()}와 동일한 시맨틱(soft-archive = 비활성).
     */
    public boolean isActive() {
        return archivedAt == null;
    }

    /**
     * 팀 이름 변경. 호출자(서비스)는 정규화/충돌 검출/normalizedName 갱신/audit emit 책임.
     * 도메인 메서드는 입력 검증(trim + 1~100자)과 displayName 할당만 담당.
     *
     * @throws IllegalArgumentException newName이 null, trim 후 빈 문자열, 또는 100자 초과
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
     * visibility 변경. lowercase 변환은 enum dbValue가 처리.
     *
     * @throws IllegalArgumentException visibility가 null
     */
    public void changeVisibility(Visibility visibility) {
        if (visibility == null) {
            throw new IllegalArgumentException("visibility must not be null");
        }
        this.visibilityRaw = visibility.dbValue();
    }

    /**
     * 팀 archive — soft archive. archivedAt/By + updatedAt 갱신, 멱등.
     *
     * <p>spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §2.2.
     * archive = read-only 시맨틱 (콘텐츠는 휴지통으로 가지 않음). 호출자(서비스)는 audit emit/listing 차단 책임.
     *
     * <p>이미 archived 상태면 no-op (archivedAt/By/updatedAt 모두 갱신 안 함) — 최초 archive 시각/주체 보존.
     *
     * @param actorId archive 수행자 (null 불가)
     * @param now archive 시각 (null 불가)
     * @throws IllegalArgumentException actorId 또는 now가 null
     */
    public void archive(UUID actorId, OffsetDateTime now) {
        if (actorId == null) throw new IllegalArgumentException("actorId must not be null");
        if (now == null) throw new IllegalArgumentException("now must not be null");
        if (this.archivedAt != null) {
            return; // idempotent — preserve original archive timestamp/actor.
        }
        this.archivedAt = now;
        this.archivedBy = actorId;
        this.updatedAt = now;
    }

    /**
     * 팀 restore — archive 해제. archivedAt/By 둘 다 clear, updatedAt 갱신, 멱등.
     *
     * <p>spec §2.2 un-archive. 활성 이름 충돌 검사는 service layer 책임 (V12 partial unique idx_teams_name_active).
     *
     * <p>이미 active 상태면 no-op.
     *
     * @param now restore 시각 (null 불가)
     * @throws IllegalArgumentException now가 null
     */
    public void restore(OffsetDateTime now) {
        if (now == null) throw new IllegalArgumentException("now must not be null");
        if (this.archivedAt == null) {
            return; // idempotent — already active.
        }
        this.archivedAt = null;
        this.archivedBy = null;
        this.updatedAt = now;
    }

    /**
     * 팀 root folder backref 부착 — 일회성. 팀 생성 직후 root folder를 만들고 한 번만 호출한다.
     *
     * <p>V12 schema는 {@code root_folder_id}를 nullable FK로만 두어 일회성을 DB에서 강제하지 않는다
     * (생성 순서: team insert → root folder insert → team update). 따라서 application 레벨에서
     * 재할당을 차단한다.
     *
     * @throws IllegalArgumentException rootFolderId가 null
     * @throws IllegalStateException 이미 rootFolderId가 설정됨
     */
    public void attachRootFolder(UUID rootFolderId) {
        if (rootFolderId == null) {
            throw new IllegalArgumentException("rootFolderId must not be null");
        }
        if (this.rootFolderId != null) {
            throw new IllegalStateException("rootFolderId already set: " + this.rootFolderId);
        }
        this.rootFolderId = rootFolderId;
    }

    /**
     * 팀 description 변경 — null 또는 blank는 null로 정규화. 호출자(서비스)가 audit emit.
     * V12 schema는 description을 nullable로 두므로 null/empty 허용. updatedAt은 service가 갱신.
     *
     * <p>최대 길이 검증은 V12 컬럼 길이 제약(TEXT, 무제한)에 의존하지 않고 application 레벨에서
     * 1000자 가드 — 디자인 admin-teams.jsx CreateTeamModal "한 줄로 설명" 의도 + DB 폭주 방지.
     *
     * @throws IllegalArgumentException newDescription 길이가 1000자 초과
     */
    public void updateDescription(String newDescription) {
        String normalized = (newDescription == null || newDescription.isBlank())
            ? null : newDescription.trim();
        if (normalized != null && normalized.length() > 1000) {
            throw new IllegalArgumentException("description must be at most 1000 characters");
        }
        this.description = normalized;
    }

    /**
     * 팀 color 변경 (#RRGGBB hex). V16 CHECK가 DB에서 강제하지만 도메인 메서드도 형식 검증.
     *
     * @throws IllegalArgumentException newColor가 null 또는 #RRGGBB 형식이 아님
     */
    public void changeColor(String newColor) {
        validateColor(newColor);
        this.color = newColor;
    }

    /**
     * 팀 lead 변경 — 단일 designated lead. 멤버십 role(OWNER/MEMBER)과 독립.
     *
     * <p>호출자(AdminTeamService)는 newLeadId가 해당 팀 멤버인지 검증할 책임. 도메인 메서드는
     * non-null 검증만 한다.
     *
     * @throws IllegalArgumentException newLeadId가 null
     */
    public void assignLead(UUID newLeadId) {
        if (newLeadId == null) {
            throw new IllegalArgumentException("leadId must not be null");
        }
        this.leadId = newLeadId;
    }

    /**
     * updatedAt을 명시적으로 갱신 — service layer가 mutation 후 호출.
     *
     * <p>rename/changeVisibility/updateDescription/changeColor/assignLead는 의도적으로 updatedAt을
     * 직접 갱신하지 않는다 (service가 트랜잭션 시점에 일괄 갱신해야 archive/restore와 일관).
     *
     * @throws IllegalArgumentException now가 null
     */
    public void touchUpdatedAt(OffsetDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        this.updatedAt = now;
    }
}
