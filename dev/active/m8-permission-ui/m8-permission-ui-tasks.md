---
Last Updated: 2026-04-29
Status: ⏳ in progress
---

# M8 — Tasks

## Phase

| Phase | 상태 | commit |
|---|---|---|
| M8.0 bootstrap | ⏳ in progress | - |
| M8.1 mock api + queryKey | ⏳ todo | - |
| M8.2 usePermission useQuery + 마이그레이션 | ⏳ todo | - |
| M8.3 ShareDialog + 진입점 | ⏳ todo | - |
| M8.4 closure | ⏳ todo | - |

## DoD

- [ ] `qk.permissions(nodeId)` / `qk.effectivePermissions()` 신설
- [ ] `api.getEffectivePermissions(nodeId?)` mock — admin preset 9 권한 (PURGE 제외)
- [ ] `usePermission(nodeId?)` — useQuery 기반, `Record<Permission, boolean>` 반환 (UPPER_SNAKE_CASE)
- [ ] BulkActionBar lowercase → UPPER_SNAKE_CASE 필드 마이그레이션
- [ ] BulkActionBar SHARE 버튼 (단일 파일 선택 + SHARE 권한)
- [ ] ShareDialog — focus trap + 링크 mock + 복사 + Esc/취소
- [ ] vitest 신규 GREEN, 회귀 0
- [ ] typecheck + lint clean
