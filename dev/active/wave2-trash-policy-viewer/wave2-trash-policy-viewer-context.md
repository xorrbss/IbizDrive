# Context — trash policy viewer

## 진실의 출처

- `TrashRetentionProperties` (PR #108로 외부화) — `app.trash.retention.days`, default 30, 0/음수 보정.
- 신규 endpoint `GET /api/admin/trash/policy`가 이 record의 값을 그대로 노출.

## 신규 파일

**Backend**
- `com.ibizdrive.admin.trash.AdminTrashPolicyDto` — `record(int retentionDays)`
- `com.ibizdrive.admin.trash.AdminTrashPolicyController` — `GET /api/admin/trash/policy`, ADMIN-only
- `com.ibizdrive.admin.trash.AdminTrashPolicyControllerTest` — 200/401/403 + non-default 값 회귀

**Frontend**
- `types/admin-trash-policy.ts` — `AdminTrashPolicy` 인터페이스
- `hooks/useAdminTrashPolicy.ts` — `useQuery` 패턴 (admin-storage-overview와 동형)
- `app/admin/trash/policy/page.tsx` — AdminGuard + 카드 3개 (보존 기간 / cron cross-link / 변경 방법)
- `app/admin/trash/policy/page.test.tsx` — loading/error/success/non-default/cross-link/h1

## 기존 파일 수정

- `lib/api.ts` — `getAdminTrashPolicy()` 추가 (`getAdminStorageOverview` 직후, 패턴 동형)
- `lib/queryKeys.ts` — `qk.adminTrashPolicy()` 추가 (adminTrashList 직후)
- `components/admin/AdminSideNav.tsx` — `'휴지통 정책'` 링크 + DEFERRED_ITEMS에서 '정책' 제거
- `components/admin/AdminSideNav.test.tsx` — ADMIN test에 휴지통 + 휴지통 정책 검증 추가

## 영향받지 않는 곳

- 기존 `/api/admin/trash` listing / bulk endpoints — 무변경
- `AdminTrashController`/`AdminTrashService`/`AdminTrashRepository` — 무변경 (활용도 ↑이지만 추가 0)
- `/admin/system` — cron 운영 상태는 그쪽 책임, 본 트랙 무관
- 다른 active worktree와의 충돌 — 새 파일 위주, 기존 수정은 신규 라인 추가 (queryKeys, api.ts)

## 관련 문서

- `docs/02 §7` admin trash 표 — `GET /api/admin/trash/policy` 신규 row 추가
- `docs/04 §8.3` — 휴지통 정책 viewer closure 마커
