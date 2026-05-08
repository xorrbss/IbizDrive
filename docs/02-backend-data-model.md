# 02 - 백엔드 데이터 모델 & API 계약

> 프론트엔드(01)가 전제하는 **서버 계약**을 정의.
> DB 스키마, 제약조건, 트랜잭션 경계, API 스펙, 에러 코드.

---

## 1. 설계 원칙

### 1.1 데이터 무결성 원칙

```text
규칙은 애플리케이션이 아니라 DB에서 강제한다.
    → unique constraint, foreign key, check constraint, not null
    → 애플리케이션 버그가 있어도 데이터가 망가지지 않는다.

동시성 제어는 DB 트랜잭션과 row-level lock으로 한다.
    → 애플리케이션 레벨 mutex/락 금지 (수평 확장 시 깨짐)

감사 로그는 append-only이며 DB 레벨에서 강제한다.
    → UPDATE/DELETE 권한 REVOKE
```

### 1.2 식별자 정책

```text
모든 테이블의 PK는 UUID (v7 권장, 시간순 정렬 가능)
외부 노출 ID와 저장소 키는 분리
    - id            : API 노출용 (URL, response)
    - storage_key   : S3 객체 키 (UUID, 내부 전용, 원본명 분리)
```

### 1.3 소프트 삭제 정책

```text
files / folders       : deleted_at + purge_after (휴지통 모델)
file_versions         : 삭제 불가 (버전은 영구 보존, soft-delete/restore 컨텍스트). hard purge(A7)는 file row와 함께 cascade 삭제.
permissions           : 물리 삭제 (revoked_at 기록 후 audit_log로만 추적)
audit_log             : 삭제 불가 (append-only)
```

---

## 2. DB 스키마

### 2.1 users

```sql
CREATE TABLE users (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email            VARCHAR(254) NOT NULL,                  -- RFC 5321 max
  display_name     VARCHAR(100) NOT NULL,                  -- 표시용 이름 (Google/MS 컨벤션)
  password_hash    VARCHAR(100),                           -- DelegatingPasswordEncoder 호환 ({bcrypt}/{argon2id} 프리픽스, 미래 Argon2id 마이그레이션). SSO 사용자는 NULL
  department_id    UUID REFERENCES departments(id),        -- A16 도입 (V7), nullable
  role             VARCHAR(50) NOT NULL DEFAULT 'MEMBER',  -- MEMBER|AUDITOR|ADMIN (docs/03 §3.2.5)
  storage_quota    BIGINT NOT NULL DEFAULT 10737418240,    -- 10GB. A4 이후 사용
  storage_used     BIGINT NOT NULL DEFAULT 0,              -- A4 이후 사용
  is_active        BOOLEAN NOT NULL DEFAULT TRUE,
  last_login_at    TIMESTAMPTZ,                            -- audit + 비활성 계정 식별
  locked_at        TIMESTAMPTZ,                            -- 관리자 수동 잠금 (ADR #20). NULL이면 미잠금
  created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at       TIMESTAMPTZ,                            -- soft delete

  CHECK (role IN ('MEMBER', 'AUDITOR', 'ADMIN'))
);

-- 이메일 unique는 lower(email) 기준 partial index — 휴지통 사용자 제외
CREATE UNIQUE INDEX users_email_unique
  ON users (lower(email))
  WHERE deleted_at IS NULL;

CREATE INDEX idx_users_department ON users(department_id) WHERE is_active = TRUE;
```

### 2.2 departments (V7 — A16 ADR #37, V9 — admin-department-crud Wave 2 T4)

```sql
-- V7__departments_users_dept.sql (A16, ADR #37)
CREATE EXTENSION IF NOT EXISTS ltree;

CREATE TABLE departments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    parent_id   UUID REFERENCES departments(id),
    path        LTREE,                 -- v1.x 조직도 트리용 schema 도입만, MVP는 flat
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ            -- soft-delete = 비활성 (admin "deactivate")
);

CREATE INDEX idx_departments_path ON departments USING GIST (path);
CREATE INDEX idx_departments_name ON departments(name) WHERE deleted_at IS NULL;

-- users.department_id FK (V2 deferred 컬럼 활성화)
ALTER TABLE users
  ADD COLUMN department_id UUID REFERENCES departments(id);
CREATE INDEX idx_users_department
  ON users(department_id) WHERE is_active = TRUE AND department_id IS NOT NULL;

-- V9__admin_departments.sql (Wave 2 T4) — 활성 부서 이름 partial unique
CREATE UNIQUE INDEX idx_departments_name_active
    ON departments(name) WHERE deleted_at IS NULL;
```

**정책**:
- `name`은 VARCHAR(100), case-sensitive(Postgres default). Admin CRUD는 동일 활성 이름 충돌을 V9 partial unique로 차단(CLAUDE.md §3 원칙 6: DB 제약이 진실의 출처). 검색은 `LOWER(name) LIKE :p ESCAPE '\\'`로 A14 user search와 동형.
- `deleted_at`이 활성/비활성 상태를 도출(별도 `active` 컬럼 없음). `Department.isActive() := deletedAt == null`. Admin "비활성화"는 soft-delete 의미 동등.
- `path` LTREE 컬럼은 **schema 도입만** — application은 flat 사용(parent_id 무시). 조직도 트리 v1.x deferred (KISS, ADR #37).
- 비활성(`deleted_at IS NOT NULL`) 부서는 일반 사용자 검색·share subject 후보에서 제외. **Admin 페이지는 비활성 포함**(reactivate 가능하도록).
- `users.department_id` NULL 허용 — 미배정 사용자 가능. NULL인 사용자는 dept 권한 매칭에서 unmatched(false).

### 2.3 folders

```sql
CREATE TABLE folders (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  parent_id         UUID REFERENCES folders(id) ON DELETE RESTRICT,
  name              VARCHAR(255) NOT NULL,
  normalized_name   VARCHAR(255) NOT NULL,
  slug              VARCHAR(255) NOT NULL,          -- URL 표시용 (NFC)
  owner_id          UUID NOT NULL REFERENCES users(id),
  audit_level       VARCHAR(20) NOT NULL DEFAULT 'standard', -- standard|strict
  deleted_at        TIMESTAMPTZ,
  purge_after       TIMESTAMPTZ,
  original_parent_id UUID REFERENCES folders(id),   -- 복원용
  created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CHECK (audit_level IN ('standard', 'strict')),
  CHECK ((deleted_at IS NULL) = (purge_after IS NULL))
);

-- 🔑 핵심 제약: 같은 부모 폴더 내 이름 중복 금지 (휴지통 제외)
-- 🔑 root parent (parent_id IS NULL)는 COALESCE로 ZERO_UUID 치환 — Postgres가 NULL을 distinct 취급해 UNIQUE 우회되는 것을 차단 (ADR #27 보장사항, V5 마이그레이션 반영)
CREATE UNIQUE INDEX idx_folders_unique_name
  ON folders (COALESCE(parent_id, '00000000-0000-0000-0000-000000000000'::uuid), normalized_name)
  WHERE deleted_at IS NULL;

CREATE INDEX idx_folders_parent ON folders(parent_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_folders_purge ON folders(purge_after) WHERE deleted_at IS NOT NULL;
```

### 2.4 files

```sql
CREATE TABLE files (
  id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  folder_id          UUID NOT NULL REFERENCES folders(id) ON DELETE RESTRICT,
  name               VARCHAR(500) NOT NULL,             -- 원본 파일명 (NFC)
  normalized_name    VARCHAR(500) NOT NULL,             -- 중복 검사용
  current_version_id UUID,                              -- file_versions.id (FK는 아래 ALTER)
  owner_id           UUID NOT NULL REFERENCES users(id),
  size_bytes         BIGINT NOT NULL,
  mime_type          VARCHAR(255),
  deleted_at         TIMESTAMPTZ,
  purge_after        TIMESTAMPTZ,
  original_folder_id UUID REFERENCES folders(id),       -- 복원용
  created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CHECK ((deleted_at IS NULL) = (purge_after IS NULL)),
  CHECK (size_bytes >= 0)
);

-- 🔑 핵심 제약: 같은 폴더 내 동일 파일명 금지 (휴지통 제외)
CREATE UNIQUE INDEX idx_files_unique_name
  ON files (folder_id, normalized_name)
  WHERE deleted_at IS NULL;

CREATE INDEX idx_files_folder ON files(folder_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_files_owner ON files(owner_id);
CREATE INDEX idx_files_purge ON files(purge_after) WHERE deleted_at IS NOT NULL;
```

### 2.5 file_versions

```sql
CREATE TABLE file_versions (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  file_id         UUID NOT NULL REFERENCES files(id) ON DELETE RESTRICT,
  version_number  INT NOT NULL,                  -- 1부터 증가
  storage_key     UUID NOT NULL UNIQUE,          -- S3 객체 키
  size_bytes      BIGINT NOT NULL,
  checksum_sha256 CHAR(64) NOT NULL,
  mime_type       VARCHAR(255),
  scan_status     VARCHAR(20) NOT NULL DEFAULT 'pending',  -- pending|clean|infected|error
  scan_result     JSONB,
  uploaded_by     UUID NOT NULL REFERENCES users(id),
  uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  comment         VARCHAR(500),

  CHECK (scan_status IN ('pending', 'clean', 'infected', 'error')),
  CHECK (version_number > 0),
  UNIQUE (file_id, version_number)
);

ALTER TABLE files
  ADD CONSTRAINT fk_files_current_version
  FOREIGN KEY (current_version_id) REFERENCES file_versions(id) DEFERRABLE INITIALLY DEFERRED;

CREATE INDEX idx_versions_file ON file_versions(file_id, version_number DESC);
CREATE INDEX idx_versions_scan_pending ON file_versions(scan_status) WHERE scan_status = 'pending';
```

### 2.6 permissions

```sql
CREATE TABLE permissions (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  resource_type   VARCHAR(20) NOT NULL,             -- folder|file
  resource_id     UUID NOT NULL,
  subject_type    VARCHAR(20) NOT NULL,             -- user|department|role|everyone
  subject_id      UUID,                             -- subject_type=everyone이면 NULL
  preset          VARCHAR(20) NOT NULL,             -- read|upload|edit|admin (preset 단일 컬럼 — 명시 deny semantics는 v1.x 이월, ADR #28)
  granted_by      UUID NOT NULL REFERENCES users(id),
  expires_at      TIMESTAMPTZ,                      -- NULL = 무기한
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CHECK (resource_type IN ('folder', 'file')),
  CHECK (subject_type IN ('user', 'department', 'role', 'everyone')),
  CHECK (preset IN ('read', 'upload', 'edit', 'admin')),
  CHECK ((subject_type = 'everyone') = (subject_id IS NULL))
);

-- 동일 (resource, subject) 중복 금지
CREATE UNIQUE INDEX idx_permissions_unique
  ON permissions (resource_type, resource_id, subject_type, COALESCE(subject_id, '00000000-0000-0000-0000-000000000000'::uuid));

CREATE INDEX idx_permissions_subject ON permissions(subject_type, subject_id);
CREATE INDEX idx_permissions_resource ON permissions(resource_type, resource_id);
CREATE INDEX idx_permissions_expires ON permissions(expires_at) WHERE expires_at IS NOT NULL;
```

> **효율적 권한 조회**: 파일/폴더의 effective permission 계산은 재귀 CTE로 상위 폴더까지 순회. 성능이 문제면 materialized view 또는 상속된 권한을 비정규화.
>
> **만료 grant 처리**: `expires_at <= NOW()` row는 `permissions-expired-cron` 트랙(2026-05-01 closure, ADR #34)이 정기 cleanup + `permission.expired` audit 기록. `findEffective`가 이미 `expires_at > NOW()` 필터링하므로 cron의 가치는 (a) DB cleanup, (b) audit trail. 정책 표는 §7.10.1 참조.

### 2.7 shares (V6 마이그레이션 — A10, ADR #34)

`shares` row = 공유 메타(message / expiresAt / revoke 추적) + `permissions` row와 1:1 연결. `permissions.preset`은 "공유받은 사람의 권한 셋"(Preset.java 5종 wire format), `shares.message`/`expires_at`/`revoked_at`은 공유 행위 자체의 메타. SRP 분리(ADR #34) — 권한 row에 메타 컬럼 추가 대안은 거부(책임 혼합).

```sql
CREATE TABLE shares (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  file_id         UUID REFERENCES files(id) ON DELETE CASCADE,
  folder_id       UUID REFERENCES folders(id) ON DELETE CASCADE,
  permission_id   UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
  shared_by       UUID NOT NULL REFERENCES users(id),
  message         TEXT,                              -- max 1000자 (controller 검증)
  expires_at      TIMESTAMPTZ,                       -- NULL = 무기한. SHARE_EXPIRED cron(별도 트랙)이 도과 row 처리
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  revoked_at      TIMESTAMPTZ,
  revoked_by      UUID REFERENCES users(id),

  CHECK ((file_id IS NOT NULL)::int + (folder_id IS NOT NULL)::int = 1)
);

CREATE INDEX idx_shares_active     ON shares(shared_by, created_at DESC) WHERE revoked_at IS NULL;
CREATE INDEX idx_shares_permission ON shares(permission_id);  -- /api/shares/with-me JOIN
```

> `POST /api/folders/:id/share` endpoint는 A12 트랙 `a12-folder-shares-endpoint`에서 도입(§7.9, ADR #34). `SHARE_EXPIRED` audit cron 및 SSE `share.created/revoked` emission은 별도 트랙(deferred).

### 2.8 audit_log (append-only)

```sql
CREATE TABLE audit_log (
  id             BIGSERIAL PRIMARY KEY,
  occurred_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),  -- PostgreSQL keyword `timestamp` 회피 + frontend `occurredAt` 정합 (A2.0)
  actor_id       UUID REFERENCES users(id),
  actor_ip       INET,
  user_agent     TEXT,
  event_type     VARCHAR(50) NOT NULL,
  target_type    VARCHAR(20) NOT NULL,              -- file|folder|user|permission|share|system|audit|department
  target_id      UUID,
  before_state   JSONB,
  after_state    JSONB,
  metadata       JSONB,                             -- 추가 컨텍스트

  -- V3 baseline: 7개. V9(Wave 2 T4)에서 'department' 추가 → 8개.
  CHECK (target_type IN ('file', 'folder', 'user', 'permission', 'share', 'system', 'audit', 'department'))
);

-- 🔑 append-only 강제: DB 사용자 권한으로 UPDATE/DELETE 차단
REVOKE UPDATE, DELETE ON audit_log FROM app_user;
GRANT INSERT, SELECT ON audit_log TO app_user;

-- 파티셔닝 (월별)
-- CREATE TABLE audit_log_2026_01 PARTITION OF audit_log FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');

CREATE INDEX idx_audit_occurred_at ON audit_log(occurred_at DESC);
CREATE INDEX idx_audit_actor       ON audit_log(actor_id, occurred_at DESC);
CREATE INDEX idx_audit_target      ON audit_log(target_type, target_id, occurred_at DESC);
CREATE INDEX idx_audit_event       ON audit_log(event_type, occurred_at DESC);
```

### 2.9 upload_sessions (v1.x tus용, MVP는 선택)

```sql
CREATE TABLE upload_sessions (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         UUID NOT NULL REFERENCES users(id),
  folder_id       UUID NOT NULL REFERENCES folders(id),
  filename        VARCHAR(500) NOT NULL,
  total_bytes     BIGINT NOT NULL,
  uploaded_bytes  BIGINT NOT NULL DEFAULT 0,
  storage_key     UUID NOT NULL UNIQUE,
  status          VARCHAR(20) NOT NULL DEFAULT 'active',
  expires_at      TIMESTAMPTZ NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CHECK (status IN ('active', 'completed', 'expired', 'cancelled')),
  CHECK (uploaded_bytes <= total_bytes)
);

CREATE INDEX idx_upload_sessions_expires ON upload_sessions(expires_at) WHERE status = 'active';
```

### 2.10 legal_holds (v2.x reserved — ADR #46)

> **Status: v2.x deferred** (docs/00 §4.3, docs/03 §6.3, docs/04 §10). 실제 V_ 마이그레이션 미작성. 본 절은 설계 reserve — v2.x 진입 시 그대로 적용.

**메타 테이블 — 사유/만료/승인자/해제자 추적**:

```sql
CREATE TABLE legal_holds (
  id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  target_type              VARCHAR(10) NOT NULL,           -- 'file' | 'folder' | 'user'
  target_id                UUID NOT NULL,                  -- 다형 FK (CHECK 제약 + 코드 가드)
  reason                   TEXT NOT NULL,                  -- 지정 사유 (필수)
  placed_by                UUID NOT NULL REFERENCES users(id),
  placed_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  released_by              UUID REFERENCES users(id),
  released_at              TIMESTAMPTZ,
  release_reason           TEXT,                            -- 해제 사유
  expires_at               TIMESTAMPTZ,                     -- NULL = 무기한
  -- dual-approval (config 게이트, app.legal-hold.dual-approval.enabled)
  dual_approval_status     VARCHAR(20),                    -- NULL | 'pending' | 'approved' | 'released'
  secondary_approver_id    UUID REFERENCES users(id),
  secondary_approved_at    TIMESTAMPTZ,

  CHECK (target_type IN ('file', 'folder', 'user')),
  CHECK (
    dual_approval_status IS NULL
    OR dual_approval_status IN ('pending', 'approved', 'released')
  ),
  -- released_* 동시성 (둘 다 NULL 또는 둘 다 NOT NULL)
  CHECK ((released_by IS NULL) = (released_at IS NULL))
);

-- active hold 조회 (place 시 중복 검사, mutation 가드 fast lookup)
CREATE INDEX idx_legal_holds_active
  ON legal_holds(target_type, target_id)
  WHERE released_at IS NULL;

-- expiration cron 후보 스캔
CREATE INDEX idx_legal_holds_expires
  ON legal_holds(expires_at)
  WHERE released_at IS NULL AND expires_at IS NOT NULL;

-- 30일 재지정 락 검사 (release 후 30일 내 동일 target에 새 hold 거부)
CREATE INDEX idx_legal_holds_recently_released
  ON legal_holds(target_type, target_id, released_at)
  WHERE released_at IS NOT NULL;
```

**Cache flag — hot path mutation 가드** (ADR #31 forward-reference):

```sql
ALTER TABLE files
  ADD COLUMN legal_hold BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE folders
  ADD COLUMN legal_hold BOOLEAN NOT NULL DEFAULT FALSE;

-- purge cron skip / mutation 가드 fast WHERE 절
CREATE INDEX idx_files_legal_hold ON files(legal_hold) WHERE legal_hold = TRUE;
CREATE INDEX idx_folders_legal_hold ON folders(legal_hold) WHERE legal_hold = TRUE;
```

**동기화 invariant** (애플리케이션 레벨 보증, 트랜잭션 + `SELECT FOR UPDATE`):

- `legal_holds` insert (place) → 동일 트랜잭션에서 target 및 후손 cache flag = TRUE
- `legal_holds` update (release) → 동일 트랜잭션에서 target 및 후손이 다른 active hold로 cover되지 않을 때만 cache flag = FALSE
- folder hold place/release → 모든 후손 file/folder cache flag 일괄 갱신 (LTREE/CTE)
- user hold place/release → 해당 user 소유 file/folder cache flag 일괄 갱신
- 신규 file 업로드 시 ancestor folder 중 active hold가 있으면 신규 file `legal_hold = TRUE`로 INSERT

**v2.x 진입 시 추가 작업** (dev/active/legal-hold-design/ 참조):
- ADR #31 `HardPurgeService` WHERE 절에 `AND legal_hold IS NOT TRUE` 1줄 추가
- `FileMutationService`/`FolderMutationService`/`TrashService`/`FileVersionMutationService`/`ShareCommandService`/`PermissionService` mutation entry에 `LegalHoldGuard` 적용 (cache flag 검사 → 423)
- `Permission.MANAGE_LEGAL_HOLD` enum 추가 (docs/03 §3)
- `LEGAL_HOLD_VIOLATION` 에러 코드 추가 (docs/02 §8)
- `AuditEventType` placeholder 2종(`ADMIN_LEGAL_HOLD_PLACED`/`RELEASED`)을 emission 활성화 + 신규 2종(`ADMIN_LEGAL_HOLD_EXPIRED`, `ADMIN_LEGAL_HOLD_VIOLATION_BLOCKED`) 추가

### 2.11 pending_admin_approvals (v1.x reserved — ADR #47)

> **Status: v1.x deferred** (docs/00 §5 ADR #47, docs/03 §6.4 일반 명세, docs/04 §16 운영 명세). 실제 V_ 마이그레이션 미작성. 본 절은 설계 reserve — v1.x 진입 시 그대로 적용.

**메타 테이블 — Generic dual-approval framework** (Tier 0 = role 변경 / trash purge / retention 변경):

```sql
CREATE TABLE pending_admin_approvals (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  action_type           VARCHAR(40) NOT NULL,   -- 'role_change' | 'trash_purge' | 'retention_change' | (v1.x+ 'cron_toggle' | 'user_deactivate' | 'legal_hold_release')
  payload_json          JSONB NOT NULL,         -- action-specific args, schema는 application-level validation
  requested_by          UUID NOT NULL REFERENCES users(id),
  requested_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  status                VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
  secondary_approver_id UUID REFERENCES users(id),
  decided_at            TIMESTAMPTZ,
  decision_reason       TEXT,
  expires_at            TIMESTAMPTZ NOT NULL,   -- requested_at + app.dual-approval.ttl-days

  CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED')),
  -- terminal status면 decided_at NOT NULL, REQUESTED면 NULL
  CHECK ((decided_at IS NULL) = (status = 'REQUESTED')),
  -- secondary는 APPROVED/REJECTED일 때만 set (CANCELLED는 requested_by 본인, EXPIRED는 system)
  CHECK ((secondary_approver_id IS NOT NULL) = (status IN ('APPROVED', 'REJECTED')))
);

-- pending 목록 (admin 알림 페이지)
CREATE INDEX idx_pending_approvals_requested
  ON pending_admin_approvals(action_type, status)
  WHERE status = 'REQUESTED';

-- 요청자 이력 (취소 가능 항목 조회)
CREATE INDEX idx_pending_approvals_by_requester
  ON pending_admin_approvals(requested_by, requested_at DESC);

-- expiration cron 후보 스캔
CREATE INDEX idx_pending_approvals_expires
  ON pending_admin_approvals(expires_at)
  WHERE status = 'REQUESTED';

-- audit join 헬퍼 (decided 항목 history)
CREATE INDEX idx_pending_approvals_decided
  ON pending_admin_approvals(decided_at DESC)
  WHERE status IN ('APPROVED', 'REJECTED');
```

**State machine**:

```text
                        ┌──────────────► CANCELLED  (requested_by 본인 취소)
                        │                [terminal]
                        │
[ controller 진입 ]      │     ┌──────► APPROVED   (secondary 승인 + action 실행)
        │              │     │        [terminal]
        │              │     │
        ▼              │     │
   REQUESTED ──────────┼─────┤
   (status=default)   │     │
        │              │     └──────► REJECTED   (secondary 거부 + action 미실행)
        │              │              [terminal]
        │              │
        └──────────────┴──────► EXPIRED    (expiration cron, expires_at <= NOW)
                                          [terminal, actor_id=NULL]
```

**Transition 규칙** (애플리케이션 레벨 보증, 트랜잭션 + `SELECT FOR UPDATE`):

- `REQUESTED → APPROVED`: secondary 승인 → 트랜잭션 내 `pending_admin_approvals` UPDATE + payload_json deserialize → action 실행(role 변경/purge/retention 등) + audit emit. 실패 시 rollback → status=REQUESTED 복귀.
- `REQUESTED → REJECTED`: secondary 거부 + decision_reason set. action 미실행.
- `REQUESTED → CANCELLED`: requested_by 본인이 DELETE. action 미실행.
- `REQUESTED → EXPIRED`: expiration cron이 `expires_at <= NOW()` row UPDATE. action 미실행. actor_id=NULL.
- 기타 transition은 모두 거부 (`APPROVAL_ALREADY_DECIDED` 409).

**Self-approval 차단 invariant** (코드 가드, DB CHECK 부족 — secondary는 NULL/non-NULL 조합만 표현):

- `secondary_approver_id ≠ requested_by` (모든 action_type 공통)
- `action_type='role_change'` 추가 체크: `secondary_approver_id ≠ payload.userId` (target 사용자가 자기 role 변경 승인 차단)
- `action_type='trash_purge'/'retention_change'`: 추가 체크 없음 (target은 시스템 자료/정책이라 self 정의 불가)

**Action_type별 payload 스키마**:

| action_type | payload_json 필드 |
|---|---|
| `role_change` | `{userId, fromRole, toRole, reason}` |
| `trash_purge` | `{type: 'file'\|'folder', ids: [UUID, ...], reason?}` (single은 ids.length=1, bulk는 N) |
| `retention_change` | `{fromDays, toDays, reason}` |
| `legal_hold_release` (ADR #46 이관) | `{holdId, releaseReason}` |
| (v1.x+) `cron_toggle` | `{cronKey, fromEnabled, toEnabled, reason?}` |
| (v1.x+) `user_deactivate` | `{userId, reason}` |

**TTL** (`app.dual-approval.ttl-days`, default 7일): `expires_at = requested_at + INTERVAL '7 days'`. 만료 cron이 자동 EXPIRED transition.

**v1.x 진입 시 추가 작업** (dev/active/v1x-confirm-2admin-design/ 참조):
- ADR #46 `legal_holds.dual_approval_*` 컬럼 deprecation 계획 (V_ 마이그레이션 — column drop 또는 NULL 강제, payload_json='legal_hold_release'로 이관)
- `Permission.APPROVE_ADMIN_ACTION` enum 추가 (docs/03 §3.1)
- 신규 audit enum 4종 (docs/03 §4.1)
- 신규 에러 코드 4종 (docs/02 §8)
- `pending_admin_approvals` entity + repository + service + controller + expiration cron + email listener

### 2.12 ER 요약

```text
users ──┬── departments
        │
        ├── folders (owner) ───── folders (parent, tree)
        │                              │
        │                              ├── files ───── file_versions
        │                              │
        │                              └── permissions (resource)
        │
        ├── shares ──── permissions
        │
        ├── audit_log (actor)
        │
        ├── legal_holds (placed_by, target=user) ─── files/folders (target, cache flag)
        │   *v2.x reserved — ADR #46*
        │
        └── pending_admin_approvals (requested_by, secondary_approver_id)
            *v1.x reserved — ADR #47*
```

---

## 3. 정규화 함수

> ADR #16: 프론트(`src/lib/normalize.ts`)와 백엔드(`backend/.../common/normalize/NormalizeUtil.java`)는 동일 로직, 동일 fixtures(`docs/normalize-fixtures.json`)로 양방향 검증.
> CLAUDE.md §3 원칙 11에 따라 두 구현의 드리프트는 CI 게이트로 차단.

### 3.1 함수 분리

3개 함수가 있고, 사용 지점이 분리됨:

| 함수 | 입력 | 출력 사용처 | 적용 단계 |
|---|---|---|---|
| `normalizeFileName(name)` | 사용자 입력 이름 | 표시·저장용 `name` 컬럼 | NFC + trim + 제어문자 strip |
| `normalizedNameForDedup(name)` | 사용자 입력 이름 | UNIQUE 제약 검사용 `normalized_name` 컬럼 | NFC + 제어문자 strip + 공백 통일 + lowercase + trim |
| `normalizeForSearch(query)` | 검색어 | tsvector 매칭 키 | NFC + 제어문자 strip + 공백 통일 + lowercase + trim + 다중 공백 collapse |

### 3.2 정규화 단계 (Pipeline)

각 단계는 **순서가 중요** — fixtures가 단계별 결정성을 검증.

#### Step 1: NFC 정규화 (Unicode Normalization Form Canonical Composition)

- 한글 자모 분리형(NFD) → 결합형(NFC). 예: `"한"`(U+1112 U+1161 U+11AB, NFD) → `"한"`(U+D55C, NFC)
- 라이브러리:
  - JavaScript: `String.prototype.normalize('NFC')`
  - Java: `java.text.Normalizer.normalize(input, Normalizer.Form.NFC)`
  - Postgres: `NORMALIZE(input, NFC)` (15+ 내장)

#### Step 2: 제어 문자 제거 (Control Character Strip)

다음 코드포인트는 **모두 제거** (이름에 부적합):

| 범위 | 의미 | 처리 |
|---|---|---|
| `U+0000` (NUL) | NULL | **거부**: `INVALID_NAME_NUL_CHAR` (입력 검증 단계) |
| `U+0001 ~ U+001F` | C0 제어 (Tab=`U+0009`, LF=`U+000A`, CR=`U+000D` 포함) | **공백으로 치환** (다음 단계에서 통일) |
| `U+007F` (DEL) | DEL | 제거 |
| `U+0080 ~ U+009F` | C1 제어 | 제거 |
| `U+200B ~ U+200F` | Zero-Width Space, ZWNJ, ZWJ, LRM, RLM | 제거 |
| `U+202A ~ U+202E` | LRE, RLE, PDF, LRO, RLO (양방향 마커) | 제거 |
| `U+2060` | Word Joiner | 제거 |
| `U+FEFF` | BOM / Zero-Width No-Break Space | 제거 |

#### Step 3: 공백 통일 (Whitespace Unification)

다음 문자는 **모두 일반 공백 `U+0020`으로 치환**:

- `U+00A0` (NBSP, Non-Breaking Space)
- `U+2000 ~ U+200A` (En Quad, Em Quad, Thin Space 등)
- `U+202F` (Narrow No-Break Space)
- `U+205F` (Medium Mathematical Space)
- `U+3000` (Ideographic Space, fullwidth 공백)
- `Tab` (Step 2에서 치환된 결과 포함)

#### Step 4: 다중 공백 Collapse (Search 함수만)

- `normalizeForSearch`만 적용: `\s+` → 단일 공백
- `normalizeFileName` / `normalizedNameForDedup`은 **collapse 안 함** (사용자가 입력한 공백 보존)

#### Step 5: Lowercase (Dedup/Search 함수만)

- `normalizedNameForDedup` / `normalizeForSearch`에만 적용
- `String.prototype.toLowerCase()` (JavaScript) / `String.toLowerCase(Locale.ROOT)` (Java) — **로케일 비의존**
- 터키어 `İ`/`I` 문제 회피 위해 `Locale.ROOT` 강제

#### Step 6: Trim

- 양 끝 공백 제거 (Step 3에서 통일된 공백 + 일반 공백)

#### Step 7: 검증 (Validation, 정규화 후)

- **정규화 후 빈 문자열** → `INVALID_NAME_EMPTY` 거부
- **길이 제약**: 정규화 후 1~255자 (UTF-16 code unit 기준). 초과 시 `INVALID_NAME_TOO_LONG`
- **금지 문자**: `/`, `\`, NUL — 발견 시 `INVALID_NAME_FORBIDDEN_CHAR` 거부 (NUL은 Step 2에서 즉시 거부)
- **예약어**: Windows 호환성을 위해 `CON`, `PRN`, `AUX`, `NUL`, `COM1~COM9`, `LPT1~LPT9` (대소문자 무관) 거부 → `INVALID_NAME_RESERVED`
- **시작/끝 점·공백**: `.foo`는 허용, `foo.`는 거부 (`INVALID_NAME_TRAILING_DOT`), `   foo`/`foo   `는 Step 6에서 trim됨

### 3.3 의사 코드

```text
normalizeFileName(name):
    s = NFC(name)
    s = stripControlChars(s)            # NUL은 즉시 throw
    s = unifyWhitespace(s)
    s = trim(s)
    validate(s)                          # 빈 문자열 / 길이 / 금지 문자 / 예약어
    return s

normalizedNameForDedup(name):
    s = NFC(name)
    s = stripControlChars(s)
    s = unifyWhitespace(s)
    s = lowercase(s, Locale.ROOT)
    s = trim(s)
    validate(s)
    return s

normalizeForSearch(query):
    s = NFC(query)
    s = stripControlChars(s)
    s = unifyWhitespace(s)
    s = lowercase(s, Locale.ROOT)
    s = collapseWhitespace(s)            # \s+ → ' '
    s = trim(s)
    return s                             # 검색어는 길이/금지 문자 검증 안 함 (서버에서 부드럽게 처리)
```

### 3.4 fixtures 기반 검증 (CI 게이트)

- 단일 진실 출처: **`docs/normalize-fixtures.json`**
- 프론트(Vitest): `frontend/src/lib/normalize.test.ts`가 fixtures 로드해 각 케이스의 `expected.{normalizeFileName, normalizedNameForDedup, normalizeForSearch}` 일치 검증
- 백엔드(JUnit): `backend/src/test/java/.../NormalizeUtilTest.java`가 동일 fixtures 로드해 동일 검증
- CI: 두 테스트 잡 모두 통과해야 PR merge 가능 (양쪽 드리프트 차단)

### 3.5 Postgres 구현

DB는 **이름 저장 시점 검증의 보조 수단** — 진실의 출처는 애플리케이션 정규화 함수. DB 트리거는 동일 결과를 보장하는 안전망.

```sql
-- 의존: 없음 (Postgres 15+ 내장 NORMALIZE 사용)

CREATE OR REPLACE FUNCTION normalize_name_for_dedup(input TEXT)
RETURNS TEXT IMMUTABLE LANGUAGE SQL AS $$
  -- Step 1~3을 SQL로 구현. lowercase + trim
  -- 주의: 제어문자/공백 통일 일부는 애플리케이션 단계에 의존하므로
  --       이 트리거는 애플리케이션이 이미 정규화한 값을 재계산하는 안전망 역할
  SELECT LOWER(TRIM(REGEXP_REPLACE(NORMALIZE(input, NFC), '[\u0000-\u001F\u007F-\u009F\u200B-\u200F\u202A-\u202E\u2060\uFEFF]', '', 'g')))
$$;

CREATE OR REPLACE FUNCTION set_normalized_name()
RETURNS TRIGGER LANGUAGE PLPGSQL AS $$
BEGIN
  NEW.normalized_name := normalize_name_for_dedup(NEW.name);
  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_files_normalize
  BEFORE INSERT OR UPDATE OF name ON files
  FOR EACH ROW EXECUTE FUNCTION set_normalized_name();

CREATE TRIGGER trg_folders_normalize
  BEFORE INSERT OR UPDATE OF name ON folders
  FOR EACH ROW EXECUTE FUNCTION set_normalized_name();
```

> 트리거의 SQL 정규화는 **`normalizedNameForDedup`만 부분 일치** — `unifyWhitespace`(NBSP/fullwidth → space)는 SQL에서 누락. 애플리케이션이 항상 정규화 후 저장하므로 실제 동작상 문제 없음. fixtures CI는 애플리케이션 함수만 검증. DB 트리거 SQL 검증은 §10.3 마이그레이션 테스트에서 별도 (TODO).

---

## 4. 핵심 제약조건 요약

| 제약 | 위치 | 목적 |
|---|---|---|
| `UNIQUE (folder_id, normalized_name) WHERE deleted_at IS NULL` | files | 동일 폴더 내 이름 중복 금지 |
| `UNIQUE (parent_id, normalized_name) WHERE deleted_at IS NULL` | folders | 동일 부모 내 폴더명 중복 금지 |
| `UNIQUE (file_id, version_number)` | file_versions | 버전 번호 중복 금지 |
| `CHECK ((deleted_at IS NULL) = (purge_after IS NULL))` | files/folders | 휴지통 상태 일관성 |
| `FK current_version_id DEFERRABLE` | files | 파일-버전 순환 참조 (트랜잭션 내 해결) |
| `REVOKE UPDATE, DELETE` | audit_log | append-only 강제 |
| `CHECK (size_bytes >= 0)` | files, file_versions | 음수 방지 |
| `CHECK (scan_status IN ...)` | file_versions | enum 강제 |

---

## 5. 저장소 정책

### 5.1 객체 키 구조

```text
{bucket}/files/{YYYY}/{MM}/{storage_key}

예: my-bucket/files/2026/04/550e8400-e29b-41d4-a716-446655440000

⚠️ 원본 파일명은 객체 키에 포함하지 않음.
   → 경로 추측 공격 방지
   → 한글/특수문자 호환성 문제 회피
```

### 5.2 다운로드 시 원본명 복원

```text
응답 헤더:
    Content-Disposition: attachment; filename*=UTF-8''<percent-encoded-name>
    Content-Type: <mime_type>
    Content-Length: <size_bytes>
    ETag: "<checksum_sha256>"

RFC 5987 filename* 사용 (한글 파일명 호환).
filename= 파라미터는 fallback용 ASCII만.
```

### 5.3 Checksum 정책

```text
업로드 시:
  - 클라이언트가 SHA-256 계산해서 헤더로 전송 (선택)
  - 서버가 저장 직전 재계산 + 검증
  - file_versions.checksum_sha256에 저장

용도:
  - 데이터 무결성 검증
  - 중복 파일 감지 (동일 checksum → 심볼릭 링크 가능, v1.x)
  - ETag로 HTTP 캐시
```

### 5.4 파일 정책

```text
최대 크기: 2GB (MVP) → 서버 config
허용 확장자 화이트리스트 권장:
    문서: pdf, doc, docx, xls, xlsx, ppt, pptx, hwp, hwpx, txt, md, csv
    이미지: jpg, jpeg, png, gif, webp, svg, bmp, heic
    압축: zip, 7z, tar, gz
    기타: 도메인별 결정

차단 확장자 블랙리스트:
    exe, bat, cmd, com, scr, msi, dll, sh, ps1, vbs, js (업로드만; 스크립트 텍스트는 .txt로)

파일 확장자 검증:
    1. 파일명 확장자
    2. MIME magic number (file(1) 또는 libmagic)
    3. 둘 다 일치해야 허용 (.pdf 확장자인데 내용이 PE 바이너리면 거부)
```

### 5.5 바이러스 스캔 (v1.x)

```text
업로드 완료 → file_versions.scan_status = 'pending'
  → 비동기 큐에 엔티티 enqueue
  → ClamAV (자체) 또는 외부 서비스 (VirusTotal, S3 GuardDuty)
  → 결과에 따라 scan_status 업데이트

다운로드 시:
  - scan_status = 'pending'  → 경고 + 다운로드 허용 (운영 정책)
  - scan_status = 'infected' → 다운로드 차단, 알림
  - scan_status = 'clean'    → 정상 다운로드
```

### 5.6 Storage Orphan Cleanup (daily cron)

> ADR #38 — A15(`storage_key` 잔존)/A7 hard purge(deferred orphan) 한계 회수.

```text
트리거: @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul"), default disabled
        - app.storage.orphan-cleanup.enabled=true 운영자 활성화
        - A7 hard purge(자정) → 1시간 격차로 cascade orphan 회수

알고리즘:
  1. liveSet = file_versions.storage_key 전체 (Stream, fetchSize=200, readOnly hint)
       - NO `JOIN files WHERE deleted_at IS NULL` — trash 30일 grace 내 version도 보존 대상
       - hard purge가 file_versions cascade 삭제하면 다음 cron에서 자연 orphan 분류
  2. walk = StorageClient.listOlderThan(grace=24h) — {root}/{YYYY}/{MM}/{UUID} 트리
       - 비-UUID name / 디렉토리 leaf / symlink → skip + WARN
       - mtime > NOW-grace → skip (in-flight 업로드 race 회피)
  3. diff = walk 결과 storage_key 추출 후 liveSet에 없으면 candidate
  4. delete = per-row try/catch — IOException 1건 → ERROR log + 다음 candidate 진행
       - cap = max-per-run (default 10000) 도달 시 truncated=true + 다음 cron 재시도
  5. audit = STORAGE_ORPHAN_CLEANED 1건/run (REQUIRES_NEW)
       - target_type=system, target_id=NULL
       - metadata: {runId, scanned, candidates, deleted, failed, truncated, durationMs}

Properties (application.yml `app.storage.orphan-cleanup.*`):
  - enabled (default false)
  - cron (default "0 0 1 * * *")
  - zone (default Asia/Seoul)
  - max-per-run (default 10000)
  - grace-hours (default 24)
  - batch-size (default 200)

S3 확장:
  - StorageClient.listOlderThan은 Stream lazy 보장 — 큰 트리에서 메모리 폭증 회피
  - S3StorageClient(v1.x)는 ListObjectsV2 paginator로 자연 매핑 (LastModified 비교)
```

⚠️ MVP single-instance 가정. 멀티 인스턴스화 시 `@SchedulerLock` 도입 별도 ADR.

---

## 6. 트랜잭션 경계

### 6.1 파일 업로드 완료 (동시성 제어)

가장 중요한 트랜잭션. Race condition 방지의 핵심.

```sql
BEGIN;

-- 1. 부모 폴더 잠금 (삭제/이동 방지, 이름 충돌 검사의 직렬화)
SELECT id FROM folders
  WHERE id = :folder_id AND deleted_at IS NULL
  FOR UPDATE;

-- 2. 동일 이름 파일 존재 검사
SELECT id, current_version_id
  FROM files
  WHERE folder_id = :folder_id
    AND normalized_name = normalize_name_for_dedup(:filename)
    AND deleted_at IS NULL
  FOR UPDATE;  -- 병렬 업로드 완료 직렬화

-- 3a. 파일 없음 → 신규 생성
INSERT INTO files (id, folder_id, name, owner_id, size_bytes, mime_type)
  VALUES (...) RETURNING id;

INSERT INTO file_versions (file_id, version_number, storage_key, size_bytes, checksum_sha256, uploaded_by)
  VALUES (..., 1, ..., ..., ..., :user_id) RETURNING id;

UPDATE files SET current_version_id = :new_version_id WHERE id = :new_file_id;

-- 3b. 파일 존재 + resolution='new_version'
INSERT INTO file_versions (file_id, version_number, ...)
  SELECT :existing_file_id, COALESCE(MAX(version_number), 0) + 1, ...
    FROM file_versions WHERE file_id = :existing_file_id;

UPDATE files SET current_version_id = :new_version_id, size_bytes = :new_size, updated_at = NOW()
  WHERE id = :existing_file_id;

-- 4. 스토리지 쿼터 업데이트
UPDATE users SET storage_used = storage_used + :size_bytes
  WHERE id = :user_id;

-- 5. 감사 로그
INSERT INTO audit_log (actor_id, event_type, target_type, target_id, after_state, ...)
  VALUES (...);

COMMIT;
```

### 6.2 실패 시 복구

```text
트랜잭션 실패 → S3 객체는 이미 업로드됨 (orphan)

처리:
  - 업로드는 임시 prefix (tmp/) 에 먼저 저장
  - 트랜잭션 성공 후 최종 경로로 COPY + DELETE (또는 tag 변경)
  - 실패 시 tmp/ 객체는 S3 Lifecycle rule로 24시간 후 자동 삭제

또는:
  - two-phase commit 스타일: 업로드 → DB 기록 (temp) → S3 finalize → DB commit
```

### 6.3 파일 이동

```sql
BEGIN;

-- from/to 폴더 둘 다 잠금 (데드락 방지: id 순으로 정렬)
SELECT id FROM folders
  WHERE id IN (:from_folder, :to_folder) AND deleted_at IS NULL
  ORDER BY id FOR UPDATE;

-- to 폴더에 동일 이름 파일 존재 검사
SELECT id FROM files
  WHERE folder_id = :to_folder
    AND normalized_name = :normalized_name
    AND deleted_at IS NULL
  FOR UPDATE;
-- 존재 시 409 반환 (ConflictDialog → 이름 변경 후 재시도)

UPDATE files SET folder_id = :to_folder, updated_at = NOW()
  WHERE id = :file_id;

INSERT INTO audit_log (..., event_type = 'file.moved', before_state, after_state);

COMMIT;
```

### 6.4 파일 이름 변경 (충돌 처리)

```sql
BEGIN;

SELECT id FROM folders WHERE id = :folder_id FOR UPDATE;

-- 신규 이름이 이미 존재하는지 검사
SELECT id FROM files
  WHERE folder_id = :folder_id
    AND normalized_name = normalize_name_for_dedup(:new_name)
    AND id != :file_id
    AND deleted_at IS NULL;
-- 존재 시 409 RENAME_CONFLICT (프론트 RenameDialog에서 다른 이름 요청)

UPDATE files SET name = :new_name, updated_at = NOW()
  WHERE id = :file_id;

INSERT INTO audit_log (..., event_type = 'file.renamed');

COMMIT;
```

### 6.5 휴지통 이동 / 복원

> **보존 기간**: `application.yml`의 `app.trash.retention.days` (default 30)에서 외부화 — `FileMutationService`/`FolderMutationService`가 `TrashRetentionProperties`를 주입받아 `purge_after = deleted_at + days`로 설정. 0/음수 입력은 default 30으로 보정 (실수로 즉시 hard purge 후보가 되는 사고 방지). 무중단 변경은 v1.x++ (Spring `@ConfigurationProperties`는 부팅 시 바인딩).

```sql
-- 이동 (보존 기간은 app.trash.retention-days, default 30일)
BEGIN;
UPDATE files
  SET deleted_at = NOW(),
      purge_after = NOW() + INTERVAL '30 days',
      original_folder_id = folder_id,
      deleted_by = :actor_id
  WHERE id = ANY(:ids);
INSERT INTO audit_log ...;
COMMIT;

-- 복원 (원위치 폴더의 이름 충돌 재검사 필수)
BEGIN;
SELECT id FROM folders WHERE id = :original_folder_id FOR UPDATE;

-- 복원하려는 이름이 현재 사용 중인지 검사
SELECT id FROM files
  WHERE folder_id = :original_folder_id
    AND normalized_name = :normalized_name
    AND deleted_at IS NULL;
-- 존재 시 409 → 프론트에서 이름 변경 후 복원 선택지 제시

UPDATE files
  SET deleted_at = NULL,
      purge_after = NULL,
      folder_id = original_folder_id,
      original_folder_id = NULL,
      deleted_by = NULL
  WHERE id = ANY(:ids);
COMMIT;
```

#### 6.5.1 `deleted_by` 컬럼 (V10, Wave 2 T9 follow-up — 2026-05-08)

**스키마**:

```sql
ALTER TABLE files
  ADD COLUMN deleted_by UUID REFERENCES users(id) ON DELETE SET NULL,
  ADD CONSTRAINT files_deleted_by_check
      CHECK (deleted_at IS NOT NULL OR deleted_by IS NULL);

ALTER TABLE folders
  ADD COLUMN deleted_by UUID REFERENCES users(id) ON DELETE SET NULL,
  ADD CONSTRAINT folders_deleted_by_check
      CHECK (deleted_at IS NOT NULL OR deleted_by IS NULL);
```

**계약**:

- **단방향 CHECK**: `deleted_at IS NOT NULL OR deleted_by IS NULL` — 활성 row(`deleted_at IS NULL`)에 deleter가 set되는 것만 차단. trash row의 NULL은 허용 (V10 이전 row의 backfill 미실시 + ON DELETE SET NULL fallback 수용).
- **ON DELETE SET NULL**: deleter 계정이 hard-delete돼도 trash row는 보존, deleter 정보만 NULL로 클리어.
- **Backfill 미실시**: V10 이전에 휴지통에 들어간 row는 `deleted_by IS NULL`로 그대로. audit_log derivation은 fragile/expensive — UI는 컷오프 이전을 "—"로 렌더한다 (docs/04 §8.3).
- **Folder cascade**: `softDeleteByIds` JPQL이 `:actor_id`를 받아 root + 모든 자손에 동시 set. `FileMutationService.softDelete` / `FolderMutationService.softDelete`가 actor 전달, restore에서 NULL로 클리어.
- **인덱스 미추가**: `deleted_by` 필터링은 v1.x++. admin 쿼리는 `deleted_at DESC` 키 사용.
- **Audit 정합성**: 새 audit emit 0. 기존 `FILE_DELETED` / `FOLDER_DELETED` actor_id가 영구 보존(V4 REVOKE 정책)되므로 cross-check 가능.

### 6.6 새 버전 업로드 시 optimistic concurrency

```text
프론트가 업로드 요청 시 "내가 본 current_version_id" 전송:

POST /api/files/:id/versions
  body: { storage_key, size_bytes, checksum, expected_current_version_id }

백엔드 트랜잭션:
  SELECT current_version_id FROM files WHERE id = :id FOR UPDATE;

  IF current_version_id != expected_current_version_id:
    ROLLBACK
    RETURN 409 VERSION_CONFLICT {
      current_version: <latest_version_info>,
      your_version: <expected_version_info>
    }

  ELSE:
    INSERT file_versions ...
    UPDATE files.current_version_id ...
    COMMIT
    RETURN 200
```

프론트는 409를 받으면 "이후 다른 사용자가 새 버전을 올렸습니다. 최신 버전을 확인하시겠습니까?" 다이얼로그 표시.

### 6.7 권한 변경

```sql
BEGIN;
-- 동일 (resource, subject)에 기존 권한이 있으면 UPDATE, 없으면 INSERT
INSERT INTO permissions (...) VALUES (...)
  ON CONFLICT (resource_type, resource_id, subject_type, COALESCE(subject_id, ...))
  DO UPDATE SET preset = EXCLUDED.preset, expires_at = EXCLUDED.expires_at;

INSERT INTO audit_log (..., event_type = 'permission.changed', before_state, after_state);
COMMIT;
```

---

## 7. API 계약

### 7.1 인증 / 공통 헤더

ADR #12에 따라 인증은 **쿠키 세션 + Spring Session JDBC** + **CSRF double-submit**.

```text
요청 공통:
  Cookie: SESSION=<spring-session-id>          (HttpOnly, Secure, SameSite=Lax)
  X-CSRF-Token: <csrf-token>                    (mutation 시 필수, GET 시 생략)
  X-Request-Id: <uuid>                          (클라이언트 생성, 로그 추적용)
  Accept-Language: ko-KR
  Origin: <frontend-origin>                     (CORS 검증)

응답 공통:
  X-Request-Id: <echo>
  X-RateLimit-Remaining: <int>
  Set-Cookie: SESSION=...                       (세션 갱신 시)
  Set-Cookie: XSRF-TOKEN=...                    (CSRF 발급 시 — JS 읽기 가능)
```

CSRF 정책:
- 쿠키 `XSRF-TOKEN` (HttpOnly **아님**, JS 읽기 가능) ↔ 헤더 `X-CSRF-Token` 일치 검증
- `GET /api/auth/csrf`로 발급, `mutation` 요청 전 클라가 헤더에 첨부
- 불일치 시 → `403 CSRF_MISMATCH`

### 7.2 에러 응답 포맷

```json
{
  "error": {
    "code": "UPLOAD_CONFLICT",
    "message": "동일한 이름의 파일이 이미 존재합니다",
    "details": {
      "existingFileId": "file_xxx",
      "existingFileName": "document.pdf"
    }
  },
  "requestId": "req_xxx"
}
```

### 7.3 매트릭스 컬럼 정의

| 컬럼 | 의미 |
|---|---|
| Guard | Spring Security 권한 가드. `@PreAuthorize` 표현식 또는 `PermissionService.check(...)` 호출 |
| TX | `@Transactional` 전파/격리 + 행 잠금. mutation에 필수, GET은 `—`. `REQUIRED + FOR UPDATE` = 트랜잭션 내 `SELECT ... FOR UPDATE` 사용 (race 방지, §6 참조) |
| Norm | 입력 이름의 정규화 적용 지점. `name → NormalizeUtil.normalize(name) → normalized_name`. `q → normalizeForSearch(q)` |
| SoftDel | soft delete 필터. `WHERE deleted_at IS NULL` = 활성 리소스만, `WHERE deleted_at IS NOT NULL` = 휴지통, `SET deleted_at = NOW()` = 휴지통 이동, `SET deleted_at = NULL + UNIQUE 재검사` = 복원 |
| Errors | 가능한 에러 코드 (docs/02 §8). `401/403`은 모든 인증 endpoint 공통 — 표에서 생략 가능, 명시된 경우만 기재 |

> 모든 mutation endpoint는 CSRF 토큰 검증 + 세션 인증 필수. 미명시 시 표 단순화를 위해 `403 CSRF_MISMATCH`, `401 UNAUTHORIZED`는 생략.

### 7.4 인증 (Auth)

**근거**: ADR #12 (쿠키 세션 + Spring Session JDBC), ADR #18~#22, docs/03 §2.

**Guard 약어**: `permitAll` = 인증 불필요 (login/csrf), `isAuthenticated()` = 세션 검증.

| Method | Path | Guard | TX | Norm | SoftDel | Errors |
|---|---|---|---|---|---|---|
| POST | `/api/auth/login` | permitAll | REQUIRED (audit·loginfail 카운터) | email→lowercase | — | 400 VALIDATION_ERROR, 401 UNAUTHORIZED, 423 ACCOUNT_LOCKED, 403 CSRF_MISMATCH |
| POST | `/api/auth/logout` | isAuthenticated | REQUIRED (audit) | — | — | 403 CSRF_MISMATCH |
| GET  | `/api/auth/me` | isAuthenticated | — | — | — | 401 UNAUTHORIZED |
| GET  | `/api/auth/csrf` | permitAll | — | — | — | — |
| POST | `/api/auth/password/forgot` | permitAll + rate-limit (ADR #44) | REQUIRED (token+email+audit) | email→lowercase | — | 400 VALIDATION_ERROR, 429 RATE_LIMIT_EXCEEDED |
| POST | `/api/auth/password/reset` | permitAll | REQUIRED (token verify+pw update+session invalidate+audit) | — | — | 400 VALIDATION_ERROR, 400 INVALID_TOKEN |
| POST | `/api/auth/password/change` | isAuthenticated | REQUIRED (pw verify+update+other sessions invalidate+audit) | — | — | 400 VALIDATION_ERROR, 401 INVALID_CREDENTIALS, 403 CSRF_MISMATCH |

> **Norm 컬럼**: 이메일은 `NormalizeUtil.normalize()` (NFC + 파일명 정규화) 적용 대상 아님. `email.trim().toLowerCase()` 별도 적용 (docs/03 §2.7).
> **TX 컬럼**: login은 audit + loginfail Redis 카운터 부수효과 있음 → REQUIRED. logout은 session.invalidate + audit.

```text
POST /api/auth/login
  Headers:  X-CSRF-Token: <token>            (필수, GET /api/auth/csrf로 사전 발급)
            Cookie: XSRF-TOKEN=<token>       (double-submit 검증)
  Request:  { email: string, password: string }
  Validation:
    - email: RFC 5322 형식, 1~254자, lowercase 정규화 후 비교
    - password: 1~128자 (정책 검증 X — 등록·변경 시점에만 적용)
  Response: 200 {
              user: { id, email, name, kind, mustChangePassword },
              departments: [{ id, name }],
              roles: ['MEMBER' | 'AUDITOR' | 'ADMIN'][],
              effectivePermissionsCacheKey: string
            }
            Set-Cookie: SESSION=<id>; HttpOnly; Secure; SameSite=Lax; Path=/
  Side-effects:
            - Spring Security changeSessionId() (session fixation 방지)
            - session attribute: { userId, roles, issuedAt, permissionsCacheKey }
            - Redis: DEL loginfail:{email}
            - audit: user.login.success
  Errors:
    400 VALIDATION_ERROR  { details: { field: 'email' | 'password', rule: 'format' | 'length' } }
    401 UNAUTHORIZED      { reason: 'INVALID_CREDENTIALS' }
                          (이메일 미존재·PW 불일치 모두 동일 응답 — 계정 enumeration 방지)
                          Side-effects: INCR loginfail:{email}, EXPIRE 900, audit user.login.failed
    423 ACCOUNT_LOCKED    { retryAfterSec: <ttl-seconds> }
                          (5회 누적 실패 후. 락 상태 시도도 카운트되어 TTL 갱신)
                          audit: user.login.failed(reason=locked) — 첫 락 진입 시만 user.locked 추가 발급
    403 CSRF_MISMATCH     { } (CSRF 토큰 누락/불일치)
    400 PASSWORD_CHANGE_REQUIRED  { } (mustChangePassword=true인 사용자 — A1.5에서 처리, MVP는 응답 후 클라가 변경 화면 유도. ADR #21)

POST /api/auth/logout
  Headers:  X-CSRF-Token: <token>            (필수)
            Cookie: SESSION=<id>             (현재 세션 식별)
  Response: 204
            Set-Cookie: SESSION=; Max-Age=0; Path=/
  Side-effects:
            - HttpServletRequest.getSession().invalidate() (DELETE FROM SPRING_SESSION WHERE PRIMARY_ID=<id>)
            - audit: user.logout
  Errors:
    403 CSRF_MISMATCH

GET /api/auth/me
  Headers:  Cookie: SESSION=<id>
  Response: 200 {
              user: {
                id: string (UUID),
                email: string,
                name: string,
                kind: 'human' | 'service',
                mustChangePassword: boolean
              },
              departments: [{ id: string, name: string, path: string (LTREE) }],
              roles: ('MEMBER' | 'AUDITOR' | 'ADMIN')[],
              effectivePermissionsCacheKey: string
                /* 권한 변경 시 변경됨. 프론트는 이 값을 watch하여
                   per-resource currentUserPermissions 캐시를 invalidate
                   (docs/01 §6.3 광역 무효화 trigger). */
            }
  Errors:
    401 UNAUTHORIZED      (세션 만료/없음 — Set-Cookie: SESSION=; Max-Age=0 동반)
                          { reason: 'SESSION_EXPIRED' | 'NO_SESSION' }
  Note:     **effectivePermissions full resolve 미포함** (ADR #22). 권한은 per-resource 응답
            (예: GET /api/folders/:id 응답에 currentUserPermissions: ['READ', 'EDIT'] 동봉).

GET /api/auth/csrf
  Response: 200 { csrfToken: string }
            Set-Cookie: XSRF-TOKEN=<token>; SameSite=Lax; Path=/  (HttpOnly **아님** — JS 읽기 가능)
  Note:     Spring Security CookieCsrfTokenRepository.withHttpOnlyFalse() 기본 동작.
            mutation 요청 전 클라가 1회 호출 → XSRF-TOKEN 쿠키 → JS가 읽어
            X-CSRF-Token 헤더로 전송 (double-submit). HTTP 헤더 case-insensitive
            라 frontend는 'X-CSRF-TOKEN'(uppercase) 송신해도 backend `X-CSRF-Token`
            매핑과 호환.

  Frontend 패턴 분기 (회귀 가드 — docs/03 §2.2 callout 동기화):
    - 인증 후 mutation: readCookie('XSRF-TOKEN') 동기 — 쿠키가 이미 있다고 가정,
      이 endpoint 사전 호출 불필요. createFolder/moveItem/renameFile/
      softDeleteFile|Folder/restoreFile|Folder|Version/purgeTrashItem/revokeShare/
      adminToggleCron/postShareCreate/adminBulkTrash/adminRevokePermission 등
      이 패턴 채택.
    - 비인증 첫 호출 (login/passwordChange/admin*): ensureCsrfToken() 비동기 —
      cookie 부재 시 본 endpoint 사전 호출 후 송신.
    - 면제 endpoint (/api/auth/signup, /api/auth/password/forgot, .../reset):
      SecurityConfig.ignoringRequestMatchers로 backend가 검증 면제. frontend는
      헤더 미송신 OK.

  회귀 가드:
    frontend/src/lib/api.csrfMutations.test.ts (sweep PR #121, 13 케이스)
    + api.createFolder.test.ts (hotfix PR #115, 4 케이스)
    + api.adminRevokePermission.test.ts (PR #123, 4 케이스).

POST /api/auth/password/forgot                     (A1.5, ADR #42·#43, #44)
  Headers:  (CSRF 불필요 — SecurityConfig ignoringRequestMatchers)
  Request:  { email: string }
  Validation:
    - email: @Email + @NotBlank, 최대 254자
  Rate limit (ADR #44):
    - email(lower-cased) + IP 두 키 독립 버킷, OR 차단, 60s 1회.
    - X-Forwarded-For 첫 값 우선 → fallback RemoteAddr.
    - 한도 초과 시 RateLimitExceededException → 429.
    - in-memory ConcurrentHashMap, single-instance MVP (Redis는 v1.x).
  Response: 200 { message: '입력하신 이메일로 재설정 링크를 보냈습니다.' }
            (anti-enumeration — 이메일 미존재여도 동일 응답·동일 latency 목표)
  Side-effects:
            - 이메일 존재 시: password_reset_tokens INSERT (token_hash=SHA-256, expires_at=now+30m)
            - EmailService.sendPasswordReset(email, rawToken) — dev=콘솔, prod=SMTP
            - audit: user.password.forgot_requested (이메일 존재여부 무관, found 플래그)
  Errors:
    400 VALIDATION_ERROR    { details: { field: 'email', rule: 'format' | 'length' } }
    429 RATE_LIMIT_EXCEEDED { code: 'RATE_LIMIT_EXCEEDED', retryAfterSec: <int> }
                            Header: Retry-After: <seconds>   (헤더와 body 동일 값)

POST /api/auth/password/reset                      (A1.5, ADR #42·#43)
  Headers:  (CSRF 불필요 — SecurityConfig ignoringRequestMatchers)
  Request:  { token: string, newPassword: string }
  Validation:
    - token: @NotBlank
    - newPassword: @NotBlank, 8~128자
  Response: 200 { message: '비밀번호가 변경되었습니다.' }
  Side-effects:
            - token_hash 매칭 + expires_at>now + used_at=null 검증 후 used_at=now (UPDATE)
            - users.password_hash = bcrypt(newPassword)
            - 해당 사용자의 모든 SPRING_SESSION DELETE (FindByIndexNameSessionRepository)
            - audit: user.password.reset
  Errors:
    400 VALIDATION_ERROR  { details: { field: 'newPassword', rule: 'length' } }
    400 INVALID_TOKEN     { } (만료·이미 사용·미존재 — 동일 코드)

POST /api/auth/password/change                     (A1.5, ADR #43)
  Headers:  X-CSRF-Token: <token>           (필수)
            Cookie: SESSION=<id>            (인증 필요)
  Request:  { currentPassword: string, newPassword: string }
  Validation:
    - currentPassword: @NotBlank, 최대 200자
    - newPassword: @NotBlank, 8~128자
  Response: 200 { message: '비밀번호가 변경되었습니다.' }
  Side-effects:
            - currentPassword bcrypt match 확인 (불일치 → 401 INVALID_CREDENTIALS)
            - users.password_hash = bcrypt(newPassword)
            - **현재 세션 제외** 다른 모든 세션 invalidate (FindByIndexNameSessionRepository.findByPrincipalName)
            - audit: user.password.changed
  Errors:
    400 VALIDATION_ERROR    { details: { field: 'newPassword', rule: 'length' } }
    401 UNAUTHORIZED        (미인증 세션)
    401 INVALID_CREDENTIALS { } (현재 비밀번호 불일치)
    403 CSRF_MISMATCH
```

**관련 audit 이벤트** (docs/03 §2.10): `user.login.success` / `user.login.failed` / `user.logout` / `user.session.expired` / `user.locked` / `user.unlocked` / `user.password.changed` / `user.password.forgot_requested` (A1.5) / `user.password.reset` (A1.5).

### 7.5 폴더 (Folders)

| Method | Path | Guard | TX | Norm | SoftDel | Errors |
|---|---|---|---|---|---|---|
| GET | `/api/folders/tree` | `hasPermission(#root, 'folder', 'READ')` (필터링) | — | — | `WHERE deleted_at IS NULL` | — |
| GET | `/api/folders/:id` | `@PreAuthorize("hasPermission(#id, 'folder', 'READ')")` | — | — | `WHERE deleted_at IS NULL` | 404 NOT_FOUND |
| GET | `/api/folders/:id/items` | `@PreAuthorize("hasPermission(#id, 'folder', 'READ')")` | — | — | `WHERE deleted_at IS NULL` (subfolders/files 모두) | 404 NOT_FOUND |
| POST | `/api/folders` | `#req.parentId == null ? hasRole('ADMIN') : hasPermission(#req.parentId, 'folder', 'EDIT')` (ADR #30) | REQUIRED + FOR UPDATE on `parentId` | `name → normalized_name` | — | 400 VALIDATION_ERROR, 404 (parent), 409 RENAME_CONFLICT |
| PATCH | `/api/folders/:id` | `hasPermission(#id, 'folder', 'EDIT')` | REQUIRED + FOR UPDATE on `id` + sibling | `name → normalized_name` | `WHERE deleted_at IS NULL` | 409 RENAME_CONFLICT |
| POST | `/api/folders/:id/move` | `hasPermission(#id, 'folder', 'MOVE')` AND `(#req.targetParentId == null ? hasRole('ADMIN') : hasPermission(#req.targetParentId, 'folder', 'EDIT'))` (ADR #30) | REQUIRED + FOR UPDATE on `id`, `targetParentId` | — (이름 유지) | `WHERE deleted_at IS NULL` | 400 MOVE_INTO_SELF, 400 MOVE_INTO_DESCENDANT, 404 TARGET_NOT_FOUND, 409 RENAME_CONFLICT |
| DELETE | `/api/folders/:id` | `hasPermission(#id, 'folder', 'DELETE')` | REQUIRED + FOR UPDATE | — | `SET deleted_at = NOW()` (재귀: 후손 폴더/파일 cascade — root 1회 audit) | 404 |
| POST | `/api/folders/:id/restore` | `hasPermission(#id, 'folder', 'DELETE')` | REQUIRED + FOR UPDATE on `id`, parent | optional `name → normalized_name` (v1.x) | `SET deleted_at = NULL` + UNIQUE 재검사 (자기 자신만 복원, 후손 잔존) | 404, 409 RESTORE_CONFLICT (원본 이름), 409 RENAME_CONFLICT (새 이름) |

```text
POST /api/folders
  Request:  { parentId: string (UUID), name: string }
  Response: 201 { folder: FolderDto, breadcrumb: FolderDto[] }
  TX:       INSERT folders → audit_log INSERT (FOLDER_CREATE) → COMMIT

PATCH /api/folders/:id
  Request:  { name: string }
  Response: 200 { folder: FolderDto }
  TX:       SELECT FOR UPDATE folders WHERE id=:id AND deleted_at IS NULL
            → UNIQUE (parent_id, normalized_name) WHERE deleted_at IS NULL 검증
            → UPDATE → audit_log INSERT (FOLDER_RENAME) → COMMIT

POST /api/folders/:id/move
  Request:  { targetParentId: string }
  Response: 200 { folder: FolderDto, breadcrumb: FolderDto[] }
  TX:       SELECT FOR UPDATE on source + target → 후손 관계 검사 (closure or 재귀)
            → UNIQUE 검증 → UPDATE parent_id → audit_log (FOLDER_MOVE) → COMMIT

DELETE /api/folders/:id   (휴지통 이동)
  Response: 204
  TX:       재귀 BFS로 후손 폴더 id 수집 (visited Set + hop limit)
            → 후손 폴더 batch UPDATE (deleted_at = NOW(), purge_after = NOW() + 30d)
            → 후손 파일 batch UPDATE (folder_id IN 후손 ids)
            → audit_log INSERT (FOLDER_DELETE, root 1회만, after_state.descendantFolders/Files 카운트) → COMMIT
  Note:     cascade 후손 폴더/파일은 동일 트랜잭션에서 soft-delete되며 audit는 root에 대해 1회만 발행.
            후손 파일은 FILE_DELETED를 발행하지 않는다(audit 폭증 회피).

POST /api/folders/:id/restore
  Response: 200 { folder, breadcrumb }
  TX:       SELECT FOR UPDATE (soft-deleted) → 원위치 parent_id 활성 확인
            → UNIQUE 충돌 검사 → SET deleted_at = NULL → audit_log (FOLDER_RESTORE)
  Note:     자기 자신만 복원 (후손은 휴지통 잔존). original_parent_id가 soft-deleted면 404.
            후손 일괄 복원은 별도 endpoint 트랙(out of scope).
  Errors:   404 (target/original parent), 409 RESTORE_CONFLICT (원위치에 동일 normalized_name 활성 폴더)

GET /api/folders/:id/items
  Request:  Query params — sort=NAME|UPDATED_AT|SIZE (default NAME), dir=ASC|DESC (default ASC)
  Response: 200 { items: FolderItemDto[] }
            FolderItemDto = { id, type: 'folder'|'file', name, size?, mimeType?, updatedAt, updatedBy, parentId }
  Order:    folders 그룹 → files 그룹 (각 그룹 내 sort/dir 적용).
            sort=SIZE 시 folder 그룹은 size 무의미 — name asc fallback.
  SoftDel:  parent 폴더 soft-deleted면 404. 자식 중 deleted_at IS NOT NULL은 제외.
  Errors:   404 NOT_FOUND
  Note:     Wave 2 T6 — frontend explorer mock 제거 + listing real wire용 endpoint.
```

### 7.6 파일 (Files)

| Method | Path | Guard | TX | Norm | SoftDel | Errors |
|---|---|---|---|---|---|---|
| GET | `/api/folders/:id/files` | `hasPermission(#id, 'folder', 'READ')` | — | — | `WHERE deleted_at IS NULL` | 404 |
| GET | `/api/files/:id` | `hasPermission(#id, 'file', 'READ')` | — | — | `WHERE deleted_at IS NULL` | 404 |
| POST | `/api/files` (multipart) | `hasPermission(#req.folderId, 'folder', 'UPLOAD')` | REQUIRED + FOR UPDATE on folder + sibling | `file.originalFilename → normalized_name` | `WHERE deleted_at IS NULL` | 403, 409 RENAME_CONFLICT, 413 QUOTA_EXCEEDED, 415 UNSUPPORTED_MEDIA_TYPE |
| PATCH | `/api/files/:id` | `hasPermission(#id, 'file', 'EDIT')` | REQUIRED + FOR UPDATE | `name → normalized_name` | `WHERE deleted_at IS NULL` | 409 RENAME_CONFLICT |
| POST | `/api/files/:id/move` | `hasPermission(#id, 'file', 'MOVE')` AND `hasPermission(#req.targetFolderId, 'folder', 'EDIT')` | REQUIRED + FOR UPDATE on `id` + targetFolder | — | `WHERE deleted_at IS NULL` | 404 TARGET_NOT_FOUND, 409 RENAME_CONFLICT |
| DELETE | `/api/files/:id` | `hasPermission(#id, 'file', 'DELETE')` | REQUIRED + FOR UPDATE | — | `SET deleted_at = NOW(), purge_after = NOW()+30d` | 404 |
| POST | `/api/files/:id/restore` | `hasPermission(#id, 'file', 'DELETE')` | REQUIRED + FOR UPDATE | optional `name → normalized_name` (v1.x) | `SET deleted_at = NULL` + UNIQUE 재검사 | 404, 409 RESTORE_CONFLICT (원본 이름), 409 RENAME_CONFLICT (새 이름) |
| POST | `/api/files/:id/versions` | `hasPermission(#id, 'file', 'EDIT')` | REQUIRED + FOR UPDATE on file row | — | `WHERE deleted_at IS NULL` | 409 VERSION_CONFLICT, 413 QUOTA_EXCEEDED, 415 |
| GET | `/api/files/:id/download` | `hasPermission(#id, 'file', 'READ')` | — | — | `WHERE deleted_at IS NULL` | 404 |
| GET | `/api/files/:id/preview` | `hasPermission(#id, 'file', 'READ')` | — | — | `WHERE deleted_at IS NULL` | 404 |
| GET | `/api/files/:id/versions` | `hasPermission(#id, 'file', 'READ')` | — | — | `WHERE deleted_at IS NULL` | 404 |
| GET | `/api/files/:id/activity` | `hasPermission(#id, 'file', 'READ')` | — | — | `WHERE deleted_at IS NULL` | 404 |

> 파일 업로드 = 단일-POST multipart (`POST /api/files`, ADR #36 / A15 closure). tus 프로토콜은 §7.7로 보존되나 ADR #13 supersede에 따라 v1.x 재이월 — MVP 미구현. 메타데이터/버전 mutation은 위 표 내 다른 행 참조.

```text
PATCH /api/files/:id
  Request:  { name: string }
  Response: 200 { file: FileDto }
  TX:       SELECT FOR UPDATE → UNIQUE (folder_id, normalized_name) WHERE deleted_at IS NULL
            → UPDATE → audit_log (FILE_RENAME) → COMMIT
  Norm:     normalized_name = NormalizeUtil.normalize(name)

POST /api/files/:id/move
  Request:  { targetFolderId: string }
  Response: 200 { file: FileDto }
  TX:       SELECT FOR UPDATE on file + target folder
            → UNIQUE 충돌 검사 → UPDATE folder_id → audit_log (FILE_MOVE) → COMMIT

DELETE /api/files/:id
  Response: 204
  TX:       SELECT FOR UPDATE → SET deleted_at, purge_after → audit_log (FILE_DELETE) → COMMIT

POST /api/files/:id/restore
  TX:       SELECT FOR UPDATE → 원 폴더 deleted 검증 → UNIQUE 검사 → restore + audit
  Errors:   409 RESTORE_CONFLICT

POST /api/files/:id/versions   (이미 업로드된 임시 객체에서 새 버전 등록)
  Request:  { uploadId: string, expectedCurrentVersionId: string, conflictResolution?: 'overwrite' | 'keep-both' }
  Response: 201 { file, version }
  TX:       SELECT FOR UPDATE files → expectedCurrentVersionId 일치 검증
            → INSERT file_versions → UPDATE files.current_version_id → audit_log (FILE_VERSION_CREATE) → COMMIT
  Errors:   409 VERSION_CONFLICT { currentVersion }, 413 QUOTA_EXCEEDED

GET /api/files/:id/versions
  Guard:    @PreAuthorize hasPermission(#fileId, 'file', 'READ') — ADMIN/AUDITOR/READ-grant 통과
  Response: 200 {
    "versions": [
      {
        "id": "uuid",
        "versionNumber": 3,                      // camelCase (FileDto/FolderDto 일관)
        "sizeBytes": 12345,
        "checksumSha256": "...",
        "mimeType": "...",
        "scanStatus": "clean",                   // pending | clean | infected | error  (V5 CHECK 정합 — lowercase wire)
        "uploadedBy": "uuid",
        "uploadedAt": "iso8601",
        "comment": "...",
        "isCurrent": true                        // file.currentVersionId === v.id
      }
    ]
  }
  Order:    versionNumber DESC (최신 버전이 배열 첫 항목)
  SoftDel:  파일이 soft-deleted(`files.deleted_at IS NOT NULL`)면 404 NOT_FOUND
            (휴지통에서 versions 노출 차단 — A5 plan 리스크 결정)
  Errors:   404 NOT_FOUND, 403 PERMISSION_DENIED (envelope code = §8 표준)
```

> **A5 closure 정합 (2026-04-29)**: JSON 응답 키는 camelCase로 wire — 프로젝트 전체 DTO 계약(`FileDto`/`FolderDto`/`PermissionDto`)과 일관. envelope `code`는 §8 정의(`NOT_FOUND` / `PERMISSION_DENIED`)를 그대로 채택 (이전 버전 본문의 `RESOURCE_NOT_FOUND` 표기 정정). 구현체: `FileVersionController` + `FileVersionDto`.

#### 7.6.1 업로드 (multipart, ADR #36 / A15)

```text
POST /api/files   (Content-Type: multipart/form-data)
  Form parts:
    file       : binary (required)
    folderId   : UUID   (required)
    resolution : 'new_version' | 'rename'  (optional — 미지정 시 충돌이면 409)
  Guard:    @PreAuthorize hasPermission(#req.folderId, 'folder', 'UPLOAD')
  Limits:   spring.servlet.multipart.{max-file-size, max-request-size} = 100MB
  Norm:     normalized_name = NormalizeUtil.normalize(file.originalFilename)
  TX (반드시 트랜잭션 + FOR UPDATE):
    1) SELECT FOR UPDATE folders WHERE id=:folderId AND deleted_at IS NULL
    2) UNIQUE (folder_id, normalized_name) WHERE deleted_at IS NULL 검증
       └ 충돌 시:
            resolution=new_version → 기존 file row에 file_versions append + UPDATE files.current_version_id
            resolution=rename      → 자동 suffix `(N)` 부여 → 신규 file row INSERT
            unset                  → 409 RENAME_CONFLICT
    3) StorageClient.write(storageKey, in, size)   — storageKey UUID, 객체 키 `{YYYY}/{MM}/{UUID}` (ADR #5)
    4) INSERT files (신규일 때) + INSERT file_versions
    5) UPDATE files.current_version_id
    6) emitAudit(FILE_UPLOADED)
    7) COMMIT — 실패 시 storage 객체 orphan (MVP 알려진 한계, cleanup job 별도 트랙)
  Response:
    201 Created   (신규 파일 INSERT)
    200 OK        (NEW_VERSION 분기)
    Body: UploadResponse { file: FileDto, versionId, versionNumber, newFile, resolution }
  Errors:
    409 envelope { error: { code: 'RENAME_CONFLICT', message, details: { fileId, fileName } } }
    413 QUOTA_EXCEEDED, 415 UNSUPPORTED_MEDIA_TYPE, 403 PERMISSION_DENIED

GET /api/files/:id/download
  Guard:    @PreAuthorize hasPermission(#id, 'file', 'READ')   — ADR #36: DOWNLOAD enum 도입 안 함
  Response: 200 OK
    Headers:
      Content-Type:        version.mimeType (null/invalid → application/octet-stream)
      Content-Length:      version.sizeBytes
      ETag:                "<versionId>"
      Content-Disposition: attachment; filename="<ascii-fallback>"; filename*=UTF-8''<percent-encoded>
    Body: stream from StorageClient.read(version.storageKey)
  Audit: emitAudit(FILE_DOWNLOADED)
  Errors: 404 (deleted or missing), 403 PERMISSION_DENIED
```

### 7.7 업로드 (tus, ADR #13 — Superseded by ADR #36)

> **Status**: ADR #13 Superseded by ADR #36 (2026-05-02). A15 트랙(`a15-file-upload-download`)에서 단일-POST multipart MVP(§7.6.1)로 재정정. tus 프로토콜은 v1.x 재이월 — 본 §7.7은 v1.x 재개 시 백업 spec으로 보존. MVP에서는 구현 0.

`tus-java-server` 표준 프로토콜. 경로 prefix `/api/files/upload`. S3 multipart는 라이브러리가 내부 위임.

| Method | Path | Guard | TX | Norm | SoftDel | Errors |
|---|---|---|---|---|---|---|
| POST | `/api/files/upload` | `hasPermission(#header.UploadFolderId, 'folder', 'UPLOAD')` | (init은 메타만, COMMIT 분리) | `Upload-Metadata.filename → normalized_name` (중복 사전 체크) | — | 403, 409 UPLOAD_CONFLICT (사전 체크), 413 QUOTA_EXCEEDED, 415 UNSUPPORTED_MEDIA_TYPE |
| HEAD | `/api/files/upload/:uploadId` | 업로드 소유자 본인 | — | — | — | 404, 410 (만료) |
| PATCH | `/api/files/upload/:uploadId` | 업로드 소유자 본인 | (chunk append, S3 multipart part 위임) | — | — | 409 (offset 불일치), 413 |
| DELETE | `/api/files/upload/:uploadId` | 업로드 소유자 본인 | REQUIRED | — | — | 404 |
| POST | `/api/files/upload/:uploadId/finalize` | `hasPermission(#folderId, 'folder', 'UPLOAD')` | **REQUIRED + FOR UPDATE on folder + sibling** | `name → normalized_name` (최종 충돌 검사) | — | 409 UPLOAD_CONFLICT, 413, 415 |

```text
POST /api/files/upload  (tus Creation)
  Headers:  Tus-Resumable: 1.0.0
            Upload-Length: <bytes>
            Upload-Metadata: filename <base64>, folderId <base64>, contentType <base64>
  Response: 201 Location: /api/files/upload/:uploadId
            Tus-Resumable: 1.0.0
  사전 체크: UPLOAD_CONFLICT, QUOTA_EXCEEDED, UNSUPPORTED_MEDIA_TYPE → 즉시 실패

PATCH /api/files/upload/:uploadId  (tus chunk)
  Headers:  Upload-Offset, Content-Type: application/offset+octet-stream
  Body:     <chunk bytes>
  Response: 204 + Upload-Offset
  내부:     S3 multipart part 업로드 위임

POST /api/files/upload/:uploadId/finalize
  Request:  { conflictResolution?: 'overwrite' | 'keep-both' }
  Response: 201 { file: FileDto, version: FileVersionDto }
  TX (반드시 트랜잭션 + FOR UPDATE):
    1) SELECT FOR UPDATE folders WHERE id=:folderId AND deleted_at IS NULL
    2) UNIQUE (folder_id, normalized_name) WHERE deleted_at IS NULL 재검증
       └ 충돌 시 conflictResolution 분기 (overwrite=새 버전, keep-both=이름 변경, 미지정=409)
    3) S3 multipart complete (라이브러리 위임)
    4) INSERT files + INSERT file_versions
    5) UPDATE quota
    6) INSERT audit_log (FILE_UPLOAD)
    7) COMMIT — 실패 시 S3 객체 cleanup job 큐잉
  ADR #16 정규화: NormalizeUtil.normalize(filename) 결과로 normalized_name 저장

DELETE /api/files/upload/:uploadId  (사용자 취소 또는 만료)
  Response: 204
  내부:     S3 multipart abort
```

### 7.8 검색 (Search)

| Method | Path | Guard | TX | Norm | SoftDel | Errors |
|---|---|---|---|---|---|---|
| GET | `/api/search` | isAuthenticated (결과는 권한 필터링) | — | `q → normalizeForSearch(q)` | `WHERE deleted_at IS NULL` | 400 `INVALID_SEARCH_QUERY` (minLength 미달 / type invalid / cursor invalid) |

```text
GET /api/search?q=&type=file|folder|all&cursor=&limit=
  - q (required, string): 검색어. 서버 정규화 q' = normalizeForSearch(q) (NFC + lowercase + 공백 collapse + trim)
                          minLength: 2 (정규화 후 기준 — 1자/공백만 → 400 INVALID_SEARCH_QUERY)
  - type (optional, default=all): "file" | "folder" | "all". invalid → 400.
  - cursor (optional): 직전 응답의 nextCursor를 echo back. 첫 페이지에서는 미지정.
                       opaque base64 url-safe `{updatedAtEpochMs}|{type}|{id}` (ADR #33).
  - limit (optional, default=50, cap=100)

  서버 처리:
  1. q normalize → minLen 2 검증
  2. type 분기 → file 검색(LIKE) + folder 검색(LIKE) [+ cursor tuple 조건]
  3. 각 테이블당 LIMIT (limit+1) — hasMore 판정 + nextCursor 발급
  4. type=all이면 in-memory merge sort `(updatedAt DESC, type DESC, id DESC)`
  5. 권한 후처리 — actor의 effective READ (ADR #33: ROLE short-circuit + resource grant fallback)
  6. SearchPage 빌드

  out-of-scope (ADR #33로 박제):
  - tsvector full-text / pg_trgm fuzzy match (별도 마이그레이션 트랙)
  - owner / modifiedFrom / modifiedTo 필터 (frontend 미사용 — 추가 시 controller param + service overload hook)
  - 검색 audit emission (SEARCH_QUERIED enum 정의 0, 보안 트랙)

  Response: 200 application/json
    {
      items: SearchResultDto[],
      nextCursor?: string,                  // 다음 페이지 존재 시
      totalEstimate: long                   // 첫 페이지 only — cursor 페이지에서는 -1
    }

  SearchResultDto (discriminated union by `type`):
    {
      type: "file" | "folder",
      id: UUID,
      name: string,                         // displayName (정규화 전 원본)
      parentId?: UUID | null,               // type="folder"일 때 (root면 null)
      folderId?: UUID,                      // type="file"일 때
      sizeBytes?: long,                     // type="file"일 때
      mimeType?: string,                    // type="file"일 때
      updatedAt: ISO8601                    // 정렬/cursor key
    }
```

### 7.9 공유 (Shares, ADR #34, A16 ADR #37 subjectName)

> A10 트랙 `a10-shares`(file POST + by-me/with-me + DELETE), A12 트랙 `a12-folder-shares-endpoint`(folder POST), A13 트랙 `a13-shares-permissions-join`(2026-05-01) — `ShareDto` ↔ `permissions` row join 복원: 응답에 `subjectType`/`subjectId`/`preset` 3 필드를 포함. POST 응답은 트랜잭션 내 `PermissionRow` 그대로 사용, by-me/with-me 응답은 페이지 결정 후 `permissionRepository.findAllById(ids)` 1회 IN 절 batch (N+1 회피, MAX_LIMIT=100 → 페이지 당 최대 100 IN). V6 FK CASCADE가 active share row의 permission 존재를 보증 — 누락 시 `IllegalStateException`. `shares` 테이블은 V6 마이그레이션에서 도입(§2.7). `SHARE_CREATED`/`SHARE_REVOKED` audit 활성화. `SHARE_EXPIRED` cron 트랙 `share-expired-cron`(2026-05-01)에서 활성화 — §7.9.1. SSE emission은 별도 트랙(deferred). **A16 트랙 `a16-department-subject-picker`(2026-05-01~02, ADR #37)** — `ShareDto`에 nullable `subjectName: String|null` 필드 추가. user → `users.display_name`, department → `departments.name`, everyone/lookup miss → null fallback. POST 응답은 트랜잭션 내 단건 helper(`resolveSubjectName`, type별 N=subjects.size lookup), by-me/with-me는 페이지 결정 후 batch helper(`fetchSubjectNames`, 페이지 당 type별 1회 IN 절). 단건 lookup endpoint 미추가.

| Method | Path | Guard | TX | Norm | SoftDel | Errors |
|---|---|---|---|---|---|---|
| GET | `/api/shares/by-me` | `isAuthenticated()` | — | — | `WHERE revoked_at IS NULL` | — |
| GET | `/api/shares/with-me` | `isAuthenticated()` | — | — | `WHERE revoked_at IS NULL AND subject_type='user' AND subject_id=actor` (MVP) | — |
| POST | `/api/files/:id/share` | `hasPermission(#fileId, 'file', 'SHARE')` | REQUIRED | — | `files.deleted_at IS NULL` | 400, 404, 409 |
| POST | `/api/folders/:id/share` | `hasPermission(#folderId, 'folder', 'SHARE')` | REQUIRED | — | `folders.deleted_at IS NULL` | 400, 404, 409 |
| DELETE | `/api/shares/:shareId` | `@shareCommandService.canRevoke(#shareId, principal)` | REQUIRED | — | `revoked_at IS NULL` (이미 revoked → 404) | 403, 404 |

```text
POST /api/files/:fileId/share                              (ADR #34)
  Guard:    hasPermission(#fileId, 'file', 'SHARE')
  Request:  { subjects: [{ type: 'user'|'department'|'role'|'everyone', id?: UUID }],
              preset:    'read'|'upload'|'edit'|'admin',
              expiresAt? : ISO8601 (미래),
              message?   : string (max 1000) }
              # subjects[].id 는 type='everyone' 일 때만 NULL (V5 idx_permissions_unique CHECK 동형)
              # preset wire format = V5 permissions_preset_check 4 값 (read|upload|edit|admin).
              #   Preset.SHARE 는 enum 정의 존재하나 V5 CHECK 미지원 → controller 진입 시 거부 (400 BAD_REQUEST). ADR #34 backlog.
  Response: 201 { shares: ShareDto[] }
              # ShareDto = { id, fileId, folderId(=null on file POST), permissionId, sharedBy,
              #              message?, expiresAt?, createdAt, revokedAt(=null), revokedBy(=null),
              #              subjectType, subjectId, preset, subjectName }
              # subjectType/subjectId/preset 는 A13에서 permissions row join으로 surface.
              # subjectName 은 A16(ADR #37)에서 추가 — user→users.display_name, department→departments.name,
              #              everyone/lookup miss → null. role 은 v1.x backlog 까지 null.
              # POST 응답은 INSERT 직후 PermissionRow 그대로 매핑 (별도 SELECT 없음). subjectName 은 트랜잭션 내 단건 lookup.
  TX:       for each subject:
              (1) INSERT permissions(preset) → PermissionGrantedEvent → audit `permission.granted`
              (2) INSERT shares(permission_id 참조) → ShareCreatedEvent → audit `share.created`
            전체 단일 트랜잭션. UNIQUE 위반 시 전체 rollback.
  Errors:   400 BAD_REQUEST       (subjects 비어있음, message > 1000자, expiresAt past, subject_id ↔ everyone 위반)
            404 NOT_FOUND         (file 미존재 또는 soft-deleted)
            409 PERMISSION_CONFLICT (V5 idx_permissions_unique 위반 — 동일 file × subject 중복 grant)

POST /api/folders/:folderId/share                          (ADR #34, A12)
  Guard:    hasPermission(#folderId, 'folder', 'SHARE')
  Request:  { subjects: [...], preset, expiresAt?, message? }   # file 변형과 동일 wire format
  Response: 201 { shares: ShareDto[] }
              # ShareDto.folderId NOT NULL, fileId NULL (V6 shares XOR CHECK 와 1:1)
  TX:       file 변형과 동형. for each subject:
              (1) INSERT permissions(node_type='folder') → PermissionGrantedEvent → audit `permission.granted`
              (2) INSERT shares(folder_id 채움, file_id NULL) → ShareCreatedEvent → audit `share.created`
            전체 단일 트랜잭션. UNIQUE 위반 시 전체 rollback.
  Errors:   400 BAD_REQUEST       (file 변형과 동일 검증 — subjects 비어있음, message > 1000자, expiresAt past, preset='share')
            404 NOT_FOUND         (folder 미존재 또는 soft-deleted)
            409 PERMISSION_CONFLICT (V5 idx_permissions_unique 위반 — 동일 folder × subject 중복 grant)
  Note:     by-me / with-me / DELETE endpoint 는 file/folder share 모두 자연 노출 — repository SQL 분기 없음.

DELETE /api/shares/:shareId                                (ADR #34)
  Guard:    @shareCommandService.canRevoke(#shareId, principal)
              # share.shared_by == principal.userId  ||  principal.role == ADMIN
  Response: 204 No Content
  TX:       SELECT share FOR UPDATE
            → share.revoked_at = NOW(), share.revoked_by = actor → save
            → permissionRepository.deleteById(share.permission_id)   -- 공유받은 사람의 접근 즉시 회수
            → ShareRevokedEvent → audit `share.revoked`
              metadata:     { shareId, permissionId, originalSharedBy, fileId|folderId }   # XOR per row, A12
              before_state: snapshot(share)
            -- audit `permission.revoked` 는 emit 하지 않음 (이중 발행 회피, ADR #34)
  Errors:   403 PERMISSION_DENIED, 404 NOT_FOUND (이미 revoked 포함)

GET /api/shares/by-me?cursor=&limit=                       (ADR #34, A13 join)
  Guard:    isAuthenticated()
  Response: 200 { items: ShareDto[], nextCursor?: string }
              # ShareDto는 POST 응답과 동일 형상 — subjectType/subjectId/preset 포함.
  Query:    (1) shares 페이지 SELECT — WHERE shared_by = :actor AND revoked_at IS NULL
                ORDER BY created_at DESC, id DESC LIMIT :limit + 1
            (2) A13 — 페이지 결정 후 permission_id 집합으로 SELECT * FROM permissions WHERE id IN (...).
                share 1쿼리 + permissions 1쿼리 — N+1 회피.
  Cursor:   opaque base64 url-safe `{createdAt epoch ms}|{id}` (ShareCursor)

GET /api/shares/with-me?cursor=&limit=                     (ADR #34, MVP scope, A13 join)
  Guard:    isAuthenticated()
  Response: 200 { items: ShareDto[], nextCursor?: string }
              # ShareDto는 POST 응답과 동일 형상 — subjectType/subjectId/preset 포함.
  Query:    (1) shares 페이지 SELECT — INNER JOIN permissions p ON shares.permission_id = p.id
                WHERE shares.revoked_at IS NULL
                  AND p.subject_type = 'user'
                  AND p.subject_id   = :actor
                  AND (p.expires_at IS NULL OR p.expires_at > NOW())
                ORDER BY shares.created_at DESC, shares.id DESC LIMIT :limit + 1
            (2) A13 — 페이지 결정 후 permission_id 집합으로 SELECT * FROM permissions WHERE id IN (...).
                with-me는 (1) JOIN으로도 grant를 집어올 수 있지만 by-me와 동형 path로 통일 (DTO mapping 동일).
  Note:     MVP는 subject_type='user' 매칭만. department/role/everyone subject 로 받은 share 는
            with-me 결과 미포함 (별도 트랙, ADR #34 backlog).
```

#### 7.9.1 만료 cron (`share-expired-cron`, ADR #34 backlog closure)

> `shares.expires_at <= NOW() AND revoked_at IS NULL` row를 자동 만료. 운영 기본 비활성, staging/prod에서 명시적 활성화. **`permissions.expires_at`(직접 grant)** 만료는 본 트랙 scope 외(별도 트랙).

| 항목 | 정책 |
|---|---|
| 빈 등록 | `app.share.expiration.enabled=true`일 때만 (`@ConditionalOnProperty`, default `false`) |
| 스케줄 | `@Scheduled(cron, zone)` — default `0 */5 * * * *` Asia/Seoul (`app.share.expiration.cron`/`zone`) |
| 한 회 한도 | `app.share.expiration.batch-size` (default `200`). repository `findExpiredActiveIds(now, PageRequest.of(0, batchSize))` |
| 처리 단위 | 후보 id 1건당 별도 트랜잭션 — `ShareCommandService.expireShare(shareId)` |
| 만료 동작 | revoke와 동형 트랜잭션: `SELECT share FOR UPDATE WHERE revoked_at IS NULL` → `revoked_at=NOW()` + `revoked_by=NULL` (시스템) → `permissions.deleteById(share.permission_id)` |
| 이벤트 | `ShareExpiredEvent`(record, `actorId` 부재) — `ShareAuditListener.onShareExpired`가 `AuditEventType.SHARE_EXPIRED`로 매핑 |
| audit row | `actor_id=NULL`, IP/UA 부재, `metadata.trigger='system.expiration'`, `before_state=share snapshot` (revoke와 동일 — CASCADE로 row 사라짐). §03 §4.1 참조 |
| 다중 인스턴스 | V6 row-level pessimistic lock이 동시 `expireShare(sameId)` 호출을 직렬화. 두 번째 호출은 `ResourceNotFoundException`으로 swallow — 분산락 별도 도입 불요 |
| 실패 격리 | per-row `RuntimeException`은 ERROR 로그 + 다음 row 진행 (배치 전체 차단 없음). scan 단계 실패는 다음 cron으로 재시도 |
| 로그 | run summary INFO `total=N ok=O failed=F`, `failed > 0`이면 WARN |

### 7.10 권한 (Permissions)

| Method | Path | Guard | TX | Norm | SoftDel | Errors |
|---|---|---|---|---|---|---|
| GET | `/api/:resource/:id/permissions` | `hasPermission(#id, #resource, 'PERMISSION_ADMIN')` | — | — | `WHERE deleted_at IS NULL` | 403, 404 |
| POST | `/api/:resource/:id/permissions` | `hasPermission(#id, #resource, 'PERMISSION_ADMIN')` (구표기 `'ADMIN'` alias) | REQUIRED | — | — | 400, 403, 404, 409 |
| DELETE | `/api/permissions/:permissionId` | `PermissionService.canRevokePermission(#permissionId, currentUser)` | REQUIRED | — | — | 403, 404 |
| GET | `/api/me/effective-permissions?nodeId=` | isAuthenticated | — | — | `WHERE deleted_at IS NULL` | 400, 401, 404 |

```text
GET /api/:resource/:id/permissions                  (M8.0)
  Guard:    hasPermission(#id, #resource, 'PERMISSION_ADMIN')   # POST/DELETE 와 대칭 — grant 분포는 권한 관리 정보
  Response: 200 { items: PermissionDto[] }
              # 정렬: created_at ASC, id ASC (audit-style oldest-first)
              # 페이지네이션 미적용 — MVP 단일 리소스의 grant 수가 작다는 가정
              # PermissionDto.subjectName: A16/ADR #36 ShareDto 미러 — user→users.display_name,
              #   department→departments.name, everyone/lookup miss(soft-delete 등) → null
              # 만료(`expires_at <= NOW()`) row 도 포함 — admin UI 가 cron 정리 전 만료-pending 가시화
  Errors:   403 PERMISSION_DENIED (PERMISSION_ADMIN 미보유)
            404 NOT_FOUND (resource 미존재 또는 deleted_at NOT NULL)
  Note:     resource ∈ { 'folder', 'file' }. POST/DELETE 와 동일 path normalization (단수/복수 모두 허용).
            POST 응답의 PermissionDto 는 subjectName=null — 호출자가 필요 시 list 재조회로 표시명 획득.

POST /api/:resource/:id/permissions
  Request:  { subject: { type: 'user'|'department'|'role'|'everyone', id?: UUID }, preset: 'read'|'upload'|'edit'|'admin', expiresAt? }
              # subject.id 는 type='everyone' 일 때만 NULL (V5 CHECK 와 일치)
              # preset 은 ADR #28 — 4값 (deny/share 제외, share 는 별도 shares 테이블)
  Response: 201 { permission: PermissionDto }   # PermissionDto.subjectName=null (M8.0)
  TX:       INSERT permissions + emit PermissionGrantedEvent
              → PermissionAuditListener (REQUIRES_NEW) → audit_log (permission.granted, target_type=permission)
  Errors:   400 BAD_REQUEST (subject_id ↔ everyone 위반, preset 미지원, expiresAt past)
            403 PERMISSION_DENIED (PERMISSION_ADMIN 미보유)
            404 NOT_FOUND (resource 미존재)
            409 PERMISSION_CONFLICT (V5 idx_permissions_unique 위반 — 동일 resource×subject 중복)
  Note:     resource ∈ { 'folder', 'file' }

DELETE /api/permissions/:permissionId
  Response: 204 No Content
  TX:       SELECT row → DELETE → emit PermissionRevokedEvent
              → PermissionAuditListener (REQUIRES_NEW) → audit_log (permission.revoked, before_state=snapshot)
  Errors:   403 PERMISSION_DENIED, 404 NOT_FOUND

GET /api/me/effective-permissions[?nodeId={uuid}]
  Response: 200 { permissions: Permission[] }   # Permission enum (UPPER_SNAKE_CASE), 정의 순 정렬
  Eval:     nodeId 미지정 → ROLE 기반 권한만 (effectivePermissions(role))
            nodeId 지정 → ROLE 권한 ∪ resource-level grant (folder|file 자동 식별)
              ADMIN role은 9 권한 즉시 grant (PermissionResolver 미호출 early return)
              PURGE는 ROLE ADMIN만 보유 — Preset 미포함이라 resource grant로 부여 불가 (docs/03 line 331~334)
  Errors:   400 BAD_REQUEST (nodeId UUID 형식 위반)
            401 UNAUTHORIZED (미인증)
            404 NOT_FOUND (nodeId 지정 + folder/file 모두 미존재 또는 deleted_at NOT NULL)
  Note:     read-only — TX 없음, audit 미기록.
            frontend `api.getEffectivePermissions(nodeId?)`의 Promise<Permission[]> 시그니처와 1:1 매핑.
            staleTime 60s (usePermission) — 빈도 낮음, 9× CTE 비용 MVP 허용.
```

#### 7.10.1 만료 cron (`permissions-expired-cron`, ADR #34 backlog closure)

> `permissions.expires_at <= NOW()` row를 자동 cleanup. 운영 기본 비활성, staging/prod에서 명시적 활성화. `findEffective`가 이미 `expires_at > NOW()` 필터링하므로 보안 측면에서는 cron이 없어도 만료 grant는 평가에서 제외 — cron의 가치는 (a) DB cleanup, (b) `permission.expired` audit row.

| 항목 | 정책 |
|---|---|
| 빈 등록 | `app.permission.expiration.enabled=true`일 때만 (`@ConditionalOnProperty`, default `false`) |
| 스케줄 | `@Scheduled(cron, zone)` — default `0 */5 * * * *` Asia/Seoul (`app.permission.expiration.cron`/`zone`) |
| 한 회 한도 | `app.permission.expiration.batch-size` (default `200`). repository `findExpiredActiveIds(now, PageRequest.of(0, batchSize))` |
| 처리 단위 | 후보 id 1건당 별도 트랜잭션 — `PermissionService.expirePermission(permissionId)` |
| 만료 동작 | revoke와 동형: `lockById(permissionId)` (PESSIMISTIC_WRITE) → snapshot → `permissions.delete(row)` (DELETE — `permissions`에는 `revoked_at` 컬럼 부재, soft-delete 불가) |
| 이벤트 | `PermissionExpiredEvent`(record, `actorId` 부재) — `PermissionAuditListener.onPermissionExpired`가 `AuditEventType.PERMISSION_EXPIRED`로 매핑 |
| audit row | `actor_id=NULL`, IP/UA 부재, `metadata.trigger='system.expiration'`, `before_state=permission snapshot` (revoke와 동일 — DELETE로 row 사라짐). §03 §4.1 참조 |
| 다중 인스턴스 | row-level pessimistic lock이 동시 `expirePermission(sameId)` 호출을 직렬화. 두 번째 호출(또는 사용자 직접 revoke와 race)은 `ResourceNotFoundException`으로 swallow — 분산락 별도 도입 불요 |
| 실패 격리 | per-row `RuntimeException`은 ERROR 로그 + 다음 row 진행 (배치 전체 차단 없음). scan 단계 실패는 다음 cron으로 재시도 |
| 로그 | run summary INFO `total=N ok=O failed=F`, `failed > 0`이면 WARN |

### 7.11 휴지통 (Trash, A6/A8)

> Restore endpoint은 A6에서 per-resource로 구현(`/api/files/:id/restore`, `/api/folders/:id/restore`) — 본 표는 그 위치 기준. Manual purge는 A8(`DELETE /api/trash/:type/:id`, ADR #32). Bulk purge `DELETE /api/trash`는 미구현(별도 트랙). 관리자 전역 휴지통 listing은 Wave 2 T9에서 별도 endpoint(`GET /api/admin/trash`)로 추가 — admin DTO(owner/originalParent/size 노출), mutation은 기존 endpoint 재사용(ADMIN ROLE이 SpEL 가드 통과).

| Method | Path | Guard | TX | Norm | SoftDel | Errors |
|---|---|---|---|---|---|---|
| GET | `/api/trash` | `isAuthenticated()` (결과는 사용자가 DELETE 권한 가진 항목만 — A8 결과 후처리, ADR #32) | — | — | **`WHERE deleted_at IS NOT NULL`** | — |
| GET | `/api/admin/trash` (Wave 2 T9) | `hasRole('ADMIN')` (`@PreAuthorize`) | — | q→LIKE escape (case-insensitive) | **`WHERE deleted_at IS NOT NULL`** | 400 VALIDATION_ERROR(invalid type/ownerId/q>200/deletedFrom·deletedTo 형식 또는 from≥to), 401, 403 |
| POST | `/api/admin/trash/bulk` (Wave 2 T9 follow-up) | `hasRole('ADMIN')` | — (per-item 단건 service 트랜잭션, fan-out) | — | per-item: 단건 endpoint와 동일 (restore: SET NULL+UNIQUE 재검사 / purge: row+versions cascade) | 200(부분 실패 허용 — `failed[]`로 표현), 400 VALIDATION_ERROR(invalid action / items.size ∉ [1,200]), 401, 403 |
| GET | `/api/admin/trash/policy` (Wave 2 T9 follow-up, wave2-trash-policy-viewer) | `hasRole('ADMIN')` | — | — | — (read-only) | 401, 403 |
| POST | `/api/files/:id/restore` (A6) | `hasPermission(#id, 'file', 'DELETE')` | REQUIRED + FOR UPDATE | — | `SET deleted_at = NULL` + UNIQUE 재검사 | 404, 409 RESTORE_CONFLICT |
| POST | `/api/folders/:id/restore` (A6) | `hasPermission(#id, 'folder', 'DELETE')` | REQUIRED + FOR UPDATE | — | `SET deleted_at = NULL` + UNIQUE 재검사 + descendant cascade | 404, 409 RESTORE_CONFLICT |
| DELETE | `/api/trash/:type/:id` (A8, ADR #32) | `hasRole('ADMIN')` | REQUIRED | — | (purge: row + file_versions cascade. S3 객체는 ADR #31 deferred) | 400 VALIDATION_ERROR(invalid type), 403, 404 |
| ~~DELETE~~ | ~~`/api/trash`~~ (bulk) | ~~`hasRole('ADMIN')`~~ | — | — | **(미구현, 별도 트랙 — `purge.expired` 배치(A7) + manual single(A8) 조합으로 운영 충분, ADR #32)** | — |

```text
GET /api/trash?cursor=&type=                    (A8.1, ADR #32)
  Guard:    isAuthenticated()
  Response: 200 { items: TrashItemDto[], nextCursor? }
  Note:     items 필드 = (id, name, type='file'|'folder', deletedAt, purgeAfter, originalPath)
  Filter:   actor의 per-row hasPermission(id, type, 'DELETE') 후처리
            (MVP 가정: 페이지 한도 < 권한 보유 row 수, 큰 dataset 시 별도 ADR)
  type 파라미터: 'file' | 'folder' | (생략 = 양쪽)

GET /api/admin/trash?q=&type=&ownerId=&deletedFrom=&deletedTo=&cursor=&limit=
                                              (Wave 2 T9, 2026-05-07; 날짜 범위 필터: T9 follow-up)
  Headers:  Cookie: SESSION=<id>             (ADMIN 인증 필요)
  Guard:    @PreAuthorize("hasRole('ADMIN')")  -- per-row DELETE 후처리 없음(ADMIN은 글로벌 viewer)
  Query:    q           (선택; max 200 chars. files.name + folders.name LIKE wildcard escape 후
                         `LOWER(...) LIKE LOWER(?) ESCAPE '\\'` — admin-user-search-update와 동일 정책)
            type        (선택; 'file' | 'folder' | (생략 = 양쪽). 그 외 → 400 VALIDATION_ERROR)
            ownerId     (선택; UUID. 형식 오류 → 400 VALIDATION_ERROR)
            deletedFrom (선택; `YYYY-MM-DD` date-only — KST(`Asia/Seoul`) 00:00 inclusive 하한.
                         형식 오류 → 400 VALIDATION_ERROR. 운영자 입력 wall-clock과 일치)
            deletedTo   (선택; `YYYY-MM-DD` date-only — backend가 입력일+1의 KST 00:00로
                         변환(exclusive 상한, 즉 입력일 KST 종일 포함). 형식 오류 → 400.
                         양쪽 모두 적용 시 deletedFrom < deletedTo여야 함 — 위반 시 400)
            cursor      (선택; opaque base64 — user-facing `GET /api/trash`와 동일 `TrashCursor` 포맷
                         재사용 — Wave 2 T9에서 visibility를 public으로 승격)
            limit       (선택; default 50, max 100 — 초과 시 clamp)
  Response: 200 {
              items: AdminTrashItemDto[],
              nextCursor: string | null
            }
            AdminTrashItemDto = {
              id:                 string (UUID),
              name:               string,                 // files.name 또는 folders.name (정규화 전 원본)
              type:               'file' | 'folder',
              deletedAt:          string (ISO-8601),
              purgeAfter:         string (ISO-8601),       // deletedAt + 보존 기간
              ownerId:            string (UUID),           // files.uploaded_by 또는 folders.created_by
              ownerEmail:         string,                  // users.email (batch lookup)
              originalParentId:   string (UUID) | null,    // null = root
              originalParentName: string | null,           // 부모 폴더의 단일 segment name (path 계산 실패 시 fallback), 부모 row 미존재 시 null
              originalParentPath: string | null,           // full-path-resolve follow-up. 부모 폴더의 절대 경로(leading '/', trailing slash 없음 — 예: '/회사/팀A/문서'). 부모가 root면 '/<parentName>'. originalParentId == null이면 null. 데이터 corruption / depth 100 초과 등으로 chain 종착 실패 시 null (UI는 originalParentName fallback).
              sizeBytes:          number                   // file: 자기 size_bytes. folder: subtree size — 자기 자신 + 모든 하위 폴더의 file size 합 (Wave 2 T9 follow-up folder-subtree-size, 빈 폴더는 0). always not-null.
            }
  Side-effects: 없음 (read-only — audit emit 0건. mutation은 기존 endpoint 재사용)
  Errors:
    400 VALIDATION_ERROR  { details: { field: 'type'|'ownerId'|'q'|'deletedFrom'|'deletedTo', rule: '...' } }
    401 UNAUTHORIZED      (미인증)
    403                   (MEMBER/AUDITOR — `@PreAuthorize` 차단)
  Note:     - cursor는 `TrashCursor` 재사용으로 user-facing `/api/trash`와 wire-호환.
            - admin DTO는 user-facing `TrashItemDto`와 분리 — owner / originalParent / size 추가 노출.
            - mutation 0 신규: 단건 복원/영구삭제는 기존 `POST /api/files|folders/{id}/restore` +
              `DELETE /api/trash/{type}/{id}` 재사용 (ADMIN ROLE이 SpEL `hasPermission(...DELETE)` /
              `hasRole('ADMIN')` 가드 통과). audit emit은 그쪽 endpoint에서 발행
              (FILE_RESTORED / FOLDER_RESTORED / FILE_PURGED / FOLDER_PURGED).
            - 날짜 범위 필터(deletedFrom/deletedTo): T9 follow-up으로 추가 (date-only 와이어,
              KST(`Asia/Seoul`) 경계 — 사내 단일 지역 운영, 운영자 wall-clock 일치). bulk
              restore·purge: T9 follow-up으로 추가 (`POST /api/admin/trash/bulk`, 하단 #7.11.0
              참조). folder subtree size: T9 follow-up으로 closure (`AdminTrashItemDto.sizeBytes`가
              folder에서도 not-null — 단일 재귀 CTE batch lookup, 빈 폴더는 0). full path resolve:
              T9 follow-up으로 closure (`originalParentPath` — 단일 재귀 CTE batch lookup,
              살아있는/삭제된 부모 모두 chain 추적, depth 100 cap). 2인 승인: v1.x deferred.
              `deletedBy` 컬럼은 V10(2026-05-08)으로 closure (cross-owner 추적은
              `deletedById`/`deletedByEmail`로 노출).

POST /api/admin/trash/bulk                       (Wave 2 T9 follow-up, 2026-05-08)
  Headers:  Cookie: SESSION=<id>             (ADMIN 인증 필요)
  Guard:    @PreAuthorize("hasRole('ADMIN')")
  Body:     {
              action: 'restore' | 'purge',     // 그 외 → 400 VALIDATION_ERROR
              items: Array<{                    // 1..200, 0 또는 201+ → 400
                type: 'file' | 'folder',
                id:   string (UUID)
              }>
            }
  Response: 200 {                                // 항상 200 — 부분 실패 허용
              succeeded: Array<{ type, id }>,
              failed:    Array<{ type, id, error: string }>
            }
            error 문자열: 'NOT_FOUND' | 'NAME_CONFLICT' | 'INVALID_TYPE' | 'INVALID_ITEM'
            (단건 endpoint 도메인 예외 매핑 — docs/02 §8 정합)
  TX:       per-item 단건 service의 자기 트랜잭션 (REQUIRED + FOR UPDATE) 200개 직렬 처리.
            bulk endpoint 자체는 트랜잭션을 열지 않음 — 한 항목 실패가 다른 항목 처리를 막지 않음.
  Audit:    per-item 기존 emit 그대로 (FILE_RESTORED / FOLDER_RESTORED / FILE_PURGED /
            FOLDER_PURGED). bulk 자체 새 enum 0 — 동일 actor + 근접 timestamp로 묶음 식별 가능.
  Errors:
    400 VALIDATION_ERROR  { details: { field: 'action'|'items', rule: '...' } }
    401 UNAUTHORIZED      (미인증)
    403                   (MEMBER/AUDITOR — `@PreAuthorize` 차단)
  Note:     - 부분 실패 모델 — 30일 만료 직전 일괄 정리 / 사용자 대량 오삭제 후 일괄 복원
              시나리오에서 한 항목의 NAME_CONFLICT가 다른 199개를 막지 않도록 설계.
            - 200 cap은 q 길이 cap과 정합. 클라이언트는 페이지당 max 100을 따라가게 두면
              자연스럽게 cap 안.
            - 단건 endpoint 무변경 — bulk는 service layer fan-out만.

POST /api/files/:id/restore                     (A6)
POST /api/folders/:id/restore                   (A6)
  TX:       SELECT FOR UPDATE → 원 부모 폴더 활성 검증 → UNIQUE 충돌 검사
            → SET deleted_at = NULL (folder는 후손 cascade) → audit_log (FILE_RESTORED / FOLDER_RESTORED)
            → COMMIT
  Errors:   409 RESTORE_CONFLICT { suggestedName }

DELETE /api/trash/:type/:id                     (A8.2, ADR #32, 영구 삭제, 관리자)
  Guard:    hasRole('ADMIN')                    -- 노드 admin preset 무시 (ADR #26 §3.2.5)
  Path:     :type ∈ {'file','folder'}, invalid → 400 VALIDATION_ERROR
  Pre:      대상 row의 deleted_at IS NOT NULL    -- active row면 404 (휴지통에 없음)
  TX:       SELECT FOR UPDATE
            → file: file_versions hard delete → file row hard delete
              folder: 후손 폴더/파일 leaf-first cascade (A7 패턴 재사용, limit=10000)
            → audit_log (FILE_PURGED 또는 FOLDER_PURGED, per-call 1건 — A6 root-only 패턴 일관)
              before_state = { name, parentId, storageKeys[] }
              after_state  = { purgedAt, descendantFolders?, descendantFiles? }
            → COMMIT
  Note:     S3 객체 hard delete는 ADR #31 deferred — orphan은 `orphan.detect` 잡 cross-check로 정리.
            SSE FILE_PURGED/FOLDER_PURGED emission은 SSE 인프라 milestone deferred (A8은 audit-only).
            audit_log은 append-only이므로 PURGE 이벤트는 보존 (ADR #25).
  Response: 204 NO_CONTENT
```

#### 7.11.1 Hard purge 배치 잡 (A7, `purge.expired`)

`docs/04 §13` `purge.expired` (매일 00:00 KST) 배치 트랙. **DB-only hard delete** — S3 객체 삭제는 storage 모듈 도입 시점에 별도 잡(`orphan.detect`)에서 처리 (ADR #31).

```text
A7 cron: 0 0 0 * * * (Asia/Seoul)
  Predicate:  WHERE deleted_at IS NOT NULL AND purge_after <= NOW()
  Order:      (1) file_versions (file_id IN expired_files)
              (2) files (id IN expired_files)             -- current_version_id FK는 DEFERRABLE INITIALLY DEFERRED (line 177)
              (3) folders (leaf-first 위상정렬, parent_id ON DELETE RESTRICT 만족)
  Limit:      app.purge.max-per-run (default 10000, files+folders 합산)
              초과 시 truncated=true → 다음 day cron에서 처리
  Audit:      `SYSTEM_PURGE_EXECUTED` 1건만 발행 (summary-only, A6 root-only 패턴 일관)
              actor_id=null, actor_role="SYSTEM"
              after_state = { runId, purgedFiles, purgedFolders,
                              orphanStorageKeys (cap=1000), durationMs, truncated }
  Skip:       Legal Hold — v2.x deferred (ADR #46). 활성화 시 `AND legal_hold IS NOT TRUE`
              1줄 추가 (cache flag 출처 = §2.10 `files.legal_hold`/`folders.legal_hold`)
  Toggle:     app.purge.enabled (default true). false → bean 미등록.
  S3:         orphanStorageKeys는 audit에 기록만. 실 삭제 = ADR #31 (storage 모듈 milestone)
```

**FK 정합 (line 37 backlink)**: `file_versions: 삭제 불가`는 soft-delete/restore 컨텍스트 한정. hard purge 시 file row와 cascade 삭제.

**`FILE_PURGED`/`FOLDER_PURGED` 이벤트는 A8 reserve** — `/api/trash/:id` (manual purge) 트랙에서 per-row 발행.

### 7.12 관리자 (Admin)

| Method | Path | Guard | TX | Norm | SoftDel | Errors |
|---|---|---|---|---|---|---|
| GET | `/api/admin/audit` | `isAuthenticated()` (ADMIN/AUDITOR 전체, MEMBER `actor_id=self` — A2.3 트랙결정 #4) | — | — | (audit_log 자체에 deleted_at 없음) | 401 |
| GET | `/api/admin/audit/export` | `hasRole('AUDITOR') OR hasRole('ADMIN')` (`@PreAuthorize`, Wave 1 T2 + audit-export-json 트랙 `format=csv\|json` 분기) | — (SELECT only — `AUDIT_EXPORTED` emit은 별도 REQUIRES_NEW) | — | — | 400 BAD_REQUEST(unknown format), 401, 403 |
| GET | `/api/admin/download-logs` | `hasRole('AUDITOR') OR hasRole('ADMIN')` | — | — | — | 403 |
| GET | `/api/admin/permission-logs` | `hasRole('AUDITOR') OR hasRole('ADMIN')` | — | — | — | 403 |
| GET | `/api/admin/storage-usage` | `hasRole('ADMIN')` | — | — | — | 403 |
| GET | `/api/admin/users` | `hasRole('ADMIN')` (`@PreAuthorize`) | — | q→lowercase + LIKE escape (admin-user-search-update) | `WHERE deleted_at IS NULL` (활성/비활성 모두 포함) | 401, 403 |
| POST | `/api/admin/users` | `hasRole('ADMIN')` (`@PreAuthorize`) | REQUIRED (user 생성 + 임시 PW BCrypt + AFTER_COMMIT audit·email) | email→lowercase trim | — | 400 VALIDATION_ERROR, 401, 403, 409 CONFLICT/DUPLICATE_EMAIL |
| PATCH | `/api/admin/users/:id` | `hasRole('ADMIN')` (`@PreAuthorize`) | REQUIRED (role/active/displayName 변경 + AFTER_COMMIT audit) | displayName trim (admin-user-search-update) | — | 400 VALIDATION_ERROR, 401, 403 SELF_PROTECTION, 404 USER_NOT_FOUND |
| GET | `/api/admin/departments` | `hasRole('ADMIN')` (`@PreAuthorize`) | — | q→lowercase + LIKE escape | (활성/비활성 모두 포함) | 401, 403 |
| POST | `/api/admin/departments` | `hasRole('ADMIN')` (`@PreAuthorize`) | REQUIRED (생성 + AFTER_COMMIT audit) | name→trim | partial unique `WHERE deleted_at IS NULL` | 400 VALIDATION_ERROR, 401, 403, 409 DEPARTMENT_CONFLICT |
| PATCH | `/api/admin/departments/:id` | `hasRole('ADMIN')` (`@PreAuthorize`) | REQUIRED (rename/(de)activate + AFTER_COMMIT audit) | name→trim | partial unique 보조 | 400 VALIDATION_ERROR, 401, 403, 404 NOT_FOUND, 409 DEPARTMENT_CONFLICT |
| GET | `/api/admin/permissions` | `hasRole('ADMIN')` (`@PreAuthorize`) | — | q→lowercase + LIKE escape | (만료 grant 포함 — `is_expired` 노출) | 400 VALIDATION_ERROR, 401, 403 |
| GET | `/api/admin/system/cron` | `hasRole('ADMIN') OR hasRole('AUDITOR')` (`@PreAuthorize`, Wave 1 T3 / 1.5 `auditor-cron-readonly`) | — (SELECT only — `@ConfigurationProperties` bean 직렬화) | — | — | 401, 403 |
| GET | `/api/admin/dashboard/summary` | `hasRole('ADMIN')` (`@PreAuthorize`) | `readOnly=true` (count·SUM·audit native COUNT, audit emit 없음) | — | 활성: `deletedAt IS NULL`, 휴지통 파일은 `IS NOT NULL` 별도 카운트 | 401, 403 |

> 감사 로그 endpoint는 ADR §1 원칙 8에 따라 read-only — UPDATE/DELETE 노출 금지.

```text
GET /api/admin/dashboard/summary                     (admin-dashboard 트랙 closure, 2026-05-07)
  Headers:  Cookie: SESSION=<id>             (ADMIN 인증 필요)
  Response: 200 {
              summary: {
                users:       { total: number, active: number },
                departments: { total: number, active: number },   // Department는 is_active 컬럼 부재로 둘 다 deletedAt IS NULL
                folders:     { active: number },
                files:       { active: number, trashed: number }, // active=deletedAt IS NULL, trashed=deletedAt IS NOT NULL
                audit:       { last24h: number },                  // audit_log COUNT(*) WHERE occurred_at >= now()-24h
                storage:     { usedBytes: number }                  // SUM(file_versions.size_bytes), 모든 버전 누적
              }
            }
  Side-effects: 없음 (read-only — audit emit 없음)
  Errors:
    401 UNAUTHORIZED      (미인증)
    403                   (ADMIN 아님 — `@PreAuthorize` 차단)
```

**관련 repository** (P1):
- `User.countByDeletedAtIsNull()` / `countByDeletedAtIsNullAndIsActiveTrue()`
- `Department.countByDeletedAtIsNull()`
- `Folder.countByDeletedAtIsNull()`
- `File.countByDeletedAtIsNull()` / `countByDeletedAtIsNotNull()`
- `FileVersion.sumAllSizeBytes()` — `SELECT COALESCE(SUM(v.sizeBytes), 0)`
- audit_log: `JdbcTemplate "SELECT COUNT(*) FROM audit_log WHERE occurred_at >= ?"` (Clock 주입으로 결정성 확보)

```text
GET /api/admin/audit/export                          (Wave 1 T2 + audit-export-json, 2026-05-08)
  Query:    fromDate, toDate, actorQuery, eventType, targetType, targetId  (모두 선택)
            format=csv|json   (default csv. unknown → 400 BAD_REQUEST)
  Headers:  Cookie: SESSION=<id>             (AUDITOR/ADMIN 인증 필요)
  Response: 200 (StreamingResponseBody)
            Content-Type:
              format=csv  → text/csv;charset=UTF-8 + UTF-8 BOM + RFC 4180 (10 cols, metadata는 JSON 문자열 cell)
              format=json → application/json;charset=UTF-8 (BOM 없음, plain array [{...}, {...}], metadata는 nested object)
            Content-Disposition: attachment; filename=audit_logs_<YYYY-MM-DD>.<csv|json>
            X-Audit-Export-Truncated: true (cap 10,000 초과 시)
  Side-effects:
            audit.exported audit_log row 1건 (REQUIRES_NEW, 별도 트랜잭션)
              metadata = { filters, rowCount, truncated, format("csv"|"json") }
  Errors:
    400 BAD_REQUEST    ({code:"BAD_REQUEST"} — format이 csv/json 외 값일 때 IllegalArgumentException 매핑)
    401 UNAUTHORIZED   (미인증)
    403                (AUDITOR/ADMIN 아님 — `@PreAuthorize` 차단)
```

```text
POST /api/admin/users                                (m-admin-entry-rewrite, ADR #21 closure, 2026-05-03)
  Headers:  X-CSRF-Token: <token>            (필수)
            Cookie: SESSION=<id>             (ADMIN 인증 필요)
  Request:  { email: string, displayName: string, role: 'MEMBER' | 'AUDITOR' | 'ADMIN' }
  Validation:
    - email: @NotBlank @Email (lowercase trim 후 저장)
    - displayName: @NotBlank @Size(max=100)
    - role: @NotNull (Role enum)
  Response: 200 {
              id: string (UUID),
              email: string,
              displayName: string,
              role: 'MEMBER' | 'AUDITOR' | 'ADMIN',
              mustChangePassword: true
            }
            Note: **tempPassword/password/passwordHash 필드는 응답에 포함되지 않는다** (docs/03 §2.8).
                  임시 PW는 EmailService를 통해 사용자 메일함으로만 전달.
  Side-effects:
            - users INSERT (mustChangePassword=true, password_hash=bcrypt(생성된 임시 PW))
            - AFTER_COMMIT 이벤트: AdminUserCreatedEvent → AdminAuditListener (REQUIRES_NEW)로
              audit_log 기록 (`admin.user.created`, actor_id=인증된 ADMIN.id, target=새 user.id)
            - EmailService.sendInviteEmail(email, rawTempPassword) — dev=stdout, prod=SMTP
  Errors:
    400 VALIDATION_ERROR  { details: { field: 'email'|'displayName'|'role', rule: '...' } }
    401 UNAUTHORIZED      (미인증)
    403                   (ADMIN role 아님 — `@PreAuthorize` 차단, 본문 없음)
    409 CONFLICT          { code: 'CONFLICT', reason: 'DUPLICATE_EMAIL' }
                          (동일 email 활성 사용자 존재 — auth flat envelope, AuthExceptionHandler 매핑)
```

**관련 audit 이벤트** (docs/03 §2.10): `admin.user.created`.

```text
GET /api/admin/users?page=0&size=50&q=<keyword>      (admin-user-mgmt + admin-user-search-update, 2026-05-06)
  Headers:  Cookie: SESSION=<id>             (ADMIN 인증 필요)
  Query:    page    (default 0, min 0)
            size    (default 50, min 1, max 200 — controller가 clamp)
            q       (선택; blank/null이면 전체 목록. 값이 있으면 email/displayName
                     case-insensitive 부분 매칭. service 단계에서 LIKE wildcard
                     `\` `%` `_` 이스케이프 후 `LOWER(...) LIKE LOWER(?) ESCAPE '\\'`)
  Response: 200 Spring Page<AdminUserSummary> 직렬화
            {
              content: [{
                id: string,
                email: string,
                displayName: string,
                role: 'MEMBER' | 'AUDITOR' | 'ADMIN',
                isActive: boolean,
                createdAt: string (ISO-8601),
                lastLoginAt: string | null
              }, ...],
              totalElements: number,
              totalPages: number,
              number: number (0-based 현재 페이지),
              size: number
            }
            정렬: createdAt DESC, id ASC (repository @Query 강제).
            soft-delete된 user 제외, 비활성(`isActive=false`) user 포함 (재활성/role 변경 대상).
            Note: passwordHash/password 필드는 응답에 포함되지 않는다 (docs/03 §2.8).
  Errors:
    401 (미인증), 403 (ADMIN role 아님 — `@PreAuthorize` 차단)
```

```text
PATCH /api/admin/users/:id                           (admin-user-mgmt + admin-user-search-update, 2026-05-06)
  Headers:  X-CSRF-Token: <token>            (필수)
            Cookie: SESSION=<id>             (ADMIN 인증 필요)
  Path:     id (UUID — 대상 사용자)
  Request:  { role?: 'MEMBER' | 'AUDITOR' | 'ADMIN',
              isActive?: boolean,
              displayName?: string }
            - 모두 optional이지만 최소 하나는 채워야 함 (모두 null이면 400)
            - 여러 필드 동시에 보내면 controller가 role → isActive → displayName 순서로
              각각 별도 service 호출 → audit row가 분리 emit (의도)
            - displayName: trim 후 1-100자 (User.changeDisplayName 도메인 검증)
  Response: 200 AdminUserSummary (위 GET content[] 항목과 동일 shape — 적용 후 스냅샷)
  Side-effects (각 분기별):
            - role 변경: users.role UPDATE + AFTER_COMMIT AdminRoleChangedEvent
              → audit_log `admin.role.changed` (before/after JSON `{"role":"..."}`)
            - isActive=false(deactivate): users.is_active UPDATE + AFTER_COMMIT
              AdminUserDeactivatedEvent → audit_log `admin.user.deactivated`
            - isActive=true(reactivate): users.is_active UPDATE + AFTER_COMMIT
              AdminUserUpdatedEvent → audit_log `admin.user.updated`
              (before/after JSON `{"isActive":false|true}`)
            - displayName 변경: users.display_name UPDATE + AFTER_COMMIT
              AdminUserUpdatedEvent → audit_log `admin.user.updated`
              (before/after JSON `{"displayName":"..."}`)
            - 멱등 no-op: 같은 role 재지정 / 이미 비활성에 deactivate / 이미 활성에
              reactivate / 동일 displayName 재설정은 모두 audit 미발행
  Self-protection:
            - actor==target && newRole != ADMIN → 403 SELF_PROTECTION (self-demote 차단)
            - actor==target && deactivate → 403 SELF_PROTECTION (self-deactivate 차단)
            - reactivate / displayName 변경은 self 허용 (제재 아님)
            - 본인이 마지막 ADMIN인지 여부와 무관하게 일관 차단 (검증 단순화)
  Errors:
    400 VALIDATION_ERROR  { details: { field: 'body', rule: '...' } }
                          (모든 필드 null 또는 displayName blank/길이 초과)
    401                   (미인증)
    403                   (ADMIN role 아님 — `@PreAuthorize`, 본문 없음)
    403 FORBIDDEN/SELF_PROTECTION
                          (actor==target self-demote/self-deactivate)
    404 NOT_FOUND/USER_NOT_FOUND
                          (target user 미존재)
```

**관련 audit 이벤트** (docs/03 §2.10): `admin.role.changed`, `admin.user.deactivated`,
`admin.user.updated` (admin-user-search-update — reactivate + displayName 편집 공용).

```text
GET /api/admin/departments?page=0&size=50&q=영업       (admin-department-crud, Wave 2 T4, 2026-05-06)
  Headers:  Cookie: SESSION=<id>             (ADMIN 인증 필요)
  Query:    page    (default 0, min 0)
            size    (default 50, min 1, max 200 — controller가 clamp)
            q       (선택, trim 후 비어있으면 전체 조회)
  Response: 200 Spring Page<AdminDepartmentSummary>
            {
              content: [{
                id: string (UUID),
                name: string,
                isActive: boolean,            // deletedAt == null 도출
                createdAt: string (ISO-8601)
              }, ...],
              totalElements, totalPages, number, size
            }
            정렬: createdAt DESC, id ASC.
            soft-delete된 dept 제외 안 함 (admin은 비활성 포함 — reactivate 가능하도록).
            검색: LOWER(name) LIKE :q ESCAPE '\\' (와일드카드 \, %, _ escape).
  Errors:
    401 (미인증), 403 (ADMIN role 아님 — `@PreAuthorize` 차단)
```

```text
POST /api/admin/departments                            (admin-department-crud, Wave 2 T4)
  Headers:  X-CSRF-Token: <token>
            Cookie: SESSION=<id>             (ADMIN 인증 필요)
  Request:  { name: string }
  Validation:
    - name: @NotBlank @Size(max=100), trim 후 1~100자
  Response: 200 AdminDepartmentSummary (위 GET content[] 항목과 동일 shape)
            Note: Spring default 200 — @ResponseStatus(CREATED) 미사용
                  (AdminUserController invite 패턴 답습).
  Side-effects:
            - departments INSERT (deleted_at=null → isActive=true)
            - AFTER_COMMIT: AdminDepartmentCreatedEvent → AdminDepartmentAuditListener
              → audit_log `admin.department.created`
  Errors:
    400 VALIDATION_ERROR  { details: { field: 'name', rule: '...' } }
    401 (미인증)
    403 (ADMIN role 아님)
    409 DEPARTMENT_CONFLICT { code: 'DEPARTMENT_CONFLICT' }
                            (동일 이름 활성 dept 존재 — V9 partial unique
                             또는 service 사전 검사로 차단)
```

```text
PATCH /api/admin/departments/:id                       (admin-department-crud, Wave 2 T4)
  Headers:  X-CSRF-Token: <token>
            Cookie: SESSION=<id>             (ADMIN 인증 필요)
  Path:     id (UUID — 대상 부서)
  Request:  { name?: string, isActive?: boolean }
            - 둘 다 optional이지만 최소 하나는 채워야 함 (`isEmpty()` → 400)
            - 둘 다 보내면 rename 먼저 적용 후 (de)activate
            - 동일 이름 rename / 같은 isActive 토글은 멱등 no-op (audit 미발행)
  Response: 200 AdminDepartmentSummary (적용 후 스냅샷)
  Side-effects:
            - rename: departments.name UPDATE
              → AFTER_COMMIT: AdminDepartmentUpdatedEvent (before/after `{name}`)
              → audit_log `admin.department.updated`
            - isActive=false: departments.deleted_at=NOW()
              → AFTER_COMMIT: AdminDepartmentDeactivatedEvent
              → audit_log `admin.department.deactivated`
            - isActive=true: departments.deleted_at=null
              → AFTER_COMMIT: AdminDepartmentUpdatedEvent (before/after `{isActive}`)
              → audit_log `admin.department.updated` (제재 분기 아님)
  Errors:
    400 VALIDATION_ERROR  (빈 body / name 길이 / blank)
    401 (미인증)
    403 (ADMIN role 아님)
    404 NOT_FOUND          (target dept 미존재)
    409 DEPARTMENT_CONFLICT (rename 또는 reactivate 시 동일 이름 활성 dept 존재)
```

**관련 audit 이벤트** (docs/03 §4.1): `admin.department.created`, `admin.department.updated`, `admin.department.deactivated`.

```text
GET /api/admin/permissions?subjectType=user&subjectId=...&resourceType=folder
                          &preset=read&q=alice&page=0&size=20      (admin-permission-matrix, Wave 2 T5, 2026-05-07)
  Headers:  Cookie: SESSION=<id>             (ADMIN 인증 필요)
  Query:    subjectType   (선택; 'user' | 'department' | 'role' | 'everyone')
            subjectId     (선택; subjectType 없이 단독 입력 시 400)
                          - subjectType=user/department → UUID 검증
                          - subjectType=role → enum 'MEMBER'|'AUDITOR'|'ADMIN' (text)
                          - subjectType=everyone → 무시(서비스가 강제 null화)
            resourceType  (선택; 'folder' | 'file')
            preset        (선택; 'read' | 'upload' | 'edit' | 'admin')
            q             (선택; subject/resource name 부분일치
                           — service에서 trim+lowercase+LIKE escape `\` `%` `_`)
            page          (default 0, min 0)
            size          (default 20, min 1, max 100 — controller가 clamp)
  Response: 200 Spring Page<AdminPermissionRowResponse>
            {
              content: [{
                id: string (UUID — permissions.id),
                subjectType: 'user' | 'department' | 'role' | 'everyone',
                subjectId: string | null,             // everyone → null
                subjectName: string | null,           // 삭제/비활성 사용자/부서 시 null
                resourceType: 'folder' | 'file',
                resourceId: string (UUID),
                resourceName: string | null,          // soft-deleted resource 시 null
                preset: 'read' | 'upload' | 'edit' | 'admin',
                grantedByActorId: string (UUID),
                grantedByName: string | null,
                grantedAt: string (ISO-8601),         // permissions.created_at
                expiresAt: string | null (ISO-8601),
                isExpired: boolean                    // expiresAt < now
              }, ...],
              totalElements, totalPages, number, size
            }
            정렬: created_at DESC, id DESC (tie-break).
            **만료된 grant도 결과 포함** — cron 정리 전 가시화 목적, isExpired=true 표시.
            soft-deleted subject/resource 도 grant row가 살아 있으면 노출
            (LEFT JOIN — name=null로 fallback).
  Errors:
    400 VALIDATION_ERROR  { details: { field: 'subjectType'|'subjectId'|'resourceType'|'preset', rule: '...' } }
                          (invalid enum / subjectId without subjectType / 잘못된 UUID)
    401 (미인증)
    403 (ADMIN role 아님 — `@PreAuthorize`, 본문 없음)
```

> read-only viewer — mutation(grant/revoke) endpoint는 v1.x deferred. 만료 정리는 별도 cron 트랙(미구현)에서 처리. 본 endpoint는 grant 가시화만 담당.

```text
GET /api/admin/system/cron                              (Wave 1 T3 system-cron-readonly, 2026-05-07)
  Headers:  Cookie: SESSION=<id>             (ADMIN 인증 필요 — AUDITOR 제외)
  Response: 200 {
              jobs: [{
                key: 'purge.expired' | 'share.expire' | 'permission.expire' | 'storage.orphan.cleanup',
                label: string,                  // 한글 라벨 (예: '휴지통 hard purge')
                enabled: boolean,
                cron: string,                   // 6-field Spring cron 표현식
                zone: string,                   // 'Asia/Seoul' 등
                batchSize?: number,             // share/permission expire 잡 한정
                maxPerRun?: number,             // purge/storage 잡 한정
                graceHours?: number             // storage.orphan.cleanup 한정
              }, ...]                           // 항상 4개, 고정 순서
            }
            순서: purge.expired → share.expire → permission.expire → storage.orphan.cleanup
            (controller에서 List.of로 강제 — 프론트 카드 순서 안정화).
            optional 필드는 응답 직렬화 시 `@JsonInclude(NON_NULL)`로 생략.
  Side-effects: 없음 (audit emit 0 — SELECT-only, 운영 설정 노출은 감사 대상 아님).
  Errors:
    401 (미인증)
    403 (MEMBER 또는 AUDITOR — `@PreAuthorize` 차단, 본문 없음)
  Note: 본 endpoint는 read-only 노출만 담당. 변경은 application.yml + 재기동
        (mutation endpoint는 v1.x deferred — 운영팀이 SCM 통제하에 변경).
```

### 7.13 SSE 실시간 동기화 (ADR #14)

`SseEmitter` (Spring MVC) 기반. 단일 endpoint `GET /api/sse/files`로 폴더 ID 구독.

| Method | Path | Guard | TX | Norm | SoftDel | Errors |
|---|---|---|---|---|---|---|
| GET | `/api/sse/files?folderIds=id1,id2,...` | `hasPermission(#folderIds[*], 'folder', 'READ')` | — | — | — | 403 (구독 불가 폴더 포함 시 → 권한 있는 것만 자동 필터), 429 (구독 한도) |

```text
GET /api/sse/files?folderIds=fld_a,fld_b
  Response: 200 (text/event-stream)
            Cache-Control: no-cache
            X-Accel-Buffering: no
  Heartbeat: 25초마다 ': keepalive\n\n' (프록시 타임아웃 회피)
  종료:     클라이언트 disconnect → SseEmitter.completeWithError 또는 complete()
```

#### 7.13.1 이벤트 타입 (`SseEventType` enum)

> 프론트 미러: `frontend/src/types/sse.ts` (docs/01 §15.2). 변경 시 양쪽 동시 갱신.

| event | 트리거 | scope.folderIds |
|---|---|---|
| `FILE_CREATED` | 업로드 완료 (A4 finalize) | `[folderId]` |
| `FILE_RENAMED` | `PATCH /api/files/:id { name }` | `[folderId]` |
| `FILE_MOVED` | `POST /api/files/:id/move` — 출발/도착 양쪽 fan-out | `[sourceFolderId, targetFolderId]` |
| `FILE_VERSION_CREATED` | `POST /api/files/:id/versions` | `[folderId]` |
| `FILE_DELETED` | soft delete (휴지통 이동) | `[folderId]` |
| `FILE_RESTORED` | `POST /api/files/:id/restore` | `[folderId]` |
| `FILE_PURGED` | 영구 삭제 (`DELETE /api/trash/:type/:id`, ADMIN) — audit는 A8 활성화, SSE emission은 인프라 milestone deferred (ADR #32) | `[folderId]` |
| `FOLDER_CREATED` | 폴더 생성 | `[parentFolderId]` |
| `FOLDER_RENAMED` | 폴더 이름변경 | `[parentFolderId, folderId]` |
| `FOLDER_MOVED` | 폴더 이동 | `[sourceParent, targetParent]` |
| `FOLDER_DELETED` | soft delete | `[parentFolderId]` |
| `FOLDER_RESTORED` | 복원 | `[parentFolderId]` |
| `FOLDER_PURGED` | 영구 삭제 (`DELETE /api/trash/:type/:id`, ADMIN) — audit는 A8 활성화, SSE emission은 인프라 milestone deferred (ADR #32) | `[parentFolderId]` |
| `PERMISSION_GRANTED` | `POST /api/:resource/:id/permissions` | `[resourceId]` (folder인 경우) |
| `PERMISSION_REVOKED` | `DELETE /api/permissions/:permissionId` | `[resourceId]` |
| `PERMISSION_CHANGED` | 기존 권한의 preset/expiry 변경 | `[resourceId]` |

#### 7.13.2 페이로드 스키마

```json
{
  "id": "evt_<uuid>",
  "type": "FILE_CREATED",
  "occurredAt": "2026-04-25T12:34:56.789Z",
  "actor": { "userId": "usr_xxx", "displayName": "홍길동" },
  "scope": { "folderIds": ["fld_a"] },
  "payload": {
    "fileId": "file_xxx",
    "name": "report.pdf",
    "currentVersionId": "ver_xxx"
  }
}
```

이벤트별 `payload` 필드는 별도 DTO (`FileCreatedPayload`, `FileMovedPayload` 등)로 정의 — A5 구현 시 명세화.

#### 7.13.3 무효화 규약 (프론트 ↔ TanStack Query)

- 클라이언트는 이벤트 수신 시 `queryKeys`(docs/01 §6.1)에 매핑하여 `invalidateQueries` 호출.
- `FILE_*` 이벤트 → `queryKeys.filesInFolder(folderId)` 무효화
- `FOLDER_*` 이벤트 → `queryKeys.folderTree()` + `queryKeys.folder(folderId)` 무효화
- `PERMISSION_CHANGED` → `queryKeys.effectivePermissions(nodeId)` 무효화

#### 7.13.4 EventBus 설계 (백엔드 내부)

- 모든 mutation 트랜잭션이 **COMMIT 후** `EventBus.publish(SseEvent)` 호출 (트랜잭션 롤백 시 이벤트 누출 방지).
- `EventBus`는 ApplicationEventPublisher + `@TransactionalEventListener(phase=AFTER_COMMIT)` 패턴.
- `FilesSseController`는 `EventBus`를 구독, 활성 `SseEmitter`의 scope와 매칭하여 fan-out.

---

### 7.14 사용자 검색 (User Search, ADR #35)

공유 subject picker(파일/폴더 공유 대상자 선택)에서 사용자 lookup 용도. 인증된 모든 사용자에 대해 공개 — ADR #18(관리자 초대 only 등록)로 사용자 명단 자체가 trust boundary 내부 정보.

| Method | Path | Guard | TX | Norm | SoftDel | Errors |
|---|---|---|---|---|---|---|
| GET | `/api/users/search?q=&limit=` | `isAuthenticated()` | — | q→trim+lowercase | `WHERE deleted_at IS NULL AND is_active=TRUE` | 400 INVALID_SEARCH_QUERY (q 길이/형식), 401 UNAUTHORIZED |

```text
GET /api/users/search?q=alice&limit=20
  Validation:
    - q: required, trim+lowercase 후 minLength 2자, blank/null 거부 → 400 INVALID_SEARCH_QUERY (A9 일관)
    - limit: 1~50 (default=20, hard cap=50, 0/음수→default)
  Algorithm:
    - LIKE escape: % _ \ → backslash prefix (literal 매칭)
    - 패턴: '%' + escaped(lowercased(q)) + '%'
    - SQL: LOWER(display_name) LIKE :p ESCAPE '\\'  OR  LOWER(email) LIKE :p ESCAPE '\\'
    - ORDER BY display_name ASC, id ASC  LIMIT :limit
  Response: 200 {
              items: [
                { id: string (UUID), displayName: string, email: string }
              ]
            }
  Errors:
    400 INVALID_SEARCH_QUERY  { } — q normalize 후 minLength 미달/blank/null
    401 UNAUTHORIZED          { } — 비인증
  Note:
    - cursor 미지원 (ADR #35) — 결과는 limit 절단(최대 50). 추후 페이지네이션 도입 시 nextCursor 필드 추가.
    - department/role 필터 deferred — share subject picker MVP는 user lookup만 필요.
    - audit emission 없음 — A9 search와 동일 정책 (검색 행위 자체 감사는 보안 트랙 별도).
```

**근거**: ADR #35.

---

### 7.15 부서 검색 (Department Search, ADR #37)

공유 subject picker에서 부서 lookup 용도. A14 user search(§7.14, ADR #35) 1:1 답습 — 인증된 모든 사용자에 공개.

| Method | Path | Guard | TX | Norm | SoftDel | Errors |
|---|---|---|---|---|---|---|
| GET | `/api/departments/search?q=&limit=` | `isAuthenticated()` | — | q→trim+lowercase | `WHERE active = TRUE` | 400 INVALID_SEARCH_QUERY (q 길이/형식), 401 UNAUTHORIZED |

```text
GET /api/departments/search?q=eng&limit=20
  Validation:
    - q: required, trim+lowercase 후 minLength 2자, blank/null 거부 → 400 INVALID_SEARCH_QUERY (A9/A14 일관)
    - limit: 1~50 (default=20, hard cap=50, 0/음수→default)
  Algorithm:
    - LIKE escape: % _ \ → backslash prefix (literal 매칭)
    - 패턴: '%' + escaped(lowercased(q)) + '%'
    - SQL: LOWER(name) LIKE :p ESCAPE '\\'
    - ORDER BY name ASC, id ASC  LIMIT :limit
  Response: 200 {
              items: [
                { id: string (UUID), name: string }
              ]
            }
  Errors:
    400 INVALID_SEARCH_QUERY  { } — q normalize 후 minLength 미달/blank/null
    401 UNAUTHORIZED          { } — 비인증
  Note:
    - cursor 미지원 (ADR #37) — A14 일관. 결과는 limit 절단(최대 50).
    - LTREE path 컬럼 필터 미사용 (ADR #37, MVP flat). 조직도 트리 v1.x deferred.
    - audit emission 없음 — A9/A14와 동일 정책.
```

**근거**: ADR #37.

---

## 8. 에러 코드 표준

| HTTP | code | 의미 | 프론트 처리 |
|---|---|---|---|
| 400 | VALIDATION_ERROR | 입력 검증 실패 | 폼 에러 표시 |
| 401 | UNAUTHORIZED | 인증 필요 | 로그인 페이지 redirect |
| 403 | PERMISSION_DENIED | 권한 없음 | 토스트 + `effectivePermissions` 재조회 |
| 404 | NOT_FOUND | 리소스 없음 | not-found 페이지 |
| 409 | UPLOAD_CONFLICT | 업로드 동일명 충돌 | ConflictDialog |
| 409 | RENAME_CONFLICT | 이름 변경 충돌 | RenameDialog 재표시 |
| 409 | RESTORE_CONFLICT | 복원 시 원위치 충돌 | 이름 변경 후 복원 제안 |
| 409 | VERSION_CONFLICT | 버전 업로드 시 최신 버전 불일치 | "최신 버전 확인" 다이얼로그 |
| 409 | DEPARTMENT_CONFLICT | 동일 이름의 활성 부서 존재 (admin create/rename/reactivate) | 인라인 에러 — "같은 이름의 활성 부서가 이미 존재합니다" |
| 400 | MOVE_INTO_SELF | 자기 자신으로 이동 시도 | UI 차단, 도달 시 토스트 |
| 400 | MOVE_INTO_DESCENDANT | 후손 폴더로 이동 시도 | UI 차단, 도달 시 토스트 |
| 404 | TARGET_NOT_FOUND | 이동 타겟 폴더가 없음 | 토스트 + 폴더 트리 재조회 |
| 413 | QUOTA_EXCEEDED | 스토리지 할당량 초과 | 관리자 문의 안내 |
| 413 | FILE_TOO_LARGE | 단일 파일 크기 초과 | 경고 |
| 415 | UNSUPPORTED_MEDIA_TYPE | 금지된 확장자 | 경고 |
| 423 | ACCOUNT_LOCKED | 로그인 5회 실패 누적 → 15분 락 (docs/03 §2.6) | `retryAfterSec` 표시 + 시간 후 재시도 안내 |
| 423 | FILE_LOCKED | 파일 잠김 (v1.x, pessimistic) | 잠금 해제 안내 |
| 423 | LEGAL_HOLD_VIOLATION | Legal Hold 활성 — mutation 차단 (v2.x, ADR #46) | "법적 보존으로 인해 작업할 수 없습니다" 토스트 + hold 사유/지정자/지정일 details 노출 (관리자 권한자 한정) |
| 409 | LEGAL_HOLD_RECENTLY_RELEASED | 30일 이내 release된 target 재지정 시도 (v2.x, ADR #46) | "최근 해제된 hold가 있어 30일간 재지정 불가" 토스트 + 재지정 가능 일자 표시 |
| 202 | APPROVAL_REQUIRED | dual-approval 게이트 활성 — 1단계 응답, secondary 결정 대기 (v1.x, ADR #47) | "승인 요청을 등록했습니다. secondary admin 결정을 대기 중입니다" 토스트 + `/admin/approvals` redirect, details: `{approvalId, expiresAt}` |
| 403 | APPROVAL_SELF | secondary가 self (requested_by 또는 action target) (v1.x, ADR #47) | "본인 요청은 승인할 수 없습니다" 인라인 에러 |
| 404 | APPROVAL_NOT_FOUND | approval row 미존재 또는 다른 사용자 cancel 시도 (v1.x, ADR #47) | 토스트 + 목록 재조회 |
| 409 | APPROVAL_ALREADY_DECIDED | terminal status(APPROVED/REJECTED/CANCELLED/EXPIRED)에 재결정 시도 (v1.x, ADR #47) | "이미 결정된 요청입니다" 토스트 + 목록 재조회 |
| 429 | RATE_LIMIT_EXCEEDED | 요청 한도 초과 | 지수 백오프 재시도 |
| 500 | INTERNAL_ERROR | 서버 오류 | 재시도 / 에러 리포트 |
| 503 | SERVICE_UNAVAILABLE | 점검 중 | 점검 페이지 |

---

## 9. 성능 고려사항

### 9.1 대용량 폴더 (파일 10k+)

- cursor 페이지네이션 (offset 금지)
- `idx_files_folder` 사용
- 응답에 `totalCount` 포함 (COUNT(*) 비용 크므로 캐시 또는 추정치)

### 9.2 폴더 트리

- MVP: 전체 트리 한 번에 반환 (폴더 수 < 1000 가정)
- v1.x: lazy loading (클릭 시 자식 조회) + closure table
- 캐시: `Cache-Control: private, max-age=60`

### 9.3 effective permission 계산

- MVP: 요청 시 재귀 CTE로 상위 폴더 순회
- v1.x: materialized view 또는 권한 상속 테이블 비정규화

### 9.4 audit_log

- 월별 파티셔닝 필수 (연 1억 행 예상 시)
- 쓰기 부하 완화: INSERT만 허용, 배치 INSERT 가능
- 읽기 분리: read replica 활용

---

## 10. 마이그레이션 전략

### 10.1 순서

```text
1. users, departments
2. folders (루트 + 부서별)
3. 권한 기본값 세팅 (users.role = admin이 전체 admin)
4. 기존 파일 이관 (있다면) → files + file_versions
5. audit_log 권한 설정 (REVOKE UPDATE/DELETE)
```

### 10.2 기존 시스템에서 마이그레이션 시

- storage_key 생성하며 S3에 복사
- 파일명 NFC 정규화 + normalized_name 계산
- 이름 충돌 발생 시: 자동으로 `(1)`, `(2)` 접미사
- 실패 로그 별도 기록

---

## 다음 문서

- **03-security-compliance.md**: 권한 매트릭스, 감사 대상 이벤트, 저장소 보안 상세, Legal Hold
- **04-admin-operations.md**: 관리자 페이지 UI, 쿼터 정책, 백업/복구, 모니터링
