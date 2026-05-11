-- V18 — quota mutation Phase 2 (5-phase track, docs/04 §6.1)
--
-- users.storage_quota / storage_used 컬럼 도입. V2__users_auth.sql 주석
-- "department_id / storage_quota / storage_used / updated_at 은 후속 phase에서 추가"의
-- 최종 phase — department_id는 V7에서, storage_quota/used는 본 V18에서 활성.
--
-- - storage_quota: 사용자당 한도, 기본 10GB (10737418240 bytes). docs/02 §2.1 spec.
-- - storage_used:  현재 사용량, 기본 0. Phase 5 enforcement에서 upload commit 시점 UPDATE.
-- - 기존 row는 DEFAULT로 backfill (NOT NULL 안전).
--
-- 본 phase는 schema만 도입. JPA entity 매핑 / endpoint / audit / enforcement는 Phase 3~5
-- 트랙에서 분리 도입 — Hibernate `spring.jpa.hibernate.ddl-auto: validate`는 schema의
-- extra column을 허용하므로 본 PR 머지 후 User entity 무수정으로 부팅 통과.
--
-- CHECK 제약: 본 phase는 negative 방지만 (DB 제약 = 진실의 출처, CLAUDE.md §3 원칙 6).
-- soft limit `storage_used <= storage_quota`는 application 레벨에서 Phase 5에 검증 +
-- 413 QUOTA_EXCEEDED 분기 (사용자 한도 변경/grace 운영 유연성).

ALTER TABLE users
    ADD COLUMN storage_quota BIGINT NOT NULL DEFAULT 10737418240,
    ADD COLUMN storage_used  BIGINT NOT NULL DEFAULT 0;

ALTER TABLE users
    ADD CONSTRAINT users_storage_quota_nonneg CHECK (storage_quota >= 0),
    ADD CONSTRAINT users_storage_used_nonneg  CHECK (storage_used  >= 0);

COMMENT ON COLUMN users.storage_quota IS '사용자당 storage 한도(bytes). 10GB default. docs/02 §2.1, docs/04 §6.1 Phase 2.';
COMMENT ON COLUMN users.storage_used  IS '현재 사용량(bytes). Phase 5 enforcement에서 upload commit 시점 UPDATE.';
