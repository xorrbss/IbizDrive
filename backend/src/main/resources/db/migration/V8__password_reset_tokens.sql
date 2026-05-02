-- Flyway V8: a1.5 비밀번호 재설정 토큰.
-- docs/03 §2.7 (a1.5에서 작성).
--
-- token_hash : 평문 토큰의 SHA-256 hex (64자). 평문은 이메일/링크에만 노출되며 DB에는 해시만.
-- expires_at : 발급 시각 + 30분 (TTL).
-- used_at    : 1회용. 사용 후 마킹되어 재사용 차단. NULL이면 미사용.
--
-- 인덱스:
--   token_hash UNIQUE   — lookup + 충돌 방지
--   user_id             — 사용자별 토큰 조회 (다중 발급 시)
--   active partial      — 만료/사용 미완료 토큰 카운트 (rate-limit/감사 보조)

CREATE TABLE password_reset_tokens (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_password_reset_tokens_user_id
    ON password_reset_tokens (user_id);

CREATE INDEX idx_password_reset_tokens_active
    ON password_reset_tokens (expires_at)
    WHERE used_at IS NULL;
