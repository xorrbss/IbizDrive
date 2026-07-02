-- Flyway V25: app_user GRANT 캐치업 + audit_log append-only 재강제 (ADR #25/#49, docs/03 §4.4).
--
-- 배경: V4가 app_user role(앱 런타임 계정)을 도입했지만, 이후 신규 테이블에는 V5/V6/V7만
-- GRANT를 추가했다. V8+ 테이블(password_reset_tokens, cron_policy, teams, team_members,
-- trash_policy, pending_admin_approvals, favorites 등)에는 GRANT가 없어, BETA-RELEASE §2.4
-- 게이트("app_user만 application 사용")를 이행하는 순간 앱이 42501(insufficient_privilege)로
-- 실패한다 — 즉 append-only 전제인 app_user 런타임 전환이 사실상 불가능한 상태였다.
--
-- 전략: 스키마 전체 blanket GRANT 후 audit_log에 대해서만 UPDATE/DELETE/TRUNCATE를 즉시
-- 재-REVOKE — V4의 append-only boundary(ADR #25)를 최종 상태로 보존한다. 테이블 개별 나열
-- 대신 blanket을 쓰는 이유: 누락이 곧 런타임 장애인 목록을 손으로 유지하는 것이 더 위험 (KISS).
-- 이후 신규 테이블 마이그레이션은 기존 관례(V5~V7)대로 개별 GRANT를 포함할 것.
--
-- 본 마이그레이션은 idempotent (GRANT/REVOKE 재실행 무해).

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO app_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_user;

-- audit_log append-only 재강제 — blanket GRANT가 부여한 UPDATE/DELETE 제거.
-- AuditLogAppendOnlyTest가 V25 적용 후에도 SQLState 42501을 회귀 검증한다.
REVOKE UPDATE, DELETE, TRUNCATE ON audit_log FROM app_user;
