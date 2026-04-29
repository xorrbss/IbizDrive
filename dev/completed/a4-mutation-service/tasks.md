# A4.6 — Tasks

## Sequence

1. **Domain exceptions** — `FolderNotFoundException` (extends `ResourceNotFoundException`), `FolderNameConflictException` (extends `RuntimeException` — `PermissionConflictException` 패턴 모방).
2. **FolderRepository 확장** — `lockByIdAndDeletedAtIsNull` (`@Lock(PESSIMISTIC_WRITE)` + JPQL), `existsActiveByParentAndNormalizedName` (native query, V5 unique index 의미와 1:1).
3. **FolderMutationService** — `create` / `rename` / `move`. `@Transactional` + lock + 정규화 + audit emit + cycle (move). `ObjectMapper` 주입으로 before/after JSON 직렬화. `DataIntegrityViolationException` 이중 가드.
4. **PermissionController 교체** — `ensureResourceExists` folder 분기를 `FolderRepository`로. `JdbcTemplate` 의존 제거 (다른 분기 미사용 시).
5. **FolderMutationServiceTest** — Testcontainers slice. 케이스 매트릭스:
   - create: root 활성, nested 활성, 동일 이름 충돌 (root 두 번), 동일 이름 충돌 (nested), soft-deleted 부모와 동일 이름은 허용.
   - rename: happy, 동일 부모 충돌, soft-deleted target → `FolderNotFoundException`, 같은 이름으로 rename은 no-op (또는 audit row 안 남기는지 결정 — plan review).
   - move: happy (다른 부모), root로 이동 (parentId=null), cycle (self), cycle (descendant), 충돌 (이동 대상에 동명 폴더), soft-deleted target.
   - audit_log 검증: 각 케이스에서 `event_type`, `target_id`, `actor_id` 행 1개. before/after 내용까지 검증할지는 effort 대비 가치로 결정.
6. **검증** — `./gradlew test` 그린 → commit → push → PR.

## Working File Boundary

- `folder/FolderMutationService.java` (신규)
- `folder/FolderNotFoundException.java` (신규)
- `folder/FolderNameConflictException.java` (신규)
- `folder/FolderRepository.java` (확장)
- `folder/FolderMutationServiceTest.java` (신규)
- `permission/PermissionController.java` (folder 분기만)

## Done Definition

- 위 6개 파일 변경 + 테스트 그린 + PR open.
- dev/active/a4-mutation-service/{plan,tasks,context}.md 종결 상태 갱신.
- dev/process/20260429-a4-mutation-service.md 삭제.

## Status (2026-04-29 — completed)

- ✅ Domain exceptions: FolderNotFoundException (extends ResourceNotFoundException → 404), FolderNameConflictException
- ✅ FolderRepository extension: lockByIdAndDeletedAtIsNull (PESSIMISTIC_WRITE), existsActiveByParentAndNormalizedName / existsActiveByParentAndNormalizedNameExcludingId (native query, V5 idx_folders_unique_name 의미와 1:1 COALESCE)
- ✅ FolderMutationService: create/rename/move + AuditService 직접 emit (REQUIRES_NEW 격리). cycle 방지 = Java ancestor walk (self/descendant/visited Set/hop limit). DataIntegrityViolation 이중 가드.
- ✅ PermissionController: ensureResourceExists folder 분기 → FolderRepository.findByIdAndDeletedAtIsNull. JdbcTemplate 의존 제거.
- ✅ PermissionControllerTest: stub jdbcTemplate → folderRepository (atomic 변경에 따른 동행).
- ✅ FolderMutationServiceTest: 22 케이스 (create×8, rename×5, move×9). @DataJpaTest + Testcontainers + Mockito AuditService.
- ✅ ./gradlew test BUILD SUCCESSFUL (Docker 비가용으로 Testcontainers 슬라이스 스킵 — A4.5와 동일 패턴, CI에서 실행). 회귀 가드(PermissionControllerTest 7/7, PermissionServiceGrantRevokeTest 16/16) 그린.

## Follow-up (out of scope)

- A4.7 FolderController + REST endpoint 도입 시 GlobalExceptionHandler에 FolderNameConflictException → 409 매핑 추가 (PermissionConflictException과 동일 envelope).
- soft delete / restore / audit_level change service 메서드.
- audit_level enum 승격 (Folder.java 변경).


## Out of Scope (다음 세션)

- A4.7 FolderController / REST endpoint
- delete / restore / audit_level change
- File CRUD MVP
- audit_level enum 승격 (Folder.java 변경)
