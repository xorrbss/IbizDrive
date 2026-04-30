---
Last Updated: 2026-05-01
Status: 🟢 ACTIVE — 게이트 5 통과 → M9.5 진입 대기
---

# M9 — Frontend 휴지통 통합 — Tasks

## phase 상태

| Phase | 제목 | 상태 |
|---|---|---|
| bootstrap | dev-docs 3파일 + worktree | ✅ done (게이트 0 통과 2026-04-30) |
| M9.0 | docs 정합 + queryKeys `qk.trash()` (no-code/1줄) | ✅ done (게이트 1 통과 2026-05-01) |
| M9.1 | API client 확장 + types | ✅ done (게이트 2 통과 2026-05-01) |
| M9.2 | TanStack Query hooks + 단위 테스트 | ✅ done (게이트 3 통과 2026-05-01) |
| M9.3 | `/trash` 페이지 + TrashTable + TrashLink + 4상태 | ✅ done (게이트 4 통과 2026-05-01) |
| M9.4 | Undo toast wiring (BulkActionBar 5초) | ✅ done (게이트 5 통과 2026-05-01) |
| M9.5 | closure (PR + archive) | 📋 ready |

---

## bootstrap — dev-docs 3파일 + worktree [✅ done]

### 구현 대상

- [x] (1) `git fetch origin master` + A8.3 머지 commit (`0c806c1`) 검증 + closure commit (`a952f78`) 확인
- [x] (2) `git worktree add .claude/worktrees/m9-frontend-trash -b feature/m9-frontend-trash origin/master` (HEAD `a952f78`)
- [x] (3) `dev/active/m9-frontend-trash/` 디렉터리 + `m9-frontend-trash-{plan,context,tasks}.md` 작성
- [x] (4) **사용자 plan 리뷰 게이트** — 통과 2026-04-30 (단일 PR + 6 sub-phase 구조 확정)
- [x] (5) bootstrap commit: `chore(M9): bootstrap dev-docs (frontend trash integration)` (`94dc00c`)

**게이트 0**: 사용자 OK → bootstrap commit → M9.0 진입.

---

## M9.0 — docs 정합 + queryKeys `qk.trash()` 추가 [📋 ready]

**작업 전 필독**:
- 본 tasks.md의 bootstrap 결과
- `docs/01-frontend-design.md` §6.1 (line 422~449 — qk 팩토리 정의 + `qk.trash()` 명시) + §6.2 (line 451~466 — 무효화 매트릭스 line 459~462: 휴지통/복원/영구삭제)
- `docs/01-frontend-design.md` §13 (line 776~817 — UX 사양) — 이미 A8 closure 시점에 endpoint backlink 추가됨, 추가 patch 필요 시 component 경로 backlink만
- `docs/00-overview.md` §5 ADR #32 (manual purge `:type/:id`)
- `frontend/src/lib/queryKeys.ts` — 현재 `qk.trash()` 미정의

**원본 코드 참조**:
- `frontend/src/lib/queryKeys.ts:35` — `qk.audit()` 옆에 `qk.trash()` 정의 위치
- `frontend/src/lib/queryKeys.ts:42~99` — `invalidations` 헬퍼 객체 — `afterDelete` 옆에 `afterTrashAction` 추가

**구현 대상**:
- [x] (1) `qk.trash()` 추가 + 추가 prep 키(`qk.trashList`, `qk.search`, `qk.searchResults`, `qk.permissions(nodeId?)`, `qk.storageQuota`) 사전 정의
- [x] (2) `invalidations.afterRestore(qc, { folderIds? })` + `invalidations.afterPurge(qc)` 신설 (Self Review 결과 `afterTrashAction` 단일 헬퍼보다 의미 분리)
- [x] (3) `invalidations.afterDelete` 확장 — `filesListPrefix` + `trash` + `search` + `folderTree` (4건). 옛 line 91 주석 제거됨.
- [ ] (4) `docs/01-frontend-design.md` §13.2 본문 component 경로 backlink — M9.3 완료 후 일괄 patch
- [x] (5) **`useDeleteBulk` Mock vs 실 backend 분기** — **(A) 채택** (backend `DELETE /api/files/:id`/`/api/folders/:id` 모두 가용 확인). Mock은 (B) 시 Undo→restore 404 모순 → (A) 강제. M9.1에서 일괄 마이그.
- [x] (6) commit: `feat(M9.0): qk.trash + afterRestore/afterPurge + afterDelete 확장 (휴지통 무효화 헬퍼)`

**검증 참조**:
- `pnpm typecheck` 통과 — `qk.trash()` 호출 측 타입 안전
- `pnpm test queryKeys` 회귀 0 — 기존 `queryKeys.test.ts` 케이스 유지

**문서 반영**:
- §13.2 backlink — M9.3 완료 후 일괄 patch (M9.5 PR 본문에서 일괄)

**게이트 1**: ✅ 통과 (2026-05-01) — queryKeys 12 tests GREEN, typecheck/lint 통과, 회귀 0 (415 tests). 분기 (A) 채택. M9.1 진입.

---

## M9.1 — API client 확장 + types [✅ done]

**작업 전 필독**:
- M9.0 결과 — `useDeleteBulk` 분기 (A) or (B)
- `docs/02-backend-data-model.md` §7.11 (line 1109~1152) — endpoint 응답 스키마
- `frontend/src/lib/api.ts:347~407` — `getAuditLogs` (실 backend fetch 패턴, A2.6 정착)
- `frontend/src/types/audit.ts` (typed enum 패턴), `frontend/src/types/file.ts` (타입 export 패턴)
- `dev/completed/a8-trash-manage/a8-trash-manage-plan.md` — backend `TrashItemDto` 필드 + `TrashItemType.from(wire)` validation

**원본 코드 참조**:
- `frontend/src/lib/api.ts:347` — `getAuditLogs` fetch + 401/403 분기 + map 패턴 mirror
- `dev/completed/a8-trash-manage/` — backend response field naming (`originalParentId` 등)

**구현 대상**:
- [x] (1) `frontend/src/types/trash.ts` 신설 — TrashItemType/TrashItem/TrashPage (backend TrashItemDto/TrashPage 1:1)
- [x] (2) `api.getTrash` — fetch GET `/api/trash?cursor=&type=` + credentials include + 응답 map (originalParentId/nextCursor NON_NULL 누락 시 null 폴백)
- [x] (3) `api.restoreFile` — POST `/api/files/:id/restore` + 409 envelope `{error.code:RESTORE_CONFLICT}` 파싱하여 err.code surface
- [x] (4) `api.restoreFolder` — POST `/api/folders/:id/restore` + 409 동일 처리
- [x] (5) `api.purgeTrashItem` — DELETE `/api/trash/:type/:id` + 비-ADMIN 403 status surface
- [x] (6) 분기 A: `api.softDeleteFile` / `api.softDeleteFolder` 신설 + Mock `deleteBulk` 제거. `useDeleteBulk` 시그니처 `ids` → `items: {id,type}[]` 변경. 호출부 2건 마이그: BulkActionBar + FileTable(Delete 단축키).
- [x] (7) `frontend/src/lib/api.trash.test.ts` — 15 tests GREEN (요건 ≥6 초과). `useDeleteBulk.test.ts` mock도 softDelete*로 교체.
- [x] (8) commit: `feat(M9.1): trash API client (getTrash + restore + purge) + types`

**검증 참조**:
- 신규 ≥6건 GREEN + 회귀 0 (`pnpm test`)
- 응답 필드 backend `TrashItemDto` 1:1 일치 — drift 시 백엔드 테스트 케이스 (`TrashControllerTest`) 응답 형태 cross-check

**문서 반영**:
- 응답 스키마 drift 발견 시 docs/02 §7.11 본문 patch

**게이트 2**: ✅ 통과 (2026-05-01) — 15 tests GREEN (요건 ≥6 초과), 회귀 0 (337 tests), typecheck/lint 통과. 분기 (A) 마이그 완료. M9.2 진입.

---

## M9.2 — TanStack Query hooks + 단위 테스트 [✅ done]

**작업 전 필독**:
- M9.1 결과 — `api.*` + types
- `docs/01-frontend-design.md` §6.2 (line 451~466) — 무효화 매트릭스 (휴지통/복원/영구삭제 행)
- `frontend/src/hooks/useDeleteBulk.ts` (markPending + invalidations.afterDelete 패턴)
- `frontend/src/hooks/useAuditLogs.ts` (useQuery + filters 인자 패턴)
- `frontend/src/hooks/useMoveBulk.ts` (mutation + onSuccess invalidate 패턴)

**원본 코드 참조**:
- `useDeleteBulk` 훅 25~45 — onMutate/onSuccess/onError 패턴
- `invalidations.afterFilesMoved` (queryKeys.ts:53) — 무효화 헬퍼 사용 예

**구현 대상**:
- [x] (1) `useTrashList(opts: { type?: TrashItemType })` — `useInfiniteQuery`. queryKey는 `qk.trashList()` 또는 `[...qk.trashList(), type]` (qk 미수정, hook 사이트 인라인). `initialPageParam: undefined`, `getNextPageParam: lastPage => lastPage.nextCursor ?? undefined`.
- [x] (2) `useRestoreItem()` — type 분기 → `restoreFile` or `restoreFolder` → `invalidations.afterRestore(qc, { folderIds: sourceFolderId ? [sourceFolderId] : undefined })`. M9.0 시점 `afterTrashAction` → `afterRestore`/`afterPurge` 분리됨에 맞춰 정합.
- [x] (3) `usePurgeTrashItem()` — `purgeTrashItem(type, id)` → `invalidations.afterPurge` (qk.trash() invalidate).
- [x] (4) 단위 테스트 — 14건 GREEN (요건 ≥10 초과):
  - useTrashList(6): 빈 / 한 페이지 / cursor 다음 페이지 / type 필터 / type 변경 시 queryKey 분리 / 401 에러
  - useRestoreItem(4): file 분기 / folder 분기 / sourceFolderId 미지정 시 qk.files() 보수 무효화 / 409 RESTORE_CONFLICT 후 invalidate skip
  - usePurgeTrashItem(4): file 성공 + qk.trash() invalidate / folder 분기 / 403 비-ADMIN / 404 이미 purge
- [ ] (5) commit: `feat(M9.2): useTrashList + useRestoreItem + usePurgeTrashItem hooks`

**검증 참조**:
- 신규 ≥10건 GREEN + 회귀 0
- 무효화 호출 횟수 = matrix 정합 (sourceFolder + trash + folderTree 1번씩)

**문서 반영**:
- (없음)

**게이트 3**: ✅ 통과 (2026-05-01) — 14 tests GREEN, 회귀 0 (351 tests). typecheck/lint 통과. M9.3 진입.

---

## M9.3 — `/trash` 페이지 + TrashTable + TrashLink [✅ done]

**작업 전 필독**:
- M9.2 결과 — hooks GREEN
- `docs/01-frontend-design.md` §13 (UX 사양) + §11 (line 729~742 — 빈/로딩/에러 상태) + §12 (line 742~775 — 키보드/aria)
- `frontend/src/app/(explorer)/files/[...parts]/page.tsx` (페이지 패턴 mirror — useFilesInFolder + 4상태)
- `frontend/src/components/files/FileTable.tsx` (테이블 가상화/aria 패턴)
- `frontend/src/types/permission.ts` (ADMIN 체크 enum)

**원본 코드 참조**:
- `app/(explorer)/files/[...parts]/page.tsx` — 페이지 컴포넌트 4상태 분기
- `components/files/FileTable.tsx` — `aria-rowcount/rowindex` 정합
- `useEffectivePermissions` 또는 `useRoleStore` — ADMIN 가드 위치

**구현 대상**:
- [x] (1) `app/(explorer)/trash/page.tsx` (server entry) + `ClientTrashPage.tsx` — 헤더 + TrashTable. 4상태 분기는 TrashTable 내부에서 처리.
- [x] (2) `components/trash/TrashTable.tsx` — 단순 list (가상화 X — MVP 충분) + `aria-rowcount/rowindex` + 6 컬럼 (이름/타입/원위치/삭제 시각/영구 삭제 예정/액션) + `hasNextPage` 더 보기 버튼.
- [x] (3) `components/trash/TrashRowActions.tsx` — 복원 + (ADMIN-only) 영구 삭제. ADMIN 가드는 `usePermission().admin` (M7 자리) — useEffectivePermissions hook은 미구현이라 기존 placeholder 사용. 변경 시 docs/03 §3 권한 hook 도입과 함께.
- [x] (4) `components/trash/TrashLink.tsx` — Sidebar 하단 링크. `usePathname` 기반 `aria-current` 분기.
- [x] (5) Sidebar 통합 — `app/(explorer)/layout.tsx`의 `<aside>` 안 `FolderTree` 아래 `mt-auto` border-top 영역에 `TrashLink` 추가.
- [x] (6) `originalParentId` path 해석 — `lib/folderTreeUtils.ts`에 `findFolderPath` 신설. `useFolderTree()` 캐시 사용. 부모 트리에 없으면 "원위치 폴더 삭제됨" 폴백, originalParentId=null이면 "최상위" 표기.
- [x] (7) 단위 테스트 9건 GREEN (요건 ≥7 초과):
  - TrashTable(7): isLoading / isError alert / Empty / 행 렌더 + 원위치 path + aria-rowcount / orphan 폴백 / ADMIN 버튼 가시 / non-ADMIN 버튼 숨김
  - TrashLink(2): href="/trash" / 비활성 라우트에서 aria-current 미지정
  - **page.test.tsx 생략** — 4상태가 TrashTable에서 직접 검증되어 page는 헤더만(중복 회피, KISS).
- [ ] (8) commit: `feat(M9.3): /trash 페이지 + TrashTable + TrashLink + 4상태`

**검증 참조**:
- 신규 ≥7건 GREEN + 회귀 0
- aria 정합 — `aria-rowcount/rowindex` lint rule 통과
- ADMIN/non-ADMIN 가드 — backend 403 시 toast 에러 폴백

**문서 반영**:
- `docs/01-frontend-design.md` §13.2 본문에 component 경로 backlink (`app/(explorer)/trash/page.tsx`, `components/trash/TrashTable.tsx`)

**게이트 4**: ✅ 통과 (2026-05-01) — 9 tests GREEN (요건 ≥7), 4상태 + ADMIN 가드 + Sidebar 진입 모두 충족. 회귀 0 (360 tests). typecheck/lint 통과. M9.4 진입.

---

## M9.4 — Undo toast wiring (BulkActionBar 5초) [✅ done]

**작업 전 필독**:
- M9.3 결과 — `/trash` + restore mutation
- `docs/01-frontend-design.md` §13 line 803~817 — 5초 Undo 토스트 코드 예시
- `frontend/src/components/files/BulkActionBar.tsx:24` — 현재 `toast.success` 위치
- `frontend/src/hooks/useDeleteBulk.ts:30` — `onSuccess` 콜백 노출

**원본 코드 참조**:
- `BulkActionBar.tsx` — `useDeleteBulk` 사용부 + toast.success 호출
- sonner action 사양: `toast.success(msg, { action: { label, onClick }, duration: 5000 })`

**구현 대상**:
- [x] (1) `BulkActionBar.tsx` 소프트 삭제 성공 토스트 — `action: { label: '되돌리기', onClick: () => undoDelete(items, folderIdAtStart, qc) }` + `duration: 5000`
- [x] (2) `undoDelete` 헬퍼 — type별 `api.restoreFile` / `api.restoreFolder` Promise.all + RESTORE_CONFLICT 분기 toast
- [x] (3) `useDeleteBulk` 분기 (A) 후이므로 onSuccess vars `{items: {id,type}[], folderIdAtStart}` 그대로 활용
- [x] (4) Undo 성공 시 `invalidations.afterRestore(qc, { folderIds: [folderId] })` (filesListPrefix + trash + folderTree + search 4건 일괄)
- [x] (5) 단위 테스트 — 4건 GREEN (요건 ≥3 초과):
  - duration:5000 + action.label:'되돌리기' 검증
  - action.onClick → api.restoreFile/restoreFolder type 분기 호출
  - Undo 성공 → 후속 toast.success("복원했습니다")
  - Undo 실패(RESTORE_CONFLICT) → toast.error("같은 이름")
- [ ] (6) commit: `feat(M9.4): soft-delete Undo toast (5초, sonner action) — A6 restore wiring`

**검증 참조**:
- ≥3건 GREEN + 회귀 0
- 5초 후 toast dismiss + Undo 버튼 비활성 (sonner 기본 동작)

**문서 반영**:
- (없음)

**게이트 5**: ✅ 통과 (2026-05-01) — 4 tests GREEN (요건 ≥3 초과), Undo 토스트 5초 + action 동작 검증, RESTORE_CONFLICT 분기 검증. 회귀 0 (364 tests). typecheck/lint 통과. M9.5 진입.

---

## M9.5 — closure (PR + archive) [📋 ready]

**작업 전 필독**:
- `dev/completed/a8-trash-manage/` closure 패턴 mirror
- 본 플랜의 DoD 10개 항목

**구현 대상**:
- [ ] (1) `cd frontend && pnpm test && pnpm typecheck && pnpm lint && pnpm build` — 모두 통과
- [ ] (2) PR 생성 — title `feat(M9): frontend 휴지통 통합 (/trash + Undo toast + A6/A8 endpoint 연결)`
- [ ] (3) 사용자 승인 → CI green → squash-merge
- [ ] (4) closure commit `chore(M9): closure — M9 마일스톤 종료 + dev-docs archive`:
  - `dev/active/m9-frontend-trash/` → `dev/completed/m9-frontend-trash/`
  - 3파일 헤더에 closure 상태 반영 (완료일 / PR # / 머지 SHA / DoD)
  - `docs/progress.md` 최상단에 M9 회고/DoD/다음단계 블록
- [ ] (5) Worktree 정리 — `git worktree remove .claude/worktrees/m9-frontend-trash` + `git branch -d feature/m9-frontend-trash`

**검증 참조**:
- master HEAD 갱신 + `dev/completed/m9-frontend-trash/` 존재 + `dev/active/` 비움
- PR 본문에 plan.md DoD 체크리스트 + ADR #32/#14 backlink

**문서 반영**: closure 자체가 archive.

**게이트 6 → 7**: 사용자 OK → CI green + squash merge → archive.
