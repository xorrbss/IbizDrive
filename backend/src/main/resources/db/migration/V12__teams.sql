-- Flyway V12: team-centric pivot — 임시 팀(사용자 자율 생성, 평면 구조, archive 가능).
-- spec docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §1.1.
--
-- teams: 사용자가 자율 생성하는 임시 워크스페이스(부서와 동등한 scope 단위, 평면).
--   - normalized_name: NFC 정규화된 이름 (앱 normalizeFileName과 동일 로직).
--   - visibility: private(멤버만) | internal(전사 검색 가능, 권한 별도).
--   - archived_at/by: soft archive (이름 재사용 허용 위해 unique index는 active만).
--   - root_folder_id: 팀 생성 후 root folder 생성 시 채워지는 backref (FK 부재 — folders 테이블이
--     scope 컬럼을 갖기 전이므로 단순 UUID. V13에서 folders.scope_team_id 추가 시 정합).
--
-- team_memberships: composite PK (team_id, user_id) — 한 팀에 한 user 1행.
--   - role: OWNER(전권) | MEMBER(일반). admin role은 차후 도입 시 CHECK 확장.
--   - team 삭제 시 cascade로 멤버십 정리. user 삭제는 RESTRICT (membership 잔존 시 user hard delete 차단).

CREATE TABLE teams (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name            VARCHAR(100) NOT NULL,
  normalized_name VARCHAR(100) NOT NULL,
  description     TEXT,
  visibility      VARCHAR(20) NOT NULL DEFAULT 'private',
  root_folder_id  UUID,
  created_by      UUID NOT NULL REFERENCES users(id),
  archived_at     TIMESTAMPTZ,
  archived_by     UUID REFERENCES users(id),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CONSTRAINT teams_visibility_check CHECK (visibility IN ('private','internal'))
);

-- archive 후 동일 이름 재생성 허용 — active(=archived_at IS NULL) row에서만 unique.
CREATE UNIQUE INDEX idx_teams_name_active
  ON teams(normalized_name) WHERE archived_at IS NULL;

CREATE TABLE team_memberships (
  team_id    UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
  user_id    UUID NOT NULL REFERENCES users(id),
  role       VARCHAR(20) NOT NULL,
  joined_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  invited_by UUID REFERENCES users(id),

  PRIMARY KEY (team_id, user_id),
  CONSTRAINT team_memberships_role_check CHECK (role IN ('OWNER','MEMBER'))
);

-- "내가 속한 팀" 조회 가속 (WorkspaceService.findForUser).
CREATE INDEX idx_team_memberships_user ON team_memberships(user_id);
