-- Wave 2 closure 후속(admin-cron-policy-toggle): cron 4종 enabled 토글을 DB로 영구 저장.
-- application.yml의 app.*.enabled는 시드 후 cron 동작에 영향 없음 (cleanup은 v1.x).
CREATE TABLE cron_policy (
    key          VARCHAR(64)  PRIMARY KEY,
    enabled      BOOLEAN      NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by   UUID         REFERENCES users(id) ON DELETE SET NULL
);

-- 4종 시드. updated_by=NULL은 system seed 표식.
INSERT INTO cron_policy (key, enabled) VALUES
    ('purge.expired',          false),
    ('share.expire',           false),
    ('permission.expire',      false),
    ('storage.orphan.cleanup', false);
