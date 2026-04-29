---
Last Updated: 2026-04-29
---

# A5 — Context

## SESSION PROGRESS

- 2026-04-29: bootstrap. plan/context/tasks 3파일 작성. dev/active 등록 commit 대기. **현재 active phase = A5.0**.

## Current Execution Contract

- 단일 PR(`feature/a5-file-versions`) — A5.0~A5.3 모두 같은 worktree.
- 매 phase commit은 `feat(A5.x)` 또는 `docs(A5.x)` 접두어. closure는 `chore(A5)`.
- 매 commit 직후 `git status --short` + `gradle test` 변경 영역 한정 GREEN 확인.
- A4/A3/A2 회귀 테스트는 phase 종료 직전 전체 `gradle test` 1회 GREEN 확인.
- POST `/api/files/:id/versions`, restore, File mutation은 **명시적으로 A6/별도 트랙 이월** — 본 worktree에서 절대 손대지 않음.

## 현재 active task

- **A5.0** — docs/02 §7.7 GET versions 응답 스키마 본문 정합 + ADR #29 트리거 마커 1줄 (no-code).

## 다음 세션 읽기 순서

1. `dev/active/a5-file-versions/a5-file-versions-context.md` (본 파일) — 진입점.
2. `dev/active/a5-file-versions/a5-file-versions-plan.md` — 단계 지도와 acceptance criteria.
3. `dev/active/a5-file-versions/a5-file-versions-tasks.md` — 현재 phase 체크박스 + 참조 블록.
4. `docs/00 §5 ADR #29` — A5 트리거 결정.
5. `docs/02 §2.5` (file_versions schema) + `§7.7` (file API GET versions).
6. `backend/src/main/resources/db/migration/V5__folders_files_permissions.sql` line 89~119.
7. `backend/.../file/FileItem.java` (currentVersionId 코멘트).
8. `docs/progress.md` 최상단 A4 closure block — accepted-deviation 항목 + evaluator 보존 정책.

## 핵심 파일과 역할

### 신설 (A5)

- `backend/src/main/java/com/ibizdrive/file/FileVersion.java` — JPA entity (A5.1).
- `backend/src/main/java/com/ibizdrive/file/VersionScanStatus.java` — scan_status enum (`PENDING/CLEAN/INFECTED/ERROR`) (A5.1).
- `backend/src/main/java/com/ibizdrive/file/FileVersionRepository.java` — `findByFileIdOrderByVersionNumberDesc`, `existsByStorageKey` (A5.1).
- `backend/src/main/java/com/ibizdrive/file/FileVersionController.java` — `GET /api/files/:id/versions` (A5.2).
- `backend/src/main/java/com/ibizdrive/file/dto/FileVersionDto.java` — list 응답 항목 (A5.2).
- `backend/src/test/java/com/ibizdrive/file/FileVersionRepositoryTest.java` — Testcontainers 단위 테스트 (A5.1).
- `backend/src/test/java/com/ibizdrive/file/FileVersionControllerTest.java` — integration 권한 매트릭스 (A5.2).

### 변경 가능

- `backend/.../file/FileItem.java` — `currentVersionId` 매핑 승격 검토 (A5.1, KISS 평가 후 결정).
- `docs/02-backend-data-model.md` §7.7 — 응답 스키마 본문 보강 (A5.0).
- `docs/00-overview.md` §5 ADR #29 — 트리거 마커 + closure status (A5.0/A5.3).
- `docs/progress.md` — phase별 진행 + closure (A5.0~A5.3).

### 보존 (절대 변경 금지)

- `backend/.../permission/IbizDrivePermissionEvaluator.java` — A4.3 resource-level evaluator. SpEL `hasPermission(#id, 'file', 'READ')` 그대로.
- `backend/src/main/resources/db/migration/V5__folders_files_permissions.sql` — V5 SQL 무수정. A5는 entity/endpoint만 추가, 마이그레이션 없음.
- `backend/.../audit/PermissionAuditListener.java` — A4.4. read-only A5에서 신규 emit 없음.

## 중요한 의사결정

- **A5 단일 PR (분할 없음)** — A4의 2 PR 분할(ADR #27)은 13~19 commits 규모 반영. A5는 4~6 commits 추정 → KISS.
- **POST /versions / restore / File mutation은 A6/별도 트랙 이월** — S3 multipart + tus-java-server + VERSION_CONFLICT 패턴(docs/02 §6.7) 의존, 단일 PR 비대화 회피.
- **soft-deleted 파일의 versions 조회 = 404** — 휴지통 UI 명세(docs/01 §13) 미연계, 안전 차단(plan §리스크).
- **FILE_VERSION_CREATE audit enum 신설은 A6** — A5는 read-only.
- **`FileItem.currentVersionId` 매핑 승격은 A5.1 KISS 평가 후 결정** — list endpoint가 매핑 없이도 동작하면 보류, A6에서 자연스러운 시점에 승격.

## 빠른 재개 안내

```bash
# 1. worktree 확인
cd C:/project/IbizDrive
git status --short
git log --oneline -3

# 2. 현재 phase 확인
cat dev/active/a5-file-versions/a5-file-versions-context.md  # SESSION PROGRESS 최하단

# 3. 해당 phase tasks 체크박스 확인
cat dev/active/a5-file-versions/a5-file-versions-tasks.md  # 미완료 [ ] 항목

# 4. 작업 전 필독 + 원본 코드 참조 섹션 따라 read

# 5. 구현 → gradle test (해당 영역) → commit
./gradlew :backend:test --tests FileVersion*
```
