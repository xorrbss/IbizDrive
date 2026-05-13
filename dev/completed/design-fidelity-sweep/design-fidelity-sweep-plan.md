# Design Fidelity Sweep — Plan

## 목표

`C:/project/IbizDrive_design.zip` (루트 영역: admin.jsx + admin-teams.jsx + styles.css 2316줄 + styles-teams.css)을 frontend 전체에 강제 반영. 메인 탐색기 + Admin Console 8탭 + 토큰까지 디자인 zip 우선.

## 사용자 결정 (2026-05-11 brainstorming)

1. **범위**: Visual fidelity sweep — 클래스/속성/컬럼/높이/애니메이션까지 zip 기준 강제. **CLAUDE.md §3 11원칙은 유지** (URL=진실, RightPanel=query param, 낙관적 비파괴, DnD 분리, ARIA). 디자인 zip README도 동일 전제.
2. **출시 일정**: Big-bang completion — sweep 완료 후 v1.0.0-beta 태그. 출시 **1~2주 슬립** 감수.
3. **분할**: 3-phase split (Tokens → 메인 탐색기 → Admin), phase별 PR.
4. **변종(variants)**: 기본 `linear`만 채택 (KISS). `notion`/`dropbox`/`terminal`은 v1.x backlog로 분리.
5. **mobile**: CLAUDE.md §3 원칙 13(폐기) 유지. `.mobile-view`, lg breakpoint, useMediaQuery 작업 진행 안 함.
6. **회귀 세션 영향**: master 머지 시 다른 세션 회귀 무효화 가능 — 사용자 OK 결정.

## 3-Phase 구성

### Phase 1 — Tokens + globals.css/admin.css

- zip styles.css 상단 `:root` 토큰 (`--bg`, `--surface-1~3`, `--border*`, `--fg*`, `--accent*`, `--danger`, `--warn`, `--success*`, `--shadow-*`, `--radius*`, `--row-h`, `--font-*`) 추출
- zip styles.css `[data-theme="dark"]` 토큰 추출
- zip styles-teams.css 추가 토큰 추출
- `frontend/src/app/globals.css` 토큰 영역 zip 기준 교체
- `frontend/src/app/admin/admin.css` 토큰 정렬 (필요 시)
- Tailwind config가 토큰 참조하면 갱신
- vitest 통과 확인 (토큰 변경은 클래스 대부분 영향 없음)
- PR: `feat/design-sweep-phase-1-tokens`

### Phase 2 — 메인 탐색기 + 공통 컴포넌트

- zip components.jsx + panels.jsx + icons.jsx 기준
- 대상: Sidebar / FolderTree / TopBar / Breadcrumb / Toolbar / BulkActionBar / FileTable / FileRow / GridView / GridThumb / RightPanel(4탭) / PreviewCard / UploadDock / UploadItem / ConflictDialog / DropOverlay / EmptyFolder / ForbiddenState / Avatar / FileIcon / UIIcon
- 매핑: 기존 `frontend/src/components/{layout,files,folders,trash,upload,common}/*` 위치 유지, 내부 JSX/className만 zip 기준 재구성
- 기능 invariant: URL routing / query param / Zustand store 인터페이스 / TanStack Query keys 그대로
- vitest 회귀: ARIA assertions, role checks는 zip 구조에서도 만족하도록 추가/조정
- PR: `feat/design-sweep-phase-2-explorer`

### Phase 3 — Admin Console

- zip admin.jsx + admin-teams.jsx 기준
- 대상: `/admin/{page,members,teams,permissions,storage,sharing,audit,retention,departments,system,trash}/*`
- 8탭 SPA 구조는 zip 디자인 prototype 표현 — frontend는 페이지별 라우트 유지 (Next.js App Router 구조 보존)
- Sharing 페이지: zip의 AdminSharing (line 582~706) 1:1 재현. 단, backend endpoint는 placeholder 유지 (v1.x backlog 별도 트랙)
- Overview / Storage / Retention / Audit 위젯 보강 (UploadChart / FlagRow / DeptRow / cleanup-list / LegalRow / SeverityTab)
- PR: `feat/design-sweep-phase-3-admin`

## 의존성 / blocker

- 회귀 세션이 master 사용 중 — Phase 1 머지 시 회귀 영향 (사용자 OK)
- 인프라팀 핸드오프 일정 — 1~2주 슬립 통보 필요 (사용자 작업)
- variants 4종 중 3종 v1.x 분리 — v1.x backlog 추가 트랙

## 트랙 종료 조건

- Phase 3 머지 + vitest GREEN + `pnpm build` PASS
- BETA-RELEASE.md §1 코드 게이트 재확인 + readiness entry 갱신
- v1.0.0-beta 태그 push 준비
