---
Last Updated: 2026-04-29
Status: ✅ done
---

# M15 — Tasks

## Phase

| Phase | 상태 | commit |
|---|---|---|
| M15.0 bootstrap | ✅ done | - |
| M15.1 SortChip + URL ?sort&?dir | ✅ done | d3ca081 |
| M15.2 ViewSwitch + URL ?view | ✅ done | d3ca081 |
| M15.3 StorageBar + useStorageQuota | ✅ done | 9790ce6 |
| M15.4 RightPanel 4-tab | ✅ done | 4c7fc44 |
| M15.5 closure | ✅ done | - |

## DoD

- [x] SortChip — name/updatedAt/size × asc/desc, URL `?sort=&dir=` 양방향, `aria-haspopup` + `menuitemradio`
- [x] ViewSwitch — List/Grid 토글 (Grid 활성 시 `?view=grid`, default 없음), `aria-pressed`
- [x] StorageBar — Sidebar 하단, used/total + 진행 바 (75% mock), `aria-label` 사용량 + `role=progressbar`
- [x] RightPanel — 세부정보(기존)/버전/활동/권한 4탭, `role=tablist` + `aria-selected`, 비활성 탭 placeholder
- [x] vitest 신규 11 GREEN (3+3+3+2), 회귀 0 — **53 files / 408 tests**
- [x] typecheck + lint clean
