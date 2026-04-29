# A4.5 Folder Entity — Tasks

## T1. Folder JPA entity 도입
- [x] `backend/src/main/java/com/ibizdrive/folder/Folder.java` 작성
- [x] V5 12 컬럼 1:1 매핑 (이름/타입/nullable)
- [x] FileItem 스타일 javadoc — 관계 매핑 정책 + soft delete 정책 기록
- [x] protected default ctor + setter/getter

## T2. FolderRepository 도입
- [x] `backend/src/main/java/com/ibizdrive/folder/FolderRepository.java` 작성
- [x] `JpaRepository<Folder, UUID>` 확장
- [x] `findByIdAndDeletedAtIsNull(UUID)`
- [x] `findByParentIdAndDeletedAtIsNull(UUID)`
- [x] javadoc — A4.6에서 mutation lock query 추가 예정 명시

## T3. FolderRepositoryTest (Testcontainers slice)
- [x] `@DataJpaTest @Testcontainers(disabledWithoutDocker=true)` PostgreSQL 15-alpine
- [x] save root folder (parent NULL) → id assigned + findByIdAndDeletedAtIsNull
- [x] findByParentIdAndDeletedAtIsNull — soft-deleted child 제외
- [x] same parent + same normalized_name 두 번 save → DataIntegrityViolationException
- [x] soft delete 후 동일 이름 save → 통과 (partial unique index)
- [x] deleted_at set + purge_after NULL → CHECK 위반 (DataIntegrityViolation)

## T4. 검증
- [x] `./gradlew :backend:compileJava :backend:compileTestJava` 통과
- [x] `./gradlew :backend:test` 전체 — 0 failure / 0 error (35 report)
- [x] FolderRepositoryTest는 Testcontainers `disabledWithoutDocker=true`로 로컬 skip
      — V5MigrationIT 등 기존 컨테이너 테스트와 동일 패턴, CI에서 실행
- [x] V5MigrationIT 회귀 없음 (folder/** 다른 파일 미수정)

## T5. PR
- [ ] commit `feat(A4): Folder JPA entity (A4-data deferred close)`
- [ ] description: V5 제약 가드 결과 + A4-data deferred close 명시
- [ ] push + 사용자 머지 게이트 보고

## 결과 요약 (2026-04-29)

- 변경 파일: 3 (entity / repository / test) + dev-docs 4
- A4-data PR #6 deferred (Folder entity layer) close
- 다음 단계: A4.6 FolderMutationService (별도 세션 — boundary entity-only 유지)
