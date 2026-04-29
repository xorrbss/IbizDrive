# A4.5 Folder Entity — Context

## Base State

- branch: `feature/a4-folder-entity`
- base: `origin/master @ 44d1a86 (PR #7 a4-evaluator merged)`
- A4-data PR #6 (22b4f00): V5 schema + FileItem/FileRepository + Permission* 도입,
  `Folder` entity는 deferred (cross-session ownership 충돌로).
- PR #7 / PR #8 (open) 영역과 충돌 없음 (folder/** 신규).

## Reference Files (read-only)

- `backend/src/main/resources/db/migration/V5__folders_files_permissions.sql`
  §2.3 folders 본문 — 12 컬럼, partial unique index, deleted/purge CHECK pair.
- `backend/src/main/java/com/ibizdrive/file/FileItem.java`
  entity 스타일 template (protected default ctor, no Lombok, javadoc 결정 기록).
- `backend/src/main/java/com/ibizdrive/file/FileRepository.java`
  repository contract 최소화 패턴.
- `backend/src/test/java/com/ibizdrive/permission/PermissionRepositoryTest.java`
  Testcontainers + @DataJpaTest 슬라이스 패턴.
- `backend/src/test/java/com/ibizdrive/folder/V5MigrationIT.java`
  raw JDBC schema-level 검증 (보완 관계).
- `docs/02-backend-data-model.md` §2.3 folders 명세.

## V5 folders 컬럼 매핑 표

| SQL                 | type           | nullable | Java field          | Java type | 비고                       |
|---------------------|----------------|----------|---------------------|-----------|---------------------------|
| id                  | UUID PK        | no       | id                  | UUID      | gen_random_uuid 기본값     |
| parent_id           | UUID FK        | yes      | parentId            | UUID      | NULL = root                |
| name                | VARCHAR(255)   | no       | name                | String    |                            |
| normalized_name     | VARCHAR(255)   | no       | normalizedName      | String    | NFC normalize              |
| slug                | VARCHAR(255)   | no       | slug                | String    |                            |
| owner_id            | UUID FK        | no       | ownerId             | UUID      |                            |
| audit_level         | VARCHAR(20)    | no       | auditLevel          | String    | CHECK 'standard'/'strict'  |
| deleted_at          | TIMESTAMPTZ    | yes      | deletedAt           | Instant   |                            |
| purge_after         | TIMESTAMPTZ    | yes      | purgeAfter          | Instant   | deleted_at과 짝 CHECK     |
| original_parent_id  | UUID FK        | yes      | originalParentId    | UUID      | 휴지통 복원 보존           |
| created_at          | TIMESTAMPTZ    | no       | createdAt           | Instant   | DEFAULT NOW()              |
| updated_at          | TIMESTAMPTZ    | no       | updatedAt           | Instant   | DEFAULT NOW()              |

## 주의점

- `ddl-auto: validate`: 컬럼 누락/타입 불일치 시 부팅 실패. V5MigrationIT 통해 검증 가능.
- `created_at` / `updated_at` DB DEFAULT 사용 → entity 측 nullable 처리 또는 INSERT 시 명시 setter 호출 패턴 (FileItem과 일관).
- 테스트 시 `@DataJpaTest`는 main package (`com.ibizdrive.IbizDriveApplication`) 기준 자동 스캔 — 별도 설정 불필요.
