# Tasks — folder subtree size

## P1 Repository

- [ ] `AdminTrashRepository.findFolderSubtreeSizes(Set<UUID>)` — native query, recursive CTE, depth cap (예: 100)
- [ ] return Map<UUID, Long> (또는 projection list → Map 변환은 service에서)

## P2 Service

- [ ] `AdminTrashService.list` — 페이지의 trashed folder ids 수집
- [ ] batch lookup 1회 → folder DTO sizeBytes 채움
- [ ] empty rootIds set은 short-circuit (query skip)

## P3 Tests

- [ ] `AdminTrashServiceTest`에 folder sizeBytes 채워지는지 케이스 추가
- [ ] 빈 폴더 size=0 케이스
- [ ] 옵션: `AdminTrashRepositoryFolderSizeTest` (실제 SQL 검증)
- [ ] 기존 admin.trash slice 회귀 0

## P4 Docs

- [ ] `docs/02 §7.11` — sizeBytes folder도 채워짐 명시
- [ ] `docs/04 §8.3` — 운영자 가치 부연
- [ ] `BETA-RELEASE.md §7` — folder subtree size 항목 closure (있으면)
- [ ] `progress.md` 트랙 회고 entry

## P5 PR

- [ ] dev-process 정리
- [ ] PR title: `feat(wave2-folder-subtree-size): admin trash folder DTO에 subtree size 채우기 (Wave 2 T9 follow-up)`
- [ ] PR body: 운영자 식별 가치 + 단일 CTE 설계 + before/after
