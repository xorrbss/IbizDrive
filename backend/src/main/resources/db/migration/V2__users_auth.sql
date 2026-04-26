-- Flyway V2: A1 인증을 위한 users 컬럼 보강.
-- docs/02 §2.1 + docs/03 §2.6 (lockout) + ADR #19 (BCrypt) + ADR #21 (관리자 초대 → 첫 로그인 PW 변경 강제).
--
-- 추가 컬럼 (5개):
--   role                  : MEMBER | AUDITOR | ADMIN  (docs/03 §3.2.5)
--   is_active             : 계정 활성화 플래그 (관리자 비활성화)
--   last_login_at         : audit + 비활성 계정 식별
--   locked_at             : 관리자 수동 잠금 (ADR #20). NULL이면 미잠금
--   must_change_password  : 첫 로그인 PW 변경 강제 (ADR #21)
--
-- 본 마이그레이션은 V1 stub의 display_name·password_hash를 그대로 유지한다
-- (드리프트 결정 — V1 stub 보존 + docs/02 §2.1 정렬).
-- department_id / storage_quota / storage_used / updated_at 은 후속 phase에서 추가.

ALTER TABLE users
    ADD COLUMN role                 VARCHAR(50) NOT NULL DEFAULT 'MEMBER',
    ADD COLUMN is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN last_login_at        TIMESTAMPTZ,
    ADD COLUMN locked_at            TIMESTAMPTZ,
    ADD COLUMN must_change_password BOOLEAN     NOT NULL DEFAULT FALSE;

ALTER TABLE users
    ADD CONSTRAINT users_role_check
    CHECK (role IN ('MEMBER', 'AUDITOR', 'ADMIN'));
