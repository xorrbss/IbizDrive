-- Flyway V4: audit_log append-only 강제 (ADR #25, docs/03 §4.4).
--
-- 전략: PostgreSQL role 분리.
--   - app_user      : 앱 런타임. audit_log INSERT/SELECT only.
--   - audit_admin   : read API용 관리자 connection. SELECT only.
--   - db_superuser  : 마이그레이션·파티션 관리. (Flyway가 사용)
--
-- REVOKE만으로 boundary 형성 — 트리거 추가 안 함 (superuser는 트리거도 우회 가능,
-- KISS 원칙). 본 정책은 RED 테스트(AuditLogAppendOnlyTest)가 SQLState 42501로 증명.
--
-- 본 마이그레이션은 idempotent: 기존 환경에서 재실행 시 role 재생성 안 함.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_user') THEN
        CREATE ROLE app_user LOGIN PASSWORD 'app_pass';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'audit_admin') THEN
        CREATE ROLE audit_admin LOGIN PASSWORD 'audit_admin_pass';
    END IF;
END
$$;

-- audit_log 권한 정책
REVOKE ALL ON audit_log         FROM app_user;
GRANT  INSERT, SELECT ON audit_log TO   app_user;
GRANT  USAGE, SELECT ON SEQUENCE audit_log_id_seq TO app_user;

REVOKE ALL ON audit_log         FROM audit_admin;
GRANT  SELECT ON audit_log      TO   audit_admin;

-- 다른 도메인 테이블은 app_user에 표준 권한 (V1/V2 baseline 유지)
GRANT  SELECT, INSERT, UPDATE, DELETE ON users TO app_user;
GRANT  USAGE ON SCHEMA public  TO app_user, audit_admin;

-- Spring Session 테이블도 app_user가 운영
GRANT  SELECT, INSERT, UPDATE, DELETE ON SPRING_SESSION             TO app_user;
GRANT  SELECT, INSERT, UPDATE, DELETE ON SPRING_SESSION_ATTRIBUTES  TO app_user;
