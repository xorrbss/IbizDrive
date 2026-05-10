# Tasks — ERR_TEAM_ARCHIVED Write Enforcement

Last Updated: 2026-05-10

## Status by phase

- [ ] Phase 1 — 인프라 (T1, T2)
- [ ] Phase 2 — 폴더 진입점 (T3)
- [ ] Phase 3 — 파일 진입점 (T4, T5)
- [ ] Phase 4 — 문서 sync (T6)

## Task list

- [ ] **T1**: TeamArchivedException + GlobalExceptionHandler 423 → `TEAM_ARCHIVED` mapping
- [ ] **T2**: TeamArchiveGuard helper service (`assertNotArchived(scopeType, scopeId)`)
- [ ] **T3**: FolderMutationService 5개 메서드 가드 + integration test
- [ ] **T4**: FileMutationService 4개 메서드 가드 + integration test
- [ ] **T5**: FileUploadService.upload + FileVersionMutationService.restoreVersion 가드 + integration test
- [ ] **T6**: docs/02 §8 "예약" 마커 제거 + dev-docs-update + progress.md

---

## T1 — TeamArchivedException + handler mapping

### 작업 전 필독

- spec §5.4 `ERR_TEAM_ARCHIVED 423`: docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md (line 411)
- docs/02 §8 항목 — wire code = `TEAM_ARCHIVED` (접두사 ERR_ 없음)
- 피어 패턴: `backend/src/main/java/com/ibizdrive/team/LastOwnerRequiredException.java` + GlobalExceptionHandler 매핑

### 원본 코드 참조

- backend/src/main/java/com/ibizdrive/team/LastOwnerRequiredException.java (peer pattern)
- backend/src/main/java/com/ibizdrive/folder/CrossScopeMoveException.java (peer pattern, 423 변형 모델)
- backend/src/main/java/com/ibizdrive/common/error/GlobalExceptionHandler.java (매핑 추가 위치)

### 구현 대상

- 신규: `backend/src/main/java/com/ibizdrive/team/TeamArchivedException.java`
  - `extends RuntimeException`
  - 생성자: `TeamArchivedException(UUID teamId)` — message: `"Team is archived: " + teamId`
  - getter: `getTeamId(): UUID`

- 변경: `GlobalExceptionHandler.java`
  - `@ExceptionHandler(TeamArchivedException.class)` → `ResponseEntity.status(423).body(ErrorResponse.of("TEAM_ARCHIVED", ex.getMessage(), Map.of("teamId", ex.getTeamId())))`
  - 기존 ErrorResponse 빌더 시그니처 / 매핑 컨벤션 그대로 따름

### 검증 참조

- 신규 단위 테스트 (handler 매핑): `backend/src/test/java/com/ibizdrive/common/error/GlobalExceptionHandlerTeamArchivedTest.java`
  - `MockMvc` + 더미 컨트롤러 throw → status 423 + body code = `TEAM_ARCHIVED` 검증
  - 이미 다른 handler 테스트가 있으면 같은 클래스에 케이스만 추가

### 문서 반영

- 본 단계에서는 문서 변경 없음 (T6에서 일괄)

---

## T2 — TeamArchiveGuard helper

### 작업 전 필독

- T1 완료 (TeamArchivedException 존재 가정)
- `Team.isActive()` (Team.java:202)
- `TeamRepository` 위치/시그니처
- ScopeType enum (`com.ibizdrive.folder.ScopeType`)

### 원본 코드 참조

- backend/src/main/java/com/ibizdrive/team/Team.java (isActive)
- backend/src/main/java/com/ibizdrive/team/TeamRepository.java
- backend/src/main/java/com/ibizdrive/folder/ScopeType.java

### 구현 대상

- 신규: `backend/src/main/java/com/ibizdrive/team/TeamArchiveGuard.java`
  - `@Service` (Spring)
  - 의존성: `TeamRepository`
  - public method: `void assertNotArchived(ScopeType scopeType, UUID scopeId)`
    - `if (scopeType != ScopeType.TEAM) return;`  // department/everyone 무영향
    - team = teamRepository.findById(scopeId).orElseThrow(...)  // 또는 그냥 isPresent 체크 후 active 검사
    - `if (!team.isActive()) throw new TeamArchivedException(scopeId);`
  - team이 존재하지 않는 경우는 정상적으로는 발생 X (denormalized scope_id가 dangling pointer일 때만). 이 경우는 가드를 silent pass 또는 IllegalStateException 중 결정 — peer 패턴 따라 silent pass (KISS, 소프트 fail 후 다른 검증에 위임).

### 검증 참조

- 신규 단위 테스트: `backend/src/test/java/com/ibizdrive/team/TeamArchiveGuardTest.java`
  - case 1: scopeType != TEAM → no-op (TeamRepository 호출 0회)
  - case 2: scopeType=TEAM, team active → no-op
  - case 3: scopeType=TEAM, team archived → TeamArchivedException 던짐, teamId 일치
  - case 4: scopeType=TEAM, team 미존재 → silent pass (peer 패턴)

### 문서 반영

- 본 단계에서는 문서 변경 없음

---

## T3 — FolderMutationService 가드 적용

### 작업 전 필독

- T1, T2 완료
- spec §2.2 archive lifecycle: archived → 콘텐츠 read-only
- 피어 가드 패턴 (3b2b0b5): FolderMutationService.move의 CrossScopeMoveException 위치

### 원본 코드 참조

- backend/src/main/java/com/ibizdrive/folder/FolderMutationService.java
  - create:106, rename:223, move:287, delete:359, restore:430/434

### 구현 대상

- 변경: `FolderMutationService.java`
  - 필드 추가: `private final TeamArchiveGuard teamArchiveGuard;`
  - 생성자 인자 추가
  - **create(parentId, ...)**: parent fetch 후 `teamArchiveGuard.assertNotArchived(parent.scopeType, parent.scopeId)`. parent 없으면 root 폴더 생성 케이스 — 본 task에서는 root 직접 생성 차단(spec §1.3) 정책에 위임.
  - **rename(folderId, ...)**: target fetch 후 가드
  - **move(folderId, newParentId, ...)**: target fetch 후 가드 (same-scope 가드가 이미 source/dest scope 동일 보장하므로 1회로 충분). newParentId == null이면 root이지만 root 직접 이동도 §1.3에 따라 별도 정책.
  - **delete(folderId, ...)**: target fetch 후 가드
  - **restore(folderId, ...)**: target fetch 후 가드 (양쪽 overload)
  - 가드 위치: 권한 체크와 mutation 사이. CrossScopeMoveException과 동일한 진입 위치 컨벤션.

### 검증 참조

- 신규: `backend/src/test/java/com/ibizdrive/folder/FolderArchivedTeamGuardTest.java`
  - 5개 메서드 × {team archived → 423, team active → 통과, dept scope → 통과} = 15 케이스 (active+dept를 묶어 케이스 수 줄여도 OK, 핵심은 archived 차단 5건 + 회귀 검사)
  - 기존 FolderMutationServiceTest 회귀 통과
- 명령어: `./gradlew :backend:test --tests "com.ibizdrive.folder.*"`

### 문서 반영

- T6에서 일괄

---

## T4 — FileMutationService 가드 적용

### 작업 전 필독

- T1, T2 완료
- File entity의 scope 컬럼 (V12 적용 — Folder와 동일 스키마)
- FileMutationService 4개 메서드 라인 위치

### 원본 코드 참조

- backend/src/main/java/com/ibizdrive/file/FileMutationService.java
  - rename:91, move:149, delete:205, restore:255/259

### 구현 대상

- 변경: `FileMutationService.java`
  - 필드 추가: `private final TeamArchiveGuard teamArchiveGuard;`
  - 생성자 인자 추가
  - **rename(fileId, ...)**: target fetch 후 가드
  - **move(fileId, newFolderId, ...)**: target fetch 후 가드 (same-scope 가정)
  - **delete(fileId, ...)**: target fetch 후 가드
  - **restore(fileId, ...)**: target fetch 후 가드 (양쪽 overload)

### 검증 참조

- 신규: `backend/src/test/java/com/ibizdrive/file/FileArchivedTeamGuardTest.java`
  - 4개 메서드 × {archived 차단, active 통과, dept 통과} 패턴
  - 기존 FileMutationServiceTest 회귀 통과
- 명령어: `./gradlew :backend:test --tests "com.ibizdrive.file.FileMutation*" "com.ibizdrive.file.FileArchived*"`

### 문서 반영

- T6에서 일괄

---

## T5 — FileUploadService + FileVersionMutationService 가드 적용

### 작업 전 필독

- T1, T2 완료
- FileUploadService.upload — destination folder의 scope 기준
- FileVersionMutationService.restoreVersion — 대상 file의 scope 기준

### 원본 코드 참조

- backend/src/main/java/com/ibizdrive/file/FileUploadService.java (upload:76)
- backend/src/main/java/com/ibizdrive/file/FileVersionMutationService.java (restoreVersion:81)

### 구현 대상

- 변경: `FileUploadService.java`
  - 필드/생성자에 `TeamArchiveGuard` 주입
  - upload 진입부: parent folder fetch (이미 함) 후 `teamArchiveGuard.assertNotArchived(parent.scopeType, parent.scopeId)`
- 변경: `FileVersionMutationService.java`
  - 필드/생성자에 `TeamArchiveGuard` 주입
  - restoreVersion: file fetch 후 `teamArchiveGuard.assertNotArchived(file.scopeType, file.scopeId)`

### 검증 참조

- 신규: `backend/src/test/java/com/ibizdrive/file/FileUploadArchivedTeamGuardTest.java`
  - upload + restoreVersion × archived/active/dept 케이스
- 명령어: `./gradlew :backend:test --tests "com.ibizdrive.file.FileUpload*" "com.ibizdrive.file.FileVersion*"`

### 문서 반영

- T6에서 일괄

---

## T6 — Docs sync + closure

### 작업 전 필독

- T1~T5 모두 완료, 전체 backend test 그린
- docs/02 §8 현재 표기: `TEAM_ARCHIVED ... — **예약**: FolderMutationService/FileUploadService의 archive 차단 미구현, 계약 선언만 (team-domain-hardening 이후 follow-on)`

### 원본 코드 참조

- docs/02-backend-data-model.md §8 (line ~2349, TEAM_ARCHIVED row)
- docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md §5.4 (이미 정합 — 변경 없음)
- docs/progress.md

### 구현 대상

- docs/02-backend-data-model.md §8: TEAM_ARCHIVED row의 "예약" 표기 제거. 정상 운영 항목으로 전환:
  - 기존: `... — **예약**: FolderMutationService/FileUploadService의 archive 차단 미구현, 계약 선언만 ...`
  - 신규: `archive된 팀의 폴더/파일에 write(create/upload/move/rename/delete/restore) 시도. (FolderMutationService / FileMutationService / FileUploadService / FileVersionMutationService 가드)`
  - 토스트 문구는 그대로 유지

- docs/progress.md: 세션 기록 추가 (완료 항목, 다음 세션 컨텍스트 등)
- dev-docs-update: 본 task 완료로 dev/active → dev/completed 이전

### 검증 참조

- final: `./gradlew :backend:check`
- final: 전체 테스트 그린 확인

### 문서 반영

- 본 task가 문서 sync 그 자체

---

## 의존성

- T1 → T2 → (T3, T4, T5 병렬 가능) → T6
- T3, T4, T5는 같은 helper(TeamArchiveGuard)를 의존하지만 서로 다른 파일을 수정하므로 subagent 병렬 디스패치 가능
