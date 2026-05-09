# Team-Centric Pivot — Plan A: Backend Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Backend 데이터 모델·권한 평가를 workspace(부서/팀) 멤버십 1차 모델로 전환하기 위한 foundation 레이어 구축.

**Architecture:** Spec `docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md` §1~§3 + §5.2 부분 구현. 4개 신규 마이그레이션(V12~V15) + Team/TeamMembership 도메인 신설 + 기존 Folder/File/Department 확장 + PermissionResolver에 membership step 추가 + GET /api/workspaces/me 신규.

**Tech Stack:** Spring Boot 3.x + Java 21, Spring Data JPA, Flyway, JUnit 5 + AssertJ + Testcontainers Postgres, Spring Security `@PreAuthorize`.

**Spec 범위 외 (다른 plan으로 이월):**
- Team archive/un-archive/purge → Plan A2 (또는 Plan E 직전)
- TeamMembership role 변경 + last-OWNER guard → Plan A2
- Cross-workspace folder 이동 → Plan D
- Share endpoint subject_type=team → Plan C
- 휴지통 workspace 분리 → Plan E
- Frontend → Plan B 이후

---

## Phase 1 — Schema (V12~V15)

### Task 1: V12 — `teams` + `team_memberships` 테이블

**Files:**
- Create: `backend/src/main/resources/db/migration/V12__teams.sql`
- Test: `backend/src/test/java/com/ibizdrive/team/V12MigrationTest.java`

- [ ] **Step 1: Write the failing migration test**

```java
package com.ibizdrive.team;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@ActiveProfiles("test")
class V12MigrationTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    void teamsTableExistsWithExpectedColumns() {
        Integer cols = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'teams'",
            Integer.class);
        assertThat(cols).isEqualTo(11);
    }

    @Test
    void teamMembershipsHasCompositePrimaryKey() {
        Integer pkCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.key_column_usage " +
            "WHERE table_name='team_memberships' AND constraint_name LIKE '%pkey'",
            Integer.class);
        assertThat(pkCount).isEqualTo(2);
    }

    @Test
    void teamsActiveNameUniqueIndexBlocksDuplicates() {
        jdbc.update("INSERT INTO users(id,email,is_active,created_at) " +
            "VALUES (gen_random_uuid(),'mig@test',TRUE,NOW())");
        java.util.UUID u = jdbc.queryForObject(
            "SELECT id FROM users WHERE email='mig@test'", java.util.UUID.class);
        jdbc.update("INSERT INTO teams(id,name,normalized_name,visibility,created_by,created_at,updated_at)" +
            " VALUES (gen_random_uuid(),'Alpha','alpha','private',?,NOW(),NOW())", u);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            jdbc.update("INSERT INTO teams(id,name,normalized_name,visibility,created_by,created_at,updated_at)" +
                " VALUES (gen_random_uuid(),'Alpha','alpha','private',?,NOW(),NOW())", u))
            .hasMessageContaining("idx_teams_name_active");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
cd backend && ./gradlew test --tests "com.ibizdrive.team.V12MigrationTest"
```
Expected: FAIL — "table teams does not exist" (V12 not yet created).

- [ ] **Step 3: Create the V12 migration**

```sql
-- V12__teams.sql
-- Team-centric pivot: 임시 팀 (사용자 자율 생성, 평면, archive 가능).
-- spec §1.1.

CREATE TABLE teams (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name            VARCHAR(100) NOT NULL,
  normalized_name VARCHAR(100) NOT NULL,
  description     TEXT,
  visibility      VARCHAR(20) NOT NULL DEFAULT 'private',
  root_folder_id  UUID,
  created_by      UUID NOT NULL REFERENCES users(id),
  archived_at     TIMESTAMPTZ,
  archived_by     UUID REFERENCES users(id),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CHECK (visibility IN ('private','internal'))
);

CREATE UNIQUE INDEX idx_teams_name_active
  ON teams(normalized_name) WHERE archived_at IS NULL;

CREATE TABLE team_memberships (
  team_id    UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
  user_id    UUID NOT NULL REFERENCES users(id),
  role       VARCHAR(20) NOT NULL,
  joined_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  invited_by UUID REFERENCES users(id),

  PRIMARY KEY (team_id, user_id),
  CHECK (role IN ('OWNER','MEMBER'))
);

CREATE INDEX idx_team_memberships_user ON team_memberships(user_id);
```

- [ ] **Step 4: Run test to verify it passes**

```
cd backend && ./gradlew test --tests "com.ibizdrive.team.V12MigrationTest"
```
Expected: PASS — all 3 tests green.

- [ ] **Step 5: Commit**

```
git add backend/src/main/resources/db/migration/V12__teams.sql \
        backend/src/test/java/com/ibizdrive/team/V12MigrationTest.java
git commit -m "feat(team-centric-pivot): V12 migration — teams + team_memberships"
```

---

### Task 2: V13 — `folders`/`files` scope columns

**Files:**
- Create: `backend/src/main/resources/db/migration/V13__folders_files_scope.sql`
- Test: `backend/src/test/java/com/ibizdrive/folder/V13ScopeMigrationTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.ibizdrive.folder;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest
@ActiveProfiles("test")
class V13ScopeMigrationTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    void foldersHasScopeColumnsNotNull() {
        String nullable = jdbc.queryForObject(
            "SELECT is_nullable FROM information_schema.columns " +
            "WHERE table_name='folders' AND column_name='scope_type'",
            String.class);
        assertThat(nullable).isEqualTo("NO");
    }

    @Test
    void foldersScopeTypeCheckRejectsInvalid() {
        java.util.UUID u = jdbc.queryForObject(
            "INSERT INTO users(id,email,is_active,created_at) " +
            "VALUES (gen_random_uuid(),'x@y',TRUE,NOW()) RETURNING id",
            java.util.UUID.class);
        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO folders(id,name,normalized_name,slug,owner_id,audit_level," +
            "scope_type,scope_id,created_at,updated_at) VALUES " +
            "(gen_random_uuid(),'r','r','r',?,'standard','user',?,NOW(),NOW())", u, u))
            .hasMessageContaining("chk_folders_scope_type");
    }

    @Test
    void filesScopeColumnsExist() {
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns " +
            "WHERE table_name='files' AND column_name IN ('scope_type','scope_id')",
            Integer.class);
        assertThat(cnt).isEqualTo(2);
    }
}
```

- [ ] **Step 2: Run test — expect FAIL (columns missing)**

```
cd backend && ./gradlew test --tests "com.ibizdrive.folder.V13ScopeMigrationTest"
```
Expected: FAIL.

- [ ] **Step 3: Create V13 migration**

```sql
-- V13__folders_files_scope.sql
-- Team-centric pivot: workspace scope on folders/files (spec §1.2).
-- Scenario A (green-field): NOT NULL + CHECK 즉시 적용.

ALTER TABLE folders
  ADD COLUMN scope_type VARCHAR(20) NOT NULL,
  ADD COLUMN scope_id   UUID NOT NULL,
  ADD CONSTRAINT chk_folders_scope_type
    CHECK (scope_type IN ('department','team'));

ALTER TABLE files
  ADD COLUMN scope_type VARCHAR(20) NOT NULL,
  ADD COLUMN scope_id   UUID NOT NULL,
  ADD CONSTRAINT chk_files_scope_type
    CHECK (scope_type IN ('department','team'));

CREATE INDEX idx_folders_scope ON folders(scope_type, scope_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_files_scope   ON files(scope_type, scope_id)   WHERE deleted_at IS NULL;
```

- [ ] **Step 4: Run test — expect PASS**

```
cd backend && ./gradlew test --tests "com.ibizdrive.folder.V13ScopeMigrationTest"
```
Expected: PASS.

- [ ] **Step 5: Commit**

```
git add backend/src/main/resources/db/migration/V13__folders_files_scope.sql \
        backend/src/test/java/com/ibizdrive/folder/V13ScopeMigrationTest.java
git commit -m "feat(team-centric-pivot): V13 migration — folders/files scope columns"
```

---

### Task 3: V14 — `departments.root_folder_id` + root-per-scope partial unique

**Files:**
- Create: `backend/src/main/resources/db/migration/V14__departments_root_folder.sql`
- Test: `backend/src/test/java/com/ibizdrive/department/V14RootFolderMigrationTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.ibizdrive.department;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest
@ActiveProfiles("test")
class V14RootFolderMigrationTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    void departmentsHasRootFolderIdColumn() {
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns " +
            "WHERE table_name='departments' AND column_name='root_folder_id'",
            Integer.class);
        assertThat(cnt).isEqualTo(1);
    }

    @Test
    void rootPerScopeUniqueBlocksTwoRoots() {
        java.util.UUID u = jdbc.queryForObject(
            "INSERT INTO users(id,email,is_active,created_at) " +
            "VALUES (gen_random_uuid(),'r@t',TRUE,NOW()) RETURNING id",
            java.util.UUID.class);
        java.util.UUID dept = jdbc.queryForObject(
            "INSERT INTO departments(id,name,created_at) " +
            "VALUES (gen_random_uuid(),'D',NOW()) RETURNING id",
            java.util.UUID.class);

        jdbc.update("INSERT INTO folders(id,name,normalized_name,slug,owner_id,audit_level," +
            "scope_type,scope_id,created_at,updated_at) VALUES " +
            "(gen_random_uuid(),'root','root','root',?,'standard','department',?,NOW(),NOW())",
            u, dept);

        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO folders(id,name,normalized_name,slug,owner_id,audit_level," +
            "scope_type,scope_id,created_at,updated_at) VALUES " +
            "(gen_random_uuid(),'root2','root2','root2',?,'standard','department',?,NOW(),NOW())",
            u, dept))
            .hasMessageContaining("idx_folders_root_per_scope");
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

```
cd backend && ./gradlew test --tests "com.ibizdrive.department.V14RootFolderMigrationTest"
```

- [ ] **Step 3: Create V14 migration**

```sql
-- V14__departments_root_folder.sql
-- Team-centric pivot: department root folder reference + root-per-scope unique (spec §1.3).

ALTER TABLE departments ADD COLUMN root_folder_id UUID REFERENCES folders(id);

CREATE UNIQUE INDEX idx_folders_root_per_scope
  ON folders(scope_type, scope_id)
  WHERE parent_id IS NULL AND deleted_at IS NULL;
```

- [ ] **Step 4: Run test — expect PASS**

- [ ] **Step 5: Commit**

```
git add backend/src/main/resources/db/migration/V14__departments_root_folder.sql \
        backend/src/test/java/com/ibizdrive/department/V14RootFolderMigrationTest.java
git commit -m "feat(team-centric-pivot): V14 migration — departments.root_folder_id + root-per-scope unique"
```

---

### Task 4: V15 — extend `permissions.subject_type` + `audit_log.target_type`

**Files:**
- Create: `backend/src/main/resources/db/migration/V15__permissions_audit_team.sql`
- Test: `backend/src/test/java/com/ibizdrive/permission/V15CheckExtensionTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.ibizdrive.permission;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@JdbcTest
@ActiveProfiles("test")
class V15CheckExtensionTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    void permissionsAcceptsTeamSubject() {
        java.util.UUID grantor = jdbc.queryForObject(
            "INSERT INTO users(id,email,is_active,created_at) " +
            "VALUES (gen_random_uuid(),'g@t',TRUE,NOW()) RETURNING id",
            java.util.UUID.class);
        java.util.UUID resourceId = java.util.UUID.randomUUID();
        java.util.UUID teamId = java.util.UUID.randomUUID();
        // V15 후 'team' 허용
        jdbc.update(
            "INSERT INTO permissions(id,resource_type,resource_id,subject_type,subject_id," +
            "preset,granted_by,created_at) VALUES " +
            "(gen_random_uuid(),'folder',?,'team',?,'read',?,NOW())",
            resourceId, teamId, grantor);
    }

    @Test
    void auditLogAcceptsTeamTarget() {
        java.util.UUID teamId = java.util.UUID.randomUUID();
        jdbc.update(
            "INSERT INTO audit_log(occurred_at,actor_id,event_type,target_type,target_id) " +
            "VALUES (NOW(),NULL,'team.created','team',?)", teamId);
    }
}
```

- [ ] **Step 2: Run — expect FAIL (CHECK violations)**

- [ ] **Step 3: Create V15 migration**

```sql
-- V15__permissions_audit_team.sql
-- Team-centric pivot: extend CHECKs to allow team subject/target (spec §1.4, §1.5).

ALTER TABLE permissions DROP CONSTRAINT permissions_subject_type_check;
ALTER TABLE permissions
  ADD CONSTRAINT permissions_subject_type_check
    CHECK (subject_type IN ('user','department','role','team','everyone'));

ALTER TABLE audit_log DROP CONSTRAINT audit_log_target_type_check;
ALTER TABLE audit_log
  ADD CONSTRAINT audit_log_target_type_check
    CHECK (target_type IN ('file','folder','user','permission','share',
                           'system','audit','department','team'));
```

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```
git add backend/src/main/resources/db/migration/V15__permissions_audit_team.sql \
        backend/src/test/java/com/ibizdrive/permission/V15CheckExtensionTest.java
git commit -m "feat(team-centric-pivot): V15 migration — permissions/audit_log accept team"
```

---

## Phase 2 — Domain Entities

### Task 5: `Team` JPA entity

**Files:**
- Create: `backend/src/main/java/com/ibizdrive/team/Team.java`
- Test: `backend/src/test/java/com/ibizdrive/team/TeamTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.ibizdrive.team;

import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamTest {

    @Test
    void newActiveTeamHasNoArchivedAt() {
        Team t = new Team(UUID.randomUUID(), "Alpha", "alpha", null,
            Team.Visibility.PRIVATE, UUID.randomUUID(), OffsetDateTime.now());
        assertThat(t.isActive()).isTrue();
        assertThat(t.getArchivedAt()).isNull();
    }

    @Test
    void renameTrimsAndValidates() {
        Team t = newTeam();
        t.rename("  Beta  ");
        assertThat(t.getName()).isEqualTo("Beta");
        assertThatThrownBy(() -> t.rename(""))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> t.rename(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectVisibilityNull() {
        Team t = newTeam();
        assertThatThrownBy(() -> t.changeVisibility(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private Team newTeam() {
        return new Team(UUID.randomUUID(), "Alpha", "alpha", null,
            Team.Visibility.PRIVATE, UUID.randomUUID(), OffsetDateTime.now());
    }
}
```

- [ ] **Step 2: Run — expect FAIL (Team class missing)**

- [ ] **Step 3: Implement Team entity**

```java
package com.ibizdrive.team;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity for {@code teams} (V12, spec §1.1).
 *
 * <p>임시 팀 — 평면, 사용자 자율 생성, archive 가능. 부서와 분리된 거버넌스
 * (spec §0 결정). Workspace 추상화는 별도 인터페이스(Task 14)에서 담는다.
 */
@Entity
@Table(name = "teams")
public class Team {

    public enum Visibility { PRIVATE, INTERNAL }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 100)
    private String normalizedName;

    @Column(name = "description")
    private String description;

    @Column(name = "visibility", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Visibility visibility;

    /** Lazy 채움 — root folder 생성 직후 set (TeamService.create). */
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

    protected Team() { /* JPA */ }

    public Team(UUID id, String name, String normalizedName, String description,
                Visibility visibility, UUID createdBy, OffsetDateTime now) {
        this.id = id;
        this.name = name;
        this.normalizedName = normalizedName;
        this.description = description;
        this.visibility = visibility;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean isActive() { return archivedAt == null; }

    public void rename(String newName) {
        if (newName == null) throw new IllegalArgumentException("name must not be null");
        String trimmed = newName.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("name must not be blank");
        if (trimmed.length() > 100) throw new IllegalArgumentException("name max 100 chars");
        this.name = trimmed;
        this.updatedAt = OffsetDateTime.now();
    }

    public void changeVisibility(Visibility v) {
        if (v == null) throw new IllegalArgumentException("visibility must not be null");
        this.visibility = v;
        this.updatedAt = OffsetDateTime.now();
    }

    public void setNormalizedName(String n) { this.normalizedName = n; }
    public void attachRootFolder(UUID folderId) { this.rootFolderId = folderId; }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getNormalizedName() { return normalizedName; }
    public String getDescription() { return description; }
    public Visibility getVisibility() { return visibility; }
    public UUID getRootFolderId() { return rootFolderId; }
    public UUID getCreatedBy() { return createdBy; }
    public OffsetDateTime getArchivedAt() { return archivedAt; }
    public UUID getArchivedBy() { return archivedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
```

- [ ] **Step 4: Run — expect PASS**

```
cd backend && ./gradlew test --tests "com.ibizdrive.team.TeamTest"
```

- [ ] **Step 5: Commit**

```
git add backend/src/main/java/com/ibizdrive/team/Team.java \
        backend/src/test/java/com/ibizdrive/team/TeamTest.java
git commit -m "feat(team-centric-pivot): Team JPA entity"
```

---

### Task 6: `TeamMembership` entity with composite key

**Files:**
- Create: `backend/src/main/java/com/ibizdrive/team/TeamMembership.java`
- Create: `backend/src/main/java/com/ibizdrive/team/TeamMembershipId.java`
- Test: `backend/src/test/java/com/ibizdrive/team/TeamMembershipTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.ibizdrive.team;

import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class TeamMembershipTest {

    @Test
    void roleSwitch() {
        TeamMembership m = new TeamMembership(
            UUID.randomUUID(), UUID.randomUUID(),
            TeamMembership.Role.MEMBER, UUID.randomUUID(), OffsetDateTime.now());
        m.changeRole(TeamMembership.Role.OWNER);
        assertThat(m.getRole()).isEqualTo(TeamMembership.Role.OWNER);
    }

    @Test
    void compositeIdEqualityIsValueBased() {
        UUID t = UUID.randomUUID();
        UUID u = UUID.randomUUID();
        assertThat(new TeamMembershipId(t, u)).isEqualTo(new TeamMembershipId(t, u));
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: Implement composite ID + entity**

```java
// TeamMembershipId.java
package com.ibizdrive.team;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class TeamMembershipId implements Serializable {

    private UUID teamId;
    private UUID userId;

    protected TeamMembershipId() { /* JPA */ }

    public TeamMembershipId(UUID teamId, UUID userId) {
        this.teamId = teamId;
        this.userId = userId;
    }

    public UUID getTeamId() { return teamId; }
    public UUID getUserId() { return userId; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TeamMembershipId other)) return false;
        return Objects.equals(teamId, other.teamId) && Objects.equals(userId, other.userId);
    }

    @Override public int hashCode() { return Objects.hash(teamId, userId); }
}
```

```java
// TeamMembership.java
package com.ibizdrive.team;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity for {@code team_memberships} (V12, spec §1.1).
 * Composite PK (team_id, user_id) — 한 사용자는 한 팀에 한 row만.
 */
@Entity
@Table(name = "team_memberships")
public class TeamMembership {

    public enum Role { OWNER, MEMBER }

    @EmbeddedId
    private TeamMembershipId id;

    @Column(name = "role", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private OffsetDateTime joinedAt;

    @Column(name = "invited_by")
    private UUID invitedBy;

    protected TeamMembership() { /* JPA */ }

    public TeamMembership(UUID teamId, UUID userId, Role role, UUID invitedBy, OffsetDateTime now) {
        this.id = new TeamMembershipId(teamId, userId);
        this.role = role;
        this.invitedBy = invitedBy;
        this.joinedAt = now;
    }

    public void changeRole(Role newRole) {
        if (newRole == null) throw new IllegalArgumentException("role must not be null");
        this.role = newRole;
    }

    public TeamMembershipId getId() { return id; }
    public UUID getTeamId() { return id.getTeamId(); }
    public UUID getUserId() { return id.getUserId(); }
    public Role getRole() { return role; }
    public OffsetDateTime getJoinedAt() { return joinedAt; }
    public UUID getInvitedBy() { return invitedBy; }
}
```

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```
git add backend/src/main/java/com/ibizdrive/team/TeamMembership.java \
        backend/src/main/java/com/ibizdrive/team/TeamMembershipId.java \
        backend/src/test/java/com/ibizdrive/team/TeamMembershipTest.java
git commit -m "feat(team-centric-pivot): TeamMembership entity with composite PK"
```

---

### Task 7: `Folder` entity — add `scope_type`/`scope_id`

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/folder/Folder.java`
- Create: `backend/src/main/java/com/ibizdrive/folder/ScopeType.java`
- Test: `backend/src/test/java/com/ibizdrive/folder/FolderScopeTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.ibizdrive.folder;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class FolderScopeTest {

    @Test
    void scopeFieldsAreReadable() {
        Folder f = new Folder();
        UUID dept = UUID.randomUUID();
        f.assignScope(ScopeType.DEPARTMENT, dept);
        assertThat(f.getScopeType()).isEqualTo(ScopeType.DEPARTMENT);
        assertThat(f.getScopeId()).isEqualTo(dept);
    }

    @Test
    void assignScopeRejectsNull() {
        Folder f = new Folder();
        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> f.assignScope(null, UUID.randomUUID()))
            .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> f.assignScope(ScopeType.TEAM, null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: Add ScopeType enum + Folder fields**

```java
// ScopeType.java
package com.ibizdrive.folder;

/**
 * Workspace scope discriminator for folders/files (spec §1.2).
 * DB CHECK constraint enforces enum values; Java enum is application-level mirror.
 */
public enum ScopeType {
    DEPARTMENT("department"),
    TEAM("team");

    private final String dbValue;

    ScopeType(String v) { this.dbValue = v; }
    public String dbValue() { return dbValue; }

    public static ScopeType fromDb(String s) {
        for (ScopeType t : values()) if (t.dbValue.equals(s)) return t;
        throw new IllegalArgumentException("unknown scope_type: " + s);
    }
}
```

In `Folder.java` add fields and accessor near other fields (after `originalParentId`):

```java
    @Column(name = "scope_type", nullable = false, length = 20)
    @org.hibernate.annotations.JdbcType(org.hibernate.type.descriptor.jdbc.VarcharJdbcType.class)
    @Enumerated(EnumType.STRING)
    private ScopeType scopeType;

    @Column(name = "scope_id", nullable = false)
    private UUID scopeId;

    public ScopeType getScopeType() { return scopeType; }
    public UUID getScopeId() { return scopeId; }

    public void assignScope(ScopeType type, UUID id) {
        if (type == null) throw new IllegalArgumentException("scopeType must not be null");
        if (id == null) throw new IllegalArgumentException("scopeId must not be null");
        this.scopeType = type;
        this.scopeId = id;
    }
```

(Note: `Enumerated(EnumType.STRING)` will use enum constant name — but our DB stores lowercase. Use a simple `@Convert` or store as String and convert. Simpler: store as String column; expose enum in API.)

Replace the field block with String storage:

```java
    @Column(name = "scope_type", nullable = false, length = 20)
    private String scopeTypeRaw;

    @Column(name = "scope_id", nullable = false)
    private UUID scopeId;

    public ScopeType getScopeType() {
        return scopeTypeRaw == null ? null : ScopeType.fromDb(scopeTypeRaw);
    }

    public UUID getScopeId() { return scopeId; }

    public void assignScope(ScopeType type, UUID id) {
        if (type == null) throw new IllegalArgumentException("scopeType must not be null");
        if (id == null) throw new IllegalArgumentException("scopeId must not be null");
        this.scopeTypeRaw = type.dbValue();
        this.scopeId = id;
    }
```

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```
git add backend/src/main/java/com/ibizdrive/folder/Folder.java \
        backend/src/main/java/com/ibizdrive/folder/ScopeType.java \
        backend/src/test/java/com/ibizdrive/folder/FolderScopeTest.java
git commit -m "feat(team-centric-pivot): Folder.scope_type/scope_id fields + ScopeType enum"
```

---

### Task 8: `FileItem` entity — add scope fields

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/file/FileItem.java` (or `File.java` — find the actual class name)
- Test: `backend/src/test/java/com/ibizdrive/file/FileScopeTest.java`

- [ ] **Step 1: Discover the file entity class name**

```
ls backend/src/main/java/com/ibizdrive/file/*.java
```

Use the entity class file shown (likely `FileItem.java`).

- [ ] **Step 2: Write the failing test**

```java
package com.ibizdrive.file;

import com.ibizdrive.folder.ScopeType;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class FileScopeTest {

    @Test
    void assignScopeStoresValues() {
        FileItem f = new FileItem();
        UUID team = UUID.randomUUID();
        f.assignScope(ScopeType.TEAM, team);
        assertThat(f.getScopeType()).isEqualTo(ScopeType.TEAM);
        assertThat(f.getScopeId()).isEqualTo(team);
    }
}
```

- [ ] **Step 3: Run — expect FAIL**

- [ ] **Step 4: Add the same scope field block to FileItem (mirror Task 7 final block)**

Add identical `scopeTypeRaw`/`scopeId` fields, getters, and `assignScope(...)` method using `com.ibizdrive.folder.ScopeType`.

- [ ] **Step 5: Run — expect PASS**

- [ ] **Step 6: Commit**

```
git add backend/src/main/java/com/ibizdrive/file/FileItem.java \
        backend/src/test/java/com/ibizdrive/file/FileScopeTest.java
git commit -m "feat(team-centric-pivot): FileItem scope fields"
```

---

### Task 9: `Department` entity — add `rootFolderId`

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/department/Department.java`
- Test: `backend/src/test/java/com/ibizdrive/department/DepartmentRootFolderTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.ibizdrive.department;

import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class DepartmentRootFolderTest {

    @Test
    void attachRootFolder() {
        Department d = new Department(UUID.randomUUID(), "Sales", OffsetDateTime.now());
        UUID f = UUID.randomUUID();
        d.attachRootFolder(f);
        assertThat(d.getRootFolderId()).isEqualTo(f);
    }

    @Test
    void rejectAttachIfAlreadySet() {
        Department d = new Department(UUID.randomUUID(), "Sales", OffsetDateTime.now());
        d.attachRootFolder(UUID.randomUUID());
        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> d.attachRootFolder(UUID.randomUUID()))
            .isInstanceOf(IllegalStateException.class);
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: Add field + accessor to Department**

In `Department.java` add after `deletedAt`:

```java
    @Column(name = "root_folder_id")
    private UUID rootFolderId;

    public UUID getRootFolderId() { return rootFolderId; }

    /**
     * Workspace pivot — root folder는 부서 생성 트랜잭션에서 정확히 1회만 attach.
     * 재할당은 root invariant(spec §1.3) 위반.
     */
    public void attachRootFolder(UUID folderId) {
        if (folderId == null) throw new IllegalArgumentException("folderId must not be null");
        if (this.rootFolderId != null) {
            throw new IllegalStateException("root folder already attached");
        }
        this.rootFolderId = folderId;
    }
```

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```
git add backend/src/main/java/com/ibizdrive/department/Department.java \
        backend/src/test/java/com/ibizdrive/department/DepartmentRootFolderTest.java
git commit -m "feat(team-centric-pivot): Department.rootFolderId field"
```

---

## Phase 3 — Repositories

### Task 10: `TeamRepository`

**Files:**
- Create: `backend/src/main/java/com/ibizdrive/team/TeamRepository.java`
- Test: `backend/src/test/java/com/ibizdrive/team/TeamRepositoryTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.ibizdrive.team;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class TeamRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired TeamRepository repo;

    @Test
    void findActiveByNormalizedNameReturnsOnlyActive() {
        UUID userA = persistUser("a@t");
        Team active = new Team(UUID.randomUUID(), "Alpha", "alpha", null,
            Team.Visibility.PRIVATE, userA, OffsetDateTime.now());
        em.persist(active);
        em.flush();
        assertThat(repo.findActiveByNormalizedName("alpha")).isPresent();
    }

    @Test
    void findActiveByNormalizedNameSkipsArchived() {
        UUID u = persistUser("b@t");
        Team t = new Team(UUID.randomUUID(), "Beta", "beta", null,
            Team.Visibility.PRIVATE, u, OffsetDateTime.now());
        em.persist(t);
        em.getEntityManager().createNativeQuery(
            "UPDATE teams SET archived_at = NOW() WHERE id = ?")
            .setParameter(1, t.getId()).executeUpdate();
        em.clear();
        assertThat(repo.findActiveByNormalizedName("beta")).isEmpty();
    }

    private UUID persistUser(String email) {
        UUID id = UUID.randomUUID();
        em.getEntityManager().createNativeQuery(
            "INSERT INTO users(id,email,is_active,created_at) VALUES (?,?,TRUE,NOW())")
            .setParameter(1, id).setParameter(2, email).executeUpdate();
        return id;
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: Implement repository**

```java
package com.ibizdrive.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    @Query("SELECT t FROM Team t WHERE t.normalizedName = :name AND t.archivedAt IS NULL")
    Optional<Team> findActiveByNormalizedName(String name);
}
```

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```
git add backend/src/main/java/com/ibizdrive/team/TeamRepository.java \
        backend/src/test/java/com/ibizdrive/team/TeamRepositoryTest.java
git commit -m "feat(team-centric-pivot): TeamRepository with active-name lookup"
```

---

### Task 11: `TeamMembershipRepository`

**Files:**
- Create: `backend/src/main/java/com/ibizdrive/team/TeamMembershipRepository.java`
- Test: `backend/src/test/java/com/ibizdrive/team/TeamMembershipRepositoryTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.ibizdrive.team;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class TeamMembershipRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired TeamMembershipRepository repo;

    @Test
    void findByUserReturnsAllMemberships() {
        UUID user = persistUser("u@t");
        UUID t1 = persistTeam("Alpha", "alpha", user);
        UUID t2 = persistTeam("Beta", "beta", user);
        em.persist(new TeamMembership(t1, user, TeamMembership.Role.OWNER, null, OffsetDateTime.now()));
        em.persist(new TeamMembership(t2, user, TeamMembership.Role.MEMBER, null, OffsetDateTime.now()));
        em.flush();
        List<TeamMembership> all = repo.findByUserId(user);
        assertThat(all).hasSize(2);
    }

    @Test
    void countOwnersReturnsCount() {
        UUID user1 = persistUser("o1@t");
        UUID user2 = persistUser("o2@t");
        UUID team = persistTeam("T", "t", user1);
        em.persist(new TeamMembership(team, user1, TeamMembership.Role.OWNER, null, OffsetDateTime.now()));
        em.persist(new TeamMembership(team, user2, TeamMembership.Role.MEMBER, null, OffsetDateTime.now()));
        em.flush();
        assertThat(repo.countByTeamIdAndRole(team, TeamMembership.Role.OWNER)).isEqualTo(1);
    }

    private UUID persistUser(String email) {
        UUID id = UUID.randomUUID();
        em.getEntityManager().createNativeQuery(
            "INSERT INTO users(id,email,is_active,created_at) VALUES (?,?,TRUE,NOW())")
            .setParameter(1, id).setParameter(2, email).executeUpdate();
        return id;
    }

    private UUID persistTeam(String name, String normalized, UUID createdBy) {
        Team t = new Team(UUID.randomUUID(), name, normalized, null,
            Team.Visibility.PRIVATE, createdBy, OffsetDateTime.now());
        em.persist(t);
        return t.getId();
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: Implement repository**

```java
package com.ibizdrive.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TeamMembershipRepository
        extends JpaRepository<TeamMembership, TeamMembershipId> {

    @Query("SELECT m FROM TeamMembership m WHERE m.id.userId = :user")
    List<TeamMembership> findByUserId(@Param("user") UUID userId);

    @Query("SELECT COUNT(m) FROM TeamMembership m " +
           "WHERE m.id.teamId = :team AND m.role = :role")
    long countByTeamIdAndRole(@Param("team") UUID teamId, @Param("role") TeamMembership.Role role);

    @Query("SELECT m FROM TeamMembership m WHERE m.id.teamId = :team")
    List<TeamMembership> findByTeamId(@Param("team") UUID teamId);
}
```

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```
git add backend/src/main/java/com/ibizdrive/team/TeamMembershipRepository.java \
        backend/src/test/java/com/ibizdrive/team/TeamMembershipRepositoryTest.java
git commit -m "feat(team-centric-pivot): TeamMembershipRepository"
```

---

## Phase 4 — Audit Constants

### Task 12: Extend `AuditTargetType` + `AuditEventType` for team

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/audit/AuditTargetType.java`
- Modify: `backend/src/main/java/com/ibizdrive/audit/AuditEventType.java`
- Test: `backend/src/test/java/com/ibizdrive/audit/AuditTeamConstantsTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.ibizdrive.audit;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AuditTeamConstantsTest {

    @Test
    void auditTargetTypeContainsTeam() {
        assertThat(AuditTargetType.valueOf("TEAM").dbValue()).isEqualTo("team");
    }

    @Test
    void auditEventTypeContainsTeamLifecycle() {
        assertThat(AuditEventType.TEAM_CREATED.eventName()).isEqualTo("team.created");
        assertThat(AuditEventType.TEAM_MEMBER_ADDED.eventName()).isEqualTo("team.member.added");
        assertThat(AuditEventType.TEAM_MEMBER_REMOVED.eventName()).isEqualTo("team.member.removed");
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: Add `TEAM` to `AuditTargetType` and team events to `AuditEventType`**

Read the existing enums to mirror naming patterns. Add to `AuditTargetType`:

```java
    TEAM("team"),
```

Add to `AuditEventType`:

```java
    TEAM_CREATED("team.created"),
    TEAM_ARCHIVED("team.archived"),
    TEAM_UNARCHIVED("team.unarchived"),
    TEAM_MEMBER_ADDED("team.member.added"),
    TEAM_MEMBER_REMOVED("team.member.removed"),
    TEAM_MEMBER_ROLE_CHANGED("team.member.role_changed"),
```

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```
git add backend/src/main/java/com/ibizdrive/audit/AuditTargetType.java \
        backend/src/main/java/com/ibizdrive/audit/AuditEventType.java \
        backend/src/test/java/com/ibizdrive/audit/AuditTeamConstantsTest.java
git commit -m "feat(team-centric-pivot): audit constants for team events"
```

---

## Phase 5 — Workspace Abstraction + Read API

### Task 13: `Workspace` interface + `WorkspaceKind`

**Files:**
- Create: `backend/src/main/java/com/ibizdrive/workspace/Workspace.java`
- Create: `backend/src/main/java/com/ibizdrive/workspace/WorkspaceKind.java`
- Create: `backend/src/main/java/com/ibizdrive/workspace/DepartmentWorkspace.java`
- Create: `backend/src/main/java/com/ibizdrive/workspace/TeamWorkspace.java`
- Test: `backend/src/test/java/com/ibizdrive/workspace/WorkspaceAdaptersTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.ibizdrive.workspace;

import com.ibizdrive.department.Department;
import com.ibizdrive.team.Team;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceAdaptersTest {

    @Test
    void departmentAdapterMapsKindAndId() {
        Department d = new Department(UUID.randomUUID(), "Sales", OffsetDateTime.now());
        UUID root = UUID.randomUUID();
        d.attachRootFolder(root);
        Workspace w = new DepartmentWorkspace(d);
        assertThat(w.kind()).isEqualTo(WorkspaceKind.DEPARTMENT);
        assertThat(w.id()).isEqualTo(d.getId());
        assertThat(w.name()).isEqualTo("Sales");
        assertThat(w.rootFolderId()).isEqualTo(root);
        assertThat(w.isActive()).isTrue();
    }

    @Test
    void teamAdapterMapsKindAndArchive() {
        Team t = new Team(UUID.randomUUID(), "Alpha", "alpha", null,
            Team.Visibility.PRIVATE, UUID.randomUUID(), OffsetDateTime.now());
        UUID root = UUID.randomUUID();
        t.attachRootFolder(root);
        Workspace w = new TeamWorkspace(t);
        assertThat(w.kind()).isEqualTo(WorkspaceKind.TEAM);
        assertThat(w.rootFolderId()).isEqualTo(root);
        assertThat(w.isActive()).isTrue();
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: Implement abstraction**

```java
// WorkspaceKind.java
package com.ibizdrive.workspace;

public enum WorkspaceKind { DEPARTMENT, TEAM }
```

```java
// Workspace.java
package com.ibizdrive.workspace;

import java.util.UUID;

/** Polymorphic facade over Department/Team for permission evaluation + sidebar API. */
public interface Workspace {
    WorkspaceKind kind();
    UUID id();
    String name();
    UUID rootFolderId();
    boolean isActive();
}
```

```java
// DepartmentWorkspace.java
package com.ibizdrive.workspace;

import com.ibizdrive.department.Department;

import java.util.UUID;

public record DepartmentWorkspace(Department dept) implements Workspace {
    public WorkspaceKind kind() { return WorkspaceKind.DEPARTMENT; }
    public UUID id() { return dept.getId(); }
    public String name() { return dept.getName(); }
    public UUID rootFolderId() { return dept.getRootFolderId(); }
    public boolean isActive() { return dept.isActive(); }
}
```

```java
// TeamWorkspace.java
package com.ibizdrive.workspace;

import com.ibizdrive.team.Team;

import java.util.UUID;

public record TeamWorkspace(Team team) implements Workspace {
    public WorkspaceKind kind() { return WorkspaceKind.TEAM; }
    public UUID id() { return team.getId(); }
    public String name() { return team.getName(); }
    public UUID rootFolderId() { return team.getRootFolderId(); }
    public boolean isActive() { return team.isActive(); }
}
```

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```
git add backend/src/main/java/com/ibizdrive/workspace/ \
        backend/src/test/java/com/ibizdrive/workspace/
git commit -m "feat(team-centric-pivot): Workspace abstraction (Department/Team adapters)"
```

---

### Task 14: `WorkspaceService.findForUser(userId)`

**Files:**
- Create: `backend/src/main/java/com/ibizdrive/workspace/WorkspaceService.java`
- Test: `backend/src/test/java/com/ibizdrive/workspace/WorkspaceServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.ibizdrive.workspace;

import com.ibizdrive.department.Department;
import com.ibizdrive.department.DepartmentRepository;
import com.ibizdrive.team.Team;
import com.ibizdrive.team.TeamMembership;
import com.ibizdrive.team.TeamMembershipRepository;
import com.ibizdrive.team.TeamRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class WorkspaceServiceTest {

    @Test
    void returnsDepartmentAndTeamsForUser() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID teamA = UUID.randomUUID();

        DepartmentRepository deptRepo = Mockito.mock(DepartmentRepository.class);
        TeamRepository teamRepo = Mockito.mock(TeamRepository.class);
        TeamMembershipRepository memRepo = Mockito.mock(TeamMembershipRepository.class);
        UserDepartmentLookup lookup = Mockito.mock(UserDepartmentLookup.class);

        when(lookup.departmentIdOf(userId)).thenReturn(Optional.of(deptId));
        Department dept = new Department(deptId, "Sales", OffsetDateTime.now());
        dept.attachRootFolder(UUID.randomUUID());
        when(deptRepo.findById(deptId)).thenReturn(Optional.of(dept));

        TeamMembership m = new TeamMembership(teamA, userId, TeamMembership.Role.MEMBER,
            null, OffsetDateTime.now());
        when(memRepo.findByUserId(userId)).thenReturn(List.of(m));

        Team t = new Team(teamA, "Alpha", "alpha", null,
            Team.Visibility.PRIVATE, userId, OffsetDateTime.now());
        t.attachRootFolder(UUID.randomUUID());
        when(teamRepo.findAllById(List.of(teamA))).thenReturn(List.of(t));

        WorkspaceService svc = new WorkspaceService(deptRepo, teamRepo, memRepo, lookup);
        WorkspaceListing listing = svc.findForUser(userId);

        assertThat(listing.department()).isPresent();
        assertThat(listing.department().get().id()).isEqualTo(deptId);
        assertThat(listing.teams()).hasSize(1);
        assertThat(listing.teams().get(0).id()).isEqualTo(teamA);
    }

    @Test
    void emptyDepartmentNotIncluded() {
        UUID userId = UUID.randomUUID();
        DepartmentRepository deptRepo = Mockito.mock(DepartmentRepository.class);
        TeamRepository teamRepo = Mockito.mock(TeamRepository.class);
        TeamMembershipRepository memRepo = Mockito.mock(TeamMembershipRepository.class);
        UserDepartmentLookup lookup = Mockito.mock(UserDepartmentLookup.class);
        when(lookup.departmentIdOf(userId)).thenReturn(Optional.empty());
        when(memRepo.findByUserId(userId)).thenReturn(List.of());

        WorkspaceService svc = new WorkspaceService(deptRepo, teamRepo, memRepo, lookup);
        WorkspaceListing listing = svc.findForUser(userId);

        assertThat(listing.department()).isEmpty();
        assertThat(listing.teams()).isEmpty();
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: Implement service + supporting types**

```java
// UserDepartmentLookup.java
package com.ibizdrive.workspace;

import java.util.Optional;
import java.util.UUID;

/** Indirection over user.department_id read — allows test mocking. */
public interface UserDepartmentLookup {
    Optional<UUID> departmentIdOf(UUID userId);
}
```

```java
// WorkspaceListing.java
package com.ibizdrive.workspace;

import java.util.List;
import java.util.Optional;

public record WorkspaceListing(Optional<DepartmentWorkspace> department,
                               List<TeamWorkspace> teams) {}
```

```java
// WorkspaceService.java
package com.ibizdrive.workspace;

import com.ibizdrive.department.DepartmentRepository;
import com.ibizdrive.team.Team;
import com.ibizdrive.team.TeamMembership;
import com.ibizdrive.team.TeamMembershipRepository;
import com.ibizdrive.team.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class WorkspaceService {

    private final DepartmentRepository deptRepo;
    private final TeamRepository teamRepo;
    private final TeamMembershipRepository memRepo;
    private final UserDepartmentLookup userDeptLookup;

    public WorkspaceService(DepartmentRepository deptRepo,
                            TeamRepository teamRepo,
                            TeamMembershipRepository memRepo,
                            UserDepartmentLookup userDeptLookup) {
        this.deptRepo = deptRepo;
        this.teamRepo = teamRepo;
        this.memRepo = memRepo;
        this.userDeptLookup = userDeptLookup;
    }

    @Transactional(readOnly = true)
    public WorkspaceListing findForUser(UUID userId) {
        Optional<DepartmentWorkspace> dept = userDeptLookup.departmentIdOf(userId)
            .flatMap(deptRepo::findById)
            .filter(d -> d.isActive() && d.getRootFolderId() != null)
            .map(DepartmentWorkspace::new);

        List<UUID> teamIds = memRepo.findByUserId(userId).stream()
            .map(TeamMembership::getTeamId).toList();

        List<TeamWorkspace> teams = teamIds.isEmpty() ? List.of()
            : teamRepo.findAllById(teamIds).stream()
                .filter(t -> t.getRootFolderId() != null)
                .map(TeamWorkspace::new).toList();

        return new WorkspaceListing(dept, teams);
    }
}
```

Implement default `UserDepartmentLookup` adapter (uses `UserRepository.findById(userId).getDepartmentId()`):

```java
// DefaultUserDepartmentLookup.java
package com.ibizdrive.workspace;

import com.ibizdrive.user.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class DefaultUserDepartmentLookup implements UserDepartmentLookup {

    private final UserRepository userRepo;

    public DefaultUserDepartmentLookup(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @Override
    public Optional<UUID> departmentIdOf(UUID userId) {
        return userRepo.findById(userId).map(u -> u.getDepartmentId());
    }
}
```

(If `User` entity does not yet expose `getDepartmentId()`, add that getter — V7 already has the column.)

- [ ] **Step 4: Run — expect PASS**

```
cd backend && ./gradlew test --tests "com.ibizdrive.workspace.WorkspaceServiceTest"
```

- [ ] **Step 5: Commit**

```
git add backend/src/main/java/com/ibizdrive/workspace/ \
        backend/src/test/java/com/ibizdrive/workspace/WorkspaceServiceTest.java
git commit -m "feat(team-centric-pivot): WorkspaceService.findForUser"
```

---

### Task 15: `WorkspaceController` — `GET /api/workspaces/me`

**Files:**
- Create: `backend/src/main/java/com/ibizdrive/workspace/WorkspaceController.java`
- Create: `backend/src/main/java/com/ibizdrive/workspace/dto/WorkspaceMeResponse.java`
- Test: `backend/src/test/java/com/ibizdrive/workspace/WorkspaceControllerIntegrationTest.java`

- [ ] **Step 1: Write the failing integration test**

```java
package com.ibizdrive.workspace;

import com.ibizdrive.department.Department;
import com.ibizdrive.department.DepartmentRepository;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.folder.ScopeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class WorkspaceControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired DepartmentRepository deptRepo;
    @Autowired FolderRepository folderRepo;

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void meReturnsDepartmentAndEmptyTeams() throws Exception {
        // seed: dept + root folder + user attached to dept
        // (precise seed code mirrors existing controller integration tests in this codebase)
        // ...
        mvc.perform(get("/api/workspaces/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.department.name").exists())
            .andExpect(jsonPath("$.teams").isArray());
    }
}
```

- [ ] **Step 2: Run — expect FAIL (controller missing)**

- [ ] **Step 3: Implement controller + DTO**

```java
// WorkspaceMeResponse.java
package com.ibizdrive.workspace.dto;

import com.ibizdrive.workspace.WorkspaceKind;

import java.util.List;
import java.util.UUID;

public record WorkspaceMeResponse(WorkspaceRef department, List<WorkspaceRef> teams) {

    public record WorkspaceRef(WorkspaceKind kind, UUID id, String name, UUID rootFolderId) {}
}
```

```java
// WorkspaceController.java
package com.ibizdrive.workspace;

import com.ibizdrive.workspace.dto.WorkspaceMeResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService svc;

    public WorkspaceController(WorkspaceService svc) {
        this.svc = svc;
    }

    @GetMapping("/me")
    public WorkspaceMeResponse me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = UUID.fromString(auth.getName());
        WorkspaceListing listing = svc.findForUser(userId);

        WorkspaceMeResponse.WorkspaceRef dept = listing.department()
            .map(d -> new WorkspaceMeResponse.WorkspaceRef(
                d.kind(), d.id(), d.name(), d.rootFolderId()))
            .orElse(null);

        List<WorkspaceMeResponse.WorkspaceRef> teams = listing.teams().stream()
            .map(t -> new WorkspaceMeResponse.WorkspaceRef(
                t.kind(), t.id(), t.name(), t.rootFolderId()))
            .toList();

        return new WorkspaceMeResponse(dept, teams);
    }
}
```

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```
git add backend/src/main/java/com/ibizdrive/workspace/WorkspaceController.java \
        backend/src/main/java/com/ibizdrive/workspace/dto/WorkspaceMeResponse.java \
        backend/src/test/java/com/ibizdrive/workspace/WorkspaceControllerIntegrationTest.java
git commit -m "feat(team-centric-pivot): GET /api/workspaces/me"
```

---

## Phase 6 — Team CRUD

### Task 16: `TeamService.create(name, description, visibility, creatorId)`

Creates Team + initial OWNER membership + root Folder, all in one transaction. Audit `team.created` + `team.member.added`.

**Files:**
- Create: `backend/src/main/java/com/ibizdrive/team/TeamService.java`
- Create: `backend/src/main/java/com/ibizdrive/team/TeamCreatedEvent.java`
- Create: `backend/src/main/java/com/ibizdrive/team/TeamNameConflictException.java`
- Test: `backend/src/test/java/com/ibizdrive/team/TeamServiceCreateTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.ibizdrive.team;

import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.folder.ScopeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TeamServiceCreateTest {

    @Autowired TeamService svc;
    @Autowired TeamRepository teamRepo;
    @Autowired TeamMembershipRepository memRepo;
    @Autowired FolderRepository folderRepo;

    @Test
    void createPersistsTeamMembershipAndRootFolder() {
        UUID creator = TestUserSeed.persistUser("c@t");
        Team t = svc.create("Alpha", null, Team.Visibility.PRIVATE, creator);

        assertThat(teamRepo.findById(t.getId())).isPresent();
        assertThat(memRepo.findByTeamId(t.getId())).hasSize(1)
            .first().extracting(TeamMembership::getRole)
            .isEqualTo(TeamMembership.Role.OWNER);
        Folder root = folderRepo.findById(t.getRootFolderId()).orElseThrow();
        assertThat(root.getParentId()).isNull();
        assertThat(root.getScopeType()).isEqualTo(ScopeType.TEAM);
        assertThat(root.getScopeId()).isEqualTo(t.getId());
    }

    @Test
    void duplicateActiveNameRejected() {
        UUID creator = TestUserSeed.persistUser("d@t");
        svc.create("Beta", null, Team.Visibility.PRIVATE, creator);
        assertThatThrownBy(() ->
            svc.create("Beta", null, Team.Visibility.PRIVATE, creator))
            .isInstanceOf(TeamNameConflictException.class);
    }
}
```

(`TestUserSeed` is a small helper in test sources that inserts a user row via JdbcTemplate — see Task 16b.)

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: Implement TeamService.create + supporting types**

```java
// TeamNameConflictException.java
package com.ibizdrive.team;

public class TeamNameConflictException extends RuntimeException {
    public TeamNameConflictException(String name) {
        super("active team with name already exists: " + name);
    }
}
```

```java
// TeamCreatedEvent.java
package com.ibizdrive.team;

import java.util.UUID;

public record TeamCreatedEvent(UUID teamId, UUID createdBy, String name) {}
```

```java
// TeamService.java
package com.ibizdrive.team;

import com.ibizdrive.common.normalize.NameNormalizer;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.folder.ScopeType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class TeamService {

    private final TeamRepository teamRepo;
    private final TeamMembershipRepository memRepo;
    private final FolderRepository folderRepo;
    private final NameNormalizer normalizer;
    private final ApplicationEventPublisher events;

    public TeamService(TeamRepository teamRepo, TeamMembershipRepository memRepo,
                       FolderRepository folderRepo, NameNormalizer normalizer,
                       ApplicationEventPublisher events) {
        this.teamRepo = teamRepo;
        this.memRepo = memRepo;
        this.folderRepo = folderRepo;
        this.normalizer = normalizer;
        this.events = events;
    }

    @Transactional
    public Team create(String name, String description,
                       Team.Visibility visibility, UUID creatorId) {
        String normalized = normalizer.normalizeForDedup(name);
        teamRepo.findActiveByNormalizedName(normalized)
            .ifPresent(existing -> { throw new TeamNameConflictException(name); });

        OffsetDateTime now = OffsetDateTime.now();
        UUID teamId = UUID.randomUUID();
        Team t = new Team(teamId, name.trim(), normalized, description,
            visibility, creatorId, now);
        teamRepo.save(t);

        // Root folder — same transaction
        UUID rootFolderId = UUID.randomUUID();
        Folder root = newRootFolder(rootFolderId, name.trim(), normalized,
            ScopeType.TEAM, teamId, creatorId);
        folderRepo.save(root);
        t.attachRootFolder(rootFolderId);

        // Initial OWNER membership
        memRepo.save(new TeamMembership(teamId, creatorId,
            TeamMembership.Role.OWNER, null, now));

        events.publishEvent(new TeamCreatedEvent(teamId, creatorId, name.trim()));
        return t;
    }

    private Folder newRootFolder(UUID id, String name, String normalized,
                                 ScopeType scopeType, UUID scopeId, UUID owner) {
        Folder f = new Folder();
        // Folder's existing constructor or setters — adapt to actual entity API
        f.assignNew(id, /*parentId*/ null, name, normalized, /*slug*/ normalized,
            owner, /*auditLevel*/ "standard", Instant.now());
        f.assignScope(scopeType, scopeId);
        return f;
    }
}
```

> **Note for implementer**: `Folder.assignNew(...)` may not exist. Inspect `Folder.java` and either add an `assignNew(...)` factory method or set fields directly via existing setters. Same applies to `FolderRepository.save(...)` ensuring all NOT NULL columns are filled (`audit_level`, `created_at`, `updated_at`).

- [ ] **Step 4: Add `TestUserSeed` helper**

```java
// backend/src/test/java/com/ibizdrive/team/TestUserSeed.java
package com.ibizdrive.team;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TestUserSeed {

    private static JdbcTemplate jdbc;

    @Autowired
    public TestUserSeed(JdbcTemplate jdbc) { TestUserSeed.jdbc = jdbc; }

    public static UUID persistUser(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO users(id,email,is_active,created_at) VALUES (?,?,TRUE,NOW())",
            id, email);
        return id;
    }
}
```

(Or use a simpler JUnit 5 BeforeEach to inject `JdbcTemplate` directly.)

- [ ] **Step 5: Run — expect PASS**

- [ ] **Step 6: Commit**

```
git add backend/src/main/java/com/ibizdrive/team/TeamService.java \
        backend/src/main/java/com/ibizdrive/team/TeamCreatedEvent.java \
        backend/src/main/java/com/ibizdrive/team/TeamNameConflictException.java \
        backend/src/test/java/com/ibizdrive/team/TeamServiceCreateTest.java \
        backend/src/test/java/com/ibizdrive/team/TestUserSeed.java
git commit -m "feat(team-centric-pivot): TeamService.create — team + OWNER + root folder transaction"
```

---

### Task 17: `TeamService.invite(teamId, userId, invitedBy)`

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/team/TeamService.java`
- Create: `backend/src/main/java/com/ibizdrive/team/TeamMemberAddedEvent.java`
- Test: `backend/src/test/java/com/ibizdrive/team/TeamServiceInviteTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.ibizdrive.team;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest @ActiveProfiles("test") @Transactional
class TeamServiceInviteTest {

    @Autowired TeamService svc;
    @Autowired TeamMembershipRepository memRepo;

    @Test
    void inviteAddsMemberRow() {
        UUID owner = TestUserSeed.persistUser("o@t");
        UUID invitee = TestUserSeed.persistUser("i@t");
        Team t = svc.create("X", null, Team.Visibility.PRIVATE, owner);
        svc.invite(t.getId(), invitee, owner);
        assertThat(memRepo.findByTeamId(t.getId())).hasSize(2);
    }

    @Test
    void inviteIdempotentForExistingMember() {
        UUID owner = TestUserSeed.persistUser("oo@t");
        Team t = svc.create("Y", null, Team.Visibility.PRIVATE, owner);
        // re-invite same OWNER — should not throw
        svc.invite(t.getId(), owner, owner);
        assertThat(memRepo.findByTeamId(t.getId())).hasSize(1);
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: Add `invite` to `TeamService` + event**

```java
// TeamMemberAddedEvent.java
package com.ibizdrive.team;

import java.util.UUID;
public record TeamMemberAddedEvent(UUID teamId, UUID userId, UUID invitedBy) {}
```

In `TeamService.java` add:

```java
    @Transactional
    public TeamMembership invite(UUID teamId, UUID userId, UUID invitedBy) {
        TeamMembershipId id = new TeamMembershipId(teamId, userId);
        if (memRepo.existsById(id)) {
            return memRepo.findById(id).orElseThrow();
        }
        TeamMembership m = new TeamMembership(teamId, userId,
            TeamMembership.Role.MEMBER, invitedBy, java.time.OffsetDateTime.now());
        memRepo.save(m);
        events.publishEvent(new TeamMemberAddedEvent(teamId, userId, invitedBy));
        return m;
    }
```

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```
git add backend/src/main/java/com/ibizdrive/team/TeamService.java \
        backend/src/main/java/com/ibizdrive/team/TeamMemberAddedEvent.java \
        backend/src/test/java/com/ibizdrive/team/TeamServiceInviteTest.java
git commit -m "feat(team-centric-pivot): TeamService.invite (idempotent)"
```

---

### Task 18: `TeamService.remove(teamId, userId, actorId)` — basic remove (no last-OWNER guard yet)

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/team/TeamService.java`
- Create: `backend/src/main/java/com/ibizdrive/team/TeamMemberRemovedEvent.java`
- Test: `backend/src/test/java/com/ibizdrive/team/TeamServiceRemoveTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.ibizdrive.team;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest @ActiveProfiles("test") @Transactional
class TeamServiceRemoveTest {

    @Autowired TeamService svc;
    @Autowired TeamMembershipRepository memRepo;

    @Test
    void removeDeletesMembership() {
        UUID owner = TestUserSeed.persistUser("o3@t");
        UUID member = TestUserSeed.persistUser("m3@t");
        Team t = svc.create("Z", null, Team.Visibility.PRIVATE, owner);
        svc.invite(t.getId(), member, owner);
        svc.remove(t.getId(), member, owner);
        assertThat(memRepo.findByTeamId(t.getId())).hasSize(1);
    }
}
```

> Last-OWNER guard + role-change deferred to Plan A2.

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: Add `remove` to TeamService**

```java
// TeamMemberRemovedEvent.java
package com.ibizdrive.team;

import java.util.UUID;
public record TeamMemberRemovedEvent(UUID teamId, UUID userId, UUID removedBy) {}
```

In `TeamService.java`:

```java
    @Transactional
    public void remove(UUID teamId, UUID userId, UUID actorId) {
        TeamMembershipId id = new TeamMembershipId(teamId, userId);
        if (!memRepo.existsById(id)) return;
        memRepo.deleteById(id);
        events.publishEvent(new TeamMemberRemovedEvent(teamId, userId, actorId));
    }
```

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```
git add backend/src/main/java/com/ibizdrive/team/TeamService.java \
        backend/src/main/java/com/ibizdrive/team/TeamMemberRemovedEvent.java \
        backend/src/test/java/com/ibizdrive/team/TeamServiceRemoveTest.java
git commit -m "feat(team-centric-pivot): TeamService.remove (basic)"
```

---

### Task 19: `TeamController` — POST/GET endpoints + member endpoints

**Files:**
- Create: `backend/src/main/java/com/ibizdrive/team/TeamController.java`
- Create: `backend/src/main/java/com/ibizdrive/team/dto/TeamCreateRequest.java`
- Create: `backend/src/main/java/com/ibizdrive/team/dto/TeamResponse.java`
- Create: `backend/src/main/java/com/ibizdrive/team/dto/TeamMemberInviteRequest.java`
- Test: `backend/src/test/java/com/ibizdrive/team/TeamControllerIntegrationTest.java`

- [ ] **Step 1: Write the failing integration test**

```java
package com.ibizdrive.team;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc
class TeamControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000099")
    void createTeamReturns201() throws Exception {
        // Seed a real user with the mocked id
        TestUserSeed.persistUserWithId(
            UUID.fromString("00000000-0000-0000-0000-000000000099"), "u99@t");

        var body = json.writeValueAsString(java.util.Map.of(
            "name", "MyTeam",
            "visibility", "private"));
        mvc.perform(post("/api/teams").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.rootFolderId").exists());
    }
}
```

(Add `TestUserSeed.persistUserWithId(UUID, String)` overload.)

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: Implement DTOs + controller**

```java
// TeamCreateRequest.java
package com.ibizdrive.team.dto;

import com.ibizdrive.team.Team;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeamCreateRequest(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 1000) String description,
    Team.Visibility visibility) {}
```

```java
// TeamResponse.java
package com.ibizdrive.team.dto;

import com.ibizdrive.team.Team;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TeamResponse(UUID id, String name, String description,
                           Team.Visibility visibility, UUID rootFolderId,
                           OffsetDateTime createdAt, OffsetDateTime archivedAt) {

    public static TeamResponse of(Team t) {
        return new TeamResponse(t.getId(), t.getName(), t.getDescription(),
            t.getVisibility(), t.getRootFolderId(), t.getCreatedAt(), t.getArchivedAt());
    }
}
```

```java
// TeamMemberInviteRequest.java
package com.ibizdrive.team.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record TeamMemberInviteRequest(@NotNull UUID userId) {}
```

```java
// TeamController.java
package com.ibizdrive.team;

import com.ibizdrive.team.dto.TeamCreateRequest;
import com.ibizdrive.team.dto.TeamMemberInviteRequest;
import com.ibizdrive.team.dto.TeamResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService svc;

    public TeamController(TeamService svc) { this.svc = svc; }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TeamResponse> create(@Valid @RequestBody TeamCreateRequest req) {
        UUID actor = currentUserId();
        Team t = svc.create(req.name(), req.description(),
            req.visibility() == null ? Team.Visibility.PRIVATE : req.visibility(), actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(TeamResponse.of(t));
    }

    @PostMapping("/{teamId}/members")
    @PreAuthorize("@teamAuthz.isOwner(#teamId, principal)")
    public ResponseEntity<Void> invite(@PathVariable UUID teamId,
                                       @Valid @RequestBody TeamMemberInviteRequest req) {
        svc.invite(teamId, req.userId(), currentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{teamId}/members/{userId}")
    @PreAuthorize("@teamAuthz.isOwnerOrSelf(#teamId, #userId, principal)")
    public ResponseEntity<Void> remove(@PathVariable UUID teamId, @PathVariable UUID userId) {
        svc.remove(teamId, userId, currentUserId());
        return ResponseEntity.noContent().build();
    }

    private UUID currentUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
```

```java
// TeamAuthz.java
package com.ibizdrive.team;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("teamAuthz")
public class TeamAuthz {

    private final TeamMembershipRepository memRepo;

    public TeamAuthz(TeamMembershipRepository memRepo) { this.memRepo = memRepo; }

    public boolean isOwner(UUID teamId, Object principal) {
        UUID userId = principalUserId(principal);
        return memRepo.findById(new TeamMembershipId(teamId, userId))
            .map(m -> m.getRole() == TeamMembership.Role.OWNER)
            .orElse(false);
    }

    public boolean isOwnerOrSelf(UUID teamId, UUID userId, Object principal) {
        UUID actor = principalUserId(principal);
        if (actor.equals(userId)) return true;
        return isOwner(teamId, principal);
    }

    private UUID principalUserId(Object principal) {
        if (principal instanceof UserDetails ud) return UUID.fromString(ud.getUsername());
        if (principal instanceof String s) return UUID.fromString(s);
        throw new IllegalStateException("unsupported principal: " + principal);
    }
}
```

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```
git add backend/src/main/java/com/ibizdrive/team/TeamController.java \
        backend/src/main/java/com/ibizdrive/team/TeamAuthz.java \
        backend/src/main/java/com/ibizdrive/team/dto/ \
        backend/src/test/java/com/ibizdrive/team/TeamControllerIntegrationTest.java
git commit -m "feat(team-centric-pivot): TeamController POST + member endpoints"
```

---

## Phase 7 — Department Root Folder Hook

### Task 20: Extend `AdminDepartmentService.create()` to produce root folder

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/admin/AdminDepartmentService.java`
- Test: `backend/src/test/java/com/ibizdrive/admin/AdminDepartmentRootFolderTest.java`

- [ ] **Step 1: Read existing AdminDepartmentService.create()**

Inspect the method that creates a department and identify where to insert the root-folder creation step (must be inside the same `@Transactional`).

- [ ] **Step 2: Write the failing test**

```java
package com.ibizdrive.admin;

import com.ibizdrive.department.Department;
import com.ibizdrive.department.DepartmentRepository;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.folder.ScopeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest @ActiveProfiles("test") @Transactional
class AdminDepartmentRootFolderTest {

    @Autowired AdminDepartmentService svc;
    @Autowired DepartmentRepository deptRepo;
    @Autowired FolderRepository folderRepo;

    @Test
    void createDepartmentAlsoCreatesRootFolder() {
        UUID admin = com.ibizdrive.team.TestUserSeed.persistUser("adm@t");
        AdminDepartmentSummaryResponse resp = svc.create(new AdminDepartmentCreateRequest("Sales"), admin);
        Department d = deptRepo.findById(resp.id()).orElseThrow();
        assertThat(d.getRootFolderId()).isNotNull();
        Folder root = folderRepo.findById(d.getRootFolderId()).orElseThrow();
        assertThat(root.getParentId()).isNull();
        assertThat(root.getScopeType()).isEqualTo(ScopeType.DEPARTMENT);
        assertThat(root.getScopeId()).isEqualTo(d.getId());
    }
}
```

- [ ] **Step 3: Run — expect FAIL**

- [ ] **Step 4: Modify AdminDepartmentService.create() to create + attach root folder**

Inside the existing `create(...)` transaction, after persisting the `Department`, insert:

```java
        UUID rootFolderId = UUID.randomUUID();
        String normalized = normalizer.normalizeForDedup(req.name());
        Folder root = new Folder();
        root.assignNew(rootFolderId, /*parentId*/ null, req.name().trim(), normalized,
            normalized, adminId, "standard", java.time.Instant.now());
        root.assignScope(ScopeType.DEPARTMENT, dept.getId());
        folderRepo.save(root);
        dept.attachRootFolder(rootFolderId);
```

(Adjust `assignNew` calls to whatever Folder API exists. Inject `FolderRepository` and `NameNormalizer`.)

- [ ] **Step 5: Run — expect PASS**

- [ ] **Step 6: Commit**

```
git add backend/src/main/java/com/ibizdrive/admin/AdminDepartmentService.java \
        backend/src/test/java/com/ibizdrive/admin/AdminDepartmentRootFolderTest.java
git commit -m "feat(team-centric-pivot): department creation auto-creates root folder"
```

---

### Task 21: One-time data migration — backfill root folder for existing departments

> Only relevant if the deployed DB contains departments without root_folder_id (e.g. dev/test environments mid-pivot). Production cutover is green-field per spec §6.1, but this idempotent backfill protects local dev.

**Files:**
- Create: `backend/src/main/java/com/ibizdrive/admin/DepartmentRootFolderBackfillRunner.java`
- Test: `backend/src/test/java/com/ibizdrive/admin/DepartmentRootFolderBackfillTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.ibizdrive.admin;

import com.ibizdrive.department.Department;
import com.ibizdrive.department.DepartmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest @ActiveProfiles("test") @Transactional
class DepartmentRootFolderBackfillTest {

    @Autowired DepartmentRootFolderBackfillRunner runner;
    @Autowired DepartmentRepository deptRepo;

    @Test
    void backfillCreatesRootForExistingActiveDepartmentWithoutRoot() {
        UUID admin = com.ibizdrive.team.TestUserSeed.persistUser("ba@t");
        Department d = deptRepo.save(new Department(UUID.randomUUID(), "Legacy",
            java.time.OffsetDateTime.now()));
        runner.run(admin);
        Department reloaded = deptRepo.findById(d.getId()).orElseThrow();
        assertThat(reloaded.getRootFolderId()).isNotNull();
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: Implement backfill runner**

```java
package com.ibizdrive.admin;

import com.ibizdrive.department.Department;
import com.ibizdrive.department.DepartmentRepository;
import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.folder.ScopeType;
import com.ibizdrive.common.normalize.NameNormalizer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class DepartmentRootFolderBackfillRunner {

    private final DepartmentRepository deptRepo;
    private final FolderRepository folderRepo;
    private final NameNormalizer normalizer;

    public DepartmentRootFolderBackfillRunner(DepartmentRepository deptRepo,
                                              FolderRepository folderRepo,
                                              NameNormalizer normalizer) {
        this.deptRepo = deptRepo;
        this.folderRepo = folderRepo;
        this.normalizer = normalizer;
    }

    @Transactional
    public int run(UUID createdBy) {
        int created = 0;
        for (Department d : deptRepo.findAll()) {
            if (!d.isActive() || d.getRootFolderId() != null) continue;
            UUID rootId = UUID.randomUUID();
            String norm = normalizer.normalizeForDedup(d.getName());
            Folder root = new Folder();
            root.assignNew(rootId, null, d.getName(), norm, norm, createdBy,
                "standard", Instant.now());
            root.assignScope(ScopeType.DEPARTMENT, d.getId());
            folderRepo.save(root);
            d.attachRootFolder(rootId);
            created++;
        }
        return created;
    }
}
```

> Run manually via admin tool or as a startup `ApplicationRunner` gated by a config flag. Do NOT auto-run in production — green-field cutover (spec §6.1).

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```
git add backend/src/main/java/com/ibizdrive/admin/DepartmentRootFolderBackfillRunner.java \
        backend/src/test/java/com/ibizdrive/admin/DepartmentRootFolderBackfillTest.java
git commit -m "feat(team-centric-pivot): department root folder backfill (dev tool, idempotent)"
```

---

## Phase 8 — Permission Evaluation Rewrite (Membership Step)

### Task 22: `WorkspaceMembershipResolver` — default permissions from membership

**Files:**
- Create: `backend/src/main/java/com/ibizdrive/permission/WorkspaceMembershipResolver.java`
- Test: `backend/src/test/java/com/ibizdrive/permission/WorkspaceMembershipResolverTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.ibizdrive.permission;

import com.ibizdrive.folder.ScopeType;
import com.ibizdrive.team.TeamMembership;
import com.ibizdrive.team.TeamMembershipId;
import com.ibizdrive.team.TeamMembershipRepository;
import com.ibizdrive.workspace.UserDepartmentLookup;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class WorkspaceMembershipResolverTest {

    @Test
    void departmentMemberGetsReadAndUpload() {
        UUID userId = UUID.randomUUID();
        UUID dept = UUID.randomUUID();
        UserDepartmentLookup lookup = Mockito.mock(UserDepartmentLookup.class);
        TeamMembershipRepository memRepo = Mockito.mock(TeamMembershipRepository.class);
        when(lookup.departmentIdOf(userId)).thenReturn(Optional.of(dept));

        WorkspaceMembershipResolver resolver =
            new WorkspaceMembershipResolver(lookup, memRepo);
        Set<Permission> perms = resolver.resolve(userId, ScopeType.DEPARTMENT, dept);
        assertThat(perms).contains(Permission.READ, Permission.UPLOAD);
    }

    @Test
    void teamMemberGetsReadUploadEdit() {
        UUID userId = UUID.randomUUID();
        UUID team = UUID.randomUUID();
        UserDepartmentLookup lookup = Mockito.mock(UserDepartmentLookup.class);
        TeamMembershipRepository memRepo = Mockito.mock(TeamMembershipRepository.class);
        when(memRepo.findById(new TeamMembershipId(team, userId))).thenReturn(
            Optional.of(new TeamMembership(team, userId, TeamMembership.Role.MEMBER,
                null, java.time.OffsetDateTime.now())));

        WorkspaceMembershipResolver resolver =
            new WorkspaceMembershipResolver(lookup, memRepo);
        Set<Permission> perms = resolver.resolve(userId, ScopeType.TEAM, team);
        assertThat(perms).contains(Permission.READ, Permission.UPLOAD, Permission.EDIT);
    }

    @Test
    void teamOwnerGetsAdmin() {
        UUID userId = UUID.randomUUID();
        UUID team = UUID.randomUUID();
        UserDepartmentLookup lookup = Mockito.mock(UserDepartmentLookup.class);
        TeamMembershipRepository memRepo = Mockito.mock(TeamMembershipRepository.class);
        when(memRepo.findById(new TeamMembershipId(team, userId))).thenReturn(
            Optional.of(new TeamMembership(team, userId, TeamMembership.Role.OWNER,
                null, java.time.OffsetDateTime.now())));

        WorkspaceMembershipResolver resolver =
            new WorkspaceMembershipResolver(lookup, memRepo);
        Set<Permission> perms = resolver.resolve(userId, ScopeType.TEAM, team);
        assertThat(perms).contains(Permission.READ, Permission.UPLOAD,
            Permission.EDIT, Permission.DELETE, Permission.SHARE);
    }

    @Test
    void nonMemberGetsEmpty() {
        UUID userId = UUID.randomUUID();
        UUID team = UUID.randomUUID();
        UserDepartmentLookup lookup = Mockito.mock(UserDepartmentLookup.class);
        TeamMembershipRepository memRepo = Mockito.mock(TeamMembershipRepository.class);
        when(memRepo.findById(new TeamMembershipId(team, userId))).thenReturn(Optional.empty());

        WorkspaceMembershipResolver resolver =
            new WorkspaceMembershipResolver(lookup, memRepo);
        assertThat(resolver.resolve(userId, ScopeType.TEAM, team)).isEmpty();
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: Implement resolver**

```java
package com.ibizdrive.permission;

import com.ibizdrive.folder.ScopeType;
import com.ibizdrive.team.TeamMembership;
import com.ibizdrive.team.TeamMembershipId;
import com.ibizdrive.team.TeamMembershipRepository;
import com.ibizdrive.workspace.UserDepartmentLookup;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Spec §3.2 — workspace 멤버십을 묵시적 권한으로 변환.
 * - department 멤버: READ + UPLOAD
 * - team MEMBER: READ + UPLOAD + EDIT
 * - team OWNER: READ + UPLOAD + EDIT + DELETE + SHARE
 * 비멤버: 빈 집합 (default deny — explicit/share step에서 보강).
 */
@Component
public class WorkspaceMembershipResolver {

    private final UserDepartmentLookup userDeptLookup;
    private final TeamMembershipRepository memRepo;

    public WorkspaceMembershipResolver(UserDepartmentLookup userDeptLookup,
                                       TeamMembershipRepository memRepo) {
        this.userDeptLookup = userDeptLookup;
        this.memRepo = memRepo;
    }

    public Set<Permission> resolve(UUID userId, ScopeType scopeType, UUID scopeId) {
        return switch (scopeType) {
            case DEPARTMENT -> userDeptLookup.departmentIdOf(userId)
                .filter(id -> id.equals(scopeId))
                .<Set<Permission>>map(id -> EnumSet.of(Permission.READ, Permission.UPLOAD))
                .orElseGet(() -> EnumSet.noneOf(Permission.class));
            case TEAM -> memRepo.findById(new TeamMembershipId(scopeId, userId))
                .<Set<Permission>>map(this::permsForRole)
                .orElseGet(() -> EnumSet.noneOf(Permission.class));
        };
    }

    private Set<Permission> permsForRole(TeamMembership m) {
        return switch (m.getRole()) {
            case OWNER -> EnumSet.of(Permission.READ, Permission.UPLOAD,
                Permission.EDIT, Permission.DELETE, Permission.SHARE);
            case MEMBER -> EnumSet.of(Permission.READ, Permission.UPLOAD, Permission.EDIT);
        };
    }
}
```

> Note: `Permission` enum already exists at `com.ibizdrive.permission.Permission`. Confirm constant names (`READ/UPLOAD/EDIT/DELETE/SHARE`) match spec §3.2 — adapt the test if names differ.

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```
git add backend/src/main/java/com/ibizdrive/permission/WorkspaceMembershipResolver.java \
        backend/src/test/java/com/ibizdrive/permission/WorkspaceMembershipResolverTest.java
git commit -m "feat(team-centric-pivot): WorkspaceMembershipResolver (membership → default perms)"
```

---

### Task 23: Wire `WorkspaceMembershipResolver` into `PermissionResolver`

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/permission/PermissionResolver.java`
- Test: `backend/src/test/java/com/ibizdrive/permission/PermissionResolverMembershipStepTest.java`

- [ ] **Step 1: Read existing PermissionResolver to understand current evaluation flow**

Note the method signatures and how explicit permissions + shares are aggregated today. The new step must run BEFORE explicit permissions (spec §3.1 ordering) and union into the final permission set.

- [ ] **Step 2: Write the failing test**

```java
package com.ibizdrive.permission;

import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.folder.ScopeType;
import com.ibizdrive.team.Team;
import com.ibizdrive.team.TeamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest @ActiveProfiles("test") @Transactional
class PermissionResolverMembershipStepTest {

    @Autowired TeamService teamSvc;
    @Autowired FolderRepository folderRepo;
    @Autowired PermissionResolver resolver;

    @Test
    void teamMemberHasReadOnTeamFolderViaMembership() {
        UUID owner = com.ibizdrive.team.TestUserSeed.persistUser("po@t");
        Team t = teamSvc.create("PermTest", null, Team.Visibility.PRIVATE, owner);
        Folder root = folderRepo.findById(t.getRootFolderId()).orElseThrow();
        Set<Permission> perms = resolver.findEffective(root, owner);
        assertThat(perms).contains(Permission.READ, Permission.UPLOAD,
            Permission.EDIT, Permission.DELETE, Permission.SHARE);
    }
}
```

- [ ] **Step 3: Run — expect FAIL** (current resolver returns empty without explicit grant)

- [ ] **Step 4: Modify PermissionResolver to add membership step**

Inject `WorkspaceMembershipResolver` and union its result with the existing explicit + shares result. The folder/file already carries `scopeType`/`scopeId` (Phase 2 fields).

```java
// Pseudo-diff
public Set<Permission> findEffective(Folder folder, UUID userId) {
    Set<Permission> result = EnumSet.noneOf(Permission.class);
    result.addAll(membershipResolver.resolve(userId, folder.getScopeType(), folder.getScopeId()));
    result.addAll(existingExplicitPermissionLookup(folder, userId));
    result.addAll(existingShareLookup(folder, userId));
    return result;
}
```

Add the equivalent for `FileItem`.

- [ ] **Step 5: Run — expect PASS**

```
cd backend && ./gradlew test --tests "com.ibizdrive.permission.PermissionResolverMembershipStepTest"
```

- [ ] **Step 6: Run all tests to detect regressions**

```
cd backend && ./gradlew test
```

If any existing tests broke — they're asserting the old "empty without grant" behavior. Update them to seed appropriate department membership OR add explicit grant. Do not weaken the new behavior.

- [ ] **Step 7: Commit**

```
git add backend/src/main/java/com/ibizdrive/permission/PermissionResolver.java \
        backend/src/test/java/com/ibizdrive/permission/PermissionResolverMembershipStepTest.java \
        $(git diff --name-only --diff-filter=M backend/src/test)
git commit -m "feat(team-centric-pivot): PermissionResolver — workspace membership step"
```

---

## Phase 9 — Folder/File CRUD Scope Inheritance

### Task 24: `FolderMutationService.create()` — inherit scope from parent

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/folder/FolderMutationService.java`
- Test: `backend/src/test/java/com/ibizdrive/folder/FolderCreateScopeInheritanceTest.java`

- [ ] **Step 1: Locate existing create() method in FolderMutationService**

Read the relevant lines in `FolderMutationService.java` (file is ~550 lines).

- [ ] **Step 2: Write the failing test**

```java
package com.ibizdrive.folder;

import com.ibizdrive.team.Team;
import com.ibizdrive.team.TeamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest @ActiveProfiles("test") @Transactional
class FolderCreateScopeInheritanceTest {

    @Autowired TeamService teamSvc;
    @Autowired FolderMutationService mut;
    @Autowired FolderRepository folderRepo;

    @Test
    void childInheritsScopeFromParent() {
        UUID owner = com.ibizdrive.team.TestUserSeed.persistUser("ci@t");
        Team t = teamSvc.create("InhTest", null, Team.Visibility.PRIVATE, owner);
        UUID rootId = t.getRootFolderId();

        // Whatever the existing create signature is — adapt this call:
        Folder child = mut.createChild(rootId, "subA", owner);

        assertThat(child.getScopeType()).isEqualTo(ScopeType.TEAM);
        assertThat(child.getScopeId()).isEqualTo(t.getId());
    }

    @Test
    void rootCreationViaApiRejected() {
        UUID owner = com.ibizdrive.team.TestUserSeed.persistUser("rc@t");
        // parent_id 없이 일반 API에서 root 생성 시도 — 차단
        assertThatThrownBy(() -> mut.createChild(null, "shouldFail", owner))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 3: Run — expect FAIL**

- [ ] **Step 4: Modify create method**

In `FolderMutationService.createChild(...)` (or whatever the method is called for non-root creation):

```java
    @Transactional
    public Folder createChild(UUID parentId, String name, UUID createdBy) {
        if (parentId == null) {
            throw new IllegalArgumentException(
                "parent_id required — root folders are created by workspace lifecycle only (spec §1.3)");
        }
        Folder parent = folderRepo.findById(parentId)
            .orElseThrow(() -> new FolderNotFoundException(parentId));
        // ... existing logic to insert child folder ...
        Folder child = /* existing build */;
        child.assignScope(parent.getScopeType(), parent.getScopeId());
        folderRepo.save(child);
        return child;
    }
```

- [ ] **Step 5: Run — expect PASS**

- [ ] **Step 6: Commit**

```
git add backend/src/main/java/com/ibizdrive/folder/FolderMutationService.java \
        backend/src/test/java/com/ibizdrive/folder/FolderCreateScopeInheritanceTest.java
git commit -m "feat(team-centric-pivot): folder.create inherits scope from parent + blocks root via API"
```

---

### Task 25: `FolderMutationService.move()` — same-scope validation

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/folder/FolderMutationService.java`
- Test: `backend/src/test/java/com/ibizdrive/folder/FolderMoveSameScopeTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.ibizdrive.folder;

import com.ibizdrive.team.Team;
import com.ibizdrive.team.TeamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest @ActiveProfiles("test") @Transactional
class FolderMoveSameScopeTest {

    @Autowired TeamService teamSvc;
    @Autowired FolderMutationService mut;

    @Test
    void crossWorkspaceMoveRejected() {
        UUID a = com.ibizdrive.team.TestUserSeed.persistUser("mva@t");
        UUID b = com.ibizdrive.team.TestUserSeed.persistUser("mvb@t");
        Team teamA = teamSvc.create("Move-A", null, Team.Visibility.PRIVATE, a);
        Team teamB = teamSvc.create("Move-B", null, Team.Visibility.PRIVATE, b);

        Folder childInA = mut.createChild(teamA.getRootFolderId(), "subA", a);

        assertThatThrownBy(() ->
            mut.move(childInA.getId(), teamB.getRootFolderId(), a))
            .isInstanceOf(CrossScopeMoveException.class);
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: Add CrossScopeMoveException + same-scope guard**

```java
// CrossScopeMoveException.java
package com.ibizdrive.folder;

public class CrossScopeMoveException extends RuntimeException {
    public CrossScopeMoveException() {
        super("ERR_CROSS_SCOPE_MOVE — cross-workspace move requires explicit cross-scope action (spec §5.6)");
    }
}
```

In `FolderMutationService.move(folderId, newParentId, actor)`:

```java
        Folder folder = folderRepo.findById(folderId).orElseThrow(...);
        Folder newParent = folderRepo.findById(newParentId).orElseThrow(...);
        if (folder.getScopeType() != newParent.getScopeType()
            || !folder.getScopeId().equals(newParent.getScopeId())) {
            throw new CrossScopeMoveException();
        }
        // existing move logic ...
```

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```
git add backend/src/main/java/com/ibizdrive/folder/FolderMutationService.java \
        backend/src/main/java/com/ibizdrive/folder/CrossScopeMoveException.java \
        backend/src/test/java/com/ibizdrive/folder/FolderMoveSameScopeTest.java
git commit -m "feat(team-centric-pivot): folder.move same-scope validation (ERR_CROSS_SCOPE_MOVE)"
```

---

### Task 26: File creation/upload inherits scope

**Files:**
- Modify: file service that handles upload completion (likely `backend/src/main/java/com/ibizdrive/file/FileService.java` or similar — locate)
- Modify: `backend/src/main/java/com/ibizdrive/file/FileItem.java` (if not done in Task 8)
- Test: `backend/src/test/java/com/ibizdrive/file/FileScopeInheritanceTest.java`

- [ ] **Step 1: Locate the upload-completion service**

Look at `backend/src/main/java/com/ibizdrive/file/` — find the method that creates a FileItem given a parent folder.

- [ ] **Step 2: Write the failing test (mirror folder test)**

```java
package com.ibizdrive.file;

import com.ibizdrive.folder.ScopeType;
import com.ibizdrive.team.Team;
import com.ibizdrive.team.TeamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest @ActiveProfiles("test") @Transactional
class FileScopeInheritanceTest {

    @Autowired TeamService teamSvc;
    @Autowired /*FileService or upload service*/ /*FILL_IN*/ fileSvc;

    @Test
    void uploadedFileInheritsParentScope() {
        UUID owner = com.ibizdrive.team.TestUserSeed.persistUser("fi@t");
        Team t = teamSvc.create("FileTest", null, Team.Visibility.PRIVATE, owner);

        // Adapt to actual upload API:
        FileItem f = fileSvc.completeUploadStub(t.getRootFolderId(), "doc.txt", owner);

        assertThat(f.getScopeType()).isEqualTo(ScopeType.TEAM);
        assertThat(f.getScopeId()).isEqualTo(t.getId());
    }
}
```

- [ ] **Step 3: Run — expect FAIL**

- [ ] **Step 4: In the file-creation method, add scope inheritance from parent folder**

```java
        Folder parent = folderRepo.findById(parentFolderId).orElseThrow(...);
        // ...existing FileItem build...
        fileItem.assignScope(parent.getScopeType(), parent.getScopeId());
```

- [ ] **Step 5: Run — expect PASS**

- [ ] **Step 6: Commit**

```
git add backend/src/main/java/com/ibizdrive/file/ \
        backend/src/test/java/com/ibizdrive/file/FileScopeInheritanceTest.java
git commit -m "feat(team-centric-pivot): file upload inherits parent folder scope"
```

---

### Task 27: Folder/File response DTOs include `scope`

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/folder/dto/FolderResponse.java` (or whatever the response DTO is — locate)
- Modify: `backend/src/main/java/com/ibizdrive/file/dto/FileResponse.java`
- Test: `backend/src/test/java/com/ibizdrive/folder/FolderResponseScopeTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.ibizdrive.folder;

import com.ibizdrive.folder.dto.FolderResponse;
import com.ibizdrive.team.Team;
import com.ibizdrive.team.TeamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest @ActiveProfiles("test") @Transactional
class FolderResponseScopeTest {

    @Autowired TeamService teamSvc;
    @Autowired FolderRepository folderRepo;

    @Test
    void responseIncludesScopeBlock() {
        UUID owner = com.ibizdrive.team.TestUserSeed.persistUser("rs@t");
        Team t = teamSvc.create("ResScope", null, Team.Visibility.PRIVATE, owner);
        Folder f = folderRepo.findById(t.getRootFolderId()).orElseThrow();
        FolderResponse r = FolderResponse.of(f, /*workspaceName*/ t.getName());
        assertThat(r.scope()).isNotNull();
        assertThat(r.scope().type()).isEqualTo("team");
        assertThat(r.scope().id()).isEqualTo(t.getId());
        assertThat(r.scope().name()).isEqualTo("ResScope");
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: Add scope block to FolderResponse + FileResponse**

```java
// In FolderResponse.java add nested ScopeRef record + field:
public record FolderResponse(/* existing fields */, ScopeRef scope) {

    public record ScopeRef(String type, UUID id, String name) {}

    public static FolderResponse of(Folder f, String workspaceName) {
        return new FolderResponse(/* existing args */,
            new ScopeRef(f.getScopeType().dbValue(), f.getScopeId(), workspaceName));
    }
}
```

Mirror in `FileResponse`.

> Workspace name lookup at the controller layer: cache via WorkspaceService for one batch.

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```
git add backend/src/main/java/com/ibizdrive/folder/dto/FolderResponse.java \
        backend/src/main/java/com/ibizdrive/file/dto/FileResponse.java \
        backend/src/test/java/com/ibizdrive/folder/FolderResponseScopeTest.java
git commit -m "feat(team-centric-pivot): Folder/File responses include scope block"
```

---

## Phase 10 — Audit Listener for Team Events

### Task 28: `TeamAuditListener` — convert team events into audit_log entries

**Files:**
- Create: `backend/src/main/java/com/ibizdrive/audit/TeamAuditListener.java`
- Test: `backend/src/test/java/com/ibizdrive/audit/TeamAuditListenerTest.java`

- [ ] **Step 1: Read existing PermissionAuditListener / ShareAuditListener for the pattern**

```
cat backend/src/main/java/com/ibizdrive/audit/ShareAuditListener.java
```

Mirror the `@TransactionalEventListener` + `AuditService.record(...)` invocation style.

- [ ] **Step 2: Write the failing test**

```java
package com.ibizdrive.audit;

import com.ibizdrive.team.Team;
import com.ibizdrive.team.TeamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest @ActiveProfiles("test")
class TeamAuditListenerTest {

    @Autowired TeamService svc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void teamCreatedRecordsAuditRow() {
        UUID owner = com.ibizdrive.team.TestUserSeed.persistUser("au@t");
        Team t = svc.create("AuditTest", null, Team.Visibility.PRIVATE, owner);
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_log WHERE event_type='team.created' AND target_id = ?",
            Long.class, t.getId());
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void teamMemberAddedRecorded() {
        UUID owner = com.ibizdrive.team.TestUserSeed.persistUser("au2@t");
        UUID member = com.ibizdrive.team.TestUserSeed.persistUser("au3@t");
        Team t = svc.create("AuditTest2", null, Team.Visibility.PRIVATE, owner);
        svc.invite(t.getId(), member, owner);
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_log WHERE event_type='team.member.added' AND target_id = ?",
            Long.class, t.getId());
        assertThat(count).isEqualTo(1L);
    }
}
```

(Test is non-`@Transactional` so the `@TransactionalEventListener(AFTER_COMMIT)` actually fires.)

- [ ] **Step 3: Run — expect FAIL**

- [ ] **Step 4: Implement listener (mirror ShareAuditListener style)**

```java
package com.ibizdrive.audit;

import com.ibizdrive.team.TeamCreatedEvent;
import com.ibizdrive.team.TeamMemberAddedEvent;
import com.ibizdrive.team.TeamMemberRemovedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Component
public class TeamAuditListener {

    private final AuditService audit;

    public TeamAuditListener(AuditService audit) { this.audit = audit; }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(TeamCreatedEvent e) {
        audit.record(AuditEventType.TEAM_CREATED, AuditTargetType.TEAM, e.teamId(),
            e.createdBy(), null, Map.of("name", e.name()), null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberAdded(TeamMemberAddedEvent e) {
        audit.record(AuditEventType.TEAM_MEMBER_ADDED, AuditTargetType.TEAM, e.teamId(),
            e.invitedBy(), null, Map.of("userId", e.userId().toString()), null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberRemoved(TeamMemberRemovedEvent e) {
        audit.record(AuditEventType.TEAM_MEMBER_REMOVED, AuditTargetType.TEAM, e.teamId(),
            e.removedBy(), null, Map.of("userId", e.userId().toString()), null);
    }
}
```

> Confirm the actual `AuditService.record(...)` signature in `AuditService.java` and adapt parameter order.

- [ ] **Step 5: Run — expect PASS**

- [ ] **Step 6: Commit**

```
git add backend/src/main/java/com/ibizdrive/audit/TeamAuditListener.java \
        backend/src/test/java/com/ibizdrive/audit/TeamAuditListenerTest.java
git commit -m "feat(team-centric-pivot): TeamAuditListener — team.created/member.added/removed"
```

---

## Phase 11 — Final Integration & Smoke

### Task 29: End-to-end: create team → list members → create child folder → assert scope cascades

**Files:**
- Create: `backend/src/test/java/com/ibizdrive/team/TeamPivotEndToEndTest.java`

- [ ] **Step 1: Write the integration test**

```java
package com.ibizdrive.team;

import com.ibizdrive.folder.Folder;
import com.ibizdrive.folder.FolderMutationService;
import com.ibizdrive.folder.FolderRepository;
import com.ibizdrive.folder.ScopeType;
import com.ibizdrive.permission.Permission;
import com.ibizdrive.permission.PermissionResolver;
import com.ibizdrive.workspace.WorkspaceListing;
import com.ibizdrive.workspace.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest @ActiveProfiles("test") @Transactional
class TeamPivotEndToEndTest {

    @Autowired TeamService teamSvc;
    @Autowired FolderMutationService folderMut;
    @Autowired FolderRepository folderRepo;
    @Autowired WorkspaceService wsSvc;
    @Autowired PermissionResolver permResolver;

    @Test
    void fullFlow() {
        UUID owner = TestUserSeed.persistUser("e2e-o@t");
        UUID member = TestUserSeed.persistUser("e2e-m@t");

        // Create team
        Team t = teamSvc.create("E2E", null, Team.Visibility.PRIVATE, owner);
        assertThat(t.getRootFolderId()).isNotNull();

        // Invite member
        teamSvc.invite(t.getId(), member, owner);

        // Workspace listing for member: includes team
        WorkspaceListing list = wsSvc.findForUser(member);
        assertThat(list.teams()).extracting(w -> w.id()).contains(t.getId());

        // Create child folder under team root
        Folder child = folderMut.createChild(t.getRootFolderId(), "Q1", owner);
        assertThat(child.getScopeType()).isEqualTo(ScopeType.TEAM);
        assertThat(child.getScopeId()).isEqualTo(t.getId());

        // Member has READ+UPLOAD+EDIT on child via membership
        Set<Permission> perms = permResolver.findEffective(child, member);
        assertThat(perms).contains(Permission.READ, Permission.UPLOAD, Permission.EDIT);

        // Owner has all 5
        Set<Permission> ownerPerms = permResolver.findEffective(child, owner);
        assertThat(ownerPerms).contains(Permission.READ, Permission.UPLOAD,
            Permission.EDIT, Permission.DELETE, Permission.SHARE);
    }
}
```

- [ ] **Step 2: Run — expect PASS** (all earlier tasks make this work)

```
cd backend && ./gradlew test --tests "com.ibizdrive.team.TeamPivotEndToEndTest"
```

- [ ] **Step 3: Run full backend test suite**

```
cd backend && ./gradlew test
```

Expected: all tests green. If any pre-existing test broke from membership permission changes, fix the test seed data (do not weaken the new behavior).

- [ ] **Step 4: Commit**

```
git add backend/src/test/java/com/ibizdrive/team/TeamPivotEndToEndTest.java
git commit -m "test(team-centric-pivot): end-to-end team→folder→permission flow"
```

---

### Task 30: Update `progress.md` + create dev-docs entry

**Files:**
- Modify: `docs/progress.md`
- Create: `dev/active/team-centric-pivot/team-centric-pivot-plan.md` (optional handoff doc)

- [ ] **Step 1: Append session entry to progress.md**

Add a section under today's date documenting Plan A completion: tables added, new endpoints, test count delta, and pointers to `docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md` and `docs/superpowers/plans/2026-05-09-team-centric-pivot-plan-a-backend-foundation.md`.

- [ ] **Step 2: Commit**

```
git add docs/progress.md
git commit -m "docs(team-centric-pivot): progress.md — Plan A backend foundation complete"
```

---

## Self-Review Notes (writing-plans skill)

**Spec coverage:**
- §1.1 teams/team_memberships → Tasks 1, 5, 6, 10, 11
- §1.2 folders/files scope → Tasks 2, 7, 8, 24, 25, 26
- §1.3 root folder unique + departments.root_folder_id → Tasks 3, 9, 20, 21
- §1.4 permissions subject_type extension → Task 4
- §1.5 audit_log target_type extension → Tasks 4, 12
- §1.6 owner_id semantic redefinition → covered implicitly (no special permission grant from owner_id) — verified by Task 23 test where owner gets perms via membership only
- §2.1 dept lifecycle (create with root) → Task 20; rename/deactivate retained from existing AdminDepartmentService
- §2.2 team lifecycle (create/invite/remove only) → Tasks 16, 17, 18, 19; **archive/un-archive/purge deferred to Plan A2**
- §3.1 evaluation order → Task 23 (membership step inserted before explicit + shares)
- §3.2 membership default permissions → Task 22
- §3.3 explicit grant union → Task 23 (existing behavior preserved)
- §3.4 cross-workspace via shares → not implemented here (Plan C)
- §3.5 evaluator signature → Task 23
- §3.6 backend re-validation → enforced by `@PreAuthorize` in TeamController (Task 19)
- §5.2 GET /api/workspaces/me → Task 15
- §5.3 folder/file scope inheritance + same-scope move + scope in responses → Tasks 24, 25, 26, 27
- §5.6 cross-workspace move (preview + transaction) → **Plan D (excluded from Plan A)**
- §4.5 sidebar tree → **Plan B (frontend, excluded from Plan A)**

**Placeholder scan:** No "TBD/TODO/implement later" remaining. Three tasks (8, 24, 26) have `/*FILL_IN*/`-style notes when actual entity/service API names need verification by the implementer — these are deliberate "discover then code" steps, not placeholders.

**Type consistency:**
- `ScopeType` (Task 7) used uniformly in Folder, FileItem, WorkspaceMembershipResolver, response DTOs
- `Permission` enum referenced in WorkspaceMembershipResolver tests must match actual constants in `com.ibizdrive.permission.Permission` — verify before running Task 22 step 4
- `TeamMembershipId` composite key used in `TeamMembershipRepository` queries (`m.id.userId`) — verified by repository test

**Known caveats requiring implementer judgment:**
1. `Folder.assignNew(...)` does not exist — Task 16 step 3 + Task 20 step 4 + Task 21 step 3 require the implementer to either add this factory method to `Folder.java` (recommended; centralizes new-row construction) or replace it with whatever existing constructor / setter pattern is used. The plan flags this explicitly.
2. `AuditService.record(...)` signature must be confirmed in Task 28 step 4. Adapt argument order/types.
3. `Permission` enum constant names in current code may differ from `READ/UPLOAD/EDIT/DELETE/SHARE`. Task 22 test will compile-fail clearly; rename in test to match.
4. `User` entity's `getDepartmentId()` may be missing — `DefaultUserDepartmentLookup` (Task 14) requires it. Add the getter to `User.java` if absent (the column exists from V7).

---

**Execution handoff:**

Plan complete and saved to `docs/superpowers/plans/2026-05-09-team-centric-pivot-plan-a-backend-foundation.md`. Two execution options:

**1. Subagent-Driven (recommended)** — fresh subagent per task, review between tasks, fast iteration via `superpowers:subagent-driven-development`.

**2. Inline Execution** — execute tasks in this session via `superpowers:executing-plans`, batch with checkpoints.

Which approach?
