# Context — RightPanel Frontend Wire (P_panel-B)

Last Updated: 2026-05-13

## SESSION PROGRESS

- 2026-05-13 — bootstrap + P1~P4 + P6 완료. master @ 6f4c766(PR #218 merged) 에서 `feat/rightpanel-frontend-wire` 분기. 단일 세션 완료, PR 생성 대기.
- P1 kindLabel (fileIcon.ts) ✓
- P2 detail 탭 7 row wire (PanelBody / DetailRow / DetailUser / DetailSharedStack / MiniAvatar) ✓
- P3 헤더 + 액션 toolbar (다운로드 / 공유 / 더보기 disabled) ✓
- P4 PreviewCard placeholder (kind별 soft 배경) ✓
- P5 버전 라벨 count — deferred (versionsCount 미노출, 본 트랙 미진행)
- P6 docs/v1x-backlog.md + docs/progress.md 갱신 ✓

검증: typecheck ✓ / lint ✓ / RightPanel.test 21/21 PASS.

## Current Execution Contract

- 본 트랙은 **frontend only**. backend / DB 변경 0. PR #218 이 owner/sharedWith/folderPath 를 이미 wire 했다.
- detail 탭 row 풀세트 중 **viewCount는 분리** (ADR #9 FILE_VIEWED audit blocker). 다른 7 row만 wire.
- 더보기 메뉴는 **disabled placeholder** (MVP). 디자인엔 dots 아이콘만 있고 동작 미정.
- 공유 버튼은 **권한 탭 전환** (디자인의 `onOpenPermissions` 콜백 의미).
- mini-avatar는 RightPanel 인라인 (admin.css `.p-avatar` 는 admin layout 안에서만 사용 가능 — RightPanel은 (explorer) 레이아웃).
- `<dl>` 구조 → `<div>` row 구조로 마이그레이션 (디자인 detail-row 형식 정합 + AvatarStack 등 복합 value 표현 용이).
- 색상 hash 로직은 `admin/teams/Avatars.tsx` PAvatar 와 동일 8색 팔레트 + 동일 hash. lib 추출 보류(YAGNI, 3번째 소비자 등장 시).

## 현재 active task

- 완료. PR 생성 + 머지 후 active → completed archive + backlog PR #TBD → 실제 PR 번호 followup.

## 다음 세션 읽기 순서

1. `dev/active/rightpanel-frontend-wire/rightpanel-frontend-wire-plan.md` (phase map + acceptance).
2. `dev/active/rightpanel-frontend-wire/rightpanel-frontend-wire-tasks.md` (실행 단위).
3. `design-reference/panels.jsx` L8~184 (RightPanel 원본 + DetailRow + PreviewCard + kindLabel).
4. `frontend/src/components/files/RightPanel.tsx` (현재 상태).
5. `frontend/src/lib/fileIcon.ts` + `frontend/src/components/icons/FileTypeIcon.tsx` (재사용 helper).
6. `frontend/src/components/admin/teams/Avatars.tsx` (PAvatar / PAvatarStack — 인라인 작성 시 색상 hash 로직 참조).
7. `docs/v1x-backlog.md` Tier 1 line 48 (closure 대상).

## 핵심 파일과 역할

| 파일 | 역할 |
|---|---|
| `frontend/src/components/files/RightPanel.tsx` | 본 트랙의 주 편집 대상 — 헤더/preview/detail row/탭 라벨. |
| `frontend/src/components/files/RightPanel.test.tsx` | 회귀 가드 + 신규 row 가시성. |
| `frontend/src/lib/kindLabel.ts` | 신규 — kind('doc'|'pdf'|...) → 한국어 라벨. |
| `frontend/src/lib/fileIcon.ts` | `fileIconKind(item)` 재사용. |
| `frontend/src/lib/api.ts` | `downloadFile(id)` 이미 존재 — 다운로드 버튼 wire. |
| `frontend/src/types/file.ts` | `FileItem.owner/sharedWith/folderPath` (PR #218 add). |
| `design-reference/panels.jsx` | 디자인 진실의 출처 L8~184. |

## 중요한 의사결정

- **`<dl>` → `<div>`**: 디자인 detail-row 형식 정합 + 복합 value (Avatar + name) 표현 용이.
- **mini-avatar 인라인**: admin.css `.p-avatar` 의존성 회피. 같은 색상 hash 로직만 재사용 (lib/avatar.ts 추출은 YAGNI — 첫 추출 시 P1 결정).
- **viewCount 분리**: ADR #9 FILE_VIEWED audit + 파티션 결정 선결. RightPanel UI에서 row 자체 미렌더.
- **더보기 disabled placeholder**: 디자인엔 dots 만 있고 동작 미정. scope creep 회피.
- **공유 = 권한 탭 전환**: 디자인 `onOpenPermissions` 의미 + 기존 BulkActionBar ShareDialog 와 entry 분리.
- **버전 라벨 count**: P5 — `useFileVersions.data.length` 에서 derive 가능하나 versions 탭 mount 전엔 count 미상. mount 시 prefetch 또는 detail envelope 확장이 필요해 **본 트랙에서는 라벨 그대로 "버전"** 유지. 추후 detail envelope 에 `versionsCount` 필드 추가 시 재오픈.

## 빠른 재개 안내

- 작업 브랜치: `feat/rightpanel-frontend-wire` (commit 1건 + 미커밋 변경 없음 확인 필요).
- worktree 미사용 — main repo path 직접 작업.
- 모든 구현 완료, PR 생성 단계.
- 머지 후 `dev/active/rightpanel-frontend-wire/` → `dev/completed/` 이동 + backlog PR #TBD → 실제 번호 followup commit.
- 검증 재현: `cd frontend && pnpm typecheck && pnpm lint && pnpm test --run src/components/files/RightPanel.test.tsx`.
