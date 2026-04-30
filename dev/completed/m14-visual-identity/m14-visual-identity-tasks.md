---
Last Updated: 2026-04-29
Status: ✅ done
---

# M14 — Tasks

## Phase

| Phase | 상태 | commit |
|---|---|---|
| M14.0 bootstrap | ✅ done | - |
| M14.1 SearchBar Lucide search 아이콘 | ✅ done | 3963ca2 |
| M14.2 TopBar Avatar | ✅ done | 3963ca2 |
| M14.3 FileRow Lucide 아이콘 | ✅ done | 3963ca2 |
| M14.4 StatusBar 하단 | ✅ done | 3963ca2 |
| M14.5 closure | ✅ done | - |

## DoD

- [x] SearchBar input prefix Lucide `Search` 아이콘 (16px, fg-muted)
- [x] TopBar 우측 Avatar (initial circle, 28px, accent bg, "U" 또는 mock initial)
- [x] FileRow 이모지 → Lucide (`Folder/File/FileText/FileImage/FileSpreadsheet`), size 16, currentColor
- [x] StatusBar 컴포넌트 — `<footer role="contentinfo">`, 항목 수 + 선택 카운트 (aria-live polite)
- [x] (explorer)/layout.tsx에 StatusBar 마운트 (main 하단)
- [x] vitest 신규 GREEN (+6, 397/397), 회귀 0
- [x] typecheck + lint clean
