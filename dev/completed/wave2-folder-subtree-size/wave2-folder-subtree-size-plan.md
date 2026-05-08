# Wave 2 T9 follow-up — admin trash folder subtree size

## 목적

`/admin/trash` 의 folder DTO `sizeBytes`는 현재 항상 `null` (`AdminTrashItemDto` javadoc + spec §4.4 v1.x deferred). 운영자가 휴지통에서 큰 폴더를 식별하고 우선 정리 결정할 수 없다.

폴더 subtree size(= 자기 자신 + 모든 하위 폴더의 file size 합)를 계산해 채워준다. **DTO 시그니처 무변경**, 기존 `sizeBytes: Long | null` 필드만 folder에서도 not-null이 됨.

## 범위

**In-scope**
- `AdminTrashRepository.findFolderSubtreeSizes(Set<UUID> rootIds): Map<UUID, Long>` — 단일 재귀 CTE 호출
- `AdminTrashService.list` — 페이지의 trashed folder ids 모아 batch lookup 1회, DTO에 채움
- `AdminTrashServiceTest` — folder sizeBytes 채워지는지 검증 (mocked repo)
- 가능하면 `AdminTrashRepositoryFolderSizeTest` (NEW, @DataJpaTest 또는 @SpringBootTest로 SQL 동작 검증)
- docs/02 §7.11 — folder sizeBytes 의미 갱신
- docs/04 §8.3 — 운영 가치 부연

**Out-of-scope (YAGNI)**
- live folder(내 폴더 트리)의 subtree size — admin trash 한정
- size cache table / materialized view — 페이지 max 100 + admin 사용량 ↓이라 현장 계산 OK
- folder 수가 대규모(>10K children)인 비정상 케이스 — pagination이 자연스럽게 보호
- frontend UI 변경 — DTO field가 이미 존재, 자동 노출됨

## 설계 결정

1. **단일 재귀 CTE per page (KISS)**
   - WITH RECURSIVE folder_tree (root_id, descendant_id) AS (
       SELECT id, id FROM folders WHERE id IN (:roots)
       UNION ALL
       SELECT ft.root_id, c.id FROM folders c JOIN folder_tree ft ON c.parent_id = ft.descendant_id
     )
     SELECT ft.root_id, COALESCE(SUM(fi.size_bytes), 0) FROM folder_tree ft
       LEFT JOIN files fi ON fi.folder_id = ft.descendant_id
       GROUP BY ft.root_id
   - root_id별로 결과 row 보장 (LEFT JOIN + GROUP BY)

2. **Live size, not snapshot**
   - 현재 시점 SUM — 사용자가 일부 file을 개별 hard-purge한 경우 줄어듦.
   - admin이 "지금 폴더가 가진 size"를 보는 게 자연스러움.
   - deleted_at 필터는 적용하지 않음 — folder 자체가 trash에 들어왔을 때 subtree 전체가 cascade soft-delete된 상태이므로 자연스럽게 모든 child file은 deleted_at NOT NULL. 굳이 filter 추가는 redundant.

3. **빈 폴더 처리**
   - subtree에 file 0개 → `0L` (not null). 명시적 측정 결과.
   - 기존 file의 sizeBytes(자신 size)와 의미가 다름 → DTO javadoc에 부연.

4. **Cycle 방지**
   - 사실 folders.parent_id는 tree로 운영(애플리케이션 레벨에서 cycle 차단). DB 레벨 CHECK 없음.
   - 재귀 CTE는 UNION ALL이라 무한 루프 가능성 — 안전을 위해 `CYCLE` 절 또는 depth cap. KISS: depth cap(예: 100)으로 처리.

## 수용 기준

- [ ] 휴지통의 folder가 응답에 `sizeBytes: <long>` 포함 (null이 아님)
- [ ] 빈 폴더는 `sizeBytes: 0`
- [ ] 중첩 폴더 트리에서 subtree 전체 합 정확
- [ ] `AdminTrashServiceTest` GREEN
- [ ] backend admin.trash slice GREEN
- [ ] frontend test 영향 없음 (시그니처 무변경)
- [ ] docs/02 §7.11 + docs/04 §8.3 + progress.md 갱신

## 위험 / 롤백

- 위험 ↓: backend-only, DTO 시그니처 무변경, 추가 query 1회/페이지(LEFT JOIN 재귀 CTE)
- 성능: page max 100 + admin 사용 빈도 ↓. CTE는 partial index `idx_folders_parent`(parent_id WHERE deleted_at IS NULL — folders.deleted_at으로 막힐 수 있음 — 검증 필요!) 활용
- 롤백: 단순 revert
