-- ADR #47 Phase 3d — pending_admin_approvals expiration cron.
-- 기존 4종(purge.expired/share.expire/permission.expire/storage.orphan.cleanup) 정합.
-- default false — 운영 진입 시 /admin/system 토글로 활성화.
INSERT INTO cron_policy (key, enabled) VALUES
    ('admin.approval.expire', false);
