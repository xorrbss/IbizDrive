---
task: grant-permission-dialog Phase C 테스트 보강 + Phase D 통합
status: completed
created: 2026-05-11
completed: 2026-05-11
merged: PR #163 (2026-05-11 02:42 KST)
spec: docs/01-frontend-design.md §14.5 (Phase A 2026-05-09, Phase B 2026-05-11, Phase C UI 머지 2026-05-11)
branch: feat/grant-permission-dialog-phase-c
worktree: C:/project/IbizDrive/.claude/worktrees/grant-perm-c (제거됨)
result: vitest 회귀 가드 누적 GrantPermissionDialog 16 + ResourcePermissionsList 10 그린, typecheck/lint exit 0, CI 통과
---

# Plan — GrantPermissionDialog Phase C 테스트 + Phase D 통합

## 0. 배경

PR #157 (`feat/grant-permission-dialog-phase-b`, commit 8b8ddf0)에서 Phase B 골격뿐 아니라 **Phase C subject 분기 UI(everyone/user/department) + Combobox 통합도 함께 머지**됐다. 그러나:

1. **테스트는 Phase B만 커버** — `GrantPermissionDialog.test.tsx`의 describe 블록은 `'Phase B'` 라벨, subject 분기/누락 가드/400/generic fallback 케이스 부재.
2. **Phase D 통합 미수행** — `ResourcePermissionsList`에 "권한 부여" 버튼·dialog state·trigger wire 미추가. Dialog는 호출자 없는 dead code.

본 트랙은 **(1) Phase C 테스트 보강 + (2) Phase D 통합**을 묶어 처리하고 dialog를 사용자 가시 화면으로 끌어올린다.

## 1. Scope

### in-scope
- **Phase C 테스트 보강** (`GrantPermissionDialog.test.tsx`)
  - subject 라디오 3종 분기 (everyone/user/department)
  - subject 변경 시 selectedUser/selectedDept reset
  - user 미선택 + submit → inline error, api 미호출
  - department 미선택 + submit → inline error, api 미호출
  - 400 VALIDATION_ERROR → submitError fallback ('입력값이 올바르지 않습니다')
  - generic error (status code 무관) → submitError fallback ('권한 부여에 실패했습니다')
  - describe 라벨: "Phase B" → "Phase B/C"
- **Phase D 통합** (`ResourcePermissionsList.tsx` + 테스트)
  - admin (PERMISSION_ADMIN) 가드는 이미 적용 (line 30 `if (!isAdmin) return null`) — 그대로 유지.
  - 헤더 우측에 "권한 부여" 버튼 추가 — admin && !isLoading && !isError 일 때만 노출.
  - dialog open state (useState), 버튼 클릭 → setOpen(true).
  - GrantPermissionDialog 마운트 + onSuccess는 hook의 invalidate에 위임 (별도 처리 불필요).
  - 새 테스트 케이스:
    - admin 보유 + data → 버튼 노출 + 클릭 시 다이얼로그 open
    - admin 보유 + empty 상태에서도 버튼 노출 (운영자가 첫 grant 부여 가능)
    - admin 미보유 → 버튼/다이얼로그 모두 미노출 (기존 가드 회귀 가드)
    - error/loading 상태 → 버튼 미노출 (재시도 후 노출)
- **spec 갱신** — `docs/01 §14.5` Status 줄 + §14.5.9 Phase 분할에 Phase C 완료/Phase D 완료 표기.
- **progress.md** — 본 트랙 closure 기록.

### out-of-scope
- ROLE/TEAM subject (v2.x backlog, §14.5.4 callout).
- admin/permissions 페이지 전역 grant (resource picker 부재, v2.x).
- Phase B 골격 dev-docs(`dev/active/grant-permission-dialog-phase-b/`)는 별도 archive PR에 위임 (본 트랙은 잔여 보강 + Phase D만).

## 2. 접근

### 2.1 Phase C 테스트 패턴
- 기존 `renderDialog()` 헬퍼 + `vi.mock('@/lib/api')` 그대로 재사용.
- UserSearchCombobox / DepartmentSearchCombobox는 별도 mock 불필요 — 누락 분기 테스트는 selectedUser/Dept 미설정 상태에서 submit만 트리거하면 inline error 출력 + `api.grantPermission` 미호출 검증.
- subject `'user'` 송신 검증은 selectedUser를 직접 setState 하기 어려움 → 컴포넌트 prop 우회 또는 Combobox mock으로 selectedUser onChange를 강제로 호출하는 패턴. ShareDialog.test.tsx 답습.
- 가장 단순한 접근: `UserSearchCombobox`/`DepartmentSearchCombobox`를 `vi.mock`으로 stub하여 `onChange(mockUser)` 호출을 노출하는 helper 컴포넌트 주입.

### 2.2 Phase D wire
- ResourcePermissionsList 헤더 row를 `<div className="flex items-center justify-between">`으로 wrap → 좌측 h3, 우측 "권한 부여" 버튼.
- 버튼 disabled 조건: `query.isLoading || query.isError` (data 없는 상태에서는 의미 없음, isLoading 동안 클릭 차단 — 부여 후 invalidate가 다시 list 조회).
- `useState<boolean>(false)` for `dialogOpen`. 버튼 onClick → `setDialogOpen(true)`. Dialog의 `onClose` → `setDialogOpen(false)`.
- onSuccess는 useGrantPermission이 이미 invalidate 3종 처리. ResourcePermissionsList는 추가 처리 불필요.
- a11y: 버튼에 `aria-haspopup="dialog"`, `aria-expanded={dialogOpen}` 부착.

### 2.3 `aria-expanded` 정책
- 버튼이 다이얼로그 트리거임을 SR에 알림. ShareDialog 트리거에서 동일 패턴 사용 시 답습, 없으면 KISS로 `aria-haspopup="dialog"`만 부여.

## 3. 인터페이스 (변경 없음)

- `GrantPermissionDialog` props 그대로.
- `ResourcePermissionsList` props 그대로 (resourceType, id).

## 4. 검증 계획

- `pnpm --filter frontend typecheck` exit 0.
- `pnpm --filter frontend lint` exit 0.
- `pnpm --filter frontend test --run GrantPermissionDialog ResourcePermissionsList` — 신규 + 기존 회귀 zero.
- 수동 확인 (선택): dev preview에서 RightPanel 권한 탭 → "권한 부여" 클릭 → 다이얼로그 → submit. 사용자가 직접 spot-check하는 것이 정확. 자율 모드는 typecheck+lint+test 그린에서 PR 올림.

## 5. 회귀 위험

- `usePermission` 비동기 응답 race — admin 가드는 응답 도착 전 false 반환. 버튼은 admin 도착 후 노출. 테스트의 `waitFor` 의존성 유지.
- Dialog가 마운트되면 form 자체가 DOM에 추가됨 → 기존 grid 행 카운트 회귀 없음 (다이얼로그는 fixed inset, grid 외부).
- `qk.permissions(resourceId)` 무효화가 `usePermission` 자체에 invalidate 영향 — 운영자가 자기에게 부여 시 admin 가드 재계산. 의도된 동작.

## 6. acceptance criteria

- [ ] Phase C 테스트 8건 추가 (subject 분기 3 + 누락 가드 2 + reset 1 + 400/generic 2)
- [ ] Phase D ResourcePermissionsList 헤더에 "권한 부여" 버튼 + dialog state + 클릭 시 open
- [ ] 버튼 노출 가드: admin 보유 + !error
- [ ] 신규 ResourcePermissionsList 테스트 4건 (button visible/click open/admin 미보유 회귀/error-loading 미노출)
- [ ] docs/01 §14.5 Status + §14.5.9 갱신 (Phase C 완료, Phase D 완료)
- [ ] vitest 전체 그린 (typecheck/lint/test)

## 7. 트랙 분할 (PR 단위)

본 트랙은 단일 PR로 묶음. 이유:
- Phase C 테스트 + Phase D 통합 모두 동일 dialog/list 영역 (high cohesion).
- Phase D 통합 없이 Phase C 테스트만 머지하면 Dead code 상태 유지 — 사용자 가시 변화 zero.
- 변경 규모: 컴포넌트 1개 + 테스트 2개 + spec 2줄. 단일 PR 적정.
