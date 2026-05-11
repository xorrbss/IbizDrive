---
task: 단축키 cheat sheet `?` 모달
last_updated: 2026-05-11
---

# Context

## 핵심 참조

| 파일 | 역할 |
|---|---|
| `docs/01 §12.1` 키맵 | 단축키 정의 source of truth |
| `frontend/src/hooks/useGlobalShortcuts.ts` | `/`, `⌘K`, `Ctrl+K` 분기 — `?` 추가 위치 |
| `frontend/src/components/shares/ShareDialog.tsx` | 모달 패턴 (focus trap, ESC, role=dialog) |
| `frontend/src/components/files/GrantPermissionDialog.tsx` | 자기 visibility + ESC 패턴 답습 |
| `frontend/src/app/(explorer)/layout.tsx` | 마운트 지점 |

## 패턴 결정

- **CustomEvent dispatch + 수신** — `useGlobalShortcuts`에서 `app:open-shortcuts` dispatch, `ShortcutsCheatSheet`이 mount 시 listener. `FOCUS_SEARCH_EVENT` 패턴 답습.
- **단축키 데이터는 컴포넌트 내부** — 통합 source는 추후 별도 PR. YAGNI.
- **모달은 self-contained** — `useState owns`, props 없음. 외부 store 불필요.

## 위험 / 함정

- `?` 키는 `Shift+/`로 입력. `e.key === '?'` 직접 비교가 더 명확 — `Shift` modifier 자체 검사 불필요.
- 다이얼로그 z-index 충돌 — `z-[60]` 등으로 다른 다이얼로그 위에. 본 PR은 keep simple.
