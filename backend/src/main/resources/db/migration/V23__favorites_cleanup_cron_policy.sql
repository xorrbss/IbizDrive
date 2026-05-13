-- v1.x — favorites orphan cleanup cron seed.
-- V22 favorites 도입 후, file/folder가 hard-purge되면 favorites row가 orphan으로 남는다.
-- 본 cron은 file/folder 양쪽 active+trashed에도 존재하지 않는 resource_id를 참조하는
-- favorites row를 일괄 삭제하여 DB hygiene을 유지한다. default false — /admin/system 토글.
INSERT INTO cron_policy (key, enabled) VALUES
    ('favorites.cleanup', false);
