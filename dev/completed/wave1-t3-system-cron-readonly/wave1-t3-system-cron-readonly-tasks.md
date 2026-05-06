# Wave 1 — T3: tasks

Last Updated: 2026-05-07

## phase별 상태

- **P1 backend** — pending
- **P2 frontend api/types/hook** — pending
- **P3 frontend page/sidenav/landing** — pending
- **P4 docs 정렬** — pending
- **P5 검증 + closure** — pending

## P1 — backend (TDD)

- [ ] P1.1 `CronJobStatusResponse` record 작성 (`@JsonInclude(NON_NULL)` for `batchSize`/`maxPerRun`/`graceHours`)
- [ ] P1.2 `AdminSystemControllerTest` 작성 (Spring `@WebMvcTest`)
- [ ] P1.3 `AdminSystemController` 구현 — 4 properties 빈 주입 + 변환

### 작업 전 필독
- `dev/active/wave1-t3-system-cron-readonly/wave1-t3-system-cron-readonly-plan.md` §P1
- `backend/src/main/java/com/ibizdrive/admin/AdminUserController.java` (controller 패턴)
- `backend/src/test/java/com/ibizdrive/admin/AdminDepartmentControllerTest.java` (`@WebMvcTest` 패턴)

### 원본 코드 참조
- `backend/.../purge/HardPurgeProperties.java`
- `backend/.../share/ShareExpirationProperties.java`
- `backend/.../permission/PermissionExpirationProperties.java`
- `backend/.../storage/StorageOrphanCleanupProperties.java`

### 구현 대상
- `backend/src/main/java/com/ibizdrive/admin/AdminSystemController.java`
- `backend/src/main/java/com/ibizdrive/admin/CronJobStatusResponse.java`
- `backend/src/test/java/com/ibizdrive/admin/AdminSystemControllerTest.java`

### 검증 참조
- `cd backend && ./gradlew test --tests "com.ibizdrive.admin.AdminSystemControllerTest"`

### 문서 반영
- 신규 endpoint는 P4.1에서 `docs/02 §7`에 반영.

---

## P2 — frontend api/types/hook (TDD)

- [ ] P2.1 `types/system.ts` — `CronJobStatus`, `CronJobsResponse`
- [ ] P2.2 `lib/api.adminSystem.test.ts` — fetch + 401/403 throw
- [ ] P2.3 `lib/api.ts` — `adminGetCronStatus()` 추가
- [ ] P2.4 `lib/queryKeys.ts` — `adminSystemCron` 키 추가
- [ ] P2.5 `hooks/useAdminSystem.ts` — useQuery 단일 hook

### 작업 전 필독
- plan §P2
- `frontend/src/lib/api.ts` (`adminListUsers` 패턴 — URL/credentials)
- `frontend/src/types/audit.ts` (DTO mirror 패턴)
- `frontend/src/lib/queryKeys.ts` (`adminUsersList` 패턴)

### 원본 코드 참조
- 신규 backend endpoint (P1 결과)

### 구현 대상
- `frontend/src/types/system.ts`
- `frontend/src/lib/api.ts` (append)
- `frontend/src/lib/queryKeys.ts` (append)
- `frontend/src/hooks/useAdminSystem.ts`
- `frontend/src/lib/api.adminSystem.test.ts`

### 검증 참조
- `cd frontend && pnpm exec vitest run src/lib/api.adminSystem.test.ts`

### 문서 반영
- queryKey/타입은 명시 spec 갱신 없음(append-only export).

---

## P3 — frontend page/sidenav/landing (TDD)

- [ ] P3.1 `app/admin/system/page.test.tsx`
- [ ] P3.2 `app/admin/system/page.tsx`
- [ ] P3.3 `components/admin/AdminSideNav.tsx` — '시스템' DEFERRED 제거 + ACTIVE 추가
- [ ] P3.4 `app/admin/page.tsx` — 가용 카드 추가 + deferred 리스트 갱신

### 작업 전 필독
- plan §P3
- `frontend/src/app/admin/audit/logs/page.tsx` (페이지 head 패턴)
- `frontend/src/app/admin/departments/page.tsx` (단일 hook 페이지 패턴)
- `frontend/src/components/admin/AdminSideNav.tsx`

### 원본 코드 참조
- P2 결과 (hook + 타입)

### 구현 대상
- `frontend/src/app/admin/system/page.tsx`
- `frontend/src/app/admin/system/page.test.tsx`
- `frontend/src/components/admin/AdminSideNav.tsx`
- `frontend/src/app/admin/page.tsx`

### 검증 참조
- `cd frontend && pnpm exec vitest run src/app/admin/system/page.test.tsx`

### 문서 반영
- `docs/04 §2` 라우트 트리 갱신은 P4.2.

---

## P4 — docs 정렬

- [ ] P4.1 `docs/02 §7` admin endpoint 표 + 신규 endpoint 명세 행
- [ ] P4.2 `docs/04 §2` 라우트 트리 — `/system` 활성 표기 + 헤더 wording / `docs/04 §13` cross-link
- [ ] P4.3 `BETA-RELEASE.md §7` admin frontend wording (시스템 페이지 활성 표기)

### 작업 전 필독
- `docs/04-admin-operations.md` line 25-73 (현재 §2 트리)
- `docs/04-admin-operations.md` line 384-396 (§13 cron 표)
- `docs/02-backend-data-model.md` §7 (admin endpoint 섹션 위치)
- `BETA-RELEASE.md` line 113 (admin frontend deferred wording)

### 구현 대상
- `docs/02-backend-data-model.md`
- `docs/04-admin-operations.md`
- `BETA-RELEASE.md`

### 검증 참조
- grep으로 '시스템 페이지' v1.x 잔존 확인
- grep으로 신규 endpoint 인용 정합성

### 문서 반영
- 본 phase가 그 자체로 문서 반영.

---

## P5 — verification + dev-docs sync + closure

- [ ] P5.1 backend `./gradlew test` GREEN (전체)
- [ ] P5.2 frontend `pnpm test --run && pnpm typecheck && pnpm lint && pnpm build` 전부 exit 0
- [ ] P5.3 `docs/progress.md` closure entry 최상단 추가
- [ ] P5.4 self review (Self Review 요약)
- [ ] P5.5 PR 생성 (master 대상)
- [ ] P5.6 머지 후 `dev/active/...` → `dev/completed/...` archive (별도 commit)
- [ ] P5.7 `dev/process/wave1-t3-2026-05-07.md` 삭제

### 작업 전 필독
- `dev/completed/audit-export-endpoint/audit-export-endpoint-tasks.md` (closure 패턴)
- `docs/progress.md` 최상단 (양식)

### 구현 대상
- `docs/progress.md`
- 본 dev-docs 3파일

### 검증 참조
- backend / frontend 풀세트 명령
