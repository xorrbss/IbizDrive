-- Flyway V24: users.updated_at 활성화 (docs/02 §2.1 spec drift 정정).
--
-- 배경:
--   docs/02 §2.1 schema는 `updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()` 명시했으나
--   V2__users_auth.sql line 13 주석에서 "department_id / storage_quota / storage_used /
--   updated_at은 후속 phase에서 추가"로 deferred. department_id는 V7, storage_*는 V18에서
--   활성화되었으나 updated_at은 phase 미할당 상태로 잔존. 본 마이그레이션이 마지막 deferred
--   컬럼을 활성화하여 §2.1 schema 명세와 코드를 정합.
--
-- 의미:
--   - INSERT 시점: Hibernate @UpdateTimestamp가 자동 set (User.java entity)
--   - UPDATE 시점: Hibernate가 자동 갱신 → audit_log와 cross-reference 가능
--   - 본 컬럼이 없던 상태에서는 모든 user의 createdAt만 유효 = updatedAt 추정 불가
--
-- 기존 row 처리: DEFAULT NOW()로 backfill — created_at과 약간 차이 발생하지만 의미 손실 X
-- (운영 시점에서 본 마이그레이션 시각이 모든 기존 user의 "마지막 변경 시각"이 됨, 의미적 안전).
-- 별도 backfill `UPDATE users SET updated_at = created_at`도 검토했으나, 이력 누락 vs 변경
-- 발생 명시 trade-off에서 DEFAULT NOW()가 운영 명확성 우위.
--
-- V4 REVOKE 정책과 충돌 없음: users 테이블에는 REVOKE 미적용. app_user의 user mutation은
-- Spring Security를 통과한 service layer에서만 가능 (DB 직접 UPDATE 차단은 별도 정책).

ALTER TABLE users
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

COMMENT ON COLUMN users.updated_at IS '마지막 변경 시각. Hibernate @UpdateTimestamp가 자동 갱신 (User entity). docs/02 §2.1.';
