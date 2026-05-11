-- V17 — trash-retention-mutation Phase B
--
-- 휴지통 보존 정책 single-row 테이블. 기존 yml 부팅 바인딩(`TrashRetentionProperties`)을
-- DB-backed runtime mutation으로 이관. yml은 V17 row 부재 시 app 부팅 시 idempotent
-- INSERT의 default value source로 잔존 (운영자 yml override 이력 보존).
--
-- - id=1 강제(다중 row 차단), CHECK BETWEEN 7 AND 90 (docs/04 §8.1 spec과 일치).
-- - updated_by ON DELETE SET NULL: 운영자 계정 hard-delete 시 정책 row는 보존
--   (자체 audit_log가 actor 보존).
-- - 이력은 audit_log `RETENTION_POLICY_CHANGED` event(`docs/03 §4.1`)가 before/after 보존.
--   별도 trash_policy_history 미도입 (YAGNI).

CREATE TABLE trash_policy (
    id              SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    retention_days  INT      NOT NULL CHECK (retention_days BETWEEN 7 AND 90),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by      UUID REFERENCES users(id) ON DELETE SET NULL
);

COMMENT ON TABLE  trash_policy IS '휴지통 보존 정책 single-row (id=1). DB-backed runtime mutation source.';
COMMENT ON COLUMN trash_policy.retention_days IS 'soft-delete 시점부터 hard purge까지 일수 (CHECK 7..90, docs/04 §8.1).';
COMMENT ON COLUMN trash_policy.updated_by  IS '마지막 변경 actor (운영자 hard-delete 시 NULL — 정책 row 자체는 보존).';

-- 초기 row INSERT는 V17 migration이 직접 하지 않는다 — app 부팅 시
-- TrashPolicyService가 yml `app.trash.retention.days` 값을 idempotent INSERT한다.
-- 운영자 yml override 이력 보존 + V17 적용 직후 service start까지의 짧은 race window는
-- FileMutationService/FolderMutationService가 service.getRetentionDays() 호출 전에
-- service @PostConstruct가 INSERT를 끝내므로 안전.
