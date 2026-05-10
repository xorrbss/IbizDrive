# Plan F — Team Member Management (설계 문서)

**Date**: 2026-05-10
**Track**: team-centric-pivot Plan F
**Base spec**: `docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md`
**Status**: Draft for review

---

## 0. 컨텍스트

team-centric-pivot Plan A·A2(backend), Plan B(frontend foundation)가 머지된 시점의 잔여 gap.
사내 시스템에서 **팀 워크스페이스를 만든 후 일상 운영(멤버 추가/제거/역할 변경)**이 UI로 안 됨.

- Plan A2 backend: `TeamService.changeRole`, `archive`, `restore` 구현 완료 — controller에 wire되지 않음.
- Plan B frontend: `TeamCreateDialog`만 노출 — 팀 생성 후 멤버 관리 동선 없음.
- 결과: 팀 OWNER가 멤버 invite/remove를 SQL/cURL로만 가능. **사내라도 IT admin 부담**.

본 Plan은 **꼭 필요한 일상 운영** 기능만 닫는다. 사용자가 명시 제외한 storage quota, 일괄 invite, 멤버 활동 로그, archive UI는 본 범위에서 제외.

---

## 1. 목표

1. 팀 OWNER가 UI로 멤버 목록을 보고, 초대/제거/역할 변경할 수 있다.
2. 백엔드 endpoint를 **service에 이미 있는 메서드 1:1 매핑**으로 추가 (last-OWNER guard, archive 차단 등은 모두 기존 service에 존재).
3. 신규 도메인·신규 추상화 **금지** (CLAUDE.md §3 — 기존 구조 우선).
4. 다른 세션이 진행 중인 Plan C(share team subject), Plan D(cross-workspace move), Plan E(trash split), team-archive-write-enforcement 와 **파일 영역 disjoint**로 머지 충돌 없도록 한다.

---

## 2. 비목표 (YAGNI)

- 팀 archive/restore UI — 사내 빈도 매우 낮음. admin이 API/SQL로 처리. 별도 plan으로 분리.
- 일괄 invite (CSV 업로드, 부서 단위 import) — 한 번의 클릭으로 충분.
- 멤버 검색/필터링 — 한 팀당 통상 30명 미만. 단순 정렬로 충분.
- 멤버 활동 로그 페이지 — `audit_log`는 admin 페이지(`/admin/audit/logs`)에서 이미 조회 가능.
- 팀 일반 설정 (이름·설명 변경) — backend rename endpoint 부재. 별도 plan.
- 멤버 ownership transfer 액션 — `changeRole` 두 번(승격 + 강등)으로 표현 가능. 전용 액션 불필요.

---

## 3. 백엔드 설계

### 3.1 신규 endpoint

| Method | Path | Auth | Body | 응답 |
|---|---|---|---|---|
| `GET` | `/api/teams/{teamId}/members` | `@teamAuthz.isMember` | — | `200 List<TeamMemberResponse>` |
| `PATCH` | `/api/teams/{teamId}/members/{userId}` | `@teamAuthz.isOwner` | `{ role: "OWNER" \| "MEMBER" }` | `204` |

같은 `/api/teams/{teamId}/members` prefix에 기존 `POST`(invite), `DELETE`(remove)가 있으므로 자연 확장.

### 3.2 권한 체크

- `GET /members`: 팀 멤버 누구나 조회 가능 (workspace 진입 가능자가 멤버 목록도 봐야 운영 가능). `@teamAuthz.isMember(teamId, principal)` 신규.
- `PATCH .../role`: OWNER만. 기존 `@teamAuthz.isOwner` 재사용.
- `TeamArchivedException`은 service layer에서 throw → `GlobalExceptionHandler` 가 `TEAM_ARCHIVED` (403)로 매핑 (이미 존재).

### 3.3 신규 DTO

**`TeamMemberResponse`** (`backend/src/main/java/com/ibizdrive/team/dto/`):

```java
public record TeamMemberResponse(
    UUID userId,
    String displayName,
    String email,
    Role role,           // TeamMembership.Role
    OffsetDateTime joinedAt
) {
    public static TeamMemberResponse of(TeamMembership m, User u) { ... }
}
```

**`TeamMemberRoleUpdateRequest`** (`backend/.../dto/`):

```java
public record TeamMemberRoleUpdateRequest(
    @NotNull TeamMembership.Role role
) {}
```

### 3.4 Repository 변경

`TeamMembership` 엔티티는 `User`에 대한 JPA 관계 매핑이 없고 단순 `userId UUID` 컬럼만 보유 (현재 entity 코드 검증). 따라서 **JPQL constructor projection**으로 DTO를 직접 생성한다 — 엔티티 측 매핑 추가 없이 read-only JOIN.

```java
@Query("""
    SELECT new com.ibizdrive.team.dto.TeamMemberResponse(
        m.id.userId, u.displayName, u.email, m.role, m.joinedAt
    )
    FROM TeamMembership m, User u
    WHERE m.id.teamId = :teamId AND m.id.userId = u.id
    ORDER BY m.joinedAt ASC
    """)
List<TeamMemberResponse> findMembersWithUser(@Param("teamId") UUID teamId);
```

> `User` 엔티티 매핑은 기존 `com.ibizdrive.user.User` 사용. 동일 패턴이 본 프로젝트에 이미 존재하면 그것을 따른다 (구현 시 확인).

### 3.5 Service layer

`TeamService.listMembers(UUID teamId)` — 단순 `@Transactional(readOnly=true)` repository 위임.
`changeRole`은 이미 존재 → controller에서 그대로 호출.

### 3.6 신규 에러 envelope

**없음**. 기존 코드 재사용:
- last-OWNER 강등 시도 → `TEAM_OWNER_REQUIRED` (400) — 기존 `LastOwnerRequiredException` 재사용
- non-OWNER 시도 → `PERMISSION_DENIED` (403)
- team/user 미존재 → `NOT_FOUND` (404)

**확인된 사실 (코드 검증)**: 현재 `TeamService.invite/remove/changeRole`은 **archived team 가드를 두지 않음**.
team-archive-write-enforcement 트랙은 이미 T1~T6 진행 (file/folder **데이터 플레인 write** 가드만 추가) — 멤버 ops는 그쪽 범위 밖.

본 Plan은 **archive 가드를 추가하지 않음** (KISS·YAGNI):
- 사내 시스템에서 팀 archive는 매우 드물고, archive 직후 멤버 변경 시도는 거의 발생하지 않음.
- archived team의 데이터(파일/폴더)는 team-archive-write-enforcement가 보호.
- 멤버 변경이 archived team에서 일어나도 데이터 무결성에는 영향 없음 (membership row 변경뿐).
- 향후 필요 시 team-archive-write-enforcement 후속 트랙이 동일 패턴(`TeamArchiveGuard`)으로 멤버 ops에 확장.

따라서 `TEAM_ARCHIVED` envelope은 본 Plan에서 backend가 throw하지 않는다. frontend는 `useTeamMembers` 응답의 `archivedAt` 필드(또는 `team` 메타)로 archived 상태를 감지해 **UX 차원에서만** 액션을 disable하고 banner를 보여준다 (방어적 UX, 보안 가드 아님).

### 3.7 테스트

- `TeamControllerListMembersTest` — 200 (member self), 403 (non-member), 단일 OWNER 케이스.
- `TeamControllerChangeRoleTest` — 204 (success), 400 (last-OWNER), 403 (non-OWNER), 404 (membership not found).
- `TeamServiceListMembersTest` — repository 결과 사이즈/순서 검증 (Mockito).
- `TeamMembershipRepositoryFindMembersWithUserTest` — `@DataJpaTest` + Testcontainers Postgres. JPQL constructor projection 동작 + `joinedAt ASC` ordering 검증.

---

## 4. 프론트엔드 설계

### 4.1 라우트

`frontend/src/app/(explorer)/t/[teamId]/settings/members/page.tsx` + `ClientMembersPage.tsx`.

Plan B의 `t/[teamId]/[[...parts]]` 패턴 미러. `(explorer)` layout 그대로 사용 (sidebar 유지).

### 4.2 진입 동선

`WorkspaceSection.tsx`의 팀 항목에 hover/contextmenu 액션 추가:
- `⚙ 설정` → `/t/{teamId}/settings/members`
- 노출 조건: `useTeamMembers(teamId).data` 에서 본인 role이 OWNER. **frontend 권한은 UX용** (CLAUDE.md §3 원칙 10), backend `@PreAuthorize`가 진실.

대안 검토: 우상단 워크스페이스 헤더에 설정 버튼 — sidebar context menu가 더 범용적이고 Plan B 패턴과 정합. **선택**.

### 4.3 컴포넌트 (`frontend/src/components/team/`)

| 파일 | 책임 |
|---|---|
| `TeamMemberTable.tsx` | 3컬럼(이름·역할·가입일). row hover 시 액션 버튼 노출 (role 변경/제거). OWNER 본인 행은 강조. |
| `InviteMemberDialog.tsx` | `UserSearchCombobox` 재사용. role 선택은 default OWNER 아님(MEMBER), promote는 PATCH로 분리. |
| `ChangeRoleDialog.tsx` | confirm + role select. last-OWNER guard 에러 시 inline 메시지. |
| `RemoveMemberDialog.tsx` | confirm. last-OWNER guard 에러 처리. self-remove 시 redirect to `/`. |

### 4.4 Hook (`frontend/src/hooks/`)

| 파일 | 패턴 |
|---|---|
| `useTeamMembers.ts` | `useQuery({ queryKey: qk.teamMembers(teamId), queryFn: api.getTeamMembers(teamId) })` |
| `useInviteTeamMember.ts` | `useMutation` → invalidate `qk.teamMembers(teamId)` |
| `useRemoveTeamMember.ts` | 동일. self-remove 시 추가 로직(`router.push('/')`)는 호출자 책임. |
| `useChangeTeamMemberRole.ts` | 동일 |

테스트는 fetch-mock 패턴 (memory `feedback_mock_transport.md`).

### 4.5 Query key

`src/lib/queryKeys.ts`:

```typescript
teams: () => [...qk.all, 'teams'] as const,
team: (id: string) => [...qk.teams(), 'detail', id] as const,
teamMembers: (id: string) => [...qk.teams(), 'members', id] as const,
```

> `teams()` namespace 자체가 신규. Plan B에서 `useCreateTeam`은 invalidation 없이 `workspaces.me` 쿼리만 invalidate했으므로 `teams` keyspace 부재. 본 plan에서 도입.

### 4.6 API client

`frontend/src/lib/api.ts` 에 추가:

```typescript
api.getTeamMembers(teamId) → GET /api/teams/{teamId}/members
api.inviteTeamMember(teamId, userId) → POST .../members
api.removeTeamMember(teamId, userId) → DELETE .../members/{userId}
api.changeTeamMemberRole(teamId, userId, role) → PATCH .../members/{userId}
```

CSRF 토큰 헤더는 mutation 3종에 모두 포함 (CLAUDE.md `csrf-mutation-sweep` 패턴).

### 4.7 에러 처리

| 코드 | UX |
|---|---|
| `TEAM_OWNER_REQUIRED` (400) | inline error in dialog: "팀에 최소 한 명의 OWNER가 필요합니다" |
| `PERMISSION_DENIED` (403) | redirect or `FileTableForbidden` 패턴의 page state |
| `NOT_FOUND` (404) | redirect `/t/{teamId}` |

`errors.ts`: 신규 상수 없음. 기존 `TEAM_OWNER_REQUIRED`, `PERMISSION_DENIED`, `NOT_FOUND` 재사용.

**Archived team UX (보안 가드 아님, 방어적 UX)**: backend는 archived team에서 멤버 ops를 차단하지 않음 (§3.6). 그러나 page에 진입했을 때 team이 archived면 frontend가 banner를 노출하고 모든 액션 버튼을 disable한다. `archivedAt` 정보는 team 메타에서 가져옴 — 별도 endpoint 없이 `useTeamMembers` 응답에 team 메타가 동봉되거나, 기존 workspaces.me 응답 활용. (구현 단계에서 결정.)

---

## 5. 데이터 흐름

```
[OWNER 사용자]
  │
  ▼
sidebar 팀 항목 → ⚙ 설정 클릭
  │
  ▼
/t/{teamId}/settings/members (page.tsx → ClientMembersPage.tsx)
  │
  ├── useTeamMembers(teamId) ──→ GET /api/teams/{teamId}/members
  │                              ├ 200: TeamMemberTable 렌더
  │                              └ 403: forbidden state
  │
  ├── 멤버 초대 → InviteMemberDialog
  │              └ UserSearchCombobox 선택 → useInviteTeamMember
  │                                          → POST .../members
  │                                          → invalidate qk.teamMembers
  │
  ├── 역할 변경 → ChangeRoleDialog
  │              └ useChangeTeamMemberRole → PATCH .../members/{userId}
  │                                          → invalidate qk.teamMembers
  │
  └── 멤버 제거 → RemoveMemberDialog
                  └ useRemoveTeamMember → DELETE .../members/{userId}
                                          → invalidate qk.teamMembers
                                          → if self: router.push('/')
```

---

## 6. 충돌 / 의존성 분석

### 6.1 다른 진행 중 트랙과의 영역 분리

| 트랙 | 영역 | 본 Plan과 겹침? |
|---|---|---|
| Plan C (PR #140, share subject_type=team) | `ShareCommandService`, `permission/`, `errors.ts` (`SHARE_EXCEEDS_MEMBER`) | **No** — share 도메인 |
| Plan D (PR #138, cross-workspace move) | `folder/CrossWorkspaceMoveService`, `MovePreviewService` | **No** — folder/file move |
| Plan E (trash split, 미시작) | `trash/`, V18 마이그레이션 예정 | **No** — trash 도메인 |
| team-archive-write-enforcement | `TeamArchiveGuard`, file/folder write 경로 (T1~T6 진행) | **No** — 본 Plan은 archive 가드를 추가하지 않음 (§3.6). 데이터 플레인(파일/폴더 write) vs 컨트롤 플레인(멤버 ops)으로 명확 분리. |

### 6.2 선행 의존

- **Plan A**: 머지됨 ✓ (V12-V15 마이그레이션 + Team 엔티티)
- **Plan A2 / team-domain-hardening**: 머지됨 ✓ (`TeamService.changeRole`, archive/restore, `TEAM_OWNER_REQUIRED`/`TEAM_ARCHIVED` envelope)
- **Plan B**: 머지됨 ✓ (sidebar `WorkspaceSection`, `t/[teamId]` 라우트, `UserSearchCombobox` 재사용 가능)

### 6.3 후행 의존 없음

본 plan 머지 후 별도 plan이 본 plan을 require하지 않음. archive UI/general settings UI를 추가할 때 본 plan의 settings layout을 재사용할 수 있으나 hard dependency 아님.

---

## 7. 작업 단위

총 17 task 추정 (writing-plans 단계에서 정확화):

**Backend (6 task)**:
1. `TeamMemberResponse` DTO + `TeamMemberRoleUpdateRequest` DTO
2. `TeamMembershipRepository.findMembersWithUser` (JPQL constructor projection)
3. `TeamService.listMembers` (read-only delegation)
4. `TeamAuthz.isMember` 신규
5. `TeamController` GET endpoint
6. `TeamController` PATCH endpoint

**Frontend (8 task)**:
7. `qk.teams` / `qk.teamMembers` query key
8. `api.getTeamMembers` / invite / remove / changeRole API client
9. 4개 hook (`useTeamMembers`, `useInviteTeamMember`, `useRemoveTeamMember`, `useChangeTeamMemberRole`)
10. `TeamMemberTable.tsx`
11. `InviteMemberDialog.tsx`
12. `ChangeRoleDialog.tsx` + `RemoveMemberDialog.tsx`
13. `t/[teamId]/settings/members` 라우트
14. `WorkspaceSection.tsx` 팀 컨텍스트 메뉴 (⚙ 설정)

**Docs/closure (3 task)**:
15. `docs/02-backend-data-model.md §7` — 새 endpoint 2개 추가
16. `docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §5` 또는 본 spec 참조 추가
17. `docs/progress.md` 세션 종료 기록

---

## 8. 위험 / 롤백

- **위험**: 낮음. 신규 read endpoint + 기존 service 메서드 1:1 wire. 기존 데이터 영향 없음.
- **롤백**: 단순 revert. frontend는 라우트 진입 차단으로 격리.
- **회귀 위험**: `WorkspaceSection.tsx` 변경이 sidebar 동작에 영향. 기존 팀 클릭 동선 회귀 테스트 필요(Plan B의 `SidebarSections.test.tsx` 확장).

---

## 9. 검증 기준 (Definition of Done)

- [ ] backend `./gradlew test` PASS (신규 4 테스트 + Plan A 회귀 zero)
- [ ] frontend `pnpm typecheck && pnpm lint` exit 0
- [ ] frontend `pnpm test` PASS (신규 hook/component 테스트 포함)
- [ ] 수동 검증: 팀 생성 → settings/members 진입 → invite → role 변경 → remove (self/other) → last-OWNER 강등 차단 confirm
- [ ] `docs/02 §7` API 표 업데이트
- [ ] PR description: 영향 범위 / 테스트 결과 / Plan C·D·E와 머지 순서 무관 명시
- [ ] `docs/progress.md` 추가
