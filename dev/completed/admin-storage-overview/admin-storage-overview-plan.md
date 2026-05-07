---
Last Updated: 2026-05-07
---

# Plan — admin-storage-overview

## 요약

운영자가 `/admin/storage`에서 시스템 전체 스토리지 사용 현황(파일 수/버전 수/총 점유 바이트/
휴지통 점유/마지막 orphan cleanup)을 한 눈에 볼 수 있도록 backend read-only summary 엔드포인트
+ frontend 페이지를 도입한다. DB 스키마 변경 없음, audit 신규 type 없음.

## 현재 상태 (master 661b7b2)

### Backend
- `com.ibizdrive.storage` 패키지 — `LocalFsStorageClient`, `StorageOrphanCleanupService`,
  `StorageOrphanCleanupResult` 등 보유. `STORAGE_ORPHAN_CLEANED` audit type emit 활성.
- `com.ibizdrive.admin` — `AdminUserController`, `AdminDepartmentController` 등 1:1 템플릿 존재.
- `FileRepository` / `FileVersionRepository` — 합계 메서드 부재 (단건 조회/검색 위주).
- `audit_log` — `idx_audit_event(event_type, occurred_at DESC)` 인덱스 활성.

### Frontend
- `/admin/storage/` 라우트 부재.
- `frontend/src/components/storage/StorageBar.tsx` — file-private `formatBytes` 11줄 함수 보유
  (lib 미추출).
- `AdminSideNav` — `'스토리지'`가 `DEFERRED_ITEMS`에 위치 (line 29).

### 의존
- `admin-department-crud` (PR #61): controller + DTO 패턴 1:1 mirror.
- `storage-orphan-cleanup` (master 기존): audit `after_state` JSON 형식 (`deleted` 필드).
- 병렬 트랙: `admin-dashboard` (worktree), `wave2-t6-folder-items-wire` (worktree),
  `sleepy-agnesi-3b2dca` (claude branch).

## 목표 상태

### Backend

- **신규 controller/service/DTO** (`com.ibizdrive.admin`):
  - `AdminStorageController`:
    - `GET /api/admin/storage/overview` (`@PreAuthorize("hasRole('ADMIN')")`).
  - `AdminStorageService`:
    - `loadOverview()` → `AdminStorageOverviewResponse.Overview`.
    - JdbcTemplate으로 audit 1-row lookup (event_type='storage.orphan.cleaned'). after_state JSON
      에서 `deleted` int 추출 → `lastDeletedCount`. 0건이면 null 반환.
  - `AdminStorageOverviewResponse` (record):
    ```
    record AdminStorageOverviewResponse(Overview overview) {
      record Overview(
        long totalFiles,
        long totalVersions,
        long totalBytes,
        long trashedFiles,
        long trashedBytes,
        OrphanCleanupSummary orphanCleanup // nullable
      ) {}
      record OrphanCleanupSummary(Instant lastRunAt, int lastDeletedCount) {}
    }
    ```

- **기존 repository 확장** (append-only):
  - `FileRepository` 추가:
    - `long countActiveFiles()` — `SELECT COUNT(*) FROM files WHERE deleted_at IS NULL`.
    - `long countTrashedFiles()` — `SELECT COUNT(*) FROM files WHERE deleted_at IS NOT NULL`.
    - `long sumActiveSizeBytes()` — `SELECT COALESCE(SUM(size_bytes),0) FROM files WHERE deleted_at IS NULL`. (저장은 안 쓰지만 응답 envelope의 `totalFiles`와 짝 맞춤 — 향후 활성-only bytes 메트릭 추가 시 재사용. **YAGNI 적용 — 응답에 안 쓰면 추가 안 함.**)
    - `long sumTrashedSizeBytes()` — `SELECT COALESCE(SUM(size_bytes),0) FROM files WHERE deleted_at IS NOT NULL`.
  - `FileVersionRepository` 추가:
    - `long countAllVersions()` — `SELECT COUNT(*) FROM file_versions`.
    - `long sumAllVersionSizeBytes()` — `SELECT COALESCE(SUM(size_bytes),0) FROM file_versions`.
  - **YAGNI 결정**: `sumActiveSizeBytes`는 응답에 없으므로 추가하지 않는다. 4개 → 3개.

- **권한**: 기존 `@PreAuthorize("hasRole('ADMIN')")` 패턴 답습. 보안 진실의 출처는 controller
  메서드 어노테이션. (CLAUDE.md §3 원칙 10).

- **audit emit 없음** (read-only). 사용자 브리프 명시.

### Frontend

- **lib 추출**:
  - `frontend/src/lib/formatBytes.ts` (신규) — `StorageBar.tsx`의 11줄 함수 그대로 이동.
  - `StorageBar.tsx` — local 정의 제거 + `import { formatBytes } from '@/lib/formatBytes'`.

- **api/queryKey/hook**:
  - `frontend/src/lib/api.ts` 끝에 append:
    ```ts
    export async function getAdminStorageOverview(): Promise<AdminStorageOverviewResponse> { ... }
    ```
  - `frontend/src/lib/queryKeys.ts`:
    ```ts
    qk.adminStorageOverview = () => ['admin', 'storage', 'overview'] as const
    ```
  - `frontend/src/hooks/useAdminStorageOverview.ts` (신규):
    ```ts
    useQuery({ queryKey: qk.adminStorageOverview(), queryFn: getAdminStorageOverview, staleTime: 30_000 })
    ```

- **타입**:
  - `frontend/src/types/admin-storage.ts` (신규) — `AdminStorageOverview` + `OrphanCleanupSummary`
    + envelope `AdminStorageOverviewResponse`.

- **페이지/컴포넌트**:
  - `frontend/src/app/admin/storage/page.tsx` (신규) — `'use client'` 단일 컴포넌트, `<StorageOverview />` 렌더.
  - `frontend/src/components/admin/StorageOverviewCards.tsx` (신규) — 4개 KPI 카드 grid.
    - 카드 1: 활성 파일 수 (`totalFiles`)
    - 카드 2: 총 점유 (`formatBytes(totalBytes)`)
    - 카드 3: 휴지통 (`trashedFiles`개 / `formatBytes(trashedBytes)`)
    - 카드 4: 마지막 orphan cleanup (`lastRunAt` 상대시간 + `lastDeletedCount`개 삭제, 또는 "기록 없음")
  - `frontend/src/components/admin/StorageOverviewTable.tsx` (신규) — 상세 표 (전 필드 + 단위 변환).
  - 로딩/에러 상태: 카드/표 각각 skeleton + error 메시지.

- **AdminSideNav**:
  - `'스토리지'`를 `DEFERRED_ITEMS` (line 29)에서 제거.
  - `ACTIVE_ITEMS` 끝에 `{ label: '스토리지', href: '/admin/storage', match: 'prefix' as const }` 추가.

### Docs

- `docs/04-admin-operations.md` §스토리지 — 신규 섹션 또는 기존 §스토리지 스켈레톤 갱신 (P4에서).
- `BETA-RELEASE.md` — admin 페이지 추가 항목 (P4에서, 1줄).

## Phase

### P1 — Backend service + controller + 테스트 (TDD)
- 추가 repository 메서드 5개 (`FileRepository` 3 + `FileVersionRepository` 2).
- `AdminStorageService.loadOverview()` — 5 합계 + audit 1-row lookup.
- `AdminStorageController.getOverview()` — `@PreAuthorize`.
- `AdminStorageOverviewResponse` record (nested).
- 테스트 (RED first):
  - `AdminStorageServiceTest` (`@SpringBootTest` 또는 `@DataJpaTest` + JdbcTemplate slice):
    - 빈 DB → 모든 합계 0, orphanCleanup null.
    - active+trashed file 각각 + version 다건 → 합계 정확.
    - audit_log에 `storage.orphan.cleaned` 2건 삽입 → 최신 1건 반환.
  - `AdminStorageControllerTest` (`@WebMvcTest` 또는 `@SpringBootTest`):
    - 미인증 401 / 비ADMIN 403 / ADMIN 200 + 응답 envelope 구조 검증.
- gate: `cd backend && ./gradlew test --tests "*.AdminStorage*"` GREEN.
- commit: `feat(admin-storage-overview): backend overview endpoint`

### P2 — Frontend api/hook/queryKey + formatBytes lib 추출 + 테스트
- `lib/formatBytes.ts` 신규 + `StorageBar.tsx` import 변경.
- `types/admin-storage.ts` 신규.
- `lib/queryKeys.ts` `adminStorageOverview()` append.
- `lib/api.ts` `getAdminStorageOverview()` 파일 끝에 append.
- `hooks/useAdminStorageOverview.ts` 신규.
- 테스트:
  - `lib/formatBytes.test.ts` — 0 / B / KB / MB / GB / TB edge.
  - `lib/api.adminStorage.test.ts` — fetch mock + 401/403/200 envelope 파싱.
  - `hooks/useAdminStorageOverview.test.tsx` — RTK + queryKey 일치.
  - `components/storage/StorageBar.test.tsx` — 기존 테스트가 있으면 import 변경 후 통과 확인.
- gate: `cd frontend && pnpm typecheck && pnpm lint && pnpm test` GREEN.
- commit: `feat(admin-storage-overview): frontend api+hook+formatBytes lib`

### P3 — Frontend page + 컴포넌트 + AdminSideNav + 테스트
- `app/admin/storage/page.tsx`.
- `components/admin/StorageOverviewCards.tsx`.
- `components/admin/StorageOverviewTable.tsx`.
- `components/admin/AdminSideNav.tsx` — `'스토리지'` ACTIVE 이동.
- 테스트:
  - `app/admin/storage/page.test.tsx` — 로딩/에러/성공 렌더, KPI 4 카드, orphanCleanup null 분기.
  - `components/admin/AdminSideNav.test.tsx` (있으면) — `/admin/storage` ACTIVE 표기 확인.
- gate: `pnpm typecheck && pnpm lint && pnpm test && pnpm build` GREEN.
- commit: `feat(admin-storage-overview): /admin/storage page + sidenav active`

### P4 — Docs sync + dev-docs 이동 + PR
- docs/04 §스토리지 + BETA-RELEASE 1줄.
- `dev/active/admin-storage-overview/` → `dev/completed/`.
- PR 생성 — 본문에 병렬 트랙 충돌 면적 명시 (admin-dashboard / wave2-t6 / sleepy-agnesi).
- commit: `docs(admin-storage-overview): archive dev-docs + docs sync`

## Acceptance criteria

- [ ] `GET /api/admin/storage/overview` — ADMIN 200, 비ADMIN 403, 미인증 401.
- [ ] 응답 envelope 정확 (`overview.totalFiles/totalVersions/totalBytes/trashedFiles/trashedBytes/orphanCleanup?`).
- [ ] `orphanCleanup`은 audit_log 0건이면 `null`, 1건 이상이면 최신 row의 `occurred_at` + `after_state.deleted`.
- [ ] DB 스키마 변경 0, audit type 신규 0, `StorageOrphanCleanupService` 시그니처 변경 0.
- [ ] `/admin/storage` 페이지가 4 KPI 카드 + 상세 표 렌더.
- [ ] `AdminSideNav`에서 `'스토리지'`가 ACTIVE로 표시되어 navigable.
- [ ] `formatBytes`는 `lib/formatBytes.ts` 단일 정의, `StorageBar`도 import 사용.
- [ ] 모든 phase에서 typecheck/lint/test GREEN.

## Risks / 핵심 원칙 충돌 점검

- CLAUDE.md §3 원칙 11개와 충돌: 없음 (read-only summary, DB 변경 없음, 트랜잭션 / 권한 / 정규화
  관련 신규 분기 없음).
- 병렬 트랙 충돌: AdminSideNav 한 줄, api.ts/queryKeys.ts append-only — 발생 시 머지 1회 수동 해결.
- 성능: `SUM(file_versions.size_bytes)` 전체 — files 수 ~10k 가정 시 비용 매우 낮음.
  10M+ 규모로 성장 시 별도 캐싱 ADR 필요 (현재는 YAGNI).
