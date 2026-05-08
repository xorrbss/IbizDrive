-- Flyway V10: deleted_by 컬럼 — Wave 2 T9 follow-up (cross-owner 복원 추적)
--
-- 목적: admin global trash (`/admin/trash/all`)에서 누가 삭제했는지 식별 가능하게 한다.
-- audit_log에는 actor_id가 이미 보존되지만 (V4 REVOKE 정책으로 영구) UI 행 단위 lookup이
-- 별도 join을 강제 — files/folders 자체에 컬럼을 두어 한 번의 SELECT로 trash listing이
-- deleter를 함께 노출하도록 한다.
--
-- 결정 (spec/plan §3.1, §3.2):
--   * nullable + 단방향 CHECK
--     `deleted_at IS NOT NULL OR deleted_by IS NULL`
--     → 활성 row(deleted_at IS NULL)에 deleted_by가 채워지는 것은 차단,
--       trash row는 NULL/non-NULL 모두 허용 (V10 이전 trash row는 backfill 안 함 → NULL).
--   * ON DELETE SET NULL — deleter 사용자가 hard-delete된 후에도 trash row 자체는 보존,
--     deleted_by만 NULL로 (V5의 owner_id RESTRICT와 다른 정책 — owner는 자료 무결성,
--     deleted_by는 추적 보조).
--   * 인덱스 미추가 — deleted_by 필터링은 빈도 낮음 (admin 쿼리 정렬 키는 deleted_at DESC),
--     필요 시 v1.x++ 별도 ADR로.
--
-- backfill 정책: 미실시. audit_log derivation은 fragile + cost ↑. 컷오프 이전 trash row는
-- UI에서 "—"로 표기 (docs/04 §8.3, BETA-RELEASE §7).

ALTER TABLE files
    ADD COLUMN deleted_by UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE files
    ADD CONSTRAINT files_deleted_by_check
        CHECK (deleted_at IS NOT NULL OR deleted_by IS NULL);

ALTER TABLE folders
    ADD COLUMN deleted_by UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE folders
    ADD CONSTRAINT folders_deleted_by_check
        CHECK (deleted_at IS NOT NULL OR deleted_by IS NULL);
