# Plan — RightPanel Frontend Wire (P_panel-B)

Last Updated: 2026-05-13

## 요약

PR #218(P_panel-A)에서 backend `FileDetailResponse`가 `owner`/`sharedWith`/`folderPath`를 노출하기 시작했다. 본 트랙은 그 소비자인 `RightPanel.tsx`를 `design-reference/panels.jsx §RightPanel L8~184` 정합으로 확장한다. 디자인 zip 대비 미반영 영역 4개(헤더 액션·preview 카드·detail row 풀세트·파일 아이콘) 를 채우고, viewCount만 ADR #9(FILE_VIEWED audit) blocker로 분리한다.

## 왜 bootstrap 인가

- 단일 파일 수정처럼 보이지만 변경 표면이 넓다: `RightPanel.tsx` + 새 helper (`kindLabel.ts`) + mini-avatar inline + 다운로드/공유 핸들러 + 테스트. 5+ commit 가능성.
- `v1x-backlog.md` Tier 1 line 48 closure 가 본 트랙의 acceptance — backlog 항목 backlink 필요.
- 다음 세션이 재개해도 디자인 정합 기준(어느 row를 wire 했고, viewCount를 왜 제외했는지)을 즉시 파악할 수 있어야 한다.

## 현재 상태 분석

### Frontend (master @ 6f4c766)

- `frontend/src/components/files/RightPanel.tsx` — 4탭 + Esc 닫기 + `PanelBody`. detail 탭은 5 row(이름·유형·크기·수정일·수정자) `<dl>` 형식.
- `frontend/src/types/file.ts` — `FileItem.owner?/sharedWith?/folderPath?` 이미 추가됨 (PR #218).
- `frontend/src/lib/api.ts` — `getFileDetail` envelope 파싱(owner/sharedWith/folderPath) + `downloadFile(id)` 이미 존재.
- `frontend/src/components/icons/FileTypeIcon.tsx` + `frontend/src/lib/fileIcon.ts` — 10 kind 컬러 SVG + mime→kind helper 존재 (PR #211).
- `frontend/src/components/topbar/Avatar.tsx` — placeholder 단일 글자 아바타. (explorer) 레이아웃 안에서만 사용 가능.
- `frontend/src/components/admin/teams/Avatars.tsx` — `PAvatar`/`PAvatarStack` 존재하나 `admin.css` `.p-avatar`에 의존 → (explorer) 영역에서 비호환. RightPanel은 자체 mini-avatar 인라인 필요.

### Design zip — `design-reference/panels.jsx` L8~184

차이점:
1. **rp-title-row** — 파일 아이콘 + 파일명 + 닫기 (현재는 텍스트만).
2. **rp-actions** — 다운로드 / 공유 / 더보기 3개 ghost 버튼 row.
3. **rp-preview** — 헤더와 탭 사이 PreviewCard (큰 file kind icon + placeholder lines, kind별 soft 배경).
4. **detail-list 8 row**:
   - 종류(kindLabel) / 크기 / 소유자(avatar+name) / 수정한 사람(avatar+name) / 수정일 / 공유됨(AvatarStack + N명) / 경로(folderPath breadcrumb) / 위치(restricted tag or "공개 링크 없음") / 최근 조회(N번).
5. **rp-tabs** — 버전 라벨에 카운트(`버전 N`).

### Backend / Docs

- `docs/02-backend-data-model.md §7.6` — GET /api/files/:id 응답 spec 확정 (4 필드 + privacy).
- `docs/v1x-backlog.md` Tier 1 line 48 — RightPanel 디자인 fidelity 항목, 본 트랙 closure 대상.
- `docs/01-frontend-design.md` §11 — 로딩/에러/빈 상태 규약.

## 목표 상태

1. `RightPanel.tsx` 헤더 = 파일 아이콘 + 이름 + (다운로드·공유·더보기) 액션 + 닫기.
2. 헤더 아래 PreviewCard placeholder — kind별 soft 배경 + 큰 컬러 SVG icon + 4 line placeholder.
3. detail 탭이 8 row를 표시 (viewCount 제외 = 7 row). 소유자/수정자는 mini-avatar, 공유됨은 AvatarStack(max 4) + N명.
4. 다운로드 버튼 = `api.downloadFile(id)`, 공유 버튼 = ?tab=permissions 전환(또는 ShareDialog), 더보기 = `<button disabled>` placeholder.
5. RightPanel.test.tsx 회귀 가드 PASS + 신규 row 가시성 테스트.
6. `docs/v1x-backlog.md` line 48 closure entry + `docs/progress.md` 본 트랙 entry.

## Phase 별 실행 지도

| phase | 범위 | 산출 | 검증 |
|---|---|---|---|
| P1 | helper — kindLabel · mini-avatar inline · 다운로드/공유 핸들러 stub | `frontend/src/lib/kindLabel.ts` 신규 + `RightPanel.tsx` 내부 헬퍼 | typecheck |
| P2 | detail 탭 7 row wire (viewCount 제외) | `RightPanel.tsx` `PanelBody` rewrite + detail row 풀세트 | typecheck + RightPanel.test.tsx 보강 |
| P3 | 헤더 — 파일 아이콘 + 액션 3개 (다운로드/공유/더보기) | `RightPanel.tsx` header rewrite | typecheck + RightPanel.test.tsx 가드 |
| P4 | PreviewCard placeholder | `RightPanel.tsx` 내부 컴포넌트 (또는 별도 file) | 시각 회귀는 dev preview 수기 — typecheck/lint 통과 |
| P5 | 탭 라벨 = 버전 N (선택) | `RightPanel.tsx` TABS labelOf + 버전 count source 결정 | typecheck (count 미지원이면 라벨 그대로) |
| P6 | docs + backlog closure | `docs/v1x-backlog.md` line 48 strikethrough + `docs/progress.md` entry | grep |

## Acceptance Criteria

- [ ] RightPanel 헤더에 파일 kind 아이콘 + 다운로드/공유/더보기 버튼 보임 (a11y label 포함).
- [ ] detail 탭이 종류/크기/소유자(avatar)/수정자(avatar)/수정일/공유됨(stack+count)/경로(breadcrumb)/위치(restricted or "공개 링크 없음") 7 row 표시.
- [ ] 다운로드 버튼 클릭 시 `api.downloadFile` 호출.
- [ ] 공유 버튼 클릭 시 권한 탭으로 전환 (또는 ShareDialog open — 결정 미정, P3에서 확정).
- [ ] `pnpm typecheck && pnpm lint && pnpm test --run RightPanel` 통과.
- [ ] `docs/v1x-backlog.md` Tier 1 RightPanel line closure + `docs/progress.md` 본 트랙 entry.

## 검증 게이트

- `pnpm typecheck` — RightPanel 사용처 (PermissionsTab/VersionsTab/ActivityTab) 타입 정합.
- `pnpm lint`.
- `pnpm test --run RightPanel` — 기존 4탭 가드 + 신규 row 가시성.
- 수기 시각 — dev preview (`pnpm dev`)에서 파일 1개 열어 RightPanel 형상 확인 (CLAUDE.md 데스크탑 메인 기준).

## 리스크와 완화

| 리스크 | 영향 | 완화 |
|---|---|---|
| owner/sharedWith가 backend에서 null fallback(soft-delete) 가능 | row 빈 표시 | 디자인의 `—` fallback 채택, AvatarStack는 빈 배열이면 "비공개" 라벨. |
| 다운로드 핸들러가 mime 미지원 파일에서 brittle | UX 저하 | 기존 `api.downloadFile` anchor click 패턴 그대로 — 백엔드가 책임. |
| 더보기 메뉴 — 디자인엔 dots 아이콘만 있고 동작 미정 | scope creep | MVP: `disabled` placeholder + TODO 주석. 추후 별도 트랙. |
| viewCount(FILE_VIEWED) — ADR #9 blocker | detail row 1개 누락 | row 자체 미렌더. backlog line에 "viewCount 분리" 명시. |
| 공유 버튼 동작 — 권한 탭 전환 vs ShareDialog | UX 모호 | 디자인의 `onOpenPermissions` 콜백 의미 = 권한 탭 전환 → tab 'permissions'로 setTab. ShareDialog는 별도 진입점(기존 BulkActionBar). |
| mini-avatar 색상 = PAvatar 와 시각 일관 | 디자인 일관성 | colorForUser hash + palette 8색 동일 로직 인라인 (admin과 동일 helper 사용 가능하면 lib/avatar.ts 추출 — P1에서 결정). |
