---
task: grant-permission-dialog Phase B
status: completed
created: 2026-05-10
completed: 2026-05-11
spec: docs/01-frontend-design.md §14.5 (Phase A 완료 — 2026-05-09)
branch: feat/grant-permission-dialog-phase-b
worktree: C:/project/IbizDrive/.claude/worktrees/grant-perm-dialog-b
result: 18/18 신규 vitest PASS, 1196/1196 전체 vitest PASS, typecheck/lint exit 0
---

# Plan — GrantPermissionDialog Phase B

## 0. 배경

- Backend `POST /api/{folders|files}/{id}/permissions` (PermissionController#grant)는 완비 (Phase A1.4 grant endpoint, 2026-04~).
- Frontend는 api wrapper + 다이얼로그 + subject picker가 미구현 — 운영자가 SQL 직접 INSERT 또는 ShareDialog 우회 사용.
- 본 트랙은 spec §14.5.9의 **Phase B**: api wrapper + useGrantPermission + GrantPermissionDialog 골격(`subject = everyone` only) + 회귀 가드 vitest.
- Phase C(subject 분기 USER/DEPT/ROLE), Phase D(ResourcePermissionsList 통합)는 별도 트랙.

## 1. Scope

### in-scope (Phase B)
- `api.grantPermission(resource, resourceId, body)` wrapper (`frontend/src/lib/api.ts` 확장)
- `api.grantPermission.test.ts` 회귀 가드 (POST/URL/CSRF/body shape/4xx envelope)
- `useGrantPermission` hook (`frontend/src/hooks/`) + invalidate 3종 (resourcePermissions/adminPermissions/permissions)
- `useGrantPermission.test.tsx` 회귀 가드
- `GrantPermissionDialog` 골격 (`frontend/src/components/files/`) — subject `everyone` 고정, preset/expiresAt 포함, 409 inline alert
- `GrantPermissionDialog.test.tsx` 회귀 가드
- spec `docs/01 §14.5` Status 마크 갱신 (Phase B 완료)

### out-of-scope (다른 phase)
- `ResourcePermissionsList`에 "권한 부여" 버튼 노출 → Phase D
- `usePermission().admin` 가드 → Phase D
- USER/DEPT/ROLE 분기 (UserSearchCombobox/DepartmentSearchCombobox 재사용) → Phase C
- Backend 무수정 (이미 완비)

## 2. 접근

### 2.1 api.ts 패턴 답습
- 인접 mutation(createFolder, adminRevokePermission)은 `readCookie('XSRF-TOKEN')` 직접 호출. spec sample과 동일 → 그대로 따른다.
- 응답 unwrap: backend `ResponseEntity<Map<String, PermissionDto>>` → `{ permission: PermissionDto }`. spec 동형.
- 응답 row 타입: 기존 `PermissionListItem`(types/permission.ts) 재사용 — backend `PermissionDto`와 1:1 미러.

### 2.2 useGrantPermission
- `useMutation` 기반. `mutationFn: (args: { resource, resourceId, body }) => api.grantPermission(...)`.
- `onSuccess`: `qk.resourcePermissions(resource, resourceId)` + `qk.adminPermissions()` + `qk.permissions(resourceId)` 3종 invalidate.
- `onError`: pass-through (다이얼로그가 ApiError를 받아서 status별 분기).
- 테스트: TanStack Query `QueryClient` mock + invalidate spy.

### 2.3 GrantPermissionDialog (골격)
- 상태: `preset` (default `'read'`), `expiresAt` (string, optional), `submitError` (ApiError | null).
- subject은 Phase B에서 `everyone` 고정 — 라디오 미노출, "권한 부여 대상: 전체 사용자(everyone)" 헤더 표시.
- Submit body: `{ subject: { type: 'everyone', id: null }, preset, expiresAt: expiresAt ? new Date(expiresAt).toISOString() : undefined }`.
- 409 PERMISSION_CONFLICT → inline alert "이미 부여된 grant — 기존 row 만료 후 재부여 또는 row 수정".
- 403/404 → toast.error + onClose 호출 (다이얼로그 닫기).
- 400 → field-level error (preset/expiresAt 한정).
- ShareDialog 패턴 답습 (modal frame/footer/Submit/Cancel).

## 3. 인터페이스

### 3.1 api.grantPermission

```ts
async grantPermission(
  resource: 'folder' | 'file',
  resourceId: string,
  body: GrantPermissionRequest,
): Promise<PermissionListItem>
```

### 3.2 wire types (`frontend/src/types/permission.ts`에 추가 — 없으면)

```ts
export interface GrantPermissionRequest {
  subject: { type: 'user' | 'department' | 'role' | 'everyone'; id: string | null }
  preset: Preset
  expiresAt?: string
}
```

### 3.3 useGrantPermission

```ts
export function useGrantPermission(): UseMutationResult<
  PermissionListItem,
  ApiError,
  { resource: 'folder' | 'file'; resourceId: string; body: GrantPermissionRequest }
>
```

### 3.4 GrantPermissionDialog props

```ts
interface GrantPermissionDialogProps {
  resource: 'folder' | 'file'
  resourceId: string
  open: boolean
  onClose: () => void
  onSuccess?: (granted: PermissionListItem) => void
}
```

## 4. 검증 계획

- `pnpm --filter frontend typecheck` — 타입 시그니처 정합.
- `pnpm --filter frontend lint` — eslint 통과.
- `pnpm --filter frontend test` — 신규 테스트 3종 + 기존 회귀 zero.
- 수동 — Phase B는 `ResourcePermissionsList` 미연결이라 visual smoke test 미실시 (Phase D에서 수행).

## 5. 회귀 위험

- `qk.permissions(resourceId)` invalidate가 자기 권한 캐시 외에도 매핑되는지 확인 (queryKeys §6.1, line 24~28).
- `PermissionListItem` 타입이 backend `PermissionDto` shape와 정합한지 — 이미 `M8.1` 시점에 미러됨. 본 트랙 변경 없음.
- ShareDialog와 동시 동작 시 modal stacking 문제 — Phase B에서는 trigger가 없어 N/A. Phase D에서 검증.

## 6. acceptance criteria

- [ ] `api.grantPermission` 호출 → POST `/api/{resource}s/{id}/permissions` + body shape 정확
- [ ] `useGrantPermission` 성공 시 invalidate 3종 호출 검증 (테스트)
- [ ] `GrantPermissionDialog` Phase B 골격 렌더 — preset select 5값 + expiresAt input + submit 시 body shape 정확
- [ ] 409/403/404/400 envelope 매핑 정확
- [ ] vitest 신규 3 테스트 그린 + 기존 회귀 zero
- [ ] docs/01 §14.5 Status 갱신
