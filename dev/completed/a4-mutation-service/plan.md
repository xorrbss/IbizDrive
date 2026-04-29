# A4.6 — FolderMutationService Plan

## Goal

Folder mutation (create / rename / move) 도메인 서비스 + audit emission + PermissionController의 folder 존재 확인 경로 교체. A4.7 (REST endpoint) 진입 전 service contract 고정.

## Acceptance Criteria

1. `FolderMutationService.create(parentId, name, ownerId, auditLevel)` — 활성 폴더 INSERT, V5 unique 제약 위반 시 `FolderNameConflictException`. `FOLDER_CREATED` 감사 기록.
2. `FolderMutationService.rename(folderId, newName)` — soft-deleted target은 `FolderNotFoundException`. 동일 부모 내 충돌 시 `FolderNameConflictException`. `FOLDER_RENAMED` 감사 (before/after `name`/`normalized_name`).
3. `FolderMutationService.move(folderId, newParentId)` — soft-deleted target은 `FolderNotFoundException`. cycle (자기 자신/하위 트리로 이동) 시 명확한 예외 (`IllegalArgumentException` 또는 도메인 예외 — plan review에서 결정). 충돌 시 `FolderNameConflictException`. `FOLDER_MOVED` 감사.
4. 모든 mutation은 `@Transactional` + `SELECT FOR UPDATE` (lock) 적용. CLAUDE.md §3 원칙 6/7 준수.
5. `PermissionController.ensureResourceExists`의 folder 분기는 `FolderRepository.findByIdAndDeletedAtIsNull`로 교체. 회귀 가드 (permission grant flow) 그린.
6. `./gradlew test` 그린.

## High-Level Design

### Lock + conflict query

`FolderRepository`에 두 가지 추가:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT f FROM Folder f WHERE f.id = :id AND f.deletedAt IS NULL")
Optional<Folder> lockByIdAndDeletedAtIsNull(@Param("id") UUID id);

// V5 idx_folders_unique_name 의미와 정확히 일치 (root parent NULL → ZERO_UUID).
@Query(value = """
    SELECT EXISTS (
      SELECT 1 FROM folders
      WHERE COALESCE(parent_id, '00000000-0000-0000-0000-000000000000'::uuid)
            = COALESCE(:parentId, '00000000-0000-0000-0000-000000000000'::uuid)
        AND normalized_name = :normalizedName
        AND deleted_at IS NULL
    )
    """, nativeQuery = true)
boolean existsActiveByParentAndNormalizedName(@Param("parentId") UUID parentId,
                                              @Param("normalizedName") String normalizedName);
```

native query 채택 이유: V5 unique index와 동일한 `COALESCE` 의미를 JPQL로 재현 시 NULL=NULL 의미 차이가 발생할 수 있어 schema 진실의 출처와 1:1 매핑. CLAUDE.md §3 원칙 6.

### 정규화

`NormalizeUtil.normalizeFileName(name)`로 표시명, `normalizedNameForDedup(name)`로 dedup. ADR #16 — 프론트와 동일 corpus.

### Audit emission

`AuditService.record(AuditEvent)` 직접 호출 (REQUIRES_NEW로 격리). before/after를 jackson `ObjectMapper`로 JSON 직렬화.

- `FOLDER_CREATED`: afterState = `{name, normalizedName, parentId, ownerId, auditLevel}`, beforeState = null.
- `FOLDER_RENAMED`: beforeState = `{name, normalizedName}`, afterState = `{name, normalizedName}`.
- `FOLDER_MOVED`: beforeState = `{parentId}`, afterState = `{parentId}`.

actor / IP / UA: `AuditedAspect`와 동일 추출 — `SecurityContextHolder` + `WebRequestContextHolder`. `currentActorId()` 헬퍼는 `AuditedAspect` private — service에서 동일 로직 재현 (혹은 명시적 actorId 인자로 받음).

> **결정**: actorId는 service 호출자 (controller)가 명시적으로 전달. PermissionController가 `principal.getUser().getId()`를 넘기는 패턴과 동일. IP/UA는 service 내부에서 `WebRequestContextHolder` 직접 호출 (HTTP 컨텍스트 의존). 이렇게 하면 service signature가 명확하고 비-HTTP (배치) 컨텍스트에서도 호출 가능.

### Cycle 방지 (move)

target folder의 ancestor chain을 `parentId`로 walk. 이동 대상 id가 chain에 있으면 cycle. Java 루프 (트리 깊이 보통 ≤ 수십). 동일 트랜잭션 내에서 이미 lock한 row와는 별개 — read-only walk.

```java
private void assertNoCycle(UUID folderId, UUID newParentId) {
    if (newParentId == null) return;       // root로 이동
    if (folderId.equals(newParentId)) throw FolderCycleException;
    UUID cursor = newParentId;
    Set<UUID> visited = new HashSet<>();
    while (cursor != null) {
        if (!visited.add(cursor)) break;   // 데이터 corruption 방어
        if (cursor.equals(folderId)) throw FolderCycleException;
        Folder parent = repo.findByIdAndDeletedAtIsNull(cursor)
            .orElseThrow(() -> FolderNotFoundException("ancestor: " + cursor));
        cursor = parent.getParentId();
    }
}
```

> **결정**: cycle 위반은 `IllegalArgumentException`이 아니라 도메인 의미가 명확한 별도 예외가 자연스러우나, MVP 단순화를 위해 `IllegalArgumentException("cannot move folder into its own descendant")` 채택. A4.7 controller가 400 BAD_REQUEST로 매핑 (GlobalExceptionHandler 기존 패턴).

### Soft-deleted target

`lockByIdAndDeletedAtIsNull` 결과 empty → `FolderNotFoundException`. soft-deleted folder는 mutation 대상 아님 (휴지통 복원은 별도 흐름).

### PermissionController 교체

```java
case "folder" -> folderRepository.findByIdAndDeletedAtIsNull(id)
    .orElseThrow(() -> new ResourceNotFoundException("folder not found: " + id));
```

`JdbcTemplate` 의존성 제거 검토 — 다른 분기에서 사용 안 함 → 제거 가능. 생성자/필드 정리.

## File Plan

| 파일 | 변경 |
|---|---|
| `folder/FolderRepository.java` | `lockByIdAndDeletedAtIsNull` + `existsActiveByParentAndNormalizedName` 추가 |
| `folder/FolderNotFoundException.java` (신규) | `extends ResourceNotFoundException` (404 매핑 재사용) |
| `folder/FolderNameConflictException.java` (신규) | `extends RuntimeException` — A4.7 controller가 409 매핑 (PermissionConflictException 패턴 모방) |
| `folder/FolderMutationService.java` (신규) | create/rename/move + audit emit + cycle |
| `permission/PermissionController.java` | folder 분기 교체, JdbcTemplate 제거 |
| `folder/FolderMutationServiceTest.java` (신규) | Testcontainers slice |

## Risks

1. **JPA dirty-check vs explicit save** — Folder는 `@Version` 없음. lock 후 setter 호출 → flush. updatedAt 명시 set 필요 (entity comment에 명시).
2. **audit_level enum 승격 보류** — String 그대로 사용. `FolderMutationService.create`는 인자로 String 받고 V5 CHECK가 검증. 향후 enum으로 좁힐 때 service signature 변경.
3. **conflict native query racy** — exists 확인 후 INSERT 사이에 다른 트랜잭션이 INSERT 가능. V5 unique 제약이 진실의 출처 → `DataIntegrityViolationException` catch 후 `FolderNameConflictException`로 변환. 이중 가드.
4. **PermissionController 회귀** — 기존 jdbcTemplate path 검증 테스트가 있는지 확인 필요. 없으면 본 PR에서 회귀 위험은 있으나 A4 perm-endpoint 통합 테스트(있으면)로 커버.

## Out of Scope

- FolderController / REST 라우팅 (A4.7)
- soft delete (`delete`) / restore (`restore`) — 별도 세션
- audit_level 변경 (`FOLDER_AUDIT_LEVEL_CHANGED`) — 별도 작업
- File CRUD
