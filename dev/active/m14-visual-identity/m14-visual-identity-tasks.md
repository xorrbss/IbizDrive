---
Last Updated: 2026-04-29
Status: ⏳ in progress
---

# M14 — Tasks

## Phase

| Phase | 상태 | commit |
|---|---|---|
| M14.0 bootstrap | ⏳ in progress | - |
| M14.1 SearchBar Lucide search 아이콘 | ⏳ todo | - |
| M14.2 TopBar Avatar | ⏳ todo | - |
| M14.3 FileRow Lucide 아이콘 | ⏳ todo | - |
| M14.4 StatusBar 하단 | ⏳ todo | - |
| M14.5 closure | ⏳ todo | - |

## DoD

- [ ] SearchBar input prefix Lucide `Search` 아이콘 (16px, fg-muted)
- [ ] TopBar 우측 Avatar (initial circle, 28px, accent bg, "U" 또는 mock initial)
- [ ] FileRow 이모지 → Lucide (`Folder/File/FileText/FileImage/FileSpreadsheet`), size 16, currentColor
- [ ] StatusBar 컴포넌트 — `<footer role="contentinfo">`, 항목 수 + 선택 카운트 (aria-live polite)
- [ ] (explorer)/layout.tsx에 StatusBar 마운트 (main 하단)
- [ ] vitest 신규 GREEN, 회귀 0
- [ ] typecheck + lint clean
