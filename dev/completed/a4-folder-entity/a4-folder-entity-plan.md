# A4.5 Folder Entity — Plan

## Goal

A4-data PR #6에서 deferred 처리된 `Folder` JPA entity와 `FolderRepository`를 도입해
A4.6/A4.7 (service/controller) 진입 전 도메인 entity layer를 닫는다.

## Non-Goals

- mutation/move 비즈니스 로직 (A4.6 FolderMutationService)
- REST endpoint (A4.7 FolderController)
- 신규 마이그레이션 (V5가 이미 진실의 출처)
- file_versions entity (ADR #29 — A5 이월)
- permission/** 변경 (PR #7/#8 영역)

## Acceptance Criteria

1. `com.ibizdrive.folder.Folder` JPA entity가 V5 `folders` 테이블 12개 컬럼과 1:1 매핑.
2. `ddl-auto=validate` 환경에서 부팅 성공 (컬럼 type/nullability 일치).
3. `com.ibizdrive.folder.FolderRepository extends JpaRepository<Folder, UUID>` 도입,
   기본 CRUD + 자주 쓰이는 lookup 메서드 (FileRepository 패턴 일치 — 본 세션은 contract 최소화).
4. `FolderRepositoryTest` Testcontainers 슬라이스가 entity 경유 4가지 시나리오 그린:
   - root INSERT (parent NULL)
   - 동일 parent + normalized_name 중복 → DataIntegrityViolation
   - soft delete 후 동일 이름 재생성 가능 (partial unique index)
   - deleted_at NOT NULL + purge_after NULL → CHECK 위반
5. PR title: `feat(A4): Folder JPA entity (A4-data deferred close)` — A4-data deferred 항목 close 명시.

## Design Decisions

- **이름**: `Folder` (not `FolderEntity`) — `java.io.File`처럼 collision 없음.
- **관계 매핑**: parent_id / owner_id / original_parent_id 모두 `UUID` 단순 컬럼.
  `@ManyToOne Folder` 자기참조 매핑은 lazy proxy 비용 + cycle 위험 + A4.5 contract
  최소화 원칙에 어긋남. service layer가 필요할 때 명시적으로 fetch.
- **Soft delete 전략**: FileItem과 동일 — Hibernate `@SQLDelete`/`@Where` 사용 안 함
  (audit emission 누락 위험 + mutation 의미 불투명). 명시적 `WHERE deletedAt IS NULL` query.
- **Lombok 미사용**: 기존 entity (FileItem 등) 정책 일치.
- **Repository 메서드 최소화**: `findByIdAndDeletedAtIsNull`, `findByParentIdAndDeletedAtIsNull`
  두 개만. mutation lock query / 충돌 검사 query는 A4.6에서 추가.

## Risk

- V5MigrationIT가 raw JDBC로 같은 제약을 검증 중 — entity 경유 검증과 중복 가능.
  → 본 세션 테스트는 **JPA entity ↔ schema 매핑 검증** 가치에 집중 (validate
  부팅 + entity persist를 통한 same constraint 검증). raw JDBC와 다른 경로.
- `audit_level` 컬럼은 String enum 매핑 — Java enum으로 둘 수 있으나, 본 세션은
  String 유지 (validation은 DB CHECK가 담당). enum 도입은 A4.6 service에서.

## Boundary

- folder/Folder.java, folder/FolderRepository.java, folder/test/FolderRepositoryTest.java만 수정.
- folder/V5MigrationIT.java 절대 수정 금지 (read-only).
- permission/**, file/** 절대 수정 금지.
