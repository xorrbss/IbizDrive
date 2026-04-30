---
Last Updated: 2026-04-29
Status: ⏳ in progress
Owner: frontend-m4-m10
---

# M14 — Visual Identity

## 목적

`docs/01 §18 #14`: TopBar(검색/테마 토글/아바타) + Lucide 아이콘 도입 + FileRow 밀도 재조정 + StatusBar 하단. M13 토큰 위에서 JSX 추가.

## 범위

- SearchBar: Lucide `Search` 아이콘 입력 prefix
- TopBar: Avatar (initial circle — 백엔드 user API 없음, "U" 또는 mock 사용자)
- FileRow: 이모지 → Lucide 아이콘 (Folder, File, FileText, FileImage, FileSpreadsheet)
- StatusBar: 하단 푸터 — 현재 폴더 항목 수 + 선택 카운트

## 비범위 (후속)

- 실제 사용자 데이터 (백엔드 `/api/me` 미구현 — M14는 placeholder)
- Avatar 메뉴 (드롭다운 — M15+)
- StatusBar 저장 용량 / 동기화 표시 — M15 StorageBar 트랙
- ViewSwitch / SortChip — M15
- Grid 모드 — M16

## 참조

- docs/01 §18 #14 로드맵 entry
- docs/01 §4 컴포넌트 트리 (StatusBar 위치)
- design-reference/IbizDrive.html (디자인 기준)
- frontend/package.json — `lucide-react` 이미 설치됨
