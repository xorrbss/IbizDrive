---
task: grant-permission-dialog Phase C 테스트 + Phase D 통합
last_updated: 2026-05-11
---

# Context — GrantPermissionDialog Phase C/D

## 핵심 참조 파일

| 파일 | 역할 |
|---|---|
| `docs/01-frontend-design.md` §14.5 (line 1111-1248) | 본 트랙 spec (Phase A-D) |
| `docs/03-security-compliance.md` §3.4.3 | PermissionResolver subject_type 매칭 — ROLE/TEAM 제외 사유 |
| `frontend/src/components/files/GrantPermissionDialog.tsx` | Phase B+C UI 머지된 본체 (commit 8b8ddf0) |
| `frontend/src/components/files/GrantPermissionDialog.test.tsx` | 7개 테스트 (Phase B만 커버) — Phase C 보강 대상 |
| `frontend/src/components/files/ResourcePermissionsList.tsx` | Phase D 통합 대상 (admin 가드 + 4상태 + grid) |
| `frontend/src/components/files/ResourcePermissionsList.test.tsx` | M8.1 테스트 — Phase D 케이스 추가 대상 |
| `frontend/src/hooks/useGrantPermission.ts` | invalidate 3종 (resourcePermissions / adminPermissions / permissions) — 변경 없음 |
| `frontend/src/hooks/usePermission.ts` | `flags.PERMISSION_ADMIN` 반환 |
| `frontend/src/components/shares/UserSearchCombobox.tsx` | `value: UserSummary | null`, `onChange(user | null)` |
| `frontend/src/components/shares/DepartmentSearchCombobox.tsx` | `value: DepartmentSummary | null`, `onChange(dept | null)` |
| `frontend/src/components/shares/ShareDialog.tsx` | 다이얼로그 + Combobox 패턴 답습 source |
| `frontend/src/types/permission.ts` | `GrantPermissionRequest` (subject 3종) + Preset 5종 |
| `frontend/src/test/mocks/sonner.ts` | toast mock 헬퍼 (toastSpy, resetSonnerToastMock) |

## 패턴 결정

### Combobox mock 전략
- 테스트에서 selectedUser/selectedDept를 강제로 변경하려면 두 가지 옵션:
  - **(A) 별도 mock 컴포넌트 주입** — `vi.mock('@/components/shares/UserSearchCombobox', () => ({ UserSearchCombobox: ({ onChange }) => <button data-testid="set-user" onClick={() => onChange(MOCK_USER)} /> }))`
  - **(B) 실제 Combobox 동작** — debounce + minLen 2 + API mock. 복잡, 분기 테스트 의도와 무관.
- → **(A) 채택** — KISS. ShareDialog.test.tsx 동형 패턴 (있다면 일관성, 없다면 본 트랙에서 시작).

### Dialog open state ownership
- ResourcePermissionsList가 owns. 외부에서 enable/disable 불필요(admin 가드는 컴포넌트 내부 처리).
- 향후 PermissionsTab 등에서 별도 진입점 필요해지면 prop으로 hoist — YAGNI.

### "권한 부여" 버튼 위치
- 헤더 row(`<h3>부여된 권한</h3>`) 우측. flex justify-between.
- 4상태 중 어디서 노출할지: data/empty(admin 보유 시 둘 다) — 운영자가 첫 grant 부여 가능.
- loading/error 동안 미노출 — 데이터 미해결 상태에서 부여하면 invalidate 후 화면 깜빡임.

## 위험 / 함정

### subject 변경 시 reset 회귀
- 라디오 onChange에서 setSelectedUser(null) / setSelectedDept(null) 호출함. 변경 시 빈 Combobox 노출 정상.
- 테스트: user 선택 → department 라디오 클릭 → submit 시 selectedUser 영향 zero (department 분기로 진입).

### 누락 가드
- `if (!selectedUser) { setSubmitError('...'); return }` — api 미호출. 테스트에서 `expect(api.grantPermission).not.toHaveBeenCalled()` 검증.
- 동일 패턴: department.

### PERMISSION_CONFLICT inline alert vs submitError
- 409만 inline alert 유지(다이얼로그 닫지 않음). 다른 status는 submitError 또는 toast + onClose.
- 테스트는 onClose 호출 여부로 분기 검증.

### usePermission 비동기 race
- ResourcePermissionsList 초기 마운트 시 admin 미해결 → false → 컴포넌트 미렌더.
- waitFor로 admin 응답 대기 → 다시 렌더 → 버튼 노출.
- 테스트에서 admin 응답 후 버튼 등장을 waitFor로 검증.

## 향후 (트랙 종료 후)

- ROLE/TEAM grant 평가 도입 (v2.x): backend `PermissionRepository.findEffective` 쿼리 확장 + `PermissionResolver` 분기 + `subject_id` UUID-encoded role enum 또는 컬럼 분리.
- admin/permissions 전역 grant: resource picker(folder tree + file search) 컴포넌트 신규 작성.
- Dialog visual polish: focus trap 강화, 키보드 navigation, 모달 stacking ShareDialog와 동시 동작 검증.
