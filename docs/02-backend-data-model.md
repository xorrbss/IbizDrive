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
file_versions         : 삭제 불가 (버전은 영구 보존)
permissions           : 물리 삭제 (revoked_at 기록 후 audit_log로만 추적)
audit_log             : 삭제 불가 (append-only)
```

---

## 2. DB 스키마

### 2.1 users

```sql
CREATE TABLE users (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email            VARCHAR(255) NOT NULL UNIQUE,
  name             VARCHAR(100) NOT NULL,
  department_id    UUID REFERENCES departments(id),
  role             VARCHAR(50) NOT NULL DEFAULT 'member',  -- member|admin|auditor
  storage_quota    BIGINT NOT NULL DEFAULT 10737418240,    -- 10GB
  storage_used     BIGINT NOT NULL DEFAULT 0,
  is_active        BOOLEAN NOT NULL DEFAULT TRUE,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_department ON users(department_id) WHERE is_active = TRUE;
```

### 2.2 departments

```sql
CREATE TABLE departments (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name        VARCHAR(100) NOT NULL,
  parent_id   UUID REFERENCES departments(id),
  path        LTREE,                 -- 계층 쿼리용 (조직도 전체 조회)
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_departments_path ON departments USING GIST (path);
```

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
CREATE UNIQUE INDEX idx_folders_unique_name
  ON folders (parent_id, normalized_name)
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
  preset          VARCHAR(20) NOT NULL,             -- read|upload|edit|admin
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

### 2.7 shares

공유는 permissions의 특수 케이스이지만, UX/감사 편의로 별도 테이블로 관리:

```sql
CREATE TABLE shares (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  file_id         UUID REFERENCES files(id) ON DELETE CASCADE,
  folder_id       UUID REFERENCES folders(id) ON DELETE CASCADE,
  permission_id   UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
  shared_by       UUID NOT NULL REFERENCES users(id),
  message         TEXT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  revoked_at      TIMESTAMPTZ,
  revoked_by      UUID REFERENCES users(id),

  CHECK ((file_id IS NOT NULL)::int + (folder_id IS NOT NULL)::int = 1)
);

CREATE INDEX idx_shares_active ON shares(shared_by) WHERE revoked_at IS NULL;
```

### 2.8 audit_log (append-only)

```sql
CREATE TABLE audit_log (
  id             BIGSERIAL PRIMARY KEY,
  timestamp      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  actor_id       UUID REFERENCES users(id),
  actor_ip       INET,
  user_agent     TEXT,
  event_type     VARCHAR(50) NOT NULL,
  target_type    VARCHAR(20) NOT NULL,              -- file|folder|user|permission|share
  target_id      UUID,
  before_state   JSONB,
  after_state    JSONB,
  metadata       JSONB,                             -- 추가 컨텍스트

  CHECK (target_type IN ('file', 'folder', 'user', 'permission', 'share', 'system'))
);

-- 🔑 append-only 강제: DB 사용자 권한으로 UPDATE/DELETE 차단
REVOKE UPDATE, DELETE ON audit_log FROM app_user;
GRANT INSERT, SELECT ON audit_log TO app_user;

-- 파티셔닝 (월별)
-- CREATE TABLE audit_log_2026_01 PARTITION OF audit_log FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');

CREATE INDEX idx_audit_timestamp ON audit_log(timestamp DESC);
CREATE INDEX idx_audit_actor ON audit_log(actor_id, timestamp DESC);
CREATE INDEX idx_audit_target ON audit_log(target_type, target_id, timestamp DESC);
CREATE INDEX idx_audit_event ON audit_log(event_type, timestamp DESC);
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

### 2.10 ER 요약

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
        └── audit_log (actor)
```

---

## 3. 정규화 함수

### 3.1 의사 코드 (프론트/백엔드 동일)

```text
normalizeFileName(name):
    return name.normalize('NFC').trim()

normalizedNameForDedup(name):
    return name.normalize('NFC').toLowerCase().trim()

normalizeForSearch(s):
    return s.normalize('NFC').toLowerCase().trim().replaceAll(/\s+/g, ' ')
```

### 3.2 Postgres 구현

```sql
-- 의존: intarray, unaccent (필요 시)
CREATE OR REPLACE FUNCTION normalize_name_for_dedup(input TEXT)
RETURNS TEXT IMMUTABLE LANGUAGE SQL AS $$
  SELECT LOWER(TRIM(NORMALIZE(input, NFC)))
$$;

-- 저장 시 자동 계산
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

```sql
-- 이동
BEGIN;
UPDATE files
  SET deleted_at = NOW(),
      purge_after = NOW() + INTERVAL '30 days',
      original_folder_id = folder_id
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
      original_folder_id = NULL
  WHERE id = ANY(:ids);
COMMIT;
```

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

```text
Authorization: Bearer <JWT>
X-Request-Id: <uuid>  (클라이언트 생성, 로그 추적용)
Accept-Language: ko-KR

응답 공통:
  X-Request-Id: <echo>
  X-RateLimit-Remaining: <int>
```

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

### 7.3 주요 엔드포인트

#### 폴더

```text
GET    /api/folders/tree                 폴더 트리 (사용자 권한 필터링)
GET    /api/folders/:id                   폴더 상세 + breadcrumb + effective permissions
POST   /api/folders                       { parentId, name } 생성
PATCH  /api/folders/:id                   { name? } 이름 변경
POST   /api/folders/:id/move              { targetParentId } 이동
DELETE /api/folders/:id                   휴지통 이동
POST   /api/folders/:id/restore           복원
```

#### 파일

```text
GET    /api/folders/:id/files?sort=&dir=&cursor=   목록 (cursor 페이지네이션)
GET    /api/files/:id                              상세
POST   /api/files/upload                           업로드 (multipart)
  - Request: multipart with { file, folderId, conflictResolution? }
  - 200: { file, version }
  - 409 UPLOAD_CONFLICT: { existingFile }
  - 413 QUOTA_EXCEEDED
  - 403 PERMISSION_DENIED

POST   /api/files/:id/versions                     새 버전 업로드
  - Body: { expectedCurrentVersionId, conflictResolution? }
  - 409 VERSION_CONFLICT: { currentVersion }

PATCH  /api/files/:id                              { name? } 이름 변경
  - 409 RENAME_CONFLICT
POST   /api/files/:id/move                         이동
DELETE /api/files/:id                              휴지통 이동
POST   /api/files/:id/restore                      복원
  - 409 RESTORE_CONFLICT: 원위치에 동일명 파일 존재

GET    /api/files/:id/download?version=?           원본 다운로드
  - 헤더: Content-Disposition (filename*)
GET    /api/files/:id/preview?version=?            프리뷰 스트림
GET    /api/files/:id/versions                     버전 목록
GET    /api/files/:id/activity                     활동 이력 (사용자 활동 한정)
```

#### 검색

```text
GET    /api/search?q=&type=&owner=&modifiedFrom=&modifiedTo=
  - minLength: 2
  - normalize(q) = normalizeForSearch(q)
  - AbortController 지원 (서버는 stateless, 클라이언트 cancel만)
```

#### 공유

```text
GET    /api/shares/by-me                           내가 공유한 것
GET    /api/shares/with-me                         내가 공유받은 것
POST   /api/files/:id/share                        { subjects: [...], preset, expiresAt?, message? }
DELETE /api/shares/:shareId                        공유 회수
```

#### 권한

```text
GET    /api/:resource/:id/permissions              권한 목록
POST   /api/:resource/:id/permissions              권한 부여
DELETE /api/permissions/:permissionId              권한 회수
GET    /api/me/effective-permissions?nodeId=?      유효 권한 계산
```

#### 휴지통

```text
GET    /api/trash                                  휴지통 목록
POST   /api/trash/:id/restore                      복원
DELETE /api/trash/:id                              영구 삭제 (관리자만)
DELETE /api/trash                                  전체 영구 삭제 (관리자만)
```

#### 관리자

```text
GET    /api/admin/audit-logs?...                   감사 로그 (필터/페이지네이션)
GET    /api/admin/download-logs
GET    /api/admin/permission-logs
GET    /api/admin/storage-usage                    전체 사용량
GET    /api/admin/users                            사용자 관리
PATCH  /api/admin/users/:id                        사용자 수정 (role, quota 등)
```

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
| 413 | QUOTA_EXCEEDED | 스토리지 할당량 초과 | 관리자 문의 안내 |
| 413 | FILE_TOO_LARGE | 단일 파일 크기 초과 | 경고 |
| 415 | UNSUPPORTED_MEDIA_TYPE | 금지된 확장자 | 경고 |
| 423 | LOCKED | 파일 잠김 (v1.x, pessimistic) | 잠금 해제 안내 |
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
