---
Last Updated: 2026-04-29
Status: ✅ done
---

# M16 — Tasks

| Phase | 상태 | commit |
|---|---|---|
| M16.0 bootstrap | ✅ done | - |
| M16.1 FileCard | ✅ done | 3fda60a |
| M16.2 FileTable view 분기 | ✅ done | 3fda60a |
| M16.3 tests | ✅ done | 3fda60a |
| M16.4 closure | ✅ done | - |

## DoD

- [x] FileCard — Lucide 아이콘 + 이름 + 메타. selection ring + onClick/onDoubleClick
- [x] FileTable — `useViewParam` view='grid'일 때 grid layout 분기, list는 기존 그대로 (회귀 보호)
- [x] Grid 모드: 헤더 없음, 가상화 없음 (MVP), 키보드 ArrowUp/Down은 list와 동일하게 1D index
- [x] vitest 신규 7 GREEN (FileCard 5 + FileTable 2), 회귀 0 — **55 files / 415 tests**
- [x] typecheck + lint clean
