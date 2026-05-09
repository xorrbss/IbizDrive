# Team-Centric Pivot — 설계 변경 명세

> **Status**: 설계 합의 완료 (2026-05-09)
> **Author**: Claude Code (brainstorming session, user: solution6@ibizsoftware.net)
> **Scope**: IbizDrive 데이터 모델·권한·URL을 "개인 소유 + 권한 첨부"에서 "workspace(부서/팀) 멤버십 1차"로 전환
> **Affected docs**: 00, 01, 02, 03, 04, CLAUDE.md (§7 참조)
> **Implementation plan**: TBD (writing-plans 스킬에서 작성)

---

## 0. 배경 — 왜 바꾸나

현재 설계(`docs/00~04`)는 다음 가정 위에 서 있다:

- `folders.owner_id` / `files.owner_id`가 단일 user를 가리킴 → 모든 콘텐츠는 개인 소유
- root folder(`parent_id IS NULL`)의 owner 정책이 명세되지 않아 사실상 사용자별 My Drive 모델
- `permissions.subject_type='department'`는 owner가 부서에 권한을 **부여**하는 용도 — 부서가 콘텐츠를 소유하지 않음
- `shares` = "내 파일을 다른 user/dept에게 보내기" — Dropbox/Google Drive 패러다임

이 모델은 **"팀 간 파일 공유"가 아니라 "개인 파일을 팀에게도 줄 수 있음"**에 가깝다. 사내 도입 목표(부서·프로젝트 팀 협업)와 정합하지 않는다.

**전환 방향**:
- 콘텐츠의 1차 컨테이너 = **workspace** (부서 또는 팀)
- 멤버십 자체가 묵시적 READ 권한 (별도 grant 불필요)
- 개인 공간(My Drive) **완전 제거**
- 공유 = 한 workspace의 콘텐츠를 다른 workspace/user에게 노출

---

## 1. 스키마 변경

### 1.1 신규 테이블 — `teams`, `team_memberships`

```sql
-- V11__teams.sql
-- 임시 팀: 사용자 자율 생성, 평면(계층 없음), archive 가능
CREATE TABLE teams (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name            VARCHAR(100) NOT NULL,
  normalized_name VARCHAR(100) NOT NULL,
  description     TEXT,
  visibility      VARCHAR(20) NOT NULL DEFAULT 'private',  -- private|internal
  root_folder_id  UUID,                                     -- §1.3 root 자동 생성 후 채움
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
  role       VARCHAR(20) NOT NULL,    -- OWNER|MEMBER
  joined_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  invited_by UUID REFERENCES users(id),

  PRIMARY KEY (team_id, user_id),
  CHECK (role IN ('OWNER','MEMBER'))
);

CREATE INDEX idx_team_memberships_user ON team_memberships(user_id);
```

**정책**:
- `visibility=private`: 멤버만 인식. `internal`: 사내 검색·가입 신청 가능 (v1.x 옵션).
- `OWNER` ≥ 1명 보장: 서비스 레이어 트랜잭션 검증 (마지막 OWNER 탈퇴/role 변경 차단). DB CHECK로는 표현 불가.
- 부서는 기존 `departments` + `users.department_id` 그대로. 별도 멤버십 테이블 신설 안 함 (1:1 매핑 유지).

### 1.2 `folders` / `files` — workspace scope 도입

```sql
-- V12__folders_files_scope.sql
-- Scenario A (green-field, 채택): NOT NULL + CHECK 즉시 적용
ALTER TABLE folders
  ADD COLUMN scope_type VARCHAR(20) NOT NULL,             -- 'department' | 'team'
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

> 시나리오 B 선택 시: NOT NULL/CHECK는 V12에서 빼고 V15에서 강화 (§6.3 참조).

**핵심 결정 — 다형 scope 컬럼을 모든 row에 denormalize**:

- root folder뿐 아니라 **자식 folder/file에도 동일한 scope_*를 복제**.
- 이유: 권한 평가 hot path가 매번 트리 traversal해서 root 찾는 비용 회피.
- **불변량**: 같은 부모를 가진 모든 자식은 부모와 동일한 `(scope_type, scope_id)`. 이동·생성 시 백엔드 트랜잭션에서 강제 (CLAUDE.md §3 원칙 7과 정렬).
- **cross-workspace 이동 = 금지**. 한 폴더를 다른 workspace로 옮기려면 명시적 "복사 후 원본 삭제" 액션. audit 추적 분명, 권한 누수 방지.

### 1.3 root folder 일관성

- workspace(부서/팀) 생성 시 **root folder 1개를 트랜잭션 안에서 같이 생성**.
- `departments.root_folder_id` 컬럼 추가 (`teams.root_folder_id`는 §1.1에 이미 포함).

```sql
-- V13__departments_root_folder.sql
ALTER TABLE departments ADD COLUMN root_folder_id UUID REFERENCES folders(id);
```

- root folder는 `parent_id IS NULL` + `(scope_type, scope_id)` set. 동일 scope 내 root 1개 보장:

```sql
CREATE UNIQUE INDEX idx_folders_root_per_scope
  ON folders(scope_type, scope_id)
  WHERE parent_id IS NULL AND deleted_at IS NULL;
```

- root folder는 **이름 변경/삭제 불가** (workspace 자체 archive로만 처리). 트리거 또는 서비스 레이어에서 차단.

### 1.4 `permissions` — subject_type 확장

```sql
-- V14__permissions_audit_team.sql
ALTER TABLE permissions DROP CONSTRAINT permissions_subject_type_check;
ALTER TABLE permissions
  ADD CONSTRAINT permissions_subject_type_check
    CHECK (subject_type IN ('user','department','role','team','everyone'));
```

- `team`을 subject로 추가 → 팀 A의 폴더를 팀 B에게 열 수 있음.

### 1.5 `audit_log.target_type` 확장

```sql
ALTER TABLE audit_log DROP CONSTRAINT audit_log_target_type_check;
ALTER TABLE audit_log
  ADD CONSTRAINT audit_log_target_type_check
    CHECK (target_type IN ('file','folder','user','permission','share',
                           'system','audit','department','team'));
```

신규 이벤트: `team.created`, `team.archived`, `team.member.added`, `team.member.removed`, `team.member.role_changed`.

### 1.6 `owner_id` 의미 재정의 (스키마 변경 없음)

- `folders.owner_id` / `files.owner_id` = "최초 생성자". **권한 부여하지 않음**.
- 권한 평가에서 owner_id 분기 제거.

### 1.7 §1 변경 요약

- 신규 테이블 2개 (`teams`, `team_memberships`)
- 변경 테이블: `folders` (+2 컬럼, +2 인덱스), `files` (+2 컬럼, +1 인덱스), `permissions` (CHECK 확장), `audit_log` (CHECK 확장), `departments` (+1 컬럼)
- 마이그레이션 V11~V14 (Phase 1) + V15~V17 (Phase 3, scenario B에서만)

---

## 2. 라이프사이클

### 2.1 부서 라이프사이클

기존 admin CRUD + root folder 자동 생성:

| 액션 | 트리거 | 동작 |
|---|---|---|
| **생성** | admin | `INSERT departments` + `INSERT folders (parent=NULL, scope=('department',dept.id))` 트랜잭션. `departments.root_folder_id` 채움 |
| **이름 변경** | admin | `departments.name` 갱신. root folder 이름은 생성 시점 고정 — rename 불가 (§1.3 불변량) |
| **비활성화** | admin (`deleted_at` set) | 콘텐츠 read-only. 멤버 fallback: `users.department_id` NULL 처리. root folder 유지 |
| **삭제(purge)** | 시스템 ROLE `ADMIN` | 비활성 + 잔존 콘텐츠 0건 + dual-approval (docs/03 §6.4) |

### 2.2 팀 라이프사이클

| 액션 | 트리거 | 동작 |
|---|---|---|
| **생성** | 인증 사용자 (정책: 사내 전원, v1.x 화이트리스트 옵션) | `INSERT teams` + `INSERT team_memberships (role=OWNER)` + `INSERT folders (root)` 트랜잭션 |
| **멤버 초대** | OWNER | `team_memberships` row 추가. audit `team.member.added` |
| **탈퇴/제외** | 본인 또는 OWNER | row 삭제. **마지막 OWNER 탈퇴 차단** |
| **OWNER 이양** | OWNER | role 변경 트랜잭션. ≥ 1 OWNER 보장 |
| **archive** | OWNER (또는 admin) | `archived_at` set. 콘텐츠 read-only |
| **un-archive** | admin only | `archived_at = NULL`. 활성 이름 충돌 시 거부 |
| **삭제(purge)** | 시스템 `ADMIN` + dual-approval | archived 상태에서만. 콘텐츠는 휴지통 경유 후 30일 retention |

### 2.3 콘텐츠 라이프사이클

- 휴지통은 **workspace 단위로 분리**. 부서 휴지통 ≠ 팀 휴지통.
- workspace archive 시 콘텐츠는 휴지통으로 가지 않음 — workspace 자체가 read-only가 곧 archive 의미.
- workspace purge 시에야 콘텐츠 → 휴지통 → 30일 후 storage purge.

---

## 3. 권한 평가 알고리즘

### 3.1 평가 순서 (앞이 우선)

```
1. 시스템 ROLE 검사     (ADMIN → 모든 권한)
2. workspace 멤버십     (소속 부서 OR 팀 멤버십 → 묵시적 READ + workspace 정책)
3. 명시적 permission 행 (folder/file에 직접 grant, 트리 상속 포함)
4. shares 통한 grant   (cross-workspace 노출)
5. 거부 (default deny)
```

### 3.2 멤버십 = 묵시적 권한 (핵심 변경)

```text
부서 콘텐츠 접근:
  user.department_id == folder.scope_id (scope_type='department')
  → READ + UPLOAD (부서 기본 정책)

팀 콘텐츠 접근:
  team_memberships(team_id=folder.scope_id, user_id=current.id) 존재
  role='OWNER'  → 모든 file-level preset
  role='MEMBER' → READ + UPLOAD + EDIT (팀 기본 정책)
```

- 멤버십 자체로 `permissions` row를 만들지 **않음**. 단일 진실 출처는 멤버십 테이블.
- 부서/팀별 "기본 정책"은 시스템 상수로 시작 (v1.x에 workspace settings로 커스터마이즈 검토).

### 3.3 명시적 grant — 멤버십 위에 추가만 (deny 미지원)

기존 ADR #28 (preset 단일 컬럼, 명시 deny 미지원) 유지. 멤버십 기본 권한을 grant로 줄일 수 없음. 좁히려면 sub-folder + fallback 차단 플래그 필요 — **v1.x 기능, MVP 미포함**.

### 3.4 cross-workspace 접근 = `shares`로만

다른 부서/팀 콘텐츠 접근은 반드시 `shares` row를 통해서만:
- 팀 A의 폴더 → `shares.permission_id` → `permissions(subject_type='team', subject_id=teamB.id)` 또는 `'department'`/`'user'`
- 받는 쪽 UI: 별도 "공유받음" 트리. workspace 트리에 섞지 않음 (소속 명료성).

### 3.5 권한 평가 함수 시그니처

```kotlin
fun evaluate(
  user: User,
  resource: Resource  // folder or file
): EffectivePermissionSet {
  if (user.hasSystemRole(ADMIN)) return ALL

  val membershipPerms = membershipDefaults(user, resource.scopeType, resource.scopeId)
  val explicitPerms   = explicitPermissions(user, resource)  // tree traversal, expires_at filter
  val sharedPerms     = sharesPermissions(user, resource)

  return membershipPerms ∪ explicitPerms ∪ sharedPerms
}
```

성능: workspace membership은 in-memory 캐시 (login 시 로드). resource scope는 row에 denormalize (§1.2).

### 3.6 백엔드 재검증

CLAUDE.md §3 원칙 10 유지. 모든 파괴적 액션은 백엔드에서 위 함수 재호출. 프론트 권한은 UX용.

---

## 4. 공유 모델 재정의

### 4.1 패러다임 전환

| 항목 | 기존 (개인 중심) | 신규 (팀 중심) |
|---|---|---|
| 공유 주체 | owner_id 본인 | workspace 내 SHARE 권한 보유자 |
| 공유 대상 | user / department / role / everyone | + **team** 추가. user/department/team 1차 |
| 1차 유스케이스 | "이 파일 누구에게 줄까" | "Q4 보고서 폴더를 영업팀에게 READ로 노출" |
| 데이터 모델 | `shares` 그대로 | 그대로. `subject_type='team'` 활성화 |

### 4.2 공유 가능 권한 — 멤버십보다 절대 넓힐 수 없음

```
share_grant_max(scope=A, target=B) =
  min( workspace_member_default(A), SHARE_PRESET_MAX )
```

- 부서/팀 내 멤버 기본권 이상 외부 공유 불가. ADMIN preset cross-workspace 공유 절대 금지.
- `PURGE`는 시스템 ROLE만 — 공유 대상 아님 (기존 정책 유지).

### 4.3 공유 받은 콘텐츠 가시성

- 받는 쪽 사이드바: 자기 부서 트리 + 가입한 팀들 + **"공유받음"** 별도 섹션
- "공유받음" 항목은 원본 workspace 명시 (`Shared from #영업팀 / Q4`)
- re-share 금지 — 원본 workspace에서만 권한 부여 가능. 권한 누수 방지.

### 4.4 만료·revoke

- 기존 `shares.expires_at` + revoke cron 그대로
- workspace archive/purge 시 source share는 자동 revoke (cascade — `shares.folder_id/file_id` ON DELETE CASCADE 활용)

---

## 5. API / URL 영향

### 5.1 URL 구조 — workspace prefix 도입

기존: `/files/[...parts]` (parts[0]=folderId)

신규:
```
/d/:departmentSlug/[...parts]    # 부서 콘텐츠
/t/:teamSlug/[...parts]          # 팀 콘텐츠
/shared/[...parts]               # 공유받은 콘텐츠
/trash/d/:deptSlug               # 부서 휴지통
/trash/t/:teamSlug               # 팀 휴지통
```

- workspace는 URL이 소유 (CLAUDE.md §3 원칙 1).
- `parts[0]`는 여전히 `folderId`(해시), 나머지는 SEO/canonical용 slug. 기존 catch-all + canonical redirect 패턴 유지.
- slug → id 매핑 server lookup. 충돌 시 short-id suffix.

### 5.2 신규 API 엔드포인트

```
# Teams
GET    /api/teams                       # 내가 속한 팀 + (visibility=internal 검색)
POST   /api/teams                       # 팀 생성
GET    /api/teams/:id
PATCH  /api/teams/:id                   # 이름·visibility (OWNER)
POST   /api/teams/:id/archive           # archive (OWNER)
POST   /api/teams/:id/unarchive         # admin only
DELETE /api/teams/:id                   # purge (시스템 ADMIN + dual-approval)

# Memberships
GET    /api/teams/:id/members
POST   /api/teams/:id/members           # invite (OWNER)
DELETE /api/teams/:id/members/:userId   # remove (OWNER) or self
PATCH  /api/teams/:id/members/:userId   # role 변경 (OWNER, OWNER ≥1 보장)

# Workspaces (사이드바용 통합 조회)
GET    /api/workspaces/me               # 부서 1 + 팀 N + 공유받은 root 리스트
```

### 5.3 변경되는 기존 엔드포인트

| 엔드포인트 | 변경 |
|---|---|
| `POST /api/folders` | `parent_id` 필수 (root 직접 생성 불가). scope는 parent에서 상속 |
| `POST /api/files` (업로드) | 동일. 업로드 destination = 어떤 폴더 → scope 자동 결정 |
| `POST /api/folders/:id/move`, `POST /api/files/:id/move` | **same-scope 검증** 추가. cross-workspace 시 409 + `ERR_CROSS_SCOPE_MOVE` |
| `GET /api/folders/:id/children`, `GET /api/files/:id` | 응답에 `scopeType`, `scopeId`, `workspaceName` 포함 |
| `POST /api/shares` | `subject` 종류에 `team` 허용. 멤버십 상한 검증 (§4.2) |
| `GET /api/auth/me` | `departments` (1) + `teams` ([]) + `effectivePermissionsCacheKey` (ADR #22 유지). 팀 멤버십 변경 시 cacheKey invalidate |

### 5.4 신규 에러 코드 (docs/02 §8)

```
ERR_CROSS_SCOPE_MOVE       409  // 다른 workspace로 이동 시도
ERR_TEAM_OWNER_REQUIRED    400  // 마지막 OWNER 탈퇴 시도
ERR_TEAM_NAME_CONFLICT     409  // 활성 팀 이름 중복
ERR_TEAM_ARCHIVED          423  // archive된 팀에 쓰기 시도
ERR_SHARE_EXCEEDS_MEMBER   403  // 멤버 기본권 초과 share 시도
ERR_NOT_WORKSPACE_MEMBER   403  // 해당 workspace 멤버 아님
```

### 5.5 OpenAPI 영향

- 신규 schema: `Team`, `TeamMembership`, `WorkspaceRef`
- `Folder`, `File` schema에 `scope: { type, id, name }` 필드
- `Share` schema의 subject에 `team` 추가
- `MeResponse`에 `teams: TeamMembership[]` 추가

---

## 6. 마이그레이션 전략

### 6.1 시나리오 A — Green-field cutover (**채택**)

- 베타 데이터를 버리고 깨끗한 schema로 재시작
- V11~V14 한 번에 적용 + DB reset
- 장점: 다형 컬럼·root folder 일관성을 백필 코드로 채울 필요 없음. 위험 최소.
- 조건: 사내 베타 사용자에게 데이터 reset 안내 가능.

**선정 근거**:
1. 현재 사내 베타 단계 → 데이터 손실 비용 < 마이그레이션 복잡도
2. CLAUDE.md §3 원칙 8 (편법 금지) + 원칙 6 (DB 제약이 진실) — Phase 2 백필은 본질적으로 임시 코드
3. 권한 모델이 본질적으로 바뀌므로(개인 owner → workspace 멤버십) 기존 `permissions` row 의미 보존이 모호 — 깨끗하게 다시 설계가 정직

### 6.2 운영 체크리스트 (시나리오 A)

- [ ] 베타 사용자 공지 (T-7, T-1, T-0)
- [ ] 기존 데이터 dump 백업 (S3 archive) — 30일 보관
- [ ] V11~V14 마이그레이션 적용 + 시드 (시스템 부서 1개 "전사", admin 사용자만 멤버)
- [ ] 신규 사용자 등록 시 부서 선택/배정 강제 (admin entry rewrite 트랙 후속)
- [ ] 롤백 플랜: dump 복원 (V11~V14 down + 백업 restore)

### 6.3 시나리오 B — In-place migration (대안, 미채택)

데이터 보존이 필수일 경우의 3-phase rolling 전략:

**Phase 1 — 스키마 추가 (backward-compatible)**
- V11: teams, team_memberships
- V12: folders/files scope_* NULLABLE
- V13: departments.root_folder_id
- V14: permissions/audit_log CHECK 확장

**Phase 2 — 데이터 백필 (배치, A6 트랙 패턴)**
1. 부서별 root folder 생성 + `departments.root_folder_id` 채움
2. 기존 user-owned root를 부서 root로 reparent (department_id 매칭)
3. department_id NULL 사용자 폴더 → "임시 보관" 부서로 이전 + admin 알림
4. 모든 folder/file에 scope 백필 (트리 BFS)

**Phase 3 — 제약 강화 (breaking)**
- V15: scope_type/scope_id NOT NULL
- V16: idx_folders_root_per_scope partial unique
- V17: 코드에서 owner_id 권한 부여 제거

비용: 백필 검증·롤백·기존 권한 매핑 복잡 → 4~6주 추가. ADR #34(shares migration) 패턴 참고.

---

## 7. 문서 업데이트 영향

스펙 변경 = 문서 동기화 (CLAUDE.md §3 원칙 12).

### docs/00-overview.md
- §1.1 목적: "부서·팀이 문서를 ..."로 재기술
- §1.2 사용자: workspace 컨텍스트
- §3.2 권한 모델: membership step 추가
- §3.3 정규화: team name 추가
- **§5 ADR 신규**: ADR #48 "Workspace 중심 데이터 모델" — 본 spec 링크
- §6 용어집: `workspace`, `team`, `team membership`, `scope`, `shared with me`

### docs/01-frontend-design.md
- §2~§4 라우팅: `/files/*` → `/d/*`, `/t/*`, `/shared/*` 재설계
- §5 Zustand: `currentWorkspace` 슬라이스 신규 (URL 파생)
- §6 Query keys: `workspaces`, `teams`, `teamMembers`
- §13 휴지통: workspace별 분리 트리
- §14 권한 패널: subject `team` 추가, "공유받음" 분리
- §17 URL canonical: workspace prefix 빌더
- §18 로드맵: M_team-pivot 마일스톤 신설
- §19 핵심 원칙: 원칙 1 갱신 (URL이 workspace + folder를 소유)

### docs/02-backend-data-model.md
- §2.2 departments: `root_folder_id` 추가
- §2.3 folders: scope_* + root unique
- §2.4 files: scope_*
- §2.6 permissions: subject_type='team'
- §2.8 audit_log: target_type='team'
- **§2.13 teams (신규)**, **§2.14 team_memberships (신규)**
- §6 트랜잭션: workspace 생성/archive/un-archive/purge 4종
- §7 API: §7.11 Teams, §7.12 Memberships, §7.13 Workspaces
- §8 에러: 6개 신규 코드 (§5.4)

### docs/03-security-compliance.md
- §3.3 Subject 유형: team 추가
- §3.4 권한 상속·평가: membership step 1순위, 알고리즘 재작성 (§3.5 본 spec)
- §3.5 권한 매트릭스: teams API 행
- §4.1 감사 이벤트: `team.*` 5종
- §6.4 Dual-approval: workspace purge 액션 추가

### docs/04-admin-operations.md
- §2 신규: 팀 관리 (admin이 팀 archive/purge)
- §3 부서 관리: root folder 자동 생성 흐름
- §13 배치: `team-purge-cron`, `archived-team-cleanup-cron`
- §15 베타 운영 런북: workspace pivot cutover 절차 (§6.2 본 spec)

### CLAUDE.md (project)
- §1 개요: "팀·부서 콘텐츠 공유" 명확화
- §2 라우팅 표: `/d/*`, `/t/*`, `/shared/*` 추가
- §3 핵심 원칙: 원칙 1 갱신, 원칙 6에 root_per_scope

### 코드 계약 파일 (CLAUDE.md §4)
- `src/lib/queryKeys.ts`: workspaces/teams keys
- `src/lib/folderPath.ts`: workspace prefix 빌더
- `src/lib/normalize.ts`: team name 정규화 (department와 동일 함수 재사용)
- `src/types/permission.ts`: SubjectType enum에 'team'
- `src/types/audit.ts`: AuditEventType에 team.*
- `src/lib/errors.ts`: 6 신규 코드

---

## 8. Open Questions / 결정 보류

다음 항목은 spec 채택 시점엔 미결, 구현 plan 작성 단계에서 확정:

1. **팀 생성 권한 화이트리스트** — MVP는 사내 전원 자유 생성. 폭증 시 admin approval 게이트 추가 여부.
2. **internal visibility 검색** — `teams.visibility='internal'` 팀의 사내 검색·가입 신청 UI는 v1.x로 미룰지 MVP에 포함할지.
3. **부서 다중 소속** — 매트릭스 조직 대응 (`users.department_id` → `user_departments` M:N)은 본 spec 범위 외. 별도 ADR 필요.
4. **workspace settings (기본 권한 커스터마이즈)** — 부서/팀별 멤버 기본 정책 변경 가능 여부. v1.x 후보.
5. **공유받은 콘텐츠 검색 범위** — 전역 검색에 포함할지, "공유받음" 섹션에서만 가능할지.

---

## 9. 변경되지 않는 것 (명시)

혼동 방지 차원에서 **유지되는 정책**:

- ADR #28: preset 단일 컬럼, 명시 deny semantics 미지원 — 그대로
- ADR #22: `/api/auth/me` 페이로드 정책 — `effectivePermissionsCacheKey` 그대로 (teams 멤버십 변경 시 invalidate trigger만 추가)
- ADR #11/#36: Spring Boot 스택, multipart 업로드 — 그대로
- 정규화 함수 (`normalizeFileName`, `normalizeForSearch`) — 그대로, team name에도 동일 적용
- audit_log append-only 강제 (REVOKE UPDATE/DELETE) — 그대로
- 휴지통 30일 retention — 그대로 (workspace별 분리만 추가)
- Dual-approval framework (docs/03 §6.4) — 그대로, 적용 액션에 workspace purge 추가
- DB 제약이 진실의 출처 (CLAUDE.md §3 원칙 6) — 그대로 강화 (root_per_scope, scope NOT NULL)

---

## 10. 다음 단계

1. 본 spec 사용자 리뷰 → 필요시 수정
2. 승인 후 `superpowers:writing-plans` 스킬로 구현 plan 작성 (`docs/superpowers/plans/2026-05-09-team-centric-pivot-plan.md`)
3. plan 승인 후 dev-docs로 작업 분해 → 백엔드/프론트 트랙 병렬 진행
4. ADR #48을 docs/00 §5에 추가 (본 spec 링크)
