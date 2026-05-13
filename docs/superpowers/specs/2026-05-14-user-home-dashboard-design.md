# User Home Dashboard — Design Spec

> **Status**: Design (brainstorming closure 2026-05-14)
>
> **Track**: 사용자 측 Home/Dashboard 신규 — 로그인 직후 진입점에 personal dashboard 도입.
>
> **Scope**: M — backend endpoint 1 신규 (shared-with-me) + frontend page 1 + widget 4 + URL 라우팅 1건 변경. 단일 PR. 즐겨찾기 endpoint/hook 은 병렬 진행 중인 favorites-list 트랙 (working tree WIP, 2026-05-14) 에 dependency.
>
> **Dependency**: favorites-list 트랙 머지 후 시작 — `GET /api/me/favorites` + `useMyFavorites` + `types/favorite.ts` 를 reuse. dashboard StarredCard 는 자체 endpoint 정의 없이 그 hook 호출.

---

## 1. 목표 (Why)

현재 `frontend/src/app/page.tsx` 의 `/` route 는 `useWorkspaces` 결과로 **첫 부서 → 첫 팀 → 0 workspace 안내** 순으로 redirect 만 한다 (`buildWorkspacePath` 후 `router.replace`). 이는 사용자에게 "내 정체성 / 내 활동" 의 entry 가 부재하다는 의미이며, 즐겨찾기 (P2a backend #237 closure), quota (Phase 6 closure), 공유받은 항목 같은 **personal context** 가 사이드바 탐색을 통해서만 접근 가능하다.

본 트랙은 `/` route 를 personal home dashboard 로 정의하고 4 위젯 (환영 헤더 + 즐겨찾기 + storage quota + shared-with-me) 으로 사용자 entry UX 를 정착시킨다. design-zip 에 mock 부재이므로 admin overview (PR #200) 의 `SectionCard` + `admin-grid` 패턴을 reuse 하여 시각 일관성을 확보한다.

---

## 2. URL 라우팅 결정

### 2.1 결정

- **`/` (root) → User Home Dashboard 렌더.**
- workspace 진입은 **사이드바 트리** 가 단일 진입점 (이미 존재 — `FolderTree`).
- 첫 로그인 후 부서/팀이 둘 다 0 인 경우 (현재 fallback) 는 dashboard 의 zero-state 카드 1 종으로 흡수: "아직 소속된 workspace 가 없습니다. 관리자에게 부서 배정을 요청하거나, 팀을 만드세요."

### 2.2 제거되는 동작

- `useEffect` 내 `router.replace(buildWorkspacePath(...))` 자동 redirect — **삭제**.
- `useWorkspaces` 는 dashboard 의 환영 헤더 + 사이드바 양쪽에서 그대로 사용 (workspace 메타데이터 source).

### 2.3 CLAUDE.md §3 원칙 1 (URL 이 "어디" 를 소유) 정합성

원칙 1 의 적용 범위는 **워크스페이스 내부의 folder/file URL** 이다 (`/d/:deptId/:folderId/...`). 본 변경은 root `/` 의 의미를 redirect 에서 dashboard 로 바꾸는 것 — 워크스페이스 내부 URL 의 owner 는 그대로 URL 이므로 원칙 1 위반 아님. ADR 신규 entry 1 건 추가 (00-overview.md).

---

## 3. 위젯 Inventory

```
┌─────────────────────────────────────────────────────────────┐
│  ① WelcomeHeader                                            │
│  └─ 사용자명 / 인사말 / quick action (업로드, 새 폴더)         │
├─────────────────────────────────────────────────────────────┤
│  ② StarredCard (50% width on md+)  │  ③ QuotaCard (50%)    │
│  └─ 즐겨찾기 8개 (file/folder mix) │  └─ 내 storage 사용량 │
├─────────────────────────────────────────────────────────────┤
│  ④ SharedWithMeCard (full width)                            │
│  └─ 공유받은 항목 최근 5개 (file/folder mix)                  │
└─────────────────────────────────────────────────────────────┘
```

레이아웃은 admin overview 의 `admin-grid` flex-col gap 16px max-width 1400px 패턴 reuse. `admin-row admin-row-1-1` (50:50) row 1개 + full-width row 1개.

### 3.1 ① WelcomeHeader

- **표시**: `안녕하세요, {currentUser.name}님` + 부서/팀 메타 1줄 + 우측 quick action 2개 (`업로드` 버튼, `새 폴더` 버튼)
- **데이터**: `useCurrentUser` (기존 hook) + `useWorkspaces`
- **동작**: 업로드 버튼 → 현재 default workspace root 폴더로 파일 picker 오픈. 새 폴더 → default workspace root 에서 폴더 생성 dialog. (둘 다 default workspace 결정 시 부서 → 첫 팀 순서. 0 workspace 면 disabled)
- **backend**: 없음
- **empty state**: workspace 0개 시 quick action disabled + 부서 배정 안내

### 3.2 ② StarredCard

- **표시**: `SectionCard` 헤더 "즐겨찾기" + 우측 "전체 보기 →" (`/favorites`, 병렬 favorites-list 트랙이 신설). favorites-list 트랙 머지 전이면 disabled link.
- **로우 N=8**: 각 row 는 아이콘 + 이름 + 종류 (file/folder) + workspace 경로 1단계 + 마지막 수정일
- **데이터 source**: **외부 dependency** — favorites-list 트랙의 `useMyFavorites({ limit: 8 })` hook reuse. 트랙이 정의하는 응답 형식 ( `FavoriteListItem` in `types/favorite.ts` ) 을 그대로 소비. 본 spec 은 endpoint contract 정의 안 함 (favorites-list 트랙 spec 에 위임).
- **동작**: row 클릭 → 해당 파일/폴더 URL 로 이동 (`api.folderPath(id)` 또는 file 의 경우 `/{workspace}/{folderId}?file={fileId}` query param)
- **empty state**: "아직 즐겨찾기한 항목이 없습니다. 파일 이름 옆 ☆ 아이콘을 눌러 추가하세요." + 1 line 안내
- **트랙 정합성 가드**: favorites-list 트랙 머지 시점에 응답 형식이 dashboard 가 필요한 필드 (`workspace`, `folderPath`, `modifiedAt`) 를 누락한다면 implementation 첫 task 에서 trade-off 결정 (필드 보강 PR 또는 dashboard 측에서 별도 lookup).

### 3.3 ③ QuotaCard

- **표시**: `SectionCard` 헤더 "내 저장공간" + 우측 사용량 mini stat
- **본문**: 진행률 바 (`<progress>` 또는 `admin-progress` reuse) + `8.2 GB / 50 GB` 텍스트 + 잔여 (`{remainGb} GB 남음`) + 80%+ 시 warning tone, 95%+ 시 danger tone
- **데이터 source**: `GET /api/me` 의 기존 `storageUsed` + `storageQuota` (User entity Phase 6 추가됨). 추가 endpoint 불필요 — 기존 `useCurrentUser` 가 캐싱 중일 가능성 확인 후 reuse.
- **empty state**: quota 0 (legacy 사용자) → "할당량 미설정" 회색 표시
- **함께 점검**: `useCurrentUser` 응답에 `storageUsed`/`storageQuota` 필드가 포함되어 있는지 implementation 시 검증. 미포함이면 `GET /api/me/quota` 신규 endpoint 추가 필요 (§4.3 contingency)

### 3.4 ④ SharedWithMeCard

- **표시**: `SectionCard` 헤더 "공유받은 항목" + 우측 "전체 보기 →" (`/shared`, 기존 explorer shared workspace)
- **로우 N=5**: 각 row 는 아이콘 + 이름 + 공유한 사람 (`sharedBy.name`) + 권한 (`READ`/`WRITE` 칩) + 공유받은 시각
- **데이터 source**: `GET /api/me/shared-with-me?limit=5` (신규 — §4.2)
- **동작**: row 클릭 → `/shared/{folderId}?file={fileId}` 로 이동
- **empty state**: "공유받은 항목이 없습니다."

---

## 4. Backend 신규 Endpoint

### 4.1 `GET /api/me/favorites` — **외부 dependency (병렬 favorites-list 트랙)**

본 spec scope 외. 병렬 진행 중인 favorites-list 트랙 (working tree WIP, 2026-05-14) 에서 정의 + 구현. dashboard StarredCard 는 해당 트랙의 hook 결과를 그대로 소비. **본 spec 은 endpoint contract 를 정의하지 않음** — favorites-list 트랙 spec 에 위임.

### 4.2 `GET /api/me/shared-with-me`

- **목적**: 다른 사용자가 나에게 직접 grant 한 권한 list. dashboard SharedWithMeCard + 별도 `/shared/recent` 페이지 (v1.x 후보).
- **인증**: ROLE_USER 이상. grantee_subject_kind=`USER` AND grantee_subject_id=`{me}` 인 permission row 만.
- **query**:
  - `limit` (default 20, max 50).
  - `cursor` (optional, `{grantedAt, permissionId}`).
- **응답 200**:
  ```json
  {
    "items": [
      {
        "permissionId": "uuid",
        "resourceType": "file" | "folder",
        "resourceId": "uuid",
        "name": "string",
        "permission": "READ" | "WRITE" | "OWNER",
        "grantedAt": "2026-05-14T08:00:00Z",
        "grantedBy": { "id": "uuid", "name": "string" },
        "folderPath": [ { "id": "uuid", "name": "string" } ]
      }
    ],
    "nextCursor": "string | null"
  }
  ```
- **권한 검증**: 자기 grant 만 (`grantee_subject_id = currentUserId`). everyone/department/team 같은 indirect grant 는 v1.1 — 본 PR 에선 USER 직접 grant 만.
- **정렬**: `granted_at DESC`.
- **구현 hint**:
  - `PermissionRepository.findGrantsToUserPaged(userId, limit, cursor)` 신규.
  - resource resolve 는 §4.1 과 동일 패턴.

### 4.3 Contingency — `GET /api/me/quota` (조건부)

- `useCurrentUser` 가 `storageUsed/storageQuota` 를 안 가져오면 신규 endpoint. 이미 가져오면 본 endpoint skip.
- response: `{ storageUsed: number, storageQuota: number }` bytes.

---

## 5. Frontend 구조

### 5.1 파일 inventory

| 경로 | 역할 |
|---|---|
| `frontend/src/app/page.tsx` | **수정** — redirect 로직 제거, `<HomeDashboard />` 렌더 |
| `frontend/src/components/home/HomeDashboard.tsx` | **신규** — 4 위젯 컨테이너 (`admin-grid` 패턴 reuse) |
| `frontend/src/components/home/WelcomeHeader.tsx` | **신규** — ① 위젯 |
| `frontend/src/components/home/StarredCard.tsx` | **신규** — ② 위젯 |
| `frontend/src/components/home/QuotaCard.tsx` | **신규** — ③ 위젯 |
| `frontend/src/components/home/SharedWithMeCard.tsx` | **신규** — ④ 위젯 |
| `frontend/src/hooks/useMyFavorites.ts` | **외부 dependency** — favorites-list 트랙이 신설. dashboard 는 import 만. |
| `frontend/src/hooks/useMySharedWithMe.ts` | **신규** — React Query hook |
| `frontend/src/lib/queryKeys.ts` | **수정** — `qk.mySharedWithMe()` 추가. (favorites-list 트랙이 `qk.myFavorites()` 갱신 완료 가정) |
| `frontend/src/lib/api.ts` | **수정** — `api.fetchMySharedWithMe(limit)` 추가. (favorites 측 api 는 favorites-list 트랙이 추가) |
| `frontend/src/types/dashboard.ts` | **신규** — SharedWithMe DTO 타입. (favorites 측 타입은 favorites-list 트랙의 `types/favorite.ts` reuse) |
| `frontend/src/components/admin/SectionCard.tsx` | **이동/공유 검토** — admin 외에도 사용 가능하도록 `components/ui/SectionCard.tsx` 로 이동할지 implementation 시 결정 (KISS — 이동 미루기 가능) |

### 5.2 시각 디자인 (admin overview 패턴 차용)

- **wrapper**: `admin-grid` (flex-col gap 16px, max-width 1400px) — 이미 admin overview / sub-page 에서 표준.
- **row**: `admin-row admin-row-1-1` (50:50) for ②+③, `admin-row admin-row-full` for ④. (`admin-row-full` 클래스 없으면 단순 `<SectionCard>` 만 wrap)
- **카드**: `SectionCard title="…" subtitle="…" right={<Link/>}` — admin overview row 와 동일 시각.
- **타이포**: h1 `text-[20px] font-semibold text-fg mb-1`, subtitle `text-[13px] text-fg-2` — admin page.tsx L42 동형.
- **컬러 토큰**: `--accent` (디자인 tweak), `text-fg`, `text-fg-2`, `text-fg-muted` — 기존 globals.css.

### 5.3 React Query 캐시 정책

- `qk.myFavorites()` — 별 토글 mutation 후 invalidate (이미 `invalidations.afterStarToggle` 에 등록됨).
- `qk.mySharedWithMe()` — permission grant/revoke mutation 후 invalidate (`AdminPermissionsController` mutation hook 의 `onSuccess` 에 추가).
- staleTime: 60s (dashboard 첫 진입 시 신선도 충분).

---

## 6. Empty / Error / Loading State

| 상태 | StarredCard | SharedWithMeCard | QuotaCard | WelcomeHeader |
|---|---|---|---|---|
| Loading | skeleton 3 row | skeleton 3 row | skeleton bar | skeleton text |
| Empty | "아직 즐겨찾기한 항목이 없습니다…" | "공유받은 항목이 없습니다." | `할당량 미설정` (회색) | workspace 0 → 안내 |
| Error | `<EmptyState>` (기존) "불러올 수 없습니다 · 재시도" 버튼 | 동일 | 동일 | 부서 메타 fail 무시, 이름만 표시 |

`<EmptyState>` 는 admin overview 가 사용하는 표준 컴포넌트 reuse 또는 신규 (`components/ui/EmptyState.tsx`) — implementation 시 결정.

---

## 7. 테스트 전략

### 7.1 Backend (junit + Spring slice)

- `MyFavoritesControllerTest` — limit 적용, cursor 페이지네이션, 본인 외 user 의 favorites 미노출, trash 항목 미노출.
- `MySharedWithMeControllerTest` — grantee 본인 row 만, indirect grant (department/team/everyone) 미노출, granted_at DESC.
- `FavoriteRepositoryMyFavoritesTest` — JPA slice, file/folder mix 정렬.
- `PermissionRepositoryFindGrantsToUserTest` — JPA slice.

### 7.2 Frontend (vitest + testing-library)

- `HomeDashboard.test.tsx` — 4 위젯 렌더, workspace 0 시 quick action disabled.
- `StarredCard.test.tsx` — empty/loading/error/8 row, row 클릭 시 router.push 호출.
- `SharedWithMeCard.test.tsx` — 동일.
- `QuotaCard.test.tsx` — 80% / 95% warning tone 전환, 0 quota 시 회색.
- `WelcomeHeader.test.tsx` — workspace 0 zero-state.
- `page.test.tsx` (root `/`) — redirect 제거, `<HomeDashboard />` 마운트 검증, workspace 0 zero-state.

### 7.3 회귀 가드

- 기존 `useWorkspaces` 호출 site (사이드바, breadcrumb 등) 는 그대로 유지 — 본 PR 에서 redirect 만 제거.
- 사용자가 root URL 진입 후 사이드바에서 workspace 선택하는 흐름은 manual smoke (dev preview).

---

## 8. Scope / PR 결정

### 8.1 본 PR 범위

- §4 backend endpoint 2 신규 + 4.3 contingency (필요 시).
- §5 frontend 4 위젯 + page.tsx 수정 + hooks + types + queryKeys.
- §6 empty/error/loading.
- §7 테스트 풀세트.
- §9 문서 갱신.

### 8.2 분리되는 트랙 (out of scope)

- **P2a FE wiring** — ✓ closure PR #241 (2026-05-14). 별 토글 + 4 badge mapper propagate 완료.
- **favorites-list 트랙 (working tree WIP)** — `GET /api/me/favorites` endpoint + `useMyFavorites` hook + `types/favorite.ts` + `(explorer)/favorites/` 페이지 + `SidebarPinnedRow`. **본 dashboard 트랙의 dependency**. 머지 후 dashboard 시작.
- **`/shared/recent` 별도 페이지** — SharedWithMeCard 의 "전체 보기 →" — 현재 `/shared` (기존 explorer shared workspace) 로 연결, 별도 페이지 신설 없음.
- **dashboard 진입 audit emit** — `USER_DASHBOARD_VIEWED` 같은 새 audit event 는 v1.1 (ADR #9 audit 파티션 결정과 함께).

### 8.3 effort 추정 (favorites-list 트랙 머지 가정)

- Backend: 1 endpoint (shared-with-me) + repo 1 메서드 + DTO 1 + test 2 — 약 3 commit
- Frontend: page.tsx + dashboard component 5 + hook 1 (sharedWithMe) + api/queryKeys/types — 약 7 commit
- Docs: 01 §X dashboard section, 00 ADR 신규, 02 §7 endpoint spec 1, v1x-backlog 업데이트 — 약 2 commit

총 **약 12 commit, M scope (favorites-list 의존 후), 단일 PR**.

---

## 9. 영향받는 문서

| 문서 | 변경 |
|---|---|
| `docs/00-overview.md` | ADR 신규 entry — "root `/` 의 redirect → dashboard 변경" |
| `docs/01-frontend-design.md` | §X (신규 섹션) — User Home Dashboard 컴포넌트 / 데이터 흐름 / URL 정합성 |
| `docs/02-backend-data-model.md` | §7 — `/api/me/favorites`, `/api/me/shared-with-me` endpoint spec 2건 |
| `docs/v1x-backlog.md` | Tier 1 closure entry (본 PR 머지 후) |
| `docs/progress.md` | 본 트랙 closure entry |

---

## 10. Open Question / Risk

- **`useCurrentUser` 의 `storageUsed/storageQuota` 노출 여부** — implementation 첫 step 에서 검증. 미노출 시 §4.3 contingency endpoint 추가.
- **`workspace.kind=shared` 인 favorite/shared row** — `/shared` workspace 에 속한 file/folder 도 favorite 가능. URL builder 가 shared workspace 를 지원하는지 (`buildWorkspacePath({kind:'shared', ...})`) implementation 시 검증.
- **대량 favorites 사용자** — 본 PR limit=8 이라 성능 영향 없음. favorites-list 트랙의 cursor 페이지네이션 정책에 의존.
- **favorites-list 응답 형식 mismatch** — favorites-list 트랙 머지 후 `FavoriteListItem` 이 dashboard StarredCard 가 필요한 `workspace` / `folderPath` / `modifiedAt` 필드를 누락하면 implementation 첫 task 에서 trade-off 결정 (필드 보강 별도 PR 또는 dashboard 측 별도 lookup hook).

---

## 11. Reference

- 디자인 mock 부재 — admin overview (`frontend/src/app/admin/page.tsx`) 의 `SectionCard` + `admin-grid` 패턴을 시각 source 로 사용.
- 기존 spec: `2026-05-09-team-centric-pivot-design.md` §5.1 (URL 정합성), admin overview PR #200 (위젯 패턴), P2a backend PR #237 (favorites 테이블 + audit).
- backlog ref: `docs/v1x-backlog.md` L53 (P2a FE wiring, dependency).
