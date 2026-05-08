# Context — folder subtree size for admin trash

## 진실의 출처 — 변경 위치

1. `backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashRepository.java`
   - 신규 메서드: `findFolderSubtreeSizes(Set<UUID> rootIds): List<Object[]>` (또는 projection)

2. `backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashService.java`
   - `list()`에서 folder DTO 생성 직전, batch 조회 후 Map<UUID, Long> 으로 채움
   - `AdminTrashItemDto` constructor의 `sizeBytes` 인자에 size를 넘김 (현재 `null` 자리)

## 영향받지 않는 곳

- `AdminTrashItemDto` 시그니처 — `Long sizeBytes` 그대로
- `AdminTrashController` / wire 엔드포인트 — 무변경
- Frontend `app/admin/trash/all/page.tsx` — 이미 `item.sizeBytes`를 렌더 (file 전용 가정 코드 없으면 자동 작동), 검증 필요
- DB schema — V5의 folders/files 그대로 사용

## 스키마 참조

- `folders.parent_id UUID REFERENCES folders(id) ON DELETE RESTRICT`
- `files.folder_id UUID REFERENCES folders(id)` (NOT NULL)
- `idx_folders_parent ON folders(parent_id) WHERE deleted_at IS NULL` — partial index. **trashed folder는 deleted_at NOT NULL이라 이 인덱스 미적용** → 풀 스캔 가능성. page 100개 root에 대한 재귀라면 OK이지만, 매우 큰 트리에선 느려질 수 있음. 베타 운영 단계라 acceptance.

## 기존 코드 위치 메모

- AdminTrashService.list() — line 73-172 (Wave 2 T9 main listing)
- 폴더 DTO 생성 — line 145-157
- userIds/parentIds 배치 lookup 패턴 (line 109-129) → subtree size 배치 lookup도 같은 위치에 추가

## 테스트 위치

- `backend/src/test/java/com/ibizdrive/admin/trash/AdminTrashServiceTest.java`
  - 기존 test는 Mockito (repository 모킹). 신규 메서드 호출 + Map 응답 stub → DTO sizeBytes 검증.
- 옵션: `AdminTrashRepositoryFolderSizeTest` (@DataJpaTest, Postgres testcontainers 또는 H2-recursive 호환) — 실제 SQL 검증.

## 관련 문서

- `docs/02 §7.11` — admin trash endpoint 표 + 상세
- `docs/04 §8.3` — 운영자 사용 시나리오
