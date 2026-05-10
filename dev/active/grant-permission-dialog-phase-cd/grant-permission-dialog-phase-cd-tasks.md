---
task: grant-permission-dialog Phase C 테스트 + Phase D 통합
last_updated: 2026-05-11
---

# Tasks

## T1. Phase C 테스트 보강 — subject 분기

**파일:** `frontend/src/components/files/GrantPermissionDialog.test.tsx`

**변경:**
- describe 라벨 변경: `'GrantPermissionDialog (Phase B)'` → `'GrantPermissionDialog (Phase B/C)'`.
- 새 describe block `'Phase C — subject 분기'` 추가.
- 테스트 케이스 추가 (8건):
  1. subject = 'user' 라디오 클릭 → UserSearchCombobox 노출 (DepartmentSearchCombobox 미노출).
  2. subject = 'department' 라디오 클릭 → DepartmentSearchCombobox 노출 (UserSearchCombobox 미노출).
  3. user 미선택 + submit → inline error '권한을 부여할 사용자를 선택해 주세요' + api 미호출.
  4. department 미선택 + submit → inline error '권한을 부여할 부서를 선택해 주세요' + api 미호출.
  5. user 선택 → submit body: `{ subject:{ type:'user', id: <user.id> }, preset:'read' }`.
  6. department 선택 → submit body: `{ subject:{ type:'department', id: <dept.id> } }`.
  7. user 선택 → department 라디오 클릭 → submit 시 selectedUser 영향 없이 dept 분기 inline error.
  8. 400 VALIDATION_ERROR → inline error '입력값이 올바르지 않습니다'. + generic error 케이스.

**Mock:**
- `vi.mock('@/components/shares/UserSearchCombobox', ...)` + `vi.mock('@/components/shares/DepartmentSearchCombobox', ...)` — onChange 노출용 stub.

**검증:**
- `pnpm --filter frontend test --run GrantPermissionDialog`

## T2. Phase D 통합 — "권한 부여" 버튼 + dialog state

**파일:** `frontend/src/components/files/ResourcePermissionsList.tsx`

**변경:**
- `import { useState } from 'react'` 추가.
- `import { GrantPermissionDialog } from './GrantPermissionDialog'` 추가.
- Dialog open 상태 useState (`dialogOpen` boolean).
- 헤더 row(`<h3>부여된 권한</h3>`)를 `flex items-center justify-between`으로 wrap.
- 우측 "권한 부여" 버튼 (button) 추가:
  - 노출 조건: `!query.isLoading && !query.isError` (data 또는 empty 상태에서 노출).
  - onClick → `setDialogOpen(true)`.
  - aria: `aria-haspopup="dialog"`, `aria-expanded={dialogOpen}`.
- 컴포넌트 끝에 GrantPermissionDialog 마운트:
  - `<GrantPermissionDialog resource={resourceType} resourceId={id} open={dialogOpen} onClose={() => setDialogOpen(false)} />`
  - onSuccess는 useGrantPermission의 invalidate에 위임 (resourcePermissions 자동 재조회).

**검증:**
- `pnpm --filter frontend typecheck`
- `pnpm --filter frontend test --run ResourcePermissionsList`

## T3. Phase D 테스트

**파일:** `frontend/src/components/files/ResourcePermissionsList.test.tsx`

**변경:**
- 테스트 케이스 추가 (4건):
  1. admin 보유 + data 응답 → "권한 부여" 버튼 노출.
  2. "권한 부여" 클릭 → dialog 표시 (role="dialog" 확인).
  3. admin 미보유 → 버튼 미노출 (기존 회귀 가드 유지) + dialog DOM 부재.
  4. error 상태 → 버튼 미노출.
- `vi.mock('./GrantPermissionDialog')` 으로 dialog는 stub (open prop만 검증). 복잡한 dialog 내부 동작은 GrantPermissionDialog.test.tsx가 책임.

**검증:**
- `pnpm --filter frontend test --run ResourcePermissionsList`

## T4. spec 갱신

**파일:** `docs/01-frontend-design.md`

**변경:**
- §14.5 헤더 라인: `(v1.x — Phase B 완료 2026-05-11)` → `(v1.x — Phase B/C/D 완료 2026-05-11)`.
- §14.5 Status 블록 갱신.
- §14.5.9 Phase 분할 표 갱신:
  - Phase C: 대기 → 완료 (subject 분기 머지일 + 테스트 보강일 함께 명시).
  - Phase D: 대기 → 완료 (ResourcePermissionsList 통합).

## T5. 검증 + 자체 리뷰

**커맨드:**
- `pnpm --filter frontend typecheck`
- `pnpm --filter frontend lint`
- `pnpm --filter frontend test --run`

**자체 리뷰 항목:**
- a11y: aria-haspopup/aria-expanded on button. dialog의 focus trap 회귀 zero.
- 회귀: `pnpm --filter frontend test --run` 전체 그린. 기존 18 (Phase B) + 신규 12 (Phase C 8 + Phase D 4) = 30 GrantPermission 관련.
- KISS 정합: 단일 useState + 단일 dialog 마운트, 추가 추상화 zero.
- spec drift: §14.5 갱신 시 wire 타입(GrantPermissionRequest) 무수정 확인.

## T6. dev-docs-update + progress.md

**파일:**
- `dev/active/grant-permission-dialog-phase-cd/*` → status `completed`로 마크.
- `docs/progress.md` 최상단에 트랙 closure 섹션 추가.

## T7. PR 생성

- `git push -u origin feat/grant-permission-dialog-phase-c`.
- `gh pr create --title "feat(grant-permission-dialog): Phase C 테스트 보강 + Phase D ResourcePermissionsList 통합" ...`
- 본문: 변경 요약, 테스트 plan, 트랙 closure 표.
