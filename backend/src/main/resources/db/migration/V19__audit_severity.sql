-- Flyway V19: audit_log.severity 컬럼 (P3 — Audit severity backend, docs/03 §4).
--
-- 도입 이유:
--   frontend 의 임시 fallback helper(`frontend/src/lib/admin/auditSeverity.ts`,
--   `severityOf()`)가 SeverityTab UI 에 사용 중이었으나, 분류 결정은 backend 에 있어야
--   감사 진실성·alert 연동·legal hold 정책과 일관된다 (docs/03 §4).
--
-- 매핑 단일 진실: Java `com.ibizdrive.audit.AuditSeverityMapper.of(AuditEventType)`.
--   본 backfill 의 CASE 표는 mapper 와 동치이며, AuditSeverityMapperTest 가 양쪽이 동일
--   결과를 내는지를 검증한다.
--
-- 분류 기준:
--   - danger: 외부 노출 가능성/정책 위반 가능성 (즉시 검토). 본 PR 에선 share.created 1건.
--   - warn  : 외부 도메인 공유/권한 회수/권한 만료/대량 삭제·purge/관리자 정책 변경 등.
--   - info  : 일상 운영 이벤트 (기본값).
--
-- "user.login.failed" 의 단건은 warn — 연속 실패 시 danger 승격은 별도 alerting 트랙
-- (out of scope, docs/03 §4 TODO).
--
-- 마이그레이션 단계:
--   1) NULL 허용 컬럼 ADD (기존 row 가 있어도 INSTANT 가능).
--   2) CASE backfill (frontend SEVERITY_MAP 와 동치).
--   3) NOT NULL + DEFAULT 'info'.
--   4) CHECK 제약.
--   5) 부분 인덱스 (severity != 'info') — info 가 압도적 다수일 것이라 hot path 만 인덱스화.
--
-- V4 REVOKE 정책과 충돌 없음: app_user 에는 INSERT/SELECT 만 부여되어 있고 본 DDL/UPDATE
-- 는 Flyway superuser connection 으로 실행된다 (V4 주석 참조).

ALTER TABLE audit_log ADD COLUMN severity VARCHAR(10);

UPDATE audit_log
SET severity = CASE event_type
    -- danger
    WHEN 'share.created'              THEN 'danger'
    -- warn
    WHEN 'user.login.failed'          THEN 'warn'
    WHEN 'permission.revoked'         THEN 'warn'
    WHEN 'permission.expired'         THEN 'warn'
    WHEN 'share.revoked'              THEN 'warn'
    WHEN 'share.expired'              THEN 'warn'
    WHEN 'admin.legal_hold.placed'    THEN 'warn'
    WHEN 'admin.legal_hold.released'  THEN 'warn'
    WHEN 'admin.user.deactivated'     THEN 'warn'
    WHEN 'admin.role.changed'         THEN 'warn'
    WHEN 'admin.cron.toggled'         THEN 'warn'
    WHEN 'file.deleted'               THEN 'warn'
    WHEN 'file.purged'                THEN 'warn'
    WHEN 'folder.deleted'             THEN 'warn'
    WHEN 'folder.purged'              THEN 'warn'
    WHEN 'team.archived'              THEN 'warn'
    WHEN 'system.purge.executed'      THEN 'warn'
    -- info default
    ELSE 'info'
END;

ALTER TABLE audit_log ALTER COLUMN severity SET NOT NULL;
ALTER TABLE audit_log ALTER COLUMN severity SET DEFAULT 'info';

ALTER TABLE audit_log ADD CONSTRAINT audit_log_severity_check
    CHECK (severity IN ('info', 'warn', 'danger'));

-- 부분 인덱스 — info 가 압도적 다수일 것이라 danger/warn 의 dashboard/필터 조회만 인덱스화.
-- 본 인덱스는 (severity, occurred_at DESC) 정렬 페이지네이션을 커버한다.
CREATE INDEX idx_audit_severity_occurred ON audit_log(severity, occurred_at DESC)
    WHERE severity != 'info';
