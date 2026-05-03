---
Last Updated: 2026-05-02
Status: ✅ T1~T4 구현 완료, G2 (PR) 직전
Owner: frontend
Branch: feature/m8.1-permission-list-frontend
Worktree: .claude/worktrees/m8.1-permission-list-frontend
---

## 구현 결과 요약 (2026-05-02)

- T1 types + api + queryKeys + api test (api.permissions.test.ts +10) — ✅
- T2 useResourcePermissions hook + test (+5) — ✅
- T3 ResourcePermissionsList UI + test (+6) — ✅
- T4 PermissionsTab 통합 + test (+2) + RightPanel.test.tsx 회귀 보강 — ✅
- 검증: typecheck/lint clean, 테스트 80→82 files / 647→670 (+23). 회귀 0건.

### 계획 대비 편차

- **Preset 한국어 라벨** — 플랜 초안은 `'관리자/편집자/뷰어/공유자/업로더'` 였으나, 기존 SharesTable
  답습으로 `'관리/편집/읽기/공유/업로드'` 사용 (UI 일관성 우선).
- 그 외 편차 없음.

# M8.1 — 권한 목록 UI (Resource permission list, frontend)

## 목적

BE `GET /api/{folders|files}/:id/permissions` (PR #44, master e623547) 를 RightPanel
권한 탭에서 소비. 리소스에 부여된 grant 목록(주체/preset/만료)을 PERMISSION_ADMIN 보유자에게
read-only 로 노출. A16 SharesTable 동형 패턴.

## 사용자 브리프 정정

브리프상 "기존 m8-permission-ui (dev/completed/) 는 mock api.getPermissionsForResource 기반 —
이를 실제 endpoint로 교체" 라고 했으나, 실제로는 그러한 mock 함수가 frontend 에 존재하지 않는다.

- 기존 `m8-permission-ui` 트랙은 `usePermission(nodeId)` + `api.getEffectivePermissions` (현재 사용자
  effective 권한 9-flag 조회) 였고, mock 이 아닌 BE A11 endpoint 와 이미 wiring 됨.
- 신규 BE `GET /{folders|files}/:id/permissions` 는 **다른 컨셉** — "이 리소스에 누가 어떤 grant
  를 받았는가" 의 admin 뷰. 기존 PermissionsTab 의 chip UI 는 그대로 유지하고 **그 아래에 새 list
  section** 을 추가하는 형태가 자연스럽다.
- 따라서 M8.1 은 mock 교체가 아니라 **신규 UI 추가** 트랙. (이 결정에 대한 사용자 ack 이
  G1 의 핵심 항목.)

## 범위 (M8.1 in)

### API 계층
- `frontend/src/lib/api.ts` 에 `listResourcePermissions(resourceType, id)` 추가.
  - 시그니처: `(resourceType: 'folder'|'file', id: string) => Promise<PermissionListItem[]>`
  - 엔드포인트: `GET /api/{resourceType}s/{id}/permissions` (path 는 복수형)
  - 응답: `{ items: PermissionDto[] }` → `items` 만 풀어 반환
  - 에러 envelope: 비-OK 응답은 `status` 필드 가진 Error throw (기존 패턴 동일 — QueryCache.onError 401/403 분기)

### 타입
- `frontend/src/types/permission.ts` 확장 — `PermissionListItem` (BE PermissionDto 미러):
  ```ts
  type PermissionListItem = {
    id: string
    resourceType: 'folder' | 'file'
    resourceId: string
    subjectType: 'user' | 'department' | 'everyone'
    subjectId: string | null
    preset: PresetName  // 'admin' | 'editor' | 'viewer' | 'downloader' | 'uploader'
    grantedBy: string
    expiresAt: string | null  // ISO
    createdAt: string  // ISO
    subjectName: string | null
  }
  ```
- 기존 `Permission` enum/`PRESETS` 와 keyspace 분리. preset key 는 BE wire string 그대로 사용.

### Query key
- `frontend/src/lib/queryKeys.ts`:
  ```ts
  resourcePermissions: (resourceType: 'folder'|'file', id: string) =>
    [...qk.all, 'permissions', 'resource', resourceType, id] as const
  ```
  기존 `qk.permissions(nodeId)` (effective) 와 keyspace 분리.

### Hook
- `frontend/src/hooks/useResourcePermissions.ts` (신규):
  - `useQuery({ queryKey: qk.resourcePermissions(...), queryFn: () => api.listResourcePermissions(...), enabled })`
  - `enabled` 는 호출자 측에서 `usePermission(id).PERMISSION_ADMIN` 으로 gating
  - staleTime: 30s (audit 류와 동일)

### UI
- `frontend/src/components/files/PermissionsTab.tsx` 확장 — 기존 chip block 아래에:
  - `<ResourcePermissionsList resourceType="file" id={fileId} />` 추가
  - 컴포넌트는 `useResourcePermissions` 호출, PERMISSION_ADMIN 미보유 시 null 반환 (조건부 렌더)
- `frontend/src/components/files/ResourcePermissionsList.tsx` (신규):
  - A16 `SharesTable` 동형 — `role="grid"` + `aria-rowcount`/`rowindex` (docs/01 §12)
  - 컬럼: 주체(subjectName 우선, fallback subjectId/`전체`) | 권한(preset 한국어) | 부여일 | 만료
  - 4상태(loading/error/empty/data) 패턴 mirror — `SharesTable.tsx` 의 isLoading/isError/empty 분기 동일 형태
  - Revoke/Grant 버튼 미노출 (read-only, 후속 트랙)

### 테스트
- `api.listResourcePermissions` unit (200/401/403/404 envelope) — `api.permissions.test.ts` 에 case 추가
- `useResourcePermissions` unit — `vi.mock(api)` + QueryClientProvider wrapper (기존 hook test 패턴)
- `ResourcePermissionsList.test.tsx` — 4상태 + admin 가드 미통과 시 미렌더
- `PermissionsTab.test.tsx` 에 list 통합 케이스 1건 추가 (chip + list 공존)

## 비범위 (defer)

- **Grant/Revoke UI** — m8 closure 결정상 read-only 우선. POST/DELETE wiring 은 별도 트랙.
- **Folder permissions UI** — 현재 RightPanel 은 file 전용. 폴더 권한 보기 진입점은 후속(폴더
  컨텍스트 메뉴 또는 admin 페이지 트랙).
- **Subject 아바타/부서 아이콘** — KISS, plain text.
- **페이지네이션** — BE 가 단일 리소스 grant 수가 작다는 가정으로 미적용 (docs/02 §7.10). FE 도
  동일 가정.
- **403 글로벌 핸들러** — 기존 m8-permission-ui 비범위와 동일.
- **자체 새로고침 버튼** — focus-refetch + invalidate-on-mutate 면 충분. 후속 grant/revoke 트랙에서
  invalidate 추가.

## 참조

- `docs/02-backend-data-model.md` §7.10 (resource permission list endpoint)
- `docs/01-frontend-design.md` §14 (권한 훅 + 조건부 렌더링), §12 (가상화/aria), §6.1 (queryKeys)
- `docs/03-security-compliance.md` §3 (preset 매트릭스 — preset 한국어 라벨 결정 시 참조)
- `frontend/src/components/shares/SharesTable.tsx` (A16 동형 UI 패턴)
- `backend/.../permission/PermissionController.java` `list()` (응답 shape SoT)
- `backend/.../permission/dto/PermissionDto.java` (wire 미러)
- `dev/completed/m8-permission-ui/` (선행 트랙 — chip UI / usePermission)

## 핵심 원칙 충돌 점검

- 원칙 #3 (URL 소유): N/A — RightPanel 은 query param `?file=` 기반, 본 트랙 변경 없음.
- 원칙 #4 (낙관적 업데이트 비파괴만): N/A — 본 트랙은 read-only.
- 원칙 #5 (가상화 aria): list 가 grid 패턴 사용 시 aria-rowcount/rowindex 필수 — 준수.
- 원칙 #10 (FE 권한은 UX, BE 재검증): admin 가드 조건부 렌더는 UX. BE `@PreAuthorize` 가 진실의 출처 — 403 은
  envelope 그대로 표면화.
- 원칙 #12 (에러코드 계약): 본 트랙은 신규 에러 코드 추가 없음 — BE `PERMISSION_DENIED`/`RESOURCE_NOT_FOUND`
  기존 envelope 사용.
