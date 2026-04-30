---
Last Updated: 2026-04-30
Status: ✅ A8.1 완료 — A8.2 진입 대기
---

# A8 — Trash Listing + Manual Purge — Context

## SESSION PROGRESS

### 2026-04-30 (bootstrap → A8.0 완료)
- A7 closure(`d539640`) 후 A8 진입. worktree `.claude/worktrees/a8-trash-manage` 생성, branch `feature/a8-trash-manage` master 기준.
- 사용자 요구 확정: GET `/api/trash` + DELETE `/api/trash/:type/:id` 2개. bulk DELETE는 out-of-scope.
- docs/02 §7.11 정독 → restore endpoint drift 발견(`/api/trash/:id/restore` doc vs `/api/files/:id/restore` & `/api/folders/:id/restore` code) → A8.0에서 docs 정정 완료.
- AuditEventType enum 검사 → `FILE_PURGED`/`FOLDER_PURGED` 정의됐으나 사용처 0(A8 활성화 대상). `SYSTEM_PURGE_EXECUTED`는 A7에서 발행.
- SSE infra 부재 확인(`sse`/`Sse`/`EventBus` 클래스 0) → A8도 audit-only. SSE emission은 별도 milestone deferred로 docs 명시.
- ADR #32 신설 — manual purge URL `:type/:id` + per-row audit 활성화 + bulk 미구현/별도 트랙 + SSE deferred + GET 권한 후처리 + restore drift 정정. 6개 핵심 결정 박제.
- bootstrap commit `7391547` (게이트 0 통과).
- A8.0 commit `27d42f6` (게이트 1 통과) — docs/00 §5 #32 추가 + docs/02 §7.11 재작성 + docs/02 §7.13.1 footnote + docs/01 §13.2 backend backlink.

### 2026-04-30 (A8.1 완료)
- Trash 도메인 신규 패키지 `com.ibizdrive.trash`: `TrashItemType` enum + `TrashItemDto` record + `TrashPage` record + `TrashCursor` record(base64 url-safe codec) + `TrashQueryService` + `TrashController`.
- `FileRepository.findTrashedPage` / `FolderRepository.findTrashedPage` 추가 — `(deletedAt, id) DESC` tuple cursor + native query (Postgres NULL OR 단축평가로 첫 페이지 short-circuit).
- DTO field 정정: 계획상 `originalPath`(string)였으나 N+1 query 발생 → `originalParentId`(UUID)로 수정. frontend가 folderTree 캐시로 path 해석 (KISS).
- 권한 후처리 — `PermissionService.effectivePermissions(role)` ROLE 경로 short-circuit + `PermissionResolver.isGranted(actorId, "file"|"folder", id, DELETE)` resource-level fallback. ADMIN → 전부, MEMBER → grant 보유분만.
- merge sort: type=null 시 file/folder 두 결과를 in-memory `deletedAt DESC, id DESC` 정렬. 동일 ms timestamp 충돌은 frontend dedup으로 흡수(ADR #32 박제).
- pure Mockito 단위 테스트 12건 GREEN — `TrashControllerTest`(6: type 파싱 5 + cursor echo 1) + `TrashQueryServiceTest`(6: empty/type filter ×2/ADMIN 전체/MEMBER 후처리/cursor round-trip/invalid cursor 400). FolderControllerTest 패턴 따름.
- 패키지-protected 생성자 우회용 fixture: `com.ibizdrive.file.FileTestFixtures` + `com.ibizdrive.folder.FolderTestFixtures` 2개 — 같은 패키지에 두어 entity no-arg 생성자 호출. 프로덕션 코드 visibility 변경 회피.
- full `./gradlew test` GREEN — A1~A7 회귀 0.

## Current Execution Contract

- **자율 모드**: 사용자가 "물어보지 말고 자율 수행해" 유지 지시. 게이트 통과 시 자동 다음 phase 진입. PR 생성 직전 게이트만 사용자 승인 대기.
- **단일 PR / squash merge**: A2/A3/A5/A6/A7과 동일 패턴. 추정 5~8 commits.
- **TDD 순서**: A8.1/A8.2는 RED→GREEN. Testcontainers 사용. A1~A7 회귀 0.
- **CLAUDE.md §3 원칙 강제**: 편법 금지(원칙 8) — bulk endpoint 미구현은 "out-of-scope 별도 트랙"으로 명시(은폐 아님). SSE emission deferred는 docs footnote로 명시(원칙 9).
- **컨텍스트 보고**: phase 종료 + commit + 다음 phase 진입 시 한 줄 보고. 60% 임계 도달 시 dev-docs-update 후 핸드오프.

## 현재 active task

- **A8.2** — `DELETE /api/trash/:type/:id` manual purge. `TrashPurgeService.purgeFile` / `purgeFolder` 신설 + `TrashController.purge` + per-row `FILE_PURGED`/`FOLDER_PURGED` audit emit. A7 leaf-first topo helper 재사용.
- 게이트 조건: 8개 테스트 GREEN + 회귀 0 + commit `feat(A8.2): ...`.

## 다음 세션 읽기 순서

1. `a8-trash-manage-plan.md` 요약 + 단위 분할
2. 이 `a8-trash-manage-context.md` SESSION PROGRESS 최신 항목
3. `a8-trash-manage-tasks.md` 현재 active phase의 체크박스
4. `docs/00-overview.md` §5 ADR (마지막 #31 → A8 = #32)
5. `docs/02-backend-data-model.md` §7.11 (휴지통 endpoint), §7.13.1 (SSE event 표)
6. `docs/03-security-compliance.md` §3 (PURGE 권한 매트릭스)
7. 필요 시 `dev/completed/a7-hard-purge/` 전체 (cascade/audit 패턴)
8. `dev/completed/a6-folder-mutation-delete/` (restore endpoint 위치)

## 핵심 파일과 역할

| 파일 | 역할 | 본 트랙 영향 |
|---|---|---|
| `docs/00-overview.md` §5 | ADR 로그 | A8.0 — #32 신설 |
| `docs/02-backend-data-model.md` §7.11 | 휴지통 endpoint 표 | A8.0 — restore 위치 정정 + DELETE URL `:type/:id` patch + bulk 행 "(미구현, 별도 트랙)" 주석 |
| `docs/02-backend-data-model.md` §7.13.1 | SSE 이벤트 표 | A8.0 — `FILE_PURGED`/`FOLDER_PURGED` 행에 "audit는 A8, SSE emission은 인프라 milestone deferred" footnote |
| `docs/01-frontend-design.md` §13 | 휴지통 UX | A8.0 — backend endpoint URL backlink만(프론트 변경 없음) |
| `docs/03-security-compliance.md` §3 | 권한 매트릭스 | 변경 없음 — PURGE = ROLE ADMIN, 결과 필터 = DELETE 권한 보유. 본 트랙 채택 그대로. |
| `backend/.../audit/AuditEventType.java` | 감사 이벤트 enum | 변경 없음 — `FILE_PURGED`/`FOLDER_PURGED` 이미 정의됨. 사용처 신설(A8.2) |
| `backend/.../file/FileRepository.java` | 파일 repo | A8.1에서 `findTrashedPage` 또는 동등 query 추가 |
| `backend/.../folder/FolderRepository.java` | 폴더 repo | A8.1에서 `findTrashedPage` 추가 |
| `backend/.../file/FileMutationService.java` | restore 구현 위치 | 변경 없음 — A6 자산. purge dispatch는 신설 service에서 처리. |
| `backend/.../folder/FolderMutationService.java` | restore 구현 위치 | 변경 없음 — 동일. |
| `backend/.../purge/HardPurgeService.java` | A7 batch | 변경 없음 — leaf-first topo helper만 A8.2에서 재사용 검토(중복 발생 시점) |

## 신설 예정 파일 (A8.1~A8.2)

| 파일 | 책임 | 의존 |
|---|---|---|
| `backend/.../trash/TrashController.java` | `GET /api/trash`, `DELETE /api/trash/:type/:id` | TrashQueryService, TrashPurgeService |
| `backend/.../trash/TrashQueryService.java` | 페이지네이션 + 권한 후처리 | FileRepository, FolderRepository, PermissionEvaluator |
| `backend/.../trash/TrashPurgeService.java` | 단건 hard delete + per-row audit emit | FileRepository, FolderRepository, FileVersionRepository, AuditWriter |
| `backend/.../trash/TrashItemDto.java` | 응답 DTO `{ id, name, type, deletedAt, purgeAfter, originalPath }` | — |
| `backend/.../trash/TrashItemType.java` | enum `FILE` / `FOLDER` (URL `:type` validation) | — |
| `backend/src/test/java/.../trash/TrashControllerTest.java` (또는 IT) | E2E 테스트 | Testcontainers |

(파일 신설은 KISS 원칙으로 최소 → 통합 가능 시 단일 service로 합치는 옵션 A8.1 시작 시 재평가)

## 중요한 의사결정

1. **URL `:type/:id`**(사용자 요구) vs docs `/:id`(drift) → **사용자 요구 채택**. 이유 plan.md 참조. ADR #32에 박제.
2. **bulk DELETE /api/trash 미구현** → 별도 트랙. docs 표 4행은 보존, 행 끝 "(미구현, 별도 트랙)" 주석.
3. **SSE FILE_PURGED/FOLDER_PURGED emission deferred** → docs/02 §7.13.1 footnote. service에 `// TODO: SSE emit` hook 주석. 인프라 milestone에서 1줄 활성화.
4. **Restore endpoint URL drift 정정** — docs/02 §7.11 line 1114은 `/api/trash/:id/restore`였으나 A6 코드는 per-resource. **코드가 진실의 출처**(원칙 6 — DB/코드 우선) → docs 정정.
5. **GET /api/trash 권한 = `isAuthenticated()` + 결과 후처리** — DB-level join은 MVP overkill. 페이지 한도 < 권한 보유 row 수 가정. 큰 dataset 시 별도 ADR.
6. **TrashPurgeService 분리** — 단건 vs batch는 책임 분리. A7 helper 재사용은 중복 발생 시점에 추출.

## 빠른 재개 안내

- 다음 작업자는 **A8.0 phase의 tasks.md 체크박스 1번**부터 시작 (ADR #32 본문 작성 → docs/02 §7.11 patch → docs/02 §7.13.1 footnote → docs/01 §13 backlink → commit).
- 게이트 1 통과 = `docs(A8.0): ...` 1 commit이면 충분. PR은 closure(A8.3)에서.
- 막히면 plan.md "현재 상태 분석" + "도메인 정책" 재독 → 충분치 않으면 사용자에게 ABORT.

## 재개 명령 박스 (9줄)

```text
cd C:\project\IbizDrive\.claude\worktrees\a8-trash-manage
git status                                # baseline 확인
cat dev/active/a8-trash-manage/a8-trash-manage-plan.md       # 요약 + 분할
cat dev/active/a8-trash-manage/a8-trash-manage-context.md    # SESSION PROGRESS
cat dev/active/a8-trash-manage/a8-trash-manage-tasks.md      # 현재 phase 체크박스
# A8.0 진입: ADR #32 + docs/02 §7.11 + §7.13.1 + docs/01 §13 backlink
# A8.1 진입: TrashController.list + Repository.findTrashedPage + Testcontainers
# A8.2 진입: TrashController.purge + TrashPurgeService + audit emit + Testcontainers
# A8.3: closure (PR + dev-docs archive)
```
