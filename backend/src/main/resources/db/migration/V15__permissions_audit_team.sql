-- Flyway V15: team-centric pivot — permissions.subject_type / audit_log.target_type CHECK 확장.
-- spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §1.4, §1.5.
--
-- 1) permissions.subject_type: V5에서 정의된 4값 (user, department, role, everyone)에
--    'team'을 추가 → 팀 A의 폴더 리소스를 팀 B에게 grant 가능 (spec §1.4).
--    제약 이름은 V5의 permissions_subject_type_check 그대로 재사용.
--
-- 2) audit_log.target_type: V9가 마지막으로 정의한 8값 (file, folder, user, permission,
--    share, system, audit, department)에 'team'을 추가 → team.created / team.archived /
--    team.member.* 이벤트가 target_type='team'으로 기록될 수 있게 함 (spec §1.5).
--    제약 이름은 V9의 audit_log_target_type_check 그대로 재사용.
--
-- 본 마이그레이션은 audit_log 테이블의 GRANT/REVOKE 정책(V4)을 건드리지 않는다 →
-- A2 append-only 회귀 가드(SQLState 42501) 보존.
-- 기존 row와 호환 — 기존 enum 값은 모두 새 CHECK에도 그대로 포함.

ALTER TABLE permissions DROP CONSTRAINT permissions_subject_type_check;
ALTER TABLE permissions
    ADD CONSTRAINT permissions_subject_type_check
        CHECK (subject_type IN ('user', 'department', 'role', 'team', 'everyone'));

ALTER TABLE audit_log DROP CONSTRAINT audit_log_target_type_check;
ALTER TABLE audit_log
    ADD CONSTRAINT audit_log_target_type_check
        CHECK (target_type IN ('file', 'folder', 'user', 'permission', 'share',
                               'system', 'audit', 'department', 'team'));
