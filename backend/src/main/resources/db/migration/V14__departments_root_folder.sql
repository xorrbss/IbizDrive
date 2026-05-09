-- Flyway V14: team-centric pivot — departments.root_folder_id + root-per-scope partial unique.
-- spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §1.3.
--
-- workspace(부서/팀) 생성 시 root folder 1개를 트랜잭션 안에서 같이 생성한다.
--   - departments.root_folder_id: 부서 row가 가리키는 root folder 참조
--     (teams.root_folder_id는 V12에서 이미 도입됨 — spec §1.1).
--
-- 동일 (scope_type, scope_id) 조합에 root folder는 단 하나만 존재해야 함.
--   - root folder = parent_id IS NULL + scope 컬럼 set
--   - partial unique index (parent_id IS NULL AND deleted_at IS NULL) — 활성 root만 강제
--   - soft-delete된 과거 root는 인덱스 제외 → workspace archive 후 재생성 시나리오 허용
--
-- root folder 이름 변경/삭제 차단(불변량)은 서비스 레이어에서 처리(spec §1.3).

ALTER TABLE departments
    ADD COLUMN root_folder_id UUID REFERENCES folders(id);

CREATE UNIQUE INDEX idx_folders_root_per_scope
    ON folders(scope_type, scope_id)
    WHERE parent_id IS NULL AND deleted_at IS NULL;
