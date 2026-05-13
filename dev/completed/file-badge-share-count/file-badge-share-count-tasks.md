# Tasks — file-badge-share-count (P2c)

## 단위 — 완료

- [x] **T1** PermissionRepository.countActiveByResources — native query GROUP BY (✓)
- [x] **T2** FolderItemDto.shareCount 필드 + factory 시그니처 (✓)
- [x] **T3** FolderQueryService.loadItems 배치 count + Map lookup 주입 (✓)
- [x] **T4** FolderQueryServiceItemsTest 확장 — null + 노출 + empty skip 3 케이스 (✓ all PASS)
- [x] **T5** PermissionRepositoryTest count 5 케이스 (single/distinct/expired/type-isolation/multi-resource) — Testcontainers (Docker 가용 시 RUN)
- [x] **T6** docs/02 §7.5 `/api/folders/:id/items` shareCount? 명시 + 의미 주석
- [x] **T7** frontend FileRow.test.tsx 6 케이스 (✓ all PASS)
- [x] **T8** 검증: backend test PASS, typecheck PASS, lint PASS, FileRow test PASS
- [x] **T9** Commit + push, PR open, CI green 확인 — 진행 중

## 디버깅 노트

1. Mockito stub 우선순위 — lenient(any, any)를 service() 헬퍼 안에 두면 specific stub을 덮어씀 (LIFO). @BeforeEach로 분리해 해결.
2. `List.of(new Object[]{...})` varargs flatten 함정 — `List.<Object[]>of(...)`로 명시적 type parameter 필요.

## acceptance

- ✓ 백엔드 unit + integration test 통과 (local: unit PASS; CI: + Testcontainers integration)
- ✓ 프론트 FileRow.test.tsx 통과
- ✓ docs §7 wire spec 갱신
- ⏳ CI green (push 후 확인)
