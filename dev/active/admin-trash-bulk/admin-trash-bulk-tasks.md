---
Last Updated: 2026-05-08
---

# Tasks — admin-trash-bulk

## Phase 상태

| Phase | 상태 | 게이트 |
|---|---|---|
| P1 backend bulk | 🟡 대기 | `./gradlew test --tests "com.ibizdrive.admin.trash.*"` GREEN |
| P2 frontend wire | ⏸️ blocked-by-P1 | `pnpm typecheck` exit 0 + `api.adminTrashBulk.test.ts` GREEN |
| P3 frontend UI | ⏸️ blocked-by-P2 | `pnpm test --run` skipped=0 + typecheck/lint/build exit 0 |
| P4 docs | ⏸️ blocked-by-P3 | drift check + 4 문서 업데이트 |

---

## P1 — Backend bulk

### 체크리스트

- [ ] `AdminTrashBulkRequestDto` 신설 — `record AdminTrashBulkRequestDto(String action, List<Item> items)` + `record Item(String type, UUID id)`
- [ ] `AdminTrashBulkResponseDto` 신설 — `record(List<Item> succeeded, List<FailedItem> failed)` + `record FailedItem(String type, UUID id, String error)`
- [ ] `AdminTrashService.bulk(String action, List<Item> items, UUID actorId)` 신규 — items 순회하며 `FileMutationService.restore` / `FolderMutationService.restore` / `TrashPurgeService.purgeFile` / `purgeFolder` 호출. try/catch로 succeeded/failed 누적
- [ ] `AdminTrashController.bulk(@RequestBody AdminTrashBulkRequestDto req, principal)` — `POST /bulk` 핸들러, `@PreAuthorize("hasRole('ADMIN')")`
- [ ] action 검증: `restore`/`purge` 외 → IllegalArgumentException → 글로벌 핸들러 400
- [ ] cap 검증: items.size() < 1 또는 > 200 → IllegalArgumentException → 400
- [ ] 단위 테스트 `AdminTrashServiceBulkTest` (NEW): 일부 실패 분기, cap 위반, action enum 검증, idempotency (중복 항목 처리)
- [ ] 슬라이스 테스트 `AdminTrashControllerBulkTest` (NEW): 200(admin) / 401 / 403(member, auditor) / 400(cap, invalid action)
- [ ] 게이트: `cd backend && ./gradlew test --tests "com.ibizdrive.admin.trash.*"` BUILD SUCCESSFUL
- [ ] commit: `feat(admin-trash-bulk): P1 backend bulk endpoint`

### 작업 전 필독

- `docs/superpowers/specs/2026-05-08-admin-trash-bulk-design.md` §3.1 ~ §3.5 (API + 권한 + audit + 트랜잭션 + 에러 코드)
- `backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashController.java` (현재 GET only 구조)
- `backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashService.java` (현재 listing service, mutation 위임 패턴)
- `backend/src/main/java/com/ibizdrive/file/FileMutationService.java:202~290` (`delete` + `restore` 시그니처)
- `backend/src/main/java/com/ibizdrive/folder/FolderMutationService.java` (동일)
- `backend/src/main/java/com/ibizdrive/trash/TrashPurgeService.java:94~210` (`purgeFile` + `purgeFolder` 시그니처)

### 원본 코드 참조

- `AdminTrashController.java` — listing 핸들러 패턴 (`@PreAuthorize`, parsing)
- `TrashController.java:92~110` — 단건 purge 핸들러 패턴 (`@DeleteMapping("/{type}/{id}")`)
- `FileMutationService.restore` — `FileNameConflictException`, `FileNotFoundException` 던짐
- `FolderMutationService.restore` — 동일
- `TrashPurgeService.purgeFile`, `purgeFolder` — `FileNotFoundException`, `FolderNotFoundException` 던짐

### 구현 대상

- `backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashBulkRequestDto.java` (NEW)
- `backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashBulkResponseDto.java` (NEW)
- `backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashService.java` (`bulk` 메서드 추가)
- `backend/src/main/java/com/ibizdrive/admin/trash/AdminTrashController.java` (`bulk` 핸들러 추가)
- `backend/src/test/java/com/ibizdrive/admin/trash/AdminTrashServiceBulkTest.java` (NEW)
- `backend/src/test/java/com/ibizdrive/admin/trash/AdminTrashControllerBulkTest.java` (NEW)

### 검증 참조

- service test: 4개 mutation service mock, items 순회 및 succeeded/failed 분기 검증
- controller test: WebMvc slice (직전 `AdminTrashControllerTest` 패턴 재사용 — `@Import` 보안 설정)
- exception → error string 매핑: `FileNameConflictException` → `"NAME_CONFLICT"`, `FileNotFoundException`/`FolderNotFoundException` → `"NOT_FOUND"`
- `./gradlew test --tests "com.ibizdrive.admin.trash.*"` GREEN

### 문서 반영

- 본 phase는 backend 도입만. 문서 업데이트는 P4에서 일괄.

---

## P2 — Frontend wire

### 체크리스트

- [ ] `frontend/src/types/trash.ts`에 추가:
  - `AdminTrashBulkAction = 'restore' | 'purge'`
  - `AdminTrashBulkRequest { action: AdminTrashBulkAction; items: Array<{type: 'file'|'folder', id: string}> }`
  - `AdminTrashBulkResponse { succeeded: Array<{type, id}>; failed: Array<{type, id, error: string}> }`
- [ ] `frontend/src/lib/api.ts` `adminBulkTrash(action, items): Promise<AdminTrashBulkResponse>` 신규
- [ ] `frontend/src/hooks/useAdminTrash.ts` `useAdminBulkTrash()` mutation 신규 — onSuccess에서 `qk.adminTrash()` prefix invalidate
- [ ] `frontend/src/lib/api.adminTrashBulk.test.ts` (NEW): wire 송신 (POST + JSON body) / 응답 파싱 / 401/403 envelope
- [ ] `frontend/src/hooks/useAdminTrash.test.tsx` 확장: `useAdminBulkTrash` 호출 + invalidate 검증
- [ ] 게이트: `cd frontend && pnpm typecheck` exit 0 + `pnpm test --run api.adminTrashBulk` + `useAdminTrash` GREEN
- [ ] commit: `feat(admin-trash-bulk): P2 frontend wire (types + api + hook)`

### 작업 전 필독

- spec §3.6.4 (query invalidation), §3.1 (API 형태)
- `frontend/src/types/trash.ts` 현재 admin trash 타입
- `frontend/src/lib/api.ts` `adminListTrash` 패턴 (1648~1666 라인)
- `frontend/src/hooks/useAdminTrash.ts` 단건 mutation hook 패턴

### 원본 코드 참조

- `adminListTrash` — fetch wrapper + `buildApiError` 패턴
- `useAdminRestoreTrashItem` / `useAdminPurgeTrashItem` — 단건 mutation hook 구조

### 구현 대상

- `frontend/src/types/trash.ts`
- `frontend/src/lib/api.ts`
- `frontend/src/hooks/useAdminTrash.ts`
- `frontend/src/lib/api.adminTrashBulk.test.ts` (NEW)
- `frontend/src/hooks/useAdminTrash.test.tsx` (확장)

### 검증 참조

- `pnpm typecheck` exit 0 — 타입 추가가 호출자 깨지 않음
- `pnpm test --run api.adminTrashBulk` GREEN — wire 계약
- `useAdminTrash` mutation 테스트에서 invalidate 호출 확인

### 문서 반영

- P4에서 일괄.

---

## P3 — Frontend UI

### 체크리스트

- [ ] `frontend/src/components/admin/AdminTrashBulkActionBar.tsx` (NEW) — props: `selectedCount`, `onRestore`, `onPurge`, `onClear`, `disabled`. 텍스트: "선택 N개 [전체 해제] | [일괄 복원] [일괄 영구삭제]"
- [ ] `/admin/trash/all` page.tsx 행 좌측 체크박스 컬럼 추가 — `selected: Set<string>` (key=`${type}:${id}`), 토글 핸들러
- [ ] 헤더 select-all 체크박스 — 현재 페이지(`list.data.items`) 한정. 전체 선택/해제 토글
- [ ] 필터/정렬 변경 또는 cursor 페이지 이동 시 `selected` 초기화 (`updateFilter` + `setCursor` 호출 지점)
- [ ] BulkActionBar 통합 — `selected.size > 0`일 때 노출. "일괄 복원" 즉시 mutate, "일괄 영구삭제" ConfirmDialog (단건과 동일 텍스트)
- [ ] 결과 toast — `useAdminBulkTrash` onSuccess에서 `복원 N개 성공, M개 실패` (또는 "영구삭제 ..."). `failed.length > 0`이면 details 펼치기
- [ ] page test 확장 — 다중 선택 / select-all / BulkActionBar 노출 / 부분 실패 toast 시나리오
- [ ] 게이트: `pnpm test --run` skipped=0 + `pnpm typecheck` + `pnpm lint` + `pnpm build` exit 0
- [ ] commit: `feat(admin-trash-bulk): P3 frontend UI (선택 + BulkActionBar + toast)`

### 작업 전 필독

- spec §3.6.1 ~ §3.6.4 (선택 모델 + BulkActionBar + toast + invalidation)
- `docs/01-frontend-design.md` §8 (선택 모델, BulkActionBar 패턴 — explorer 측 참고용, 본 컴포넌트는 admin 전용)
- `frontend/src/app/admin/trash/all/page.tsx` 현재 구조 (행 렌더 / ConfirmDialog 패턴)
- `frontend/src/app/admin/trash/all/page.test.tsx` 기존 시나리오

### 원본 코드 참조

- `page.tsx:107~135` 테이블 구조 (헤더 9컬럼 + 행 매핑)
- `page.tsx:220~264` ConfirmPurgeDialog 구조
- `page.test.tsx` 단건 복원/영구삭제 시나리오

### 구현 대상

- `frontend/src/components/admin/AdminTrashBulkActionBar.tsx` (NEW)
- `frontend/src/app/admin/trash/all/page.tsx`
- `frontend/src/app/admin/trash/all/page.test.tsx`

### 검증 참조

- 다중 선택 시나리오: 행 2개 체크 → BulkActionBar에 "선택 2개"
- select-all: 헤더 체크 → 모든 행 체크박스 on, 다시 클릭 → off
- 필터 변경 시 선택 초기화 검증
- 영구삭제 ConfirmDialog → 확인 → bulk mutate
- 부분 실패 toast 렌더 (mock으로 succeeded N + failed M 응답)
- `pnpm test --run` skipped=0 + typecheck/lint/build exit 0

### 문서 반영

- P4에서 일괄.

---

## P4 — Docs

### 체크리스트

- [ ] `docs/02-backend-data-model.md` §7.11에 `POST /api/admin/trash/bulk` row 추가 + 부분 실패 모델 + cap 200 명시
- [ ] `docs/04-admin-operations.md` §8.3에 bulk UI 항목 [x] 전환 + 결과 toast 형식 + cap 200 명시
- [ ] `BETA-RELEASE.md` §7 "bulk restore·purge" 항목 closure (✓ marker + admin-trash-bulk 트랙 backlink)
- [ ] `docs/progress.md` 최상단에 closure entry (admin-trash-bulk 트랙 종료, 게이트 결과)
- [ ] (선택) `docs/00-overview.md §5 ADR` 인덱스에 트랙명 1줄 추가 — 본 트랙은 ADR 신설 안 함
- [ ] 게이트: drift check (spec ↔ plan ↔ 실제 코드 ↔ docs)
- [ ] commit: `docs(admin-trash-bulk): docs/02 §7.11 + docs/04 §8.3 + BETA + progress`

### 작업 전 필독

- spec §4 (영향 범위)
- 직전 트랙 closure 패턴: `dev/completed/wave2-t9-deleted-by/`의 docs 변경 diff

### 구현 대상

- 위 4개 문서

### 검증 참조

- spec ↔ plan ↔ 실제 코드 ↔ docs 4-way drift 0
- progress.md entry 형식 (CLAUDE.md §7)

### 문서 반영

- 본 phase가 문서 반영 단계.

---

## PR + Closure

### 체크리스트

- [ ] 모든 phase 게이트 통과
- [ ] `git fetch origin && git rebase origin/master` (필요 시)
- [ ] PR open: base=master, body에 spec/plan backlink + 게이트 결과 + acceptance 체크
- [ ] code review (자체 review)
- [ ] CI 통과 → squash merge
- [ ] 머지 후 `dev/active/admin-trash-bulk/` → `dev/completed/`로 archive (별도 PR)
- [ ] 워크트리 + 브랜치 정리: `git worktree remove`, `git branch -D feat/admin-trash-bulk`
