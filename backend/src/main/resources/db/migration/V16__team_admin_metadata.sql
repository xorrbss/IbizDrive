-- Flyway V16: team admin metadata — color (UI swatch) + lead_id (designated team lead).
-- spec: design handoff 2026-05-10 admin-teams.jsx CreateTeamModal/TeamDetail.
--
-- color: 7-char hex (#RRGGBB). default '#5B7FCC' (디자인 TEAM_COLORS[0]).
--   admin-teams.jsx CreateTeamModal §color picker, TeamDetail §swatch.
-- lead_id: 단일 designated lead (FK users). 멤버십 role(OWNER/MEMBER)과 독립 — UI label
--   ("팀 리더" / "manager" pill) 전용. team archive cascade는 RESTRICT (lead 선출 후 archive).
--
-- 기존 row backfill: lead_id := created_by (initial OWNER). 이후 NOT NULL 강제.
-- color는 default 적용으로 backfill 자동.

ALTER TABLE teams
  ADD COLUMN color   VARCHAR(7) NOT NULL DEFAULT '#5B7FCC',
  ADD COLUMN lead_id UUID REFERENCES users(id);

UPDATE teams SET lead_id = created_by WHERE lead_id IS NULL;

ALTER TABLE teams
  ALTER COLUMN lead_id SET NOT NULL;

ALTER TABLE teams
  ADD CONSTRAINT teams_color_format CHECK (color ~ '^#[0-9A-Fa-f]{6}$');
