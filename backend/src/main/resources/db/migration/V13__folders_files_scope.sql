-- Flyway V13: team-centric pivot — folders/files workspace scope columns.
-- spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §1.2.
--
-- 모든 folder/file은 하나의 워크스페이스(부서 또는 팀) 안에 귀속.
--   - scope_type: 'department' (V7~V9 도입된 부서 트리) | 'team' (V12 도입된 팀)
--   - scope_id  : 해당 부서/팀 row의 PK (FK 미설정 — V14 이후 root-per-scope unique 강제로 정합 보장)
--
-- Scenario A (green-field 컷오버, spec §6): 기존 데이터 wipe 전제. NOT NULL + CHECK 즉시 적용.
--   - Testcontainers는 매 테스트마다 fresh DB → migration 통과.
--   - 로컬 PG에 기존 row가 있다면 본 마이그레이션 적용 전 wipe 필요 (spec §6).
--
-- 인덱스: scope_type+scope_id 조합 partial index (활성 row만) — "이 워크스페이스의 항목" 조회 가속.

ALTER TABLE folders
  ADD COLUMN scope_type VARCHAR(20) NOT NULL,
  ADD COLUMN scope_id   UUID NOT NULL,
  ADD CONSTRAINT chk_folders_scope_type
    CHECK (scope_type IN ('department','team'));

ALTER TABLE files
  ADD COLUMN scope_type VARCHAR(20) NOT NULL,
  ADD COLUMN scope_id   UUID NOT NULL,
  ADD CONSTRAINT chk_files_scope_type
    CHECK (scope_type IN ('department','team'));

CREATE INDEX idx_folders_scope ON folders(scope_type, scope_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_files_scope   ON files(scope_type, scope_id)   WHERE deleted_at IS NULL;
