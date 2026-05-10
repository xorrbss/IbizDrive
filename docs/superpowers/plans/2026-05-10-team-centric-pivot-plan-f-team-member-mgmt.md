# Plan F — Team Member Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 팀 OWNER가 UI로 멤버 목록을 조회/추가/제거하고 역할을 변경할 수 있게 한다 — 사내 시스템에서 팀 워크스페이스 일상 운영의 마지막 gap 닫기.

**Architecture:** backend는 service에 이미 있는 `listMembers`/`changeRole` 메서드를 controller에 1:1 wire (신규 도메인 zero). frontend는 `/t/{teamId}/settings/members` 단일 페이지 + 4개 hook + 4개 dialog. archive 가드는 데이터 플레인 트랙(team-archive-write-enforcement)에 위임 — 본 plan은 컨트롤 플레인만.

**Tech Stack:** Spring Boot, JPA (constructor projection), TanStack Query v5, Vitest + fetch-mock pattern, Tailwind CSS.

**Spec:** `docs/superpowers/specs/2026-05-10-team-centric-pivot-plan-f-team-member-mgmt-design.md`

**Branch:** `feat/team-centric-pivot-plan-f-team-member-mgmt` (origin/master 기반, 이미 생성됨)

**Working directory:** `C:\project\IbizDrive\.claude\worktrees\plan-f-team-member-mgmt`

---

## Phase 1 — Backend (T1~T6)

### T1: Backend DTOs (TeamMemberResponse + TeamMemberRoleUpdateRequest)

**Files:**
- Create: `backend/src/main/java/com/ibizdrive/team/dto/TeamMemberResponse.java`
- Create: `backend/src/main/java/com/ibizdrive/team/dto/TeamMemberRoleUpdateRequest.java`
- Create: `backend/src/test/java/com/ibizdrive/team/dto/TeamMemberRoleUpdateRequestTest.java`

- [ ] **Step 1: Write failing validation test for TeamMemberRoleUpdateRequest**

```java
package com.ibizdrive.team.dto;

import com.ibizdrive.team.TeamMembership;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TeamMemberRoleUpdateRequestTest {

    private final Validator validator;

    TeamMemberRoleUpdateRequestTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            this.validator = factory.getValidator();
        }
    }

    @Test
    void roleNotNull_isRequired() {
        TeamMemberRoleUpdateRequest req = new TeamMemberRoleUpdateRequest(null);
        Set<ConstraintViolation<TeamMemberRoleUpdateRequest>> v = validator.validate(req);
        assertThat(v).hasSize(1);
        assertThat(v.iterator().next().getPropertyPath().toString()).isEqualTo("role");
    }

    @Test
    void validRole_passes() {
        TeamMemberRoleUpdateRequest req = new TeamMemberRoleUpdateRequest(TeamMembership.Role.OWNER);
        Set<ConstraintViolation<TeamMemberRoleUpdateRequest>> v = validator.validate(req);
        assertThat(v).isEmpty();
    }
}
```

- [ ] **Step 2: Run to confirm fail**

```
cd backend && ./gradlew test --tests "com.ibizdrive.team.dto.TeamMemberRoleUpdateRequestTest"
```
Expected: compilation FAIL — `TeamMemberRoleUpdateRequest` not defined.

- [ ] **Step 3: Create TeamMemberRoleUpdateRequest record**

`backend/src/main/java/com/ibizdrive/team/dto/TeamMemberRoleUpdateRequest.java`:

```java
package com.ibizdrive.team.dto;

import com.ibizdrive.team.TeamMembership;
import jakarta.validation.constraints.NotNull;

/** {@code PATCH /api/teams/{teamId}/members/{userId}} request body — Plan F T1. */
public record TeamMemberRoleUpdateRequest(@NotNull TeamMembership.Role role) {}
```

- [ ] **Step 4: Create TeamMemberResponse record**

`backend/src/main/java/com/ibizdrive/team/dto/TeamMemberResponse.java`:

```java
package com.ibizdrive.team.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ibizdrive.team.TeamMembership;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * {@code GET /api/teams/{teamId}/members} response item — Plan F T1.
 *
 * <p>JPQL constructor projection이 직접 인스턴스화하므로 record 컴팩트 생성자에 검증 없음 —
 * spec §3.4 참조.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TeamMemberResponse(
    UUID userId,
    String displayName,
    String email,
    TeamMembership.Role role,
    OffsetDateTime joinedAt
) {}
```

- [ ] **Step 5: Run test to verify pass**

```
cd backend && ./gradlew test --tests "com.ibizdrive.team.dto.TeamMemberRoleUpdateRequestTest"
```
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```
git add backend/src/main/java/com/ibizdrive/team/dto/TeamMemberResponse.java backend/src/main/java/com/ibizdrive/team/dto/TeamMemberRoleUpdateRequest.java backend/src/test/java/com/ibizdrive/team/dto/TeamMemberRoleUpdateRequestTest.java
git commit -m "feat(team-centric-pivot): TeamMemberResponse + TeamMemberRoleUpdateRequest DTOs (Plan F T1)"
```

---

### T2: TeamMembershipRepository.findMembersWithUser

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/team/TeamMembershipRepository.java`
- Modify: `backend/src/test/java/com/ibizdrive/team/TeamMembershipRepositoryTest.java`

- [ ] **Step 1: Open existing repository test to follow @DataJpaTest pattern**

Read `backend/src/test/java/com/ibizdrive/team/TeamMembershipRepositoryTest.java` to confirm Testcontainers setup is reused.

- [ ] **Step 2: Add failing test for findMembersWithUser**

Append to `TeamMembershipRepositoryTest.java`:

```java
    @Test
    void findMembersWithUser_returnsJoinedDtoOrderedByJoinedAt() {
        UUID teamId = UUID.randomUUID();

        // 2 users with different displayName/email
        UUID u1 = persistUser("alice@example.com", "Alice");
        UUID u2 = persistUser("bob@example.com", "Bob");

        OffsetDateTime t0 = OffsetDateTime.now();
        memRepo.save(new TeamMembership(teamId, u1, TeamMembership.Role.OWNER, null, t0));
        memRepo.save(new TeamMembership(teamId, u2, TeamMembership.Role.MEMBER, u1, t0.plusSeconds(60)));

        List<TeamMemberResponse> members = memRepo.findMembersWithUser(teamId);

        assertThat(members).hasSize(2);
        assertThat(members.get(0).userId()).isEqualTo(u1);
        assertThat(members.get(0).displayName()).isEqualTo("Alice");
        assertThat(members.get(0).email()).isEqualTo("alice@example.com");
        assertThat(members.get(0).role()).isEqualTo(TeamMembership.Role.OWNER);
        assertThat(members.get(1).userId()).isEqualTo(u2);
        assertThat(members.get(1).displayName()).isEqualTo("Bob");
    }

    @Test
    void findMembersWithUser_emptyForUnknownTeam() {
        assertThat(memRepo.findMembersWithUser(UUID.randomUUID())).isEmpty();
    }
```

> Note: `persistUser` helper may need to be added in the test class if not present. Pattern: `entityManager.persist(new User(...))`. If existing test has a different helper or fixture, follow that. Confirm by reading the file before editing.

- [ ] **Step 3: Run test to confirm compile or runtime fail**

```
cd backend && ./gradlew test --tests "com.ibizdrive.team.TeamMembershipRepositoryTest.findMembersWithUser*"
```
Expected: compile FAIL — `findMembersWithUser` not defined.

- [ ] **Step 4: Add findMembersWithUser to TeamMembershipRepository**

In `TeamMembershipRepository.java`, add after `findByTeamId`:

```java
    /**
     * 팀 멤버 목록을 user 정보(displayName, email)와 함께 DTO로 직접 반환한다 — Plan F T2.
     *
     * <p>JPQL constructor projection으로 entity 측 User 매핑 추가 없이 read-only JOIN.
     * spec docs/superpowers/specs/2026-05-10-team-centric-pivot-plan-f-team-member-mgmt-design.md §3.4.
     *
     * @param teamId 조회할 팀 ID (null 불가)
     * @return TeamMemberResponse list — joinedAt 오름차순. team이 없거나 멤버가 없으면 빈 리스트.
     */
    @Query("""
        SELECT new com.ibizdrive.team.dto.TeamMemberResponse(
            m.id.userId, u.displayName, u.email, m.role, m.joinedAt
        )
        FROM TeamMembership m, com.ibizdrive.user.User u
        WHERE m.id.teamId = :team AND m.id.userId = u.id
        ORDER BY m.joinedAt ASC
        """)
    List<TeamMemberResponse> findMembersWithUser(@Param("team") UUID teamId);
```

Add import:
```java
import com.ibizdrive.team.dto.TeamMemberResponse;
```

- [ ] **Step 5: Run test to verify pass**

```
cd backend && ./gradlew test --tests "com.ibizdrive.team.TeamMembershipRepositoryTest.findMembersWithUser*"
```
Expected: PASS (2 tests). If User persistence helper is missing, add it inline in the test class — follow existing User entity constructor.

- [ ] **Step 6: Commit**

```
git add backend/src/main/java/com/ibizdrive/team/TeamMembershipRepository.java backend/src/test/java/com/ibizdrive/team/TeamMembershipRepositoryTest.java
git commit -m "feat(team-centric-pivot): TeamMembershipRepository.findMembersWithUser (Plan F T2)"
```

---

### T3: TeamService.listMembers

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/team/TeamService.java`
- Create: `backend/src/test/java/com/ibizdrive/team/TeamServiceListMembersTest.java`

- [ ] **Step 1: Write failing test (Mockito)**

`backend/src/test/java/com/ibizdrive/team/TeamServiceListMembersTest.java`:

```java
package com.ibizdrive.team;

import com.ibizdrive.audit.AuditLogger;
import com.ibizdrive.folder.FolderMutationService;
import com.ibizdrive.team.dto.TeamMemberResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeamServiceListMembersTest {

    private TeamRepository teamRepo;
    private TeamMembershipRepository memRepo;
    private FolderMutationService folderService;
    private ApplicationEventPublisher events;
    private TeamService svc;

    @BeforeEach
    void setUp() {
        teamRepo = mock(TeamRepository.class);
        memRepo = mock(TeamMembershipRepository.class);
        folderService = mock(FolderMutationService.class);
        events = mock(ApplicationEventPublisher.class);
        svc = new TeamService(teamRepo, memRepo, folderService, events);
    }

    @Test
    void listMembers_delegatesToRepository() {
        UUID teamId = UUID.randomUUID();
        TeamMemberResponse r1 = new TeamMemberResponse(UUID.randomUUID(), "Alice", "a@x.io",
            TeamMembership.Role.OWNER, OffsetDateTime.now());
        when(memRepo.findMembersWithUser(eq(teamId))).thenReturn(List.of(r1));

        List<TeamMemberResponse> result = svc.listMembers(teamId);

        assertThat(result).containsExactly(r1);
        Mockito.verify(memRepo).findMembersWithUser(teamId);
        Mockito.verifyNoMoreInteractions(memRepo, teamRepo, folderService, events);
    }

    @Test
    void listMembers_nullTeamId_throws() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> svc.listMembers(null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

> Constructor signature `TeamService(teamRepo, memRepo, folderService, events)` is the existing one. Verify by reading `TeamService.java` first if uncertain.

- [ ] **Step 2: Run to confirm fail**

```
cd backend && ./gradlew test --tests "com.ibizdrive.team.TeamServiceListMembersTest"
```
Expected: compile FAIL — `svc.listMembers` not defined.

- [ ] **Step 3: Add listMembers to TeamService**

In `TeamService.java`, add after `restore` method:

```java
    /**
     * 팀 멤버 목록 조회 — Plan F T3.
     *
     * <p>read-only delegation. user 정보(displayName, email)는 JPQL constructor projection으로
     * 동시에 로드 (spec §3.4).
     *
     * @param teamId 조회할 팀 ID (null 불가)
     * @return joinedAt 오름차순 멤버 목록; team이 없으면 빈 리스트
     * @throws IllegalArgumentException teamId가 null
     */
    @Transactional(readOnly = true)
    public List<com.ibizdrive.team.dto.TeamMemberResponse> listMembers(UUID teamId) {
        if (teamId == null) {
            throw new IllegalArgumentException("teamId must not be null");
        }
        return memRepo.findMembersWithUser(teamId);
    }
```

> If `TeamService` does not already import `java.util.List` — add it. If using FQCN inline, no import needed for `TeamMemberResponse`.

- [ ] **Step 4: Run test to verify pass**

```
cd backend && ./gradlew test --tests "com.ibizdrive.team.TeamServiceListMembersTest"
```
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```
git add backend/src/main/java/com/ibizdrive/team/TeamService.java backend/src/test/java/com/ibizdrive/team/TeamServiceListMembersTest.java
git commit -m "feat(team-centric-pivot): TeamService.listMembers (Plan F T3)"
```

---

### T4: TeamAuthz.isMember

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/team/TeamAuthz.java`
- Modify: `backend/src/test/java/com/ibizdrive/team/TeamAuthzTest.java` (or create if absent)

- [ ] **Step 1: Locate or create TeamAuthzTest**

Search: `find backend/src/test -name "TeamAuthzTest.java"` (or use Glob in tool).
- If exists: append tests to existing file.
- If absent: create new file using pattern below.

- [ ] **Step 2: Write failing test**

If creating new `backend/src/test/java/com/ibizdrive/team/TeamAuthzTest.java`:

```java
package com.ibizdrive.team;

import com.ibizdrive.user.IbizDriveUserDetails;
import com.ibizdrive.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeamAuthzTest {

    private TeamMembershipRepository memRepo;
    private TeamAuthz authz;

    @BeforeEach
    void setUp() {
        memRepo = mock(TeamMembershipRepository.class);
        authz = new TeamAuthz(memRepo);
    }

    private IbizDriveUserDetails principal(UUID userId) {
        User u = mock(User.class);
        when(u.getId()).thenReturn(userId);
        IbizDriveUserDetails uds = mock(IbizDriveUserDetails.class);
        when(uds.getUser()).thenReturn(u);
        return uds;
    }

    @Test
    void isMember_trueWhenMembershipExists() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TeamMembership m = mock(TeamMembership.class);
        when(memRepo.findById(new TeamMembershipId(teamId, userId))).thenReturn(Optional.of(m));

        assertThat(authz.isMember(teamId, principal(userId))).isTrue();
    }

    @Test
    void isMember_falseWhenNoMembership() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(memRepo.findById(new TeamMembershipId(teamId, userId))).thenReturn(Optional.empty());

        assertThat(authz.isMember(teamId, principal(userId))).isFalse();
    }

    @Test
    void isMember_falseWhenPrincipalNotIbizDriveUserDetails() {
        assertThat(authz.isMember(UUID.randomUUID(), "anonymous")).isFalse();
    }
}
```

- [ ] **Step 3: Run to confirm fail**

```
cd backend && ./gradlew test --tests "com.ibizdrive.team.TeamAuthzTest"
```
Expected: compile FAIL — `authz.isMember` not defined.

- [ ] **Step 4: Add isMember to TeamAuthz**

In `TeamAuthz.java`, add after `isOwnerOrSelf`:

```java
    /**
     * principal이 team의 멤버인지 (role 무관) — Plan F T4.
     *
     * <p>{@code GET /api/teams/{teamId}/members} 권한 가드용. spec §3.2.
     */
    public boolean isMember(UUID teamId, Object principal) {
        UUID userId = principalUserId(principal);
        if (userId == null) return false;
        return memRepo.findById(new TeamMembershipId(teamId, userId)).isPresent();
    }
```

- [ ] **Step 5: Run test to verify pass**

```
cd backend && ./gradlew test --tests "com.ibizdrive.team.TeamAuthzTest"
```
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```
git add backend/src/main/java/com/ibizdrive/team/TeamAuthz.java backend/src/test/java/com/ibizdrive/team/TeamAuthzTest.java
git commit -m "feat(team-centric-pivot): TeamAuthz.isMember (Plan F T4)"
```

---

### T5: TeamController GET /members endpoint

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/team/TeamController.java`
- Modify: `backend/src/test/java/com/ibizdrive/team/TeamControllerTest.java`

- [ ] **Step 1: Add failing controller test**

Append to `TeamControllerTest.java`:

```java
    @Test
    void listMembers_returns200_whenMember() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        com.ibizdrive.team.dto.TeamMemberResponse r1 =
            new com.ibizdrive.team.dto.TeamMemberResponse(
                userId, "Alice", "a@x.io",
                TeamMembership.Role.OWNER, OffsetDateTime.parse("2026-05-10T00:00:00Z"));
        when(teamAuthz.isMember(eq(teamId), any())).thenReturn(true);
        when(teamService.listMembers(eq(teamId))).thenReturn(java.util.List.of(r1));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/teams/" + teamId + "/members")
                .with(user(testUserDetails(userId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].userId").value(userId.toString()))
            .andExpect(jsonPath("$[0].displayName").value("Alice"))
            .andExpect(jsonPath("$[0].role").value("OWNER"));
    }

    @Test
    void listMembers_returns403_whenNonMember() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(teamAuthz.isMember(eq(teamId), any())).thenReturn(false);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/teams/" + teamId + "/members")
                .with(user(testUserDetails(userId))))
            .andExpect(status().isForbidden());
        Mockito.verify(teamService, never()).listMembers(any());
    }
```

> The helper `testUserDetails(UUID)` may already exist in `TeamControllerTest`; use the existing pattern. If not, add a small builder using `IbizDriveUserDetails`/`User` mocks (peer pattern in same file).

- [ ] **Step 2: Run to confirm fail**

```
cd backend && ./gradlew test --tests "com.ibizdrive.team.TeamControllerTest.listMembers*"
```
Expected: 404 (endpoint not mapped) or compile fail.

- [ ] **Step 3: Add GET endpoint to TeamController**

In `TeamController.java`, add after `remove` method:

```java
    @GetMapping("/{teamId}/members")
    @PreAuthorize("@teamAuthz.isMember(#teamId, principal)")
    public ResponseEntity<java.util.List<com.ibizdrive.team.dto.TeamMemberResponse>> listMembers(
            @PathVariable UUID teamId) {
        return ResponseEntity.ok(svc.listMembers(teamId));
    }
```

Add import:
```java
import org.springframework.web.bind.annotation.GetMapping;
```

Update class javadoc endpoint list to include the new GET.

- [ ] **Step 4: Run test to verify pass**

```
cd backend && ./gradlew test --tests "com.ibizdrive.team.TeamControllerTest.listMembers*"
```
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```
git add backend/src/main/java/com/ibizdrive/team/TeamController.java backend/src/test/java/com/ibizdrive/team/TeamControllerTest.java
git commit -m "feat(team-centric-pivot): TeamController GET /members endpoint (Plan F T5)"
```

---

### T6: TeamController PATCH role endpoint

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/team/TeamController.java`
- Modify: `backend/src/test/java/com/ibizdrive/team/TeamControllerTest.java`

- [ ] **Step 1: Add failing controller tests for PATCH role**

Append to `TeamControllerTest.java`:

```java
    @Test
    void changeRole_returns204_whenOwner() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        when(teamAuthz.isOwner(eq(teamId), any())).thenReturn(true);

        com.ibizdrive.team.dto.TeamMemberRoleUpdateRequest req =
            new com.ibizdrive.team.dto.TeamMemberRoleUpdateRequest(TeamMembership.Role.OWNER);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/teams/" + teamId + "/members/" + targetUserId)
                .with(csrf())
                .with(user(testUserDetails(actorId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(req)))
            .andExpect(status().isNoContent());

        Mockito.verify(teamService).changeRole(eq(teamId), eq(targetUserId),
            eq(TeamMembership.Role.OWNER), eq(actorId));
    }

    @Test
    void changeRole_returns400_whenLastOwner() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        when(teamAuthz.isOwner(eq(teamId), any())).thenReturn(true);
        Mockito.doThrow(new LastOwnerRequiredException(teamId))
            .when(teamService).changeRole(any(), any(), any(), any());

        com.ibizdrive.team.dto.TeamMemberRoleUpdateRequest req =
            new com.ibizdrive.team.dto.TeamMemberRoleUpdateRequest(TeamMembership.Role.MEMBER);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/teams/" + teamId + "/members/" + targetUserId)
                .with(csrf())
                .with(user(testUserDetails(actorId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("TEAM_OWNER_REQUIRED"));
    }

    @Test
    void changeRole_returns403_whenNonOwner() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        when(teamAuthz.isOwner(eq(teamId), any())).thenReturn(false);

        com.ibizdrive.team.dto.TeamMemberRoleUpdateRequest req =
            new com.ibizdrive.team.dto.TeamMemberRoleUpdateRequest(TeamMembership.Role.MEMBER);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/teams/" + teamId + "/members/" + targetUserId)
                .with(csrf())
                .with(user(testUserDetails(actorId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(req)))
            .andExpect(status().isForbidden());
        Mockito.verify(teamService, never()).changeRole(any(), any(), any(), any());
    }
```

- [ ] **Step 2: Run to confirm fail**

```
cd backend && ./gradlew test --tests "com.ibizdrive.team.TeamControllerTest.changeRole*"
```
Expected: 404/405 for unmapped PATCH or compile fail.

- [ ] **Step 3: Add PATCH endpoint to TeamController**

In `TeamController.java`, add after the new GET listMembers:

```java
    @PatchMapping("/{teamId}/members/{userId}")
    @PreAuthorize("@teamAuthz.isOwner(#teamId, principal)")
    public ResponseEntity<Void> changeRole(@PathVariable UUID teamId,
                                           @PathVariable UUID userId,
                                           @Valid @RequestBody com.ibizdrive.team.dto.TeamMemberRoleUpdateRequest req,
                                           @AuthenticationPrincipal IbizDriveUserDetails principal) {
        UUID actor = principal.getUser().getId();
        svc.changeRole(teamId, userId, req.role(), actor);
        return ResponseEntity.noContent().build();
    }
```

Add import:
```java
import org.springframework.web.bind.annotation.PatchMapping;
```

Update class javadoc endpoint list.

- [ ] **Step 4: Verify GlobalExceptionHandler maps LastOwnerRequiredException → TEAM_OWNER_REQUIRED**

Open `backend/src/main/java/com/ibizdrive/common/error/GlobalExceptionHandler.java` and confirm there is a handler that maps `LastOwnerRequiredException` to a 400 with code `TEAM_OWNER_REQUIRED`. (Plan A2 should have added this — `2099f64`.) If absent, add one (peer: existing handlers in the same class).

- [ ] **Step 5: Run all controller tests**

```
cd backend && ./gradlew test --tests "com.ibizdrive.team.TeamControllerTest"
```
Expected: all PASS (existing 3 from Plan A + new 2 list + 3 patch = 8).

- [ ] **Step 6: Commit**

```
git add backend/src/main/java/com/ibizdrive/team/TeamController.java backend/src/test/java/com/ibizdrive/team/TeamControllerTest.java
git commit -m "feat(team-centric-pivot): TeamController PATCH role endpoint (Plan F T6)"
```

---

## Phase 2 — Frontend foundation (T7~T9)

### T7: queryKeys + types

**Files:**
- Modify: `frontend/src/types/team.ts`
- Modify: `frontend/src/lib/queryKeys.ts`

- [ ] **Step 1: Extend types/team.ts**

Append to `frontend/src/types/team.ts`:

```typescript
export type TeamMemberRole = 'OWNER' | 'MEMBER'

export interface TeamMember {
  userId: string
  displayName: string
  email: string
  role: TeamMemberRole
  joinedAt: string
}

export interface TeamMemberRoleUpdateRequest {
  role: TeamMemberRole
}
```

- [ ] **Step 2: Extend qk.teams + invalidations**

In `frontend/src/lib/queryKeys.ts`, replace the `teams` block (line ~217-220):

```typescript
  // ── Teams (Plan B + Plan F — POST/GET/PATCH /api/teams + members) ──
  teams: {
    all: () => [...qk.all, 'teams'] as const,
    detail: (id: string) => [...qk.all, 'teams', 'detail', id] as const,
    members: (id: string) => [...qk.all, 'teams', 'members', id] as const,
  },
```

Add a new helper to `invalidations` block (after `afterTeamChanged`):

```typescript
  /**
   * 팀 멤버 변경 후 무효화 — Plan F.
   * - 해당 팀의 멤버 목록
   * - workspaces.me() (자기 자신을 remove한 경우 워크스페이스 리스트 갱신)
   */
  afterTeamMembersChanged(qc: QueryClient, teamId: string): Promise<void> {
    return Promise.all([
      qc.invalidateQueries({ queryKey: qk.teams.members(teamId) }),
      qc.invalidateQueries({ queryKey: qk.workspaces.me() }),
    ]).then(() => undefined)
  },
```

- [ ] **Step 3: Verify TypeScript compiles**

```
cd frontend && pnpm typecheck
```
Expected: exit 0.

- [ ] **Step 4: Commit**

```
git add frontend/src/types/team.ts frontend/src/lib/queryKeys.ts
git commit -m "feat(team-centric-pivot): teams.members query key + TeamMember types (Plan F T7)"
```

---

### T8: API client methods

**Files:**
- Modify: `frontend/src/lib/api.ts`
- Create: `frontend/src/lib/api.team-members.test.ts`

- [ ] **Step 1: Write failing API client tests (fetch-mock pattern)**

`frontend/src/lib/api.team-members.test.ts`:

```typescript
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { api } from '@/lib/api'

describe('api team members (Plan F T8)', () => {
  const fetchMock = vi.fn()
  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    document.cookie = 'XSRF-TOKEN=test-token'
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
    fetchMock.mockReset()
  })

  it('getTeamMembers — GET /api/teams/{id}/members', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify([
      { userId: 'u1', displayName: 'A', email: 'a@x.io', role: 'OWNER', joinedAt: '2026-05-10T00:00:00Z' },
    ]), { status: 200 }))
    const result = await api.getTeamMembers('team-1')
    expect(result).toHaveLength(1)
    expect(result[0].userId).toBe('u1')
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/teams/team-1/members')
    expect((init as RequestInit).method).toBe('GET')
  })

  it('inviteTeamMember — POST .../members with CSRF', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 201 }))
    await api.inviteTeamMember('team-1', 'user-2')
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/teams/team-1/members')
    expect((init as RequestInit).method).toBe('POST')
    expect((init as RequestInit).headers).toMatchObject({ 'X-CSRF-TOKEN': 'test-token' })
    expect(JSON.parse(String((init as RequestInit).body))).toEqual({ userId: 'user-2' })
  })

  it('removeTeamMember — DELETE .../members/{userId}', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))
    await api.removeTeamMember('team-1', 'user-2')
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/teams/team-1/members/user-2')
    expect((init as RequestInit).method).toBe('DELETE')
    expect((init as RequestInit).headers).toMatchObject({ 'X-CSRF-TOKEN': 'test-token' })
  })

  it('changeTeamMemberRole — PATCH .../members/{userId}', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))
    await api.changeTeamMemberRole('team-1', 'user-2', 'OWNER')
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/teams/team-1/members/user-2')
    expect((init as RequestInit).method).toBe('PATCH')
    expect(JSON.parse(String((init as RequestInit).body))).toEqual({ role: 'OWNER' })
  })

  it('400 TEAM_OWNER_REQUIRED → throws Error with code', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({
      code: 'TEAM_OWNER_REQUIRED',
    }), { status: 400 }))
    await expect(api.changeTeamMemberRole('team-1', 'user-2', 'MEMBER')).rejects.toMatchObject({
      status: 400,
      code: 'TEAM_OWNER_REQUIRED',
    })
  })
})
```

- [ ] **Step 2: Run to confirm fail**

```
cd frontend && pnpm test src/lib/api.team-members.test.ts --run
```
Expected: FAIL — methods not defined.

- [ ] **Step 3: Add methods to api.ts**

Append the four methods inside the `api` object (after `createTeam`):

```typescript
  /**
   * Plan F T8 — 팀 멤버 목록 ({@code GET /api/teams/{teamId}/members}).
   */
  async getTeamMembers(teamId: string): Promise<TeamMember[]> {
    const res = await fetch(`/api/teams/${encodeURIComponent(teamId)}/members`, {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    if (!res.ok) {
      throw await buildApiError(res, `getTeamMembers failed: ${res.status}`)
    }
    return (await res.json()) as TeamMember[]
  },

  /**
   * Plan F T8 — 팀 멤버 초대 ({@code POST /api/teams/{teamId}/members}). idempotent.
   */
  async inviteTeamMember(teamId: string, userId: string): Promise<void> {
    const csrf = readCookie('XSRF-TOKEN') ?? ''
    const res = await fetch(`/api/teams/${encodeURIComponent(teamId)}/members`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': csrf,
      },
      body: JSON.stringify({ userId }),
    })
    if (!res.ok) {
      throw await buildApiError(res, `inviteTeamMember failed: ${res.status}`)
    }
  },

  /**
   * Plan F T8 — 팀 멤버 제거 ({@code DELETE /api/teams/{teamId}/members/{userId}}).
   * OWNER 제거 시도 시 last-OWNER 가드 → 400 TEAM_OWNER_REQUIRED.
   */
  async removeTeamMember(teamId: string, userId: string): Promise<void> {
    const csrf = readCookie('XSRF-TOKEN') ?? ''
    const res = await fetch(
      `/api/teams/${encodeURIComponent(teamId)}/members/${encodeURIComponent(userId)}`,
      {
        method: 'DELETE',
        credentials: 'include',
        headers: { 'X-CSRF-TOKEN': csrf },
      },
    )
    if (!res.ok) {
      throw await buildApiError(res, `removeTeamMember failed: ${res.status}`)
    }
  },

  /**
   * Plan F T8 — 팀 멤버 역할 변경 ({@code PATCH .../members/{userId}}).
   * last-OWNER 강등 시도 → 400 TEAM_OWNER_REQUIRED.
   */
  async changeTeamMemberRole(
    teamId: string,
    userId: string,
    role: TeamMemberRole,
  ): Promise<void> {
    const csrf = readCookie('XSRF-TOKEN') ?? ''
    const res = await fetch(
      `/api/teams/${encodeURIComponent(teamId)}/members/${encodeURIComponent(userId)}`,
      {
        method: 'PATCH',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
          'X-CSRF-TOKEN': csrf,
        },
        body: JSON.stringify({ role }),
      },
    )
    if (!res.ok) {
      throw await buildApiError(res, `changeTeamMemberRole failed: ${res.status}`)
    }
  },
```

Update imports at top of `api.ts`:

```typescript
import type { TeamCreateRequest, TeamMember, TeamMemberRole, TeamResponse } from '@/types/team'
```

- [ ] **Step 4: Run test to verify pass**

```
cd frontend && pnpm test src/lib/api.team-members.test.ts --run
```
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```
git add frontend/src/lib/api.ts frontend/src/lib/api.team-members.test.ts
git commit -m "feat(team-centric-pivot): api.{getTeamMembers,inviteTeamMember,removeTeamMember,changeTeamMemberRole} (Plan F T8)"
```

---

### T9: useTeamMembers hook (read)

**Files:**
- Create: `frontend/src/hooks/useTeamMembers.ts`
- Create: `frontend/src/hooks/useTeamMembers.test.tsx`

- [ ] **Step 1: Write failing test**

`frontend/src/hooks/useTeamMembers.test.tsx`:

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useTeamMembers } from './useTeamMembers'
import { api } from '@/lib/api'

vi.mock('@/lib/api', () => ({
  api: { getTeamMembers: vi.fn() },
}))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

describe('useTeamMembers', () => {
  beforeEach(() => vi.clearAllMocks())

  it('fetches members for a teamId', async () => {
    ;(api.getTeamMembers as ReturnType<typeof vi.fn>).mockResolvedValue([
      { userId: 'u1', displayName: 'Alice', email: 'a@x.io', role: 'OWNER', joinedAt: '2026-05-10T00:00:00Z' },
    ])
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { result } = renderHook(() => useTeamMembers('team-1'), { wrapper: wrap(qc) })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.getTeamMembers).toHaveBeenCalledWith('team-1')
    expect(result.current.data).toHaveLength(1)
  })

  it('skips fetch when teamId is null', () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    renderHook(() => useTeamMembers(null), { wrapper: wrap(qc) })
    expect(api.getTeamMembers).not.toHaveBeenCalled()
  })
})
```

- [ ] **Step 2: Run to confirm fail**

```
cd frontend && pnpm test src/hooks/useTeamMembers.test.tsx --run
```
Expected: FAIL.

- [ ] **Step 3: Implement hook**

`frontend/src/hooks/useTeamMembers.ts`:

```typescript
'use client'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'

/**
 * Plan F T9 — 팀 멤버 목록 조회.
 * `teamId === null` 이면 비활성 (예: 라우트 미진입). 캐시 키는 `qk.teams.members(teamId)`.
 */
export function useTeamMembers(teamId: string | null) {
  return useQuery({
    queryKey: teamId ? qk.teams.members(teamId) : ['__skip__'],
    queryFn: () => api.getTeamMembers(teamId!),
    enabled: !!teamId,
  })
}
```

- [ ] **Step 4: Run test to verify pass**

```
cd frontend && pnpm test src/hooks/useTeamMembers.test.tsx --run
```
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```
git add frontend/src/hooks/useTeamMembers.ts frontend/src/hooks/useTeamMembers.test.tsx
git commit -m "feat(team-centric-pivot): useTeamMembers hook (Plan F T9)"
```

---

## Phase 3 — Frontend mutations (T10~T12)

### T10: useInviteTeamMember

**Files:**
- Create: `frontend/src/hooks/useInviteTeamMember.ts`
- Create: `frontend/src/hooks/useInviteTeamMember.test.tsx`

- [ ] **Step 1: Write failing test**

`frontend/src/hooks/useInviteTeamMember.test.tsx`:

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useInviteTeamMember } from './useInviteTeamMember'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'

vi.mock('@/lib/api', () => ({ api: { inviteTeamMember: vi.fn() } }))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

describe('useInviteTeamMember', () => {
  beforeEach(() => vi.clearAllMocks())

  it('invites and invalidates qk.teams.members(teamId)', async () => {
    ;(api.inviteTeamMember as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const spy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useInviteTeamMember('team-1'), { wrapper: wrap(qc) })

    act(() => {
      result.current.mutate({ userId: 'user-2' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(api.inviteTeamMember).toHaveBeenCalledWith('team-1', 'user-2')
    expect(spy).toHaveBeenCalledWith({ queryKey: qk.teams.members('team-1') })
  })
})
```

- [ ] **Step 2: Run to confirm fail**

```
cd frontend && pnpm test src/hooks/useInviteTeamMember.test.tsx --run
```
Expected: FAIL.

- [ ] **Step 3: Implement hook**

`frontend/src/hooks/useInviteTeamMember.ts`:

```typescript
'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { invalidations } from '@/lib/queryKeys'

/** Plan F T10 — 팀에 사용자 초대. */
export function useInviteTeamMember(teamId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ userId }: { userId: string }) => api.inviteTeamMember(teamId, userId),
    onSuccess: () => invalidations.afterTeamMembersChanged(qc, teamId),
  })
}
```

- [ ] **Step 4: Run test to verify pass**

```
cd frontend && pnpm test src/hooks/useInviteTeamMember.test.tsx --run
```
Expected: PASS.

- [ ] **Step 5: Commit**

```
git add frontend/src/hooks/useInviteTeamMember.ts frontend/src/hooks/useInviteTeamMember.test.tsx
git commit -m "feat(team-centric-pivot): useInviteTeamMember hook (Plan F T10)"
```

---

### T11: useRemoveTeamMember

**Files:**
- Create: `frontend/src/hooks/useRemoveTeamMember.ts`
- Create: `frontend/src/hooks/useRemoveTeamMember.test.tsx`

- [ ] **Step 1: Write failing test**

`frontend/src/hooks/useRemoveTeamMember.test.tsx`:

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useRemoveTeamMember } from './useRemoveTeamMember'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'

vi.mock('@/lib/api', () => ({ api: { removeTeamMember: vi.fn() } }))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

describe('useRemoveTeamMember', () => {
  beforeEach(() => vi.clearAllMocks())

  it('removes and invalidates qk.teams.members(teamId)', async () => {
    ;(api.removeTeamMember as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const spy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useRemoveTeamMember('team-1'), { wrapper: wrap(qc) })

    act(() => result.current.mutate({ userId: 'user-2' }))
    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(api.removeTeamMember).toHaveBeenCalledWith('team-1', 'user-2')
    expect(spy).toHaveBeenCalledWith({ queryKey: qk.teams.members('team-1') })
  })

  it('400 TEAM_OWNER_REQUIRED → isError + skip invalidate', async () => {
    const err = Object.assign(new Error('removeTeamMember failed: 400'), {
      status: 400,
      code: 'TEAM_OWNER_REQUIRED',
    })
    ;(api.removeTeamMember as ReturnType<typeof vi.fn>).mockRejectedValue(err)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const spy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useRemoveTeamMember('team-1'), { wrapper: wrap(qc) })

    act(() => result.current.mutate({ userId: 'user-2' }))
    await waitFor(() => expect(result.current.isError).toBe(true))
    expect((result.current.error as Error & { code?: string }).code).toBe('TEAM_OWNER_REQUIRED')
    expect(spy).not.toHaveBeenCalled()
  })
})
```

- [ ] **Step 2: Run to confirm fail**

```
cd frontend && pnpm test src/hooks/useRemoveTeamMember.test.tsx --run
```
Expected: FAIL.

- [ ] **Step 3: Implement hook**

`frontend/src/hooks/useRemoveTeamMember.ts`:

```typescript
'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { invalidations } from '@/lib/queryKeys'

/** Plan F T11 — 팀에서 멤버 제거. self-remove 처리는 호출자가 router.push로 분기. */
export function useRemoveTeamMember(teamId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ userId }: { userId: string }) => api.removeTeamMember(teamId, userId),
    onSuccess: () => invalidations.afterTeamMembersChanged(qc, teamId),
  })
}
```

- [ ] **Step 4: Run test to verify pass**

```
cd frontend && pnpm test src/hooks/useRemoveTeamMember.test.tsx --run
```
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```
git add frontend/src/hooks/useRemoveTeamMember.ts frontend/src/hooks/useRemoveTeamMember.test.tsx
git commit -m "feat(team-centric-pivot): useRemoveTeamMember hook (Plan F T11)"
```

---

### T12: useChangeTeamMemberRole

**Files:**
- Create: `frontend/src/hooks/useChangeTeamMemberRole.ts`
- Create: `frontend/src/hooks/useChangeTeamMemberRole.test.tsx`

- [ ] **Step 1: Write failing test**

`frontend/src/hooks/useChangeTeamMemberRole.test.tsx`:

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useChangeTeamMemberRole } from './useChangeTeamMemberRole'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'

vi.mock('@/lib/api', () => ({ api: { changeTeamMemberRole: vi.fn() } }))

function wrap(qc: QueryClient) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
  Wrapper.displayName = 'Wrapper'
  return Wrapper
}

describe('useChangeTeamMemberRole', () => {
  beforeEach(() => vi.clearAllMocks())

  it('changes role and invalidates qk.teams.members(teamId)', async () => {
    ;(api.changeTeamMemberRole as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const spy = vi.spyOn(qc, 'invalidateQueries')
    const { result } = renderHook(() => useChangeTeamMemberRole('team-1'), { wrapper: wrap(qc) })

    act(() => result.current.mutate({ userId: 'user-2', role: 'OWNER' }))
    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(api.changeTeamMemberRole).toHaveBeenCalledWith('team-1', 'user-2', 'OWNER')
    expect(spy).toHaveBeenCalledWith({ queryKey: qk.teams.members('team-1') })
  })
})
```

- [ ] **Step 2: Run to confirm fail**

```
cd frontend && pnpm test src/hooks/useChangeTeamMemberRole.test.tsx --run
```
Expected: FAIL.

- [ ] **Step 3: Implement hook**

`frontend/src/hooks/useChangeTeamMemberRole.ts`:

```typescript
'use client'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { invalidations } from '@/lib/queryKeys'
import type { TeamMemberRole } from '@/types/team'

/** Plan F T12 — 팀 멤버 역할 변경. last-OWNER 강등 시도 시 400 TEAM_OWNER_REQUIRED. */
export function useChangeTeamMemberRole(teamId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: TeamMemberRole }) =>
      api.changeTeamMemberRole(teamId, userId, role),
    onSuccess: () => invalidations.afterTeamMembersChanged(qc, teamId),
  })
}
```

- [ ] **Step 4: Run test to verify pass**

```
cd frontend && pnpm test src/hooks/useChangeTeamMemberRole.test.tsx --run
```
Expected: PASS.

- [ ] **Step 5: Commit**

```
git add frontend/src/hooks/useChangeTeamMemberRole.ts frontend/src/hooks/useChangeTeamMemberRole.test.tsx
git commit -m "feat(team-centric-pivot): useChangeTeamMemberRole hook (Plan F T12)"
```

---

## Phase 4 — Frontend UI (T13~T17)

### T13: TeamMemberTable component

**Files:**
- Create: `frontend/src/components/team/TeamMemberTable.tsx`
- Create: `frontend/src/components/team/TeamMemberTable.test.tsx`

- [ ] **Step 1: Write failing test**

`frontend/src/components/team/TeamMemberTable.test.tsx`:

```typescript
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { TeamMemberTable } from './TeamMemberTable'
import type { TeamMember } from '@/types/team'

const M1: TeamMember = {
  userId: 'u1', displayName: 'Alice', email: 'a@x.io', role: 'OWNER',
  joinedAt: '2026-05-10T00:00:00Z',
}
const M2: TeamMember = {
  userId: 'u2', displayName: 'Bob', email: 'b@x.io', role: 'MEMBER',
  joinedAt: '2026-05-10T01:00:00Z',
}

describe('TeamMemberTable', () => {
  it('renders 3 columns and member rows', () => {
    render(
      <TeamMemberTable
        members={[M1, M2]}
        currentUserId="u1"
        canManage={true}
        onChangeRole={vi.fn()}
        onRemove={vi.fn()}
      />,
    )
    expect(screen.getByText('Alice')).toBeInTheDocument()
    expect(screen.getByText('Bob')).toBeInTheDocument()
    expect(screen.getAllByRole('row')).toHaveLength(3) // header + 2 members
  })

  it('hides action buttons when canManage=false', () => {
    render(
      <TeamMemberTable
        members={[M1, M2]}
        currentUserId="u1"
        canManage={false}
        onChangeRole={vi.fn()}
        onRemove={vi.fn()}
      />,
    )
    expect(screen.queryByRole('button', { name: /역할 변경/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /제거/i })).not.toBeInTheDocument()
  })

  it('clicking 역할 변경 calls onChangeRole with member', async () => {
    const onChangeRole = vi.fn()
    const { getAllByRole } = render(
      <TeamMemberTable
        members={[M1, M2]}
        currentUserId="u1"
        canManage={true}
        onChangeRole={onChangeRole}
        onRemove={vi.fn()}
      />,
    )
    const buttons = getAllByRole('button', { name: /역할 변경/i })
    buttons[1].click() // Bob's row
    expect(onChangeRole).toHaveBeenCalledWith(M2)
  })
})
```

- [ ] **Step 2: Run to confirm fail**

```
cd frontend && pnpm test src/components/team/TeamMemberTable.test.tsx --run
```
Expected: FAIL.

- [ ] **Step 3: Implement component**

`frontend/src/components/team/TeamMemberTable.tsx`:

```typescript
'use client'
import type { TeamMember } from '@/types/team'

export function TeamMemberTable({
  members, currentUserId, canManage, onChangeRole, onRemove,
}: {
  members: TeamMember[]
  currentUserId: string | null
  canManage: boolean
  onChangeRole: (member: TeamMember) => void
  onRemove: (member: TeamMember) => void
}) {
  return (
    <table className="w-full text-[13px]">
      <thead className="text-fg-muted text-[12px]">
        <tr>
          <th className="text-left py-2 px-3">이름</th>
          <th className="text-left py-2 px-3">역할</th>
          <th className="text-left py-2 px-3">가입일</th>
          {canManage && <th className="text-right py-2 px-3">액션</th>}
        </tr>
      </thead>
      <tbody>
        {members.map((m) => (
          <tr key={m.userId} className="border-t border-border hover:bg-surface-2">
            <td className="py-2 px-3">
              <div className="flex flex-col">
                <span className={m.userId === currentUserId ? 'font-semibold' : undefined}>
                  {m.displayName}
                  {m.userId === currentUserId && <span className="ml-1 text-fg-muted">(나)</span>}
                </span>
                <span className="text-[12px] text-fg-muted">{m.email}</span>
              </div>
            </td>
            <td className="py-2 px-3">{m.role}</td>
            <td className="py-2 px-3 text-fg-muted">
              {new Date(m.joinedAt).toLocaleDateString('ko-KR')}
            </td>
            {canManage && (
              <td className="py-2 px-3 text-right">
                <button
                  type="button"
                  onClick={() => onChangeRole(m)}
                  className="text-[12px] underline mr-2"
                >
                  역할 변경
                </button>
                <button
                  type="button"
                  onClick={() => onRemove(m)}
                  className="text-[12px] text-danger underline"
                >
                  제거
                </button>
              </td>
            )}
          </tr>
        ))}
      </tbody>
    </table>
  )
}
```

- [ ] **Step 4: Run test to verify pass**

```
cd frontend && pnpm test src/components/team/TeamMemberTable.test.tsx --run
```
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```
git add frontend/src/components/team/TeamMemberTable.tsx frontend/src/components/team/TeamMemberTable.test.tsx
git commit -m "feat(team-centric-pivot): TeamMemberTable component (Plan F T13)"
```

---

### T14: InviteMemberDialog component

**Files:**
- Create: `frontend/src/components/team/InviteMemberDialog.tsx`
- Create: `frontend/src/components/team/InviteMemberDialog.test.tsx`

- [ ] **Step 1: Write failing test**

`frontend/src/components/team/InviteMemberDialog.test.tsx`:

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { InviteMemberDialog } from './InviteMemberDialog'
import { api } from '@/lib/api'

vi.mock('@/lib/api', () => ({
  api: {
    inviteTeamMember: vi.fn(),
    searchUsers: vi.fn().mockResolvedValue([
      { id: 'u-2', displayName: 'Bob', email: 'b@x.io' },
    ]),
  },
}))

function wrap(children: React.ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('InviteMemberDialog', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders dialog with role=ARIA + UserSearchCombobox', () => {
    render(wrap(<InviteMemberDialog teamId="team-1" onClose={vi.fn()} />))
    expect(screen.getByRole('dialog', { name: /멤버 초대/ })).toBeInTheDocument()
  })

  it('Esc closes dialog', () => {
    const onClose = vi.fn()
    render(wrap(<InviteMemberDialog teamId="team-1" onClose={onClose} />))
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })
    expect(onClose).toHaveBeenCalled()
  })
})
```

> Note: full submit flow tests cover only essentials (UserSearchCombobox is reused — already tested separately). Keep this test focused.

- [ ] **Step 2: Run to confirm fail**

```
cd frontend && pnpm test src/components/team/InviteMemberDialog.test.tsx --run
```
Expected: FAIL.

- [ ] **Step 3: Implement component**

`frontend/src/components/team/InviteMemberDialog.tsx`:

```typescript
'use client'
import { useState } from 'react'
import { UserSearchCombobox } from '@/components/shares/UserSearchCombobox'
import { useInviteTeamMember } from '@/hooks/useInviteTeamMember'

export function InviteMemberDialog({
  teamId, onClose,
}: { teamId: string; onClose: () => void }) {
  const [selected, setSelected] = useState<{ id: string; displayName: string } | null>(null)
  const invite = useInviteTeamMember(teamId)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (!selected) return
    try {
      await invite.mutateAsync({ userId: selected.id })
      onClose()
    } catch {
      // invite.isError handles inline display
    }
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="멤버 초대"
      onKeyDown={(e) => { if (e.key === 'Escape') onClose() }}
      className="fixed inset-0 flex items-center justify-center bg-black/40 z-50"
    >
      <form
        onSubmit={submit}
        className="bg-surface-1 border border-border rounded p-4 w-[420px] flex flex-col gap-3"
      >
        <h2 className="text-[14px] font-semibold">멤버 초대</h2>
        <label className="flex flex-col gap-1 text-[12px]">
          사용자 검색 *
          <UserSearchCombobox
            value={selected}
            onChange={setSelected}
            ariaLabel="사용자 검색"
          />
        </label>
        {invite.isError && (
          <p role="alert" className="text-[12px] text-danger">
            초대 실패: {String(invite.error)}
          </p>
        )}
        <div className="flex justify-end gap-2 pt-2">
          <button type="button" onClick={onClose} className="px-3 py-1 text-[12px]">
            취소
          </button>
          <button
            type="submit"
            disabled={!selected || invite.isPending}
            className="px-3 py-1 bg-accent text-white text-[12px] rounded disabled:opacity-50"
          >
            {invite.isPending ? '초대 중...' : '초대'}
          </button>
        </div>
      </form>
    </div>
  )
}
```

> Verify `UserSearchCombobox` props (`value`, `onChange`, `ariaLabel`) by reading the component before implementing — adjust if the actual prop names differ.

- [ ] **Step 4: Run test to verify pass**

```
cd frontend && pnpm test src/components/team/InviteMemberDialog.test.tsx --run
```
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```
git add frontend/src/components/team/InviteMemberDialog.tsx frontend/src/components/team/InviteMemberDialog.test.tsx
git commit -m "feat(team-centric-pivot): InviteMemberDialog component (Plan F T14)"
```

---

### T15: ChangeRoleDialog + RemoveMemberDialog components

**Files:**
- Create: `frontend/src/components/team/ChangeRoleDialog.tsx`
- Create: `frontend/src/components/team/ChangeRoleDialog.test.tsx`
- Create: `frontend/src/components/team/RemoveMemberDialog.tsx`
- Create: `frontend/src/components/team/RemoveMemberDialog.test.tsx`

- [ ] **Step 1: Write failing test for ChangeRoleDialog**

`frontend/src/components/team/ChangeRoleDialog.test.tsx`:

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ChangeRoleDialog } from './ChangeRoleDialog'
import { api } from '@/lib/api'
import type { TeamMember } from '@/types/team'

vi.mock('@/lib/api', () => ({ api: { changeTeamMemberRole: vi.fn() } }))

const M: TeamMember = {
  userId: 'u-2', displayName: 'Bob', email: 'b@x.io', role: 'MEMBER',
  joinedAt: '2026-05-10T00:00:00Z',
}

function wrap(children: React.ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('ChangeRoleDialog', () => {
  beforeEach(() => vi.clearAllMocks())

  it('submits with selected new role', async () => {
    ;(api.changeTeamMemberRole as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
    const onClose = vi.fn()
    render(wrap(<ChangeRoleDialog teamId="team-1" member={M} onClose={onClose} />))

    // default targetRole switches MEMBER → OWNER
    fireEvent.click(screen.getByRole('button', { name: /변경/ }))
    await waitFor(() => expect(api.changeTeamMemberRole).toHaveBeenCalledWith('team-1', 'u-2', 'OWNER'))
    await waitFor(() => expect(onClose).toHaveBeenCalled())
  })

  it('TEAM_OWNER_REQUIRED 에러 inline 표시', async () => {
    const err = Object.assign(new Error('changeTeamMemberRole failed: 400'), {
      status: 400, code: 'TEAM_OWNER_REQUIRED',
    })
    ;(api.changeTeamMemberRole as ReturnType<typeof vi.fn>).mockRejectedValue(err)
    render(wrap(<ChangeRoleDialog teamId="team-1" member={{ ...M, role: 'OWNER' }} onClose={vi.fn()} />))
    fireEvent.click(screen.getByRole('button', { name: /변경/ }))
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/최소 한 명의 OWNER/)
    })
  })
})
```

- [ ] **Step 2: Implement ChangeRoleDialog**

`frontend/src/components/team/ChangeRoleDialog.tsx`:

```typescript
'use client'
import { useChangeTeamMemberRole } from '@/hooks/useChangeTeamMemberRole'
import { TEAM_OWNER_REQUIRED } from '@/lib/errors'
import type { TeamMember, TeamMemberRole } from '@/types/team'

export function ChangeRoleDialog({
  teamId, member, onClose,
}: { teamId: string; member: TeamMember; onClose: () => void }) {
  const targetRole: TeamMemberRole = member.role === 'OWNER' ? 'MEMBER' : 'OWNER'
  const change = useChangeTeamMemberRole(teamId)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    try {
      await change.mutateAsync({ userId: member.userId, role: targetRole })
      onClose()
    } catch {
      // change.isError handles inline display
    }
  }

  const errCode = (change.error as Error & { code?: string } | undefined)?.code
  const errMsg = errCode === TEAM_OWNER_REQUIRED
    ? '팀에 최소 한 명의 OWNER가 필요합니다'
    : change.isError ? `변경 실패: ${String(change.error)}` : null

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="역할 변경"
      onKeyDown={(e) => { if (e.key === 'Escape') onClose() }}
      className="fixed inset-0 flex items-center justify-center bg-black/40 z-50"
    >
      <form
        onSubmit={submit}
        className="bg-surface-1 border border-border rounded p-4 w-[420px] flex flex-col gap-3"
      >
        <h2 className="text-[14px] font-semibold">역할 변경</h2>
        <p className="text-[13px]">
          <strong>{member.displayName}</strong> 의 역할을{' '}
          <strong>{member.role}</strong> → <strong>{targetRole}</strong> 로 변경합니다.
        </p>
        {errMsg && (
          <p role="alert" className="text-[12px] text-danger">
            {errMsg}
          </p>
        )}
        <div className="flex justify-end gap-2 pt-2">
          <button type="button" onClick={onClose} className="px-3 py-1 text-[12px]">
            취소
          </button>
          <button
            type="submit"
            disabled={change.isPending}
            className="px-3 py-1 bg-accent text-white text-[12px] rounded disabled:opacity-50"
          >
            {change.isPending ? '변경 중...' : '변경'}
          </button>
        </div>
      </form>
    </div>
  )
}
```

- [ ] **Step 3: Run ChangeRoleDialog test**

```
cd frontend && pnpm test src/components/team/ChangeRoleDialog.test.tsx --run
```
Expected: PASS (2 tests).

- [ ] **Step 4: Write failing test for RemoveMemberDialog**

`frontend/src/components/team/RemoveMemberDialog.test.tsx`:

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RemoveMemberDialog } from './RemoveMemberDialog'
import { api } from '@/lib/api'
import type { TeamMember } from '@/types/team'

vi.mock('@/lib/api', () => ({ api: { removeTeamMember: vi.fn() } }))

const M: TeamMember = {
  userId: 'u-2', displayName: 'Bob', email: 'b@x.io', role: 'MEMBER',
  joinedAt: '2026-05-10T00:00:00Z',
}

function wrap(children: React.ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('RemoveMemberDialog', () => {
  beforeEach(() => vi.clearAllMocks())

  it('confirms and removes member', async () => {
    ;(api.removeTeamMember as ReturnType<typeof vi.fn>).mockResolvedValue(undefined)
    const onClose = vi.fn()
    render(wrap(<RemoveMemberDialog teamId="team-1" member={M} onClose={onClose} />))
    fireEvent.click(screen.getByRole('button', { name: /제거/ }))
    await waitFor(() => expect(api.removeTeamMember).toHaveBeenCalledWith('team-1', 'u-2'))
    await waitFor(() => expect(onClose).toHaveBeenCalled())
  })

  it('TEAM_OWNER_REQUIRED 에러 inline', async () => {
    const err = Object.assign(new Error('removeTeamMember failed: 400'), {
      status: 400, code: 'TEAM_OWNER_REQUIRED',
    })
    ;(api.removeTeamMember as ReturnType<typeof vi.fn>).mockRejectedValue(err)
    render(wrap(<RemoveMemberDialog teamId="team-1" member={{ ...M, role: 'OWNER' }} onClose={vi.fn()} />))
    fireEvent.click(screen.getByRole('button', { name: /제거/ }))
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/최소 한 명의 OWNER/)
    })
  })
})
```

- [ ] **Step 5: Implement RemoveMemberDialog**

`frontend/src/components/team/RemoveMemberDialog.tsx`:

```typescript
'use client'
import { useRemoveTeamMember } from '@/hooks/useRemoveTeamMember'
import { TEAM_OWNER_REQUIRED } from '@/lib/errors'
import type { TeamMember } from '@/types/team'

export function RemoveMemberDialog({
  teamId, member, onClose,
}: { teamId: string; member: TeamMember; onClose: () => void }) {
  const remove = useRemoveTeamMember(teamId)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    try {
      await remove.mutateAsync({ userId: member.userId })
      onClose()
    } catch {
      // remove.isError handles inline display
    }
  }

  const errCode = (remove.error as Error & { code?: string } | undefined)?.code
  const errMsg = errCode === TEAM_OWNER_REQUIRED
    ? '팀에 최소 한 명의 OWNER가 필요합니다'
    : remove.isError ? `제거 실패: ${String(remove.error)}` : null

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="멤버 제거"
      onKeyDown={(e) => { if (e.key === 'Escape') onClose() }}
      className="fixed inset-0 flex items-center justify-center bg-black/40 z-50"
    >
      <form
        onSubmit={submit}
        className="bg-surface-1 border border-border rounded p-4 w-[420px] flex flex-col gap-3"
      >
        <h2 className="text-[14px] font-semibold">멤버 제거</h2>
        <p className="text-[13px]">
          <strong>{member.displayName}</strong> 을(를) 팀에서 제거합니다.
        </p>
        {errMsg && (
          <p role="alert" className="text-[12px] text-danger">
            {errMsg}
          </p>
        )}
        <div className="flex justify-end gap-2 pt-2">
          <button type="button" onClick={onClose} className="px-3 py-1 text-[12px]">
            취소
          </button>
          <button
            type="submit"
            disabled={remove.isPending}
            className="px-3 py-1 bg-danger text-white text-[12px] rounded disabled:opacity-50"
          >
            {remove.isPending ? '제거 중...' : '제거'}
          </button>
        </div>
      </form>
    </div>
  )
}
```

- [ ] **Step 6: Run RemoveMemberDialog test**

```
cd frontend && pnpm test src/components/team/RemoveMemberDialog.test.tsx --run
```
Expected: PASS (2 tests).

- [ ] **Step 7: Commit**

```
git add frontend/src/components/team/ChangeRoleDialog.tsx frontend/src/components/team/ChangeRoleDialog.test.tsx frontend/src/components/team/RemoveMemberDialog.tsx frontend/src/components/team/RemoveMemberDialog.test.tsx
git commit -m "feat(team-centric-pivot): ChangeRoleDialog + RemoveMemberDialog (Plan F T15)"
```

---

### T16: /t/[teamId]/settings/members route

**Files:**
- Create: `frontend/src/app/(explorer)/t/[teamId]/settings/members/page.tsx`
- Create: `frontend/src/app/(explorer)/t/[teamId]/settings/members/ClientMembersPage.tsx`
- Create: `frontend/src/app/(explorer)/t/[teamId]/settings/members/ClientMembersPage.test.tsx`

- [ ] **Step 1: Write failing test for ClientMembersPage**

`frontend/src/app/(explorer)/t/[teamId]/settings/members/ClientMembersPage.test.tsx`:

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ClientMembersPage } from './ClientMembersPage'
import { api } from '@/lib/api'

vi.mock('@/lib/api', () => ({
  api: {
    getTeamMembers: vi.fn(),
    inviteTeamMember: vi.fn(),
    removeTeamMember: vi.fn(),
    changeTeamMemberRole: vi.fn(),
  },
}))

vi.mock('@/hooks/useMe', () => ({
  useMe: () => ({ data: { id: 'u1' } }),
}))

function wrap(children: React.ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('ClientMembersPage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders member table after fetch', async () => {
    ;(api.getTeamMembers as ReturnType<typeof vi.fn>).mockResolvedValue([
      { userId: 'u1', displayName: 'Alice', email: 'a@x.io', role: 'OWNER', joinedAt: '2026-05-10T00:00:00Z' },
      { userId: 'u2', displayName: 'Bob', email: 'b@x.io', role: 'MEMBER', joinedAt: '2026-05-10T01:00:00Z' },
    ])
    render(wrap(<ClientMembersPage teamId="team-1" />))
    await waitFor(() => {
      expect(screen.getByText('Alice')).toBeInTheDocument()
      expect(screen.getByText('Bob')).toBeInTheDocument()
    })
  })

  it('403 → forbidden state', async () => {
    const err = Object.assign(new Error('getTeamMembers failed: 403'), {
      status: 403, code: 'PERMISSION_DENIED',
    })
    ;(api.getTeamMembers as ReturnType<typeof vi.fn>).mockRejectedValue(err)
    render(wrap(<ClientMembersPage teamId="team-1" />))
    await waitFor(() => {
      expect(screen.getByText(/접근 권한이 없습니다/)).toBeInTheDocument()
    })
  })
})
```

- [ ] **Step 2: Implement page entry (server component)**

`frontend/src/app/(explorer)/t/[teamId]/settings/members/page.tsx`:

```typescript
import { ClientMembersPage } from './ClientMembersPage'

export default async function Page({ params }: { params: Promise<{ teamId: string }> }) {
  const { teamId } = await params
  return <ClientMembersPage teamId={teamId} />
}
```

> Next.js 15 App Router params is a Promise — match Plan B's `t/[teamId]/[[...parts]]/page.tsx` pattern. Read that file first if uncertain.

- [ ] **Step 3: Implement client page**

`frontend/src/app/(explorer)/t/[teamId]/settings/members/ClientMembersPage.tsx`:

```typescript
'use client'
import { useState } from 'react'
import { useTeamMembers } from '@/hooks/useTeamMembers'
import { useMe } from '@/hooks/useMe'
import { TeamMemberTable } from '@/components/team/TeamMemberTable'
import { InviteMemberDialog } from '@/components/team/InviteMemberDialog'
import { ChangeRoleDialog } from '@/components/team/ChangeRoleDialog'
import { RemoveMemberDialog } from '@/components/team/RemoveMemberDialog'
import type { TeamMember } from '@/types/team'

export function ClientMembersPage({ teamId }: { teamId: string }) {
  const me = useMe()
  const members = useTeamMembers(teamId)
  const [inviteOpen, setInviteOpen] = useState(false)
  const [changeTarget, setChangeTarget] = useState<TeamMember | null>(null)
  const [removeTarget, setRemoveTarget] = useState<TeamMember | null>(null)

  const myMember = members.data?.find((m) => m.userId === me.data?.id)
  const canManage = myMember?.role === 'OWNER'

  if (members.isLoading) {
    return <div className="p-6 text-fg-muted">멤버 로딩 중…</div>
  }
  const errCode = (members.error as Error & { code?: string } | undefined)?.code
  if (errCode === 'PERMISSION_DENIED') {
    return <div className="p-6 text-danger">접근 권한이 없습니다.</div>
  }
  if (members.isError) {
    return <div className="p-6 text-danger">멤버 로드 실패: {String(members.error)}</div>
  }

  return (
    <div className="p-6 flex flex-col gap-4">
      <header className="flex items-center justify-between">
        <h1 className="text-[18px] font-semibold">팀 멤버</h1>
        {canManage && (
          <button
            type="button"
            onClick={() => setInviteOpen(true)}
            className="px-3 py-1.5 bg-accent text-white text-[13px] rounded"
          >
            멤버 초대
          </button>
        )}
      </header>
      <TeamMemberTable
        members={members.data ?? []}
        currentUserId={me.data?.id ?? null}
        canManage={canManage}
        onChangeRole={setChangeTarget}
        onRemove={setRemoveTarget}
      />
      {inviteOpen && (
        <InviteMemberDialog teamId={teamId} onClose={() => setInviteOpen(false)} />
      )}
      {changeTarget && (
        <ChangeRoleDialog
          teamId={teamId}
          member={changeTarget}
          onClose={() => setChangeTarget(null)}
        />
      )}
      {removeTarget && (
        <RemoveMemberDialog
          teamId={teamId}
          member={removeTarget}
          onClose={() => setRemoveTarget(null)}
        />
      )}
    </div>
  )
}
```

- [ ] **Step 4: Run page test**

```
cd frontend && pnpm test src/app/\(explorer\)/t/\[teamId\]/settings/members/ClientMembersPage.test.tsx --run
```

(On Windows shell, escape parens / brackets per shell rules, or use `pnpm test --run "**/ClientMembersPage.test.tsx"`.)

Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```
git add "frontend/src/app/(explorer)/t/[teamId]/settings/members"
git commit -m "feat(team-centric-pivot): /t/{teamId}/settings/members route + ClientMembersPage (Plan F T16)"
```

---

### T17: WorkspaceSection context menu wire (settings link)

**Files:**
- Modify: `frontend/src/components/sidebar/WorkspaceSection.tsx`
- Modify: `frontend/src/components/sidebar/SidebarSections.test.tsx` (or add component-level test if absent)

- [ ] **Step 1: Read existing WorkspaceSection and decide minimal addition**

Current `WorkspaceSection.tsx` (lines 1-37) is wrapper-only. Plan F adds a Settings link rendered next to the section header **only when `kind === 'team'`**. UX: appears on hover near the team title.

- [ ] **Step 2: Add a settings link in WorkspaceSection**

Replace `WorkspaceSection.tsx` with:

```typescript
'use client'
import Link from 'next/link'
import { WorkspaceFolderTree } from './WorkspaceFolderTree'

export function WorkspaceSection({
  kind, workspaceId, title, rootFolderId, archived = false,
}: {
  kind: 'department' | 'team'
  workspaceId: string
  title: string
  rootFolderId: string
  archived?: boolean
}) {
  return (
    <div className={archived ? 'opacity-60 group' : 'group'}>
      {archived && (
        <span aria-label="보관됨" className="ml-2 text-[11px] text-fg-muted font-medium">
          [보관됨]
        </span>
      )}
      {kind === 'team' && !archived && (
        <Link
          href={`/t/${workspaceId}/settings/members`}
          className="float-right opacity-0 group-hover:opacity-100 text-[11px] text-fg-muted hover:underline"
          aria-label={`${title} 팀 설정`}
        >
          설정
        </Link>
      )}
      <WorkspaceFolderTree
        kind={kind}
        workspaceId={workspaceId}
        rootFolderId={rootFolderId}
        rootName={title}
      />
    </div>
  )
}
```

- [ ] **Step 3: TypeScript check + lint**

```
cd frontend && pnpm typecheck && pnpm lint
```
Expected: exit 0 both.

- [ ] **Step 4: Manual smoke test**

```
cd frontend && pnpm dev
```
Open `http://localhost:3000`. Hover over a team in sidebar — `설정` link appears at right. Click → navigate to `/t/{teamId}/settings/members`. Verify member table renders.

- [ ] **Step 5: Commit**

```
git add frontend/src/components/sidebar/WorkspaceSection.tsx
git commit -m "feat(team-centric-pivot): WorkspaceSection — team 설정 link (Plan F T17)"
```

---

## Phase 5 — Closure (T18)

### T18: Docs sync + progress + PR

**Files:**
- Modify: `docs/02-backend-data-model.md` (§7 endpoint table)
- Modify: `docs/progress.md`

- [ ] **Step 1: Update docs/02 §7 endpoint table**

Find the team endpoints section in `docs/02-backend-data-model.md §7` (likely §7 something with team). Add the two new rows:

| Method | Path | Auth | 응답 |
|---|---|---|---|
| GET | /api/teams/{teamId}/members | member | 200 List<TeamMemberResponse> |
| PATCH | /api/teams/{teamId}/members/{userId} | OWNER | 204 |

Match the existing table's exact column conventions (read the section first). If `docs/02 §7` does not have a team subsection, add one in the existing format.

- [ ] **Step 2: Update progress.md**

Prepend to `docs/progress.md` (after the file header):

```markdown
## 2026-05-10 — 🎯 team-centric-pivot Plan F (Team Member Management)

### 범위

사내 시스템 일상 운영 gap 마무리: 팀 OWNER가 UI로 멤버 목록 조회 / 초대 /
역할 변경 / 제거. backend는 service에 이미 있는 메서드 1:1 wire (신규 도메인 zero).

### 변경 핵심

**Backend (T1~T6)**:
- TeamMemberResponse / TeamMemberRoleUpdateRequest DTO
- TeamMembershipRepository.findMembersWithUser (JPQL constructor projection)
- TeamService.listMembers
- TeamAuthz.isMember
- TeamController GET /members + PATCH /members/{userId}

**Frontend (T7~T17)**:
- qk.teams.members(teamId) + invalidations.afterTeamMembersChanged
- api.{getTeamMembers, inviteTeamMember, removeTeamMember, changeTeamMemberRole}
- 4 hooks: useTeamMembers, useInviteTeamMember, useRemoveTeamMember, useChangeTeamMemberRole
- 4 components: TeamMemberTable, InviteMemberDialog, ChangeRoleDialog, RemoveMemberDialog
- /t/{teamId}/settings/members 라우트 + WorkspaceSection 설정 link

### YAGNI 명시 제외

- 팀 archive/restore UI — 사내 빈도 매우 낮음
- 일괄 invite (CSV) — 한 번 클릭으로 충분
- 멤버 검색/필터링 — 30명 미만 팀 가정
- 멤버 활동 로그 페이지 — admin audit 페이지로 충분
- archive 가드 — 데이터 플레인은 team-archive-write-enforcement 트랙

### 검증

- backend `./gradlew test` PASS
- frontend `pnpm typecheck && pnpm lint && pnpm test` PASS
- 수동: 팀 생성 → settings/members → invite → role 변경 → remove → last-OWNER 강등 차단 confirm

### 다음 세션 컨텍스트

- 다른 진행 트랙(Plan C/D/E, team-archive-write-enforcement)과 영역 disjoint — 머지 순서 무관.
```

- [ ] **Step 3: Run full test suites**

```
cd backend && ./gradlew test
cd frontend && pnpm typecheck && pnpm lint && pnpm test --run
```
Expected: all PASS.

- [ ] **Step 4: Push and create PR**

```
git push -u origin feat/team-centric-pivot-plan-f-team-member-mgmt
gh pr create --title "feat(team-centric-pivot): Plan F — Team Member Management" --body "$(cat <<'EOF'
## Summary

사내 시스템 팀 워크스페이스 일상 운영의 마지막 gap. OWNER가 UI로 멤버 목록 조회 / 초대 /
역할 변경 / 제거할 수 있게 한다. backend는 Plan A/A2 service 메서드를 controller에 1:1 wire —
신규 도메인 / 신규 추상화 zero.

## 변경 (T1~T18)

**Backend**: TeamMemberResponse·TeamMemberRoleUpdateRequest DTO,
TeamMembershipRepository.findMembersWithUser (JPQL constructor projection),
TeamService.listMembers, TeamAuthz.isMember, TeamController GET /members + PATCH /role.

**Frontend**: qk.teams.members + invalidation helper, api 4 methods, 4 hooks,
TeamMemberTable / InviteMemberDialog / ChangeRoleDialog / RemoveMemberDialog,
/t/{teamId}/settings/members 라우트, WorkspaceSection 설정 link.

## YAGNI 명시 제외

archive UI / 일괄 invite / 멤버 검색·필터 / 활동 로그 페이지 / archive 가드 (데이터 플레인은
team-archive-write-enforcement 트랙). 사용자 요청: "꼭 필요한 기능만, 사내라 디테일 과잉 금지."

## 다른 트랙과의 관계

Plan C (PR #140 share team subject) / Plan D (PR #138 cross-workspace move) /
Plan E (trash split, 미시작) / team-archive-write-enforcement 와 파일 영역 disjoint —
머지 순서 무관.

## Test plan

- [x] backend `./gradlew test` PASS
- [x] frontend `pnpm typecheck && pnpm lint && pnpm test` PASS
- [x] 수동: 팀 생성 → settings/members → invite → role 변경 → remove → last-OWNER 강등 차단

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 5: Commit docs**

```
git add docs/02-backend-data-model.md docs/progress.md
git commit -m "docs(team-centric-pivot): Plan F — §7 endpoint + progress.md closure (T18)"
git push
```

---

## Self-Review

**Spec coverage check** (spec §7 vs plan tasks):

| Spec task | Plan task |
|---|---|
| Backend 1: DTOs | T1 |
| Backend 2: Repository | T2 |
| Backend 3: Service | T3 |
| Backend 4: Authz | T4 |
| Backend 5: Controller GET | T5 |
| Backend 6: Controller PATCH | T6 |
| Frontend 7: queryKey | T7 |
| Frontend 8: API client | T8 |
| Frontend 9: 4 hooks | T9, T10, T11, T12 |
| Frontend 10: TeamMemberTable | T13 |
| Frontend 11: InviteMemberDialog | T14 |
| Frontend 12: ChangeRoleDialog + RemoveMemberDialog | T15 |
| Frontend 13: 라우트 | T16 |
| Frontend 14: WorkspaceSection 메뉴 | T17 |
| Docs/closure 15: docs/02 §7 | T18 |
| Docs/closure 16: spec 참조 | (covered by spec doc itself) |
| Docs/closure 17: progress.md | T18 |

All 17 spec tasks → 18 plan tasks (one extra split for hooks). No gaps.

**Type consistency check:**
- `TeamMember.role: TeamMemberRole = 'OWNER' | 'MEMBER'` — used consistently in frontend.
- `TeamMembership.Role` (Java enum: OWNER, MEMBER) — backend.
- Method signatures: `useInviteTeamMember(teamId).mutate({ userId })`, `useChangeTeamMemberRole(teamId).mutate({ userId, role })`, `useRemoveTeamMember(teamId).mutate({ userId })` — consistent across hook tests and dialog usages.

**Placeholder scan**: no TBD/TODO/"appropriate"/etc. in plan body. Repository test note ("persistUser helper... if existing test has a different helper, follow that") is a verification instruction, not a placeholder.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-10-team-centric-pivot-plan-f-team-member-mgmt.md`. Two execution options:

1. **Subagent-Driven (recommended)** — fresh subagent per task with two-stage review (implementer → spec reviewer → code quality reviewer). Memory `feedback_subagent_workflow` shows 8-task / 11-commit autonomous run validated.
2. **Inline Execution** — execute tasks in this session via `executing-plans`, batched checkpoints.

Which approach?
