---
date: 2026-05-07
wave: 2
track: T9
topic: admin-global-trash
status: design
related:
  - docs/04-admin-operations.md §8.3
  - docs/02-backend-data-model.md §7.11
  - BETA-RELEASE.md §7
prereqs:
  - Wave 2 T6 folder-items-wire (PR #67, merged 2026-05-07)
  - A6 restore endpoints (already shipped)
  - A8 trash list/purge endpoints (already shipped)
---

# Wave 2 T9 — Admin Global Trash (`/admin/trash/all`)

## 1. 목적

사내 베타 시 ADMIN이 시스템 전체 휴지통을 한 화면에서 보고 다른 user의 실수 삭제를 복원할 수 있는 **마지막 안전망**. `app.purge.enabled=true` 운영 (prod profile, mvp-prod-profile closure)에서 영구 삭제 직전 ADMIN의 인지·복원 기회를 제공한다.

closure 대상 — `docs/04 §8.3`:

> [ ] 전체 사용자의 휴지통 파일 (관리자 전용) — *v1.x deferred (admin frontend 미구현)*

## 2. 비목적

- **휴지통 정책 페이지 `/admin/trash/policy`** (보존 기간 조정 UI) — application.yml + 재기동으로 1차 충분, v1.x.
- **즉시 영구삭제 승인 워크플로** (2인 승인 등, docs/04 §8.3) — v1.x.
- **bulk restore / bulk purge** — single-row 패턴이 사내 베타 운영에 충분, v1.x.
- **`deleted_by` 컬럼 추가** (V10 schema) — audit_log lookup으로 차선 경로 존재, v1.x.
- **full path resolve** (recursive CTE) — immediate parent name으로 80% 효용 확보, v1.x.
- **날짜 범위 / deletedBy 필터** — `q` + `type` + `ownerId` 3개로 90% 운영 시나리오 커버, v1.x.

## 3. 배경 — 기존 백엔드 자산

코드 조사 결과, **백엔드 mutation은 모두 admin-capable 상태**:

| Endpoint | 가드 | ADMIN 통과 사유 |
|---|---|---|
| `GET /api/trash` | `isAuthenticated()` + service 단 `Permission.DELETE` 후처리 | `ROLE.ADMIN.effectivePermissions().contains(DELETE)` ⇒ 시스템 전체 row 노출 |
| `POST /api/files/{id}/restore` | `hasPermission(#id, 'file', 'DELETE')` | ADMIN ROLE이 effective DELETE 보유 |
| `POST /api/folders/{id}/restore` | `hasPermission(#id, 'folder', 'DELETE')` | 동상 |
| `DELETE /api/trash/{type}/{id}` | `hasRole('ADMIN')` | 직접 |
| audit emit `FILE_RESTORED`, `FOLDER_RESTORED`, `FILE_PURGED`, `FOLDER_PURGED` | — | 기존 listener에서 emit |

→ 갭은 **(a) admin DTO에 필요한 metadata 부재** (소유자/원경로/사이즈), **(b) admin frontend 페이지 부재** 두 가지.

## 4. 설계 결정

### 4.1 Listing endpoint 분리 — 신규 `GET /api/admin/trash`

신규 endpoint를 `/api/admin/**` namespace에 둔다. 기존 `GET /api/trash`는 그대로 보존.

**근거:**
- T1/T4/T5/T7/T8 admin 트랙 모두 동형 namespace 패턴.
- DTO 분리가 곧 권한 contract 분리. ADMIN 시야가 우연한 superset이 아니라 의도된 endpoint.
- user-facing DTO는 owner/path 미노출이 정책 — 기존 `TrashItemDto` shape를 흔들지 않음.

### 4.2 Mutation 재사용 — 신규 endpoint 0

restore / purge는 기존 endpoint를 그대로 호출.

| 동작 | 호출 endpoint |
|---|---|
| 단건 file 복원 | `POST /api/files/{id}/restore` |
| 단건 folder 복원 | `POST /api/folders/{id}/restore` |
| 단건 영구삭제 | `DELETE /api/trash/{type}/{id}` |

**근거:**
- 기존 service (`FileMutationService.restore` / `FolderMutationService.restore` / `TrashPurgeService.purge*`)는 이미 `@Transactional` + SELECT FOR UPDATE + RESTORE_CONFLICT(409) + audit emit + cascade 잔존 정책(폴더 자기 자신만)이 검증됨.
- mirror endpoint = thin wrapper = 로직 중복. KISS / CLAUDE.md §3 원칙 4 (구조 무결성).
- ADMIN ROLE이 SpEL 가드를 통과하는 contract가 의도된 (docs/03 §3.1 ROLE.ADMIN ⇒ 전 9 Permission).

### 4.3 Audit emit 변경 0

기존 `FILE_RESTORED` / `FOLDER_RESTORED` / `FILE_PURGED` / `FOLDER_PURGED` 그대로. 47 enum 변경 없음.

**cross-owner 식별:** `actor_id != target.owner_id` 조회 (audit_log + files/folders join)로 식별 가능. metadata flag (`crossOwner: true`) 추가도 회귀 가드 비용 대비 효용 낮음 — query convenience만 제공하나 동일 정보를 join으로 추출 가능.

**근거:** T4 `ADMIN_USER_DEACTIVATED` ↔ `ADMIN_USER_UPDATED` 분리 정책은 "제재 vs 일반 변경" 의미 분리이지 actor가 ADMIN인지 자체로 enum을 나눈 게 아님. restore/purge는 user/admin 동일 의미의 동일 동작 ⇒ 분리 사유 없음.

### 4.4 Listing DTO scope

```text
AdminTrashItemDto = {
  id            : UUID,
  name          : String,
  type          : "file" | "folder",
  deletedAt     : Instant,
  purgeAfter    : Instant,
  ownerId       : UUID,
  ownerEmail    : String,           // LEFT JOIN users
  originalParentId   : UUID | null,  // null = root
  originalParentName : String | null,// LEFT JOIN folders by original_*_id (null = root)
  sizeBytes     : long | null       // files만, folders는 null
}
```

**미포함:**
- `deletedBy` — `files.deleted_by` / `folders.deleted_by` 컬럼 부재. 추가하면 V10 migration + 모든 delete service에 actor 전달 + 기존 row NULL backfill ⇒ 회귀 가드 비용 ↑. audit_log에서 actorId로 조회로 차선 경로 존재.
- `originalPath` 전체 — recursive CTE 회피. immediate parent name으로 80% 효용 확보.
- folder subtree size 합산 — A6 정책상 폴더 복원은 자기 자신만 (후손 잔존). subtree size를 보여줘도 의사결정 가치 낮음.

**필터:**
- `q` — name LIKE escape (`%`, `_`, `\`), case-insensitive, 300ms debounce.
- `type` — `file` / `folder` / null (양쪽).
- `ownerId` — UUID, optional.
- `cursor`, `limit` — 기존 `TrashCursor` 패턴 (deletedAt DESC + id DESC tie-break), default 50 / max 100.

### 4.5 Frontend UX

| 동작 | UX |
|---|---|
| Restore | 즉시 실행 + toast "복원했습니다". 비파괴적 (docs/01 §19 원칙 3) — confirm 미사용. |
| Purge | ConfirmDialog 필수 ("영구 삭제하시겠습니까? 이 동작은 되돌릴 수 없습니다."). 불가역. |
| Filter | q (300ms debounce) + ownerId(text input) + type select. 변경 시 cursor=null 리셋. |
| Pagination | cursor next/prev. T5 page-based와 다름(cursor 모델). page 전환 깜빡임 완화는 `placeholderData: keepPreviousData`. |
| Empty state | "휴지통이 비어 있습니다" 또는 "필터에 일치하는 항목이 없습니다" |

### 4.6 권한

- listing endpoint: `@PreAuthorize("hasRole('ADMIN')")`. AUDITOR 제외 (T8 storage-overview 동형 — 운영 액션 페이지는 ADMIN, AUDITOR는 audit_log 직접 조회로 우회).
- mutation: 기존 SpEL 그대로.

## 5. 컴포넌트

### 5.1 Backend (신규)

| 파일 | 역할 |
|---|---|
| `admin/trash/AdminTrashController.java` | `GET /api/admin/trash` + `@PreAuthorize("hasRole('ADMIN')")` |
| `admin/trash/AdminTrashService.java` | `list(filters, cursor, limit)` — source별 page query + merge sort + cursor 계산. 권한 후처리 0 |
| `admin/trash/AdminTrashRepository.java` | native `@Query` 2종: `findTrashedFilesAdminPage(q, ownerId, cursor, limit)` + `findTrashedFoldersAdminPage(...)`. LEFT JOIN users + LEFT JOIN folders(original parent), LIKE escape, deleted_at DESC + id DESC |
| `admin/trash/AdminTrashItemDto.java` | record (10 fields) |
| `admin/trash/AdminTrashPage.java` | record (`items: List<AdminTrashItemDto>`, `nextCursor: String?`) |
| `admin/trash/AdminTrashFilters.java` | record (`q: String?`, `type: TrashItemType?`, `ownerId: UUID?`) |

기존 `TrashCursor` (already in `trash/`)는 admin 트랙에서도 재사용. `TrashItemType`도 재사용.

### 5.2 Frontend (신규/확장)

| 파일 | 역할 |
|---|---|
| `types/trash.ts` (확장) | `AdminTrashItem`, `AdminTrashFilters`, `AdminTrashPage` |
| `lib/api.ts` (add) | `adminListTrash(filters, cursor)` GET — URLSearchParams로 빈 값 omit, 401/403/400 envelope `buildApiError` 매핑 |
| `lib/queryKeys.ts` (add) | `qk.adminTrash`, `qk.adminTrashList(filters, cursor?)` factory + `invalidations.afterAdminTrashChanged` |
| `hooks/useAdminTrash.ts` (신규) | `useAdminTrashList(filters)` — cursor state 내장. `useAdminRestoreTrashItem()` (POST file/folder restore reuse) + `useAdminPurgeTrashItem()` (DELETE trash reuse) — onSuccess에서 `afterAdminTrashChanged` invalidate |
| `app/admin/trash/all/page.tsx` (신규) | FilterBar(q + ownerId + type) + Table(8 컬럼: name, type 배지, owner, originalParent, size, deletedAt, purgeAfter, actions) + 행별 [복원]/[영구삭제] 버튼 + ConfirmDialog (purge) + cursor pagination |
| `components/admin/AdminSideNav.tsx` (toggle) | '휴지통' DEFERRED → ACTIVE_ITEMS |
| `app/admin/page.tsx` (toggle) | landing 카드에 '전역 휴지통' 추가, deferred 리스트에서 제거 |

### 5.3 Docs

| 문서 | 변경 |
|---|---|
| `docs/02 §7.11` | `GET /api/admin/trash` 행 + 풀 스펙 블록 (요청/응답/필터 매트릭스/cursor 정책/audit emit 0) |
| `docs/04 §2` | 활성 라우트 5 → 6, 트리에서 `/admin/trash/all` 활성 swap (서브트리 `/policy`는 v1.x 유지) |
| `docs/04 §8.3` | 전역 휴지통 뷰 ✓ (`/admin/trash/all` 활성, single-row purge reuse, 워크플로 v1.x 유지) |
| `BETA-RELEASE.md` | header Source 트랙 추가 + §7 admin frontend wording 갱신 (`/admin/trash/all` 활성) |

## 6. 데이터 흐름

```text
ADMIN /admin/trash/all 진입
  → AuthGuard (already)→ AdminGuard (already)
  → useAdminTrashList({q:"", type:null, ownerId:null}, cursor=null)
    → GET /api/admin/trash?limit=50
      → AdminTrashController.list (@PreAuthorize hasRole ADMIN)
      → AdminTrashService.list
        → AdminTrashRepository.findTrashedFilesAdminPage + findTrashedFoldersAdminPage (over-fetch +1)
        → in-memory merge sort (deletedAt DESC, id DESC)
        → cursor 계산 (hasMore 시 last item key encode)
        → AdminTrashPage 응답
  → Table 렌더 (10 fields × N rows)

ADMIN [복원] 클릭
  → useAdminRestoreTrashItem({id, type})
    → POST /api/files/{id}/restore  또는  POST /api/folders/{id}/restore
    → 200 / 409 RESTORE_CONFLICT / 404
  → onSuccess
    → invalidate qk.adminTrashList() prefix
    → invalidate qk.filesInFolder(originalParentId) (복원 위치 listing 갱신)
    → toast "복원했습니다"
  → onError (409) → toast "원위치에 같은 이름이 이미 있습니다"
  → onError (404) → toast "원폴더가 사라졌습니다 — 먼저 폴더를 복원하세요"

ADMIN [영구삭제] 클릭
  → ConfirmDialog "영구 삭제하시겠습니까? 이 동작은 되돌릴 수 없습니다."
  → 확인 → useAdminPurgeTrashItem({type, id})
    → DELETE /api/trash/{type}/{id}
    → 204 / 404
  → onSuccess
    → invalidate qk.adminTrashList() prefix
    → toast "영구 삭제했습니다"
  → onError (404) → toast "이미 삭제된 항목입니다" + 동상 invalidate (자연 회복)
```

## 7. 에러 처리

| 시나리오 | HTTP | UX |
|---|---|---|
| listing 401 | 401 | AuthGuard redirect `/login?next=/admin/trash/all` |
| listing 403 (MEMBER) | 403 | AdminGuard redirect `/files` (이미 처리, 본 트랙 변경 0) |
| listing 400 (q 너무 김 / ownerId 형식) | 400 | inline error |
| restore 409 RESTORE_CONFLICT | 409 envelope | toast "원위치에 같은 이름이 이미 있습니다" |
| restore 404 (parent soft-deleted) | 404 | toast "원폴더가 사라졌습니다 — 먼저 폴더를 복원하세요" |
| purge 404 (race: 이미 hard-deleted) | 404 | toast + listing invalidate |

## 8. 테스트 게이트 (TDD)

### 8.1 Backend

`AdminTrashServiceTest`:
- q LIKE escape (`%`, `_`, `\` 케이스)
- ownerId 필터링 (file/folder 양쪽 동시)
- type=FILE / type=FOLDER / type=null merge sort
- cursor 페이지 경계 (over-fetch +1, hasMore 계산, nextCursor encode)
- empty result (nextCursor=null)
- joins (ownerEmail, originalParentName)

`AdminTrashControllerTest` (`@WebMvcTest`):
- 200 with admin (paginated)
- 401 unauthenticated
- 403 MEMBER
- 403 AUDITOR
- query string echo (q, ownerId, type, cursor, limit)
- limit cap (101 → 100)

### 8.2 Frontend

`lib/api.adminTrash.test.ts`:
- q encode (URLSearchParams + omit blank)
- 401/403/404/400 envelope → `buildApiError`
- cursor pass-through

`hooks/useAdminTrash.test.tsx`:
- q debounce 300ms
- cursor 진행 (next/prev)
- restore mutation invalidates `qk.adminTrashList` + `qk.filesInFolder`
- purge mutation invalidates `qk.adminTrashList`

`app/admin/trash/all/page.test.tsx`:
- 행 렌더 (8 컬럼)
- restore 클릭 → 즉시 mutation
- purge 클릭 → ConfirmDialog → 확인 → mutation
- empty state
- filter 변경 시 cursor 리셋

## 9. 영향 범위 + 회귀 가드

| 영역 | 변경 |
|---|---|
| 기존 `GET /api/trash` user-facing | **변경 0** (그대로) |
| 기존 `POST /api/files/{id}/restore`, `POST /api/folders/{id}/restore` | **변경 0** |
| 기존 `DELETE /api/trash/{type}/{id}` | **변경 0** |
| audit emit | 47 enum **변경 0** |
| DB schema | V10 **없음** |
| 권한 enum (9종) | **변경 0** |
| 신규 endpoint 노출 표면 | ADMIN-only — MEMBER/AUDITOR 노출 0 |

## 10. 머지 게이트

- `cd backend && ./gradlew test` — BUILD SUCCESSFUL
- `cd frontend && pnpm test --run` — 전체 GREEN (신규 ~24 케이스 추가, §8 상세)
- `pnpm typecheck && pnpm lint && pnpm build` — exit 0
- T7 admin-dashboard PR #70 (CONFLICTING)와의 conflict 사전 점검:
  - `lib/api.ts`: T7 `adminGetDashboardSummary` 추가 / T9 `adminListTrash` 추가 — 다른 함수, add-only conflict 자명 머지
  - `lib/queryKeys.ts`: `adminDashboard*` vs `adminTrash*` namespace 분리 — 자명
  - `components/admin/AdminSideNav.tsx`: T7 '대시보드' / T9 '휴지통' 둘 다 ACTIVE 토글 — 같은 배열 add-only, 다른 키 자연 머지
  - `app/admin/page.tsx` landing: T7/T9 같은 파일 카드 add — 자연 머지

## 11. 단일 PR 스코프 정당성

- **backend**: 1 controller + 1 service + 1 repository + 3 records (DTO/Page/Filters) + 2 test classes (~15 cases). ~700 LOC 신규.
- **frontend**: 1 page + 1 hook file + types extend + api.ts add + queryKeys.ts add + AdminSideNav 1줄 + landing 1 카드. ~600 LOC 신규.
- **docs**: 4 파일 wording 갱신.

총 분량 = T6 (folder-items-wire) / T5 (admin-permission-matrix) 규모. 단일 PR로 ship 가능.

## 12. v1.x deferred (본 트랙 외)

- `deleted_by` 컬럼 + V10 schema (필요 시 별도 트랙)
- 휴지통 정책 페이지 `/admin/trash/policy` (보존 기간 조정 UI)
- 즉시 영구삭제 승인 워크플로 (2인 승인)
- bulk restore / bulk purge
- 날짜 범위 필터 / deletedBy 필터
- full path resolve (recursive CTE)
- folder subtree size 합산
- bulk DELETE `/api/admin/trash` (전체 비우기)

## 13. 핵심 원칙 충돌 점검 (CLAUDE.md §3)

| 원칙 | 점검 |
|---|---|
| 1. KISS | mutation 재사용 / audit emit 변경 0 / DB schema 변경 0 — 최소 surface |
| 2. YAGNI | bulk / 날짜 필터 / deletedBy / full path / subtree size 모두 v1.x |
| 3. 기존 구조 우선 | `/api/admin/**` namespace 일관 / `TrashCursor` 재사용 / 기존 mutation endpoint 호출 |
| 4. 구조 무결성 | listing은 신규 (admin DTO contract), mutation은 재사용 (이미 admin-capable) — 의존 단방향 |
| 5. 확장 전 검토 | 기존 `TrashItemDto` 확장 vs 신규 admin DTO 분리 → 분리 채택 (user-facing contract 보존) |
| 6. 구조 우선 | DTO 분리가 권한 contract 분리와 정렬 |
| 7. 파일 제한 | 신규 파일 모두 500 LOC 미만 가정. page.tsx가 가장 큼(예상 ~300 LOC) |
| 8. 편법 금지 | per-row audit_log scan / `crossOwner` flag 추가 모두 회피 (편법 우회 안 함) |
| 9. 문제 은폐 금지 | `deletedBy` 미surface는 명시적 limitation으로 §4.4에 기록 |
| 10. 중단 및 보고 | 본 spec이 보고서 — 모든 결정 근거 포함 |
| 11. 정규화 함수 | 본 트랙은 정규화 함수 변경 0 |
| 12. 에러 코드 계약 | 신규 에러 코드 0 — 기존 RESTORE_CONFLICT / NOT_FOUND 재사용 |

## 14. Phase 분할 (writing-plans 입력 초안)

| Phase | 범위 | 게이트 |
|---|---|---|
| P1 (BE-1) | Repository — `findTrashedFilesAdminPage` + `findTrashedFoldersAdminPage` (native @Query, JOIN, LIKE escape) + repo 단위 테스트 | repo test GREEN |
| P2 (BE-2) | Service — `AdminTrashService.list` + DTO/Page/Filters records + cursor 재사용 + 단위 테스트 | service test GREEN |
| P3 (BE-3) | Controller — `AdminTrashController` + `@PreAuthorize("hasRole('ADMIN')")` + `@WebMvcTest` (200/401/403/필터 echo) | gradle test GREEN |
| P4 (FE-1) | types + api.ts + queryKeys.ts + invalidations + api 테스트 | api 테스트 GREEN |
| P5 (FE-2) | hooks (`useAdminTrashList` + `useAdminRestoreTrashItem` + `useAdminPurgeTrashItem`) + hooks 테스트 | hooks 테스트 GREEN |
| P6 (FE-3) | `/admin/trash/all/page.tsx` + ConfirmDialog + AdminSideNav 토글 + landing 카드 + page 테스트 | pnpm test --run / typecheck / lint / build GREEN |
| P7 (Docs) | docs/02 §7.11 / docs/04 §2 / docs/04 §8.3 / BETA-RELEASE §7 갱신 | grep으로 wording drift 0 |

## 15. 참조

- 기존 휴지통 트랙: `dev/completed/a8-trash-manage/`, `dev/completed/a7-hard-purge/`
- 기존 admin 트랙(동형 패턴): `dev/completed/admin-permission-matrix/` (T5), `dev/completed/admin-department-crud/` (T4), `dev/completed/wave1-t3-system-cron-readonly/`
- ADR #32 (휴지통 권한 후처리), ADR #38 (storage orphan cleanup), ADR #21 (admin namespace)
