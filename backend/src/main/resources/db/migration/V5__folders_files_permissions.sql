-- Flyway V5: A4-data 트랙 — folder/file 도메인 + resource-level permissions.
--
-- 적용 범위 (docs/02 §2.3~§2.6, ADR #27/#28/#29):
--   1. folders          — 트리 구조 + soft delete + 휴지통 복원
--   2. files            — folder 소속 + soft delete + current_version_id (FK는 file_versions 생성 후 ALTER)
--   3. file_versions    — A4 schema-only 도입. entity/repository/CRUD endpoint는 A5 이월 (ADR #29)
--   4. permissions      — resource-level grant (subject × resource × preset)
--
-- 핵심 제약 (CLAUDE.md §3 원칙 6 — DB 제약이 진실):
--   - folders / files / permissions: UNIQUE INDEX WHERE deleted_at IS NULL (휴지통 제외 충돌 검사)
--   - folders / permissions: 부모 또는 subject NULL을 ZERO_UUID로 COALESCE (root parent / everyone subject) — Postgres NULL distinct 우회 차단 (ADR #27, docs/02 §2.3 본문 정합)
--   - files.current_version_id FK는 DEFERRABLE INITIALLY DEFERRED — files INSERT와 file_versions INSERT를 동일 트랜잭션 내 임의 순서로 가능하게 함 (ADR #29 보장사항 (b))
--   - file_versions.version_number > 0 + (file_id, version_number) UNIQUE — 이력 단조 증가 (A5에서 사용)
--   - permissions.preset 단일 컬럼 — deny semantics는 v1.x 이월 (ADR #28)
--
-- audit_log 정책 무영향: V4 REVOKE/GRANT 정책은 audit_log에만 적용. 본 마이그레이션이 audit_log 권한을 건드리지 않음 → A2 회귀 가드 (SQLState 42501) 보존.
--
-- pgcrypto 확장은 gen_random_uuid() 호출에 필요. V1~V4가 명시적으로 enable하지 않았으므로 idempotent 가드 추가.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================
-- 2.3 folders
-- ============================================================

CREATE TABLE folders (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id          UUID REFERENCES folders(id) ON DELETE RESTRICT,
    name               VARCHAR(255) NOT NULL,
    normalized_name    VARCHAR(255) NOT NULL,
    slug               VARCHAR(255) NOT NULL,
    owner_id           UUID NOT NULL REFERENCES users(id),
    audit_level        VARCHAR(20)  NOT NULL DEFAULT 'standard',
    deleted_at         TIMESTAMPTZ,
    purge_after        TIMESTAMPTZ,
    original_parent_id UUID REFERENCES folders(id),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT folders_audit_level_check
        CHECK (audit_level IN ('standard', 'strict')),
    CONSTRAINT folders_deleted_purge_check
        CHECK ((deleted_at IS NULL) = (purge_after IS NULL))
);

-- root parent (parent_id IS NULL)는 ZERO_UUID로 치환 — Postgres가 NULL을 distinct 취급해
-- 동일 root 안에서 같은 이름이 중복 생성되는 것을 차단 (ADR #27, docs/02 §2.3 본문 정합).
CREATE UNIQUE INDEX idx_folders_unique_name
    ON folders (COALESCE(parent_id, '00000000-0000-0000-0000-000000000000'::uuid), normalized_name)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_folders_parent ON folders(parent_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_folders_purge  ON folders(purge_after) WHERE deleted_at IS NOT NULL;

-- ============================================================
-- 2.4 files
-- ============================================================

CREATE TABLE files (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    folder_id          UUID NOT NULL REFERENCES folders(id) ON DELETE RESTRICT,
    name               VARCHAR(500) NOT NULL,
    normalized_name    VARCHAR(500) NOT NULL,
    current_version_id UUID,                              -- FK는 file_versions 생성 후 ALTER (ADR #29 (b))
    owner_id           UUID NOT NULL REFERENCES users(id),
    size_bytes         BIGINT       NOT NULL,
    mime_type          VARCHAR(255),
    deleted_at         TIMESTAMPTZ,
    purge_after        TIMESTAMPTZ,
    original_folder_id UUID REFERENCES folders(id),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT files_deleted_purge_check
        CHECK ((deleted_at IS NULL) = (purge_after IS NULL)),
    CONSTRAINT files_size_check
        CHECK (size_bytes >= 0)
);

CREATE UNIQUE INDEX idx_files_unique_name
    ON files (folder_id, normalized_name)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_files_folder ON files(folder_id)  WHERE deleted_at IS NULL;
CREATE INDEX idx_files_owner  ON files(owner_id);
CREATE INDEX idx_files_purge  ON files(purge_after) WHERE deleted_at IS NOT NULL;

-- ============================================================
-- 2.5 file_versions  (A4 schema-only, ADR #29)
-- ============================================================

CREATE TABLE file_versions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_id         UUID         NOT NULL REFERENCES files(id) ON DELETE RESTRICT,
    version_number  INT          NOT NULL,
    storage_key     UUID         NOT NULL UNIQUE,    -- S3 객체 키 = UUID. 원본 파일명 절대 미저장 (CLAUDE.md §3 원칙 9)
    size_bytes      BIGINT       NOT NULL,
    checksum_sha256 CHAR(64)     NOT NULL,
    mime_type       VARCHAR(255),
    scan_status     VARCHAR(20)  NOT NULL DEFAULT 'pending',
    scan_result     JSONB,
    uploaded_by     UUID         NOT NULL REFERENCES users(id),
    uploaded_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    comment         VARCHAR(500),

    CONSTRAINT file_versions_scan_status_check
        CHECK (scan_status IN ('pending', 'clean', 'infected', 'error')),
    CONSTRAINT file_versions_version_number_check
        CHECK (version_number > 0),
    CONSTRAINT file_versions_unique_number
        UNIQUE (file_id, version_number)
);

ALTER TABLE files
    ADD CONSTRAINT fk_files_current_version
    FOREIGN KEY (current_version_id) REFERENCES file_versions(id) DEFERRABLE INITIALLY DEFERRED;

CREATE INDEX idx_versions_file         ON file_versions(file_id, version_number DESC);
CREATE INDEX idx_versions_scan_pending ON file_versions(scan_status) WHERE scan_status = 'pending';

-- ============================================================
-- 2.6 permissions
-- ============================================================

CREATE TABLE permissions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resource_type   VARCHAR(20)  NOT NULL,             -- folder|file
    resource_id     UUID         NOT NULL,
    subject_type    VARCHAR(20)  NOT NULL,             -- user|department|role|everyone
    subject_id      UUID,                              -- subject_type=everyone이면 NULL
    preset          VARCHAR(20)  NOT NULL,             -- read|upload|edit|admin (preset 단일 — deny v1.x 이월, ADR #28)
    granted_by      UUID         NOT NULL REFERENCES users(id),
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT permissions_resource_type_check
        CHECK (resource_type IN ('folder', 'file')),
    CONSTRAINT permissions_subject_type_check
        CHECK (subject_type IN ('user', 'department', 'role', 'everyone')),
    CONSTRAINT permissions_preset_check
        CHECK (preset IN ('read', 'upload', 'edit', 'admin')),
    CONSTRAINT permissions_subject_id_everyone_check
        CHECK ((subject_type = 'everyone') = (subject_id IS NULL))
);

-- 동일 (resource, subject) 중복 grant 금지 — subject_id NULL은 ZERO_UUID로 COALESCE.
CREATE UNIQUE INDEX idx_permissions_unique
    ON permissions (
        resource_type,
        resource_id,
        subject_type,
        COALESCE(subject_id, '00000000-0000-0000-0000-000000000000'::uuid)
    );

CREATE INDEX idx_permissions_subject  ON permissions(subject_type, subject_id);
CREATE INDEX idx_permissions_resource ON permissions(resource_type, resource_id);
CREATE INDEX idx_permissions_expires  ON permissions(expires_at) WHERE expires_at IS NOT NULL;

-- ============================================================
-- 권한 정책 (V4 baseline 호환 — app_user 표준 CRUD)
--
-- audit_log REVOKE 정책은 V4가 audit_log 한정으로 적용 — 본 마이그레이션이 audit_log를
-- 건드리지 않음 → A2 append-only 회귀 가드(SQLState 42501) 무영향.
-- ============================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_user') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON folders        TO app_user;
        GRANT SELECT, INSERT, UPDATE, DELETE ON files          TO app_user;
        GRANT SELECT, INSERT, UPDATE, DELETE ON file_versions  TO app_user;
        GRANT SELECT, INSERT, UPDATE, DELETE ON permissions    TO app_user;
    END IF;
END
$$;
