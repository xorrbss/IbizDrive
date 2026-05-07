---
Last Updated: 2026-05-07
---

# folder-create-ui — Session Context

## SESSION PROGRESS

- 2026-05-07: bootstrap (P0). plan/context/tasks 3파일 작성. 코드 변경 없음.

## Current Execution Contract

- **자율 실행 모드**: phase별 RED→GREEN→commit. phase 완료 시 보고.
- **PR 단위**: 본 트랙 1개 PR. P1~P4 누적 후 최종 push + PR open.
- **테스트 게이트**: phase별 `pnpm typecheck && pnpm lint && pnpm test` 통과 필수.
- **충돌 시 우선순위**: §3 원칙 11개 → CLAUDE.md 운영 규칙 → 본 plan.

## 현재 active phase / task

- **active phase**: P0 (bootstrap) — **완료 직전**, commit만 남음
- **next**: P1 (invalidation 헬퍼 + `useCreateFolder` hook RED→GREEN)

## 다음 세션 읽기 순서

1. `dev/active/folder-create-ui/folder-create-ui-plan.md` — 범위 / phase 지도 / acceptance
2. `dev/active/folder-create-ui/folder-create-ui-tasks.md` — 체크박스 진행 상태
3. `frontend/src/lib/api.ts:435` — `createFolder` 시그니처
4. `frontend/src/lib/queryKeys.ts:182~` — `invalidations` 헬퍼 패턴 (afterRename / afterRestore 답습)
5. `frontend/src/hooks/useRestoreItem.ts` — mutation hook 답습 모델
6. `frontend/src/components/upload/FolderToolbar.tsx` — 진입 버튼 둘 위치
7. `frontend/src/lib/normalize.ts` — 클라이언트 validation 함수

## 핵심 파일과 역할

| 파일 | 역할 | 본 트랙 변경 |
|---|---|---|
| `lib/api.ts` | `createFolder(parentId, name)` | 변경 없음 |
| `lib/queryKeys.ts` | `qk.*` + `invalidations.*` | `invalidations.afterFolderCreated` 추가 |
| `lib/normalize.ts` | NFC + validation | 변경 없음 (호출만) |
| `hooks/useCreateFolder.ts` | TanStack mutation | **신규** |
| `components/explorer/CreateFolderDialog.tsx` | 입력 + validation + 제출 | **신규** |
| `components/upload/FolderToolbar.tsx` | 진입 버튼 | "새 폴더" 버튼 추가 |
| `hooks/useCurrentFolder.ts` | URL → folderId | 변경 없음 (호출만) |

## 중요한 의사결정

1. **frontend permission gating 없음** — backend 403/409 envelope를 다이얼로그 인라인 surface (KISS, `docs/01 §14` "프론트 권한은 UX hint, 보안 아님"과 정합).
2. **낙관적 업데이트 안 함** — 본 트랙은 새 항목 추가이지만 §3 원칙 3 (파괴적 액션 낙관 X)와는 무관. 단순화를 위해 mutation pending 상태 사용.
3. **다이얼로그 mount 위치** — `FolderToolbar` 내부에 `useState`로 보유. 전역 store 도입 안 함 (KISS).
4. **무효화 키 3개** — `qk.filesListPrefix(parentId)` + `qk.folderTree()` + `qk.folder(parentId)`. `afterRename` 폴더 케이스와 동일 매트릭스.
5. **자동 네비게이션 안 함** — 생성 후 다이얼로그만 닫고 목록은 invalidate. 사용자가 클릭으로 진입 (KISS).

## 빠른 재개 안내

```text
1. dev/active/folder-create-ui/folder-create-ui-tasks.md 열기
2. 첫 미완료 task의 "원본 코드 참조" → "구현 대상" → "검증 참조" 순으로 진행
3. RED 테스트 작성 → 실패 확인 → GREEN 구현 → 통과 → commit
4. phase 끝나면 plan §"Phase 지도" 표 갱신 + context.md SESSION PROGRESS 추가
```
