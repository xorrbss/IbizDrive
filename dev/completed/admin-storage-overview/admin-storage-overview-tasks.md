---
Last Updated: 2026-05-07
---

# Tasks — admin-storage-overview

phase = commit. 각 phase 끝에 gate 통과 확인 후 다음 phase 진입.

---

## P0 — dev-docs bootstrap [in progress]

- [x] worktree 생성 (`admin-storage-overview` branch, master 661b7b2 기준)
- [x] dev-docs 3종 작성 (`-context.md` / `-plan.md` / `-tasks.md`)
- [ ] commit: `docs(admin-storage-overview): bootstrap dev-docs`

---

## P1 — Backend overview endpoint (TDD) [pending]

### P1.1 — RED: 테스트 작성
- [ ] `AdminStorageServiceTest` (backend/src/test/java/com/ibizdrive/admin/):
  - 빈 DB → 모든 합계 0, `orphanCleanup == null`.
  - active file 2 + trashed file 1 + version 4 (active 3 + trashed 1) → 합계 정확.
    - `totalFiles=2, trashedFiles=1, totalVersions=4`.
    - `totalBytes = SUM(all 4 versions)`, `trashedBytes = SUM(trashed file size)`.
  - audit_log `storage.orphan.cleaned` 2건 삽입 (1시간 차이) → 최신 1건의 `lastRunAt` + `deleted` 반환.
- [ ] `AdminStorageControllerTest` (`@SpringBootTest` + MockMvc):
  - 미인증 → 401.
  - 비ADMIN 사용자 → 403.
  - ADMIN → 200 + 응답 envelope 키 검증 (`overview.totalFiles` 등 6 필드).
  - `orphanCleanup: null` 분기.
- [ ] gate: `./gradlew test --tests "*.AdminStorage*"` — RED 확인.

### P1.2 — GREEN: 구현
- [ ] `FileRepository` append: `countActiveFiles()`, `countTrashedFiles()`, `sumTrashedSizeBytes()` (3 native queries).
- [ ] `FileVersionRepository` append: `countAllVersions()`, `sumAllVersionSizeBytes()` (2 native queries).
- [ ] `AdminStorageOverviewResponse.java` record (nested `Overview` + `OrphanCleanupSummary`).
- [ ] `AdminStorageService.java` (`@Service`, `@Transactional(readOnly=true)`):
  - DI: `FileRepository`, `FileVersionRepository`, `JdbcTemplate`.
  - `loadOverview()` — 5 합계 + audit 1-row JdbcTemplate lookup.
  - audit lookup SQL:
    ```sql
    SELECT occurred_at, after_state
    FROM audit_log
    WHERE event_type = 'storage.orphan.cleaned'
    ORDER BY occurred_at DESC
    LIMIT 1
    ```
  - JSON 파싱: `ObjectMapper.readTree(after_state).get("deleted").asInt()`. 파싱 실패 시 fallback null.
- [ ] `AdminStorageController.java`:
  - `@RestController`, `@RequestMapping("/api/admin/storage")`.
  - `@GetMapping("/overview")` + `@PreAuthorize("hasRole('ADMIN')")`.
  - return `new AdminStorageOverviewResponse(service.loadOverview())`.
- [ ] gate: `./gradlew test --tests "*.AdminStorage*"` — GREEN.
- [ ] gate: `./gradlew test` — 전체 GREEN (회귀 0).

### P1.3 — commit
- [ ] `feat(admin-storage-overview): backend overview endpoint + audit-based orphan cleanup metric`
- [ ] mark P1 done in tasks.md.

---

## P2 — Frontend api/hook/queryKey + formatBytes lib (TDD) [pending]

### P2.1 — RED: 테스트
- [ ] `frontend/src/lib/formatBytes.test.ts`:
  - 0 → `"0 B"`, 1023 → `"1023 B"`, 1024 → `"1 KB"`, 1.5MB / 1.5GB / TB edge.
- [ ] `frontend/src/lib/api.adminStorage.test.ts`:
  - fetch mock 200 → envelope 그대로 반환.
  - 401/403 → 적절한 error throw (기존 errors.ts 코드 활용).
- [ ] `frontend/src/hooks/useAdminStorageOverview.test.tsx`:
  - QueryClient + render → queryKey가 `qk.adminStorageOverview()`와 일치.
  - mocked queryFn → `data.overview.totalFiles` 노출 확인.
- [ ] gate: `pnpm test --run formatBytes adminStorage useAdminStorageOverview` — RED.

### P2.2 — GREEN: 구현
- [ ] `frontend/src/lib/formatBytes.ts` 신규 — `StorageBar.tsx:5` 함수 그대로 이동 + JSDoc.
- [ ] `frontend/src/components/storage/StorageBar.tsx` — local `formatBytes` 제거 + `import`.
- [ ] `frontend/src/types/admin-storage.ts` 신규 — `AdminStorageOverview`, `OrphanCleanupSummary`, `AdminStorageOverviewResponse`.
- [ ] `frontend/src/lib/queryKeys.ts` — `adminStorageOverview` 키 팩토리 append.
- [ ] `frontend/src/lib/api.ts` 끝에 `getAdminStorageOverview()` append.
- [ ] `frontend/src/hooks/useAdminStorageOverview.ts` 신규.
- [ ] gate: `pnpm typecheck && pnpm lint && pnpm test` — GREEN.

### P2.3 — commit
- [ ] `feat(admin-storage-overview): frontend api/hook/queryKey + formatBytes lib`

---

## P3 — Frontend page + 컴포넌트 + AdminSideNav (TDD) [pending]

### P3.1 — RED: 테스트
- [ ] `frontend/src/app/admin/storage/page.test.tsx`:
  - mock `useAdminStorageOverview` → loading skeleton 표시.
  - mock success → 4 KPI 카드 텍스트 검증 (`totalFiles`, `formatBytes(totalBytes)`, trashed pair, orphan cleanup).
  - mock success with `orphanCleanup: null` → "기록 없음" 표기.
  - mock error → error 메시지.
- [ ] `frontend/src/components/admin/AdminSideNav.test.tsx` (기존 존재 시): `/admin/storage` ACTIVE 진입 시 `aria-current="page"` 부여 확인.

### P3.2 — GREEN: 구현
- [ ] `frontend/src/components/admin/StorageOverviewCards.tsx` 신규 — 4 카드 grid.
- [ ] `frontend/src/components/admin/StorageOverviewTable.tsx` 신규 — 상세 표.
- [ ] `frontend/src/app/admin/storage/page.tsx` 신규 — `'use client'` + 두 컴포넌트 렌더.
- [ ] `frontend/src/components/admin/AdminSideNav.tsx` — `'스토리지'` DEFERRED 제거 + ACTIVE 추가.
- [ ] gate: `pnpm typecheck && pnpm lint && pnpm test && pnpm build` — GREEN.

### P3.3 — commit
- [ ] `feat(admin-storage-overview): /admin/storage page + cards/table + sidenav active`

---

## P4 — Docs + closure + PR [pending]

- [ ] `docs/04-admin-operations.md` §스토리지 (or §관련 절) — 본 페이지 진입점, 응답 형식, 데이터 의미 (`totalBytes` vs `trashedBytes`) 1 절 추가.
- [ ] `BETA-RELEASE.md` — admin 추가 페이지 1줄.
- [ ] `dev/active/admin-storage-overview/` → `dev/completed/` 이동.
- [ ] context.md SESSION PROGRESS 마지막 줄에 closure 기록.
- [ ] commit: `docs(admin-storage-overview): archive dev-docs + docs sync`
- [ ] PR 생성 — 본문에 병렬 트랙(admin-dashboard / wave2-t6 / sleepy-agnesi) 충돌 면적 최소화 명시.

---

## 참조 블록

### 응답 envelope (계약)
```json
{
  "overview": {
    "totalFiles": 1234,
    "totalVersions": 1789,
    "totalBytes": 524288000,
    "trashedFiles": 12,
    "trashedBytes": 8388608,
    "orphanCleanup": {
      "lastRunAt": "2026-05-06T14:30:00Z",
      "lastDeletedCount": 3
    }
  }
}
```

### audit_log lookup SQL
```sql
SELECT occurred_at, after_state
FROM audit_log
WHERE event_type = 'storage.orphan.cleaned'
ORDER BY occurred_at DESC
LIMIT 1
```
인덱스 `idx_audit_event(event_type, occurred_at DESC)` 활용 → 비용 0.

### `after_state` JSON 형식 (`StorageOrphanCleanupResult`)
```json
{
  "runId": "uuid",
  "scanned": 1000,
  "candidates": 5,
  "deleted": 3,
  "failed": 0,
  "truncated": false,
  "durationMs": 1234
}
```
본 트랙은 `deleted` 필드만 사용.
