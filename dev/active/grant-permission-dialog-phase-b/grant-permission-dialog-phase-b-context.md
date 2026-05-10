---
task: grant-permission-dialog Phase B
last_updated: 2026-05-10
---

# Context — GrantPermissionDialog Phase B

## 핵심 참조 파일

| 파일 | 역할 |
|---|---|
| `docs/01-frontend-design.md` §14.5 | 본 트랙의 spec (Phase A 완료 — 2026-05-09) |
| `docs/02-backend-data-model.md` §7.10 | backend grant endpoint spec |
| `frontend/src/lib/api.ts` (1500~1530) | adjacent `adminRevokePermission` 패턴 |
| `frontend/src/lib/api.ts` (452~478) | createFolder POST + readCookie 패턴 |
| `frontend/src/lib/api.adminRevokePermission.test.ts` | 테스트 패턴 (fetchMock + jsonResponse + CSRF cookie) |
| `frontend/src/lib/queryKeys.ts` (22~36, 154~167) | qk.permissions / resourcePermissions / adminPermissions 형태 |
| `frontend/src/types/permission.ts` | Permission/Preset/SubjectType/PermissionListItem |
| `frontend/src/components/shares/ShareDialog.tsx` | Dialog modal 패턴 (Phase B 골격 답습) |
| `frontend/src/components/files/ResourcePermissionsList.tsx` | Phase D 통합 대상 (Phase B에서는 무수정) |
| `backend/src/main/java/com/ibizdrive/permission/PermissionController.java` (75~112) | grant endpoint impl |
| `backend/src/main/java/com/ibizdrive/permission/dto/GrantPermissionRequest.java` | wire shape |

## 패턴 결정

### CSRF
- `readCookie('XSRF-TOKEN')` 직접 호출 — adjacent createFolder 패턴 답습.
- spec sample은 `readCookie('XSRF-TOKEN') ?? ''`이지만 실제 createFolder는 `?` 없이 truthy guard 후 헤더에 포함. 후자 채택.

### 응답 unwrap
- backend: `ResponseEntity<Map<String, PermissionDto>>` body = `{ permission: PermissionDto }`.
- frontend: `(await res.json() as { permission: PermissionListItem }).permission` return.

### subject = everyone
- Phase B에서는 라디오 미노출. dialog 헤더에 "권한 부여 대상: 전체 사용자(everyone)" 텍스트.
- submit body: `{ subject: { type: 'everyone', id: null } }`. backend SubjectRef.id는 nullable UUID이므로 JSON `null` OK.

### preset 5값
- `read | upload | edit | share | admin` (Preset 타입). admin/permissions의 4값(`AdminPreset`)과 다름 — 본 트랙은 5값.

### invalidate 3종
- `qk.resourcePermissions(resource, resourceId)` — RightPanel 권한 탭 갱신
- `qk.adminPermissions()` — /admin/permissions viewer 갱신 (prefix invalidate, 159~167 list 포함)
- `qk.permissions(resourceId)` — useEffectivePermissions 자기 권한 갱신 (운영자가 자기에게 부여 시)

## 위험 / 함정

### CSRF 헤더 누락 회귀
- createFolder 회귀 (사용자가 ADMIN인데 "폴더를 만들 권한이 없습니다") 동형.
- 헤더 누락 시 backend 403 → ApiError → 사용자에게 잘못된 메시지 노출.
- → 회귀 가드 vitest 필수.

### Phase B 골격이 dead code
- ResourcePermissionsList 통합은 Phase D. Phase B에서는 GrantPermissionDialog가 어디서도 호출 안 됨.
- spec §14.5.9가 phase 분할을 명시하므로 의도된 결과. lint/typecheck는 통과해야 함.

### everyone subject_id nullable
- backend GrantPermissionRequest.SubjectRef는 `UUID id` (nullable). JSON `id: null` 송신 시 Spring 역직렬화 OK (Optional UUID).
- 테스트에서 `{ id: null }` 송신 검증.

## 향후 (Phase C/D)

- Phase C: subject 라디오 활성화 + UserSearchCombobox (A14) / DepartmentSearchCombobox (A16) 재사용 + ROLE select.
- Phase D: ResourcePermissionsList에 "권한 부여" 버튼 + `usePermission().admin` 가드 + dialog trigger wire.
