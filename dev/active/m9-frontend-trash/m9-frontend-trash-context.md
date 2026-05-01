---
Last Updated: 2026-05-01
Status: 🟢 ACTIVE — 게이트 5 통과 + rebase onto master (PR #16/A10 흡수) 완료 → M9.5 PR 생성 단계
---

# M9 — Frontend 휴지통 통합 — Context

## SESSION PROGRESS

- **2026-04-30 bootstrap** — plan/context/tasks 3파일 작성 + worktree `feature/m9-frontend-trash` 생성 (master `a952f78` = A8 closure 기준).
  - 동기: A8 closure progress 블록의 "다음 단계 — Frontend M9 (휴지통 UI 통합) bootstrap" 진입.
  - 범위: frontend 한정. backend 코드 변경 0. A6/A7/A8 endpoint를 `/trash` 페이지 + Undo toast로 통합.
  - 분기점: `useDeleteBulk` Mock vs 실 backend 마이그레이션 → M9.0 게이트에서 사용자 확인.
- **2026-04-30 게이트 0 통과** — 사용자 plan 리뷰 OK. 단일 PR + 6 sub-phase 구조 확정.
  - 노트: 별도 세션이 짧게 3-PR 분할 대안 제시했으나 패턴 이탈 비용으로 철회. 기존 plan 그대로.
- **2026-05-01 M9.0 완료 (`6e67785`)** — 게이트 1 통과.
  - 변경: `qk.trash()` + prep 키(qk.search/searchResults/storageQuota/trashList/permissions(nodeId?)).
  - `invalidations.afterDelete` 확장 → filesListPrefix + trash + search + folderTree (4건).
  - `invalidations.afterRestore({folderIds?})` + `invalidations.afterPurge` 신설 (afterTrashAction 단일 헬퍼 → 의미 분리).
  - **Self Review 반영**: afterRestore/afterDelete에 folderTree 추가 (folder cascade restore/soft-delete 시 사이드바 stale 방지).
  - **분기 (A) 채택**: backend `DELETE /api/files|folders/:id` 가용 확인 → M9.1에서 `api.deleteBulk` Mock 제거 + 실 fetch 마이그.
  - 검증: queryKeys.test.ts 12 tests GREEN, 회귀 0 (전체 415 tests), typecheck/lint 통과.
  - **lesson**: 이 worktree(`feature/m9-frontend-trash`)에서 작업 시 frontend 편집 경로는 반드시 `.claude/worktrees/m9-frontend-trash/frontend/...`. main repo `C:/project/IbizDrive/frontend/...`이 아님. 헷갈리면 `git rev-parse --show-toplevel`로 확인.
- **다음**: M9.1 — types/trash.ts + api.getTrash/restoreFile/restoreFolder/purgeTrashItem + (분기 A) api.deleteBulk Mock 제거 + 실 fetch 마이그 + api.trash.test.ts ≥6 GREEN.
- **2026-05-01 M9.1 완료 (`7bd63f2`)** — 게이트 2 통과 (사용자 OK → commit + dev/process 정리).
- **2026-05-01 M9.3 완료** — 게이트 4 통과 (/trash 페이지 + TrashTable + TrashLink 9 tests GREEN, 회귀 0).
  - 신설: `app/(explorer)/trash/{page,ClientTrashPage}.tsx` (server entry → client 분리, files 라우트 패턴 mirror).
  - 신설: `components/trash/{TrashTable,TrashRowActions,TrashLink}.tsx`.
  - 신설: `lib/folderTreeUtils.ts`에 `findFolderPath(node, id): FolderNode[] | null` 추가 (M9.3 originalParentId path 해석 + 향후 재사용).
  - 통합: `app/(explorer)/layout.tsx` `<aside>` 하단에 `TrashLink` mount (mt-auto + border-top).
  - **ADMIN 가드는 `usePermission().admin` 사용** — useEffectivePermissions hook 미구현. M7 권한 hook 도입 시 1줄 교체. plan §의사결정 #3 정합.
  - 4상태: isLoading=로딩 / isError=에러 alert / items.length===0=Empty "휴지통이 비어있습니다" / success=row 렌더.
  - 검증: pnpm test 41 files / 360 tests GREEN, 회귀 0. typecheck/lint 통과.
  - **lesson**: 본 프로젝트는 jest-dom 미설정. 컴포넌트 테스트 단언은 `toBeTruthy/toBeNull/getAttribute/textContent` 패턴 사용 (audit/AuditTable.test 따름).
- **2026-05-01 M9.2 완료** — 게이트 3 통과 (TanStack Query hooks 14 tests GREEN, 회귀 0).
  - 신설: `frontend/src/hooks/useTrashList.ts` (`useInfiniteQuery` + cursor + type 필터; queryKey는 hook 사이트에서 `[...qk.trashList(), type]` 인라인 — qk 미수정).
  - 신설: `frontend/src/hooks/useRestoreItem.ts` — type 분기 + `invalidations.afterRestore({folderIds:[sourceFolderId]})` 정밀/보수 무효화.
  - 신설: `frontend/src/hooks/usePurgeTrashItem.ts` — `invalidations.afterPurge` (qk.trash() 단독).
  - 신설 테스트 3종 (14 GREEN). useTrashList(6) / useRestoreItem(4) / usePurgeTrashItem(4).
  - 검증: pnpm test 39 files / 351 tests GREEN, 회귀 0. typecheck/lint 통과.
  - **lesson**: M9.2 plan은 단일 `afterTrashAction` 가정이었으나 M9.0에서 `afterRestore`/`afterPurge`로 분리됨 — 구현 시 `invalidations`의 실제 export로 정합. tasks.md 본문에 정합 노트 추가.
  - 신설: `frontend/src/types/trash.ts` (TrashItemType/TrashItem/TrashPage, backend TrashItemDto/TrashPage 1:1).
  - `frontend/src/lib/api.ts` 추가: `getTrash` / `restoreFile` / `restoreFolder` / `purgeTrashItem` + `softDeleteFile` / `softDeleteFolder` (분기 A 마이그). `deleteBulk` Mock 제거. `buildApiError` helper 추가 — backend envelope `{error:{code}}` → `err.status + err.code` surface (RESTORE_CONFLICT 분기용).
  - `useDeleteBulk` 시그니처 `ids: string[]` → `items: { id, type }[]`로 변경. Promise.all per-item fetch.
  - 호출부 갱신: `BulkActionBar` (handleDelete + onSuccess vars.items.length 메시지) + `FileTable` (Delete 단축키 분기). 두 곳 모두 캐시 items에서 type 동봉. 캐시 미스 시 'file' 폴백.
  - 신설: `frontend/src/lib/api.trash.test.ts` — 15 tests GREEN (요건 ≥6 초과). `useDeleteBulk.test.ts`는 mock을 softDelete*로 교체.
  - 검증: pnpm test 36 files / 337 tests GREEN, 회귀 0. typecheck/lint 통과.
  - **lesson**: useDeleteBulk 호출부가 BulkActionBar 외에 FileTable 키보드 단축키에도 있어 두 곳 동시 마이그가 필요했다. 다음 마이그 시 호출부 grep 선행.
- **2026-05-01 M9.4 완료** — 게이트 5 통과 (Undo toast wiring 4 tests GREEN, 회귀 0).
  - 변경: `BulkActionBar.tsx` `useDeleteBulk` onSuccess에 `toast.success(msg, { duration:5000, action:{ label:'되돌리기', onClick:() => undoDelete(items, folderIdAtStart, qc) } })` 추가.
  - 신설 헬퍼: `undoDelete(items, folderId, qc)` — type별 `Promise.all([api.restoreFile|restoreFolder])` + `invalidations.afterRestore(qc, {folderIds:[folderId]})` + 성공/실패 toast (RESTORE_CONFLICT → '같은 이름의 항목이 이미 존재합니다').
  - 신설 테스트 4건: duration+action.label / action.onClick type 분기 / Undo 성공 후 복원 toast / 409 RESTORE_CONFLICT 분기. 기존 4 rename 테스트 유지 → 8 total in BulkActionBar.test.tsx.
  - 검증: pnpm test 41 files / 364 tests GREEN, 회귀 0. typecheck/lint 통과.
  - **lesson**: 본 프로젝트는 `vi.mocked(toast).success.mock.calls` 패턴이 TS narrow 실패 — `@/test/mocks/sonner`의 `toastSpy('success').mock.calls`가 표준. 다음 toast 호출 검증 시 toastSpy helper 선행.
- **2026-05-01 rebase onto origin/master (PR #16/A10 흡수)** — 게이트 5와 M9.5 사이에 origin/master가 9 commit 진행 (PR #16/F1.1/A9/A10). 충돌 해소 후 회귀 0 (439 tests GREEN).
  - 배경: PR #16(`f77f886`)이 우리 작업 동안 mock 기반 휴지통 hooks(`useRestoreBulk`/`usePurgeBulk`/`useTrashHooks.test.tsx`)를 추가 + M14 권한 시스템(`usePermission` useQuery 기반, UPPER_SNAKE_CASE flags) + M11 검색(`api.searchFiles`) + M15 storage(`api.getStorageQuota`) + Share UI(`useShareUiStore`). 우리 트랙은 동일 영역에서 real-backend 마이그를 진행 중이었음.
  - 충돌 해소 전략: M9 real-backend 구현(ours)을 base로 + PR #16의 무관 추가(searchFiles/getEffectivePermissions/getStorageQuota/StorageBar/ShareUiStore/Share button)는 cherry-pick 형식으로 흡수.
    - `frontend/src/lib/queryKeys.ts` (4 conflict block) — folderTree 추가는 양측 합의, `afterDelete`/`afterRestore`의 추가 무효화 키는 우리 것 채택.
    - `frontend/src/lib/api.ts` — ours base + master의 `searchFiles`/`getEffectivePermissions`/`getStorageQuota` import 추가.
    - `frontend/src/components/files/BulkActionBar.{tsx,test.tsx}` — ours 채택 후 새 `usePermission()` mock 추가(admin preset 8 권한). `useSortParams` mock에 `setSort: vi.fn()` 추가(M11이 `setSort`를 hook 시그니처에 추가).
    - `frontend/src/app/(explorer)/layout.tsx` — duplicate `TrashLink` import 제거 + master의 `<StorageBar />`를 `<TrashLink />` 다음에 mount.
  - **orphan 정리 commit `488ca5a`** — PR #16의 mock 기반 hooks 3종 (`useRestoreBulk.ts` / `usePurgeBulk.ts` / `useTrashHooks.test.tsx`) 삭제. 우리의 real-backend `useRestoreItem` / `usePurgeTrashItem` / `useTrashList`로 대체.
  - **M14 권한 시스템 정합 commit `ba978e5`** — `TrashRowActions.tsx`의 `usePermission().admin` (불리언 stub) → `usePermission().PURGE` (M14 권한 hook 표준 flag) 마이그. docs/03 §3.2 PURGE는 ADMIN role 전용이라 의미적으로 정합. `TrashTable.test.tsx`의 mock도 `{ admin: true/false }` → `{ PURGE: true/false }` 일괄 교체 (3 instances). `BulkActionBar.test.tsx`의 `useSortParams` mock에 `setSort: vi.fn()` 추가.
  - 검증: pnpm test 47 files / 439 tests GREEN, 회귀 0. typecheck/lint 통과.
  - **out-of-scope blocker (M9 외)**: `pnpm build` 실패 (`useSearchParams() should be wrapped in a suspense boundary at page "/files"`). origin/master(`24a78b2`)에서도 동일 재현 — PR #16(M11 검색) 또는 F1.1(`api.searchFiles`)에서 `/files` 페이지가 `useSearchParams`를 Suspense 외부에서 호출하는 것이 원인. **M9 트랙 책임 외**, 별도 트랙(`fix(F1.x): /files Suspense boundary`)에서 처리 권고. 본 PR description에 명시 + skip 사유 기록.
  - **lesson**: 자율 모드에서 long-running worktree는 origin/master 변경에 노출됨. 게이트 사이마다 `git fetch && git log master..HEAD --oneline` 점검 → 충돌 영역 미리 알림. 또한 stub `usePermission`은 향후 M14 도입 시 mock 시그니처 일괄 갱신이 필요하므로 worktree 시작 시 PR 동향(특히 `useQuery`-기반 시스템 hook) 점검 필요.

## Current Execution Contract

- **자율 실행 모드** 활성 (memory/feedback_autonomous_mode.md). 게이트마다 사용자 보고 + 다음 게이트 대기. 본 세션은 사용자가 명시적으로 "단계 3 사용자 plan 리뷰 게이트" 지정 → bootstrap 직후 STOP.
- **Decision style**: 추천안 + 근거. A/B/C 분기 질문 회피 (memory/feedback_decision_style.md).
- **TDD**: RED → GREEN per phase. M9.2부터 hooks RED 테스트 작성 → 구현.
- **단일 PR**: A2~A8 패턴 mirror.
- **Worktree**: `C:/project/IbizDrive/.claude/worktrees/m9-frontend-trash`, branch `feature/m9-frontend-trash`.
- **Backend mock 정책** (memory/feedback_mock_transport.md): backend 응답이 백엔드 미연결 시 fake-XHR 또는 fetch interceptor — 본 트랙은 backend 실 endpoint 100% 가용 → mock 제거 + 실 fetch.

## 현재 active task

- **Phase**: M9.5 진입 — rebase onto master 완료 → PR 생성 단계
- **선행 완료**: M9.0~M9.4 + rebase onto origin/master (PR #16/A10 흡수) + orphan 정리(`488ca5a`) + M14 권한 정합(`ba978e5`).
- **다음**: M9.5 — push + PR 생성 (CI green 확인 + 사용자 승인 → squash merge → archive + worktree 정리). 빌드 blocker(`useSearchParams` Suspense)는 master pre-existing이라 별도 트랙으로 분리 — PR description에 flag.

## 다음 세션 읽기 순서

1. `m9-frontend-trash-plan.md` — 전체 계획 + DoD 10개 + 핵심 결정
2. 본 `m9-frontend-trash-context.md` — SESSION PROGRESS (가장 마지막 항목 = 현재 상태)
3. `m9-frontend-trash-tasks.md` — phase별 체크박스 + 미완료 task 참조 블록
4. `docs/01-frontend-design.md`:
   - §13 (line 776~817) — UX 사양 + backend endpoint backlink (A6/A7/A8)
   - §6 (line 422~511) — queryKeys + 무효화 매트릭스 (line 459~462: 휴지통/복원/영구삭제 행)
5. `docs/02-backend-data-model.md` §7.11 (line 1109~1152) — endpoint 응답 스키마 + 권한 + 에러
6. `docs/00-overview.md` §5 ADR #32 (manual purge `:type/:id`, ADMIN-only)
7. `dev/completed/a8-trash-manage/a8-trash-manage-plan.md` — backend 결정 8개 (M9는 그 위에서 통합)
8. `frontend/src/lib/queryKeys.ts` + `frontend/src/lib/api.ts` (audit fetch 패턴 mirror)
9. `frontend/src/hooks/useDeleteBulk.ts` + `frontend/src/components/files/BulkActionBar.tsx` (Undo wiring 좌표)

## 핵심 파일과 역할

| 파일 | 역할 |
|---|---|
| `frontend/src/app/(explorer)/trash/page.tsx` | (NEW) `/trash` 페이지 — useTrashList + TrashTable 렌더 |
| `frontend/src/components/trash/TrashTable.tsx` | (NEW) 휴지통 테이블 — 4상태 + 행 액션 (복원 / 영구 삭제 ADMIN-only) |
| `frontend/src/components/trash/TrashLink.tsx` | (NEW) Sidebar 하단 진입 링크 |
| `frontend/src/hooks/useTrashList.ts` | (NEW) `qk.trash()` query — cursor + type 필터 |
| `frontend/src/hooks/useRestoreItem.ts` | (NEW) restore mutation — file/folder 분기 + invalidations.afterTrashAction |
| `frontend/src/hooks/usePurgeTrashItem.ts` | (NEW) purge mutation — ADMIN-only |
| `frontend/src/lib/api.ts` | (EDIT) `getTrash` / `restoreFile` / `restoreFolder` / `purgeTrashItem` 추가 (audit fetch 패턴 mirror) |
| `frontend/src/lib/queryKeys.ts` | (EDIT) `qk.trash()` 정의 + `invalidations.afterTrashAction` |
| `frontend/src/types/trash.ts` | (NEW) `TrashItem` / `TrashPage` / `TrashItemType` |
| `frontend/src/components/files/BulkActionBar.tsx` | (EDIT) toast.success 내 `action: 되돌리기` 5초 wiring |
| `frontend/src/hooks/useDeleteBulk.ts` | (READ-ONLY/EDIT) Undo wiring 시 `onSuccess`에 restore 콜백 노출 |
| `docs/01-frontend-design.md` | (EDIT) §13.2 본문에 component 경로 backlink 추가 |

## 중요한 의사결정 (변경 시 docs 동기화)

1. **MOCK → 실 backend fetch 일괄 전환** — A2 audit 패턴 mirror. `api.audit`의 `fetch('/api/admin/audit', { credentials: 'include' })` 형식 그대로. 변경 시: `api.ts` 다른 endpoint도 일관 → 별도 트랙으로 일괄 마이그레이션 ADR.
2. **`qk.trash()` opaque (인자 없음)** — docs/01 §6.1 정의 그대로. cursor/type은 query 함수 내부 인자, 키에는 미포함 → 모든 변종 한 번에 invalidate. cursor 별 페이지 캐시는 TanStack Query infinite query (`useInfiniteQuery`)로 관리.
3. **권한 후처리 신뢰** — backend가 이미 per-row `hasPermission(_, _, 'DELETE')` 후처리 → frontend는 응답 그대로 렌더. ADMIN 영구 삭제 버튼만 `useRoleStore` 또는 `useEffectivePermissions`로 추가 가드. 변경 시: backend 권한 정책 ADR 갱신 후 frontend 게이트 재설계.
4. **`originalParentId` → folderTree 해석** — N+1 회피. `useFolderTree()` 캐시에서 path 빌드. 부모도 trashed면 "원위치 폴더 삭제됨" 폴백. 변경 시: backend가 originalPath 반환하도록 endpoint 수정 → docs/02 §7.11 응답 스키마 patch.
5. **Undo toast 5초 — 단일 sonner action** — `toast.success(msg, { action: { label: '되돌리기', onClick: ... }, duration: 5000 })`. 변경 시: 5초 시한 + 다중 action UX는 별도 디자인 검토.
6. **`useDeleteBulk` Mock 마이그레이션 — 본 트랙 범위 외** — backend `DELETE /api/files/:id` 미연결 시 Mock 유지 + Undo wiring만. M9.0 게이트에서 사용자에게 backend 상태 확인. 변경 시: 별도 트랙 (M-frontend-soft-delete-migration).
7. **RESTORE_CONFLICT 409 — toast 에러 폴백** — MVP는 사용자가 폴더에서 충돌 항목 정리 후 재시도. ConflictDialog는 v1.x. 변경 시: docs/01 §13에 ConflictDialog 사양 신설.
8. **Bulk purge 미구현** — ADR #32. UI에서 "전체 비우기" 버튼 노출 안 함. 변경 시: ADR #32 close + backend `DELETE /api/trash` 트랙.

## 빠른 재개 안내

- 다음 작업자: `m9-frontend-trash-tasks.md`의 첫 미완료 phase로 진입.
- 막히면: 본 context의 "중요한 의사결정" 8개 + plan §리스크 표 확인. 그래도 모호하면 사용자 게이트.
- 검증 명령:
  - 단위 테스트: `cd frontend && pnpm test`
  - 타입체크: `cd frontend && pnpm typecheck`
  - 린트: `cd frontend && pnpm lint`
  - 빌드: `cd frontend && pnpm build`
- 커밋 메시지 컨벤션 (A8 PR #18 mirror):
  - phase 단위: `feat(M9.x): <title>` 또는 `docs(M9.x): <title>`
  - bootstrap: `chore(M9): bootstrap dev-docs (frontend trash integration)`
  - closure: `chore(M9): closure — M9 마일스톤 종료 + dev-docs archive`

## 사용자 plan 리뷰 게이트 (게이트 0)

본 bootstrap 세션은 **plan 리뷰 직전에 STOP**하도록 설계됨. 사용자에게 다음을 보고:

1. 단위 분할 5+1 phase 구성 (M9.0 docs / M9.1 API / M9.2 hooks / M9.3 page / M9.4 Undo / M9.5 closure)
2. out-of-scope 명시 4건 (bulk purge / SSE / originalPath backend / useDeleteBulk Mock 마이그)
3. DoD 10개 + 게이트 7단계
4. 리스크 7개 + 완화

사용자 OK 또는 plan patch 후 → bootstrap commit → M9.0 진입.
