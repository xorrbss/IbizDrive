---
Last Updated: 2026-04-29
---

# M14 — Context

## 현재 상태

- TopBar(`src/components/topbar/TopBar.tsx`): SearchBar + ThemeToggle (이미 Lucide 사용)
- ThemeToggle: 이미 Lucide `Moon/Sun` 사용 중 — 추가 작업 없음
- SearchBar: 텍스트 placeholder "파일 검색 ( / )"만, 시각적 search affordance 없음
- FileRow: 이모지 (📁🖼️📄📊📝📎) — design 기준에 미달
- StatusBar: 미존재 (docs/01 §4 트리에는 있지만 컴포넌트 없음)
- `lucide-react ^1.11.0` 설치됨

## 결정

- SearchBar: input의 left padding 추가 + absolute positioned `Search` 아이콘
- Avatar: 백엔드 `/api/me` 미구현 — placeholder "U" 28px circle. 향후 useMe() 훅 신설 시 교체
- FileRow: 1:1 mapping (folder→Folder, image/*→FileImage, pdf/word→FileText, spreadsheet→FileSpreadsheet, default→File). 16px size. currentColor (text-fg-muted folder는 text-accent)
- StatusBar: useFilesInFolder + useSelectionStore. 폴더가 비어 있어도 표시. SSE 등 부가 정보는 v1.x

## 영향 범위

- SearchBar.test.tsx: search 아이콘 검증 추가 (선택 — Lucide는 svg 노드, role 없음 → aria-hidden 정상)
- FileRow.test (있다면) — 이모지 텍스트 검증이 있다면 svg로 마이그레이션
- StatusBar 신규 + test 신규
- (explorer)/layout.tsx — main 하단에 StatusBar 마운트
