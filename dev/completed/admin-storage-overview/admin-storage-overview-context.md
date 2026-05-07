---
Last Updated: 2026-05-07
---

# Context — admin-storage-overview

## SESSION PROGRESS

- 2026-05-07 — bootstrap. brainstorming → spec 승인 → worktree(`admin-storage-overview`) 생성 →
  dev-docs 3종 작성. 다음: P1 (backend AdminStorage* + 테스트, TDD).

## Current Execution Contract

- worktree: `C:/project/IbizDrive/.claude/worktrees/admin-storage-overview`
- branch: `admin-storage-overview` (master `661b7b2` 기준)
- 작업 단위: phase = commit. PR은 P4 closure에서 1회.
- 검증: 각 phase 끝에 `./gradlew test` (backend) / `pnpm typecheck && pnpm lint && pnpm test`
  (frontend) GREEN 확인 후 다음 phase로.

## 병렬 트랙 충돌 회피 (사용자 브리프)

다른 세션이 동시에 진행 중. 본 트랙은 신설 파일 위주로 충돌 면적 최소화.

- 절대 수정 금지:
  - `backend/src/main/java/com/ibizdrive/folder/**`
  - `backend/src/main/java/com/ibizdrive/file/**` (※ `FileRepository`/`FileVersionRepository` 합계
    메서드 append만 허용 — 파일 끝)
  - `backend/src/main/java/com/ibizdrive/admin/AdminDashboard*.java` (admin-dashboard 트랙 신설)
  - `frontend/src/components/files/**`, `/upload/**`, `/folders/**`
  - `frontend/src/components/admin/Dashboard*.tsx` (admin-dashboard 트랙 신설)
  - `frontend/src/app/admin/page.tsx` (admin-dashboard 전면 재작성)
  - `frontend/src/hooks/useUpload.ts`, `useCurrentFolder.ts`, `useFilesInFolder.ts`
- 머지 시 한 줄 conflict 가능:
  - `frontend/src/components/admin/AdminSideNav.tsx` — admin-dashboard 트랙도 변경 중. conflict
    발생 시 둘 다 ACTIVE에 합치기.
  - `frontend/src/lib/api.ts` — append-only (파일 **끝**에 추가).
  - `frontend/src/lib/queryKeys.ts` — append-only (다른 트랙도 같은 패턴).

## Active phase / task

- **active phase**: P0 — dev-docs bootstrap (in progress).
- **active task**: context/plan/tasks 작성 후 commit → P1 진입.

## 다음 세션 읽기 순서

1. `admin-storage-overview-plan.md` — phase 지도 + acceptance criteria.
2. `admin-storage-overview-tasks.md` — 미완료 phase의 첫 task + 참조 블록.
3. master HEAD `661b7b2` (file-list-wiring Phase B) — 본 트랙은 이 위에서 분기.
4. `dev/completed/admin-department-crud/` — controller/service/audit emit 패턴 1:1 답습.
5. `dev/completed/storage-orphan-cleanup/` — `StorageOrphanCleanupService` 동작 + audit emit 형식
   (after_state JSON 필드명: `runId/scanned/candidates/deleted/failed/truncated/durationMs`).

## 핵심 파일과 역할

### Backend — 신규 (`com.ibizdrive.admin`)

| 파일 | 역할 | 템플릿 |
|---|---|---|
| `AdminStorageController` | `GET /api/admin/storage/overview` | `AdminDepartmentController` (list 단순) |
| `AdminStorageService` | 합계 집계 + audit 1-row lookup | n/a (read-only, audit 없음) |
| `AdminStorageOverviewResponse` | `{ overview: { totalFiles, totalVersions, totalBytes, trashedFiles, trashedBytes, orphanCleanup? } }` | record nested |

### Backend — 기존 파일 확장 (append-only, 충돌 면적 최소)

| 파일 | 변경 |
|---|---|
| `FileRepository` | `countActiveFiles()` / `countTrashedFiles()` / `sumActiveSizeBytes()` / `sumTrashedSizeBytes()` 4 메서드 append (native `SUM`/`COUNT`). |
| `FileVersionRepository` | `countAllVersions()` / `sumAllVersionSizeBytes()` 2 메서드 append. |

### Frontend — 신규

| 파일 | 변경 |
|---|---|
| `frontend/src/lib/formatBytes.ts` (신규) | `StorageBar.tsx`의 file-private `formatBytes` 함수를 lib로 추출 (DRY). |
| `frontend/src/components/storage/StorageBar.tsx` | local `formatBytes` 제거 → `import { formatBytes } from '@/lib/formatBytes'`. |
| `frontend/src/lib/queryKeys.ts` | `qk.adminStorageOverview()` append. |
| `frontend/src/lib/api.ts` | `getAdminStorageOverview()` 파일 **끝**에 append. |
| `frontend/src/hooks/useAdminStorageOverview.ts` (신규) | TanStack Query wrapper, staleTime 30s. |
| `frontend/src/types/admin.ts` 또는 `admin-storage.ts` (신규) | `AdminStorageOverview` 타입. |
| `frontend/src/app/admin/storage/page.tsx` (신규) | `'use client'` + `<StorageOverview />`. |
| `frontend/src/components/admin/StorageOverviewCards.tsx` (신규) | KPI 카드 grid (4개). |
| `frontend/src/components/admin/StorageOverviewTable.tsx` (신규) | 상세 표. |
| `frontend/src/components/admin/AdminSideNav.tsx` | `'스토리지'` DEFERRED → ACTIVE_ITEMS. |

## 중요한 의사결정

### bytes 집계 — `totalBytes`는 file_versions 기준, `trashedBytes`는 files 기준

- `totalBytes = SUM(file_versions.size_bytes)` (전체 row, 휴지통/active 무관) — **실제 disk 점유량**.
  - 이유: storage_key 1:1 → orphan cleanup의 liveSet 크기와 의미적으로 정합.
- `trashedBytes = SUM(files.size_bytes) WHERE deleted_at IS NOT NULL` — current-version 합.
  - 이유: UI 의미 = "휴지통 비우면 회수되는 양". 휴지통 파일의 과거 버전까지 합치면 의미가 모호해짐.
- 두 값은 동일 단위이지만 의미가 다름 (총 점유 vs 회수 가능). 응답 envelope에서 명시.

### orphanCleanup 메트릭 — audit_log 1-row lookup

- 출처: `audit_log WHERE event_type='storage.orphan.cleaned' ORDER BY occurred_at DESC LIMIT 1`.
- `occurred_at` → `lastRunAt`, `after_state.deleted` → `lastDeletedCount`.
- 0건이면 `orphanCleanup: null`.
- 이유:
  1. `StorageOrphanCleanupService` 시그니처 미변경 (사용자 비추천 항목 부합).
  2. 서버 재시작 후에도 보존.
  3. `idx_audit_event(event_type, occurred_at DESC)` 인덱스 활용 — 비용 0.

### `formatBytes` lib 추출

- 기존: `frontend/src/components/storage/StorageBar.tsx:5`에 file-private 11줄 함수.
- 결정: `frontend/src/lib/formatBytes.ts`로 이동. StorageBar는 import.
- 이유: DRY (StorageOverview에서도 사용). 변경 면적 좁음 — 다른 트랙 충돌 없음.
- Risk: StorageBar 단위 테스트가 있다면 import path만 수정 — 기능 보존.

### TDD 분리 단위

- P1 backend: service 합계 + audit lookup → MockMvc / `@DataJpaTest` / `@SpringBootTest`.
- P2 frontend: api wrapper + hook → `vi.mock` fetch + RTK / RTL.
- P3 frontend: 컴포넌트 → RTL 렌더 + a11y 점검 + AdminSideNav 활성 표기.

## 빠른 재개 안내

```bash
# 1. context 확인
cat dev/active/admin-storage-overview/admin-storage-overview-tasks.md

# 2. 현재 phase 확인 (위 SESSION PROGRESS)

# 3. P1 시작 시
cd .claude/worktrees/admin-storage-overview/backend
./gradlew test --tests "*.AdminStorageServiceTest" --tests "*.AdminStorageControllerTest"

# 4. 직전 commit 확인
git log --oneline -5
```

## 백링크

- `docs/04-admin-operations.md` §스토리지 (P4에서 추가)
- `BETA-RELEASE.md` — admin 페이지 추가 항목 (P4)
- 비범위: ADR 신규 발번 0 (기존 패턴 답습), audit 신규 type 0 (read-only)
