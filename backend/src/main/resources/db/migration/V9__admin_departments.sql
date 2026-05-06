-- Flyway V9: admin-department-crud (Wave 2 — T4)
--
-- 본 마이그레이션은 두 가지를 박는다.
--
-- 1) departments(name) partial unique index
--    - admin이 부서를 생성할 때 동일 이름 중복을 DB 레벨에서 차단 (CLAUDE.md §3 원칙 6).
--    - soft-delete된 row와는 충돌하지 않도록 `WHERE deleted_at IS NULL` partial.
--    - V7가 만든 `idx_departments_name`(non-unique)와 별도 — 검색용 + 충돌검사용을 분리 유지.
--
-- 2) audit_log target_type CHECK 갱신 — 'department' 추가 (V3의 7값 → 8값)
--    - admin-department-crud가 audit_log INSERT 시 target_type='department'를 사용.
--    - V3의 CHECK가 7값으로 막혀 있으므로 ALTER로 추가.
--    - V3 row와 호환 — 기존 row의 target_type 값(7값)은 모두 새 CHECK에도 포함.
--    - frontend `types/audit.ts` `AuditResourceType` mirror도 'department' 동기 (계약).
--
-- 본 마이그레이션은 audit_log 테이블 자체의 GRANT/REVOKE 정책(V4)을 건드리지 않는다 →
-- A2 회귀 가드(SQLState 42501) 보존.

CREATE UNIQUE INDEX idx_departments_name_active
    ON departments(name)
    WHERE deleted_at IS NULL;

ALTER TABLE audit_log DROP CONSTRAINT audit_log_target_type_check;
ALTER TABLE audit_log ADD CONSTRAINT audit_log_target_type_check
    CHECK (target_type IN ('file', 'folder', 'user', 'permission', 'share', 'system', 'audit', 'department'));
