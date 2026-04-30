-- Flyway V6: A10 — shares 테이블 (docs/02 §2.7, ADR #34).
--
-- 적용 범위:
--   1. shares — 파일/폴더 공유 메타 (message / expiresAt / revoke 추적). permissions row와 1:1 연결.
--
-- SRP 분리 (ADR #34):
--   - permissions.preset = "공유받은 사람의 권한 셋" (V5 4값: read|upload|edit|admin).
--   - shares.message / expires_at / revoked_at = "공유 행위 자체"의 메타.
--   - 권한 row에 메타 컬럼 추가 대안은 거부 (책임 혼합).
--
-- 핵심 제약:
--   - permission_id FK ON DELETE CASCADE — share row 단독 lifecycle은 revoke (revoked_at set + permission row 별도 delete) 경로 사용.
--   - file_id / folder_id 양립 (XOR CHECK) — MVP endpoint는 file 공유 한정 (`POST /api/files/:id/share`),
--     folder 공유 endpoint는 별도 트랙 (ADR #34 backlog).
--   - expires_at: NULL = 무기한. SHARE_EXPIRED audit cron은 별도 트랙 (deferred).
--
-- audit_log REVOKE 정책 무영향: V6은 audit_log를 건드리지 않음 → A2 회귀 가드 보존.

-- ============================================================
-- 2.7 shares
-- ============================================================

CREATE TABLE shares (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_id         UUID REFERENCES files(id)    ON DELETE CASCADE,
    folder_id       UUID REFERENCES folders(id)  ON DELETE CASCADE,
    permission_id   UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    shared_by       UUID NOT NULL REFERENCES users(id),
    message         TEXT,                                  -- max 1000자 (controller 검증)
    expires_at      TIMESTAMPTZ,                           -- NULL = 무기한
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at      TIMESTAMPTZ,
    revoked_by      UUID REFERENCES users(id),

    CONSTRAINT shares_target_xor_check
        CHECK ((file_id IS NOT NULL)::int + (folder_id IS NOT NULL)::int = 1),
    CONSTRAINT shares_revoked_pair_check
        CHECK ((revoked_at IS NULL) = (revoked_by IS NULL))
);

-- /api/shares/by-me 쿼리 (shared_by + cursor on created_at DESC, active만).
CREATE INDEX idx_shares_active     ON shares(shared_by, created_at DESC) WHERE revoked_at IS NULL;

-- /api/shares/with-me JOIN — permissions.id 매칭 fast path.
CREATE INDEX idx_shares_permission ON shares(permission_id);

-- ============================================================
-- 권한 정책 (V4/V5 baseline 호환)
--
-- V4 audit_log REVOKE는 audit_log 한정. V6은 audit_log 권한 미수정 → A2 회귀 가드 무영향.
-- ============================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_user') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON shares TO app_user;
    END IF;
END
$$;
