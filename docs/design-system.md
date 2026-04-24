# 디자인 시스템 (IbizDrive)

원본 레퍼런스: Claude Design 핸드오프 번들 `design_handoff_ibizdrive_file_explorer/styles.css`, `README.md`.
Linear / Notion 스타일의 조용한 엔터프라이즈 톤. 3열 레이아웃(사이드바 / 컨텐츠 / 우측 패널) + fixed bottom-right UploadDock.

---

## 1. 토큰 전략

### 두 층 구조

- **베이스 CSS 변수 (`:root` / `[data-theme="dark"]`)** — 디자인 원본 이름 그대로 (`--bg`, `--surface-1`, `--accent` …)
- **Tailwind `@theme inline` 매핑** — 베이스 변수를 참조해 Tailwind 유틸리티(`bg-bg`, `text-fg-muted`, `border-border`, `rounded-lg` …)로 노출

`@theme inline`이 `var(...)` 참조를 그대로 인라인하므로, `html[data-theme="dark"]`에서 값만 바꾸면 **모든 Tailwind 클래스가 자동으로 재평가**됩니다. 다크 모드 확장 시 컴포넌트 코드 수정 불필요.

### 왜 Tailwind 4?

이미 프로젝트는 Tailwind 4 + `@tailwindcss/postcss` 사용. 별도 `tailwind.config.ts` 없음 — Tailwind 4는 CSS-first. `globals.css`에 토큰 선언.

### 하드코딩 금지

컴포넌트에서는 `bg-white`, `text-gray-500`, `border-gray-200` 같은 절대값 사용 금지. 반드시 토큰 유틸 (`bg-surface-1`, `text-fg-muted`, `border-border`) 사용.

---

## 2. 색상

### Light (기본)

| 토큰 | 값 | Tailwind 클래스 예 |
|---|---|---|
| `--bg` | `#FAFAF9` | `bg-bg` |
| `--surface-1` | `#FFFFFF` | `bg-surface-1` |
| `--surface-2` | `#F3F3F1` | `bg-surface-2` (hover 표면) |
| `--surface-3` | `#EBEBE8` | `bg-surface-3` (press 표면, 바 트랙) |
| `--border` | `rgba(20,20,18,.08)` | `border-border` |
| `--border-strong` | `rgba(20,20,18,.14)` | `border-border-strong` |
| `--fg` | `#1C1B18` | `text-fg` |
| `--fg-2` | `#3A3832` | `text-fg-2` |
| `--fg-muted` | `#6C6A62` | `text-fg-muted` |
| `--fg-subtle` | `#97948C` | `text-fg-subtle` |
| `--accent` | `oklch(55% 0.14 250)` | `bg-accent`, `text-accent`, `border-accent` |
| `--accent-hover` | `oklch(50% 0.14 250)` | `hover:bg-accent-hover` |
| `--accent-soft` | `oklch(95% 0.03 250)` | `bg-accent-soft` (선택 행, 네비 액티브) |
| `--danger` | `oklch(55% 0.16 25)` | `text-danger`, `bg-danger` |
| `--warn` | `oklch(65% 0.13 70)` | `text-warn` |
| `--success` | `oklch(55% 0.11 150)` | `text-success` |

### Dark (오버라이드, `[data-theme="dark"]`)

- `--bg: #131313` / `--surface-1: #1A1A19` / `--surface-2: #232321` / `--surface-3: #2E2E2B`
- `--fg: #EAE9E4` / `--fg-muted: #8A8880`
- `--accent: oklch(72% 0.14 250)` / `--accent-soft: oklch(30% 0.08 250)`

---

## 3. 타이포그래피

### 폰트 패밀리

- Sans: `Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Pretendard, "Noto Sans KR", sans-serif`
- Mono: `"JetBrains Mono", ui-monospace, "SF Mono", Menlo, monospace`

### 사이즈 가이드

| 용도 | px | Tailwind |
|---|---|---|
| 본문 / row text | 13 | `text-[13px]` 또는 `text-sm` (14) 근사 |
| 행 메타 (크기/날짜) | 12.5 | `text-xs` (12) 근사 |
| 테이블 헤더 (uppercase) | 11 | `text-[11px]` + `uppercase tracking-wider` |
| 섹션 라벨 | 10.5-11 | `text-[11px] uppercase` |
| Breadcrumb last | 15 600 | `text-[15px] font-semibold` |
| Breadcrumb others | 13.5 | `text-[13.5px]` |
| 파일명 | 13 500 | `text-[13px] font-medium` |
| 패널 제목 | 13.5 600 | `text-[13.5px] font-semibold` |

Tailwind 기본 `text-sm`(14)/`text-xs`(12)가 1-1.5px 차이로 근사합니다. 정확도 요구 지점은 `text-[NNpx]` 임의값.

---

## 4. Spacing / Radius / Shadow / Row Heights

### Radius (토큰)

| 토큰 | 값 | Tailwind |
|---|---|---|
| `--radius-sm` | `4px` | `rounded-sm` |
| `--radius` | `6px` | `rounded` (기본) |
| `--radius-lg` | `10px` | `rounded-lg` |

체크박스 3px, 아이콘 버튼 6px, 카드/모달 8-10px.

### Shadow

| 토큰 | 값 | Tailwind |
|---|---|---|
| `--shadow-sm` | `0 1px 2px rgba(20,20,18,.04)` | `shadow-sm` |
| `--shadow-md` | `0 2px 8px rgba(20,20,18,.06), 0 1px 2px rgba(20,20,18,.04)` | `shadow-md` |
| `--shadow-lg` | `0 12px 40px rgba(20,20,18,.12), 0 2px 8px rgba(20,20,18,.06)` | `shadow-lg` (UploadDock, 모달) |

### 행 높이 (밀도)

| 밀도 | 값 |
|---|---|
| compact | 28px |
| regular (기본) | 34px |
| comfortable | 40px |

CSS 변수 `--row-h`로 노출. M5 이후 `view` store에 density 연결 예정 (현재는 기본 34px 하드코딩).

---

## 5. 컴포넌트 매핑 (주요 클래스 변환)

### 전역 리셋

- body: `font-sans text-[13px] leading-[1.45] text-fg bg-bg`

### Sidebar (FolderTree 래퍼)

- 248px 폭, `bg-surface-1 border-r border-border`
- 폴더 행: `px-2 py-1 rounded gap-1.5` + 호버 `hover:bg-surface-2` + 액티브 `bg-accent-soft text-accent font-medium`
- depth 들여쓰기: `paddingLeft: depth * 12 + 8` (현행 유지)

### Breadcrumb

- 컨테이너: `px-4 pt-2.5 pb-1.5 flex items-center gap-0.5 flex-wrap text-[13.5px] text-fg-muted`
- 구분자: `text-fg-subtle` (문자 `›` 또는 SVG chevron-right)
- 일반 링크: `px-1.5 py-[3px] rounded-sm hover:bg-surface-2 hover:text-fg`
- 마지막: `text-[15px] font-semibold text-fg`

### BulkActionBar

- `flex items-center justify-between px-4 py-1.5 bg-accent-soft border-y border-border`
- 선택 수: `text-[12.5px] font-semibold text-accent`
- 액션 버튼: `.btn`/`.btn-ghost` 스타일 (아래)

### 버튼 스타일

| 종류 | 클래스 |
|---|---|
| `.btn` (2차) | `h-7 px-2.5 rounded bg-surface-1 border border-border-strong text-fg text-[12.5px] font-medium hover:bg-surface-2 inline-flex items-center gap-1.5` |
| `.btn-primary` | `h-7 px-2.5 rounded bg-accent text-white border border-accent text-[12.5px] font-medium hover:bg-accent-hover inline-flex items-center gap-1.5` |
| `.btn-ghost` | `h-7 px-2.5 rounded bg-transparent text-fg-2 text-[12.5px] font-medium hover:bg-surface-2 hover:text-fg inline-flex items-center gap-1.5` |
| `.btn-ghost.danger` | 위 + `hover:bg-[color-mix(in_oklch,var(--danger)_12%,transparent)] hover:text-danger` |
| `.icon-btn` | `w-7 h-7 rounded text-fg-muted hover:bg-surface-2 hover:text-fg inline-flex items-center justify-center` |

접근성:
- 모든 버튼 `focus-visible:outline-2 focus-visible:outline-accent focus-visible:outline-offset-1` (프로젝트 전체 기본)
- `cursor: default` (엔터프라이즈 톤, 디자인 원본 따름)

### FileTable

- 헤더: `grid grid-cols-[36px_1fr_140px_130px_90px_44px] px-4 h-[30px] bg-surface-1 border-y border-border text-[11px] uppercase tracking-[0.04em] font-medium text-fg-muted items-center`
- 행: `grid grid-cols-[36px_1fr_140px_130px_90px_44px] px-4 items-center min-h-[var(--row-h)] border-b border-transparent`
- 호버: `hover:bg-surface-2`
- 선택: `bg-accent-soft` / 호버 시 `hover:bg-[color-mix(in_oklch,var(--accent)_22%,transparent)]`
- 열린 행(RightPanel 타겟): `shadow-[inset_2px_0_0_var(--accent)]`
- 드롭 타겟: `bg-accent-soft shadow-[inset_0_0_0_1.5px_var(--accent)] rounded`
- pending: `opacity-55`
- 이름 셀: `gap-2.5 font-medium text-fg`
- 소유자 셀: `text-fg-2 text-[12.5px]`
- 수정일/크기: `text-fg-muted text-[12.5px] tabular-nums`
- 액션 셀: `opacity-0 group-hover:opacity-100 transition-opacity`

`aria-rowcount`, `aria-rowindex`, `aria-selected`, `aria-multiselectable` 유지 (M3/M4 기존).

### 현재 6열 → 5열 매핑

현재 FileRow는 5열(icon/name/size/date/updater). 디자인은 6열(check/name/owner/modified/size/actions). M5/M7에서 체크박스/액션 컬럼을 추가하며 정식 매핑 예정. **현 M5 리스타일 단계는 5열 유지**, 너비만 디자인 비율에 맞게 조정:

`grid-cols-[28px_1fr_110px_130px_90px]` — icon / name / size / modified / updater. 체크박스/액션 컬럼은 M5 UploadButton 통합 시점에 재검토.

### RightPanel

- 폭 360px, `bg-surface-1 border-l border-border flex flex-col`
- 헤더: `px-3.5 pt-3 pb-2 border-b border-border flex items-center gap-2`
- 제목: `text-[13.5px] font-semibold text-fg truncate flex-1`
- 닫기: `.icon-btn` + aria-label
- 탭 바 (미구현): `flex border-b border-border px-2 gap-0.5` / 탭 `py-2.5 px-2.5 text-[12px] font-medium text-fg-muted border-b-2 border-transparent hover:text-fg` / 액티브 `text-fg border-accent`
- 상세 그리드: `grid grid-cols-[80px_1fr] gap-2.5 text-[12px]`
- 라벨: `text-fg-muted` / 값: `text-fg truncate`

### Empty / Forbidden / Error 상태

- 컨테이너: `flex-1 flex flex-col items-center justify-center gap-3 py-[60px] px-5 text-center`
- 아이콘 배경: `w-20 h-20 rounded-full bg-surface-2 text-fg-muted inline-flex items-center justify-center`
- 403 danger: `bg-[color-mix(in_oklch,var(--danger)_10%,transparent)] text-danger`
- 제목: `text-[15px] font-semibold text-fg`
- 설명: `text-[12.5px] text-fg-muted max-w-[320px]`

### Skeleton

- 애니메이션은 `animate-pulse` (Tailwind 기본) 유지
- 배경: `bg-surface-2` (기존 `bg-gray-200` 대체)

---

## 6. 레이아웃

### AppLayout (Explorer)

```
grid grid-rows-[48px_1fr] h-screen w-screen bg-bg text-fg
  ├─ TopBar (48px, bg-surface-1, border-b border-border)
  └─ app-main: grid grid-cols-[248px_1fr_auto] min-h-0 overflow-hidden
      ├─ Sidebar (248px, bg-surface-1, border-r border-border)
      ├─ Content (flex-1 min-w-0 flex flex-col bg-bg relative)
      └─ RightPanel (360px auto, bg-surface-1, border-l border-border)
```

TopBar는 본 리스타일 단계에서 skeleton만 (간단한 브랜드 마크 + 검색 placeholder). 실 기능은 M11(검색) 등에서.

### 반응형

디자인 원본의 mobile-view 클래스는 본 리스타일 단계 범위 밖. 데스크톱 3열 고정. 추후 media query 추가.

---

## 7. 접근성 원칙 (유지)

- `aria-rowcount/rowindex` (가상화) — 원칙 #8
- Esc: RightPanel 전역 → 선택 → 모달 순 (M6 구축, M5에서 ConflictDialog 추가)
- focus-visible ring은 모든 interactive에 적용 (브라우저 기본 + `focus-visible:outline-accent`)
- 색 대비: `--fg-muted` 6.1:1, `--fg-subtle` 4.1:1 (light 기준). 본문은 `--fg` 사용
- 포커스 트랩은 ConflictDialog(M5)에서 구현

---

## 8. 변경 영향 (컴포넌트별)

| 파일 | 변경 | 구조/로직 변경 |
|---|---|---|
| `app/globals.css` | 토큰 + Tailwind 매핑 도입 | ✗ |
| `app/(explorer)/layout.tsx` | 3열 그리드 + 토큰 적용 | 최소 (Sidebar 래퍼 style만) |
| `app/(explorer)/files/[...parts]/ClientFilesPage.tsx` | 레이아웃 className | ✗ |
| `components/folders/FolderTree.tsx` | 컬러 토큰 + 행 높이/radius | ✗ (이모지/구조 유지) |
| `components/folders/Breadcrumb.tsx` | 토큰 + 타이포 | ✗ |
| `components/files/BulkActionBar.tsx` | 토큰, 버튼 스타일 | ✗ |
| `components/files/FileTable.tsx` | 그리드 컬럼, 토큰 | ✗ (aria 유지) |
| `components/files/FileRow.tsx` | 토큰 기반 상태별 배경 | ✗ |
| `components/files/FileTableEmpty/Error/Forbidden/Skeleton.tsx` | 토큰 + 80px 아이콘 배경 | ✗ |
| `components/files/RightPanel.tsx` | 폭 360px, 토큰 | ✗ |
| `app/(explorer)/files/[...parts]/loading.tsx`, `error.tsx`, `not-found.tsx` | 토큰 + empty-state 패턴 | ✗ |

**절대 변경하지 않는 것:** 훅, 상태 스토어, API, 라우팅, 테스트 파일 assertion. 시각 변경만.

---

## 9. 후속 작업 (범위 밖)

- 아이콘 라이브러리 도입 (Lucide/Phosphor) — 현재는 이모지 유지 (새 의존성 회피)
- 웹 폰트 로딩 (`next/font`로 Inter / Pretendard) — 현재는 시스템 fallback
- TopBar 기능 (검색, 테마 토글, 아바타)
- 다크 모드 토글 UI (변수는 이미 세팅)
- GridView (FileTable grid 모드)
- UploadDock / ConflictDialog / DropOverlay (M5 업로드 구현 시점에 이 토큰 위에서)
- 탭 바 (RightPanel 버전/활동/권한)
- 밀도 토글 (compact/regular/comfortable)
