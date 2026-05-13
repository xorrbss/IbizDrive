-- P2a — FileRow/FileCard 즐겨찾기 별 아이콘 wiring.
-- user 단위 favorites: file/folder resource를 즐겨찾기로 표시.
-- 복합 PK (user_id, resource_type, resource_id)로 멱등 보장 + 중복 INSERT 차단.
CREATE TABLE favorites (
    user_id       UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    resource_type VARCHAR(10) NOT NULL CHECK (resource_type IN ('file', 'folder')),
    resource_id   UUID        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, resource_type, resource_id)
);

-- 사용자별 즐겨찾기 목록 (v1.x `/favorites` 화면용 — 시간순 desc).
CREATE INDEX idx_favorites_by_user_created
    ON favorites(user_id, created_at DESC);

-- batch starred join 가속 — FolderItemDto.starred wiring에서
-- 한 부모 폴더의 자식 N개 + 현재 user 1개 row를 한번에 lookup.
CREATE INDEX idx_favorites_by_resource
    ON favorites(resource_type, resource_id);
