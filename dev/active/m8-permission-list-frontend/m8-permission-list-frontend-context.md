---
Last Updated: 2026-05-02
---

# M8.1 — Context

## 현재 상태

### Backend (이미 머지됨, master e623547 / PR #44)
- `GET /api/{folders|files}/:id/permissions` — `@PreAuthorize("hasPermission(#id, #resource, 'PERMISSION_ADMIN')")`
- 응답: `{ items: PermissionDto[] }` (페이지네이션 없음)
- DTO 필드: `id, resourceType, subjectType, subjectId, preset, grantedBy, expiresAt, createdAt, subjectName`
- `subjectName` 은 BE 가 user/department batch resolve (A16 ShareDto 동형). soft-delete/everyone → null
- 정렬: `created_at ASC, id ASC`
- 에러: 404 (리소스 없음), 403 (PERMISSION_ADMIN 미보유), 401 (미인증)

### Frontend 현재
- `frontend/src/types/permission.ts` — `Permission` enum + `PRESETS` (UPPER_SNAKE_CASE)
- `frontend/src/lib/api.ts` — `getEffectivePermissions(nodeId)` (A11 wiring 완료, my flags 9개)
- `frontend/src/hooks/usePermission.ts` — useQuery 기반, `Record<Permission, boolean>` 반환
- `frontend/src/lib/queryKeys.ts` — `qk.permissions(nodeId)` / `qk.effectivePermissions()` 존재 (effective 용)
- `frontend/src/components/files/PermissionsTab.tsx` — 9-chip 표시, read-only
- `frontend/src/components/files/RightPanel.tsx` — `tab === 'permissions'` 일 때만 PermissionsTab mount

### A16 동형 참조
- `frontend/src/components/shares/SharesTable.tsx` — `role="grid"` + `aria-rowcount` + 4상태 + grid-cols
- `useSharesWithMe` — `useInfiniteQuery` 사용 (M8.1 은 `useQuery` 단순)

## 미러링 영향

- `PermissionsTab.tsx` 변경 — 기존 chip ul 아래 새 section 추가 (mount 조건은 그대로)
- `PermissionsTab.test.tsx` — 기존 9개 chip 테스트는 유지, list 통합 케이스 1건 추가
- 다른 컴포넌트 영향 0 — 회귀 위험 낮음

## 결정 (G1 ack 대기)

1. **신규 트랙 (mock 교체 아님)** — 사용자 브리프와 다름. ack 필요.
2. **PermissionsTab 확장 vs 별도 탭** — 기존 chip 과 같은 탭에 list 추가 (KISS, 탭 늘리지 않음).
3. **read-only 만 wiring** — Grant/Revoke 버튼은 후속 트랙.
4. **file 만 커버, folder 보류** — 현재 folder RightPanel UI 미존재.
5. **새 type 파일 vs 기존 확장** — 기존 `permission.ts` 에 `PermissionListItem` 추가 (KISS, 새 파일 안 만듦).
6. **preset 한국어 라벨** — `'admin'→'관리자'`, `'editor'→'편집자'`, `'viewer'→'뷰어'`, `'downloader'→'다운로더'`, `'uploader'→'업로더'` (docs/03 §3 참조 후 확정).
7. **subject 표시** — `subjectName ?? (subjectType === 'everyone' ? '전체' : subjectId)`. raw UUID fallback 은 A16 패턴.
8. **404/403 표면화** — `SharesTable` 와 동일하게 isError 분기 단일 메시지 ("권한 목록을 불러올 수 없습니다"). 사용자가 PERMISSION_ADMIN 미보유면 컴포넌트 자체를 미렌더 → 403 도달 자체가 거의 없음.
