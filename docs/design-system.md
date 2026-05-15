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

### 토큰 이름 화이트리스트

`globals.css` `@theme inline` 블록에 정의된 토큰 이름만 사용. 노출된 토큰 ground truth 는 globals.css §`@theme inline` (`--color-bg`, `--color-surface-1/2/3`, `--color-border/-strong`, `--color-fg/-2/-muted/-subtle`, `--color-accent/-hover/-soft/-text/-fg`, `--color-danger`, `--color-warn`, `--color-success/-soft`) — 본 문서 §2 색상 표와 1:1.

**`bg-bg-1`, `bg-bg-2` 같은 미정의 이름은 Tailwind 가 silent drop** — 컴파일 에러 0, 테스트 fail 0, 시각상으로만 배경이 transparent 로 렌더됨. 2026-05-15 PR #267 sweep 이 9 사이트 / 6 파일 systemic drift 정정한 사례 — 컴포넌트 첫 작성 시 silent drop 이 발견되지 않아 후속 PR 들이 같은 typo 차용.

#### 회귀 가드 패턴

ESLint 자동 가드 없음. 시각 회귀를 막으려면 컴포넌트 test 에 className 단정 추가:

```ts
expect(element.className).toContain('bg-surface-1')
expect(element.className).not.toMatch(/\bbg-bg-\d/)
```

`bg-bg-\d` 정규식이 invalid 이름 재발을 1차 차단. 예시: PR #267 (FilterChips/FilterPopover) + PR #268 (DashboardCard/QuotaCard/StarredCard/SharedWithMeCard).

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

### M5 업로드 컴포넌트 (추가, 2026-04-25)

`styles.css`의 `.btn-primary` / `.drop-overlay` / `.upload-dock` / `.modal` 섹션 매핑.

#### UploadButton (`.btn-primary` / `.btn-ghost`)

- base: `h-7 px-2.5 inline-flex items-center gap-1.5 rounded border text-[12.5px] font-medium transition-colors`
- primary: `bg-accent text-white border-accent hover:bg-accent-hover hover:border-accent-hover`
- ghost: `bg-transparent text-fg-2 border-transparent hover:bg-surface-2 hover:text-fg`

#### UploadOverlay (`.drop-overlay`)

- `absolute inset-2 z-30 flex items-center justify-center pointer-events-none border-2 border-dashed border-accent rounded-lg backdrop-blur-[2px]`
- 배경: `bg-[color-mix(in_oklch,var(--accent)_8%,transparent)]`
- 안내문: `text-[14px] font-medium text-accent` (styles.css의 `drop-overlay-card` 카드형 구조는 JSX 변경 필요 — M13 범위 밖)

#### UploadQueueDock (`.upload-dock`)

- 컨테이너: `fixed bottom-5 right-5 w-[340px] max-h-[420px] z-40 bg-surface-1 border border-border-strong rounded-lg shadow-lg flex flex-col overflow-hidden`
- 헤더: `flex items-center justify-between px-3 py-2.5 border-b border-border bg-surface-2`
- 타이틀: `text-[12.5px] font-semibold text-fg`
- 개별 progress bar: `h-[2px] bg-surface-3 rounded-[1px]` + fill `bg-accent`(또는 실패 시 `bg-danger`)
- 개별 task 영역: `px-3.5 py-2` + `border-b border-border`(divide-y로 구현 중)

#### UploadConflictDialog (`.modal` / `.modal-bg`)

- 백드롭: `fixed inset-0 z-50 flex items-center justify-center p-5 bg-[rgba(0,0,0,0.32)]`
- 다이얼로그: `w-full max-w-[460px] bg-surface-1 border border-border rounded-lg shadow-lg p-5`
- 타이틀: `text-[15px] font-semibold text-fg`
- 설명: `text-[12.5px] text-fg-muted break-all`
- 버튼: 취소=`text-fg-2 hover:bg-surface-2` / 적용=`bg-accent text-white hover:bg-accent-hover`
- **미적용(JSX 구조 필요)**: styles.css `.radio-option`의 카드형(border + accent-soft checked 상태) → 현재는 평문 radio. M7/M8에서 카드형 라디오 적용 예정.

#### FolderToolbar (`.toolbar`)

- `flex items-center gap-2 px-4 py-2 border-b border-border bg-bg` (styles.css `.toolbar` 기본. 우측 정렬 액션은 추후 SortChip/ViewSwitch 도입 시)

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
- 탭 바 (RightPanel 버전/활동/권한)
- 밀도 토글 (compact/regular/comfortable)
- ConflictDialog 카드형 radio-option (M7/M8)
- UploadOverlay 안내 카드 (아이콘 + 제목 + 부제) — JSX 확장
- SortChip / ViewSwitch / StorageBar / StatusBar (mockup에만 존재, 기능 미구현)

---

## 10. Open Questions (미해결)

### 다크 모드 활성화 방식

현재 `[data-theme="dark"]` 오버라이드 토큰만 정의돼 있고 토글 UI 없음. 선택지:

1. **시스템 선호 기반** (`prefers-color-scheme`) — `<html data-theme="dark">` 조건부 적용
2. **사용자 토글 + localStorage** — TopBar에 토글 버튼, 선택 저장
3. **둘 다** — 시스템 기본 + 사용자 override

제안: 2번. 사용자 명시적 선택 우선, 초기 미선택 시 시스템 값. TopBar 구현 마일스톤 (추후)에서 결정.

### Variant 지원 범위

→ **M13.1 (2026-05-08) 에서 해소**: 4종 variant 모두 활성화 + TweaksPanel 에서 사용자 토글 가능. 자세한 내용은 §11.

### 폰트 로딩

현재 `Inter` / `JetBrains Mono`는 CSS fallback 체인에만 있고 실제 로드 없음 (시스템 `-apple-system` / `Segoe UI`로 대체). 선택지:

1. **next/font/google** (`Inter`, `JetBrains_Mono`) — SSR 최적화, 자동 subset
2. **CDN `<link>`** (Google Fonts) — design-reference의 방식. 간단하지만 FOIT/FOUT 가능성
3. **로드 안 함** — 시스템 폰트로 사용 (현재 상태)

제안: 1번 (next/font). 의존성 0개(Next.js 빌트인). M14 후보.

### 체크박스 / 액션 컬럼 (FileTable 6열)

design-reference는 6열 (check / name / owner / modified / size / actions). 현재 5열 (icon / name / size / modified / updater). BulkActionBar는 이미 선택 모델이 존재하므로 체크박스 컬럼 도입 준비 완료. M7에서 FileTable 재설계 때 함께 정리.

---

## 11. Variant 시스템 (M13.1)

### 11.1 개요

`[data-variant]` HTML 속성으로 4종 디자인 변형을 토글한다. 토큰 override 방식이라 컴포넌트 코드 변경 0건. `[data-theme="dark"]` 와 직교 — 두 axis 가 독립적으로 결합 가능 (`[data-variant="notion"][data-theme="dark"]` 등).

### 11.2 4종 비교

| Variant | 적용 방식 | bg | accent | row-h | font | 특징 |
|---|---|---|---|---|---|---|
| default | 속성 미설정 | warm gray (#FAFAF9) | indigo (oklch 55% 0.14 250) | 34px | Inter | 베이스 |
| notion | `[data-variant="notion"]` | warmer (#FCFCFB) | violet (oklch 50% 0.09 260) | 40px | Inter | radius 4px, comfy |
| dropbox | `[data-variant="dropbox"]` | warm gray | blue (oklch 58% 0.17 250) | 38px | Inter | accent + row-h 변경만 |
| terminal | `[data-variant="terminal"]` | dark (#0F0F0E) | green (oklch 78% 0.17 140) | 28px | JetBrains Mono | letter-spacing -0.01em, dense |

### 11.3 영속

- localStorage 키 `'variant'` (값: `'default'` | `'notion'` | `'dropbox'` | `'terminal'`).
- `'default'` 는 속성 자체를 제거 — base `:root` 토큰만 적용 (CSS 우선순위 단순).
- FOUC 방지: `app/layout.tsx` 의 inline `variantInitScript` 가 hydration 전 동기 실행 (themeInitScript 패턴 미러).

### 11.4 옵션 B (KISS) — terminal variant 폰트 처리

design-reference `styles.css` L94~113 의 terminal variant 17 selector 폰트 override (`.topbar`/`.sidebar`/`.btn` 등) 는 master frontend 의 Tailwind 유틸리티 환경에 그대로 이식 불가. 다음 두 줄로 흡수:

```css
[data-variant="terminal"] {
  --font-sans: var(--font-mono);
}
[data-variant="terminal"] body {
  letter-spacing: -0.01em;
}
```

`:root` 변수 토글이 모든 element 의 `font-sans` 유틸리티를 자동 전파. 옵션 A (의미적 className 노출 + per-element override) 는 v1.x.

### 11.5 사용 (TweaksPanel)

- TopBar 의 `<SlidersHorizontal>` 아이콘 → popover (`role="dialog"`) 안에 두 섹션:
  - "테마": ThemeToggle 임베드 (light/dark).
  - "변형": variant 4종 라디오 그룹 (`role="radiogroup"`).
- outside click / Esc 닫기. focus trap 미적용 (v1.x).

### 11.6 관련 파일

| 파일 | 역할 |
|---|---|
| `frontend/src/lib/variant.ts` | `Variant` 타입 + 5함수 (`theme.ts` 미러) |
| `frontend/src/hooks/useVariant.ts` | `{ variant, setVariant }` |
| `frontend/src/components/topbar/TweaksPanel.tsx` | popover UI |
| `frontend/src/app/globals.css` | variant 4종 CSS 블록 + terminal letter-spacing |
| `frontend/src/app/layout.tsx` | `variantInitScript` (FOUC) |
