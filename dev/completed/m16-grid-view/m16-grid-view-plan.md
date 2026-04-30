---
Last Updated: 2026-04-29
Status: ⏳ in progress
Owner: frontend-m4-m10 worktree
---

# M16 — Grid View (Plan)

## 출처
docs/01 §18 row 16 — `FileTable에 grid 모드 추가 (썸네일 카드형). M14의 ViewSwitch에서 토글`

## 범위 (M16.x)

| Phase | 산출물 | 비고 |
|---|---|---|
| M16.0 | dev-docs bootstrap | - |
| M16.1 | **FileCard** — grid 모드 단일 카드 (Lucide 아이콘 + 이름 + 메타) | selection/click/double-click. dnd는 v1.x |
| M16.2 | **FileTable** — `?view=grid` 분기, CSS grid layout (가상화 X 초기) | useViewParam 통합 |
| M16.3 | tests — FileCard + FileTable view 분기 | +5 |
| M16.4 | closure | - |

## 비범위 (v1.x)

- Grid 모드 가상화 (TanStack Virtual grid) — 100+ items 시 성능 이슈 → v1.x
- Grid 모드 키보드 wrap (좌/우 + 상/하 2D 네비게이션) — 화살표는 list 모드와 동일하게 1D index
- Grid 모드 DnD — list 모드 useDraggable 재사용은 가능하나 drop target 시각화는 v1.x
- 썸네일 이미지 (실제 미리보기) — backend thumbnail API 미정 → 아이콘만

## 핵심 결정 사항 (사전)

1. **Grid는 별도 컴포넌트 (FileCard) — FileRow 재사용 X**: gridCols 5-col table layout이 카드 레이아웃과 호환 안됨. KISS.
2. **`useViewParam`으로 분기**: FileTable이 `view` 읽고 list/grid 분기. 헤더(컬럼)는 list 모드만, grid는 헤더 없음.
3. **가상화는 list만**: grid는 직접 렌더 (MVP). 폴더 당 50+ 항목은 드물기에 KISS.
4. **selection/openFile/double-click 동일 동작**: useSelectionStore + useOpenFile 그대로 재사용. 키보드 처리도 1D index 기반 그대로.
5. **카드 디자인**: 정사각형 비율, accent 폴더 색, 이름 truncate (2줄 max), 메타(updatedAt/size)는 작게.

## 구현 순서

- M16.1: `FileCard.tsx` 작성
- M16.2: FileTable에 view 분기 추가 — list 모드는 기존 코드 그대로, grid 모드는 새 div + map
- M16.3: tests
- M16.4: closure
