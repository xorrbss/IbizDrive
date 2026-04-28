-- Flyway V5: A3 folder/file domain foundation.
-- DB constraints are the source of truth for active sibling name uniqueness.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE folders (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id          UUID REFERENCES folders(id) ON DELETE RESTRICT,
    name               VARCHAR(255) NOT NULL,
    normalized_name    VARCHAR(255) NOT NULL,
    slug               VARCHAR(255) NOT NULL,
    owner_id           UUID NOT NULL REFERENCES users(id),
    audit_level        VARCHAR(20) NOT NULL DEFAULT 'standard',
    deleted_at         TIMESTAMPTZ,
    purge_after        TIMESTAMPTZ,
    original_parent_id UUID REFERENCES folders(id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT folders_audit_level_check CHECK (audit_level IN ('standard', 'strict')),
    CONSTRAINT folders_deleted_purge_check CHECK ((deleted_at IS NULL) = (purge_after IS NULL))
);

CREATE UNIQUE INDEX idx_folders_unique_name
    ON folders (COALESCE(parent_id, '00000000-0000-0000-0000-000000000000'::uuid), normalized_name)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_folders_parent ON folders(parent_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_folders_purge ON folders(purge_after) WHERE deleted_at IS NOT NULL;

CREATE TABLE files (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    folder_id          UUID NOT NULL REFERENCES folders(id) ON DELETE RESTRICT,
    name               VARCHAR(500) NOT NULL,
    normalized_name    VARCHAR(500) NOT NULL,
    current_version_id UUID,
    owner_id           UUID NOT NULL REFERENCES users(id),
    size_bytes         BIGINT NOT NULL,
    mime_type          VARCHAR(255),
    deleted_at         TIMESTAMPTZ,
    purge_after        TIMESTAMPTZ,
    original_folder_id UUID REFERENCES folders(id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT files_deleted_purge_check CHECK ((deleted_at IS NULL) = (purge_after IS NULL)),
    CONSTRAINT files_size_check CHECK (size_bytes >= 0)
);

CREATE UNIQUE INDEX idx_files_unique_name
    ON files (folder_id, normalized_name)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_files_folder ON files(folder_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_files_owner ON files(owner_id);
CREATE INDEX idx_files_purge ON files(purge_after) WHERE deleted_at IS NOT NULL;

CREATE TABLE file_versions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_id         UUID NOT NULL REFERENCES files(id) ON DELETE RESTRICT,
    version_number  INT NOT NULL,
    storage_key     UUID NOT NULL UNIQUE,
    size_bytes      BIGINT NOT NULL,
    checksum_sha256 CHAR(64) NOT NULL,
    mime_type       VARCHAR(255),
    scan_status     VARCHAR(20) NOT NULL DEFAULT 'pending',
    scan_result     JSONB,
    uploaded_by     UUID NOT NULL REFERENCES users(id),
    uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    comment         VARCHAR(500),

    CONSTRAINT file_versions_scan_status_check CHECK (scan_status IN ('pending', 'clean', 'infected', 'error')),
    CONSTRAINT file_versions_version_number_check CHECK (version_number > 0),
    CONSTRAINT file_versions_unique_number UNIQUE (file_id, version_number)
);

ALTER TABLE files
    ADD CONSTRAINT fk_files_current_version
    FOREIGN KEY (current_version_id) REFERENCES file_versions(id) DEFERRABLE INITIALLY DEFERRED;

CREATE INDEX idx_versions_file ON file_versions(file_id, version_number DESC);
CREATE INDEX idx_versions_scan_pending ON file_versions(scan_status) WHERE scan_status = 'pending';
