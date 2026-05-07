---
Last Updated: 2026-05-07
---

# Tasks — admin-dashboard

## Phase 상태

- [ ] P1 — Backend (repo + service + controller + DTO + 테스트)  ← **active**
- [ ] P2 — Frontend api wrapper + queryKey + hook + types + 테스트
- [ ] P3 — Frontend 페이지 재작성 + KPI 카드 + AdminSideNav 정정 + 테스트
- [ ] P4 — Docs sync + closure + PR

---

## P1 — Backend (repo + service + controller + DTO + 테스트)

- [ ] `UserRepository.countByDeletedAtIsNull()` + `countByDeletedAtIsNullAndIsActiveTrue()` (derived)
- [ ] `DepartmentRepository.countByDeletedAtIsNull()` (derived)
- [ ] `FolderRepository.countByDeletedAtIsNull()` (derived)
- [ ] `FileRepository.countByDeletedAtIsNull()` + `countByDeletedAtIsNotNull()` (derived)
- [ ] `FileVersionRepository.sumAllSizeBytes()` — JPQL `SELECT COALESCE(SUM(v.sizeBytes), 0)`
- [ ] `AdminDashboardSummaryResponse` record + 중첩 records (`Users`, `Departments`, `Folders`, `Files`, `Audit`, `Storage`, `SummaryData`)
- [ ] `AdminDashboardService.getSummary()` — `@Transactional(readOnly=true)`, JdbcTemplate 주입, audit count 1개 native
- [ ] `AdminDashboardController.summary()` — `GET /api/admin/dashboard/summary`, `@PreAuthorize("hasRole('ADMIN')")`
- [ ] `AdminDashboardServiceTest` (Mockito — 8 mock + DTO 조립)
- [ ] `AdminDashboardControllerTest` (`@WebMvcTest` — 200/401/403)
- [ ] `AdminDashboardRepositoryIT` (`@DataJpaTest` — 6 count + SUM + audit count 통합)
- [ ] gate: `cd backend && ./gradlew test` GREEN — 신규 ≥10
- [ ] commit: `feat(admin-dashboard): backend GET /api/admin/dashboard/summary + tests`

### 작업 전 필독
- `dev/active/admin-dashboard/admin-dashboard-plan.md` §"P1"
- `backend/src/main/java/com/ibizdrive/admin/AdminDepartmentController.java` (`@PreAuthorize` + envelope 패턴)
- `backend/src/main/java/com/ibizdrive/admin/AdminDepartmentService.java` (`@Transactional(readOnly=true)` + DTO 조립)
- `backend/src/main/java/com/ibizdrive/audit/AuditQueryService.java` (JdbcTemplate count 쿼리 패턴)
- `backend/src/main/java/com/ibizdrive/user/UserRepository.java` (derived 메서드 추가 위치)

### 원본 코드 참조
- `AdminDepartmentService.list(Pageable, String q)` — service 패턴 1:1.
- `AdminDepartmentController` — controller envelope/`@PreAuthorize` 패턴.
- `AuditQueryService.count*` 또는 jdbc.queryForObject — native count 호출 패턴.
- `FileVersionRepository.lastVersionNumberByFileId` — `@Query` aggregation 예시.

### 구현 대상

**Repository derived 메서드 (메서드 시그니처만 추가, JPA가 자동 생성)**:
- `UserRepository`:
  ```java
  long countByDeletedAtIsNull();
  long countByDeletedAtIsNullAndIsActiveTrue();
  ```
- `DepartmentRepository`:
  ```java
  long countByDeletedAtIsNull();
  ```
- `FolderRepository`:
  ```java
  long countByDeletedAtIsNull();
  ```
- `FileRepository`:
  ```java
  long countByDeletedAtIsNull();
  long countByDeletedAtIsNotNull();
  ```

**Repository JPQL `@Query` (1개)**:
- `FileVersionRepository`:
  ```java
  @Query("SELECT COALESCE(SUM(v.sizeBytes), 0) FROM FileVersion v")
  long sumAllSizeBytes();
  ```

**DTO**:
- `AdminDashboardSummaryResponse` (record), 중첩 records:
  - `Users(long total, long active)`
  - `Departments(long total, long active)`
  - `Folders(long active)`
  - `Files(long active, long trashed)`
  - `Audit(long last24h)`
  - `Storage(long usedBytes)`
  - `SummaryData(Users users, Departments departments, Folders folders, Files files, Audit audit, Storage storage)`
- 최상위 envelope: `{ "summary": SummaryData }`.

**Service**:
- `AdminDashboardService.getSummary()`:
  - 6 derived count + 1 SUM (JPA repos)
  - audit count: `jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE occurred_at >= ?", Long.class, Timestamp.from(Instant.now().minus(24, HOURS)))`
  - DTO 조립 후 반환.
  - `@Transactional(readOnly = true)`.

**Controller**:
- `AdminDashboardController.summary()`:
  - `@GetMapping("/api/admin/dashboard/summary")`, `@PreAuthorize("hasRole('ADMIN')")`.
  - 응답 `Map.of("summary", summaryData)` 또는 wrapper record `{summary}`.

### 검증 참조
- `AdminDepartmentControllerTest` (`@WebMvcTest` 200/401/403 매트릭스 패턴).
- `AdminDepartmentServiceTest` (Mockito mock 호출 검증 패턴).
- `DepartmentRepositoryTest` (`@DataJpaTest` 검증 패턴).

### 문서 반영
- 없음 (P1 단계 — docs sync는 P4).

---

## P2 — Frontend api wrapper + queryKey + hook + types + 테스트

- [ ] `frontend/src/types/admin.ts` (NEW) — `AdminDashboardSummary` 타입 미러
- [ ] `frontend/src/lib/formatBytes.ts` (NEW) — `formatBytes(n: number): string` (1024 base, ko-KR)
- [ ] `frontend/src/lib/api.ts` 끝에 `getAdminDashboardSummary()` append (CSRF 불필요 — GET)
- [ ] `frontend/src/lib/queryKeys.ts` 끝에 `qk.adminDashboard()` append + 의도 주석
- [ ] `frontend/src/hooks/useAdminDashboardSummary.ts` (NEW) — `useQuery` + `staleTime: 60_000` + `refetchOnWindowFocus: true`
- [ ] `frontend/src/lib/api.adminDashboard.test.ts` — fetch URL/메서드/200/401/403 envelope
- [ ] `frontend/src/lib/formatBytes.test.ts` — 0 / B / KB / MB / GB / TB 경계
- [ ] `frontend/src/hooks/useAdminDashboardSummary.test.tsx` — loading / success / error / cacheKey
- [ ] gate: `cd frontend && pnpm test --run` (신규 ≥6)
- [ ] commit: `feat(admin-dashboard): frontend api wrapper + hook + types + tests`

### 작업 전 필독
- `frontend/src/lib/api.ts` — admin* wrapper 패턴 (특히 `adminListDepartments`).
- `frontend/src/lib/queryKeys.ts` — `qk.adminDepartmentsList` 패턴.
- `frontend/src/hooks/useAdminDepartments.ts` — useQuery + staleTime 패턴.
- `frontend/src/types/audit.ts` 또는 `types/department.ts` — backend mirror type 패턴.

### 원본 코드 참조
- `api.adminListDepartments` — fetch + envelope 파싱 패턴.
- `useAdminDepartments` — useQuery 옵션 (refetchOnWindowFocus 등).

### 구현 대상

**`types/admin.ts`**:
```ts
export interface AdminDashboardSummary {
  users: { total: number; active: number }
  departments: { total: number; active: number }
  folders: { active: number }
  files: { active: number; trashed: number }
  audit: { last24h: number }
  storage: { usedBytes: number }
}
```

**`lib/api.ts` (파일 끝 append)**:
```ts
export async function getAdminDashboardSummary(): Promise<AdminDashboardSummary> {
  const res = await fetch('/api/admin/dashboard/summary', { credentials: 'include' })
  if (!res.ok) throw await parseApiError(res)
  const body = await res.json()
  return body.summary
}
```

**`lib/queryKeys.ts` (파일 끝 append)**:
```ts
adminDashboard: () => [...qk.all, 'admin', 'dashboard'] as const,
```

**`hooks/useAdminDashboardSummary.ts`**:
```ts
export function useAdminDashboardSummary() {
  return useQuery({
    queryKey: qk.adminDashboard(),
    queryFn: getAdminDashboardSummary,
    staleTime: 60_000,
    refetchOnWindowFocus: true,
  })
}
```

### 검증 참조
- `frontend/src/lib/api.adminDepartments.test.ts` (fetch 테스트 패턴).
- `frontend/src/hooks/useAdminDepartments.test.tsx` (renderHook + QueryClientProvider 패턴).

### 문서 반영
- 없음 (P4에서 일괄).

---

## P3 — Frontend 페이지 재작성 + KPI 카드 + AdminSideNav 정정 + 테스트

- [ ] `frontend/src/components/admin/DashboardKpiCard.tsx` (NEW) — `{label, value, hint?, href?}` props
- [ ] `frontend/src/components/admin/DashboardSummary.tsx` (NEW) — 6 카드 + loading skeleton + error fallback
- [ ] `frontend/src/app/admin/page.tsx` — 전면 재작성 → `<DashboardSummary />`
- [ ] `frontend/src/components/admin/AdminSideNav.tsx` — ACTIVE 최상단 '대시보드' 추가 + DEFERRED에서 '대시보드' 제거
- [ ] `DashboardKpiCard.test.tsx` — value/hint/href 렌더, 숫자 포맷팅
- [ ] `DashboardSummary.test.tsx` — loading/error/success + 6 카드
- [ ] `app/admin/page.test.tsx` — KPI 표시 + 401/403 처리
- [ ] `AdminSideNav.test.tsx` — '대시보드' active(`/admin` exact), DEFERRED에서 부재 (기존 테스트 있으면 확장)
- [ ] gate: `cd frontend && pnpm test --run && pnpm typecheck && pnpm lint && pnpm build` exit 0
- [ ] commit: `feat(admin-dashboard): /admin KPI grid + sidenav + tests`

### 작업 전 필독
- `frontend/src/app/admin/page.tsx` 현재 (v1.x landing — 재작성 대상).
- `frontend/src/components/admin/AdminSideNav.tsx` 현재 (ACTIVE 3 / DEFERRED 7).
- `frontend/src/app/admin/departments/page.tsx` (페이지 + hooks 컴포지션 패턴).

### 원본 코드 참조
- `app/admin/departments/page.tsx` — 페이지 구조 패턴 (loading/error/list).
- `app/admin/users/page.tsx` (있다면) — admin 페이지 컴포지션.

### 구현 대상

**`DashboardKpiCard.tsx`**:
- props: `{ label: string; value: string | number; hint?: string; href?: string }`.
- value가 number면 `Intl.NumberFormat('ko-KR')` 적용.
- href 있으면 `<Link>`로 래핑.
- Tailwind: `block p-4 rounded border border-border bg-surface-1 hover:bg-surface-2`.

**`DashboardSummary.tsx`**:
- `useAdminDashboardSummary()` 호출.
- isLoading → 6 회색 placeholder card.
- isError → 한 박스 + 재시도 버튼 (`refetch`).
- success → 6 KPI card (users / departments / folders / files / audit / storage).
- 카드 라벨/값/힌트:
  | label | value | hint | href |
  |---|---|---|---|
  | 사용자 | `users.active` | `${users.total} 등록` | `/admin/users` |
  | 부서 | `departments.active` | — | `/admin/departments` |
  | 폴더 | `folders.active` | — | — |
  | 파일 | `files.active` | `휴지통 ${files.trashed}` | — |
  | 감사 (24h) | `audit.last24h` | — | `/admin/audit/logs` |
  | 사용 공간 | `formatBytes(storage.usedBytes)` | — | — |

**`app/admin/page.tsx`** 전면 재작성:
```tsx
export default function AdminDashboardPage() {
  return (
    <div className="p-8 max-w-[960px]">
      <h1 className="text-[20px] font-semibold text-fg mb-1">대시보드</h1>
      <p className="text-[13px] text-fg-2 mb-6">시스템 운영 지표 6종.</p>
      <DashboardSummary />
    </div>
  )
}
```

**`AdminSideNav.tsx`** 변경 부분:
```ts
const ACTIVE_ITEMS = [
  { label: '대시보드', href: '/admin', match: 'exact' as const },     // ← 추가 (최상단)
  { label: '감사 로그', href: '/admin/audit/logs', match: 'exact' as const },
  { label: '사용자 초대', href: '/admin/users', match: 'prefix' as const },
  { label: '부서', href: '/admin/departments', match: 'prefix' as const },
]

const DEFERRED_ITEMS = [
  // '대시보드' 제거
  '권한',
  '스토리지',
  '휴지통',
  'Legal Hold',
  '정책',
  '시스템',
]
```

### 검증 참조
- `frontend/src/app/admin/departments/page.test.tsx` — 페이지 테스트 패턴.
- `frontend/src/components/admin/AdminSideNav.test.tsx` (있다면) — 사이드바 active 검증.

### 문서 반영
- 없음 (P4에서 일괄).

---

## P4 — Docs sync + closure + PR

- [ ] `docs/04-admin-operations.md` §2 — `/admin` (대시보드) 활성 명시
- [ ] `docs/04-admin-operations.md` §3 (스켈레톤) → KPI 6종 + 정의 + envelope schema 채움
- [ ] `docs/02-backend-data-model.md` §7 — `GET /api/admin/dashboard/summary` 1행 추가
- [ ] `BETA-RELEASE.md` — admin entry 활성 페이지 카운트/이름 갱신
- [ ] `docs/progress.md` 본 트랙 entry (최상단 추가)
- [ ] `dev/active/admin-dashboard/` → `dev/completed/`
- [ ] commit: `docs(admin-dashboard): sync + archive dev-docs`
- [ ] PR 생성 — body에 "병렬 wave2-t6 / sleepy-agnesi 세션과 파일 충돌 면적 검토 완료" 명시

### 작업 전 필독
- `docs/04-admin-operations.md` 현재 §2 §3 (스켈레톤 상태 확인).
- `docs/02-backend-data-model.md` 현재 §7 admin endpoint 목록.
- `dev/completed/admin-department-crud/` — closure 절차 1:1 참고.

### 원본 코드 참조
- `docs/progress.md` 최상단 entry — admin-department-crud의 양식.

### 구현 대상

- `docs/04 §2` 라우트 트리에 `/admin` (대시보드) 활성 라인.
- `docs/04 §3` KPI 명세:
  - 6종 정의 표 (label / source / 정의).
  - `audit.last24h`: `audit_log WHERE occurred_at >= now() - 24h`.
  - `storage.usedBytes`: `SUM(file_versions.size_bytes)` 모든 버전.
- `docs/02 §7`: `GET /api/admin/dashboard/summary` 200 / 401 / 403 / envelope.
- `BETA-RELEASE.md`: 활성 admin 페이지 4종 (대시보드 / 감사 로그 / 사용자 / 부서).

### 검증 참조
- 수동: PR 빌드 CI 그린 확인.

### 문서 반영
- 본 phase 자체가 docs 반영.
