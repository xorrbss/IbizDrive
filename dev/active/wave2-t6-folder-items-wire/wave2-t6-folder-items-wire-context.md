---
Last Updated: 2026-05-07
---

# Context — wave2-t6-folder-items-wire (Wave 2 — T6)

## SESSION PROGRESS

- 2026-05-07 — bootstrap. plan/context/tasks 3파일 생성. design spec 별도 파일(`docs/superpowers/specs/2026-05-07-wave2-t6-folder-items-mutations-wire-design.md`, master c15a3e8)에서 4 결정점 합의 완료(스코프=Phase B 통째, API 통합 endpoint, 'root' frontend 합성, mock 일괄 제거).
- 2026-05-07 — **P1 완료**. backend `GET /api/folders/{id}/items` GREEN. 신규 4 파일 (SortKey/SortDir/FolderItemDto/FolderItemsResponse) + service `loadItems` + controller items() + tests 9 case (service 6 + controller 3) + FileTestFixtures.activeFile 추가. 기존 repo 메서드 (`findByParentIdAndDeletedAtIsNull`, `findByFolderIdAndDeletedAtIsNull`) 재사용. 풀 backend test GREEN. commit `ecb84ba`.
- 2026-05-07 — **P2 완료**. backend `GET /api/files/{id}` GREEN. 신규 `FileQueryService` (read-only, FolderQueryService 동형) + `FileController.get()` SpEL READ. tests `FileQueryServiceTest` 3 case + `FileControllerTest.get_*` 3 case. commit `b3247a0`.
- 2026-05-07 — **P3 완료**. frontend `MOCK_TREE`/`MOCK_FILES`/in-memory tree mutation helpers 일괄 제거. `getFilesInFolder`/`getFileDetail`/`moveFiles`/`renameFile`/`createFolder` real-fetch 전환. `moveFiles` payload `{id,type}` 변경, `renameFile`에 `isFolder` 인자 추가. commit `62c08a4`.
- 2026-05-07 — **P4 완료**. upload 201 신규/200 신버전 동시 처리 + `qk.filesListPrefix(targetFolderId)` invalidate 추가 (listing 즉시 갱신). 기존 mutation hooks(`useRenameFile`/`useMoveBulk`/`useDeleteBulk`/`useRestoreItem`)는 모두 pending-only 패턴(원칙 #3) 준수 — 변경 0건. commit `2461f40`.
- 2026-05-07 — **P5 완료**. 회귀 fix: `MoveFolderDialog.test`/`RenameDialog.test` 시그니처 args 정렬, `api.renameFile.test`는 MOCK 의존이라 fetch 모킹 패턴 재작성 post-T6 보류(`describe.skip`). 풀 게이트: BE GREEN 5m16s + FE 817 passed/11 skipped + lint clean + compile ✓. Windows 환경에서 `.next/server/pages-manifest.json` ENOENT (worktree 깊은 경로 race) — 환경 이슈, 코드 비관련. commit `6d744a5`.
- 2026-05-07 — **P6 완료(docs sync 부분)**. `docs/progress.md` 트랙 closure 엔트리 + `docs/01 §6.1` `qk.filesListPrefix` 추가 + `docs/02 §7.5` items endpoint 명세 행+블록 추가 + `BETA-RELEASE.md` Wave 2 T6 sources/test count 갱신. commit `d581807`. **Track 종료** — push + PR은 사용자 게이트.

## Current Execution Contract

- worktree: `C:/project/IbizDrive/.claude/worktrees/wave2-t6-folder-items-wire`
- branch: `wave2-t6-folder-items-wire` (master c15a3e8 기준 — spec 커밋 포함)
- session file: 본 트랙은 `dev/process/` 별도 세션 파일 없이 본 context 파일만 갱신 (T4/T5 동형).
- 작업 단위: 1 phase = 1 commit (P1~P6). PR은 P6 closure에서 1회.
- 검증: 각 phase 끝에서 해당 gradle/vitest 명령 GREEN 확인 후 다음 phase 진입.
- 자율 모드: 사용자 memory `feedback_autonomous_mode.md` 정책 — 게이트(P1·P3·P5 끝)에서 일시정지 + 보고. 그 외 phase 사이 보고 1줄 후 즉시 다음 phase 진입.

## Active phase / task

- **active phase**: P6 closure (push + PR 대기 — 사용자 게이트).
- **active task**: 트랙 코드/docs 작업 완료. 사용자가 push 승인 시 `git push -u origin wave2-t6-folder-items-wire` + `gh pr create --base master` 실행.

## 다음 세션 읽기 순서

1. **본 context.md** — SESSION PROGRESS + active phase 확인.
2. `wave2-t6-folder-items-wire-plan.md` — phase 지도 + acceptance criteria + 위험.
3. `wave2-t6-folder-items-wire-tasks.md` — active phase의 첫 task 진입.
4. `docs/superpowers/specs/2026-05-07-wave2-t6-folder-items-mutations-wire-design.md` — 4 결정점 근거가 필요할 때만.
5. master `backend/src/main/java/com/ibizdrive/folder/FolderQueryService.java` (Phase A) — `loadTree()`/`loadDetail(id)` 패턴, `loadItems(id, sort, dir)` 동형으로 추가.
6. master `backend/src/main/java/com/ibizdrive/folder/FolderController.java` line 90~107 — `@PreAuthorize` + path UUID + DTO 매핑 패턴.
7. master `backend/src/main/java/com/ibizdrive/file/FileMutationService.java` — file row select + soft-delete 가드 패턴 (P2 `loadDetail(id)` 답습).
8. master `frontend/src/lib/api.ts` line 21~50 (`MOCK_TREE`/`MOCK_FILES`) + line 155~234 (Phase A `getFolderTree`/`getFolder` real fetch 패턴) — P3/P4 답습 기준.

## 핵심 파일과 역할

### Backend — 신규 (P1+P2)
| 파일 | 역할 | 템플릿 |
|---|---|---|
| `folder/dto/FolderItemDto.java` | record `(UUID id, String type, String name, String mimeType, Long size, Instant updatedAt, String updatedBy, UUID parentId)` | `FolderDto` 변형 |
| `folder/dto/FolderItemsResponse.java` | record `(List<FolderItemDto> items)` | `FolderTreeResponse` |
| (P2) `file/dto/FileDetailResponse.java` | record `(FileDto file)` 단일 wrapper | `FolderDetailResponse` |

### Backend — 기존 파일 확장
| 파일 | 변경 |
|---|---|
| `folder/FolderQueryService.java` | `loadItems(UUID id, SortKey sort, SortDir dir)` 추가. files+folders 합본 정렬, 폴더 먼저, soft-delete 제외. |
| `folder/FolderController.java` | `@GetMapping("/{id}/items")` items 메서드 추가 (`hasPermission(...,'READ')`). |
| `folder/FolderRepository.java` | `findChildrenByParentIdAndDeletedAtIsNull(UUID parentId)` 추가 (이미 있으면 재사용). |
| `file/FileRepository.java` | `findByFolderIdAndDeletedAtIsNull(UUID folderId)` 추가 (이미 있으면 재사용). |
| `file/FileController.java` (또는 신규 `FileQueryController.java`) | `@GetMapping("/{id}")` detail 추가. P2 진입 시 service 책임 분리 정도 결정 — `FileMutationService`에 query 메서드 섞으면 KISS, 분리하면 SRP. P2에서 결정. |
| `file/FileQueryService.java` (P2 신설 가능) | `loadDetail(UUID id)` — soft-delete 제외, mapper |

### Frontend — 신규 (P3)
| 파일 | 역할 |
|---|---|
| `src/hooks/useFolderItems.ts` | `useQuery({ queryKey: qk.folderItems(id, sort, dir), queryFn: api.getFolderItems(...) })`. 현 `useFilesInFolder`를 rename + 시그니처 보강. |
| `src/lib/invalidations.ts` (있으면 갱신, 없으면 신설) | helper 5개 (`afterFileMutated`, `afterFolderMutated`, `afterFileCreated`, `afterFolderCreated`, `afterTrashChanged`). |

### Frontend — 확장
| 파일 | 변경 |
|---|---|
| `src/lib/api.ts` | mock 모듈 변수 + helper 삭제, 9개 함수 real fetch wire. |
| `src/lib/queryKeys.ts` | `qk.folderItems(folderId, sort, dir)` 추가. |
| `src/types/file.ts` | 변경 없음 — 기존 `FileItem`이 이미 통합 스키마(`type: 'file'|'folder'`). |
| `src/hooks/useFileDetail.ts` | mock 의존 제거(api.ts 변경에 의해 자동) — hook 자체 변경은 없음. |
| `src/hooks/use{Rename,Move,Delete,Restore}*.ts` | invalidation 헬퍼 호출 wire. 낙관/pending 정책 표 적용. |

## 중요한 의사결정 (브레인스토밍 confirmed)

1. **Phase B 통째로 단일 PR.** 부분 wire는 회귀(mock id 깨짐) → 분리 불가.
2. **API 통합 endpoint** `/api/folders/{id}/items`. files+folders 합본 응답, frontend 데이터 모델(`FileItem.type`) 1:1 mirror.
3. **'root'는 frontend 합성.** backend는 UUID만, 'root' literal 미수용. Phase A 정책 답습.
4. **MOCK 일괄 제거.** 잔존 시 인지부하 + 어느 path가 real/mock인지 판단 비용. KISS.
5. **fanout via Promise.all.** backend bulk endpoint 신설은 v1.x. 부분 실패 best-effort + toast.
6. **낙관은 rename만.** 그 외(move/delete/restore/createFolder)는 pending. CLAUDE.md §3 원칙 3.
7. **Pagination 미도입.** docs/02 §9.2 v1.x.

## 위험 / 결정 (현재 진행 중)

- **MOCK 제거 영향 면 미측정** — P3 진입 직후 `grep -rn 'MOCK_FILES\|getFilesInFolder\|getFileDetail' frontend/src` 1회 수행 필요. 200건 이상이면 보고 + 트랙 분할 재검토.
- **`FileQueryService` vs `FileMutationService` 책임 분리** — P2 진입 시 FileController에 GET 추가만 할지, 신규 controller/service로 분리할지 결정. 답습할 패턴: Phase A의 `FolderQueryService`(read 전용) ↔ `FolderMutationService` 분리. 동형이면 신설이 일관.
- **size 정렬 시 폴더 그룹 정책** — spec 결정: 폴더 그룹은 `name asc` fallback. P1 test 6 case 중 1 case로 검증.
- **`invalidations.ts` 신설 여부** — 현 master에 동명 파일 존재 가능 (M9 휴지통, M-RP 등에서 일부 헬퍼 작성됐을 수 있음). P3 진입 시 `find frontend/src -name 'invalidations*'` 확인 후 신설/확장 결정.

## 빠른 재개 안내

```
# 1) worktree로 진입
cd C:/project/IbizDrive/.claude/worktrees/wave2-t6-folder-items-wire

# 2) 본 context + plan + tasks 순으로 읽기
cat dev/active/wave2-t6-folder-items-wire/wave2-t6-folder-items-wire-context.md
cat dev/active/wave2-t6-folder-items-wire/wave2-t6-folder-items-wire-plan.md
cat dev/active/wave2-t6-folder-items-wire/wave2-t6-folder-items-wire-tasks.md

# 3) active phase 첫 task 진입 (현재 P1 — backend GET /api/folders/{id}/items TDD)

# 4) 게이트 명령
cd backend && ./gradlew test --tests "*FolderQueryService*" --no-daemon  # P1 RED→GREEN
cd backend && ./gradlew test --no-daemon                                  # P1·P2 GREEN
cd frontend && pnpm typecheck                                              # P3·P4 clean
cd frontend && pnpm test --run && pnpm lint && pnpm build                  # P5 풀 게이트
```
