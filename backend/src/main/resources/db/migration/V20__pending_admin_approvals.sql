-- =============================================================================
-- V20 — pending_admin_approvals
-- =============================================================================
-- Generic dual-approval framework — ADR #47 / docs/02 §2.11 / docs/04 §16.
--
-- Phase 1 (본 PR): 데이터 레이어만 — table + indexes + CHECK 제약 + entity + repository.
-- Service / controller / audit emit / expiration cron / per-action hook (role 변경 / trash purge /
-- retention 변경)는 Phase 2 이후 별도 PR.
--
-- Tier 0 action_type:
--   - role_change         payload: {userId, fromRole, toRole, reason}
--   - trash_purge         payload: {type: 'file'|'folder', ids: [UUID...], reason?}
--   - retention_change    payload: {fromDays, toDays, reason}
-- Tier 1+ reserved (legal_hold_release / cron_toggle / user_deactivate)는 동일 테이블 재사용.
--
-- State machine: REQUESTED → APPROVED|REJECTED|CANCELLED|EXPIRED. terminal 4종.
--
-- CHECK 제약 (DB 진실의 출처 — CLAUDE.md §3 원칙 6):
--   - status 5개 enum
--   - terminal (≠ REQUESTED)면 decided_at NOT NULL, REQUESTED면 NULL
--   - secondary_approver_id는 APPROVED/REJECTED일 때만 set (CANCELLED는 requested_by 본인, EXPIRED는 system)
-- =============================================================================

CREATE TABLE pending_admin_approvals (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  action_type           VARCHAR(40) NOT NULL,
  payload_json          JSONB NOT NULL,
  requested_by          UUID NOT NULL REFERENCES users(id),
  requested_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  status                VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
  secondary_approver_id UUID REFERENCES users(id),
  decided_at            TIMESTAMPTZ,
  decision_reason       TEXT,
  expires_at            TIMESTAMPTZ NOT NULL,

  CONSTRAINT chk_pending_approvals_status
    CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED')),
  -- terminal status면 decided_at NOT NULL, REQUESTED면 NULL
  CONSTRAINT chk_pending_approvals_decided_at_invariant
    CHECK ((decided_at IS NULL) = (status = 'REQUESTED')),
  -- secondary는 APPROVED/REJECTED일 때만 set
  CONSTRAINT chk_pending_approvals_secondary_invariant
    CHECK ((secondary_approver_id IS NOT NULL) = (status IN ('APPROVED', 'REJECTED')))
);

-- pending 목록 (admin 알림 페이지) — status='REQUESTED' partial index
CREATE INDEX idx_pending_approvals_requested
  ON pending_admin_approvals(action_type, status)
  WHERE status = 'REQUESTED';

-- 요청자 이력 (취소 가능 항목 조회)
CREATE INDEX idx_pending_approvals_by_requester
  ON pending_admin_approvals(requested_by, requested_at DESC);

-- expiration cron 후보 스캔 — partial index
CREATE INDEX idx_pending_approvals_expires
  ON pending_admin_approvals(expires_at)
  WHERE status = 'REQUESTED';

-- audit join 헬퍼 (decided 항목 history) — partial index
CREATE INDEX idx_pending_approvals_decided
  ON pending_admin_approvals(decided_at DESC)
  WHERE status IN ('APPROVED', 'REJECTED');

COMMENT ON TABLE pending_admin_approvals IS
  'Generic dual-approval framework. ADR #47 / docs/02 §2.11. Tier 0 action_type: role_change, trash_purge, retention_change. State machine: REQUESTED → APPROVED|REJECTED|CANCELLED|EXPIRED.';

COMMENT ON COLUMN pending_admin_approvals.action_type IS
  'role_change | trash_purge | retention_change | legal_hold_release | cron_toggle | user_deactivate';

COMMENT ON COLUMN pending_admin_approvals.payload_json IS
  'action-specific args, schema는 application-level validation. PostgreSQL JSONB.';

COMMENT ON COLUMN pending_admin_approvals.expires_at IS
  'requested_at + app.dual-approval.ttl-days (default 7). 만료 cron이 자동 EXPIRED transition.';

COMMENT ON COLUMN pending_admin_approvals.secondary_approver_id IS
  'APPROVED/REJECTED 시점 결정자. CANCELLED는 requested_by 본인, EXPIRED는 system이라 NULL.';
