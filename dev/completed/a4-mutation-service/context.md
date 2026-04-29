# A4.6 — Context

## Base State (origin/master @ 1091339, PR #9)

- `Folder` entity: 12 컬럼 매핑 (id/parent_id/name/normalized_name/slug/owner_id/audit_level/deleted_at/purge_after/original_parent_id/created_at/updated_at). `parentId` 단순 UUID (no `@ManyToOne`). `auditLevel` String.
- `FolderRepository`: 2 메서드 (`findByIdAndDeletedAtIsNull`, `findByParentIdAndDeletedAtIsNull`).
- V5 `idx_folders_unique_name`: `(COALESCE(parent_id, ZERO_UUID), normalized_name) WHERE deleted_at IS NULL`.
- V5 CHECK `audit_level IN ('standard','strict')`, `(deleted_at IS NULL) = (purge_after IS NULL)`.
- `AuditService.record(AuditEvent)` — REQUIRES_NEW 트랜잭션, JSONB cast.
- `AuditEventType` 38개 — `FOLDER_CREATED`, `FOLDER_RENAMED`, `FOLDER_MOVED` 모두 정의됨.
- `AuditTargetType.FOLDER` 정의됨.
- `WebRequestContextHolder.currentIp()/currentUserAgent()` — HTTP 컨텍스트 추출.
- `NormalizeUtil` — `normalizeFileName` (표시명) / `normalizedNameForDedup` (dedup).

## 호출 지점

- `PermissionController.ensureResourceExists("folder", id)`: 현재 `JdbcTemplate` native — 본 세션에서 `FolderRepository`로 교체.
- A4.7 (별도 세션): `FolderController`가 service.create/rename/move 호출 예정.

## 주의 / 함정

1. **dirty-check vs setter**: Folder는 `@Version` 없음. mutation 후 `repo.save()` 명시 권장. `updatedAt`은 entity comment에 따라 명시 set.
2. **JSONB**: `AuditService`가 `?::jsonb`로 cast. `beforeState`/`afterState`/`metadata`는 valid JSON 문자열이어야. Jackson `ObjectMapper` 주입 (Spring Boot autoconfigure)으로 직렬화.
3. **V5 unique race**: exists 확인 후 INSERT 사이 race 가능. `DataIntegrityViolationException` catch + `FolderNameConflictException` 변환 — 이중 가드.
4. **soft-deleted folder의 lock**: `SELECT FOR UPDATE` + `WHERE deleted_at IS NULL` → soft-deleted는 lock도 안 잡힘 → `FolderNotFoundException`. 휴지통 복원 흐름은 별도 query (본 세션 out of scope).
5. **cycle walk visited Set**: 데이터 corruption (orphan cycle in DB) 방어용. 정상 케이스는 root까지 도달 후 break.
6. **PermissionController JdbcTemplate**: 제거 시 다른 사용처 없는지 grep 확인. 다른 분기에서 사용하면 유지.
7. **frontend audit type 동기화**: `AuditEventType` 변경 없음 (FOLDER_CREATED/RENAMED/MOVED 이미 존재). 본 세션은 frontend types/audit.ts 미변경.

## 테스트 인프라

- 기존 Testcontainers 테스트가 있는지 확인 필요 (`PermissionServiceTest`, `FolderRepositoryTest` 등). 같은 슬라이스 패턴 재사용.
- `audit_log` 테이블 직접 query로 INSERT 검증 — `AuditService` REQUIRES_NEW이라 비즈니스 트랜잭션과 분리되므로 트랜잭션 commit 후 visible.

## 위반 시 중단 조건 (CLAUDE.md §3)

- 원칙 6 (DB 제약 진실): unique 검사를 충돌 검사로 대체하지 않는다 — V5 native query + DataIntegrityViolation 이중 가드.
- 원칙 7 (트랜잭션 + SELECT FOR UPDATE): mutation은 반드시 `@Transactional` + lock query.
- 원칙 8 (audit append-only): audit_log INSERT만, UPDATE/DELETE 금지. AuditService 단일 진입점 사용.
