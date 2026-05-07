---
Last Updated: 2026-05-07
---

# Plan — admin-dashboard

## 요약

`/admin` 진입 직후 운영 KPI 6종을 단일 페이지로 노출한다. 백엔드는 read-only
`GET /api/admin/dashboard/summary` 단일 엔드포인트, 프론트는 KPI 카드 그리드.
스키마 변경/audit emit/신규 비즈니스 로직 ❌ — 카운트와 SUM만 다룬다.
master `/admin/page.tsx`는 v1.x deferred 안내 카드를 노출하는 stale landing이며 본
트랙으로 대시보드 KPI 그리드로 전면 재작성된다.

## 현재 상태 (master 661b7b2)

### Backend
- `com.ibizdrive.admin` 패키지 — User/Department CRUD 컨트롤러·서비스·리스너 존재. dashboard 부재.
- `UserRepository` / `DepartmentRepository` / `FolderRepository` / `FileRepository` — admin list 페이징 메서드는 있으나 `count*` derived 메서드 부재.
- `FileVersionRepository` — `existsByStorageKey`, `findByFileIdOrderByVersionNumberDesc` 등 보유. SUM 메서드 부재.
- `AuditService` / `AuditQueryService` — JdbcTemplate 기반. JPA repository 없음 — last24h count는 `JdbcTemplate.queryForObject` native 1개 필요.

### Frontend
- `app/admin/page.tsx` — v1.x 안내 카드 2개(`/admin/audit/logs`, `/admin/users`) + deferred 목록(`'대시보드','부서',…`). `'부서'`는 이미 `admin-department-crud` PR로 활성화되었으나 본 페이지 deferred 라벨에 stale.
- `components/admin/AdminSideNav.tsx` — `ACTIVE_ITEMS`: 감사 로그/사용자/부서 3개. `DEFERRED_ITEMS`: 대시보드/권한/스토리지/휴지통/Legal Hold/정책/시스템 7개.
- `lib/api.ts` / `lib/queryKeys.ts` — admin 관련 wrapper/key 보유 (User, Department). dashboard 부재.
- `app/admin/layout.tsx` — `AdminSideNav` 렌더 (변경 불필요).

### 의존 / 제약
- 병렬 worktree 2개 진행 중 (master 외):
  - `wave2-t6-folder-items-wire` — folder/file backend + `lib/api.ts`/`useUpload`/`useCurrentFolder`/`useFilesInFolder` 등.
  - `sleepy-agnesi-3b2dca` — 알 수 없으나 위 영역 동시 가능.
- 본 트랙은 위 영역을 건드리지 않음. `lib/api.ts`/`lib/queryKeys.ts`는 **파일 끝에만** 추가 (rebase 충돌 면적 최소화).

## 목표 상태

### Backend
- **DB / 마이그레이션 ❌** — 스키마 무변경. count/sum 추가만.
- **Repository (derived count 메서드 — `@Query` native 아님)**:
  - `UserRepository.countByDeletedAtIsNull()`
  - `UserRepository.countByDeletedAtIsNullAndIsActiveTrue()`
  - `DepartmentRepository.countByDeletedAtIsNull()`
  - `FolderRepository.countByDeletedAtIsNull()`
  - `FileRepository.countByDeletedAtIsNull()`
  - `FileRepository.countByDeletedAtIsNotNull()`
- **Repository (native `@Query` 1개)**:
  - `FileVersionRepository.sumAllSizeBytes()` — `SELECT COALESCE(SUM(v.sizeBytes), 0) FROM FileVersion v` (JPQL aggregation, 인덱스 부담 없음).
- **Audit 카운트 (JdbcTemplate, audit는 JPA 미도입이라 일관성 유지)**:
  - `AdminDashboardService` 내부에서 `jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE occurred_at >= ?", Long.class, since)`.
- **Service (`AdminDashboardService`)** — 신규:
  - `getSummary()` → `AdminDashboardSummaryResponse` 조립. `@Transactional(readOnly = true)`. 내부 호출 8개 (count 6 + SUM 1 + audit count 1).
- **Controller (`AdminDashboardController`)** — 신규:
  - `GET /api/admin/dashboard/summary` `@PreAuthorize("hasRole('ADMIN')")`.
  - 응답 envelope `{ summary: { users:{total,active}, departments:{total,active}, folders:{active}, files:{active,trashed}, audit:{last24h}, storage:{usedBytes} } }`.
- **DTO**: `AdminDashboardSummaryResponse` (record) + 중첩 records (`Users`, `Departments`, `Folders`, `Files`, `Audit`, `Storage`).
- **Audit emit ❌** — read-only summary. listener 없음.
- **에러**: 카운트 중 일부 실패 → 500 (envelope 오염 방지). 부분 응답 ❌.

### Frontend
- `frontend/src/lib/api.ts` 끝에 `getAdminDashboardSummary()` 추가 (CSRF 불필요 — GET).
- `frontend/src/lib/queryKeys.ts` 끝에 `qk.adminDashboard()` + 의도 주석.
- `frontend/src/types/admin.ts` (NEW) — `AdminDashboardSummary` 타입 미러.
- `frontend/src/hooks/useAdminDashboardSummary.ts` (NEW) — `useQuery`, `staleTime: 60_000`, `refetchOnWindowFocus: true`. 자동 setInterval ❌.
- `frontend/src/components/admin/DashboardKpiCard.tsx` (NEW) — `{ label, value, hint?, href? }` props. 숫자 포맷팅 (`Intl.NumberFormat('ko-KR')`), bytes는 KB/MB/GB/TB 헬퍼.
- `frontend/src/components/admin/DashboardSummary.tsx` (NEW) — KPI 카드 6개 컴포지션, loading skeleton (회색 placeholder), error fallback (재시도 버튼).
- `frontend/src/lib/formatBytes.ts` (NEW or 기존 유틸 재사용) — `formatBytes(n)` (1024 base, ko-KR locale 숫자 포맷).
- `frontend/src/app/admin/page.tsx` — 전면 재작성. v1.x 안내 카드 + deferred 목록 제거 → `<DashboardSummary />` 단일 컴포지션.
- `frontend/src/components/admin/AdminSideNav.tsx`:
  - `ACTIVE_ITEMS` **최상단에** `{ label: '대시보드', href: '/admin', match: 'exact' }` 추가.
  - `DEFERRED_ITEMS`에서 `'대시보드'` 제거 (`'부서'`는 이미 ACTIVE_ITEMS에 존재 — 변경 없음).
  - `match: 'exact'`인 이유: `/admin/users` 진입 시 `pathname.startsWith('/admin')`로 잘못 active 표시되는 문제 회피.

### Docs
- `docs/04-admin-operations.md` §2 라우트 트리 — `/admin` (대시보드) 활성 명시 + §3 (스켈레톤) → 본 트랙 KPI 6종 명세 채움.
- `docs/02-backend-data-model.md` §7 — `GET /api/admin/dashboard/summary` 엔드포인트 1행 추가.
- `BETA-RELEASE.md` — admin entry 활성 페이지 카운트 정정 (감사/사용자/부서 → +대시보드).
- `docs/progress.md` — 본 트랙 closure entry.

## Phase

### P1 — Backend (repo + service + controller + DTO + 테스트)
- 6 derived `count*` 메서드 + `FileVersionRepository.sumAllSizeBytes()` JPQL.
- `AdminDashboardSummaryResponse` (+ 중첩 records).
- `AdminDashboardService.getSummary()` — 카운트 8개 호출 → DTO. `@Transactional(readOnly = true)`. JdbcTemplate 의존 주입 (audit count).
- `AdminDashboardController.summary()` — `GET /api/admin/dashboard/summary` `@PreAuthorize("hasRole('ADMIN')")`.
- 테스트:
  - `AdminDashboardServiceTest` (Mockito) — 8 mock 호출, DTO 조립 검증.
  - `AdminDashboardControllerTest` (`@WebMvcTest`) — 200 (ADMIN) / 401 (anonymous) / 403 (USER role).
  - `AdminDashboardRepositoryIT` (`@DataJpaTest` + JdbcTemplate) — 6 derived count + 1 SUM + audit count의 실제 동작 확인 (활성/비활성/삭제 row 셋업 후 검증).
- gate: `cd backend && ./gradlew test` GREEN — 신규 테스트 ≥10.

### P2 — Frontend api wrapper + queryKey + hook + types + 테스트
- `types/admin.ts` (`AdminDashboardSummary` 타입).
- `lib/api.ts` `getAdminDashboardSummary()` (파일 끝 append).
- `lib/queryKeys.ts` `qk.adminDashboard` (파일 끝 append).
- `lib/formatBytes.ts` (또는 기존 utility 확장).
- `hooks/useAdminDashboardSummary.ts`.
- 테스트:
  - `lib/api.adminDashboard.test.ts` — fetch URL/메서드/200/401/403 envelope.
  - `hooks/useAdminDashboardSummary.test.tsx` — loading/success/error + cacheKey + staleTime.
  - `lib/formatBytes.test.ts` — 0 / B / KB / MB / GB / TB 경계.
- gate: `cd frontend && pnpm test --run` (기존 통과 갯수 + 신규 ≥6).

### P3 — Frontend 페이지 재작성 + KPI 카드 + AdminSideNav 정정 + 테스트
- `components/admin/DashboardKpiCard.tsx`.
- `components/admin/DashboardSummary.tsx`.
- `app/admin/page.tsx` 전면 재작성.
- `components/admin/AdminSideNav.tsx` — ACTIVE에 '대시보드' 추가, DEFERRED에서 '대시보드' 제거.
- 테스트:
  - `DashboardKpiCard.test.tsx` — value/hint/href 렌더, 숫자 포맷팅.
  - `DashboardSummary.test.tsx` — loading skeleton / error fallback / 6 카드 표시.
  - `app/admin/page.test.tsx` — 페이지 렌더 + KPI 표시 + 401/403 처리.
  - `AdminSideNav.test.tsx` — '대시보드' active 표기 (`/admin` exact), DEFERRED 목록에서 '대시보드' 부재.
- gate: `cd frontend && pnpm test --run && pnpm typecheck && pnpm lint && pnpm build` exit 0.

### P4 — Docs sync + closure + PR
- `docs/04 §2 §3` 갱신.
- `docs/02 §7` 1행 추가.
- `BETA-RELEASE.md` admin entry wording.
- `docs/progress.md` 본 트랙 entry.
- `dev/active/admin-dashboard/` → `dev/completed/`.
- PR 생성 — 본문에 "병렬 wave2-t6 / sleepy-agnesi 세션과 파일 충돌 면적 검토 완료 (lib/api.ts·queryKeys.ts append-only, AdminSideNav 단독)" 명시.

## Acceptance Criteria

- `cd backend && ./gradlew test` GREEN — 신규 테스트 ≥10.
- `cd frontend && pnpm test --run && pnpm typecheck && pnpm lint && pnpm build` exit 0 — 신규 테스트 ≥9.
- `GET /api/admin/dashboard/summary` 200 (ADMIN) — envelope schema 일치.
- `GET /api/admin/dashboard/summary` 401 (미인증) / 403 (USER role).
- `/admin` 페이지 — 6 KPI 카드 그리드 표시, loading skeleton 동작, 에러 시 재시도 버튼.
- `AdminSideNav` — '대시보드'가 ACTIVE_ITEMS 최상단, `/admin` 정확히 일치 시 active. DEFERRED에서 '대시보드' 제거.
- `storage.usedBytes` SUM 결과가 `file_versions.size_bytes` 전체 합과 일치 (수동 검증: `psql -c "SELECT SUM(size_bytes) FROM file_versions"`).
- `audit.last24h` — 24시간 내 `audit_log` row 수와 일치.

## 검증 게이트

- backend: junit (`AdminDashboardServiceTest`, `AdminDashboardControllerTest`, `AdminDashboardRepositoryIT`).
- frontend: vitest + typecheck + lint + build.
- 수동: `/admin` 진입 → KPI 카드 6개 노출 + 새로고침 시 skeleton → 데이터 swap.

## 리스크 / 완화

| 리스크 | 완화 |
|---|---|
| audit count가 audit_log full scan — 인덱스 부담 | `audit_log`의 `occurred_at` 인덱스(V3)가 이미 존재. range scan으로 KPI 부담 미미 (MVP 데이터 < 1M row 가정). 큰 데이터셋 시 timestamp partition 별도 결정. |
| storage SUM이 `file_versions` full scan | MVP 데이터셋 가정 하에 단순 SUM 허용. 큰 데이터셋 시 materialized view 또는 별도 cron으로 갱신 결정 (v1.x). |
| 병렬 worktree(`wave2-t6` / `sleepy-agnesi`)가 `lib/api.ts` 동시 수정 | append-only 정책으로 git auto-merge 가능. 충돌 발생 시 본 트랙이 후순위 rebase하며 해결. |
| `AdminSideNav` exact match (`/admin`)으로 `/admin/users` 진입 시 대시보드도 active 되는 문제 | `match: 'exact'` 사용. 기존 prefix 항목과 분기 — `pathname === item.href` 비교. 기존 prefix 항목 영향 없음. |
| `Department` total/active envelope 필드 둘 다 동일값 — 사용자 혼란 | UI는 `departments` 카드에 단일 숫자 + 라벨 "부서". envelope 형태 일관성을 위해 둘 다 유지 (백엔드 schema 안정성 우선). docs/04 §3에 명시. |
| `users.active` 정의 모호 (last login? is_active flag?) | `is_active=TRUE AND deleted_at IS NULL` — admin-user-mgmt 정책 동형 (계정 잠금/비활성 분기). `last login`은 본 트랙 범위 외. |
