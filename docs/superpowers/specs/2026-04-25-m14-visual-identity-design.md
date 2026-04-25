# M14 Visual Identity — 설계

> 자율 모드 spec. 마일스톤: docs/01 §18 #14.
> 진실 출처(시각): `design-reference/IbizDrive.html` + `styles.css` (M13 핸드오프).
> 토큰 기반 (M13 완료): `bg-bg`/`bg-surface-1`/`bg-accent`/`text-fg`/... 그대로 사용.

---

## 1. 범위

| 항목 | 추가/수정 |
|---|---|
| TopBar (검색/테마/아이콘 버튼) | 신규 |
| Lucide 아이콘 (mime → icon) | 신규 + FileRow 교체 |
| FileRow 밀도 (40 → 36px) | 수정 |
| StatusBar (main 하단) | 신규 |
| 의존성 `lucide-react` | 추가 |

**범위 외** (M15/M16):
- SortChip, ViewSwitch, StorageBar (sidebar 하단), RightPanel 탭 → M15
- Grid View → M16
- 다크 모드 토글 동작은 포함하나 사용자 설정 영구 저장은 v1.x

---

## 2. 선행 결정

### 2.1 lucide-react 의존성 추가 — **확정**
- 트리쉐이킹 아이콘 라이브러리. 사용분만 번들에 포함.
- 대안 (자체 SVG 컴포넌트화) 대비 유지보수 비용 ↓, 디자인 일관성 ↑.

### 2.2 StatusBar 위치 — **main 하단 확정**
- 근거: design-reference/styles.css `.status-bar`는 `border-top` + `surface-1` + `padding 6px 16px`로 정의되어 있고, sidebar 하단 영역은 별도 `.sidebar-storage` (M15 StorageBar)로 분리되어 있음.
- StatusBar = "현재 보고 있는 폴더의 컨텍스트" (선택 개수, 항목 개수, 합계 크기) → main 하단이 자연스러움.
- StorageBar (M15) = "사용자 계정 컨텍스트" (저장공간 진척률) → sidebar 하단.

### 2.3 FileRow 행 높이 — **40 → 36px**
- 현재 `h-10` (40px), `density-comfortable`. 디자인 ref 기본 톤은 더 dense.
- 36px로 조정 (font 13px 유지). `density-compact` 28px은 마우스 hit-area가 부족 → 채택 안 함.
- M14 단계에서는 단일 밀도 (toggle 없음). 토글은 v1.x.

### 2.4 TopBar 위치 — **전체 폭, sidebar+main 위**
- 디자인 ref 구조와 일치 (`grid-rows-[48px_1fr]` 후 `app-main` 가로 분할).
- 사이드바 브랜드(M13에서 마운트) 유지 — 사이드바는 좌측 네비게이션 컨텍스트, TopBar는 전역 컨텍스트.

### 2.5 테마 토글 — **TopBar에 단순 light/dark 토글, 비영구**
- `document.documentElement.dataset.theme` 직접 조작. 새로고침 시 light로 복귀.
- localStorage 영속화는 v1.x (사용자 설정 페이지 M9 합쳐서).

---

## 3. 컴포넌트 설계

### 3.1 `src/components/layout/TopBar.tsx` (신규)

```text
<header role="banner" className="grid grid-cols-[auto_1fr_auto] items-center gap-3 h-12 px-3 border-b border-border bg-surface-1">
  <button icon-btn> (Menu icon, sidebar toggle - 현재 no-op 스텁)
  <SearchBox /> (검색 영역, 가운데 max-w-[560px])
  <div topbar-right>
    <button icon-btn theme-toggle> (Sun/Moon)
    <button icon-btn help> (HelpCircle)
    <Avatar placeholder> ("나")
  </div>
</header>
```

**SearchBox**:
- `<input>` placeholder "드라이브에서 검색…"
- `useEffect`로 `app:focus-search` 이벤트 (M10 `useGlobalShortcuts` 디스패치) 수신 → input.focus()
- 검색 동작 자체는 M11 (검색) 진입 시 구현. M14에서는 placeholder + 포커스만.
- `⌘K` 키 힌트 표시 (text-only — 실제 핸들러는 M11).

**ThemeToggle**:
- 클릭 시 `document.documentElement.dataset.theme = current === 'dark' ? 'light' : 'dark'`.
- 아이콘은 현재 theme 반영 (light → Moon, dark → Sun).

**Avatar**:
- 현재 22px 원형. 이니셜 "나" + accent 배경. `usePermission` 등 유저 정보 훅이 없으므로 하드코딩.

### 3.2 `src/lib/fileIcons.tsx` (신규)

```ts
import { Folder, FileText, FileSpreadsheet, Image, Video, FileArchive, FileCode, File } from 'lucide-react'
import type { FileItem } from '@/types/file'

export function getFileIcon(item: FileItem) {
  if (item.type === 'folder') return Folder
  const m = item.mimeType ?? ''
  if (m.startsWith('image/')) return Image
  if (m.startsWith('video/')) return Video
  if (m.includes('pdf')) return FileText
  if (m.includes('spreadsheet') || m.includes('excel') || m.includes('csv')) return FileSpreadsheet
  if (m.includes('zip') || m.includes('compressed') || m.includes('tar')) return FileArchive
  if (m.includes('javascript') || m.includes('typescript') || m.includes('json') || m.includes('html') || m.includes('css')) return FileCode
  if (m.includes('word') || m.includes('document') || m.startsWith('text/')) return FileText
  return File
}

export function getFileIconColor(item: FileItem): string {
  // tailwind 클래스 반환. 폴더는 accent, 나머지는 fg-muted.
  if (item.type === 'folder') return 'text-accent'
  const m = item.mimeType ?? ''
  if (m.includes('pdf')) return 'text-danger'
  if (m.includes('spreadsheet') || m.includes('excel')) return 'text-[#3EA971]'
  if (m.startsWith('image/')) return 'text-[#B06BCC]'
  return 'text-fg-muted'
}
```

(단순 색상 매핑 — 디자인 ref의 풀 팔레트를 토큰으로 환원할지는 v1.x)

### 3.3 `src/components/files/FileRow.tsx` (수정)

- `fileIcon` 문자열 함수 제거 → `getFileIcon` lucide 컴포넌트 호출.
- `<span>📁</span>` → `<Icon size={16} className="..." />`.
- `h-10` → `h-9` (36px). `text-[13px]` 유지.
- aria-hidden 유지.

### 3.4 `src/components/files/FileTable.tsx` (수정)

- `ROW_HEIGHT = 40` → `36`.
- `GRID_COLS` 그대로 (5열 유지). M15에서 6열 확장 시 변경.

### 3.5 `src/components/layout/StatusBar.tsx` (신규)

```text
<footer role="contentinfo" className="flex items-center justify-between gap-3 px-4 h-7 border-t border-border bg-surface-1 text-[11.5px] text-fg-muted">
  <div>{itemCount}개 항목{selectedCount > 0 ? ` · ${selectedCount}개 선택됨` : ''}</div>
  <div>{selectedSizeSum > 0 ? formatSize(selectedSizeSum) : ''}</div>
</footer>
```

- `useFilesInFolder(folderId, sort, dir)` 호출 (이미 FileTable에서 호출 중이라 캐시 히트).
- `useSelectionStore`에서 `ids` 구독.
- 선택된 ids에 대응하는 size 합산 (folder는 0).

### 3.6 `(explorer)/layout.tsx` (수정)

```text
<DndProvider>
  <div className="grid grid-rows-[48px_1fr] h-screen w-screen bg-bg text-fg overflow-hidden">
    <TopBar />
    <div className="grid grid-cols-[248px_1fr] min-h-0">
      <aside ...>...</aside>
      <main ...>{children}</main>
    </div>
  </div>
</DndProvider>
```

### 3.7 `ClientFilesPage.tsx` (수정)

- 메인 컬럼 끝에 `<StatusBar />` 추가 (FileTable 아래).
- 다른 변경 없음.

---

## 4. 원칙 체크

| 원칙 | 영향 |
|---|---|
| #1 URL folderId | 영향 없음 (TopBar 검색은 M11에서 URL 파라미터 도입 예정) |
| #2 RightPanel query param | 영향 없음 |
| #3 낙관적 업데이트 비파괴적만 | 영향 없음 |
| #4 DnD 컨텍스트 분리 | TopBar/StatusBar는 DnD와 무관 |
| #5 가상화 aria-rowcount | FileRow 시각만 변경, aria 그대로 |
| #11 정규화 | 영향 없음 |
| #12 에러 코드 계약 | 영향 없음 |

aria 속성 모두 유지 (role=banner / contentinfo / row / gridcell).

---

## 5. 테스트 전략

| 컴포넌트 | 테스트 범위 |
|---|---|
| `fileIcons` | `getFileIcon` 매핑 8가지 (folder/pdf/image/video/sheet/code/word/default) |
| `TopBar` | (a) `app:focus-search` 이벤트 → input focus, (b) 테마 토글 클릭 → `data-theme` 전환, (c) 검색어 입력 + Clear |
| `StatusBar` | 항목 개수 표시, 선택 시 "N개 선택됨", 선택 size sum |
| `FileRow` | (기존 테스트 없음 — 이번에 emoji assertion 없음 검증만, 신규 추가 없음) |

기존 테스트 영향:
- `MoveFolderDialog.test.tsx`, `RenameDialog.test.tsx`, `RightPanel.test.tsx`, `UploadOverlay.test.tsx`, `UploadQueueDock.test.tsx`, `UploadConflictDialog.test.tsx` — emoji/icon assertion 없음, 영향 0.
- FileTable 통합 테스트 없음 (jsdom 한계).

---

## 6. 구현 순서

1. lucide-react 의존성 추가 + 설치
2. `lib/fileIcons.tsx` + 단위 테스트
3. `FileRow` 교체 (emoji → lucide)
4. FileRow/FileTable 행 높이 40 → 36
5. `components/layout/TopBar.tsx` + 단위 테스트
6. `(explorer)/layout.tsx` 그리드 재구성 + TopBar 마운트
7. `components/layout/StatusBar.tsx` + 단위 테스트
8. `ClientFilesPage` 마운트
9. 검증: typecheck / lint / test
10. docs/01 §18 M14 행에 완료 마커 + progress.md 갱신

---

## 7. DoD

- typecheck 통과
- lint 통과
- 기존 136개 + 신규 (~14) 테스트 통과
- 시각적: TopBar 48px / 사이드바 248px / FileRow 36px / StatusBar 28px 렌더링
- 키보드 `/` → TopBar 검색 인풋 포커스 (M10 트리거 + M14 리스너 결합)
- `?file=` 딥링크와 폴더 변경 등 기존 흐름 영향 없음
