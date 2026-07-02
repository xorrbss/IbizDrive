-- Flyway V26: 검색 LIKE 성능 — pg_trgm GIN 인덱스 (ADR #33 후속, docs/02 §7.8).
--
-- 배경: GET /api/search는 normalized_name LIKE '%q%' (leading wildcard)라 btree 사용 불가 —
-- FileRepository.searchByNormalizedName 주석이 "큰 데이터셋 시 trigram 마이그레이션 트랙"으로
-- 예정해 둔 항목. pg_trgm GIN 인덱스는 양측 wildcard LIKE를 인덱스 스캔으로 전환한다.
--
-- 검색이 deleted_at IS NULL을 항상 포함하므로 partial index로 크기 절감.
-- CREATE EXTENSION은 superuser(db_superuser=Flyway 실행 계정) 권한 필요 — managed PG(RDS 등)는
-- pg_trgm이 허용 목록에 있어 master user로 생성 가능.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_files_normalized_name_trgm
    ON files USING gin (normalized_name gin_trgm_ops)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_folders_normalized_name_trgm
    ON folders USING gin (normalized_name gin_trgm_ops)
    WHERE deleted_at IS NULL;
