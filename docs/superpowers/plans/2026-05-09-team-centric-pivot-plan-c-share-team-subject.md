# Team-Centric Pivot — Plan C: Share endpoint `subject_type=team` Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Share endpoint(`POST /api/{files|folders}/{id}/share`)가 `subject_type=team`을 1차 시민으로 받고, §4.2 멤버십 cap (sharer의 workspace 기본권 ≥ 부여 preset)을 백엔드에서 강제하도록 확장한다.

**Architecture:** Spec `docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md` §4 (공유 모델 재정의) + §5.2/§5.4 (신규 endpoint·에러 코드) 부분 구현. Plan A의 V15 마이그레이션(이미 적용)과 `WorkspaceMembershipResolver`(이미 구현)에 **얹어** subject 매트릭스 1줄 확장 + 표시명 lookup 분기 + cap 검증 단일 helper를 추가한다. ADMIN preset cross-workspace 절대 금지 가드는 Plan D(cross-workspace move)에서 함께 다루며, 본 plan은 same-workspace cap만 강제한다.

**Tech Stack:** Spring Boot 3.x + Java 21, Spring Data JPA, JUnit 5 + AssertJ + Mockito, Testcontainers Postgres (V15 회귀용).

**Spec 범위 외 (다른 plan으로 이월):**
- ADMIN preset cross-workspace 절대 금지 → Plan D
- Frontend share dialog에 team picker 추가 → Plan B 이후 (별도 frontend track)
- Re-share 금지(공유받음 자원에 대한 share grant 차단) → Plan E 또는 Plan B
- `GET /api/shares/with-me`의 team 멤버십 inbox 통합 → Plan B 의존(트리 §4.5)

---

## File Structure

| 파일 | 역할 | 변경 종류 |
|---|---|---|
| `backend/src/main/java/com/ibizdrive/permission/PermissionService.java` | grant 매트릭스 — `ALLOWED_SUBJECT_TYPES` 1줄 확장, 에러 메시지 동기 | Modify |
| `backend/src/main/java/com/ibizdrive/share/ShareCommandService.java` | `resolveSubjectName` team 분기 + cap 검증 호출 | Modify |
| `backend/src/main/java/com/ibizdrive/share/ShareGrantCapValidator.java` | §4.2 cap helper — preset ⊆ sharer membership perms 검증 | **Create** |
| `backend/src/main/java/com/ibizdrive/share/ShareExceedsMembershipException.java` | cap 위반 도메인 예외 | **Create** |
| `backend/src/main/java/com/ibizdrive/common/error/GlobalExceptionHandler.java` | `ShareExceedsMembershipException` → 403 envelope | Modify |
| `backend/src/test/java/com/ibizdrive/permission/PermissionServiceGrantTest.java` (또는 기존 grant 테스트) | team subject 회귀 가드 | Modify or extend |
| `backend/src/test/java/com/ibizdrive/share/ShareCommandServiceTest.java` | team subject + cap 회귀 가드 | Modify |
| `backend/src/test/java/com/ibizdrive/share/ShareGrantCapValidatorTest.java` | cap 단위 테스트 | **Create** |
| `backend/src/test/java/com/ibizdrive/share/ShareControllerTest.java` | team wire E2E 회귀 | Modify |
| `frontend/src/lib/errors.ts` | `ERR_SHARE_EXCEEDS_MEMBER` 상수 (CLAUDE.md §4 계약 동기화) | Modify |
| `docs/02-backend-data-model.md` §7.9, §8 | share endpoint 'team' 명시 + 새 에러 코드 | Modify |
| `docs/progress.md` | closure entry | Modify (append) |

**의존성**:
- V15 마이그레이션: `permissions.subject_type` CHECK가 'team' 허용 (이미 PR #129 적용).
- `WorkspaceMembershipResolver.resolve(userId, ScopeType, UUID)`: Plan A Task 22로 base에 머지됨 — cap 검증의 단일 입력원.
- `Folder/FileItem.scopeType, scopeId`: Plan A Phase 9로 base에 머지됨 — sharer가 어느 workspace 자원을 공유하는지 확인 가능.

---

## Phase 1 — Permission grant 매트릭스 'team' 확장

### Task 1: `PermissionService.ALLOWED_SUBJECT_TYPES`에 'team' 추가

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/permission/PermissionService.java:365-389`
- Modify or Test: `backend/src/test/java/com/ibizdrive/permission/PermissionServiceGrantTest.java` (없으면 `PermissionServiceTest.java` 안에 nested class)

**왜**: V15 DB CHECK는 'team'을 허용하지만 application 레벨 `validateGrantInput`이 `IllegalArgumentException`으로 사전 차단 중. share endpoint가 'team' subject를 보내려면 이 layer를 먼저 풀어야 한다.

- [ ] **Step 1: 실패 테스트 추가**

`PermissionServiceTest.java`(또는 grant 전용 nested test class) 안에 추가. team UUID는 임의 — Service 레벨 단위라 DB 검증은 별도 IT가 처리.

```java
@Test
@DisplayName("grantPermission: subjectType='team'은 IllegalArgumentException을 던지지 않는다")
void grantPermission_teamSubjectTypeAccepted() {
    UUID folderId = UUID.randomUUID();
    UUID teamId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();

    // 통과 조건은 "validateGrantInput에서 throw하지 않음" — 실제 DB 호출은 stub.
    // 기존 테스트 패턴(예: grantPermission_userSubject*) 그대로 따라 mock 세팅.
    when(permissionRepository.save(any(PermissionRow.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    PermissionRow row = service.grantPermission(
        "folder", folderId, "team", teamId,
        Preset.READ, /*expiresAt=*/ null, actorId
    );

    assertThat(row.getSubjectType()).isEqualTo("team");
    assertThat(row.getSubjectId()).isEqualTo(teamId);
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.ibizdrive.permission.PermissionServiceTest.grantPermission_teamSubjectTypeAccepted"
```

Expected: FAIL — `IllegalArgumentException: subjectType must be one of user|department|role|everyone`

- [ ] **Step 3: 최소 구현**

`PermissionService.java:365-366`:

```java
private static final Set<String> ALLOWED_SUBJECT_TYPES =
    Set.of("user", "department", "role", "team", "everyone");
```

`PermissionService.java:379-382` 에러 메시지 동기:

```java
if (subjectType == null || !ALLOWED_SUBJECT_TYPES.contains(subjectType)) {
    throw new IllegalArgumentException(
        "subjectType must be one of user|department|role|team|everyone");
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd backend && ./gradlew test --tests "com.ibizdrive.permission.PermissionServiceTest"
```

Expected: PASS — 신규 테스트 + 기존 모든 테스트.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/ibizdrive/permission/PermissionService.java \
        backend/src/test/java/com/ibizdrive/permission/PermissionServiceTest.java
git commit -m "feat(team-centric-pivot): PermissionService — accept subjectType='team' (Plan C Task 1)"
```

---

## Phase 2 — Share subject 표시명 + 매트릭스 확장

### Task 2: `ShareCommandService.resolveSubjectName`에 team 분기 추가

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/share/ShareCommandService.java:62-89` (생성자 — TeamRepository 주입), `:247-256` (resolveSubjectName)
- Modify: `backend/src/test/java/com/ibizdrive/share/ShareCommandServiceTest.java` (team 분기 회귀)

**왜**: spec §4.5의 "출처 workspace로 그룹핑"에서 share row가 표시할 subject 이름이 필요. `subject_type=user/department`는 이미 분기 존재. team은 빠져 있음.

- [ ] **Step 1: 실패 테스트 추가**

`ShareCommandServiceTest.java`에 (기존 user/department 테스트 패턴 그대로 따라):

```java
@Test
@DisplayName("createShares: subjectType='team'이면 ShareDto.subjectName=Team.name")
void createShares_teamSubject_resolvesTeamName() {
    UUID fileId = UUID.randomUUID();
    UUID teamId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();

    // 기존 user 분기 테스트 패턴: file lookup mock + permission grant mock + repo save stub
    FileItem file = mock(FileItem.class);
    when(file.getId()).thenReturn(fileId);
    when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));

    PermissionRow grant = new PermissionRow();
    grant.setId(UUID.randomUUID());
    grant.setSubjectType("team");
    grant.setSubjectId(teamId);
    grant.setPreset(Preset.READ);
    when(permissionService.grantPermission(eq("file"), eq(fileId), eq("team"), eq(teamId),
        eq(Preset.READ), isNull(), eq(actorId))).thenReturn(grant);

    Team team = mock(Team.class);
    when(team.getName()).thenReturn("ProjectAlpha");
    when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

    ShareCreateRequest req = new ShareCreateRequest(
        List.of(new ShareCreateRequest.Subject("team", teamId)),
        "read", null, null
    );

    List<ShareDto> created = service.createShares(fileId, req, actorId);

    assertThat(created).hasSize(1);
    assertThat(created.get(0).subjectName()).isEqualTo("ProjectAlpha");
    assertThat(created.get(0).subjectType()).isEqualTo("team");
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.ibizdrive.share.ShareCommandServiceTest.createShares_teamSubject_resolvesTeamName"
```

Expected: FAIL — `subjectName == null` (현재 resolveSubjectName이 team 분기 없음).

- [ ] **Step 3: 구현 — 생성자에 TeamRepository 주입 + team 분기 추가**

`ShareCommandService.java` 임포트 추가:

```java
import com.ibizdrive.team.Team;
import com.ibizdrive.team.TeamRepository;
```

필드 + 생성자 (`:62-89` 영역) — `TeamRepository teamRepository` 추가:

```java
private final TeamRepository teamRepository;

public ShareCommandService(FileRepository fileRepository,
                           FolderRepository folderRepository,
                           PermissionService permissionService,
                           PermissionRepository permissionRepository,
                           ShareRepository shareRepository,
                           UserRepository userRepository,
                           DepartmentRepository departmentRepository,
                           TeamRepository teamRepository,
                           ApplicationEventPublisher eventPublisher) {
    this.fileRepository = fileRepository;
    this.folderRepository = folderRepository;
    this.permissionService = permissionService;
    this.permissionRepository = permissionRepository;
    this.shareRepository = shareRepository;
    this.userRepository = userRepository;
    this.departmentRepository = departmentRepository;
    this.teamRepository = teamRepository;
    this.eventPublisher = eventPublisher;
}
```

`resolveSubjectName` (`:247-256`)에 team 분기 추가:

```java
private String resolveSubjectName(String subjectType, UUID subjectId) {
    if (subjectId == null) return null;
    if ("user".equals(subjectType)) {
        return userRepository.findById(subjectId).map(User::getDisplayName).orElse(null);
    }
    if ("department".equals(subjectType)) {
        return departmentRepository.findById(subjectId).map(Department::getName).orElse(null);
    }
    if ("team".equals(subjectType)) {
        return teamRepository.findById(subjectId).map(Team::getName).orElse(null);
    }
    return null;
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd backend && ./gradlew test --tests "com.ibizdrive.share.ShareCommandServiceTest"
```

Expected: PASS — 신규 테스트 + 기존 user/department/everyone 회귀.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/ibizdrive/share/ShareCommandService.java \
        backend/src/test/java/com/ibizdrive/share/ShareCommandServiceTest.java
git commit -m "feat(team-centric-pivot): ShareCommandService — resolveSubjectName team branch (Plan C Task 2)"
```

### Task 3: ShareController E2E — team subject wire 회귀 가드

**Files:**
- Modify: `backend/src/test/java/com/ibizdrive/share/ShareControllerTest.java`

**왜**: controller 레이어가 subject `{type:'team', id:UUID}`를 그대로 service로 흘려보내는지 wire 차원에서 한 번 가드. 기존 user 테스트 패턴 그대로 따라간다.

- [ ] **Step 1: 실패 테스트 추가** (또는 기존 createShares 테스트 parametrize)

```java
@Test
@DisplayName("POST /api/files/:id/share: subject={type:'team',id} → 201 + ShareDto")
void createShares_teamSubject_201() throws Exception {
    UUID fileId = UUID.randomUUID();
    UUID teamId = UUID.randomUUID();

    when(shareCommandService.createShares(eq(fileId), any(), any())).thenReturn(
        List.of(/* ShareDto with subjectType='team', subjectId=teamId, subjectName='ProjectAlpha' */ )
    );

    String body = """
        {
          "subjects": [{"type":"team","id":"%s"}],
          "preset": "read"
        }
        """.formatted(teamId);

    mockMvc.perform(post("/api/files/{id}/share", fileId)
            .with(csrf())
            .with(user(testUser()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.shares[0].subjectType").value("team"))
        .andExpect(jsonPath("$.shares[0].subjectId").value(teamId.toString()));
}
```

- [ ] **Step 2: 실패 확인 → Step 3 구현 → Step 4 통과 확인**

Service-only 변경이라 테스트가 처음부터 통과할 수도 있음. 통과한다면 본 task는 회귀 가드만으로 가치 있음 — Step 3 별도 구현 없이 Step 5 커밋으로 진행.

```bash
cd backend && ./gradlew test --tests "com.ibizdrive.share.ShareControllerTest"
```

Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add backend/src/test/java/com/ibizdrive/share/ShareControllerTest.java
git commit -m "test(team-centric-pivot): ShareController — team subject wire regression guard (Plan C Task 3)"
```

---

## Phase 3 — §4.2 멤버십 cap 검증

### Task 4: `ShareExceedsMembershipException` 도메인 예외 추가

**Files:**
- Create: `backend/src/main/java/com/ibizdrive/share/ShareExceedsMembershipException.java`

**왜**: cap 위반은 `IllegalArgumentException`(400)이 아니라 인가 거부(403)로 매핑돼야 함 — sharer가 자기 권한을 넘는 grant를 시도한 경우. spec §5.4 `ERR_SHARE_EXCEEDS_MEMBER` 403.

- [ ] **Step 1: 파일 작성**

```java
package com.ibizdrive.share;

import com.ibizdrive.permission.Permission;
import com.ibizdrive.permission.Preset;

import java.util.Set;

/**
 * Plan C — §4.2 share grant cap 위반 시 발생.
 *
 * <p>sharer의 workspace 멤버십 default permission 집합을 넘어선 preset을 grant하려 할 때.
 * 예: sharer가 MEMBER ({READ, UPLOAD, EDIT})인데 ADMIN preset으로 share 시도.
 *
 * <p>{@link com.ibizdrive.common.error.GlobalExceptionHandler}가 403 +
 * {@code ERR_SHARE_EXCEEDS_MEMBER}로 매핑.
 */
public class ShareExceedsMembershipException extends RuntimeException {

    private final Preset requestedPreset;
    private final Set<Permission> sharerMembershipPerms;

    public ShareExceedsMembershipException(Preset requestedPreset,
                                           Set<Permission> sharerMembershipPerms) {
        super("share preset " + requestedPreset
            + " exceeds sharer membership perms " + sharerMembershipPerms);
        this.requestedPreset = requestedPreset;
        this.sharerMembershipPerms = sharerMembershipPerms;
    }

    public Preset getRequestedPreset() {
        return requestedPreset;
    }

    public Set<Permission> getSharerMembershipPerms() {
        return sharerMembershipPerms;
    }
}
```

- [ ] **Step 2: 컴파일 확인**

```bash
cd backend && ./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/ibizdrive/share/ShareExceedsMembershipException.java
git commit -m "feat(team-centric-pivot): ShareExceedsMembershipException for §4.2 cap (Plan C Task 4)"
```

### Task 5: `ShareGrantCapValidator` helper 작성

**Files:**
- Create: `backend/src/main/java/com/ibizdrive/share/ShareGrantCapValidator.java`
- Create: `backend/src/test/java/com/ibizdrive/share/ShareGrantCapValidatorTest.java`

**왜**: §4.2 검증을 단일 helper로 격리. ShareCommandService.createShares/createFolderShares 양쪽에서 같은 호출. KISS — 검증 1줄 호출, 위반 시 throw.

**알고리즘** (spec §4.2 단순 형태):
```
preset이 펼쳐지는 Permission 집합 ⊆ WorkspaceMembershipResolver.resolve(sharerId, scopeType, scopeId)
```
sharer가 해당 자원 workspace의 멤버가 아닌 경우 멤버권 집합이 비어있어 항상 위반 — 단, controller `@PreAuthorize("hasPermission(..., 'SHARE')")`가 이미 그 케이스를 차단하므로 본 검증은 cap만 본다.

- [ ] **Step 1: 실패 테스트 작성** — `ShareGrantCapValidatorTest.java`

```java
package com.ibizdrive.share;

import com.ibizdrive.folder.ScopeType;
import com.ibizdrive.permission.Permission;
import com.ibizdrive.permission.Preset;
import com.ibizdrive.permission.WorkspaceMembershipResolver;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShareGrantCapValidatorTest {

    private final WorkspaceMembershipResolver resolver = mock(WorkspaceMembershipResolver.class);
    private final ShareGrantCapValidator validator = new ShareGrantCapValidator(resolver);

    @Test
    void teamMember_canGrantEditPreset() {
        UUID sharer = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        when(resolver.resolve(sharer, ScopeType.TEAM, teamId))
            .thenReturn(EnumSet.of(Permission.READ, Permission.UPLOAD, Permission.EDIT));

        // EDIT preset은 {READ, UPLOAD, EDIT} → MEMBER cap 안.
        validator.validate(sharer, ScopeType.TEAM, teamId, Preset.EDIT);
        // throw 없음 = 통과
    }

    @Test
    void teamMember_cannotGrantAdminPreset() {
        UUID sharer = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        when(resolver.resolve(sharer, ScopeType.TEAM, teamId))
            .thenReturn(EnumSet.of(Permission.READ, Permission.UPLOAD, Permission.EDIT));

        assertThatThrownBy(() ->
            validator.validate(sharer, ScopeType.TEAM, teamId, Preset.ADMIN)
        ).isInstanceOf(ShareExceedsMembershipException.class);
    }

    @Test
    void teamOwner_canGrantAdminPreset() {
        UUID sharer = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        when(resolver.resolve(sharer, ScopeType.TEAM, teamId))
            .thenReturn(EnumSet.of(
                Permission.READ, Permission.UPLOAD, Permission.EDIT,
                Permission.DELETE, Permission.SHARE));

        // ADMIN preset이 펼치는 권한 ⊆ OWNER 멤버권. 단, ADMIN이 PERMISSION_ADMIN을 포함하면
        // OWNER도 거부될 수 있음 — 그 경우 ADMIN preset의 펼침을 spec 정의대로 조정 필요.
        // 본 테스트는 "OWNER ≥ ADMIN preset이라는 spec 의도가 코드로 표현됐는지" 가드.
        validator.validate(sharer, ScopeType.TEAM, teamId, Preset.ADMIN);
    }

    @Test
    void departmentMember_grantUploadPreset_ok() {
        UUID sharer = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        when(resolver.resolve(sharer, ScopeType.DEPARTMENT, deptId))
            .thenReturn(EnumSet.of(Permission.READ, Permission.UPLOAD));

        validator.validate(sharer, ScopeType.DEPARTMENT, deptId, Preset.UPLOAD);
    }

    @Test
    void departmentMember_grantEditPreset_blocked() {
        UUID sharer = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        when(resolver.resolve(sharer, ScopeType.DEPARTMENT, deptId))
            .thenReturn(EnumSet.of(Permission.READ, Permission.UPLOAD));

        assertThatThrownBy(() ->
            validator.validate(sharer, ScopeType.DEPARTMENT, deptId, Preset.EDIT)
        ).isInstanceOf(ShareExceedsMembershipException.class);
    }
}
```

- [ ] **Step 2: 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.ibizdrive.share.ShareGrantCapValidatorTest"
```

Expected: FAIL with compile error — `ShareGrantCapValidator` 미존재.

- [ ] **Step 3: 구현 — `ShareGrantCapValidator.java`**

`Preset.expandsTo()` 메서드(또는 동등 펼침 함수)가 이미 있는지 먼저 확인하고, 없으면 본 task에서 추가하지 말고 기존 PermissionResolver 펼침 로직을 그대로 재사용한다. 펼침 함수가 두 곳에 분산되면 §4.2 cap이 틀어질 수 있으므로 단일 진실의 출처를 유지.

```java
package com.ibizdrive.share;

import com.ibizdrive.folder.ScopeType;
import com.ibizdrive.permission.Permission;
import com.ibizdrive.permission.Preset;
import com.ibizdrive.permission.WorkspaceMembershipResolver;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Plan C — §4.2 share grant cap helper.
 *
 * <p>sharer의 workspace 멤버십 default permission 집합 안에서만 grant 허용.
 * preset이 펼치는 Permission 집합이 sharer 멤버권을 초과하면
 * {@link ShareExceedsMembershipException} (→ 403 ERR_SHARE_EXCEEDS_MEMBER).
 *
 * <p>비멤버 sharer는 controller {@code @PreAuthorize("hasPermission(..., 'SHARE')")}가 이미 차단 —
 * 본 helper는 cap 검증만.
 */
@Component
public class ShareGrantCapValidator {

    private final WorkspaceMembershipResolver membershipResolver;

    public ShareGrantCapValidator(WorkspaceMembershipResolver membershipResolver) {
        this.membershipResolver = membershipResolver;
    }

    /**
     * @throws ShareExceedsMembershipException preset이 sharer 멤버권 cap을 초과
     */
    public void validate(UUID sharerId, ScopeType scopeType, UUID scopeId, Preset preset) {
        Set<Permission> sharerPerms =
            membershipResolver.resolve(sharerId, scopeType, scopeId);
        Set<Permission> presetPerms = expandPreset(preset);
        if (!sharerPerms.containsAll(presetPerms)) {
            throw new ShareExceedsMembershipException(preset, sharerPerms);
        }
    }

    /**
     * Preset → Permission 집합 펼침. {@code PermissionResolver}의 펼침 로직과 1:1 정합 필수 —
     * 진실의 출처가 분산되면 cap 의미가 꺠진다. 펼침 메서드가 Preset enum에 추가되어 있다면
     * 그것을 그대로 호출하고 본 메서드는 제거한다.
     */
    private static Set<Permission> expandPreset(Preset preset) {
        // TODO(plan-c-task-5): Preset 클래스에 펼침 메서드가 이미 있는지 확인. 있으면 본 메서드 제거 후 호출.
        // 확인 명령: grep -n "EnumSet\|Set<Permission>" backend/src/main/java/com/ibizdrive/permission/Preset.java
        // 없으면 PermissionResolver의 펼침 로직과 동일하게 EnumSet 리터럴로 작성.
        throw new UnsupportedOperationException(
            "expandPreset 진실의 출처를 본 task 구현 시점에 결정 — Preset.expandsTo() 우선 사용");
    }
}
```

**중요**: Step 3에서 작성자는 먼저 `Preset.java`와 `PermissionResolver.java`에서 펼침 로직을 찾아 단일 진실의 출처로 사용해야 한다. 두 곳에 분기를 만들면 cap 의미가 분기된다 (CLAUDE.md §3 원칙 4: 구조 무결성). 펼침 메서드가 없다면 본 task에 별도 sub-step으로 `Preset.expandsTo()` 추가를 포함시키되, 이는 plan 작성 시점에 미확인이므로 구현자 판단.

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd backend && ./gradlew test --tests "com.ibizdrive.share.ShareGrantCapValidatorTest"
```

Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/ibizdrive/share/ShareGrantCapValidator.java \
        backend/src/test/java/com/ibizdrive/share/ShareGrantCapValidatorTest.java
git commit -m "feat(team-centric-pivot): ShareGrantCapValidator — §4.2 cap helper (Plan C Task 5)"
```

### Task 6: `GlobalExceptionHandler` 매핑

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/common/error/GlobalExceptionHandler.java`

**왜**: `ShareExceedsMembershipException`이 기본 RuntimeException → 500으로 매핑됨. 403 + 표준 envelope으로 바꿔야 함.

- [ ] **Step 1: 실패 테스트 추가** (이미 있을 수도 있는 GlobalExceptionHandlerTest에)

기존 envelope 테스트 패턴 따라서:

```java
@Test
void shareExceedsMembership_returns403WithCode() {
    ResponseEntity<?> resp = handler.handleShareExceedsMembership(
        new ShareExceedsMembershipException(Preset.ADMIN,
            EnumSet.of(Permission.READ, Permission.UPLOAD, Permission.EDIT))
    );
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    Map<String, Object> body = (Map<String, Object>) resp.getBody();
    assertThat(body).containsEntry("code", "ERR_SHARE_EXCEEDS_MEMBER");
}
```

- [ ] **Step 2: 실패 확인 → 구현 → 통과 확인**

`GlobalExceptionHandler.java`에 `@ExceptionHandler` 추가:

```java
@ExceptionHandler(ShareExceedsMembershipException.class)
public ResponseEntity<Map<String, Object>> handleShareExceedsMembership(
    ShareExceedsMembershipException ex
) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
        "code", "ERR_SHARE_EXCEEDS_MEMBER",
        "message", ex.getMessage()
    ));
}
```

기존 핸들러의 envelope 형식을 정확히 따라 작성 (필드명: `code`/`message` 외에 `details`, `traceId` 등이 있을 수 있음 — 기존 user/share 에러 핸들러 1개를 grep으로 먼저 확인).

```bash
cd backend && ./gradlew test --tests "*GlobalExceptionHandlerTest*"
```

Expected: PASS

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/ibizdrive/common/error/GlobalExceptionHandler.java \
        backend/src/test/java/com/ibizdrive/common/error/GlobalExceptionHandlerTest.java
git commit -m "feat(team-centric-pivot): map ShareExceedsMembershipException → 403 ERR_SHARE_EXCEEDS_MEMBER (Plan C Task 6)"
```

### Task 7: `ShareCommandService.createShares`/`createFolderShares` cap 검증 호출 통합

**Files:**
- Modify: `backend/src/main/java/com/ibizdrive/share/ShareCommandService.java` (생성자 + createShares + createFolderShares)
- Modify: `backend/src/test/java/com/ibizdrive/share/ShareCommandServiceTest.java`

**왜**: cap helper 자체는 Task 5에서 끝났지만 ShareCommandService가 호출하지 않으면 효과 없음. file/folder 양쪽 진입점에서 모두 검증.

- [ ] **Step 1: 실패 테스트 추가**

```java
@Test
@DisplayName("createShares: sharer 멤버권 < preset이면 ShareExceedsMembershipException")
void createShares_sharerExceedsCap_throws() {
    UUID fileId = UUID.randomUUID();
    UUID teamId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    UUID fileScopeTeamId = UUID.randomUUID();

    FileItem file = mock(FileItem.class);
    when(file.getId()).thenReturn(fileId);
    when(file.getScopeType()).thenReturn(ScopeType.TEAM);
    when(file.getScopeId()).thenReturn(fileScopeTeamId);
    when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(file));

    // sharer는 file이 속한 team의 MEMBER만 → ADMIN preset 차단되어야 함.
    doThrow(new ShareExceedsMembershipException(Preset.ADMIN,
        EnumSet.of(Permission.READ, Permission.UPLOAD, Permission.EDIT)))
        .when(capValidator).validate(actorId, ScopeType.TEAM, fileScopeTeamId, Preset.ADMIN);

    ShareCreateRequest req = new ShareCreateRequest(
        List.of(new ShareCreateRequest.Subject("team", teamId)),
        "admin", null, null
    );

    assertThatThrownBy(() -> service.createShares(fileId, req, actorId))
        .isInstanceOf(ShareExceedsMembershipException.class);
}
```

`createFolderShares` 변형도 동일하게 1건 추가 (XOR invariant 정합 — folder.getScopeType/ScopeId).

- [ ] **Step 2: 실패 확인**

```bash
cd backend && ./gradlew test --tests "com.ibizdrive.share.ShareCommandServiceTest.createShares_sharerExceedsCap_throws"
```

Expected: FAIL — capValidator 호출이 service에 없음.

- [ ] **Step 3: 구현 — ShareCommandService에 capValidator 주입 + 양쪽 호출**

생성자 시그니처에 `ShareGrantCapValidator capValidator` 추가, `createShares` 본체에서 file 조회 직후, `for` 루프 진입 전(트랜잭션 안에서 1회):

```java
// createShares 본체 (file 변형):
FileItem file = fileRepository.findByIdAndDeletedAtIsNull(fileId)
    .orElseThrow(() -> new ResourceNotFoundException("file not found: " + fileId));

// §4.2 cap — 자원의 workspace 기준 sharer 멤버권 ≥ preset.
capValidator.validate(actorId, file.getScopeType(), file.getScopeId(), preset);

List<ShareDto> created = new ArrayList<>(subjects.size());
for (...) { ... }
```

`createFolderShares`도 동일 패턴 — `folder.getScopeType()/getScopeId()` 사용.

**주의**: cap은 자원의 workspace 1회 검증으로 충분 (subject 별로 매번 호출 X). subject가 N명이어도 sharer 1명의 cap은 동일.

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd backend && ./gradlew test --tests "com.ibizdrive.share.ShareCommandServiceTest"
```

Expected: PASS — 신규 + 기존 모든 테스트.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/ibizdrive/share/ShareCommandService.java \
        backend/src/test/java/com/ibizdrive/share/ShareCommandServiceTest.java
git commit -m "feat(team-centric-pivot): ShareCommandService — invoke §4.2 cap validator (Plan C Task 7)"
```

---

## Phase 4 — 문서·계약 동기화

### Task 8: `docs/02 §8` 에러 코드 + `§7.9` share endpoint subject 매트릭스 갱신

**Files:**
- Modify: `docs/02-backend-data-model.md` §7.9 (subject_type=team 명시), §8 (ERR_SHARE_EXCEEDS_MEMBER 추가)

- [ ] **Step 1: 영역 찾기**

```bash
grep -n "## 7.9\|## 8\|ERR_PERMISSION\|ERR_SHARE" docs/02-backend-data-model.md | head -20
```

- [ ] **Step 2: §7.9에 subject 매트릭스 'team' 추가** — 기존 문장에 'team' 추가만 (KISS, 별도 sub-section 신설 X).

- [ ] **Step 3: §8 신규 행** — 기존 ERR_* 표 패턴 그대로:

```markdown
| `ERR_SHARE_EXCEEDS_MEMBER` | 403 | sharer 멤버권 < 부여 preset (§4.2 cap). spec `team-centric-pivot-design` §4.2. |
```

(spec 링크 + cap 설명만; 본 plan은 same-workspace cap만이고 cross-workspace ADMIN 절대 금지는 Plan D에서 추가.)

- [ ] **Step 4: 커밋**

```bash
git add docs/02-backend-data-model.md
git commit -m "docs(team-centric-pivot): docs/02 §7.9/§8 — share team subject + ERR_SHARE_EXCEEDS_MEMBER (Plan C Task 8)"
```

### Task 9: Frontend `errors.ts` 동기화

**Files:**
- Modify: `frontend/src/lib/errors.ts`

**왜**: CLAUDE.md §4 — `errors.ts`는 docs/02 §8의 코드 표현. backend가 새 코드를 발행하기 시작하면 frontend 상수도 함께 추가해야 분기 처리 가능 (KISS — 본 plan은 상수 추가만, UI 처리는 Plan B share dialog에서).

- [ ] **Step 1: 영역 찾기**

```bash
grep -n "ERR_SHARE\|ERR_PERMISSION" frontend/src/lib/errors.ts | head
```

- [ ] **Step 2: 상수 추가** — 기존 ERR_* 패턴 그대로:

```typescript
export const ERR_SHARE_EXCEEDS_MEMBER = 'ERR_SHARE_EXCEEDS_MEMBER';
```

(타입 union이 있다면 거기에도 추가.)

- [ ] **Step 3: typecheck**

```bash
cd frontend && pnpm typecheck
```

Expected: exit 0

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/lib/errors.ts
git commit -m "feat(team-centric-pivot): errors.ts — ERR_SHARE_EXCEEDS_MEMBER constant (Plan C Task 9)"
```

---

## Phase 5 — Closure

### Task 10: `progress.md` 항목 + dev-docs archive

**Files:**
- Modify: `docs/progress.md` (append 항목)
- Create: `dev/active/team-centric-pivot-plan-c/README.md` (소형 — 본 plan을 가리키는 backlink만)

- [ ] **Step 1: progress.md 항목 작성** — 기존 closure 패턴 따라 (범위 / 변경 핵심 / 검증 / 결정·편차 / 다음 세션 컨텍스트):

```markdown
## YYYY-MM-DD — team-centric-pivot Plan C 종료 (share endpoint subject_type='team' + §4.2 멤버십 cap)

### 범위
Plan C — share grant matrix에 'team' 추가 + §4.2 cap (sharer 멤버권 ≥ 부여 preset) 백엔드 강제. ADMIN cross-workspace 절대 금지는 Plan D.

### 변경 핵심
- `PermissionService.ALLOWED_SUBJECT_TYPES` ← 'team' 1줄 확장 + 에러 메시지 동기.
- `ShareCommandService.resolveSubjectName` ← 'team' 분기 (TeamRepository 주입).
- `ShareGrantCapValidator` 신규 — `WorkspaceMembershipResolver` 위임. `ShareCommandService.createShares/createFolderShares`가 자원 workspace 기준 1회 호출.
- `ShareExceedsMembershipException` → 403 `ERR_SHARE_EXCEEDS_MEMBER` (GlobalExceptionHandler).
- docs/02 §7.9 / §8 + frontend `errors.ts` 동기화.

### 검증
- `cd backend && ./gradlew test`: BUILD SUCCESSFUL.
- `cd frontend && pnpm typecheck`: exit 0.
- 신규 테스트: PermissionServiceTest 1, ShareGrantCapValidatorTest 5, ShareCommandServiceTest 2~3, ShareControllerTest 1, GlobalExceptionHandlerTest 1.

### 결정·편차
- cap 알고리즘 = "preset 펼침 ⊆ sharer 멤버권" 단순형. spec §4.2 `min(member_default, SHARE_PRESET_MAX)`의 의미를 코드로 옮길 때 SHARE_PRESET_MAX는 enum-only Preset.SHARE의 의미가 아닌 "preset이 펼치는 권한"으로 해석 — `preset='admin'` 자체가 SHARE_PRESET_MAX 한계.
- ADMIN preset cross-workspace 절대 금지는 본 plan 범위 외 (Plan D — cross-workspace move와 함께 cross-scope 차단을 도입할 때 추가). 따라서 sharer가 자기 workspace 멤버이고 preset이 cap 안이면 외부 subject(다른 부서/팀)에 ADMIN 부여 가능 — Plan D 적용 전 한정.
- Preset 펼침 함수가 PermissionResolver에 분산되어 있다면 cap helper도 그것을 재사용 — 펼침 진실의 출처는 단일 (CLAUDE.md §3 원칙 4).

### 다음 세션 컨텍스트
- Plan D(cross-workspace move): 본 plan이 도입한 cap helper에 ADMIN preset cross-workspace 절대 금지 분기 추가. preset=ADMIN + sharer.scope ≠ subject.scope 차단.
- Plan B(frontend share dialog): team picker + `ERR_SHARE_EXCEEDS_MEMBER` 사용자 메시지.

### 블로커
- 없음.
```

- [ ] **Step 2: dev-docs README 작성** — 본 plan을 가리키는 backlink + 종료 표시.

- [ ] **Step 3: 커밋**

```bash
git add docs/progress.md dev/active/team-centric-pivot-plan-c/
git commit -m "docs(team-centric-pivot): Plan C closure — progress.md + dev-docs (Plan C Task 10)"
```

### Task 11: PR 생성

- [ ] **Step 1: base 결정** — 다른 세션이 `feat/team-centric-pivot-plan-a`를 활발히 push 중. PR base는 그 브랜치(merge target은 그것이 origin/master로 머지될 때 자동으로 정렬). 만약 그 브랜치가 그동안 origin/master로 머지됐다면 origin/master로 base 변경.

```bash
git fetch origin
git log --oneline origin/feat/team-centric-pivot-plan-a..HEAD | head
```

- [ ] **Step 2: push + PR**

```bash
git push -u origin worktree-feat-team-centric-pivot-plan-c-share-team
gh pr create \
  --base feat/team-centric-pivot-plan-a \
  --title "feat(team-centric-pivot): Plan C — share subject_type=team + §4.2 cap" \
  --body "$(cat <<'EOF'
## Summary
Plan C 구현. share endpoint(file/folder)가 subject_type='team'을 받고, §4.2 sharer 멤버권 cap을 백엔드에서 강제.

## 변경
- Backend: PermissionService matrix 확장 / ShareCommandService team branch + cap helper / GlobalExceptionHandler ERR_SHARE_EXCEEDS_MEMBER
- Docs: docs/02 §7.9, §8
- Frontend: errors.ts 상수만

## Test plan
- [ ] `cd backend && ./gradlew test` 전체 통과
- [ ] `cd frontend && pnpm typecheck` exit 0

## 위험 / 롤백
- cap 도입은 sharer가 자기 권한 초과 grant를 시도할 때만 영향. 기존 데이터/grant 무영향.
- ADMIN cross-workspace 절대 금지(§4.2 강한 형태)는 Plan D로 이월 — 본 PR은 same-workspace cap만.
- 롤백: revert.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Self-Review Notes (writing-plans skill)

**Spec coverage:**
- §4.1 패러다임 전환 (subject에 team 추가): Task 1, 2, 3 ✅
- §4.2 멤버십 cap: Task 4, 5, 6, 7 ✅ (단, ADMIN cross-workspace 절대 금지는 Plan D로 명시 이월)
- §4.3 공유 받은 콘텐츠 가시성: Plan B/E 의존 — 본 plan 범위 외 ✅ (명시)
- §4.4 만료·revoke: 기존 동작 무변경 ✅
- §4.5 사이드바 트리: Plan B 의존 — 본 plan 범위 외 ✅ (명시)
- §5.2 신규 endpoint (POST /api/shares 'team' 허용): Task 1~3, 7 ✅
- §5.4 새 에러 코드 ERR_SHARE_EXCEEDS_MEMBER: Task 4, 6, 8, 9 ✅

**Placeholder scan:**
- Task 5 Step 3에 `expandPreset`의 `TODO` 주석은 의도적 — 구현자가 `Preset.java`/`PermissionResolver.java`를 먼저 점검해 단일 진실의 출처를 결정해야 함. plan 작성 시점에 펼침 메서드 존재 여부 미확인이라 추측보다 점검 지시가 정확.
- Task 6 envelope 필드(`details`/`traceId`)는 "기존 핸들러를 grep해서 정확히 따라가라" 지시 — 기존 코드를 진실의 출처로 사용.

**Type consistency:**
- `ShareGrantCapValidator.validate(UUID, ScopeType, UUID, Preset)` — Task 5에서 정의, Task 7에서 호출 시 정확히 동일 시그니처.
- `ShareExceedsMembershipException(Preset, Set<Permission>)` — Task 4에서 정의, Task 5/Task 6/Task 7 테스트에서 동일 생성자.
- `WorkspaceMembershipResolver.resolve(UUID, ScopeType, UUID)` — base 코드 시그니처 그대로 사용.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-09-team-centric-pivot-plan-c-share-team-subject.md`. 두 가지 실행 옵션:

**1. Subagent-Driven (recommended)** — task별 fresh subagent dispatch, 사이 사이 review, 빠른 iteration.

**2. Inline Execution** — 본 세션에서 `executing-plans`로 batch 실행, checkpoint 단위 review.

어느 쪽으로 갈까요?
