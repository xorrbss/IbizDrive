---
Last Updated: 2026-04-29
Status: ✅ COMPLETED
---

# M8 — Tasks

## Phase

| Phase | 상태 | commit |
|---|---|---|
| M8.0 bootstrap | ✅ done | b608ba0 |
| M8.1 mock api + queryKey | ✅ done | 2bee918 |
| M8.2 usePermission useQuery + 마이그레이션 | ✅ done | e674004 |
| M8.3 ShareDialog + 진입점 | ✅ done | 052a1e6 |
| M8.4 closure | ⏳ in progress | - |

## DoD

- [x] `qk.permissions(nodeId)` / `qk.effectivePermissions()` 신설
- [x] `api.getEffectivePermissions(nodeId?)` mock — admin preset 8 권한 (PURGE 제외)
- [x] `usePermission(nodeId?)` — useQuery 기반, `Record<Permission, boolean>` 반환 (UPPER_SNAKE_CASE)
- [x] BulkActionBar lowercase → UPPER_SNAKE_CASE 필드 마이그레이션
- [x] BulkActionBar SHARE 버튼 (단일 파일 선택 + SHARE 권한)
- [x] ShareDialog — focus trap + 링크 mock + 복사 + Esc/취소
- [x] vitest 신규 20 GREEN, 회귀 0
- [x] typecheck + lint clean

## 검증 결과

- `npx vitest run`: 48 files / 391 tests passed (M8 신규 20: api.permissions 4 + usePermission 4 + shareUi 3 + ShareDialog 6 + BulkActionBar 공유 3).
- `npx tsc --noEmit`: clean.
- `npx eslint .`: clean.

## 비범위 (후속 PR)

- 403 글로벌 핸들러 (toast + qk.permissions invalidate) — 별도 PR (api fetch wrapper 정리 후).
- ShareDialog 만료/권한 옵션 — 백엔드 `POST /api/files/:id/share` endpoint 신설 후.
- 폴더 공유 — file 우선 (v1.x).
- FileRow 우클릭/단축키 진입점 — KISS.
