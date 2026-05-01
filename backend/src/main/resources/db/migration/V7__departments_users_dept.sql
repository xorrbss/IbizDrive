-- Flyway V7: A16-track — Department 도메인 도입 + users.department_id ALTER.
--
-- 적용 범위 (docs/02 §2.2, ADR #36):
--   1. departments       — flat list. LTREE path 컬럼은 schema에 도입(v1.x 트리 쿼리 도입 시 재migration 회피),
--                          application 레벨에서는 직속 매칭만 사용 (KISS, A16는 dept share resolution이 직속만).
--   2. users.department_id — nullable FK. 기존 row는 NULL로 채워짐. seed 데이터는 별도 운영 트랙.
--
-- 핵심 결정 (CLAUDE.md §3 원칙 6 — DB 제약이 진실):
--   - departments soft-delete 컬럼 보유(deleted_at). 활성 row index는 partial.
--   - departments.parent_id FK self-reference (조직도 트리 — application 미사용 v1.x).
--   - users.department_id partial index (is_active=TRUE AND department_id IS NOT NULL) — 권한 평가 SQL의
--     dept resolution path가 활성 + 직속 dept user를 fast-lookup.
--
-- 권한 매트릭스 영향 (ADR #36):
--   - PermissionRepository.findEffective SQL이 본 컬럼을 사용해 dept subject 매칭 분기 추가 (A16.2).
--
-- audit_log 정책 무영향: V4 REVOKE/GRANT 정책은 audit_log에만 적용. 본 마이그레이션이 audit_log 권한을
-- 건드리지 않음 → A2 회귀 가드 (SQLState 42501) 보존.

CREATE EXTENSION IF NOT EXISTS ltree;

-- ============================================================
-- 2.2 departments
-- ============================================================

CREATE TABLE departments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    parent_id   UUID REFERENCES departments(id),
    path        LTREE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ
);

CREATE INDEX idx_departments_path ON departments USING GIST (path);
CREATE INDEX idx_departments_name ON departments(name) WHERE deleted_at IS NULL;

-- ============================================================
-- users.department_id (V2 deferred 컬럼 활성화)
-- ============================================================

ALTER TABLE users
    ADD COLUMN department_id UUID REFERENCES departments(id);

CREATE INDEX idx_users_department
    ON users(department_id)
    WHERE is_active = TRUE AND department_id IS NOT NULL;

-- ============================================================
-- 권한 정책 (V4 baseline 호환 — app_user 표준 CRUD)
-- ============================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_user') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON departments TO app_user;
    END IF;
END
$$;
