---
Last Updated: 2026-05-01
---

# F5 — Frontend Folder Share UI 확장 context

## SESSION PROGRESS

- **2026-05-01 (게이트 0)** — bootstrap. worktree `feature/f5-frontend-folder-share-ui` 생성(base `7c179d1` master). dev-docs 3파일 + ownership(`dev/process/f5-2026-05-01.md`). F5.1 진입 직전 baseline 재확인 task 등록.
- **2026-05-01 (게이트 1) ✅** — F5.1 wire 정합 완료.
  - `types/share.ts`: ShareDto 10필드(file/folder XOR + revokedAt/revokedBy null), ShareTarget discriminated 도입.
  - `stores/shareUi.ts`: `target: ShareTarget` discriminator 전환.
  - `lib/api.ts`: `createShares` → `createFileShares`/`createFolderShares` 분리 + `postShareCreate` 헬퍼.
  - `useCreateShare`: `api.createFileShares` 호출 (Vars file-only 유지 — discriminated 전환은 F5.2).
  - `BulkActionBar`: `open({kind:'file', ...})` 갱신.
  - `ShareDialog`: target 기반 + 기존공유 list에서 subject/preset 제거(만료+해제만). folder kind 진입 시 toast 가드(F5.2까지 임시).
  - `SharesTable`: preset 컬럼 제거 → 3컬럼(항목/공유한 사람/만료) + folder 아이콘 분기(folderId NOT NULL).
  - 테스트: 489/489 GREEN (`pnpm test` `pnpm typecheck` `pnpm lint` 모두 클린).

## Current Execution Contract

- **모드**: 자율 실행 (gate-driven). 게이트마다 한 줄 보고, 5단계 컨텍스트 임계값 보고(60/70/75/80%) 유지.
- **scope freeze**: 본 plan §"비-목표"는 트랙 종료 전 변경 금지. 새 요청 들어오면 별도 트랙 또는 backlog.
- **branch**: `feature/f5-frontend-folder-share-ui` (worktree `.claude/worktrees/f5-frontend-folder-share-ui`).
- **PR 규약**: F4 closure 패턴(squash-merge, 단일 PR, dev-docs `dev/completed/` 이관, progress.md 행 추가).
- **테스트 게이트**: 각 phase 종료 시 `cd frontend && pnpm test` GREEN. F5.4에서 추가로 `pnpm typecheck && pnpm lint && pnpm build` GREEN.

## 현재 active task

- **phase**: F5.2 🟡 active (F5.1 ✅ 완료, gate 1 통과)
- **next action**: F5.2.1 — `useCreateShare` Vars discriminated 전환 (`{target, req}` + target.kind 분기). F5.2.2 — `ShareDialog` 이동(`files/` → `shares/`) + folder kind 분기 활성. F5.2.3 — `(explorer)/layout.tsx` import 경로 갱신.
- **blocker**: 없음

## 다음 세션 읽기 순서

1. 이 context
2. `f5-frontend-folder-share-ui-plan.md` (목표 상태/phase 지도/acceptance criteria/리스크)
3. `f5-frontend-folder-share-ui-tasks.md` (다음 task 참조 블록)
4. `frontend/src/types/share.ts` (수정 대상)
5. `backend/src/main/java/com/ibizdrive/share/ShareDto.java` (wire 진실)
6. `frontend/src/lib/api.ts` (createShares 분리 대상)
7. `frontend/src/stores/shareUi.ts` (target discriminator 도입 대상)

## 핵심 파일과 역할

### 수정 대상 (frontend)

- `frontend/src/types/share.ts` — `ShareDto`/`ShareTarget`/`ShareCreateRequest` 형상 소유. 본 트랙에서 fileId/folderId XOR 도입.
- `frontend/src/stores/shareUi.ts` — ShareDialog UI 트리거 store. target discriminator로 generalize.
- `frontend/src/lib/api.ts:659-700` — share endpoint 4종. `createShares` → file/folder 분리.
- `frontend/src/hooks/useCreateShare.ts` — Vars discriminated.
- `frontend/src/components/files/ShareDialog.tsx` → `frontend/src/components/shares/ShareDialog.tsx` (이동).
- `frontend/src/components/shares/SharesTable.tsx` — kind-aware 컬럼.
- `frontend/src/components/files/BulkActionBar.tsx` — `open({kind:'file', ...})` 갱신.
- `frontend/src/app/(explorer)/layout.tsx` — ShareDialog import 경로 갱신.
- Breadcrumb(F5.3 진입 시 위치 확정) — folder share 진입점.

### 참조 (read-only, backend)

- `backend/src/main/java/com/ibizdrive/share/ShareDto.java` — wire 진실. record 본문이 1:1.
- `backend/src/main/java/com/ibizdrive/share/ShareController.java` — 라우트 4종(POST file/folder, GET by-me/with-me, DELETE).
- `backend/src/main/java/com/ibizdrive/share/Share.java` — `file_id`/`folder_id` XOR 인비ariant.

### docs

- `docs/01-frontend-design.md` §6.1(qk), §6.2(invalidation), §14.4(ShareDialog), §17(routing) — F5.4에서 sync.
- `docs/02-backend-data-model.md` §7.9 — folder POST 이미 등재 (A12). 변경 없음.
- `docs/00-overview.md` ADR #34 — 변경 없음.

## 중요한 의사결정

1. **ShareDto wire 형상은 backend record를 진실로 본다.** F5.1.0 결과 — frontend types가 `subjectType/subjectId/preset` 가정 + `folderId/revokedAt/revokedBy` 누락 = drift. A안 채택 → frontend types를 wire에 정렬. ShareDialog 기존공유 row의 subject/preset 표기 단순화(만료+해제만), SharesTable preset 컬럼 제거. UI 복원은 backend join 트랙(A13 backlog).
2. **createShares 메서드 분리 (file/folder)** — backend 라우트 분리와 1:1. 단일 메서드로 통합하면 endpoint 분기 로직이 클라이언트에 들어가서 KISS 위반.
3. **useCreateShare는 단일 hook 유지, Vars만 discriminated** — 호출자 관점에서 동일 액션, qk.shares 무효화도 동일. hook을 두 개로 쪼개면 무효화 중복.
4. **ShareDialog 위치 이동 (`files/` → `shares/`)** — 더 이상 file 전용이 아님. 소유 경계 정합.
5. **폴더 진입점은 Breadcrumb 우측 작은 액션** — 현재 폴더 = URL `folderId`이므로 §19 원칙 1과 정합. FolderTree row 우클릭 메뉴는 별도 트랙(범용 폴더 액션 시스템 신설 필요).
6. **revokedAt/revokedBy 미노출** — backend가 active row에서 항상 null. UI 가치 0. YAGNI.
7. **with-me revoke 미노출 유지** — F4 보수 정책 그대로.

## 빠른 재개 안내

```
# 이 작업으로 재개하려면
cd C:/project/IbizDrive/.claude/worktrees/f5-frontend-folder-share-ui
cat dev/active/f5-frontend-folder-share-ui/f5-frontend-folder-share-ui-context.md  # 이 파일
cat dev/active/f5-frontend-folder-share-ui/f5-frontend-folder-share-ui-tasks.md    # 다음 task
cat dev/process/f5-2026-05-01.md                                                    # 현재 working_files
```

## 잔여 backlog

- **A13 (가칭) — backend ShareDto subject/preset join** — `permissions` row의 subject_type/subject_id/preset을 ShareDto에 join. ShareDialog 기존공유 row의 subject/preset 표기 + SharesTable preset 컬럼 복원에 필요. F5에서 wire drift 정정 후 등록되는 backlog.
- 폴더 다중 선택 BulkActionBar `공유` 액션 (folder 다중 선택 자체 부재 → 함께 트랙 필요)
- FolderTree row 우클릭 컨텍스트 메뉴 (범용 액션 시스템)
- subject picker UI (user/department/role 목록 endpoint 부재)
- with-me revoke (수신자 자진 반납 사양 결정 필요)
- ShareControllerTest에 wire JSON 필드 검증 추가 (현 갭)
