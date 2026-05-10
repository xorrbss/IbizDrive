-- =============================================================
-- IbizDrive — dev preview seed (idempotent)
-- =============================================================
-- 목적: dev preview 환경에서 디자인 fidelity 시각 검증을 위한
--       1 부서 + 1 팀 + 폴더/파일 1세트를 backend가 기대하는 schema에 맞게 시드.
--
-- 사전조건:
--   1. PG에 V1~V15 migration 적용 완료 (Flyway).
--   2. `admin@local.test` 사용자가 self-signup으로 이미 가입.
--      비밀번호 hash는 backend가 발급하므로 본 SQL은 평문/hash 모두 다루지 않는다.
--      가입 명령 예:
--        curl -X POST http://localhost:3001/api/auth/signup \
--          -H "Content-Type: application/json" \
--          -d '{"email":"admin@local.test","password":"AdminPass123","displayName":"Admin"}'
--      (frontend dev proxy 또는 backend 직접 8080에 호출. 첫 가입자는 ADMIN role 자동 부여 — ADR #41.)
--
-- idempotency:
--   - 모든 INSERT는 PK 충돌 시 DO NOTHING. 동일 SQL 두 번 실행해도 에러 없이 no-op.
--   - 모든 UPDATE는 `IS DISTINCT FROM` 가드로 같은 값 재기록 회피.
--   - 트랜잭션 단위 — 중간 실패 시 전체 롤백.
--
-- 미포함:
--   - permissions row (V5 permissions 테이블) — 부서/팀 root는 멤버십만으로 접근, 별도 grant 미필요.
--     세부 권한 시나리오 검증은 별도 시드 또는 admin UI로.
--   - shares — preview 단계에서는 부서/팀 본인 컨텍스트만 검증.
--   - audit_log — backend가 시드 직후 첫 호출에서 자동 기록.
-- =============================================================

BEGIN;

-- =============================================================
-- 0. 사전조건 검증 — admin@local.test 존재
-- =============================================================
DO $$
DECLARE
  v_admin_id UUID;
BEGIN
  SELECT id INTO v_admin_id
  FROM users
  WHERE lower(email) = 'admin@local.test'
    AND deleted_at IS NULL
    AND is_active = TRUE;

  IF v_admin_id IS NULL THEN
    RAISE EXCEPTION USING
      MESSAGE = 'admin@local.test 사용자가 없습니다.',
      DETAIL  = '먼저 self-signup 으로 가입하세요. 가입 직후 첫 사용자는 ADMIN role 자동 부여(ADR #41).',
      HINT    = 'curl -X POST http://localhost:8080/api/auth/signup -H "Content-Type: application/json" -d ''{"email":"admin@local.test","password":"AdminPass123","displayName":"Admin"}''';
  END IF;
END$$;


-- =============================================================
-- 1. 부서 — 디자인팀
-- =============================================================
INSERT INTO departments (id, name, parent_id, path)
VALUES
  ('0d000000-0000-0000-0000-000000000001'::uuid, '디자인팀', NULL, 'design'::ltree)
ON CONFLICT (id) DO NOTHING;

-- admin을 디자인팀에 배정 (멱등)
UPDATE users
SET department_id = '0d000000-0000-0000-0000-000000000001'::uuid
WHERE lower(email) = 'admin@local.test'
  AND department_id IS DISTINCT FROM '0d000000-0000-0000-0000-000000000001'::uuid;


-- =============================================================
-- 2. 부서 root folder + departments.root_folder_id 연결
--    WorkspaceService.findForUser 가 root_folder_id != null 필터하므로 필수.
--    folders.idx_folders_root_per_scope partial unique index 보호:
--      (scope_type, scope_id) WHERE parent_id IS NULL AND deleted_at IS NULL
--    → 같은 부서에 root folder 1개. ON CONFLICT (id) 로 PK 충돌만 방어.
-- =============================================================
INSERT INTO folders
  (id, parent_id, name, normalized_name, slug, owner_id, scope_type, scope_id)
VALUES
  ('0d000000-0000-0000-0000-0000000ff001'::uuid,
   NULL,
   '디자인팀', '디자인팀', 'design-team-root',
   (SELECT id FROM users WHERE lower(email) = 'admin@local.test'),
   'department',
   '0d000000-0000-0000-0000-000000000001'::uuid)
ON CONFLICT (id) DO NOTHING;

UPDATE departments
SET root_folder_id = '0d000000-0000-0000-0000-0000000ff001'::uuid
WHERE id = '0d000000-0000-0000-0000-000000000001'::uuid
  AND root_folder_id IS DISTINCT FROM '0d000000-0000-0000-0000-0000000ff001'::uuid;


-- =============================================================
-- 3. 부서 하위 폴더 (2026 Q1, Brand Assets)
-- =============================================================
INSERT INTO folders
  (id, parent_id, name, normalized_name, slug, owner_id, scope_type, scope_id)
VALUES
  ('0d000000-0000-0000-0000-0000000ff101'::uuid,
   '0d000000-0000-0000-0000-0000000ff001'::uuid,
   '2026 Q1', '2026 q1', '2026-q1',
   (SELECT id FROM users WHERE lower(email) = 'admin@local.test'),
   'department',
   '0d000000-0000-0000-0000-000000000001'::uuid),
  ('0d000000-0000-0000-0000-0000000ff102'::uuid,
   '0d000000-0000-0000-0000-0000000ff001'::uuid,
   'Brand Assets', 'brand assets', 'brand-assets',
   (SELECT id FROM users WHERE lower(email) = 'admin@local.test'),
   'department',
   '0d000000-0000-0000-0000-000000000001'::uuid)
ON CONFLICT (id) DO NOTHING;


-- =============================================================
-- 4. 팀 — 디자인 챕터
--    teams.idx_teams_name_active partial unique index 보호:
--      normalized_name WHERE archived_at IS NULL → 같은 normalized_name이라도 archive 후 재생성 가능.
--    ON CONFLICT (id) 로 PK 충돌만 방어.
-- =============================================================
INSERT INTO teams (id, name, normalized_name, description, visibility, created_by)
VALUES
  ('07000000-0000-0000-0000-000000000001'::uuid,
   '디자인 챕터', '디자인 챕터',
   '디자인팀 사이드 트랙. 스프린트별 협업 산출물 모음.',
   'private',
   (SELECT id FROM users WHERE lower(email) = 'admin@local.test'))
ON CONFLICT (id) DO NOTHING;


-- =============================================================
-- 5. 팀 root folder + teams.root_folder_id 연결
-- =============================================================
INSERT INTO folders
  (id, parent_id, name, normalized_name, slug, owner_id, scope_type, scope_id)
VALUES
  ('07000000-0000-0000-0000-0000000ff001'::uuid,
   NULL,
   '디자인 챕터', '디자인 챕터', 'design-chapter-root',
   (SELECT id FROM users WHERE lower(email) = 'admin@local.test'),
   'team',
   '07000000-0000-0000-0000-000000000001'::uuid)
ON CONFLICT (id) DO NOTHING;

UPDATE teams
SET root_folder_id = '07000000-0000-0000-0000-0000000ff001'::uuid
WHERE id = '07000000-0000-0000-0000-000000000001'::uuid
  AND root_folder_id IS DISTINCT FROM '07000000-0000-0000-0000-0000000ff001'::uuid;


-- =============================================================
-- 6. 팀 멤버십 — admin = OWNER
--    PK: (team_id, user_id). ON CONFLICT 가 PK 자체.
-- =============================================================
INSERT INTO team_memberships (team_id, user_id, role, invited_by)
SELECT
  '07000000-0000-0000-0000-000000000001'::uuid,
  u.id,
  'OWNER',
  u.id
FROM users u
WHERE lower(u.email) = 'admin@local.test'
ON CONFLICT (team_id, user_id) DO NOTHING;


-- =============================================================
-- 7. 팀 하위 폴더 (Sprint 26)
-- =============================================================
INSERT INTO folders
  (id, parent_id, name, normalized_name, slug, owner_id, scope_type, scope_id)
VALUES
  ('07000000-0000-0000-0000-0000000ff101'::uuid,
   '07000000-0000-0000-0000-0000000ff001'::uuid,
   'Sprint 26', 'sprint 26', 'sprint-26',
   (SELECT id FROM users WHERE lower(email) = 'admin@local.test'),
   'team',
   '07000000-0000-0000-0000-000000000001'::uuid)
ON CONFLICT (id) DO NOTHING;


-- =============================================================
-- 8. 파일 + file_versions
--    files.current_version_id FK 는 DEFERRABLE INITIALLY DEFERRED — 동일 트랜잭션
--    안에서 files INSERT (미존재 version_id 참조) → file_versions INSERT 순서로
--    commit 시점에 양방향 검증 통과.
--    file_versions.file_id FK 는 즉시 검증 → files INSERT 가 먼저여야 함.
--    storage_key 는 placeholder UUID (실제 S3 객체 미생성). preview 화면에서는
--    metadata만 보이므로 다운로드/스트리밍은 시도하지 않을 것.
-- =============================================================

-- 8a. files (6개)
INSERT INTO files (id, folder_id, name, normalized_name, current_version_id,
                   owner_id, size_bytes, mime_type, scope_type, scope_id)
VALUES
  -- 부서/2026 Q1
  ('0c000000-0000-0000-0000-000000000001'::uuid,
   '0d000000-0000-0000-0000-0000000ff101'::uuid,
   '2026 KPI 보고서.pdf', '2026 kpi 보고서.pdf',
   '0c000000-0000-0000-0000-0000fff00001'::uuid,
   (SELECT id FROM users WHERE lower(email) = 'admin@local.test'),
   125432, 'application/pdf',
   'department', '0d000000-0000-0000-0000-000000000001'::uuid),

  ('0c000000-0000-0000-0000-000000000002'::uuid,
   '0d000000-0000-0000-0000-0000000ff101'::uuid,
   'Q1 로드맵.docx', 'q1 로드맵.docx',
   '0c000000-0000-0000-0000-0000fff00002'::uuid,
   (SELECT id FROM users WHERE lower(email) = 'admin@local.test'),
   89234,
   'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
   'department', '0d000000-0000-0000-0000-000000000001'::uuid),

  -- 부서/Brand Assets
  ('0c000000-0000-0000-0000-000000000003'::uuid,
   '0d000000-0000-0000-0000-0000000ff102'::uuid,
   'Brand Guidelines v3.pdf', 'brand guidelines v3.pdf',
   '0c000000-0000-0000-0000-0000fff00003'::uuid,
   (SELECT id FROM users WHERE lower(email) = 'admin@local.test'),
   2421567, 'application/pdf',
   'department', '0d000000-0000-0000-0000-000000000001'::uuid),

  ('0c000000-0000-0000-0000-000000000004'::uuid,
   '0d000000-0000-0000-0000-0000000ff102'::uuid,
   'Color Tokens.xlsx', 'color tokens.xlsx',
   '0c000000-0000-0000-0000-0000fff00004'::uuid,
   (SELECT id FROM users WHERE lower(email) = 'admin@local.test'),
   34128,
   'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
   'department', '0d000000-0000-0000-0000-000000000001'::uuid),

  -- 팀/Sprint 26
  ('0c000000-0000-0000-0000-000000000005'::uuid,
   '07000000-0000-0000-0000-0000000ff101'::uuid,
   '스프린트 회고.md', '스프린트 회고.md',
   '0c000000-0000-0000-0000-0000fff00005'::uuid,
   (SELECT id FROM users WHERE lower(email) = 'admin@local.test'),
   12048, 'text/markdown',
   'team', '07000000-0000-0000-0000-000000000001'::uuid),

  ('0c000000-0000-0000-0000-000000000006'::uuid,
   '07000000-0000-0000-0000-0000000ff101'::uuid,
   'Wireframe Hero.png', 'wireframe hero.png',
   '0c000000-0000-0000-0000-0000fff00006'::uuid,
   (SELECT id FROM users WHERE lower(email) = 'admin@local.test'),
   845632, 'image/png',
   'team', '07000000-0000-0000-0000-000000000001'::uuid)
ON CONFLICT (id) DO NOTHING;

-- 8b. file_versions (각 파일당 v1 — clean scan, fake checksum/storage_key)
INSERT INTO file_versions (id, file_id, version_number, storage_key, size_bytes,
                           checksum_sha256, mime_type, scan_status, uploaded_by)
VALUES
  ('0c000000-0000-0000-0000-0000fff00001'::uuid,
   '0c000000-0000-0000-0000-000000000001'::uuid,
   1,
   'aa000001-aa01-aa01-aa01-aaaaaaaa0001'::uuid,
   125432,
   '0000000000000000000000000000000000000000000000000000000000000001',
   'application/pdf', 'clean',
   (SELECT id FROM users WHERE lower(email) = 'admin@local.test')),

  ('0c000000-0000-0000-0000-0000fff00002'::uuid,
   '0c000000-0000-0000-0000-000000000002'::uuid,
   1,
   'aa000002-aa02-aa02-aa02-aaaaaaaa0002'::uuid,
   89234,
   '0000000000000000000000000000000000000000000000000000000000000002',
   'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'clean',
   (SELECT id FROM users WHERE lower(email) = 'admin@local.test')),

  ('0c000000-0000-0000-0000-0000fff00003'::uuid,
   '0c000000-0000-0000-0000-000000000003'::uuid,
   1,
   'aa000003-aa03-aa03-aa03-aaaaaaaa0003'::uuid,
   2421567,
   '0000000000000000000000000000000000000000000000000000000000000003',
   'application/pdf', 'clean',
   (SELECT id FROM users WHERE lower(email) = 'admin@local.test')),

  ('0c000000-0000-0000-0000-0000fff00004'::uuid,
   '0c000000-0000-0000-0000-000000000004'::uuid,
   1,
   'aa000004-aa04-aa04-aa04-aaaaaaaa0004'::uuid,
   34128,
   '0000000000000000000000000000000000000000000000000000000000000004',
   'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', 'clean',
   (SELECT id FROM users WHERE lower(email) = 'admin@local.test')),

  ('0c000000-0000-0000-0000-0000fff00005'::uuid,
   '0c000000-0000-0000-0000-000000000005'::uuid,
   1,
   'aa000005-aa05-aa05-aa05-aaaaaaaa0005'::uuid,
   12048,
   '0000000000000000000000000000000000000000000000000000000000000005',
   'text/markdown', 'clean',
   (SELECT id FROM users WHERE lower(email) = 'admin@local.test')),

  ('0c000000-0000-0000-0000-0000fff00006'::uuid,
   '0c000000-0000-0000-0000-000000000006'::uuid,
   1,
   'aa000006-aa06-aa06-aa06-aaaaaaaa0006'::uuid,
   845632,
   '0000000000000000000000000000000000000000000000000000000000000006',
   'image/png', 'clean',
   (SELECT id FROM users WHERE lower(email) = 'admin@local.test'))
ON CONFLICT (id) DO NOTHING;


COMMIT;


-- =============================================================
-- 검증 쿼리 (시드 후 수동 확인용 — 트랜잭션 외부)
-- =============================================================
-- SELECT 'department=' || count(*) FROM departments
--   WHERE id = '0d000000-0000-0000-0000-000000000001'::uuid AND root_folder_id IS NOT NULL;
-- SELECT 'team=' || count(*) FROM teams
--   WHERE id = '07000000-0000-0000-0000-000000000001'::uuid AND root_folder_id IS NOT NULL;
-- SELECT 'membership=' || count(*) FROM team_memberships
--   WHERE team_id = '07000000-0000-0000-0000-000000000001'::uuid AND role = 'OWNER';
-- SELECT 'folders=' || count(*) FROM folders
--   WHERE scope_id IN ('0d000000-0000-0000-0000-000000000001'::uuid,
--                      '07000000-0000-0000-0000-000000000001'::uuid)
--     AND deleted_at IS NULL;  -- 기대값 5 (부서 root + 부서 하위 2 + 팀 root + 팀 하위 1)
-- SELECT 'files=' || count(*) FROM files
--   WHERE scope_id IN ('0d000000-0000-0000-0000-000000000001'::uuid,
--                      '07000000-0000-0000-0000-000000000001'::uuid)
--     AND deleted_at IS NULL;  -- 기대값 6
