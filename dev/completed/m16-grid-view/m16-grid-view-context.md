---
Last Updated: 2026-04-29
---

# M16 — Context

## 관련 파일

| 파일 | 역할 | 변경 |
|---|---|---|
| `frontend/src/components/files/FileCard.tsx` | (신규) Grid 모드 카드 | 신규 |
| `frontend/src/components/files/FileCard.test.tsx` | (신규) 테스트 | 신규 |
| `frontend/src/components/files/FileTable.tsx` | grid/list 분기 | view 읽고 분기 |
| `frontend/src/hooks/useViewParam.ts` | URL ?view 동기화 | (M15.2 신규, 변경 없음) |

## 기존 패턴 재사용

- `fileIconFor` (M14.3): FileRow에서 만든 mime → Lucide 매핑 — FileCard에서도 동일하게 사용. 중복 방지를 위해 export 또는 분리 검토.
- `useSelectionStore` toggle/selectOnly/selectRange — 동일하게 사용
- `useOpenFile.open` — 더블클릭 시 동일 처리 (폴더는 router.push, 파일은 ?file=)

## 위험 요소

- **FileTable이 list 분기에서 사용하는 useVirtualizer + scrollRef**: grid 분기에선 사용 X. 동일 hook은 호출 순서 안정화 필요 — 항상 호출하되 grid 모드일 땐 결과 무시 (안전).
- **키보드 핸들러**: list 모드 ArrowUp/Down은 grid에선 의미 다름. M16 시점엔 grid 모드에서 화살표 무시 (마우스 only) — DoD에 명시.
- **FileTable.test.tsx**: grid view에 대한 새 테스트 필요. 기존 테스트는 view='list' 유지 (mock useViewParam).

## 테스트 계획

| 컴포넌트 | 케이스 | 수 |
|---|---|---|
| FileCard | (1) 이름/아이콘 표시, (2) 클릭 시 onClick 호출, (3) 더블클릭 시 onDoubleClick 호출, (4) selected 시 ring 클래스 | 4 |
| FileTable view 분기 | (1) view='grid'일 때 role=grid 헤더 없음 + FileCard 렌더, (2) view='list'(default)일 때 기존 헤더 + FileRow 유지 | 2 |

총 +6 테스트.
