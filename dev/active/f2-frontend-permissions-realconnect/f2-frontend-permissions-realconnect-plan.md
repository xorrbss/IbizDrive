---
Last Updated: 2026-05-01
---

# F2 — Frontend `usePermission` 실연결 plan

## 요약

Frontend `api.getEffectivePermissions(nodeId?)` 본체를 mock(80ms 지연 + admin preset 8권한 하드코딩)에서 `fetch('/api/me/effective-permissions?nodeId=...')` 직접 호출로 교체한다. 백엔드 endpoint는 A11(`13b8c45`, PR #23)에서 이미 `GET /api/me/effective-permissions` 신설 완료(응답 `{ permissions: Permission[] }`, role∪resource grant 합산, ADMIN early return, PURGE 제외). 호출부(`usePermission.ts` / `BulkActionBar.tsx` / 권한 가드 UI) drift 0 — 시그니처 `(nodeId?: string) => Promise<Permission[]>` 무수정.

## 현재 상태 분석

- **Backend (master `097e904`)**: `GET /api/me/effective-permissions?nodeId={UUID}` 활성. 응답 `{ permissions: Permission[] }` (Permission enum natural order 정렬). 401(미인증)/400(UUID 형식 오류)/404(node 부재) envelope.
- **Frontend mock 위치**: `frontend/src/lib/api.ts:500-505` — `void nodeId` + 80ms setTimeout + admin preset 8권한 하드코딩. `nodeId` 인자는 무시.
- **호출부**: `frontend/src/hooks/usePermission.ts:28` (`api.getEffectivePermissions(nodeId)` 단일 호출, 60s staleTime, `qk.permissions(nodeId)` 키).
- **테스트**: `frontend/src/lib/api.permissions.test.ts` (mock 한정 2 케이스), `frontend/src/hooks/usePermission.test.tsx`(integration), `frontend/src/components/files/BulkActionBar.test.tsx`(consumer).

## 목표 상태

- `api.getEffectivePermissions(nodeId?)` 본체 = `fetch` 호출 + `response.ok` 분기 + `await response.json()` → `data.permissions` 반환.
- mock 한정 테스트 → fetch wire 테스트로 전면 갱신 (F1 패턴 `vi.stubGlobal('fetch', ...)` 미러).
- 호출부 무수정. `usePermission.test.tsx` integration의 fetch stub만 재정합.
- 회귀 0 — `pnpm test` / `pnpm typecheck` / `pnpm lint` 전부 GREEN.

## phase별 실행 지도

### F2.0 — dev-docs bootstrap (현재 phase)

본 plan/context/tasks 3파일 생성. 사용자 승인 후 F2.1 진입.

### F2.1 — api.getEffectivePermissions fetch swap + 테스트 갱신

**구현 (TDD)**:
1. `api.permissions.test.ts` 전면 재작성 — fetch wire 계약 (URL+query, response shape, 401/403/404/AbortSignal) RED.
2. `api.ts` mock body 교체 — `URLSearchParams` + `fetch` + `response.json()` GREEN.
3. `usePermission.test.tsx` integration의 mock api → fetch stub 재정합 (필요 시).
4. `pnpm test` 전체 GREEN 확인.

**테스트 케이스 (예상 6~8건)**:
- nodeId 미지정 → URL `/api/me/effective-permissions` (no query).
- nodeId 지정 → URL `/api/me/effective-permissions?nodeId={uuid}`.
- 응답 `{ permissions: ['READ', ...] }` → 그대로 반환.
- 응답 `{ permissions: [] }` → 빈 배열 반환.
- HTTP 401 → throw (글로벌 onError handler가 처리).
- HTTP 404 → throw.
- HTTP 5xx → throw.
- AbortSignal 전파 (선택 — 호출부에서 signal 미전달이지만 hook level에서 전달 가능성 대비).

**범위 제외**:
- `usePermission` 시그니처 변경(`Promise<Permission[]>` → 다른 형) 금지.
- `PermissionFlags` (Record<Permission, boolean>) 형 변환 로직 변경 금지 — 그대로 유지.
- 새 query key 추가 금지.
- API 에러 envelope 처리 변경 금지 — 글로벌 `QueryCache.onError`에 위임.

### F2.2 — PR + 마일스톤 종료

PR `feat(F2.1): usePermission real /api/me/effective-permissions 연결` → CI green → squash-merge → archive `dev/active → dev/completed` → `docs/progress.md` F2 closure entry → closure 커밋.

## acceptance criteria

1. `api.getEffectivePermissions` 본체가 `fetch('/api/me/effective-permissions...')` 호출이며 mock setTimeout 제거.
2. 시그니처 `(nodeId?: string) => Promise<Permission[]>` 무수정.
3. `usePermission.ts` 본체 무수정 (queryFn 내부 호출 그대로).
4. `api.permissions.test.ts`가 fetch mock 패턴으로 재작성, ≥6 케이스 GREEN.
5. `usePermission.test.tsx` integration GREEN.
6. `BulkActionBar.test.tsx` 등 consumer 회귀 0.
7. `pnpm test` 전체 GREEN, `pnpm typecheck` clean, `pnpm lint` clean.
8. `qk.permissions(nodeId)` 키 무수정 (캐시 격리 정책 보존).
9. 응답 매핑 inline (KISS) — 별도 mapper 파일 신설 금지.

## 검증 게이트

- F2.1 종료 시: `pnpm --dir frontend test`, `pnpm --dir frontend typecheck`, `pnpm --dir frontend lint` 전부 GREEN.
- F2.2 종료 시: PR CI 양 잡(backend junit + frontend vitest) SUCCESS, master squash-merge 완료, dev-docs archive 완료.

## 리스크와 완화

1. **응답 shape mismatch** — backend가 `{ permissions: ... }` 래핑 사용. 매핑 inline `data.permissions`만 접근. ADR 신설 불필요.
2. **AbortSignal 누락** — `useQuery`의 queryFn은 ctx.signal 전달 가능. 현재 호출부는 미전달이지만 수정 시 hook 레벨에서만 추가하고 본 트랙 범위 외.
3. **401 무한 루프** — 401 handling은 글로벌 `QueryCache.onError`(이미 존재) 책임. 본 fetch는 단순 throw.
4. **PURGE 누락 가능성** — backend `resolveAll`이 PURGE를 명시 skip. mock과 동일하게 PURGE 미포함. 회귀 없음.
5. **nodeId UUID 형식** — frontend는 hook 호출자(`folder_xxx` 같은 mock id 가능성)와 backend(strict UUID)의 형식 차이. M8 권한 UI 코드의 nodeId 출처 확인 필요. 만약 mock id 시점이면 ADR 신설 후 normalize 처리 — 그 경우 F2.1 차단 후 보고.
