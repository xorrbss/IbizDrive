# spec-permission-grant-dialog — 단일 자원 권한 grant 다이얼로그 spec

- 시작: 2026-05-09
- v1.x backlog "권한 grant 다이얼로그" 진입 — Phase A spec.
- Backend 완비: `POST /api/{folders|files}/{id}/permissions` (`@PreAuthorize("hasPermission(#id, #resource, 'PERMISSION_ADMIN')")`).
- Frontend 누락: `api.grantPermission` wrapper + `useGrantPermission` hook + `GrantPermissionDialog` 컴포넌트.
- ShareDialog (§14.4) 패턴 답습 — UserSearchCombobox / DepartmentSearchCombobox 재사용.
- 본 트랙 산출: docs/01 §14.5 신설 + docs/04 §15.3 backlink 갱신. 코드 0줄.
- 머지 후 `dev/completed/`로 이동. Phase B/C/D 별도 트랙 진입.
