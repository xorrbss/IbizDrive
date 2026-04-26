-- Flyway V3: audit_log 테이블 (docs/02 §2.8, ADR #24).
--
-- append-only 강제는 V4가 담당 (DB role 분리 + REVOKE).
-- 본 마이그레이션은 스키마/제약/인덱스만 정의한다.
--
-- target_type CHECK는 docs/02 §2.8 + frontend AuditResourceType(7개) 동기.
-- frontend가 'audit' 추가(audit.exported 자기참조 이벤트) → 본 enum도 7개.
--
-- 파티셔닝(docs/02 §9.4)은 MVP 단일 테이블로 진입. 월별 파티션 SQL은
-- 1년차 끝나기 전 별도 마이그레이션으로 cutover (ADR #24 본문).

CREATE TABLE audit_log (
    id              BIGSERIAL    PRIMARY KEY,
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    actor_id        UUID         REFERENCES users(id),
    actor_ip        INET,
    user_agent      TEXT,
    event_type      VARCHAR(50)  NOT NULL,
    target_type     VARCHAR(20)  NOT NULL,
    target_id       UUID,
    before_state    JSONB,
    after_state     JSONB,
    metadata        JSONB,

    CONSTRAINT audit_log_target_type_check
        CHECK (target_type IN ('file', 'folder', 'user', 'permission', 'share', 'system', 'audit'))
);

-- 조회 패턴 (docs/02 §2.8):
--   1. 최신순 페이지네이션 → occurred_at DESC
--   2. actor 별 활동 → (actor_id, occurred_at DESC)
--   3. 리소스 별 이력 → (target_type, target_id, occurred_at DESC)
--   4. 이벤트 타입 별 → (event_type, occurred_at DESC)
CREATE INDEX idx_audit_occurred_at ON audit_log(occurred_at DESC);
CREATE INDEX idx_audit_actor       ON audit_log(actor_id, occurred_at DESC);
CREATE INDEX idx_audit_target      ON audit_log(target_type, target_id, occurred_at DESC);
CREATE INDEX idx_audit_event       ON audit_log(event_type, occurred_at DESC);
