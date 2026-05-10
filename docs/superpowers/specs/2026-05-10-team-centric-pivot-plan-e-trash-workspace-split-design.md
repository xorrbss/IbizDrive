# Plan E — Trash Workspace Split — Design

Last Updated: 2026-05-10

## 0. 컨텍스트

team-centric-pivot 5개 plan 중 마지막 untouched 트랙. 휴지통을 workspace 단위(부서 / 팀)로 분리한다.

**Spec base** (이미 통합 spec에 정의된 사항을 본 design이 detail expansion):

- `docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md`
  - §2.3 콘텐츠 라이프사이클: "휴지통은 **workspace 단위로 분리**. 부서 휴지통 ≠ 팀 휴지통."
  - §4.5(7) 사이드바 `🗑 휴지통` 단일 진입점 → workspace 선택 탭 페이지
  - §5.1 URL: `/trash/d/:deptSlug` `/trash/t/:teamSlug`

**참조 문서**:
- `docs/02-backend-data-model.md` §6.5 휴지통 이동/복원 트랜잭션 (V10 deleted_by 컬럼 포함)
- `docs/01-frontend-design.md` §13 휴지통 & 되돌리기 (M9 trash-undo 구현 + RESTORE_CONFLICT 다이얼로그)

**의존성 상태**:
- Plan A (V12 scope columns + Team entity) — ✅ MERGED (PR #129)
- Plan B (workspaces.me + 사이드바 3-section + `<TrashLink />`) — ✅ MERGED (PR #139)
- Plan C #140 (share subject_type=team), Plan D #138 (cross-workspace move) — OPEN, 본 plan과 영역 비충돌
- team-archive-write-enforcement (다른 세션 진행 중) — TeamArchiveGuard helper 의존. 미머지 상태이면 본 plan 구현 시 inline 검사 시작 후 helper 머지 시 교체

---

## 1. 목표 / 비목표

### 목표

1. 휴지통을 workspace 단위로 분리. 부서/팀이 각자의 trash view 보유.
2. URL `/trash/d/:deptSlug` `/trash/t/:teamSlug`로 진입 (spec §5.1).
3. 사이드바 단일 `🗑 휴지통` 진입점 → workspace 선택 탭 페이지 (spec §4.5(7)).
4. 권한: workspace 멤버십 기반 일관 정책. archived workspace 콘텐츠는 read-only 유지.
5. 기존 trash listing/RESTORE_CONFLICT 다이얼로그/Undo 토스트 등 UX 컴포넌트는 **재사용** (회귀 위험 최소).

### 비목표

1. **admin 글로벌 trash** (`/admin/trash/*`) — 변경 없음. 기존 workspace 무관 view 유지.
2. **purge 정책** — ADR #32 (DELETE manual purge ADMIN only) 그대로.
3. **workspace archive/purge 시 콘텐츠 휴지통 진입 흐름** (spec §2.3 후반: "workspace purge 시에야 콘텐츠 → 휴지통 → 30일 후 storage purge") — 별도 트랙. 본 plan은 **이미 휴지통에 들어간 row를 어떻게 분리해서 노출하느냐**만 다룬다.
4. **trash retention 정책 변경** — `app.trash.retention-days` (default 30)는 그대로 (spec §6.1 변경 없음).
5. **신규 trash mutation** (e.g., bulk restore, trash 비우기) — v1.x++. 본 plan은 listing/restore/purge 시그니처 보존.

---

## 2. URL & 라우팅

### 2.1 라우트 정의

| URL | 동작 |
|---|---|
| `/trash` | `router.replace(/trash/d/:firstDept)` (default: 사용자 부서). 부서 미배정이면 `/trash/t/:firstTeam`. 둘 다 없으면 EmptyState (workspace 무) |
| `/trash/d/:deptSlug` | 부서 휴지통 페이지 (탭 활성화: 부서) |
| `/trash/t/:teamSlug` | 팀 휴지통 페이지 (탭 활성화: 해당 팀) |

### 2.2 deep link 호환

기존 `<TrashLink />` href는 `/trash`. 본 plan에서 변경 없음. 진입 시 페이지가 `useEffect`로 redirect 결정.

### 2.3 slug → id 매핑

Plan B에서 workspace prefix slug-id 매핑이 이미 결정됨 (spec §5.1: "slug → id 매핑 server lookup. 충돌 시 short-id suffix"). 본 plan은 동일 매핑 재사용. trash 페이지가 slug를 받아 backend `GET /api/workspaces/me`에서 id 조회.

### 2.4 archived team URL

archived team의 `/trash/t/:teamSlug`도 정상 라우트. 페이지가 archived 시 read-only 표시 + restore 버튼 비활성.

---

## 3. Backend API

### 3.1 listing endpoint 변경

```
GET /api/trash?scopeType={department|team}&scopeId={uuid}&cursor=&type={file|folder|all}
```

- `scopeType`, `scopeId` **필수**. 누락 시 `422 VALIDATION_ERROR`.
- `cursor`, `type` 기존 시그니처 유지.
- 응답 row는 trash row 중 `scope_type` / `scope_id`가 query와 일치하는 것만.
- 권한: 호출 사용자가 해당 workspace 멤버 (DEPT department_id 매치 또는 TEAM membership row 존재). 비멤버 → `403 PERMISSION_DENIED`.
- archived workspace: listing **통과** (read-only 의미 보존).

### 3.2 응답 형식 (변경 없음)

기존 `TrashItem` 형식 유지. `scope_type`, `scope_id` 필드는 이미 V12 후 응답에 노출되어있을 가능성 → 구현 시 grep 확인.

### 3.3 권한 검증 위치

`PermissionResolver` membership step (Plan A Task 22~23 `WorkspaceMembershipResolver`)이 trash listing endpoint에서도 호출되도록 wiring. row별 explicit READ N+1 검사는 **수행하지 않음** — KISS, workspace 멤버이면 같은 workspace의 trash 전체 노출.

### 3.4 restore endpoint 변경

`POST /api/files/:id/restore`, `POST /api/folders/:id/restore` — 시그니처 유지. 다음 검증을 진입부에 추가:

1. **archived workspace 차단**: target file/folder의 `scope_type=team` && `team.archived_at IS NOT NULL` → `423 TEAM_ARCHIVED`. team-archive-write-enforcement 트랙의 `TeamArchiveGuard` helper 의존. 미머지 시 inline 검사:
   ```java
   if (target.getScopeType() == ScopeType.TEAM) {
     Team team = teamRepository.findById(target.getScopeId()).orElseThrow();
     if (!team.isActive()) throw new TeamArchivedException(team.getId());
   }
   ```
2. **cross-workspace 원위치 거부**: `original_folder_id`의 폴더 scope ≠ trash row scope이면 `409 RESTORE_CONFLICT` + `body.reason = 'scope_mismatch'`. Plan D 호환 (cross-workspace move가 일어난 상태에서 restore 시도하면 원위치 폴더가 다른 workspace로 이동했을 수 있음).

### 3.5 purge endpoint (변경 없음)

`DELETE /api/trash/:type/:id` — ADR #32 ADMIN only 그대로. 본 plan 변경 없음.

### 3.6 wire 에러 형식

| 시나리오 | status | code | body 추가 |
|---|---|---|---|
| listing scopeType/scopeId 누락 | 422 | VALIDATION_ERROR | `{ field: 'scopeType' \| 'scopeId' }` |
| listing 비멤버 | 403 | PERMISSION_DENIED | — |
| restore archived team | 423 | TEAM_ARCHIVED | `{ teamId }` (team-archive-write-enforcement 정합) |
| restore cross-workspace 원위치 mismatch | 409 | RESTORE_CONFLICT | `{ reason: 'scope_mismatch', expectedScopeType, expectedScopeId, actualScopeType, actualScopeId }` |
| restore 이름 충돌 (기존) | 409 | RESTORE_CONFLICT | `{ reason: 'name_conflict' }` (기존 동작에 reason 추가만) |

`reason` 필드는 신규. errors.ts 코드 추가 0. frontend 다이얼로그가 reason으로 메시지 분기.

---

## 4. Frontend

### 4.1 라우트 파일

- `frontend/src/app/trash/page.tsx` — **변경**: 기존 페이지를 redirect handler로 전환. `useWorkspacesMe` 응답 기반 redirect 결정.
- `frontend/src/app/trash/d/[deptSlug]/page.tsx` — **신규**: 부서 휴지통.
- `frontend/src/app/trash/t/[teamSlug]/page.tsx` — **신규**: 팀 휴지통.

### 4.2 컴포넌트

- **신규**: `frontend/src/components/trash/TrashWorkspaceTabs.tsx`
  - 가로 탭. 부서 1개 + 본인 팀 N개 + archived 팀 N개(`opacity-60` + 🔒 prefix).
  - active 탭은 URL 기반 (`useParams`).
  - 탭 클릭 시 `router.push(/trash/{d|t}/:slug)`.
  - source: `useWorkspacesMe()` (Plan B 재사용).
- **재사용** (변경 없음):
  - 기존 trash listing 행 컴포넌트 (M9 trash-undo).
  - `RestoreConflictDialog` — body의 `reason` 분기 메시지 추가만 (`scope_mismatch` 시 "원위치가 다른 workspace로 이동했습니다" 메시지).
  - softDelete 직후 토스트 + Undo 버튼.
  - `<TrashLink />` (Plan B sidebar) — href `/trash` 그대로.

### 4.3 훅

- **신규**: `frontend/src/hooks/useWorkspaceTrash.ts`
  ```ts
  function useWorkspaceTrash(scopeType: 'department' | 'team', scopeId: string)
  ```
  - `qk.trash(scopeType, scopeId)` 키
  - `enabled: Boolean(scopeId)`
  - cursor 페이징 (기존 `useTrash` 패턴 답습)
- **변경**: 기존 `useTrash` (있다면) — `useWorkspaceTrash`로 교체 또는 wrapper.

### 4.4 TanStack Query 키

`frontend/src/lib/queryKeys.ts`:

```ts
// Before
trash: () => ['trash'] as const,

// After
trash: (scopeType: 'department' | 'team', scopeId: string) =>
  ['trash', scopeType, scopeId] as const,
```

**호출부 마이그레이션**:
- `useTrash` 훅
- softDelete mutation의 `onSuccess` invalidate
- restore mutation의 `onSuccess` invalidate
- Undo 토스트 → restore mutation
- 모든 호출에 현재 페이지의 `(scopeType, scopeId)` 컨텍스트 주입 필요. 페이지에서 props 또는 context로 전달.

### 4.5 EmptyState

| 케이스 | 메시지 |
|---|---|
| 멤버십 0 (`workspaces.me` 빈 응답) | "참여 중인 workspace가 없습니다. 관리자에게 문의하세요." |
| 부서 미배정 (현재 spec 정책) | 기존 "부서 미배정" 메시지 재사용 |
| 해당 workspace 휴지통 빈 | 기존 EmptyState 재사용 ("휴지통이 비어 있습니다") |
| archived 팀 (read-only) | listing 정상 + 페이지 상단 alert: "이 팀은 archive되어 콘텐츠 복원이 불가능합니다." restore 버튼 disabled. |

### 4.6 사이드바 (변경 없음)

`<TrashLink />`가 사이드바 하단 단일 진입점 (Plan B 구현). workspace별 휴지통 노출 X (spec §4.5(7) 단일 진입점 원칙).

---

## 5. 권한 / archived / cross-workspace edge

### 5.1 권한 매트릭스

| 액션 | 권한 | archived workspace |
|---|---|---|
| listing (`GET /api/trash?scopeType&scopeId`) | listing endpoint 진입 시 workspace 멤버십 fast-fail 가드 (DEPT 매치 또는 TEAM membership row 존재). 비멤버 `403 PERMISSION_DENIED`, **ADMIN bypass**. row 별 DELETE 후처리는 ADR #32 그대로 유지 — 사용자 단위 휴지통은 본인이 DELETE 권한 가진 항목만 노출. | 통과 (read-only 의미 보존, frontend alert + restore disabled) |
| restore (`POST /api/{files,folders}/:id/restore`) | Plan A `WorkspaceMembershipResolver` membership step 이 `PermissionResolver` 내부에서 자동 평가 — TEAM MEMBER+ → DELETE 묵시 권한, DEPT 멤버는 explicit grant 만. 추가로 archive guard (`423 TEAM_ARCHIVED`, Plan E T4/T5 — TeamArchiveGuard.assertNotArchived) + cross-workspace 원위치 mismatch (`409 RESTORE_CONFLICT` `reason='scope_mismatch'`). | **차단** (`423 TEAM_ARCHIVED`) |
| purge (`DELETE /api/trash/:type/:id`) | ADMIN only (ADR #32, Plan E 변경 없음) | 차단 (write 이므로) |
| Undo (softDelete 직후 토스트) | restore 와 동일 — `useRestoreItem` 재사용 | 동일 차단 |

> **ADR #32 정합성**: row 별 DELETE 후처리(per-row `hasPermission(id, type, 'DELETE')`)는 본 plan 에서 그대로 유지. workspace 멤버십 가드는 listing endpoint 진입 시 1회 실행되는 fast-fail 추가 layer 일 뿐 — 결과 row 필터링은 기존 ADR #32 매커니즘 그대로 사용.

### 5.2 archived workspace 처리

- **listing 노출**: archived team의 휴지통 row는 listing에 포함. 사용자가 archive 직전 보낸 휴지통 row를 확인할 수 있음.
- **restore 차단**: `TeamArchiveGuard.assertNotArchived(scopeType, scopeId)` 호출. helper 미머지 시 inline 검사 (§3.4 참조).
- **frontend 표시**: archived 팀 탭은 `opacity-60` + 🔒. 페이지 상단 alert + restore 버튼 `disabled` + 호버 툴팁 "archive된 팀의 콘텐츠는 복원할 수 없습니다".

### 5.3 cross-workspace restore (mismatch)

**시나리오**: 사용자 A가 부서1 폴더 X 안의 파일을 휴지통으로 보낸 후, admin이 폴더 X를 부서2로 cross-workspace move (Plan D). 사용자 A가 휴지통에서 restore 시도.

**결정**: 거부.
- backend: `target.scope_type == original_folder.scope_type` && `target.scope_id == original_folder.scope_id` 검증 후 mismatch 시 `409 RESTORE_CONFLICT` + `body.reason = 'scope_mismatch'`.
- frontend: `RestoreConflictDialog`가 reason 분기. `scope_mismatch` 시 "원위치가 다른 workspace로 이동되어 복원할 수 없습니다. 관리자에게 문의하세요." 메시지 + `newName` 입력 비활성 (재시도 불가).

**대안 = 신규 에러 코드**: 기존 RESTORE_CONFLICT 재사용 결정 (위 design 승인). 신규 코드 0 — `errors.ts` 변경 없음.

### 5.4 RESTORE_CONFLICT 이름 충돌 (기존, 변경 없음)

기존 v1.x 동작 그대로. body에 `reason: 'name_conflict'` 추가 — frontend 다이얼로그 분기 가능.

---

## 6. 마이그레이션

### 6.1 기존 `/trash` URL 호환

기존 `<TrashLink />`나 외부 deep link `/trash`로 진입하는 사용자 → 페이지 redirect handler가 `/trash/d/:firstDept`로 자동 이동. 사용자 인지 사이클 0.

### 6.2 backend `GET /api/trash` 호출자 마이그레이션

- Frontend 내부 호출: `qk.trash()` → `qk.trash(scopeType, scopeId)` 시그니처 변경. 모든 호출부 grep 후 일괄 수정.
- 외부 client (admin tool 등): 본 endpoint를 admin tool이 사용하지 않는지 grep 확인. 사용 시 admin endpoint(`/api/admin/trash` 등)로 분리.
- 누락 호출: 422 VALIDATION_ERROR로 빠르게 표면화 (silent failure 회피).

### 6.3 admin global trash 분리

본 plan은 user-facing `/trash`만. admin global trash(`/admin/trash`)는 변경 없음. 만약 admin 페이지가 `GET /api/trash` (workspace 무관)를 호출 중이면, 본 plan 구현 시 admin endpoint로 분리하거나 `?admin=1` 분기 추가 (구현 시 grep 결과로 결정).

---

## 7. 검증

### 7.1 backend

- 신규 단위 테스트:
  - `TrashListingTest`: workspace 멤버 listing 통과 / non-member 403 / scope filter 정확성
  - `TrashRestoreArchivedTest`: archived team scope restore 423 / active team 통과 / department scope 통과
  - `TrashRestoreCrossScopeTest`: original folder scope mismatch 시 409 RESTORE_CONFLICT + reason='scope_mismatch'
- 기존 회귀: `FolderMutationServiceTest` / `FileMutationServiceTest` / 기존 trash test 100% 통과
- 명령: `./gradlew :backend:test`

### 7.2 frontend

- 신규 테스트:
  - `useWorkspaceTrash.test.ts`: queryKey + cursor 페이징
  - `TrashWorkspaceTabs.test.tsx`: workspace.me 응답 → 탭 렌더, archived dim, active 탭 표시
  - `app/trash/page.test.tsx`: redirect 결정 (부서 → 첫 팀 → EmptyState)
  - `RestoreConflictDialog.test.tsx`: reason 분기 메시지 (`scope_mismatch` / `name_conflict`)
- 기존 회귀: M9 trash-undo 컴포넌트 통과
- 명령: `pnpm typecheck && pnpm lint && pnpm test`

### 7.3 E2E (Testcontainers 가용 시)

- workspace 멤버 변경 → trash listing 노출 변화
- team archive → restore 차단
- cross-workspace move 후 restore → mismatch 거부

Plan A 패턴 따라 Testcontainers 미가용 시 SKIP 처리.

---

## 8. 영향받는 문서

- `docs/01-frontend-design.md` §13: workspace 분리 / 탭 UI / URL 갱신
- `docs/02-backend-data-model.md` §6.5 / §7.x trash endpoint: query param 필수화 명시 + RESTORE_CONFLICT body.reason 명시
- `docs/03-security-compliance.md` §3.5 권한 매트릭스: trash listing/restore 행 추가
- `CLAUDE.md` §2 라우팅 표: `/trash/d/*`, `/trash/t/*` 추가
- `frontend/src/lib/queryKeys.ts`: trash 키 시그니처 변경 (계약 파일)
- `frontend/src/lib/errors.ts`: **변경 없음** (RESTORE_CONFLICT 재사용)
- spec `docs/superpowers/specs/2026-05-09-team-centric-pivot-design.md`: §5.1 trash URL 정합 확인 (이미 정합, 변경 없음 예상)

---

## 9. 의존성 / 충돌 회피

| 트랙 | 상태 | 본 plan 영향 |
|---|---|---|
| Plan A (#129) | MERGED | scope columns / WorkspaceMembershipResolver 가용 |
| Plan B (#139) | MERGED | workspaces.me / `<TrashLink />` 가용 |
| Plan C (#140) | OPEN, DIRTY | 영역 비충돌 (share 영역) |
| Plan D (#138) | OPEN, UNSTABLE | 영역 비충돌. cross-workspace move가 머지되면 §5.3 mismatch 시나리오가 실제로 발생 가능 |
| team-archive-write-enforcement | 다른 세션 진행 중 (T1+T2 commit) | TeamArchiveGuard helper 의존. 미머지 시 §3.4 inline 검사 시작, 머지 시 helper로 교체 (작은 follow-up commit) |

**같은 파일 충돌 회피**:
- 본 plan은 `frontend/src/app/trash/*`, `components/trash/*`, `hooks/useWorkspaceTrash.ts`, `lib/queryKeys.ts`만 수정.
- backend는 `TrashController`, `TrashService`, `FolderMutationService.restore`, `FileMutationService.restore`만 수정.
- team-archive-write-enforcement가 `FolderMutationService` / `FileMutationService`의 다른 메서드(create/move/delete/upload)에 가드 추가 — restore에서 부분 충돌 가능. 머지 순서: 본 plan이 늦으면 inline 검사를 helper 호출로 교체. 빠르면 helper 머지 시 가드 호출 추가.

---

## 10. 구현 시 결정 (open)

본 design 단계에선 미확정, 구현(plan) 단계에서 결정:

1. **TeamArchiveGuard helper 사용 vs inline**: 머지 시점 의존. 결정 deadline = restore endpoint 변경 task 진입 시점.
   - **Resolved (T4/T5)**: cherry-pick `637c3dd`으로 TeamArchiveGuard helper 도입, FolderMutationService/FileMutationService 모두 helper 호출 패턴 채택.
2. **admin global trash와 endpoint 분리**: 구현 시 grep 결과로 결정. 분리 필요하면 작은 sub-task 추가.
   - **Resolved (T2)**: AdminTrashController 이미 분리되어 있어 작업 0. TrashController/TrashQueryService만 수정.
3. **`useTrash` 기존 훅 처리**: rename + reuse vs delete + replace. 호출부 grep 후 결정.
   - **Resolved (T7)**: `useTrashList`로 rename + scope 파라미터 확장 패턴 채택. `src/hooks/useTrashList.ts`에 통합. 기존 호출부 전체 migration 완료.
4. **redirect handler의 default workspace 선택 알고리즘**: 부서 → 첫 팀 순. `workspaces.me` 응답이 정렬 보장하는지 확인 필요. 미보장 시 frontend에서 정렬 (createdAt 오름차순 등).
   - **Resolved (T11)**: 부서 우선 → 없으면 첫 팀으로 client-side 정렬 (workspacePath builder 활용). `workspaces.me` 정렬 미보장이므로 frontend filter 채택.

---

## 11. ADR 검토

- **신규 ADR 불필요**: 본 design은 spec §2.3 / §4.5 / §5.1의 detail expansion. 권한 모델은 Plan A의 `WorkspaceMembershipResolver` 재사용. wire format 변경은 RESTORE_CONFLICT body에 `reason` 부가뿐 — minor backward-compatible extension.
- 만약 admin endpoint 분리가 필요하면 ADR-level 결정이 될 수 있음. 구현 시 결정.
